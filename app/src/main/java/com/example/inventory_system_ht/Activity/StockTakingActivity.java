package com.example.inventory_system_ht.Activity;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.densowave.scannersdk.Barcode.BarcodeData;
import com.densowave.scannersdk.Barcode.BarcodeDataReceivedEvent;
import com.densowave.scannersdk.Common.CommScanner;
import com.densowave.scannersdk.Listener.BarcodeDataDelegate;
import com.densowave.scannersdk.Listener.RFIDDataDelegate;
import com.densowave.scannersdk.RFID.RFIDData;
import com.densowave.scannersdk.RFID.RFIDDataReceivedEvent;

import com.example.inventory_system_ht.Adapter.StockTakingItemAdapter;
import com.example.inventory_system_ht.Helper.ApiClient;
import com.example.inventory_system_ht.Helper.ApiService;
import com.example.inventory_system_ht.Helper.AppDatabase;
import com.example.inventory_system_ht.Helper.PrefManager;
import com.example.inventory_system_ht.Helper.RfidBulkHelper;
import com.example.inventory_system_ht.Helper.ScannerManager;
import com.example.inventory_system_ht.Models.GeneralResponse;
import com.example.inventory_system_ht.Models.StockTakingModels;
import com.example.inventory_system_ht.R;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@SuppressLint("UseSwitchCompatOrMaterialCode")
public class StockTakingActivity extends BaseScannerActivity
        implements BarcodeDataDelegate, RFIDDataDelegate {

    // ─── Fields ───────────────────────────────────────────────────────────────
    private Switch switchRfid;
    private CardView btnSave, btnRefresh;
    private EditText resultScan;
    private RecyclerView rvTags;
    private TextView tvRemark, tvLocation, tvQty, tvSyncStatus;
    private Spinner spinnerPower;
    private FloatingActionButton fabScanCamera;

    private ApiService api;
    private AppDatabase db;
    private String token;
    private String sttId  = "";
    private String remark = "";

    private final List<StockTakingModels.SessionItem> sessionItems = new ArrayList<>();
    private final Map<String, Integer> epcIndexMap = new HashMap<>();
    private final List<String> powerList = Arrays.asList(
            "5 dBm", "10 dBm", "15 dBm", "18 dBm", "21 dBm", "24 dBm", "27 dBm", "30 dBm"
    );
    private boolean hasChanges = false;
    private StockTakingItemAdapter adapter;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // ─── Abstract Override ────────────────────────────────────────────────────
    @Override
    protected CommScanner getScannerInstance() {
        return ScannerManager.getInstance().getScanner();
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stock_taking_adjustment);

        sttId  = getIntent().getStringExtra("sttId");
        remark = getIntent().getStringExtra("remark");

        if (sttId == null || sttId.isEmpty()) {
            showError("Session ID missing");
            finish();
            return;
        }
        if (remark == null) remark = "";

        token = "Bearer " + new PrefManager(this).getToken();
        api = ApiClient.getClient(this).create(ApiService.class);
        db = AppDatabase.getDatabase(this);

        bindViews();
        setupPowerSpinner();
        setupAdapter();
        setupListeners();

        if (isNetworkConnected()) loadSessionTagsFromServer();
        else { loadSessionTagsFromCache(); showWarning("Offline, using cache"); }
    }

    @Override
    protected void onResume() {
        super.onResume();
        CommScanner scanner = getScannerInstance();
        updateReaderBattery(findViewById(R.id.ivReaderBattery), switchRfid.isChecked());
        if (!switchRfid.isChecked() && scanner != null) RfidBulkHelper.openBarcode(scanner, this);

        syncPendingQueue();
        checkSessionStatus();

        int bat = getHTBatteryLevel();
        if (bat <= 15) {
            showWarning("Battery low: " + bat + "%");
            playScanFeedback(2);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        CommScanner scanner = getScannerInstance();
        RfidBulkHelper.closeInventory(scanner);
        RfidBulkHelper.closeBarcode(scanner);
    }

    // ─── Init ─────────────────────────────────────────────────────────────────
    private void bindViews() {
        switchRfid = findViewById(R.id.switchRfid);
        btnSave = findViewById(R.id.btnSave);
        btnRefresh = findViewById(R.id.btnRefresh);
        resultScan = findViewById(R.id.resultScan);
        rvTags = findViewById(R.id.rvTags);
        tvRemark = findViewById(R.id.tvRemark);
        tvLocation = findViewById(R.id.tvLocation);
        tvQty = findViewById(R.id.tvQty);
        tvSyncStatus = findViewById(R.id.tvSyncStatus);
        spinnerPower = findViewById(R.id.spinnerPower);
        fabScanCamera = findViewById(R.id.fabScanCamera);

        spinnerPower.setVisibility(View.GONE);
        switchRfid.setChecked(false);
        tvRemark.setText("Note: " + (remark.isEmpty() ? "-" : remark));
    }

    // Setup spinner power
    private void setupPowerSpinner() {
        ArrayAdapter<String> powerAdapter = new ArrayAdapter<String>(this, R.layout.item_spinner_selected, R.id.tvSpinnerSelected, powerList) {
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
        spinnerPower.setAdapter(powerAdapter);
        spinnerPower.setSelection(6);

        spinnerPower.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!switchRfid.isChecked()) return;
                CommScanner scanner = getScannerInstance();
                if (scanner != null) {
                    int power = parsePower(powerList.get(position), 27);
                    RfidBulkHelper.closeInventory(scanner);
                    RfidBulkHelper.openInventory(scanner, StockTakingActivity.this, power);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void setupAdapter() {
        adapter = new StockTakingItemAdapter(sessionItems);
        adapter.setOnItemClickListener(this::showAdjustmentDialog);
        rvTags.setLayoutManager(new LinearLayoutManager(this));
        rvTags.setAdapter(adapter);
        rvTags.setItemAnimator(null);
    }

    private void setupListeners() {
        findViewById(R.id.btnBack).setOnClickListener(v -> handleBackPressed());

        getOnBackPressedDispatcher().addCallback(this,
                new androidx.activity.OnBackPressedCallback(true) {
                    @Override public void handleOnBackPressed() {
                        StockTakingActivity.this.handleBackPressed();
                    }
                });

        switchRfid.setOnCheckedChangeListener((btn, isChecked) -> {
            CommScanner scanner = getScannerInstance();
            updateReaderBattery(findViewById(R.id.ivReaderBattery), isChecked);

            if (isChecked) {
                if (scanner == null) {
                    showError("RFID not connected");
                    switchRfid.setChecked(false);
                    updateReaderBattery(findViewById(R.id.ivReaderBattery), false);
                    return;
                }
                RfidBulkHelper.closeBarcode(scanner);
                int power = parsePower(
                        spinnerPower.getSelectedItem() != null
                                ? spinnerPower.getSelectedItem().toString() : "27 dBm", 27);
                boolean ok = RfidBulkHelper.openInventory(scanner, this, power);
                if (ok) {
                    resultScan.setEnabled(false);
                    spinnerPower.setVisibility(View.VISIBLE);
                } else {
                    showError("Failed to start RFID");
                    switchRfid.setChecked(false);
                }
            } else {
                RfidBulkHelper.closeInventory(scanner);
                if (scanner != null) RfidBulkHelper.openBarcode(scanner, this);
                resultScan.setEnabled(true);
                resultScan.requestFocus();
                spinnerPower.setVisibility(View.GONE);
            }
        });

        btnSave.setOnClickListener(v -> {
            if (sttId.isEmpty()) { showWarning("No active session"); return; }
            showCustomConfirmDialog(
                    "Submit scan results? Scanned: " + countScanned(),
                    this::handleSave);
        });

        if (btnRefresh != null) {
            btnRefresh.setOnClickListener(v -> {
                if (!isNetworkConnected()) {
                    showWarning("No internet, showing cache");
                    loadSessionTagsFromCache();
                    return;
                }
                showLoading();
                new Thread(() -> {
                    db.appDao().clearSessionItemsBySttId(sttId);
                    handler.post(this::loadSessionTagsFromServer);
                }).start();
            });
        }

        resultScan.setOnEditorActionListener((v, actionId, event) -> {
            String data = resultScan.getText().toString().trim();
            if (!data.isEmpty()) {
                processScan(data);
                resultScan.setText("");
            }
            return true;
        });
        fabScanCamera.setOnClickListener(v -> {
            if (switchRfid.isChecked()) switchRfid.setChecked(false);
            cameraScanLauncher.launch(new Intent(this, BarcodeCameraActivity.class));
        });

    }
    private final ActivityResultLauncher<Intent> cameraScanLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                            String barcode = result.getData().getStringExtra(BarcodeCameraActivity.EXTRA_BARCODE);
                            if (barcode != null && !barcode.isEmpty()) processScan(barcode);
                        } else if (result.getResultCode() == BarcodeCameraActivity.RESULT_PERMISSION_DENIED) {
                            showError("Camera permission denied");
                        }
                    }
            );

    // ─── Data Load ────────────────────────────────────────────────────────────

    private void loadSessionTagsFromServer() {
        if (!isNetworkConnected()) {
            loadSessionTagsFromCache();
            showWarning("Offline, using cache");
            return;
        }
        showLoading();
        api.getSessionTags(token, sttId)
                .enqueue(new Callback<List<StockTakingModels.SessionItem>>() {
                    @Override
                    public void onResponse(Call<List<StockTakingModels.SessionItem>> call,
                                           Response<List<StockTakingModels.SessionItem>> response) {
                        hideLoading();
                        if (response.isSuccessful() && response.body() != null) {
                            List<StockTakingModels.SessionItem> body = response.body();
                            sessionItems.clear();
                            epcIndexMap.clear();
                            for (StockTakingModels.SessionItem item : body) {
                                item.state = "PENDING";
                                if (item.epcTag != null)
                                    epcIndexMap.put(item.epcTag.toUpperCase(), sessionItems.size());
                                sessionItems.add(item);
                            }
                            adapter.notifyDataSetChanged();
                            updateInfo();
                            updateSyncStatus();
                            saveSessionItemsToCache(body);
                        } else {
                            showError("Load failed, using cache");
                            loadSessionTagsFromCache();
                        }
                    }

                    @Override
                    public void onFailure(Call<List<StockTakingModels.SessionItem>> call, Throwable t) {
                        hideLoading();
                        showError("Load failed, using cache");
                        loadSessionTagsFromCache();
                    }
                });
    }

    private void loadSessionTagsFromCache() {
        showLoading();
        new Thread(() -> {
            List<StockTakingModels.SessionItemEntity> cached =
                    db.appDao().getSessionItemsBySttId(sttId);
            handler.post(() -> {
                hideLoading();
                TextView tvEmpty = findViewById(R.id.tvEmpty);
                if (cached.isEmpty()) {
                    if (tvEmpty != null) tvEmpty.setVisibility(View.VISIBLE);
                    showWarning("No cached data, tap Refresh");
                    return;
                }
                if (tvEmpty != null) tvEmpty.setVisibility(View.GONE);
                sessionItems.clear();
                epcIndexMap.clear();
                for (StockTakingModels.SessionItemEntity e : cached) {
                    StockTakingModels.SessionItem item = e.toSessionItem();
                    if (item.epcTag != null)
                        epcIndexMap.put(item.epcTag.toUpperCase(), sessionItems.size());
                    sessionItems.add(item);
                }
                adapter.notifyDataSetChanged();
                updateInfo();
                updateSyncStatus();
            });
        }).start();
    }

    private void saveSessionItemsToCache(List<StockTakingModels.SessionItem> items) {
        new Thread(() -> {
            db.appDao().clearSessionItemsBySttId(sttId);
            List<StockTakingModels.SessionItemEntity> entities = new ArrayList<>();
            for (StockTakingModels.SessionItem item : items)
                entities.add(StockTakingModels.SessionItemEntity.from(sttId, item));
            if (!entities.isEmpty()) db.appDao().insertSessionItems(entities);
        }).start();
    }

    // ─── Scan Processing ──────────────────────────────────────────────────────

    private void processScan(String epcOrBarcode) {
        if (sttId.isEmpty()) { playScanFeedback(2); return; }

        Integer idx = epcIndexMap.get(epcOrBarcode.toUpperCase());
        if (idx == null) {
            for (int i = 0; i < sessionItems.size(); i++) {
                if (epcOrBarcode.equalsIgnoreCase(sessionItems.get(i).tagId)) {
                    idx = i; break;
                }
            }
        }

        if (idx == null) {
            playScanFeedback(2);
            showWarning("Tag not found: " + epcOrBarcode);
            return;
        }

        StockTakingModels.SessionItem item = sessionItems.get(idx);
        if (!"PENDING".equals(item.state)) {
            if (!switchRfid.isChecked()) showWarning("Already scanned");
            return;
        }

        item.state = "FOUND";
        hasChanges = true;
        adapter.notifyItemChanged(idx);
        rvTags.scrollToPosition(idx);
        updateInfo();
        playScanFeedback(0);

        saveToQueue(item.epcTag, "FOUND", null, null);
        if (isNetworkConnected()) syncSingleScan(item.epcTag);
        else updateSyncStatus();
    }

    private void saveToQueue(String epc, String action, String itemId, String remarkText) {
        new Thread(() -> {
            StockTakingModels.ScanQueueEntity e = new StockTakingModels.ScanQueueEntity();
            e.sttId = sttId;
            e.epcTag = epc;
            e.action = action;
            e.itemId = itemId;
            e.remark = remarkText;
            e.isSynced = false;
            e.createdAt = System.currentTimeMillis();
            db.appDao().insertScanQueue(e);
            handler.post(this::updateSyncStatus);
        }).start();
    }

    // ─── Sync ─────────────────────────────────────────────────────────────────

    private void syncSingleScan(String epc) {
        api.scanStockTaking(token, new StockTakingModels.ScanReq(sttId, epc))
                .enqueue(new Callback<GeneralResponse>() {
                    @Override
                    public void onResponse(Call<GeneralResponse> call, Response<GeneralResponse> res) {
                        if (res.isSuccessful()) {
                            new Thread(() -> {
                                db.appDao().markSyncedByEpc(sttId, epc);
                                handler.post(StockTakingActivity.this::updateSyncStatus);
                            }).start();
                        }
                    }
                    @Override public void onFailure(Call<GeneralResponse> call, Throwable t) {}
                });
    }

    private void syncPendingQueue() {
        if (!isNetworkConnected()) return;
        new Thread(() -> {
            List<StockTakingModels.ScanQueueEntity> pending = db.appDao().getUnsyncedBySttId(sttId);
            if (pending.isEmpty()) return;

            List<String> foundEpcs = new ArrayList<>();
            for (StockTakingModels.ScanQueueEntity q : pending)
                if ("FOUND".equals(q.action)) foundEpcs.add(q.epcTag);

            if (!foundEpcs.isEmpty()) {
                try {
                    Response<GeneralResponse> res = api.bulkScanStockTaking(token,
                            new StockTakingModels.BulkScanReq(sttId, foundEpcs)).execute();
                    if (res.isSuccessful()) {
                        db.appDao().markBulkSynced(sttId, foundEpcs);
                        handler.post(() -> {
                            updateSyncStatus();
                        });
                    }
                } catch (Exception ignored) {}
            }

            for (StockTakingModels.ScanQueueEntity q : pending) {
                if ("FOUND".equals(q.action)) continue;
                try {
                    if ("REMOVE".equals(q.action))
                        api.removeStockTaking(token,
                                new StockTakingModels.RemoveReq(q.sttId, q.epcTag)).execute();
                    else if ("MANUAL_ADD".equals(q.action))
                        api.manualAddStockTaking(token,
                                new StockTakingModels.ManualAddReq(q.sttId, q.itemId, q.remark)).execute();
                    db.appDao().markSyncedById(q.id);
                } catch (Exception ignored) {}
            }
            handler.post(this::updateSyncStatus);
        }).start();
    }

    private void syncPendingQueueSync() {
        List<StockTakingModels.ScanQueueEntity> pending = db.appDao().getUnsyncedBySttId(sttId);
        if (pending.isEmpty()) return;

        List<String> foundEpcs = new ArrayList<>();
        for (StockTakingModels.ScanQueueEntity q : pending)
            if ("FOUND".equals(q.action)) foundEpcs.add(q.epcTag);

        if (!foundEpcs.isEmpty()) {
            try {
                Response<GeneralResponse> res = api.bulkScanStockTaking(token,
                        new StockTakingModels.BulkScanReq(sttId, foundEpcs)).execute();
                if (res.isSuccessful()) db.appDao().markBulkSynced(sttId, foundEpcs);
            } catch (Exception ignored) {}
        }

        for (StockTakingModels.ScanQueueEntity q : pending) {
            if ("FOUND".equals(q.action)) continue;
            try {
                if ("REMOVE".equals(q.action))
                    api.removeStockTaking(token,
                            new StockTakingModels.RemoveReq(q.sttId, q.epcTag)).execute();
                else if ("MANUAL_ADD".equals(q.action))
                    api.manualAddStockTaking(token,
                            new StockTakingModels.ManualAddReq(q.sttId, q.itemId, q.remark)).execute();
                db.appDao().markSyncedById(q.id);
            } catch (Exception ignored) {}
        }
    }

    // ─── Submit ───────────────────────────────────────────────────────────────

    private void handleSave() {
        if (!isNetworkConnected()) {
            showWarning("Saved offline");
            return;
        }
        showLoading();
        new Thread(() -> {
            syncPendingQueueSync();
            handler.post(this::sendApplyAdjustment);
        }).start();
    }

    private void sendApplyAdjustment() {
        api.applyAdjustment(token, new StockTakingModels.FinalizeReq(sttId))
                .enqueue(new Callback<GeneralResponse>() {
                    @Override
                    public void onResponse(Call<GeneralResponse> call, Response<GeneralResponse> response) {
                        hideLoading();
                        if (response.isSuccessful()) {
                            new Thread(() -> {
                                db.appDao().clearSyncedBySttId(sttId);
                                db.appDao().clearSessionItemsBySttId(sttId);
                            }).start();
                            showSuccess("Data submitted");
                            playScanFeedback(0);
                            hasChanges = false;
                            finish();
                        } else {
                            handleApiError(response.code());
                            playScanFeedback(2);
                        }
                    }
                    @Override
                    public void onFailure(Call<GeneralResponse> call, Throwable t) {
                        hideLoading(); handleFailure(t); playScanFeedback(2);
                    }
                });
    }

    // ─── Session Check ────────────────────────────────────────────────────────

    private void checkSessionStatus() {
        if (!isNetworkConnected()) return;
        api.getActiveStockTaking(token).enqueue(new Callback<StockTakingModels.ActiveRes>() {
            @Override
            public void onResponse(Call<StockTakingModels.ActiveRes> call,
                                   Response<StockTakingModels.ActiveRes> response) {
                boolean ended = !response.isSuccessful() || response.body() == null
                        || !sttId.equals(response.body().sttId);
                if (ended) showSessionEndedDialog();
            }
            @Override public void onFailure(Call<StockTakingModels.ActiveRes> call, Throwable t) {}
        });
    }

    // ─── UI Update ────────────────────────────────────────────────────────────

    private void updateInfo() {
        int scanned = countScanned();
        tvQty.setText("Qty: " + scanned + "/" + sessionItems.size());

        List<String> locations = new ArrayList<>();
        for (StockTakingModels.SessionItem item : sessionItems) {
            if (item.location != null && !item.location.isEmpty() && !locations.contains(item.location))
                locations.add(item.location);
        }
        tvLocation.setText("Location: " + (locations.isEmpty() ? "-" : String.join(", ", locations)));
    }

    private void updateSyncStatus() {
        new Thread(() -> {
            int unsynced = db.appDao().countUnsyncedBySttId(sttId);
            handler.post(() -> {
                if (tvSyncStatus == null) return;
                if (unsynced == 0) {
                    tvSyncStatus.setText("✅ All synced");
                    tvSyncStatus.setTextColor(Color.parseColor("#01C470"));
                } else {
                    tvSyncStatus.setText("⚠️ " + unsynced + " pending sync");
                    tvSyncStatus.setTextColor(Color.parseColor("#FFA000"));
                }
            });
        }).start();
    }

    private int countScanned() {
        int count = 0;
        for (StockTakingModels.SessionItem item : sessionItems)
            if ("FOUND".equals(item.state) || "MANUAL_ADD".equals(item.state)) count++;
        return count;
    }

    // ─── Dialog ───────────────────────────────────────────────────────────────

    private void showAdjustmentDialog(StockTakingModels.SessionItem item, int position) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_adj);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.findViewById(R.id.btnFaq).setOnClickListener(v -> showFaqDialog());
        dialog.findViewById(R.id.btnRemove).setOnClickListener(v -> {
            dialog.dismiss();
            showRemoveConfirmDialog(item, position);
        });
        dialog.findViewById(R.id.btnAddManual).setOnClickListener(v -> {
            dialog.dismiss();
            showManualAddDialog(item, position);
        });
        dialog.show();
    }

    private void showRemoveConfirmDialog(StockTakingModels.SessionItem item, int position) {
        showCustomConfirmDialog("Remove this item? Qty will decrease.", () -> {
            if (item.tagId != null && !item.tagId.isEmpty()) {
                saveToQueue(item.tagId, "REMOVE", null, null);
                if (isNetworkConnected()) {
                    api.removeStockTaking(token,
                                    new StockTakingModels.RemoveReq(sttId, item.tagId))
                            .enqueue(new Callback<GeneralResponse>() {
                                @Override public void onResponse(Call<GeneralResponse> c, Response<GeneralResponse> r) {}
                                @Override public void onFailure(Call<GeneralResponse> c, Throwable t) {}
                            });
                }
            }
            sessionItems.remove(position);
            epcIndexMap.remove(item.epcTag != null ? item.epcTag.toUpperCase() : "");
            adapter.notifyItemRemoved(position);
            adapter.notifyItemRangeChanged(position, sessionItems.size());
            hasChanges = true;
            updateInfo();
        });
    }

    private void showManualAddDialog(StockTakingModels.SessionItem item, int position) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_manual_add);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(
                    (int)(getResources().getDisplayMetrics().widthPixels * 0.90),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        ViewGroup dialogRoot = (ViewGroup) dialog.getWindow().getDecorView();

        EditText etItemId = dialog.findViewById(R.id.etManualItemId);
        EditText etRemark = dialog.findViewById(R.id.etManualRemark);
        etItemId.setText(item.itemId != null ? item.itemId : "");
        etItemId.setEnabled(false);

        dialog.findViewById(R.id.btnCancelManual).setOnClickListener(v -> dialog.dismiss());
        dialog.findViewById(R.id.btnSaveManual).setOnClickListener(v -> {
            String remarkText = etRemark.getText().toString().trim();
            if (remarkText.isEmpty()) {
                showSagaFeedback(dialogRoot, "Remark cannot be empty", 1);
                return;
            }

            saveToQueue(item.epcTag, "MANUAL_ADD", item.itemId, remarkText);
            if (isNetworkConnected()) {
                api.manualAddStockTaking(token,
                                new StockTakingModels.ManualAddReq(sttId, item.itemId, remarkText))
                        .enqueue(new Callback<GeneralResponse>() {
                            @Override
                            public void onResponse(Call<GeneralResponse> c, Response<GeneralResponse> r) {
                                if (r.isSuccessful()) {
                                    new Thread(() -> {
                                        List<StockTakingModels.ScanQueueEntity> pending =
                                                db.appDao().getUnsyncedBySttId(sttId);
                                        for (StockTakingModels.ScanQueueEntity q : pending) {
                                            if ("MANUAL_ADD".equals(q.action)
                                                    && item.itemId != null
                                                    && item.itemId.equals(q.itemId)) {
                                                db.appDao().markSyncedById(q.id);
                                                break;
                                            }
                                        }
                                        handler.post(StockTakingActivity.this::updateSyncStatus);
                                    }).start();
                                }
                            }
                            @Override public void onFailure(Call<GeneralResponse> c, Throwable t) {}
                        });
            }
            item.state = "MANUAL_ADD";
            item.manualRemark = remarkText;
            hasChanges = true;
            adapter.notifyItemChanged(position);
            updateInfo();
            playScanFeedback(0);
            dialog.dismiss();
            showSuccess("Manual add saved");
        });
        dialog.show();
    }

    private void showFaqDialog() {
        Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setContentView(R.layout.dialog_faq);
        if (d.getWindow() != null) {
            d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            d.getWindow().setLayout(
                    (int)(getResources().getDisplayMetrics().widthPixels * 0.90),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        d.show();
    }

    private void showCustomConfirmDialog(String message, Runnable onYes) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_confirm);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(
                    (int)(getResources().getDisplayMetrics().widthPixels * 0.85),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        ((TextView) dialog.findViewById(R.id.tvConfirmMessage)).setText(message);
        dialog.findViewById(R.id.btnConfirmNo).setOnClickListener(v -> dialog.dismiss());
        dialog.findViewById(R.id.btnConfirmYes).setOnClickListener(v -> {
            dialog.dismiss(); onYes.run();
        });
        dialog.show();
    }

    private void showSessionEndedDialog() {
        if (isFinishing() || isDestroyed()) return;
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Session Ended")
                .setMessage("This session has been finalized by admin.")
                .setCancelable(false)
                .setPositiveButton("OK", (d, w) -> {
                    new Thread(() -> {
                        db.appDao().clearSyncedBySttId(sttId);
                        db.appDao().clearSessionItemsBySttId(sttId);
                    }).start();
                    finish();
                }).show();
    }

    // ─── Back Handler ─────────────────────────────────────────────────────────

    private void handleBackPressed() {
        if (!hasChanges) { finish(); return; }
        showCustomConfirmDialog(
                "Exit? Scanned data saved locally.",
                () -> {
                    sessionItems.clear();
                    epcIndexMap.clear();
                    hasChanges = false;
                    finish();
                });
    }

    // ─── Scanner Callbacks ────────────────────────────────────────────────────
    @Override
    public void onRFIDDataReceived(CommScanner scanner, RFIDDataReceivedEvent event) {
        for (RFIDData data : event.getRFIDData()) {
            String epc = RfidBulkHelper.bytesToHex(data.getUII());
            if (!epc.isEmpty()) handler.post(() -> processScan(epc));
        }
    }

    @Override
    public void onBarcodeDataReceived(CommScanner scanner, BarcodeDataReceivedEvent event) {
        List<BarcodeData> dataList = event.getBarcodeData();
        if (!dataList.isEmpty()) {
            String barcode = new String(dataList.get(0).getData());
            handler.post(() -> processScan(barcode));
        }
    }
}