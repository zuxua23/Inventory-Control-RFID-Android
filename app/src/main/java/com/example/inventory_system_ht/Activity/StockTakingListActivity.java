package com.example.inventory_system_ht.Activity;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.densowave.scannersdk.Common.CommScanner;
import com.example.inventory_system_ht.Helper.ApiClient;
import com.example.inventory_system_ht.Helper.ApiService;
import com.example.inventory_system_ht.Helper.PrefManager;
import com.example.inventory_system_ht.Models.StockTakingModels;
import com.example.inventory_system_ht.R;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class StockTakingListActivity extends BaseScannerActivity {

    private TextView tvEmpty;
    private ApiService api;
    private String token;

    private final List<StockTakingModels.ActiveRes> sessionList = new ArrayList<>();
    private SessionAdapter adapter;

    @Override
    protected CommScanner getScannerInstance() { return null; }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stock_taking_list);

        PrefManager pref = new PrefManager(this);
        token = "Bearer " + pref.getToken();
        api   = ApiClient.getClient(this).create(ApiService.class);

        ImageView btnBack    = findViewById(R.id.btnBack);
        CardView btnRefresh = findViewById(R.id.btnRefresh);
        RecyclerView rvSessions = findViewById(R.id.rvTags);
        tvEmpty    = findViewById(R.id.tvEmpty);

        adapter = new SessionAdapter(sessionList, session -> {
            Intent i = new Intent(this, StockTakingActivity.class);
            i.putExtra("sttId",  session.sttId);
            i.putExtra("remark", session.remark);
            startActivity(i);
        });

        rvSessions.setLayoutManager(new LinearLayoutManager(this));
        rvSessions.setAdapter(adapter);

        btnBack.setOnClickListener(v -> finish());
        btnRefresh.setOnClickListener(v -> loadActiveSession());

        loadActiveSession();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadActiveSession();
    }

    private void loadActiveSession() {
        if (!isNetworkConnected()) {
            showWarning("No internet connection.");
            return;
        }
        showLoading();
        api.getActiveStockTaking(token).enqueue(new Callback<StockTakingModels.ActiveRes>() {
            @Override
            public void onResponse(@NonNull Call<StockTakingModels.ActiveRes> call,
                                   @NonNull Response<StockTakingModels.ActiveRes> response) {
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
            public void onFailure(@NonNull Call<StockTakingModels.ActiveRes> call, @NonNull Throwable t) {
                hideLoading();
                showError("Gagal memuat sesi: " + t.getMessage());
                tvEmpty.setVisibility(View.VISIBLE);
                adapter.notifyDataSetChanged();
            }
        });
    }

    @SuppressWarnings("unused")
    private void showCreateSessionDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_manual_add);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int w = (int)(getResources().getDisplayMetrics().widthPixels * 0.90);
            dialog.getWindow().setLayout(w, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        EditText etRemark  = dialog.findViewById(R.id.etManualItemId);
        EditText etRemark2 = dialog.findViewById(R.id.etManualRemark);
        Button   btnCancel = dialog.findViewById(R.id.btnCancelManual);
        Button   btnSave   = dialog.findViewById(R.id.btnSaveManual);

        etRemark.setHint("Remark / Keterangan");
        etRemark2.setVisibility(View.GONE);

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> {
            String remark = etRemark.getText().toString().trim();
            if (remark.isEmpty()) remark = "PT Sato Routine Inventory";
            dialog.dismiss();
            createNewSession(remark);
        });

        dialog.show();
    }

    private void createNewSession(String remark) {
        showLoading();
        StockTakingModels.CreateReq req = new StockTakingModels.CreateReq(remark);
        api.createNewStockTaking(token, req).enqueue(new Callback<StockTakingModels.CreateRes>() {
            @Override
            public void onResponse(@NonNull Call<StockTakingModels.CreateRes> call,
                                   @NonNull Response<StockTakingModels.CreateRes> response) {
                hideLoading();
                if (response.isSuccessful() && response.body() != null) {
                    showSuccess("Sesi berhasil dibuat!");
                    loadActiveSession();
                } else {
                    handleApiError(response.code());
                }
            }
            @Override
            public void onFailure(@NonNull Call<StockTakingModels.CreateRes> call, @NonNull Throwable t) {
                hideLoading();
                handleFailure(t);
            }
        });
    }

    // ── Inner Adapter ─────────────────────────────────────────────

    static class SessionAdapter extends RecyclerView.Adapter<SessionAdapter.VH> {
        interface OnClick { void onClick(StockTakingModels.ActiveRes s); }

        private final List<StockTakingModels.ActiveRes> list;
        private final OnClick click;

        SessionAdapter(List<StockTakingModels.ActiveRes> list, OnClick click) {
            this.list  = list;
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
            StockTakingModels.ActiveRes s = list.get(position);
            h.tvSttId.setText(s.sttId != null
                    ? "ID: " + s.sttId.substring(0, Math.min(8, s.sttId.length())) + (s.sttId.length() > 8 ? "..." : "")
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
                tvSttId   = v.findViewById(R.id.tvSttId);
                tvLocation = v.findViewById(R.id.tvSttLocation);
            }
        }
    }
}
