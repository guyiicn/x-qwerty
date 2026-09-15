# guyii社交 导航重构计划

这份文档是导航重构的施工图。背景和历史决策见 `DEVELOPMENT_PROCESS.md`，代码约束见 `CLAUDE.md`。

## 已定方向

- 参考真实 X 客户端的导航结构重做，但**底栏保留现在的悬浮 pill 形态**，不改成全宽 bar。
- 抽屉用**混合方案**：原生画外壳和条目，点击走 X 的真实链接（SPA 路由），头像/昵称/计数从页面抓取回传。
- 键盘只考虑**黑莓式 Android 物理全键盘**（KEY2 类布局）：没有 Ctrl、没有 Esc、没有 F 键、没有方向键。现有快捷键全部保留，但每个组合键都要补一条单键或前缀层路径。
- P0 到 P6 一次做完。

## 现状的 6 个问题

### 1. 网页顶部被状态栏吃掉（「返回按钮被遮住」的根因）

`app/build.gradle.kts:12` 是 `targetSdk = 36`，Android 15+ 对 SDK 35+ 强制 edge-to-edge。而 `MainActivity.installInsetsListener()` 只算了 bottom：

```java
systemBottomInset = ...getInsets(systemBars() | ime()).bottom;
```

顶部完全没处理，`root` 也没有 `fitsSystemWindows`。网页最上面 24–48dp 画在状态栏底下，X 的返回箭头正好在那个位置，看得见点不到。

### 2. 网页自带顶栏被 CSS 整个删了

`injected-shortcuts.js:7` 把 `header[role="banner"]` 设成 `display:none`。二级页的返回箭头、标题、头像入口是一起没的，触屏上只剩系统返回键。

### 3. 返回键永远退不出 App

`MainActivity.goBackOrHome()`：

```java
if (webView.canGoBack()) webView.goBack();
else webView.loadUrl(HOME_URL);   // 历史又长出来了
```

`canGoBack` 为 false 时又塞一条历史进去，于是永远为 true。只能在首页和上一页之间打转。

### 4. 切 Tab 是整页重载

底栏用 `webView.loadUrl()`（`MainActivity.java:144-149`），脚本用 `location.href`（`injected-shortcuts.js:180-184`），都绕开了 X 的 SPA 路由：丢滚动位置、丢时间线状态、每切一次多一条历史。

### 5. 没有抽屉，侧边入口全部失联

`injected-shortcuts.js:13` 把 `DashButton_ProfileIcon_Link` 也隐藏了。个人资料、书签、列表、社区、已喜欢、草稿、设置**触屏零入口**，只有 `g p` 一条键盘路径。

### 6. 底栏没有当前位置，也没有未读

六个纯文字按钮无选中态、无角标、无图标；`⌕` 在不同字体下渲染不一致；`@` 只去 `/notifications/mentions`，漏掉赞和关注通知。

### 另外两个潜在 bug

- `hideFullscreen()` 没有重新调用 `positionOverlay()`，退全屏后 WebView 底部 margin 可能停在 0，内容被底栏压住。
- `div[role="dialog"] { padding-bottom: 86px }`（`injected-shortcuts.js:36`）作用于所有对话框，图片查看器和菜单都被塞进多余留白。

## 布局常量统一

现在 `positionOverlay()` 的 margin、JS 的 `contentTop()=78` / `contentBottom()=innerHeight-118`、dialog 的 `padding-bottom:86px` 是三处各写各的魔数。改成原生是唯一真源：

```text
TOP_BAR = 52dp   PILL = 58dp   PILL_MARGIN = 12dp   FAB_GAP = 12dp

webView topMargin    = statusInset + TOP_BAR
webView bottomMargin = navInset + PILL_MARGIN + PILL + 8dp
```

原生算完通过桥下发 `setChrome(topDp, bottomDp)`，脚本据此算 `contentTop` / `contentBottom` 和 dialog padding。以后改底栏高度只动一处。

需真机验证：WebView 默认 viewport 下 1 CSS px 是否等于 1 dp。不等的话在脚本里按 `devicePixelRatio` 校准。

## 桥接口最终形态

网页 → 原生（`ShortcutBridge`）：

