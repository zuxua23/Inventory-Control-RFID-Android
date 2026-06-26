package com.example.inventory_system_ht.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "tb_scan_queue",
        indices = {@Index(value = {"stt_id", "is_synced"})}
)
public class ScanQueueEntity {
    @PrimaryKey(autoGenerate = true) public int id;
    @ColumnInfo(name = "stt_id") public String sttId;
    @ColumnInfo(name = "epc_tag") public String epcTag;
    @ColumnInfo(name = "action") public String action;
    @ColumnInfo(name = "item_id") public String itemId;
    @ColumnInfo(name = "new_tag_id") public String newTagId;
    @ColumnInfo(name = "remark") public String remark;
    @ColumnInfo(name = "is_synced") public boolean isSynced;
    @ColumnInfo(name = "created_at") public long createdAt;
}
