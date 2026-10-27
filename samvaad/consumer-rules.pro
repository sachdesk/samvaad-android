# The page calls SamvaadAndroid.postMessage(String) through the JavaScript bridge.
-keepclassmembers class com.samvaads.widget.internal.JsBridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface
