package com.example.inventory_system_ht.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

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

import com.example.inventory_system_ht.activity.base.ScannerActivity;
import com.example.inventory_system_ht.util.LogManager;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.example.inventory_system_ht.adapter.DeliveryOrderAdapter;
import com.example.inventory_system_ht.database.AppDao;
import com.example.inventory_system_ht.database.AppDatabase;
import com.example.inventory_system_ht.entity.DeliveryOrderEntity;
import com.example.inventory_system_ht.model.DOModel;
import com.example.inventory_system_ht.network.ApiClient;
import com.example.inventory_system_ht.network.ApiService;
import com.example.inventory_system_ht.util.PrefManager;
import com.example.inventory_system_ht.util.RfidBulkHelper;
import com.example.inventory_system_ht.util.ScannerManager;
import com.example.inventory_system_ht.R;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;

public class StockPrepActivity extends ScannerActivity implements BarcodeDataDelegate {

    private RecyclerView rvTags;
    private DeliveryOrderAdapter adapter;
    private View tvEmpty;
    private com.facebook.shimmer.ShimmerFrameLayout shimmerLayout;
    private List<DeliveryOrderEntity> doList;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private AppDao appDao;

    @Override
    protected CommScanner getScannerInstance() {
        return ScannerManager.getInstance().getScanner();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stock_prep_delivery_order);
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
            int dp28 = (int)(28 * getResources().getDisplayMetrics().density);
            int dp20 = (int)(20 * getResources().getDisplayMetrics().density);
            p.bottomMargin = bars.bottom + dp28;
            p.rightMargin = dp20;
            v.setLayoutParams(p);
            return insets;
        });
        appDao = AppDatabase.getDatabase(this).appDao();

        initViews();
        setupListeners();
        loadDataFromLocalDB();

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
        updateReaderBattery(findViewById(R.id.ivReaderBattery));
        if (scanner != null) RfidBulkHelper.openBarcode(scanner, this);
        loadDataFromLocalDB();

        int bat = getHTBatteryLevel();
        if (bat <= 15) {
            showWarning("Battery low: " + bat + "%");
            playScanFeedback(2);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        RfidBulkHelper.closeBarcode(getScannerInstance());
    }

    private void initViews() {
        rvTags = findViewById(R.id.rvTags);
        doList = new ArrayList<>();
        tvEmpty = findViewById(R.id.tvEmpty);
        shimmerLayout = findViewById(R.id.shimmerLayout);
        adapter = new DeliveryOrderAdapter(doList, this::openDetailDO);
        rvTags.setLayoutManager(new LinearLayoutManager(this));
        rvTags.setAdapter(adapter);

        rvTags.setVisibility(View.GONE);
        shimmerLayout.startShimmer();
    }

    private void setupListeners() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnRefresh).setOnClickListener(v -> fetchDOFromServer());
    }

    private void loadDataFromLocalDB() {
        showLoading();
        new Thread(() -> {
            List<DeliveryOrderEntity> data = appDao.getAllDO();
            runOnUiThread(() -> {
                hideLoading();
                shimmerLayout.stopShimmer();
                shimmerLayout.setVisibility(View.GONE);
                rvTags.setVisibility(View.VISIBLE);
                doList.clear();
                if (data != null && !data.isEmpty()) {
                    doList.addAll(data);
                    tvEmpty.setVisibility(View.GONE);
                } else {
                    tvEmpty.setVisibility(View.VISIBLE);
                }
                adapter.notifyDataSetChanged();
            });
        }).start();
    }

    private void fetchDOFromServer() {
        if (!isNetworkConnected()) {
            showWarning("Offline, showing cached data");
            playScanFeedback(2);
            loadDataFromLocalDB();
            return;
        }

        showLoading();
        String token = "Bearer " + new PrefManager(this).getToken();
        String userId = new PrefManager(this).getUserId();
        String reqJson = "{\"endpoint\":\"getDo\"}";

        ApiClient.getClient(this).create(ApiService.class)
                .getDo(token)
                .enqueue(new retrofit2.Callback<List<DOModel.DOResponse>>() {
                    @Override
                    public void onResponse(Call<List<DOModel.DOResponse>> call,
                                           retrofit2.Response<List<DOModel.DOResponse>> response) {
                        String resJson = "{\"http_code\":" + response.code() + ",\"count\":"
                                + (response.body() != null ? response.body().size() : 0) + "}";
                        if (response.isSuccessful() && response.body() != null) {
                            LogManager.get(StockPrepActivity.this).log(LogManager.INFO, LogManager.ACTION_READ,
                                    "Stock Preparation", "DO List",
                                    "Fetch DO success: " + response.body().size() + " items",
                                    userId, reqJson, resJson);
                            List<DOModel.DOResponse> remoteList = response.body();
                            List<DeliveryOrderEntity> entities = new ArrayList<>();
                            for (DOModel.DOResponse r : remoteList) {
                                entities.add(new DeliveryOrderEntity(
                                        r.getDoId(), r.getDoNumber(), "", "", ""));
                            }
                            new Thread(() -> {
                                try { appDao.deleteAllDO(); } catch (Exception e) { Log.e("StockPrep", "DB clear error", e); }
                                appDao.insertDOList(entities);
                                runOnUiThread(() -> {
                                    hideLoading();
                                    playScanFeedback(0);
                                    loadDataFromLocalDB();
                                });
                            }).start();
                        } else {
                            LogManager.get(StockPrepActivity.this).log(LogManager.WARNING, LogManager.ACTION_READ,
                                    "Stock Preparation", "DO List",
                                    "Fetch DO failed: HTTP " + response.code(),
                                    userId, reqJson, resJson);
                            hideLoading();
                            handleApiError(response);
                            playScanFeedback(2);
                        }
                    }

                    @Override
                    public void onFailure(Call<List<DOModel.DOResponse>> call, Throwable t) {
                        String resJson = "{\"error\":\"" + t.getMessage() + "\"}";
                        LogManager.get(StockPrepActivity.this).log(LogManager.ERROR, LogManager.ACTION_READ,
                                "Stock Preparation", "DO List",
                                "Fetch DO error: " + t.getMessage(),
                                userId, reqJson, resJson);
                        hideLoading();
                        handleFailure(t);
                        playScanFeedback(2);
                    }
                });
    }

    private void openDetailDO(DeliveryOrderEntity item) {
        Intent intent = new Intent(this, StockPrepProductActivity.class);
        intent.putExtra("DO_ID", item.getDoId());
        intent.putExtra("NO_DO", item.getDoNo());
        intent.putExtra("DATE_DO", item.getCreatedAt());
        startActivity(intent);
    }

    @Override
    public void onBarcodeDataReceived(CommScanner scanner, BarcodeDataReceivedEvent event) {
        List<BarcodeData> dataList = event.getBarcodeData();
        if (!dataList.isEmpty()) {
            String scannedDo = new String(dataList.get(0).getData());
            handler.post(() -> {
                DeliveryOrderEntity match = null;
                for (DeliveryOrderEntity doItem : doList) {
                    if (doItem.getDoNo().equalsIgnoreCase(scannedDo)) {
                        match = doItem;
                        break;
                    }
                }
                if (match != null) {
                    playScanFeedback(0);
                    openDetailDO(match);
                } else {
                    playScanFeedback(2);
                    showError("DO not found: " + scannedDo);
                }
            });
        }
    }
}
