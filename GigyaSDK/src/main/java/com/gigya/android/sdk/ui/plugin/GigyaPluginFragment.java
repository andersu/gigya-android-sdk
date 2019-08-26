package com.gigya.android.sdk.ui.plugin;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.ProgressBar;

import com.gigya.android.sdk.Config;
import com.gigya.android.sdk.GigyaLogger;
import com.gigya.android.sdk.GigyaPluginCallback;
import com.gigya.android.sdk.R;
import com.gigya.android.sdk.account.models.GigyaAccount;
import com.gigya.android.sdk.ui.Presenter;

import org.json.JSONArray;

import java.util.Locale;

import static com.gigya.android.sdk.ui.plugin.PluginAuthEventDef.ADD_CONNECTION;
import static com.gigya.android.sdk.ui.plugin.PluginAuthEventDef.CANCELED;
import static com.gigya.android.sdk.ui.plugin.PluginAuthEventDef.LOGIN;
import static com.gigya.android.sdk.ui.plugin.PluginAuthEventDef.LOGIN_STARTED;
import static com.gigya.android.sdk.ui.plugin.PluginAuthEventDef.LOGOUT;
import static com.gigya.android.sdk.ui.plugin.PluginAuthEventDef.REMOVE_CONNECTION;
import static com.gigya.android.sdk.ui.plugin.PluginEventDef.AFTER_SCREEN_LOAD;
import static com.gigya.android.sdk.ui.plugin.PluginEventDef.AFTER_SUBMIT;
import static com.gigya.android.sdk.ui.plugin.PluginEventDef.AFTER_VALIDATION;
import static com.gigya.android.sdk.ui.plugin.PluginEventDef.BEFORE_SCREEN_LOAD;
import static com.gigya.android.sdk.ui.plugin.PluginEventDef.BEFORE_SUBMIT;
import static com.gigya.android.sdk.ui.plugin.PluginEventDef.BEFORE_VALIDATION;
import static com.gigya.android.sdk.ui.plugin.PluginEventDef.ERROR;
import static com.gigya.android.sdk.ui.plugin.PluginEventDef.FIELD_CHANGED;
import static com.gigya.android.sdk.ui.plugin.PluginEventDef.HIDE;
import static com.gigya.android.sdk.ui.plugin.PluginEventDef.LOAD;
import static com.gigya.android.sdk.ui.plugin.PluginEventDef.SUBMIT;

@SuppressLint("ValidFragment")
public class GigyaPluginFragment<A extends GigyaAccount> extends DialogFragment implements IGigyaPluginFragment<A> {

    private static final String LOG_TAG = "GigyaPluginFragment";

    private static final String BASE_URL = "http://www.gigya.com";
    private static final String MIME_TYPE = "text/html";
    private static final String ENCODING = "utf-8";

    public static final String PLUGIN_SCREENSETS = "accounts.screenSet";
    public static final String PLUGIN_COMMENTS = "comments.commentsUI";

    /*
    Web bridge invocation callback. Injected into the web bridge when initializing the fragment.
     */
    public interface IBridgeCallbacks<A extends GigyaAccount> {

        void invokeCallback(String invocation);

        void onPluginEvent(GigyaPluginEvent event, String containerID);

        void onPluginAuthEvent(@PluginAuthEventDef.PluginAuthEvent String method, @Nullable A accountObj);
    }

    // Dependencies.
    private Config _config;
    private IGigyaWebBridge<A> _gigyaWebBridge;

    // Setter data.
    private GigyaPluginCallback<A> _pluginCallback;
    private String _html;
    private boolean _obfuscation = false;

    // Members.
    private WebView _webView;
    private ProgressBar _progressBar;
    private GigyaPluginFileChooser _fileChooserClient;

    public void setConfig(Config config) {
        _config = config;
    }

    public void setWebBridge(IGigyaWebBridge<A> gigyaWebBridge) {
        _gigyaWebBridge = gigyaWebBridge;
    }

    @Override
    public void setCallback(GigyaPluginCallback<A> gigyaPluginCallback) {
        _pluginCallback = gigyaPluginCallback;
    }

    @Override
    public void setHtml(String html) {
        _html = html;
    }

