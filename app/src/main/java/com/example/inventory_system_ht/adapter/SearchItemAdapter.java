package com.example.inventory_system_ht.adapter;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.example.inventory_system_ht.model.TagModel;
import com.example.inventory_system_ht.R;

import java.util.List;

public class SearchItemAdapter extends RecyclerView.Adapter<SearchItemAdapter.VH> {
    private final List<TagModel.SearchItemDto> list;
    private OnItemClickListener listener;
    private int lastScannedPosition = -1;
    public interface OnItemClickListener {
        void onItemClick(TagModel.SearchItemDto item);
    }

    public SearchItemAdapter(List<TagModel.SearchItemDto> list) {
        this.list = list;
    }

    public void setOnItemClickListener(OnItemClickListener l) { this.listener = l; }
    public void setLastScannedPosition(int pos) { this.lastScannedPosition = pos; }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_tag, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        TagModel.SearchItemDto item = list.get(pos);
        h.tvItemName.setText(item.getItemName() != null ? item.getItemName() : "-");
        h.tvTagId.setText(item.getTagId() != null ? item.getTagId() : "-");
        h.tvLocation.setText(item.getLocation() != null ? item.getLocation() : "-");

        if (pos == lastScannedPosition) {
            h.cardView.setCardBackgroundColor(Color.parseColor("#E3F2FD"));
            h.tvItemName.setTextColor(Color.parseColor("#1565C0"));
            h.tvTagId.setTextColor(Color.parseColor("#1565C0"));
            h.tvLocation.setTextColor(Color.parseColor("#1565C0"));
        } else {
            h.cardView.setCardBackgroundColor(Color.parseColor("#1976D2"));
            h.tvItemName.setTextColor(Color.WHITE);
            h.tvTagId.setTextColor(Color.WHITE);
            h.tvLocation.setTextColor(Color.WHITE);
        }

        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(item);
        });
    }

    @Override
    public int getItemCount() { return list.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvItemName, tvTagId, tvLocation;
        CardView cardView;

        VH(@NonNull View v) {
            super(v);
            tvItemName = v.findViewById(R.id.tvItemName);
            tvTagId = v.findViewById(R.id.tvTagId);
            tvLocation = v.findViewById(R.id.tvLocation);
            cardView = (CardView) v;
        }
    }
}
