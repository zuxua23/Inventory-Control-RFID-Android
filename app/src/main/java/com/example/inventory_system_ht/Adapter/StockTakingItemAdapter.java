package com.example.inventory_system_ht.Adapter;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.example.inventory_system_ht.Models.StockTakingModels;
import com.example.inventory_system_ht.R;

import java.util.List;

/**
 * Adapter untuk list item dalam sesi Stock Taking.
 *
 * State warna:
 *  - PENDING    → biru (blue_theme)  — bisa diklik
 *  - FOUND      → hijau (#01C470)    — tidak bisa diklik
 *  - MANUAL_ADD → hijau (#01C470)    — tidak bisa diklik
 */
public class StockTakingItemAdapter
        extends RecyclerView.Adapter<StockTakingItemAdapter.VH> {

    public interface OnItemClickListener {
        void onItemClick(StockTakingModels.SessionItem item, int position);
    }

    private static final int COLOR_PENDING     = Color.parseColor("#1565C0"); // blue_theme
    private static final int COLOR_FOUND       = Color.parseColor("#01C470"); // hijau
    private static final int COLOR_MANUAL_ADD  = Color.parseColor("#01C470"); // sama

    private final List<StockTakingModels.SessionItem> list;
    private OnItemClickListener listener;

    public StockTakingItemAdapter(List<StockTakingModels.SessionItem> list) {
        this.list = list;
    }

    public void setOnItemClickListener(OnItemClickListener l) {
        this.listener = l;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_stock_taking_tag, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        StockTakingModels.SessionItem item = list.get(position);

        // Tampilkan nama item, fallback ke itemId jika null
        String displayName = (item.itemName != null && !item.itemName.isEmpty())
                ? item.itemName : (item.itemId != null ? item.itemId : "-");
        h.tvItemName.setText(displayName);

        String state = item.state != null ? item.state : "PENDING";

        int bgColor;
        switch (state) {
            case "FOUND":
            case "MANUAL_ADD":
                bgColor = COLOR_FOUND;
                break;
            default:
                bgColor = COLOR_PENDING;
                break;
        }
        h.card.setCardBackgroundColor(bgColor);

        switch (state) {
            case "FOUND":
                h.tvStatus.setText("✓ Scanned");
                h.tvStatus.setVisibility(View.VISIBLE);
                break;
            case "MANUAL_ADD":
                h.tvStatus.setText("+ Manual");
                h.tvStatus.setVisibility(View.VISIBLE);
                break;
            default:
                h.tvStatus.setVisibility(View.GONE);
                break;
        }

        boolean clickable = "PENDING".equals(state);
        h.card.setClickable(clickable);
        h.card.setFocusable(clickable);

        if (clickable && listener != null) {
            h.card.setOnClickListener(v -> {
                int pos = h.getAdapterPosition();
                if (pos != RecyclerView.NO_ID) listener.onItemClick(list.get(pos), pos);
            });
        } else {
            h.card.setOnClickListener(null);
        }
    }

    static class VH extends RecyclerView.ViewHolder {
        CardView card;
        TextView tvItemName, tvStatus; // ← ganti tvItemId + tvLocation → tvItemName

        VH(@NonNull View itemView) {
            super(itemView);
            card       = (CardView) itemView;
            tvItemName = itemView.findViewById(R.id.tvItemName);
            tvStatus   = itemView.findViewById(R.id.tvStatus);
        }
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

}