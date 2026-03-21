# 围棋AI助手 (Go AI Assistant)

一款基于 Flutter 开发的 Android 围棋AI分析助手应用，通过悬浮窗实时分析围棋棋盘并推荐最佳落子。

## 功能特性

- **悬浮窗分析**: 通过悬浮球快速启动AI分析，无需切换应用
- **实时截图识别**: 自动截取当前屏幕棋盘图像进行分析
- **多模型支持**: 支持配置多个AI模型，可自由切换
- **执子选择**: 支持黑棋/白棋执子方向选择
- **推荐落子展示**: 直观展示AI推荐的落子位置及胜率
- **历史记录**: 记住上次推荐的落子位置

## 技术架构

### 核心技术
- **Flutter**: 跨平台移动应用开发框架
- **Kotlin**: Android原生服务实现
- **MediaProjection API**: 屏幕截图功能
- **MethodChannel**: Flutter与原生代码通信

### 关键实现
- **常驻像素管道 (Persistent Pixel Pipeline)**: 维护长连接的MediaProjection、ImageReader和VirtualDisplay实例，实现快速截图
- **前台服务**: 确保悬浮窗服务在后台稳定运行
- **权限管理**: 处理悬浮窗权限和屏幕录制权限

## 环境要求

- Flutter SDK >= 3.0.0
- Android SDK >= 21 (Android 5.0)
- Android 10+ 推荐以获得最佳体验

## 安装

```bash
# 克隆项目
git clone <repository-url>

# 进入项目目录
cd phone-cli

# 安装依赖
flutter pub get

# 运行应用
flutter run
```

## 配置

### 服务器配置
在应用设置中配置AI分析服务器地址，服务器需要提供以下接口：

```
POST /analyze
Content-Type: application/json

Request:
{
  "image": "base64_encoded_image",
  "model_id": "model_identifier",
  "side": "B" // B for Black, W for White
}

Response:
{
  "recommendations": [
    {
      "x": "A",
      "y": 16,
      "win_rate": 0.85,
      "move_sgf": "A16"
    }
  ]
}
```

### 模型配置
支持在应用中配置多个AI模型，每个模型包含：
- 模型ID
- 模型名称

## 权限说明

应用需要以下权限：
- **悬浮窗权限**: 显示分析悬浮窗
- **屏幕录制权限**: 截取棋盘图像进行分析

## 使用方法

1. 启动应用，配置服务器地址和模型
2. 点击 **START** 按钮启动悬浮窗服务
3. 授予屏幕录制权限
4. 打开围棋应用或网页
5. 点击悬浮球展开菜单
6. 选择执子方向（黑棋/白棋）
7. 点击 **开始AI分析** 按钮
8. 等待分析完成，查看推荐落子

## 项目结构

```
lib/
├── main.dart                 # 应用入口
├── models/                   # 数据模型
├── providers/                # 状态管理
├── screens/                  # 页面
├── services/                 # 服务层
└── widgets/                  # 自定义组件

android/app/src/main/kotlin/
└── com/goai/go_ai_assistant/
    ├── MainActivity.kt       # 主Activity
    └── FloatingService.kt    # 悬浮窗服务
```

## 开发说明

### Android版本适配

- **Android 10-13**: 需要声明 `foregroundServiceType="mediaProjection"`
- **Android 14+**: 必须先启动前台服务，再请求屏幕录制权限

### 常见问题

1. **截图失败**: 检查屏幕录制权限是否已授予
2. **悬浮窗不显示**: 检查悬浮窗权限是否已授予
3. **分析失败**: 检查服务器地址配置是否正确

## 许可证

MIT License
