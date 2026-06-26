package com.example.inventory_system_ht.util;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.inventory_system_ht.database.AppDatabase;
import com.example.inventory_system_ht.database.AppDao;
import com.example.inventory_system_ht.entity.PendingSubmitEntity;
import com.example.inventory_system_ht.entity.StockInScanEntity;
import com.example.inventory_system_ht.model.AuthModel;
import com.example.inventory_system_ht.model.GeneralResponse;
import com.example.inventory_system_ht.model.StockInRequest;
import com.example.inventory_system_ht.model.StockPrepBulkRequest;
import com.example.inventory_system_ht.network.ApiClient;
import com.example.inventory_system_ht.network.ApiService;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import retrofit2.Response;

public class SyncWorker extends Worker {

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        AppDao appDao = AppDatabase.getDatabase(getApplicationContext()).appDao();
        PrefManager pref = new PrefManager(getApplicationContext());
        String token = "Bearer " + pref.getToken();

        ApiService api = ApiClient.getClient(getApplicationContext()).create(ApiService.class);
        boolean hasFailure = false;

        // ── 1. PendingSubmit (TAG_REGISTRATION + StockPrep) ──────────────────
        List<PendingSubmitEntity> pendingList = appDao.getAllPendingSubmit();
        if (pendingList != null && !pendingList.isEmpty()) {
            Gson gson = new Gson();
            Type listType = new TypeToken<List<String>>() {}.getType();

            for (PendingSubmitEntity pending : pendingList) {
                try {
                    List<String> codes = gson.fromJson(pending.scannedCodes, listType);
                    Response<GeneralResponse> response;

                    if ("TAG_REGISTRATION".equals(pending.doId)) {
                        response = api.registerTags(token, new AuthModel.RegisterRequest(codes)).execute();
                    } else {
                        response = api.submitStockPrep(token, new StockPrepBulkRequest(
                                pending.doId, codes, pending.scannerType, pending.locId)).execute();
                    }

                    if (response.isSuccessful()) {
                        appDao.deletePendingSubmitById(pending.id);
                    } else {
                        hasFailure = true;
                    }
                } catch (Exception e) {
                    hasFailure = true;
                }
            }
        }

        // ── 2. StockIn offline queue ──────────────────────────────────────────
        try {
            List<StockInScanEntity> unsynced = appDao.getUnsyncedStockInScans();
            if (unsynced != null && !unsynced.isEmpty()) {
                // Group by locationId + scannerType
                String locId = null;
                String scannerType = null;
                List<String> codes = new ArrayList<>();

                for (StockInScanEntity e : unsynced) {
                    if (!e.isResolved) continue; // skip unresolved tags
                    if (e.epcTag == null || e.epcTag.isEmpty()) continue;
                    if (locId == null && e.locationId != null) locId = e.locationId;
                    if (scannerType == null && e.scannerType != null) scannerType = e.scannerType;
                    codes.add(e.epcTag);
                }

                if (!codes.isEmpty() && locId != null && scannerType != null) {
                    Response<GeneralResponse> res = api.stockIn(token,
                            new StockInRequest(scannerType, codes, locId)).execute();
                    if (res.isSuccessful()) {
                        appDao.markAllStockInSynced();
                        appDao.clearSyncedStockInScans();
                    } else {
                        hasFailure = true;
                    }
                }
            }
        } catch (Exception e) {
            hasFailure = true;
        }

        return hasFailure ? Result.retry() : Result.success();
    }
}