| 方法 | 说明 |
| --- | --- |
| `routeChanged(url, title, isRootTab)` | 新增，整个重构的地基 |
| `profileChanged(json)` | 新增，`{avatarUrl, name, handle, following, followers}` |
| `badgeChanged(notif, dm)` | 新增 |
| `themeChanged(cssColor)` | 新增，回传页面实际背景色，原生据此切浅色/深色外壳 |
| `composerChanged(visible, canSubmit)` | 保留 |
| `reload` / `openSettings` / `toast` | 保留 |

原生 → 网页（`window.__guyii`）：

| 方法 | 说明 |
| --- | --- |
| `go(tab)` | 改写，优先点 X 自己的 anchor 走 SPA 路由 |
| `openItem(key)` | 新增，抽屉条目，同样 anchor 优先 |
| `setChrome(top, bottom)` | 新增，下发原生占用高度 |
| `scrape()` | 新增，主动触发一次头像/计数抓取 |
| `compose` / `submit` / `dismiss` / `back` | 保留 |

改动其中一层的接口必须同步另一层，两边没有类型检查兜底。

## P0 · 止血（已完成，2026-09-15）

实际落地的改动：

1. **显式打开 edge-to-edge**（新增 `applyEdgeToEdge()`）。`targetSdk 36` 只在 Android 15+ 上强制 edge-to-edge，低版本行为不同；显式打开让 API 24–36 走同一条路径，inset 计算不会因设备版本而双重计算或漏算。
2. `installInsetsListener()` 同时取 `systemBars().top`，新增 `updateTopMargin()`，`positionOverlay()` 给 WebView 和 progressBar 设 topMargin。
3. `goBackOrHome()` 重写：有历史就 `goBack()`，没历史就 `confirmExit()`（2 秒内再按一次 `finish()`，Toast「再按一次退出」）。不再 `loadUrl(HOME_URL)` 重新种历史。
4. `onPageFinished` 里首次到达首页时 `clearHistory()` 一次，清掉 `x.com -> /home` 重定向留下的那条历史，否则返回键会在两者之间打转。
5. `onShowCustomView` / `hideFullscreen()` 都补调 `positionOverlay()`。
6. `div[role="dialog"]` 的 86px 底部留白改成只作用于带 `data-guyii-composer` 标记的发帖框；标记由 `tagComposer()` 在 `notifyComposer()` 里维护。
7. **从 P5 提前**：`header[role="banner"]` 和 `DashButton_ProfileIcon_Link` 不再隐藏。不提前的话，inset 修完触屏上依然没有任何返回控件。等 P2 的原生顶栏做好后再决定是否重新隐藏。

与原计划的两处偏差：

- 原计划的返回链里有「不在首页 Tab 就切回首页」。P0 阶段没有原生 Tab 状态，用 URL 判断会退化成「回首页 → 历史又长出来 → 返回 → 回上一页 → 没历史 → 又回首页」的两步循环。所以 P0 只保留「有历史就退，没历史就双击退出」，这条规则一定收敛。切回首页那一步挪到 P3，那时它是原生 Tab 切换而不是 history push。
- `positionOverlay()` 里给 `updateTopMargin()` 加了 `params.topMargin == margin` 的提前返回，避免 inset 回调频繁触发 `setLayoutParams`。

redroid14（API 34、720x1280、density 320）上实测：

```text
状态栏          [0,0][720,48]
WebView         [0,48][720,1028]     顶边 = 状态栏高度，无双重计算
导航栏          [0,1184][720,1280]
底栏 pill       [44,1044][676,1160]  = 1280 - 96(导航栏) - 24(12dp)
```

**1 CSS px = 1 dp 已确认**（density 320，WebView viewport 宽 360 CSS px 对应 720 物理 px）。P1 的 `setChrome` 可以直接传 dp 值，不需要按 `devicePixelRatio` 校准，但换设备后值得复查。

返回键实测：启动页按一次弹「再按一次退出」且不跳转，2 秒内再按一次退出 App。登录流程页的 `←` 已完整露在状态栏下方，点击可用。

未登录环境验不了的部分：发帖框 padding 收窄、发帖框和图片查看器的实际版式。需要真机登录账号复查。

### P0.5 · 左侧导航栏可以提前隐藏（0.2.1，真机验证过）

把 `header[role="banner"]` 重新加回隐藏列表后，**二级页左上角的返回箭头仍然在**。

