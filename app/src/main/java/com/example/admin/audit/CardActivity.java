package com.example.admin.audit;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class CardActivity extends AppCompatActivity {

    private TextureView surfaceTextureView;
    private TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            setupCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };

    private CameraDevice mCameraDevice;
    private CameraDevice.StateCallback mCameraDeviceStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            mCameraDevice = camera;
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                camera.close();
                mCameraDevice = null;
            }
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                camera.close();
            }
            mCameraDevice = null;
        }
    };

    private HandlerThread mBackgroundHandlerThread;
    private Handler mBackgroundHandler;
    private String mCameraId;
    private Size mPreviewSize;
    private static SparseIntArray orientations = new SparseIntArray();

    static {
        orientations.append(Surface.ROTATION_0, 0);
        orientations.append(Surface.ROTATION_90, 90);
        orientations.append(Surface.ROTATION_180, 180);
        orientations.append(Surface.ROTATION_270, 270);
    }

    private static class CompareSizeByArea implements Comparator<Size>{

        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() /
                    (long) rhs.getWidth() * rhs.getHeight());
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_card);

        surfaceTextureView = (TextureView) findViewById(R.id.camera_surface_texture_view);

    }

    @Override
    protected void onResume() {
        super.onResume();

        startBackgroundThread();
        if (surfaceTextureView.isAvailable()){
            setupCamera(surfaceTextureView.getWidth(), surfaceTextureView.getHeight());
        } else{
            surfaceTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    @Override
    protected void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        View decorView= getWindow().getDecorView();
        if (hasFocus){
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|
                    View.SYSTEM_UI_FLAG_FULLSCREEN|
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    }

    private void setupCamera(int width, int height){
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

            try{
                for (String cameraId : cameraManager.getCameraIdList()){
                    CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
                    if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT){
                        continue;
                    }
                    StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    int deviceOrientation = getWindowManager().getDefaultDisplay().getRotation();
                    int totalOrientation = sensorToDeviceRotation(cameraCharacteristics, deviceOrientation);
                    boolean swapRotation = totalOrientation == 90 || totalOrientation == 270;
                    int rotatedWidth = width;
                    int rotatedHeight = height;
                    if (swapRotation){
                        rotatedWidth = height;
                        rotatedHeight = width;
                    }
                    mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), rotatedWidth, rotatedHeight);
                    mCameraId = cameraId;
                    return;
                }
            } catch (CameraAccessException e){
                e.printStackTrace();
            }
        }
    }

    private void closeCamera(){
        if (mCameraDevice != null){
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mCameraDevice.close();
            }
            mCameraDevice = null;
        }
    }

    private void startBackgroundThread(){
        mBackgroundHandlerThread = new HandlerThread("Audit");
        mBackgroundHandlerThread.start();
        mBackgroundHandler = new Handler(mBackgroundHandlerThread.getLooper());
    }

    private void stopBackgroundThread(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            mBackgroundHandlerThread.quitSafely();
            try {
                mBackgroundHandlerThread.join();
                mBackgroundHandlerThread = null;
                mBackgroundHandler = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private static int sensorToDeviceRotation(CameraCharacteristics cameraCharacteristics, int deviceOrientation){

            @SuppressLint({"NewApi", "LocalSuppress"}) int sensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            deviceOrientation = orientations.get(deviceOrientation);
        return (sensorOrientation + deviceOrientation + 360) % 360;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private static Size chooseOptimalSize(Size[] choices, int width, int height){
        List<Size> bigEnough = new ArrayList<Size>();
        for (Size option: choices){
            if (option.getHeight() == option.getWidth() * height / width &&
                    option.getWidth() >= width && option.getHeight() >= height){
                bigEnough.add(option);
            }
        }

        if (bigEnough.size() > 0){
            return Collections.min(bigEnough, new CompareSizeByArea());
        } else{
            return choices[0];
        }
    }
}
