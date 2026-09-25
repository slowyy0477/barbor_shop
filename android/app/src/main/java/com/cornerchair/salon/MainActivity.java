package com.cornerchair.salon;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceError;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.ValueCallback;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.drawable.GradientDrawable;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;

import com.cornerchair.salon.security.NavigationPolicy;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Native Android shell for the offline-first salon experience. */
public class MainActivity extends Activity {
    // Release APKs use a separate localStorage namespace and never expose seeded QA records.
    private static final String START_URL = "file:///android_asset/index.html?mode=release";
    /**
     * The owner can point the installed app at the salon server (their own laptop
     * behind an HTTPS tunnel) without rebuilding the APK. The address is public
     * configuration, never a credential, and is stored per device.
     */
    private static final String SHELL_PREFERENCES = "ayan_shell";
    private static final String PREFERENCE_SERVER_URL = "server_url";
    /**
     * The salon laptop republishes its current HTTPS tunnel address in this tiny
     * public file (never a credential) so a freshly installed phone can find the
     * salon without the owner typing an address.
     */
    private static final String PUBLISHED_CONFIG_URL = "https://slowyy0477.github.io/barbor_shop/api.json";
    /** Address this phone found by itself; an owner-typed address always wins over it. */
    private static final String PREFERENCE_DISCOVERED_SERVER_URL = "discovered_server_url";
    /** True when the owner deliberately chose offline mode on this phone. */
    private static final String PREFERENCE_OFFLINE_CHOICE = "offline_choice";
    private static final int MAX_CONFIG_BYTES = 2048;
    /** Owner-chosen salon name, applied to the launch screen and home-screen icon. */
    private static final String PREFERENCE_SALON_NAME = "salon_name";
    private static final String SALON_LOGO_FILE = "salon-logo.png";
    private static final String SALON_SHORTCUT_ID = "salon-home";
    private static final int SHORTCUT_ICON_PX = 192;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WebView webView;
    private View splash;
    private boolean splashDismissed;
    /** Set once per launch so a dead server cannot loop between remote and bundled pages. */
    private boolean bundledFallbackShown;
    /** Pending HTML file input callback, if the owner opened an image chooser. */
    private ValueCallback<Uri[]> filePathCallback;
    private static final int FILE_CHOOSER_REQUEST_CODE = 4107;
    /** Volatile because JavaScript bridge calls are dispatched off the UI thread. */
    private volatile boolean trustedPageLoaded;
    /** Per-document capability; never reused across WebView navigations. */
    private volatile String bridgeToken;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(30, 30, 32));
        getWindow().setNavigationBarColor(Color.rgb(30, 30, 32));

        FrameLayout root = new FrameLayout(this);
        webView = createWebView();
        splash = createSplash();
        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(splash, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        webView.loadUrl(startUrl());
        // A freshly installed phone connects on its own: the salon laptop publishes
        // its current address, so the owner never has to type one.
        refreshDiscoveredServerUrl();
        // A timeout keeps a slow or unavailable WebView from leaving the launch screen up forever.
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                dismissSplash();
            }
        }, 1800L);
    }

    private WebView createWebView() {
        WebView view = new WebView(this);
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        // Owner image uploads arrive as read-only content:// URIs from the
        // system picker. The WebView still cannot reach arbitrary local files.
        settings.setAllowContentAccess(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setTextZoom(100);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            // Prevent a file page from reaching arbitrary local files or network origins.
            settings.setAllowFileAccessFromFileURLs(false);
            settings.setAllowUniversalAccessFromFileURLs(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(true);
        }
        view.setOverScrollMode(View.OVER_SCROLL_NEVER);
        view.setBackgroundColor(Color.rgb(229, 229, 229));
        view.addJavascriptInterface(new SalonBridge(this), "AndroidSalon");
        view.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(
                    WebView webView,
                    ValueCallback<Uri[]> callback,
                    FileChooserParams fileChooserParams) {
                // Chromium permits only one active chooser. Resolve a stale callback
                // before replacing it so an abandoned request cannot leak a URI.
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                }
                filePathCallback = callback;

                Intent chooserIntent;
                try {
                    chooserIntent = fileChooserParams.createIntent();
                } catch (RuntimeException ignored) {
                    chooserIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    chooserIntent.addCategory(Intent.CATEGORY_OPENABLE);
                    chooserIntent.setType("image/*");
                }
                // Branding and haircut inputs are images. Keep the chooser constrained
                // even if a browser supplies a broad or malformed accept attribute.
                chooserIntent.setType("image/*");
                chooserIntent.addCategory(Intent.CATEGORY_OPENABLE);
                chooserIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                if (fileChooserParams.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) {
                    chooserIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                }
                try {
                    startActivityForResult(chooserIntent, FILE_CHOOSER_REQUEST_CODE);
                    return true;
                } catch (RuntimeException ignored) {
                    filePathCallback.onReceiveValue(null);
                    filePathCallback = null;
                    return false;
                }
            }
        });
        view.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                // A bridge call is trusted only after the bundled page finishes loading.
                trustedPageLoaded = false;
                bridgeToken = null;
                if (!isTrustedPageUrl(url)) {
                    view.stopLoading();
                    openExternalIfNeeded(parseUri(url));
                }
                super.onPageStarted(view, url, favicon);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (!isTrustedPageUrl(url)) {
                    return;
                }
                trustedPageLoaded = true;
                bridgeToken = UUID.randomUUID().toString();
                view.evaluateJavascript(apiConfigScript(url), null);
                // Expose native share/copy hooks without requiring a third-party SDK.
                view.evaluateJavascript(nativeBridgeScript(bridgeToken), null);
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        dismissSplash();
                    }
                }, 350L);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                // A remote server that is switched off or offline must not leave the
                // customer on a browser error page. Fall back to the bundled copy,
                // which still supports offline records, and say so plainly.
                if (request != null && request.isForMainFrame()) {
                    fallBackToBundledAssets();
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) {
                    return false;
                }
                return openExternalIfNeeded(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return openExternalIfNeeded(parseUri(url));
            }
        });
        return view;
    }

    private static Uri parseUri(String rawUrl) {
        return rawUrl == null ? null : Uri.parse(rawUrl);
    }

    private boolean openExternalIfNeeded(Uri uri) {
        if (uri == null) {
            return true;
        }
        String rawUrl = uri.toString();
        if (isTrustedPageUrl(rawUrl)) {
            trustedPageLoaded = false;
            bridgeToken = null;
            return false;
        }
        // Unknown schemes (including javascript:) are blocked instead of delegated blindly.
        if (!NavigationPolicy.isKnownExternalUrl(rawUrl)) {
            return true;
        }
        // Revoke native capabilities before handing the navigation to another app.
        trustedPageLoaded = false;
        bridgeToken = null;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
            return true;
        } catch (Exception ignored) {
            // Keep the unhandled URL out of the WebView even when no external app is installed.
            return true;
        }
    }

    /**
     * Normalizes an owner-supplied server address. Only a bare HTTPS origin is
     * accepted: no credentials, query string, fragment or path, so a mistyped
     * value can never smuggle anything into the WebView origin.
     */
    private static String normalizeServerUrl(String rawValue) {
        if (rawValue == null) {
            return "";
        }
        String candidate = rawValue.trim().replaceAll("/+$", "");
        if (candidate.isEmpty()) {
            return "";
        }
        try {
            URI parsed = new URI(candidate);
            if (!"https".equalsIgnoreCase(parsed.getScheme())) {
                return "";
            }
            if (parsed.getHost() == null || parsed.getHost().isEmpty()) {
                return "";
            }
            if (parsed.getUserInfo() != null || parsed.getQuery() != null || parsed.getFragment() != null) {
                return "";
            }
            if (parsed.getPath() != null && !parsed.getPath().isEmpty() && !"/".equals(parsed.getPath())) {
                return "";
            }
            return candidate;
        } catch (URISyntaxException ignored) {
            return "";
        }
    }

    private String savedServerUrl() {
        try {
            return normalizeServerUrl(getSharedPreferences(SHELL_PREFERENCES, MODE_PRIVATE)
                    .getString(PREFERENCE_SERVER_URL, ""));
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    /**
     * An address the owner typed on this phone wins, then a value baked at build
     * time, then the address this phone looked up for itself.
     */
    private String resolvedServerUrl() {
        String saved = savedServerUrl();
        if (!saved.isEmpty()) {
            return saved;
        }
        String baked = normalizeServerUrl(BuildConfig.API_BASE_URL == null ? "" : BuildConfig.API_BASE_URL.trim());
        if (!baked.isEmpty()) {
            return baked;
        }
        return discoveredServerUrl();
    }

    private String discoveredServerUrl() {
        try {
            return normalizeServerUrl(getSharedPreferences(SHELL_PREFERENCES, MODE_PRIVATE)
                    .getString(PREFERENCE_DISCOVERED_SERVER_URL, ""));
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private boolean offlineChoiceStored() {
        try {
            return getSharedPreferences(SHELL_PREFERENCES, MODE_PRIVATE)
                    .getBoolean(PREFERENCE_OFFLINE_CHOICE, false);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    /**
     * Looks up the address the salon published so a just-installed phone reaches
     * the salon without any typing. It runs off the UI thread, is time-boxed and
     * stays silent when it fails, so a phone without internet simply stays
     * offline. A phone where the owner typed an address or chose offline mode is
     * never overridden.
     */
    private void refreshDiscoveredServerUrl() {
        if (!savedServerUrl().isEmpty() || offlineChoiceStored()) {
            return;
        }
        final String current = resolvedServerUrl();
        Thread lookup = new Thread(new Runnable() {
            @Override
            public void run() {
                final String found = fetchPublishedServerUrl();
                if (found.isEmpty() || found.equals(current)) {
                    return;
                }
                try {
                    getSharedPreferences(SHELL_PREFERENCES, MODE_PRIVATE).edit()
                            .putString(PREFERENCE_DISCOVERED_SERVER_URL, found).apply();
                } catch (RuntimeException ignored) {
                    return;
                }
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (webView != null && !isFinishing()) {
                            bundledFallbackShown = false;
                            webView.loadUrl(startUrl());
                        }
                    }
                });
            }
        }, "salon-address-lookup");
        lookup.setDaemon(true);
        lookup.start();
    }

    /** Reads apiBaseUrl from the small config file the salon laptop publishes. */
    private static String fetchPublishedServerUrl() {
        HttpURLConnection connection = null;
        InputStream stream = null;
        try {
            URL url = new URL(PUBLISHED_CONFIG_URL + "?t=" + System.currentTimeMillis());
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(4000);
            connection.setReadTimeout(4000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("Accept", "application/json");
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return "";
            }
            stream = connection.getInputStream();
            byte[] buffer = new byte[MAX_CONFIG_BYTES];
            int total = 0;
            int read;
            while (total < buffer.length && (read = stream.read(buffer, total, buffer.length - total)) > 0) {
                total += read;
            }
            if (total <= 0) {
                return "";
            }
            String body = new String(buffer, 0, total, "UTF-8");
            Matcher matcher = Pattern.compile("\"apiBaseUrl\"\\s*:\\s*\"([^\"]{0,256})\"").matcher(body);
            return matcher.find() ? normalizeServerUrl(matcher.group(1)) : "";
        } catch (Exception ignored) {
            return "";
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException ignored) {
                    // Nothing left to close.
                }
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private boolean isRemotePageUrl(String rawUrl) {
        String configured = resolvedServerUrl();
        if (configured.isEmpty()) {
            return false;
        }
        Uri page = parseUri(rawUrl);
        Uri origin = parseUri(configured);
        if (page == null || origin == null || !"https".equalsIgnoreCase(page.getScheme())
                || !origin.getHost().equalsIgnoreCase(page.getHost())) {
            return false;
        }
        int originPort = origin.getPort() < 0 ? 443 : origin.getPort();
        int pagePort = page.getPort() < 0 ? 443 : page.getPort();
        return originPort == pagePort;
    }

    private void fallBackToBundledAssets() {
        if (bundledFallbackShown || webView == null) {
            return;
        }
        String current = webView.getUrl();
        if (!isRemotePageUrl(current)) {
            // Already on the bundled copy (or a non-remote page); nothing to recover.
            return;
        }
        bundledFallbackShown = true;
        Toast.makeText(this, "Salon server is not reachable - working offline", Toast.LENGTH_LONG).show();
        webView.loadUrl(START_URL + "&offline=1");
        // The salon may have moved to a fresh tunnel address while this phone was
        // offline, so look the published address up once more.
        refreshDiscoveredServerUrl();
    }

    /** Applies a new address and immediately reloads into it (or back to bundled mode). */
    private void applyServerUrl(String rawValue) {
        String normalized = normalizeServerUrl(rawValue);
        try {
            getSharedPreferences(SHELL_PREFERENCES, MODE_PRIVATE).edit()
                    .putString(PREFERENCE_SERVER_URL, normalized)
                    // An empty address means the owner chose offline mode, which
                    // stops this phone from looking the salon up again.
                    .putBoolean(PREFERENCE_OFFLINE_CHOICE, normalized.isEmpty())
                    .apply();
        } catch (RuntimeException ignored) {
            // A device that cannot persist the choice still gets the current session.
        }
        bundledFallbackShown = false;
        if (webView != null) {
            webView.loadUrl(startUrl());
        }
    }

    private String savedSalonName() {
        try {
            return sanitizeBrandingName(getSharedPreferences(SHELL_PREFERENCES, MODE_PRIVATE)
                    .getString(PREFERENCE_SALON_NAME, ""));
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    /** Owner text becomes a label, so control characters and runaway length are dropped. */
    private static String sanitizeBrandingName(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.replaceAll("\\p{Cntrl}", " ").trim().replaceAll("\\s{2,}", " ");
        return cleaned.length() <= 40 ? cleaned : cleaned.substring(0, 40);
    }

    private static String initialsFor(String name) {
        String cleaned = sanitizeBrandingName(name);
        if (cleaned.isEmpty()) {
            return "AB";
        }
        StringBuilder initials = new StringBuilder();
        for (String part : cleaned.split(" ")) {
            if (part.isEmpty()) {
                continue;
            }
            initials.append(Character.toUpperCase(part.charAt(0)));
            if (initials.length() == 2) {
                break;
            }
        }
        return initials.length() == 0 ? "AB" : initials.toString();
    }

    private File salonLogoFile() {
        return new File(getFilesDir(), SALON_LOGO_FILE);
    }

    private Bitmap loadSalonLogo() {
        try {
            File file = salonLogoFile();
            return file.isFile() ? BitmapFactory.decodeFile(file.getAbsolutePath()) : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /**
     * Accepts only a base64 {@code data:image/...} value of a sane size. Anything
     * else is ignored so a malformed value can never reach the filesystem.
     */
    private static Bitmap decodeLogoDataUrl(String dataUrl) {
        if (dataUrl == null) {
            return null;
        }
        String value = dataUrl.trim();
        int comma = value.indexOf(',');
        if (comma < 0 || !value.startsWith("data:image/")
                || !value.substring(0, comma).toLowerCase(java.util.Locale.US).contains(";base64")) {
            return null;
        }
        try {
            byte[] bytes = Base64.decode(value.substring(comma + 1), Base64.DEFAULT);
            if (bytes.length == 0 || bytes.length > 4 * 1024 * 1024) {
                return null;
            }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /**
     * Stores the owner's salon name and logo for this device. The launch screen
     * uses them from the next start, and a matching home-screen icon is offered
     * because Android cannot rename an installed app's own launcher entry.
     */
    boolean applySalonBranding(String rawName, String logoDataUrl) {
        String name = sanitizeBrandingName(rawName);
        if (name.isEmpty()) {
            return false;
        }
        Bitmap logo = decodeLogoDataUrl(logoDataUrl);
        try {
            getSharedPreferences(SHELL_PREFERENCES, MODE_PRIVATE).edit()
                    .putString(PREFERENCE_SALON_NAME, name).apply();
        } catch (RuntimeException ignored) {
            // A device that cannot persist branding still gets the shortcut below.
        }
        if (logo != null) {
            try (FileOutputStream stream = new FileOutputStream(salonLogoFile())) {
                logo.compress(Bitmap.CompressFormat.PNG, 100, stream);
            } catch (IOException | RuntimeException ignored) {
                // Keeping the previous logo is better than losing the name.
            }
        }
        // This bridge call arrives off the UI thread, so the launcher hand-off is
        // posted back to it before touching the shortcut service.
        final String shortcutName = name;
        final Bitmap shortcutLogo = logo;
        handler.post(new Runnable() {
            @Override
            public void run() {
                publishSalonShortcut(shortcutName, shortcutLogo);
            }
        });
        return true;
    }

    /**
     * Runs on the UI thread. Pinning a shortcut exists only from Android 8.0, so
     * older phones keep the saved name and logo for the launch screen and pick
     * the home-screen icon up as soon as their launcher can offer it.
     */
    private void publishSalonShortcut(String name, Bitmap logo) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
            return;
        }
        try {
            ShortcutManager manager = getSystemService(ShortcutManager.class);
            if (manager == null) {
                return;
            }
            Intent launch = new Intent(this, MainActivity.class);
            launch.setAction(Intent.ACTION_MAIN);
            launch.addCategory(Intent.CATEGORY_LAUNCHER);
            ShortcutInfo.Builder builder = new ShortcutInfo.Builder(this, SALON_SHORTCUT_ID)
                    .setShortLabel(name.length() > 10 ? name.substring(0, 10) : name)
                    .setLongLabel(name)
                    .setIntent(launch)
                    .setIcon(logo == null
                            ? Icon.createWithResource(this, R.drawable.ic_salon)
                            : Icon.createWithBitmap(Bitmap.createScaledBitmap(logo, SHORTCUT_ICON_PX, SHORTCUT_ICON_PX, true)));
            ShortcutInfo shortcut = builder.build();
            for (ShortcutInfo pinned : manager.getPinnedShortcuts()) {
                if (SALON_SHORTCUT_ID.equals(pinned.getId())) {
                    manager.updateShortcuts(java.util.Collections.singletonList(shortcut));
                    return;
                }
            }
            // ShortcutManager#requestPinShortcut arrived in Android 8.0; calling it
            // on 7.1 would fail at runtime, so the newer path stays guarded.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.isRequestPinShortcutSupported()) {
                manager.requestPinShortcut(shortcut, null);
            }
        } catch (RuntimeException ignored) {
            // A launcher without shortcut support must not break the branding save.
        }
    }

    private View createSplash() {
        FrameLayout backdrop = new FrameLayout(this);
        backdrop.setBackgroundColor(Color.rgb(229, 229, 229));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        int horizontal = dp(28);
        content.setPadding(horizontal, 0, horizontal, 0);

        // Nothing about the salon is compiled in: the launch screen shows
        // whatever name and logo the owner saved, and a neutral mark until then.
        String salonName = savedSalonName();
        Bitmap salonLogo = loadSalonLogo();
        if (salonLogo != null) {
            ImageView mark = new ImageView(this);
            mark.setImageBitmap(salonLogo);
            mark.setScaleType(ImageView.ScaleType.CENTER_CROP);
            GradientDrawable markBackground = new GradientDrawable();
            markBackground.setShape(GradientDrawable.OVAL);
            markBackground.setColor(Color.WHITE);
            mark.setBackground(markBackground);
            mark.setClipToOutline(true);
            content.addView(mark, new LinearLayout.LayoutParams(dp(72), dp(72)));
        } else {
            TextView mark = new TextView(this);
            mark.setText(initialsFor(salonName));
            mark.setTextColor(Color.rgb(30, 30, 32));
            mark.setTextSize(22);
            mark.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            mark.setGravity(Gravity.CENTER);
            GradientDrawable markBackground = new GradientDrawable();
            markBackground.setShape(GradientDrawable.OVAL);
            markBackground.setColor(Color.rgb(255, 158, 59));
            mark.setBackground(markBackground);
            content.addView(mark, new LinearLayout.LayoutParams(dp(72), dp(72)));
        }

        TextView title = new TextView(this);
        title.setText(salonName.isEmpty() ? getString(R.string.splash_default_title) : salonName);
        title.setTextColor(Color.rgb(30, 30, 32));
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = dp(20);
        content.addView(title, titleParams);

        TextView subtitle = new TextView(this);
        subtitle.setText("Salon OS  ·  PKR");
        subtitle.setTextColor(Color.rgb(140, 134, 130));
        subtitle.setTextSize(13);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = dp(7);
        content.addView(subtitle, subtitleParams);

        ProgressBar progress = new ProgressBar(this);
        progress.setIndeterminate(true);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(dp(24), dp(24));
        progressParams.topMargin = dp(28);
        content.addView(progress, progressParams);

        FrameLayout.LayoutParams contentParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        backdrop.addView(content, contentParams);

        content.setAlpha(0f);
        content.setScaleX(0.94f);
        content.setScaleY(0.94f);
        content.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(520L).start();
        return backdrop;
    }

    private void dismissSplash() {
        if (splashDismissed || splash == null) {
            return;
        }
        splashDismissed = true;
        splash.animate().alpha(0f).setDuration(260L).withEndAction(new Runnable() {
            @Override
            public void run() {
                splash.setVisibility(View.GONE);
            }
        }).start();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        trustedPageLoaded = false;
        bridgeToken = null;
        handler.removeCallbacksAndMessages(null);
        if (filePathCallback != null) {
            filePathCallback.onReceiveValue(null);
            filePathCallback = null;
        }
        if (webView != null) {
            webView.removeJavascriptInterface("AndroidSalon");
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    private boolean isBridgeAvailable(String token) {
        return trustedPageLoaded && token != null && token.equals(bridgeToken) && !isFinishing();
    }

    private String startUrl() {
        String configured = resolvedServerUrl();
        if (!configured.isEmpty()) {
            return configured + "/?mode=release";
        }
        return START_URL;
    }

    private boolean isTrustedPageUrl(String rawUrl) {
        if (NavigationPolicy.isBundledAssetUrl(rawUrl)) return true;
        return isRemotePageUrl(rawUrl);
    }

    private String apiConfigScript(String loadedUrl) {
        boolean offlineBundled = loadedUrl != null && loadedUrl.contains("offline=1");
        String api = offlineBundled ? "" : resolvedServerUrl();
        String salon = BuildConfig.SALON_ID == null ? "" : BuildConfig.SALON_ID.trim();
        return "window.__AYAN_API_BASE_URL__='" + jsQuote(api) + "';window.__AYAN_SALON_ID__='" + jsQuote(salon)
                + "';window.__AYAN_OFFLINE_FALLBACK__=" + (offlineBundled ? "true" : "false")
                + ";window.dispatchEvent(new Event('ayan-api-configured'));";
    }

    private static String jsQuote(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'").replace("\r", "").replace("\n", "");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != FILE_CHOOSER_REQUEST_CODE || filePathCallback == null) {
            return;
        }
        Uri[] results = null;
        if (resultCode == RESULT_OK && data != null) {
            results = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
        }
        filePathCallback.onReceiveValue(results);
        filePathCallback = null;
    }

    private static String nativeBridgeScript(String token) {
        // UUIDs contain no characters that need escaping in a single-quoted JS literal.
        return "(function(token){if(window.AndroidSalon&&!navigator.clipboard){var c={writeText:function(t){AndroidSalon.copy(token,String(t));return Promise.resolve();}};try{Object.defineProperty(navigator,'clipboard',{value:c,configurable:true});}catch(e){try{navigator.clipboard=c;}catch(ignore){}}}window.AyanSalonNative={copy:function(t){AndroidSalon.copy(token,String(t));},share:function(t){AndroidSalon.share(token,String(t));},setServerUrl:function(u){return AndroidSalon.setServerUrl(token,String(u||''));},getServerUrl:function(){return AndroidSalon.getServerUrl(token);},clearServerUrl:function(){return AndroidSalon.setServerUrl(token,'');},applyBranding:function(n,l){return AndroidSalon.applyBranding(token,String(n||''),String(l||''));},getSalonName:function(){return AndroidSalon.getSalonName(token);}};})('"
                + token + "');";
    }

    public static final class SalonBridge {
        private static final int MAX_TEXT_LENGTH = 4096;
        private final MainActivity activity;

        SalonBridge(MainActivity activity) {
            this.activity = activity;
        }

        @JavascriptInterface
        public void copy(final String token, final String value) {
            if (!activity.isBridgeAvailable(token) || value == null) {
                return;
            }
            final String safeValue = limitText(value);
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (!activity.isBridgeAvailable(token)) {
                        return;
                    }
                    ClipboardManager manager = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (manager != null) {
                        manager.setPrimaryClip(ClipData.newPlainText("Salon referral", safeValue));
                    }
                }
            });
        }

        @JavascriptInterface
        public void share(final String token, final String value) {
            if (!activity.isBridgeAvailable(token) || value == null) {
                return;
            }
            final String safeValue = limitText(value);
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (!activity.isBridgeAvailable(token)) {
                        return;
                    }
                    Intent send = new Intent(Intent.ACTION_SEND);
                    send.setType("text/plain");
                    send.putExtra(Intent.EXTRA_TEXT, safeValue);
                    try {
                        activity.startActivity(Intent.createChooser(send, "Share referral code"));
                    } catch (RuntimeException ignored) {
                        // A device without a share target should not crash the salon app.
                    }
                }
            });
        }

        /** Returns the address the app is currently using, or an empty string in bundled mode. */
        @JavascriptInterface
        public String getServerUrl(final String token) {
            if (!activity.isBridgeAvailable(token)) {
                return "";
            }
            return activity.resolvedServerUrl();
        }

        /** Returns the salon name saved on this device, or an empty string. */
        @JavascriptInterface
        public String getSalonName(final String token) {
            if (!activity.isBridgeAvailable(token)) {
                return "";
            }
            return activity.savedSalonName();
        }

        /**
         * Saves the owner's salon name and logo on this phone and offers a
         * home-screen icon that carries them. Returns false for a blank name.
         */
        @JavascriptInterface
        public boolean applyBranding(final String token, final String name, final String logoDataUrl) {
            if (!activity.isBridgeAvailable(token)) {
                return false;
            }
            final boolean accepted = activity.applySalonBranding(name, logoDataUrl);
            if (accepted) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (activity.isBridgeAvailable(token) && !activity.isFinishing()) {
                            Toast.makeText(activity,
                                    "Salon name and logo saved on this phone.",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
            return accepted;
        }

        /**
         * Saves an owner-supplied salon server address and reloads the app into it.
         * Returns false for anything that is not a bare https:// origin.
         */
        @JavascriptInterface
        public boolean setServerUrl(final String token, final String value) {
            if (!activity.isBridgeAvailable(token)) {
                return false;
            }
            String rawValue = value == null ? "" : value.trim();
            if (!rawValue.isEmpty() && normalizeServerUrl(rawValue).isEmpty()) {
                return false;
            }
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (!activity.isBridgeAvailable(token)) {
                        return;
                    }
                    activity.applyServerUrl(rawValue);
                }
            });
            return true;
        }

        private static String limitText(String value) {
            return value.length() <= MAX_TEXT_LENGTH
                    ? value
                    : value.substring(0, MAX_TEXT_LENGTH);
        }
    }
}
