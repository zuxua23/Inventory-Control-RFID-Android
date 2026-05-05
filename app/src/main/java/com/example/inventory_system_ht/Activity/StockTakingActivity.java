package com.example.inventory_system_ht.Activity;

import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.densowave.scannersdk.Barcode.BarcodeData;
import com.densowave.scannersdk.Barcode.BarcodeDataReceivedEvent;
import com.densowave.scannersdk.Common.CommScanner;
import com.densowave.scannersdk.Listener.BarcodeDataDelegate;
import com.densowave.scannersdk.Listener.RFIDDataDelegate;
import com.densowave.scannersdk.RFID.RFIDData;
import com.densowave.scannersdk.RFID.RFIDDataReceivedEvent;

import com.example.inventory_system_ht.Adapter.StockTakingItemAdapter;
import com.example.inventory_system_ht.Helper.ApiClient;
import com.example.inventory_system_ht.Helper.ApiService;
import com.example.inventory_system_ht.Helper.PrefManager;
import com.example.inventory_system_ht.Models.GeneralResponse;
import com.example.inventory_system_ht.Models.StockTakingModels;
import com.example.inventory_system_ht.R;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Detail halaman Stock Taking.
 *
 * Flow:
 * 1. Terima sttId + remark dari Intent (dari StockTakingListActivity)
 * 2. Load snapshot tags dari API → simpan ke sessionItems (in-memory)
 * 3. Semua operasi (scan, remove, manual add) hanya ubah state lokal
 * 4. Finalize → kirim bulk scan + remove + manual add → finalize API
 * 5. Kembali dengan ada perubahan → dialog konfirmasi, jika keluar data dibuang
 */
