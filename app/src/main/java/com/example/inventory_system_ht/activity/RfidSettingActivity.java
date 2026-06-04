package com.example.inventory_system_ht.activity;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.densowave.scannersdk.Common.CommScanner;
import com.example.inventory_system_ht.R;
import com.example.inventory_system_ht.activity.base.ScannerActivity;
import com.example.inventory_system_ht.util.RfidSettingsManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RfidSettingActivity extends ScannerActivity {
    private Spinner spinnerPower, spinnerSession, spinnerQ;
    private final List<String> powerList = new ArrayList<>(Arrays.asList(
            "5 dBm", "10 dBm", "15 dBm", "18 dBm", "21 dBm", "24 dBm", "27 dBm", "30 dBm"));
    private final int[] powerValues = {5, 10, 15, 18, 21, 24, 27, 30};
    private final List<String> sessionList = new ArrayList<>(Arrays.asList("S0", "S1", "S2", "S3"));
    private final List<String> qList = new ArrayList<>();

    @Override
    protected CommScanner getScannerInstance() { return null; }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rfid_setting);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.btnBack), (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            ViewGroup.MarginLayoutParams p = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            p.topMargin = bars.top + (int) (12 * getResources().getDisplayMetrics().density);
            v.setLayoutParams(p);
            return insets;
        });

        for (int i = 0; i <= 7; i++) qList.add(String.valueOf(i));

        spinnerPower = findViewById(R.id.spinnerPower);
        spinnerSession = findViewById(R.id.spinnerSession);
        spinnerQ = findViewById(R.id.spinnerQ);

        spinnerPower.setAdapter(buildAdapter(powerList));
        spinnerSession.setAdapter(buildAdapter(sessionList));
        spinnerQ.setAdapter(buildAdapter(qList));

        RfidSettingsManager mgr = new RfidSettingsManager(this);
        spinnerPower.setSelection(indexOfPower(mgr.getPower()));
        spinnerSession.setSelection(mgr.getSession());
        spinnerQ.setSelection(mgr.getQFactor());

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        Button btnSave = findViewById(R.id.btnSaveSetting);
        btnSave.setOnClickListener(v -> {
            int power = powerValues[spinnerPower.getSelectedItemPosition()];
            int session = spinnerSession.getSelectedItemPosition();
            int q = spinnerQ.getSelectedItemPosition();
            new RfidSettingsManager(this).save(power, session, q);
            showSuccess("RFID settings saved");
            finish();
        });
    }

    private ArrayAdapter<String> buildAdapter(List<String> items) {
        return new ArrayAdapter<String>(this, R.layout.item_spinner_selected, R.id.tvSpinnerSelected, items) {
            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View view = LayoutInflater.from(getContext()).inflate(R.layout.item_dropdown_loc, parent, false);
                TextView tv = view.findViewById(R.id.tvDropdownItem);
                ImageView icon = view.findViewById(R.id.ivDropdownIcon);
                if (tv != null) tv.setText(getItem(position));
                if (icon != null) icon.setVisibility(View.GONE);
                return view;
            }
        };
    }
}
