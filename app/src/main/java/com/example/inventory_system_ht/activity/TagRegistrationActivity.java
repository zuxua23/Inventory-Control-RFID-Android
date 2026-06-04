package com.example.inventory_system_ht.activity;

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
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.densowave.scannersdk.Common.CommScanner;
import com.densowave.scannersdk.Listener.RFIDDataDelegate;
import com.densowave.scannersdk.RFID.RFIDData;
import com.densowave.scannersdk.RFID.RFIDDataReceivedEvent;

import com.example.inventory_system_ht.activity.base.ScannerActivity;
import com.example.inventory_system_ht.adapter.TagRegistrationAdapter;
import com.example.inventory_system_ht.database.AppDatabase;
import com.example.inventory_system_ht.entity.PendingSubmitEntity;
import com.example.inventory_system_ht.entity.TagLocalEntity;
import com.example.inventory_system_ht.model.AuthModel;
import com.example.inventory_system_ht.model.GeneralResponse;
import com.example.inventory_system_ht.model.TagModel;
import com.example.inventory_system_ht.network.ApiClient;
import com.example.inventory_system_ht.network.ApiService;
import com.example.inventory_system_ht.util.LogManager;
import com.example.inventory_system_ht.util.PrefManager;
import com.example.inventory_system_ht.util.RfidBulkHelper;
import com.example.inventory_system_ht.util.RfidSettingsManager;
import com.example.inventory_system_ht.util.ScannerManager;
import com.example.inventory_system_ht.util.SyncWorker;
import com.example.inventory_system_ht.R;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import retrofit2.Call;

