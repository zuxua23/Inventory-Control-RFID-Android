package com.example.inventory_system_ht.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import com.densowave.scannersdk.Common.CommScanner;
import com.example.inventory_system_ht.MyApplication;
import com.example.inventory_system_ht.R;
import com.example.inventory_system_ht.activity.base.ScannerActivity;
import com.example.inventory_system_ht.util.PrefManager;

public class MainActivity extends ScannerActivity {

    @Override
    protected CommScanner getScannerInstance() { return null; }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.launch_activity);
        new PrefManager(this).clearSession();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{
                            Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.BLUETOOTH_SCAN
                    }, 100);
        } else {
            ((MyApplication) getApplication()).startScannerAccept();
            goToLogin();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            boolean granted = grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (!granted) {
                showWarning("Bluetooth permission is required for RFID scanner.");
            }
        }
        ((MyApplication) getApplication()).startScannerAccept();
        goToLogin();
    }

    private void goToLogin() {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        }, 500);
    }
}