结论：**返回箭头不在 `header[role="banner"]` 里**，而在 primary column 自己的顶部栏。原来触屏点不到返回，根因只有一条 —— 被状态栏盖住（P0 的 inset 问题），和 header 被隐藏无关。

所以 P5 里「不再删整个 header」那条**作废**：header 保持隐藏，左侧导航栏不会再出现，也不需要等 P2 的原生顶栏接管。P2 的原生顶栏改为纯增益（统一标题、头像入口、不受 X 改版影响），而不是恢复返回能力的必要条件。

## P1 · SPA 路由桥（已完成，0.2.2）

落地内容：注入脚本包了 `pushState` / `replaceState` 并监听 `popstate`，配合 1.5s 轮询回传 `routeChanged(url, title, isRootTab)`；同时读 `document.body` 计算背景色回传 `themeChanged(cssColor)`，原生按亮度切换外壳配色（根背景、pill 底色与描边、按钮文字、状态栏/导航栏颜色和图标明暗）。白底上 pill 会和背景糊在一起，所以浅色时 pill 底色压到页面色的 94% 并把描边提到 20% 黑。

redroid 实测：`routeChanged root=true url=https://x.com/`、`themeChanged css=rgb(0, 0, 0) parsed=#ff000000` 都按预期触发；临时强制白底验证过浅色分支的实际观感，验完已移除临时代码。`routeIsRootTab` 目前只是存下来，消费方在 P2/P3。

**`setChrome` 只留了接口没下发数值**：原生底栏是用 margin 把 WebView 顶开的，和页面不重叠，真按"占用高度"发 0 会改掉调过的 `j`/`k` 落位。等 P3 底栏定稿再接，`contentTop` / `contentBottom` 暂时保持 78 / 118。

原始计划：


X 是单页应用，客户端路由不触发 `onPageFinished`，原生现在完全不知道用户在哪一页。注入脚本加：

```js
['pushState', 'replaceState'].forEach(patch);   // 包一层，调完派发事件
window.addEventListener('popstate', report);
```

`report()` 解析 `location.pathname` 判断是不是根 Tab（`/home` `/explore` `/i/grok` `/notifications` `/messages`），连同标题回传 `routeChanged()`。MutationObserver 兜底，X 有时不走 history API。

脚本每次注入都要重新应用 chrome 值，别让 `__guyiiShortcutsInstalled` 的防重入把 `setChrome` 吞掉。

## 主题开关（0.5.0）

原先只做「外壳跟随页面」，等于把切主题这件事推给 X 自己的设置页。用户要的是 App 里能直接切。

做法：**X 把主题存在 `night_mode` cookie 里**（`0` 浅色 / `1` Dim / `2` 纯黑）。原生用 `CookieManager.setCookie()` 写这个 cookie 再 `webView.reload()`，页面就换主题；外壳原有的「跟随页面配色」逻辑随即自动跟上，所以壳和页面永远一致，不会出现壳白页面黑。

- 抽屉底部三个 chip：`跟随` / `浅色` / `深色`，选择存 SharedPreferences，启动时重新写 cookie。
- `跟随` 不写 cookie，沿用 X 账号自己的设置（Dim 也走这条）。
- redroid 实测通过：点「浅色」后页面和外壳一起变白、状态栏图标转深色，force-stop 重启后保持。

同一版还修了抽屉内容超过一屏的问题：条目区放进 `ScrollView`，主题行和字号行固定在底部。

## 主题：外壳跟随页面（0.2.2 的原始做法，仍是 `跟随` 模式的实现）

新增需求：要浅色主题。

做法不是给外壳单独做一套开关，而是**让外壳跟随 X 页面的实际配色**。注入脚本读 `document.body` 的计算背景色回传 `themeChanged()`，原生按亮度决定前景色，并直接用页面背景色作为根背景、状态栏和底栏 pill 的底色。

这样 X 的三套主题自动都对：

| X 主题 | 页面背景 | 外壳 |
| --- | --- | --- |
| Light | `rgb(255,255,255)` | 白底 + 深色文字 + 深色状态栏图标 |
| Dim | `rgb(21,32,43)` | 同色底 + 浅色文字 |
| Lights out | `rgb(0,0,0)` | 纯黑 + 浅色文字 |

