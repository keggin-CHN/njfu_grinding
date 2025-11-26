#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import json
import requests
import time
import sys
import os
from typing import Dict, List, Optional
from concurrent.futures import ThreadPoolExecutor, as_completed
os.environ['NO_PROXY'] = '*'
os.environ['no_proxy'] = '*'
if 'HTTP_PROXY' in os.environ:
    del os.environ['HTTP_PROXY']
if 'HTTPS_PROXY' in os.environ:
    del os.environ['HTTPS_PROXY']
if 'http_proxy' in os.environ:
    del os.environ['http_proxy']
if 'https_proxy' in os.environ:
    del os.environ['https_proxy']
# ==================== 配置区域 ====================
API_BASE_URL = "http://s8.serv00.com:6083/proxy/buyaozaisile/v1beta/openai/chat/completions#"  # 修改为你的API地址
API_KEY = "sk-rwK55PRhYtSr6-b1DMwJa-CJp7BerVoNh7XfXocdklJ6iTwO"  # 修改为你的API Key
MAX_RETRY = 5  # 最大重试次数
CONCURRENCY = 500  # 并发数
TEMPERATURE = 0.6
TOP_P = 1.0
# ==================================================

# 提示词定义
FIX_BRACKET_PROMPT = """请检查以下题目是否缺少填空标记括号"( )"。如果题目中应该有填空位置但缺少括号,请在正确的位置添加"( )"。

题目:{question}
正确答案:{answer}

重要要求:
1. 只能添加一个括号"( )"
2. 不要修改题目的其他任何内容
3. 只返回修复后的题目文本,不要添加任何解释
4. 如果题目不需要修复,请返回原题目"""

FIX_BRACKET_STRICT_PROMPT = """【严格要求】请为以下题目添加填空括号"( )":

题目:{question}
正确答案:{answer}

【必须遵守的规则】:
1. 必须且只能添加一个括号"( )",不能多也不能少
2. 除了添加括号外,不得修改题目的任何其他文字、标点或格式
3. 只返回修复后的完整题目文本,不要有任何额外的说明、解释或标记
4. 括号的位置应该在答案对应的填空位置

【示例】:
原题目:"是实现中华民族伟大复兴的根本保证。"
修复后:"( )是实现中华民族伟大复兴的根本保证。"

现在请修复上面的题目:"""


