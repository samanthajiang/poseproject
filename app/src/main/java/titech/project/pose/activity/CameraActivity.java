package titech.project.pose.activity;

import android.os.Bundle;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import titech.project.nama.mRenderer;
import titech.project.pose.R;
import titech.project.pose.renderer.BaseCameraRenderer;
import titech.project.pose.renderer.Camera1Renderer;
import titech.project.pose.renderer.OnCameraRendererListener;
import titech.project.pose.util.CameraUtils;
import titech.project.pose.util.ToastUtil;
import titech.project.nama.Effect;


public class CameraActivity extends BaseGlActivity implements OnCameraRendererListener
         {
    private BaseCameraRenderer mCameraRenderer;
    private TextView Coutdowntext;
    private TextView Myscore;
    private TextView Finish;
    private Effect mFullEffect;
    private ImageView Plot;
    private ImageView SwitchCamera;
    private static final String FULL_BODY_LANDMARKS_BUNDLE_PATH = "others/" + "bodyLandmarks_dance.bundle"; // 人体关键点 全身
    boolean flag=false;
    int countdown=3;
    int scorecount = 0;
    String Score = "Total Score: "+scorecount;
    String countnum = String.valueOf(countdown);



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mFullEffect = new Effect(FULL_BODY_LANDMARKS_BUNDLE_PATH, Effect.MODULE_CODE_HUMAN_LANDMARKS);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        mCameraRenderer.onTouchEvent((int) event.getX(), (int) event.getY(), event.getAction());
        return super.onTouchEvent(event);
    }

    @Override
    protected void initView() {
        Coutdowntext = findViewById(R.id.countdown);
        Myscore=findViewById(R.id.myscore);
        Finish=findViewById(R.id.finish);
        Plot=findViewById(R.id.iv_debug);
        SwitchCamera=findViewById(R.id.iv_switch_cam);
        //findViewById(R.id.iv_debug).setOnClickListener(mViewClickListener);
        //findViewById(R.id.iv_switch_cam).setOnClickListener(mViewClickListener);
        //mPhotoTaker.setFlipY(true);
    }

    @Override
    protected void initFuRenderer() {
        mMRenderer = new mRenderer.Builder(this)
                .setCameraFacing(mCameraRenderer.getCameraFacing())
                .setInputImageOrientation(CameraUtils.getCameraOrientation(mCameraRenderer.getCameraFacing()))
                .setRunBenchmark(true)
                .build();
    }

    @Override
    protected void initGlRenderer() {
        mCameraRenderer = new Camera1Renderer(getLifecycle(), this, mGlSurfaceView, this);
        mIsFlipX = mCameraRenderer.getCameraFacing() == mRenderer.CAMERA_FACING_FRONT;
    }

    @Override
    public void onSurfaceCreated() {
        super.onSurfaceCreated();
        mCameraRenderer.setRenderRotatedImage(false);
    }

    @Override
    public int onDrawFrame(byte[] cameraNv21Byte, int cameraTexId, int cameraWidth, int cameraHeight, float[] mvpMatrix, float[] texMatrix, long timeStamp) {
        int TexId = mMRenderer.drawFrame(cameraNv21Byte, cameraTexId, cameraWidth, cameraHeight);
        trackPose();
        trackStatus();
        return TexId;
    }

    @Override
    public void onCameraChanged(int cameraFacing, int cameraOrientation) {
        mIsFlipX = cameraFacing == mRenderer.CAMERA_FACING_FRONT;
        mMRenderer.onCameraChanged(cameraFacing, cameraOrientation);
    }

    @Override
    public void onCameraOpened(int cameraWidth, int cameraHeight) {
    }

    @Override
    public void onCameraError(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ToastUtil.makeText(CameraActivity.this, message);
            }
        });
    }

     public void plot(View view){
         boolean isShow = flag;
         flag=!flag;
         if (isShow == true) {
             mMRenderer.selectEffect(mFullEffect);
         } else {
             mMRenderer.unselectEffect(mFullEffect);
         }
     }

     public void switchCamera(View view){
         mCameraRenderer.switchCamera();
     }

    public void appear(View view){
        Handler handler = new Handler();
        Goodjob.animate().alpha(1).setDuration(2000);
        scorecount++;
        Score = "Total Score: "+scorecount;
        Myscore.setText(Score);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Goodjob.animate().alpha(0);
            }
        },2000);
    }

    public void fade0(View view) {
        Handler handler = new Handler();
        ImageView Standing = (ImageView) findViewById(R.id.standing);
        ImageView imageview1 = (ImageView) findViewById(R.id.imageView1);
        ImageView imageview2 = (ImageView) findViewById(R.id.imageView2);

        Coutdowntext.setText(countnum);
        Coutdowntext .setVisibility(View.VISIBLE);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Coutdowntext.setText("2");
            }
        },1000);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Coutdowntext.setText("1");
            }
        },2000);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Coutdowntext.setText("START");
            }
        },3000);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Coutdowntext .setVisibility(View.INVISIBLE);
                Standing.animate().alpha(0).setDuration(1000);
                scorecount=0;
                Score = "Total Score: "+scorecount;
                Myscore.setText(Score);
                Myscore.setVisibility(View.VISIBLE);
            }
        },4000);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                action0=true;
                StartAnimation.animate().translationYBy(-4500).setDuration(8000);
            }
        },6000);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                action1=true;
                imageview1.animate().translationYBy(-4500).setDuration(8000);
            }
        },12000);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                action2=true;
                imageview2.animate().translationYBy(-4500).setDuration(8000);
            }
        },18000);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Myscore.setVisibility(View.INVISIBLE);
                Finish .setVisibility(View.VISIBLE);
            }
        },27000);

    }

}