package com.example.inventory_system_ht.activity;

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

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.inventory_system_ht.R;
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

public class LogActivity extends AppCompatActivity {

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
    private final List<String> menuOptions = Arrays.asList("All Menus", "Home", "Stock In", "Stock Preparation", "Stock Taking", "Tag Registration", "Search Item", "Search Signal", "Login");
    private final List<String> dateOptions = Arrays.asList("All Time", "Today", "Last 7 Days", "Last 30 Days");

    private boolean isInitializing = false;

    private final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss MM/dd/yyyy", Locale.getDefault());

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
            long cutoff = System.currentTimeMillis() - (60L * 24 * 60 * 60 * 1000);
            db.appDao().deleteOldLogs(cutoff);
        }).start();

        loadLogs();
    }

    private void setupSpinners() {
        setupSpinner(spinnerLevel, levelOptions);
        setupSpinner(spinnerAction, actionOptions);
        setupSpinner(spinnerMenu, menuOptions);
        setupSpinner(spinnerDate, dateOptions);
    }

    private void setupSpinner(Spinner spinner, List<String> items) {
        ArrayAdapter<String> adp = new ArrayAdapter<>(this, R.layout.item_spinner_selected, R.id.tvSpinnerSelected, items);
        adp.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
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
        }

        final long finalFromTime = fromTime;
        new Thread(() -> {
            List<AppLogEntity> results = db.appDao().filterLogs(level, action, menu, finalFromTime, search);
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
            case "ERROR":   levelColor = Color.parseColor("#E74C3C"); break;
            default:        levelColor = Color.parseColor("#4CAF50"); break;
        }

        TextView tvDetailLevel = dialog.findViewById(R.id.tvDetailLevel);
        tvDetailLevel.setText(log.level != null ? log.level : "");
        tvDetailLevel.setBackgroundColor(levelColor);

        ((TextView) dialog.findViewById(R.id.tvDetailTimestamp)).setText(sdf.format(new Date(log.timestamp)));
        ((TextView) dialog.findViewById(R.id.tvDetailAction)).setText(log.action != null ? log.action : "-");
        ((TextView) dialog.findViewById(R.id.tvDetailMenu)).setText(log.menu != null ? log.menu : "-");
        ((TextView) dialog.findViewById(R.id.tvDetailEntity)).setText(log.entity != null && !log.entity.isEmpty() ? log.entity : "-");
        ((TextView) dialog.findViewById(R.id.tvDetailMessage)).setText(log.message != null ? log.message : "-");
        ((TextView) dialog.findViewById(R.id.tvDetailUserId)).setText(log.userId != null && !log.userId.isEmpty() ? log.userId : "-");

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
        dialog.show();
    }
}
