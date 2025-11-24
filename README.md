# 南京林业大学马院刷题系统
如果本项目对你有帮助，请点个 Star ⭐。(づ｡◕‿‿◕｡)づ

BUG问题可提交issue，也可联系本人: admin@mail.keggin.me

本项目包含两个主要部分：题库爬虫和Android刷题应用。

## ⚠️ 使用声明

**仅用于学习与技术研究，禁止用于任何商业用途。**

---

## 📦 项目结构

```
njfu_exam_app-main/
├── 爬虫/                                    # 题库爬虫模块
│   ├── scraper.py                          # 爬虫主程序
│   ├── requirements.txt                    # Python依赖列表
│   └── question_bank.json                  # 爬取的题库数据
│
├── app/                                    # Android应用源码
│   ├── src/main/
│   │   ├── java/com/examapp/              # Java源代码
│   │   │   ├── adapter/                   # 列表适配器
│   │   │   │   ├── HistoryAdapter.java           # 历史记录适配器
│   │   │   │   ├── QuestionAdapter.java          # 题目列表适配器
│   │   │   │   ├── QuestionCircleAdapter.java    # 题目圆圈适配器
│   │   │   │   ├── ReviewAdapter.java            # 复习适配器
│   │   │   │   ├── SubjectAdapter.java           # 科目适配器
│   │   │   │   ├── SubjectExpandableAdapter.java # 可展开科目适配器
│   │   │   │   └── WrongQuestionAdapter.java     # 错题适配器
│   │   │   │
│   │   │   ├── data/                      # 数据管理层
│   │   │   │   ├── AICacheManager.java           # AI缓存管理
│   │   │   │   ├── AISettingsManager.java        # AI设置管理
│   │   │   │   ├── HitokotoManager.java          # 一言API管理
│   │   │   │   ├── QuestionImporter.java         # 题目导入
│   │   │   │   ├── QuestionManager.java          # 题目管理
│   │   │   │   └── SettingsManager.java          # 设置管理
│   │   │   │
│   │   │   ├── model/                     # 数据模型
│   │   │   │   ├── ExamHistoryEntry.java         # 考试历史记录
│   │   │   │   ├── Question.java                 # 题目模型
│   │   │   │   └── Subject.java                  # 科目模型
│   │   │   │
│   │   │   ├── service/                   # 后台服务
│   │   │   │   ├── AIProcessingService.java      # AI处理服务
│   │   │   │   └── AIService.java                # AI服务接口
│   │   │   │
│   │   │   ├── util/                      # 工具类
│   │   │   │   ├── BackgroundApplier.java        # 背景应用工具
│   │   │   │   ├── DraggableFABHelper.java       # 可拖拽FAB助手
│   │   │   │   └── XGBoostPredictor.java         # XGBoost预测
│   │   │   │
│   │   │   ├── widget/                    # 自定义控件
│   │   │   │   └── ScoreChartView.java           # 成绩图表视图
│   │   │   │
│   │   │   └── *.java                     # Activity类
│   │   │       ├── BaseActivity.java             # 基础Activity
│   │   │       ├── MainActivity.java             # 主界面
│   │   │       ├── PracticeActivity.java         # 练习模式
│   │   │       ├── MockExamActivity.java         # 模拟考试
│   │   │       ├── EndlessModeActivity.java      # 无尽模式
│   │   │       ├── StudyModeActivity.java        # 学习模式
│   │   │       ├── ReviewActivity.java           # 复习界面
│   │   │       ├── WrongQuestionsActivity.java   # 错题本
│   │   │       ├── WrongAnalysisActivity.java    # 错题分析
│   │   │       ├── HistoryActivity.java          # 历史记录
│   │   │       ├── SearchActivity.java           # 搜索界面
│   │   │       ├── ImportActivity.java           # 导入界面
│   │   │       ├── SettingsActivity.java         # 设置界面
│   │   │       └── ResultActivity.java           # 结果界面
│   │   │
│   │   ├── res/                           # 资源文件
│   │   │   ├── drawable/                  # 图片资源
│   │   │   │   ├── *_color.png                   # AI服务商图标
│   │   │   │   ├── ic_*.xml                      # 矢量图标
│   │   │   │   └── *.xml                         # 其他drawable
│   │   │   │
│   │   │   ├── layout/                    # 布局文件
│   │   │   │   ├── activity_*.xml                # Activity布局
│   │   │   │   ├── dialog_*.xml                  # 对话框布局
│   │   │   │   └── item_*.xml                    # 列表项布局
│   │   │   │
│   │   │   ├── menu/                      # 菜单资源
│   │   │   ├── values/                    # 值资源
│   │   │   │   ├── colors.xml                    # 颜色定义
│   │   │   │   ├── strings.xml                   # 字符串资源
│   │   │   │   ├── styles.xml                    # 样式定义
│   │   │   │   └── dimens.xml                    # 尺寸定义
│   │   │   │
│   │   │   └── xml/                       # XML配置
│   │   │       ├── data_extraction_rules.xml     # 数据提取规则
│   │   │       └── network_security_config.xml   # 网络安全配置
│   │   │
│   │   └── AndroidManifest.xml            # 应用清单文件
│   │
│   └── build.gradle                       # 应用构建配置
│
├── gradle/                                # Gradle配置
│   └── wrapper/                           # Gradle包装器
│
├── build.gradle                           # 项目构建配置
├── settings.gradle                        # 项目设置
├── gradle.properties                      # Gradle属性
├── gradlew                                # Gradle包装器脚本(Unix)
├── gradlew.bat                            # Gradle包装器脚本(Windows)
├── question_bank.json                     # 题库数据文件
├── LICENSE                                # 开源许可证
└── README.md                              # 项目说明文档
```

