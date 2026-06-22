package com.example.inventory_system_ht.activity;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.TextView;

import com.example.inventory_system_ht.util.LogManager;
import com.example.inventory_system_ht.util.ScannerManager;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import androidx.activity.OnBackPressedCallback;
import androidx.cardview.widget.CardView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.densowave.scannersdk.Common.CommManager;
import com.densowave.scannersdk.Common.CommScanner;
import com.example.inventory_system_ht.MyApplication;
import com.example.inventory_system_ht.activity.base.ScannerActivity;
import com.example.inventory_system_ht.util.PrefManager;
import com.example.inventory_system_ht.R;

public class HomeActivity extends ScannerActivity {

    private CardView btnStockIn, btnStockPrep, btnStockTaking, btnTagRegis, btnSearchItem, btnSettingRfid;
    private TextView tvNamaOperator, tvRoleOperator;
    private ImageButton btnLogout;
    private PrefManager prefManager;

    @Override
    protected CommScanner getScannerInstance() { return ScannerManager.getInstance().getScanner(); }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.statusBarSpacer), (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
            );
            v.getLayoutParams().height = bars.top;
            v.requestLayout();
            return insets;
        });

        prefManager = new PrefManager(this);
        if (!prefManager.isSessionValid()) {
            redirectToLogin();
            return;
        }

        initViews();
        setupUserInfo();
        setupListeners();

        FloatingActionButton fabLog = findViewById(R.id.fabLog);
        if (fabLog != null) {
            fabLog.setOnClickListener(v -> {
                Intent i = new Intent(this, LogActivity.class);
                i.putExtra(LogActivity.EXTRA_MENU, "Home");
                startActivity(i);
            });
        }

        LogManager.get(this).log(LogManager.INFO, LogManager.ACTION_OPEN, "Home", "", "Opened Home", prefManager.getUserId());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!prefManager.isSessionValid()) {
            redirectToLogin();
            return;
        }
        if (!ScannerManager.getInstance().isConnected()) {
            ((MyApplication) getApplication()).startScannerAccept();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        CommManager.endAccept();
    }

    private void initViews() {
        btnStockIn = findViewById(R.id.ButtonStockIn);
        btnStockPrep = findViewById(R.id.ButtonStockPreparation);
        btnStockTaking = findViewById(R.id.ButtonStockTaking);
        btnTagRegis = findViewById(R.id.ButtonTagRegis);
        btnSearchItem = findViewById(R.id.ButtonSearchItem);
        btnSettingRfid = findViewById(R.id.ButtonSettingRfid);
        tvNamaOperator = findViewById(R.id.textViewNamaOperator);
        tvRoleOperator = findViewById(R.id.textViewRoleOperator);
        btnLogout = findViewById(R.id.btnLogout);
    }

    private void setupUserInfo() {
        tvNamaOperator.setText("Welcome " + prefManager.getFullName());
        tvRoleOperator.setText(prefManager.getRoleName());
    }

    private void setupListeners() {
        btnLogout.setOnClickListener(v -> showLogoutDialog());

        View.OnClickListener menuClickListener = v -> {
            int id = v.getId();
            if (id == R.id.ButtonSettingRfid) {
                startActivity(new Intent(this, RfidSettingActivity.class));
                return;
            }

            if (!isNetworkConnected()) showWarning("No internet connection");

            Intent intent = null;

            if (id == R.id.ButtonStockIn) intent = new Intent(this, StockInActivity.class);
            else if (id == R.id.ButtonStockPreparation) intent = new Intent(this, StockPrepActivity.class);
            else if (id == R.id.ButtonStockTaking) intent = new Intent(this, StockTakingListActivity.class);
            else if (id == R.id.ButtonTagRegis) intent = new Intent(this, TagRegistrationActivity.class);
            else if (id == R.id.ButtonSearchItem) intent = new Intent(this, SearchItemActivity.class);

            if (intent != null) startActivity(intent);
        };

        btnStockIn.setOnClickListener(menuClickListener);
        btnStockPrep.setOnClickListener(menuClickListener);
        btnStockTaking.setOnClickListener(menuClickListener);
        btnTagRegis.setOnClickListener(menuClickListener);
        btnSearchItem.setOnClickListener(menuClickListener);
        btnSettingRfid.setOnClickListener(menuClickListener);

        MaterialCardView cardFabLog = findViewById(R.id.cardFabLog);

        ViewCompat.setOnApplyWindowInsetsListener(cardFabLog, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            int dp16 = (int)(16 * getResources().getDisplayMetrics().density);
            params.bottomMargin = bars.bottom + dp16;
            params.rightMargin = dp16;
            v.setLayoutParams(params);

            int fabHeight = (int)(40 * getResources().getDisplayMetrics().density);
            int spacerHeight = fabHeight + bars.bottom + dp16 * 2;

            return insets;
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                Dialog dialog = new Dialog(HomeActivity.this);
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                dialog.setContentView(R.layout.dialog_confirm);
                if (dialog.getWindow() != null) {
                    dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                    dialog.getWindow().setLayout(
                            (int) (getResources().getDisplayMetrics().widthPixels * 0.85),
                            ViewGroup.LayoutParams.WRAP_CONTENT);
                }
                ((TextView) dialog.findViewById(R.id.tvConfirmMessage)).setText("Are you sure you want to go out?");
                dialog.findViewById(R.id.btnConfirmNo).setOnClickListener(v -> dialog.dismiss());
                dialog.findViewById(R.id.btnConfirmYes).setOnClickListener(v -> { dialog.dismiss(); finish(); });
                dialog.show();
            }
        });
    }

    private void showLogoutDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_alert_logout);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        dialog.findViewById(R.id.btnNo).setOnClickListener(v -> dialog.dismiss());
        dialog.findViewById(R.id.btnYes).setOnClickListener(v -> {
            dialog.dismiss();
            prefManager.clearSession();
            redirectToLogin();
        });

        dialog.show();
    }

    private void redirectToLogin() {
        prefManager.clearSession();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
