package com.example.inventory_system_ht.activity;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.cardview.widget.CardView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.densowave.scannersdk.Barcode.BarcodeData;
import com.densowave.scannersdk.Barcode.BarcodeDataReceivedEvent;
import com.densowave.scannersdk.Common.CommScanner;
import com.densowave.scannersdk.Listener.BarcodeDataDelegate;
import com.densowave.scannersdk.Listener.RFIDDataDelegate;
import com.densowave.scannersdk.RFID.RFIDData;
import com.densowave.scannersdk.RFID.RFIDDataReceivedEvent;

import com.example.inventory_system_ht.activity.base.ScannerActivity;
import com.example.inventory_system_ht.adapter.StockTakingItemAdapter;
import com.example.inventory_system_ht.database.AppDatabase;
import com.example.inventory_system_ht.entity.ScanQueueEntity;
import com.example.inventory_system_ht.entity.SessionItemEntity;
import com.example.inventory_system_ht.model.GeneralResponse;
import com.example.inventory_system_ht.model.StockTakingResponses;
import com.example.inventory_system_ht.network.ApiClient;
import com.example.inventory_system_ht.network.ApiService;
import com.example.inventory_system_ht.util.LogManager;
import com.example.inventory_system_ht.util.PrefManager;
import com.example.inventory_system_ht.util.RfidBulkHelper;
import com.example.inventory_system_ht.util.RfidSettingsManager;
import com.example.inventory_system_ht.util.ScannerManager;
import com.example.inventory_system_ht.R;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@SuppressLint("UseSwitchCompatOrMaterialCode")
public class StockTakingActivity extends ScannerActivity
        implements BarcodeDataDelegate, RFIDDataDelegate {
    private Switch switchRfid;
    private CardView btnSave, btnRefresh;
    private EditText resultScan;
    private RecyclerView rvTags;
    private TextView tvRemark, tvLocation, tvQty;
    private View tvEmpty;
    private Spinner spinnerPower;
    private FloatingActionButton fabScanCamera;
    private ApiService api;
    private AppDatabase db;
    private String token;
    private String sttId = "";
    private String remark = "";
    private final List<StockTakingResponses.SessionItem> sessionItems = new ArrayList<>();
    private final Set<String> scannedEpcSet = new HashSet<>();
    private final Map<String, Integer> epcIndexMap = new HashMap<>();
    private final Map<String, Integer> tagIdIndexMap = new HashMap<>();
    private final List<String> powerList = Arrays.asList(
            "5 dBm", "10 dBm", "15 dBm", "18 dBm", "21 dBm", "24 dBm", "27 dBm", "30 dBm"
    );
    private boolean hasChanges = false;
    private boolean isManualAddDialogOpen = false;
    private java.util.function.Consumer<String> activeDialogScanHandler = null;
    private StockTakingItemAdapter adapter;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int scannedCount = 0;
    private String cachedLocationsString = "-";

    @Override
    protected CommScanner getScannerInstance() {
        return ScannerManager.getInstance().getScanner();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stock_taking_adjustment);
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

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.bottomButtonsContainer), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            int dp10 = (int)(10 * getResources().getDisplayMetrics().density);
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), bars.bottom + dp10);
            return insets;
        });

        sttId = getIntent().getStringExtra("sttId");
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

        FloatingActionButton fabLog = findViewById(R.id.fabLog);
        if (fabLog != null) {
            fabLog.setOnClickListener(v -> {
                Intent intent = new Intent(this, LogActivity.class);
                intent.putExtra(LogActivity.EXTRA_MENU, "Stock Taking");
                startActivity(intent);
            });
        }
        LogManager.get(this).log(LogManager.INFO, LogManager.ACTION_OPEN, "Stock Taking", "", "Opened Stock Taking", new PrefManager(this).getUserId());
    }

    @Override
    protected void onResume() {
        super.onResume();
        CommScanner scanner = getScannerInstance();
        if (!switchRfid.isChecked() && ScannerManager.getInstance().isClaimed() && scanner != null) {
            CommScanner s = scanner;
            new Thread(() -> RfidBulkHelper.openBarcode(s, StockTakingActivity.this)).start();
        }
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

    private void bindViews() {
        switchRfid = findViewById(R.id.switchRfid);
        btnSave = findViewById(R.id.btnSave);
        btnRefresh = findViewById(R.id.btnReset);
        resultScan = findViewById(R.id.resultScan);
        rvTags = findViewById(R.id.rvTags);
        tvRemark = findViewById(R.id.tvRemark);
        tvLocation = findViewById(R.id.tvLocation);
        tvQty = findViewById(R.id.tvQty);
        tvEmpty = findViewById(R.id.tvEmpty);
        spinnerPower = findViewById(R.id.spinnerPower);
        fabScanCamera = findViewById(R.id.fabScanCamera);

        rvTags.setVisibility(View.VISIBLE);
        spinnerPower.setVisibility(View.GONE);
        switchRfid.setChecked(false);
        tvRemark.setText("Note: " + (remark.isEmpty() ? "-" : remark));
    }

    private void setupPowerSpinner() {
        ArrayAdapter<String> powerAdapter = new ArrayAdapter<String>(this, R.layout.item_spinner_selected, R.id.tvSpinnerSelected, powerList) {
            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View view = LayoutInflater.from(getContext()).inflate(R.layout.item_dropdown_loc, parent, false);
                TextView tv = view.findViewById(R.id.tvDropdownItem);
                ImageView icon = view.findViewById(R.id.ivDropdownIcon);
                if (tv != null) tv.setText(getItem(position));
                if (icon != null) icon.setVisibility(View.GONE);
                return view;
            }
        };
        spinnerPower.setAdapter(powerAdapter);
        spinnerPower.setSelection(indexOfPower(new RfidSettingsManager(this).getPower()));

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
                if (scanner != null) {
                    CommScanner s = scanner;
                    new Thread(() -> RfidBulkHelper.openBarcode(s, StockTakingActivity.this)).start();
                }
                resultScan.setEnabled(true);
                resultScan.requestFocus();
                spinnerPower.setVisibility(View.GONE);
            }
        });

        btnSave.setOnClickListener(v -> {
            if (sttId.isEmpty()) { showWarning("No active session"); return; }
            showCustomConfirmDialog(
                    "Submit scan results? Scanned: " + scannedCount,
                    this::handleSave);
        });

        if (btnRefresh != null) {
            btnRefresh.setOnClickListener(v -> {
                showCustomConfirmDialog("Reset all scan data?", () -> {
                    scannedEpcSet.clear();
                    scannedCount = 0;
                    hasChanges = false;
                    updateInfo();

                    if (!isNetworkConnected()) {
                        new Thread(() -> db.appDao().clearScanQueueBySttId(sttId)).start();
                        loadSessionTagsFromCache();
                        showWarning("Offline");
                        return;
                    }

                    showLoading();
                    new Thread(() -> {
                        db.appDao().clearScanQueueBySttId(sttId);
                        db.appDao().clearSessionItemsBySttId(sttId);
                        handler.post(this::loadSessionTagsFromServer);
                    }).start();
                });
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

    private void loadSessionTagsFromServer() {
        showLoading();
        String userId = new PrefManager(this).getUserId();
        String reqJson = "{\"sttId\":\"" + sttId + "\"}";
        api.getSessionTags(token, sttId).enqueue(new Callback<List<StockTakingResponses.SessionItem>>() {
            @Override
            public void onResponse(Call<List<StockTakingResponses.SessionItem>> call,
                                   Response<List<StockTakingResponses.SessionItem>> res) {
                hideLoading();
                String resJson = "{\"http_code\":" + res.code()
                        + ",\"count\":" + (res.body() != null ? res.body().size() : 0) + "}";
                if (!res.isSuccessful() || res.body() == null) {
                    LogManager.get(StockTakingActivity.this).log(LogManager.WARNING, LogManager.ACTION_READ,
                            "Stock Taking", sttId, "Load session tags failed: HTTP " + res.code(),
                            userId, reqJson, resJson);
                    loadSessionTagsFromCache();
                    return;
                }

                List<StockTakingResponses.SessionItem> fromServer = res.body();
                LogManager.get(StockTakingActivity.this).log(LogManager.INFO, LogManager.ACTION_READ,
                        "Stock Taking", sttId, "Load session tags success: " + fromServer.size(),
                        userId, reqJson, resJson);

                sessionItems.clear();
                epcIndexMap.clear();
                tagIdIndexMap.clear();
                scannedEpcSet.clear();
                scannedCount = 0;

                for (StockTakingResponses.SessionItem item : fromServer) {
                    if (item == null) continue;

                    String backendAction = item.action != null ? item.action.toUpperCase() : "SYSTEM";

                    if ("REMOVE".equals(backendAction)) {
                        continue;
                    }

                    if ("FOUND".equals(backendAction) || "ADD_MANUAL".equals(backendAction) || "MANUAL_ADD".equals(backendAction)) {
                        scannedEpcSet.add(item.epcTag != null ? item.epcTag.toUpperCase() : "");
                        continue;
                    }

                    item.state = "PENDING";

                    int pos = sessionItems.size();
                    if (item.epcTag != null && !item.epcTag.isEmpty())
                        epcIndexMap.put(item.epcTag.toUpperCase(), pos);
                    if (item.tagId != null && !item.tagId.isEmpty())
                        tagIdIndexMap.put(item.tagId.toUpperCase(), pos);

                    sessionItems.add(item);
                }
                rebuildLocationsCache();

                if (tvEmpty != null) {
                    tvEmpty.setVisibility(sessionItems.isEmpty() ? View.VISIBLE : View.GONE);
                }

                saveSessionItemsToCache(new ArrayList<>(sessionItems));
                applyQueueStateToSessionItems();
                adapter.notifyDataSetChanged();
                updateInfo();
            }

            @Override
            public void onFailure(Call<List<StockTakingResponses.SessionItem>> call, Throwable t) {
                hideLoading();
                String resJson = "{\"error\":\"" + t.getMessage() + "\"}";
                LogManager.get(StockTakingActivity.this).log(LogManager.ERROR, LogManager.ACTION_READ,
                        "Stock Taking", sttId, "Load session tags error: " + t.getMessage(),
                        userId, reqJson, resJson);
                showWarning("Failed to load tags, using cache");
                loadSessionTagsFromCache();
            }
        });
    }

    private void applyQueueStateToSessionItems() {
        new Thread(() -> {
            List<ScanQueueEntity> queue = db.appDao().getUnsyncedBySttId(sttId);
            if (queue.isEmpty()) return;
            handler.post(() -> {
                for (ScanQueueEntity q : queue) {
                    if (q.epcTag == null) continue;
                    String upperEpc = q.epcTag.toUpperCase();
                    scannedEpcSet.add(upperEpc);
                }

                for (ScanQueueEntity q : queue) {
                    if (q.epcTag == null) continue;
                    Integer idx = epcIndexMap.get(q.epcTag.toUpperCase());
                    if (idx == null) idx = tagIdIndexMap.get(q.epcTag.toUpperCase());
                    if (idx == null) continue;
                    StockTakingResponses.SessionItem item = sessionItems.get(idx);
                    boolean wasScanned = "FOUND".equals(item.state) || "MANUAL_ADD".equals(item.state);
                    if ("FOUND".equals(q.action)) {
                        item.state = "FOUND";
                    } else if ("MANUAL_ADD".equals(q.action)) {
                        item.state = "MANUAL_ADD";
                        item.manualRemark = q.remark != null ? q.remark : "";
                    }
                    boolean isNowScanned = "FOUND".equals(item.state) || "MANUAL_ADD".equals(item.state);
                    if (!wasScanned && isNowScanned) scannedCount++;
                }
                adapter.notifyDataSetChanged();
                updateInfo();
            });
        }).start();
    }

    private void loadSessionTagsFromCache() {
        showLoading();
        new Thread(() -> {
            List<SessionItemEntity> cached = db.appDao().getSessionItemsBySttId(sttId);
            handler.post(() -> {
                hideLoading();
                if (cached.isEmpty()) {
                    if (tvEmpty != null) tvEmpty.setVisibility(View.VISIBLE);
                    showWarning("No cached data, tap Refresh");
                    return;
                }
                if (tvEmpty != null) tvEmpty.setVisibility(View.GONE);
                sessionItems.clear();
                epcIndexMap.clear();
                tagIdIndexMap.clear();
                scannedEpcSet.clear();
                scannedCount = 0;
                for (SessionItemEntity e : cached) {
                    StockTakingResponses.SessionItem item = e.toSessionItem();
                    int pos = sessionItems.size();
                    if (item.epcTag != null)
                        epcIndexMap.put(item.epcTag.toUpperCase(), pos);
                    if (item.tagId != null && !item.tagId.isEmpty())
                        tagIdIndexMap.put(item.tagId.toUpperCase(), pos);
                    sessionItems.add(item);
                }
                rebuildLocationsCache();
                adapter.notifyDataSetChanged();
                applyQueueStateToSessionItems();
                updateInfo();
            });
        }).start();
    }

    private void saveSessionItemsToCache(List<StockTakingResponses.SessionItem> items) {
        new Thread(() -> {
            db.appDao().clearSessionItemsBySttId(sttId);
            List<SessionItemEntity> entities = new ArrayList<>();
            for (StockTakingResponses.SessionItem item : items)
                entities.add(SessionItemEntity.from(sttId, item));
            if (!entities.isEmpty()) db.appDao().insertSessionItems(entities);
        }).start();
    }

    private void processScan(String epcOrBarcode) {
        if (sttId.isEmpty()) {
            playScanFeedback(2);
            return;
        }

        String key = epcOrBarcode.toUpperCase();

        Integer idx = epcIndexMap.get(key);
        if (idx == null) idx = tagIdIndexMap.get(key);

        if (idx == null) {
            LogManager.get(this).log(LogManager.WARNING, LogManager.ACTION_SCAN,
                    "Stock Taking", epcOrBarcode,
                    "Tag not in session: " + epcOrBarcode,
                    new PrefManager(this).getUserId());
            return;
        }

        StockTakingResponses.SessionItem item = sessionItems.get(idx);

        if (!"PENDING".equals(item.state)) {
            LogManager.get(this).log(LogManager.WARNING, LogManager.ACTION_SCAN,
                    "Stock Taking", epcOrBarcode,
                    "Duplicate scan: " + epcOrBarcode,
                    new PrefManager(this).getUserId());
            return;
        }

        scannedEpcSet.add(key);
        item.state = "FOUND";
        scannedCount++;
        hasChanges = true;
        adapter.notifyItemChanged(idx);
        rvTags.scrollToPosition(idx);
        updateInfo();
        playScanFeedback(0);

        LogManager.get(this).log(LogManager.INFO, LogManager.ACTION_SCAN,
                "Stock Taking", epcOrBarcode,
                "Scanned: " + epcOrBarcode,
                new PrefManager(this).getUserId());

        saveToQueue(item.epcTag, "FOUND", null, null, null);
    }

    private void rebuildLocationsCache() {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (StockTakingResponses.SessionItem item : sessionItems) {
            if (item.location != null && !item.location.isEmpty())
                seen.add(item.location);
        }
        cachedLocationsString = seen.isEmpty() ? "-" : (String) TextUtils.join(", ", seen);
    }


    private void saveToQueue(String epc, String action, String itemId, String newTagId, String remarkText) {
        new Thread(() -> {
            ScanQueueEntity e = new ScanQueueEntity();
            e.sttId = sttId;
            e.epcTag = epc;
            e.action = action;
            e.itemId = itemId;
            e.newTagId = newTagId;
            e.remark = remarkText;
            e.isSynced = false;
            e.createdAt = System.currentTimeMillis();
            db.appDao().insertScanQueue(e);
        }).start();
    }

    private void handleSave() {
        if (!isNetworkConnected()) {
            showWarning("No internet connection");
            return;
        }
        showLoading();
        String userId = new PrefManager(this).getUserId();
        new Thread(() -> {
            List<ScanQueueEntity> queue = db.appDao().getUnsyncedBySttId(sttId);

            List<StockTakingResponses.OperatorSubmitItem> items = new ArrayList<>();
            for (ScanQueueEntity q : queue) {
                StockTakingResponses.OperatorSubmitItem submitItem = new StockTakingResponses.OperatorSubmitItem();
                submitItem.action = q.action;
                if ("FOUND".equals(q.action)) {
                    submitItem.epc = q.epcTag;
                } else if ("REMOVE".equals(q.action)) {
                    submitItem.tagId = q.epcTag;
                } else if ("MANUAL_ADD".equals(q.action)) {
                    submitItem.itemId = q.itemId;
                    submitItem.newTagId = q.newTagId;
                    submitItem.remark = q.remark;
                }
                items.add(submitItem);
            }

            if (items.isEmpty()) {
                handler.post(() -> { hideLoading(); showWarning("No scan data to submit"); });
                return;
            }

            StockTakingResponses.OperatorSubmitReq req = new StockTakingResponses.OperatorSubmitReq(sttId, items);
            String reqJson = "{\"sttId\":\"" + sttId + "\",\"count\":" + items.size() + "}";

            ApiService longApi = ApiClient.getClientLongTimeout(StockTakingActivity.this)
                    .create(ApiService.class);

            handler.post(() -> longApi.operatorSubmit(token, req).enqueue(new Callback<GeneralResponse>() {
                @Override
                public void onResponse(Call<GeneralResponse> call, Response<GeneralResponse> response) {
                    hideLoading();
                    String resJson = "{\"http_code\":" + response.code() + ",\"success\":" + response.isSuccessful() + "}";
                    if (response.isSuccessful()) {
                        LogManager.get(StockTakingActivity.this).log(LogManager.INFO, LogManager.ACTION_SUBMIT,
                                "Stock Taking", sttId, "Operator submit success: " + sttId, userId, reqJson, resJson);
                        new Thread(() -> {
                            List<ScanQueueEntity> all = db.appDao().getUnsyncedBySttId(sttId);
                            for (ScanQueueEntity q : all) db.appDao().markSyncedById(q.id);
                            db.appDao().clearSyncedBySttId(sttId);
                            db.appDao().clearSessionItemsBySttId(sttId);
                        }).start();
                        showSuccess("Data submitted");
                        playScanFeedback(0);
                        hasChanges = false;
                        finish();
                    } else {
                        LogManager.get(StockTakingActivity.this).log(LogManager.WARNING, LogManager.ACTION_SUBMIT,
                                "Stock Taking", sttId, "Operator submit failed: HTTP " + response.code(), userId, reqJson, resJson);
                        handleApiError(response.code());
                        playScanFeedback(2);
                    }
                }

                @Override
                public void onFailure(Call<GeneralResponse> call, Throwable t) {
                    hideLoading();
                    String resJson = "{\"error\":\"" + t.getMessage() + "\"}";
                    LogManager.get(StockTakingActivity.this).log(LogManager.ERROR, LogManager.ACTION_SUBMIT,
                            "Stock Taking", sttId, "Operator submit error: " + t.getMessage(), userId, reqJson, resJson);
                    handleFailure(t);
                    playScanFeedback(2);
                }
            }));
        }).start();
    }

    private void checkSessionStatus() {
        if (!isNetworkConnected()) return;
        api.getActiveStockTaking(token).enqueue(new Callback<StockTakingResponses.ActiveRes>() {
            @Override
            public void onResponse(Call<StockTakingResponses.ActiveRes> call,
                                   Response<StockTakingResponses.ActiveRes> response) {
                boolean ended = !response.isSuccessful() || response.body() == null
                        || !sttId.equals(response.body().sttId);
                if (ended) showSessionEndedDialog();
            }
            @Override public void onFailure(Call<StockTakingResponses.ActiveRes> call, Throwable t) {}
        });
    }

    private void updateInfo() {
        tvQty.setText("Qty: " + scannedCount + "/" + sessionItems.size());
        tvLocation.setText("Location: " + cachedLocationsString);
    }

    private void showAdjustmentDialog(StockTakingResponses.SessionItem item, int position) {
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

    private void showRemoveConfirmDialog(StockTakingResponses.SessionItem item, int position) {
        showCustomConfirmDialog("Remove this item? Qty will decrease.", () -> {
            if (item.tagId != null && !item.tagId.isEmpty()) {
                saveToQueue(item.tagId, "REMOVE", null, null, null);
            }
            boolean wasScanned = "FOUND".equals(item.state) || "MANUAL_ADD".equals(item.state);
            if (wasScanned) scannedCount--;
            scannedEpcSet.remove(item.epcTag != null ? item.epcTag.toUpperCase() : "");
            sessionItems.remove(position);
            epcIndexMap.remove(item.epcTag != null ? item.epcTag.toUpperCase() : "");
            if (item.tagId != null) tagIdIndexMap.remove(item.tagId.toUpperCase());
            for (int i = position; i < sessionItems.size(); i++) {
                StockTakingResponses.SessionItem si = sessionItems.get(i);
                if (si.epcTag != null) epcIndexMap.put(si.epcTag.toUpperCase(), i);
                if (si.tagId != null) tagIdIndexMap.put(si.tagId.toUpperCase(), i);
            }
            adapter.notifyItemRemoved(position);
            adapter.notifyItemRangeChanged(position, sessionItems.size());
            hasChanges = true;
            updateInfo();
        });
    }

    private void showManualAddDialog(StockTakingResponses.SessionItem item, int position) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_manual_add);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.90),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        ViewGroup dialogRoot = (ViewGroup) dialog.getWindow().getDecorView();
        EditText etItemId = dialog.findViewById(R.id.etManualItemId);
        EditText etRemark = dialog.findViewById(R.id.etManualRemark);
        View frameTagResult = dialog.findViewById(R.id.frameTagResult);
        View layoutPlaceholder = dialog.findViewById(R.id.layoutTagPlaceholder);
        TextView tvManualTagId = frameTagResult.findViewById(R.id.tvManualTagId);
        TextView tvManualEpc = frameTagResult.findViewById(R.id.tvManualEpc);
        TextView tvManualStatus = frameTagResult.findViewById(R.id.tvManualTagStatus);
        View btnDeleteTag = frameTagResult.findViewById(R.id.btnDeleteTag);

        String itemCode = (item.itemCode != null && !item.itemCode.isEmpty()) ? item.itemCode : "";
        String itemName = (item.itemName != null && !item.itemName.isEmpty()) ? item.itemName : "";
        String displayItem = itemCode.isEmpty() && itemName.isEmpty()
                ? (item.itemId != null ? item.itemId : "")
                : (itemCode + (itemCode.isEmpty() || itemName.isEmpty() ? "" : " - ") + itemName);
        etItemId.setText(displayItem);
        etItemId.setEnabled(false);

        final StockTakingResponses.ValidateTagResult[] validatedTag = {null};

        Runnable showPlaceholder = () -> {
            frameTagResult.setVisibility(View.GONE);
            layoutPlaceholder.setVisibility(View.VISIBLE);
            validatedTag[0] = null;
        };

        btnDeleteTag.setOnClickListener(v -> {
            showPlaceholder.run();
            playScanFeedback(2);
        });

        isManualAddDialogOpen = true;

        final boolean[] isValidating = {false};

        activeDialogScanHandler = (epc) -> {
            if (validatedTag[0] != null) {
                showSagaFeedback(dialogRoot, "Delete current tag first before scanning a new one", 1);
                return;
            }

            if (isValidating[0]) return;

            if (!isNetworkConnected()) {
                showSagaFeedback(dialogRoot, "Internet connection required to validate tag", 1);
                return;
            }

            isValidating[0] = true;

            api.validateManualTag(token, epc, sttId).enqueue(new Callback<StockTakingResponses.ValidateTagResult>() {
                @Override
                public void onResponse(Call<StockTakingResponses.ValidateTagResult> call,
                                       Response<StockTakingResponses.ValidateTagResult> response) {
                    isValidating[0] = false;
                    handler.post(() -> {
                        if (response.isSuccessful() && response.body() != null) {
                            StockTakingResponses.ValidateTagResult result = response.body();
                            validatedTag[0] = result;
                            tvManualTagId.setText(result.tagId);
                            tvManualEpc.setText(result.epcTag);
                            tvManualStatus.setText(result.status);
                            layoutPlaceholder.setVisibility(View.GONE);
                            frameTagResult.setVisibility(View.VISIBLE);
                            playScanFeedback(0);
                        }
                    });
                }

                @Override
                public void onFailure(Call<StockTakingResponses.ValidateTagResult> call, Throwable t) {
                    isValidating[0] = false;
                    handler.post(() -> {
                        showSagaFeedback(dialogRoot, "Connection failed: " + t.getMessage(), 1);
                        playScanFeedback(2);
                    });
                }
            });
        };

        dialog.setOnDismissListener(d -> {
            isManualAddDialogOpen = false;
            activeDialogScanHandler = null;
        });

        dialog.findViewById(R.id.btnCancelManual).setOnClickListener(v -> dialog.dismiss());

        dialog.findViewById(R.id.btnSaveManual).setOnClickListener(v -> {
            String remarkText = etRemark.getText().toString().trim();

            if (remarkText.isEmpty()) {
                showSagaFeedback(dialogRoot, "Remark is required", 1);
                etRemark.requestFocus();
                return;
            }

            dialog.dismiss();

            String newTagId = (validatedTag[0] != null) ? validatedTag[0].tagId : null;
            saveToQueue(item.epcTag, "MANUAL_ADD", item.itemId, newTagId, remarkText);

            boolean wasScanned = "FOUND".equals(item.state) || "MANUAL_ADD".equals(item.state);
            item.state = "MANUAL_ADD";
            item.manualRemark = remarkText;
            if (!wasScanned) scannedCount++;
            hasChanges = true;
            adapter.notifyItemChanged(position);
            updateInfo();
            playScanFeedback(0);
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
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.90),
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
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.85),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        ((TextView) dialog.findViewById(R.id.tvConfirmMessage)).setText(message);
        dialog.findViewById(R.id.btnConfirmNo).setOnClickListener(v -> dialog.dismiss());
        dialog.findViewById(R.id.btnConfirmYes).setOnClickListener(v -> { dialog.dismiss(); onYes.run(); });
        dialog.show();
    }

    private void showSessionEndedDialog() {
        if (isFinishing() || isDestroyed()) return;
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_confirm);
        dialog.setCancelable(false);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.85),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        ((TextView) dialog.findViewById(R.id.tvConfirmMessage)).setText("This session has been finalized by admin.");
        Button btnNo = dialog.findViewById(R.id.btnConfirmNo);
        Button btnOk = dialog.findViewById(R.id.btnConfirmYes);
        btnNo.setVisibility(View.GONE);
        btnOk.setText("OK");
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, (int) (50 * getResources().getDisplayMetrics().density));
        params.weight = 2;
        btnOk.setLayoutParams(params);
        btnOk.setOnClickListener(v -> {
            dialog.dismiss();
            new Thread(() -> {
                db.appDao().clearSyncedBySttId(sttId);
                db.appDao().clearSessionItemsBySttId(sttId);
            }).start();
            finish();
        });
        dialog.show();
    }

    private void handleBackPressed() {
        if (!hasChanges) { finish(); return; }
        showCustomConfirmDialog(
                "Exit? Scanned data saved locally.",
                () -> {
                    sessionItems.clear();
                    epcIndexMap.clear();
                    tagIdIndexMap.clear();
                    scannedEpcSet.clear();
                    hasChanges = false;
                    finish();
                });
    }

    @Override
    public void onRFIDDataReceived(CommScanner scanner, RFIDDataReceivedEvent event) {
        List<RFIDData> dataList = event.getRFIDData();
        if (dataList == null || dataList.isEmpty()) return;
        List<String> epcs = new ArrayList<>(dataList.size());
        for (RFIDData data : dataList) {
            String epc = RfidBulkHelper.bytesToHex(data.getUII());
            if (!epc.isEmpty()) epcs.add(epc);
        }
        if (!epcs.isEmpty()) {
            handler.post(() -> {
                if (isManualAddDialogOpen && activeDialogScanHandler != null) {
                    activeDialogScanHandler.accept(epcs.get(0));
                } else {
                    for (String epc : epcs) processScan(epc);
                }
            });
        }
    }

    @Override
    public void onBarcodeDataReceived(CommScanner scanner, BarcodeDataReceivedEvent event) {
        List<BarcodeData> dataList = event.getBarcodeData();
        if (!dataList.isEmpty()) {
            String barcode = new String(dataList.get(0).getData()).trim();
            if (barcode.isEmpty()) return;
            handler.post(() -> {
                if (isManualAddDialogOpen && activeDialogScanHandler != null) {
                    activeDialogScanHandler.accept(barcode);
                } else {
                    processScan(barcode);
                }
            });
        }
    }
}