用户在 X 自己的「设置 → 显示」里切主题，外壳跟着变，不需要两处各切一次。

因此**所有原生控件从一开始就用 token 建**，不写死颜色：P2 的顶栏、P3 的 pill 和 FAB、P4 的抽屉都从同一张表取色，避免做完再返工改一遍。

待办：`error.html` 还是写死的深色，要跟着一起做；抽屉里留一个「跟随页面 / 强制浅色 / 强制深色」的覆盖开关（P4）。

## P2 · 原生顶栏（52dp）

替代被 CSS 删掉的网页 header，两态由 `routeChanged` 驱动：

- 根 Tab 态：左头像（开抽屉）/ 中标题 / 右时间线切换
- 二级页态：`←` 返回 / 标题 / `⋯`

返回箭头从此由原生提供，不再受 X 改版影响。另加右滑边缘手势：在 `root` 上做 touch 判定，起点 < 20dp 且横向位移 > 80dp。

## P3 · 悬浮 pill 底栏 + FAB（已完成，0.3.0）

落地内容：pill 从 6 个纯文字按钮换成 5 个图标 Tab（首页/搜索/Grok/通知/私信），带文字标签、选中态（强调色 + 圆角底块）和未读角标；发帖挪到右下 FAB。图标用 `Path` + 自写的 `IconDrawable` 画（24x24 坐标按 bounds 缩放描边），**没有引入 AndroidX**。

- 切 Tab 走 `__guyii.go(key)`：优先 `.click()` X 自己的导航 anchor 触发 SPA 路由，找不到才 `location.href`。
- 高亮由 `routeChanged` 回来的 URL 驱动（`tabIndexForUrl`），非根页面全部取消高亮。
- 角标从 X 导航链接的 `aria-label` 抠数字，经 `badgeChanged(notif, dm)` 回传。
- 通知 Tab 长按进 mentions。
- 发帖 FAB 只在根 Tab 显示，二级页隐藏，避免挡住页面内容。
- 发帖框打开时 pill 和 FAB 一起让位，只留「发布」按钮 —— 原来的 `publishButton` 保留，和 FAB 互斥出现，没有按计划删掉（它是已验证好用的能力，删了触屏发帖就只能靠 X 自己的按钮）。

redroid 实测：5 个 Tab 渲染正常；点「通知」触发跳转且到达非根页面后高亮自动清空；FAB 在二级页隐藏。

途中修掉一个自己引入的 bug：`positionOverlay()` 漏给 FAB 加导航栏 inset，导致 FAB 压住「私信」Tab。

未登录环境验不了：SPA anchor 点击是否真的走客户端路由（现在未登录一律跳登录墙，看不出区别）、角标数字、长按 mentions。

原始计划：


- pill 内容 6 → 5：首页 / 搜索 / Grok / 通知 / 私信，发帖挪到 FAB。
- FAB 落在原来「发布」按钮的位置（底部 82dp），`publishButton` 撤销。
- 图标用 `Path` 代码画，维持零依赖，不引 AndroidX。
- 选中态：48×44 圆角块 `rgba(29,155,240,0.16)`，图标和文字转 `#1d9bf0`。
- `@` 改去 `/notifications`（全部），长按再进 mentions。
- 未读角标数据源优先用 `document.title` 的 `(12) ` 前缀，比 `data-testid` 稳得多；抓不到降级为小红点。
- 切 Tab 不再整页重载：

```js
const a = document.querySelector(TAB_ANCHOR[tab]);
if (a) { a.click(); return; }        // SPA 路由，保滚动位置和时间线状态
location.href = FALLBACK[tab];        // 退路
```

坑：这些 anchor 现在被 `pointer-events:none` 隐藏（`injected-shortcuts.js:7-21`）。程序化 `.click()` 理论上不受影响，但建议改成离屏隐藏（`position:absolute;left:-9999px`）更保险，要在真机上确认。

## 已修回归 · 详情页底栏消失（0.3.2）

现象：进推文详情页后底栏整条消失，回不了 TL。