class QuestionBankFixer:
    def __init__(self, api_url: str, api_key: str, model: str):
        self.api_url = api_url
        self.api_key = api_key
        self.model = model
        self.session = requests.Session()
        self.session.headers.update({
            'Authorization': f'Bearer {api_key}',
            'Content-Type': 'application/json'
        })
        self.session.trust_env = False
        self.session.proxies = {
            'http': None,
            'https': None,
        }
        self.total_questions = 0
        self.questions_need_fix = 0
        self.fixed_count = 0
        self.failed_count = 0
    
    def needs_bracket_fix(self, question_text: str) -> bool:
        if any(bracket in question_text for bracket in 
               ["( )", "()", "（ ）", "（）", "(　)", "（　）"]):
            return False
        return True
    
    def count_brackets(self, text: str) -> int:
        count = 0
        for bracket_pair in ["( )", "()", "（ ）", "（）", "(　)", "（　）"]:
            count += text.count(bracket_pair)
        return count
    
    def validate_fix(self, original: str, fixed: str) -> tuple[bool, str]:
        original_count = self.count_brackets(original)
        fixed_count = self.count_brackets(fixed)
        added = fixed_count - original_count
        
        if added == 0:
            return False, "未添加括号"
        if added > 1:
            return False, f"添加了{added}个括号,超过1个"
        original_clean = original
        fixed_clean = fixed
        for bracket in ["(", ")", "（", "）", " ", "　"]:
            original_clean = original_clean.replace(bracket, "")
            fixed_clean = fixed_clean.replace(bracket, "")
        
        if original_clean.strip() != fixed_clean.strip():
            return False, "修改了题目的其他内容"
        
        if not any(bracket in fixed for bracket in 
                  ["( )", "()", "（ ）", "（）", "(　)", "（　）"]):
            return False, "括号格式不正确"
        
        return True, "验证通过"
    
    def call_ai(self, prompt: str) -> Optional[str]:
        try:
            payload = {
                "model": self.model,
                "messages": [
                    {"role": "user", "content": prompt}
                ],
                "temperature": TEMPERATURE,
                "top_p": TOP_P
            }
            
            response = self.session.post(
                self.api_url,
                json=payload,
                timeout=60
            )
            
            if response.status_code == 200:
                data = response.json()
                if 'choices' in data and len(data['choices']) > 0:
                    return data['choices'][0]['message']['content'].strip()
            else:
                print(f"API错误: {response.status_code} - {response.text}")
                return None
                
        except Exception as e:
            print(f"调用AI失败: {str(e)}")
            return None
    
    def fix_question_with_retry(self, question_text: str, answer: str) -> Optional[str]:
        for retry in range(MAX_RETRY):
            use_strict = retry >= 2
            prompt_template = FIX_BRACKET_STRICT_PROMPT if use_strict else FIX_BRACKET_PROMPT
            prompt = prompt_template.replace("{question}", question_text).replace("{answer}", answer)
            fixed_text = self.call_ai(prompt)
            if not fixed_text:
                print(f"  重试 {retry + 1}/{MAX_RETRY}: AI调用失败")
                time.sleep(2)
                continue
            is_valid, reason = self.validate_fix(question_text, fixed_text)
            if is_valid:
                return fixed_text
            else:
                print(f"  重试 {retry + 1}/{MAX_RETRY}: {reason}")
                time.sleep(1)
        
        print(f"  ✗ 修复失败,已达到最大重试次数")
        return None
    
    def fix_question_bank(self, input_file: str, output_file: str):
        print(f"正在读取题库文件: {input_file}")
        with open(input_file, 'r', encoding='utf-8') as f:
            question_bank = json.load(f)
        

        questions_to_fix = []
        for category, questions in question_bank.items():
            category_type = self._determine_question_type(category)
            
            if category_type not in ["单选题", "多选题"]:
                continue
            
            for question_text, question_data in questions.items():
                self.total_questions += 1
                
                if self.needs_bracket_fix(question_text):
                    self.questions_need_fix += 1
                    questions_to_fix.append({
                        'category': category,
                        'question_text': question_text,
                        'answer': question_data.get('answer', ''),
                        'data': question_data
                    })
        
        print(f"\n检测完成:")
        print(f"  总题目数: {self.total_questions}")
        print(f"  需要修复: {self.questions_need_fix}")
        
        if self.questions_need_fix == 0:
            print("\n没有需要修复的题目!")
            return
        
        print(f"\n开始修复 (并发数: {CONCURRENCY})...")
        print("=" * 60)
        
        with ThreadPoolExecutor(max_workers=CONCURRENCY) as executor:
            futures = {}
            for item in questions_to_fix:
                future = executor.submit(
                    self.fix_question_with_retry,
                    item['question_text'],
                    item['answer']
                )
                futures[future] = item
            
            for i, future in enumerate(as_completed(futures), 1):
                item = futures[future]
                try:
                    fixed_text = future.result()
                    if fixed_text:
                        category = item['category']
                        old_text = item['question_text']

                        question_data = question_bank[category].pop(old_text)
                        question_bank[category][fixed_text] = question_data
                        
                        self.fixed_count += 1
                        print(f"[{i}/{self.questions_need_fix}] ✓ 修复成功")
                        print(f"  原题: {old_text[:50]}...")
                        print(f"  修复: {fixed_text[:50]}...")
                    else:
                        self.failed_count += 1
                        print(f"[{i}/{self.questions_need_fix}] ✗ 修复失败")
                        
                except Exception as e:
                    self.failed_count += 1
                    print(f"[{i}/{self.questions_need_fix}] ✗ 处理异常: {str(e)}")
        
        print("\n" + "=" * 60)
        print(f"修复完成! 正在保存到: {output_file}")
        with open(output_file, 'w', encoding='utf-8') as f:
            json.dump(question_bank, f, ensure_ascii=False, indent=4)
        
        print("\n修复统计:")
        print(f"  总题目数: {self.total_questions}")
        print(f"  需要修复: {self.questions_need_fix}")
        print(f"  修复成功: {self.fixed_count}")
        print(f"  修复失败: {self.failed_count}")
        print(f"  成功率: {self.fixed_count / self.questions_need_fix * 100:.1f}%")
    
    def _determine_question_type(self, category: str) -> str:
        """判断题目类型"""
        if "单选" in category:
            return "单选题"
        elif "多选" in category:
            return "多选题"
        elif "判断" in category:
            return "判断题"
        else:
            return "单选题"


def get_full_api_url(base_url: str) -> str:
    if base_url.endswith("#"):
        return base_url[:-1]
    if base_url.endswith("/"):
        base_url = base_url[:-1]
    return base_url + "/v1/chat/completions"


def get_models_api_url(base_url: str) -> str:
    """获取模型列表API URL"""
    if base_url.endswith("#"):
        custom_url = base_url[:-1]
        if "chat/completions" in custom_url:
            return custom_url.replace("chat/completions", "models")
        return custom_url
    if base_url.endswith("/"):
        base_url = base_url[:-1]
    return base_url + "/v1/models"


