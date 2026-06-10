package com.example.inventory_system_ht.activity;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;
import com.google.android.material.card.MaterialCardView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import com.densowave.scannersdk.Common.CommScanner;
import com.example.inventory_system_ht.activity.base.ScannerActivity;
import com.example.inventory_system_ht.model.AuthModel;
import com.example.inventory_system_ht.model.GeneralResponse;
import com.example.inventory_system_ht.network.ApiClient;
import com.example.inventory_system_ht.network.ApiService;
import com.example.inventory_system_ht.util.LogManager;
import com.example.inventory_system_ht.util.PrefManager;
import com.example.inventory_system_ht.R;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends ScannerActivity {

    private ImageButton btnSetting;
    private Button btnLogin;
    private EditText etUsername, etPassword;
    private PrefManager prefManager;

    @Override
    protected CommScanner getScannerInstance() { return null; }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.layout_root), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), systemBars.top + 10, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });

        MaterialCardView cardFabLog = findViewById(R.id.cardFabLog);
        ViewCompat.setOnApplyWindowInsetsListener(cardFabLog, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            params.bottomMargin = systemBars.bottom + 16;
            params.rightMargin = systemBars.right + 16;
            v.setLayoutParams(params);
            return insets;
        });

        prefManager = new PrefManager(this);

        if (prefManager.isSessionValid()) {
            startActivity(new Intent(this, HomeActivity.class));
            finish();
            return;
        }

        initViews();
        setupListeners();
    }

    private void initViews() {
        btnSetting = findViewById(R.id.btnSetting);
        btnLogin = findViewById(R.id.btnLogin);
        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
    }

    private void setupListeners() {
        btnLogin.setOnClickListener(v -> performLogin());
        btnSetting.setOnClickListener(v -> showSettingDialog());

        FloatingActionButton fabLog = findViewById(R.id.fabLog);
        if (fabLog != null) {
            fabLog.setOnClickListener(v -> {
                Intent i = new Intent(this, LogActivity.class);
                i.putExtra(LogActivity.EXTRA_MENU, "Login");
                startActivity(i);
            });
        }
    }

    private void performLogin() {
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (username.isEmpty() || password.isEmpty()) {
            LogManager.get(this).log(LogManager.WARNING, LogManager.ACTION_LOGIN,
                    "Login", username, "Login attempt: username or password is empty", "");
            showSagaFeedback("Username & password required", 1);
            return;
        }

        if (!isNetworkConnected()) {
            LogManager.get(this).log(LogManager.WARNING, LogManager.ACTION_LOGIN,
                    "Login", username, "Login attempt: no internet connection", "");
            showSagaFeedback("No internet connection", 1);
            return;
        }

        showLoading();

        String reqJson = "{\"username\":\"" + username + "\",\"password\":\"***\"}";

        ApiService apiService = ApiClient.getClient(this).create(ApiService.class);
        apiService.login(new AuthModel.LoginRequest(username, password))
                .enqueue(new Callback<AuthModel.LoginResponse>() {
                    @Override
                    public void onResponse(Call<AuthModel.LoginResponse> call,
                                           Response<AuthModel.LoginResponse> response) {
                        hideLoading();
                        String resJson = "{\"http_code\":" + response.code() + "}";
                        if (response.isSuccessful() && response.body() != null) {
                            LogManager.get(LoginActivity.this).log(LogManager.INFO, LogManager.ACTION_LOGIN,
                                    "Login", username, "Login success: " + username, "", reqJson, resJson);
                            handleLoginSuccess(response.body());
                        } else {
                            String msg = response.code() == 401 ? "Invalid username or password" : "Username or password is incorrect";
                            LogManager.get(LoginActivity.this).log(LogManager.WARNING, LogManager.ACTION_LOGIN,
                                    "Login", username, "Login failed: " + msg, "", reqJson, resJson);
                            showError(msg);
                        }
                    }

                    @Override
                    public void onFailure(Call<AuthModel.LoginResponse> call, Throwable t) {
                        hideLoading();
                        String resJson = "{\"error\":\"" + t.getMessage() + "\"}";
                        LogManager.get(LoginActivity.this).log(LogManager.ERROR, LogManager.ACTION_LOGIN,
                                "Login", username, "Login error: " + t.getMessage(), "", reqJson, resJson);
                        handleFailure(t);
                    }
                });
    }

    private void handleLoginSuccess(AuthModel.LoginResponse body) {
        String token = body.getToken();
        String username = body.getUser();

        if (token == null || token.isEmpty() || username == null) {
            LogManager.get(this).log(LogManager.ERROR, LogManager.ACTION_LOGIN,
                    "Login", "", "Invalid server response after successful login", "");
            showSagaFeedback("Invalid server response", 2);
            return;
        }

        String roleCode = (body.getRoles() != null && !body.getRoles().isEmpty())
                ? body.getRoles().get(0) : "";

        String permissionsJson = new com.google.gson.Gson()
                .toJson(body.getPermissions() != null ? body.getPermissions() : new java.util.ArrayList<>());

        prefManager.saveUserSession(token, "", username, username, roleCode, permissionsJson);

        Intent intent = new Intent(this, HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private boolean isValidUrl(String url) {
        return url.startsWith("http://") || url.startsWith("https://");
    }

    private void showSettingDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_setting);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        TextView tvSettingStatus = dialog.findViewById(R.id.tvSettingStatus);
        java.util.function.BiConsumer<String, Integer> showStatus = (msg, type) -> {
            int color;
            switch (type) {
                case 1: color = 0xFFF57C00; break;
                case 2: color = 0xFFE74C3C; break;
                default: color = 0xFF4CAF50; break;
            }
            tvSettingStatus.setText(msg);
            tvSettingStatus.setTextColor(color);
            tvSettingStatus.setVisibility(View.VISIBLE);
            LogManager.get(LoginActivity.this).log(
                    type == 2 ? LogManager.ERROR : type == 1 ? LogManager.WARNING : LogManager.INFO,
                    LogManager.ACTION_MESSAGE, "LoginActivity", "", msg, prefManager.getUserId());
        };
        java.util.function.Consumer<String> dWarn = msg -> showStatus.accept(msg, 1);
        java.util.function.Consumer<String> dError = msg -> showStatus.accept(msg, 2);
        java.util.function.Consumer<String> dSuccess = msg -> showStatus.accept(msg, 0);

        EditText etIpAPI = dialog.findViewById(R.id.etIpAPI);
        Button btnCancel = dialog.findViewById(R.id.btnCancel);
        Button btnApplyIp = dialog.findViewById(R.id.btnApplyIp);
        ImageButton btnCekIp = dialog.findViewById(R.id.buttonCekIp);

        etIpAPI.setText(prefManager.getIp());
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnCekIp.setOnClickListener(v -> {
            String ip = etIpAPI.getText().toString().trim();
            if (ip.isEmpty()) { dWarn.accept("Server IP is empty"); return; }
            if (!isValidUrl(ip)) { dError.accept("Wrong format, must start with http:// or https://"); return; }

            showLoading();
            prefManager.saveIp(ip);
            String pingReq = "{\"action\":\"ping\"}";
            ApiClient.getClient(this).create(ApiService.class)
                    .ping().enqueue(new Callback<GeneralResponse>() {
                        @Override
                        public void onResponse(Call<GeneralResponse> call, Response<GeneralResponse> response) {
                            hideLoading();
                            String pingRes = "{\"http_code\":" + response.code() + "}";
                            if (response.isSuccessful()) {
                                LogManager.get(LoginActivity.this).log(LogManager.INFO, LogManager.ACTION_SETTING,
                                        "Setting", "Ping", "Ping success", "", pingReq, pingRes);
                                dSuccess.accept("Server connected");
                            } else {
                                LogManager.get(LoginActivity.this).log(LogManager.WARNING, LogManager.ACTION_SETTING,
                                        "Setting", "Ping", "Ping failed: HTTP " + response.code(), "", pingReq, pingRes);
                                dError.accept("Server error: " + response.code());
                            }
                        }

                        @Override
                        public void onFailure(Call<GeneralResponse> call, Throwable t) {
                            hideLoading();
                            String pingRes = "{\"error\":\"" + t.getMessage() + "\"}";
                            LogManager.get(LoginActivity.this).log(LogManager.ERROR, LogManager.ACTION_SETTING,
                                    "Setting", "Ping", "Ping error: " + t.getMessage(), "", pingReq, pingRes);
                            dError.accept("Cannot connect to server");
                        }
                    });
        });

        btnApplyIp.setOnClickListener(v -> {
            String ip = etIpAPI.getText().toString().trim();
            if (ip.isEmpty()) { dWarn.accept("Server IP is empty"); return; }
            if (!isValidUrl(ip)) { dError.accept("Wrong format, must start with http:// or https://"); return; }

            prefManager.saveIp(ip);
            LogManager.get(this).log(LogManager.INFO, LogManager.ACTION_SETTING,
                    "Setting", "API URL", "API URL saved", prefManager.getUserId());
            dSuccess.accept("URL saved");
            dialog.dismiss();
        });

        dialog.show();
    }
}
