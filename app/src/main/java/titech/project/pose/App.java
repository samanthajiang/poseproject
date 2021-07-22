package titech.project.pose;

import android.app.Application;

import titech.project.nama.mRenderer;

/**
 * @author Richie on 2020.05.21
 */
public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        mRenderer.setup(this);
    }

}
