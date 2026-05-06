package com.example.inventory_system_ht.Helper;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.inventory_system_ht.Models.AuthModels;
import com.example.inventory_system_ht.Models.GeneralResponse;
import com.example.inventory_system_ht.Models.PendingSubmitEntity;
import com.example.inventory_system_ht.Models.StockPrepBulkRequest;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.List;

import retrofit2.Response;

public class SyncWorker extends Worker {

    private static final String TAG = "SyncWorker";

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
        List<PendingSubmitEntity> pendingList = appDao.getAllPendingSubmit();

        if (pendingList == null || pendingList.isEmpty()) {
            Log.d(TAG, "Tidak ada pending submit");
            return Result.success();
        }

        Gson gson = new Gson();
        Type listType = new TypeToken<List<String>>() {}.getType();
        boolean hasFailure = false;

        for (PendingSubmitEntity pending : pendingList) {
            try {
                List<String> codes = gson.fromJson(pending.scannedCodes, listType);
                Response<GeneralResponse> response;

                if ("TAG_REGISTRATION".equals(pending.doId)) {
                    AuthModels.RegisterRequest regReq = new AuthModels.RegisterRequest(codes);
                    response = api.registerTags(token, regReq).execute();
                } else {
                    StockPrepBulkRequest request = new StockPrepBulkRequest(
                            pending.doId, codes, pending.scannerType, pending.locId);
                    response = api.submitStockPrep(token, request).execute();
                }

                if (response.isSuccessful()) {
                    appDao.deletePendingSubmitById(pending.id);
                    Log.d(TAG, "Sync sukses untuk doId: " + pending.doId);
                } else {
                    Log.e(TAG, "Sync gagal " + pending.doId + " | code: " + response.code());
                    hasFailure = true;
                }
            } catch (Exception e) {
                Log.e(TAG, "Exception saat sync: " + e.getMessage());
                hasFailure = true;
            }
        }

        // Kalau ada yang gagal, retry nanti
        return hasFailure ? Result.retry() : Result.success();
    }
}