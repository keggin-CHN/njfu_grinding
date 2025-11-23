from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.chrome.options import Options as ChromeOptions
from selenium.webdriver.edge.options import Options as EdgeOptions
from bs4 import BeautifulSoup
import matplotlib.pyplot as plt
import matplotlib
matplotlib.rcParams['font.sans-serif'] = ['SimHei']  # 用来正常显示中文标签
matplotlib.rcParams['axes.unicode_minus'] = False  # 用来正常显示负号
import json
import re
import time
import os

# --- 宏定义 ---
USERNAME = "2410407105"
PASSWORD = "30287X"
EXAM_URL = "http://202.119.208.106/servlet/pc/ExamCaseController?exam_id=47091939-045b-4d5c-9a34-3a28d99764df"
LOOP_COUNT = 50
BASE_URL = "http://202.119.208.106"
HEADLESS = False  # 设置为 True 启用无头模式（看不到浏览器窗口）
USE_EDGE = True   # 设置为 True 使用 Edge 浏览器，False 使用 Chrome
# --- 宏定义结束 ---

# 全局题库文件
QUESTION_BANK_FILE = 'question_bank.json'

def get_user_input():
    """如果宏定义为空，则获取用户的输入"""
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
    """从本地JSON文件加载题库，返回扁平化字典以便查询"""
    if os.path.exists(QUESTION_BANK_FILE):
        try:
            with open(QUESTION_BANK_FILE, 'r', encoding='utf-8') as f:
                data = json.load(f)
                
            # 检查是否是分类结构
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
    """保存题库到本地JSON文件，按题型分类"""
    # 分类整理
    categorized_bank = {
        "单选题": {},
        "多选题": {},
        "判断题": {}
    }
    
    # 如果bank已经是分类的结构，先展平
    flat_bank = {}
    if "单选题" in bank or "多选题" in bank or "判断题" in bank:
        for cat in bank:
            if isinstance(bank[cat], dict):
                flat_bank.update(bank[cat])
        # 处理可能混杂在根目录的未分类题目
        for k, v in bank.items():
            if k not in ["单选题", "多选题", "判断题"]:
                flat_bank[k] = v
    else:
        flat_bank = bank

    # 开始分类
    for q_text, q_data in flat_bank.items():
        # 清理题号 (再次清理以防万一)
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
    """创建并配置 WebDriver (支持 Chrome 和 Edge)"""
    browser_name = "Edge" if USE_EDGE else "Chrome"
    print(f"  正在配置 {browser_name} 浏览器...")
    
    # 配置浏览器选项
    if USE_EDGE:
        options = EdgeOptions()
    else:
        options = ChromeOptions()
    
    if HEADLESS:
        options.add_argument('--headless')  # 无头模式
        options.add_argument('--disable-gpu')
    
    options.add_argument('--no-sandbox')
    options.add_argument('--disable-dev-shm-usage')
    options.add_argument('--disable-blink-features=AutomationControlled')
    options.add_argument('--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36')
    options.add_argument('--window-size=1920,1080')
    
    # 禁用自动化标识
    options.add_experimental_option('excludeSwitches', ['enable-automation'])
    options.add_experimental_option('useAutomationExtension', False)
    
    driver = None
    
    if USE_EDGE:
        # ========== Edge 浏览器方案 ==========
        
        # 方法1: 使用 webdriver-manager 自动管理 EdgeDriver
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
        
        # 方法2: 使用系统自带的 EdgeDriver（Edge 浏览器通常自带）
        if driver is None:
            try:
                print("  尝试使用系统内置的 EdgeDriver...")
                driver = webdriver.Edge(options=options)
                print("  ✓ 使用系统 EdgeDriver 成功")
            except Exception as e:
                print(f"  ⚠️ 系统 EdgeDriver 失败: {e}")
        
        # 方法3: 使用本地 msedgedriver.exe
        if driver is None:
            try:
                from selenium.webdriver.edge.service import Service
                local_driver_path = os.path.join(os.path.dirname(__file__), 'msedgedriver.exe')
                
                if os.path.exists(local_driver_path):
                    print(f"  尝试使用本地 EdgeDriver: {local_driver_path}")
                    service = Service(local_driver_path)
                    driver = webdriver.Edge(service=service, options=options)
                    print("  ✓ 使用本地 EdgeDriver 成功")
                else:
                    print(f"  ⚠️ 本地未找到 msedgedriver.exe")
            except Exception as e:
                print(f"  ⚠️ 本地 EdgeDriver 失败: {e}")
    
    else:
        # ========== Chrome 浏览器方案 ==========
        
        # 方法1: 使用 webdriver-manager 自动管理 ChromeDriver
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
        
        # 方法2: 使用系统 PATH 中的 ChromeDriver
        if driver is None:
            try:
                print("  尝试使用系统 PATH 中的 ChromeDriver...")
                driver = webdriver.Chrome(options=options)
                print("  ✓ 使用系统 ChromeDriver 成功")
            except Exception as e:
                print(f"  ⚠️ 系统 ChromeDriver 失败: {e}")
        
        # 方法3: 使用本地 chromedriver.exe
        if driver is None:
            try:
                from selenium.webdriver.chrome.service import Service
                local_driver_path = os.path.join(os.path.dirname(__file__), 'chromedriver.exe')
                
                if os.path.exists(local_driver_path):
                    print(f"  尝试使用本地 ChromeDriver: {local_driver_path}")
                    service = Service(local_driver_path)
                    driver = webdriver.Chrome(service=service, options=options)
                    print("  ✓ 使用本地 ChromeDriver 成功")
                else:
                    print(f"  ⚠️ 本地未找到 chromedriver.exe")
            except Exception as e:
                print(f"  ⚠️ 本地 ChromeDriver 失败: {e}")
    
    # 如果所有方法都失败
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
    
    # 配置 WebDriver
    driver.execute_cdp_cmd('Page.addScriptToEvaluateOnNewDocument', {
        'source': 'Object.defineProperty(navigator, "webdriver", {get: () => undefined})'
    })
    
    return driver

