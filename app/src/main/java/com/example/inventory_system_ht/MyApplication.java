package com.example.inventory_system_ht;

import android.app.Application;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.densowave.scannersdk.Common.CommManager;
import com.densowave.scannersdk.Common.CommScanner;
import com.densowave.scannersdk.Common.CommStatusChangedEvent;
import com.densowave.scannersdk.Const.CommConst;
import com.densowave.scannersdk.Listener.ScannerStatusListener;
import com.densowave.scannersdk.Listener.ScannerAcceptStatusListener;

import com.example.inventory_system_ht.util.PrefManager;
import com.example.inventory_system_ht.util.ScannerManager;
import com.example.inventory_system_ht.util.SyncWorker;

public class MyApplication extends Application
        implements ScannerAcceptStatusListener, ScannerStatusListener {

    // Unique name — WorkManager will deduplicate enqueues with this key
    private static final String SYNC_WORK_NAME = "global_pending_sync";

    // Debounce: prevent rapid onAvailable() calls from enqueueing multiple workers
    private volatile boolean syncScheduled = false;
    private final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable syncRunnable = () -> {
        syncScheduled = false;
        PrefManager pref = new PrefManager(MyApplication.this);
        if (pref.getToken() == null || pref.getToken().isEmpty()) return;

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        WorkManager.getInstance(MyApplication.this).enqueueUniqueWork(
                SYNC_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                new OneTimeWorkRequest.Builder(SyncWorker.class)
                        .setConstraints(constraints)
                        .build()
        );
    };

    private ConnectivityManager.NetworkCallback networkCallback;

    @Override
    public void onCreate() {
        super.onCreate();
        registerGlobalNetworkCallback();
    }

    /**
     * Global network callback — fires whenever network becomes available.
     * Uses KEEP policy so duplicate enqueues from rapid onAvailable() calls
     * are ignored if a SyncWorker is already queued or running.
     */
    private void registerGlobalNetworkCallback() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null) return;

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                // Debounce: if already scheduled, cancel and reschedule (300ms window)
                // This handles rapid multiple onAvailable() calls from ConnectivityManager
                if (syncScheduled) {
                    mainHandler.removeCallbacks(syncRunnable);
                }
                syncScheduled = true;
                mainHandler.postDelayed(syncRunnable, 300);
            }
        };

        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .build();

        cm.registerNetworkCallback(request, networkCallback);
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        mainHandler.removeCallbacks(syncRunnable);
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm != null && networkCallback != null) {
            cm.unregisterNetworkCallback(networkCallback);
        }
    }

    // ── Scanner lifecycle ─────────────────────────────────────────────────────

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