原因：X 详情页顶部的**内联回复框**用的也是 `tweetTextarea_0`，而旧的 `composerRoot()` 在拿不到 `closest('div[role="dialog"]')` 时会回退成 `document`，于是 `composerOpen()` 在每个详情页都恒为 true。这个误判一直存在，但以前 `composerVisible` 只参与「发布」按钮的显隐（还要叠加 `canSubmit`），所以没暴露；P3 拿它去控制整条底栏，问题才浮出来。

修法：把「全屏发帖对话框」和「页面内联回复框」拆成两个信号。

- `composerDialog()` 只在编辑器确实位于 `div[role="dialog"]` 内时返回该对话框，否则 null。
- `composerChanged(dialogOpen, canSubmit)` 第一个参数改为「是否全屏对话框」，第二个仍是「有没有可发布内容」，内联回复也算。
- 原生侧：底栏和 FAB 只在 `composerDialogOpen` 时让位；「发布」按钮改为只看 `canSubmit`（内联回复照样能浮出）；返回键 / `Esc` 的 dismiss 只在对话框打开时触发；`Ctrl/Alt+Enter` 改为只看 `canSubmit`。

教训：一个长期存在但无害的误判，被复用到新场景时会立刻变成 bug。桥上传的语义要写清楚是「对话框」还是「有内容」，不要用一个模糊的 "visible"。

## 已修 · 页面自带发推入口重复（0.4.1 / 0.5.0）

原生 FAB 加上之后，X 自己的两个发推入口都成了重复：

- **0.4.1 隐藏浮动按钮**：`a[href="/compose/post"]`、`a[href="/compose/tweet"]`、`[data-testid="FloatingActionButton"]`、`[data-testid="SideNav_NewTweet_Button"]`。
- **0.5.0 隐藏时间线顶部的发推框**：这个才是用户说的「每个页面都有一个发推框」。纯 CSS 选不中（要选的是包住输入框的整块），改成 JS 找：从 `tweetTextarea_0` 往上走，第一个「包含 `toolBar` 但不包含 `article`」的祖先就是那一块，打上 `data-guyii-inline-composer` 由 CSS 隐藏，每次 tick 重新标记以对抗 React 重渲染。**只在 `/` 和 `/home` 生效** —— 详情页那个长得一样的框是回复框，隐藏了就没法回帖。

注意：`window.__guyii.compose()` 正是靠 `.click()` 这些元素来发帖的。`display:none` 不影响程序化 `.click()`，所以隐藏之后原生 FAB 照常工作 —— 和底栏 Tab 点 anchor 是同一个道理。



X 移动版每个页面右下角都有自己的浮动发推按钮，和 P3 加的原生 FAB 撞在一起，同一个位置两个蓝色按钮。

修法：把 `a[href="/compose/post"]`、`a[href="/compose/tweet"]`、`[data-testid="FloatingActionButton"]`、`[data-testid="SideNav_NewTweet_Button"]` 加进隐藏列表。

注意：`window.__guyii.compose()` 正是靠 `.click()` 这些元素来发帖的。`display:none` 不影响程序化 `.click()`，所以隐藏之后原生 FAB 照常工作 —— 和底栏 Tab 点 anchor 是同一个道理。

## 快捷键清单重排（0.6.2）

用户反馈：「所有 ctrl 都不存在，f5 这种早就换成了 `.`，为什么还有」「esc 也是不存在」。

问题在于清单的**排序**而不是内容：项目从一开始的约束就是「只考虑类似黑莓的物理键盘」，但 0.6.1 的清单把 `Ctrl+Enter` / `F5` / `Alt+方向键` / `Ctrl+L` / `Ctrl+±0` / `Esc` 整整一屏排在最前面 —— 而这些在目标设备上一个都按不出来。等于开屏就是噪音。

重排后的顺序：单键 → 发布与关闭（`Alt+Enter` + 返回键）→ `g` 前缀层 → `z` 前缀层 → 触屏 → 「只有外接全键盘能按」。最后一段每条都直接写明本机的等效按法（`Ctrl+Enter` → `Alt+Enter`、`F5` → `.`、`Esc` → 返回键 …）。

组合键在代码里全部保留不动，只是在清单里降级。核查过：**没有任何功能是只能靠组合键才能用的**，每一条都有单键或前缀层的等效路径。

教训：目标设备的约束不只影响做什么功能，也影响**信息怎么排**。给黑莓用户看的清单，开头就不该是 Ctrl。

