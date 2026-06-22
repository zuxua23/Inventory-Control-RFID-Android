package com.example.inventory_system_ht;

import android.app.Application;

import com.densowave.scannersdk.Common.CommManager;
import com.densowave.scannersdk.Common.CommScanner;
import com.densowave.scannersdk.Common.CommStatusChangedEvent;
import com.densowave.scannersdk.Const.CommConst;
import com.densowave.scannersdk.Listener.ScannerStatusListener;

import com.densowave.scannersdk.Listener.ScannerAcceptStatusListener;
import com.example.inventory_system_ht.util.PrefManager;
import com.example.inventory_system_ht.util.ScannerManager;

public class MyApplication extends Application
        implements ScannerAcceptStatusListener, ScannerStatusListener {
    @Override
    public void onCreate() {
        super.onCreate();
        new PrefManager(this).clearSession();
    }

    public void startScannerAccept() {
        CommManager.addAcceptStatusListener(this);
        CommManager.startAccept();
    }

    @Override
    public void OnScannerAppeared(CommScanner scanner) {
        CommManager.endAccept();
        CommManager.removeAcceptStatusListener(this);
        try {
            scanner.claim();
            ScannerManager.getInstance().setScanner(scanner);
            scanner.addStatusListener(this);
        } catch (Exception ignored) {}
    }

    @Override
    public void onScannerStatusChanged(CommScanner scanner, CommStatusChangedEvent event) {
        if (event.getStatus() == CommConst.ScannerStatus.CLOSE_WAIT) {
            try { scanner.close(); } catch (Exception ignored) {}
            ScannerManager.getInstance().clearScanner();

            startScannerAccept();
        }
    }
}
