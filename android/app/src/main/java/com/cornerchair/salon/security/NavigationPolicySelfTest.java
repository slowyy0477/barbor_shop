package com.cornerchair.salon.security;

/** Plain-JDK checks for the WebView navigation trust boundary. */
public final class NavigationPolicySelfTest {
    private NavigationPolicySelfTest() {
    }

    public static void main(String[] args) {
        run();
        System.out.println("Ayan Android navigation policy checks passed");
    }

    public static void run() {
        assertTrue(NavigationPolicy.isBundledAssetUrl(
                "file:///android_asset/index.html"), "asset URL");
        assertTrue(NavigationPolicy.isBundledAssetUrl(
                "file:///android_asset/index.html?v=6#home"), "asset query/hash");
        assertFalse(NavigationPolicy.isBundledAssetUrl(
                "file:///android_asset/../data/private.txt"), "asset traversal");
        assertFalse(NavigationPolicy.isBundledAssetUrl(
                "file:///android_asset/%2e%2e/data/private.txt"), "encoded traversal");
        assertFalse(NavigationPolicy.isBundledAssetUrl(
                "file:///sdcard/private.txt"), "device file");
        assertFalse(NavigationPolicy.isBundledAssetUrl(
                "file://localhost/android_asset/index.html"), "file authority");
        assertFalse(NavigationPolicy.isBundledAssetUrl(
                "https://example.com/"), "remote page is not trusted");

        assertTrue(NavigationPolicy.isKnownExternalUrl(
                "https://example.com/help"), "HTTPS handoff");
        assertTrue(NavigationPolicy.isKnownExternalUrl(
                "tel:+923105301460"), "phone handoff");
        assertTrue(NavigationPolicy.isKnownExternalUrl(
                "upi://pay?pa=salon@example"), "payment handoff");
        assertFalse(NavigationPolicy.isKnownExternalUrl(
                "https:"), "malformed HTTPS blocked");
        assertFalse(NavigationPolicy.isKnownExternalUrl(
                "javascript:AndroidSalon.share('leak')"), "javascript blocked");
        assertFalse(NavigationPolicy.isKnownExternalUrl(
                "file:///android_asset/index.html"), "asset stays in app");
    }

    private static void assertTrue(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label + " should be allowed");
        }
    }

    private static void assertFalse(boolean condition, String label) {
        if (condition) {
            throw new AssertionError(label + " should be rejected");
        }
    }
}
