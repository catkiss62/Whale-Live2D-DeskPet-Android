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
  `motions/idle.motion3.json`主要驱动星星、锤子、贴纸等道具，并非真人式呼吸/转头待机；
  v0.2.0起仅手动单次播放。
- 鼠控版 VTube Studio 配置登记了标准头部、身体、视线、眼睛开合和呼吸参数，
  可用于不改购买素材的独立程序化待机层。
- 模型素材不得提交，本仓库只实现安全扫描、加载和语义调用。

## v0.1.0 实机反馈与根因

- [x] 画面可以渲染，但原生动作/表情/道具的中文按钮显示乱码。
- [x] 购买包 ZIP 的旧编码文件名带有 Info-ZIP Unicode Path（`0x7075`）额外字段；
  Android 默认 `ZipInputStream`采用了错误名称，目录 ID 因此也无法匹配中文陪玩反应。
- [x] “开心庆祝”把 `双手比耶.exp3.json`误写进动作槽，属于第二处独立映射错误。
- [x] 旧版持续播放的原生 idle 不是人物自然待机，无法提供皮套式灵动感。

## 提交前本地校验

- [x] 对鼠控版目录重新扫描：44 个 `.exp3.json`、8 个 `.motion3.json`，并确认 `motions/idle.motion3.json` 存在。
- [x] 13 类陪玩反应引用的表情与动作 ID 均在实测目录中存在。
- [x] `git diff --cached --check` 通过，暂存区不含 ZIP、moc3、model3、physics3、exp3、motion3 或贴图。
- [x] Cubism Core AAR SHA-256：`3f05da57ab855e803000e6353888dd561c47758598c6c0200dcd0109312705f8`。
- [x] 公开测试 debug keystore SHA-256：`52ad5de65ba4159e336b72971e42a3e211718ed7c8179d935fe3d7bc1e745830`。

## v0.1.0-native-catalog（已构建，待实机确认）

- [x] Android/Cubism Java 5 R5 工程骨架、Core AAR、Framework子模块、无mipmap纹理补丁和公开测试签名。
- [x] 支持直接内层 ZIP 与外层嵌套 ZIP，优先选择鼠控版；安全限制路径、条目数和解压总量。
- [x] 运行时扫描未登记的44表情与8动作，不修改购买包原文件。
- [x] idle持续循环，其他动作单次播放并自动恢复idle；表情独立开关和一键还原。
- [x] 13类陪玩语义反应独立封装，后续可替换组合而不拆渲染器。
- [x] 系统悬浮桌宠服务、透明GL窗口、拖动、轻点反应、前台通知与关闭入口。
- [x] GitHub Actions完整编译成功并校验APK。
- [ ] 目标手机确认内层/外层导入、44/8目录、画面、物理、动作、表情和13反应。
- [ ] 目标手机确认悬浮窗授权、桌宠透明度、拖动、触摸、前后台与关闭流程。

## v0.2.0-unicode-lively-idle（已构建、待实机确认）

- [x] 从 ZIP `0x7075`字段读取正确 UTF-8 路径，保留原有路径穿越、条目数和总量防护。
- [x] 检测 v0.1.0 遗留的 U+FFFD 乱码目录，并明确要求用户重新导入 ZIP。
- [x] 修正“开心庆祝”：`开心兴奋`与`双手比耶`均按表情加载，不再把后者当动作。
- [x] 运行时按类型核对13类反应，界面显示可用数并禁用实际缺文件的组合。
- [x] 原生8动作和44表情全部保留；原生 idle 改为手动单次测试。
- [x] 新增可整体拆除的程序化待机层，默认自然档，提供关闭/自然/活泼三档；
  仅使用模型原有头、身体、视线、呼吸、眨眼参数，原生动作播放时让路。
- [x] GitHub Actions 编译成功并校验测试 APK。
- [ ] 真机重新导入后确认中文目录、44/8计数和陪玩映射 `13/13`。
- [ ] 真机比较三档灵动程度，确认物理跟随、眨眼和原生动作不存在冲突。
- [x] 真机确认默认自主待机已有明显效果且总体可以继续使用。
- [x] 真机发现旧乱码目录触发界面提前返回，导致13类反应与44/8原生按钮全部未创建；
  这是 v0.2.0 的测试面板逻辑错误，并非模型动作被删除。

## v0.2.1-full-controls（待构建、待实机确认）

- [x] 删除乱码目录下的面板提前返回逻辑，灵动待机、13类反应、还原、原生动作和
  原生表情/道具区域始终创建。
- [x] 正常目录继续显示中文；旧乱码目录将8个动作与44个表情/道具显示为编号，
  按钮使用实际扫描 ID，仍可逐项触发和筛选。
- [x] 13类语义反应在旧目录下保留可见并标注“重导入后”；重新导入得到正确中文 ID 后
  自动恢复可用并显示 `13/13`。
- [x] ZIP 解压增加 GB18030 文件名解码兜底，同时保留 `0x7075` Unicode Path 优先解析，
  兼容 Android 未向 `ZipEntry`暴露额外字段的情况。
- [ ] GitHub Actions 编译成功并校验测试 APK。
- [ ] 真机确认所有按钮恢复；重新导入后中文名称正常，或异常时完整回退为编号。

## v0.2.0 构建证据

- 远端功能提交：`c94d237a7a293af21ceacb438336002601847bc3`（Git 树 `9ad0bb05f82a38b548cdf955c504396fa4378b88`）。
- GitHub Actions：run `35519197475`，`assembleDebug` 与 APK artifact 上传均成功。
- Artifact：`Whale-Live2D-DeskPet-Test-APK`，ID `10607558160`。
- Artifact ZIP SHA-256：`fdf11a4ce06aa227154d3e8f0e453446dec804749116d60c0385a63bef36b687`（与 GitHub artifact digest 一致）。
- `app-debug.apk` SHA-256：`9cae2c0f0e114c92328a995ca0b566f610ee2a010a2088adabd1bace9b72f85a`。
- ZIP 完整性检查通过；APK 包含 arm64-v8a/x86/x86_64 Cubism Core JNI，未发现模型 ZIP、
  moc3、model3、physics3、cdi3、vtube、exp3、motion3 或 `assets/` 模型图片。

## 构建证据

- 远端功能提交：`cce29a5074919bbc11bad0d11e7bfbf6f5ec9bdd`（Git 树 `d9a121e17747963cb75be8131a21fa6246a4a6c3`）。
- GitHub Actions：run `35516357402`，`assembleDebug` 与 APK artifact 上传均为 `success`。
- Artifact：`Whale-Live2D-DeskPet-Test-APK`，ID `10607096383`。
- Artifact ZIP SHA-256：`6f1ea8f96bf658c0141daa8f9539d0bea6958873523bdb869a060d924844a287`（与 GitHub artifact digest 一致）。
- `app-debug.apk` SHA-256：`521a1a0711e7e8bdc778cbe32d7ae39882586528e8cf9c352cd46dce9387a0cb`。
- ZIP 完整性检查通过；APK 包含 arm64-v8a/x86/x86_64 Cubism Core JNI，未发现 moc3、model3、physics3、exp3、motion3 或 `assets/` 下模型贴图。

## 下一步顺序

1. 构建 v0.2.1 测试 APK，并确认包内仍不含购买素材。
2. 用户覆盖安装后先观察旧目录的编号按钮，再重新导入 ZIP，确认中文目录与 `13/13`。
3. 根据逐项按钮测试结果筛选原生动作/表情；本轮暂不验收悬浮桌宠，也不接入LLM。
