package com.example.inventory_system_ht.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.densowave.scannersdk.Common.CommScanner;

import com.example.inventory_system_ht.activity.base.ScannerActivity;
import com.example.inventory_system_ht.model.StockTakingModel;
import com.example.inventory_system_ht.network.ApiClient;
import com.example.inventory_system_ht.network.ApiService;
import com.example.inventory_system_ht.util.LogManager;
import com.example.inventory_system_ht.util.PrefManager;
import com.example.inventory_system_ht.util.ScannerManager;
import com.example.inventory_system_ht.R;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class StockTakingListActivity extends ScannerActivity {

    private View tvEmpty;
    private RecyclerView rvSessions;
    private com.facebook.shimmer.ShimmerFrameLayout shimmerLayout;
    private ApiService api;
    private String token;

    private final List<StockTakingModel.ActiveRes> sessionList = new ArrayList<>();
    private SessionAdapter adapter;

    @Override
    protected CommScanner getScannerInstance() {
        return ScannerManager.getInstance().getScanner();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stock_taking_list);

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

        token = "Bearer " + new PrefManager(this).getToken();
        api = ApiClient.getClient(this).create(ApiService.class);

        initViews();
        setupListeners();
        loadActiveSession();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateReaderBattery(findViewById(R.id.ivReaderBattery));
        loadActiveSession();
    }

    private void initViews() {
        rvSessions = findViewById(R.id.rvTags);
        tvEmpty = findViewById(R.id.tvEmpty);
        shimmerLayout = findViewById(R.id.shimmerLayout);

        rvSessions.setVisibility(View.GONE);
        shimmerLayout.startShimmer();

        adapter = new SessionAdapter(sessionList, session -> {
            Intent i = new Intent(this, StockTakingActivity.class);
            i.putExtra("sttId", session.sttId);
            i.putExtra("remark", session.remark != null ? session.remark : "");
            startActivity(i);
        });

        rvSessions.setLayoutManager(new LinearLayoutManager(this));
        rvSessions.setAdapter(adapter);
    }

    private void setupListeners() {
        ((ImageView) findViewById(R.id.btnBack)).setOnClickListener(v -> finish());
        ((CardView) findViewById(R.id.btnRefresh)).setOnClickListener(v -> loadActiveSession());

        FloatingActionButton fabLog = findViewById(R.id.fabLog);
        if (fabLog != null) {
            fabLog.setOnClickListener(v -> {
                Intent i = new Intent(this, LogActivity.class);
                i.putExtra(LogActivity.EXTRA_MENU, "Stock Taking");
                startActivity(i);
            });
        }
        LogManager.get(this).log(LogManager.INFO, LogManager.ACTION_OPEN, "Stock Taking", "", "Opened Stock Taking List", new PrefManager(this).getUserId());
    }

    private void loadActiveSession() {
        if (!isNetworkConnected()) {
            showWarning("No internet connection");
            return;
        }
        showLoading();
        String userId = new PrefManager(this).getUserId();
        String reqJson = "{\"endpoint\":\"getActiveStockTaking\"}";
        api.getActiveStockTaking(token).enqueue(new Callback<StockTakingModel.ActiveRes>() {
            @Override
            public void onResponse(@NonNull Call<StockTakingModel.ActiveRes> call,
                                   @NonNull Response<StockTakingModel.ActiveRes> response) {
                hideLoading();
                String resJson = "{\"http_code\":" + response.code() + ",\"found\":"
                        + (response.body() != null) + "}";
                shimmerLayout.stopShimmer();
                shimmerLayout.setVisibility(View.GONE);
                rvSessions.setVisibility(View.VISIBLE);

                sessionList.clear();
                if (response.isSuccessful() && response.body() != null) {
                    LogManager.get(StockTakingListActivity.this).log(LogManager.INFO, LogManager.ACTION_READ,
                            "Stock Taking", "Session",
                            "Active session found: " + response.body().sttId,
                            userId, reqJson, resJson);
                    sessionList.add(response.body());
                    tvEmpty.setVisibility(View.GONE);
                } else {
                    LogManager.get(StockTakingListActivity.this).log(LogManager.INFO, LogManager.ACTION_READ,
                            "Stock Taking", "Session",
                            "No active session: HTTP " + response.code(),
                            userId, reqJson, resJson);
                    tvEmpty.setVisibility(View.VISIBLE);
                }
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onFailure(@NonNull Call<StockTakingModel.ActiveRes> call,
                                  @NonNull Throwable t) {
                hideLoading();
                String resJson = "{\"error\":\"" + t.getMessage() + "\"}";
                LogManager.get(StockTakingListActivity.this).log(LogManager.ERROR, LogManager.ACTION_READ,
                        "Stock Taking", "Session",
                        "Load session error: " + t.getMessage(),
                        userId, reqJson, resJson);
                shimmerLayout.stopShimmer();
                shimmerLayout.setVisibility(View.GONE);
                rvSessions.setVisibility(View.VISIBLE);
                handleFailure(t);
                tvEmpty.setVisibility(View.VISIBLE);
                adapter.notifyDataSetChanged();
            }
        });
    }

    static class SessionAdapter extends RecyclerView.Adapter<SessionAdapter.VH> {

        interface OnClick { void onClick(StockTakingModel.ActiveRes s); }

        private final List<StockTakingModel.ActiveRes> list;
        private final OnClick click;

        SessionAdapter(List<StockTakingModel.ActiveRes> list, OnClick click) {
            this.list = list;
            this.click = click;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_stock_taking_session, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            StockTakingModel.ActiveRes s = list.get(position);
            h.tvSttId.setText(s.sttId != null
                    ? "ID: " + s.sttId.substring(0, Math.min(8, s.sttId.length()))
                    + (s.sttId.length() > 8 ? "..." : "")
                    : "-");
            h.tvLocation.setText(s.location != null ? s.location : "-");
            h.itemView.setOnClickListener(v -> click.onClick(s));
        }

        @Override
        public int getItemCount() { return list.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvSttId, tvLocation;
            VH(View v) {
                super(v);
                tvSttId = v.findViewById(R.id.tvSttId);
                tvLocation = v.findViewById(R.id.tvSttLocation);
            }
        }
    }
}
