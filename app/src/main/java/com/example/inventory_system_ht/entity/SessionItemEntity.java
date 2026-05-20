package com.example.inventory_system_ht.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import com.example.inventory_system_ht.model.StockTakingModel;

@Entity(tableName = "tb_session_items")
public class SessionItemEntity {
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "epc_tag") public String epcTag = "";
    @ColumnInfo(name = "tag_id") public String tagId;
    @ColumnInfo(name = "item_id") public String itemId;
    @ColumnInfo(name = "item_name") public String itemName;
    @ColumnInfo(name = "location") public String location;
    @ColumnInfo(name = "stt_id") public String sttId;

    public StockTakingModel.SessionItem toSessionItem() {
        StockTakingModel.SessionItem s = new StockTakingModel.SessionItem();
        s.epcTag = epcTag;
        s.tagId = tagId;
        s.itemId = itemId;
        s.itemName = itemName;
        s.location = location;
        s.state = "PENDING";
        return s;
    }

    public static SessionItemEntity from(String sttId, StockTakingModel.SessionItem s) {
        SessionItemEntity e = new SessionItemEntity();
        e.sttId = sttId;
        e.epcTag = s.epcTag != null ? s.epcTag : "";
        e.tagId = s.tagId;
        e.itemId = s.itemId;
        e.itemName = s.itemName;
        e.location = s.location;
        return e;
    }
}
