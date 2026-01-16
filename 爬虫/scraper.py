import requests
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry
from bs4 import BeautifulSoup
import matplotlib.pyplot as plt
import matplotlib
matplotlib.rcParams['font.sans-serif'] = ['SimHei']
matplotlib.rcParams['axes.unicode_minus'] = False
import json
import re
import time
import os
from urllib.parse import urljoin, urlparse, parse_qs
from concurrent.futures import ThreadPoolExecutor, as_completed
import threading

# --- 宏定义 ---
USERNAME = ""
PASSWORD = ""
EXAM_URL = "http://202.119.208.57/servlet/pc/ExamCaseController?exam_id=6532cc75-6929-476d-a155-cf12ec756fdd"
LOOP_COUNT = 100
BASE_URL = "http://202.119.208.57"
DEBUG = False  
PARALLEL_WORKERS = 6
# --- 宏定义结束 ---

QUESTION_BANK_FILE = 'question_bank.json'

question_bank_lock = threading.Lock()


class ExamCrawler:
    
    def __init__(self, worker_id=0):
        self.worker_id = worker_id
        self.session = requests.Session()
        
        retry_strategy = Retry(
            total=3,
            backoff_factor=0.5,
            status_forcelist=[500, 502, 503, 504],
        )
        adapter = HTTPAdapter(
            max_retries=retry_strategy,
            pool_connections=10,
            pool_maxsize=10
        )
        self.session.mount("http://", adapter)
        self.session.mount("https://", adapter)
        
        self.session.headers.update({
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
            'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7',
            'Accept-Language': 'zh-CN,zh;q=0.9,en;q=0.8',
            'Accept-Encoding': 'gzip, deflate',
            'Connection': 'keep-alive',
            'Upgrade-Insecure-Requests': '1',
            'Cache-Control': 'max-age=0',
        })
        self.logged_in = False
        self.current_url = None
    
    def _log(self, msg):
        if PARALLEL_WORKERS > 1:
            print(f"[Worker {self.worker_id}] {msg}")
        else:
            print(msg)
    
    def _save_debug(self, filename, content, prefix="debug_"):
        if DEBUG:
            filepath = f"{prefix}w{self.worker_id}_{filename}"
            with open(filepath, 'w', encoding='utf-8') as f:
                f.write(content)
            self._log(f"  [DEBUG] 已保存到 {filepath}")
    
    def _get_soup(self, html):
        return BeautifulSoup(html, 'html.parser')
    
    def _extract_viewstate(self, soup):
        vs = soup.find('input', {'name': 'javax.faces.ViewState'})
        if vs:
            return vs.get('value', '')
        vs = soup.find('input', {'id': re.compile(r'.*ViewState.*', re.I)})
        if vs:
            return vs.get('value', '')
        return None
    
    def _extract_form_data(self, soup, form_id=None):
        if form_id:
            form = soup.find('form', {'id': form_id})
        else:
            form = soup.find('form')
        
        if not form:
            return {}, None
        
        form_action = form.get('action', '')
        fields = {}
        
        for inp in form.find_all('input'):
            name = inp.get('name')
            if name:
                input_type = inp.get('type', 'text').lower()
                if input_type == 'checkbox' or input_type == 'radio':
                    if inp.get('checked'):
                        fields[name] = inp.get('value', 'on')
                else:
                    fields[name] = inp.get('value', '')
        
        for select in form.find_all('select'):
            name = select.get('name')
            if name:
                selected_option = select.find('option', selected=True)
                if selected_option:
                    fields[name] = selected_option.get('value', '')
                else:
                    first_option = select.find('option')
                    if first_option:
                        fields[name] = first_option.get('value', '')
        
        for textarea in form.find_all('textarea'):
            name = textarea.get('name')
            if name:
                fields[name] = textarea.get_text()
        
        return fields, form_action
    
    def _make_absolute_url(self, url, base=None):
        if not url:
            return base or BASE_URL
        if url.startswith('http'):
            return url
        return urljoin(base or BASE_URL, url)
    
    def login(self, username, password):
        self._log("步骤 1/6: 访问登录页面...")
        
        try:
            resp = self.session.get(f"{BASE_URL}/", timeout=15)
            resp.raise_for_status()
        except requests.RequestException as e:
            self._log(f"❌ 无法访问登录页面: {e}")
            return False
        
        self._save_debug('login_page.html', resp.text)
        self.current_url = resp.url
        
        soup = self._get_soup(resp.text)
        
        login_form = soup.find('form')
        if not login_form:
            self._log("❌ 未找到登录表单")
            return False
        
        form_id = login_form.get('id', '')
        form_action = login_form.get('action', '')
        self._log(f"  找到表单: id='{form_id}', action='{form_action}'")
        
        username_input = None
        password_input = None
        
        for inp in soup.find_all('input'):
            inp_id = inp.get('id', '').lower()
            inp_name = inp.get('name', '').lower()
            inp_type = inp.get('type', '').lower()
            
            if not username_input:
                if 'urn' in inp_id or 'user' in inp_id or 'name' in inp_id or 'account' in inp_id:
                    if inp_type != 'password':
                        username_input = inp
                elif 'urn' in inp_name or 'user' in inp_name or 'name' in inp_name or 'account' in inp_name:
                    if inp_type != 'password':
                        username_input = inp
            
            if not password_input:
                if inp_type == 'password':
                    password_input = inp
                elif 'pwd' in inp_id or 'pass' in inp_id:
                    password_input = inp
                elif 'pwd' in inp_name or 'pass' in inp_name:
                    password_input = inp
        
        if not username_input or not password_input:
            self._log("❌ 未找到用户名或密码输入框")
            return False
        
        username_name = username_input.get('name')
        password_name = password_input.get('name')
        self._log(f"  用户名字段: {username_name}")
        self._log(f"  密码字段: {password_name}")
        
        self._log("步骤 2/6: 准备登录数据...")
        form_data, _ = self._extract_form_data(soup, form_id if form_id else None)
        
        form_data[username_name] = username
        form_data[password_name] = password
        
        login_button = soup.find('button', {'id': re.compile(r'.*login.*', re.I)})
        if not login_button:
            login_button = soup.find('button', {'type': 'submit'})
        if not login_button:
            login_button = soup.find('input', {'type': 'submit'})
        
        if login_button:
            btn_name = login_button.get('name')
            btn_id = login_button.get('id')
            if btn_name:
                form_data[btn_name] = login_button.get('value', '')
            if btn_id and ':' in btn_id:
                form_data[btn_id] = ''
            self._log(f"  登录按钮: id='{btn_id}' name='{btn_name}'")
        
        submit_url = self._make_absolute_url(form_action, resp.url)
        self._log(f"  提交 URL: {submit_url}")
        
        self._log("步骤 3/6: 提交登录请求...")
        self.session.headers['Referer'] = resp.url
        self.session.headers['Origin'] = BASE_URL
        
        try:
            resp = self.session.post(submit_url, data=form_data, timeout=15, allow_redirects=True)
            resp.raise_for_status()
        except requests.RequestException as e:
            self._log(f"❌ 登录请求失败: {e}")
            return False
        
        self._save_debug('after_login.html', resp.text)
        self.current_url = resp.url
        self._log(f"  登录后 URL: {resp.url}")
        
        if "Default.jspx" in resp.url or "talk" in resp.url:
            self._log("✅ 登录成功！")
            self.logged_in = True
            return True
        
        soup = self._get_soup(resp.text)
        
        logout_link = soup.find('a', href=re.compile(r'logout|signout|exit', re.I))
        if logout_link:
            self._log("✅ 登录成功！（检测到退出链接）")
            self.logged_in = True
            return True
        
        error_patterns = [
            soup.find(class_=re.compile(r'error|alert-danger|warning', re.I)),
            soup.find(id=re.compile(r'error|message', re.I)),
            soup.find(string=re.compile(r'用户名.*错误|密码.*错误|登录失败', re.I))
        ]
        
        for error in error_patterns:
            if error:
                error_text = error.get_text(strip=True) if hasattr(error, 'get_text') else str(error)
                self._log(f"❌ 登录失败: {error_text[:100]}")
                return False
        
        if resp.url != f"{BASE_URL}/" and len(resp.url) > len(BASE_URL) + 5:
            self._log("✅ 登录可能成功（URL 已变化）")
            self.logged_in = True
            return True
        
        self._log(f"⚠️ 登录状态不确定，当前 URL: {resp.url}")
        return False
    
    def do_exam(self):
        if not self.logged_in:
            self._log("❌ 请先登录")
            return None
        
        self._log(f"步骤 4/6: 访问考试页面...")
        
        self.session.headers['Referer'] = self.current_url or BASE_URL
        
        try:
            resp = self.session.get(EXAM_URL, timeout=15)
            resp.raise_for_status()
        except requests.RequestException as e:
            self._log(f"❌ 无法访问考试页面: {e}")
            return None
        
        self._save_debug('exam_page.html', resp.text)
        self.current_url = resp.url
        
        soup = self._get_soup(resp.text)
        
        start_button = soup.find('button', onclick=re.compile(r'begin', re.I))
        if start_button:
            self._log("  发现'开始考试'按钮，尝试处理...")
            
            btn_id = start_button.get('id', '')
            btn_name = start_button.get('name', '')
            
            if btn_id or btn_name:
                form_data, form_action = self._extract_form_data(soup)
                viewstate = self._extract_viewstate(soup)
                
                if viewstate:
                    form_data['javax.faces.ViewState'] = viewstate
                
                if btn_id:
                    form_data[btn_id] = ''
                if btn_name:
                    form_data[btn_name] = ''
                
                submit_url = self._make_absolute_url(form_action, resp.url)
                
                try:
                    resp = self.session.post(submit_url, data=form_data, timeout=15, allow_redirects=True)
                    resp.raise_for_status()
                    soup = self._get_soup(resp.text)
                    self._save_debug('after_start.html', resp.text)
                except requests.RequestException as e:
                    self._log(f"  ⚠️ 点击开始按钮失败: {e}")
        
        my_form = soup.find('form', {'id': 'myForm'})
        if my_form:
            self._log("✅ 找到考试表单 (myForm)")
        else:
            if "ExamCaseReport" in resp.url or "Report" in resp.url:
                return resp.text
        
        self._log("步骤 5/6: 提交试卷...")
        
        form_data, form_action = self._extract_form_data(soup, 'myForm')
        viewstate = self._extract_viewstate(soup)
        
        if viewstate:
            form_data['javax.faces.ViewState'] = viewstate
        
        submit_button = soup.find('button', {'id': 'myForm:subcase'})
        if submit_button:
            form_data['myForm:subcase'] = ''
        else:
            submit_button = soup.find('button', string=re.compile(r'提交'))
            if submit_button:
                btn_id = submit_button.get('id', '')
                btn_name = submit_button.get('name', '')
                if btn_id:
                    form_data[btn_id] = ''
                if btn_name:
                    form_data[btn_name] = ''
            else:
                form_data['myForm:subcase'] = ''
        
        submit_url = self._make_absolute_url(form_action, resp.url)
        
        self.session.headers['Referer'] = resp.url
        
        try:
            resp = self.session.post(submit_url, data=form_data, timeout=15, allow_redirects=True)
            resp.raise_for_status()
        except requests.RequestException as e:
            self._log(f"❌ 提交请求失败: {e}")
            return None
        
        self._save_debug('after_submit.html', resp.text)
        self.current_url = resp.url
        
        soup = self._get_soup(resp.text)
        
        if "ExamCaseResult" in resp.url or "ExamCaseReport" in resp.url or "Report" in resp.url:
            self._log("✅ 步骤 6/6: 成功进入报告页面!")
            
            if "ExamCaseResult" in resp.url:
                soup = self._get_soup(resp.text)
                view_details = soup.find('button', string=re.compile(r'查看详情'))
                if view_details:
                    onclick = view_details.get('onclick', '')
                    
                    url_match = re.search(r"window\.open\(['\"]([^'\"]+)['\"]", onclick)
                    if url_match:
                        report_path = url_match.group(1)
                        report_path = report_path.replace('\\/', '/')
                        report_url = self._make_absolute_url(report_path, resp.url)
                        
                        try:
                            self.session.headers['Referer'] = resp.url
                            resp = self.session.get(report_url, timeout=15, allow_redirects=True)
                            resp.raise_for_status()
                            self._save_debug('report_page.html', resp.text)
                            self._log(f"  ✅ 成功获取报告页面")
                            return resp.text
                        except requests.RequestException as e:
                            self._log(f"  ⚠️ 获取报告页面失败: {e}")
            
            return resp.text
        
        if soup.find(class_='ui-panel-content') or soup.find(string=re.compile(r'正确答案')):
            self._log("✅ 步骤 6/6: 页面包含报告内容!")
            return resp.text
        
        self._log(f"❌ 未能跳转到报告页面")
        return None
    
    def close(self):
        self.session.close()


