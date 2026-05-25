package com.example.inventory_system_ht.util;

import android.content.Context;
import android.util.Log;
import com.example.inventory_system_ht.database.AppDatabase;
import com.example.inventory_system_ht.entity.AppLogEntity;

public class LogManager {
    private static final String TAG = "LogManager";
    private static LogManager instance;
    private final Context appContext;

    public static final String INFO = "INFO";
    public static final String WARNING = "WARNING";
    public static final String ERROR = "ERROR";

    public static final String ACTION_OPEN = "OPEN";
    public static final String ACTION_SCAN = "SCAN";
    public static final String ACTION_READ = "READ";
    public static final String ACTION_SUBMIT = "SUBMIT";
    public static final String ACTION_DELETE = "DELETE";
    public static final String ACTION_CREATE = "CREATE";
    public static final String ACTION_LOGIN = "LOGIN";
    public static final String ACTION_LOGOUT = "LOGOUT";
    public static final String ACTION_MESSAGE = "MESSAGE";
    public static final String ACTION_SETTING = "SETTING";

    private LogManager(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public static synchronized LogManager get(Context context) {
        if (instance == null) {
            instance = new LogManager(context);
        }
        return instance;
    }

    public void log(String level, String action, String menu, String entity,
                    String message, String userId, String requestApi, String responseApi) {
        AppLogEntity entry = new AppLogEntity();
        entry.timestamp = System.currentTimeMillis();
        entry.level = level;
        entry.action = action;
        entry.menu = menu != null ? menu : "";
        entry.entity = entity != null ? entity : "";
        entry.message = message != null ? message : "";
        entry.userId = userId != null ? userId : "";
        entry.requestApi = requestApi;
        entry.responseApi = responseApi;

        new Thread(() -> {
            try {
                AppDatabase.getDatabase(appContext).appDao().insertLog(entry);
            } catch (Exception e) {
                Log.e(TAG, "Failed to insert log", e);
            }
        }).start();
    }

    public void log(String level, String action, String menu, String entity, String message, String userId) {
        log(level, action, menu, entity, message, userId, null, null);
    }
}