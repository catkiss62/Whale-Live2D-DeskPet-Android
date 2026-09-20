# Q版鲸鱼 Live2D Android 任务总账

> 本文件是本项目唯一任务与实机交接事实源。编译成功不等于实机确认。

## 项目边界

- 目标公开仓库：`catkiss62/Whale-Live2D-DeskPet-Android`。
- 项目与 Sen Live2D 仓库完全分离。
- 用户购买模型只允许本机 ZIP 导入，不得写入仓库或 APK。
- 使用官方 Cubism SDK for Java 5 R5、Core AAR和原生 OpenGL。
- 仓库内 debug keystore 只用于公开测试包覆盖安装。

## 交接说明

- 上一窗口记录的本地提交 `1b46f1c` 没有进入远端，旧临时工作区也已不可用，因此无法原样取回该 Git 对象。
- 当前版本依据已确认需求、模型目录实测结果与相同 Cubism 工程骨架重建；以本仓库新提交为准，不把新提交冒充为 `1b46f1c`。

## 已确认的模型事实

- 外层购买包包含“DS鼠控版.zip”和“DS面捕版.zip”；首版默认选择鼠控版。
- 鼠控版 `c_0120.model3.json`只登记 moc3、2张2048贴图、物理和CDI，没有登记购买包中的表情与动作。
- 目录实测包含44个 `.exp3.json`。
- 动作共8个：根目录 `aidale.motion3.json`，以及 `motions/` 下7个文件；其中
  `motions/idle.motion3.json`作为持续待机，其余7个动作单次播放。
- 模型素材不得提交，本仓库只实现安全扫描、加载和语义调用。

## 提交前本地校验

- [x] 对鼠控版目录重新扫描：44 个 `.exp3.json`、8 个 `.motion3.json`，并确认 `motions/idle.motion3.json` 存在。
- [x] 13 类陪玩反应引用的表情与动作 ID 均在实测目录中存在。
- [x] `git diff --cached --check` 通过，暂存区不含 ZIP、moc3、model3、physics3、exp3、motion3 或贴图。
- [x] Cubism Core AAR SHA-256：`3f05da57ab855e803000e6353888dd561c47758598c6c0200dcd0109312705f8`。
- [x] 公开测试 debug keystore SHA-256：`52ad5de65ba4159e336b72971e42a3e211718ed7c8179d935fe3d7bc1e745830`。

## v0.1.0-native-catalog（已制作，待Actions与实机确认）

- [x] Android/Cubism Java 5 R5 工程骨架、Core AAR、Framework子模块、无mipmap纹理补丁和公开测试签名。
- [x] 支持直接内层 ZIP 与外层嵌套 ZIP，优先选择鼠控版；安全限制路径、条目数和解压总量。
- [x] 运行时扫描未登记的44表情与8动作，不修改购买包原文件。
- [x] idle持续循环，其他动作单次播放并自动恢复idle；表情独立开关和一键还原。
- [x] 13类陪玩语义反应独立封装，后续可替换组合而不拆渲染器。
- [x] 系统悬浮桌宠服务、透明GL窗口、拖动、轻点反应、前台通知与关闭入口。
- [ ] GitHub Actions完整编译成功并校验APK。
- [ ] 目标手机确认内层/外层导入、44/8目录、画面、物理、动作、表情和13反应。
- [ ] 目标手机确认悬浮窗授权、桌宠透明度、拖动、触摸、前后台与关闭流程。

## 下一步顺序

1. 推送首个完整根提交并运行GitHub Actions。
2. 若编译失败，只修直接编译错误并保留失败记录。
3. 下载并校验APK，再由用户进行模型与悬浮桌宠实机测试。
4. 根据实机结果修正原生表达式/动作组合；在此之前不接入LLM或正式AI伴侣。
