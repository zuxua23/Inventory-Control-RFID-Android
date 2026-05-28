package com.example.inventory_system_ht.activity;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.densowave.scannersdk.Common.CommScanner;
import com.example.inventory_system_ht.R;
import com.example.inventory_system_ht.activity.base.ScannerActivity;
import com.example.inventory_system_ht.adapter.LogAdapter;
import com.example.inventory_system_ht.database.AppDatabase;
import com.example.inventory_system_ht.entity.AppLogEntity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LogActivity extends ScannerActivity {

    public static final String EXTRA_MENU = "extra_menu";

    private EditText etSearch;
    private Spinner spinnerLevel, spinnerAction, spinnerMenu, spinnerDate;
    private RecyclerView rvLogs;
    private TextView tvEmpty, tvLogCount;
    private ImageView btnBack;

    private LogAdapter adapter;
    private final List<AppLogEntity> logList = new ArrayList<>();
    private AppDatabase db;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final List<String> levelOptions = Arrays.asList("All Levels", "INFO", "WARNING", "ERROR");
    private final List<String> actionOptions = Arrays.asList("All Actions", "OPEN", "SCAN", "READ", "SUBMIT", "DELETE", "CREATE", "LOGIN", "LOGOUT");
    private final List<String> menuOptions = Arrays.asList("All Modules", "Home", "Stock In", "Stock Preparation", "Stock Taking", "Tag Registration", "Search Item", "Search Signal", "Login");
    private final List<String> dateOptions = new ArrayList<>(Arrays.asList("All Time", "Today", "Last 7 Days", "Last 30 Days", "Pick Date..."));
    private ArrayAdapter<String> dateAdapter;
    private long pickedFromTime = 0;
    private long pickedToTime = 0;

    private boolean isInitializing = false;

    private final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss MM/dd/yyyy", Locale.getDefault());

    @Override
    protected CommScanner getScannerInstance() { return null; }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log);

        db = AppDatabase.getDatabase(this);

        btnBack = findViewById(R.id.btnBack);
        etSearch = findViewById(R.id.etSearch);
        spinnerLevel = findViewById(R.id.spinnerLevel);
        spinnerAction = findViewById(R.id.spinnerAction);
        spinnerMenu = findViewById(R.id.spinnerMenu);
        spinnerDate = findViewById(R.id.spinnerDate);
        rvLogs = findViewById(R.id.rvLogs);
        tvEmpty = findViewById(R.id.tvEmpty);
        tvLogCount = findViewById(R.id.tvLogCount);

        adapter = new LogAdapter(logList);
        adapter.setOnItemClickListener(this::showDetailDialog);
        rvLogs.setLayoutManager(new LinearLayoutManager(this));
        rvLogs.setAdapter(adapter);

        isInitializing = true;
        setupSpinners();
        isInitializing = false;

        btnBack.setOnClickListener(v -> finish());

        String menuFilter = getIntent().getStringExtra(EXTRA_MENU);
        if (menuFilter != null && !menuFilter.isEmpty()) {
            etSearch.setText(menuFilter);
        }

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { loadLogs(); }
        });

        new Thread(() -> {
            long cutoff = System.currentTimeMillis() - (30L * 24 * 30 * 30 * 1000);
            db.appDao().deleteOldLogs(cutoff);
        }).start();

        loadLogs();
    }

    private void setupSpinners() {
        setupSpinner(spinnerLevel, levelOptions);
        setupSpinner(spinnerAction, actionOptions);
        setupSpinner(spinnerMenu, menuOptions);

        dateAdapter = new ArrayAdapter<>(this, R.layout.item_spinner_selected, dateOptions);
        dateAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        spinnerDate.setAdapter(dateAdapter);
        spinnerDate.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                if (isInitializing) return;
                if (pos == dateOptions.size() - 1 && dateOptions.get(pos).equals("Pick Date...")) {
                    handler.post(() -> showDatePicker());
                } else {
                    loadLogs();
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void showDatePicker() {
        Calendar cal = Calendar.getInstance();
        Calendar minCal = Calendar.getInstance();
        minCal.add(Calendar.DAY_OF_YEAR, -30);

        DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, day) -> {
            Calendar from = Calendar.getInstance();
            from.set(year, month, day, 0, 0, 0);
            from.set(Calendar.MILLISECOND, 0);
            pickedFromTime = from.getTimeInMillis();

            Calendar to = Calendar.getInstance();
            to.set(year, month, day, 23, 59, 59);
            to.set(Calendar.MILLISECOND, 999);
            pickedToTime = to.getTimeInMillis();

            String label = String.format(Locale.getDefault(), "%02d/%02d/%04d", day, month + 1, year);
            isInitializing = true;
            dateOptions.set(dateOptions.size() - 1, label);
            dateAdapter.notifyDataSetChanged();
            spinnerDate.setSelection(dateOptions.size() - 1);
            isInitializing = false;
            loadLogs();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
        dialog.getDatePicker().setMaxDate(cal.getTimeInMillis());
        dialog.getDatePicker().setMinDate(minCal.getTimeInMillis());
        dialog.show();
    }

    private void setupSpinner(Spinner spinner, List<String> items) {
        ArrayAdapter<String> adp = new ArrayAdapter<>(this, R.layout.item_spinner_selected, items);
        adp.setDropDownViewResource(R.layout.item_spinner_dropdown);
        spinner.setAdapter(adp);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!isInitializing) loadLogs();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void loadLogs() {
        String searchText = etSearch.getText().toString().trim();
        String level = spinnerLevel.getSelectedItemPosition() == 0 ? null : levelOptions.get(spinnerLevel.getSelectedItemPosition());
        String action = spinnerAction.getSelectedItemPosition() == 0 ? null : actionOptions.get(spinnerAction.getSelectedItemPosition());
        String menu = spinnerMenu.getSelectedItemPosition() == 0 ? null : menuOptions.get(spinnerMenu.getSelectedItemPosition());
        String search = searchText.isEmpty() ? null : searchText;

        long fromTime = 0;
        long toTime = 0;
        int datePos = spinnerDate.getSelectedItemPosition();
        if (datePos == 1) {
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            fromTime = cal.getTimeInMillis();
        } else if (datePos == 2) {
            fromTime = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000);
        } else if (datePos == 3) {
            fromTime = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000);
        } else if (datePos == dateOptions.size() - 1 && pickedFromTime > 0) {
            fromTime = pickedFromTime;
            toTime = pickedToTime;
        }

        final long finalFromTime = fromTime;
        final long finalToTime = toTime;
        new Thread(() -> {
            List<AppLogEntity> results = db.appDao().filterLogs(level, action, menu, finalFromTime, finalToTime, search);
            handler.post(() -> {
                logList.clear();
                logList.addAll(results);
                adapter.notifyDataSetChanged();
                tvEmpty.setVisibility(logList.isEmpty() ? View.VISIBLE : View.GONE);
                tvLogCount.setText(logList.size() + " entries");
            });
        }).start();
    }

    private void showDetailDialog(AppLogEntity log) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_log_detail);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        int levelColor;
        switch (log.level != null ? log.level : "") {
            case "WARNING": levelColor = Color.parseColor("#F57C00"); break;
            case "ERROR": levelColor = Color.parseColor("#E74C3C"); break;
            default: levelColor = Color.parseColor("#4CAF50"); break;
        }

        TextView tvDetailLevel = dialog.findViewById(R.id.tvDetailLevel);
        tvDetailLevel.setText(log.level != null ? log.level : "");
        android.graphics.drawable.GradientDrawable badge = new android.graphics.drawable.GradientDrawable();
        badge.setColor(levelColor);
        badge.setCornerRadius(32f);
        tvDetailLevel.setBackground(badge);

        ((TextView) dialog.findViewById(R.id.tvDetailTimestamp)).setText(sdf.format(new Date(log.timestamp)));
        ((TextView) dialog.findViewById(R.id.tvDetailAction)).setText(log.action != null ? log.action : "-");
        ((TextView) dialog.findViewById(R.id.tvDetailMenu)).setText(log.menu != null ? log.menu : "-");
        ((TextView) dialog.findViewById(R.id.tvDetailEntity)).setText(log.entity != null && !log.entity.isEmpty() ? log.entity : "-");
        ((TextView) dialog.findViewById(R.id.tvDetailMessage)).setText(log.message != null ? log.message : "-");
        ((TextView) dialog.findViewById(R.id.tvDetailUserId)).setText(log.userId != null && !log.userId.isEmpty() ? log.userId : "-");
        ((TextView) dialog.findViewById(R.id.tvDetailDevice)).setText(log.device != null && !log.device.isEmpty() ? log.device : "-");

        if (log.requestApi != null && !log.requestApi.isEmpty()) {
            dialog.findViewById(R.id.labelRequest).setVisibility(View.VISIBLE);
            TextView tvReq = dialog.findViewById(R.id.tvDetailRequest);
            tvReq.setVisibility(View.VISIBLE);
            tvReq.setText(log.requestApi);
        }
        if (log.responseApi != null && !log.responseApi.isEmpty()) {
            dialog.findViewById(R.id.labelResponse).setVisibility(View.VISIBLE);
            TextView tvResp = dialog.findViewById(R.id.tvDetailResponse);
            tvResp.setVisibility(View.VISIBLE);
            tvResp.setText(log.responseApi);
        }

        dialog.findViewById(R.id.btnClose).setOnClickListener(v -> dialog.dismiss());

        dialog.findViewById(R.id.btnCopyLog).setOnClickListener(v -> {
            StringBuilder sb = new StringBuilder();
            sb.append("[").append(log.level != null ? log.level : "INFO").append("] ")
                    .append(sdf.format(new Date(log.timestamp))).append("\n");
            sb.append("Action   : ").append(log.action != null ? log.action : "-").append("\n");
            sb.append("Menu     : ").append(log.menu != null ? log.menu : "-").append("\n");
            sb.append("Entity   : ").append(log.entity != null && !log.entity.isEmpty() ? log.entity : "-").append("\n");
            sb.append("Message  : ").append(log.message != null ? log.message : "-").append("\n");
            sb.append("User ID  : ").append(log.userId != null && !log.userId.isEmpty() ? log.userId : "-").append("\n");
            sb.append("Device   : ").append(log.device != null && !log.device.isEmpty() ? log.device : "-");
            if (log.requestApi != null && !log.requestApi.isEmpty()) {
                sb.append("\nRequest : ").append(log.requestApi);
            }
            if (log.responseApi != null && !log.responseApi.isEmpty()) {
                sb.append("\nResponse: ").append(log.responseApi);
            }
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("log", sb.toString()));
        });

        dialog.show();
    }
}