def load_question_bank():
    if os.path.exists(QUESTION_BANK_FILE):
        try:
            with open(QUESTION_BANK_FILE, 'r', encoding='utf-8') as f:
                data = json.load(f)
            if "单选题" in data or "多选题" in data or "判断题" in data:
                flat_bank = {}
                for cat in data:
                    if isinstance(data[cat], dict):
                        flat_bank.update(data[cat])
                return flat_bank
            else:
                return data
        except (json.JSONDecodeError):
            print(f"警告: {QUESTION_BANK_FILE} 文件格式错误，将创建一个新的题库。")
            return {}
    return {}


def save_question_bank(bank):
    categorized_bank = {
        "单选题": {},
        "多选题": {},
        "判断题": {}
    }
    flat_bank = {}
    if "单选题" in bank or "多选题" in bank or "判断题" in bank:
        for cat in bank:
            if isinstance(bank[cat], dict):
                flat_bank.update(bank[cat])
        for k, v in bank.items():
            if k not in ["单选题", "多选题", "判断题"]:
                flat_bank[k] = v
    else:
        flat_bank = bank
    for q_text, q_data in flat_bank.items():
        clean_text = re.sub(r'^\d+[、.]\s*', '', q_text).strip()
        answer = q_data.get('answer', '')
        if answer in ['正确', '错误', 'true', 'false']:
            categorized_bank['判断题'][clean_text] = q_data
        elif len(answer) > 1:
            categorized_bank['多选题'][clean_text] = q_data
        else:
            categorized_bank['单选题'][clean_text] = q_data
    with open(QUESTION_BANK_FILE, 'w', encoding='utf-8') as f:
        json.dump(categorized_bank, f, ensure_ascii=False, indent=4)
    print(f"题库已成功保存到 {QUESTION_BANK_FILE} (已分类)")


