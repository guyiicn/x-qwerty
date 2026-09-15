package guyii.social.tools;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.content.Context;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {
    private static final String HOME_URL = "https://x.com/home";
    private static final String START_URL = "https://x.com";
    private static final String MENTIONS_URL = "https://x.com/notifications/mentions";
    private static final String MESSAGES_URL = "https://x.com/messages";
    private static final String SEARCH_URL = "https://x.com/search";
    private static final String GROK_URL = "https://x.com/i/grok";
    private static final int ACCENT = 0xff1d9bf0;
    private static final int ACCENT_FOREGROUND = 0xffffffff;
    private static final int BADGE = 0xfff4212e;
    private static final String PREFS = "guyii";
    private static final String KEY_THEME_MODE = "theme_mode";
    private static final String KEY_BAR_MODE = "bar_mode";
    private static final String KEY_IMMERSIVE = "immersive";
    private static final int BAR_HEIGHT_DP = 78;
    private static final String[] THEME_LABELS = {"跟随", "浅色", "深色"};
    // "#" 开头的是分组标题。顺序按黑莓式键盘的实际可用性排，组合键沉到最后
    private static final String[][] SHORTCUTS = {
            {"#", "单键 · 输入框聚焦时全部让路"},
            {"b / f", "后退 / 前进（f 是浏览器前进，需先后退过）"},
            {"空格 / ⇧空格", "下翻 / 上翻一页"},
            {"j / k", "下一条 / 上一条帖子"},
            {"Enter", "进入选中的帖子"},
            {"u", "刷新当前视图（回顶 + 拉新帖）"},
            {".", "整页重载"},
            {"/", "聚焦搜索框"},
            {"n", "发帖"},
            {"l", "赞"},
            {"r", "回复"},
            {"t", "转发 / 再按一次取消"},
            {"q", "引用转发"},
            {"#", "发布与关闭"},
            {"Alt+Enter", "发布"},
            {"返回键", "关快捷键页 → 关抽屉 → 退全屏 → 关发帖框 → 后退"},
            {"返回键", "已在首页且无历史时，两秒内再按一次退出"},
            {"#", "g 前缀层 · 按 g 后 1.2 秒内接第二个键"},
            {"g h", "首页"},
            {"g n", "通知"},
            {"g m", "@我的"},
            {"g d", "私信"},
            {"g s", "搜索"},
            {"g g", "Grok"},
            {"g p", "个人资料"},
            {"g b", "书签"},
            {"g l", "列表"},
            {"g k", "已喜欢"},
            {"g c", "社区"},
            {"#", "z 前缀层 · 字号"},
            {"z k / z j", "字号 ＋ / −"},
            {"z 0", "复位 100%"},
            {"#", "触屏"},
            {"向下滚动", "底栏收起，让出整块高度"},
            {"向上滚动 / 回到顶部", "底栏回来"},
            {"点已选中的 Tab", "回到顶部并刷新"},
            {"长按「通知」", "进 @我的"},
            {"长按「首页」", "打开抽屉"},
            {"左边缘右滑", "打开抽屉（手势导航的机器上会被系统返回吃掉）"},
            {"#", "以下只有外接全键盘能按 · 本机键盘按不出来"},
            {"Ctrl+Enter", "发布 —— 本机用 Alt+Enter"},
            {"F5 / Ctrl+R", "整页重载 —— 本机用 ."},
            {"Alt+← / Alt+→", "后退 / 前进 —— 本机用 b / f"},
            {"Ctrl+L", "搜索 —— 本机用 / 或 g s"},
            {"Ctrl++ / - / 0", "字号 —— 本机用 z 前缀层"},
            {"Esc", "逐级关闭 —— 本机用返回键"},
    };

    private static final int FILE_CHOOSER_REQUEST = 41;
    private static final int WEB_PERMISSION_REQUEST = 42;
    private static final long EXIT_CONFIRM_WINDOW_MS = 2000L;
    private static final String[] TAB_KEYS = {"home", "explore", "grok", "notifications", "messages"};
    private static final String[] TAB_LABELS = {"首页", "搜索", "Grok", "通知", "私信"};

    private WebView webView;
    private ProgressBar progressBar;
    private EdgeSwipeLayout root;
    private LinearLayout bottomNav;
    private TextView publishButton;
    private FrameLayout fullscreenContainer;
    private View fullscreenView;
    private WebChromeClient.CustomViewCallback fullscreenCallback;
    private ValueCallback<Uri[]> fileChooserCallback;
    private PermissionRequest pendingPermissionRequest;
    private GeolocationPermissions.Callback pendingGeoCallback;
    private String pendingGeoOrigin;
    private int textZoom = 110;
    private boolean composerDialogOpen = false;
    private boolean canSubmitComposer = false;
    private int systemBottomInset = 0;
    private int systemTopInset = 0;
    private long lastBackPressAt = 0L;
    private boolean initialHistoryCleared = false;
    private int themeColor = Color.BLACK;
    private boolean themeApplied = false;
    private boolean routeIsRootTab = true;
    private final List<Tab> tabs = new ArrayList<>();
    private View composeFab;
    private ImageView drawerButtonIcon;
    private TextView drawerButtonLabel;
    private int activeTab = 0;
    private View drawerScrim;
    private LinearLayout drawerPanel;
    private LinearLayout drawerList;
    private boolean drawerOpen = false;
    private TextView drawerAvatar;
    private TextView drawerHandle;
    private TextView zoomValue;
    private final List<TextView> drawerTexts = new ArrayList<>();
    private String profileHandle = "";
    private int themeMode = 0;
    private int barMode = 0;          // 0 滚动自动隐藏，1 常驻
    private boolean immersive = false;
    private boolean barVisible = true;
    private final List<TextView> themeChips = new ArrayList<>();
    private TextView barChip;
    private TextView immersiveChip;
    private View shortcutsPanel;
    private LinearLayout shortcutsList;
    private TextView shortcutsTitle;
    private final List<TextView> shortcutsTexts = new ArrayList<>();
    private boolean shortcutsOpen = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        applyEdgeToEdge();
        WebView.setWebContentsDebuggingEnabled(false);

        root = new EdgeSwipeLayout(this);
        root.setBackgroundColor(Color.BLACK);
        webView = new WebView(this);
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);

        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(progressBar, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(3)));
        buildOverlayUi();
        buildDrawer();
        buildShortcutsPanel();
        installInsetsListener();
        setContentView(root);
        positionOverlay();

        android.content.SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        themeMode = prefs.getInt(KEY_THEME_MODE, 0);
        barMode = prefs.getInt(KEY_BAR_MODE, 0);
        immersive = prefs.getBoolean(KEY_IMMERSIVE, false);
        configureWebView();
        installScrollListener();
        applyImmersive();
        applyThemeCookie();
        webView.loadUrl(resolveStartUrl());
    }

    // targetSdk 36 在 Android 15+ 上本来就强制 edge-to-edge，显式打开让所有版本走同一条路径
    private void applyEdgeToEdge() {
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
        } else {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void configureWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadsImagesAutomatically(true);
        s.setLoadWithOverviewMode(false);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setTextZoom(textZoom);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) s.setSafeBrowsingEnabled(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        }
        s.setUserAgentString(chromeLikeUserAgent());

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookies.setAcceptThirdPartyCookies(webView, true);
        }

        webView.addJavascriptInterface(new ShortcutBridge(), "AndroidShortcut");
        webView.setWebViewClient(new XWebViewClient());
        webView.setWebChromeClient(new XWebChromeClient());
        webView.setDownloadListener(new XDownloadListener());
    }

    private void buildOverlayUi() {
        bottomNav = new LinearLayout(this);
        bottomNav.setOrientation(LinearLayout.HORIZONTAL);
        bottomNav.setGravity(Gravity.CENTER);
        bottomNav.setPadding(dp(6), 0, dp(6), 0);
        bottomNav.setBackground(rounded(0xf2050505, dp(29), 0x22ffffff));
        for (int i = 0; i < TAB_KEYS.length; i++) tabs.add(addTab(i));
        addDrawerButton();
        FrameLayout.LayoutParams navParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(58), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        navParams.setMargins(dp(12), 0, dp(12), dp(12));
        root.addView(bottomNav, navParams);

        composeFab = new ImageView(this);
        ((ImageView) composeFab).setImageDrawable(new IconDrawable(composePath(), ACCENT_FOREGROUND, dp(2)));
        ((ImageView) composeFab).setScaleType(ImageView.ScaleType.FIT_CENTER);
        composeFab.setPadding(dp(15), dp(15), dp(15), dp(15));
        composeFab.setBackground(rounded(ACCENT, dp(28), 0));
        composeFab.setOnClickListener(v -> runPageAction("window.__guyii&&window.__guyii.compose&&window.__guyii.compose();"));
        FrameLayout.LayoutParams fabParams = new FrameLayout.LayoutParams(dp(56), dp(56), Gravity.BOTTOM | Gravity.RIGHT);
        fabParams.setMargins(dp(12), 0, dp(16), dp(82));
        root.addView(composeFab, fabParams);

        publishButton = new TextView(this);
        publishButton.setText("发布");
        publishButton.setTextColor(ACCENT_FOREGROUND);
        publishButton.setTextSize(16);
        publishButton.setTypeface(Typeface.DEFAULT_BOLD);
        publishButton.setGravity(Gravity.CENTER);
        publishButton.setBackground(rounded(ACCENT, dp(24), 0));
        publishButton.setOnClickListener(v -> runPageAction("window.__guyii&&window.__guyii.submit&&window.__guyii.submit();"));
        publishButton.setVisibility(View.GONE);
        FrameLayout.LayoutParams publishParams = new FrameLayout.LayoutParams(dp(86), dp(48), Gravity.BOTTOM | Gravity.RIGHT);
        publishParams.setMargins(dp(12), 0, dp(16), dp(82));
        root.addView(publishButton, publishParams);
    }

    private void buildDrawer() {
        drawerScrim = new View(this);
        drawerScrim.setBackgroundColor(0x99000000);
        drawerScrim.setVisibility(View.GONE);
        drawerScrim.setOnClickListener(v -> closeDrawer());
        root.addView(drawerScrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        drawerPanel = new LinearLayout(this);
        drawerPanel.setOrientation(LinearLayout.VERTICAL);
        drawerPanel.setBackgroundColor(themeColor);
        drawerPanel.setClickable(true);
        drawerPanel.setTranslationX(-dp(304));

        drawerAvatar = new TextView(this);
        drawerAvatar.setGravity(Gravity.CENTER);
        drawerAvatar.setTextSize(20);
        drawerAvatar.setTypeface(Typeface.DEFAULT_BOLD);
        drawerAvatar.setTextColor(Color.WHITE);
        drawerAvatar.setBackground(rounded(0xff3d4a52, dp(24), 0));
        drawerAvatar.setText("?");
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        avatarParams.setMargins(dp(18), dp(14), 0, dp(10));
        drawerAvatar.setLayoutParams(avatarParams);

        drawerList = new LinearLayout(this);
        drawerList.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroller = new ScrollView(this);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.addView(drawerList, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        drawerPanel.addView(scroller, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        drawerList.addView(drawerAvatar);

        drawerHandle = new TextView(this);
        drawerHandle.setText("未登录");
        drawerHandle.setTextSize(15);
        drawerHandle.setTypeface(Typeface.DEFAULT_BOLD);
        drawerHandle.setPadding(dp(18), 0, dp(18), dp(14));
        drawerList.addView(drawerHandle);
        drawerTexts.add(drawerHandle);

        drawerList.addView(divider());
        addDrawerItem("profile", "个人资料");
        addDrawerItem("bookmarks", "书签");
        addDrawerItem("lists", "列表");
        addDrawerItem("communities", "社区");
        addDrawerItem("likes", "已喜欢");
        addDrawerItem("drafts", "草稿");
        drawerList.addView(divider());
        addDrawerItem("settings", "设置与隐私");
        addDrawerAction("快捷键一览", this::openShortcuts);

        drawerPanel.addView(divider());
        drawerPanel.addView(buildLayoutRow());
        drawerPanel.addView(buildThemeRow());
        drawerPanel.addView(buildZoomRow());

        root.addView(drawerPanel, new FrameLayout.LayoutParams(
                dp(304), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.LEFT));
    }

    private void addDrawerAction(String label, Runnable action) {
        TextView item = new TextView(this);
        item.setText(label);
        item.setTextSize(16);
        item.setTypeface(Typeface.DEFAULT_BOLD);
        item.setPadding(dp(18), dp(13), dp(18), dp(13));
        item.setOnClickListener(v -> {
            closeDrawer();
            action.run();
        });
        drawerList.addView(item);
        drawerTexts.add(item);
    }

    private void buildShortcutsPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(themeColor);
        panel.setClickable(true);
        panel.setVisibility(View.GONE);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), dp(10), dp(10), dp(10));
        shortcutsTitle = new TextView(this);
        shortcutsTitle.setText("快捷键一览");
        shortcutsTitle.setTextSize(19);
        shortcutsTitle.setTypeface(Typeface.DEFAULT_BOLD);
        bar.addView(shortcutsTitle, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView close = new TextView(this);
        close.setText("✕");
        close.setTextSize(19);
        close.setTypeface(Typeface.DEFAULT_BOLD);
        close.setGravity(Gravity.CENTER);
        close.setTextColor(ACCENT);
        close.setOnClickListener(v -> closeShortcuts());
        close.setLayoutParams(new LinearLayout.LayoutParams(dp(44), dp(44)));
        bar.addView(close);
        panel.addView(bar);

        shortcutsList = new LinearLayout(this);
        shortcutsList.setOrientation(LinearLayout.VERTICAL);
        shortcutsList.setPadding(dp(16), 0, dp(16), dp(24));
        for (String[] row : SHORTCUTS) shortcutsList.addView(shortcutRow(row[0], row[1]));
        ScrollView scroller = new ScrollView(this);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.addView(shortcutsList, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.addView(scroller, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        shortcutsPanel = panel;
        root.addView(shortcutsPanel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private View shortcutRow(String key, String label) {
        if ("#".equals(key)) {
            TextView header = new TextView(this);
            header.setText(label);
            header.setTextSize(12);
            header.setTypeface(Typeface.DEFAULT_BOLD);
            header.setTextColor(ACCENT);
            header.setPadding(0, dp(18), 0, dp(8));
            return header;
        }
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(5), 0, dp(5));
        TextView keyView = new TextView(this);
        keyView.setText(key);
        keyView.setTextSize(12);
        keyView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        keyView.setTextColor(ACCENT);
        keyView.setLayoutParams(new LinearLayout.LayoutParams(dp(120),
                ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(keyView);
        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextSize(13);
        labelView.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(labelView);
        shortcutsTexts.add(labelView);
        return row;
    }

    private void openShortcuts() {
        shortcutsOpen = true;
        shortcutsPanel.setVisibility(View.VISIBLE);
        setOverlayVisible(false);
    }

    private void closeShortcuts() {
        if (!shortcutsOpen) return;
        shortcutsOpen = false;
        shortcutsPanel.setVisibility(View.GONE);
        setOverlayVisible(true);
    }

    private View divider() {
        View line = new View(this);
        line.setBackgroundColor(0x33808080);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1) / 2));
        params.setMargins(dp(18), dp(6), dp(18), dp(6));
        line.setLayoutParams(params);
        return line;
    }

    private void addDrawerItem(String key, String label) {
        TextView item = new TextView(this);
        item.setText(label);
        item.setTextSize(16);
        item.setTypeface(Typeface.DEFAULT_BOLD);
        item.setPadding(dp(18), dp(13), dp(18), dp(13));
        item.setOnClickListener(v -> {
            closeDrawer();
            runPageAction("window.__guyii&&window.__guyii.openItem&&window.__guyii.openItem('" + key + "');");
        });
        drawerList.addView(item);
        drawerTexts.add(item);
    }

    private View buildLayoutRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), dp(10), dp(18), dp(2));
        barChip = toggleChip(v -> {
            barMode = barMode == 0 ? 1 : 0;
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(KEY_BAR_MODE, barMode).apply();
            applyBarMode();
            updateLayoutChips();
        });
        immersiveChip = toggleChip(v -> {
            immersive = !immersive;
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_IMMERSIVE, immersive).apply();
            applyImmersive();
            updateLayoutChips();
        });
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, dp(32), 1f);
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, dp(32), 1f);
        right.setMargins(dp(6), 0, 0, 0);
        barChip.setLayoutParams(left);
        immersiveChip.setLayoutParams(right);
        row.addView(barChip);
        row.addView(immersiveChip);
        updateLayoutChips();
        return row;
    }

    private TextView toggleChip(View.OnClickListener listener) {
        TextView chip = new TextView(this);
        chip.setTextSize(12);
        chip.setTypeface(Typeface.DEFAULT_BOLD);
        chip.setGravity(Gravity.CENTER);
        chip.setOnClickListener(listener);
        return chip;
    }

    private void updateLayoutChips() {
        if (barChip == null) return;
        boolean light = luminance(themeColor) > 0.5;
        int idle = light ? 0xff5b6b78 : 0xff8b98a5;
        barChip.setText(barMode == 0 ? "底栏自动隐藏" : "底栏常驻");
        immersiveChip.setText(immersive ? "沉浸模式 开" : "沉浸模式 关");
        boolean barOn = barMode == 0;
        barChip.setTextColor(barOn ? ACCENT_FOREGROUND : idle);
        barChip.setBackground(rounded(barOn ? ACCENT : 0x00000000, dp(16), barOn ? 0 : 0x33808080));
        immersiveChip.setTextColor(immersive ? ACCENT_FOREGROUND : idle);
        immersiveChip.setBackground(
                rounded(immersive ? ACCENT : 0x00000000, dp(16), immersive ? 0 : 0x33808080));
    }

    private View buildThemeRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), dp(10), dp(18), dp(4));
        for (int i = 0; i < THEME_LABELS.length; i++) {
            final int mode = i;
            TextView chip = new TextView(this);
            chip.setText(THEME_LABELS[i]);
            chip.setTextSize(12);
            chip.setTypeface(Typeface.DEFAULT_BOLD);
            chip.setGravity(Gravity.CENTER);
            chip.setOnClickListener(v -> setThemeMode(mode));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(32), 1f);
            params.setMargins(i == 0 ? 0 : dp(6), 0, 0, 0);
            chip.setLayoutParams(params);
            themeChips.add(chip);
            row.addView(chip);
        }
        updateThemeChips();
        return row;
    }

    private void updateThemeChips() {
        boolean light = luminance(themeColor) > 0.5;
        int idle = light ? 0xff5b6b78 : 0xff8b98a5;
        for (int i = 0; i < themeChips.size(); i++) {
            TextView chip = themeChips.get(i);
            boolean on = i == themeMode;
            chip.setTextColor(on ? ACCENT_FOREGROUND : idle);
            chip.setBackground(rounded(on ? ACCENT : 0x00000000, dp(16), on ? 0 : 0x33808080));
        }
    }

    // X 把主题存在 night_mode cookie 里：0 浅色 / 1 Dim / 2 纯黑
    private void setThemeMode(int mode) {
        themeMode = mode;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(KEY_THEME_MODE, mode).apply();
        updateThemeChips();
        if (mode == 0) {
            Toast.makeText(this, "跟随 X 自己的设置", Toast.LENGTH_SHORT).show();
            return;
        }
        applyThemeCookie();
        webView.reload();
    }

    private void applyThemeCookie() {
        if (themeMode == 0) return;
        CookieManager cookies = CookieManager.getInstance();
        String value = "night_mode=" + (themeMode == 1 ? "0" : "2")
                + "; Domain=.x.com; Path=/; Max-Age=31536000";
        cookies.setCookie("https://x.com", value);
        cookies.setCookie("https://twitter.com",
                value.replace("Domain=.x.com", "Domain=.twitter.com"));
        cookies.flush();
    }

    private View buildZoomRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), dp(8), dp(18), dp(18));
        row.addView(zoomButton("−", -10));
        zoomValue = new TextView(this);
        zoomValue.setGravity(Gravity.CENTER);
        zoomValue.setTextSize(13);
        zoomValue.setTypeface(Typeface.DEFAULT_BOLD);
        zoomValue.setText(textZoom + "%");
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(0, dp(34), 1f);
        valueParams.setMargins(dp(8), 0, dp(8), 0);
        zoomValue.setLayoutParams(valueParams);
        row.addView(zoomValue);
        drawerTexts.add(zoomValue);
        row.addView(zoomButton("＋", 10));
        return row;
    }

    private TextView zoomButton(String label, int delta) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextSize(17);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setGravity(Gravity.CENTER);
        button.setBackground(rounded(0x00000000, dp(17), 0x33808080));
        button.setOnClickListener(v -> setTextZoom(delta == 0 ? 100 : textZoom + delta));
        button.setLayoutParams(new LinearLayout.LayoutParams(dp(42), dp(34)));
        drawerTexts.add(button);
        return button;
    }

    private void openDrawer() {
        if (drawerOpen) return;
        drawerOpen = true;
        drawerScrim.setAlpha(0f);
        drawerScrim.setVisibility(View.VISIBLE);
        drawerScrim.animate().alpha(1f).setDuration(180).start();
        drawerPanel.animate().translationX(0f).setDuration(220).start();
    }

    private void closeDrawer() {
        if (!drawerOpen) return;
        drawerOpen = false;
        drawerScrim.animate().alpha(0f).setDuration(160)
                .withEndAction(() -> drawerScrim.setVisibility(View.GONE)).start();
        drawerPanel.animate().translationX(-dp(304)).setDuration(200).start();
    }

    private class EdgeSwipeLayout extends FrameLayout {
        private float downX;
        private float downY;
        private boolean tracking;

        EdgeSwipeLayout(Context context) {
            super(context);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getX();
                    downY = event.getY();
                    tracking = !drawerOpen && downX < dp(20);
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (!tracking) break;
                    float dx = event.getX() - downX;
                    float dy = event.getY() - downY;
                    if (dx > dp(48) && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                        tracking = false;
                        openDrawer();
                        return true;
                    }
                    break;
                default:
                    tracking = false;
            }
            return false;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return true;
        }
    }

    private Tab addTab(int index) {
        Tab tab = new Tab();
        tab.icon = new ImageView(this);
        tab.icon.setImageDrawable(new IconDrawable(tabPath(index), 0xff8b98a5, dp(2)));
        tab.icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(22), dp(22));
        tab.icon.setLayoutParams(iconParams);

        tab.label = new TextView(this);
        tab.label.setText(TAB_LABELS[index]);
        tab.label.setTextSize(9);
        tab.label.setGravity(Gravity.CENTER);
        tab.label.setTextColor(0xff8b98a5);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER);
        column.addView(tab.icon);
        column.addView(tab.label);

        tab.badge = new TextView(this);
        tab.badge.setTextSize(9);
        tab.badge.setTypeface(Typeface.DEFAULT_BOLD);
        tab.badge.setTextColor(Color.WHITE);
        tab.badge.setGravity(Gravity.CENTER);
        tab.badge.setPadding(dp(4), 0, dp(4), 0);
        tab.badge.setBackground(rounded(BADGE, dp(8), 0));
        tab.badge.setVisibility(View.GONE);
        FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(16), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        badgeParams.setMargins(dp(20), dp(2), 0, 0);
        tab.badge.setLayoutParams(badgeParams);

        tab.container = new FrameLayout(this);
        tab.container.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        tab.container.addView(tab.badge);
        tab.container.setOnClickListener(v -> switchTab(index));
        if ("home".equals(TAB_KEYS[index])) {
            tab.container.setOnLongClickListener(v -> {
                openDrawer();
                return true;
            });
        }
        if ("notifications".equals(TAB_KEYS[index])) {
            tab.container.setOnLongClickListener(v -> {
                runPageAction("window.__guyii&&window.__guyii.mentions&&window.__guyii.mentions();");
                return true;
            });
        }
        tab.container.setLayoutParams(tabSlotParams());
        bottomNav.addView(tab.container);
        return tab;
    }

    private LinearLayout.LayoutParams tabSlotParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(50), dp(46));
        params.setMargins(dp(1), 0, dp(1), 0);
        return params;
    }

    // 抽屉必须有个看得见的入口：手势导航下左边缘属于系统返回，边缘右滑根本到不了 App
    private void addDrawerButton() {
        drawerButtonIcon = new ImageView(this);
        drawerButtonIcon.setImageDrawable(new IconDrawable(personPath(), 0xff8b98a5, dp(2)));
        drawerButtonIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        drawerButtonIcon.setLayoutParams(new LinearLayout.LayoutParams(dp(22), dp(22)));

        drawerButtonLabel = new TextView(this);
        drawerButtonLabel.setText("我");
        drawerButtonLabel.setTextSize(9);
        drawerButtonLabel.setGravity(Gravity.CENTER);
        drawerButtonLabel.setTextColor(0xff8b98a5);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER);
        column.addView(drawerButtonIcon);
        column.addView(drawerButtonLabel);

        FrameLayout container = new FrameLayout(this);
        container.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        container.setOnClickListener(v -> openDrawer());
        container.setLayoutParams(tabSlotParams());
        bottomNav.addView(container);
    }

    private Path personPath() {
        Path path = new Path();
        path.addCircle(12f, 8f, 3.6f, Path.Direction.CW);
        path.moveTo(4.6f, 20.4f);
        path.cubicTo(4.6f, 16.6f, 7.9f, 14.5f, 12f, 14.5f);
        path.cubicTo(16.1f, 14.5f, 19.4f, 16.6f, 19.4f, 20.4f);
        return path;
    }

    private void switchTab(int index) {
        // 再点一次当前 Tab = 回顶 + 软刷新，和原生客户端一致
        boolean reselect = index == activeTab && routeIsRootTab;
        activeTab = index;
        applyTabColors();
        if (reselect) {
            runPageAction("window.__guyii&&window.__guyii.softRefresh&&window.__guyii.softRefresh();");
            return;
        }
        runPageAction("window.__guyii&&window.__guyii.go&&window.__guyii.go('" + TAB_KEYS[index] + "');");
    }

    private int tabIndexForUrl(String url) {
        if (url == null) return -1;
        if (url.contains("/notifications")) return 3;
        if (url.contains("/messages")) return 4;
        if (url.contains("/i/grok")) return 2;
        if (url.contains("/explore") || url.contains("/search")) return 1;
        if (url.contains("/home") || url.endsWith("x.com") || url.endsWith("x.com/")) return 0;
        return -1;
    }

    private void applyTabColors() {
        boolean light = luminance(themeColor) > 0.5;
        int idle = light ? 0xff5b6b78 : 0xff8b98a5;
        for (int i = 0; i < tabs.size(); i++) {
            Tab tab = tabs.get(i);
            boolean on = i == activeTab && routeIsRootTab;
            int color = on ? ACCENT : idle;
            tab.icon.setImageDrawable(new IconDrawable(tabPath(i), color, dp(2)));
            tab.label.setTextColor(color);
            tab.label.setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            tab.container.setBackground(on ? rounded(withAlpha(ACCENT, 0x24), dp(23), 0) : null);
        }
        if (drawerButtonIcon != null) {
            drawerButtonIcon.setImageDrawable(new IconDrawable(personPath(), idle, dp(2)));
            drawerButtonLabel.setTextColor(idle);
        }
    }

    private void setProfileHandle(String handle) {
        if (handle == null || handle.isEmpty() || handle.equals(profileHandle)) return;
        profileHandle = handle;
        drawerHandle.setText("@" + handle);
        drawerAvatar.setText(handle.substring(0, 1).toUpperCase(Locale.US));
    }

    private void setBadges(int notifications, int messages) {
        applyBadge(tabs.get(3).badge, notifications);
        applyBadge(tabs.get(4).badge, messages);
    }

    private void applyBadge(TextView badge, int count) {
        if (count <= 0) {
            badge.setVisibility(View.GONE);
            return;
        }
        badge.setText(count > 99 ? "99+" : String.valueOf(count));
        badge.setVisibility(View.VISIBLE);
    }

    private Path tabPath(int index) {
        switch (index) {
            case 1: return searchPath();
            case 2: return sparklePath();
            case 3: return bellPath();
            case 4: return envelopePath();
            default: return homePath();
        }
    }

    private Path homePath() {
        Path path = new Path();
        path.moveTo(12f, 3.2f);
        path.lineTo(3.5f, 10f);
        path.lineTo(3.5f, 20f);
        path.lineTo(9.5f, 20f);
        path.lineTo(9.5f, 14f);
        path.lineTo(14.5f, 14f);
        path.lineTo(14.5f, 20f);
        path.lineTo(20.5f, 20f);
        path.lineTo(20.5f, 10f);
        path.close();
        return path;
    }

    private Path searchPath() {
        Path path = new Path();
        path.addCircle(11f, 11f, 6.6f, Path.Direction.CW);
        path.moveTo(15.8f, 15.8f);
        path.lineTo(20.5f, 20.5f);
        return path;
    }

    private Path sparklePath() {
        Path path = new Path();
        path.moveTo(12f, 3.4f);
        path.lineTo(14.2f, 8.8f);
        path.lineTo(19.6f, 11f);
        path.lineTo(14.2f, 13.2f);
        path.lineTo(12f, 18.6f);
        path.lineTo(9.8f, 13.2f);
        path.lineTo(4.4f, 11f);
        path.lineTo(9.8f, 8.8f);
        path.close();
        return path;
    }

    private Path bellPath() {
        Path path = new Path();
        path.moveTo(5.2f, 16.8f);
        path.cubicTo(6.6f, 15.2f, 6.6f, 13.4f, 6.6f, 10.4f);
        path.cubicTo(6.6f, 7.4f, 9f, 5f, 12f, 5f);
        path.cubicTo(15f, 5f, 17.4f, 7.4f, 17.4f, 10.4f);
        path.cubicTo(17.4f, 13.4f, 17.4f, 15.2f, 18.8f, 16.8f);
        path.close();
        path.moveTo(10.1f, 19.3f);
        path.cubicTo(10.5f, 20.3f, 11.2f, 20.9f, 12f, 20.9f);
        path.cubicTo(12.8f, 20.9f, 13.5f, 20.3f, 13.9f, 19.3f);
        return path;
    }

    private Path envelopePath() {
        Path path = new Path();
        path.addRoundRect(new RectF(3f, 5f, 21f, 19f), 2.4f, 2.4f, Path.Direction.CW);
        path.moveTo(3.8f, 6.4f);
        path.lineTo(12f, 12.6f);
        path.lineTo(20.2f, 6.4f);
        return path;
    }

    private Path composePath() {
        Path path = new Path();
        path.moveTo(16.5f, 3.6f);
        path.lineTo(20.4f, 7.5f);
        path.lineTo(8.6f, 19.3f);
        path.lineTo(3.5f, 20.5f);
        path.lineTo(4.7f, 15.4f);
        path.close();
        path.moveTo(14.3f, 5.8f);
        path.lineTo(18.2f, 9.7f);
        return path;
    }

    private static class Tab {
        FrameLayout container;
        ImageView icon;
        TextView label;
        TextView badge;
    }

    // 24x24 的矢量路径按 bounds 缩放描边，省掉 AndroidX 的 VectorDrawableCompat
    private static class IconDrawable extends Drawable {
        private final Path source;
        private final Path scaled = new Path();
        private final Matrix matrix = new Matrix();
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float strokeWidth;

        IconDrawable(Path source, int color, float strokeWidth) {
            this.source = source;
            this.strokeWidth = strokeWidth;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(color);
        }

        @Override
        protected void onBoundsChange(android.graphics.Rect bounds) {
            float scale = Math.min(bounds.width(), bounds.height()) / 24f;
            matrix.reset();
            matrix.setScale(scale, scale);
            matrix.postTranslate(bounds.left + (bounds.width() - 24f * scale) / 2f,
                    bounds.top + (bounds.height() - 24f * scale) / 2f);
            source.transform(matrix, scaled);
            paint.setStrokeWidth(Math.max(1f, strokeWidth * scale / 2f));
        }

        @Override
        public void draw(Canvas canvas) {
            canvas.drawPath(scaled, paint);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    private GradientDrawable rounded(int color, int radius, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeColor != 0) drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private void applyPageTheme(int color) {
        if (themeApplied && color == themeColor) return;
        themeApplied = true;
        themeColor = color;
        boolean light = luminance(color) > 0.5;
        int stroke = light ? 0x33000000 : 0x22ffffff;
        // pill 浮在正文上，必须完全不透明；再和页面底色拉开一档才看得出边界
        int pill = light ? mix(color, Color.BLACK, 0.06f) : mix(color, Color.WHITE, 0.10f);
        root.setBackgroundColor(color);
        bottomNav.setBackground(rounded(pill, dp(28), stroke));
        applyTabColors();
        if (drawerPanel != null) {
            drawerPanel.setBackgroundColor(color);
            int drawerText = light ? 0xff0f1419 : 0xffffffff;
            for (TextView view : drawerTexts) view.setTextColor(drawerText);
            updateThemeChips();
            updateLayoutChips();
        }
        if (shortcutsPanel != null) {
            shortcutsPanel.setBackgroundColor(color);
            int panelText = light ? 0xff0f1419 : 0xffffffff;
            shortcutsTitle.setTextColor(panelText);
            for (TextView view : shortcutsTexts) view.setTextColor(panelText);
        }
        Window window = getWindow();
        window.setStatusBarColor(color);
        window.setNavigationBarColor(color);
        setLightSystemBars(light);
    }

    private void applyImmersive() {
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.view.WindowInsetsController controller = window.getInsetsController();
            if (controller == null) return;
            controller.setSystemBarsBehavior(
                    android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            if (immersive) controller.hide(WindowInsets.Type.statusBars());
            else controller.show(WindowInsets.Type.statusBars());
        } else {
            View decor = window.getDecorView();
            int flags = decor.getSystemUiVisibility();
            int mask = View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE;
            if (immersive) flags |= mask;
            else flags &= ~mask;
            decor.setSystemUiVisibility(flags);
        }
    }

    private void setLightSystemBars(boolean light) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.view.WindowInsetsController controller = getWindow().getInsetsController();
            if (controller == null) return;
            int mask = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            controller.setSystemBarsAppearance(light ? mask : 0, mask);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            View decor = getWindow().getDecorView();
            int flags = decor.getSystemUiVisibility();
            if (light) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            else flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            decor.setSystemUiVisibility(flags);
        }
    }

    private double luminance(int color) {
        return (0.2126 * Color.red(color) + 0.7152 * Color.green(color) + 0.0722 * Color.blue(color)) / 255.0;
    }

    private int mix(int color, int target, float ratio) {
        return Color.rgb(
                clamp((int) (Color.red(color) * (1 - ratio) + Color.red(target) * ratio)),
                clamp((int) (Color.green(color) * (1 - ratio) + Color.green(target) * ratio)),
                clamp((int) (Color.blue(color) * (1 - ratio) + Color.blue(target) * ratio)));
    }

    private int shade(int color, float factor) {
        return Color.rgb(
                clamp((int) (Color.red(color) * factor)),
                clamp((int) (Color.green(color) * factor)),
                clamp((int) (Color.blue(color) * factor)));
    }

    private int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00ffffff);
    }

    private int parseCssColor(String css) {
        if (css == null) return Color.TRANSPARENT;
        int open = css.indexOf('(');
        int close = css.lastIndexOf(')');
        if (open < 0 || close <= open) return Color.TRANSPARENT;
        String[] parts = css.substring(open + 1, close).split(",");
        if (parts.length < 3) return Color.TRANSPARENT;
        try {
            int r = (int) Float.parseFloat(parts[0].trim());
            int g = (int) Float.parseFloat(parts[1].trim());
            int b = (int) Float.parseFloat(parts[2].trim());
            return Color.rgb(clamp(r), clamp(g), clamp(b));
        } catch (NumberFormatException e) {
            return Color.TRANSPARENT;
        }
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private void runPageAction(String js) {
        webView.evaluateJavascript(js, null);
    }

    private void installInsetsListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    android.graphics.Insets bars =
                            insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
                    systemTopInset = bars.top;
                    systemBottomInset = bars.bottom;
                } else {
                    systemTopInset = insets.getSystemWindowInsetTop();
                    systemBottomInset = insets.getSystemWindowInsetBottom();
                }
                positionOverlay();
                return insets;
            });
        }
    }

    private void setComposerState(boolean dialogOpen, boolean canSubmit) {
        composerDialogOpen = dialogOpen;
        canSubmitComposer = canSubmit;
        updatePublishButton();
        positionOverlay();
    }

    private void updatePublishButton() {
        boolean fullscreen = fullscreenView != null;
        boolean showPublish = canSubmitComposer && !fullscreen;
        publishButton.setVisibility(showPublish ? View.VISIBLE : View.GONE);
        composeFab.setVisibility(
                !showPublish && !composerDialogOpen && !fullscreen && routeIsRootTab ? View.VISIBLE : View.GONE);
        bottomNav.setVisibility(!composerDialogOpen && !fullscreen ? View.VISIBLE : View.GONE);
    }

    private void setOverlayVisible(boolean visible) {
        if (!visible) {
            bottomNav.setVisibility(View.GONE);
            publishButton.setVisibility(View.GONE);
            composeFab.setVisibility(View.GONE);
            return;
        }
        updatePublishButton();
    }

    private void positionOverlay() {
        int bottom = Math.max(systemBottomInset, 0);
        int top = fullscreenView == null ? Math.max(systemTopInset, 0) : 0;
        updateBottomMargin(bottomNav, dp(12) + bottom);
        updateBottomMargin(publishButton, dp(82) + bottom);
        updateBottomMargin(composeFab, dp(82) + bottom);
        int reserved = barMode == 1 ? dp(BAR_HEIGHT_DP) : 0;
        updateBottomMargin(webView, fullscreenView == null ? reserved + bottom : 0);
        updateTopMargin(webView, top);
        updateTopMargin(progressBar, top);
        pushChrome();
        if (drawerPanel != null) {
            drawerPanel.setPadding(0, Math.max(systemTopInset, 0), 0, Math.max(systemBottomInset, 0));
        }
        if (shortcutsPanel != null) {
            shortcutsPanel.setPadding(0, Math.max(systemTopInset, 0), 0, Math.max(systemBottomInset, 0));
        }
    }

    // 原生是布局常量的唯一真源：把底栏占的高度下发给页面，让它在文档末尾补 padding
    private void pushChrome() {
        // 常驻模式下 WebView 已经让出了位置，页面不需要再补 padding
        int bottomDp = barMode == 1 ? 0 : BAR_HEIGHT_DP + px2dp(Math.max(systemBottomInset, 0));
        runPageAction("window.__guyii&&window.__guyii.setChrome&&window.__guyii.setChrome(0,"
                + bottomDp + ");");
    }

    private int px2dp(int px) {
        return (int) (px / getResources().getDisplayMetrics().density + 0.5f);
    }

    private void installScrollListener() {
        webView.setOnScrollChangeListener((v, x, y, oldX, oldY) -> {
            if (barMode != 0 || drawerOpen || shortcutsOpen || composerDialogOpen) return;
            if (y <= dp(8)) {
                setBarVisible(true);
                return;
            }
            int dy = y - oldY;
            if (dy > dp(3)) setBarVisible(false);
            else if (dy < -dp(10)) setBarVisible(true);
        });
    }

    private void setBarVisible(boolean visible) {
        if (barVisible == visible) return;
        barVisible = visible;
        float offset = visible ? 0f : dp(BAR_HEIGHT_DP) + Math.max(systemBottomInset, 0);
        bottomNav.animate().translationY(offset).alpha(visible ? 1f : 0f).setDuration(160).start();
        composeFab.animate().translationY(offset).alpha(visible ? 1f : 0f).setDuration(160).start();
        publishButton.animate().translationY(offset).alpha(visible ? 1f : 0f).setDuration(160).start();
    }

    private void applyBarMode() {
        if (barMode == 1) setBarVisible(true);
        positionOverlay();
    }

    private void updateTopMargin(View view, int margin) {
        ViewGroup.LayoutParams raw = view.getLayoutParams();
        if (!(raw instanceof FrameLayout.LayoutParams)) return;
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) raw;
        if (params.topMargin == margin) return;
        params.topMargin = margin;
        view.setLayoutParams(params);
    }

    private void updateBottomMargin(View view, int margin) {
        ViewGroup.LayoutParams raw = view.getLayoutParams();
        if (!(raw instanceof FrameLayout.LayoutParams)) return;
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) raw;
        params.bottomMargin = margin;
        view.setLayoutParams(params);
    }

    private String resolveStartUrl() {
        Uri data = getIntent() == null ? null : getIntent().getData();
        if (data != null && isXHost(data)) return data.toString();
        return START_URL;
    }

    private boolean isXHost(Uri uri) {
        String host = uri.getHost();
        if (host == null) return false;
        host = host.toLowerCase(Locale.US);
        return host.equals("x.com")
                || host.endsWith(".x.com")
                || host.equals("twitter.com")
                || host.endsWith(".twitter.com")
                || host.equals("t.co");
    }

    private String chromeLikeUserAgent() {
        return "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36";
    }

    private void injectShortcuts() {
        try {
            webView.evaluateJavascript(readAsset("injected-shortcuts.js"), null);
        } catch (IOException e) {
            Toast.makeText(this, "快捷键脚本加载失败", Toast.LENGTH_SHORT).show();
        }
    }

    private String readAsset(String name) throws IOException {
        InputStream input = getAssets().open(name);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
        input.close();
        return output.toString("UTF-8");
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event);
        int key = event.getKeyCode();

        if ((event.isCtrlPressed() || event.isAltPressed()) && key == KeyEvent.KEYCODE_ENTER && canSubmitComposer) {
            runPageAction("window.__guyii&&window.__guyii.submit&&window.__guyii.submit();");
            return true;
        }
        if (key == KeyEvent.KEYCODE_F5 || (event.isCtrlPressed() && key == KeyEvent.KEYCODE_R)) {
            webView.reload();
            return true;
        }
        if (event.isAltPressed() && key == KeyEvent.KEYCODE_DPAD_LEFT) {
            goBackOrHome();
            return true;
        }
        if (event.isAltPressed() && key == KeyEvent.KEYCODE_DPAD_RIGHT && webView.canGoForward()) {
            webView.goForward();
            return true;
        }
        if (event.isCtrlPressed() && key == KeyEvent.KEYCODE_L) {
            webView.loadUrl(SEARCH_URL);
            return true;
        }
        if (event.isCtrlPressed() && (key == KeyEvent.KEYCODE_PLUS || key == KeyEvent.KEYCODE_EQUALS)) {
            setTextZoom(textZoom + 10);
            return true;
        }
        if (event.isCtrlPressed() && key == KeyEvent.KEYCODE_MINUS) {
            setTextZoom(textZoom - 10);
            return true;
        }
        if (event.isCtrlPressed() && key == KeyEvent.KEYCODE_0) {
            setTextZoom(100);
            return true;
        }
        if (key == KeyEvent.KEYCODE_ESCAPE) {
            if (shortcutsOpen) {
                closeShortcuts();
                return true;
            }
            if (drawerOpen) {
                closeDrawer();
                return true;
            }
            if (fullscreenView != null) {
                hideFullscreen();
                return true;
            }
            if (composerDialogOpen) {
                runPageAction("window.__guyii&&window.__guyii.dismiss&&window.__guyii.dismiss();");
                return true;
            }
            if (webView.canGoBack()) {
                webView.goBack();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void setTextZoom(int value) {
        textZoom = Math.max(70, Math.min(150, value));
        webView.getSettings().setTextZoom(textZoom);
        if (zoomValue != null) zoomValue.setText(textZoom + "%");
        Toast.makeText(this, "Zoom " + textZoom + "%", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBackPressed() {
        if (shortcutsOpen) closeShortcuts();
        else if (drawerOpen) closeDrawer();
        else if (fullscreenView != null) hideFullscreen();
        else if (composerDialogOpen) runPageAction("window.__guyii&&window.__guyii.dismiss&&window.__guyii.dismiss();");
        else goBackOrHome();
    }

    private void goBackOrHome() {
        if (webView.canGoBack()) {
            webView.goBack();
            return;
        }
        confirmExit();
    }

    private void confirmExit() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastBackPressAt < EXIT_CONFIRM_WINDOW_MS) {
            finish();
            return;
        }
        lastBackPressAt = now;
        Toast.makeText(this, "再按一次退出", Toast.LENGTH_SHORT).show();
    }

    private boolean isHomeUrl(String url) {
        if (url == null) return false;
        return url.startsWith(HOME_URL)
                || url.equals(START_URL)
                || url.equals(START_URL + "/");
    }

    private void showErrorPage() {
        webView.loadUrl("file:///android_asset/error.html");
    }

    private void openExternal(Uri uri) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, uri.toString(), Toast.LENGTH_LONG).show();
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private boolean hasNetwork() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null) return true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
            return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        }
        return true;
    }

    private String[] permissionsForResources(String[] resources) {
        Set<String> out = new HashSet<>();
        for (String resource : resources) {
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)) out.add(Manifest.permission.CAMERA);
            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) out.add(Manifest.permission.RECORD_AUDIO);
        }
        return out.toArray(new String[0]);
    }

    private boolean hasAllPermissions(String[] permissions) {
        for (String permission : permissions) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    && checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private class XWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            if (isXHost(uri) || "file".equals(uri.getScheme())) return false;
            openExternal(uri);
            return true;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            progressBar.setVisibility(View.VISIBLE);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            CookieManager.getInstance().flush();
            progressBar.setVisibility(View.GONE);
            if (url != null && url.startsWith("https://")) injectShortcuts();
            // x.com -> /home 的重定向会留下一条历史，不清掉返回键会在两者之间打转
            if (!initialHistoryCleared && isHomeUrl(url)) {
                initialHistoryCleared = true;
                view.clearHistory();
            }
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (request.isForMainFrame() && !hasNetwork()) showErrorPage();
        }
    }

    private class XWebChromeClient extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            progressBar.setProgress(newProgress);
            progressBar.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
        }

        @Override
        public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
            if (fileChooserCallback != null) fileChooserCallback.onReceiveValue(null);
            fileChooserCallback = filePathCallback;
            Intent intent = fileChooserParams.createIntent();
            try {
                startActivityForResult(intent, FILE_CHOOSER_REQUEST);
            } catch (ActivityNotFoundException e) {
                fileChooserCallback = null;
                Toast.makeText(MainActivity.this, "没有可用的文件选择器", Toast.LENGTH_SHORT).show();
                return false;
            }
            return true;
        }

        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            if (fullscreenView != null) {
                callback.onCustomViewHidden();
                return;
            }
            fullscreenView = view;
            fullscreenCallback = callback;
            fullscreenContainer = new FrameLayout(MainActivity.this);
            fullscreenContainer.setBackgroundColor(Color.BLACK);
            fullscreenContainer.addView(view, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            root.addView(fullscreenContainer, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            webView.setVisibility(View.GONE);
            setOverlayVisible(false);
            positionOverlay();
        }

        @Override
        public void onHideCustomView() {
            hideFullscreen();
        }

        @Override
        public void onPermissionRequest(PermissionRequest request) {
            String[] permissions = permissionsForResources(request.getResources());
            if (permissions.length == 0 || hasAllPermissions(permissions)) {
                request.grant(request.getResources());
                return;
            }
            pendingPermissionRequest = request;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) requestPermissions(permissions, WEB_PERMISSION_REQUEST);
            else request.grant(request.getResources());
        }

        @Override
        public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
            String[] permissions = new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION};
            if (hasAllPermissions(permissions)) {
                callback.invoke(origin, true, false);
                return;
            }
            pendingGeoOrigin = origin;
            pendingGeoCallback = callback;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) requestPermissions(permissions, WEB_PERMISSION_REQUEST);
            else callback.invoke(origin, true, false);
        }
    }

    private void hideFullscreen() {
        if (fullscreenView == null) return;
        root.removeView(fullscreenContainer);
        fullscreenContainer = null;
        fullscreenView = null;
        webView.setVisibility(View.VISIBLE);
        setOverlayVisible(true);
        positionOverlay();
        if (fullscreenCallback != null) fullscreenCallback.onCustomViewHidden();
        fullscreenCallback = null;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != FILE_CHOOSER_REQUEST || fileChooserCallback == null) return;
        Uri[] results = null;
        if (resultCode == RESULT_OK && data != null) {
            if (data.getClipData() != null) {
                int count = data.getClipData().getItemCount();
                List<Uri> uris = new ArrayList<>();
                for (int i = 0; i < count; i++) uris.add(data.getClipData().getItemAt(i).getUri());
                results = uris.toArray(new Uri[0]);
            } else if (data.getData() != null) {
                results = new Uri[]{data.getData()};
            }
        }
        fileChooserCallback.onReceiveValue(results);
        fileChooserCallback = null;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != WEB_PERMISSION_REQUEST) return;
        boolean granted = true;
        for (int result : grantResults) granted = granted && result == PackageManager.PERMISSION_GRANTED;
        if (pendingPermissionRequest != null) {
            if (granted) pendingPermissionRequest.grant(pendingPermissionRequest.getResources());
            else pendingPermissionRequest.deny();
            pendingPermissionRequest = null;
        }
        if (pendingGeoCallback != null) {
            pendingGeoCallback.invoke(pendingGeoOrigin, granted, false);
            pendingGeoCallback = null;
            pendingGeoOrigin = null;
        }
    }

    private class XDownloadListener implements DownloadListener {
        @Override
        public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setMimeType(mimeType);
            request.addRequestHeader("User-Agent", userAgent);
            request.addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url));
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            String filename = URLUtil.guessFileName(url, contentDisposition, mimeType);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);
            DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            if (manager != null) {
                manager.enqueue(request);
                Toast.makeText(MainActivity.this, "开始下载 " + filename, Toast.LENGTH_SHORT).show();
            }
        }
    }

    public class ShortcutBridge {
        @JavascriptInterface
        public void reload() {
            runOnUiThread(() -> webView.reload());
        }

        @JavascriptInterface
        public void composerChanged(boolean visible, boolean canSubmit) {
            runOnUiThread(() -> setComposerState(visible, canSubmit));
        }

        @JavascriptInterface
        public void routeChanged(String url, String title, boolean isRootTab) {
            runOnUiThread(() -> {
                routeIsRootTab = isRootTab;
                int index = tabIndexForUrl(url);
                if (index >= 0) activeTab = index;
                applyTabColors();
                updatePublishButton();
            });
        }

        @JavascriptInterface
        public void profileChanged(String handle) {
            runOnUiThread(() -> setProfileHandle(handle));
        }

        @JavascriptInterface
        public void barVisible(boolean show) {
            runOnUiThread(() -> {
                if (barMode != 0 || drawerOpen || shortcutsOpen || composerDialogOpen) return;
                setBarVisible(show);
            });
        }

        @JavascriptInterface
        public void zoom(int delta) {
            runOnUiThread(() -> setTextZoom(delta == 0 ? 100 : textZoom + delta));
        }

        @JavascriptInterface
        public void badgeChanged(int notifications, int messages) {
            runOnUiThread(() -> setBadges(notifications, messages));
        }

        @JavascriptInterface
        public void themeChanged(String cssColor) {
            int color = parseCssColor(cssColor);
            if (color == Color.TRANSPARENT) return;
            runOnUiThread(() -> applyPageTheme(color));
        }

        @JavascriptInterface
        public void openSettings() {
            runOnUiThread(() -> openExternal(Uri.parse(Settings.ACTION_APPLICATION_DETAILS_SETTINGS + ":" + getPackageName())));
        }

        @JavascriptInterface
        public void toast(String text) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, text, Toast.LENGTH_SHORT).show());
        }
    }
}
