package com.example.inventory_system_ht.activity;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

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
import com.example.inventory_system_ht.adapter.SearchItemAdapter;
import com.example.inventory_system_ht.database.AppDatabase;
import com.example.inventory_system_ht.entity.SearchItemEntity;
import com.example.inventory_system_ht.model.TagModel;
import com.example.inventory_system_ht.network.ApiClient;
import com.example.inventory_system_ht.network.ApiService;
import com.example.inventory_system_ht.util.LogManager;
import com.example.inventory_system_ht.util.PrefManager;
import com.example.inventory_system_ht.util.RfidBulkHelper;
import com.example.inventory_system_ht.util.RfidSettingsManager;
import com.example.inventory_system_ht.util.ScannerManager;
import com.example.inventory_system_ht.R;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SearchItemActivity extends ScannerActivity
        implements BarcodeDataDelegate, RFIDDataDelegate {
    private EditText etSearchItem;
    private RecyclerView rvTags;
    private View tvEmpty;
    private Spinner spinnerStatus;
    private Spinner spinnerWarehouse;
    private SearchItemAdapter adapter;
    private List<TagModel.SearchItemDto> allItemList;
    private List<TagModel.SearchItemDto> filteredList;
    private String selectedStatus = "All";
    private String selectedWarehouse = "All";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ApiService api;
    private String token;
    private AppDatabase db;
    private boolean rfidSearchActive = false;
    private com.google.android.material.floatingactionbutton.FloatingActionButton fabRfid;
    private com.google.android.material.card.MaterialCardView cardFabRfid;

    @Override
    protected CommScanner getScannerInstance() {
        return ScannerManager.getInstance().getScanner();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search_item);
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

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.btnRefresh), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            ViewGroup.MarginLayoutParams p = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            int dp20 = (int)(20 * getResources().getDisplayMetrics().density);
            p.bottomMargin = bars.bottom + dp20;
            p.rightMargin = (int)(20 * getResources().getDisplayMetrics().density);
            v.setLayoutParams(p);
            return insets;
        });

        token = "Bearer " + new PrefManager(this).getToken();
        api = ApiClient.getClient(this).create(ApiService.class);
        db = AppDatabase.getDatabase(this);

        initViews();
        setupListeners();

        loadFromLocal();
        if (isNetworkConnected()) fetchData();
        else showWarning("Offline mode");

        FloatingActionButton fabLog = findViewById(R.id.fabLog);
        if (fabLog != null) {
            fabLog.setOnClickListener(v -> {
                Intent i = new Intent(this, LogActivity.class);
                i.putExtra(LogActivity.EXTRA_MENU, "Search Item");
                startActivity(i);
            });
        }
        LogManager.get(this).log(LogManager.INFO, LogManager.ACTION_OPEN, "Search Item", "", "Opened Search Item", new PrefManager(this).getUserId());
    }

    @Override
    protected void onResume() {
        super.onResume();
        CommScanner scanner = getScannerInstance();
        updateReaderBattery(findViewById(R.id.ivReaderBattery));
        if (rfidSearchActive) {
            int power = new RfidSettingsManager(this).getPower();
            RfidBulkHelper.openInventory(scanner, this, power);
        } else {
            if (scanner != null) RfidBulkHelper.openBarcode(scanner, this);
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
        if (rfidSearchActive) {
            RfidBulkHelper.closeInventory(getScannerInstance());
        } else {
            RfidBulkHelper.closeBarcode(getScannerInstance());
        }
    }

    private void initViews() {
        etSearchItem = findViewById(R.id.searchItem);
        rvTags = findViewById(R.id.rvTags);
        tvEmpty = findViewById(R.id.tvEmpty);
        spinnerStatus = findViewById(R.id.spinnerStatus);
        spinnerWarehouse = findViewById(R.id.spinnerWarehouse);
        fabRfid = findViewById(R.id.fabRfid);
        cardFabRfid = findViewById(R.id.cardFabRfid);

        allItemList = new ArrayList<>();
        filteredList = new ArrayList<>();

        adapter = new SearchItemAdapter(filteredList);
        rvTags.setLayoutManager(new LinearLayoutManager(this));
        rvTags.setAdapter(adapter);

        populateFilters();
    }

    private void setRfidSearchActive(boolean active) {
        rfidSearchActive = active;
        CommScanner scanner = getScannerInstance();
        if (active) {
            RfidBulkHelper.closeBarcode(scanner);
            int power = new RfidSettingsManager(this).getPower();
            RfidBulkHelper.openInventory(scanner, this, power);
            if (cardFabRfid != null) cardFabRfid.setStrokeColor(getColor(android.R.color.holo_green_dark));
            if (fabRfid != null) fabRfid.setImageTintList(android.content.res.ColorStateList.valueOf(getColor(android.R.color.holo_green_dark)));
        } else {
            RfidBulkHelper.closeInventory(scanner);
            RfidBulkHelper.openBarcode(scanner, this);
            if (cardFabRfid != null) cardFabRfid.setStrokeColor(getColor(R.color.blue_theme));
            if (fabRfid != null) fabRfid.setImageTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.blue_theme)));
        }
    }

    private void updateEmptyState() {
        if (tvEmpty != null)
            tvEmpty.setVisibility(filteredList.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void setupListeners() {
        etSearchItem.setOnFocusChangeListener((v, hasFocus) -> {
            findViewById(R.id.cardSearch).setSelected(hasFocus);
        });

        etSearchItem.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { filter(s.toString()); }
        });

        AdapterView.OnItemSelectedListener filterListener = new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                if (parent.getId() == R.id.spinnerStatus)
                    selectedStatus = (String) parent.getItemAtPosition(pos);
                else
                    selectedWarehouse = (String) parent.getItemAtPosition(pos);
                filter(etSearchItem.getText().toString());
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        };
        spinnerStatus.setOnItemSelectedListener(filterListener);
        spinnerWarehouse.setOnItemSelectedListener(filterListener);

        adapter.setOnItemClickListener(this::fetchAndShowDetail);

        if (fabRfid != null) {
            fabRfid.setOnClickListener(v -> setRfidSearchActive(!rfidSearchActive));
        }

        findViewById(R.id.btnRefresh).setOnClickListener(v -> {
            if (!isNetworkConnected()) { showWarning("No internet connection"); return; }
            fetchData();
        });

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    private void loadFromLocal() {
        List<SearchItemEntity> cached = db.appDao().getAllSearchItems();
        allItemList.clear();
        for (SearchItemEntity e : cached) {
            TagModel.SearchItemDto dto = new TagModel.SearchItemDto();
            dto.setTagId(e.tagId);
            dto.setEpcTag(e.epcTag);
            dto.setItemName(e.itemName);
            dto.setLocation(e.location);
            dto.setStatus(e.status);
            allItemList.add(dto);
        }
        populateFilters();
        filter(etSearchItem != null ? etSearchItem.getText().toString() : "");
    }

    private void saveToLocal(List<TagModel.SearchItemDto> items) {
        new Thread(() -> {
            db.appDao().deleteAllSearchItems();
            List<SearchItemEntity> entities = new ArrayList<>();
            for (TagModel.SearchItemDto dto : items) {
                SearchItemEntity e = new SearchItemEntity();
                e.tagId = dto.getTagId();
                e.epcTag = dto.getEpcTag();
                e.itemName = dto.getItemName();
                e.location = dto.getLocation();
                e.status = dto.getStatus();
                entities.add(e);
            }
            db.appDao().insertSearchItems(entities);
        }).start();
    }

    private void populateFilters() {
        Set<String> statuses = new LinkedHashSet<>();
        Set<String> warehouses = new LinkedHashSet<>();
        statuses.add("All");
        warehouses.add("All");
        for (TagModel.SearchItemDto item : allItemList) {
            if (item.getStatus() != null && !item.getStatus().isEmpty())
                statuses.add(item.getStatus());
            if (item.getLocation() != null && !item.getLocation().isEmpty()
                    && !"-".equals(item.getLocation()))
                warehouses.add(item.getLocation());
        }
        ArrayAdapter<String> statusAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, new ArrayList<>(statuses));
        statusAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerStatus.setAdapter(statusAdapter);

        ArrayAdapter<String> warehouseAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, new ArrayList<>(warehouses));
        warehouseAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerWarehouse.setAdapter(warehouseAdapter);
    }

    private void fetchData() {
        showLoading();
        String userId = new PrefManager(this).getUserId();
        String reqJson = "{\"endpoint\":\"getSearchItems\"}";
        api.getSearchItems(token).enqueue(new Callback<List<TagModel.SearchItemDto>>() {
            @Override
            public void onResponse(Call<List<TagModel.SearchItemDto>> call,
                                   Response<List<TagModel.SearchItemDto>> response) {
                hideLoading();
                String resJson = "{\"http_code\":" + response.code() + ",\"count\":"
                        + (response.body() != null ? response.body().size() : 0) + "}";
                if (response.isSuccessful() && response.body() != null) {
                    List<TagModel.SearchItemDto> data = response.body();
                    LogManager.get(SearchItemActivity.this).log(LogManager.INFO, LogManager.ACTION_READ,
                            "Search Item", "Item List", "Fetch items success: " + data.size() + " items",
                            userId, reqJson, resJson);
                    saveToLocal(data);
                    allItemList.clear();
                    allItemList.addAll(data);
                    populateFilters();
                    filter(etSearchItem.getText().toString());
                } else {
                    LogManager.get(SearchItemActivity.this).log(LogManager.WARNING, LogManager.ACTION_READ,
                            "Search Item", "Item List", "Fetch items failed: HTTP " + response.code(),
                            userId, reqJson, resJson);
                    handleApiError(response);
                }
            }

            @Override
            public void onFailure(Call<List<TagModel.SearchItemDto>> call, Throwable t) {
                hideLoading();
                String resJson = "{\"error\":\"" + t.getMessage() + "\"}";
                LogManager.get(SearchItemActivity.this).log(LogManager.ERROR, LogManager.ACTION_READ,
                        "Search Item", "Item List", "Fetch items error: " + t.getMessage(),
                        userId, reqJson, resJson);
                handleFailure(t);
            }
        });
    }

    private void fetchAndShowDetail(TagModel.SearchItemDto item) {
        if (!isNetworkConnected()) { showWarning("Offline, cannot view detail"); return; }
        showLoading();
        String userId = new PrefManager(this).getUserId();
        String reqJson = "{\"tagId\":\"" + item.getTagId() + "\"}";
        api.getTagDetailSearchItem(token, item.getTagId())
                .enqueue(new Callback<TagModel.TagDetailDto>() {
                    @Override
                    public void onResponse(Call<TagModel.TagDetailDto> call,
                                           Response<TagModel.TagDetailDto> response) {
                        hideLoading();
                        String resJson = "{\"http_code\":" + response.code() + ",\"found\":"
                                + (response.body() != null) + "}";
                        if (response.isSuccessful() && response.body() != null) {
                            LogManager.get(SearchItemActivity.this).log(LogManager.INFO, LogManager.ACTION_READ,
                                    "Search Item", item.getTagId(), "Fetch tag detail success",
                                    userId, reqJson, resJson);
                            handler.post(() -> showTagDetailDialog(item, response.body()));
                        } else {
                            LogManager.get(SearchItemActivity.this).log(LogManager.WARNING, LogManager.ACTION_READ,
                                    "Search Item", item.getTagId(), "Fetch tag detail failed: HTTP " + response.code(),
                                    userId, reqJson, resJson);
                            showError("Tag not found");
                        }
                    }

                    @Override
                    public void onFailure(Call<TagModel.TagDetailDto> call, Throwable t) {
                        hideLoading();
                        String resJson = "{\"error\":\"" + t.getMessage() + "\"}";
                        LogManager.get(SearchItemActivity.this).log(LogManager.ERROR, LogManager.ACTION_READ,
                                "Search Item", item.getTagId(), "Fetch tag detail error: " + t.getMessage(),
                                userId, reqJson, resJson);
                        handleFailure(t);
                    }
                });
    }

    private void showTagDetailDialog(TagModel.SearchItemDto selectedItem,
                                     TagModel.TagDetailDto detail) {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_tag_detail);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.9),
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        View view = dialog.getWindow().getDecorView();
        ((TextView) view.findViewById(R.id.tvDetailItemName)).setText(detail.getItemName());
        ((TextView) view.findViewById(R.id.tvDetailTagId)).setText(detail.getTagId());
        ((TextView) view.findViewById(R.id.tvDetailEpc)).setText(detail.getEpcTag());
        ((TextView) view.findViewById(R.id.tvDetailLocation)).setText(detail.getLocation());

        TextView tvStatus = view.findViewById(R.id.tvDetailStatus);
        CardView cvStatus = view.findViewById(R.id.cvDetailStatus);
        tvStatus.setText(detail.getStatus());
        cvStatus.setCardBackgroundColor(statusColor(detail.getStatus()));

        view.findViewById(R.id.btnSearchSignal).setOnClickListener(v -> {
            if (getScannerInstance() == null || getScannerInstance().getRFIDScanner() == null) {
                dialog.dismiss();
                showError("RFID not connected");
                playScanFeedback(2);
                return;
            }
            dialog.dismiss();
            Intent intent = new Intent(this, SearchSignalActivity.class);
            intent.putExtra("SELECTED_ITEM", selectedItem);
            intent.putExtra("SELECTED_DETAIL", detail);
            startActivity(intent);
        });

        dialog.show();
    }

    @Override
    public void onRFIDDataReceived(CommScanner scanner, RFIDDataReceivedEvent event) {
        for (RFIDData data : event.getRFIDData()) {
            String epc = RfidBulkHelper.bytesToHex(data.getUII());
            handler.post(() -> {
                etSearchItem.setText(epc);
                moveScannedItemToTop(epc);
            });
        }
    }

    @Override
    public void onBarcodeDataReceived(CommScanner scanner, BarcodeDataReceivedEvent event) {
        List<BarcodeData> dataList = event.getBarcodeData();
        if (!dataList.isEmpty()) {
            String barcode = new String(dataList.get(0).getData()).trim();
            handler.post(() -> {
                etSearchItem.setText(barcode);
                etSearchItem.setSelection(etSearchItem.getText().length());
                moveScannedItemToTop(barcode);
            });
        }
    }

    private void filter(String text) {
        filteredList.clear();
        String query = text.toLowerCase().trim();
        for (TagModel.SearchItemDto item : allItemList) {
            if (!"All".equals(selectedStatus)) {
                String itemStatus = item.getStatus() != null ? item.getStatus() : "";
                if (!selectedStatus.equalsIgnoreCase(itemStatus)) continue;
            }
            if (!"All".equals(selectedWarehouse)) {
                String itemLoc = item.getLocation() != null ? item.getLocation() : "";
                if (!selectedWarehouse.equalsIgnoreCase(itemLoc)) continue;
            }
            if (!query.isEmpty()) {
                String name = item.getItemName() != null ? item.getItemName().toLowerCase() : "";
                String tid = item.getTagId() != null ? item.getTagId().toLowerCase() : "";
                String location = item.getLocation() != null ? item.getLocation().toLowerCase() : "";
                String epc = item.getEpcTag() != null ? item.getEpcTag().toLowerCase() : "";
                if (!name.contains(query) && !tid.contains(query) && !location.contains(query) && !epc.contains(query))
                    continue;
            }
            filteredList.add(item);
        }
        adapter.notifyDataSetChanged();
        updateEmptyState();
    }

    private void moveScannedItemToTop(String code) {
        TagModel.SearchItemDto found = null;
        for (TagModel.SearchItemDto item : allItemList) {
            if (code.equalsIgnoreCase(item.getEpcTag()) || code.equalsIgnoreCase(item.getTagId())) {
                found = item; break;
            }
        }
        if (found != null) {
            playScanFeedback(0);
            filteredList.remove(found);
            filteredList.add(0, found);
            adapter.setLastScannedPosition(0);
            adapter.notifyDataSetChanged();
            rvTags.scrollToPosition(0);
            showSuccess("Found: " + found.getItemName());
            if (rfidSearchActive) setRfidSearchActive(false);
        } else {
            playScanFeedback(2);
            showError("Item not found");
        }
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
