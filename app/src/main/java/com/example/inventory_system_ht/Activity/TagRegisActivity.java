package com.example.inventory_system_ht.Activity;

import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import com.example.inventory_system_ht.Helper.AppDatabase;
import com.example.inventory_system_ht.Helper.SyncWorker;
import com.example.inventory_system_ht.Models.PendingSubmitEntity;
import com.google.gson.Gson;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;

import android.annotation.SuppressLint;

import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.densowave.scannersdk.Barcode.BarcodeDataReceivedEvent;
import com.densowave.scannersdk.Common.CommScanner;
import com.densowave.scannersdk.Listener.BarcodeDataDelegate;
import com.densowave.scannersdk.Listener.RFIDDataDelegate;
import com.densowave.scannersdk.RFID.RFIDData;
import com.densowave.scannersdk.RFID.RFIDDataReceivedEvent;
import com.example.inventory_system_ht.Adapter.TagRegisAdapter;
import com.example.inventory_system_ht.Helper.ApiClient;
import com.example.inventory_system_ht.Helper.ApiService;
import com.example.inventory_system_ht.Helper.PrefManager;
import com.example.inventory_system_ht.Helper.RfidBulkHelper;
import com.example.inventory_system_ht.Helper.ScannerManager;
import com.example.inventory_system_ht.Models.AuthModels;
import com.example.inventory_system_ht.Models.GeneralResponse;
import com.example.inventory_system_ht.Models.TagModels;
import com.example.inventory_system_ht.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import retrofit2.Call;

