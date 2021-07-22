package titech.project.pose.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;

import titech.project.pose.R;
import titech.project.pose.util.PermissionUtil;
import titech.project.pose.util.ScreenUtils;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScreenUtils.fullScreen(this);
        setContentView(R.layout.activity_main);

        PermissionUtil.checkPermission(this);

        ViewTouchListener viewTouchListener = new ViewTouchListener();
        findViewById(R.id.cv_camera).setOnTouchListener(viewTouchListener);
    }

    private class ViewTouchListener implements View.OnTouchListener {
        @Override
        public boolean onTouch(View v, MotionEvent event) {

                        if (v.getId() == R.id.cv_camera) {
                            Intent intent = new Intent(MainActivity.this, CameraActivity.class);
                            startActivity(intent);
                       }
            return true;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
    }
}
