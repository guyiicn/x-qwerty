(function () {
  if (window.__guyiiShortcutsInstalled) return;
  window.__guyiiShortcutsInstalled = true;

  const css = document.createElement("style");
  css.textContent = `
    header[role="banner"],
    nav[aria-label="Primary"],
    nav[aria-label="Primary navigation"],
    div[data-testid="AppTabBar_Home_Link"],
    div[data-testid="BottomBar"],
    div[data-testid="sidebarColumn"],
    a[data-testid="AppTabBar_Home_Link"],
    a[data-testid="AppTabBar_Search_Link"],
    a[data-testid="AppTabBar_Notifications_Link"],
    a[data-testid="AppTabBar_DirectMessage_Link"],
    a[href="/compose/post"],
    a[href="/compose/tweet"],
    div[data-testid="FloatingActionButton"],
    a[data-testid="FloatingActionButton"],
    [data-testid="SideNav_NewTweet_Button"] {
      display: none !important;
      visibility: hidden !important;
      pointer-events: none !important;
    }

    main[role="main"] {
      margin-left: 0 !important;
      max-width: none !important;
      width: 100vw !important;
    }

    div[data-testid="primaryColumn"] {
      border-left: 0 !important;
      border-right: 0 !important;
      max-width: none !important;
      width: 100vw !important;
    }

    [data-guyii-inline-composer] {
      display: none !important;
    }

    body {
      padding-bottom: var(--guyii-chrome-bottom, 0px) !important;
    }

    div[role="dialog"][data-guyii-composer] {
      padding-bottom: max(var(--guyii-chrome-bottom, 86px), env(safe-area-inset-bottom)) !important;
    }

    div[data-testid="toolBar"] {
      padding-bottom: 8px !important;
    }
  `;
  document.documentElement.appendChild(css);

  const editable = (el) => {
    if (!el) return false;
    const tag = (el.tagName || "").toLowerCase();
    return tag === "input" || tag === "textarea" || tag === "select" || el.isContentEditable;
  };

  let prefix = "";
  const setPrefix = (key) => {
    prefix = key;
    setTimeout(() => { if (prefix === key) prefix = ""; }, 1200);
  };
  let selected = -1;
  let chromeTop = 78;
  let chromeBottom = 118;
  const contentTop = () => chromeTop;
  const contentBottom = () => window.innerHeight - chromeBottom;
  const applyChrome = (top, bottom) => {
    chromeTop = Number(top) || 0;
    chromeBottom = Number(bottom) || 0;
    document.documentElement.style.setProperty("--guyii-chrome-bottom", chromeBottom + "px");
  };
  const scrollTop = () => window.scrollY || document.documentElement.scrollTop || 0;

  // X 的时间线是内部容器在滚，原生的 WebView.setOnScrollChangeListener 完全收不到。
  // 用捕获阶段监听 document —— scroll 不冒泡，但捕获阶段能抓到任意元素的滚动。
  let lastBarState = null;
  let scrollEvents = 0;
  let lastScrollDesc = "-";
  let lastScrollPos = 0;
  let upAccum = 0;
  // 页面上可能有多个元素各自在滚，必须按元素分别记位置，
  // 用单个变量存"上一个滚动目标"会导致每次事件都在重置基准，永远算不出差值。
  const scrollPositions = new WeakMap();
  const reportBar = (show) => {
    if (show === lastBarState) return;
    lastBarState = show;
    try {
      if (window.AndroidShortcut && AndroidShortcut.barVisible) AndroidShortcut.barVisible(show);
    } catch (e) {}
  };
  const resetBarTracking = () => {
    lastBarState = null;
    upAccum = 0;
    reportBar(true);
  };
  const positionOf = (target) => {
    if (!target || target === document || target === window) {
      return window.scrollY || document.documentElement.scrollTop || 0;
    }
    return typeof target.scrollTop === "number" ? target.scrollTop : 0;
  };
  const describe = (target) => {
    if (!target || target === document) return "document";
    if (target === window) return "window";
    const tag = (target.tagName || "?").toLowerCase();
    const cls = (typeof target.className === "string" ? target.className : "").trim().split(/\s+/)[0] || "";
    return cls ? tag + "." + cls.slice(0, 14) : tag;
  };
  document.addEventListener("scroll", (event) => {
    const target = event.target;
    const pos = positionOf(target);
    const prev = scrollPositions.get(target);
    scrollPositions.set(target, pos);
    scrollEvents++;
    lastScrollDesc = describe(target);
    lastScrollPos = pos;
    if (prev === undefined) return;
    const delta = pos - prev;
    if (delta === 0) return;
    if (pos <= 8) {
      upAccum = 0;
      reportBar(true);
      return;
    }
    if (delta > 0) {
      upAccum = 0;
      reportBar(false);
    } else {
      upAccum -= delta;
      if (upAccum > 24) reportBar(true);
    }
  }, true);
  const scrollDebug = () =>
    "滚动事件 " + scrollEvents + " · " + lastScrollDesc + " · pos " + Math.round(lastScrollPos)
      + " · 底栏 " + (lastBarState === null ? "-" : (lastBarState ? "显示" : "收起"));

  const articles = () => Array.from(document.querySelectorAll("article"));
  const visibleArticles = () => articles().filter((el) => {
    const r = el.getBoundingClientRect();
    return r.bottom > contentTop() && r.top < contentBottom();
  });
  const selectedArticle = () => {
    const marked = document.querySelector("[data-guyii-selected]");
    return marked && marked.matches("article") ? marked : null;
  };
  const nearestArticleIndex = (list) => {
    const visible = visibleArticles();
    const anchor = contentTop();
    const current = selectedArticle();
    if (current && visible.indexOf(current) >= 0) {
      const currentIndex = list.indexOf(current);
      if (currentIndex >= 0) return currentIndex;
    }
    if (!visible.length) return -1;
    let best = visible[0];
    let bestDistance = Math.abs(visible[0].getBoundingClientRect().top - anchor);
    visible.slice(1).forEach((el) => {
      const distance = Math.abs(el.getBoundingClientRect().top - anchor);
      if (distance < bestDistance) {
        best = el;
        bestDistance = distance;
      }
    });
    return list.indexOf(best);
  };
  const mark = (el) => {
    document.querySelectorAll("[data-guyii-selected]").forEach((x) => {
      x.removeAttribute("data-guyii-selected");
      x.style.outline = "";
      x.style.outlineOffset = "";
    });
    if (!el) return;
    el.setAttribute("data-guyii-selected", "1");
    el.style.outline = "2px solid #1d9bf0";
    el.style.outlineOffset = "3px";
  };
  const scrollArticleIntoSlot = (el) => {
    const rect = el.getBoundingClientRect();
    const top = contentTop();
    const bottom = contentBottom();
    if (rect.top < top || rect.top > top + 18 || rect.bottom > bottom) {
      window.scrollBy({ top: rect.top - top, behavior: "smooth" });
    }
  };
  const move = (delta) => {
    const list = articles();
    if (!list.length) {
      window.scrollBy({ top: delta > 0 ? window.innerHeight * 0.55 : -window.innerHeight * 0.55, behavior: "smooth" });
      return;
    }
    const anchor = nearestArticleIndex(list);
    selected = Math.max(0, Math.min(list.length - 1, (anchor < 0 ? 0 : anchor) + delta));
    const target = list[selected];
    mark(target);
    scrollArticleIntoSlot(target);
  };
  const ensureSelection = () => {
    if (document.querySelector("[data-guyii-selected]")) return;
    const visible = visibleArticles();
    if (visible.length) mark(visible[0]);
  };
  // Enter 进入选中的推文，等同于点它
  const openSelected = () => {
    const article = selectedArticle() || visibleArticles()[0];
    if (!article) return false;
    const link = Array.from(article.querySelectorAll('a[href*="/status/"]'))
      .find((a) => /\/status\/\d+$/.test(a.getAttribute("href") || ""));
    if (!link) return false;
    link.click();
    setTimeout(reportRoute, 60);
    return true;
  };
  const goBack = () => {
    if (history.length > 1) history.back();
    else location.href = "https://x.com/home";
  };
  const clickByLabel = (root, labels) => {
    const nodes = Array.from(root.querySelectorAll("[aria-label], [data-testid]"));
    const found = nodes.find((el) => {
      const text = ((el.getAttribute("aria-label") || "") + " " + (el.getAttribute("data-testid") || "")).toLowerCase();
      return labels.some((label) => text.includes(label));
    });
    if (found) found.click();
  };
  const current = () => document.querySelector("[data-guyii-selected]") || visibleArticles()[0] || document;
  const notify = (text) => {
    try {
      if (window.AndroidShortcut && AndroidShortcut.toast) AndroidShortcut.toast(text);
    } catch (e) {}
  };
  const waitFor = (probe, timeout) => new Promise((resolve) => {
    const started = Date.now();
    const poll = () => {
      const found = probe();
      if (found) return resolve(found);
      if (Date.now() - started > timeout) return resolve(null);
      setTimeout(poll, 50);
    };
    poll();
  });
  const byTestId = (root, ids) => {
    for (const id of ids) {
      const el = root.querySelector('[data-testid="' + id + '"]');
      if (el) return el;
    }
    return null;
  };
  // X 的转发图标只是打开菜单，真正的转发是菜单里的 retweetConfirm
  const repost = (quote) => {
    const button = byTestId(current(), ["retweet", "unretweet"]);
    if (!button) return false;
    const undoing = button.getAttribute("data-testid") === "unretweet";
    button.click();
    if (quote) {
      waitFor(() => {
        const items = Array.from(document.querySelectorAll('[role="menu"] [role="menuitem"]'));
        return items.find((el) => /quote|引用/i.test(el.textContent || "")) || null;
      }, 1500).then((item) => {
        if (item) item.click();
        else notify("没找到引用转发入口");
      });
      return true;
    }
    waitFor(() => document.querySelector(
      '[data-testid="retweetConfirm"], [data-testid="unretweetConfirm"]'), 1500).then((confirm) => {
      if (!confirm) {
        notify("转发菜单没出来");
        return;
      }
      confirm.click();
      notify(undoing ? "已取消转发" : "已转发 · 再按 t 撤销");
    });
    return true;
  };
  const TAB_FOR_PATH = {
    "/": "home",
    "/home": "home",
    "/explore": "explore",
    "/search": "explore",
    "/i/grok": "grok",
    "/notifications": "notifications",
    "/notifications/mentions": "mentions",
    "/messages": "messages"
  };
  // 软刷新：对同一个路由再导航一次，X 会回顶并拉新帖，不走整页重载
  const softRefresh = () => {
    const key = TAB_FOR_PATH[location.pathname];
    mark(null);
    window.scrollTo({ top: 0, behavior: "smooth" });
    if (!key) return false;
    goTab(key);
    return true;
  };
  const composerEditor = () => document.querySelector(
    '[data-testid^="tweetTextarea"], div[role="dialog"] [contenteditable="true"]');
  const composerDialog = () => {
    const editor = composerEditor();
    return editor ? editor.closest('div[role="dialog"]') : null;
  };
  const submitButton = () => document.querySelector('[data-testid="tweetButtonInline"], [data-testid="tweetButton"]');
  const canSubmit = () => {
    const button = submitButton();
    return !!button && button.getAttribute("aria-disabled") !== "true" && !button.disabled;
  };
  const INLINE_COMPOSER_PATHS = new Set(["/", "/home"]);
  const inlineComposerBlock = () => {
    const editor = document.querySelector('[data-testid="tweetTextarea_0"]');
    if (!editor || editor.closest('div[role="dialog"]')) return null;
    let node = editor.parentElement;
    while (node && node !== document.body) {
      if (node.querySelector('[data-testid="toolBar"]') && !node.querySelector("article")) return node;
      node = node.parentElement;
    }
    return null;
  };
  const tagInlineComposer = () => {
    const wanted = INLINE_COMPOSER_PATHS.has(location.pathname) ? inlineComposerBlock() : null;
    document.querySelectorAll("[data-guyii-inline-composer]").forEach((el) => {
      if (el !== wanted) el.removeAttribute("data-guyii-inline-composer");
    });
    if (wanted && !wanted.hasAttribute("data-guyii-inline-composer")) {
      wanted.setAttribute("data-guyii-inline-composer", "1");
    }
  };
  const tagComposer = () => {
    const dialog = composerDialog();
    document.querySelectorAll("[data-guyii-composer]").forEach((el) => {
      if (el !== dialog) el.removeAttribute("data-guyii-composer");
    });
    if (dialog && !dialog.hasAttribute("data-guyii-composer")) {
      dialog.setAttribute("data-guyii-composer", "1");
    }
  };
  const notifyComposer = () => {
    tagComposer();
    tagInlineComposer();
    try {
      if (window.AndroidShortcut) AndroidShortcut.composerChanged(!!composerDialog(), canSubmit());
    } catch (e) {}
  };
  const clickPost = () => {
    const button = submitButton();
    if (button) {
      button.click();
      return true;
    }
    return false;
  };
  const clickCancel = () => {
    const button = document.querySelector('[aria-label="Close"], [data-testid="app-bar-close"], [aria-label="Back"]');
    if (button) {
      button.click();
      return true;
    }
    history.back();
    return true;
  };
  const editor = () => document.querySelector('[data-testid="tweetTextarea_0"], div[role="dialog"] [contenteditable="true"], [contenteditable="true"]');
  const insertText = (text) => {
    const el = editor();
    if (!el) return false;
    el.focus();
    document.execCommand("insertText", false, text);
    notifyComposer();
    return true;
  };
  const ROOT_PATHS = new Set([
    "/", "/home", "/explore", "/search", "/i/grok",
    "/notifications", "/notifications/mentions", "/notifications/verified", "/messages"
  ]);
  let lastRoute = "";
  const reportRoute = () => {
    const url = location.href;
    if (url === lastRoute) return;
    lastRoute = url;
    mark(null);
    resetBarTracking();
    try {
      if (window.AndroidShortcut && AndroidShortcut.routeChanged) {
        AndroidShortcut.routeChanged(url, document.title || "", ROOT_PATHS.has(location.pathname));
      }
    } catch (e) {}
  };
  const pageBackground = () => {
    for (const el of [document.body, document.documentElement]) {
      if (!el) continue;
      const color = getComputedStyle(el).backgroundColor;
      if (color && color !== "transparent" && !/^rgba\(0, 0, 0, 0\)$/.test(color)) return color;
    }
    return "";
  };
  let lastTheme = "";
  const reportTheme = () => {
    const background = pageBackground();
    if (!background || background === lastTheme) return;
    lastTheme = background;
    try {
      if (window.AndroidShortcut && AndroidShortcut.themeChanged) AndroidShortcut.themeChanged(background);
    } catch (e) {}
  };
  // X 是 SPA，客户端路由不触发 onPageFinished，原生只能靠这里知道页面变了
  ["pushState", "replaceState"].forEach((name) => {
    const original = history[name];
    if (typeof original !== "function" || original.__guyiiPatched) return;
    const patched = function () {
      const result = original.apply(this, arguments);
      setTimeout(reportRoute, 0);
      return result;
    };
    patched.__guyiiPatched = true;
    history[name] = patched;
  });
  window.addEventListener("popstate", () => setTimeout(reportRoute, 0));

  // 点 X 自己的导航链接走 SPA 路由，保住滚动位置和时间线状态；找不到才整页跳
  const TAB_TARGET = {
    home: { anchor: 'a[data-testid="AppTabBar_Home_Link"], a[href="/home"]', url: "https://x.com/home" },
    explore: { anchor: 'a[data-testid="AppTabBar_Explore_Link"], a[href="/explore"]', url: "https://x.com/explore" },
    grok: { anchor: 'a[href="/i/grok"]', url: "https://x.com/i/grok" },
    notifications: { anchor: 'a[data-testid="AppTabBar_Notifications_Link"], a[href="/notifications"]', url: "https://x.com/notifications" },
    mentions: { anchor: 'a[href="/notifications/mentions"]', url: "https://x.com/notifications/mentions" },
    messages: { anchor: 'a[data-testid="AppTabBar_DirectMessage_Link"], a[href="/messages"]', url: "https://x.com/messages" }
  };
  const zoom = (delta) => {
    try {
      if (window.AndroidShortcut && AndroidShortcut.zoom) AndroidShortcut.zoom(delta);
    } catch (e) {}
  };
  const profileHandle = () => {
    const link = document.querySelector('a[data-testid="AppTabBar_Profile_Link"]');
    const href = link && link.getAttribute("href");
    return href && /^\/[A-Za-z0-9_]+$/.test(href) ? href.slice(1) : "";
  };
  const openBookmarks = () => {
    const anchor = document.querySelector('a[href="/i/bookmarks"]');
    if (anchor) anchor.click();
    else location.href = "https://x.com/i/bookmarks";
    setTimeout(reportRoute, 60);
  };
  const openLists = () => {
    const anchor = document.querySelector('a[href$="/lists"]');
    if (anchor) {
      anchor.click();
      setTimeout(reportRoute, 60);
      return true;
    }
    const handle = profileHandle();
    if (!handle) return false;
    location.href = "https://x.com/" + handle + "/lists";
    return true;
  };
  // 抽屉条目：先点页面上真实的链接走 SPA，其次用确定的固定地址，最后才用 handle 拼
  const ITEM_ANCHOR = {
    profile: 'a[data-testid="AppTabBar_Profile_Link"]',
    bookmarks: 'a[href="/i/bookmarks"]',
    lists: 'a[href$="/lists"]',
    communities: 'a[href$="/communities"]',
    likes: 'a[href$="/likes"]',
    drafts: 'a[href="/compose/post/unsent/drafts"]',
    settings: 'a[href^="/settings"]'
  };
  const ITEM_URL = {
    bookmarks: "https://x.com/i/bookmarks",
    drafts: "https://x.com/compose/post/unsent/drafts",
    settings: "https://x.com/settings"
  };
  const ITEM_BY_HANDLE = {
    profile: "",
    lists: "/lists",
    likes: "/likes",
    communities: "/communities"
  };
  const openItem = (key) => {
    const selector = ITEM_ANCHOR[key];
    const anchor = selector && document.querySelector(selector);
    if (anchor) {
      anchor.click();
      setTimeout(reportRoute, 60);
      return true;
    }
    if (ITEM_URL[key]) {
      location.href = ITEM_URL[key];
      return true;
    }
    const handle = profileHandle();
    if (!handle || ITEM_BY_HANDLE[key] === undefined) return false;
    location.href = "https://x.com/" + handle + ITEM_BY_HANDLE[key];
    return true;
  };
  let lastHandle = "";
  const reportProfile = () => {
    const handle = profileHandle();
    if (!handle || handle === lastHandle) return;
    lastHandle = handle;
    try {
      if (window.AndroidShortcut && AndroidShortcut.profileChanged) AndroidShortcut.profileChanged(handle);
    } catch (e) {}
  };
  const goTab = (key) => {
    const target = TAB_TARGET[key];
    if (!target) return false;
    const anchor = document.querySelector(target.anchor);
    if (anchor) {
      anchor.click();
      setTimeout(reportRoute, 60);
      return true;
    }
    location.href = target.url;
    return true;
  };
  const unreadCount = (selector) => {
    const el = document.querySelector(selector);
    if (!el) return 0;
    const label = el.getAttribute("aria-label") || "";
    const match = label.match(/(\d+)/);
    return match ? parseInt(match[1], 10) : 0;
  };
  let lastBadges = "";
  const reportBadges = () => {
    const notifications = unreadCount('a[data-testid="AppTabBar_Notifications_Link"]');
    const messages = unreadCount('a[data-testid="AppTabBar_DirectMessage_Link"]');
    const key = notifications + "/" + messages;
    if (key === lastBadges) return;
    lastBadges = key;
    try {
      if (window.AndroidShortcut && AndroidShortcut.badgeChanged) AndroidShortcut.badgeChanged(notifications, messages);
    } catch (e) {}
  };

  window.__guyii = {
    setChrome: applyChrome,
    scrollTop: scrollTop,
    go: goTab,
    compose: () => {
      const compose = document.querySelector('[data-testid="SideNav_NewTweet_Button"], a[href="/compose/post"]');
      if (compose) compose.click();
      else location.href = "https://x.com/compose/post";
    },
    submit: clickPost,
    dismiss: clickCancel,
    insert: insertText,
    home: () => goTab("home"),
    mentions: () => goTab("mentions"),
    messages: () => goTab("messages"),
    search: () => goTab("explore"),
    grok: () => goTab("grok"),
    bookmarks: openBookmarks,
    lists: openLists,
    openItem: openItem,
    open: openSelected,
    softRefresh: softRefresh,
    repost: repost,
    back: goBack
  };
  const tick = () => {
    notifyComposer();
    reportRoute();
    reportTheme();
    reportBadges();
    reportProfile();
    ensureSelection();
    try {
      if (window.AndroidShortcut && AndroidShortcut.debugInfo) AndroidShortcut.debugInfo(scrollDebug());
    } catch (e) {}
  };
  new MutationObserver(notifyComposer).observe(document.documentElement, { childList: true, subtree: true });
  setInterval(tick, 1500);
  tick();

  document.addEventListener("keydown", (event) => {
    if (event.altKey || event.ctrlKey || event.metaKey || editable(document.activeElement)) return;
    if (event.key === "g" || event.key === "z") {
      setPrefix(event.key);
      return;
    }
    if (prefix === "z") {
      prefix = "";
      if (event.key === "k") { zoom(10); event.preventDefault(); }
      if (event.key === "j") { zoom(-10); event.preventDefault(); }
      if (event.key === "0") { zoom(0); event.preventDefault(); }
      return;
    }
    if (prefix === "g") {
      prefix = "";
      if (event.key === "h") { goTab("home"); event.preventDefault(); }
      if (event.key === "m") { goTab("mentions"); event.preventDefault(); }
      if (event.key === "d") { goTab("messages"); event.preventDefault(); }
      if (event.key === "s") { goTab("explore"); event.preventDefault(); }
      if (event.key === "g") { goTab("grok"); event.preventDefault(); }
      if (event.key === "n") { goTab("notifications"); event.preventDefault(); }
      if (event.key === "p") { openItem("profile"); event.preventDefault(); }
      if (event.key === "b") { openItem("bookmarks"); event.preventDefault(); }
      if (event.key === "l") { openItem("lists"); event.preventDefault(); }
      if (event.key === "k") { openItem("likes"); event.preventDefault(); }
      if (event.key === "c") { openItem("communities"); event.preventDefault(); }
      return;
    }
    if (event.key === "Escape" && composerDialog()) { clickCancel(); event.preventDefault(); }
    if (event.key === "Enter") { openSelected(); event.preventDefault(); }
    if (event.key === "b") { goBack(); event.preventDefault(); }
    if (event.key === "f") { history.forward(); event.preventDefault(); }
    if (event.key === " ") {
      window.scrollBy({ top: (event.shiftKey ? -0.85 : 0.85) * window.innerHeight, behavior: "smooth" });
      event.preventDefault();
    }
    if (event.key === "j") { move(1); event.preventDefault(); }
    if (event.key === "k") { move(-1); event.preventDefault(); }
    if (event.key === "/") {
      const input = document.querySelector('input[data-testid="SearchBox_Search_Input"], input[aria-label*="Search"], input[placeholder*="Search"]');
      if (input) { input.focus(); event.preventDefault(); }
    }
    if (event.key === ".") { location.reload(); event.preventDefault(); }
    if (event.key === "l") { clickByLabel(current(), ["like"]); event.preventDefault(); }
    if (event.key === "r") { clickByLabel(current(), ["reply"]); event.preventDefault(); }
    if (event.key === "t") { repost(false); event.preventDefault(); }
    if (event.key === "q") { repost(true); event.preventDefault(); }
    if (event.key === "u") { softRefresh(); event.preventDefault(); }
    if (event.key === "n") {
      window.__guyii.compose();
      event.preventDefault();
    }
  }, true);
})();
