from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.chrome.options import Options as ChromeOptions
from selenium.webdriver.edge.options import Options as EdgeOptions
from bs4 import BeautifulSoup
import matplotlib.pyplot as plt
import matplotlib
matplotlib.rcParams['font.sans-serif'] = ['SimHei']
matplotlib.rcParams['axes.unicode_minus'] = False
import json
import re
import time
import os

# --- 宏定义 ---
USERNAME = ""
PASSWORD = ""
EXAM_URL = "http://202.119.208.57/servlet/pc/ExamCaseController?exam_id=0f770163-73fe-4328-861a-dfd15ce26726"
LOOP_COUNT = 2
BASE_URL = "http://202.119.208.57"
HEADLESS = True  # 设置为 True 启用无头模式（看不到浏览器窗口）
USE_EDGE = True   # 设置为 True 使用 Edge 浏览器，False 使用 Chrome
# WebDriver 获取策略: 'auto' (自动尝试所有), 'manager' (仅 webdriver-manager), 'system' (仅系统路径), 'local' (仅本地文件)
DRIVER_STRATEGY = 'system'
# --- 宏定义结束 ---

QUESTION_BANK_FILE = 'question_bank.json'

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

def create_driver():
    browser_name = "Edge" if USE_EDGE else "Chrome"
    print(f"  正在配置 {browser_name} 浏览器...")
    if USE_EDGE:
        options = EdgeOptions()
    else:
        options = ChromeOptions()
    if HEADLESS:
        options.add_argument('--headless')
        options.add_argument('--disable-gpu')
    options.add_argument('--no-sandbox')
    options.add_argument('--disable-dev-shm-usage')
    options.add_argument('--disable-blink-features=AutomationControlled')
    options.add_argument('--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36')
    options.add_argument('--window-size=1920,1080')
    options.add_experimental_option('excludeSwitches', ['enable-automation'])
    options.add_experimental_option('useAutomationExtension', False)
    options.add_experimental_option("prefs", {"profile.managed_default_content_settings.images": 2})
    driver = None
    if USE_EDGE:
        if DRIVER_STRATEGY in ['auto', 'manager']:
            try:
                from selenium.webdriver.edge.service import Service
                from webdriver_manager.microsoft import EdgeChromiumDriverManager
                print("  尝试使用 webdriver-manager 自动管理 EdgeDriver...")
                service = Service(EdgeChromiumDriverManager().install())
                driver = webdriver.Edge(service=service, options=options)
                print("  ✓ 使用 webdriver-manager 成功")
            except ImportError:
                print("  ⚠️ webdriver-manager 未安装，尝试其他方法...")
                print("  提示: 运行 'pip install webdriver-manager' 可自动管理 EdgeDriver")
            except Exception as e:
                print(f"  ⚠️ webdriver-manager 失败: {e}")
        if driver is None and DRIVER_STRATEGY in ['auto', 'system']:
            try:
                print("  尝试使用系统内置的 EdgeDriver...")
                driver = webdriver.Edge(options=options)
                print("  ✓ 使用系统 EdgeDriver 成功")
            except Exception as e:
                print(f"  ⚠️ 系统 EdgeDriver 失败: {e}")
        if driver is None and DRIVER_STRATEGY in ['auto', 'local']:
            try:
                from selenium.webdriver.edge.service import Service
                local_driver_path = os.path.join(os.path.dirname(__file__), 'msedgedriver.exe')
                if os.path.exists(local_driver_path):
                    print(f"  尝试使用本地 EdgeDriver: {local_driver_path}")
                    service = Service(local_driver_path)
                    driver = webdriver.Edge(service=service, options=options)
                    print("  ✓ 使用本地 EdgeDriver 成功")
                else:
                    if DRIVER_STRATEGY == 'local':
                        print(f"  ⚠️ 本地未找到 msedgedriver.exe")
            except Exception as e:
                print(f"  ⚠️ 本地 EdgeDriver 失败: {e}")
    else:
        if DRIVER_STRATEGY in ['auto', 'manager']:
            try:
                from selenium.webdriver.chrome.service import Service
                from webdriver_manager.chrome import ChromeDriverManager
                print("  尝试使用 webdriver-manager 自动管理 ChromeDriver...")
                service = Service(ChromeDriverManager().install())
                driver = webdriver.Chrome(service=service, options=options)
                print("  ✓ 使用 webdriver-manager 成功")
            except ImportError:
                print("  ⚠️ webdriver-manager 未安装，尝试其他方法...")
                print("  提示: 运行 'pip install webdriver-manager' 可自动管理 ChromeDriver")
            except Exception as e:
                print(f"  ⚠️ webdriver-manager 失败: {e}")
        if driver is None and DRIVER_STRATEGY in ['auto', 'system']:
            try:
                print("  尝试使用系统 PATH 中的 ChromeDriver...")
                driver = webdriver.Chrome(options=options)
                print("  ✓ 使用系统 ChromeDriver 成功")
            except Exception as e:
                print(f"  ⚠️ 系统 ChromeDriver 失败: {e}")
        if driver is None and DRIVER_STRATEGY in ['auto', 'local']:
            try:
                from selenium.webdriver.chrome.service import Service
                local_driver_path = os.path.join(os.path.dirname(__file__), 'chromedriver.exe')
                if os.path.exists(local_driver_path):
                    print(f"  尝试使用本地 ChromeDriver: {local_driver_path}")
                    service = Service(local_driver_path)
                    driver = webdriver.Chrome(service=service, options=options)
                    print("  ✓ 使用本地 ChromeDriver 成功")
                else:
                    if DRIVER_STRATEGY == 'local':
                        print(f"  ⚠️ 本地未找到 chromedriver.exe")
            except Exception as e:
                print(f"  ⚠️ 本地 ChromeDriver 失败: {e}")
    if driver is None:
        print("\n" + "="*70)
        print(f"❌ 无法启动 {browser_name} 浏览器！")
        print("="*70)
        if USE_EDGE:
            print("\n请选择以下解决方案之一:\n")
            print("方案 1 (推荐): 安装 webdriver-manager")
            print("  pip install webdriver-manager")
            print()
            print("方案 2: 确认 Edge 浏览器已安装")
            print("  Edge 浏览器路径通常在:")
            print("  C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe")
            print()
            print("方案 3: 手动下载 EdgeDriver")
            print("  1. 查看 Edge 版本: edge://version/")
            print("  2. 下载匹配版本: https://developer.microsoft.com/en-us/microsoft-edge/tools/webdriver/")
            print(f"  3. 解压 msedgedriver.exe 到: {os.path.dirname(__file__)}")
            print()
            print("方案 4: 改用 Chrome 浏览器")
            print("  在脚本中设置: USE_EDGE = False")
        else:
            print("\n请选择以下解决方案之一:\n")
            print("方案 1 (推荐): 安装 webdriver-manager")
            print("  pip install webdriver-manager")
            print()
            print("方案 2: 手动下载 ChromeDriver")
            print("  1. 查看 Chrome 版本: chrome://version/")
            print("  2. 下载匹配版本: https://chromedriver.chromium.org/downloads")
            print("     或: https://googlechromelabs.github.io/chrome-for-testing/")
            print(f"  3. 解压 chromedriver.exe 到: {os.path.dirname(__file__)}")
            print()
            print("方案 3: 使用国内镜像下载")
            print("  https://registry.npmmirror.com/binary.html?path=chromedriver/")
            print()
            print("方案 4: 改用 Edge 浏览器")
            print("  在脚本中设置: USE_EDGE = True")
        print("="*70)
        raise Exception(f"无法创建 {browser_name} WebDriver，请按照上述方案解决")
    driver.execute_cdp_cmd('Page.addScriptToEvaluateOnNewDocument', {
        'source': 'Object.defineProperty(navigator, "webdriver", {get: () => undefined})'
    })
    return driver