@SuppressLint("UseSwitchCompatOrMaterialCode")
public class TagRegisActivity extends BaseScannerActivity
        implements RFIDDataDelegate, BarcodeDataDelegate {

    private EditText resultScan;
    private TextView tvScanned;
    private Switch switchRfid;
    private Button btnClear, btnSubmitRegis;
    private RecyclerView rvTags;
    private CardView btnPowerDropdown;
    private TextView tvPowerLevel;
    private TagRegisAdapter adapter;
    private List<TagModels.TagModel> registeredTagList;
    private final Handler handler = new Handler();
    private AppDatabase db;

    private final List<String> powerList = new ArrayList<>(Arrays.asList(
            "10 dBm", "15 dBm", "20 dBm", "25 dBm", "27 dBm"
    ));

    @Override
    protected CommScanner getScannerInstance() {
        return ScannerManager.getInstance().getScanner();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_regist);

        db = AppDatabase.getDatabase(this);

        // ── findViewById ──────────────────────────────────────────────
        resultScan       = findViewById(R.id.resultScan);
        tvScanned        = findViewById(R.id.tvScanned);
        switchRfid       = findViewById(R.id.switchRfid);
        btnClear         = findViewById(R.id.btnClear);
        btnSubmitRegis   = findViewById(R.id.btnSubmitRegis);
        rvTags           = findViewById(R.id.rvTags);
        btnPowerDropdown = findViewById(R.id.btnPowerDropdown);
        tvPowerLevel     = findViewById(R.id.tvPowerLevel);

        // Dropdown hidden by default saat switch masih OFF
        btnPowerDropdown.setVisibility(View.GONE);

        // ── RecyclerView ──────────────────────────────────────────────
        rvTags.setItemAnimator(null);
        rvTags.setLayoutManager(new LinearLayoutManager(this));
        registeredTagList = new ArrayList<>();
        adapter = new TagRegisAdapter(registeredTagList);
        rvTags.setAdapter(adapter);
        adapter.setOnItemClickListener(item -> {
            int pos = registeredTagList.indexOf(item);
            if (pos != -1) showDeleteDialog(item, pos);
        });

        // ── Switch RFID ON/OFF ────────────────────────────────────────
        switchRfid.setOnCheckedChangeListener((btn, isChecked) -> {
            CommScanner scanner = getScannerInstance();

            // Update icon battery tiap toggle
            updateReaderBattery(findViewById(R.id.ivReaderBattery), isChecked);

            if (isChecked) {
                if (scanner == null) {
                    showError("SP1 Reader not connected!");
                    switchRfid.setChecked(false);
                    updateReaderBattery(findViewById(R.id.ivReaderBattery), false);
                    return;
                }

                // Tutup barcode dulu — SDK tidak bisa RFID + Barcode bersamaan
                RfidBulkHelper.closeBarcode(scanner);

                int power = parsePower(tvPowerLevel.getText().toString(), 27);
                boolean ok = RfidBulkHelper.openInventory(scanner, this, power);

                if (ok) {
                    showSuccess("RFID Bulk Scan: ON");
                    resultScan.setEnabled(false);
                    btnPowerDropdown.setVisibility(View.VISIBLE);
                } else {
                    showError("Failed to start RFID inventory");
                    switchRfid.setChecked(false);
                }

            } else {
                RfidBulkHelper.closeInventory(scanner);
                if (scanner != null) RfidBulkHelper.openBarcode(scanner, this);
                showSagaFeedback("RFID Bulk Scan: OFF", true);
                resultScan.setEnabled(true);
                resultScan.requestFocus();
                btnPowerDropdown.setVisibility(View.GONE);
            }
        });

        // ── Power dropdown popup ──────────────────────────────────────
        btnPowerDropdown.setOnClickListener(v ->
                showPowerDropdownPopup(btnPowerDropdown, powerList, tvPowerLevel));

        // ── Barcode input via EditText (saat RFID OFF) ────────────────
        resultScan.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int i, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int i, int b, int c) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (switchRfid.isChecked()) return;
                String data = s.toString().trim();
                if (data.length() < 4) return;

                resultScan.removeTextChangedListener(this);
                resultScan.setText("");
                resultScan.addTextChangedListener(this);

                processScannedData(data);
            }
        });

        // ── Tombol ────────────────────────────────────────────────────
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        btnClear.setOnClickListener(v -> {
            registeredTagList.clear();
            adapter.notifyDataSetChanged();
            updateCount();
            showSuccess("List cleared");
        });

        btnSubmitRegis.setOnClickListener(v -> {
            if (registeredTagList.isEmpty()) showWarning("No tags scanned yet!");
            else showBulkConfirmDialog();
        });

        resultScan.setShowSoftInputOnFocus(false);
        resultScan.postDelayed(() -> resultScan.requestFocus(), 150);
    }

    // ── RFID Bulk Callback ────────────────────────────────────────────
    @Override
    public void onRFIDDataReceived(CommScanner scanner, RFIDDataReceivedEvent event) {
        for (RFIDData data : event.getRFIDData()) {
            String epc = RfidBulkHelper.bytesToHex(data.getUII());
            if (!epc.isEmpty()) {
                handler.post(() -> processScannedData(epc));
            }
        }
    }

    // ── Barcode Callback ──────────────────────────────────────────────
    @Override
    public void onBarcodeDataReceived(CommScanner scanner, BarcodeDataReceivedEvent event) {
        if (!event.getBarcodeData().isEmpty()) {
            String barcode = new String(event.getBarcodeData().get(0).getData());
            handler.post(() -> processScannedData(barcode));
        }
    }

    // ── Process data (RFID / Barcode) ─────────────────────────────────
    private void processScannedData(String data) {
        for (TagModels.TagModel t : registeredTagList) {
            if (t.getEpcTag().equalsIgnoreCase(data)) {
                playScanFeedback(1); // beep duplicate, silent skip
                return;
            }
        }

        TagModels.TagModel newTag = new TagModels.TagModel(
                data, data, "TAG", "Scanned Item", "STAGING", 0
        );
        registeredTagList.add(0, newTag);
        adapter.setLastScannedPosition(0);
        adapter.notifyItemInserted(0);
        rvTags.scrollToPosition(0);
        updateCount();
        playScanFeedback(0); // beep success
    }

    private void updateCount() {
        tvScanned.setText("Scanned: " + registeredTagList.size());
    }

    // ── Lifecycle ─────────────────────────────────────────────────────
    @Override
    protected void onResume() {
        super.onResume();
        CommScanner scanner = getScannerInstance();

        updateReaderBattery(findViewById(R.id.ivReaderBattery), switchRfid.isChecked());
        if (!switchRfid.isChecked() && scanner != null) {
            RfidBulkHelper.openBarcode(scanner, this);
        }

        if (getHTBatteryLevel() <= 15)
            showWarning("HT Battery " + getHTBatteryLevel() + "%, charge now!");
    }

    @Override
    protected void onPause() {
        super.onPause();
        CommScanner scanner = getScannerInstance();
        RfidBulkHelper.closeInventory(scanner);
        RfidBulkHelper.closeBarcode(scanner);
    }

    // ── Dialog Confirm Register ───────────────────────────────────────
    private void showBulkConfirmDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_regist);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        ((TextView) dialog.findViewById(R.id.tvTitle))
                .setText("Register " + registeredTagList.size() + " Tags?");
        dialog.findViewById(R.id.btnNo).setOnClickListener(v -> dialog.dismiss());
        dialog.findViewById(R.id.btnSave).setOnClickListener(v -> {
            dialog.dismiss();
            List<String> ids = new ArrayList<>();
            for (TagModels.TagModel t : registeredTagList) ids.add(t.getEpcTag());
            hitApiRegisterTags(ids);
        });
        dialog.show();
    }

    // ── Dialog Delete Item ────────────────────────────────────────────
    private void showDeleteDialog(TagModels.TagModel tag, int position) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_regist);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        ((TextView) dialog.findViewById(R.id.tvTitle)).setText("Remove tag from list?");

        Button btnYes = dialog.findViewById(R.id.btnSave);
        btnYes.setText("Remove");
        btnYes.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(Color.RED));

        dialog.findViewById(R.id.btnNo).setOnClickListener(v -> dialog.dismiss());
        btnYes.setOnClickListener(v -> {
            dialog.dismiss();
            registeredTagList.remove(position);
            adapter.notifyItemRemoved(position);
            adapter.notifyItemRangeChanged(position, registeredTagList.size());
            updateCount();
            showSuccess("Tag removed");
        });
        dialog.show();
    }

    // ── API Register Tags ─────────────────────────────────────────────
    private void hitApiRegisterTags(List<String> tagIds) {
        if (!isNetworkConnected()) {
            new Thread(() -> {
                PendingSubmitEntity pending = new PendingSubmitEntity();
                pending.doId         = "TAG_REGISTRATION";
                pending.scannedCodes = new Gson().toJson(tagIds);
                pending.scannerType  = switchRfid.isChecked() ? "RFID" : "QR";
                pending.locId        = "";
                pending.createdAt    = System.currentTimeMillis();
                db.appDao().insertPendingSubmit(pending);

                WorkManager.getInstance(getApplicationContext()).enqueue(
                        new OneTimeWorkRequest.Builder(SyncWorker.class)
                                .setConstraints(new Constraints.Builder()
                                        .setRequiredNetworkType(NetworkType.CONNECTED).build())
                                .build());

                runOnUiThread(() -> {
                    showWarning("Offline – " + tagIds.size() + " tag tersimpan lokal, akan dikirim saat online");
                    registeredTagList.clear();
                    adapter.notifyDataSetChanged();
                    updateCount();
                });
            }).start();
            return;
        }

        showLoading();
        String token = "Bearer " + new PrefManager(this).getToken();
        ApiClient.getClient(this).create(ApiService.class)
                .registerTags(token, new AuthModels.RegisterRequest(tagIds))
                .enqueue(new retrofit2.Callback<GeneralResponse>() {
                    @Override
                    public void onResponse(Call<GeneralResponse> call,
                                           retrofit2.Response<GeneralResponse> response) {
                        hideLoading();
                        if (response.isSuccessful()) {
                            showSuccess(response.body().getMessage());
                            playScanFeedback(0);
                            registeredTagList.clear();
                            adapter.notifyDataSetChanged();
                            updateCount();
                        } else {
                            handleApiError(response);
                            playScanFeedback(2);
                        }
                    }

                    @Override
                    public void onFailure(Call<GeneralResponse> call, Throwable t) {
                        hideLoading();
                        handleFailure(t);
                        playScanFeedback(2);
                    }
                });
    }

    // ── Helper ────────────────────────────────────────────────────────
    private int parsePower(String text, int defaultVal) {
        try {
            return Integer.parseInt(text.replace(" dBm", "").trim());
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }
}