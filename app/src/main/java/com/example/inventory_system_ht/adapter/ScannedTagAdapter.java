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

public class ScannedTagAdapter extends RecyclerView.Adapter<ScannedTagAdapter.VH> {

    private static final int COLOR_CARD = Color.parseColor("#0181CC");
    private final List<StockTakingModel.ScannedTagItem> list;

    public ScannedTagAdapter(List<StockTakingModel.ScannedTagItem> list) {
        this.list = list;
    }

    @Override
    public int getItemCount() { return list.size(); }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_scanned_tag, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        StockTakingModel.ScannedTagItem item = list.get(position);
        h.tvItemName.setText(item.itemName != null && !item.itemName.isEmpty() ? item.itemName : "-");
        h.tvTagId.setText(item.tagId != null && !item.tagId.isEmpty() ? item.tagId : "-");
        h.tvEpc.setText(item.epcTag != null && !item.epcTag.isEmpty() ? item.epcTag : "-");
        h.card.setCardBackgroundColor(COLOR_CARD);
    }

    static class VH extends RecyclerView.ViewHolder {
        CardView card;
        TextView tvItemName, tvTagId, tvEpc;

        VH(@NonNull View itemView) {
            super(itemView);
            card = (CardView) itemView;
            tvItemName = itemView.findViewById(R.id.tvItemName);
            tvTagId = itemView.findViewById(R.id.tvTagId);
            tvEpc = itemView.findViewById(R.id.tvEpc);
        }
    }
}
