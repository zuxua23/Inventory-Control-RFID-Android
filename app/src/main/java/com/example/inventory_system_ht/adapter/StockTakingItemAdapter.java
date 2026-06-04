package com.example.inventory_system_ht.adapter;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.example.inventory_system_ht.model.StockTakingModel;
import com.example.inventory_system_ht.R;

import java.util.List;

public class StockTakingItemAdapter
        extends RecyclerView.Adapter<StockTakingItemAdapter.VH> {
    public interface OnItemClickListener {
        void onItemClick(StockTakingModel.SessionItem item, int position);
    }
    private static final int COLOR_PENDING = Color.parseColor("#0181CC");
    private static final int COLOR_DONE = Color.parseColor("#01C470");
    private final List<StockTakingModel.SessionItem> list;
    private OnItemClickListener listener;

    public StockTakingItemAdapter(List<StockTakingModel.SessionItem> list) {
        this.list = list;
    }

    public void setOnItemClickListener(OnItemClickListener l) {
        this.listener = l;
    }

    @Override
    public int getItemCount() { return list.size(); }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_stock_taking_tag, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        StockTakingModel.SessionItem item = list.get(position);

        String displayName;
        if (item.itemName != null && !item.itemName.isEmpty()) {
            displayName = item.itemName;
        } else if (item.itemCode != null && !item.itemCode.isEmpty()) {
            displayName = item.itemCode;
        } else if (item.itemId != null) {
            displayName = item.itemId;
        } else {
            displayName = "-";
        }
        h.tvItemName.setText(displayName);
        h.tvTagId.setText(item.tagId != null ? item.tagId : "-");

        String state = item.state != null ? item.state : "PENDING";

        switch (state) {
            case "FOUND":
                h.card.setCardBackgroundColor(COLOR_DONE);
                h.tvStatus.setText("✓ Scanned");
                h.tvStatus.setVisibility(View.VISIBLE);
                break;
            case "MANUAL_ADD":
                h.card.setCardBackgroundColor(COLOR_DONE);
                h.tvStatus.setText("+ Manual");
                h.tvStatus.setVisibility(View.VISIBLE);
                break;
            default:
                h.card.setCardBackgroundColor(COLOR_PENDING);
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
        TextView tvItemName, tvTagId, tvStatus;

        VH(@NonNull View itemView) {
            super(itemView);
            card = (CardView) itemView;
            tvItemName = itemView.findViewById(R.id.tvItemName);
            tvTagId = itemView.findViewById(R.id.tvTagId);
            tvStatus = itemView.findViewById(R.id.tvStatus);
        }
    }
}
