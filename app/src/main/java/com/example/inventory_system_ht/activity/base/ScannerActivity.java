package com.example.inventory_system_ht.activity.base;

import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
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
import android.view.WindowManager;
import android.widget.FrameLayout;
import androidx.appcompat.app.AppCompatActivity;

import com.densowave.scannersdk.Common.CommScanner;
import com.densowave.scannersdk.Const.CommConst;
import com.densowave.scannersdk.Dto.RFIDScannerSettings;
import com.example.inventory_system_ht.activity.LoginActivity;
import com.example.inventory_system_ht.util.LogManager;
import com.example.inventory_system_ht.util.PrefManager;
import com.example.inventory_system_ht.R;

public abstract class ScannerActivity extends AppCompatActivity {

    // ─── Fields ───────────────────────────────────────────────────────────────
    private Dialog loadingDialog;
    private ToneGenerator toneGen;
    private Vibrator vibrator;
    private PopupWindow activePowerPopup;

    // ─── Abstract ─────────────────────────────────────────────────────────────
    protected abstract CommScanner getScannerInstance();

    // ─── Lifecycle ────────────────────────────────────────────────────────────
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (toneGen != null) {
            toneGen.release();
            toneGen = null;
        }
    }

    // ─── Network ──────────────────────────────────────────────────────────────

    public boolean isNetworkConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkCapabilities cap = cm.getNetworkCapabilities(cm.getActiveNetwork());
            return cap != null && (cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    cap.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR));
        }
        return false;
    }

    // ─── UI Feedback ──────────────────────────────────────────────────────────

    // Shortcut: true = success, false = error
    public void showSagaFeedback(String pesan, boolean isSuccess) {
        showSagaFeedback(pesan, isSuccess ? 0 : 2);
    }

    // Banner ke Activity root (normal usage)
    public void showSagaFeedback(String pesan, int type) {
        FrameLayout rootLayout = findViewById(android.R.id.content);
        showSagaFeedback(rootLayout, pesan, type);
    }

    // Banner ke ViewGroup custom (dipakai internal)
    public void showSagaFeedback(ViewGroup root, String pesan, int type) {
        View bannerView = getLayoutInflater().inflate(R.layout.layout_message_banner, root, false);
        ImageView dot = bannerView.findViewById(R.id.dotIndicator);
        TextView tvMessage = bannerView.findViewById(R.id.tvBannerMessage);

        switch (type) {
            case 1: dot.setImageResource(R.drawable.dot_warning); break;
            case 2: dot.setImageResource(R.drawable.dot_error); break;
            default: dot.setImageResource(R.drawable.dot_success); break;
        }
        tvMessage.setText(pesan);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.TOP;
        params.setMargins(15, 15, 15, 0);
        bannerView.setLayoutParams(params);

        bannerView.setAlpha(0f);
        bannerView.setTranslationY(-60f);
        root.addView(bannerView);

        bannerView.animate().alpha(1f).translationY(0f).setDuration(200)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.2f)).start();

        bannerView.postDelayed(() ->
                bannerView.animate().alpha(0f).translationY(-40f).setDuration(250)
                        .withEndAction(() -> root.removeView(bannerView)).start(), 2000);
    }

    // Banner overlay via WindowManager — muncul di atas dialog sekalipun
    public void showBannerOverlay(String pesan, int type) {
        View bannerView = getLayoutInflater().inflate(R.layout.layout_message_banner, null);
        ImageView dot = bannerView.findViewById(R.id.dotIndicator);
        TextView tvMessage = bannerView.findViewById(R.id.tvBannerMessage);

        switch (type) {
            case 1: dot.setImageResource(R.drawable.dot_warning); break;
            case 2: dot.setImageResource(R.drawable.dot_error); break;
            default: dot.setImageResource(R.drawable.dot_success); break;
        }
        tvMessage.setText(pesan);

        FrameLayout wrapper = new FrameLayout(this);
        int px = (int)(15 * getResources().getDisplayMetrics().density);
        wrapper.setPadding(px, 0, px, 0);
        wrapper.addView(bannerView);

        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP;
        params.y = px;

        wrapper.setAlpha(0f);
        wrapper.setTranslationY(-60f);
        wm.addView(wrapper, params);

        wrapper.animate().alpha(1f).translationY(0f).setDuration(200)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.2f)).start();

        wrapper.postDelayed(() ->
                wrapper.animate().alpha(0f).translationY(-40f).setDuration(250)
                        .withEndAction(() -> {
                            try { wm.removeView(wrapper); } catch (Exception ignored) {}
                        }).start(), 2000);
    }

    public void showSuccess(String pesan) {
        showSagaFeedback(pesan, 0);
        LogManager.get(this).log(
                LogManager.INFO,
                LogManager.ACTION_MESSAGE,
                getClass().getSimpleName(),
                "",
                pesan,
                new PrefManager(this).getUserId()
        );
    }

    public void showError(String pesan) {
        showSagaFeedback(pesan, 2);
        LogManager.get(this).log(
                LogManager.ERROR,
                LogManager.ACTION_MESSAGE,
                getClass().getSimpleName(),
                "",
                pesan,
                new PrefManager(this).getUserId()
        );
    }

    public void showWarning(String pesan) {
        showSagaFeedback(pesan, 1);
        LogManager.get(this).log(
                LogManager.WARNING,
                LogManager.ACTION_MESSAGE,
                getClass().getSimpleName(),
                "",
                pesan,
                new PrefManager(this).getUserId()
        );
    }

    // ─── Loading Dialog ───────────────────────────────────────────────────────

    public void showLoading() {
        if (loadingDialog == null) {
            loadingDialog = new Dialog(this);
            loadingDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            loadingDialog.setContentView(R.layout.dialog_loading);
            loadingDialog.setCancelable(false);
            if (loadingDialog.getWindow() != null) {
                loadingDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                loadingDialog.getWindow().setLayout(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        }
        if (!loadingDialog.isShowing()) loadingDialog.show();
    }

    public void hideLoading() {
        if (loadingDialog != null && loadingDialog.isShowing()) loadingDialog.dismiss();
    }

    // ─── API Error Handling ───────────────────────────────────────────────────

    public void handleApiError(int statusCode) {
        hideLoading();
        if (statusCode == 401) {
            showSagaFeedback("Session expired", false);
            new PrefManager(this).clearSession();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        } else if (statusCode == 403) {
            showSagaFeedback("Access denied", false);
        } else if (statusCode == 404) {
            showSagaFeedback("Data not found", false);
        } else if (statusCode >= 500) {
            showSagaFeedback("Server error, try again", false);
        } else {
            showSagaFeedback("Request failed", false);
        }
    }

    public void handleApiError(retrofit2.Response<?> response) {
        hideLoading();
        int statusCode = response.code();
        if (statusCode == 401) {
            new PrefManager(this).clearSession();
            showSagaFeedback("Session expired", false);
            Intent intent = new Intent(this, com.example.inventory_system_ht.activity.LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }
        String msg = com.example.inventory_system_ht.network.ErrorParser.getMessage(response);
        showSagaFeedback(msg, false);
    }

    public void handleFailure(Throwable t) {
        hideLoading();
        if (t instanceof java.net.SocketTimeoutException) {
            showSagaFeedback("Connection timeout", false);
        } else if (t instanceof java.net.ConnectException) {
            showSagaFeedback("Server unreachable", false);
        } else if (t instanceof java.io.IOException) {
            showSagaFeedback("Network error", false);
        } else {
            showSagaFeedback("Unexpected error", false);
        }
    }

    // ─── Scan Feedback ────────────────────────────────────────────────────────

    public void playScanFeedback(int type) {
        if (toneGen == null) toneGen = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
        if (vibrator == null) vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        switch (type) {
            case 0:
                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 50);
                break;
            case 1:
                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 100);
                break;
            case 2:
                toneGen.startTone(ToneGenerator.TONE_CDMA_HIGH_L, 200);
                if (vibrator != null && vibrator.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE));
                    } else {
                        vibrator.vibrate(300);
                    }
                }
                break;
        }
    }

    // ─── RFID Hardware ────────────────────────────────────────────────────────

    public void applyRfidPower(int dbm) {
        CommScanner scanner = getScannerInstance();
        if (scanner == null || scanner.getRFIDScanner() == null) {
            showError("RFID not connected");
            return;
        }
        int safePower = Math.max(4, Math.min(30, dbm));
        try {
            RFIDScannerSettings settings = scanner.getRFIDScanner().getSettings();
            settings.scan.powerLevelRead = safePower;
            settings.scan.powerLevelWrite = safePower;
            scanner.getRFIDScanner().setSettings(settings);
            showSuccess("Power: " + safePower + " dBm");
        } catch (Exception e) {
            showError("Set power failed");
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
            if (battery == CommConst.CommBattery.UNDER10) color = Color.parseColor("#F44336");
            else if (battery == CommConst.CommBattery.UNDER40) color = Color.parseColor("#FFC107");
            else color = Color.parseColor("#4CAF50");
            ivBattery.setColorFilter(color);
        } catch (Exception e) {
            ivBattery.setVisibility(View.GONE);
        }
    }

    public void updateReaderBattery(ImageView ivBattery, boolean switchOn) {
        if (ivBattery == null) return;
        if (!switchOn) { ivBattery.setVisibility(View.GONE); return; }
        updateReaderBattery(ivBattery);
    }

    public int getHTBatteryLevel() {
        BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
    }

    // ─── Dropdown Popup ───────────────────────────────────────────────────────

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
                    try { applyRfidPower(parsePower(selected, 20)); }
                    catch (NumberFormatException ignored) {}
                    if (activePowerPopup != null) activePowerPopup.dismiss();
                });
            }

            @Override
            public int getItemCount() { return items.size(); }
        });

        int itemHeightPx = (int)(56 * getResources().getDisplayMetrics().density);
        int maxHeight = itemHeightPx * 4;

        PopupWindow popup = new PopupWindow(
                popupView, anchor.getWidth(), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setElevation(16f);
        popup.setOutsideTouchable(true);

        popupView.measure(
                View.MeasureSpec.makeMeasureSpec(anchor.getWidth(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        popup.setHeight(Math.min(popupView.getMeasuredHeight(), maxHeight));
        popup.showAsDropDown(anchor, 0, 6);
        activePowerPopup = popup;
    }

    // ─── Utility ──────────────────────────────────────────────────────────────

    protected int parsePower(String text, int defaultVal) {
        try { return Integer.parseInt(text.replace(" dBm", "").trim()); }
        catch (NumberFormatException e) { return defaultVal; }
    }
}