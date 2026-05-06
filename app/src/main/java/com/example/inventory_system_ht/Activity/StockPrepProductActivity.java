package com.example.inventory_system_ht.Activity;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.Switch;
import android.widget.TextView;

import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
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
import com.example.inventory_system_ht.Adapter.SumProductPrepAdapter;
import com.example.inventory_system_ht.Adapter.TagAdapter;
import com.example.inventory_system_ht.Helper.ApiClient;
import com.example.inventory_system_ht.Helper.ApiService;
import com.example.inventory_system_ht.Helper.AppDao;
import com.example.inventory_system_ht.Helper.AppDatabase;
import com.example.inventory_system_ht.Helper.ErrorParser;
import com.example.inventory_system_ht.Helper.PrefManager;
import com.example.inventory_system_ht.Helper.RfidBulkHelper;
import com.example.inventory_system_ht.Helper.ScannerManager;
import com.example.inventory_system_ht.Helper.SyncWorker;
import com.example.inventory_system_ht.Models.DOModels;
import com.example.inventory_system_ht.Models.GeneralResponse;
import com.example.inventory_system_ht.Models.ItemModels;
import com.example.inventory_system_ht.Models.LocationModels;
import com.example.inventory_system_ht.Models.PendingSubmitEntity;
import com.example.inventory_system_ht.Models.StockPrepBulkRequest;
import com.example.inventory_system_ht.Models.TagModels;
import com.example.inventory_system_ht.R;
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
public class StockPrepProductActivity extends BaseScannerActivity
        implements BarcodeDataDelegate, RFIDDataDelegate {

    // ── Views ─────────────────────────────────────────────────────
    private EditText resultScan;
    private TextView tvScanned, tvNoDo, tvDateDo, tvPowerLevel, etLocation;
    private Switch switchRfid;
    private ImageView ivLocationArrow;
    private RecyclerView rvTags;
    private CardView btnLocationDropdown, btnPowerDropdown;
    private Button btnListProduct, btnSumProduct;

    // ── Adapters & Data ───────────────────────────────────────────
    private TagAdapter adapter;
    private SumProductPrepAdapter sumAdapter;
    private List<TagModels.TagModel> scannedList;
    private List<ItemModels.SumProductModel> sumProductList = new ArrayList<>();

    private final Map<String, Integer> requiredQtyMap = new HashMap<>();
    private final Map<String, String> itemNameMap = new HashMap<>();

    private final Set<String> scannedRawSet = new HashSet<>();
    private final Set<String> scannedEpcSet = new HashSet<>();
    private final Set<String> inFlightSet   = new HashSet<>();

    // ── State ─────────────────────────────────────────────────────
    private int scanCount = 0;
    private boolean isListProductTab = true;
    private String selectedLocation   = "";
    private String selectedLocationId = "";
    private String selectedPower      = "20 dBm";
    private PopupWindow activePopup   = null;
    private String currentDoId = "";
    private String currentDoNo = "";

    private ToneGenerator toneGen;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ApiService api;
    private String token;
    private AppDao appDao;

    private List<LocationModels.LocationModel> masterLocationList = new ArrayList<>();
    private final List<String> locationList = new ArrayList<>();
    private final List<String> powerList = new ArrayList<>(Arrays.asList(
            "10 dBm", "15 dBm", "20 dBm", "25 dBm", "27 dBm"
    ));

    // ── Scanner via ScannerManager ────────────────────────────────
    @Override
    protected CommScanner getScannerInstance() {
        return ScannerManager.getInstance().getScanner();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stock_prep_product);

        appDao = AppDatabase.getDatabase(this).appDao();
        token  = "Bearer " + new PrefManager(this).getToken();
        api    = ApiClient.getClient(this).create(ApiService.class);

        initUI();
        setupSwitchRfid();       // ← ganti setupScanner + setupPowerDropdown
        setupTabButtons();
        setupLocationDropdown();

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
    }

    // ── UI Init ───────────────────────────────────────────────────

    private void initUI() {
        try { toneGen = new ToneGenerator(AudioManager.STREAM_MUSIC, 100); } catch (Exception ignored) {}

        tvScanned          = findViewById(R.id.tvScanned);
        tvNoDo             = findViewById(R.id.tvNoDo);
        tvDateDo           = findViewById(R.id.tvDateDo);
        resultScan         = findViewById(R.id.resultScan);
        switchRfid         = findViewById(R.id.switchRfid);
        rvTags             = findViewById(R.id.rvTags);
        btnLocationDropdown= findViewById(R.id.btnLocationDropdown);
        btnPowerDropdown   = findViewById(R.id.btnPowerDropdown);
        tvPowerLevel       = findViewById(R.id.tvPowerLevel);
        btnListProduct     = findViewById(R.id.btnListProduct);
        btnSumProduct      = findViewById(R.id.btnSumProduct);
        etLocation         = findViewById(R.id.etLocation);
        ivLocationArrow    = findViewById(R.id.ivLocationArrow);

        btnPowerDropdown.setVisibility(View.GONE);
        switchRfid.setChecked(false);

        scannedList = new ArrayList<>();
        adapter = new TagAdapter(scannedList);
        rvTags.setLayoutManager(new LinearLayoutManager(this));
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

    // ── Switch RFID ───────────────────────────────────────────────

    private void setupSwitchRfid() {
        switchRfid.setOnCheckedChangeListener((btn, isChecked) -> {
            CommScanner scanner = getScannerInstance();

            updateReaderBattery(findViewById(R.id.ivReaderBattery), isChecked);

            if (isChecked) {
                if (scanner == null) {
                    showError("SP1 Reader not connected!");
                    switchRfid.setChecked(false);
                    updateReaderBattery(findViewById(R.id.ivReaderBattery), false);
                    return;
                }
                RfidBulkHelper.closeBarcode(scanner);
                int power = parsePower(tvPowerLevel.getText().toString(), 20);
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

        // Power dropdown popup
        btnPowerDropdown.setOnClickListener(v ->
                showPowerDropdownPopup(btnPowerDropdown, powerList, tvPowerLevel));
    }

    // ── Listeners ─────────────────────────────────────────────────

    private void setupListeners() {
        findViewById(R.id.btnBack).setOnClickListener(v -> confirmExit());

        resultScan.addTextChangedListener(new TextWatcher() {
            private boolean isProcessing = false;

            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (switchRfid.isChecked() || isProcessing) return;
                String data = s.toString().trim();
                if (data.length() < 8) return;

                isProcessing = true;
                resultScan.removeTextChangedListener(this);
                resultScan.setText("");
                resultScan.addTextChangedListener(this);
                processScan(data);
                isProcessing = false;
            }
        });

        resultScan.setOnEditorActionListener((v, actionId, event) -> true);
        resultScan.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                resultScan.postDelayed(() -> {
                    if (activePopup == null || !activePopup.isShowing())
                        resultScan.requestFocus();
                }, 150);
            }
        });

        findViewById(R.id.btnSave).setOnClickListener(v -> confirmSubmit());

        findViewById(R.id.btnClear).setOnClickListener(v -> {
            new Thread(() -> {
                for (TagModels.TagModel t : new ArrayList<>(scannedList)) {
                    try { appDao.deleteScannedTagByEpc(t.getEpcTag()); } catch (Exception ignored) {}
                }
                runOnUiThread(() -> {
                    scannedList.clear();
                    sumProductList.clear();
                    scannedRawSet.clear();
                    scannedEpcSet.clear();
                    inFlightSet.clear();
                    buildSumProductList();
                    if (isListProductTab) adapter.notifyDataSetChanged();
                    else if (sumAdapter != null) sumAdapter.updateData(sumProductList);
                    scanCount = 0;
                    tvScanned.setText("Scanned : 0");
                    showSuccess("List has been cleared");
                });
            }).start();
        });
    }

    // ── Location Dropdown ─────────────────────────────────────────

    private void setupLocationDropdown() {
        View.OnClickListener listener = v -> showDropdownPopup(btnLocationDropdown, locationList);
        btnLocationDropdown.setOnClickListener(listener);
        etLocation.setOnClickListener(listener);
        ivLocationArrow.setOnClickListener(listener);
    }

    private void showDropdownPopup(View anchor, List<String> items) {
        if (items.isEmpty()) {
            showWarning("Loading locations, please wait...");
            fetchLocations();
            return;
        }
        if (activePopup != null && activePopup.isShowing()) activePopup.dismiss();

        View popupView = getLayoutInflater().inflate(R.layout.dropdown_popup, null);
        RecyclerView rv = popupView.findViewById(R.id.rvDropdown);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setNestedScrollingEnabled(true);

        rv.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                View v = getLayoutInflater().inflate(R.layout.item_dropdown, parent, false);
                return new RecyclerView.ViewHolder(v) {};
            }

            @Override
            public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                ((TextView) holder.itemView.findViewById(R.id.tvDropdownItem))
                        .setText(items.get(position));
                holder.itemView.setOnClickListener(v -> {
                    selectedLocation   = masterLocationList.get(position).getName();
                    selectedLocationId = masterLocationList.get(position).getId();
                    etLocation.setText(selectedLocation);
                    if (activePopup != null) activePopup.dismiss();
                });
            }

            @Override public int getItemCount() { return items.size(); }
        });

        int maxHeight = (int) (56 * getResources().getDisplayMetrics().density) * 4;
        PopupWindow popup = new PopupWindow(
                popupView, anchor.getWidth(), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setElevation(16f);
        popup.setOutsideTouchable(true);
        popupView.measure(
                View.MeasureSpec.makeMeasureSpec(anchor.getWidth(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        popup.setHeight(Math.min(popupView.getMeasuredHeight(), maxHeight));
        popup.showAsDropDown(anchor, 0, 6);
        activePopup = popup;
    }

    // ── Tab Buttons ───────────────────────────────────────────────

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
                sumAdapter = new SumProductPrepAdapter(sumProductList);
                rvTags.setAdapter(sumAdapter);
            }
        });
    }

    private void setTabActive(boolean listActive) {
        btnListProduct.setBackgroundTintList(ColorStateList.valueOf(
                getColor(listActive ? R.color.blue_theme : R.color.white)));
        btnListProduct.setTextColor(getColor(listActive ? R.color.white : R.color.blue_theme));
        btnSumProduct.setBackgroundTintList(ColorStateList.valueOf(
                getColor(listActive ? R.color.white : R.color.blue_theme)));
        btnSumProduct.setTextColor(getColor(listActive ? R.color.blue_theme : R.color.white));
    }

    private void buildSumProductList() {
        Map<String, ItemModels.SumProductModel> map = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : requiredQtyMap.entrySet()) {
            String itemId = e.getKey();
            String name   = itemNameMap.containsKey(itemId) ? itemNameMap.get(itemId) : itemId;
            map.put(itemId, new ItemModels.SumProductModel(itemId, name, 0, e.getValue()));
        }
        for (TagModels.TagModel item : scannedList) {
            String itemId = item.getItmId();
            if (map.containsKey(itemId)) map.get(itemId).addCount(1);
            else map.put(itemId, new ItemModels.SumProductModel(itemId, item.getProductName(), 1, 0));
        }
        sumProductList.clear();
        sumProductList.addAll(map.values());
    }

    // ── Scan Logic ────────────────────────────────────────────────

    private void processScan(String scannedData) {
        if (selectedLocationId == null || selectedLocationId.isEmpty()) {
            if (!switchRfid.isChecked()) showWarning("Please select a location before scanning.");
            return;
        }

        boolean isRfid = switchRfid.isChecked();
        final String key = scannedData.toUpperCase();

        if (scannedRawSet.contains(key) || scannedEpcSet.contains(key)) {
            if (!isRfid) { playScanFeedback(1); showWarning("This item is already in the list."); }
            return;
        }

        scannedRawSet.add(key);

        final TagModels.TagModel placeholder = new TagModels.TagModel(
                scannedData, scannedData, "PENDING", "Validating...", currentDoNo, 0);
        scannedList.add(0, placeholder);
        if (adapter != null) { adapter.setLastScannedPosition(0); adapter.notifyItemInserted(0); }
        rvTags.scrollToPosition(0);
        scanCount++;
        tvScanned.setText("Scanned : " + scanCount);
        playScanFeedback(0);

        // OFFLINE
        if (!isNetworkConnected()) {
            new Thread(() -> {
                TagModels.TagCacheEntity cached = appDao.getTagCacheByKey(key);
                runOnUiThread(() -> {
                    if (cached != null) {
                        if (!requiredQtyMap.containsKey(cached.itemId)) {
                            rollbackScan(placeholder, key, "This item is not part of this delivery order.", isRfid);
                            return;
                        }
                        if (!"IN_STOCK".equals(cached.status)) {
                            rollbackScan(placeholder, key, "This item is not ready to be shipped yet.", isRfid);
                            return;
                        }
                        if (scannedEpcSet.contains(cached.epcTag.toUpperCase())) {
                            rollbackScan(placeholder, key, "This item is already in the list.", isRfid);
                            return;
                        }
                        int idx = scannedList.indexOf(placeholder);
                        if (idx != -1) {
                            TagModels.TagModel real = new TagModels.TagModel(
                                    cached.epcTag, cached.tagId, cached.itemId,
                                    cached.itemName, currentDoNo, 0);
                            scannedList.set(idx, real);
                            scannedEpcSet.add(cached.epcTag.toUpperCase());
                            buildSumProductList();
                            if (isListProductTab) adapter.notifyItemChanged(idx);
                            else if (sumAdapter != null) sumAdapter.updateData(sumProductList);
                            new Thread(() -> appDao.insertScannedTag(real)).start();
                        }
                        if (!isRfid) showSuccess("Offline: validated from local cache.");
                    } else {
                        new Thread(() -> appDao.insertScannedTag(placeholder)).start();
                        if (!isRfid) showWarning("Offline: item saved temporarily.");
                    }
                });
            }).start();
            return;
        }

        // ONLINE
        api.getTagInfo(token, scannedData).enqueue(new Callback<TagModels.TagInfoDto>() {
            @Override
            public void onResponse(Call<TagModels.TagInfoDto> call,
                                   Response<TagModels.TagInfoDto> response) {
                if (response.isSuccessful() && response.body() != null) {
                    TagModels.TagInfoDto info = response.body();

                    new Thread(() -> {
                        TagModels.TagCacheEntity cache = new TagModels.TagCacheEntity();
                        cache.epcTag   = info.getEpcTag();
                        cache.tagId    = info.getTagId();
                        cache.itemId   = info.getItemId();
                        cache.itemName = info.getItemName();
                        cache.status   = info.getStatus();
                        cache.cachedAt = System.currentTimeMillis();
                        appDao.insertTagCache(cache);
                    }).start();

                    if (!requiredQtyMap.containsKey(info.getItemId())) {
                        rollbackScan(placeholder, key, "This item is not part of this delivery order.", isRfid);
                        return;
                    }
                    if (!"IN_STOCK".equals(info.getStatus())) {
                        rollbackScan(placeholder, key, "This item is not ready to be shipped yet.", isRfid);
                        return;
                    }
                    if (scannedEpcSet.contains(info.getEpcTag().toUpperCase())) {
                        rollbackScan(placeholder, key, "This item is already in the list.", isRfid);
                        return;
                    }

                    int idx = scannedList.indexOf(placeholder);
                    if (idx != -1) {
                        TagModels.TagModel real = new TagModels.TagModel(
                                info.getEpcTag(), info.getTagId(), info.getItemId(),
                                info.getItemName(), currentDoNo, 0);
                        scannedList.set(idx, real);
                        scannedEpcSet.add(info.getEpcTag().toUpperCase());
                        buildSumProductList();
                        if (isListProductTab) adapter.notifyItemChanged(idx);
                        else if (sumAdapter != null) sumAdapter.updateData(sumProductList);
                        new Thread(() -> appDao.insertScannedTag(real)).start();
                    }
                } else {
                    rollbackScan(placeholder, key, null, isRfid);
                    if (!isRfid) { handleApiErrorFriendly(response); playScanFeedback(2); }
                }
            }

            @Override
            public void onFailure(Call<TagModels.TagInfoDto> call, Throwable t) {
                rollbackScan(placeholder, key, null, isRfid);
                if (!isRfid) { handleFailure(t); playScanFeedback(2); }
            }
        });
    }

    private void rollbackScan(TagModels.TagModel placeholder, String key,
                              String message, boolean isRfid) {
        int idx = scannedList.indexOf(placeholder);
        if (idx != -1) {
            scannedList.remove(idx);
            if (isListProductTab) adapter.notifyItemRemoved(idx);
            else { buildSumProductList(); if (sumAdapter != null) sumAdapter.updateData(sumProductList); }
        }
        scannedRawSet.remove(key);
        scanCount = Math.max(0, scanCount - 1);
        tvScanned.setText("Scanned : " + scanCount);
        if (message != null && !isRfid) { showError(message); playScanFeedback(2); }
    }

    // ── Submit ────────────────────────────────────────────────────

    private void confirmSubmit() {
        if (scannedList.isEmpty()) { showWarning("No items have been scanned yet."); return; }
        if (selectedLocationId == null || selectedLocationId.isEmpty()) {
            showWarning("Please select a destination location first."); return;
        }
        for (TagModels.TagModel t : scannedList) {
            if ("PENDING".equals(t.getItmId())) {
                showWarning("Some items are still being validated."); return;
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
                .setText("Save " + scannedList.size() + " item(s) to \"" + selectedLocation + "\"?");
        Button btnYes = dialog.findViewById(R.id.btnSave);
        btnYes.setText("Save");
        btnYes.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.green_button)));
        dialog.findViewById(R.id.btnNo).setOnClickListener(v -> dialog.dismiss());
        btnYes.setOnClickListener(v -> { dialog.dismiss(); submitToBackend(); });
        dialog.show();
    }

    private void submitToBackend() {
        final boolean isRfid      = switchRfid.isChecked();
        final String  scannerType = isRfid ? "RFID" : "QR";

        List<String> codes = new ArrayList<>();
        for (TagModels.TagModel t : scannedList) {
            String code = isRfid ? t.getEpcTag() : t.getTagId();
            if (code != null && !code.isEmpty()) codes.add(code);
        }
        if (codes.isEmpty()) { showWarning("No valid items to save."); return; }

        if (!isNetworkConnected()) {
            new Thread(() -> {
                PendingSubmitEntity pending = new PendingSubmitEntity();
                pending.doId         = currentDoId;
                pending.scannedCodes = new Gson().toJson(codes);
                pending.scannerType  = scannerType;
                pending.locId        = selectedLocationId;
                pending.createdAt    = System.currentTimeMillis();
                appDao.insertPendingSubmit(pending);

                WorkManager.getInstance(getApplicationContext()).enqueue(
                        new OneTimeWorkRequest.Builder(SyncWorker.class)
                                .setConstraints(new Constraints.Builder()
                                        .setRequiredNetworkType(NetworkType.CONNECTED).build())
                                .build());

                for (TagModels.TagModel t : new ArrayList<>(scannedList)) {
                    try { appDao.deleteScannedTagByEpc(t.getEpcTag()); } catch (Exception ignored) {}
                }

                runOnUiThread(() -> {
                    showSuccess("Offline: saved locally, will sync when connected.");
                    playScanFeedback(0);
                    clearScannedData();
                    finish();
                });
            }).start();
            return;
        }

        showLoading();
        api.submitStockPrep(token, new StockPrepBulkRequest(
                        currentDoId, codes, scannerType, selectedLocationId))
                .enqueue(new Callback<GeneralResponse>() {
                    @Override
                    public void onResponse(Call<GeneralResponse> call,
                                           Response<GeneralResponse> response) {
                        hideLoading();
                        if (response.isSuccessful()) {
                            new Thread(() -> {
                                for (TagModels.TagModel t : new ArrayList<>(scannedList)) {
                                    try { appDao.deleteScannedTagByEpc(t.getEpcTag()); } catch (Exception ignored) {}
                                }
                                runOnUiThread(() -> {
                                    showSuccess("Items saved successfully!");
                                    playScanFeedback(0);
                                    clearScannedData();
                                    finish();
                                });
                            }).start();
                        } else {
                            handleApiErrorFriendly(response);
                            playScanFeedback(2);
                        }
                    }

                    @Override
                    public void onFailure(Call<GeneralResponse> call, Throwable t) {
                        hideLoading(); handleFailure(t); playScanFeedback(2);
                    }
                });
    }

    private void clearScannedData() {
        scannedList.clear();
        sumProductList.clear();
        scannedRawSet.clear();
        scannedEpcSet.clear();
        inFlightSet.clear();
        buildSumProductList();
        adapter.notifyDataSetChanged();
        if (sumAdapter != null) sumAdapter.updateData(sumProductList);
        scanCount = 0;
        tvScanned.setText("Scanned : 0");
    }

    // ── Fetch Data ────────────────────────────────────────────────

    private void fetchDoDetail() {
        if (currentDoId == null || currentDoId.isEmpty() || !isNetworkConnected()) return;
        api.getPickingListById(token, currentDoId).enqueue(new Callback<DOModels.DOResponseDto>() {
            @Override
            public void onResponse(Call<DOModels.DOResponseDto> call,
                                   Response<DOModels.DOResponseDto> response) {
                if (response.isSuccessful() && response.body() != null) {
                    requiredQtyMap.clear();
                    itemNameMap.clear();
                    DOModels.DOResponseDto body = response.body();
                    if (body.getDetails() != null) {
                        for (DOModels.DODetailResponseDto d : body.getDetails()) {
                            requiredQtyMap.put(d.getItemId(), d.getQtyRequired());
                            itemNameMap.put(d.getItemId(), d.getItemName());
                        }
                    } else {
                        showWarning("This order doesn't have any items yet.");
                    }
                    runOnUiThread(() -> {
                        if (isListProductTab) adapter.notifyDataSetChanged();
                        else { buildSumProductList(); if (sumAdapter != null) sumAdapter.updateData(sumProductList); }
                    });
                } else {
                    handleApiErrorFriendly(response);
                }
            }

            @Override
            public void onFailure(Call<DOModels.DOResponseDto> call, Throwable t) { handleFailure(t); }
        });
    }

    private void fetchLocations() {
        if (!isNetworkConnected()) return;
        api.getLocations(token).enqueue(new Callback<List<LocationModels.LocationModel>>() {
            @Override
            public void onResponse(Call<List<LocationModels.LocationModel>> call,
                                   Response<List<LocationModels.LocationModel>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    masterLocationList = response.body();
                    locationList.clear();
                    for (LocationModels.LocationModel loc : masterLocationList)
                        locationList.add(loc.getName());
                }
            }
            @Override
            public void onFailure(Call<List<LocationModels.LocationModel>> call, Throwable t) {}
        });
    }

    private void restoreScannedTagsFromRoom() {
        new Thread(() -> {
            try {
                List<TagModels.TagModel> saved = appDao.getPendingTags();
                List<TagModels.TagModel> forThis = new ArrayList<>();
                for (TagModels.TagModel t : saved) {
                    if (currentDoNo != null && currentDoNo.equalsIgnoreCase(t.getDoIdRef()))
                        forThis.add(t);
                }
                runOnUiThread(() -> {
                    scannedList.clear(); scannedRawSet.clear(); scannedEpcSet.clear(); scanCount = 0;
                    for (TagModels.TagModel t : forThis) {
                        scannedList.add(t);
                        scannedEpcSet.add(t.getEpcTag().toUpperCase());
                        scannedRawSet.add(t.getEpcTag().toUpperCase());
                        scanCount++;
                    }
                    adapter.notifyDataSetChanged();
                    tvScanned.setText("Scanned : " + scanCount);
                    if (!forThis.isEmpty())
                        showWarning("Restored " + forThis.size() + " unsaved item(s).");
                });
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    // ── Dialog ────────────────────────────────────────────────────

    private void showDeleteItemDialog(TagModels.TagModel tag, int position) {
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
                .setText("Remove \"" + displayName + "\" from the list?");
        Button btnYes = dialog.findViewById(R.id.btnSave);
        btnYes.setText("Remove");
        btnYes.setBackgroundTintList(ColorStateList.valueOf(Color.RED));
        dialog.findViewById(R.id.btnNo).setOnClickListener(v -> dialog.dismiss());
        btnYes.setOnClickListener(v -> {
            dialog.dismiss();
            new Thread(() -> {
                try { appDao.deleteScannedTagByEpc(tag.getEpcTag()); } catch (Exception ignored) {}
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
                    showSuccess("Item removed");
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
                .setText("Leave? Scanned items are saved locally.");
        Button btnYes = dialog.findViewById(R.id.btnSave);
        btnYes.setText("Leave");
        btnYes.setBackgroundTintList(ColorStateList.valueOf(Color.RED));
        dialog.findViewById(R.id.btnNo).setOnClickListener(v -> dialog.dismiss());
        btnYes.setOnClickListener(v -> { dialog.dismiss(); finish(); });
        dialog.show();
    }

    // ── RFID / Barcode Callbacks ──────────────────────────────────

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

    // ── Lifecycle ─────────────────────────────────────────────────

    @Override
    protected void onResume() {
        super.onResume();
        CommScanner scanner = getScannerInstance();
        updateReaderBattery(findViewById(R.id.ivReaderBattery), switchRfid.isChecked());

        if (!switchRfid.isChecked() && scanner != null)
            RfidBulkHelper.openBarcode(scanner, this);

        if (getHTBatteryLevel() <= 15) {
            showWarning("HT Battery at " + getHTBatteryLevel() + "%, please charge now!");
            playScanFeedback(2);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (activePopup != null && activePopup.isShowing()) activePopup.dismiss();
        CommScanner scanner = getScannerInstance();
        RfidBulkHelper.closeInventory(scanner);
        RfidBulkHelper.closeBarcode(scanner);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (toneGen != null) { toneGen.release(); toneGen = null; }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        confirmExit();
    }

    // ── Helper ────────────────────────────────────────────────────

    private int parsePower(String text, int defaultVal) {
        try { return Integer.parseInt(text.replace(" dBm", "").trim()); }
        catch (NumberFormatException e) { return defaultVal; }
    }

    private void handleApiErrorFriendly(Response<?> response) {
        if (response.code() == 401) { handleApiError(response); return; }
        hideLoading();
        showError(humanizeError(ErrorParser.getMessage(response), response.code()));
    }

    private String humanizeError(String rawMsg, int code) {
        if (code == 403) return "You don't have permission to do this.";
        if (code == 404) return "Data not found. Please refresh and try again.";
        if (code >= 500) return "Server error. Please try again later.";
        if (rawMsg != null && !rawMsg.isEmpty()
                && !rawMsg.toLowerCase().contains("exception")
                && !rawMsg.toLowerCase().contains("null")
                && rawMsg.length() < 100) return rawMsg;
        return "Something went wrong. Please try again.";
    }

    private String formatToEnglishDate(String rawDate) {
        try {
            SimpleDateFormat in  = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            SimpleDateFormat out = new SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH);
            return out.format(in.parse(rawDate));
        } catch (Exception e) { return rawDate; }
    }
}