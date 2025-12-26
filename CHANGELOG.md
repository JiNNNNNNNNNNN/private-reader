# Changelog 更新日志

## [2.4.0] - 2025-12-26

### 架构升级 | Architecture Upgrade
- 重构 UI 架构为 MVI 模式 | Refactored UI architecture to MVI pattern
- 增强响应式数据处理 | Enhanced reactive data handling
- 优化阅读器性能 | Optimized reader performance

## [2.3.0] - 2025-11-27

### 功能优化 | Feature Improvements
- 优化了核心性能 | Optimized core performance
- 改进了UI响应 | Improved UI responsiveness
- 修复了一些已知问题 | Fixed several known issues

## [2.2.0] - 2025-07-30

### 功能优化 | Feature Improvements
- 优化大文件加载性能 | Optimized large file loading performance
- 调整缓存策略 | Adjusted caching strategy
- 修复已知问题 | Fixed known issues

## [2.1.0] - 2025-05-27

### 功能优化 | Feature Improvements
- 优化解析引擎性能 | Optimized parsing engine performance
- 改进缓存管理机制 | Improved cache management mechanism
- 增强用户界面响应速度 | Enhanced UI response speed
- 修复已知问题 | Fixed known issues

## [2.0.0] - 2025-05-09

### 重大版本升级 | Major Version Upgrade
- 全面重构核心解析引擎 | Complete refactoring of core parsing engine
- 优化内存使用和性能 | Optimized memory usage and performance
- 改进用户界面体验 | Improved user interface experience
- 增强缓存管理系统 | Enhanced cache management system

## [1.1.0] - 2025-03-05

### 功能优化 | Feature Improvements
- 优化阅读器界面 | Enhanced reader interface
- 提升章节加载速度 | Improved chapter loading speed
- 改进缓存机制 | Enhanced caching mechanism

## [1.0.0] - 2025-02-27

### 新增功能 | New Features
- 基础功能实现 | Basic Features Implementation
  - 支持添加、移除书籍 | Add and remove books
  - 支持阅读进度保存和同步 | Reading progress save and sync
  - 支持章节内容缓存和预加载 | Chapter content cache and preload
  - 支持通用网站智能解析 | Universal website smart parsing
  - 支持阅读器模式和通知栏模式切换 | Switch between reader mode and notification bar mode
  - 支持章节内容刷新（忽略缓存）| Refresh chapter content (ignore cache)
  - 支持书籍列表排序（按书名、进度、时间）| Book list sorting (by name, progress, time)
  - 支持阅读进度实时更新 | Real-time reading progress update
  - 支持章节列表缓存 | Chapter list caching

### 界面优化 | UI Optimization
- UI 优化 | UI Improvements
  - 优雅的阅读界面 | Elegant reading interface
  - 合理的菜单布局 | Reasonable menu layout
  - 快捷键支持 | Keyboard shortcuts support
  - 清屏、翻页、刷新等快捷键 | Shortcuts for clear screen, page turning, refresh
  - 可折叠的侧边栏 | Collapsible sidebar
  - 自定义字体和大小 | Custom font and size
  - 阅读进度实时显示 | Real-time progress display
  - 通知栏阅读模式优化 | Notification bar reading mode optimization

### 存储优化 | Storage Optimization
- 分离存储方案 | Separated Storage Solution
  - 主索引文件：存储所有书籍的基本信息 | Main index file: stores basic information of all books
  - 书籍详情文件：每本书单独存储详细信息 | Book detail files: separate storage for each book's details
  - 章节缓存：分散存储减少内存占用 | Chapter cache: distributed storage to reduce memory usage
  - LRU 缓存策略 | LRU cache strategy
  - 定期清理过期缓存 | Regular expired cache cleanup

### 性能优化 | Performance Optimization
- 内存缓存优化 | Memory Cache Optimization
  - 智能预加载机制 | Smart preloading mechanism
  - 缓存大小限制 | Cache size limitation
  - 过期缓存自动清理 | Automatic expired cache cleanup
  - 磁盘空间管理 | Disk space management
- 文件系统优化 | File System Optimization
  - 安全的文件名处理 | Safe filename handling
  - 分散存储减少锁竞争 | Distributed storage to reduce lock contention
  - 异步写入和预加载 | Asynchronous writing and preloading
- 后台任务处理 | Background Task Processing
  - 章节预加载 | Chapter preloading
  - 缓存清理 | Cache cleanup
  - 存储同步 | Storage synchronization

### 安全性 | Security
- 文件名安全处理 | Filename Security Handling
  - Unicode 规范化 | Unicode normalization
  - 非法字符过滤 | Illegal character filtering
  - 长度限制处理 | Length limitation handling
  - Hash 处理保持可读性 | Hash processing while maintaining readability
- 缓存目录权限控制 | Cache directory permission control
- 异常处理和日志记录 | Exception Handling and Logging
  - 详细的错误日志 | Detailed error logs
  - 优雅的降级处理 | Graceful degradation
  - 备用方案支持 | Fallback solution support