def get_available_models(api_url: str, api_key: str) -> List[str]:
    """获取可用的模型列表"""
    try:
        models_url = get_models_api_url(api_url)
        session = requests.Session()
        session.trust_env = False  
        session.proxies = {'http': None, 'https': None}
        
        response = session.get(
            models_url,
            headers={'Authorization': f'Bearer {api_key}'},
            timeout=30
        )
        
        if response.status_code == 200:
            data = response.json()
            if 'data' in data:
                models = [model['id'] for model in data['data']]
                return sorted(models)
        
        print(f"获取模型列表失败: {response.status_code}")
        return []
        
    except Exception as e:
        print(f"获取模型列表异常: {str(e)}")
        return []


def display_models(models: List[str]) -> str:
    """显示模型列表并让用户选择"""
    if models:
        print("\n可用模型列表:")
        print("=" * 60)
        for i in range(0, len(models), 5):
            row_models = models[i:i+5]
            for j, model in enumerate(row_models, 1):
                idx = i + j
                print(f"{idx:2d}. {model:30s}", end="  ")
            print()
        
        print("=" * 60)
        print("0. 手动输入模型名称")
        print("=" * 60)
    
    while True:
        try:
            if models:
                choice = input(f"\n请选择模型 (0-{len(models)}, 0为手动输入): ").strip()
                if choice == "0":
                    model_name = input("请输入模型名称: ").strip()
                    if model_name:
                        return model_name
                    else:
                        print("模型名称不能为空")
                        continue
                idx = int(choice) - 1
                if 0 <= idx < len(models):
                    return models[idx]
                else:
                    print(f"请输入 0 到 {len(models)} 之间的数字")
            else:
                model_name = input("\n请输入模型名称: ").strip()
                if model_name:
                    return model_name
                else:
                    print("模型名称不能为空")
        except ValueError:
            print("请输入有效的数字")
        except KeyboardInterrupt:
            print("\n\n用户取消操作")
            sys.exit(0)


def main():
    """主函数"""
    print("=" * 60)
    print("题库修复脚本 v1.0")
    print("用于修复单选多选题中缺失的括号")
    print("=" * 60)
    if API_KEY == "sk-your-api-key-here":
        print("\n错误: 请先在脚本开头配置 API_KEY")
        sys.exit(1)
    print("\n当前配置信息:")
    print("=" * 60)
    print(f"API Base URL: {API_BASE_URL}")
    full_api_url = get_full_api_url(API_BASE_URL)
    print(f"完整API URL: {full_api_url}")
    models_url = get_models_api_url(API_BASE_URL)
    print(f"模型列表URL: {models_url}")
    if len(API_KEY) > 8:
        masked_key = API_KEY[:4] + "*" * (len(API_KEY) - 8) + API_KEY[-4:]
    else:
        masked_key = "*" * len(API_KEY)
    print(f"API Key: {masked_key}")
    
    print(f"并发数: {CONCURRENCY}")
    print(f"最大重试: {MAX_RETRY}")
    print(f"Temperature: {TEMPERATURE}")
    print(f"Top P: {TOP_P}")
    print("=" * 60)
    print("\n正在获取可用模型...")
    models = get_available_models(API_BASE_URL, API_KEY)
    
    if not models:
        print("\n警告: 无法获取模型列表")
        print("可能的原因:")
        print("  1. API地址配置错误")
        print("  2. API Key无效")
        print("  3. 网络连接问题（代理设置）")
        print("  4. API服务不可用")
        print("\n您可以手动输入模型名称继续")

    selected_model = display_models(models)
    print(f"\n已选择模型: {selected_model}")
    
    input_file = input("\n请输入题库文件路径 (默认: question_bank.json): ").strip()
    if not input_file:
        input_file = "question_bank.json"
    
    output_file = input("请输入输出文件路径 (默认: question_bank_fixed.json): ").strip()
    if not output_file:
        output_file = "question_bank_fixed.json"
    
    print(f"\n配置信息:")
    print(f"  API地址: {API_BASE_URL}")
    print(f"  模型: {selected_model}")
    print(f"  输入文件: {input_file}")
    print(f"  输出文件: {output_file}")
    print(f"  并发数: {CONCURRENCY}")
    print(f"  最大重试: {MAX_RETRY}")
    
    confirm = input("\n确认开始修复? (y/n): ").strip().lower()
    if confirm != 'y':
        print("用户取消操作")
        sys.exit(0)
    
    print("\n" + "=" * 60)
    fixer = QuestionBankFixer(API_BASE_URL, API_KEY, selected_model)
    
    try:
        fixer.fix_question_bank(input_file, output_file)
        print("\n✓ 修复完成!")
    except FileNotFoundError:
        print(f"\n错误: 找不到文件 {input_file}")
        sys.exit(1)
    except json.JSONDecodeError:
        print(f"\n错误: {input_file} 不是有效的JSON文件")
        sys.exit(1)
    except KeyboardInterrupt:
        print("\n\n用户中断操作")
        sys.exit(0)
    except Exception as e:
        print(f"\n错误: {str(e)}")
        import traceback
        traceback.print_exc()
        sys.exit(1)


if __name__ == "__main__":
    main()