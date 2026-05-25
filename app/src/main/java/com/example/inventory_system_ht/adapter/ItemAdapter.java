package com.example.inventory_system_ht.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.inventory_system_ht.model.ItemModel;
import com.example.inventory_system_ht.R;

import java.util.List;

public class ItemAdapter extends RecyclerView.Adapter<ItemAdapter.ViewHolder> {

    private final List<ItemModel.Item> itemList;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(ItemModel.Item item);
    }

    public ItemAdapter(List<ItemModel.Item> itemList) {
        this.itemList = itemList;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setLastScannedPosition(int position) {}

    @Override
    public int getItemCount() { return itemList == null ? 0 : itemList.size(); }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_product, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ItemModel.Item item = itemList.get(position);
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

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTagId, tvProductName;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTagId = itemView.findViewById(R.id.tvTagId);
            tvProductName = itemView.findViewById(R.id.tvProductName);
        }
    }
}
