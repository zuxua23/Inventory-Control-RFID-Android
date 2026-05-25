package com.example.inventory_system_ht.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "tb_app_log")
public class AppLogEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "timestamp")
    public long timestamp;

    @ColumnInfo(name = "level")
    public String level;

    @ColumnInfo(name = "action")
    public String action;

    @ColumnInfo(name = "menu")
    public String menu;

    @ColumnInfo(name = "entity")
    public String entity;

    @ColumnInfo(name = "message")
    public String message;

    @ColumnInfo(name = "user_id")
    public String userId;

    @ColumnInfo(name = "request_api")
    public String requestApi;

    @ColumnInfo(name = "response_api")
    public String responseApi;
}