def login_with_browser(driver, username, password):
    try:
        print("步骤 1/6: 访问登录页面...")
        driver.get(f"{BASE_URL}/")
        wait = WebDriverWait(driver, 15)
        username_input = wait.until(EC.presence_of_element_located((By.CSS_SELECTOR, "input[id*='urn']")))
        print("步骤 2/6: 输入用户名和密码...")
        username_input.clear()
        username_input.send_keys(username)
        password_input = driver.find_element(By.CSS_SELECTOR, "input[id*='pwd']")
        password_input.clear()
        password_input.send_keys(password)
        print("步骤 3/6: 点击登录按钮...")
        login_button = driver.find_element(By.CSS_SELECTOR, "button[id*='login']")
        login_button.click()
        wait.until(lambda d: "Default.jspx" in d.current_url or "ExamCase" in d.current_url or len(d.current_url) > len(BASE_URL) + 10)
        print(f"当前URL: {driver.current_url}")
        if "Default.jspx" in driver.current_url or "talk" in driver.current_url:
            print("✅ 登录成功！")
            return True
        else:
            print(f"⚠️ 登录可能失败，当前URL: {driver.current_url}")
            return False
    except Exception as e:
        print(f"❌ 登录过程中发生错误: {e}")
        with open('debug_login_error.html', 'w', encoding='utf-8') as f:
            f.write(driver.page_source)
        print("已将页面保存到 debug_login_error.html")
        return False

