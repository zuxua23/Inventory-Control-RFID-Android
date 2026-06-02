package com.example.inventory_system_ht.activity;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Size;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import com.example.inventory_system_ht.R;
import com.example.inventory_system_ht.util.LogManager;
import com.example.inventory_system_ht.util.PrefManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class BarcodeCameraActivity extends AppCompatActivity {
    public static final String EXTRA_BARCODE = "barcode";
    public static final int RESULT_PERMISSION_DENIED = 1001;
    private static final int REQ_CAMERA = 42;
    private PreviewView previewView;
    private View cornerOverlay;
    private ImageView btnTorch;
    private boolean torchOn = false;
    private ExecutorService cameraExecutor;
    private BarcodeScanner barcodeScanner;
    private androidx.camera.core.Camera camera;
    private final AtomicBoolean handled = new AtomicBoolean(false);
    private Preview preview;
    private ImageAnalysis imageAnalysis;
    private String currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_barcode_camera);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        currentUserId = new PrefManager(this).getUserId();

        previewView = findViewById(R.id.previewView);
        previewView.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);

        cornerOverlay = findViewById(R.id.viewfinderCorners);
        btnTorch = findViewById(R.id.btnTorch);

        findViewById(R.id.btnClose).setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });
        btnTorch.setOnClickListener(v -> toggleTorch());

        BarcodeScannerOptions opts = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                        Barcode.FORMAT_CODE_128,
                        Barcode.FORMAT_CODE_39,
                        Barcode.FORMAT_EAN_13,
                        Barcode.FORMAT_EAN_8,
                        Barcode.FORMAT_QR_CODE,
                        Barcode.FORMAT_DATA_MATRIX,
                        Barcode.FORMAT_PDF417
                ).build();
        barcodeScanner = BarcodeScanning.getClient(opts);

        cameraExecutor = Executors.newSingleThreadExecutor();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] perms, @NonNull int[] grants) {
        super.onRequestPermissionsResult(code, perms, grants);
        if (code == REQ_CAMERA) {
            if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                LogManager.get(this).log(LogManager.WARNING, LogManager.ACTION_SCAN,
                        "Camera Scanner", "", "Camera permission denied by user", currentUserId);
                setResult(RESULT_PERMISSION_DENIED);
                finish();
            }
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();

                preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                imageAnalysis = new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                imageAnalysis.setAnalyzer(cameraExecutor, this::analyze);

                provider.unbindAll();
                camera = provider.bindToLifecycle(
                        this,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                );
            } catch (Exception e) {
                LogManager.get(this).log(LogManager.ERROR, LogManager.ACTION_SCAN,
                        "Camera Scanner", "", "Failed to bind camera: " + e.getMessage(), currentUserId);
                finish();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void analyze(@NonNull ImageProxy imageProxy) {
        if (handled.get()) { imageProxy.close(); return; }
        if (imageProxy.getImage() == null) { imageProxy.close(); return; }

        InputImage image = InputImage.fromMediaImage(
                imageProxy.getImage(),
                imageProxy.getImageInfo().getRotationDegrees()
        );

        barcodeScanner.process(image)
                .addOnSuccessListener(barcodes -> {
                    for (Barcode b : barcodes) {
                        String raw = b.getRawValue();
                        if (raw != null && !raw.isEmpty() && handled.compareAndSet(false, true)) {
                            onBarcodeDetected(raw);
                            break;
                        }
                    }
                })
                .addOnCompleteListener(t -> imageProxy.close());
    }

    private void onBarcodeDetected(String raw) {
        LogManager.get(this).log(LogManager.INFO, LogManager.ACTION_SCAN,
                "Camera Scanner", raw, "Barcode scanned: " + raw, currentUserId);
        runOnUiThread(() -> {
            if (cornerOverlay != null) cornerOverlay.setSelected(true);

            android.content.Intent out = new android.content.Intent();
            out.putExtra(EXTRA_BARCODE, raw);
            setResult(RESULT_OK, out);
            previewView.postDelayed(this::finish, 250);
        });
    }

    private void toggleTorch() {
        if (camera == null || camera.getCameraInfo() == null) return;
        if (!camera.getCameraInfo().hasFlashUnit()) return;
        torchOn = !torchOn;
        camera.getCameraControl().enableTorch(torchOn);
        btnTorch.setSelected(torchOn);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) cameraExecutor.shutdown();
        if (barcodeScanner != null) barcodeScanner.close();
    }
}