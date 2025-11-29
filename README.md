# 喵喵喂食监控 (Cat Feeder Monitor)

这是一个基于 Android 的智能猫咪喂食监控应用，利用 YOLOv8 对象检测技术实时识别猫咪，自动记录进食行为，并提供统计功能。

## ✨ 功能特点 (App Features)

### 1. 实时监控与识别 (Real-time Monitoring)
- **对象检测**: 集成 YOLOv8 模型，通过手机摄像头实时检测画面中的猫咪。
- **特定识别**: 能够识别特定的猫咪（如 "putong" 显示为 "噗通"）。
- **实时预览**: 在 "实时监控" 页面显示摄像头画面及检测框。

### 2. 智能抓拍与记录 (Smart Capture & Logging)
- **自动抓拍**: 当检测到猫咪进食时，自动抓拍照片。
- **进食会话管理**: 智能判断进食开始和结束，记录进食时长。
- **本地存储**: 照片保存在本地存储中，进食记录（猫咪名字、时间、时长、照片路径）存储于本地 Room 数据库。

### 3. 进食统计 (Feeding Statistics)
- **历史记录**: 在 "进食统计" 页面查看历史喂食记录。
- **数据展示**: 显示每次进食的时间、时长和抓拍照片。

### 4. 省电/伪息屏模式 (Power Saving Mode)
- **防休眠**: 保持屏幕常亮，防止系统自动休眠导致监控中断。
- **伪息屏**: 提供全屏黑色遮罩层，降低屏幕亮度（最低至 0.01f），节省电量并减少光污染。
- **双击唤醒**: 在伪息屏模式下，双击屏幕即可恢复正常显示。

## 📂 工程结构 (Project Structure)

项目采用标准的 Android 架构，主要代码位于 `app/src/main/java/com/example/catfeedermonitor` 下：

```
com.example.catfeedermonitor
├── data/                  # 数据层
│   ├── AppDatabase.kt     # Room 数据库定义
│   ├── FeedingDao.kt      # 数据库访问对象 (DAO)
│   └── FeedingRecord.kt   # 进食记录实体类
├── logic/                 # 业务逻辑层
│   ├── FeedingSessionManager.kt # 进食会话管理（状态机、计时）
│   └── ObjectDetectorHelper.kt  # YOLOv8 对象检测封装
├── ui/                    # UI 界面层
│   ├── CaptureController.kt     # 拍照控制器
│   ├── MonitorScreen.kt         # 实时监控页面 (Compose)
│   └── StatsScreen.kt           # 统计页面 (Compose)
└── MainActivity.kt        # 主入口，包含导航、权限处理和省电模式逻辑
```

## 🛠️ 技术栈 (Tech Stack)

- **语言**: Kotlin
- **UI 框架**: Jetpack Compose (Material3)
- **架构**: MVVM (Model-View-ViewModel)
- **数据库**: Room Database
- **AI/ML**: TensorFlow Lite / YOLOv8 (通过 `ObjectDetectorHelper` 集成)
- **异步处理**: Coroutines (协程)
- **导航**: Navigation Compose
- **权限**: Accompanist Permissions

## 🚀 快速开始 (Getting Started)

1.  **环境要求**: Android Studio Ladybug 或更高版本。
2.  **权限**: 应用首次启动时需要授予 **相机权限**。
3.  **模型文件**: 确保 `app/src/main/assets` 目录下包含有效的 YOLOv8 TFLite 模型文件（如 `best_int8.tflite`）。
4.  **构建与运行**: 连接 Android 设备，点击 Run 即可安装运行。

## 📝 注意事项

- 请确保设备电量充足或连接电源，因为实时图像处理和屏幕常亮会消耗较多电量。
- 建议使用三脚架固定手机，对准喂食器位置以获得最佳检测效果。