def auto_exam_process(driver):
    try:
        print(f"步骤 4/6: 访问考试页面...")
        print(f"URL: {EXAM_URL}")
        driver.get(EXAM_URL)
        print(f"当前URL: {driver.current_url}")
        try:
            wait = WebDriverWait(driver, 3)
            start_button = wait.until(
                EC.element_to_be_clickable((By.XPATH, "//button[contains(@onclick, 'begin')]"))
            )
            print("✅ 发现'开始考试'按钮，点击开始...")
            driver.execute_script("arguments[0].scrollIntoView(true);", start_button)
            start_button.click()
            print("已点击'开始考试'")
        except Exception as e:
            print("ℹ️ 未发现'开始考试'弹窗，可能已经在考试页面")
        wait = WebDriverWait(driver, 10)
        wait.until(EC.presence_of_element_located((By.ID, "myForm")))
        print("✅ 考试页面已加载")
        print("步骤 5/6: 提交试卷...")
        submit_success = False
        try:
            submit_button = driver.find_element(By.ID, "myForm:subcase")
            driver.execute_script("arguments[0].scrollIntoView(true);", submit_button)
            submit_button.click()
            print("✅ 已点击'提交试卷'按钮 (方法1: ID)")
            submit_success = True
        except Exception as e1:
            try:
                submit_button = driver.find_element(By.XPATH, "//button[contains(text(), '提交')]")
                driver.execute_script("arguments[0].scrollIntoView(true);", submit_button)
                submit_button.click()
                print("✅ 已点击'提交'按钮 (方法2: 文本)")
                submit_success = True
            except Exception as e2:
                try:
                    print("尝试使用JavaScript提交...")
                    driver.execute_script("""
                        var btn = document.getElementById('myForm:subcase');
                        if (btn) {
                            btn.click();
                        } else {
                            if (typeof jQuery !== 'undefined') {
                                jQuery('#myForm\\\\:subcase').trigger('click');
                            }
                        }
                    """)
                    print("✅ 已使用JavaScript提交 (方法3)")
                    submit_success = True
                except Exception as e3:
                    print(f"❌ 方法3也失败: {e3}")
        if not submit_success:
            print("❌ 所有提交方法都失败了")
            return None
        try:
            confirm_button = WebDriverWait(driver, 2).until(
                EC.element_to_be_clickable((By.XPATH, "//button[contains(text(), '提交') or contains(text(), '确定')]"))
            )
            confirm_button.click()
            print("✅ 已点击确认提交对话框")
        except:
            print("ℹ️ 没有确认对话框或已自动提交")
        print("等待跳转到报告页面...")
        wait = WebDriverWait(driver, 15)
        try:
            wait.until(
                EC.any_of(
                    EC.url_contains("ExamCaseResult.jspx"),
                    EC.url_contains("ExamCaseReport"),
                    EC.url_contains("Report")
                )
            )

            current_url = driver.current_url
            if "ExamCaseResult.jspx" in current_url:
                try:
                    view_details_btn = wait.until(
                        EC.element_to_be_clickable((By.XPATH, "//button[contains(., '查看详情')]"))
                    )
                    print("✅ 发现'查看详情'按钮，点击进入报告页面...")
                    view_details_btn.click()
                    wait.until(
                        EC.any_of(
                            EC.url_contains("ExamCaseReport"),
                            EC.url_contains("Report")
                        )
                    )
                except Exception:
                    print("ℹ️  在结果页上未找到'查看详情'按钮或点击后未跳转，尝试直接解析当前页")

            print(f"✅ 步骤 6/6: 成功进入报告页面!")
            return driver.page_source

        except Exception:
            print("❌ 等待超时，未能跳转到报告页面")
            print(f"最终URL: {driver.current_url}")
            return None
    except Exception as e:
        print(f"❌ 自动化考试流程出错: {e}")
        import traceback
        traceback.print_exc()
        try:
            with open('debug_exam_process_error.html', 'w', encoding='utf-8') as f:
                f.write(driver.page_source)
            print(f"当前URL: {driver.current_url}")
            print("已将页面保存到 debug_exam_process_error.html")
        except:
            pass
        return None

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
        return text.replace('（）', '').replace('()', '').strip()

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
        print('⚠️ 警告：未找到 .ui-panel-content 容器，页面结构可能已变更')
        return question_bank

    print(f'✅ 找到 {len(panels)} 个面板容器')
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
                is_new = clean_text not in question_bank
                record = question_bank.setdefault(clean_text, {})
                record['answer'] = correct_answer
                if options:
                    unique_options = list(dict.fromkeys(opt for opt in options if opt))
                    if unique_options:
                        record['options'] = unique_options
                if is_new:
                    new_questions_found += 1
                    print(f'  新增题目: {clean_text[:30]}... => {correct_answer}')

    if new_questions_found:
        print(f'✅ 成功解析并添加/更新 {new_questions_found} 个题目')
    else:
        print('ℹ️ 报告页面解析完成，没有发现新问题')

    return question_bank

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
    print(f"🌐 使用浏览器: {'Edge' if USE_EDGE else 'Chrome'}")
    print(f"🖥️  无头模式: {'开启 (不显示浏览器窗口)' if HEADLESS else '关闭 (显示浏览器窗口)'}")
    print(f"🔄 计划循环次数: {LOOP_COUNT}")
    print()
    driver = None
    try:
        for i in range(1, LOOP_COUNT + 1):
            print("\n" + "=" * 70)
            print(f"{'  第 ' + str(i) + '/' + str(LOOP_COUNT) + ' 次循环':^70}")
            print("=" * 70)
            try:
                if driver is None:
                    browser_name = "Edge" if USE_EDGE else "Chrome"
                    print(f"🚀 正在启动 {browser_name} 浏览器...")
                    driver = create_driver()
                    print(f"✅ {browser_name} 浏览器启动成功")
                else:
                    print("ℹ️  使用已有浏览器实例...")
                if i == 1:
                    if not login_with_browser(driver, USERNAME, PASSWORD):
                        print("❌ 登录失败，终止程序")
                        break
                else:
                    print("ℹ️  使用已有登录会话...")
                report_html = auto_exam_process(driver)
                if not report_html:
                    print("❌ 无法获取报告页面，跳过本次循环")
                    continue
                print("\n📖 正在解析报告页面...")
                old_count = len(question_bank)
                question_bank = parse_report_page(report_html, question_bank)
                new_count = len(question_bank)
                added = new_count - old_count
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
                    browser_name = "Edge" if USE_EDGE else "Chrome"
                    print("\n⚠️ 第一次循环失败，可能是环境配置问题")
                    print("请检查:")
                    print(f"  1. {browser_name} 浏览器是否已安装")
                    print(f"  2. {browser_name}Driver 是否正确配置")
                    print("  3. 网络连接是否正常")
                    if USE_EDGE:
                        print("\n提示: Edge 通常已预装在 Windows 10/11 系统中")
                        print("  如果 Edge 未安装，可以:")
                        print("  - 下载安装: https://www.microsoft.com/edge")
                        print("  - 或设置 USE_EDGE = False 改用 Chrome")
                    break
            if i < LOOP_COUNT:
                print(f"\n🚀 准备下一次循环...")
    finally:
        if driver:
            print("\n🔒 正在关闭浏览器...")
            try:
                driver.quit()
                print("✅ 浏览器已关闭")
            except:
                pass
    print("\n" + "=" * 70)
    if len(question_bank) > initial_q_count:
        save_question_bank(question_bank)
        total_added = len(question_bank) - initial_q_count
        print(f"✅ 题库已更新：从 {initial_q_count} 增加到 {len(question_bank)} 道题")
        print(f"📈 本次运行共新增 {total_added} 道题")
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