---

## 📊 题库爬虫

基于 Selenium 的自动化题库爬虫工具,用于从南林考试系统抓取题目数据,并进行数据分析和可视化。

### 使用方法

#### 1. 环境准备

```bash
cd 爬虫
pip install -r requirements.txt
```

#### 2. 配置爬虫参数

编辑 `scraper.py` 文件,修改以下配置:

```python
USERNAME = "你的用户名"          # 登录用户名
PASSWORD = "你的密码"            # 登录密码
EXAM_URL = "考试页面URL"         # 目标考试的URL
LOOP_COUNT = 50                  # 循环抓取次数
USE_EDGE = True                  # True使用Edge浏览器, False使用Chrome
HEADLESS = False                 # True为无头模式(不显示浏览器窗口)
```

#### 3. 运行爬虫

```bash
python scraper.py
```

程序会自动:
1. 启动浏览器并登录系统
2. 循环进入考试页面
3. 自动提交试卷
4. 解析报告页面并提取题目
5. 保存到 `question_bank.json`

#### 4. 数据分析与可视化

爬虫运行完成后,会自动生成数据分析报告和可视化图表:

<div align="center">
  <img src="image-UGxV.png" width="85%" />
  <p><i>爬虫运行过程 - 实时显示抓取进度和统计信息</i></p>
</div>

<br>

<div align="center">
  <img src="Figure_1-EThj.png" width="85%" />
  <p><i>题库增长趋势图 - 展示各类题目数量随循环次数的变化</i></p>
</div>

### 主要功能

- 🔍 **自动化抓取**: 自动登录、进入考试、提交试卷、解析报告
- 📈 **智能去重**: 自动识别并过滤重复题目
- 📊 **实时统计**: 实时显示题库增长情况和分类统计
- 💾 **数据持久化**: 题目自动分类保存为JSON格式
- 🔄 **增量更新**: 支持断点续爬,新题目自动追加到题库
- 📉 **可视化分析**: 自动生成题库增长趋势图表

### 技术栈

- **Selenium**: 浏览器自动化控制
- **BeautifulSoup**: HTML页面解析
- **Matplotlib**: 数据可视化
- **WebDriver Manager**: 自动管理浏览器驱动

### 📖 详细文档

<h3>更多有关爬虫的原理、细节、操作方案见个人博客: <a href="https://keggin.me/archives/5d7289ad-5e2e-4bde-a5f3-159e2b56e457">点击查看</a></h3>

---

## 📱 Android刷题应用

一款功能强大的Android刷题应用,支持多种学习模式和AI智能助手功能。

<div align="center">
  <img src="0FF13D0949F9F476CDAF67C439CA78BA.jpg" width="40%" style="margin: 10px;" />
  <img src="B36DDECF6BDFE1044FF6AC230898166E.jpg" width="40%" style="margin: 10px;" />
  <p><i>应用界面展示</i></p>
</div>

### 核心功能

- 📚 **多种学习模式**
  - 顺序练习
  - 随机练习
  - 模拟考试
  - 无尽模式
  
- 🤖 **AI智能助手**
  - 支持多个AI服务商(ChatGLM, Claude, DeepSeek, Gemini, Grok, Ollama, OpenAI, Qwen)
  - 智能题目解析
  - 个性化学习建议
  
- 📊 **学习数据分析**
  - 答题历史记录
  - 错题本管理
  - 学习进度追踪
  - 成绩统计图表

- 🔍 **题目管理**
  - 题目搜索
  - 分类浏览
  - 题目收藏
  - 自定义导入

### 技术特性

- 基于Android原生开发
- Material Design设计风格
- 本地数据持久化
- 网络请求与缓存优化
- AI服务集成

### 开发环境

- Android Studio
- Gradle构建系统
- Java开发语言
- Android SDK

---

## 🚀 快速开始

### Android应用安装

1. 使用Android Studio打开项目
2. 同步Gradle依赖
3. 连接Android设备或启动模拟器
4. 点击运行按钮

## 💖 支持项目

开发不易，如果本项目对你有帮助，欢迎 Star ⭐，也欢迎投喂 (๑• . •๑)♡

<div align="center">
  <img src="https://github.com/keggin-CHN/njfu_grinding/blob/main/3B7FBA4EFAB7A4B08D4F46080F8DED8D.jpg?raw=true" width="300" />
  <p><i>微信赞赏码</i></p>
</div>

---

##  许可证

本项目采用开源许可证,详见 [LICENSE](LICENSE) 文件。

## 👥 贡献

欢迎提交Issue和Pull Request来帮助改进项目!

---

<div align="center">
  <sub>Built with ❤️ for NJFU students</sub>
</div>