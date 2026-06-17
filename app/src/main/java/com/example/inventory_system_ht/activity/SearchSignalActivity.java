package com.example.inventory_system_ht.activity;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.densowave.scannersdk.Common.CommScanner;
import com.densowave.scannersdk.Dto.RFIDScannerSettings;
import com.densowave.scannersdk.Listener.RFIDDataDelegate;
import com.densowave.scannersdk.RFID.RFIDData;
import com.densowave.scannersdk.RFID.RFIDDataReceivedEvent;

import com.example.inventory_system_ht.activity.base.ScannerActivity;
import com.example.inventory_system_ht.model.TagModel;
import com.example.inventory_system_ht.util.LogManager;
import com.example.inventory_system_ht.util.PrefManager;
import com.example.inventory_system_ht.util.RfidBulkHelper;
import com.example.inventory_system_ht.util.ScannerManager;
import com.example.inventory_system_ht.R;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class SearchSignalActivity extends ScannerActivity implements RFIDDataDelegate {
    private TagModel.SearchItemDto selectedItem;
    private TagModel.TagDetailDto selectedDetail;
    private LinearLayout containerSignalBars;
    private TextView tvItemTitle, tvRssiValue;
    private Button btnToggleScan;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean tagFoundNotified = false;
    private boolean isScanning = false;
    private static final int NO_SIGNAL_TIMEOUT_MS = 8000;

    private int currentBarLevel = 0;
    private int targetBarLevel = 0;
    private static final int BAR_ANIM_DELAY_MS = 40;

    private final Runnable barAnimRunnable = new Runnable() {
        @Override
        public void run() {
            if (currentBarLevel == targetBarLevel) return;
            if (currentBarLevel < targetBarLevel) {
                currentBarLevel++;
                renderBarLevel(currentBarLevel);
                handler.postDelayed(this, BAR_ANIM_DELAY_MS);
            } else {
                currentBarLevel--;
                renderBarLevel(currentBarLevel);
                if (currentBarLevel > targetBarLevel)
                    handler.postDelayed(this, BAR_ANIM_DELAY_MS / 2);
            }
        }
    };

    private final Runnable noSignalRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isScanning) return;
            playScanFeedback(2);
            resetSignalDisplay();
            handler.postDelayed(this, NO_SIGNAL_TIMEOUT_MS);
        }
    };

    @Override
    protected CommScanner getScannerInstance() {
        return ScannerManager.getInstance().getScanner();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search_signal);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.btnBack), (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
            );
            ViewGroup.MarginLayoutParams p = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            p.topMargin = bars.top + (int)(12 * getResources().getDisplayMetrics().density);
            v.setLayoutParams(p);
            return insets;
        });

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.btnStopSearch), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            ViewGroup.MarginLayoutParams p = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            int dp10 = (int)(10 * getResources().getDisplayMetrics().density);
            p.bottomMargin = bars.bottom + dp10;
            v.setLayoutParams(p);
            return insets;
        });

        selectedItem = (TagModel.SearchItemDto) getIntent().getSerializableExtra("SELECTED_ITEM");
        selectedDetail = (TagModel.TagDetailDto) getIntent().getSerializableExtra("SELECTED_DETAIL");

        initViews();
        setupListeners();

        if (selectedItem != null) {
            String location = (selectedDetail != null && selectedDetail.getLocation() != null)
                    ? selectedDetail.getLocation() : "-";
            tvItemTitle.setText("Locating: " + selectedItem.getItemName() + " | " + location);
        }

        FloatingActionButton fabLog = findViewById(R.id.fabLog);
        if (fabLog != null) {
            fabLog.setOnClickListener(v -> {
                Intent i = new Intent(this, LogActivity.class);
                i.putExtra(LogActivity.EXTRA_MENU, "Search Signal");
                startActivity(i);
            });
        }
        LogManager.get(this).log(LogManager.INFO, LogManager.ACTION_OPEN, "Search Signal", "", "Opened Search Signal", new PrefManager(this).getUserId());
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateReaderBattery(findViewById(R.id.ivReaderBattery));
        tagFoundNotified = false;
        resetSignalDisplay();

        int bat = getHTBatteryLevel();
        if (bat <= 15) {
            showWarning("Battery low: " + bat + "%");
            playScanFeedback(2);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopScanning();
    }

    private void initViews() {
        containerSignalBars = findViewById(R.id.containerSignalBars);
        tvItemTitle = findViewById(R.id.tvItemTitle);
        tvRssiValue = findViewById(R.id.tvRssiValue);
        btnToggleScan = findViewById(R.id.btnStopSearch);
        setButtonStartState();
    }

    private void setupListeners() {
        btnToggleScan.setOnClickListener(v -> {
            if (isScanning) stopScanning();
            else startScanning();
        });
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    private void startScanning() {
        CommScanner scanner = getScannerInstance();
        if (scanner == null) { showWarning("RFID reader not connected"); return; }

        if (selectedItem == null || selectedItem.getEpcTag() == null) {
            showWarning("No target EPC"); return;
        }

        isScanning = true;
        tagFoundNotified = false;
        RfidBulkHelper.closeBarcode(scanner);

        try {
            // Set delegate dulu sebelum openRead agar onRFIDDataReceived terpanggil
            scanner.getRFIDScanner().setDataDelegate(this);

            byte[] targetEpc = hexStringToBytes(selectedItem.getEpcTag());
            scanner.getRFIDScanner().openRead(
                    RFIDScannerSettings.RFIDBank.UII,
                    (short) 0,
                    (short) targetEpc.length,
                    new byte[]{0,0,0,0},
                    targetEpc
            );
        } catch (Exception e) {
            RfidBulkHelper.openInventory(scanner, this, 4);
        }

        handler.removeCallbacks(noSignalRunnable);
        handler.postDelayed(noSignalRunnable, NO_SIGNAL_TIMEOUT_MS);
        setButtonStopState();
    }

    private static byte[] hexStringToBytes(String hex) {
        if (hex == null || hex.isEmpty()) return new byte[0];
        String s = hex.length() % 2 == 0 ? hex : "0" + hex;
        byte[] b = new byte[s.length() / 2];
        for (int i = 0; i < b.length; i++)
            b[i] = (byte) Short.parseShort(s.substring(i*2, i*2+2), 16);
        return b;
    }

    private void stopScanning() {
        isScanning = false;
        handler.removeCallbacks(noSignalRunnable);
        handler.removeCallbacks(barAnimRunnable);
        // Gunakan closeInventoryKeepDelegate agar delegate tidak di-null-kan,
        // sehingga saat startScanning() dipanggil lagi tidak perlu set ulang dari nol
        RfidBulkHelper.closeInventoryKeepDelegate(getScannerInstance());
        tagFoundNotified = false;
        resetSignalDisplay();
        setButtonStartState();
    }

    private void setButtonStartState() {
        if (btnToggleScan == null) return;
        handler.post(() -> {
            btnToggleScan.setText("Start Searching");
            btnToggleScan.setBackgroundTintList(
                    ColorStateList.valueOf(getResources().getColor(R.color.blue_theme, null)));
        });
    }

    private void setButtonStopState() {
        if (btnToggleScan == null) return;
        handler.post(() -> {
            btnToggleScan.setText("Stop Searching");
            btnToggleScan.setBackgroundTintList(
                    ColorStateList.valueOf(getResources().getColor(R.color.red, null)));
        });
    }

    @Override
    public void onRFIDDataReceived(CommScanner scanner, RFIDDataReceivedEvent event) {
        if (!isScanning) return;
        for (RFIDData data : event.getRFIDData()) {
            String epc = RfidBulkHelper.bytesToHex(data.getUII());
            if (epc == null || epc.isEmpty()) continue;
            float rssi = data.getRSSI() / 10f;

            if (selectedItem != null && epc.equalsIgnoreCase(selectedItem.getEpcTag())) {
                final float finalRssi = rssi;
                LogManager.get(SearchSignalActivity.this).log(LogManager.INFO, LogManager.ACTION_SCAN,
                        "Search Signal", epc,
                        "Tag detected: " + selectedItem.getItemName() + " | RSSI: " + rssi + " dBm",
                        new PrefManager(SearchSignalActivity.this).getUserId());
                handler.removeCallbacks(noSignalRunnable);
                handler.post(() -> {
                    // Beep hanya berbunyi saat tag ditemukan sangat dekat (level >= 9),
                    // bukan setiap kali data RFID diterima
                    updateSignalBars(finalRssi);
                    if (isScanning) handler.postDelayed(noSignalRunnable, NO_SIGNAL_TIMEOUT_MS);
                });
            }
        }
    }

    public void updateSignalBars(float rssi) {
        int level;
        if (rssi > -45) level = 10;
        else if (rssi > -50) level = 9;
        else if (rssi > -55) level = 8;
        else if (rssi > -60) level = 7;
        else if (rssi > -65) level = 6;
        else if (rssi > -70) level = 5;
        else if (rssi > -75) level = 4;
        else if (rssi > -80) level = 3;
        else if (rssi > -85) level = 2;
        else if (rssi > -90) level = 1;
        else level = 0;

        tvRssiValue.setText(String.format("%.1f dBm", rssi));

        targetBarLevel = level;
        handler.removeCallbacks(barAnimRunnable);
        handler.post(barAnimRunnable);

        if (level >= 9 && !tagFoundNotified) {
            tagFoundNotified = true;
            String itemName = selectedItem != null ? selectedItem.getItemName() : "-";
            String epcTag  = selectedItem != null ? selectedItem.getEpcTag()  : "-";
            LogManager.get(this).log(LogManager.INFO, LogManager.ACTION_SCAN,
                    "Search Signal", epcTag,
                    "Tag very close: " + itemName + " | RSSI: " + String.format("%.1f", rssi) + " dBm",
                    new PrefManager(this).getUserId());
            showSuccess("Tag found! Very close.");
            playScanFeedback(0);
            isScanning = false;
            handler.removeCallbacks(noSignalRunnable);
            CommScanner sc = getScannerInstance();
            if (sc != null) RfidBulkHelper.closeInventoryKeepDelegate(sc);
            setButtonStartState();
        } else if (level < 7) {
            tagFoundNotified = false;
        }
    }

    private void renderBarLevel(int level) {
        if (containerSignalBars == null) return;
        int count = containerSignalBars.getChildCount();
        for (int i = 0; i < count; i++) {
            View bar = containerSignalBars.getChildAt(i);
            if (i < level) {
                int color;
                if (i < 3) color = Color.parseColor("#F44336");
                else if (i < 6) color = Color.parseColor("#FFC107");
                else if (i < 8) color = Color.parseColor("#4CAF50");
                else color = Color.parseColor("#2E7D32");
                bar.setBackgroundColor(color);
            } else {
                bar.setBackgroundColor(Color.parseColor("#E0E0E0"));
            }
        }
    }

    private void resetSignalDisplay() {
        handler.removeCallbacks(barAnimRunnable);
        currentBarLevel = 0;
        targetBarLevel = 0;
        if (tvRssiValue != null) tvRssiValue.setText("-- dBm");
        if (containerSignalBars != null) {
            for (int i = 0; i < containerSignalBars.getChildCount(); i++)
                containerSignalBars.getChildAt(i).setBackgroundColor(Color.parseColor("#E0E0E0"));
        }
    }
}
