package com.example.inventory_system_ht.util;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.inventory_system_ht.database.AppDatabase;
import com.example.inventory_system_ht.database.AppDao;
import com.example.inventory_system_ht.entity.PendingSubmitEntity;
import com.example.inventory_system_ht.model.AuthModel;
import com.example.inventory_system_ht.model.GeneralResponse;
import com.example.inventory_system_ht.model.StockPrepBulkRequest;
import com.example.inventory_system_ht.network.ApiClient;
import com.example.inventory_system_ht.network.ApiService;
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
            Log.d(TAG, "No pending submissions");
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
                    response = api.registerTags(token, new AuthModel.RegisterRequest(codes)).execute();
                } else {
                    response = api.submitStockPrep(token, new StockPrepBulkRequest(
                            pending.doId, codes, pending.scannerType, pending.locId)).execute();
                }

                if (response.isSuccessful()) {
                    appDao.deletePendingSubmitById(pending.id);
                    Log.d(TAG, "Sync success for doId: " + pending.doId);
                } else {
                    Log.e(TAG, "Sync failed for " + pending.doId + " | code: " + response.code());
                    hasFailure = true;
                }
            } catch (Exception e) {
                Log.e(TAG, "Sync exception: " + e.getMessage());
                hasFailure = true;
            }
        }

        return hasFailure ? Result.retry() : Result.success();
    }
}
