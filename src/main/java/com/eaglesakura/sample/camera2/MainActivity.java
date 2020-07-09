package com.eaglesakura.sample.camera2;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.List;


public class MainActivity extends Activity {

    CameraManager cameraManager;

    CameraDevice cameraDevice;

    /**
     * カメラプレビュー用のView
     */
    Camera2PreviewView cameraPreviewView;

    /**
     * カメラプレビューの挿入先
     */
    ViewGroup previewParent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 撮影モード指定
        {
            ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

            adapter.add("JPEG");
            adapter.add("バーストモード");
            adapter.add("RAW");
            adapter.add("マニュアル撮影(ISO感度 LOW)");
            adapter.add("マニュアル撮影(ISO感度 HIGH)");

            Spinner spinner = (Spinner) findViewById(R.id.Main_Setting_CameraMode);
            spinner.setAdapter(adapter);
        }

        // プレビュー有無を切り替える
        previewParent = (ViewGroup) findViewById(R.id.Main_Preview_Root);
        ((Switch) findViewById(R.id.Main_WithPreview)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked && cameraPreviewView == null) {
                    // プレビューを開始する
                    cameraPreviewView = new Camera2PreviewView(MainActivity.this, cameraManager, cameraDevice);
                    previewParent.addView(cameraPreviewView);
                } else if (!isChecked && cameraPreviewView != null) {
                    // プレビューを停止する
                    previewParent.removeView(cameraPreviewView);
                    cameraPreviewView = null;
                }
            }
        });
        ((Switch) findViewById(R.id.Main_WithPreview)).setChecked(true);
    }

    /**
     * 選択されているカメラモードを取得する
     *
     * @return
     */
    int getSelectedCameraMode() {
        return ((Spinner) findViewById(R.id.Main_Setting_CameraMode)).getSelectedItemPosition();
    }


    @Override
    protected void onResume() {
        super.onResume();

        // カメラの初期化を行う
        initializeCamera();
    }

    /**
     * カメラの初期化を行う
     */
    void initializeCamera() {
        // Camera機能はSystemServiceとして提供される
        this.cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
        this.cameraDevice = null;

        try {
            String[] cameraIdList = cameraManager.getCameraIdList();
            for (String id : cameraIdList) {
                log("camera id(%s)", id);
            }

            // 第３引数 handler は、コールバックを受け取りたいスレッドを指定できる
            // nullの場合、現在のスレッドで受け取る
            cameraManager.openCamera(cameraIdList[0], new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice camera) {
                    cameraDevice = camera;
                    log("camera opened");

                    // プレビューを再開する
                    if (cameraPreviewView != null) {
                        cameraPreviewView.startPreview(cameraManager, cameraDevice);
                    }

                    // カメラの能力を調べる
                    try {
                        TextView hwLevelInfo = (TextView) findViewById(R.id.Main_Info_SupportedLevel);
                        final CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraDevice.getId());
                        int supportedHardwareLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
                        switch (supportedHardwareLevel) {
                            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL:
                                log("support full!");
                                hwLevelInfo.setText("SUPPORTED FULL");
                                break;
                            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED:
                                log("support limited");
                                hwLevelInfo.setText("SUPPORTED LIMITED");
                                break;
                            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY:
                                log("support legacy");
                                hwLevelInfo.setText("SUPPORTED LEGACY");
                                break;
                            default:
                                throw new IllegalStateException("unknown supported levell :: " + supportedHardwareLevel);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                }

                @Override
                public void onDisconnected(CameraDevice camera) {
                    log("camera disconnected");
                }

                @Override
                public void onError(CameraDevice camera, int error) {
                    toast("camera open error(%d)", error);
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        // カメラを開いていた場合、閉じる
        if (cameraPreviewView != null) {
            cameraPreviewView.stopPreview();
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
    }

    void log(String message, Object... args) {
        Log.i("camera2sample", String.format(message, args));
    }

    void toast(String message, Object... args) {
        log(message, args);
        Toast.makeText(this, String.format(message, args), Toast.LENGTH_SHORT).show();
    }

    /**
     * メディア用のFileクラスを生成する
     *
     * @param ext
     * @return
     */
    public static File createMediaFile(String ext) {
        File file = new File(Environment.getExternalStorageDirectory(), "DCIM/camera2");
        file.mkdirs();

        return new File(file, String.format("%d%s", System.currentTimeMillis(), ext));
    }

    /**
     * 画像ファイルから小さい画像を取得する
     *
     * @param file
     * @return
     */
    public static Bitmap loadSmallImage(File file, int sample) {
        try {

            BitmapFactory.Options opt = new BitmapFactory.Options();
            opt.inSampleSize = sample;
            opt.inScaled = true;
            return BitmapFactory.decodeFile(file.getPath(), opt);

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
