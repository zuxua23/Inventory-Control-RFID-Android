package com.example.inventory_system_ht.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "tb_search_item")
public class SearchItemEntity {
    @PrimaryKey @NonNull @ColumnInfo(name = "tag_id") public String tagId;
    @ColumnInfo(name = "epc_tag") public String epcTag;
    @ColumnInfo(name = "item_name") public String itemName;
    @ColumnInfo(name = "location") public String location;
}
