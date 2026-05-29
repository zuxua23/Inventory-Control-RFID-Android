package com.example.inventory_system_ht.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.inventory_system_ht.entity.TagLocalEntity;
import com.example.inventory_system_ht.R;

import java.util.List;

public class TagAdapter extends RecyclerView.Adapter<TagAdapter.TagVH> {
    private final List<TagLocalEntity> list;
    private OnItemClickListener listener;
    private int lastScannedPosition = -1;

    public interface OnItemClickListener {
        void onItemClick(TagLocalEntity item);
    }

    public TagAdapter(List<TagLocalEntity> list) {
        this.list = list;
    }

    public void setOnItemClickListener(OnItemClickListener l) {
        this.listener = l;
    }

    public void setLastScannedPosition(int pos) {
        this.lastScannedPosition = pos;
    }

    @NonNull
    @Override
    public TagVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_product, parent, false);
        return new TagVH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull TagVH h, int position) {
        TagLocalEntity item = list.get(position);
        h.tvTagId.setText(item.getTagId() != null ? item.getTagId() : item.getEpcTag());

        String name = item.getProductName();
        boolean isPending = "PENDING".equals(item.getItmId());

        if (name == null || name.isEmpty() || "Validating...".equals(name)) {
            h.tvProductName.setText(isPending ? "Validating..." : item.getItmId());
        } else {
            h.tvProductName.setText(name);
        }

        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(item);
        });
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class TagVH extends RecyclerView.ViewHolder {
        TextView tvProductName, tvTagId;
        TagVH(@NonNull View itemView) {
            super(itemView);
            tvTagId = itemView.findViewById(R.id.tvTagId);
            tvProductName = itemView.findViewById(R.id.tvProductName);
        }
    }
}
