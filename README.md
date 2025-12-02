# TikTok 简化直播间 Android 应用

一个高性能的 Android 直播应用，采用 MVVM 架构，支持 DASH 直播流播放、实时评论、在线人数统计等功能。通过多种性能优化技术，实现了流畅的直播观看体验。

## 📱 项目简介

本项目是一个仿 TikTok 直播间的 Android 应用，主要功能包括：

- 🎥 **DASH 直播流播放**：使用 xgplayer + Shaka.js 播放器，支持流畅的直播流播放
- 💬 **实时评论系统**：支持发送和接收实时评论，评论列表自动滚动
- 👥 **在线人数统计**：通过 WebSocket 实时更新在线观众数量
- 🎯 **性能优化**：预加载、视图池、TTL 缓存等多种优化技术
- 🔌 **插件化架构**：支持点赞、礼物、聊天等插件功能

## 🎯 核心功能

### 1. 启动流程
- **SplashActivity**：启动页，显示应用 Logo，后台预加载关键组件
- 预加载 WebView、WebSocket、房间信息、图片等资源
- 确保启动后快速进入直播间

### 2. 直播间列表
- **RoomListActivity**：显示所有直播间列表
- 网格布局展示直播间缩略图和主播信息
- 支持预加载和图片缓存

### 3. 直播间详情
- **LiveRoomActivity**：直播间主界面
  - 视频播放区域（WebView + xgplayer）
  - 主播信息展示（头像、名称、关注数）
  - 实时评论列表（RecyclerView）
  - 在线人数显示
  - 评论输入和发送功能

### 4. 性能优化
- **预加载机制**：启动时预加载 WebView、WebSocket、房间信息
- **TTL 缓存**：退出直播间后 30 秒内重新进入，快速恢复完整状态
- **视图池**：复用 RecyclerView 的 item 布局，减少 inflate 开销
- **布局预加载**：预渲染 Activity 布局，减少首次渲染时间
- **图片预加载**：使用 Glide 预加载主播头像

## 🏗️ 技术栈

### 架构模式
- **MVVM (Model-View-ViewModel)**：清晰的职责分离，数据驱动 UI
- **Repository 模式**：统一数据访问接口
- **插件化架构**：支持功能扩展

### 核心技术
- **xgplayer**：HTML5 视频播放器（集成 Shaka.js 支持 DASH）
- **WebView**：承载播放器，支持硬件加速
- **OkHttp**：网络请求库
- **Gson**：JSON 解析
- **Glide**：图片加载和缓存
- **WebSocket**：实时通信（在线人数、评论）

### Android 组件
- **Lifecycle & LiveData**：生命周期感知，响应式数据更新
- **ViewModel**：管理 UI 相关数据，独立于 Activity 生命周期
- **RecyclerView**：列表展示，支持 DiffUtil 优化
- **ConstraintLayout**：灵活的布局系统

## 📂 项目结构

```
app/src/main/java/com/bytedance/myapplication/
│
├── activity/                    # View 层 - Activity
│   ├── SplashActivity.java      # 启动页，预加载关键组件
│   ├── RoomListActivity.java    # 直播间列表
│   └── LiveRoomActivity.java    # 直播间详情（核心界面）
│
├── adapter/                     # View 层 - 列表适配器
│   ├── CommentAdapter.java      # 评论列表适配器（支持长文本截断）
│   └── RoomListAdapter.java    # 直播间列表适配器
│
├── viewmodel/                   # ViewModel 层 - 业务逻辑
│   ├── LiveRoomViewModel.java   # 直播间业务逻辑（主播、评论、在线人数）
│   └── RoomListViewModel.java   # 直播间列表业务逻辑
│
├── repository/                  # Model 层 - 数据仓库
│   └── LiveRoomRepository.java  # 统一数据访问接口
│
├── model/                       # Model 层 - 数据模型
│   ├── Host.java                # 主播信息模型
│   └── Comment.java             # 评论信息模型
│
├── utils/                       # 工具类
│   ├── PreloadManager.java      # 预加载管理器（WebView、WebSocket、TTL缓存）
│   ├── WebViewConfigHelper.java # WebView 配置优化
│   ├── WebSocketManager.java    # WebSocket 连接管理
│   ├── ApiService.java          # HTTP API 服务
│   ├── ViewPoolManager.java     # RecyclerView 视图池管理
│   ├── ActivityLayoutPreloader.java # Activity 布局预加载
│   ├── PerformanceMonitor.java  # 性能监控
│   └── SmoothnessMonitor.java   # 流畅度监控
│
├── plugin/                      # 插件化架构
│   ├── PluginManager.java       # 插件管理器
│   ├── IPlugin.java             # 插件接口
│   ├── BasePlugin.java          # 插件基类
│   └── example/                 # 示例插件
│       ├── LikePlugin.java      # 点赞插件
│       ├── GiftPlugin.java      # 礼物插件
│       └── ChatPlugin.java      # 聊天插件
│
└── LiveBoard.java               # Application 类（全局初始化）
```

