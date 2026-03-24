# 围棋AI助手 (Go AI Assistant)

一款基于 Flutter 开发的 Android 围棋AI分析助手应用，通过悬浮窗实时分析围棋棋盘并推荐最佳落子。

## 功能特性

### 核心功能
- **悬浮窗分析**: 通过悬浮球快速启动AI分析，无需切换应用
- **实时截图识别**: 自动截取当前屏幕棋盘图像进行分析
- **多模型支持**: 支持配置多个AI模型，可自由切换
- **执子选择**: 支持黑棋/白棋执子方向选择，实时显示当前执子方
- **推荐落子展示**: 直观展示AI推荐的落子位置、胜率、分数
- **历史记录**: 记住上次推荐的落子位置

### 交互设计
- **三态悬浮窗**: 空闲态(IDLE)、加载态(LOADING)、结果态(RESULT)平滑切换
- **动画过渡**: 展开/关闭动画效果，提升用户体验
- **智能避让**: 截图时自动隐藏悬浮窗，避免遮挡棋盘
- **防抖输入**: 订阅地址输入支持防抖，停止输入后自动保存

### 技术亮点
- **常驻像素管道 (Persistent Pixel Pipeline)**: 维护长连接的MediaProjection、ImageReader和VirtualDisplay实例，实现快速截图
- **WebP压缩**: 截图采用WebP格式(质量75%)，体积更小、边缘保留更好
- **前台服务**: 确保悬浮窗服务在后台稳定运行

## 技术架构

### 核心技术
| 技术 | 说明 |
|------|------|
| Flutter | 跨平台移动应用开发框架 |
| Kotlin | Android原生服务实现 |
| MediaProjection API | 屏幕截图功能 |
| MethodChannel | Flutter与原生代码通信 |
| Provider | 状态管理 |

### 项目结构

```
lib/
├── main.dart                 # 应用入口
├── models/                   # 数据模型
│   └── app_config.dart       # 配置模型
├── providers/                # 状态管理
│   └── config_provider.dart  # 配置状态管理
├── screens/                  # 页面
│   └── home_screen.dart      # 主页面
├── services/                 # 服务层
│   ├── api_service.dart      # API服务
│   └── storage_service.dart  # 本地存储服务
└── widgets/                  # 自定义组件

android/app/src/main/kotlin/
└── com/goai/go_ai_assistant/
    ├── MainActivity.kt       # 主Activity
    └── FloatingService.kt    # 悬浮窗服务
```

## 环境要求

- Flutter SDK >= 3.11.0
- Android SDK >= 21 (Android 5.0)
- Android 10+ 推荐以获得最佳体验

## 安装

### 从APK安装
下载最新的APK文件，安装到Android设备即可使用。

### 从源码构建

```bash
# 克隆项目
git clone https://github.com/chengjiahua/go-ai-assistant.git

# 进入项目目录
cd go-ai-assistant

# 安装依赖
flutter pub get

# 构建APK
flutter build apk --release

# 或直接运行
flutter run
```

## 配置

### 服务器配置
在应用设置中配置AI分析服务器地址，服务器需要提供以下接口：

#### 获取模型列表
```
GET /api/models

Response:
[
  {
    "id": "model_id",
    "name": "模型名称",
    "board_size": 19
  }
]
```

#### 分析棋盘
```
POST /api/analyze
Content-Type: application/json

Request:
{
  "image": "base64_encoded_webp_image",
  "model_id": "model_identifier",
  "side": "B"  // B for Black, W for White
}

Response:
{
  "board_size": 19,
  "side_to_play": "B",
  "recommendations": [
    {
      "x": 0,
      "y": 15,
      "win_rate": 0.85,
      "score": 2.5,
      "visits": 1000,
      "move_sgf": "A16"
    }
  ]
}
```

## 权限说明

应用需要以下权限：

| 权限 | 说明 |
|------|------|
| 悬浮窗权限 | 显示分析悬浮窗 |
| 屏幕录制权限 | 截取棋盘图像进行分析 |
| 前台服务 | 保持悬浮窗服务在后台运行 |

## 使用方法

1. **启动应用**: 打开围棋AI助手
2. **配置服务器**: 点击设置图标，输入服务器地址，自动获取模型列表
3. **选择模型**: 从下拉列表选择要使用的AI模型
4. **启动服务**: 点击 **START SERVICE** 按钮启动悬浮窗服务
5. **授予权限**: 授予屏幕录制权限
6. **打开棋盘**: 打开围棋应用或网页
7. **点击悬浮球**: 点击右侧悬浮球展开菜单
8. **选择执子**: 选择执子方向（黑棋/白棋）
9. **开始分析**: 点击 **开始AI分析** 按钮
10. **查看结果**: 等待分析完成，查看推荐落子列表

## 开发说明

### Android版本适配

| Android版本 | 说明 |
|-------------|------|
| Android 10-13 | 需要声明 `foregroundServiceType="mediaProjection"` |
| Android 14+ | 必须先启动前台服务，再请求屏幕录制权限 |

### 常见问题

| 问题 | 解决方案 |
|------|----------|
| 截图失败 | 检查屏幕录制权限是否已授予 |
| 悬浮窗不显示 | 检查悬浮窗权限是否已授予 |
| 分析失败 | 检查服务器地址配置是否正确 |
| 模型列表为空 | 检查服务器地址是否正确，确保服务器可访问 |
| 第二次分析无响应 | 重启悬浮窗服务，重新授予屏幕录制权限 |

## 更新日志

### v20260325-0021
- 新增: 悬浮窗结果显示执子方向标签（黑棋/白棋）
- 新增: 点击设置自动刷新模型列表
- 新增: 订阅地址输入防抖功能
- 优化: 三态悬浮窗交互体验
- 优化: 截图时自动隐藏悬浮窗
- 修复: 黑白棋选择立即响应问题
- 修复: DropdownButton模型不存在时崩溃问题
- 修复: 订阅地址输入卡顿问题

## 许可证

MIT License

## 贡献

欢迎提交 Issue 和 Pull Request！
