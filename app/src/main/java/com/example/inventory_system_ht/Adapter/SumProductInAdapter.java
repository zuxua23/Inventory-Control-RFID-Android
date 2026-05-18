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

public class SumProductInAdapter extends RecyclerView.Adapter<SumProductInAdapter.ViewHolder> {

    private List<ItemModels.SumProductModel> list;

    public SumProductInAdapter(List<ItemModels.SumProductModel> list) {
        this.list = list;
    }

    public void updateData(List<ItemModels.SumProductModel> newList) {
        this.list = newList;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() { return list == null ? 0 : list.size(); }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_sum_product_in, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        ItemModels.SumProductModel item = list.get(position);

        String name = item.getItemName();
        if (name == null || name.trim().isEmpty())
            name = item.getItemId() != null ? item.getItemId() : "Unknown item";

        h.tvProductName.setText(name);
        h.tvQty.setText("Qty: " + item.getCount());
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvProductName, tvQty;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvProductName = itemView.findViewById(R.id.tvProductName);
            tvQty = itemView.findViewById(R.id.tvQty);
        }
    }
}