# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目性质

`guyii社交`（包名 `guyii.social.tools`）是一个极薄的 Android WebView 壳，把 X/Twitter 移动版网页包成更像原生客户端的应用。**不调用 X API**，所有数据、登录态、功能都来自 X Web 本身；本项目只做两件事：原生外壳 UI + 注入脚本改造页面。

核心约束：**目标设备是带黑莓式物理全键盘的 Android 手机** —— 没有 Ctrl、没有 Esc、没有 F 键、没有方向键。任何功能都必须有单键或前缀层的操作路径，组合键只能作为外接全键盘的补充。

两份文档要先读：

- `NAVIGATION_PLAN.md` —— 导航重构的完整施工图和各阶段落地记录，含每次踩过的坑和结论。动导航相关代码前以它为准。
- `DEVELOPMENT_PROCESS.md` —— 更早的设计决策历史、废弃过的方案、测试流程。

## 构建与测试

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk   # 需要 JDK 17
export ANDROID_HOME=$HOME/Android/Sdk            # 或本机实际 SDK 路径
./gradlew clean assembleDebug
# 产物: app/build/outputs/apk/debug/app-debug.apk
```

**发布包一律用 `clean assembleDebug`** —— 增量构建产出的 APK 压缩不充分（同样的 dex 内容打出过 73KB vs clean 的 44KB）。

没有单元测试、没有 lint、没有 CI，验证方式是装到设备或模拟器里手动跑。

```bash
adb connect 127.0.0.1:5555
adb -s 127.0.0.1:5555 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s 127.0.0.1:5555 shell am force-stop guyii.social.tools
adb -s 127.0.0.1:5555 shell am start -W -n guyii.social.tools/.MainActivity
adb -s 127.0.0.1:5555 exec-out screencap -p > screen.png   # 别让其他 stdout 混进 PNG
```

**模拟器里登录不了 X**，会一直停在登录页。信息流相关的功能（`j`/`k`、`Enter`、点赞、转发、角标、抓 handle）在那里全都验不了，不要根据登录页的表现下结论。`testing/test-timeline.html` 是为此准备的本地假信息流，用法见 `DEVELOPMENT_PROCESS.md`。

## 架构：两层，靠一个 JS 桥接

整个应用只有两个实质文件，职责严格分开：

**`app/src/main/java/guyii/social/tools/MainActivity.java`** —— 原生层。单 Activity，继承的是老 `android.app.Activity` 而非 AppCompatActivity；**项目零依赖**（`app/build.gradle.kts` 没有 `dependencies` 块，不用 AndroidX），所以不要引入需要 AndroidX 的 API。UI 全部用代码构建，**没有 layout XML**，图标用 `Path` + 自写的 `IconDrawable` 画。

**`app/src/main/assets/injected-shortcuts.js`** —— 页面层。每次 `onPageFinished` 且 URL 是 https 时被 `evaluateJavascript` 重新注入（脚本用 `window.__guyiiShortcutsInstalled` 自防重入）。负责隐藏 X 自带导航、处理网页内快捷键、把页面状态回传原生。

### 桥接口

网页 → 原生（`ShortcutBridge`，`@JavascriptInterface`）：

| 方法 | 说明 |
| --- | --- |
| `routeChanged(url, title, isRootTab)` | SPA 路由变化。底栏高亮、FAB 显隐、返回逻辑都依赖它 |
| `themeChanged(cssColor)` | 页面实际背景色，原生按亮度决定外壳配色 |
| `composerChanged(dialogOpen, canSubmit)` | **第一个参数是「是否全屏发帖对话框」，不是「有没有输入框」** |
| `badgeChanged(notif, dm)` | 未读数 |
| `profileChanged(handle)` | 当前账号 handle |
| `zoom(delta)` | 调原生 `WebSettings.setTextZoom`，`0` 表示复位 |
| `reload` / `openSettings` / `toast` | 杂项 |

原生 → 网页（`window.__guyii`）：`go(tab)`、`openItem(key)`、`open()`（进入选中帖子）、`softRefresh()`、`repost(quote)`、`compose`/`submit`/`dismiss`、`setChrome(top,bottom)`。

**改动其中一层的接口时必须同步另一层** —— 两边没有类型检查兜底。

### 导航跳转必须收敛到一个函数

同一个目的地有三个入口：底栏 Tab、抽屉条目、键盘快捷键。跳转统一走 `goTab()` / `openItem()`，**不要在各处直接写 `location.href`** —— 曾经因为 `g` 前缀层没跟上底栏的 SPA 化，同一个目的地出现过三种行为。

## 需要注意的约束

**X 的 DOM 会变。** 注入脚本大量依赖 `data-testid`。功能失效时第一怀疑对象是选择器过期。已知最脆的两处：引用转发靠菜单项文本匹配 `/quote|引用/i`（没有稳定 testid）；时间线顶部发推框靠「向上找第一个含 `toolBar` 且不含 `article` 的祖先」来定位。

**`display:none` 不影响程序化 `.click()`。** 底栏 Tab、发帖 FAB、抽屉条目全都靠点击被 CSS 隐藏的 X 原生链接来触发 SPA 路由。隐藏它们是安全的。

**edge-to-edge 是显式打开的。** `applyEdgeToEdge()` 让 API 24–36 走同一条路径，inset 全部自己算。加浮层时记得在 `positionOverlay()` 里给它算 inset —— FAB 和抽屉都因为漏算而出过 bug。

**X 的时间线是内部容器在滚，不是整页滚动。** 所以原生 `WebView.setOnScrollChangeListener` 感知不到时间线滚动，底栏的滚动隐藏靠注入脚本里**捕获阶段**的 `document` scroll 监听（`addEventListener("scroll", fn, true)`）。换滚动容器时要重置基准，否则坐标系不同会算出垃圾差值。

**`j`/`k` 不能用 `scrollIntoView`。** 在 X 的虚拟列表里会一次跳过很多条。现在是把目标 `article` 顶部对齐到 `contentTop()` 再 `scrollBy`。

**底栏布局是一组耦合的魔数**：`positionOverlay()` 的 margin、脚本里的 `contentTop()` = 78 / `contentBottom()` = innerHeight − 118、对话框的 `padding-bottom`。`setChrome()` 接口已经留好但还没下发真实数值，改底栏高度目前仍要三处一起改。

**全屏视频要藏掉所有原生浮层**，进出都要调 `positionOverlay()`。

**外链一律外跳。** `shouldOverrideUrlLoading` 只让 x.com / twitter.com / t.co 及子域和 `file:` 留在 WebView 内。

**主题跟随页面。** 抽屉里的浅色/深色是写 X 的 `night_mode` cookie（`0` 浅色 / `1` Dim / `2` 纯黑）再 reload，外壳随后自动跟上页面配色，所以壳和页面永远一致。新增控件要在 `applyPageTheme()` 里一起上色。

## 快捷键分工

- **原生 `dispatchKeyEvent`**：带修饰键的（`Alt+Enter` 发布、`Ctrl` 系列、`F5`、`Esc`）。Activity 层先于 WebView 拿到事件，消费后不会再传到页面。
- **注入脚本 keydown（capture）**：所有单键和 `g` / `z` 前缀层。

**输入框聚焦时单键必须全部让路**（`editable()` 判断），加新单键时保持这个判断。

完整清单见 `README.md`，App 内也有一份（抽屉 → 快捷键一览），两处和代码要一起改。
