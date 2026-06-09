package com.example.inventory_system_ht.activity;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Intent;
import android.content.res.ColorStateList;
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
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
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
import com.example.inventory_system_ht.adapter.ScannedTagAdapter;
import com.example.inventory_system_ht.adapter.StockTakingItemAdapter;
import com.example.inventory_system_ht.database.AppDatabase;
import com.example.inventory_system_ht.entity.ScanQueueEntity;
import com.example.inventory_system_ht.entity.SessionItemEntity;
import com.example.inventory_system_ht.model.GeneralResponse;
import com.example.inventory_system_ht.model.StockTakingModel;
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
    private RecyclerView rvScannedTags;
    private TextView tvRemark, tvLocation, tvQty;
    private Button btnTabScanResult, btnTabTakingData;
    private View tvEmpty;
    private Spinner spinnerPower;
    private FloatingActionButton fabScanCamera;
    private ApiService api;
    private AppDatabase db;
    private String token;
    private String sttId = "";
    private String remark = "";
    private final List<StockTakingModel.SessionItem> sessionItems = new ArrayList<>();
    private final List<StockTakingModel.ScannedTagItem> scannedItems = new ArrayList<>();
    private final Set<String> scannedEpcSet = new HashSet<>();
    private final Map<String, Integer> epcIndexMap = new HashMap<>();
    private final Map<String, Integer> tagIdIndexMap = new HashMap<>();
    private final List<String> powerList = Arrays.asList(
            "5 dBm", "10 dBm", "15 dBm", "18 dBm", "21 dBm", "24 dBm", "27 dBm", "30 dBm"
    );
    private boolean hasChanges = false;
    private StockTakingItemAdapter adapter;
    private ScannedTagAdapter scannedAdapter;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int scannedCount = 0;
    private int currentTab = 0; // 0 = Scan Result, 1 = Taking Data
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
        setupTabs();

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
        updateReaderBattery(findViewById(R.id.ivReaderBattery), switchRfid.isChecked());
        if (!switchRfid.isChecked() && scanner != null) RfidBulkHelper.openBarcode(scanner, this);
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
        btnRefresh = findViewById(R.id.btnRefresh);
        resultScan = findViewById(R.id.resultScan);
        rvTags = findViewById(R.id.rvTags);
        rvScannedTags = findViewById(R.id.rvScannedTags);
        tvRemark = findViewById(R.id.tvRemark);
        tvLocation = findViewById(R.id.tvLocation);
        tvQty = findViewById(R.id.tvQty);
        tvEmpty = findViewById(R.id.tvEmpty);
        spinnerPower = findViewById(R.id.spinnerPower);
        fabScanCamera = findViewById(R.id.fabScanCamera);
        btnTabScanResult = findViewById(R.id.btnTabScanResult);
        btnTabTakingData = findViewById(R.id.btnTabTakingData);

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

        scannedAdapter = new ScannedTagAdapter(scannedItems);
        rvScannedTags.setLayoutManager(new LinearLayoutManager(this));
        rvScannedTags.setAdapter(scannedAdapter);
        rvScannedTags.setItemAnimator(null);
    }

    private void setupTabs() {
        btnTabScanResult.setOnClickListener(v -> switchTab(0));
        btnTabTakingData.setOnClickListener(v -> switchTab(1));
        switchTab(0);
    }

    private void switchTab(int tab) {
        currentTab = tab;
        if (tab == 0) {
            rvScannedTags.setVisibility(View.VISIBLE);
            rvTags.setVisibility(View.GONE);
            if (tvEmpty != null) tvEmpty.setVisibility(View.GONE);
            btnTabScanResult.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.blue_theme)));
            btnTabScanResult.setTextColor(Color.WHITE);
            btnTabTakingData.setBackgroundTintList(ColorStateList.valueOf(Color.WHITE));
            btnTabTakingData.setTextColor(ContextCompat.getColor(this, R.color.blue_theme));
        } else {
            rvScannedTags.setVisibility(View.GONE);
            rvTags.setVisibility(View.VISIBLE);
            if (tvEmpty != null) {
                tvEmpty.setVisibility(sessionItems.isEmpty() ? View.VISIBLE : View.GONE);
            }
            btnTabTakingData.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.blue_theme)));
            btnTabTakingData.setTextColor(Color.WHITE);
            btnTabScanResult.setBackgroundTintList(ColorStateList.valueOf(Color.WHITE));
            btnTabScanResult.setTextColor(ContextCompat.getColor(this, R.color.blue_theme));
        }
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
                    "Submit scan results? Scanned: " + scannedCount,
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

    private void loadSessionTagsFromServer() {
        showLoading();
        String userId = new PrefManager(this).getUserId();
        String reqJson = "{\"sttId\":\"" + sttId + "\"}";
        api.getSessionTags(token, sttId).enqueue(new Callback<List<StockTakingModel.SessionItem>>() {
            @Override
            public void onResponse(Call<List<StockTakingModel.SessionItem>> call,
                                   Response<List<StockTakingModel.SessionItem>> res) {
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

                List<StockTakingModel.SessionItem> fromServer = res.body();
                LogManager.get(StockTakingActivity.this).log(LogManager.INFO, LogManager.ACTION_READ,
                        "Stock Taking", sttId, "Load session tags success: " + fromServer.size(),
                        userId, reqJson, resJson);

                sessionItems.clear();
                epcIndexMap.clear();
                tagIdIndexMap.clear();
                scannedItems.clear();
                scannedEpcSet.clear();
                scannedCount = 0;

                for (StockTakingModel.SessionItem item : fromServer) {
                    if (item == null) continue;
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
                    tvEmpty.setVisibility(
                            sessionItems.isEmpty() && currentTab == 1 ? View.VISIBLE : View.GONE);
                }

                saveSessionItemsToCache(new ArrayList<>(sessionItems));
                applyQueueStateToSessionItems();
                adapter.notifyDataSetChanged();
                scannedAdapter.notifyDataSetChanged();
                updateInfo();
            }

            @Override
            public void onFailure(Call<List<StockTakingModel.SessionItem>> call, Throwable t) {
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
                // Rebuild Tab 1 (Scan Result) - only EPC from FOUND actions
                List<StockTakingModel.ScannedTagItem> queuedScans = new ArrayList<>();
                for (ScanQueueEntity q : queue) {
                    if (q.epcTag == null || !"FOUND".equals(q.action)) continue;
                    String upperEpc = q.epcTag.toUpperCase();
                    if (scannedEpcSet.contains(upperEpc)) continue;
                    scannedEpcSet.add(upperEpc);
                    queuedScans.add(new StockTakingModel.ScannedTagItem(null, q.epcTag, null));
                }
                scannedItems.addAll(queuedScans);
                scannedAdapter.notifyDataSetChanged();

                // Apply Tab 2 (Taking Data) states
                for (ScanQueueEntity q : queue) {
                    if (q.epcTag == null) continue;
                    Integer idx = epcIndexMap.get(q.epcTag.toUpperCase());
                    if (idx == null) idx = tagIdIndexMap.get(q.epcTag.toUpperCase());
                    if (idx == null) continue;
                    StockTakingModel.SessionItem item = sessionItems.get(idx);
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
                    if (tvEmpty != null && currentTab == 1) tvEmpty.setVisibility(View.VISIBLE);
                    showWarning("No cached data, tap Refresh");
                    return;
                }
                if (tvEmpty != null) tvEmpty.setVisibility(View.GONE);
                sessionItems.clear();
                epcIndexMap.clear();
                tagIdIndexMap.clear();
                scannedItems.clear();
                scannedEpcSet.clear();
                scannedCount = 0;
                for (SessionItemEntity e : cached) {
                    StockTakingModel.SessionItem item = e.toSessionItem();
                    int pos = sessionItems.size();
                    if (item.epcTag != null)
                        epcIndexMap.put(item.epcTag.toUpperCase(), pos);
                    if (item.tagId != null && !item.tagId.isEmpty())
                        tagIdIndexMap.put(item.tagId.toUpperCase(), pos);
                    sessionItems.add(item);
                }
                rebuildLocationsCache();
                adapter.notifyDataSetChanged();
                scannedAdapter.notifyDataSetChanged();
                applyQueueStateToSessionItems();
                updateInfo();
            });
        }).start();
    }

    private void saveSessionItemsToCache(List<StockTakingModel.SessionItem> items) {
        new Thread(() -> {
            db.appDao().clearSessionItemsBySttId(sttId);
            List<SessionItemEntity> entities = new ArrayList<>();
            for (StockTakingModel.SessionItem item : items)
                entities.add(SessionItemEntity.from(sttId, item));
            if (!entities.isEmpty()) db.appDao().insertSessionItems(entities);
        }).start();
    }

    private void processScan(String epcOrBarcode) {
        if (sttId.isEmpty()) { playScanFeedback(2); return; }

        String key = epcOrBarcode.toUpperCase();

        // Add to Scan Result (Tab 1) - only EPC, dedup by EPC key
        if (!scannedEpcSet.contains(key)) {
            scannedEpcSet.add(key);
            scannedItems.add(0, new StockTakingModel.ScannedTagItem(null, epcOrBarcode, null));
            scannedAdapter.notifyItemInserted(0);
            rvScannedTags.scrollToPosition(0);
        }

        // Match against Taking Data snapshot (Tab 2)
        Integer idx = epcIndexMap.get(key);
        if (idx == null) idx = tagIdIndexMap.get(key);

        if (idx == null) {
            if (!switchRfid.isChecked()) playScanFeedback(2);
            showWarning("Tag not found: " + epcOrBarcode);
            LogManager.get(this).log(LogManager.WARNING, LogManager.ACTION_SCAN, "Stock Taking", epcOrBarcode, "Tag not found in session: " + epcOrBarcode, new PrefManager(this).getUserId());
            return;
        }

        StockTakingModel.SessionItem item = sessionItems.get(idx);
        if (!"PENDING".equals(item.state)) {
            if (!switchRfid.isChecked()) showWarning("Already scanned");
            LogManager.get(this).log(LogManager.WARNING, LogManager.ACTION_SCAN, "Stock Taking", epcOrBarcode, "Duplicate scan: " + epcOrBarcode, new PrefManager(this).getUserId());
            return;
        }

        item.state = "FOUND";
        scannedCount++;
        hasChanges = true;
        adapter.notifyItemChanged(idx);
        rvTags.scrollToPosition(idx);
        updateInfo();
        playScanFeedback(0);
        LogManager.get(this).log(LogManager.INFO, LogManager.ACTION_SCAN, "Stock Taking", epcOrBarcode, "Scanned: " + epcOrBarcode, new PrefManager(this).getUserId());

        saveToQueue(item.epcTag, "FOUND", null, null, null);
    }

    private void rebuildLocationsCache() {
        List<String> locations = new ArrayList<>();
        for (StockTakingModel.SessionItem item : sessionItems) {
            if (item.location != null && !item.location.isEmpty() && !locations.contains(item.location))
                locations.add(item.location);
        }
        cachedLocationsString = locations.isEmpty() ? "-" : String.join(", ", locations);
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

            List<StockTakingModel.OperatorSubmitItem> items = new ArrayList<>();
            for (ScanQueueEntity q : queue) {
                StockTakingModel.OperatorSubmitItem submitItem = new StockTakingModel.OperatorSubmitItem();
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

            StockTakingModel.OperatorSubmitReq req = new StockTakingModel.OperatorSubmitReq(sttId, items);
            String reqJson = "{\"sttId\":\"" + sttId + "\",\"count\":" + items.size() + "}";

            handler.post(() -> api.operatorSubmit(token, req).enqueue(new Callback<GeneralResponse>() {
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
        api.getActiveStockTaking(token).enqueue(new Callback<StockTakingModel.ActiveRes>() {
            @Override
            public void onResponse(Call<StockTakingModel.ActiveRes> call,
                                   Response<StockTakingModel.ActiveRes> response) {
                boolean ended = !response.isSuccessful() || response.body() == null
                        || !sttId.equals(response.body().sttId);
                if (ended) showSessionEndedDialog();
            }
            @Override public void onFailure(Call<StockTakingModel.ActiveRes> call, Throwable t) {}
        });
    }

    private void updateInfo() {
        tvQty.setText("Qty: " + scannedCount + "/" + sessionItems.size());
        tvLocation.setText("Location: " + cachedLocationsString);
    }

    private void showAdjustmentDialog(StockTakingModel.SessionItem item, int position) {
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

    private void showRemoveConfirmDialog(StockTakingModel.SessionItem item, int position) {
        showCustomConfirmDialog("Remove this item? Qty will decrease.", () -> {
            if (item.tagId != null && !item.tagId.isEmpty()) {
                saveToQueue(item.tagId, "REMOVE", null, null, null);
            }
            boolean wasScanned = "FOUND".equals(item.state) || "MANUAL_ADD".equals(item.state);
            if (wasScanned) scannedCount--;
            sessionItems.remove(position);
            epcIndexMap.remove(item.epcTag != null ? item.epcTag.toUpperCase() : "");
            if (item.tagId != null) tagIdIndexMap.remove(item.tagId.toUpperCase());
            for (int i = position; i < sessionItems.size(); i++) {
                StockTakingModel.SessionItem si = sessionItems.get(i);
                if (si.epcTag != null) epcIndexMap.put(si.epcTag.toUpperCase(), i);
                if (si.tagId != null) tagIdIndexMap.put(si.tagId.toUpperCase(), i);
            }
            adapter.notifyItemRemoved(position);
            adapter.notifyItemRangeChanged(position, sessionItems.size());
            hasChanges = true;
            updateInfo();
        });
    }

    private void showManualAddDialog(StockTakingModel.SessionItem item, int position) {
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
        Spinner spinnerNewTagId = dialog.findViewById(R.id.spinnerNewTagId);

        String displayItem;
        if (item.itemCode != null && !item.itemCode.isEmpty()) displayItem = item.itemCode;
        else if (item.itemName != null && !item.itemName.isEmpty()) displayItem = item.itemName;
        else displayItem = item.itemId != null ? item.itemId : "";
        etItemId.setText(displayItem);
        etItemId.setEnabled(false);

        List<String> spinnerTagIds = new ArrayList<>();
        List<StockTakingModel.AvailableTag> filteredTags = new ArrayList<>();
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, spinnerTagIds);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerNewTagId.setAdapter(spinnerAdapter);

        if (isNetworkConnected()) {
            showLoading();
            api.getAvailableTags(token, sttId).enqueue(new Callback<List<StockTakingModel.AvailableTag>>() {
                @Override
                public void onResponse(Call<List<StockTakingModel.AvailableTag>> call, Response<List<StockTakingModel.AvailableTag>> response) {
                    hideLoading();
                    if (response.isSuccessful() && response.body() != null) {
                        for (StockTakingModel.AvailableTag tag : response.body()) {
                            if (tag.itemId != null && tag.itemId.equals(item.itemId)) {
                                filteredTags.add(tag);
                                spinnerTagIds.add(tag.tagId);
                            }
                        }
                        spinnerAdapter.notifyDataSetChanged();
                        if (spinnerTagIds.isEmpty()) showWarning("Tidak ada tag Standby/Printed untuk item ini.");
                    } else {
                        showError("Gagal mengambil data tag available.");
                    }
                }
                @Override
                public void onFailure(Call<List<StockTakingModel.AvailableTag>> call, Throwable t) {
                    hideLoading();
                    showError("Error: " + t.getMessage());
                }
            });
        } else {
            showWarning("Offline, tidak bisa load tag pengganti.");
        }

        dialog.findViewById(R.id.btnCancelManual).setOnClickListener(v -> dialog.dismiss());
        dialog.findViewById(R.id.btnSaveManual).setOnClickListener(v -> {
            if (spinnerNewTagId.getSelectedItem() == null) {
                showSagaFeedback(dialogRoot, "Pilih Tag pengganti terlebih dahulu", 1);
                return;
            }
            String selectedTagId = spinnerNewTagId.getSelectedItem().toString();
            String remarkText = etRemark.getText().toString().trim();

            saveToQueue(item.epcTag, "MANUAL_ADD", item.itemId, selectedTagId, remarkText);

            boolean wasScanned = "FOUND".equals(item.state) || "MANUAL_ADD".equals(item.state);
            item.state = "MANUAL_ADD";
            item.manualRemark = remarkText;
            if (!wasScanned) scannedCount++;
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

    private void handleBackPressed() {
        if (!hasChanges) { finish(); return; }
        showCustomConfirmDialog(
                "Exit? Scanned data saved locally.",
                () -> {
                    sessionItems.clear();
                    epcIndexMap.clear();
                    tagIdIndexMap.clear();
                    scannedItems.clear();
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
            handler.post(() -> { for (String epc : epcs) processScan(epc); });
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
