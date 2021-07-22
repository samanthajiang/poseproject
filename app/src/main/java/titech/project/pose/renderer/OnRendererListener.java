package titech.project.pose.renderer;

public interface OnRendererListener {
    /**
     * Called when surface is created or recreated.
     */
    void onSurfaceCreated();

    /**
     * Called when surface'size changed.
     *
     * @param viewWidth
     * @param viewHeight
     */
    void onSurfaceChanged(int viewWidth, int viewHeight);

    /**
     * Called when surface is destroyed
     */
    void onSurfaceDestroy();
}