public class StockTakingActivity extends BaseScannerActivity
        implements BarcodeDataDelegate, RFIDDataDelegate {

    // ── Views ─────────────────────────────────────────────────────
    private ImageView   btnBack;
    private Switch      switchRfid;
    private CardView    btnFinalize;
    private EditText    resultScan;
    private RecyclerView rvTags;
    private TextView    tvRemark, tvLocation, tvQty;

    // ── Scanner ───────────────────────────────────────────────────
    private CommScanner mCommScanner;
    private ToneGenerator toneGen;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // ── Data ──────────────────────────────────────────────────────
    private ApiService  api;
    private String      token;
    private String      sttId  = "";
    private String      remark = "";

    /** Snapshot lokal: semua tag dalam sesi ini */
    private final List<StockTakingModels.SessionItem> sessionItems = new ArrayList<>();

    /** Tag yang sudah di-remove (untuk dikirim ke API saat finalize) */
    private final List<String> removedTagIds = new ArrayList<>();

    /** Entry manual add (untuk dikirim ke API saat finalize) */
    private final List<StockTakingModels.ManualAddReq> manualEntries = new ArrayList<>();

    private boolean hasChanges = false;

    private StockTakingItemAdapter adapter;

    // ─────────────────────────────────────────────────────────────

    @Override
    protected CommScanner getScannerInstance() { return mCommScanner; }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stock_taking_adjustment);

        // Ambil data dari intent
        sttId  = getIntent().getStringExtra("sttId");
        remark = getIntent().getStringExtra("remark");
        if (sttId == null)  sttId  = "";
        if (remark == null) remark = "";

        PrefManager pref = new PrefManager(this);
        token = "Bearer " + pref.getToken();
        api   = ApiClient.getClient(this).create(ApiService.class);

        try { toneGen = new ToneGenerator(AudioManager.STREAM_MUSIC, 100); } catch (Exception e) { /* ignore */ }

        // Bind views
        btnBack    = findViewById(R.id.btnBack);
        switchRfid = findViewById(R.id.switchRfid);
        btnFinalize = findViewById(R.id.btnSave);
        resultScan = findViewById(R.id.resultScan);
        rvTags     = findViewById(R.id.rvTags);
        tvRemark   = findViewById(R.id.tvRemark);
        tvLocation = findViewById(R.id.tvLocation);
        tvQty      = findViewById(R.id.tvQty);

        // Setup adapter
        adapter = new StockTakingItemAdapter(sessionItems);
        adapter.setOnItemClickListener(this::showAdjustmentDialog);
        rvTags.setLayoutManager(new LinearLayoutManager(this));
        rvTags.setAdapter(adapter);

        // Tampilkan remark
        tvRemark.setText("Note: " + (remark.isEmpty() ? "-" : remark));

        // Handle back gesture (AndroidX OnBackPressedDispatcher)
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBackPressed();
            }
        });

        setupScanner();
        setupListeners();

        // Load data sesi dari API
        if (!sttId.isEmpty()) {
            loadSessionTags();
        } else {
            showError("Session ID tidak ditemukan!");
        }
    }

    // ── Setup ─────────────────────────────────────────────────────

    private void setupListeners() {
        // Back → jika ada perubahan, tanya konfirmasi
        btnBack.setOnClickListener(v -> handleBackPressed());

        CardView btnPowerDropdown = findViewById(R.id.btnPowerDropdown);
        TextView tvPowerLevel     = findViewById(R.id.tvPowerLevel);
        setupPowerDropdown(btnPowerDropdown, switchRfid, tvPowerLevel);

        switchRfid.setOnCheckedChangeListener((btn, isChecked) -> {
            if (isChecked) {
                CommScanner s = getScannerInstance();
                if (s == null || s.getRFIDScanner() == null) {
                    showError("HT belum terhubung ke RFID Reader!");
                    switchRfid.setChecked(false);
                    return;
                }
                btnPowerDropdown.setVisibility(View.VISIBLE);
            } else {
                btnPowerDropdown.setVisibility(View.GONE);
            }
            showSuccess(isChecked ? "RFID Mode Active" : "Barcode Mode Active");
            resultScan.requestFocus();
        });

        btnFinalize.setOnClickListener(v -> {
            if (sttId.isEmpty()) {
                showWarning("Tidak ada sesi aktif!");
                return;
            }
            showFinalizeConfirmDialog();
        });

        resultScan.setOnEditorActionListener((v, actionId, event) -> {
            String data = resultScan.getText().toString().trim();
            if (!data.isEmpty()) {
                processScan(data);
                resultScan.setText("");
            }
            return true;
        });
    }

    private void setupScanner() {
        if (mCommScanner != null) {
            try {
                mCommScanner.getRFIDScanner().setDataDelegate(this);
                mCommScanner.getBarcodeScanner().setDataDelegate(this);
            } catch (Exception e) { /* ignore */ }
        }
    }

    // ── Load Data ─────────────────────────────────────────────────

    private void loadSessionTags() {
        if (!isNetworkConnected()) {
            showWarning("Tidak ada koneksi internet.");
            return;
        }
        showLoading();
        api.getSessionTags(token, sttId).enqueue(new Callback<List<StockTakingModels.SessionItem>>() {
            @Override
            public void onResponse(Call<List<StockTakingModels.SessionItem>> call,
                                   Response<List<StockTakingModels.SessionItem>> response) {
                hideLoading();
                if (response.isSuccessful() && response.body() != null) {
                    sessionItems.clear();
                    removedTagIds.clear();
                    manualEntries.clear();
                    hasChanges = false;

                    for (StockTakingModels.SessionItem item : response.body()) {
                        item.state = "PENDING";
                        sessionItems.add(item);
                    }

                    adapter.notifyDataSetChanged();
                    updateInfo();
                    showSuccess("Loaded " + sessionItems.size() + " items");
                } else {
                    handleApiError(response.code());
                }
            }

            @Override
            public void onFailure(Call<List<StockTakingModels.SessionItem>> call, Throwable t) {
                hideLoading();
                handleFailure(t);
            }
        });
    }

    // ── Scan Processing ───────────────────────────────────────────

    private void processScan(String epcOrBarcode) {
        if (sttId.isEmpty()) {
            playScanFeedback(2);
            return;
        }

        // Cari EPC di sessionItems
        for (int i = 0; i < sessionItems.size(); i++) {
            StockTakingModels.SessionItem item = sessionItems.get(i);

            if (epcOrBarcode.equalsIgnoreCase(item.epcTag)) {
                if ("PENDING".equals(item.state)) {
                    // Valid & belum discanned → mark FOUND
                    item.state = "FOUND";
                    hasChanges = true;
                    adapter.notifyItemChanged(i);
                    rvTags.smoothScrollToPosition(i);
                    updateInfo();
                    playScanFeedback(0); // success beep
                } else {
                    // Sudah scanned sebelumnya → ignore, feedback netral
                    showWarning("Item sudah discanned.");
                }
                return;
            }
        }

        // EPC tidak ditemukan dalam list
        playScanFeedback(2); // error beep
        showWarning("Tag tidak ada dalam daftar sesi ini.");
    }

    // ── UI Update ─────────────────────────────────────────────────

    private void updateInfo() {
        int total   = sessionItems.size();
        int scanned = countScanned();

        // Qty counter
        tvQty.setText("Qty: " + scanned + "/" + total);

        // Kumpulkan lokasi unik
        List<String> locations = new ArrayList<>();
        for (StockTakingModels.SessionItem item : sessionItems) {
            if (item.location != null && !item.location.isEmpty()
                    && !locations.contains(item.location)) {
                locations.add(item.location);
            }
        }
        tvLocation.setText("Lokasi: " + (locations.isEmpty() ? "-" : String.join(", ", locations)));
    }

    private int countScanned() {
        int count = 0;
        for (StockTakingModels.SessionItem item : sessionItems) {
            if ("FOUND".equals(item.state) || "MANUAL_ADD".equals(item.state)) count++;
        }
        return count;
    }

    // ── Adjustment Dialog ─────────────────────────────────────────

    private void showAdjustmentDialog(StockTakingModels.SessionItem selectedItem, int position) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_adj);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        ImageButton btnFaq      = dialog.findViewById(R.id.btnFaq);
        Button      btnRemove   = dialog.findViewById(R.id.btnRemove);
        Button      btnAddManual = dialog.findViewById(R.id.btnAddManual);

        btnFaq.setOnClickListener(v -> showFaqDialog());

        // ── Remove ──
        btnRemove.setOnClickListener(v -> {
            dialog.dismiss();
            showRemoveConfirmDialog(selectedItem, position);
        });

        // ── Manual Add ──
        btnAddManual.setOnClickListener(v -> {
            dialog.dismiss();
            showManualAddDialog(selectedItem, position);
        });

        dialog.show();
    }

    // Ganti showRemoveConfirmDialog
    private void showRemoveConfirmDialog(StockTakingModels.SessionItem item, int position) {
        showCustomConfirmDialog(
                "Yakin mau hapus item ini dari daftar?\nQty akan berkurang.",
                () -> {
                    if (item.tagId != null && !item.tagId.isEmpty()) {
                        removedTagIds.add(item.tagId);
                    }
                    sessionItems.remove(position);
                    adapter.notifyItemRemoved(position);
                    adapter.notifyItemRangeChanged(position, sessionItems.size());
                    hasChanges = true;
                    updateInfo();
                    showSuccess("Item dihapus dari daftar.");
                }
        );
    }

    // Ganti showFinalizeConfirmDialog
    private void showFinalizeConfirmDialog() {
        int scanned = countScanned();
        String msg = "Yakin ingin finalisasi?\nScanned: " + scanned
                + "\nRemoved: " + removedTagIds.size()
                + "\nManual Add: " + manualEntries.size();
        showCustomConfirmDialog(msg, this::startFinalize);
    }

    // Ganti handleBackPressed (bagian AlertDialog)
    private void handleBackPressed() {
        if (!hasChanges) { finish(); return; }
        showCustomConfirmDialog(
                "Yakin mau keluar?\nSemua perubahan akan dibatalkan.",
                () -> {
                    sessionItems.clear();
                    removedTagIds.clear();
                    manualEntries.clear();
                    hasChanges = false;
                    finish();
                }
        );
    }

    private void showManualAddDialog(StockTakingModels.SessionItem item, int position) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_manual_add);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int w = (int)(getResources().getDisplayMetrics().widthPixels * 0.90);
            dialog.getWindow().setLayout(w, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        EditText etItemId = dialog.findViewById(R.id.etManualItemId);
        EditText etRemark = dialog.findViewById(R.id.etManualRemark);
        Button   btnCancel = dialog.findViewById(R.id.btnCancelManual);
        Button   btnSave   = dialog.findViewById(R.id.btnSaveManual);

        // Pre-fill item ID yang dipilih
        etItemId.setText(item.itemId != null ? item.itemId : "");
        etItemId.setEnabled(false); // item sudah diketahui dari list

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> {
            String remarkText = etRemark.getText().toString().trim();
            if (remarkText.isEmpty()) {
                showWarning("Remark tidak boleh kosong!");
                return;
            }

            // Simpan untuk dikirim ke API saat finalize
            manualEntries.add(new StockTakingModels.ManualAddReq(sttId, item.itemId, remarkText));

            // Update state lokal → hijau
            item.state        = "MANUAL_ADD";
            item.manualRemark = remarkText;
            hasChanges        = true;

            adapter.notifyItemChanged(position);
            updateInfo();
            playScanFeedback(0);
            showSuccess("Manual add berhasil!");
            dialog.dismiss();
        });

        dialog.show();
    }

    // ── Finalize ──────────────────────────────────────────────────

    private void startFinalize() {
        showLoading();
        showWarning("Mengirim data scan...");

        // Kumpulkan semua EPC yang FOUND
        List<String> foundEpcs = new ArrayList<>();
        for (StockTakingModels.SessionItem item : sessionItems) {
            if ("FOUND".equals(item.state)) {
                foundEpcs.add(item.epcTag);
            }
        }

        if (!foundEpcs.isEmpty()) {
            sendBulkScan(foundEpcs);
        } else {
            sendRemoves(0);
        }
    }

    private void sendBulkScan(List<String> epcs) {
        StockTakingModels.BulkScanReq req = new StockTakingModels.BulkScanReq(sttId, epcs);
        api.bulkScanStockTaking(token, req).enqueue(new Callback<GeneralResponse>() {
            @Override
            public void onResponse(Call<GeneralResponse> call, Response<GeneralResponse> response) {
                // Lanjut kirim removes meski ada error (best-effort)
                sendRemoves(0);
            }
            @Override
            public void onFailure(Call<GeneralResponse> call, Throwable t) {
                sendRemoves(0);
            }
        });
    }

    private void sendRemoves(int index) {
        if (index >= removedTagIds.size()) {
            sendManualAdds(0);
            return;
        }
        StockTakingModels.RemoveReq req = new StockTakingModels.RemoveReq(sttId, removedTagIds.get(index));
        api.removeStockTaking(token, req).enqueue(new Callback<GeneralResponse>() {
            @Override
            public void onResponse(Call<GeneralResponse> call, Response<GeneralResponse> response) {
                sendRemoves(index + 1);
            }
            @Override
            public void onFailure(Call<GeneralResponse> call, Throwable t) {
                sendRemoves(index + 1);
            }
        });
    }

    private void sendManualAdds(int index) {
        if (index >= manualEntries.size()) {
            sendApplyAdjustment(); // ← ganti dari sendFinalize ke applyAdjustment
            return;
        }
        StockTakingModels.ManualAddReq req = manualEntries.get(index);
        api.manualAddStockTaking(token, req).enqueue(new Callback<GeneralResponse>() {
            @Override
            public void onResponse(Call<GeneralResponse> call, Response<GeneralResponse> response) {
                sendManualAdds(index + 1);
            }
            @Override
            public void onFailure(Call<GeneralResponse> call, Throwable t) {
                sendManualAdds(index + 1);
            }
        });
    }

    private void sendApplyAdjustment() {
        showWarning("Menerapkan adjustment...");
        StockTakingModels.FinalizeReq req = new StockTakingModels.FinalizeReq(sttId);
        api.applyAdjustment(token, req).enqueue(new Callback<GeneralResponse>() {
            @Override
            public void onResponse(Call<GeneralResponse> call, Response<GeneralResponse> response) {
                hideLoading();
                if (response.isSuccessful()) {
                    showSuccess("Data berhasil dikirim! Tunggu admin untuk finalisasi.");
                    playScanFeedback(0);
                    hasChanges = false;
                    finish();
                } else {
                    handleApiError(response.code());
                    playScanFeedback(2);
                }
            }
            @Override
            public void onFailure(Call<GeneralResponse> call, Throwable t) {
                hideLoading();
                handleFailure(t);
                playScanFeedback(2);
            }
        });
    }
    // ── FAQ ───────────────────────────────────────────────────────

    private void showFaqDialog() {
        Dialog faqDialog = new Dialog(this);
        faqDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        faqDialog.setContentView(R.layout.dialog_faq);
        if (faqDialog.getWindow() != null) {
            faqDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int w = (int)(getResources().getDisplayMetrics().widthPixels * 0.90);
            faqDialog.getWindow().setLayout(w, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        faqDialog.show();
    }

    // ── Scanner Callbacks ─────────────────────────────────────────

    @Override
    public void onRFIDDataReceived(CommScanner scanner, RFIDDataReceivedEvent event) {
        if (!switchRfid.isChecked()) return;
        for (RFIDData data : event.getRFIDData()) {
            String epc = bytesToHexString(data.getUII());
            handler.post(() -> processScan(epc));
        }
    }

    @Override
    public void onBarcodeDataReceived(CommScanner scanner, BarcodeDataReceivedEvent event) {
        if (switchRfid.isChecked()) return;
        List<BarcodeData> dataList = event.getBarcodeData();
        if (!dataList.isEmpty()) {
            String barcode = new String(dataList.get(0).getData());
            handler.post(() -> processScan(barcode));
        }
    }

    private String bytesToHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02X", b));
        return sb.toString();
    }

    // ── Lifecycle ─────────────────────────────────────────────────

    @Override
    protected void onResume() {
        super.onResume();
        setupScanner();
        if (getHTBatteryLevel() <= 15) {
            showWarning("Baterai HT " + getHTBatteryLevel() + "%, segera charge!");
            playScanFeedback(2);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mCommScanner != null) {
            try {
                if (mCommScanner.getRFIDScanner()  != null) mCommScanner.getRFIDScanner().setDataDelegate(null);
                if (mCommScanner.getBarcodeScanner() != null) mCommScanner.getBarcodeScanner().setDataDelegate(null);
            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (toneGen != null) { toneGen.release(); toneGen = null; }
    }
    // Helper: custom confirm dialog pakai dialog_confirm.xml
    private void showCustomConfirmDialog(String message, Runnable onYes) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_confirm);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int w = (int)(getResources().getDisplayMetrics().widthPixels * 0.85);
            dialog.getWindow().setLayout(w, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        TextView tvMsg    = dialog.findViewById(R.id.tvConfirmMessage);
        Button   btnNo    = dialog.findViewById(R.id.btnConfirmNo);
        Button   btnYes   = dialog.findViewById(R.id.btnConfirmYes);

        tvMsg.setText(message);
        btnNo.setOnClickListener(v -> dialog.dismiss());
        btnYes.setOnClickListener(v -> {
            dialog.dismiss();
            onYes.run();
        });

        dialog.show();
    }
}