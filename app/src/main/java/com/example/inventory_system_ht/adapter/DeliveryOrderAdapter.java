package com.example.inventory_system_ht.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.inventory_system_ht.entity.DeliveryOrderEntity;
import com.example.inventory_system_ht.R;
import java.util.List;

public class DeliveryOrderAdapter extends RecyclerView.Adapter<DeliveryOrderAdapter.DeliveryOrderViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(DeliveryOrderEntity item);
    }

    private final List<DeliveryOrderEntity> doList;
    private final OnItemClickListener listener;

    public DeliveryOrderAdapter(List<DeliveryOrderEntity> doList, OnItemClickListener listener) {
        this.doList = doList;
        this.listener = listener;
    }

    @Override public int getItemCount() { return doList.size(); }

    @NonNull
    @Override
    public DeliveryOrderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_delivery_order, parent, false);
        return new DeliveryOrderViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DeliveryOrderViewHolder holder, int position) {
        DeliveryOrderEntity item = doList.get(position);
        holder.tvDoNo.setText(item.getDoNo());
        holder.tvDoName.setText(formatDate(item.getCreatedAt()));
        holder.itemView.setOnClickListener(v -> listener.onItemClick(item));
    }

    private String formatDate(String raw) {
        try {
            java.text.SimpleDateFormat in = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
            java.text.SimpleDateFormat out = new java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.ENGLISH);
            return out.format(in.parse(raw));
        } catch (Exception e) { return raw; }
    }

    static class DeliveryOrderViewHolder extends RecyclerView.ViewHolder {
        TextView tvDoNo, tvDoName;
        DeliveryOrderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDoNo = itemView.findViewById(R.id.tvDoNo);
            tvDoName = itemView.findViewById(R.id.tvDoName);
        }
    }
}
