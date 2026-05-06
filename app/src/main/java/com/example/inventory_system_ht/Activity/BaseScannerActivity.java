package com.example.inventory_system_ht.Activity;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.Switch;
import android.widget.TextView;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.BatteryManager;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.Window;
import android.widget.FrameLayout;
import androidx.appcompat.app.AppCompatActivity;

import com.densowave.scannersdk.Common.CommScanner;
import com.densowave.scannersdk.Const.CommConst;
import com.densowave.scannersdk.Dto.RFIDScannerSettings;
import com.example.inventory_system_ht.Helper.PrefManager;
import com.example.inventory_system_ht.R;

public abstract class BaseScannerActivity extends AppCompatActivity {

    private Dialog loadingDialog;
    private ToneGenerator toneGen;
    private Vibrator vibrator;
    private PopupWindow activePowerPopup = null;
    protected abstract CommScanner getScannerInstance();

    // ── Network ───────────────────────────────────────────────────

    public boolean isNetworkConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkCapabilities cap = cm.getNetworkCapabilities(cm.getActiveNetwork());
            return cap != null && (cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    cap.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR));
        }
        return false;
    }

    // ── Snackbar Banner ───────────────────────────────────────────

    public void showSagaFeedback(String pesan, boolean isSuccess) {
        showSagaFeedback(pesan, isSuccess ? 0 : 2);
    }

    public void showSagaFeedback(String pesan, int type) {
        FrameLayout rootLayout = findViewById(android.R.id.content);

        View     bannerView = getLayoutInflater().inflate(R.layout.layout_message_banner, rootLayout, false);
        ImageView dot       = bannerView.findViewById(R.id.dotIndicator);
        TextView tvMessage  = bannerView.findViewById(R.id.tvBannerMessage);

        switch (type) {
            case 1:  dot.setImageResource(R.drawable.dot_warning); break;
            case 2:  dot.setImageResource(R.drawable.dot_error);   break;
            default: dot.setImageResource(R.drawable.dot_success); break;
        }

        tvMessage.setText(pesan);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.gravity = Gravity.TOP;
        params.setMargins(15, 15, 15, 0);
        bannerView.setLayoutParams(params);

        bannerView.setAlpha(0f);
        bannerView.setTranslationY(-60f);
        rootLayout.addView(bannerView);

        bannerView.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(200)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.2f))
                .start();

        bannerView.postDelayed(() ->
                        bannerView.animate()
                                .alpha(0f)
                                .translationY(-40f)
                                .setDuration(250)
                                .withEndAction(() -> rootLayout.removeView(bannerView))
                                .start(),
                2500
        );
    }

    public void showSuccess(String pesan) { showSagaFeedback(pesan, 0); }
    public void showError(String pesan)   { showSagaFeedback(pesan, 2); }
    public void showWarning(String pesan) { showSagaFeedback(pesan, 1); }

    // ── Loading Dialog ────────────────────────────────────────────

    public void showLoading() {
        if (loadingDialog == null) {
            loadingDialog = new Dialog(this);
            loadingDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            loadingDialog.setContentView(R.layout.dialog_loading);
            loadingDialog.setCancelable(false);
            if (loadingDialog.getWindow() != null) {
                loadingDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                loadingDialog.getWindow().setLayout(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        }
        if (!loadingDialog.isShowing()) loadingDialog.show();
    }

    public void hideLoading() {
        if (loadingDialog != null && loadingDialog.isShowing()) loadingDialog.dismiss();
    }

    // ── Error Handling ────────────────────────────────────────────

    public void handleApiError(int statusCode) {
        hideLoading();
        if (statusCode == 401) {
            showSagaFeedback("Session expired, please login again", false);
            PrefManager pref = new PrefManager(this);
            pref.clearSession();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        } else if (statusCode >= 500) {
            showSagaFeedback("Server is having issues, please try again later", false);
        } else if (statusCode == 403) {
            showSagaFeedback("You don't have permission for this action", false);
        } else if (statusCode == 404) {
            showSagaFeedback("Data not found", false);
        } else {
            showSagaFeedback("Something went wrong, please check your input", false);
        }
    }

    public void handleApiError(retrofit2.Response<?> response) {
        hideLoading();
        int statusCode = response.code();
        if (statusCode == 401) {
            PrefManager pref = new PrefManager(this);
            pref.clearSession();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            showSagaFeedback("Session expired, please login again", false);
            return;
        }
        String msg = com.example.inventory_system_ht.Helper.ErrorParser.getMessage(response);
        showSagaFeedback(msg, false);
    }

    public void handleFailure(Throwable t) {
        hideLoading();
        if (t instanceof java.net.SocketTimeoutException) {
            showSagaFeedback("Connection timeout, please check your internet", false);
        } else if (t instanceof java.net.ConnectException) {
            showSagaFeedback("Cannot reach server, is it online?", false);
        } else if (t instanceof java.io.IOException) {
            showSagaFeedback("Network error, check your WiFi/Data connection", false);
        } else {
            showSagaFeedback("Something went wrong, please try again", false);
        }
    }

    // ── Scan Feedback ─────────────────────────────────────────────

    public void playScanFeedback(int type) {
        if (toneGen  == null) toneGen  = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
        if (vibrator == null) vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        switch (type) {
            case 0: // SUCCESS
                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 50);
                break;
            case 1: // DUPLICATE
                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 100);
                break;
            case 2: // ERROR
                toneGen.startTone(ToneGenerator.TONE_CDMA_HIGH_L, 200);
                if (vibrator != null && vibrator.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(
                                300, VibrationEffect.DEFAULT_AMPLITUDE));
                    } else {
                        vibrator.vibrate(300);
                    }
                }
                break;
        }
    }

    // ── RFID Power Dropdown ───────────────────────────────────────

    /**
     * Setup power dropdown + switch RFID.
     * Pilih power dari dropdown → langsung apply ke SDK via applyRfidPower().
     */
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    public void setupPowerDropdown(CardView btnPowerDropdown,
                                   Switch switchRfid,
                                   TextView tvPowerLevel) {
        btnPowerDropdown.setVisibility(View.GONE);

        switchRfid.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                CommScanner scanner = getScannerInstance();
                boolean isRfidReady = (scanner != null && scanner.getRFIDScanner() != null);
                if (!isRfidReady) {
                    showSagaFeedback("HT not Connected to Reader RFID", false);
                    switchRfid.setChecked(false);
                    return;
                }
                btnPowerDropdown.setVisibility(View.VISIBLE);
            } else {
                btnPowerDropdown.setVisibility(View.GONE);
            }
            showSagaFeedback(isChecked ? "Mode RFID: ON" : "Mode RFID: OFF", true);
        });

        // Power options: 10–27 dBm (sesuai range SDK 4–30)
        List<String> powerList = new ArrayList<>(Arrays.asList(
                "10 dBm", "15 dBm", "20 dBm", "25 dBm", "27 dBm"
        ));

        btnPowerDropdown.setOnClickListener(v ->
                showPowerDropdownPopup(btnPowerDropdown, powerList, tvPowerLevel));
    }

    public void applyRfidPower(int dbm) {
        CommScanner scanner = getScannerInstance();
        if (scanner == null || scanner.getRFIDScanner() == null) {
            showError("RFID Reader not connected.");
            return;
        }
        // Clamp ke range valid SDK (4–30 dBm)
        int safePower = Math.max(4, Math.min(30, dbm));
        try {
            RFIDScannerSettings settings = scanner.getRFIDScanner().getSettings();
            settings.scan.powerLevelRead  = safePower;
            settings.scan.powerLevelWrite = safePower;
            scanner.getRFIDScanner().setSettings(settings);
            showSuccess("RFID Power set to " + safePower + " dBm");
        } catch (Exception e) {
            showError("Failed to set power: " + e.getMessage());
        }
    }

    // ── RFID Reader Indicator ─────────────────────────────────────

    /**
     * Update indikator koneksi RFID reader.
     * Panggil di onResume() activity yang pakai RFID.
     *
     * @param dotView  View bulat kecil (dot indicator) — warna berubah hijau/merah
     * @param tvStatus TextView label status (boleh null kalau tidak ada)
     */
    public void updateRfidIndicator(View dotView, TextView tvStatus) {
        CommScanner scanner   = getScannerInstance();
        boolean isConnected   = (scanner != null && scanner.getRFIDScanner() != null);

        int color = Color.parseColor(isConnected ? "#4CAF50" : "#F44336");
        String label = isConnected ? "RFID: Connected" : "RFID: Disconnected";

        if (dotView != null) {
            dotView.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(color));
        }
        if (tvStatus != null) {
            tvStatus.setText(label);
            tvStatus.setTextColor(color);
        }
    }

    public void updateReaderBattery(ImageView ivBattery) {
        if (ivBattery == null) return;
        CommScanner scanner = getScannerInstance();
        if (scanner == null) {
            ivBattery.setVisibility(View.GONE);
            return;
        }
        try {
            CommConst.CommBattery battery = scanner.getRemainingBattery();
            ivBattery.setVisibility(View.VISIBLE);
            int color;
            if      (battery == CommConst.CommBattery.UNDER10) color = Color.parseColor("#F44336");
            else if (battery == CommConst.CommBattery.UNDER40) color = Color.parseColor("#FFC107");
            else                                                color = Color.parseColor("#4CAF50");
            ivBattery.setColorFilter(color);
        } catch (Exception e) {
            ivBattery.setVisibility(View.GONE);
        }
    }
    public void updateReaderBattery(ImageView ivBattery, boolean switchOn) {
        if (ivBattery == null) return;
        if (!switchOn) {
            ivBattery.setVisibility(View.GONE);
            return;
        }
        updateReaderBattery(ivBattery);
    }

    // ── Power Popup ───────────────────────────────────────────────

    protected void showPowerDropdownPopup(View anchor, List<String> items, TextView tvPowerLevel) {
        View popupView = getLayoutInflater().inflate(R.layout.dropdown_popup, null);
        RecyclerView rv = popupView.findViewById(R.id.rvDropdown);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setNestedScrollingEnabled(true);

        rv.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                View v = getLayoutInflater().inflate(R.layout.item_dropdown, parent, false);
                return new RecyclerView.ViewHolder(v) {};
            }

            @Override
            public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                TextView tv = holder.itemView.findViewById(R.id.tvDropdownItem);
                tv.setText(items.get(position));
                holder.itemView.setOnClickListener(v -> {
                    String selected = items.get(position);
                    tvPowerLevel.setText(selected);

                    // ← Apply ke SDK (parse "10 dBm" → 10)
                    try {
                        int dbm = Integer.parseInt(selected.replace(" dBm", "").trim());
                        applyRfidPower(dbm);
                    } catch (NumberFormatException ignored) {}

                    if (activePowerPopup != null) activePowerPopup.dismiss();
                });
            }

            @Override
            public int getItemCount() { return items.size(); }
        });

        int itemHeightPx = (int)(56 * getResources().getDisplayMetrics().density);
        int maxHeight    = itemHeightPx * 4;

        PopupWindow popup = new PopupWindow(
                popupView,
                anchor.getWidth(),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
        );
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setElevation(16f);
        popup.setOutsideTouchable(true);

        popupView.measure(
                View.MeasureSpec.makeMeasureSpec(anchor.getWidth(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );
        popup.setHeight(Math.min(popupView.getMeasuredHeight(), maxHeight));
        popup.showAsDropDown(anchor, 0, 6);
        activePowerPopup = popup;
    }

    // ── Battery ───────────────────────────────────────────────────

    public int getHTBatteryLevel() {
        BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
    }

    // ── Lifecycle ─────────────────────────────────────────────────

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (toneGen != null) {
            toneGen.release();
            toneGen = null;
        }
    }
}