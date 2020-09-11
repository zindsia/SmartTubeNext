package com.liskovsoft.smartyoutubetv2.tv.ui.common;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import androidx.fragment.app.FragmentActivity;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.tv.ui.main.MainApplication;
import com.liskovsoft.smartyoutubetv2.tv.ui.search.SearchActivity;

/**
 * This parent class contains common methods that run in every activity such as search.
 */
public abstract class LeanbackActivity extends FragmentActivity {
    private static final String TAG = LeanbackActivity.class.getSimpleName();
    private LongClickManager mLongClickManager;
    private UriBackgroundManager mBackgroundManager;
    private ViewManager mViewManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mLongClickManager = new LongClickManager();
        mBackgroundManager = new UriBackgroundManager(this);
        mViewManager = ViewManager.instance(this);
        mViewManager.setDefault(this.getClass()); // we can't do it in the ViewManager because activity may be started from outside
    }

    @Override
    public boolean onSearchRequested() {
        startActivity(new Intent(this, SearchActivity.class));
        return true;
    }
    
    @SuppressLint("RestrictedApi")
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        Log.d(TAG, event);

        mLongClickManager.dispatchKeyEvent(event);

        return super.dispatchKeyEvent(event);
    }

    public boolean isLongClick() {
        return mLongClickManager.isLongClick();
    }

    public UriBackgroundManager getBackgroundManager() {
        return mBackgroundManager;
    }

    @Override
    protected void onStart() {
        super.onStart();
        mBackgroundManager.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mBackgroundManager.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mBackgroundManager.onDestroy();
    }

    @Override
    public void finish() {
        super.finish();
        
        mViewManager.startParentView(this);
    }
}