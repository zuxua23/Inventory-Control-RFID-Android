package com.example.inventory_system_ht.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "tb_tag_cache", indices = {@Index(value = "tag_id")})
public class TagCacheEntity {
    @PrimaryKey @NonNull @ColumnInfo(name = "epc_tag") public String epcTag;
    @ColumnInfo(name = "tag_id") public String tagId;
    @ColumnInfo(name = "item_id") public String itemId;
    @ColumnInfo(name = "item_name") public String itemName;
    @ColumnInfo(name = "status") public String status;
    @ColumnInfo(name = "cached_at") public long cachedAt;
}
