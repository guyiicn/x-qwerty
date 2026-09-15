# guyii社交开发过程

这份文档给后续 agent 或人工接手用，记录当前项目怎么来的、为什么这样做、如何继续改和发布。

## 项目定位

`guyii社交` 是一个轻量 Android WebView 壳，用 HTML/WebView 方式包装 X/Twitter 移动 Web 站点。目标不是重写 X 客户端 API，而是在 X Web 登录态和页面能力之上，做更像 Android 客户端的原生辅助 UI。

当前包名：

```text
guyii.social.tools
```

当前 APK 发布名：

```text
guyii.so.tools.apk
```

## 目录和关键文件

```text
guyii-social-x/
  README.md
  app/src/main/AndroidManifest.xml
  app/src/main/java/guyii/social/tools/MainActivity.java
  app/src/main/assets/injected-shortcuts.js
  app/src/main/assets/error.html
  app/src/main/res/drawable/ic_launcher.xml
  app/src/main/res/values/strings.xml
```

关键点：

- `MainActivity.java`：WebView 容器、底部原生导航、发布浮动按钮、权限、下载、全屏视频、键盘事件。
- `injected-shortcuts.js`：注入到 X 页面里，隐藏 X 自带导航，处理网页内快捷键、发帖/回复框检测和发布按钮状态回传。
- `ic_launcher.xml`：当前使用微博风格红白图标，不使用 X 图标。
- `README.md`：给用户看的简短使用说明。

## UI 演进记录

最初尝试过左侧工具栏和发帖托盘，后来废弃。当前方向是“Android 客户端思路改造 WebView”：

- 不显示左侧工具栏。
- 尽量隐藏 X Web 自带导航栏，把主要入口移到底部原生栏。
- 底部常驻导航从左到右是：`TL`、`@`、`DM`、搜索、Grok、发帖。
- 不保留常驻发送按钮。发推或回复输入框有可发布内容后，右下角自动浮出 `发布` 按钮。
- 媒体按钮暂时不要做在原生栏里，交给 X 页面自己的编辑器处理。
- Web 页面的返回箭头如果被隐藏，主路径是 Android 返回键、物理键盘 `b` 或 `Alt+Left`。

当前 Grok 入口：

```text
https://x.com/i/grok
```

## 快捷键设计

项目面向有物理 Android 键盘的使用方式，不要再假设只有软键盘。

Android 全局键盘事件在 `MainActivity.dispatchKeyEvent`：

- `F5` / `Ctrl+R`：刷新
- `Alt+Left`：后退，没历史则回主 TL
- `Alt+Right`：前进
- `Ctrl+L`：搜索
- `Ctrl++` / `Ctrl+-` / `Ctrl+0`：调整 WebView 字体缩放
- `Esc`：退出全屏、关闭发帖/回复框或后退

网页内快捷键在 `injected-shortcuts.js`：

- `b`：后退，没历史则回主 TL
- `j` / `k`：逐条选择帖子
- `/`：聚焦搜索框
- `.`：刷新
- `n`：发帖
- `l`：赞当前选中/可见帖子
- `r`：回复当前选中/可见帖子
- `t`：转发当前选中/可见帖子
- `g h`：主 TL
- `g m`：@我的
- `g d`：私信
- `g s`：搜索
- `g g`：Grok

`j/k` 曾经使用 `scrollIntoView({ block: "center" })`，在 X 虚拟列表里会跳很多条。现在改为按相邻 `article` 的顶部位置对齐内容区，尽量做到一条一条移动。

## 字体和显示

默认 WebView 字体缩放是：

```text
110%
```

用户要求“字体调大一号”后改成此值。`Ctrl+0` 仍回到 `100%` 标准缩放。

底部原生导航常驻，因此 `MainActivity.positionOverlay()` 会给 WebView 设置底部 margin，避免页面内容被底栏遮住。全屏视频时隐藏所有原生浮层。

## 构建

构建命令：

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
export ANDROID_HOME=$HOME/Android/Sdk
export ANDROID_SDK_ROOT=$HOME/Android/Sdk
export PATH=/usr/lib/jvm/java-17-openjdk/bin:$HOME/Android/Sdk/platform-tools:$PATH
./gradlew assembleDebug
```

APK 输出：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 离线测试 j/k 导航

redroid 上登录不了 X，信息流相关的逻辑（`j`/`k` 选中、`Enter` 进入、自动选中第一条）在真实页面上没法验。`testing/test-timeline.html` 是一个带假 `<article>` 的本地页面，链接故意混了 `/status/1001` 和 `/status/1001/photo/1` 两种，用来确认只会点中时间戳链接。

临时启用方法（验完记得撤销）：

1. `MainActivity.onPageFinished` 的注入条件加上 `|| url.contains("test-timeline")`
2. 把该文件拷进 `app/src/main/assets/`
3. `webView.loadUrl("file:///android_asset/test-timeline.html")`

页面底部的蓝条会显示最后点中的链接，`adb shell input keyevent 66` 发 Enter、`38` 发 j、`39` 发 k。

## 打包注意

发布前一律用 `./gradlew clean assembleDebug`。增量构建产出的 APK 压缩不充分（同样的 dex 内容打出过 73KB vs clean 的 44KB），虽然能装能跑，但不该把这种包发出去。

## Docker Android 测试

用 redroid 容器跑一个 Android 环境，常用流程：

```bash
docker start redroid14 >/dev/null
export PATH=$HOME/Android/Sdk/platform-tools:$PATH
adb connect 127.0.0.1:5555
adb -s 127.0.0.1:5555 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s 127.0.0.1:5555 shell am force-stop guyii.social.tools
adb -s 127.0.0.1:5555 shell am start -W -n guyii.social.tools/.MainActivity
```

截图：

```bash
adb -s 127.0.0.1:5555 exec-out screencap -p > screen.png
```

注意：截图命令不要把其他 stdout 文本混进 PNG。

redroid 里登录不了 X，未登录会一直停在登录页；信息流相关的功能只能在真机的登录账号上验。

## 发布

APK 发布到自己的静态站点，流程记在私有 runbook 里，不在本仓库。要点：

- 每次上传后同步更新 `SHA256SUMS.txt` 和索引页
- 上传完在服务器上跑一次 `sha256sum -c SHA256SUMS.txt`
- 发布包一律用 `./gradlew clean assembleDebug` 产出

## 已知限制和后续方向

- X 页面结构经常变化，`injected-shortcuts.js` 的选择器需要按实际页面维护。
- 登录态、Grok、私信等都依赖 X Web 本身；未登录的 redroid 测试环境会被带到登录页，这是正常现象。
- 原生底部栏目前是文字/符号按钮，后续可以换成更明确的矢量图标，但要保持 `TL / @ / DM / 搜索 / Grok / 发帖` 的顺序。
- 发布按钮依赖注入脚本检测 X 的提交按钮状态，如果 X 改了 `data-testid`，需要更新 `submitButton()`。
- 如果要继续优化 `j/k`，优先在真实登录账号的信息流里观察，不要只根据未登录页判断。
