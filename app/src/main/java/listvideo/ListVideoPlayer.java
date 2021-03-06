package listvideo;

import android.content.Context;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

import com.example.hunliji.livelistvideo.R;

import utils.VideoUtil;


public class ListVideoPlayer extends FrameLayout {

    public class STATE {
        public static final int ERROR = -1;
        public static final int NORMAL = 0;
        public static final int PREPARING = 1;
        public static final int PLAYING = 2;
        public static final int PAUSE = 3;
        public static final int COMPLETE = 4;
    }


    public enum ScaleType {
        FIT_CENTER,
        CENTER_CROP
    }

    FrameLayout textureContainer;
    ProgressBar loadingBar;

    private Uri source;
    private int currentState;

    private ScaleType scaleType= ScaleType.CENTER_CROP;

    private OnStateChangeListener onStateChangeListener;

    private OnBufferingUpdateListener onBufferingUpdateListener;

    public ListVideoPlayer(@NonNull Context context) {
        super(context);
        init();
    }

    public ListVideoPlayer(
            @NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ListVideoPlayer(
            @NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public ListVideoPlayer(
            @NonNull Context context,
            @Nullable AttributeSet attrs,
            int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    private void init() {
        View.inflate(getContext(), R.layout.widget_list_video_player, this);
        textureContainer = findViewById(R.id.texture_container);
        loadingBar = findViewById(R.id.progress_loading);
    }

    public void setScaleType(ScaleType scaleType) {
        this.scaleType = scaleType;
    }

    public void setSource(Uri source) {
        if (isCurrentVideo() && MediaManager.INSTANCE()
                .getSource()
                .equals(source)) {
            return;
        }
        this.source = source;
        currentState = STATE.NORMAL;
        if (onStateChangeListener != null) {
            onStateChangeListener.OnStateChange(currentState);
        }
    }


    public void startVideo() {
        if (source == null) {
            return;
        }
        ListVideoPlayerManager.setCurrentVideo(this);
        initTextureView(scaleType);
        addTextureView();
        AudioManager mAudioManager = (AudioManager) getContext().getSystemService(Context
                .AUDIO_SERVICE);
        if (mAudioManager != null) {
            mAudioManager.requestAudioFocus(ListVideoPlayerManager.onAudioFocusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        }
        VideoUtil.scanForActivity(getContext())
                .getWindow()
                .addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        MediaManager.INSTANCE()
                .setSource(source);
        currentState = STATE.PREPARING;
        loadingBar.setVisibility(VISIBLE);
        if (onStateChangeListener != null) {
            onStateChangeListener.OnStateChange(currentState);
        }
    }

    public void onPrepared() {
        long position = VideoUtil.getSavedProgress(getContext(), source.toString());
        if (position > 0) {
            MediaManager.INSTANCE()
                    .seekTo((int) position);
        }
        onPlaying();
    }

    public void onPlaying() {
        currentState = STATE.PLAYING;
        MediaManager.INSTANCE()
                .start();
        loadingBar.setVisibility(GONE);
        if (onStateChangeListener != null) {
            onStateChangeListener.OnStateChange(currentState);
        }
    }

    public void onPause() {
        currentState = STATE.PAUSE;
        MediaManager.INSTANCE()
                .pause();
        loadingBar.setVisibility(GONE);
        if (onStateChangeListener != null) {
            onStateChangeListener.OnStateChange(currentState);
        }
    }

    public void onError() {
        currentState = STATE.ERROR;
        MediaManager.INSTANCE()
                .releaseMediaPlayer();
        loadingBar.setVisibility(GONE);
        if (onStateChangeListener != null) {
            onStateChangeListener.OnStateChange(currentState);
        }
    }

    public void onCompletion() {
        System.gc();
        currentState = STATE.COMPLETE;
        MediaManager.INSTANCE()
                .releaseMediaPlayer();
        VideoUtil.saveProgress(getContext(), source.toString(), 0);
        if (onStateChangeListener != null) {
            onStateChangeListener.OnStateChange(currentState);
        }
    }


    public void onRelease() {
        if (currentState == STATE.PAUSE || currentState == STATE.PLAYING) {
            long position = MediaManager.INSTANCE()
                    .getCurrentPosition();
            VideoUtil.saveProgress(getContext(), source.toString(), position);
        }
        currentState = STATE.NORMAL;
        MediaManager.INSTANCE()
                .releaseMediaPlayer();
        removeTextureView();
        AudioManager mAudioManager = (AudioManager) getContext().getSystemService(Context
                .AUDIO_SERVICE);
        if (mAudioManager != null) {
            mAudioManager.abandonAudioFocus(ListVideoPlayerManager.onAudioFocusChangeListener);
        }
        VideoUtil.scanForActivity(getContext())
                .getWindow()
                .clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        MediaManager.INSTANCE()
                .release();
        if (onStateChangeListener != null) {
            onStateChangeListener.OnStateChange(currentState);
        }
    }

    private void initTextureView(ScaleType scaleType) {
        MediaManager.INSTANCE()
                .initTextureView(getContext(),scaleType);
    }


    public void addTextureView() {
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(ViewGroup
                .LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER);
        textureContainer.addView(MediaManager.INSTANCE().textureView, layoutParams);
    }


    public void removeTextureView() {
        textureContainer.removeView(MediaManager.INSTANCE().textureView);
    }

    public boolean isCurrentVideo() {
        return ListVideoPlayerManager.getCurrentVideo() != null && ListVideoPlayerManager
                .getCurrentVideo() == this;
    }

    public int getCurrentState() {
        return currentState;
    }


    public void setOnStateChangeListener(OnStateChangeListener onStateChangeListener) {
        this.onStateChangeListener = onStateChangeListener;
        this.onStateChangeListener.OnStateChange(currentState);
    }

    public interface OnStateChangeListener {

        void OnStateChange(int state);
    }

    public interface OnBufferingUpdateListener {

        void onBufferingUpdate(int state);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (isCurrentVideo()) {
            ListVideoPlayerManager.releaseAllVideos();
        }
    }

    public void onBufferingUpdate(int percent){
        if(onBufferingUpdateListener!=null){
            onBufferingUpdateListener.onBufferingUpdate(percent);
        }
    }

    public void setOnBufferingUpdateListener(OnBufferingUpdateListener onBufferingUpdateListener) {
        this.onBufferingUpdateListener = onBufferingUpdateListener;
    }
}
