package com.example.inventory_system_ht.adapter;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.inventory_system_ht.R;
import com.example.inventory_system_ht.entity.AppLogEntity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LogAdapter extends RecyclerView.Adapter<LogAdapter.LogViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(AppLogEntity log);
    }

    private final List<AppLogEntity> logs;
    private OnItemClickListener listener;
    private final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss MM/dd/yyyy", Locale.getDefault());

    public LogAdapter(List<AppLogEntity> logs) {
        this.logs = logs;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public LogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_log, parent, false);
        return new LogViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
        AppLogEntity log = logs.get(position);

        holder.tvTimestamp.setText(sdf.format(new Date(log.timestamp)));
        holder.tvLevel.setText(log.level != null ? log.level : "");
        holder.tvAction.setText(log.action != null ? log.action : "");
        holder.tvMenu.setText(log.menu != null ? log.menu : "");
        holder.tvMessage.setText(log.message != null ? log.message : "");

        int color;
        switch (log.level != null ? log.level : "") {
            case "WARNING":
                color = Color.parseColor("#F57C00");
                break;
            case "ERROR":
                color = Color.parseColor("#E74C3C");
                break;
            default:
                color = Color.parseColor("#4CAF50");
                break;
        }
        holder.levelBar.setBackgroundColor(color);
        holder.tvLevel.setBackgroundColor(color);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(log);
        });
    }

    @Override
    public int getItemCount() {
        return logs.size();
    }

    static class LogViewHolder extends RecyclerView.ViewHolder {
        View levelBar;
        TextView tvTimestamp, tvLevel, tvAction, tvMenu, tvMessage;

        LogViewHolder(View itemView) {
            super(itemView);
            levelBar = itemView.findViewById(R.id.levelBar);
            tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
            tvLevel = itemView.findViewById(R.id.tvLevel);
            tvAction = itemView.findViewById(R.id.tvAction);
            tvMenu = itemView.findViewById(R.id.tvMenu);
            tvMessage = itemView.findViewById(R.id.tvMessage);
        }
    }
}
