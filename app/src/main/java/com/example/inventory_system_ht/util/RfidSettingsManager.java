package com.example.inventory_system_ht.util;

import android.content.Context;
import android.content.SharedPreferences;

public class RfidSettingsManager {
    public static final int DEFAULT_POWER = 27;
    public static final int DEFAULT_SESSION = 1;
    public static final int DEFAULT_Q = 4;
    private static final String PREF_NAME = "RfidSettingsPrefs";
    private static final String KEY_POWER = "rfid_power";
    private static final String KEY_SESSION = "rfid_session";
    private static final String KEY_Q = "rfid_q";

    private final SharedPreferences pref;

    public RfidSettingsManager(Context ctx) {
        pref = ctx.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public int getPower() { return pref.getInt(KEY_POWER, DEFAULT_POWER); }
    public int getSession() { return pref.getInt(KEY_SESSION, DEFAULT_SESSION); }
    public int getQFactor() { return pref.getInt(KEY_Q, DEFAULT_Q); }

    public void save(int power, int session, int qFactor) {
        pref.edit()
                .putInt(KEY_POWER, power)
                .putInt(KEY_SESSION, session)
                .putInt(KEY_Q, qFactor)
                .apply();
    }
}
