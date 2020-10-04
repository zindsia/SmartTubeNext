package com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import com.liskovsoft.mediaserviceinterfaces.MediaItemManager;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.sharedutils.helpers.KeyHelpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers.SuggestionsLoader.MetadataListener;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelPresenter;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.FormatItem;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;

import java.util.List;

public class PlayerUiManager extends PlayerEventListenerHelper implements MetadataListener {
    private static final String TAG = PlayerUiManager.class.getSimpleName();
    private final Handler mHandler;
    private static final long UI_HIDE_TIMEOUT_MS = 2_000;
    private static final long SUGGESTIONS_RESET_TIMEOUT_MS = 500;
    private boolean mEngineReady;
    private boolean mDebugViewEnabled;
    private final Runnable mSuggestionsResetHandler = () -> mController.resetSuggestedPosition();
    private final Runnable mUiVisibilityHandler = () -> {
        if (mController.isPlaying()) {
            if (!mController.isSuggestionsShown()) { // don't hide when suggestions is shown
                mController.showControls(false);
            }
        } else {
            // in seeking state? doing recheck...
            disableUiAutoHideTimeout();
            enableUiAutoHideTimeout();
        }
    };

    public PlayerUiManager() {
        mHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    public void onActivity(Activity activity) {
        super.onActivity(activity);

        AppSettingsPresenter.instance(activity).setPlayerUiManager(this);
    }

    @Override
    public void onKeyDown(int keyCode) {
        disableUiAutoHideTimeout();
        disableSuggestionsResetTimeout();

        if (KeyHelpers.isBackKey(keyCode)) {
            enableSuggestionsResetTimeout();
        } else {
            enableUiAutoHideTimeout();
        }
    }

    @Override
    public void onChannelClicked() {
        ChannelPresenter.instance(mActivity).openChannel(mController.getVideo());
    }

    @Override
    public void onClosedCaptionsClicked() {
        List<FormatItem> subtitleFormats = mController.getSubtitleFormats();
        String subtitleFormatsTitle = mActivity.getString(R.string.subtitle_formats_title);

        AppSettingsPresenter settingsPresenter = AppSettingsPresenter.instance(mActivity);

        settingsPresenter.clear();

        settingsPresenter.appendRadioCategory(subtitleFormatsTitle,
                UiOptionItem.from(subtitleFormats,
                        option -> mController.selectFormat(UiOptionItem.toFormat(option)),
                        mActivity.getString(R.string.default_subtitle_option)));

        settingsPresenter.showDialog();
    }

    @Override
    public void onPlaylistAddClicked() {
        MessageHelpers.showMessage(mActivity, R.string.not_implemented);
    }

    @Override
    public void onVideoStatsClicked(boolean enabled) {
        mDebugViewEnabled = enabled;
        mController.showDebugView(enabled);
    }

    @Override
    public void onEngineInitialized() {
        mEngineReady = true;
    }

    @Override
    public void onVideoLoaded(Video item) {
        // Next lines on engine initialized stage cause other listeners to disappear.
        mController.showDebugView(mDebugViewEnabled);
        mController.setDebugButtonState(mDebugViewEnabled);
    }

    @Override
    public void onEngineReleased() {
        Log.d(TAG, "Engine released. Disabling all callbacks...");
        mEngineReady = false;

        disableUiAutoHideTimeout();
        disableSuggestionsResetTimeout();
    }

    @Override
    public void onRepeatModeClicked(int modeIndex) {
        mController.setRepeatMode(modeIndex);
    }

    @Override
    public void onRepeatModeChange(int modeIndex) {
        mController.setRepeatButtonState(modeIndex);
    }

    @Override
    public void onMetadata(MediaItemMetadata metadata) {
        mController.setLikeButtonState(metadata.getLikeStatus() == MediaItemMetadata.LIKE_STATUS_LIKE);
        mController.setDislikeButtonState(metadata.getLikeStatus() == MediaItemMetadata.LIKE_STATUS_DISLIKE);
        mController.setSubscribeButtonState(metadata.isSubscribed());
    }

    public void disableUiAutoHideTimeout() {
        Log.d(TAG, "Stopping hide ui timer...");
        mHandler.removeCallbacks(mUiVisibilityHandler);
    }

    public void enableUiAutoHideTimeout() {
        Log.d(TAG, "Starting hide ui timer...");
        if (mEngineReady) {
            mHandler.postDelayed(mUiVisibilityHandler, UI_HIDE_TIMEOUT_MS);
        }
    }

    private void disableSuggestionsResetTimeout() {
        Log.d(TAG, "Stopping reset position timer...");
        mHandler.removeCallbacks(mSuggestionsResetHandler);
    }

    private void enableSuggestionsResetTimeout() {
        Log.d(TAG, "Starting reset position timer...");
        if (mEngineReady) {
            mHandler.postDelayed(mSuggestionsResetHandler, SUGGESTIONS_RESET_TIMEOUT_MS);
        }
    }

    @Override
    public void onSubscribeClicked(boolean subscribed) {
        if (mController.getVideo() == null) {
            Log.e(TAG, "Seems that video isn't initialized yet. Cancelling...");
            return;
        }

        MediaService service = YouTubeMediaService.instance();
        MediaItemManager mediaItemManager = service.getMediaItemManager();

        Observable<Void> observable;

        if (subscribed) {
            observable = mediaItemManager.subscribeObserve(mController.getVideo().mediaItem);
        } else {
            observable = mediaItemManager.unsubscribeObserve(mController.getVideo().mediaItem);
        }

        observable
                .subscribeOn(Schedulers.newThread())
                .subscribe();
    }

    @Override
    public void onThumbsDownClicked(boolean thumbsDown) {
        if (mController.getVideo() == null) {
            Log.e(TAG, "Seems that video isn't initialized yet. Cancelling...");
            return;
        }

        MediaService service = YouTubeMediaService.instance();
        MediaItemManager mediaItemManager = service.getMediaItemManager();

        Observable<Void> observable;

        if (thumbsDown) {
            observable = mediaItemManager.setDislikeObserve(mController.getVideo().mediaItem);
        } else {
            observable = mediaItemManager.removeDislikeObserve(mController.getVideo().mediaItem);
        }

        observable
                .subscribeOn(Schedulers.newThread())
                .subscribe();
    }

    @Override
    public void onThumbsUpClicked(boolean thumbsUp) {
        if (mController.getVideo() == null) {
            Log.e(TAG, "Seems that video isn't initialized yet. Cancelling...");
            return;
        }

        MediaService service = YouTubeMediaService.instance();
        MediaItemManager mediaItemManager = service.getMediaItemManager();

        Observable<Void> observable;

        if (thumbsUp) {
            observable = mediaItemManager.setLikeObserve(mController.getVideo().mediaItem);
        } else {
            observable = mediaItemManager.removeLikeObserve(mController.getVideo().mediaItem);
        }

        observable
                .subscribeOn(Schedulers.newThread())
                .subscribe();
    }
}
