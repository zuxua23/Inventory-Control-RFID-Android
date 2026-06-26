package com.example.inventory_system_ht.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "tb_pending_tag_registration")
public class PendingTagRegistrationEntity {
    @PrimaryKey(autoGenerate = true) public int id;
    @ColumnInfo(name = "epc_tag")   public String epcTag;
    @ColumnInfo(name = "item_id")   public String itemId;
    @ColumnInfo(name = "item_name") public String itemName;
    @ColumnInfo(name = "created_at") public long createdAt;
}