    //region LIFE CYCLE

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Parse arguments.
        if (getArguments() != null) {
            _obfuscation = getArguments().getBoolean(Presenter.ARG_OBFUSCATE, false);

            /* When using GigyaPluginPresenter.ARG_STYLE_SHOW_FULL_SCREEN option the style attribute will be ignored. */
            final boolean fullScreen = getArguments().getBoolean(Presenter.ARG_STYLE_SHOW_FULL_SCREEN, false);
            if (fullScreen) {
                setStyle(DialogFragment.STYLE_NORMAL, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
            }
        }
    }


    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        }
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        final Dialog dialog = getDialog();
        if (dialog != null) {
            final Window window = dialog.getWindow();
            if (window != null) {
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                final Bundle args = getArguments();
                if (args != null) {
                    // Check fullscreen mode request.
                    final boolean fullScreen = args.getBoolean(Presenter.ARG_STYLE_SHOW_FULL_SCREEN, false);
                    if (fullScreen) {
                        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                    }
                }
            }
        }
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        super.onCancel(dialog);
        if (getActivity() != null) {
            getActivity().onBackPressed();
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.gigya_fragment_webview, container, false);
    }

    @Override
    public void onDestroyView() {
        if (_gigyaWebBridge != null) {
            _gigyaWebBridge.detachFrom(_webView);
        }
        super.onDestroyView();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        evaluateActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        evaluatePermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        setUpUiElements(view);
        setUpWebViewElement();

        // Load URL.
        loadUrl(view);
    }

    //endregion

    @Override
    public void setUpUiElements(final View fragmentView) {
        // Reference UI elements. Must be called first!
        _webView = fragmentView.findViewById(R.id.web_frag_web_view);
        _progressBar = fragmentView.findViewById(R.id.web_frag_progress_bar);
    }

    @SuppressLint({"JavascriptInterface", "AddJavascriptInterface", "SetJavaScriptEnabled"})
    @Override
    public void setUpWebViewElement() {
        _fileChooserClient = new GigyaPluginFileChooser(this);

        final WebSettings webSettings = _webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setAllowFileAccess(true);

        // Setting up a custom veb view client to handle WebView interaction.
        _webView.setWebViewClient(_webViewClient);
        _webView.setWebChromeClient(_fileChooserClient);

        // Web bridge.
        _gigyaWebBridge.attachTo(
                _webView,
                _obfuscation,
                _pluginCallback,
                _progressBar, new Runnable() {
            @Override
            public void run() {
                if (getActivity() != null) {
                    getActivity().onBackPressed();
                }
            }
        });
    }

    @Override
    public void loadUrl(final View fragmentView) {
        fragmentView.post(new Runnable() {
            @Override
            public void run() {
                _webView.loadDataWithBaseURL(BASE_URL, _html, MIME_TYPE, ENCODING, null);
            }
        });
    }

    @Override
    public void dismissWhenDone() {

    }

    @Override
    public void evaluateActivityResult(int requestCode, int resultCode, Intent data) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (requestCode != GigyaPluginFileChooser.FILE_CHOOSER_MEDIA_REQUEST_CODE) {
                super.onActivityResult(requestCode, resultCode, data);
            } else {
                _fileChooserClient.onActivityResult(resultCode, data);
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void evaluatePermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == GigyaPluginFileChooser.FIRE_ACCESS_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                GigyaLogger.debug(LOG_TAG, "External storage permission explicitly granted.");
                _fileChooserClient.onRequestPermissionsResult(requestCode, permissions, grantResults);
            } else {
                // Permission denied by the user.
                GigyaLogger.debug(LOG_TAG, "External storage permission explicitly denied.");
            }
        }
    }

    /*
    Web View client implementations.
     */
    private GigyaPluginWebViewClient _webViewClient = new GigyaPluginWebViewClient(
            new IGigyaPluginWebViewClientInteractions() {

                @Override
                public void onPageStarted() {
                    _progressBar.setVisibility(View.VISIBLE);
                }

                @Override
                public void onPageError(GigyaPluginEvent errorEvent) {
                    _pluginCallback.onError(errorEvent);
                }

                @Override
                public boolean onUrlInvoke(String url) {
                    return _gigyaWebBridge.invoke(url);
                }

                @Override
                public void onBrowserIntent(Uri uri) {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, uri);
                    startActivity(browserIntent);
                }
            });

    /*
    Define the JavaScript interface.
     */
    private Object _JSInterface = new Object() {

        private static final String ADAPTER_NAME = "mobile";

        @JavascriptInterface
        public String getAPIKey() {
            return _config.getApiKey();
        }

        @JavascriptInterface
        public String getAdapterName() {
            return ADAPTER_NAME;
        }

        @JavascriptInterface
        public String getObfuscationStrategy() {
            return _obfuscation ? "base64" : "";
        }

        @JavascriptInterface
        public String getFeatures() {
            JSONArray features = new JSONArray();
            for (GigyaWebBridge.Feature feature : GigyaWebBridge.Feature.values()) {
                features.put(feature.toString().toLowerCase(Locale.ROOT));
            }
            return features.toString();
        }

        @JavascriptInterface
        public boolean sendToMobile(String action, String method, String queryStringParams) {
            return _gigyaWebBridge.invoke(action, method, queryStringParams);
        }

    };
}