def parse_report_page(html_content, question_bank):
    def is_question_title(tag):
        if not getattr(tag, 'name', None):
            return False
        if tag.name != 'span':
            return False
        classes = tag.get('class', [])
        if 'choiceTitle' not in classes:
            return False
        text = tag.get_text(strip=True)
        return bool(re.match(r'^\d+[、.]', text))

    def clean_question_text(text):
        text = re.sub(r'^\d+[、.]\s*', '', text).strip()
        text = re.sub(r'（?\d+\.\d+分）?', '', text).strip()
        return text.strip()

    def normalize_answer(answer):
        if not answer:
            return None
        answer = answer.replace('.', '').replace(' ', '').strip()
        if answer.lower() == 'true':
            return '正确'
        if answer.lower() == 'false':
            return '错误'
        return answer

    def extract_answer(block):
        selectors = [
            'span[style*="color:green"][style*="font-weight"]',
            'span[style*="color: green"]',
            '.answer span[style*="color"]',
            'span.answer'
        ]
        for selector in selectors:
            span = block.select_one(selector)
            if span:
                return normalize_answer(span.get_text(strip=True))
        label = block.find(string=re.compile(r'正确答案'))
        if label:
            parent = label.find_parent()
            if parent:
                span = parent.select_one('span[style*="color"]')
                if span:
                    return normalize_answer(span.get_text(strip=True))
        return None

    soup = BeautifulSoup(html_content, 'html.parser')
    panels = soup.select('.ui-panel-content')

    if not panels:
        return question_bank, 0

    new_questions_found = 0

    for panel in panels:
        children = [child for child in panel.children if getattr(child, 'name', None)]
        idx = 0
        while idx < len(children):
            node = children[idx]
            idx += 1
            if not is_question_title(node):
                continue

            raw_question = node.get_text(strip=True)
            options = []
            correct_answer = None

            while idx < len(children):
                current = children[idx]
                idx += 1
                if current.name == 'hr':
                    break
                if is_question_title(current):
                    idx -= 1
                    break
                if current.name != 'div':
                    continue

                opt_spans = current.select('span.choiceTitle')
                for opt in opt_spans:
                    opt_text = opt.get_text(strip=True)
                    if not re.match(r'^\d+[、.]', opt_text):
                        options.append(opt_text)

                if not correct_answer:
                    correct_answer = extract_answer(current)

            clean_text = clean_question_text(raw_question)
            correct_answer = normalize_answer(correct_answer)

            if clean_text and correct_answer:
                with question_bank_lock:
                    is_new = clean_text not in question_bank
                    record = question_bank.setdefault(clean_text, {})
                    record['answer'] = correct_answer
                    if options:
                        unique_options = list(dict.fromkeys(opt for opt in options if opt))
                        if unique_options:
                            record['options'] = unique_options
                    if is_new:
                        new_questions_found += 1

    return question_bank, new_questions_found