## 快捷键一览（0.6.1）

快捷键表原来只在 `README.md` 里，手机上根本看不到 —— 用户问「你说的快捷方式说明在什么地方」就是去找了没找到。

做成抽屉里的一个全屏原生页：`SHORTCUTS` 二维数组（`"#"` 开头的行是分组标题）渲染成「等宽强调色按键 + 说明」两列，放进 `ScrollView`，顶部是标题和 ✕。主题、insets 和抽屉共用同一套处理。返回键 / `Esc` 的优先级链里排在抽屉之前。

内容除了键盘还列了三条触屏手势（重点 Tab 回顶刷新、长按通知进 @我的、长按首页开抽屉）—— 这些不写出来没人会知道。

## j/k 导航补全（0.7.0）

用户提的三条：

1. **`Enter` 进入选中的帖子**。原来没做。`openSelected()` 在选中的 `article` 里找 `a[href*="/status/"]` 且 href 以 `/status/<数字>` 结尾的那个 —— 这个结尾限定很关键，否则会点中 `/status/1001/photo/1` 变成打开图片。
2. **`f` 是什么**。`f` = `history.forward()`，和 `b` 成对，跟焦点帖子无关。用户测下来「没反应」是因为没先后退过就没有前进历史。清单里补了说明。
3. **列表页自动选中第一条**。`ensureSelection()` 在没有选中项时标记第一个可见 `article`，挂在 1.5s 的 tick 上；路由变化和软刷新时先 `mark(null)` 清空，让它重新选。打开页面就能直接 `j`/`k`。

顺带修了 `nearestArticleIndex()`：原来只要存在选中项就返回它的下标，哪怕它已经滚出视野 —— 于是滚动一段后按 `j` 会跳回老位置。改成只在选中项仍然可见时才沿用。

验证方式：搭了 `testing/test-timeline.html` 本地假信息流（见 `DEVELOPMENT_PROCESS.md`），确认了自动选中、`Enter` 点中时间戳链接而非图片链接、`j` 后 `Enter` 点中第二条。

## 转发与软刷新（0.6.0）

**传统转发**：原来的 `t` 只是 `clickByLabel(current(), ["repost","retweet"])`，而 X 的转发图标点开是一个菜单（转发 / 引用），所以 `t` 只把菜单打开，等于半个快捷键。现在 `repost(quote)` 点完图标后用 `waitFor` 轮询等菜单出现（上限 1.5s），再点 `retweetConfirm` / `unretweetConfirm` 完成。已转发的帖子按钮是 `unretweet`，同一条流程自动变成取消转发。

`q` 走同一个函数但取菜单里文本匹配 `/quote|引用/i` 的那一项 —— 引用项没有稳定的 `data-testid`，只能按文本找。

**误触保护**：没做可点的 Snackbar。转发本身可切换，所以转发成功后 Toast 提示「已转发 · 再按 t 撤销」，再按一次 `t` 就取消，比实现一个可点浮层简单也更可靠。

**软刷新**：`.` / `F5` / `Ctrl+R` 都是整页重载，慢且丢状态。真正的「刷新当前视图」等价于对同一路由再导航一次 —— X 的 SPA 会回顶并拉新帖。`softRefresh()` 用 `TAB_FOR_PATH` 把当前 pathname 映回 tab key 再调 `goTab()`，两个入口：

- 触屏：再点一次**已选中**的底栏 Tab（原生 `switchTab` 里判 `reselect`）
- 键盘：`u`

`.` 保留整页重载不动 —— 黑莓按不出 `F5` / `Ctrl+R`，不能把唯一的硬刷新路径也拿走。

## P4 · 混合抽屉（已完成，0.4.0）

原生画外壳，条目跳转优先走 X 的真实链接。没有引入 AndroidX 的 DrawerLayout。

- **入口**：底栏第 6 格 `我`（0.4.1 追加，见下）。另外还有左边缘右滑和长按 `首页` 两条。返回键 / `Esc` 优先关抽屉。

  0.4.1 修正：只给手势入口是错的。用户反馈「我就没见到抽屉」—— **手势导航的设备上，屏幕左边缘属于系统返回手势，边缘右滑根本到不了 App**；redroid 是三键导航所以本地测得通，掩盖了这个问题。长按首页又完全没有提示。所以补了底栏第 6 格 `我`（人形图标），单格宽度从 56dp 收到 50dp 让 6 格放得下（6×52 + padding = 324dp，360dp 宽的屏幕装得下）。教训：**手势入口不能作为唯一入口**，尤其当它和系统手势抢同一块区域。
