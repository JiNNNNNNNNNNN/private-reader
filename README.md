# Private Reader - IntelliJ IDEA 小说阅读插件

一个轻量级的小说阅读插件，支持多个小说网站的内容抓取和本地阅读。让你在编码之余，享受阅读的乐趣。

## 主要功能

- 📚 智能网页解析
  - 自动识别小说标题、作者
  - 智能提取章节列表
  - 基于文本密度分析的正文识别
  - 自动清理广告和无关内容

- 📖 优雅的阅读体验
  - 清晰的排版和布局
  - 可调节字体和行距
  - 章节导航（上一章/下一章）
  - 阅读进度保存
  - 支持键盘快捷键

- 💾 高效的缓存机制
  - 章节内容本地缓存
  - 自动清理过期缓存
  - 可配置缓存大小
  - 可配置缓存有效期

- 🔄 同步功能
  - 自动记录阅读位置
  - 多设备进度同步
  - 支持刷新章节列表

## 使用方法

1. 安装插件
   - 在 IDEA 插件市场搜索 "Private Reader"
   - 或下载 `.jar` 文件手动安装

2. 打开阅读器
   - 通过 `Tools -> Private Reader` 菜单
   - 或使用快捷键 `Ctrl+Alt+R`（可自定义）

3. 添加书籍
   - 点击工具栏的 "添加书籍" 按钮
   - 输入小说网页地址
   - 插件会自动解析书籍信息

4. 开始阅读
   - 双击书籍打开
   - 使用左右方向键导航章节
   - 阅读进度会自动保存

## 支持的网站

- 起点中文网
- 纵横中文网
- 本地 txt 文件
- 其他支持自动解析的小说网站

## 技术特性

- 基于 IntelliJ 平台开发
- 使用 JSoup 进行网页解析
- 文本密度分析算法
- 智能元数据提取
- 高效的缓存管理
- 异步加载和解析

## 配置说明

1. 缓存设置
   - 启用/禁用缓存
   - 设置缓存大小限制
   - 设置缓存有效期
   - 手动清理缓存

2. 显示设置
   - 字体设置
   - 行距调整
   - 边距设置

## 快捷键

- `←` - 上一章
- `→` - 下一章
- `Ctrl+Alt+R` - 打开阅读器（可自定义）

## 注意事项

- 本插件仅用于学习和技术研究
- 请勿用于任何商业用途
- 注意遵守相关网站的使用规则
- 建议开启缓存以提高阅读体验

## 问题反馈

如果遇到问题或有功能建议，欢迎：
1. 提交 Issue
2. 发送邮件到 support@yourcompany.com
3. 访问官网 http://www.yourcompany.com

## 更新日志

### v1.0.0
- 基础功能实现
  - 支持添加、移除书籍
  - 支持阅读进度保存
  - 支持章节内容缓存
  - 支持本地 txt 文件解析
- UI 优化
  - 优雅的阅读界面
  - 合理的菜单布局
  - 快捷键支持
- 网站支持
  - 起点中文网
  - 纵横中文网

## 开发计划

- [ ] 支持更多小说网站
- [ ] 添加书架分类功能
- [ ] 支持导入导出数据
- [ ] 添加搜索功能
- [ ] 支持自定义主题
- [ ] 支持更多格式的本地文件

## 项目结构

```
src/main/java/com/lv/tool/privatereader/
├── model/          # 数据模型
├── parser/         # 解析器相关代码
├── storage/        # 存储相关代码
│   └── cache/      # 缓存管理
└── ui/            # 用户界面相关代码
    ├── actions/    # 菜单动作
    └── settings/   # 设置界面
```

## 开发环境

- Java 21
- IntelliJ IDEA 2023.3+
- Gradle 8.5

## 构建方法

```bash
./gradlew buildPlugin
```

构建完成后，插件文件位于 `build/distributions/` 目录下。

## 安装方法

1. 在 IDEA 中打开 Settings
2. 转到 Plugins
3. 点击 ⚙ 图标
4. 选择 "Install Plugin from Disk..."
5. 选择构建好的插件文件

## 贡献指南

1. Fork 本项目
2. 创建你的特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交你的改动 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启一个 Pull Request

## 许可证

本项目采用 MIT 许可证 - 详见 [LICENSE](LICENSE) 文件

## 开发指南

### 环境准备
1. JDK 21
2. IntelliJ IDEA 2023.3+
3. Gradle 8.5+

### 本地开发
1. 克隆项目
```bash
git clone https://github.com/yourusername/private-reader.git
cd private-reader
```

2. 导入项目
- 使用IDEA打开项目目录
- 等待Gradle同步完成
- 确保Project Structure中JDK版本为21

3. 运行与调试
```bash
# 运行插件
./gradlew runIde

# 运行测试
./gradlew test

# 构建插件
./gradlew buildPlugin
```

4. 代码风格
- 使用4空格缩进
- 遵循Java代码规范
- 保持代码简洁清晰
- 添加必要的注释

### 模块说明

1. `model` - 数据模型
   - `Book.java` - 书籍模型
   - `Chapter.java` - 章节模型

2. `parser` - 解析模块
   - `NovelParser.java` - 解析器接口
   - `TextDensityAnalyzer.java` - 文本密度分析
   - `MetadataAnalyzer.java` - 元数据提取

3. `storage` - 存储模块
   - `BookStorage.java` - 书籍存储
   - `ChapterCacheManager.java` - 缓存管理

4. `ui` - 界面模块
   - `PrivateReaderPanel.java` - 主界面
   - `AddBookDialog.java` - 添加书籍对话框

### 调试技巧

1. 日志输出
```java
private static final Logger LOG = Logger.getInstance(YourClass.class);
LOG.info("信息日志");
LOG.debug("调试日志");
LOG.error("错误日志", exception);
```

2. 运行配置
- 在IDEA中创建"Gradle"运行配置
- Tasks填写: `runIde`
- VM options: `-Xmx2g -XX:+UseG1GC`

3. 常见问题
- 插件无法加载: 检查`plugin.xml`配置
- 解析失败: 开启DEBUG日志查看详细信息
- 缓存问题: 清理`.private-reader`目录

### 发布流程

1. 版本号更新
- 修改`build.gradle`中的version
- 更新`plugin.xml`中的change-notes
- 更新README中的更新日志

2. 构建发布包
```bash
./gradlew clean buildPlugin
```

3. 测试验证
- 在新的IDEA实例中安装构建的插件
- 验证所有功能是否正常
- 检查日志中是否有错误

4. 提交发布
- 创建Release Tag
- 上传插件到JetBrains Marketplace
- 更新文档和发布说明 