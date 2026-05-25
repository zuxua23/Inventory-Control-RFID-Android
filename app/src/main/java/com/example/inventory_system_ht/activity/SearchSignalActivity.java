package com.example.inventory_system_ht.activity;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.cardview.widget.CardView;

import com.densowave.scannersdk.Common.CommScanner;
import com.densowave.scannersdk.Listener.RFIDDataDelegate;
import com.densowave.scannersdk.RFID.RFIDData;
import com.densowave.scannersdk.RFID.RFIDDataReceivedEvent;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import com.example.inventory_system_ht.activity.base.ScannerActivity;
import com.example.inventory_system_ht.model.TagModel;
import com.example.inventory_system_ht.util.LogManager;
import com.example.inventory_system_ht.util.PrefManager;
import com.example.inventory_system_ht.util.RfidBulkHelper;
import com.example.inventory_system_ht.util.ScannerManager;
import com.example.inventory_system_ht.R;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.Arrays;
import java.util.List;

public class SearchSignalActivity extends ScannerActivity implements RFIDDataDelegate {

    private TagModel.SearchItemDto selectedItem;
    private TagModel.TagDetailDto selectedDetail;

    private LinearLayout containerSignalBars;
    private TextView tvItemTitle, tvRssiValue;
    private Spinner spinnerPower;
    private BottomSheetDialog currentDialog;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean tagFoundNotified = false;

    private static final int NO_SIGNAL_TIMEOUT_MS = 8000;

    private final List<String> powerList = Arrays.asList(
            "5 dBm", "10 dBm", "15 dBm", "18 dBm", "21 dBm", "24 dBm", "27 dBm", "30 dBm"
    );

    private final Runnable noSignalRunnable = new Runnable() {
        @Override
        public void run() {
            showWarning("Tag not detected, move closer");
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
        CommScanner scanner = getScannerInstance();
        updateReaderBattery(findViewById(R.id.ivReaderBattery));

        tagFoundNotified = false;
        resetSignalDisplay();

        if (scanner != null) {
            int power = parsePower(spinnerPower.getSelectedItem().toString(), 21);
            RfidBulkHelper.closeBarcode(scanner);
            RfidBulkHelper.openInventory(scanner, this, power);
            handler.removeCallbacks(noSignalRunnable);
            handler.postDelayed(noSignalRunnable, NO_SIGNAL_TIMEOUT_MS);
        } else {
            showWarning("RFID reader not connected");
        }

        int bat = getHTBatteryLevel();
        if (bat <= 15) {
            showWarning("Battery low: " + bat + "%");
            playScanFeedback(2);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(noSignalRunnable);
        RfidBulkHelper.closeInventory(getScannerInstance());
        if (currentDialog != null && currentDialog.isShowing()) currentDialog.dismiss();
    }

    private void initViews() {
        containerSignalBars = findViewById(R.id.containerSignalBars);
        tvItemTitle = findViewById(R.id.tvItemTitle);
        tvRssiValue = findViewById(R.id.tvRssiValue);
        spinnerPower = findViewById(R.id.spinnerPower);

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, R.layout.item_spinner_selected, R.id.tvSpinnerSelected, powerList) {
            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View view = LayoutInflater.from(getContext()).inflate(R.layout.item_dropdown, parent, false);
                TextView tv = view.findViewById(R.id.tvDropdownItem);
                ImageView icon = view.findViewById(R.id.ivDropdownIcon);
                if (tv != null) tv.setText(getItem(position));
                if (icon != null) icon.setVisibility(View.GONE);
                return view;
            }
        };
        spinnerPower.setAdapter(adapter);
        spinnerPower.setSelection(4);
    }

    private void setupListeners() {
        spinnerPower.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                CommScanner scanner = getScannerInstance();
                if (scanner != null) {
                    int power = parsePower(powerList.get(position), 21);
                    RfidBulkHelper.closeInventory(scanner);
                    RfidBulkHelper.openInventory(scanner, SearchSignalActivity.this, power);
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        findViewById(R.id.btnStopSearch).setOnClickListener(v -> finish());
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    @Override
    public void onRFIDDataReceived(CommScanner scanner, RFIDDataReceivedEvent event) {
        for (RFIDData data : event.getRFIDData()) {
            String epc = RfidBulkHelper.bytesToHex(data.getUII());
            float rssi = data.getRSSI() / 10f;

            if (selectedItem != null && epc.equalsIgnoreCase(selectedItem.getEpcTag())) {
                handler.removeCallbacks(noSignalRunnable);
                handler.post(() -> {
                    playScanFeedback(0);
                    updateSignalBars(rssi);
                    handler.postDelayed(noSignalRunnable, NO_SIGNAL_TIMEOUT_MS);
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

        final int finalLevel = level;
        handler.post(() -> {
            tvRssiValue.setText(String.format("%.1f dBm", rssi));
            int activeColor = (finalLevel >= 8)
                    ? Color.parseColor("#4CAF50")
                    : Color.parseColor("#03A9F4");

            for (int i = 0; i < containerSignalBars.getChildCount(); i++) {
                containerSignalBars.getChildAt(i).setBackgroundColor(
                        i < finalLevel ? activeColor : Color.parseColor("#E0E0E0"));
            }

            if (finalLevel >= 9 && !tagFoundNotified) {
                tagFoundNotified = true;
                showSuccess("Tag found! Very close.");
                playScanFeedback(0);
            } else if (finalLevel < 7) {
                tagFoundNotified = false;
            }
        });
    }

    private void resetSignalDisplay() {
        if (tvRssiValue != null) tvRssiValue.setText("-- dBm");
        if (containerSignalBars != null) {
            for (int i = 0; i < containerSignalBars.getChildCount(); i++) {
                containerSignalBars.getChildAt(i).setBackgroundColor(Color.parseColor("#E0E0E0"));
            }
        }
    }

    private void showTagDetailBottomSheet(TagModel.TagDetailDto detail) {
        if (currentDialog != null && currentDialog.isShowing()) currentDialog.dismiss();

        currentDialog = new BottomSheetDialog(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_tag_detail, null);

        ((TextView) view.findViewById(R.id.tvDetailItemName)).setText(detail.getItemName());
        ((TextView) view.findViewById(R.id.tvDetailTagId)).setText(detail.getTagId());
        ((TextView) view.findViewById(R.id.tvDetailEpc)).setText(detail.getEpcTag());
        ((TextView) view.findViewById(R.id.tvDetailLocation)).setText(detail.getLocation());

        TextView tvStatus = view.findViewById(R.id.tvDetailStatus);
        CardView cvStatus = view.findViewById(R.id.cvDetailStatus);
        tvStatus.setText(detail.getStatus());
        cvStatus.setCardBackgroundColor(statusColor(detail.getStatus()));
        view.findViewById(R.id.btnSearchSignal).setVisibility(View.GONE);

        currentDialog.setContentView(view);
        currentDialog.show();
    }

    private int statusColor(String status) {
        if (status == null) return Color.parseColor("#9E9E9E");
        switch (status.toUpperCase()) {
            case "STOCK IN": return Color.parseColor("#28a745");
            case "PREPARATION": return Color.parseColor("#ffc107");
            default: return Color.parseColor("#9E9E9E");
        }
    }
}
