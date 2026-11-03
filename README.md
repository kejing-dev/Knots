# 绳结 Knots

![绳结 Knots](docs/poster.jpg)

[中文](#中文) · [English](#english)

---

## 中文

**一根真实粗细的绳子在你面前一步步打出 13 种常用结，你手里的真绳跟着打。**

PICO 混合现实应用（PICO OS 6 · Spatial SDK 6.1.9 · 透视模式）。先在真实地面上点一个标记，演示绳就立在标记上方，从你自己的视角一步一步打出绳结；手里有绳子就跟着打，没有绳子也能学——每一步最后轮到你，捏住橙色绳头沿虚线拖过去。

### 能做什么

- **13 种常用结**：单结、双重单结、8 字结、平结、单套结、丁香结、双半结、圈绕双半结、营钉结、单结环、8 字绕圈结、接绳结、双渔人结；按基础 / 露营 / 航海 / 攀登筛选
- **真实粗细**：4 / 8 / 11 mm 三种绳径，绳与绳之间留出真实间隙，收紧时总长度不变
- **双绳结**：接绳结、双渔人结用一蓝一红两根绳演示
- **看清穿法**：每一步自动演示，可暂停、拖进度、调速、重放；「转 90°」「我的视角」从侧面、背面看清从上还是从下穿
- **不挡面板**：绳子立在地面标记上方；面板偏到惯用手一侧，绳子挡住视线时自动变半透明
- **腾不出手也能翻页**：看着「下一步」停一会儿就翻页（眼动，回退头朝向）
- 手势捏合 / 眼动注视 / 手柄射线都能用；左手模式、三档高度；中英双语；本机运行，不联网、不要账号
- 攀登相关的结注明「仅供学习，须由合格教练当面检查」

### 截图

> 截图来自 PICO 模拟器 6.1（画质高清，中文界面），由调试广播驱动，**非真实手势录像**。

| | |
|---|---|
| ![营钉结](docs/shot-01.jpg) 营钉结第 3 步：绕木桩，面板在惯用手一侧 | ![绳结库](docs/shot-02.jpg) 绳结库：13 个结，按类别筛选 |
| ![地面标记](docs/shot-03.jpg) 先在地上放标记，绳子立在标记上方 | ![单套结](docs/shot-04.jpg) 单套结第 3 步「绕到固定端后面」 |
| ![单结](docs/shot-05.jpg) 单结第 2 步「绳头从圈里穿过来」 | ![v0.1.3 新增](docs/shot-06.jpg) v0.1.3 新增与修正的结（局部放大） |

### 安装

从 [Release v0.1.3](https://github.com/kejing-dev/Knots/releases/tag/v0.1.3) 下载 `Knots-v0.1.3.apk`，头显开启开发者模式并用 USB 连上电脑：

```bash
adb install -r Knots-v0.1.3.apk
pico-cli app launch io.embodit.knots
```

需要 PICO OS 6（Android 15，minSdk 35）。首次打开会请求眼动权限（用于注视翻页；拒绝也能用，回退到头朝向）。

### 已知问题

1. v0.1.3 的模拟器 QA 没跑完（QA #5 被硬件掉线打断）；本版由用户在头显上验证后放行。
2. 模拟器上手柄路径无法验证：PICO 6.1 模拟器手柄 provider 报 `DEVICE_NOT_SUPPORTED`（平台限制），手柄提示条和射线拖绳头只在真机上验证过。
3. 绳子挡住面板变半透明后，绳子的点击区域仍可能挡在面板前，偶尔点「下一步」会落到绳子上（QA #3 发现，v0.1.3 是否已修没经 QA 确认）；换个角度或移开标记即可。

### 版本历史

- **v0.1.3**：新增营钉结、接绳结、双渔人结（双绳一蓝一红），共 13 个结；修好双重单结（之前实际只是单结）；图标换成标准自适应图标
- **v0.1.2**：新增单结、双重单结、双半结、单结环、圈绕双半结、8 字绕圈结，共 10 个结
- **v0.1.1**：绳子不再按头的位置放——先在地上点标记；面板偏到惯用手一侧，绳子挡视线时变半透明；中英文案不再截断
- **v0.1.0**：首个版本，8 字结、平结、丁香结、单套结

### 许可与素材

- 字体：Noto Sans / Noto Sans SC（SIL Open Font License，见 `third_party/`）
- 绳结打法对照公开资料核对，来源见 [docs/knot-sources.md](docs/knot-sources.md)；绳路全部由应用自己的控制点生成
- 本仓库私有；用 Claude Code 在「黑灯工厂」产线上研发（Claude Opus 5.5 研发 / QA / 发布，Claude Fable 5.1 调研 / 设计）
- 开发结构、QA 钩子见 [AGENTS.md](AGENTS.md)

---

## English

**A life-size rope ties 13 common knots step by step right in front of you — tie along with a real rope in your hands.**

A PICO mixed-reality app (PICO OS 6 · Spatial SDK 6.1.9 · passthrough). Put a marker on your real floor and the demo rope stands above it, tying each knot from your own point of view. Have a rope? Tie along. No rope? Every step ends with "your turn": pinch the orange tip and drag it along the dashes.

### Features

- **13 knots**: overhand, double overhand, figure eight, square knot, bowline, clove hitch, two half hitches, round turn & two half hitches, taut-line hitch, overhand loop, figure-eight follow-through, sheet bend, double fisherman's; filter by basics / camping / sailing / climbing
- **Real thickness**: 4 / 8 / 11 mm ropes with real gaps between strands; tightening keeps the rope length
- **Two-rope knots**: sheet bend and double fisherman's use a blue and a red rope
- **See every crossing**: auto demo with pause, scrub, speed and replay; "Turn 90°" and "My view" show whether the end goes over or under
- **Never blocks the panel**: the rope stands over a floor marker, the panel sits on your hand side, and the rope turns see-through when it crosses your view of the panel
- **Hands-free paging**: look at "Next" for a moment (eye tracking, head direction as fallback)
- Hand pinch, eye gaze and controller ray all work; left-hand mode, three heights; Chinese and English; fully offline, no account
- Climbing knots carry "for learning only — have a qualified instructor check it in person"

### Screenshots

> PICO emulator 6.1 screenshots (high-definition, Chinese UI), driven by debug broadcasts — **not a recording of real hand input**.

| | |
|---|---|
| ![Taut-line hitch](docs/shot-01.jpg) Taut-line hitch, step 3, around the post | ![Library](docs/shot-02.jpg) Knot library: 13 knots by category |
| ![Floor marker](docs/shot-03.jpg) Put a marker on the floor first | ![Bowline](docs/shot-04.jpg) Bowline, step 3 |
| ![Overhand](docs/shot-05.jpg) Overhand knot, step 2 | ![New in v0.1.3](docs/shot-06.jpg) Knots added / fixed in v0.1.3 (close-ups) |

### Install

Download `Knots-v0.1.3.apk` from [Release v0.1.3](https://github.com/kejing-dev/Knots/releases/tag/v0.1.3), enable developer mode on the headset and connect it over USB:

```bash
adb install -r Knots-v0.1.3.apk
pico-cli app launch io.embodit.knots
```

Requires PICO OS 6 (Android 15, minSdk 35). First launch asks for eye-tracking permission (used for look-to-advance; denying falls back to head direction).

### Known issues

1. Emulator QA for v0.1.3 did not finish (QA #5 was cut off by a hardware disconnect); this release was approved after the user verified it on the headset.
2. The controller path cannot be verified on the emulator: the PICO 6.1 emulator's controller provider reports `DEVICE_NOT_SUPPORTED` (platform limit); the controller hint and ray-dragging were verified on device only.
3. When the rope turns see-through in front of the panel, its tap box may still sit in front of the panel, so a tap on "Next" can land on the rope (found in QA #3; whether v0.1.3 fixes it is not QA-confirmed). Look from another angle or move the marker.

### Version history

- **v0.1.3**: added taut-line hitch, sheet bend and double fisherman's (two ropes, blue and red) — 13 knots; fixed the double overhand (it was really a single overhand); standard adaptive icon
- **v0.1.2**: added overhand, double overhand, two half hitches, overhand loop, round turn & two half hitches, figure-eight follow-through — 10 knots
- **v0.1.1**: the rope is no longer placed from the head pose — put a floor marker first; panel on the hand side, rope turns see-through when it blocks the panel; no more truncated text
- **v0.1.0**: first version — figure eight, square knot, clove hitch, bowline

### Licenses

- Fonts: Noto Sans / Noto Sans SC (SIL Open Font License, see `third_party/`)
- Knot paths are generated from the app's own control points and checked against public references listed in [docs/knot-sources.md](docs/knot-sources.md)
- Private repository; built with Claude Code on the "dark factory" pipeline (Claude Opus 5.5 for build / QA / release, Claude Fable 5.1 for research / design)
- Code structure and QA hooks: [AGENTS.md](AGENTS.md)
