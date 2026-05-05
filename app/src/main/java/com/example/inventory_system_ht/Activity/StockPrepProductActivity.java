package com.example.inventory_system_ht.Activity;

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

public class StockPrepProductActivity extends BaseScannerActivity implements BarcodeDataDelegate, RFIDDataDelegate {

    private EditText resultScan;
    private TextView tvScanned, tvNoDo, tvDateDo, tvPowerLevel, etLocation;
    private Switch switchRfid;
    private ImageView ivLocationArrow;
    private RecyclerView rvTags;
    private CardView btnLocationDropdown, btnPowerDropdown;
    private Button btnListProduct, btnSumProduct;

    private TagAdapter adapter;
    private SumProductPrepAdapter sumAdapter;
    private List<TagModels.TagModel> scannedList;
    private List<ItemModels.SumProductModel> sumProductList = new ArrayList<>();

    private Map<String, Integer> requiredQtyMap = new HashMap<>();
    private Map<String, String> itemNameMap = new HashMap<>();

    private final Set<String> scannedRawSet = new HashSet<>();
    private final Set<String> scannedEpcSet = new HashSet<>();
    private final Set<String> inFlightSet = new HashSet<>();

    private int scanCount = 0;
    private boolean isListProductTab = true;
    private String selectedLocation = "";
    private String selectedLocationId = "";
    private String selectedPower = "20 dBm";
    private PopupWindow activePopup = null;
    private String currentDoId = "";
    private String currentDoNo = "";

    private CommScanner mCommScanner;
    private ToneGenerator toneGen;
    private Handler handler = new Handler(Looper.getMainLooper());
    private ApiService api;
    private String token;
    private AppDao appDao;

    private List<LocationModels.LocationModel> masterLocationList = new ArrayList<>();
    private List<String> locationList = new ArrayList<>();
    private final List<String> powerList = new ArrayList<>(Arrays.asList(
            "10 dBm", "15 dBm", "20 dBm", "25 dBm", "27 dBm"
    ));

    @Override
    protected CommScanner getScannerInstance() {
        return mCommScanner;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stock_prep_product);

        appDao = AppDatabase.getDatabase(this).appDao();
        PrefManager pref = new PrefManager(this);
        token = "Bearer " + pref.getToken();
        api = ApiClient.getClient(this).create(ApiService.class);

