package com.eaglesakura.sample.camera2;

import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;

import java.util.Arrays;

/**
 * Camera2 APIのプレビュー用View
 * <p/>
 * 生成後、自動的にプレビューを開始する
 */
public class Camera2PreviewView extends TextureView implements TextureView.SurfaceTextureListener {

    final MainActivity mainActivity;

    CameraManager cameraManager;

    CameraDevice cameraDevice;

    CameraCaptureSession captureSession;

    Surface captureSurface;

    /**
     * プレビューに使用するカメラ解像度
     */
    Size previewSize;

    public Camera2PreviewView(MainActivity activity, CameraManager cameraManager, CameraDevice cameraDevice) {
        super(activity);
        this.mainActivity = activity;
        this.cameraManager = cameraManager;
        this.cameraDevice = cameraDevice;

        setSurfaceTextureListener(this);
    }

    /**
     * Sets the aspect ratio for this view. The size of the view will be measured based on the ratio
     * calculated from the parameters. Note that the actual sizes of parameters don't matter, that
     * is, calling setAspectRatio(2, 3) and setAspectRatio(4, 6) make the same result.
     *
     * @param width  Relative horizontal size
     * @param height Relative vertical size
     */
    void setAspectRatio(int width, int height) {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Size cannot be negative.");
        }
        int rotation = mainActivity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, getWidth(), getHeight());
        RectF bufferRect = new RectF(0, 0, previewSize.getHeight(), previewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            log("rotate view(%d)", rotation);

            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) getHeight() / previewSize.getHeight(),
                    (float) getWidth() / previewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        }
        setTransform(matrix);
    }

    /**
     * プレビューを開始する
     */
    public void startPreview(CameraManager cameraManager, final CameraDevice cameraDevice) {
        try {
            this.cameraDevice = cameraDevice;
            this.cameraManager = cameraManager;

            if (getSurfaceTexture() == null || cameraDevice == null || cameraManager == null) {
                // サーフェイスが用意されていないか、カメラデバイスに接続されていない
                return;
            }

            // プレビューサイズを取得する
            CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraDevice.getId());
            Size[] sizes = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(SurfaceTexture.class);
            // プレビュー可能なサイズをログ出力
            for (Size size : sizes) {
                log("preview size(%d x %d)", size.getWidth(), size.getHeight());
            }
            previewSize = sizes[0];
            log("selected preview size(%d x %d)", previewSize.getWidth(), previewSize.getHeight());


            // サーフェイスのサイズとViewのアスペクト比を設定する
            getSurfaceTexture().setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            setAspectRatio(previewSize.getWidth(), previewSize.getHeight());

            // 焼き込み用のサーフェイスを用意する
            captureSurface = new Surface(getSurfaceTexture());
            // プレビュー用のセッションを開始する
            cameraDevice.createCaptureSession(Arrays.asList(captureSurface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    log("session onConfigured(%s)", session.toString());
                    captureSession = session;

                    try {
                        CaptureRequest.Builder captureRequest = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                        captureRequest.addTarget(captureSurface);
                        captureSession.setRepeatingRequest(captureRequest.build(), null, null);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                    log("session onConfigureFailed(%s)", session.toString());
                }
            }, null);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * プレビューを停止する
     */
    public void stopPreview() {
        if (captureSession != null) {
            try {
                captureSession.abortCaptures();
                captureSession.close();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
            captureSession = null;
        }

        if (captureSurface != null) {
            captureSurface.release();
            captureSurface = null;
        }

        captureSession = null;
        cameraDevice = null;

    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
        startPreview(cameraManager, cameraDevice);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        // プレビューテクスチャが無効になったら、カメラのプレビューを停止する
        stopPreview();
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {

    }

    void log(String message, Object... args) {
        Log.i("preview", String.format(message, args));
    }
}