### 资源文件结构

```
app/src/main/res/
├── layout/                      # 布局文件
│   ├── activity_splash.xml      # 启动页布局
│   ├── activity_room_list.xml  # 直播间列表布局
│   ├── activity_live_room.xml  # 直播间详情布局
│   ├── item_comment.xml         # 评论项布局
│   └── item_room.xml           # 直播间项布局
│
├── drawable/                    # 图片资源
│   ├── tiktok_logo.png         # 应用 Logo
│   └── splash_logo_background.xml # 启动页 Logo 背景
│
└── assets/                      # 资源文件
    ├── player.html              # 播放器 HTML（xgplayer + Shaka.js）
    └── dash_live_final.html     # DASH 直播流测试页面
```

## 🚀 核心流程

### 应用启动流程

```
1. LiveBoard.onCreate()
   ├── 创建全局线程池
   ├── 初始化插件管理器
   ├── 初始化视图池管理器
   ├── 初始化布局预加载器
   └── 启动预加载管理器

2. SplashActivity.onCreate()
   ├── 显示启动页 UI
   ├── 启动 PreloadManager.startPreload()
   │   ├── 预加载 WebView
   │   ├── 预加载 WebSocket
   │   ├── 预加载房间信息（JSON）
   │   └── 预加载图片
   └── 等待预加载完成（最多 5 秒）

3. 跳转到 RoomListActivity
   └── 显示直播间列表
```

### 进入直播间流程

```
1. 用户点击直播间
   └── 启动 LiveRoomActivity

2. LiveRoomActivity.setupVideoPlayer()
   ├── 检查 TTL 缓存
   │   ├── 有缓存且有效 → 恢复 WebView 和完整状态
   │   └── 无缓存或无效 → 继续下一步
   ├── item1 第一次进入 → 检查预加载的 WebView
   └── 其他情况 → 使用布局中的 WebView，调用 loadPlayer()

3. 加载数据
   ├── viewModel.loadHostInfo() → 获取主播信息
   ├── viewModel.loadComments() → 获取评论列表
   └── viewModel.setupWebSocket() → 连接 WebSocket（在线人数）

4. 播放器加载
   └── WebView.loadUrl("file:///android_asset/player.html?url=...")
       └── xgplayer 初始化 → Shaka.js 加载 DASH 流 → 开始播放
```

### 退出直播间流程

```
1. LiveRoomActivity.onDestroy()
   ├── 检查 WebView 状态
   │   ├── 正在加载中 → 回收到复用池
   │   └── 已加载完成 → 保存到 TTL 缓存
   │       ├── WebView（保持活跃，静音播放）
   │       ├── 主播信息
   │       ├── 评论列表
   │       ├── 在线人数
   │       └── 评论滚动位置
   └── 清理资源
```

## ⚡ 性能优化技术

### 1. 预加载机制（PreloadManager）

**描述**：
- 为了让用户可以减少数据加载时间
- 假设item1为用户常去直播间，方便用户打开软件的时候可以直接获取到该直播间数据，直接0ms渲染


**功能**：
- 启动时预加载 WebView、WebSocket、房间信息、图片
- 预加载 item1 的直播流（后台播放）
- 管理 TTL 缓存（30 秒有效期）



### 2. TTL 缓存（Time-To-Live Cache）

**功能**：
- 退出直播间后 30 秒内重新进入，快速恢复完整状态
- 保存 WebView、主播信息、评论、在线人数、滚动位置

**关键特性**：
- WebView 在隐藏容器中保持活跃播放（静音）
- 切回时只需取消静音，无需重新加载
- 如果数据不完整（如主播信息未加载），不保存到 TTL 缓存

### 3. 视图池（ViewPoolManager）

**功能**：
- 复用 RecyclerView 的 item 布局，减少 inflate 开销
- 预加载 `item_comment` 和 `item_room` 布局



### 4. 布局预加载（ActivityLayoutPreloader）

**功能**：
- 预渲染 Activity 布局，减少首次渲染时间
- 使用 `LayoutInflater.inflate()` 预创建 View 树



### 5. 图片预加载（Glide）

**功能**：
- 启动时预加载主播头像
- 使用 Glide 的缓存机制


### 6. WebView 优化

**配置**：
- 硬件加速（支持时）
- JavaScript 接口
- 缓存策略优化


### 7. 防抖机制

**在线人数更新防抖**：
- 累积多次 WebSocket 消息后，延迟 500ms 一次性更新 UI
- 避免频繁的 `setText()` 操作

