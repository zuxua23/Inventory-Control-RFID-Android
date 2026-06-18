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
import android.text.Editable;
import android.text.TextWatcher;
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

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.densowave.scannersdk.Barcode.BarcodeData;
import com.densowave.scannersdk.Barcode.BarcodeDataReceivedEvent;
import com.densowave.scannersdk.Common.CommScanner;
import com.densowave.scannersdk.Listener.BarcodeDataDelegate;
import com.densowave.scannersdk.Listener.RFIDDataDelegate;
import com.densowave.scannersdk.RFID.RFIDData;
import com.densowave.scannersdk.RFID.RFIDDataReceivedEvent;

import com.example.inventory_system_ht.activity.base.ScannerActivity;
import com.example.inventory_system_ht.adapter.StockPrepProductAdapter;
import com.example.inventory_system_ht.adapter.TagAdapter;
import com.example.inventory_system_ht.database.AppDao;
import com.example.inventory_system_ht.database.AppDatabase;
import com.example.inventory_system_ht.entity.PendingSubmitEntity;
import com.example.inventory_system_ht.entity.TagCacheEntity;
import com.example.inventory_system_ht.entity.TagLocalEntity;
import com.example.inventory_system_ht.model.DOModel;
import com.example.inventory_system_ht.model.GeneralResponse;
import com.example.inventory_system_ht.model.ItemModel;
import com.example.inventory_system_ht.model.LocationModel;
import com.example.inventory_system_ht.model.StockPrepBulkRequest;
import com.example.inventory_system_ht.model.TagModel;
import com.example.inventory_system_ht.network.ApiClient;
import com.example.inventory_system_ht.network.ApiService;
import com.example.inventory_system_ht.network.ErrorParser;
import com.example.inventory_system_ht.util.LogManager;
import com.example.inventory_system_ht.util.PrefManager;
import com.example.inventory_system_ht.util.RfidBulkHelper;
import com.example.inventory_system_ht.util.RfidSettingsManager;
import com.example.inventory_system_ht.util.ScannerManager;
import com.example.inventory_system_ht.util.SyncWorker;
import com.example.inventory_system_ht.R;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.gson.Gson;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@SuppressLint("UseSwitchCompatOrMaterialCode")
public class StockPrepProductActivity extends ScannerActivity
        implements BarcodeDataDelegate, RFIDDataDelegate {
    private EditText resultScan;
    private TextView tvScanned, tvNoDo, tvDateDo;
    private Switch switchRfid;
    private RecyclerView rvTags;
    private Spinner spinnerLocation, spinnerPower;
    private Button btnListProduct, btnSumProduct;
    private FloatingActionButton fabScanCamera;
    private TagAdapter adapter;
    private StockPrepProductAdapter sumAdapter;
    private List<TagLocalEntity> scannedList;
    private List<ItemModel.SumProduct> sumProductList = new ArrayList<>();
    private final Map<String, Integer> requiredQtyMap = new HashMap<>();
    private final Map<String, String> itemNameMap = new HashMap<>();
    private final Set<String> scannedRawSet = new HashSet<>();
    private final Set<String> scannedEpcSet = new HashSet<>();
    private int scanCount = 0;
    private boolean isListProductTab = true;
    private String selectedLocation = "";
    private String selectedLocationId = "";
    private String currentDoId = "";
    private String currentDoNo = "";
    private boolean isDoDetailLoaded = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ApiService api;
    private String token;
    private AppDao appDao;
    private List<LocationModel> masterLocationList = new ArrayList<>();
    private final List<String> locationList = new ArrayList<>();
    private final List<String> powerList = new ArrayList<>(Arrays.asList(
            "5 dBm", "10 dBm", "15 dBm", "18 dBm", "21 dBm", "24 dBm", "27 dBm", "30 dBm"
    ));
    private ArrayAdapter<String> locationSpinnerAdapter;

    private final Set<String> tagBuffer = new HashSet<>();
    private boolean isProcessingBuffer = false;
    private static final int BATCH_DELAY_MS = 500;

    @Override
    protected CommScanner getScannerInstance() {
        return ScannerManager.getInstance().getScanner();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stock_prep_product);
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
        appDao = AppDatabase.getDatabase(this).appDao();
        token = "Bearer " + new PrefManager(this).getToken();
        api = ApiClient.getClient(this).create(ApiService.class);

        initUI();
        setupLocationSpinner();
        setupPowerSpinner();
        setupSwitchRfid();
        setupTabButtons();

        if (getIntent() != null) {
            currentDoId = getIntent().getStringExtra("DO_ID");
            currentDoNo = getIntent().getStringExtra("NO_DO");
            tvNoDo.setText("No : " + currentDoNo);
            String rawDate = getIntent().getStringExtra("DATE_DO");
            tvDateDo.setText("Date : " + formatToEnglishDate(rawDate));
        }

        fetchLocations();
        fetchDoDetail();
        restoreScannedTagsFromRoom();
        setupListeners();

        FloatingActionButton fabLog = findViewById(R.id.fabLog);
        if (fabLog != null) {
            fabLog.setOnClickListener(v -> {
                Intent i = new Intent(this, LogActivity.class);
                i.putExtra(LogActivity.EXTRA_MENU, "Stock Preparation");
                startActivity(i);
            });
        }
        LogManager.get(this).log(LogManager.INFO, LogManager.ACTION_OPEN, "Stock Preparation", "", "Opened Stock Preparation", new PrefManager(this).getUserId());
    }

    @Override
    protected void onResume() {
        super.onResume();
        CommScanner scanner = getScannerInstance();
        updateReaderBattery(findViewById(R.id.ivReaderBattery), switchRfid.isChecked());

        if (!switchRfid.isChecked() && scanner != null)
            RfidBulkHelper.openBarcode(scanner, this);

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

    private void initUI() {
        tvScanned = findViewById(R.id.tvScanned);
        tvNoDo = findViewById(R.id.tvNoDo);
        tvDateDo = findViewById(R.id.tvDateDo);
        resultScan = findViewById(R.id.resultScan);
        switchRfid = findViewById(R.id.switchRfid);
        rvTags = findViewById(R.id.rvTags);
        spinnerLocation = findViewById(R.id.spinnerLocation);
        spinnerPower = findViewById(R.id.spinnerPower);
        btnListProduct = findViewById(R.id.btnListProduct);
        btnSumProduct = findViewById(R.id.btnSumProduct);
        fabScanCamera = findViewById(R.id.fabScanCamera);

        spinnerPower.setVisibility(View.GONE);
        switchRfid.setChecked(false);

        scannedList = new ArrayList<>();
        adapter = new TagAdapter(scannedList);
        rvTags.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        rvTags.setAdapter(adapter);
        rvTags.setItemAnimator(null);

        adapter.setOnItemClickListener(item -> {
            if (!isListProductTab) return;
            int pos = scannedList.indexOf(item);
            if (pos != -1) showDeleteItemDialog(item, pos);
        });

        resultScan.setShowSoftInputOnFocus(false);
        resultScan.postDelayed(() -> resultScan.requestFocus(), 100);
    }

    private void setupLocationSpinner() {
        List<String> locationListWithHint = new ArrayList<>();
        locationListWithHint.add("Select Location");
        locationListWithHint.addAll(locationList);

        locationSpinnerAdapter = new ArrayAdapter<String>(this, R.layout.item_spinner_selected, R.id.tvSpinnerSelected, locationListWithHint) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView tv = view.findViewById(R.id.tvSpinnerSelected);
                if (tv != null) tv.setTextColor(position == 0
                        ? getColor(R.color.text_grey)
                        : getColor(R.color.black));
                return view;
            }
            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View view = LayoutInflater.from(getContext()).inflate(R.layout.item_dropdown_loc, parent, false);
                TextView tv = view.findViewById(R.id.tvDropdownItem);
                ImageView icon = view.findViewById(R.id.ivDropdownIcon);
                if (tv != null) tv.setText(getItem(position));
                if (icon != null) icon.setVisibility(position == 0 ? View.GONE : View.VISIBLE);
                return view;
            }
        };

        spinnerLocation.setAdapter(locationSpinnerAdapter);
        spinnerLocation.setSelection(0);

        spinnerLocation.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) { selectedLocation = ""; selectedLocationId = ""; return; }
                int realPos = position - 1;
                if (realPos >= masterLocationList.size()) return;
                selectedLocation = masterLocationList.get(realPos).getName();
                selectedLocationId = masterLocationList.get(realPos).getId();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
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
                    int power = parsePower(powerList.get(position), 21);
                    RfidBulkHelper.closeInventory(scanner);
                    RfidBulkHelper.openInventory(scanner, StockPrepProductActivity.this, power);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void setupSwitchRfid() {
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
                                ? spinnerPower.getSelectedItem().toString() : "21 dBm", 21);
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

    }

    private void setupListeners() {
        findViewById(R.id.btnBack).setOnClickListener(v -> confirmExit());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { confirmExit(); }
        });

        resultScan.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (switchRfid.isChecked()) return;
                String data = s.toString().trim();
                if (data.length() < 8) return;

                resultScan.removeTextChangedListener(this);
                resultScan.setText("");
                resultScan.addTextChangedListener(this);

                queueScan(data);
            }
        });

        resultScan.setOnEditorActionListener((v, actionId, event) -> true);
        resultScan.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus)
                resultScan.postDelayed(() -> resultScan.requestFocus(), 150);
        });

        findViewById(R.id.btnSave).setOnClickListener(v -> confirmSubmit());

        findViewById(R.id.btnClear).setOnClickListener(v -> {
            new Thread(() -> {
                for (TagLocalEntity t : new ArrayList<>(scannedList)) {
                    try { appDao.deleteScannedTagByEpc(t.getEpcTag()); } catch (Exception e) { LogManager.get(StockPrepProductActivity.this).log(LogManager.ERROR, LogManager.ACTION_DELETE, "Stock Preparation", "DB", "DB delete error: " + e.getMessage(), new PrefManager(StockPrepProductActivity.this).getUserId()); }
                }
                runOnUiThread(() -> {
                    scannedList.clear();
                    sumProductList.clear();
                    scannedRawSet.clear();
                    scannedEpcSet.clear();
                    buildSumProductList();
                    if (isListProductTab) adapter.notifyDataSetChanged();
                    else if (sumAdapter != null) sumAdapter.updateData(sumProductList);
                    scanCount = 0;
                    tvScanned.setText("Scanned : 0");
                });
            }).start();
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
                            if (barcode != null && !barcode.isEmpty()) queueScan(barcode);
                        } else if (result.getResultCode() == BarcodeCameraActivity.RESULT_PERMISSION_DENIED) {
                            showError("Camera permission denied");
                        }
                    }
            );

    private void setupTabButtons() {
        setTabActive(true);
        btnListProduct.setOnClickListener(v -> {
            if (!isListProductTab) {
                isListProductTab = true;
                setTabActive(true);
                rvTags.setAdapter(adapter);
            }
        });
        btnSumProduct.setOnClickListener(v -> {
            if (isListProductTab) {
                isListProductTab = false;
                setTabActive(false);
                buildSumProductList();
                sumAdapter = new StockPrepProductAdapter(sumProductList);
                rvTags.setAdapter(sumAdapter);
            }
        });
    }

    private void fetchDoDetail() {
        if (currentDoId == null || currentDoId.isEmpty()) return;

        if (!isNetworkConnected()) {
            showWarning("Offline: DO details not loaded. Connect to network and reopen.");
            return;
        }

        String userId = new PrefManager(this).getUserId();
        String reqJson = "{\"doId\":\"" + currentDoId + "\"}";
        api.getDoDetailForPrep(token, currentDoId).enqueue(new Callback<DOModel.DOResponse>() {
            @Override
            public void onResponse(Call<DOModel.DOResponse> call,
                                   Response<DOModel.DOResponse> response) {
                String resJson = "{\"http_code\":" + response.code() + ",\"hasDetails\":"
                        + (response.body() != null && response.body().getDetails() != null) + "}";
                if (response.isSuccessful() && response.body() != null) {
                    LogManager.get(StockPrepProductActivity.this).log(LogManager.INFO, LogManager.ACTION_READ,
                            "Stock Preparation", currentDoId, "Fetch DO detail success: " + currentDoNo,
                            userId, reqJson, resJson);
                    requiredQtyMap.clear();
                    itemNameMap.clear();
                    DOModel.DOResponse body = response.body();
                    if (body.getDetails() != null && !body.getDetails().isEmpty()) {
                        for (DOModel.DODetailResponse d : body.getDetails()) {
                            if (d.getItemId() == null) continue;
                            requiredQtyMap.put(d.getItemId(), d.getQtyRequired() != null ? d.getQtyRequired() : 0);
                            if (d.getItemName() != null && !d.getItemName().isEmpty()) {
                                itemNameMap.put(d.getItemId(), d.getItemName());
                            }
                        }
                        isDoDetailLoaded = true;
                    } else {
                        showWarning("DO has no items");
                    }
                    if (itemNameMap.size() < requiredQtyMap.size()) {
                        fetchItemNamesForDo();
                    } else {
                        runOnUiThread(() -> {
                            if (isListProductTab) adapter.notifyDataSetChanged();
                            else { buildSumProductList(); if (sumAdapter != null) sumAdapter.updateData(sumProductList); }
                        });
                    }
                } else {
                    LogManager.get(StockPrepProductActivity.this).log(LogManager.WARNING, LogManager.ACTION_READ,
                            "Stock Preparation", currentDoId, "Fetch DO detail failed: HTTP " + response.code(),
                            userId, reqJson, resJson);
                    handleApiErrorFriendly(response);
                }
            }

            @Override
            public void onFailure(Call<DOModel.DOResponse> call, Throwable t) {
                String resJson = "{\"error\":\"" + t.getMessage() + "\"}";
                LogManager.get(StockPrepProductActivity.this).log(LogManager.ERROR, LogManager.ACTION_READ,
                        "Stock Preparation", currentDoId, "Fetch DO detail error: " + t.getMessage(),
                        userId, reqJson, resJson);
                runOnUiThread(() -> showWarning("Failed to load DO details. Please check network and restart."));
                handleFailure(t);
            }
        });
    }

    private void fetchLocations() {
        if (!isNetworkConnected()) return;
        String userId = new PrefManager(this).getUserId();
        String reqJson = "{\"endpoint\":\"getLocations\"}";
        api.getLocations(token).enqueue(new Callback<List<LocationModel>>() {
            @Override
            public void onResponse(Call<List<LocationModel>> call,
                                   Response<List<LocationModel>> response) {
                String resJson = "{\"http_code\":" + response.code() + ",\"count\":"
                        + (response.body() != null ? response.body().size() : 0) + "}";
                if (response.isSuccessful() && response.body() != null) {
                    LogManager.get(StockPrepProductActivity.this).log(LogManager.INFO, LogManager.ACTION_READ,
                            "Stock Preparation", "Location",
                            "Fetch locations success: " + response.body().size() + " items",
                            userId, reqJson, resJson);
                    masterLocationList = response.body();
                    locationList.clear();
                    for (LocationModel loc : masterLocationList)
                        locationList.add(loc.getName());
                    runOnUiThread(() -> {
                        List<String> withHint = new ArrayList<>();
                        withHint.add("Select Location");
                        for (LocationModel loc : masterLocationList)
                            withHint.add(loc.getName());

                        locationSpinnerAdapter.clear();
                        locationSpinnerAdapter.addAll(withHint);
                        locationSpinnerAdapter.notifyDataSetChanged();

                        if (!selectedLocationId.isEmpty()) {
                            for (int i = 0; i < masterLocationList.size(); i++) {
                                if (masterLocationList.get(i).getId().equals(selectedLocationId)) {
                                    spinnerLocation.setSelection(i + 1);
                                    break;
                                }
                            }
                        }
                    });
                } else {
                    LogManager.get(StockPrepProductActivity.this).log(LogManager.WARNING, LogManager.ACTION_READ,
                            "Stock Preparation", "Location",
                            "Fetch locations failed: HTTP " + response.code(),
                            userId, reqJson, resJson);
                }
            }
            @Override
            public void onFailure(Call<List<LocationModel>> call, Throwable t) {
                String resJson = "{\"error\":\"" + t.getMessage() + "\"}";
                LogManager.get(StockPrepProductActivity.this).log(LogManager.ERROR, LogManager.ACTION_READ,
                        "Stock Preparation", "Location",
                        "Fetch locations error: " + t.getMessage(),
                        userId, reqJson, resJson);
            }
        });
    }

    private void fetchItemNamesForDo() {
        if (!isNetworkConnected()) return;
        String userId = new PrefManager(this).getUserId();
        String reqJson = "{\"endpoint\":\"getAllItems\"}";
        api.getAllItems(token).enqueue(new Callback<List<ItemModel.ItemResponse>>() {
            @Override
            public void onResponse(Call<List<ItemModel.ItemResponse>> call,
                                   Response<List<ItemModel.ItemResponse>> response) {
                String resJson = "{\"http_code\":" + response.code() + ",\"count\":"
                        + (response.body() != null ? response.body().size() : 0) + "}";
                if (response.isSuccessful() && response.body() != null) {
                    LogManager.get(StockPrepProductActivity.this).log(LogManager.INFO, LogManager.ACTION_READ,
                            "Stock Preparation", "Item Names",
                            "Fetch all items success: " + response.body().size() + " items",
                            userId, reqJson, resJson);
                    for (ItemModel.ItemResponse item : response.body()) {
                        if (requiredQtyMap.containsKey(item.getItemId())
                                && !itemNameMap.containsKey(item.getItemId())) {
                            itemNameMap.put(item.getItemId(), item.getItemName());
                        }
                    }
                } else {
                    LogManager.get(StockPrepProductActivity.this).log(LogManager.WARNING, LogManager.ACTION_READ,
                            "Stock Preparation", "Item Names",
                            "Fetch all items failed: HTTP " + response.code(),
                            userId, reqJson, resJson);
                }
                runOnUiThread(() -> {
                    if (isListProductTab) adapter.notifyDataSetChanged();
                    else { buildSumProductList(); if (sumAdapter != null) sumAdapter.updateData(sumProductList); }
                });
            }

            @Override
            public void onFailure(Call<List<ItemModel.ItemResponse>> call, Throwable t) {
                String resJson = "{\"error\":\"" + t.getMessage() + "\"}";
                LogManager.get(StockPrepProductActivity.this).log(LogManager.ERROR, LogManager.ACTION_READ,
                        "Stock Preparation", "Item Names",
                        "Fetch all items error: " + t.getMessage(),
                        userId, reqJson, resJson);
                runOnUiThread(() -> {
                    if (isListProductTab) adapter.notifyDataSetChanged();
                    else { buildSumProductList(); if (sumAdapter != null) sumAdapter.updateData(sumProductList); }
                });
            }
        });
    }

    private void restoreScannedTagsFromRoom() {
        new Thread(() -> {
            try {
                List<TagLocalEntity> saved = appDao.getPendingTags();
                List<TagLocalEntity> forThis = new ArrayList<>();
                for (TagLocalEntity t : saved) {
                    if (currentDoNo != null && currentDoNo.equalsIgnoreCase(t.getDoIdRef()))
                        forThis.add(t);
                }
                runOnUiThread(() -> {
                    scannedList.clear(); scannedRawSet.clear(); scannedEpcSet.clear(); scanCount = 0;
                    for (TagLocalEntity t : forThis) {
                        scannedList.add(t);
                        scannedEpcSet.add(t.getEpcTag().toUpperCase());
                        scannedRawSet.add(t.getEpcTag().toUpperCase());
                        scanCount++;
                    }
                    adapter.notifyDataSetChanged();
                    tvScanned.setText("Scanned : " + scanCount);
                    if (!forThis.isEmpty())
                        showWarning("Restored " + forThis.size() + " item(s)");
                });
            } catch (Exception e) { LogManager.get(StockPrepProductActivity.this).log(LogManager.ERROR, LogManager.ACTION_READ, "Stock Preparation", "Session", "Failed to restore scan session: " + e.getMessage(), new PrefManager(StockPrepProductActivity.this).getUserId()); }
        }).start();
    }

    private void queueScan(String scannedData) {
        if (!isDoDetailLoaded) {
            showWarning("Loading DO details, please wait...");
            return;
        }

        if (selectedLocationId == null || selectedLocationId.isEmpty()) {
            showWarning("Select location first");
            return;
        }

        String key = scannedData.toUpperCase();

        if (scannedEpcSet.contains(key)) {
            playScanFeedback(1);
            if (!switchRfid.isChecked()) showWarning("Already scanned");
            return;
        }
        if (scannedRawSet.contains(key)) {
            return;
        }

        synchronized (tagBuffer) {
            tagBuffer.add(key);

            if (!isProcessingBuffer) {
                isProcessingBuffer = true;
                handler.postDelayed(this::processTagBuffer, BATCH_DELAY_MS);
            }
        }
    }

    private void processTagBuffer() {
        List<String> batchToProcess = new ArrayList<>();
        synchronized (tagBuffer) {
            batchToProcess.addAll(tagBuffer);
            tagBuffer.clear();
            isProcessingBuffer = false;
        }

        if (batchToProcess.isEmpty()) return;

        boolean isRfid = switchRfid.isChecked();

        for (String epc : batchToProcess) {
            scannedRawSet.add(epc);
            TagLocalEntity placeholder = new TagLocalEntity(
                    epc, epc, "PENDING", "Validating...", currentDoNo, 0);
            scannedList.add(0, placeholder);
            scanCount++;
        }

        tvScanned.setText("Scanned : " + scanCount);
        adapter.notifyDataSetChanged();
        rvTags.scrollToPosition(0);
        playScanFeedback(0);

        validateTagsBulk(batchToProcess, isRfid);
    }

    private static final long CACHE_EXPIRY_MS = 16 * 60 * 60 * 1000L;

    private void validateTagsBulk(List<String> codes, boolean isRfid) {
        new Thread(() -> {
            List<TagLocalEntity> successfulTags = new ArrayList<>();
            List<String> failedCodes = new ArrayList<>();
            Map<String, String> rejectionReasons = new HashMap<>();
            String userId = new PrefManager(this).getUserId();

            for (String code : codes) {
                TagLocalEntity candidate = null;

                if (!isNetworkConnected()) {
                    TagCacheEntity cached = appDao.getTagCacheByKey(code);
                    if (cached == null) {
                        rejectionReasons.put(code, "Tag not found in cache");
                        failedCodes.add(code);
                        continue;
                    }
                    if (System.currentTimeMillis() - cached.cachedAt > CACHE_EXPIRY_MS) {
                        rejectionReasons.put(code, "Cache expired, please go online to re-validate");
                        failedCodes.add(code);
                        continue;
                    }
                    if (!requiredQtyMap.containsKey(cached.itemId)) {
                        rejectionReasons.put(code, "Item " + cached.itemId + " not in this DO");
                        failedCodes.add(code);
                        continue;
                    }
                    if (scannedEpcSet.contains(cached.epcTag.toUpperCase())) {
                        rejectionReasons.put(code, "Already scanned");
                        failedCodes.add(code);
                        continue;
                    }
                    candidate = new TagLocalEntity(
                            cached.epcTag, cached.tagId, cached.itemId,
                            cached.itemName, currentDoNo, 0);
                } else {
                    try {
                        TagModel.BulkInfoReq req = new TagModel.BulkInfoReq(
                                Arrays.asList(code), isRfid ? "RFID" : "QR");
                        Response<List<TagModel.TagInfoDto>> response = api.getTagsInfoBulk(token, req).execute();

                        if (!response.isSuccessful() || response.body() == null || response.body().isEmpty()) {
                            rejectionReasons.put(code, "Tag not registered in database");
                            LogManager.get(this).log(LogManager.WARNING, LogManager.ACTION_SCAN,
                                    "Stock Preparation", code,
                                    "Tag rejected - not registered in database", userId);
                            failedCodes.add(code);
                            continue;
                        }

                        TagModel.TagInfoDto info = response.body().get(0);

                        TagCacheEntity cache = new TagCacheEntity();
                        cache.epcTag = info.getEpcTag() != null ? info.getEpcTag() : code;
                        cache.tagId = info.getTagId();
                        cache.itemId = info.getItemId();
                        cache.itemName = info.getItemName();
                        cache.status = info.getStatus();
                        cache.cachedAt = System.currentTimeMillis();
                        appDao.insertTagCache(cache);

                        if (!requiredQtyMap.containsKey(info.getItemId())) {
                            String reason = "Item " + info.getItemId() + " not in this DO";
                            rejectionReasons.put(code, reason);
                            LogManager.get(this).log(LogManager.WARNING, LogManager.ACTION_SCAN,
                                    "Stock Preparation", code,
                                    "Tag rejected - " + reason, userId);
                            failedCodes.add(code);
                            continue;
                        }
                        if (scannedEpcSet.contains(info.getEpcTag() != null
                                ? info.getEpcTag().toUpperCase() : code)) {
                            rejectionReasons.put(code, "Already scanned");
                            failedCodes.add(code);
                            continue;
                        }
                        candidate = new TagLocalEntity(
                                info.getEpcTag() != null ? info.getEpcTag() : code,
                                info.getTagId(), info.getItemId(),
                                info.getItemName(), currentDoNo, 0);
                    } catch (Exception e) {
                        LogManager.get(this).log(LogManager.ERROR, LogManager.ACTION_SCAN, "Stock Preparation", code, "Tag API error: " + e.getMessage(), userId);
                        rejectionReasons.put(code, "Network error");
                        failedCodes.add(code);
                        continue;
                    }
                }

                successfulTags.add(candidate);
                appDao.insertScannedTag(candidate);
            }

            runOnUiThread(() -> {
                boolean isUiModified = false;

                for (TagLocalEntity real : successfulTags) {
                    for (int i = 0; i < scannedList.size(); i++) {
                        TagLocalEntity item = scannedList.get(i);
                        if ("PENDING".equals(item.getItmId()) && item.getEpcTag().equalsIgnoreCase(real.getEpcTag())) {
                            scannedList.set(i, real);
                            scannedEpcSet.add(real.getEpcTag().toUpperCase());
                            isUiModified = true;
                            break;
                        }
                    }
                }

                for (String code : failedCodes) {
                    for (int i = 0; i < scannedList.size(); i++) {
                        TagLocalEntity item = scannedList.get(i);
                        if ("PENDING".equals(item.getItmId()) && item.getEpcTag().equalsIgnoreCase(code)) {
                            scannedList.remove(i);
                            scannedRawSet.remove(code.toUpperCase());
                            scanCount--;
                            isUiModified = true;
                            break;
                        }
                    }
                }

                if (isUiModified) {
                    buildSumProductList();
                    tvScanned.setText("Scanned : " + scanCount);
                    adapter.notifyDataSetChanged();
                    if (sumAdapter != null) sumAdapter.updateData(sumProductList);
                }

                if (!failedCodes.isEmpty()) {
                    playScanFeedback(2);
                    String reason = rejectionReasons.get(failedCodes.get(0));
                    showWarning(reason != null ? reason : "Tag rejected");
                }
            });

        }).start();
    }

    @Override
    public void onRFIDDataReceived(CommScanner scanner, RFIDDataReceivedEvent event) {
        List<String> epcs = new ArrayList<>();
        for (RFIDData data : event.getRFIDData()) {
            String epc = RfidBulkHelper.bytesToHex(data.getUII());
            if (!epc.isEmpty()) epcs.add(epc.toUpperCase());
        }
        if (!epcs.isEmpty()) handler.post(() -> { for (String epc : epcs) queueScan(epc); });
    }

    @Override
    public void onBarcodeDataReceived(CommScanner scanner, BarcodeDataReceivedEvent event) {
        List<BarcodeData> dataList = event.getBarcodeData();
        if (!dataList.isEmpty()) {
            String barcode = new String(dataList.get(0).getData());
            handler.post(() -> queueScan(barcode));
        }
    }

    private void confirmSubmit() {
        if (scannedList.isEmpty()) { showWarning("No items scanned"); return; }
        if (selectedLocationId == null || selectedLocationId.isEmpty()) {
            showWarning("Select location first"); return;
        }
        for (TagLocalEntity t : scannedList) {
            if ("PENDING".equals(t.getItmId())) {
                showWarning("Some items still validating"); return;
            }
        }
        for (Map.Entry<String, Integer> entry : requiredQtyMap.entrySet()) {
            int scannedForItem = 0;
            for (TagLocalEntity t : scannedList) {
                if (entry.getKey().equals(t.getItmId())) scannedForItem++;
            }
            if (scannedForItem < entry.getValue()) {
                showWarning("Belum semua item terpenuhi. Scan semua item terlebih dahulu.");
                return;
            }
        }

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_regist);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        ((TextView) dialog.findViewById(R.id.tvTitle))
                .setText("Save " + scannedList.size() + " item's to \"" + selectedLocation + "\"?");
        Button btnYes = dialog.findViewById(R.id.btnSave);
        btnYes.setText("Save");
        btnYes.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.green_button)));
        dialog.findViewById(R.id.btnNo).setOnClickListener(v -> dialog.dismiss());
        btnYes.setOnClickListener(v -> { dialog.dismiss(); submitToBackend(); });
        dialog.show();
    }

    private void submitToBackend() {
        final boolean isRfid = switchRfid.isChecked();
        final String scannerType = isRfid ? "RFID" : "QR";

        List<String> codes = new ArrayList<>();
        for (TagLocalEntity t : scannedList) {
            String code = isRfid ? t.getEpcTag() : t.getTagId();
            if (code != null && !code.isEmpty()) codes.add(code);
        }
        if (codes.isEmpty()) { showWarning("No valid items"); return; }

        if (!isNetworkConnected()) {
            new Thread(() -> {
                PendingSubmitEntity pending = new PendingSubmitEntity();
                pending.doId = currentDoId;
                pending.scannedCodes = new Gson().toJson(codes);
                pending.scannerType = scannerType;
                pending.locId = selectedLocationId;
                pending.createdAt = System.currentTimeMillis();
                appDao.insertPendingSubmit(pending);

                WorkManager.getInstance(getApplicationContext()).enqueue(
                        new OneTimeWorkRequest.Builder(SyncWorker.class)
                                .setConstraints(new Constraints.Builder()
                                        .setRequiredNetworkType(NetworkType.CONNECTED).build())
                                .build());

                for (TagLocalEntity t : new ArrayList<>(scannedList)) {
                    try { appDao.deleteScannedTagByEpc(t.getEpcTag()); } catch (Exception e) { LogManager.get(StockPrepProductActivity.this).log(LogManager.ERROR, LogManager.ACTION_DELETE, "Stock Preparation", "DB", "DB delete error: " + e.getMessage(), new PrefManager(StockPrepProductActivity.this).getUserId()); }
                }

                runOnUiThread(() -> {
                    showSuccess("Saved offline, will sync later");
                    playScanFeedback(0);
                    clearScannedData();
                    finish();
                });
            }).start();
            return;
        }

        showLoading();
        String userId = new PrefManager(this).getUserId();
        String submitReqJson = "{\"doId\":\"" + currentDoId + "\",\"scannerType\":\"" + scannerType
                + "\",\"locationId\":\"" + selectedLocationId + "\",\"count\":" + codes.size() + "}";
        api.submitStockPrep(token, new StockPrepBulkRequest(
                        currentDoId, codes, scannerType, selectedLocationId))
                .enqueue(new Callback<GeneralResponse>() {
                    @Override
                    public void onResponse(Call<GeneralResponse> call,
                                           Response<GeneralResponse> response) {
                        hideLoading();
                        String submitResJson = "{\"http_code\":" + response.code() + ",\"success\":"
                                + response.isSuccessful() + "}";
                        if (response.isSuccessful()) {
                            LogManager.get(StockPrepProductActivity.this).log(LogManager.INFO, LogManager.ACTION_SUBMIT,
                                    "Stock Preparation", currentDoId,
                                    "Submitted " + codes.size() + " items for DO: " + currentDoNo,
                                    userId, submitReqJson, submitResJson);
                            new Thread(() -> {
                                for (TagLocalEntity t : new ArrayList<>(scannedList)) {
                                    try { appDao.deleteScannedTagByEpc(t.getEpcTag()); } catch (Exception e) { LogManager.get(StockPrepProductActivity.this).log(LogManager.ERROR, LogManager.ACTION_DELETE, "Stock Preparation", "DB", "DB delete error: " + e.getMessage(), new PrefManager(StockPrepProductActivity.this).getUserId()); }
                                }
                                runOnUiThread(() -> {
                                    showSuccess("Items saved");
                                    playScanFeedback(0);
                                    clearScannedData();
                                    finish();
                                });
                            }).start();
                        } else {
                            LogManager.get(StockPrepProductActivity.this).log(LogManager.WARNING, LogManager.ACTION_SUBMIT,
                                    "Stock Preparation", currentDoId,
                                    "Submit failed: HTTP " + response.code(),
                                    userId, submitReqJson, submitResJson);
                            handleApiErrorFriendly(response);
                            playScanFeedback(2);
                        }
                    }

                    @Override
                    public void onFailure(Call<GeneralResponse> call, Throwable t) {
                        hideLoading();
                        String submitResJson = "{\"error\":\"" + t.getMessage() + "\"}";
                        LogManager.get(StockPrepProductActivity.this).log(LogManager.ERROR, LogManager.ACTION_SUBMIT,
                                "Stock Preparation", currentDoId,
                                "Submit error: " + t.getMessage(),
                                userId, submitReqJson, submitResJson);
                        handleFailure(t);
                        playScanFeedback(2);
                    }
                });
    }

    private void clearScannedData() {
        scannedList.clear();
        sumProductList.clear();
        scannedRawSet.clear();
        scannedEpcSet.clear();
        buildSumProductList();
        adapter.notifyDataSetChanged();
        if (sumAdapter != null) sumAdapter.updateData(sumProductList);
        scanCount = 0;
        tvScanned.setText("Scanned : 0");
    }

    private void buildSumProductList() {
        Map<String, String> tagDerivedNames = new HashMap<>();
        for (TagLocalEntity item : scannedList) {
            String pName = item.getProductName();
            if (pName != null && !pName.trim().isEmpty() && !pName.equals("Validating...")) {
                tagDerivedNames.put(item.getItmId(), pName);
            }
        }

        Map<String, ItemModel.SumProduct> map = new LinkedHashMap<>();

        for (Map.Entry<String, Integer> e : requiredQtyMap.entrySet()) {
            String itemId = e.getKey();
            String name = itemNameMap.get(itemId);

            if (name == null || name.trim().isEmpty()) name = tagDerivedNames.get(itemId);
            if (name == null || name.trim().isEmpty()) name = itemId;

            map.put(itemId, new ItemModel.SumProduct(itemId, name, 0, e.getValue()));
        }

        for (TagLocalEntity item : scannedList) {
            String itemId = item.getItmId();
            if ("PENDING".equals(itemId)) continue;
            if (map.containsKey(itemId)) {
                map.get(itemId).addCount(1);
            } else {
                String name = item.getProductName();
                if (name == null || name.trim().isEmpty() || name.equals("Validating...")) {
                    name = itemId;
                }
                map.put(itemId, new ItemModel.SumProduct(itemId, name, 1, 0));
            }
        }

        sumProductList.clear();
        sumProductList.addAll(map.values());
    }

    private void showDeleteItemDialog(TagLocalEntity tag, int position) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_regist);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        String displayName = (tag.getProductName() != null && !tag.getProductName().isEmpty()
                && !"Validating...".equals(tag.getProductName()))
                ? tag.getProductName() : "this item";
        ((TextView) dialog.findViewById(R.id.tvTitle))
                .setText("Remove \"" + displayName + "\"?");
        Button btnYes = dialog.findViewById(R.id.btnSave);
        btnYes.setText("Remove");
        btnYes.setBackgroundTintList(ColorStateList.valueOf(Color.RED));
        dialog.findViewById(R.id.btnNo).setOnClickListener(v -> dialog.dismiss());
        btnYes.setOnClickListener(v -> {
            dialog.dismiss();
            new Thread(() -> {
                try { appDao.deleteScannedTagByEpc(tag.getEpcTag()); } catch (Exception e) { LogManager.get(StockPrepProductActivity.this).log(LogManager.ERROR, LogManager.ACTION_DELETE, "Stock Preparation", "DB", "DB delete error: " + e.getMessage(), new PrefManager(StockPrepProductActivity.this).getUserId()); }
                runOnUiThread(() -> {
                    scannedRawSet.remove(tag.getEpcTag().toUpperCase());
                    scannedEpcSet.remove(tag.getEpcTag().toUpperCase());
                    scannedList.remove(position);
                    scanCount = Math.max(0, scanCount - 1);
                    if (isListProductTab) {
                        adapter.notifyItemRemoved(position);
                        adapter.notifyItemRangeChanged(position, scannedList.size());
                    } else {
                        buildSumProductList();
                        if (sumAdapter != null) sumAdapter.updateData(sumProductList);
                    }
                    tvScanned.setText("Scanned : " + scanCount);
                });
            }).start();
        });
        dialog.show();
    }

    private void confirmExit() {
        if (scannedList.isEmpty()) { finish(); return; }
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_regist);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        ((TextView) dialog.findViewById(R.id.tvTitle))
                .setText("Leave? Items are saved locally.");
        Button btnYes = dialog.findViewById(R.id.btnSave);
        btnYes.setText("Leave");
        btnYes.setBackgroundTintList(ColorStateList.valueOf(Color.RED));
        dialog.findViewById(R.id.btnNo).setOnClickListener(v -> dialog.dismiss());
        btnYes.setOnClickListener(v -> { dialog.dismiss(); finish(); });
        dialog.show();
    }

    private void setTabActive(boolean listActive) {
        btnListProduct.setBackgroundTintList(ColorStateList.valueOf(
                getColor(listActive ? R.color.blue_theme : R.color.white)));
        btnListProduct.setTextColor(getColor(listActive ? R.color.white : R.color.blue_theme));
        btnSumProduct.setBackgroundTintList(ColorStateList.valueOf(
                getColor(listActive ? R.color.white : R.color.blue_theme)));
        btnSumProduct.setTextColor(getColor(listActive ? R.color.blue_theme : R.color.white));
    }

    private void handleApiErrorFriendly(Response<?> response) {
        if (response.code() == 401) { handleApiError(response); return; }
        hideLoading();
        showError(ErrorParser.getMessage(response));
    }

    private String formatToEnglishDate(String rawDate) {
        try {
            if (rawDate == null || rawDate.isEmpty()) return "";
            String datePart = rawDate.length() >= 10 ? rawDate.substring(0, 10) : rawDate;
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            SimpleDateFormat out = new SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH);
            return out.format(in.parse(datePart));
        } catch (Exception e) { return rawDate; }
    }
}