- **面板**：304dp，scrim + `translationX` 动画。`root` 从 `FrameLayout` 换成自写的 `EdgeSwipeLayout`，在 `onInterceptTouchEvent` 里判定「起点 < 20dp 且横移 > 48dp 且横向位移大于纵向 1.5 倍」才拦截，拦到后 WebView 自动收到 ACTION_CANCEL。
- **条目**：个人资料 / 书签 / 列表 / 社区 / 已喜欢 / 草稿 / 设置与隐私。`openItem(key)` 三级回退：先点页面上真实的 anchor（走 SPA），其次用确定的固定地址（书签、草稿、设置），最后才用抓到的 handle 拼 `/<handle>/lists` 这类地址；都取不到就什么都不做。
- **头部**：显示 `@handle`（从 `AppTabBar_Profile_Link` 的 href 抓，经 `profileChanged` 回传）+ 首字母占位圆。**没有加载真实头像图片** —— 那需要自己写 HttpURLConnection + Bitmap 圆形裁剪，在未登录环境完全验不了，先留占位。
- **底部**：字号 `− / 读数 / ＋`，和 `setTextZoom` 共用一套状态，读数实时同步。

redroid 实测：左边缘右滑开、返回键关、长按首页开、字号 ＋ 同时更新 toast 和抽屉读数（110% → 120%）、点「设置与隐私」关抽屉并跳转且 Tab 高亮自动清空。

途中修掉一个一直存在但没暴露的 bug：`applyPageTheme()` 开头的 `if (color == themeColor) return;` 在 `themeColor` 初值恰好等于页面色（都是黑）时会跳过首次上色，导致抽屉文字一直是系统默认灰。加了 `themeApplied` 标志。

未登录环境验不了：真实 handle 抓取、各条目在登录态下的落点是否正确（尤其「社区」和「列表」这两个没有确定地址的）。

原始计划：


- 原生画：scrim + 304dp 面板 + 滑动动画 + 左边缘拖拽，手写在 `root` 这个 FrameLayout 里。
- 条目：个人资料 / 书签 / 列表 / 社区 / 已喜欢 / 草稿 / 设置与隐私 / 快捷键一览。
- 点击走 `openItem(key)`，优先点 X 的真实链接，失败才 `location.href`。
- 头部用户信息由脚本抓：头像 `[data-testid="DashButton_ProfileIcon_Link"] img`，handle 从 `AppTabBar_Profile_Link` 的 href 取。**抓不到降级成纯图标列表**，不能因为抓取失败让抽屉不可用。
- 底部放字号控件 `Aa − / 110% / ＋`，承接黑莓按不出来的 Ctrl±。

## P5 · 注入脚本收敛

- ~~不再删整个 `header[role="banner"]`~~ —— 已在 P0.5 推翻，header 保持隐藏。
- `contentTop` / `contentBottom` 改由 `setChrome` 下发。
- `submitButton()` 增加选择器兜底链，X 改 `data-testid` 时只改一处。

## P6 · 键盘层（已完成，0.3.1；0.5.1 补齐一致性）

0.5.1 的修正：整理快捷键清单时发现 `g` 前缀层和底栏/抽屉三套入口行为不一致 ——

- `g h/m/d/s/g` 还在用 `location.href` 整页跳，而底栏 Tab 早已改成点 anchor 走 SPA。改为统一调 `goTab()`。
- `g s` 去 `/search`，底栏「搜索」去 `/explore`。统一到 `explore`。
- `g p` 去的是 `/settings/profile`（设置页），不是个人资料。改为 `openItem("profile")`，和抽屉同一条路径。
- 顺手补了 `g n` 通知、`g k` 已喜欢、`g c` 社区，让前缀层覆盖抽屉的全部条目。

教训：同一个目的地有三个入口（底栏、抽屉、键盘）时，跳转必须收敛到同一个函数，否则改了一处另外两处会悄悄落后。



