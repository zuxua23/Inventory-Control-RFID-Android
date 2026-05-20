package com.example.inventory_system_ht.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import java.io.IOException;
import java.security.GeneralSecurityException;

public class PrefManager {

    private SharedPreferences securePref;
    private final SharedPreferences pref;

    private static final String PREF_NAME = "InventoryPrefsBase";
    private static final String SECURE_PREF = "InventoryPrefsSecure";
    private static final String KEY_BASE_URL = "base_url_api";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_FULL_NAME = "full_name";
    private static final String KEY_ROLE_CODE = "role_code";
    private static final String KEY_PERMISSIONS = "permissions";
    private static final String KEY_LOGGED_IN = "is_logged_in";

    public PrefManager(Context context) {
        pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        try {
            String masterKey = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            securePref = EncryptedSharedPreferences.create(
                    SECURE_PREF,
                    masterKey,
                    context,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException | IOException e) {
            try {
                context.getSharedPreferences(SECURE_PREF, Context.MODE_PRIVATE)
                        .edit().clear().apply();
                String masterKey = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
                securePref = EncryptedSharedPreferences.create(
                        SECURE_PREF,
                        masterKey,
                        context,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                );
            } catch (Exception e2) {
                securePref = context.getSharedPreferences(SECURE_PREF, Context.MODE_PRIVATE);
            }
        }
    }

    public void saveUserSession(String token, String userId, String username,
                                String fullName, String roleCode, String permissionsJson) {
        securePref.edit()
                .putBoolean(KEY_LOGGED_IN, true)
                .putString(KEY_TOKEN, token)
                .putString(KEY_USER_ID, userId)
                .putString(KEY_USERNAME, username)
                .putString(KEY_FULL_NAME, fullName)
                .putString(KEY_ROLE_CODE, roleCode)
                .putString(KEY_PERMISSIONS, permissionsJson != null ? permissionsJson : "[]")
                .apply();
    }

    public boolean isSessionValid() {
        return securePref.getBoolean(KEY_LOGGED_IN, false)
                && securePref.getString(KEY_TOKEN, null) != null;
    }

    public void clearSession() {
        securePref.edit()
                .remove(KEY_LOGGED_IN)
                .remove(KEY_TOKEN)
                .remove(KEY_USER_ID)
                .remove(KEY_USERNAME)
                .remove(KEY_FULL_NAME)
                .remove(KEY_ROLE_CODE)
                .remove(KEY_PERMISSIONS)
                .apply();
    }

    public String getToken() { return securePref.getString(KEY_TOKEN, null); }
    public String getUserId() { return securePref.getString(KEY_USER_ID, ""); }
    public String getUsername() { return securePref.getString(KEY_USERNAME, ""); }
    public String getFullName() { return securePref.getString(KEY_FULL_NAME, "Guest"); }
    public String getRoleCode() { return securePref.getString(KEY_ROLE_CODE, ""); }
    public String getPermissions() { return securePref.getString(KEY_PERMISSIONS, "[]"); }

    public String getRoleName() {
        String code = getRoleCode();
        if (code == null || code.isEmpty()) return "Unknown Role";
        return code.substring(0, 1).toUpperCase() + code.substring(1).toLowerCase();
    }

    public void saveIp(String url) {
        if (!url.startsWith("http://")) url = "http://" + url;
        if (!url.endsWith("/")) url += "/";
        pref.edit().putString(KEY_BASE_URL, url).apply();
    }

    public String getBaseUrl() { return pref.getString(KEY_BASE_URL, ""); }
    public String getIp() { return getBaseUrl(); }
}