def count_categories(bank):
    counts = {"单选题": 0, "多选题": 0, "判断题": 0}
    for q_data in bank.values():
        answer = q_data.get('answer', '')
        if answer in ['正确', '错误', 'true', 'false']:
            counts['判断题'] += 1
        elif len(answer) > 1:
            counts['多选题'] += 1
        else:
            counts['单选题'] += 1
    return counts


def plot_results(history):
    if not history or not history.get('total') or len(history['total']) < 1:
        print("数据点不足，无法生成图表。")
        return
    plt.rcParams.update({'font.size': 16})
    plt.figure(figsize=(16, 10))
    iterations = range(1, len(history['total']) + 1)
    lines_config = [
        ('total', '题库总数', '#e74c3c', 'o'),
        ('single', '单选题', '#3498db', 's'),
        ('multi', '多选题', '#2ecc71', '^'),
        ('judge', '判断题', '#f1c40f', 'D')
    ]
    for key, label, color, marker in lines_config:
        if key in history and history[key]:
            data = history[key]
            plt.plot(iterations, data, marker=marker, linestyle='-', color=color,
                     linewidth=4, markersize=10, label=label)
            if data:
                plt.text(iterations[-1], data[-1], f' {data[-1]}',
                         ha='left', va='center', fontsize=18, fontweight='bold', color=color)
    if len(history['total']) > 1:
        growth = history['total'][-1] - history['total'][0]
        plt.title(f'习概题库爬取 (总增长: {growth} 题)', fontsize=26, fontweight='bold', pad=20)
    else:
        plt.title('习概题库爬取', fontsize=26, fontweight='bold', pad=20)
    plt.xlabel('循环次数', fontsize=22, labelpad=15)
    plt.ylabel('题目数量', fontsize=22, labelpad=15)
    plt.grid(True, which='major', linestyle='-', linewidth=1.5, alpha=0.6, color='gray')
    plt.grid(True, which='minor', linestyle=':', linewidth=1.0, alpha=0.4, color='lightgray')
    plt.minorticks_on()
    plt.xticks(fontsize=18)
    plt.yticks(fontsize=18)
    plt.legend(fontsize=20, loc='upper left', frameon=True, shadow=True, borderpad=1)
    plt.tight_layout()
    plot_filename = 'question_growth.png'
    plt.savefig(plot_filename, dpi=300)
    print(f"📊 图表已保存为 {plot_filename}")
    try:
        plt.show()
    except:
        pass


