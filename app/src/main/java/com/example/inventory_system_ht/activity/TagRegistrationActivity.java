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

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class TagRegistrationActivity extends ScannerActivity implements RFIDDataDelegate {

    // Views
    private AutoCompleteTextView actvItemSearch;
    private TextView tvSelectedItem, tvScanned, tvProcessing;
    private Button btnClear, btnSubmitRegis;
    private RecyclerView rvTags;
    private View tvEmpty;

    // State
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TagRegistrationAdapter adapter;
    private final List<TagLocalEntity> tagList = new ArrayList<>();

    // Selected item
    private String selectedItemId = null;
    private String selectedItemName = null;

    // All items from API
    private List<ItemModel.ItemResponse> allItems = new ArrayList<>();

    // Scan guard — max 1 tag
    private String currentEpc = null;
    private boolean isProcessing = false;

    // Recent items
    private static final String PREF_RECENT = "tag_regis_recent_items";
    private static final int MAX_RECENT = 5;

    // Power spinner
    private final List<String> powerList = new ArrayList<>(Arrays.asList(
            "5 dBm", "10 dBm", "15 dBm", "18 dBm", "21 dBm", "24 dBm", "27 dBm", "30 dBm"));
    private final int[] powerValues = {5, 10, 15, 18, 21, 24, 27, 30};

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
        loadItems();

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

    // ─── Bind Views ───────────────────────────────────────────────────────────

    private void bindViews() {
        actvItemSearch = findViewById(R.id.actvItemSearch);
        tvSelectedItem = findViewById(R.id.tvSelectedItem);
        tvScanned = findViewById(R.id.tvScanned);
        tvProcessing = findViewById(R.id.tvProcessing);
        btnClear = findViewById(R.id.btnClear);
        btnSubmitRegis = findViewById(R.id.btnSubmitRegis);
        rvTags = findViewById(R.id.rvTags);
        tvEmpty = findViewById(R.id.tvEmpty);
    }

    // ─── Item Search / Autocomplete ───────────────────────────────────────────

    private void setupItemSearch() {
        actvItemSearch.setDropDownBackgroundResource(R.drawable.bg_spinner_dropdown);

        actvItemSearch.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && actvItemSearch.getText().toString().isEmpty()) {
                showRecentDropdown();
            }
        });

        actvItemSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {
                if (selectedItemId != null) {
                    selectedItemId = null;
                    selectedItemName = null;
                    tvSelectedItem.setVisibility(View.GONE);
                }
                if (s.length() == 0) {
                    showRecentDropdown();
                } else {
                    // Swap ke full list adapter kalau masih di recent mode
                    if (actvItemSearch.getAdapter() instanceof ItemAutoCompleteAdapter
                            && ((ItemAutoCompleteAdapter) actvItemSearch.getAdapter()).isRecentMode) {
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
                actvItemSearch.setText(selected.getItemName());
                actvItemSearch.dismissDropDown();
                tvSelectedItem.setText("✓ " + selected.getItemName());
                tvSelectedItem.setVisibility(View.VISIBLE);
                saveRecentItem(selected);
            }
        });
    }

    private void loadItems() {
        String token = "Bearer " + new PrefManager(this).getToken();
        ApiClient.getClient(this).create(ApiService.class)
                .getAllItems(token)
                .enqueue(new Callback<List<ItemModel.ItemResponse>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<ItemModel.ItemResponse>> call,
                                           @NonNull Response<List<ItemModel.ItemResponse>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            allItems = response.body();
                            setupAutoCompleteAdapter();
                        }
                    }
                    @Override
                    public void onFailure(@NonNull Call<List<ItemModel.ItemResponse>> call,
                                          @NonNull Throwable t) {
                        showWarning("Gagal load item: " + t.getMessage());
                    }
                });
    }

    private void setupAutoCompleteAdapter() {
        ItemAutoCompleteAdapter a = new ItemAutoCompleteAdapter(allItems, false);
        actvItemSearch.setAdapter(a);
    }

    private void showRecentDropdown() {
        LinkedList<ItemModel.ItemResponse> recents = loadRecentItems();
        if (recents.isEmpty()) return;
        ItemAutoCompleteAdapter recentAdapter = new ItemAutoCompleteAdapter(
                new ArrayList<>(recents), true);
        actvItemSearch.setAdapter(recentAdapter);
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
                list.add(new ItemModel.ItemResponse(
                        obj.getString("itemId"),
                        obj.getString("itemName")
                ));
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
        // Tap item = hapus (reset scan)
        adapter.setOnItemClickListener(item -> resetScan());
        updateEmptyState();
    }

    private void updateEmptyState() {
        boolean empty = tagList.isEmpty();
        tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        rvTags.setVisibility(empty ? View.GONE : View.VISIBLE);
        tvScanned.setText(empty ? "Tag: -" : "Tag: " + tagList.get(0).getEpcTag());
    }

    // ─── Power Spinner ───────────────────────────────────────────────────────

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
                showWarning("Pilih item terlebih dahulu");
                return;
            }
            if (tagList.isEmpty()) {
                showWarning("Scan tag RFID terlebih dahulu");
                return;
            }
            hitApiRegisterTagWithItem(currentEpc, selectedItemId);
        });
    }

    // ─── RFID ────────────────────────────────────────────────────────────────

    @Override
    public void onRFIDDataReceived(CommScanner scanner, RFIDDataReceivedEvent event) {
        if (isProcessing || !tagList.isEmpty()) return; // sudah ada 1 tag, ignore
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
            showWarning("Pilih item dulu sebelum scan tag");
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

        // Validasi status tag ke BE (harus PRINTED atau OUT)
        String token = "Bearer " + new PrefManager(this).getToken();
        ApiClient.getClient(this).create(ApiService.class)
                .getTagsRegistBulk(token, new TagModel.BulkInfoReq(
                        Collections.singletonList(epc), "RFID"))
                .enqueue(new Callback<List<TagModel.TagInfoDto>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<TagModel.TagInfoDto>> call,
                                           @NonNull Response<List<TagModel.TagInfoDto>> response) {
                        if (tvProcessing != null) tvProcessing.setVisibility(View.GONE);
                        isProcessing = false;

                        if (!response.isSuccessful() || response.body() == null
                                || response.body().isEmpty()) {
                            showError("Tag tidak ditemukan di database");
                            playScanFeedback(2);
                            currentEpc = null;
                            return;
                        }

                        TagModel.TagInfoDto info = response.body().get(0);
                        String status = info.getStatus();
                        if (!"PRINTED".equalsIgnoreCase(status) && !"OUT".equalsIgnoreCase(status)) {
                            showWarning("Tag status '" + status + "' tidak bisa di-register");
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
                        // Offline fallback
                        addTagToList(epc);
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
                "Scanned: " + epc + " → " + selectedItemName,
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
                            showSuccess("Tag berhasil di-register ke " + selectedItemName);
                            playScanFeedback(0);
                            // Reset scan saja, item selection tetap → operator bisa langsung scan tag berikutnya
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

    /** Reset penuh — item selection + scan */
    private void resetAll() {
        actvItemSearch.setText("");
        tvSelectedItem.setVisibility(View.GONE);
        selectedItemId = null;
        selectedItemName = null;
        resetScan();
    }

    /** Reset scan saja, item selection tetap */
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
        boolean isRecentMode; // package-private, diakses dari outer class

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
                if (position == 0) return null; // header
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
                // Header "Terakhir dipilih"
                View headerView = LayoutInflater.from(getContext())
                        .inflate(android.R.layout.simple_list_item_1, parent, false);
                headerView.setTag("header");
                TextView tv = headerView.findViewById(android.R.id.text1);
                tv.setText("🕐 Terakhir dipilih");
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
            ((TextView) convertView.findViewById(android.R.id.text1))
                    .setText(item != null ? item.getItemName() : "");
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
                            if (item.getItemName().toLowerCase().contains(query))
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
