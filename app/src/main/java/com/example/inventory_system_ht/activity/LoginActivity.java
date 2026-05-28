package com.example.inventory_system_ht.activity;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
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
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.layout_header), (v, insets) -> {
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
                    "Login", username, "Login attempt: username atau password kosong", "");
            showSagaFeedback("Username & password required", 1);
            return;
        }

        if (!isNetworkConnected()) {
            LogManager.get(this).log(LogManager.WARNING, LogManager.ACTION_LOGIN,
                    "Login", username, "Login attempt: tidak ada koneksi internet", "");
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
                        String resJson = "{\"http_code\":" + response.code() + ",\"success\":"
                                + (response.body() != null && response.body().isSuccess()) + "}";
                        if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
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
        AuthModel.UserModel user = body.getUser();

        if (token == null || token.isEmpty() || user == null) {
            LogManager.get(this).log(LogManager.ERROR, LogManager.ACTION_LOGIN,
                    "Login", "", "Server response tidak valid setelah login berhasil", "");
            showSagaFeedback("Invalid server response", 2);
            return;
        }

        String fullName = (user.getUsrFullname() != null && !user.getUsrFullname().trim().isEmpty())
                ? user.getUsrFullname()
                : user.getUsrName();

        String permissionsJson = new com.google.gson.Gson()
                .toJson(user.getPermissions() != null ? user.getPermissions() : new java.util.ArrayList<>());

        prefManager.saveUserSession(token, user.getUsrId(), user.getUsrName(), fullName,
                user.getPrimaryRole(), permissionsJson);

        Intent intent = new Intent(this, HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showSettingDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_setting);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        java.util.function.Consumer<String> dWarn = msg -> showBannerOverlay(msg, 1);
        java.util.function.Consumer<String> dError = msg -> showBannerOverlay(msg, 2);
        java.util.function.Consumer<String> dSuccess = msg -> showBannerOverlay(msg, 0);

        EditText etIpAPI = dialog.findViewById(R.id.etIpAPI);
        Button btnCancel = dialog.findViewById(R.id.btnCancel);
        Button btnApplyIp = dialog.findViewById(R.id.btnApplyIp);
        ImageButton btnCekIp = dialog.findViewById(R.id.buttonCekIp);

        etIpAPI.setText(prefManager.getIp());
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnCekIp.setOnClickListener(v -> {
            String ip = etIpAPI.getText().toString().trim();
            if (ip.isEmpty()) { dWarn.accept("Server IP is empty"); return; }
            if (!ip.startsWith("http://")) { dError.accept("Wrong format, must start with http://"); return; }

            showLoading();
            prefManager.saveIp(ip);
            String pingReq = "{\"url\":\"" + ip + "\"}";
            ApiClient.getClient(this).create(ApiService.class)
                    .ping().enqueue(new Callback<GeneralResponse>() {
                        @Override
                        public void onResponse(Call<GeneralResponse> call, Response<GeneralResponse> response) {
                            hideLoading();
                            String pingRes = "{\"http_code\":" + response.code() + "}";
                            if (response.isSuccessful()) {
                                LogManager.get(LoginActivity.this).log(LogManager.INFO, LogManager.ACTION_SETTING,
                                        "Setting", "Ping", "Ping success: " + ip, "", pingReq, pingRes);
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

            prefManager.saveIp(ip);
            LogManager.get(this).log(LogManager.INFO, LogManager.ACTION_SETTING,
                    "Setting", "API URL", "URL API disimpan: " + prefManager.getIp(), prefManager.getUserId());
            dSuccess.accept("URL saved");
            dialog.dismiss();
        });

        dialog.show();
    }
}