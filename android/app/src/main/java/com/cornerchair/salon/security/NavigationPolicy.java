package com.cornerchair.salon.security;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * Navigation rules for the bundled WebView shell.
 *
 * <p>The app only needs to render files packaged in {@code android_asset}. All other
 * top-level navigations must either be handed to an external app (for known user-facing
 * schemes) or blocked. Keeping this policy free of Android classes makes it executable in a
 * plain JDK self-test as well as from the Android activity.</p>
 */
public final class NavigationPolicy {
    private static final String FILE_SCHEME = "file";
    private static final String ASSET_PREFIX = "/android_asset/";

    private NavigationPolicy() {
    }

    /** Returns true only for a path inside the APK's packaged asset directory. */
    public static boolean isBundledAssetUrl(String rawUrl) {
        URI uri = parse(rawUrl);
        if (uri == null || !FILE_SCHEME.equalsIgnoreCase(uri.getScheme())) {
            return false;
        }
        if (uri.getRawAuthority() != null && uri.getRawAuthority().length() > 0) {
            return false;
        }
        String path = uri.getPath();
        if (path == null || !path.startsWith(ASSET_PREFIX)
                || path.length() == ASSET_PREFIX.length()) {
            return false;
        }
        String[] segments = path.split("/", -1);
        for (String segment : segments) {
            if (".".equals(segment) || "..".equals(segment)) {
                return false;
            }
        }
        return true;
    }

    /** Returns true for schemes that may be handed to an external Android application. */
    public static boolean isKnownExternalUrl(String rawUrl) {
        URI uri = parse(rawUrl);
        if (uri == null || uri.getScheme() == null) {
            return false;
        }
        String scheme = uri.getScheme().toLowerCase(Locale.US);
        if ("http".equals(scheme) || "https".equals(scheme)) {
            return uri.getRawAuthority() != null && uri.getRawAuthority().length() > 0;
        }
        if ("tel".equals(scheme) || "mailto".equals(scheme) || "sms".equals(scheme)
                || "geo".equals(scheme) || "upi".equals(scheme)) {
            return uri.getSchemeSpecificPart() != null
                    && uri.getSchemeSpecificPart().trim().length() > 0;
        }
        return false;
    }

    private static URI parse(String rawUrl) {
        if (rawUrl == null || rawUrl.trim().length() == 0) {
            return null;
        }
        try {
            return new URI(rawUrl.trim());
        } catch (URISyntaxException ignored) {
            return null;
        }
    }
}
