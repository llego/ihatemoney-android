package net.llego.ihatemoney.android.activity;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.budiyev.android.codescanner.CodeScanner;
import com.budiyev.android.codescanner.CodeScannerView;
import com.budiyev.android.codescanner.DecodeCallback;
import com.budiyev.android.codescanner.ErrorCallback;
import com.google.zxing.Result;

import net.llego.ihatemoney.R;

public class QrCodeScannerActivity extends AppCompatActivity {
    private CodeScanner mCodeScanner;
    private CodeScannerView mScannerView;

    private static final String TAG = QrCodeScannerActivity.class.getSimpleName();

    public static final String KEY_QR_CODE = "net.llego.ihatemoney.android.activity.key_qr_code";

    private final static int PERMISSION_CAMERA = 2;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_code_scanner);
        mScannerView = findViewById(R.id.scanner_view);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            initScanner();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, PERMISSION_CAMERA);
        }
    }

    /**
     * Create the scanner only once the camera permission is granted. The
     * library starts a camera thread on its first preview; doing that before
     * the permission result makes {@code Camera.open} throw and, without an
     * error callback, crash the process.
     */
    private void initScanner() {
        if (mCodeScanner != null || mScannerView == null) {
            return;
        }
        mCodeScanner = new CodeScanner(this, mScannerView);
        mCodeScanner.setDecodeCallback(new DecodeCallback() {
            @Override
            public void onDecoded(@NonNull final Result result) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        handleResult(result);
                    }
                });
            }
        });
        mCodeScanner.setErrorCallback(new ErrorCallback() {
            @Override
            public void onError(@NonNull Throwable thrown) {
                Log.e(TAG, "Camera error", thrown);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(QrCodeScannerActivity.this,
                                R.string.camera_unavailable, Toast.LENGTH_LONG).show();
                        finish();
                    }
                });
            }
        });
        mScannerView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mCodeScanner.startPreview();
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != PERMISSION_CAMERA) {
            return;
        }
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initScanner();
            if (mCodeScanner != null) {
                mCodeScanner.startPreview();
            }
        } else {
            Toast.makeText(this, R.string.camera_permission_required, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mCodeScanner != null && ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            mCodeScanner.startPreview();
        }
        if (BillsListViewActivity.DEBUG) { Log.d(TAG, "[Scanner onResume]"); }
    }

    @Override
    public void onPause() {
        if (mCodeScanner != null) {
            mCodeScanner.releaseResources();
        }
        if (BillsListViewActivity.DEBUG) { Log.d(TAG, "[Scanner onPause]"); }
        super.onPause();
    }

    public void handleResult(Result rawResult) {
        // Do something with the result here
        // Prints scan results
        Log.v(TAG, "QR result " + rawResult.getText());
        // Prints the scan format (qrcode, pdf417 etc.)
        Log.v(TAG, "QRresult" + rawResult.getBarcodeFormat().toString());
        //If you would like to resume scanning, call this method below:
        //mScannerView.resumeCameraPreview(this);

        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        // Vibrate for 300 milliseconds
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            //deprecated in API 26
            v.vibrate(300);
        }

        Intent intent = new Intent();
        intent.putExtra(KEY_QR_CODE, rawResult.getText());
        setResult(RESULT_OK, intent);
        finish();
    }
}