public class TagRegistrationActivity extends ScannerActivity
        implements RFIDDataDelegate {
    private TextView tvScanned;
    private Button btnClear, btnSubmitRegis;
    private RecyclerView rvTags;
    private Spinner spinnerPower;
    private TagRegistrationAdapter adapter;
    private List<TagLocalEntity> registeredTagList;
    private View tvEmpty;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private AppDatabase db;
    private final List<String> powerList = new ArrayList<>(Arrays.asList(
            "5 dBm", "10 dBm", "15 dBm", "18 dBm", "21 dBm", "24 dBm", "27 dBm", "30 dBm"));
    private final int[] powerValues = {5, 10, 15, 18, 21, 24, 27, 30};

    private final java.util.Set<String> tagBuffer = new java.util.HashSet<>();
    private static final int BATCH_DELAY_MS = 500;
    private Runnable batchRunnable;

    @Override
    protected CommScanner getScannerInstance() {
        return ScannerManager.getInstance().getScanner();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tag_registration);

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

        db = AppDatabase.getDatabase(this);

        bindViews();
        setupPowerSpinner();
        setupRecyclerView();
        updateEmptyState();
        setupButtonListeners();

        FloatingActionButton fabLog = findViewById(R.id.fabLog);
        if (fabLog != null) {
            fabLog.setOnClickListener(v -> {
                Intent i = new Intent(this, LogActivity.class);
                i.putExtra(LogActivity.EXTRA_MENU, "Tag Registration");
                startActivity(i);
            });
        }
        LogManager.get(this).log(LogManager.INFO, LogManager.ACTION_OPEN, "Tag Registration", "", "Opened Tag Registration", new PrefManager(this).getUserId());
    }

    @Override
    protected void onResume() {
        super.onResume();
        CommScanner scanner = getScannerInstance();
        updateReaderBattery(findViewById(R.id.ivReaderBattery), true);
        if (scanner == null) {
            showError("RFID not connected");
        } else {
            boolean ok = RfidBulkHelper.openInventory(scanner, this, this);
            if (!ok) showError("Failed to start RFID");
        }

        int bat = getHTBatteryLevel();
        if (bat <= 15) showWarning("Battery low: " + bat + "%");
    }

    @Override
    protected void onPause() {
        super.onPause();
        CommScanner scanner = getScannerInstance();
        RfidBulkHelper.closeInventory(scanner);
    }

    private void bindViews() {
        tvScanned = findViewById(R.id.tvScanned);
        btnClear = findViewById(R.id.btnClear);
        btnSubmitRegis = findViewById(R.id.btnSubmitRegis);
        rvTags = findViewById(R.id.rvTags);
        spinnerPower = findViewById(R.id.spinnerPower);
    }

    private void setupPowerSpinner() {
        ArrayAdapter<String> powerAdapter = new ArrayAdapter<String>(this,
                R.layout.item_spinner_selected, R.id.tvSpinnerSelected, powerList) {
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
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void updateEmptyState() {
        if (tvEmpty != null)
            tvEmpty.setVisibility(registeredTagList.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void setupRecyclerView() {
        tvEmpty = findViewById(R.id.tvEmpty);
        rvTags.setItemAnimator(null);
        rvTags.setLayoutManager(new LinearLayoutManager(this));
        registeredTagList = new ArrayList<>();
        adapter = new TagRegistrationAdapter(registeredTagList);
        rvTags.setAdapter(adapter);
        adapter.setOnItemClickListener(item -> {
            int pos = registeredTagList.indexOf(item);
            if (pos != -1) showDeleteDialog(item, pos);
        });
    }

    private void setupButtonListeners() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        btnClear.setOnClickListener(v -> {
            registeredTagList.clear();
            adapter.notifyDataSetChanged();
            updateCount();
            updateEmptyState();
        });

        btnSubmitRegis.setOnClickListener(v -> {
            if (registeredTagList.isEmpty()) { showWarning("No tags scanned"); return; }
            showBulkConfirmDialog();
        });
    }

    private void queueScan(String epc) {
        synchronized (tagBuffer) {
            tagBuffer.add(epc.toUpperCase());
        }
        if (batchRunnable != null) handler.removeCallbacks(batchRunnable);
        batchRunnable = this::processBatch;
        handler.postDelayed(batchRunnable, BATCH_DELAY_MS);
    }

    private void processBatch() {
        List<String> batch;
        synchronized (tagBuffer) {
            if (tagBuffer.isEmpty()) return;
            batch = new ArrayList<>(tagBuffer);
            tagBuffer.clear();
        }

        // Filter out EPCs already in list
        List<String> newEpcs = new ArrayList<>();
        for (String epc : batch) {
            boolean alreadyIn = false;
            for (TagLocalEntity t : registeredTagList) {
                if (epc.equalsIgnoreCase(t.getEpcTag())) { alreadyIn = true; break; }
            }
            if (!alreadyIn) newEpcs.add(epc);
        }
        if (newEpcs.isEmpty()) return;

        if (!isNetworkConnected()) {
            for (String epc : newEpcs) addTagToList(epc, epc);
            return;
        }

        String token = "Bearer " + new PrefManager(this).getToken();
        ApiClient.getClient(this).create(ApiService.class)
                .getTagsInfoBulk(token, new TagModel.BulkInfoReq(newEpcs, "RFID"))
                .enqueue(new retrofit2.Callback<List<TagModel.TagInfoDto>>() {
                    @Override
                    public void onResponse(Call<List<TagModel.TagInfoDto>> call, retrofit2.Response<List<TagModel.TagInfoDto>> response) {
                        if (!response.isSuccessful() || response.body() == null) return;
                        int added = 0;
                        for (TagModel.TagInfoDto info : response.body()) {
                            String status = info.getStatus();
                            if ("PRINTED".equalsIgnoreCase(status) || "OUT".equalsIgnoreCase(status)) {
                                addTagToList(info.getEpcTag(), info.getTagId());
                                added++;
                            } else {
                                LogManager.get(TagRegistrationActivity.this).log(LogManager.WARNING, LogManager.ACTION_SCAN, "Tag Registration", info.getEpcTag(), "Rejected status=" + status, new PrefManager(TagRegistrationActivity.this).getUserId());
                            }
                        }
                        if (added > 0) playScanFeedback(0);
                    }
                    @Override
                    public void onFailure(Call<List<TagModel.TagInfoDto>> call, Throwable t) {
                        for (String epc : newEpcs) addTagToList(epc, epc);
                    }
                });
    }

    private void addTagToList(String epc, String tagId) {
        TagLocalEntity newTag = new TagLocalEntity(
                epc, tagId, "TAG", "Scanned Item", "STAGING", 0);
        registeredTagList.add(0, newTag);
        adapter.setLastScannedPosition(0);
        adapter.notifyItemInserted(0);
        rvTags.scrollToPosition(0);
        updateCount();
        updateEmptyState();
        LogManager.get(this).log(LogManager.INFO, LogManager.ACTION_SCAN, "Tag Registration", epc, "Scanned: " + epc, new PrefManager(this).getUserId());
    }

    private void updateCount() {
        tvScanned.setText("Scanned: " + registeredTagList.size());
    }

    private void hitApiRegisterTags(List<String> tagIds) {
        if (!isNetworkConnected()) {
            new Thread(() -> {
                PendingSubmitEntity pending = new PendingSubmitEntity();
                pending.doId = "TAG_REGISTRATION";
                pending.scannedCodes = new Gson().toJson(tagIds);
                pending.scannerType = "RFID";
                pending.locId = "";
                pending.createdAt = System.currentTimeMillis();
                db.appDao().insertPendingSubmit(pending);

                WorkManager.getInstance(getApplicationContext()).enqueue(
                        new OneTimeWorkRequest.Builder(SyncWorker.class)
                                .setConstraints(new Constraints.Builder()
                                        .setRequiredNetworkType(NetworkType.CONNECTED).build())
                                .build());

                runOnUiThread(() -> {
                    showWarning(tagIds.size() + " tags saved offline, will sync later");
                    registeredTagList.clear();
                    adapter.notifyDataSetChanged();
                    updateCount();
                    updateEmptyState();
                });
            }).start();
            return;
        }

        showLoading();
        String token = "Bearer " + new PrefManager(this).getToken();
        String userId = new PrefManager(this).getUserId();
        String reqJson = "{\"count\":" + tagIds.size() + "}";
        ApiClient.getClient(this).create(ApiService.class)
                .registerTags(token, new AuthModel.RegisterRequest(tagIds))
                .enqueue(new retrofit2.Callback<GeneralResponse>() {
                    @Override
                    public void onResponse(Call<GeneralResponse> call,
                                           retrofit2.Response<GeneralResponse> response) {
                        hideLoading();
                        String resJson = "{\"http_code\":" + response.code() + ",\"success\":" + response.isSuccessful() + "}";
                        if (response.isSuccessful()) {
                            LogManager.get(TagRegistrationActivity.this).log(LogManager.INFO, LogManager.ACTION_SUBMIT,
                                    "Tag Registration", "", "Registered " + tagIds.size() + " tags",
                                    userId, reqJson, resJson);
                            showSuccess(response.body().getMessage());
                            playScanFeedback(0);
                            registeredTagList.clear();
                            adapter.notifyDataSetChanged();
                            updateCount();
                        } else {
                            LogManager.get(TagRegistrationActivity.this).log(LogManager.WARNING, LogManager.ACTION_SUBMIT,
                                    "Tag Registration", "", "Register failed: HTTP " + response.code(),
                                    userId, reqJson, resJson);
                            handleApiError(response);
                            playScanFeedback(2);
                        }
                    }

                    @Override
                    public void onFailure(Call<GeneralResponse> call, Throwable t) {
                        hideLoading();
                        String resJson = "{\"error\":\"" + t.getMessage() + "\"}";
                        LogManager.get(TagRegistrationActivity.this).log(LogManager.ERROR, LogManager.ACTION_SUBMIT,
                                "Tag Registration", "", "Register error: " + t.getMessage(),
                                userId, reqJson, resJson);
                        handleFailure(t);
                        playScanFeedback(2);
                    }
                });
    }

    private void showBulkConfirmDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_regist);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        ((TextView) dialog.findViewById(R.id.tvTitle))
                .setText("Register " + registeredTagList.size() + " tags?");
        dialog.findViewById(R.id.btnNo).setOnClickListener(v -> dialog.dismiss());
        dialog.findViewById(R.id.btnSave).setOnClickListener(v -> {
            dialog.dismiss();
            List<String> ids = new ArrayList<>();
            for (TagLocalEntity t : registeredTagList) ids.add(t.getEpcTag());
            hitApiRegisterTags(ids);
        });
        dialog.show();
    }

    private void showDeleteDialog(TagLocalEntity tag, int position) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_regist);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        ((TextView) dialog.findViewById(R.id.tvTitle)).setText("Remove tag from list?");

        Button btnYes = dialog.findViewById(R.id.btnSave);
        btnYes.setText("Remove");
        btnYes.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(Color.RED));

        dialog.findViewById(R.id.btnNo).setOnClickListener(v -> dialog.dismiss());
        btnYes.setOnClickListener(v -> {
            dialog.dismiss();
            registeredTagList.remove(position);
            adapter.notifyItemRemoved(position);
            adapter.notifyItemRangeChanged(position, registeredTagList.size());
            updateCount();
        });
        dialog.show();
    }

    @Override
    public void onRFIDDataReceived(CommScanner scanner, RFIDDataReceivedEvent event) {
        for (RFIDData data : event.getRFIDData()) {
            String epc = RfidBulkHelper.bytesToHex(data.getUII());
            if (!epc.isEmpty()) handler.post(() -> queueScan(epc));
        }
    }
}
