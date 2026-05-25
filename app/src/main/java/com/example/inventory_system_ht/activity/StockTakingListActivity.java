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
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class StockTakingListActivity extends ScannerActivity {

    private TextView tvEmpty;
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
        RecyclerView rvSessions = findViewById(R.id.rvTags);
        tvEmpty = findViewById(R.id.tvEmpty);

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
        api.getActiveStockTaking(token).enqueue(new Callback<StockTakingModel.ActiveRes>() {
            @Override
            public void onResponse(@NonNull Call<StockTakingModel.ActiveRes> call,
                                   @NonNull Response<StockTakingModel.ActiveRes> response) {
                hideLoading();
                sessionList.clear();
                if (response.isSuccessful() && response.body() != null) {
                    sessionList.add(response.body());
                    tvEmpty.setVisibility(View.GONE);
                } else {
                    tvEmpty.setVisibility(View.VISIBLE);
                }
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onFailure(@NonNull Call<StockTakingModel.ActiveRes> call,
                                  @NonNull Throwable t) {
                hideLoading();
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