def login_with_browser(driver, username, password):
    """使用 Selenium 登录"""
    try:
        print("步骤 1/6: 访问登录页面...")
        driver.get(f"{BASE_URL}/")
        
        # 等待登录表单加载
        wait = WebDriverWait(driver, 15)
        username_input = wait.until(EC.presence_of_element_located((By.CSS_SELECTOR, "input[id*='urn']")))
        
        print("步骤 2/6: 输入用户名和密码...")
        username_input.clear()
        username_input.send_keys(username)
        
        password_input = driver.find_element(By.CSS_SELECTOR, "input[id*='pwd']")
        password_input.clear()
        password_input.send_keys(password)
        
        # time.sleep(0.5)
        
        print("步骤 3/6: 点击登录按钮...")
        # 查找登录按钮
        login_button = driver.find_element(By.CSS_SELECTOR, "button[id*='login']")
        login_button.click()
        
        # 等待页面跳转
        time.sleep(0.5)
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
        # 保存页面源代码用于调试
        with open('debug_login_error.html', 'w', encoding='utf-8') as f:
            f.write(driver.page_source)
        print("已将页面保存到 debug_login_error.html")
        return False

def auto_exam_process(driver):
    """自动化考试流程：访问考试URL -> 开始考试 -> 提交 -> 获取报告页面HTML"""
    try:
        print(f"步骤 4/6: 访问考试页面...")
        print(f"URL: {EXAM_URL}")
        driver.get(EXAM_URL)
        # time.sleep(1)  # 减少等待
        
        print(f"当前URL: {driver.current_url}")
        
        # 检查是否有"开始考试"弹窗或按钮
        try:
            wait = WebDriverWait(driver, 3) # 减少等待时间
            # 方法1: 查找包含 begin() 的按钮
            start_button = wait.until(
                EC.element_to_be_clickable((By.XPATH, "//button[contains(@onclick, 'begin')]"))
            )
            print("✅ 发现'开始考试'按钮，点击开始...")
            driver.execute_script("arguments[0].scrollIntoView(true);", start_button)
            # time.sleep(0.5)
            start_button.click()
            time.sleep(0.5)
            print("已点击'开始考试'")
        except Exception as e:
            print("ℹ️ 未发现'开始考试'弹窗，可能已经在考试页面")
        
        # 等待考试页面完全加载
        wait = WebDriverWait(driver, 10)
        wait.until(EC.presence_of_element_located((By.ID, "myForm")))
        print("✅ 考试页面已加载")
        
        # 保存考试页面HTML用于调试
        # with open('debug_exam_page.html', 'w', encoding='utf-8') as f:
        #     f.write(driver.page_source)
        # print("已保存考试页面到 debug_exam_page.html")
        
        print("步骤 5/6: 提交试卷...")
        time.sleep(0.2)  # 减少等待
        
        # 查找并点击"提交试卷"按钮
        submit_success = False
        
        # 方法1: 通过ID查找 myForm:subcase
        try:
            submit_button = driver.find_element(By.ID, "myForm:subcase")
            driver.execute_script("arguments[0].scrollIntoView(true);", submit_button)
            # time.sleep(0.5)
            submit_button.click()
            print("✅ 已点击'提交试卷'按钮 (方法1: ID)")
            submit_success = True
        except Exception as e1:
            # print(f"方法1失败: {e1}")
            
            # 方法2: 通过文本查找
            try:
                submit_button = driver.find_element(By.XPATH, "//button[contains(text(), '提交')]")
                driver.execute_script("arguments[0].scrollIntoView(true);", submit_button)
                # time.sleep(0.5)
                submit_button.click()
                print("✅ 已点击'提交'按钮 (方法2: 文本)")
                submit_success = True
            except Exception as e2:
                # print(f"方法2失败: {e2}")
                
                # 方法3: 使用JavaScript强制点击
                try:
                    print("尝试使用JavaScript提交...")
                    driver.execute_script("""
                        var btn = document.getElementById('myForm:subcase');
                        if (btn) {
                            btn.click();
                        } else {
                            // 尝试jQuery
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
        
        time.sleep(0.5)
        
        # 处理可能出现的确认对话框
        try:
            # 如果有"确认提交"的对话框，点击确认
            confirm_button = WebDriverWait(driver, 2).until(
                EC.element_to_be_clickable((By.XPATH, "//button[contains(text(), '提交') or contains(text(), '确定')]"))
            )
            confirm_button.click()
            print("✅ 已点击确认提交对话框")
            time.sleep(0.5)
        except:
            print("ℹ️ 没有确认对话框或已自动提交")
        
        # 等待跳转到报告页面
        print("等待跳转到报告页面...")
        max_wait = 15
        start_time = time.time()
        
        while time.time() - start_time < max_wait:
            current_url = driver.current_url
            
            # 检查是否还在结果页面但有弹窗
            if "ExamCaseResult.jspx" in current_url:
                try:
                    # 查找"查看详情"按钮
                    view_details_btn = driver.find_element(By.XPATH, "//button[contains(., '查看详情')]")
                    if view_details_btn.is_displayed():
                        print("✅ 发现'查看详情'按钮，点击进入报告页面...")
                        view_details_btn.click()
                        time.sleep(0.5)
                        continue
                except:
                    pass
            
            if "ExamCaseReport" in current_url or "Report" in current_url:
                print(f"✅ 步骤 6/6: 成功进入报告页面!")
                time.sleep(1)  # 等待页面完全加载
                
                # 保存报告页面
                # with open('debug_report_page.html', 'w', encoding='utf-8') as f:
                #     f.write(driver.page_source)
                # print("已保存报告页面到 debug_report_page.html")
                
                return driver.page_source
            
            time.sleep(0.5)
        
        print("❌ 等待超时，未能跳转到报告页面")
        print(f"最终URL: {driver.current_url}")
        return None
        
    except Exception as e:
        print(f"❌ 自动化考试流程出错: {e}")
        import traceback
        traceback.print_exc()
        
        # 保存当前页面用于调试
        try:
            with open('debug_exam_process_error.html', 'w', encoding='utf-8') as f:
                f.write(driver.page_source)
            print(f"当前URL: {driver.current_url}")
            print("已将页面保存到 debug_exam_process_error.html")
        except:
            pass
        
        return None

def parse_report_page(html_content, question_bank):
    """
    解析考试报告页面，提取问题和答案。
    基于油猴脚本的提取逻辑改写
    """
    soup = BeautifulSoup(html_content, 'html.parser')
    new_questions_found = 0
    
    # 使用油猴脚本中的选择器
    question_elements = soup.select('div[id*="j_idt191_content"] > span.choiceTitle:first-of-type, div[id*="j_idt191_content"] > hr + span.choiceTitle')
    
    if not question_elements:
        print("⚠️ 警告：在报告页面上没有找到问题元素（选择器1）")
        # 尝试备用选择器
        question_elements = soup.select('a[id^="archor-"] + span.choiceTitle')
        if not question_elements:
            print("⚠️ 警告：备用选择器也未找到问题")
            return question_bank
        else:
            print(f"✅ 使用备用选择器找到 {len(question_elements)} 个题目")
    else:
        print(f"✅ 找到 {len(question_elements)} 个题目")

    for element in question_elements:
        try:
            # 1. 提取题干
            question_text = element.get_text(strip=True) if element else None
            if not question_text:
                continue
            
            # 2. 获取选项容器（题干 -> 分数span -> 选项div）
            # 使用 find_next_sibling() 跳过文本节点，直接获取下一个 Tag
            score_span = element.find_next_sibling()
            options_container = score_span.find_next_sibling() if score_span else None
            
            # 3. 获取答案容器（选项容器的下一个兄弟 Tag）
            answer_container = options_container.find_next_sibling() if options_container else None
            
            # 4. 提取正确答案（绿色加粗的文本）
            correct_answer_element = None
            if answer_container:
                correct_answer_element = answer_container.select_one('span[style*="color:green"][style*="font-weight: bold"]')
            
            if not correct_answer_element:
                correct_answer_element = answer_container.select_one('span[style*="color: green"]') if answer_container else None
            
            correct_answer = correct_answer_element.get_text(strip=True) if correct_answer_element else None
            
            if correct_answer:
                # 清理答案文本
                correct_answer = correct_answer.replace('.', '').replace(' ', '')
                if correct_answer == "true":
                    correct_answer = "正确"
                elif correct_answer == "false":
                    correct_answer = "错误"
            
            # 5. 提取选项
            options = []
            if options_container:
                option_spans = options_container.select('div[id*="j_idt"] > span.choiceTitle, div[id*="j_idt"] > div.choiceTitle')
                if not option_spans:
                    option_spans = options_container.select('span.choiceTitle, div.choiceTitle')
                
                options = [span.get_text(strip=True) for span in option_spans]
            
            # 6. 清理题干文本
            if question_text and correct_answer:
                # 移除题号、分数、括号
                question_text = re.sub(r'^\d+[、.]\s*', '', question_text).strip()
                question_text = re.sub(r'\(\d+\.\d+分\)', '', question_text).strip()
                question_text = question_text.replace('（）', '').replace('()', '').strip()
                
                # 添加到题库
                if question_text not in question_bank:
                    question_bank[question_text] = {
                        'answer': correct_answer,
                        'options': options
                    }
                    new_questions_found += 1
                    print(f"  新增题目: {question_text[:30]}... => {correct_answer}")
                else:
                    # 更新已有题目
                    question_bank[question_text]['answer'] = correct_answer
                    if options:
                        question_bank[question_text]['options'] = options
            
        except Exception as e:
            print(f"⚠️ 解析单个题目时出错: {e}")
            continue

    if new_questions_found > 0:
        print(f"✅ 成功解析并添加了 {new_questions_found} 个新问题到题库")
    else:
        print("ℹ️ 报告页面解析完成，没有发现新问题")
        
    return question_bank

def count_categories(bank):
    """统计各题型数量"""
    counts = {"单选题": 0, "多选题": 0, "判断题": 0}
    for q_data in bank.values():
        answer = q_data.get('answer', '')
        # 简单的分类逻辑，需与 save_question_bank 保持一致
        if answer in ['正确', '错误', 'true', 'false']:
            counts['判断题'] += 1
        elif len(answer) > 1:
            counts['多选题'] += 1
        else:
            counts['单选题'] += 1
    return counts

def plot_results(history):
    """使用matplotlib绘制并保存题目数量增长图 (美化版)"""
    if not history or not history.get('total') or len(history['total']) < 1:
        print("数据点不足，无法生成图表。")
        return
        
    # 设置全局大字体
    plt.rcParams.update({'font.size': 16})
    
    plt.figure(figsize=(16, 10)) # 更大的画布
    
    iterations = range(1, len(history['total']) + 1)
    
    # 定义线条样式配置: (键名, 图例标签, 颜色, 标记点形状)
    lines_config = [
        ('total', '题库总数', '#e74c3c', 'o'),  # 红色
        ('single', '单选题', '#3498db', 's'),   # 蓝色
        ('multi', '多选题', '#2ecc71', '^'),    # 绿色
        ('judge', '判断题', '#f1c40f', 'D')     # 黄色
    ]
    
    for key, label, color, marker in lines_config:
        if key in history and history[key]:
            data = history[key]
            # 线条加粗(linewidth=4)，点变大(markersize=10)
            plt.plot(iterations, data, marker=marker, linestyle='-', color=color, 
                     linewidth=4, markersize=10, label=label)
            
            # 标注最后一个点的值
            if data:
                plt.text(iterations[-1], data[-1], f' {data[-1]}', 
                         ha='left', va='center', fontsize=18, fontweight='bold', color=color)

    # 标题和标签 - 字号调大
    if len(history['total']) > 1:
        growth = history['total'][-1] - history['total'][0]
        plt.title(f'题库增长趋势 (总增长: {growth} 题)', fontsize=26, fontweight='bold', pad=20)
    else:
        plt.title('题库增长趋势', fontsize=26, fontweight='bold', pad=20)
    
    plt.xlabel('循环次数', fontsize=22, labelpad=15)
    plt.ylabel('题目数量', fontsize=22, labelpad=15)
    
    # 网格加深
    plt.grid(True, which='major', linestyle='-', linewidth=1.5, alpha=0.6, color='gray')
    plt.grid(True, which='minor', linestyle=':', linewidth=1.0, alpha=0.4, color='lightgray')
    plt.minorticks_on()
    
    # 刻度字体
    plt.xticks(fontsize=18)
    plt.yticks(fontsize=18)
    
    # 图例 - 字号调大
    plt.legend(fontsize=20, loc='upper left', frameon=True, shadow=True, borderpad=1)
    
    plt.tight_layout()
    
    plot_filename = 'question_growth.png'
    plt.savefig(plot_filename, dpi=300) # 高清保存
    print(f"📊 图表已保存为 {plot_filename}")
    
    try:
        plt.show()
    except:
        pass

def main():
    """脚本主入口 - 使用 Selenium 自动化"""
    print("=" * 70)
    print(" " * 20 + "南林考试系统自动爬虫")
    print("=" * 70)
    
    get_user_input()
    
    question_bank = load_question_bank()
    
    # 初始化历史数据记录
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
                # 创建浏览器实例（只在第一次循环时创建）
                if driver is None:
                    browser_name = "Edge" if USE_EDGE else "Chrome"
                    print(f"🚀 正在启动 {browser_name} 浏览器...")
                    driver = create_driver()
                    print(f"✅ {browser_name} 浏览器启动成功")
                else:
                    print("ℹ️  使用已有浏览器实例...")
                
                # 1. 登录（只在第一次循环时登录）
                if i == 1:
                    if not login_with_browser(driver, USERNAME, PASSWORD):
                        print("❌ 登录失败，终止程序")
                        break
                    time.sleep(1)
                else:
                    print("ℹ️  使用已有登录会话...")
                
                # 2. 自动化考试流程：访问 -> 开始 -> 提交 -> 获取报告
                report_html = auto_exam_process(driver)
                
                if not report_html:
                    print("❌ 无法获取报告页面，跳过本次循环")
                    continue
                
                # 3. 解析报告页面
                print("\n📖 正在解析报告页面...")
                old_count = len(question_bank)
                question_bank = parse_report_page(report_html, question_bank)
                
                new_count = len(question_bank)
                added = new_count - old_count
                
                # 统计分类数量并记录
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
                
                # 如果是第一次循环就失败，可能是环境问题，直接退出
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
            
            # 每次循环后暂停
            if i < LOOP_COUNT:
                wait_time = 1
                print(f"\n⏸️  暂停 {wait_time} 秒，准备下一次循环...")
                time.sleep(wait_time)
    
    finally:
        # 关闭浏览器
        if driver:
            print("\n🔒 正在关闭浏览器...")
            try:
                driver.quit()
                print("✅ 浏览器已关闭")
            except:
                pass
    
    # 保存结果
    print("\n" + "=" * 70)
    if len(question_bank) > initial_q_count:
        save_question_bank(question_bank)
        total_added = len(question_bank) - initial_q_count
        print(f"✅ 题库已更新：从 {initial_q_count} 增加到 {len(question_bank)} 道题")
        print(f"📈 本次运行共新增 {total_added} 道题")
    else:
        print("ℹ️  题库没有更新")
    
    # 绘制图表
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