def get_user_input():
    global USERNAME, PASSWORD, EXAM_URL, LOOP_COUNT
    if not USERNAME:
        USERNAME = input("请输入您的用户名: ")
    if not PASSWORD:
        PASSWORD = input("请输入您的密码: ")
    if not EXAM_URL:
        EXAM_URL = input("请输入考试的 URL: ")
    if LOOP_COUNT is None:
        while True:
            try:
                LOOP_COUNT = int(input("请输入循环次数: "))
                break
            except ValueError:
                print("请输入一个有效的数字。")


def worker_task(worker_id, task_range, question_bank, results, history_records):
    crawler = ExamCrawler(worker_id)
    local_added = 0
    local_completed = 0
    
    try:
        max_login_retries = 5
        login_success = False
        for retry in range(max_login_retries):
            if crawler.login(USERNAME, PASSWORD):
                login_success = True
                break
            else:
                if retry < max_login_retries - 1:
                    wait_time = (retry + 1) * 2
                    print(f"[Worker {worker_id}] ⚠️ 登录失败，{wait_time}秒后重试 ({retry + 1}/{max_login_retries})")
                    time.sleep(wait_time)
                    crawler.close()
                    crawler = ExamCrawler(worker_id)
        
        if not login_success:
            print(f"[Worker {worker_id}] ❌ 登录失败，已重试{max_login_retries}次")
            return
        
        for i in task_range:
            try:
                report_html = crawler.do_exam()
                
                if report_html:
                    _, added = parse_report_page(report_html, question_bank)
                    local_added += added
                    local_completed += 1
                    
                    with question_bank_lock:
                        current_total = len(question_bank)
                        cats = count_categories(question_bank)
                        history_records.append({
                            'timestamp': time.time(),
                            'total': current_total,
                            'single': cats['单选题'],
                            'multi': cats['多选题'],
                            'judge': cats['判断题']
                        })
                    
                    if added > 0:
                        print(f"[Worker {worker_id}] 第 {i} 次完成，新增 {added} 题，当前共 {current_total} 题")
                    else:
                        print(f"[Worker {worker_id}] 第 {i} 次完成，无新题")
                else:
                    print(f"[Worker {worker_id}] 第 {i} 次失败")
                    
            except Exception as e:
                print(f"[Worker {worker_id}] 第 {i} 次出错: {e}")
                
    finally:
        crawler.close()
        results[worker_id] = {'added': local_added, 'completed': local_completed}


