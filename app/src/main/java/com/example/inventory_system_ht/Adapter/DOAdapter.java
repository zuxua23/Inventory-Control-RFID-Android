package com.example.inventory_system_ht.Adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.inventory_system_ht.Models.DOModels;
import com.example.inventory_system_ht.R;

import java.util.List;

public class DOAdapter extends RecyclerView.Adapter<DOAdapter.DOViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(DOModels.DOModel doModel);
    }

    private final List<DOModels.DOModel> doList;
    private final OnItemClickListener listener;

    public DOAdapter(List<DOModels.DOModel> doList, OnItemClickListener listener) {
        this.doList = doList;
        this.listener = listener;
    }

    @Override
    public int getItemCount() { return doList.size(); }

    @NonNull
    @Override
    public DOViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_delivery_order, parent, false);
        return new DOViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DOViewHolder holder, int position) {
        DOModels.DOModel doItem = doList.get(position);
        holder.tvDoNo.setText(doItem.getDoNo());
        holder.tvDoName.setText(formatDate(doItem.getCreatedAt()));
        holder.itemView.setOnClickListener(v -> listener.onItemClick(doItem));
    }

    private String formatDate(String raw) {
        try {
            java.text.SimpleDateFormat in = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
            java.text.SimpleDateFormat out = new java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.ENGLISH);
            return out.format(in.parse(raw));
        } catch (Exception e) { return raw; }
    }

    static class DOViewHolder extends RecyclerView.ViewHolder {
        TextView tvDoNo, tvDoName;

        DOViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDoNo = itemView.findViewById(R.id.tvDoNo);
            tvDoName = itemView.findViewById(R.id.tvDoName);
        }
    }
}
