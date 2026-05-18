package com.example.inventory_system_ht.Adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.inventory_system_ht.Models.TagModels;
import com.example.inventory_system_ht.R;

import java.util.List;

public class TagAdapter extends RecyclerView.Adapter<TagAdapter.TagVH> {

    private final List<TagModels.TagModel> list;
    private OnItemClickListener listener;
    private int lastScannedPosition = -1;

    public interface OnItemClickListener {
        void onItemClick(TagModels.TagModel item);
    }

    public TagAdapter(List<TagModels.TagModel> list) {
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
        TagModels.TagModel item = list.get(position);
        String name = item.getProductName();
        if (name == null || name.isEmpty() || "Validating...".equals(name)) {
            h.tvProductName.setText("Validating...");
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
        TextView tvProductName;
        TagVH(@NonNull View itemView) {
            super(itemView);
            tvProductName = itemView.findViewById(R.id.tvProductName);
        }
    }
}