def main():
    print("=" * 70)
    print(" " * 20 + "南林考试系统自动爬虫")
    print("=" * 70)
    
    get_user_input()
    
    question_bank = load_question_bank()
    history = {
        'total': [],
        'single': [],
        'multi': [],
        'judge': []
    }
    
    initial_q_count = len(question_bank)
    print(f"\n📚 启动时，题库中已有 {initial_q_count} 道题目")
    print(f"🔄 计划循环次数: {LOOP_COUNT}")
    print(f"⚡ 并行线程数: {PARALLEL_WORKERS}")
    if DEBUG:
        print(f"🐛 调试模式: 开启")
    print()
    
    start_time = time.time()
    
    if PARALLEL_WORKERS > 1:
        print(f"🚀 启动 {PARALLEL_WORKERS} 个并行工作线程...")
        print("=" * 70)
        
        tasks_per_worker = LOOP_COUNT // PARALLEL_WORKERS
        remainder = LOOP_COUNT % PARALLEL_WORKERS
        
        task_ranges = []
        current = 1
        for w in range(PARALLEL_WORKERS):
            count = tasks_per_worker + (1 if w < remainder else 0)
            if count > 0:
                task_ranges.append(list(range(current, current + count)))
                current += count
        
        results = {}
        history_records = []
        
        with ThreadPoolExecutor(max_workers=PARALLEL_WORKERS) as executor:
            futures = []
            for worker_id, task_range in enumerate(task_ranges):
                if task_range:
                    future = executor.submit(worker_task, worker_id, task_range, question_bank, results, history_records)
                    futures.append(future)
            
            for future in as_completed(futures):
                try:
                    future.result()
                except Exception as e:
                    print(f"Worker 异常: {e}")
        
        history_records.sort(key=lambda x: x['timestamp'])
        for record in history_records:
            history['total'].append(record['total'])
            history['single'].append(record['single'])
            history['multi'].append(record['multi'])
            history['judge'].append(record['judge'])
        
        total_added = sum(r.get('added', 0) for r in results.values())
        total_completed = sum(r.get('completed', 0) for r in results.values())
        
        print("\n" + "=" * 70)
        print(f"📊 并行执行完成")
        print(f"   完成次数: {total_completed}/{LOOP_COUNT}")
        print(f"   新增题目: {total_added}")
        
    else:
        crawler = ExamCrawler()
        
        try:
            for i in range(1, LOOP_COUNT + 1):
                print("\n" + "=" * 70)
                print(f"{'  第 ' + str(i) + '/' + str(LOOP_COUNT) + ' 次循环':^70}")
                print("=" * 70)
                
                try:
                    if i == 1:
                        if not crawler.login(USERNAME, PASSWORD):
                            print("❌ 登录失败，终止程序")
                            break
                    else:
                        print("ℹ️  使用已有登录会话...")
                    
                    report_html = crawler.do_exam()
                    
                    if not report_html:
                        print("❌ 无法获取报告页面，跳过本次循环")
                        continue
                    
                    print("\n📖 正在解析报告页面...")
                    old_count = len(question_bank)
                    question_bank, added = parse_report_page(report_html, question_bank)
                    new_count = len(question_bank)
                    
                    cats = count_categories(question_bank)
                    history['total'].append(new_count)
                    history['single'].append(cats['单选题'])
                    history['multi'].append(cats['多选题'])
                    history['judge'].append(cats['判断题'])
                    
                    print("\n" + "=" * 70)
                    print(f"  ✅ 第 {i} 次循环完成")
                    print(f"  📈 本次新增: {added} 道题")
                    print(f"  📚 当前统计: 总计 {new_count} | 单选 {cats['单选题']} | 多选 {cats['多选题']} | 判断 {cats['判断题']}")
                    print("=" * 70)
                    
                except Exception as e:
                    print(f"\n❌ 循环 {i} 中发生错误: {e}")
                    import traceback
                    traceback.print_exc()
                    
                    if i == 1:
                        print("\n⚠️ 第一次循环失败")
                        break
                    
        finally:
            crawler.close()
    
    elapsed_time = time.time() - start_time
    
    print("\n" + "=" * 70)
    print(f"⏱️  总耗时: {elapsed_time:.1f} 秒")
    
    if len(question_bank) > initial_q_count:
        save_question_bank(question_bank)
        total_added = len(question_bank) - initial_q_count
        print(f"✅ 题库已更新：从 {initial_q_count} 增加到 {len(question_bank)} 道题")
        print(f"📈 本次运行共新增 {total_added} 道题")
        if elapsed_time > 0:
            print(f"🚀 平均速度: {total_added / elapsed_time * 60:.1f} 题/分钟")
    else:
        print("ℹ️  题库没有更新")
    
    if history['total']:
        print("\n📊 正在生成题库增长图表...")
        plot_results(history)
    
    print("\n" + "=" * 70)
    print(" " * 28 + "🎉 任务完成！")
    print("=" * 70)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n\n⚠️  用户中断程序")
    except Exception as e:
        print(f"\n\n❌ 程序异常退出: {e}")
        import traceback
        traceback.print_exc()
