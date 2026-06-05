package com.example.inventory_system_ht.activity.base;

import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
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
import android.os.Handler;
import android.os.Looper;
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
    private Dialog loadingDialog;
    private ToneGenerator toneGen;
    private Vibrator vibrator;
    private PopupWindow activePowerPopup;

    // ─── FAB Auto-Fade (like iPhone shortcuts) ────────────────────────────────
    private static final long FAB_HIDE_DELAY_MS = 5000L;
    private final Handler fabHideHandler = new Handler(Looper.getMainLooper());
    private View[] fabAutoHideViews;
    private boolean fabsCurrentlyVisible = true;
    private final Runnable fabHideRunnable = () -> {
        if (fabAutoHideViews == null) return;
        fabsCurrentlyVisible = false;
        for (View v : fabAutoHideViews) v.animate().alpha(0.15f).setDuration(500).start();
    };

    protected abstract CommScanner getScannerInstance();

    // ─── Lifecycle ────────────────────────────────────────────────────────────
    @Override
    protected void onResume() {
        super.onResume();
        setupFabsAutoHideIfPresent();
    }

    @Override
    protected void onPause() {
        super.onPause();
        fabHideHandler.removeCallbacks(fabHideRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        fabHideHandler.removeCallbacksAndMessages(null);
        if (toneGen != null) {
            toneGen.release();
            toneGen = null;
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) resetFabTimer();
        return super.dispatchTouchEvent(ev);
    }

    // ─── FAB Auto-Fade helpers ────────────────────────────────────────────────
    private void setupFabsAutoHideIfPresent() {
        View logCard = findViewById(R.id.cardFabLog);
        View camCard = findViewById(R.id.cardFabCamera);
        if (logCard == null && camCard == null) return;
        List<View> views = new ArrayList<>();
        if (logCard != null) views.add(logCard);
        if (camCard != null) views.add(camCard);
        fabAutoHideViews = views.toArray(new View[0]);
        fabsCurrentlyVisible = true;
        for (View v : fabAutoHideViews) v.setAlpha(1f);
        scheduleFabHide();
    }

    private void scheduleFabHide() {
        fabHideHandler.removeCallbacks(fabHideRunnable);
        fabHideHandler.postDelayed(fabHideRunnable, FAB_HIDE_DELAY_MS);
    }

    private void resetFabTimer() {
        if (fabAutoHideViews == null) return;
        if (!fabsCurrentlyVisible) {
            fabsCurrentlyVisible = true;
            for (View v : fabAutoHideViews) v.animate().alpha(1f).setDuration(200).start();
        }
        scheduleFabHide();
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
    private int getTopInset() {
        View decorView = getWindow().getDecorView();
        WindowInsetsCompat insets = androidx.core.view.ViewCompat.getRootWindowInsets(decorView);
        if (insets != null) {
            androidx.core.graphics.Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
            );
            return bars.top;
        }
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return resourceId > 0 ? getResources().getDimensionPixelSize(resourceId) : 0;
    }

    public void showSagaFeedback(String pesan, boolean isSuccess) {
        showSagaFeedback(pesan, isSuccess ? 0 : 2);
    }

    public void showSagaFeedback(String pesan, int type) {
        FrameLayout rootLayout = findViewById(android.R.id.content);
        showSagaFeedback(rootLayout, pesan, type);
    }

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

        int topInset = getTopInset();
        int sidePx = (int)(20 * getResources().getDisplayMetrics().density);
        int topMargin = topInset + (int)(8 * getResources().getDisplayMetrics().density);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.TOP;
        params.setMargins(sidePx, topMargin, sidePx, 0);
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

        String level = (type == 2) ? LogManager.ERROR : (type == 1) ? LogManager.WARNING : LogManager.INFO;
        LogManager.get(this).log(level, LogManager.ACTION_MESSAGE,
                getClass().getSimpleName(), "", pesan, new PrefManager(this).getUserId());

        FrameLayout wrapper = new FrameLayout(this);
        int sidePx = (int)(15 * getResources().getDisplayMetrics().density);
        wrapper.setPadding(sidePx, 0, sidePx, 0);
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
        params.y = getTopInset() + (int)(8 * getResources().getDisplayMetrics().density);

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
        updateReaderBattery(ivBattery);
    }

    public int getHTBatteryLevel() {
        BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
    }

    // ─── Utility ──────────────────────────────────────────────────────────────
    protected int parsePower(String text, int defaultVal) {
        try { return Integer.parseInt(text.replace(" dBm", "").trim()); }
        catch (NumberFormatException e) { return defaultVal; }
    }

    protected int indexOfPower(int p) {
        int[] values = {5, 10, 15, 18, 21, 24, 27, 30};
        for (int i = 0; i < values.length; i++) if (values[i] == p) return i;
        return 6;
    }
}
