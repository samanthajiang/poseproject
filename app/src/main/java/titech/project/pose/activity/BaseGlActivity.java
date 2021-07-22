package titech.project.pose.activity;

import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import titech.project.pose.R;
import titech.project.pose.renderer.OnRendererListener;
import titech.project.nama.Effect;
import titech.project.nama.mRenderer;

public abstract class BaseGlActivity extends AppCompatActivity implements
        OnRendererListener{
    private static final String TAG = "BaseGlActivity";
    protected TextView mTvTrackStatus;
    protected GLSurfaceView mGlSurfaceView;
    protected mRenderer mMRenderer;
    private int mRecognitionIndex = -1;
    private int mRecognitionCount;
    protected boolean mIsFlipX;
    private Effect mActionEffect;
    private static final String ASSETS_DIR = "others/";
    public static final String HUMAN_ACTION_BUNDLE_PATH = ASSETS_DIR + "human_action.bundle";
    int trackhuman = 0;
    public ImageView StartAnimation;
    public ImageView Goodjob;
    public boolean action0 = false;
    public boolean action1 = false;
    public boolean action2 = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setBackgroundDrawable(null);
        setContentView(R.layout.activity_base);
        mTvTrackStatus = findViewById(R.id.tv_track_status);
        mGlSurfaceView = findViewById(R.id.gl_surface);
        StartAnimation =findViewById(R.id.imageView0);
        Goodjob = findViewById(R.id.goodjob);
        initView();
        initGlRenderer();
        initFuRenderer();
        mActionEffect = new Effect(HUMAN_ACTION_BUNDLE_PATH, getString(R.string.config_item_action_recognition), Effect.TYPE_ACTION, Effect.MODULE_CODE_ACTION);
    }


    @Override
    public void onSurfaceCreated() {
        mMRenderer.onSurfaceCreated();
        if (mActionEffect != null) {
            mMRenderer.selectEffect(mActionEffect);
        }
    }

    @Override
    public void onSurfaceChanged(int viewWidth, int viewHeight) {

    }

    @Override
    public void onSurfaceDestroy() {
        mMRenderer.onSurfaceDestroyed();
    }


    protected abstract void initView();

    protected abstract void initFuRenderer();

    protected abstract void initGlRenderer();

    protected void trackStatus() {
        int toastStrId = 0;
        boolean invisible = mMRenderer.queryHumanTrackStatus() > 0;
            if (!invisible) {
                toastStrId = R.string.track_status_no_human;
            }else{
                if(trackhuman<4) {
                    trackhuman++;
                }
            }
        final boolean visible = !invisible;
        final int strId = toastStrId;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (visible) {
                    mTvTrackStatus.setVisibility(View.VISIBLE);
                    mTvTrackStatus.setText(strId);
                } else {
                    mTvTrackStatus.setVisibility(View.INVISIBLE);
                }
                /* recognize human for more than 3 camera frame*/
                if(trackhuman==3){
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            StartAnimation.performClick();
                        }
                    }, 2000);
                }
            }
        });
    }

    protected void trackPose() {
        int index = -1;
            int type = mRenderer.detectHumanAction();
            index = type;

        if (mRecognitionIndex == index) {
            mRecognitionCount++;
        } else {
            mRecognitionCount = 0;
        }
        mRecognitionIndex = index;
        Log.i("myindex", String.valueOf(mRecognitionCount));

        if (mRecognitionCount >= 3&&type==1&&action0==true) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                  action0=false;
                  Goodjob.performClick();
                }
            });
        }else if(mRecognitionCount >= 3&&type==2&&action1==true){
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    action1=false;
                    Goodjob.performClick();
                }
            });
        }else if(mRecognitionCount >= 3&&type==3&&action2==true) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    action2=false;
                    Goodjob.performClick();
                }
            });
        }
    }
}