**评论滚动防抖**：
- 延迟 200ms 执行滚动操作
- 减少布局计算频率

## 🔌 插件化架构

### 插件接口

```java
public interface IPlugin {
    String getName();
    void onActivate(Activity activity);
    void onDeactivate();
    boolean isEnabled();
}
```

### 示例插件

1. **LikePlugin（点赞插件）**
   - 管理点赞数和点赞状态
   - 提供 LiveData 供 UI 观察

2. **GiftPlugin（礼物插件）**
   - 发送礼物功能
   - 礼物动画效果

3. **ChatPlugin（聊天插件）**
   - 扩展聊天功能


## 📊 性能监控

### PerformanceMonitor

**功能**：
- 记录应用启动时间
- 记录页面启动和渲染时间
- 记录图片加载时间
- 记录视频播放性能指标

**使用**：
```java
// 记录页面启动
PerformanceMonitor.recordPageStartTime("LiveRoomActivity");

// 记录页面渲染
PerformanceMonitor.recordPageRenderTime("LiveRoomActivity");

// 记录视频性能
PerformanceMonitor.recordVideoPerformanceMetrics(pageId, metrics);
```

### SmoothnessMonitor

**功能**：
- 监控页面流畅度（FPS）
- 检测卡顿情况

## 🎨 UI 设计

### 布局层级

```
ConstraintLayout (根布局)
├── WebView (视频播放区域，elevation=0dp)
├── host_info_layout (主播信息，elevation=10dp)
├── online_count (在线人数，elevation=10dp)
├── like_container_stub (点赞按钮，ViewStub 延迟加载)
├── comment_recycler_view (评论列表)
└── comment_input_layout (评论输入区域)
```

**关键设计**：
- WebView 在底层，其他组件在上层（通过 elevation 控制）
- 使用 ViewStub 延迟加载点赞 UI
- 评论列表支持长文本截断和展开

## 🔧 配置说明

### 网络配置

- **API 基础地址**：在 `ApiService.java` 中配置
- **WebSocket 地址**：在 `WebSocketManager.java` 中配置
- **直播流地址**：在 `LiveRoomActivity.java` 中配置 `DEFAULT_STREAM_URL`

### 预加载配置

- **TTL 缓存时间**：30 秒（`PreloadManager.DEFAULT_TTL_MS`）
- **预加载等待时间**：最多 5 秒（`SplashActivity.MAX_WAIT_TIME_MS`）
- **最小显示时间**：1.5 秒（`SplashActivity.MIN_DISPLAY_TIME_MS`）

## 📝 代码规范

### 命名规范
- **Activity**：`XxxActivity.java`
- **ViewModel**：`XxxViewModel.java`
- **Repository**：`XxxRepository.java`
- **Adapter**：`XxxAdapter.java`
- **工具类**：`XxxHelper.java` 或 `XxxManager.java`

### 注释规范
- 类和方法使用 JavaDoc 注释
- 关键逻辑添加行内注释
- 复杂算法添加说明注释

### 代码组织
- 按功能模块组织代码
- 工具类统一放在 `utils` 包
- 遵循单一职责原则

## 🚀 快速开始

### 环境要求

- Android Studio Hedgehog | 2023.1.1 或更高版本
- JDK 11 或更高版本
- Android SDK 23+ (Android 6.0+)
- Gradle 8.0+

### 构建步骤

1. **克隆项目**
   ```bash
   git clone <repository-url>
   cd MyApplication2
   ```

2. **配置网络地址**
   - 修改 `ApiService.java` 中的 API 基础地址
   - 修改 `WebSocketManager.java` 中的 WebSocket 地址
   - 修改 `LiveRoomActivity.java` 中的 `DEFAULT_STREAM_URL`



3. **运行应用**
   - 在 Android Studio 中打开项目
   - 连接 Android 设备或启动模拟器
   - 点击 Run 按钮



## 🔍 关键代码位置

### 核心 Activity
- **启动页**：`activity/SplashActivity.java`
- **直播间列表**：`activity/RoomListActivity.java`
- **直播间详情**：`activity/LiveRoomActivity.java`

### 核心工具类
- **预加载管理**：`utils/PreloadManager.java`
- **WebView 配置**：`utils/WebViewConfigHelper.java`
- **性能监控**：`utils/PerformanceMonitor.java`

### 播放器
- **HTML 播放器**：`assets/player.html`
- **播放器配置**：参考 `dash_live_final.html`


## 🔮 未来计划

- [ ] 支持更多直播流格式（HLS、FLV 等）
- [ ] 添加礼物动画效果
- [ ] 添加更多性能优化技术

## 📄 许可证

本项目仅供学习和研究使用。

## 👥 贡献

欢迎提交 Issue 和 Pull Request！

---

