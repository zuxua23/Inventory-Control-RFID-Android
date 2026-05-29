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

public class TagRegistrationAdapter extends RecyclerView.Adapter<TagRegistrationAdapter.ViewHolder> {
    private final List<TagLocalEntity> list;
    private int lastScannedPosition = -1;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(TagLocalEntity item);
    }

    public TagRegistrationAdapter(List<TagLocalEntity> list) {
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
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_tag_regist, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TagLocalEntity item = list.get(position);
        holder.tvTagId.setText(item.getEpcTag());
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(item);
        });
    }

    @Override
    public int getItemCount() { return list.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTagId;
        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTagId = itemView.findViewById(R.id.tvTagId);
        }
    }
}
