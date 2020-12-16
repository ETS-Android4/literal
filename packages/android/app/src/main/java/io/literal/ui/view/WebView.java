package io.literal.ui.view;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.webkit.WebSettings;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.webkit.WebMessageCompat;
import androidx.webkit.WebMessagePortCompat;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.UUID;

import io.literal.BuildConfig;
import io.literal.lib.Constants;
import io.literal.lib.WebEvent;
import io.literal.lib.WebRoutes;

public class WebView extends android.webkit.WebView {

    private WebEvent.Callback webEventCallback;
    private PageFinishedCallback pageFinishedCallback;
    private ArrayDeque<String> baseHistory;

    public WebView(Context context) {
        super(context);
    }

    public WebView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @SuppressLint("SetJavaScriptEnabled")
    public void initialize(Activity activity) {
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);
        this.addJavascriptInterface(new JavascriptInterface(), "literalWebview");
        WebSettings webSettings = this.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);

        this.onWebEvent(new WebEvent.Callback(activity, this));
        this.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(android.webkit.WebView webview, String url) {
                initializeWebMessageChannel();
                if (WebView.this.pageFinishedCallback != null) {
                    pageFinishedCallback.onPageFinished(webview, url);
                }
            }
        });
    }

    public void postWebEvent(WebEvent webEvent) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.POST_WEB_MESSAGE)) {
            WebViewCompat.postWebMessage(
                    this,
                    new WebMessageCompat(webEvent
                            .toJSON()
                            .toString()
                    ),
                    Uri.parse(WebRoutes.getWebHost())
            );
        }
    }

    public void onWebEvent(WebEvent.Callback cb) {
        this.webEventCallback = cb;
    }

    public void onPageFinished(PageFinishedCallback cb) {
        this.pageFinishedCallback = cb;
    }

    public void loadUrlWithHistory(String url, String[] history) {
        this.baseHistory = new ArrayDeque<>(Arrays.asList(history));
        this.loadUrl(url);
    }

    private void initializeWebMessageChannel() {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.CREATE_WEB_MESSAGE_CHANNEL) &&
                WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_PORT_SET_MESSAGE_CALLBACK) &&
                WebViewFeature.isFeatureSupported(WebViewFeature.POST_WEB_MESSAGE)) {
            final WebMessagePortCompat[] channel = WebViewCompat.createWebMessageChannel(this);
            final Handler handler = new Handler(Looper.getMainLooper());
            channel[0].setWebMessageCallback(handler, new WebMessagePortCompat.WebMessageCallbackCompat() {
                @SuppressLint("RequiresFeature")
                @Override
                public void onMessage(@NonNull WebMessagePortCompat port, @Nullable WebMessageCompat message) {
                    super.onMessage(port, message);
                    String data = message.getData();
                    try {
                        JSONObject json = new JSONObject(data);
                        Log.i("Literal", "Received WebEvent " + json.toString());
                        WebEvent webEvent = new WebEvent(json);
                        if (webEventCallback != null) {
                            webEventCallback.onWebEvent(webEvent);
                        }
                    } catch (JSONException ex) {
                        Log.e("Literal", "Error in onMessage", ex);
                    }
                }
            });

            // Initial handshake - deliver WebMessageChannel port to JS.
            WebViewCompat.postWebMessage(
                    this,
                    new WebMessageCompat("", new WebMessagePortCompat[]{channel[1]}),
                    Uri.parse(WebRoutes.getWebHost())
            );
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_BACK) && this.canGoBack()) {
            Log.i(Constants.LOG_TAG, "can go back");
            this.goBack();
            return true;
        }

        if ((keyCode == KeyEvent.KEYCODE_BACK) && baseHistory != null && baseHistory.peek() != null) {
            String prev = baseHistory.poll();
            try {
                if (WebViewFeature.isFeatureSupported(WebViewFeature.POST_WEB_MESSAGE)) {
                    JSONObject data = new JSONObject();
                    data.put("url", prev);
                    WebViewCompat.postWebMessage(
                            this,
                            new WebMessageCompat(
                                    new WebEvent(WebEvent.TYPE_ROUTER_REPLACE, UUID.randomUUID().toString(), data)
                                            .toJSON()
                                            .toString()
                            ),
                            Uri.parse(WebRoutes.getWebHost())
                    );
                } else {
                    this.loadUrl(prev);
                }
            } catch (JSONException ex) {
                this.loadUrl(prev);
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    public interface PageFinishedCallback {
        public void onPageFinished(android.webkit.WebView view, String Url);
    }

    private class JavascriptInterface {
        @android.webkit.JavascriptInterface
        public boolean isWebview() {
            return true;
        }
    }
}