package com.example.inventory_system_ht.util;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.inventory_system_ht.database.AppDatabase;
import com.example.inventory_system_ht.database.AppDao;
import com.example.inventory_system_ht.entity.PendingSubmitEntity;
import com.example.inventory_system_ht.entity.PendingTagRegistrationEntity;
import com.example.inventory_system_ht.entity.StockInScanEntity;
import com.example.inventory_system_ht.model.AuthResponses;
import com.example.inventory_system_ht.model.GeneralResponse;
import com.example.inventory_system_ht.model.StockInRequest;
import com.example.inventory_system_ht.model.StockPrepBulkRequest;
import com.example.inventory_system_ht.model.TagResponses;
import com.example.inventory_system_ht.network.ApiClient;
import com.example.inventory_system_ht.network.ApiService;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        if (pref.getToken() == null || pref.getToken().isEmpty()) return Result.success();

        ApiService api = ApiClient.getClient(getApplicationContext()).create(ApiService.class);
        boolean hasNetworkFailure = false;

        List<PendingSubmitEntity> pendingList = appDao.getAllPendingSubmit();
        if (pendingList != null && !pendingList.isEmpty()) {
            Gson gson = new Gson();
            Type listType = new TypeToken<List<String>>() {}.getType();

            for (PendingSubmitEntity pending : pendingList) {
                try {
                    List<String> codes = gson.fromJson(pending.scannedCodes, listType);
                    Response<GeneralResponse> response;

                    if ("TAG_REGISTRATION".equals(pending.doId)) {
                        response = api.registerTags(token, new AuthResponses.RegisterRequest(codes)).execute();
                    } else {
                        response = api.submitStockPrep(token, new StockPrepBulkRequest(
                                pending.doId, codes, pending.scannerType, pending.locId)).execute();
                    }
                    if (response.isSuccessful() || response.code() >= 400) {
                        appDao.deletePendingSubmitById(pending.id);
                    } else {
                        hasNetworkFailure = true;
                    }
                } catch (Exception e) {
                    hasNetworkFailure = true;
                }
            }
        }

        List<PendingTagRegistrationEntity> pendingTagRegs = appDao.getAllPendingTagRegistrations();
        if (pendingTagRegs != null && !pendingTagRegs.isEmpty()) {
            for (PendingTagRegistrationEntity pending : pendingTagRegs) {
                try {
                    Response<GeneralResponse> response = api.registerTagWithItem(
                            token,
                            new TagResponses.RegisterWithItemReq(pending.epcTag, pending.itemId)
                    ).execute();

                    if (response.isSuccessful() || response.code() >= 400) {
                        appDao.deletePendingTagRegistrationById(pending.id);
                    } else {
                        hasNetworkFailure = true;
                    }
                } catch (Exception e) {
                    hasNetworkFailure = true;
                }
            }
        }

        List<StockInScanEntity> unsynced = appDao.getUnsyncedStockInScans();
        if (unsynced != null && !unsynced.isEmpty()) {
            Map<String, List<StockInScanEntity>> grouped = new LinkedHashMap<>();
            for (StockInScanEntity e : unsynced) {
                if (!e.isResolved) continue;
                String groupKey = (e.locationId != null ? e.locationId : "") + "|"
                        + (e.scannerType != null ? e.scannerType : "QR");
                if (!grouped.containsKey(groupKey)) grouped.put(groupKey, new ArrayList<>());
                grouped.get(groupKey).add(e);
            }

            for (Map.Entry<String, List<StockInScanEntity>> entry : grouped.entrySet()) {
                String[] parts = entry.getKey().split("\\|", 2);
                String locationId  = parts[0];
                String scannerType = parts.length > 1 ? parts[1] : "QR";

                List<String> codes = new ArrayList<>();
                for (StockInScanEntity e : entry.getValue()) codes.add(e.epcTag);

                try {
                    Response<GeneralResponse> response = api.stockIn(
                            token,
                            new StockInRequest(scannerType, codes, locationId)
                    ).execute();

                    if (response.isSuccessful() || response.code() >= 400) {
                        appDao.markAllStockInSynced();
                        appDao.clearSyncedStockInScans();
                    } else {
                        hasNetworkFailure = true;
                    }
                } catch (Exception e) {
                    hasNetworkFailure = true;
                }
            }
        }

        return hasNetworkFailure ? Result.retry() : Result.success();
    }
}