新增：`f` 前进、`z` 前缀层管字号（`z k` / `z j` / `z 0`）、`空格` / `Shift+空格` 翻页、`g b` 书签、`g l` 列表、`Alt+Enter` 发布。原有的 Ctrl 组合和 `Esc` 全部保留，留给外接蓝牙全键盘。

实现要点：

- 前缀状态从写死的 `g` 改成通用的 `setPrefix(key)`，`g` 和 `z` 共用一套超时清除。
- 字号不在网页层做，走新增的 `AndroidShortcut.zoom(delta)` 调原生 `WebSettings.setTextZoom`，`delta = 0` 表示复位 100%。
- `Alt+Enter` 在原生 `dispatchKeyEvent` 里和 `Ctrl+Enter` 合并成一个分支，只在 `composerVisible` 时生效；Activity 层先于 WebView 拿到事件并消费，所以不会和网页层重复触发。
- `g l` 列表没有稳定的 `/i/lists` 路径，改成先点页面上 `href$="/lists"` 的链接，取不到就从 `AppTabBar_Profile_Link` 的 href 推出 handle 拼 `/<handle>/lists`，再取不到就什么都不做（不乱跳）。

redroid 实测：`z k` 弹「Zoom 120%」且页面字号可见变大，`z 0` 回到 100%；`b` 从登录墙退回落地页、`f` 再前进回去，且回到根页面时 Tab 高亮和 FAB 自动恢复。测试中误点到输入框，`b` 被当作文字打进去了 —— 顺带验证了 `editable()` 让路判断是对的。

未验证：`空格` 翻页（未登录落地页不滚动，看不出效果）；**`Alt+Enter` 依赖黑莓 alt 键是否真带 `META_ALT_ON`，必须真机确认**，不带的话这条作废，发布只剩右下角按钮。

原始计划：


保留全部现有快捷键（留给外接蓝牙全键盘），给每个组合键补一条黑莓可用的路径。

新增：

| 键 | 作用 |
| --- | --- |
| `f` | 前进，补 `Alt+→` |
| `z` 前缀层 | `z k` 字号＋ / `z j` 字号− / `z 0` 复位 |
| `space` / `⇧+space` | 翻页 |
| `g b` / `g l` | 书签 / 列表 |
| `alt + ↵` | 发布，补 `Ctrl+Enter` |

组合键在黑莓键盘上的替代关系：

| 现有 | 黑莓可用 | 替代 |
| --- | --- | --- |
| `F5` / `Ctrl+R` | 否 | `.`（已有） |
| `Alt+←` / `Alt+→` | 否 | `b` 后退 / `f` 前进 |
| `Ctrl+L` | 否 | `/` 聚焦 · `g s` 跳转 |
| `Ctrl+±` / `Ctrl+0` | 否 | `z k` / `z j` / `z 0` · 抽屉 Aa |
| `Ctrl+Enter` | 否 | `alt+↵`（待验证）· 顶栏按钮 |
| `Esc` | 否 | 系统返回键（唯一可靠） |

`editable()` 的让路判断必须保留。黑莓用户打字多，单键不能在输入框里触发。

必须真机确认：黑莓的 `alt` 在 Android 里是否真带 `META_ALT_ON`，还是只做本地符号输入。如果不带，`alt+↵` 作废，发布只剩顶栏按钮，`Esc` 类操作只能靠系统返回键。

## 验证计划

redroid 未登录只能验 P0 的 inset 和返回退出，其余全部依赖登录态的信息流和侧栏，必须在真实账号上验。

1. P0 后装 redroid，看顶部有没有让出状态栏、返回能不能退出 App。
2. P1 之后临时打开 `WebView.setWebContentsDebuggingEnabled(true)`，用 chrome://inspect 确认 `routeChanged` 在 SPA 跳转时有触发。
3. P3 的 anchor 点击、P4 的抓取，在真机登录账号的信息流里逐个确认。

## 主要风险

- X 改 DOM：P3 / P4 依赖 anchor 和抓取，所有抓取路径都要有降级，不能让功能整个失效。
- `.click()` 被 X 的事件委托拦截：如果 SPA 路由点不动，退回 `location.href`，切 Tab 重载的老问题就还在。
- dp / CSS px 换算：`setChrome` 下发的值算错的话，`j` / `k` 落位和 dialog padding 会整体偏移。
