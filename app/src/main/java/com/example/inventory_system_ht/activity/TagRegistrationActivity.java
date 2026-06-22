package com.example.inventory_system_ht.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.densowave.scannersdk.Common.CommScanner;
import com.densowave.scannersdk.Listener.RFIDDataDelegate;
import com.densowave.scannersdk.RFID.RFIDData;
import com.densowave.scannersdk.RFID.RFIDDataReceivedEvent;

import com.example.inventory_system_ht.R;
import com.example.inventory_system_ht.activity.base.ScannerActivity;
import com.example.inventory_system_ht.adapter.TagRegistrationAdapter;
import com.example.inventory_system_ht.database.AppDatabase;
import com.example.inventory_system_ht.entity.ItemCacheEntity;
import com.example.inventory_system_ht.entity.TagLocalEntity;
import com.example.inventory_system_ht.model.GeneralResponse;
import com.example.inventory_system_ht.model.ItemModel;
import com.example.inventory_system_ht.model.TagModel;
import com.example.inventory_system_ht.network.ApiClient;
import com.example.inventory_system_ht.network.ApiService;
import com.example.inventory_system_ht.util.LogManager;
import com.example.inventory_system_ht.util.PrefManager;
import com.example.inventory_system_ht.util.RfidBulkHelper;
import com.example.inventory_system_ht.util.RfidSettingsManager;
import com.example.inventory_system_ht.util.ScannerManager;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class TagRegistrationActivity extends ScannerActivity implements RFIDDataDelegate {

    private static final String PREF_RECENT = "tag_regis_recent_items";
    private static final int MAX_RECENT = 5;

    // Power spinner
    private final List<String> powerList = new ArrayList<>(Arrays.asList(
            "5 dBm", "10 dBm", "15 dBm", "18 dBm", "21 dBm", "24 dBm", "27 dBm", "30 dBm"));
    private final int[] powerValues = {5, 10, 15, 18, 21, 24, 27, 30};

    // Views
    private AutoCompleteTextView actvItemSearch;
    private TextView tvProcessing;
    private Button btnClear, btnSubmitRegis;
    private RecyclerView rvTags;
    private View tvEmpty;

    // State
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private TagRegistrationAdapter adapter;
    private final List<TagLocalEntity> tagList = new ArrayList<>();

    // Selected item
    private String selectedItemId = null;
    private String selectedItemName = null;

    // Items for autocomplete
    private List<ItemModel.ItemResponse> allItems = new ArrayList<>();

    // Flag to prevent onTextChanged from resetting selection when we programmatically set text
    private boolean isSettingText = false;

    // Scan guard — max 1 tag
    private String currentEpc = null;
    private boolean isProcessing = false;

    @Override
    protected CommScanner getScannerInstance() {
        return ScannerManager.getInstance().getScanner();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tag_registration);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        applyWindowInsets();
        bindViews();
        setupPowerSpinner();
        setupRecyclerView();
        setupItemSearch();
        setupButtonListeners();
        loadItemsWithRoomCache();

        FloatingActionButton fabLog = findViewById(R.id.fabLog);
        if (fabLog != null) {
            fabLog.setOnClickListener(v -> {
                Intent i = new Intent(this, LogActivity.class);
                i.putExtra(LogActivity.EXTRA_MENU, "Tag Registration");
                startActivity(i);
            });
        }

        LogManager.get(this).log(LogManager.INFO, LogManager.ACTION_OPEN,
                "Tag Registration", "", "Opened Tag Registration",
                new PrefManager(this).getUserId());
    }


    private void applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.btnBack), (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            ViewGroup.MarginLayoutParams p = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            p.topMargin = bars.top + (int) (12 * getResources().getDisplayMetrics().density);
            v.setLayoutParams(p);
            return insets;
        });
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.bottomButtonsContainer), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            int dp10 = (int) (10 * getResources().getDisplayMetrics().density);
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), bars.bottom + dp10);
            return insets;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateReaderBattery(findViewById(R.id.ivReaderBattery), true);
        CommScanner scanner = getScannerInstance();
        if (scanner == null) {
            showError("RFID not connected");
        } else {
            boolean ok = RfidBulkHelper.openInventory(scanner, this, this);
            if (!ok) showError("Failed to start RFID");
            else playScanFeedback(1);
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
        RfidBulkHelper.closeInventory(getScannerInstance());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }

    // ─── Bind Views ───────────────────────────────────────────────────────────

    private void bindViews() {
        actvItemSearch = findViewById(R.id.actvItemSearch);
        tvProcessing = findViewById(R.id.tvProcessing);
        btnClear = findViewById(R.id.btnClear);
        btnSubmitRegis = findViewById(R.id.btnSubmitRegis);
        rvTags = findViewById(R.id.rvTags);
        tvEmpty = findViewById(R.id.tvEmpty);
    }

    // ─── Item Cache — Room DB ─────────────────────────────────────────────────

    /**
     * Strategy:
     * 1. Read from Room immediately → populate autocomplete (no delay, works offline)
     * 2. Fetch API in background → upsert to Room + refresh adapter
     */
    private void loadItemsWithRoomCache() {
        executor.execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(this);

            // Step 1: load from Room
            List<ItemCacheEntity> cached = db.appDao().getAllItemCache();
            if (!cached.isEmpty()) {
                List<ItemModel.ItemResponse> fromCache = entityToModel(cached);
                handler.post(() -> {
                    allItems = fromCache;
                    setupAutoCompleteAdapter();
                });
            }

            // Step 2: fetch fresh from API (always, to keep Room up to date)
            handler.post(this::fetchItemsFromApi);
        });
    }

    private void fetchItemsFromApi() {
        String token = "Bearer " + new PrefManager(this).getToken();
        ApiClient.getClient(this).create(ApiService.class)
                .getAllItems(token)
                .enqueue(new Callback<List<ItemModel.ItemResponse>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<ItemModel.ItemResponse>> call,
                                           @NonNull Response<List<ItemModel.ItemResponse>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            List<ItemModel.ItemResponse> fresh = response.body();
                            // Persist to Room on background thread
                            executor.execute(() -> {
                                AppDatabase db = AppDatabase.getDatabase(TagRegistrationActivity.this);
                                db.appDao().clearItemCache();
                                db.appDao().insertItemCache(modelToEntity(fresh));
                            });
                            // Update UI
                            allItems = fresh;
                            setupAutoCompleteAdapter();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<ItemModel.ItemResponse>> call,
                                          @NonNull Throwable t) {
                        // Room cache already loaded — silently ignore if offline
                        if (allItems.isEmpty()) {
                            showWarning("Failed to load items. Check connection.");
                        }
                    }
                });
    }

    private List<ItemModel.ItemResponse> entityToModel(List<ItemCacheEntity> entities) {
        List<ItemModel.ItemResponse> list = new ArrayList<>();
        for (ItemCacheEntity e : entities) {
            list.add(new ItemModel.ItemResponse(e.itemId, e.itemName));
        }
        return list;
    }

    private List<ItemCacheEntity> modelToEntity(List<ItemModel.ItemResponse> models) {
        List<ItemCacheEntity> list = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (ItemModel.ItemResponse m : models) {
            list.add(new ItemCacheEntity(m.getItemId(), m.getItemName(), now));
        }
        return list;
    }

    // ─── Item Search / Autocomplete ───────────────────────────────────────────

    private void setupItemSearch() {
        actvItemSearch.setDropDownBackgroundResource(R.drawable.bg_spinner_dropdown);

        actvItemSearch.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && actvItemSearch.getText().toString().isEmpty()) {
                showRecentDropdown();
            }
        });

        actvItemSearch.setOnClickListener(v -> {
            if (actvItemSearch.getText().toString().isEmpty()) {
                showRecentDropdown();
            }
        });

        actvItemSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {
                if (isSettingText) return;
                if (selectedItemId != null) {
                    selectedItemId = null;
                    selectedItemName = null;
                }
                if (s.length() == 0) {
                    showRecentDropdown();
                } else {
                    boolean noAdapter = !(actvItemSearch.getAdapter() instanceof ItemAutoCompleteAdapter);
                    boolean isRecent = !noAdapter
                            && ((ItemAutoCompleteAdapter) actvItemSearch.getAdapter()).isRecentMode;
                    if (noAdapter || isRecent) {
                        setupAutoCompleteAdapter();
                    }
                }
            }

            @Override public void afterTextChanged(Editable s) {}
        });

        actvItemSearch.setOnItemClickListener((parent, view, position, id) -> {
            ItemModel.ItemResponse selected = (ItemModel.ItemResponse) parent.getItemAtPosition(position);
            if (selected != null) {
                selectedItemId = selected.getItemId();
                selectedItemName = selected.getItemName();
                isSettingText = true;
                actvItemSearch.setText(selected.getItemName());
                isSettingText = false;
                actvItemSearch.dismissDropDown();
                saveRecentItem(selected);
            }
        });
    }

    private void setupAutoCompleteAdapter() {
        actvItemSearch.setAdapter(new ItemAutoCompleteAdapter(allItems, false));
        if (!actvItemSearch.getText().toString().isEmpty() && actvItemSearch.hasFocus()) {
            actvItemSearch.showDropDown();
        }

    }

    private void showRecentDropdown() {
        LinkedList<ItemModel.ItemResponse> recents = loadRecentItems();
        if (recents.isEmpty()) return;
        actvItemSearch.setAdapter(new ItemAutoCompleteAdapter(new ArrayList<>(recents), true));
        actvItemSearch.showDropDown();
    }

    // ─── Recent Items (SharedPreferences) ────────────────────────────────────

    private void saveRecentItem(ItemModel.ItemResponse item) {
        SharedPreferences prefs = getSharedPreferences(PREF_RECENT, MODE_PRIVATE);
        LinkedList<ItemModel.ItemResponse> recents = parseRecentList(prefs.getString("list", "[]"));
        recents.removeIf(r -> r.getItemId().equals(item.getItemId()));
        recents.addFirst(item);
        while (recents.size() > MAX_RECENT) recents.removeLast();
        prefs.edit().putString("list", serializeRecentList(recents)).apply();
    }

    private LinkedList<ItemModel.ItemResponse> loadRecentItems() {
        SharedPreferences prefs = getSharedPreferences(PREF_RECENT, MODE_PRIVATE);
        return parseRecentList(prefs.getString("list", "[]"));
    }

    private String serializeRecentList(List<ItemModel.ItemResponse> list) {
        JSONArray arr = new JSONArray();
        for (ItemModel.ItemResponse item : list) {
            try {
                JSONObject obj = new JSONObject();
                obj.put("itemId", item.getItemId());
                obj.put("itemName", item.getItemName());
                arr.put(obj);
            } catch (JSONException ignored) {}
        }
        return arr.toString();
    }

    private LinkedList<ItemModel.ItemResponse> parseRecentList(String json) {
        LinkedList<ItemModel.ItemResponse> list = new LinkedList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String id = obj.optString("itemId", "");
                String name = obj.optString("itemName", "");
                if (!id.isEmpty() && !name.isEmpty()){
                    list.add(new ItemModel.ItemResponse(id, name));
                }

            }
        } catch (JSONException ignored) {}
        return list;
    }

    // ─── RecyclerView ────────────────────────────────────────────────────────

    private void setupRecyclerView() {
        rvTags.setItemAnimator(null);
        rvTags.setLayoutManager(new LinearLayoutManager(this));
        rvTags.setNestedScrollingEnabled(false);
        adapter = new TagRegistrationAdapter(tagList);
        rvTags.setAdapter(adapter);
        adapter.setOnItemClickListener(item -> resetScan());
        updateEmptyState();
    }

    private void updateEmptyState() {
        boolean empty = tagList.isEmpty();
        tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        rvTags.setVisibility(empty ? View.GONE : View.VISIBLE);
    }


    // ─── Power Spinner ───────────────────────────────────────────────────────────

    private void setupPowerSpinner() {
        android.widget.Spinner spinnerPower = findViewById(R.id.spinnerPower);
        ArrayAdapter<String> powerAdapter = new ArrayAdapter<String>(this,
                R.layout.item_spinner_selected, R.id.tvSpinnerSelected, powerList) {
            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View view = LayoutInflater.from(getContext())
                        .inflate(R.layout.item_dropdown_loc, parent, false);
                TextView tv = view.findViewById(R.id.tvDropdownItem);
                android.widget.ImageView icon = view.findViewById(R.id.ivDropdownIcon);
                if (tv != null) tv.setText(getItem(position));
                if (icon != null) icon.setVisibility(View.GONE);
                return view;
            }
        };
        spinnerPower.setAdapter(powerAdapter);
        spinnerPower.setSelection(indexOfPower(new RfidSettingsManager(this).getPower()));
        spinnerPower.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view,
                                       int position, long id) {
                int power = powerValues[position];
                RfidSettingsManager mgr = new RfidSettingsManager(TagRegistrationActivity.this);
                if (power == mgr.getPower()) return;
                mgr.save(power, mgr.getSession(), mgr.getQFactor());
                CommScanner scanner = getScannerInstance();
                if (scanner != null) {
                    RfidBulkHelper.closeInventory(scanner);
                    RfidBulkHelper.openInventory(scanner, TagRegistrationActivity.this,
                            TagRegistrationActivity.this);
                }
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    }

    // ─── Buttons ─────────────────────────────────────────────────────────────

    private void setupButtonListeners() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        btnClear.setOnClickListener(v -> resetAll());
        btnSubmitRegis.setOnClickListener(v -> {
            if (selectedItemId == null) {
                showWarning("Please select an item first");
                return;
            }
            if (tagList.isEmpty()) {
                showWarning("Please scan an RFID tag first");
                return;
            }
            hitApiRegisterTagWithItem(currentEpc, selectedItemId);
        });
    }

    // ─── RFID ────────────────────────────────────────────────────────────────

    @Override
    public void onRFIDDataReceived(CommScanner scanner, RFIDDataReceivedEvent event) {
        if (isProcessing || !tagList.isEmpty()) return;
        List<String> epcs = new ArrayList<>();
        for (RFIDData data : event.getRFIDData()) {
            String epc = RfidBulkHelper.bytesToHex(data.getUII()).trim().toUpperCase();
            if (!epc.isEmpty()) epcs.add(epc);
        }
        if (!epcs.isEmpty()) handler.post(() -> processScannedEpc(epcs.get(0)));
    }

    private void processScannedEpc(String epc) {
        if (isProcessing || !tagList.isEmpty()) return;

        if (selectedItemId == null) {
            showWarning("Select an item before scanning a tag");
            playScanFeedback(2);
            return;
        }

        isProcessing = true;
        currentEpc = epc;
        if (tvProcessing != null) tvProcessing.setVisibility(View.VISIBLE);

        if (!isNetworkConnected()) {
            addTagToList(epc);
            isProcessing = false;
            if (tvProcessing != null) tvProcessing.setVisibility(View.GONE);
            return;
        }

        String token = "Bearer " + new PrefManager(this).getToken();
        ApiClient.getClient(this).create(ApiService.class)
                .validateTagEpc(token, new TagModel.BulkInfoReq(
                        Collections.singletonList(epc), "RFID"))
                .enqueue(new Callback<List<TagModel.TagInfoDto>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<TagModel.TagInfoDto>> call,
                                           @NonNull Response<List<TagModel.TagInfoDto>> response) {
                        if (tvProcessing != null) tvProcessing.setVisibility(View.GONE);
                        isProcessing = false;

                        if (!response.isSuccessful() || response.body() == null
                                || response.body().isEmpty()) {
                            showError("Tag not found in database");
                            playScanFeedback(2);
                            currentEpc = null;
                            return;
                        }

                        TagModel.TagInfoDto info = response.body().get(0);
                        String status = info.getStatus();
                        if (!"PRINTED".equalsIgnoreCase(status) && !"OUT".equalsIgnoreCase(status)) {
                            showWarning("Tag status '" + status + "' cannot be registered");
                            playScanFeedback(2);
                            currentEpc = null;
                            return;
                        }

                        addTagToList(epc);
                        playScanFeedback(0);
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<TagModel.TagInfoDto>> call,
                                          @NonNull Throwable t) {
                        if (tvProcessing != null) tvProcessing.setVisibility(View.GONE);
                        isProcessing = false;
                        addTagToList(epc); // offline fallback
                    }
                });
    }

    private void addTagToList(String epc) {
        tagList.clear();
        tagList.add(new TagLocalEntity(epc, epc, "TAG", selectedItemName, "STANDBY", 0));
        adapter.setLastScannedPosition(0);
        adapter.notifyDataSetChanged();
        updateEmptyState();
        LogManager.get(this).log(LogManager.INFO, LogManager.ACTION_SCAN,
                "Tag Registration", epc,
                "Scanned: " + epc + " -> " + selectedItemName,
                new PrefManager(this).getUserId());
    }

    // ─── API Submit ───────────────────────────────────────────────────────────

    private void hitApiRegisterTagWithItem(String epcTag, String itemId) {
        showLoading();
        String token = "Bearer " + new PrefManager(this).getToken();
        String userId = new PrefManager(this).getUserId();

        ApiClient.getClient(this).create(ApiService.class)
                .registerTagWithItem(token, new TagModel.RegisterWithItemReq(epcTag, itemId))
                .enqueue(new Callback<GeneralResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<GeneralResponse> call,
                                           @NonNull Response<GeneralResponse> response) {
                        hideLoading();
                        if (response.isSuccessful()) {
                            LogManager.get(TagRegistrationActivity.this).log(
                                    LogManager.INFO, LogManager.ACTION_SUBMIT,
                                    "Tag Registration", epcTag,
                                    "Registered EPC=" + epcTag + " ItemId=" + itemId, userId);
                            showSuccess("Tag successfully registered to " + selectedItemName);
                            playScanFeedback(0);
                            resetScan();
                        } else {
                            LogManager.get(TagRegistrationActivity.this).log(
                                    LogManager.WARNING, LogManager.ACTION_SUBMIT,
                                    "Tag Registration", epcTag,
                                    "Register failed: HTTP " + response.code(), userId);
                            handleApiError(response);
                            playScanFeedback(2);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<GeneralResponse> call, @NonNull Throwable t) {
                        hideLoading();
                        LogManager.get(TagRegistrationActivity.this).log(
                                LogManager.ERROR, LogManager.ACTION_SUBMIT,
                                "Tag Registration", epcTag,
                                "Register error: " + t.getMessage(), userId);
                        handleFailure(t);
                        playScanFeedback(2);
                    }
                });
    }

    // ─── Reset ────────────────────────────────────────────────────────────────

    private void resetAll() {
        isSettingText = true;
        actvItemSearch.setText("");
        isSettingText = false;
        selectedItemId = null;
        selectedItemName = null;
        resetScan();
        actvItemSearch.post(() -> {
            if (actvItemSearch.hasFocus()) showRecentDropdown();
        });
    }

    private void resetScan() {
        tagList.clear();
        currentEpc = null;
        isProcessing = false;
        adapter.notifyDataSetChanged();
        updateEmptyState();
    }

    // ─── AutoComplete Adapter ─────────────────────────────────────────────────

    private class ItemAutoCompleteAdapter extends ArrayAdapter<ItemModel.ItemResponse>
            implements Filterable {

        private static final int VIEW_TYPE_HEADER = 0;
        private static final int VIEW_TYPE_ITEM = 1;

        private List<ItemModel.ItemResponse> filtered;
        private final List<ItemModel.ItemResponse> original;
        boolean isRecentMode;

        ItemAutoCompleteAdapter(List<ItemModel.ItemResponse> items, boolean isRecentMode) {
            super(TagRegistrationActivity.this, 0, items);
            this.original = new ArrayList<>(items);
            this.filtered = new ArrayList<>(items);
            this.isRecentMode = isRecentMode;
        }

        @Override public int getCount() {
            return filtered.size() + (isRecentMode ? 1 : 0);
        }

        @Override public int getViewTypeCount() { return 2; }

        @Override
        public int getItemViewType(int position) {
            return (isRecentMode && position == 0) ? VIEW_TYPE_HEADER : VIEW_TYPE_ITEM;
        }

        @Override
        public ItemModel.ItemResponse getItem(int position) {
            if (isRecentMode) {
                if (position == 0) return null;
                return filtered.get(position - 1);
            }
            return filtered.get(position);
        }

        @Override
        public boolean isEnabled(int position) {
            return !(isRecentMode && position == 0);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            if (isRecentMode && position == 0) {
                View headerView = LayoutInflater.from(getContext())
                        .inflate(android.R.layout.simple_list_item_1, parent, false);
                headerView.setTag("header");
                TextView tv = headerView.findViewById(android.R.id.text1);
                tv.setText("Recently selected");
                tv.setTextSize(11f);
                tv.setTextColor(0xFF888888);
                tv.setPadding(32, 12, 32, 4);
                return headerView;
            }

            if (convertView == null || "header".equals(convertView.getTag())) {
                convertView = LayoutInflater.from(getContext())
                        .inflate(android.R.layout.simple_dropdown_item_1line, parent, false);
                convertView.setTag("item");
            }
            ItemModel.ItemResponse item = getItem(position);
            TextView tvItem = convertView.findViewById(android.R.id.text1);

            tvItem.setText(item != null ? item.getItemName() : "");
            tvItem.setTextColor(0xFF000000);

            return convertView;
        }

        @NonNull
        @Override
        public Filter getFilter() {
            return new Filter() {
                @Override
                protected FilterResults performFiltering(CharSequence constraint) {
                    FilterResults results = new FilterResults();
                    if (isRecentMode || constraint == null || constraint.length() == 0) {
                        results.values = original;
                        results.count = original.size();
                    } else {
                        String query = constraint.toString().toLowerCase().trim();
                        List<ItemModel.ItemResponse> out = new ArrayList<>();
                        for (ItemModel.ItemResponse item : original) {
                            if (item.getItemName() != null && item.getItemName().toLowerCase().contains(query))
                                out.add(item);
                        }
                        results.values = out;
                        results.count = out.size();
                    }
                    return results;
                }

                @SuppressWarnings("unchecked")
                @Override
                protected void publishResults(CharSequence constraint, FilterResults results) {
                    filtered = (List<ItemModel.ItemResponse>) results.values;
                    notifyDataSetChanged();
                }

                @Override
                public CharSequence convertResultToString(Object resultValue) {
                    if (resultValue == null) return "";
                    return ((ItemModel.ItemResponse) resultValue).getItemName();
                }
            };
        }
    }
}