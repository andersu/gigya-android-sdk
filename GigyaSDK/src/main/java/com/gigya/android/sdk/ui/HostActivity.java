package com.gigya.android.sdk.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

import java.io.Serializable;

public class HostActivity extends AppCompatActivity {

    public static final String EXTRA_LIFECYCLE_CALLBACKS_ID = "lifecycleCallbacks_id";

    private HostActivityLifecycleCallbacks _lifecycleCallbacks;
    private int _lifecycleCallbacksId = -1;

    private FrameLayout _mainFrame;
    private ProgressBar _progressBar;

    public static void present(Context context, HostActivityLifecycleCallbacks lifecycleCallbacks) {
        Intent intent = new Intent(context, HostActivity.class);
        intent.putExtra(EXTRA_LIFECYCLE_CALLBACKS_ID, GigyaPresenter.addLifecycleCallbacks(lifecycleCallbacks));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        context.startActivity(intent);
    }

    public HostActivityLifecycleCallbacks getLifecycleCallbacks() {
        return _lifecycleCallbacks;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        _mainFrame = new FrameLayout(this);
        addProgressBar();

        setContentView(_mainFrame);

        if (getIntent() != null && getIntent().getExtras() != null) {
            _lifecycleCallbacksId = getIntent().getIntExtra(EXTRA_LIFECYCLE_CALLBACKS_ID, -1);
            if (_lifecycleCallbacksId == -1) {
                finish();
                return;
            }
            _lifecycleCallbacks = GigyaPresenter.getCallbacks(_lifecycleCallbacksId);
        }

        if (_lifecycleCallbacks != null) {
            _lifecycleCallbacks.onCreate(this, savedInstanceState);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (_lifecycleCallbacks != null) {
            _lifecycleCallbacks.onStart(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (_lifecycleCallbacks != null) {
            _lifecycleCallbacks.onResume(this);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (_lifecycleCallbacks != null) {
            _lifecycleCallbacks.onActivityResult(this, requestCode, resultCode, data);
        }
    }

    @Override
    public void finish() {
        GigyaPresenter.flushLifecycleCallbacks(_lifecycleCallbacksId);
        super.finish();
        /*
        Disable exit animation.
         */
        overridePendingTransition(0, 0);
    }

    private void addProgressBar() {
        _progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleSmall);
        _progressBar.setIndeterminate(true);
        _progressBar.setVisibility(View.GONE); // Default behaviour is hidden.
        _mainFrame.addView(_progressBar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    //region UI bindings

    public void showProgress() {
        if (_progressBar.getVisibility() == View.GONE) {
            _progressBar.setVisibility(View.VISIBLE);
        }
    }

    public void dismissProgress() {
        if (_progressBar.getVisibility() == View.VISIBLE) {
            _progressBar.setVisibility(View.GONE);
        }
    }

    public static abstract class HostActivityLifecycleCallbacks {

        public abstract void onCreate(AppCompatActivity activity, @Nullable Bundle savedInstanceState);

        public void onStart(AppCompatActivity activity) {
            // Stub.
        }

        public void onResume(AppCompatActivity activity) {
            // Stub.
        }

        public void onActivityResult(AppCompatActivity activity, int requestCode, int resultCode, @Nullable Intent data) {
            // Stub.
        }
    }

    //endregion

}
