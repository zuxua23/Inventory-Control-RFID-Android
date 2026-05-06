package com.example.inventory_system_ht.Adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.inventory_system_ht.Models.ItemModels;
import com.example.inventory_system_ht.R;

import java.util.List;

public class ItemAdapter extends RecyclerView.Adapter<ItemAdapter.ViewHolder> {

    private List<ItemModels.ItemModel> itemList;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(ItemModels.ItemModel item);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setLastScannedPosition(int position) {
        // reserved untuk highlight animasi kalau dibutuhkan nanti
    }

    public ItemAdapter(List<ItemModels.ItemModel> itemList) {
        this.itemList = itemList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_product_stock_in, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ItemModels.ItemModel item = itemList.get(position);
        holder.tvTagId.setText(item.getEpcTag());
        holder.tvProductName.setText(
                item.getItemName() != null && !item.getItemName().isEmpty()
                        ? item.getItemName()
                        : item.getItemId()
        );
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(item);
        });
    }

    @Override
    public int getItemCount() {
        return itemList == null ? 0 : itemList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTagId, tvProductName;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTagId = itemView.findViewById(R.id.tvTagId);
            tvProductName = itemView.findViewById(R.id.tvProductName);
        }
    }
}