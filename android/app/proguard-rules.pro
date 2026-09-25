# Keep methods exposed to the bundled WebView if release shrinking is enabled later.
-keepclassmembers class com.cornerchair.salon.MainActivity$SalonBridge {
    @android.webkit.JavascriptInterface <methods>;
}
