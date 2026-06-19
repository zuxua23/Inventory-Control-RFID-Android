package com.example.inventory_system_ht.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "tb_item_cache")
public class ItemCacheEntity {
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "item_id")
    public String itemId;

    @ColumnInfo(name = "item_name")
    public String itemName;

    @ColumnInfo(name = "updated_at")
    public long updatedAt;

    public ItemCacheEntity(@NonNull String itemId, String itemName, long updatedAt) {
        this.itemId = itemId;
        this.itemName = itemName;
        this.updatedAt = updatedAt;
    }
}