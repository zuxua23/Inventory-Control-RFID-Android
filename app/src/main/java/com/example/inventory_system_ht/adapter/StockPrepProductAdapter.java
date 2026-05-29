package com.example.inventory_system_ht.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.inventory_system_ht.model.ItemModel;
import com.example.inventory_system_ht.R;

import java.util.List;

public class StockPrepProductAdapter extends RecyclerView.Adapter<StockPrepProductAdapter.ViewHolder> {
    private List<ItemModel.SumProduct> list;

    public StockPrepProductAdapter(List<ItemModel.SumProduct> list) {
        this.list = list;
    }

    public void updateData(List<ItemModel.SumProduct> newList) {
        this.list = newList;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() { return list == null ? 0 : list.size(); }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_sum_product_do, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        ItemModel.SumProduct item = list.get(position);

        String name = item.getItemName();
        if (name == null || name.trim().isEmpty())
            name = item.getItemId() != null ? item.getItemId() : "Unknown item";

        h.tvProductName.setText(name);
        h.tvQty.setText(item.getCount() + "/" + item.getRequired());

        boolean fulfilled = item.getRequired() > 0 && item.getCount() >= item.getRequired();
        h.cardSumItem.setCardBackgroundColor(
                ContextCompat.getColor(h.itemView.getContext(),
                        fulfilled ? R.color.green_button : R.color.blue_theme));
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        CardView cardSumItem;
        TextView tvProductName, tvQty;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            cardSumItem = itemView.findViewById(R.id.cardSumItem);
            tvProductName = itemView.findViewById(R.id.tvProductName);
            tvQty = itemView.findViewById(R.id.tvQty);
        }
    }
}