        initUI();
        setupScanner();
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
        cleanPendingForCurrentDO();
        setupListeners();
    }

    private void initUI() {
        try { toneGen = new ToneGenerator(AudioManager.STREAM_MUSIC, 100); } catch (Exception ignored) {}

        tvScanned = findViewById(R.id.tvScanned);
        tvNoDo = findViewById(R.id.tvNoDo);
        tvDateDo = findViewById(R.id.tvDateDo);
        resultScan = findViewById(R.id.resultScan);
        switchRfid = findViewById(R.id.switchRfid);
        rvTags = findViewById(R.id.rvTags);
        btnLocationDropdown = findViewById(R.id.btnLocationDropdown);
        btnPowerDropdown = findViewById(R.id.btnPowerDropdown);
        tvPowerLevel = findViewById(R.id.tvPowerLevel);
        btnListProduct = findViewById(R.id.btnListProduct);
        btnSumProduct = findViewById(R.id.btnSumProduct);
        etLocation = findViewById(R.id.etLocation);
        ivLocationArrow = findViewById(R.id.ivLocationArrow);

        btnPowerDropdown.setVisibility(View.GONE);

        scannedList = new ArrayList<>();
        adapter = new TagAdapter(scannedList);
        rvTags.setLayoutManager(new LinearLayoutManager(this));
        rvTags.setAdapter(adapter);

        if (rvTags.getItemAnimator() != null) {
            ((androidx.recyclerview.widget.SimpleItemAnimator) rvTags.getItemAnimator())
                    .setSupportsChangeAnimations(false);
            rvTags.getItemAnimator().setAddDuration(50);
            rvTags.getItemAnimator().setMoveDuration(50);
        }

        adapter.setOnItemClickListener(item -> {
            if (!isListProductTab) return;
            int position = scannedList.indexOf(item);
            if (position != -1) showDeleteItemDialog(item, position);
        });

        resultScan.setShowSoftInputOnFocus(false);
        resultScan.postDelayed(() -> resultScan.requestFocus(), 100);
    }

    private void setupLocationDropdown() {
        View.OnClickListener showDropdownListener = v ->
                showDropdownPopup(btnLocationDropdown, locationList, true);

        btnLocationDropdown.setOnClickListener(showDropdownListener);
        etLocation.setOnClickListener(showDropdownListener);
        ivLocationArrow.setOnClickListener(showDropdownListener);
    }

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
                    if (activePopup == null || !activePopup.isShowing()) {
                        resultScan.requestFocus();
                    }
                }, 150);
            }
        });

        switchRfid.setFocusable(false);
        switchRfid.setFocusableInTouchMode(false);
        setupPowerDropdown(btnPowerDropdown, switchRfid, tvPowerLevel);

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

                    if (isListProductTab) {
                        adapter.notifyDataSetChanged();
                    } else {
                        if (sumAdapter != null) sumAdapter.updateData(sumProductList);
                    }
                    scanCount = 0;
                    tvScanned.setText("Scanned : 0");
                    showSuccess("List has been cleared");
                });
            }).start();
        });
    }

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

    private void setTabActive(boolean listProductActive) {
        if (listProductActive) {
            btnListProduct.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.blue_theme)));
            btnListProduct.setTextColor(getColor(R.color.white));
            btnSumProduct.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.white)));
            btnSumProduct.setTextColor(getColor(R.color.blue_theme));
        } else {
            btnSumProduct.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.blue_theme)));
            btnSumProduct.setTextColor(getColor(R.color.white));
            btnListProduct.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.white)));
            btnListProduct.setTextColor(getColor(R.color.blue_theme));
        }
    }

    private void buildSumProductList() {
        Map<String, ItemModels.SumProductModel> map = new LinkedHashMap<>();

        for (Map.Entry<String, Integer> e : requiredQtyMap.entrySet()) {
            String itemId = e.getKey();
            int required = e.getValue();
            String name = itemNameMap.containsKey(itemId) ? itemNameMap.get(itemId) : itemId;
            map.put(itemId, new ItemModels.SumProductModel(itemId, name, 0, required));
        }

        for (TagModels.TagModel item : scannedList) {
            String itemId = item.getItmId();
            if (map.containsKey(itemId)) {
                map.get(itemId).addCount(1);
            } else {
                ItemModels.SumProductModel m = new ItemModels.SumProductModel(
                        itemId, item.getProductName(), 1, 0);
                map.put(itemId, m);
            }
        }

        sumProductList.clear();
        sumProductList.addAll(map.values());
    }

    private void fetchDoDetail() {
        if (currentDoId == null || currentDoId.isEmpty()) {
            showError("Delivery order ID is missing. Please go back and try again.");
            return;
        }
        if (!isNetworkConnected()) return;

        api.getPickingListById(token, currentDoId).enqueue(new Callback<DOModels.DOResponseDto>() {
            @Override
            public void onResponse(Call<DOModels.DOResponseDto> call, Response<DOModels.DOResponseDto> response) {
                if (response.isSuccessful() && response.body() != null) {
                    DOModels.DOResponseDto body = response.body();
                    requiredQtyMap.clear();
                    itemNameMap.clear();

                    if (body.getDetails() != null && !body.getDetails().isEmpty()) {
                        for (DOModels.DODetailResponseDto d : body.getDetails()) {
                            requiredQtyMap.put(d.getItemId(), d.getQtyRequired());
                            itemNameMap.put(d.getItemId(), d.getItemName());
                        }
                    } else {
                        showWarning("This order doesn't have any items yet.");
                    }

                    runOnUiThread(() -> {
                        if (isListProductTab) adapter.notifyDataSetChanged();
                        else {
                            buildSumProductList();
                            if (sumAdapter != null) sumAdapter.updateData(sumProductList);
                        }
                    });
                } else {
                    showError("Failed to load order details. Please try again.");
                    handleApiErrorFriendly(response);
                }
            }

            @Override
            public void onFailure(Call<DOModels.DOResponseDto> call, Throwable t) {
                showError("Could not reach the server. Please check your connection.");
                handleFailure(t);
            }
        });
    }

    private void fetchLocations() {
        if (!isNetworkConnected()) return;

        api.getLocations(token).enqueue(new Callback<List<LocationModels.LocationModel>>() {
            @Override
            public void onResponse(Call<List<LocationModels.LocationModel>> call, Response<List<LocationModels.LocationModel>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    masterLocationList = response.body();
                    locationList.clear();
                    for (LocationModels.LocationModel loc : masterLocationList) {
                        locationList.add(loc.getName());
                    }
                } else {
                    handleApiErrorFriendly(response);
                }
            }

            @Override
            public void onFailure(Call<List<LocationModels.LocationModel>> call, Throwable t) {
                handleFailure(t);
            }
        });
    }

    private void showDropdownPopup(View anchor, List<String> items, boolean isLocation) {
        if (items.isEmpty()) {
            if (isLocation) {
                showWarning("Loading locations, please wait...");
                fetchLocations();
            }
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
                TextView tv = holder.itemView.findViewById(R.id.tvDropdownItem);
                tv.setText(items.get(position));
                holder.itemView.setOnClickListener(v -> {
                    if (isLocation) {
                        selectedLocation = masterLocationList.get(position).getName();
                        selectedLocationId = masterLocationList.get(position).getId();
                        etLocation.setText(selectedLocation);
                    } else {
                        selectedPower = items.get(position);
                        tvPowerLevel.setText(selectedPower);
                    }
                    if (activePopup != null) activePopup.dismiss();
                });
            }

            @Override
            public int getItemCount() { return items.size(); }
        });

        int itemHeightPx = (int) (56 * getResources().getDisplayMetrics().density);
        int maxHeight = itemHeightPx * 4;

        PopupWindow popup = new PopupWindow(
                popupView,
                anchor.getWidth(),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
        );
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setElevation(16f);
        popup.setOutsideTouchable(true);

        popupView.measure(
                View.MeasureSpec.makeMeasureSpec(anchor.getWidth(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );
        popup.setHeight(Math.min(popupView.getMeasuredHeight(), maxHeight));
        popup.showAsDropDown(anchor, 0, 6);
        activePopup = popup;
    }

    private void processScan(String scannedData) {
        if (selectedLocationId == null || selectedLocationId.isEmpty()) {
            if (!switchRfid.isChecked()) showWarning("Please select a location before scanning.");
            return;
        }

        boolean isRfid = switchRfid.isChecked();
        final String key = scannedData.toUpperCase();

        if (scannedRawSet.contains(key) || scannedEpcSet.contains(key)) {
            if (!isRfid) {
                playScanFeedback(1);
                showWarning("This item is already in the list.");
            }
            return;
        }

        scannedRawSet.add(key);

        final TagModels.TagModel placeholder = new TagModels.TagModel(
                scannedData, scannedData, "PENDING", "Validating...", currentDoNo, 0
        );
        scannedList.add(0, placeholder);
        if (adapter != null) {
            adapter.setLastScannedPosition(0);
            adapter.notifyItemInserted(0);
        }
        rvTags.scrollToPosition(0);
        scanCount++;
        tvScanned.setText("Scanned : " + scanCount);
        playScanFeedback(0);

        // OFFLINE: cari di cache lokal
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
                        if (!isRfid) showWarning("Offline: item saved temporarily, validate when online.");
                    }
                });
            }).start();
            return;
        }

        // ONLINE: call API
        api.getTagInfo(token, scannedData).enqueue(new Callback<TagModels.TagInfoDto>() {
            @Override
            public void onResponse(Call<TagModels.TagInfoDto> call, Response<TagModels.TagInfoDto> response) {
                if (response.isSuccessful() && response.body() != null) {
                    TagModels.TagInfoDto info = response.body();

                    new Thread(() -> {
                        TagModels.TagCacheEntity cache = new TagModels.TagCacheEntity();
                        cache.epcTag    = info.getEpcTag();
                        cache.tagId     = info.getTagId();
                        cache.itemId    = info.getItemId();
                        cache.itemName  = info.getItemName();
                        cache.status    = info.getStatus();
                        cache.cachedAt  = System.currentTimeMillis();
                        appDao.insertTagCache(cache);
                    }).start();

                    if (!requiredQtyMap.containsKey(info.getItemId())) {
                        rollbackScan(placeholder, key, "This item is not part of this delivery order.", isRfid);
                        return;
                    }
                    if (!"IN_STOCK".equals(info.getStatus())) {
                        rollbackScan(placeholder, key, "This item is not ready to be shipped yet. Please check its status.", isRfid);
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

    private void submitToBackend() {
        final boolean isRfid = switchRfid.isChecked();
        final String scannerType = isRfid ? "RFID" : "QR";

        List<String> codes = new ArrayList<>();
        for (TagModels.TagModel t : scannedList) {
            String code = isRfid ? t.getEpcTag() : t.getTagId();
            if (code != null && !code.isEmpty()) codes.add(code);
        }

        if (codes.isEmpty()) {
            showWarning("No valid items to save.");
            return;
        }

        // OFFLINE: simpan ke pending submit, sync nanti pakai WorkManager
        if (!isNetworkConnected()) {
            new Thread(() -> {
                PendingSubmitEntity pending = new PendingSubmitEntity();
                pending.doId         = currentDoId;
                pending.scannedCodes = new Gson().toJson(codes);
                pending.scannerType  = scannerType;
                pending.locId        = selectedLocationId;
                pending.createdAt    = System.currentTimeMillis();
                appDao.insertPendingSubmit(pending);

                Constraints constraints = new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build();

                OneTimeWorkRequest syncRequest = new OneTimeWorkRequest.Builder(SyncWorker.class)
                        .setConstraints(constraints)
                        .build();

                WorkManager.getInstance(getApplicationContext()).enqueue(syncRequest);

                for (TagModels.TagModel t : new ArrayList<>(scannedList)) {
                    try { appDao.deleteScannedTagByEpc(t.getEpcTag()); } catch (Exception ignored) {}
                }

                runOnUiThread(() -> {
                    showSuccess("Offline: Data saved locally, will sync when connected.");
                    playScanFeedback(0);
                    scannedList.clear();
                    sumProductList.clear();
                    scannedRawSet.clear();
                    scannedEpcSet.clear();
                    buildSumProductList();
                    adapter.notifyDataSetChanged();
                    if (sumAdapter != null) sumAdapter.updateData(sumProductList);
                    scanCount = 0;
                    tvScanned.setText("Scanned : 0");
                    finish();
                });
            }).start();
            return;
        }

        // ONLINE: kirim langsung
        showLoading();
        StockPrepBulkRequest request = new StockPrepBulkRequest(
                currentDoId, codes, scannerType, selectedLocationId);

        api.submitStockPrep(token, request).enqueue(new Callback<GeneralResponse>() {
            @Override
            public void onResponse(Call<GeneralResponse> call, Response<GeneralResponse> response) {
                hideLoading();
                if (response.isSuccessful()) {
                    new Thread(() -> {
                        for (TagModels.TagModel t : new ArrayList<>(scannedList)) {
                            try { appDao.deleteScannedTagByEpc(t.getEpcTag()); } catch (Exception ignored) {}
                        }
                        runOnUiThread(() -> {
                            showSuccess("Items saved successfully!");
                            playScanFeedback(0);
                            scannedList.clear();
                            sumProductList.clear();
                            scannedRawSet.clear();
                            scannedEpcSet.clear();
                            buildSumProductList();
                            adapter.notifyDataSetChanged();
                            if (sumAdapter != null) sumAdapter.updateData(sumProductList);
                            scanCount = 0;
                            tvScanned.setText("Scanned : 0");
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
                hideLoading();
                handleFailure(t);
                playScanFeedback(2);
            }
        });
    }

    private void rollbackScan(TagModels.TagModel placeholder, String key, String message, boolean isRfid) {
        int idx = scannedList.indexOf(placeholder);
        if (idx != -1) {
            scannedList.remove(idx);
            if (isListProductTab) adapter.notifyItemRemoved(idx);
            else {
                buildSumProductList();
                if (sumAdapter != null) sumAdapter.updateData(sumProductList);
            }
        }
        scannedRawSet.remove(key);
        scanCount = Math.max(0, scanCount - 1);
        tvScanned.setText("Scanned : " + scanCount);
        if (message != null && !isRfid) {
            showError(message);
            playScanFeedback(2);
        }
    }

    private void showDeleteItemDialog(TagModels.TagModel tag, int position) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_regist);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        TextView tvTitle = dialog.findViewById(R.id.tvTitle);
        String displayName = (tag.getProductName() != null
                && !tag.getProductName().isEmpty()
                && !"Validating...".equals(tag.getProductName()))
                ? tag.getProductName() : "this item";
        tvTitle.setText("Are you sure you want to remove \"" + displayName + "\" from the list?");

        Button btnYes = dialog.findViewById(R.id.btnSave);
        btnYes.setText("Remove");
        btnYes.setBackgroundTintList(ColorStateList.valueOf(Color.RED));

        dialog.findViewById(R.id.btnNo).setOnClickListener(v -> dialog.dismiss());
        btnYes.setOnClickListener(v -> {
            dialog.dismiss();
            if (position < 0 || position >= scannedList.size()) return;
            final TagModels.TagModel removed = scannedList.remove(position);
            scanCount--;
            scannedEpcSet.remove(removed.getEpcTag().toUpperCase());
            scannedRawSet.remove(removed.getEpcTag().toUpperCase());

            new Thread(() -> {
                try { appDao.deleteScannedTagByEpc(removed.getEpcTag()); } catch (Exception ignored) {}
            }).start();

            if (isListProductTab) {
                adapter.notifyItemRemoved(position);
                adapter.notifyItemRangeChanged(position, scannedList.size());
            } else {
                buildSumProductList();
                if (sumAdapter != null) sumAdapter.updateData(sumProductList);
            }

            tvScanned.setText("Scanned : " + scanCount);
            showSuccess("Item removed from list");
            resultScan.requestFocus();
        });
        dialog.show();
    }

    private void confirmSubmit() {
        if (scannedList.isEmpty()) {
            showWarning("No items have been scanned yet.");
            return;
        }
        if (selectedLocationId == null || selectedLocationId.isEmpty()) {
            showWarning("Please select a destination location first.");
            return;
        }
        for (TagModels.TagModel t : scannedList) {
            if ("PENDING".equals(t.getItmId())) {
                showWarning("Please wait, some items are still being validated.");
                return;
            }
        }

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_regist);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        TextView tvTitle = dialog.findViewById(R.id.tvTitle);
        tvTitle.setText("You're about to save " + scannedList.size() + " item(s) to \""
                + selectedLocation + "\". Confirm?");

        Button btnYes = dialog.findViewById(R.id.btnSave);
        btnYes.setText("Save");
        btnYes.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.green_button)));

        dialog.findViewById(R.id.btnNo).setOnClickListener(v -> dialog.dismiss());
        btnYes.setOnClickListener(v -> {
            dialog.dismiss();
            submitToBackend();
        });
        dialog.show();
    }

    private void cleanPendingForCurrentDO() {
        new Thread(() -> {
            try {
                List<TagModels.TagModel> pending = appDao.getPendingTags();
                for (TagModels.TagModel t : pending) {
                    if (currentDoNo != null && currentDoNo.equalsIgnoreCase(t.getDoIdRef())) {
                        appDao.deleteScannedTagByEpc(t.getEpcTag());
                    }
                }
            } catch (Exception ignored) {}
            runOnUiThread(() -> {
                scannedList.clear();
                scannedRawSet.clear();
                scannedEpcSet.clear();
                scanCount = 0;
                adapter.notifyDataSetChanged();
                tvScanned.setText("Scanned : 0");
            });
        }).start();
    }

    private void handleApiErrorFriendly(Response<?> response) {
        if (response.code() == 401) {
            handleApiError(response);
            return;
        }
        hideLoading();
        String rawMsg = ErrorParser.getMessage(response);
        showError(humanizeError(rawMsg, response.code()));
    }

    private String humanizeError(String rawMsg, int statusCode) {
        if (rawMsg == null) rawMsg = "";

        if (statusCode == 403) return "You don't have permission to do this. Please contact admin.";
        if (statusCode == 404) return "Data not found. Please refresh and try again.";
        if (statusCode >= 500) return "The server is having a problem. Please try again in a few minutes.";

        if (!rawMsg.isEmpty()
                && !rawMsg.toLowerCase().contains("exception")
                && !rawMsg.toLowerCase().contains("null")
                && rawMsg.length() < 100) {
            return rawMsg;
        }
        return "Something went wrong. Please try again.";
    }

    @Override
    public void onRFIDDataReceived(CommScanner scanner, RFIDDataReceivedEvent event) {
        if (!switchRfid.isChecked()) return;
        for (RFIDData data : event.getRFIDData()) {
            String epc = bytesToHexString(data.getUII());
            handler.post(() -> processScan(epc));
        }
    }

    @Override
    public void onBarcodeDataReceived(CommScanner scanner, BarcodeDataReceivedEvent event) {
        if (switchRfid.isChecked()) return;
        List<BarcodeData> dataList = event.getBarcodeData();
        if (!dataList.isEmpty()) {
            String barcode = new String(dataList.get(0).getData());
            handler.post(() -> processScan(barcode));
        }
    }

    private String bytesToHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02X", b));
        return sb.toString();
    }

    private void setupScanner() {
        if (mCommScanner != null) {
            try {
                mCommScanner.getRFIDScanner().setDataDelegate(this);
                mCommScanner.getBarcodeScanner().setDataDelegate(this);
            } catch (Exception ignored) {}
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupScanner();
        updateReaderBattery(findViewById(R.id.ivReaderBattery));
        updateReaderBattery(findViewById(R.id.ivReaderBattery));
        if (getHTBatteryLevel() <= 15) {
            showWarning("HT Battery at " + getHTBatteryLevel() + "%, please charge now!");
            playScanFeedback(2);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (activePopup != null && activePopup.isShowing()) activePopup.dismiss();
        if (mCommScanner != null) {
            try {
                if (mCommScanner.getRFIDScanner() != null)
                    mCommScanner.getRFIDScanner().setDataDelegate(null);
                if (mCommScanner.getBarcodeScanner() != null)
                    mCommScanner.getBarcodeScanner().setDataDelegate(null);
            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (toneGen != null) { toneGen.release(); toneGen = null; }
    }

    private String formatToEnglishDate(String rawDate) {
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            SimpleDateFormat outputFormat = new SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH);
            java.util.Date date = inputFormat.parse(rawDate);
            return outputFormat.format(date);
        } catch (Exception e) {
            return rawDate;
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        confirmExit();
    }

    private void confirmExit() {
        if (scannedList.isEmpty()) {
            finish();
            return;
        }

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_regist);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        TextView tvTitle = dialog.findViewById(R.id.tvTitle);
        tvTitle.setText("Are you sure you want to leave? All scanned items will be lost.");

        Button btnYes = dialog.findViewById(R.id.btnSave);
        btnYes.setText("Leave");
        btnYes.setBackgroundTintList(ColorStateList.valueOf(Color.RED));

        dialog.findViewById(R.id.btnNo).setOnClickListener(v -> dialog.dismiss());
        btnYes.setOnClickListener(v -> {
            dialog.dismiss();
            new Thread(() -> {
                for (TagModels.TagModel t : new ArrayList<>(scannedList)) {
                    try { appDao.deleteScannedTagByEpc(t.getEpcTag()); } catch (Exception ignored) {}
                }
                runOnUiThread(() -> {
                    scannedList.clear();
                    scannedRawSet.clear();
                    scannedEpcSet.clear();
                    inFlightSet.clear();
                    scanCount = 0;
                    finish();
                });
            }).start();
        });
        dialog.show();
    }
}