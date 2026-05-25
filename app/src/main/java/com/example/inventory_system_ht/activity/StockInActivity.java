package com.example.inventory_system_ht.activity;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.densowave.scannersdk.Barcode.BarcodeDataReceivedEvent;
import com.densowave.scannersdk.Common.CommScanner;
import com.densowave.scannersdk.Listener.BarcodeDataDelegate;
import com.densowave.scannersdk.Listener.RFIDDataDelegate;
import com.densowave.scannersdk.RFID.RFIDData;
import com.densowave.scannersdk.RFID.RFIDDataReceivedEvent;

import com.example.inventory_system_ht.activity.base.ScannerActivity;
import com.example.inventory_system_ht.util.LogManager;
import com.example.inventory_system_ht.adapter.ItemAdapter;
import com.example.inventory_system_ht.adapter.StockInProductAdapter;
import com.example.inventory_system_ht.database.AppDatabase;
import com.example.inventory_system_ht.entity.StockInScanEntity;
import com.example.inventory_system_ht.model.GeneralResponse;
import com.example.inventory_system_ht.model.ItemModel;
import com.example.inventory_system_ht.model.LocationModel;
import com.example.inventory_system_ht.model.StockInRequest;
import com.example.inventory_system_ht.model.TagModel;
import com.example.inventory_system_ht.network.ApiClient;
import com.example.inventory_system_ht.network.ApiService;
import com.example.inventory_system_ht.network.ErrorParser;
import com.example.inventory_system_ht.util.PrefManager;
import com.example.inventory_system_ht.util.RfidBulkHelper;
import com.example.inventory_system_ht.util.ScannerManager;
import com.example.inventory_system_ht.R;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@SuppressLint("UseSwitchCompatOrMaterialCode")
public class StockInActivity extends ScannerActivity
        implements BarcodeDataDelegate, RFIDDataDelegate {

    private ImageView btnBack;
    private Button btnClear, btnSave, btnListProduct, btnSumProduct;
    private Switch switchRfid;
    private EditText resultScan;
    private TextView tvScanned, tvEmpty;
    private RecyclerView rvTags;
    private Spinner spinnerLocation, spinnerPower;
    private FloatingActionButton fabScanCamera;

    private ItemAdapter adapter;
    private StockInProductAdapter sumAdapter;
    private final List<ItemModel.Item> scannedItemsList = new ArrayList<>();
    private List<ItemModel.SumProduct> sumProductList = new ArrayList<>();
    private List<LocationModel> masterLocationList = new ArrayList<>();
    private final List<String> locationList = new ArrayList<>();
    private final List<String> powerList = new ArrayList<>(Arrays.asList(
            "5 dBm", "10 dBm", "15 dBm", "18 dBm", "21 dBm", "24 dBm", "27 dBm", "30 dBm"
    ));

    private ArrayAdapter<String> locationSpinnerAdapter;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private AppDatabase db;

    private int totalScanCount = 0;
    private boolean isListProductTab = true;
    private String selectedLocation = "";
    private String selectedLocationId = "";

    @Override
    protected CommScanner getScannerInstance() {
        return ScannerManager.getInstance().getScanner();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stock_in);

        db = AppDatabase.getDatabase(this);

        bindViews();
        setupRecyclerView();
        setupTabButtons();
        setupLocationSpinner();
        setupPowerSpinner();
        setupSwitchRfid();
        setupBarcodeTextWatcher();
        setupButtonListeners();

        resultScan.setShowSoftInputOnFocus(false);
        resultScan.postDelayed(() -> resultScan.requestFocus(), 100);

        fabScanCamera.setOnClickListener(v -> {
            if (switchRfid.isChecked()) switchRfid.setChecked(false);
            cameraScanLauncher.launch(new Intent(this, BarcodeCameraActivity.class));
        });

        FloatingActionButton fabLog = findViewById(R.id.fabLog);
        if (fabLog != null) {
            fabLog.setOnClickListener(v -> {
                Intent i = new Intent(this, LogActivity.class);
                i.putExtra(LogActivity.EXTRA_MENU, "Stock In");
                startActivity(i);
            });
        }
        LogManager.get(this).log(LogManager.INFO, LogManager.ACTION_OPEN, "Stock In", "", "Opened Stock In", new PrefManager(this).getUserId());

        fetchLocations();
        restoreFromRoom();
    }

    @Override
    protected void onResume() {
        super.onResume();
        CommScanner scanner = getScannerInstance();
        updateReaderBattery(findViewById(R.id.ivReaderBattery), switchRfid.isChecked());
        if (!switchRfid.isChecked() && scanner != null) RfidBulkHelper.openBarcode(scanner, this);

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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isFinishing()) {
            new Thread(() -> db.appDao().clearAllStockInScans()).start();
        }
    }

    private void bindViews() {
        btnBack = findViewById(R.id.btnBack);
        btnClear = findViewById(R.id.btnClear);
        btnSave = findViewById(R.id.btnSave);
        btnListProduct = findViewById(R.id.btnListProduct);
        btnSumProduct = findViewById(R.id.btnSumProduct);
        switchRfid = findViewById(R.id.switchRfid);
        resultScan = findViewById(R.id.resultScan);
        tvScanned = findViewById(R.id.tvScanned);
        rvTags = findViewById(R.id.rvTags);
        spinnerLocation = findViewById(R.id.spinnerLocation);
        spinnerPower = findViewById(R.id.spinnerPower);
        tvEmpty = findViewById(R.id.tvEmpty);
        fabScanCamera = findViewById(R.id.fabScanCamera);

        switchRfid.setChecked(false);
        spinnerPower.setVisibility(View.GONE);
        rvTags.setItemAnimator(null);

        updateEmptyState();
    }

    private void setupRecyclerView() {
        adapter = new ItemAdapter(scannedItemsList);
        rvTags.setLayoutManager(new LinearLayoutManager(this));
        rvTags.setAdapter(adapter);
        adapter.setOnItemClickListener(item -> {
            if (!isListProductTab) return;
            int pos = scannedItemsList.indexOf(item);
            if (pos != -1) showDeleteItemDialog(item, pos);
        });
    }

    private void setupTabButtons() {
        setTabActive(true);
        btnListProduct.setOnClickListener(v -> {
            if (!isListProductTab) {
                isListProductTab = true;
                setTabActive(true);
                adapter = new ItemAdapter(scannedItemsList);
                adapter.setOnItemClickListener(item -> {
                    int pos = scannedItemsList.indexOf(item);
                    if (pos != -1) showDeleteItemDialog(item, pos);
                });
                rvTags.setAdapter(adapter);
            }
        });
        btnSumProduct.setOnClickListener(v -> {
            if (isListProductTab) {
                isListProductTab = false;
                setTabActive(false);
                buildSumProductList();
                sumAdapter = new StockInProductAdapter(sumProductList);
                rvTags.setAdapter(sumAdapter);
            }
        });
    }

    private final ActivityResultLauncher<Intent> cameraScanLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        overridePendingTransition(0, 0);
                        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                            String barcode = result.getData().getStringExtra(BarcodeCameraActivity.EXTRA_BARCODE);
                            if (barcode != null && !barcode.isEmpty()) enqueueScan(barcode);
                        } else if (result.getResultCode() == BarcodeCameraActivity.RESULT_PERMISSION_DENIED) {
                            showError("Camera permission denied");
                        }
                    }
            );

    private void setupLocationSpinner() {
        List<String> locationListWithHint = new ArrayList<>();
        locationListWithHint.add("Select Location");
        locationListWithHint.addAll(locationList);

        locationSpinnerAdapter = new ArrayAdapter<String>(this, R.layout.item_spinner_selected, R.id.tvSpinnerSelected, locationListWithHint) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView tv = view.findViewById(R.id.tvSpinnerSelected);
                if (tv != null) {
                    tv.setTextColor(position == 0
                            ? getColor(R.color.text_grey)
                            : getColor(R.color.black));
                }
                return view;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View view = LayoutInflater.from(getContext())
                        .inflate(R.layout.item_dropdown, parent, false);
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
                if (position == 0) {
                    selectedLocation = "";
                    selectedLocationId = "";
                    return;
                }
                int realPos = position - 1;
                if (realPos >= masterLocationList.size()) return;
                selectedLocation = masterLocationList.get(realPos).getName();
                selectedLocationId = masterLocationList.get(realPos).getId();
                new Thread(() -> db.appDao().updateStockInLocation(selectedLocationId)).start();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void setupPowerSpinner() {
        ArrayAdapter<String> powerAdapter = new ArrayAdapter<String>(this, R.layout.item_spinner_selected, R.id.tvSpinnerSelected, powerList) {
            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View view = LayoutInflater.from(getContext())
                        .inflate(R.layout.item_dropdown, parent, false);
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
                    RfidBulkHelper.openInventory(scanner, StockInActivity.this, power);
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

        spinnerPower.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!switchRfid.isChecked()) return;
                CommScanner scanner = getScannerInstance();
                if (scanner != null) {
                    int power = parsePower(powerList.get(position), 27);
                    RfidBulkHelper.closeInventory(scanner);
                    RfidBulkHelper.openInventory(scanner, StockInActivity.this, power);
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void setupBarcodeTextWatcher() {
        resultScan.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                String data = s.toString().trim();
                if (data.length() >= 8 && !switchRfid.isChecked()) {
                    resultScan.setText("");
                    enqueueScan(data);
                }
            }
        });
        resultScan.setOnKeyListener((v, keyCode, event) ->
                keyCode == android.view.KeyEvent.KEYCODE_ENTER);
    }

    private void setupButtonListeners() {
        btnBack.setOnClickListener(v -> finish());

        btnClear.setOnClickListener(v -> {
            if (scannedItemsList.isEmpty()) { showWarning("Nothing to clear"); return; }
            new AlertDialog.Builder(this)
                    .setTitle("Clear")
                    .setMessage("Clear all " + totalScanCount + " items?")
                    .setPositiveButton("Clear", (d, w) -> {
                        new Thread(() -> db.appDao().clearAllStockInScans()).start();
                        clearAllData();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        btnSave.setOnClickListener(v -> {
            if (scannedItemsList.isEmpty()) { showWarning("No items scanned"); return; }
            if (selectedLocationId.isEmpty()) { showWarning("Select location first"); return; }
            showSaveConfirmDialog();
        });
    }

    private void restoreFromRoom() {
        new Thread(() -> {
            List<StockInScanEntity> saved = db.appDao().getAllStockInScans();
            if (saved.isEmpty()) return;

            List<ItemModel.Item> restored = new ArrayList<>();
            String locId = null;
            for (StockInScanEntity e : saved) {
                restored.add(new ItemModel.Item(
                        e.epcTag,
                        e.itemId != null ? e.itemId : "",
                        e.isResolved ? e.itemName : "Pending...", 1));
                if (locId == null && e.locationId != null && !e.locationId.isEmpty())
                    locId = e.locationId;
            }

            final String finalLocId = locId;
            handler.post(() -> {
                scannedItemsList.clear();
                scannedItemsList.addAll(restored);
                totalScanCount = scannedItemsList.size();
                adapter.notifyDataSetChanged();
                updateEmptyState();
                updateScanCount();

                if (finalLocId != null) {
                    selectedLocationId = finalLocId;
                    for (int i = 0; i < masterLocationList.size(); i++) {
                        if (masterLocationList.get(i).getId().equals(finalLocId)) {
                            selectedLocation = masterLocationList.get(i).getName();
                            spinnerLocation.setSelection(i);
                            break;
                        }
                    }
                }
                showWarning(totalScanCount + " items restored");
            });
        }).start();
    }

    private void fetchLocations() {
        if (!isNetworkConnected()) return;
        String token = "Bearer " + new PrefManager(this).getToken();
        ApiClient.getClient(this).create(ApiService.class)
                .getLocations(token)
                .enqueue(new Callback<List<LocationModel>>() {
                    @Override
                    public void onResponse(Call<List<LocationModel>> call,
                                           Response<List<LocationModel>> response) {
                        if (response.isSuccessful() && response.body() != null) {
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
                        }
                    }

                    @Override
                    public void onFailure(Call<List<LocationModel>> call, Throwable t) {}
                });
    }

    private void enqueueScan(String scannedData) {
        final String cleanData = scannedData.trim().replace("\r", "").replace("\n", "");

        for (ItemModel.Item t : scannedItemsList) {
            if (t.getEpcTag().equalsIgnoreCase(cleanData)) {
                playScanFeedback(1);
                showWarning("Already scanned");
                LogManager.get(this).log(LogManager.WARNING, LogManager.ACTION_SCAN, "Stock In", cleanData, "Duplicate scan: " + cleanData, new PrefManager(this).getUserId());
                return;
            }
        }
        if (selectedLocationId.isEmpty()) { showWarning("Select location first"); return; }

        addItemToList(new ItemModel.Item(cleanData, "", "Loading...", 1));
        playScanFeedback(0);
        LogManager.get(this).log(LogManager.INFO, LogManager.ACTION_SCAN, "Stock In", cleanData, "Scanned: " + cleanData, new PrefManager(this).getUserId());

        if (!isNetworkConnected()) {
            new Thread(() -> db.appDao().insertStockInScan(
                    buildEntity(cleanData, "", "Loading...", false))).start();
            showWarning("Saved offline");
            return;
        }

        String token = "Bearer " + new PrefManager(this).getToken();
        String type = switchRfid.isChecked() ? "RFID" : "QR";

        ApiClient.getClient(this).create(ApiService.class)
                .getTagByCode(token, cleanData, type)
                .enqueue(new Callback<TagModel.TagResponse>() {
                    @Override
                    public void onResponse(Call<TagModel.TagResponse> call, Response<TagModel.TagResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            TagModel.TagResponse tag = response.body();
                            new Thread(() -> db.appDao().insertStockInScan(
                                    buildEntity(cleanData, tag.getItemId(), tag.getItemName(), true))).start();
                            runOnUiThread(() -> updateItemInList(cleanData, tag.getItemId(), tag.getItemName()));
                        } else {
                            runOnUiThread(() -> {
                                removeItemFromList(cleanData);
                                playScanFeedback(2);
                                String errMsg = ErrorParser.getMessage(response);
                                LogManager.get(StockInActivity.this).log(LogManager.ERROR, LogManager.ACTION_SCAN, "Stock In", cleanData, "Scan rejected: " + errMsg, new PrefManager(StockInActivity.this).getUserId());
                                showError(errMsg);
                            });
                        }
                    }

                    @Override
                    public void onFailure(Call<TagModel.TagResponse> call, Throwable t) {
                        new Thread(() -> db.appDao().insertStockInScan(
                                buildEntity(cleanData, "", "Loading...", false))).start();
                        handler.post(() -> showWarning("Saved offline"));
                    }
                });
    }

    private StockInScanEntity buildEntity(String epc, String itemId, String itemName, boolean resolved) {
        StockInScanEntity e = new StockInScanEntity();
        e.epcTag = epc;
        e.itemId = itemId;
        e.itemName = itemName;
        e.scannerType = switchRfid.isChecked() ? "RFID" : "QR";
        e.locationId = selectedLocationId;
        e.isResolved = resolved;
        e.isSynced = false;
        e.createdAt = System.currentTimeMillis();
        return e;
    }

    private void saveToRoomThenSubmit() {
        showLoading();
        String type = switchRfid.isChecked() ? "RFID" : "QR";

        new Thread(() -> {
            List<StockInScanEntity> existing = db.appDao().getAllStockInScans();
            for (ItemModel.Item item : scannedItemsList) {
                boolean found = false;
                for (StockInScanEntity e : existing) {
                    if (e.epcTag.equalsIgnoreCase(item.getEpcTag())) { found = true; break; }
                }
                if (!found) db.appDao().insertStockInScan(buildEntity(
                        item.getEpcTag(), item.getItemId(), item.getItemName(),
                        !item.getItemName().equals("Loading...") && !item.getItemName().equals("Pending...")));
            }
            db.appDao().updateStockInLocation(selectedLocationId);
            db.appDao().updateStockInScannerType(type);

            handler.post(() -> {
                hideLoading();
                if (isNetworkConnected()) hitApiStockIn(type);
                else showWarning("Saved offline");
            });
        }).start();
    }

    private void hitApiStockIn(String scannerType) {
        showLoading();
        String token = "Bearer " + new PrefManager(this).getToken();

        List<String> codes = new ArrayList<>();
        for (ItemModel.Item item : scannedItemsList) codes.add(item.getEpcTag());

        ApiClient.getClient(this).create(ApiService.class)
                .stockIn(token, new StockInRequest(scannerType, codes, selectedLocationId))
                .enqueue(new Callback<GeneralResponse>() {
                    @Override
                    public void onResponse(Call<GeneralResponse> call, Response<GeneralResponse> response) {
                        hideLoading();
                        if (response.isSuccessful()) {
                            new Thread(() -> db.appDao().clearAllStockInScans()).start();
                            showSuccess(response.body().getMessage());
                            playScanFeedback(0);
                            LogManager.get(StockInActivity.this).log(LogManager.INFO, LogManager.ACTION_SUBMIT, "Stock In", "", "Stock In submitted: " + totalScanCount + " items", new PrefManager(StockInActivity.this).getUserId());
                            clearAllData();
                        } else {
                            handleApiErrorFriendly(response);
                            playScanFeedback(2);
                        }
                        resultScan.requestFocus();
                    }

                    @Override
                    public void onFailure(Call<GeneralResponse> call, Throwable t) {
                        hideLoading();
                        handleFailure(t);
                        playScanFeedback(2);
                        showWarning("Saved offline");
                    }
                });
    }

    private void addItemToList(ItemModel.Item item) {
        scannedItemsList.add(0, item);
        if (adapter != null) adapter.setLastScannedPosition(0);
        if (!isListProductTab) {
            buildSumProductList();
            if (sumAdapter != null) sumAdapter.updateData(sumProductList);
        } else {
            adapter.notifyItemInserted(0);
        }
        rvTags.scrollToPosition(0);
        totalScanCount++;
        updateScanCount();
        updateEmptyState();
    }

    private void updateItemInList(String epc, String itemId, String itemName) {
        for (int i = 0; i < scannedItemsList.size(); i++) {
            if (scannedItemsList.get(i).getEpcTag().equalsIgnoreCase(epc)) {
                scannedItemsList.get(i).setItemId(itemId);
                scannedItemsList.get(i).setItemName(itemName);
                adapter.notifyItemChanged(i);
                break;
            }
        }
    }

    private void removeItemFromList(String epc) {
        for (int i = 0; i < scannedItemsList.size(); i++) {
            if (scannedItemsList.get(i).getEpcTag().equalsIgnoreCase(epc)) {
                scannedItemsList.remove(i);
                totalScanCount--;
                if (isListProductTab) {
                    adapter.notifyItemRemoved(i);
                    adapter.notifyItemRangeChanged(i, scannedItemsList.size());
                } else {
                    buildSumProductList();
                    if (sumAdapter != null) sumAdapter.updateData(sumProductList);
                }
                updateScanCount();
                updateEmptyState();
                break;
            }
        }
    }

    private void clearAllData() {
        scannedItemsList.clear();
        sumProductList.clear();
        if (isListProductTab) adapter.notifyDataSetChanged();
        else if (sumAdapter != null) sumAdapter.updateData(sumProductList);
        totalScanCount = 0;
        updateScanCount();
        updateEmptyState();
    }

    private void updateScanCount() {
        tvScanned.setText("Scanned: " + totalScanCount);
    }

    private void buildSumProductList() {
        Map<String, ItemModel.SumProduct> map = new LinkedHashMap<>();
        for (ItemModel.Item item : scannedItemsList) {
            if (map.containsKey(item.getItemId()))
                map.get(item.getItemId()).addCount(1);
            else
                map.put(item.getItemId(),
                        new ItemModel.SumProduct(item.getItemId(), item.getItemName(), 1));
        }
        sumProductList = new ArrayList<>(map.values());
    }

    private void showSaveConfirmDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Confirm")
                .setMessage("Save " + totalScanCount + " items to warehouse?")
                .setCancelable(false)
                .setPositiveButton("Save", (d, w) -> saveToRoomThenSubmit())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDeleteItemDialog(ItemModel.Item item, int position) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_regist);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        ((TextView) dialog.findViewById(R.id.tvTitle)).setText("Remove " + item.getEpcTag() + "?");

        Button btnYes = dialog.findViewById(R.id.btnSave);
        btnYes.setText("Remove");
        btnYes.setBackgroundTintList(ColorStateList.valueOf(Color.RED));

        dialog.findViewById(R.id.btnNo).setOnClickListener(v -> dialog.dismiss());
        btnYes.setOnClickListener(v -> {
            dialog.dismiss();
            new Thread(() -> db.appDao().deleteStockInScanByEpc(item.getEpcTag())).start();
            scannedItemsList.remove(position);
            totalScanCount--;
            if (isListProductTab) {
                adapter.notifyItemRemoved(position);
                adapter.notifyItemRangeChanged(position, scannedItemsList.size());
            } else {
                buildSumProductList();
                if (sumAdapter != null) sumAdapter.updateData(sumProductList);
            }
            updateScanCount();
            resultScan.requestFocus();
        });
        dialog.show();
    }

    @Override
    public void onRFIDDataReceived(CommScanner scanner, RFIDDataReceivedEvent event) {
        for (RFIDData data : event.getRFIDData()) {
            String epc = RfidBulkHelper.bytesToHex(data.getUII());
            if (!epc.isEmpty()) handler.post(() -> enqueueScan(epc));
        }
    }

    @Override
    public void onBarcodeDataReceived(CommScanner scanner, BarcodeDataReceivedEvent event) {
        if (!event.getBarcodeData().isEmpty()) {
            String barcode = new String(event.getBarcodeData().get(0).getData());
            handler.post(() -> enqueueScan(barcode));
        }
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

    void updateEmptyState() {
        tvEmpty.setVisibility(scannedItemsList.isEmpty() ? View.VISIBLE : View.GONE);
    }
}
