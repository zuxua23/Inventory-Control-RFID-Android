package com.example.inventory_system_ht.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import com.example.inventory_system_ht.model.StockTakingResponses;

@Entity(
        tableName = "tb_session_items",
        indices = {@Index(value = "stt_id")}
)public class SessionItemEntity {
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "epc_tag") public String epcTag = "";
    @ColumnInfo(name = "tag_id") public String tagId;
    @ColumnInfo(name = "item_id") public String itemId;
    @ColumnInfo(name = "item_code") public String itemCode;
    @ColumnInfo(name = "item_name") public String itemName;
    @ColumnInfo(name = "location") public String location;
    @ColumnInfo(name = "stt_id") public String sttId;

    public StockTakingResponses.SessionItem toSessionItem() {
        StockTakingResponses.SessionItem s = new StockTakingResponses.SessionItem();
        s.epcTag = epcTag;
        s.tagId = tagId;
        s.itemId = itemId;
        s.itemCode = itemCode;
        s.itemName = itemName;
        s.location = location;
        s.state = "PENDING";
        return s;
    }

    public static SessionItemEntity from(String sttId, StockTakingResponses.SessionItem s) {
        SessionItemEntity e = new SessionItemEntity();
        e.sttId = sttId;
        e.epcTag = s.epcTag != null ? s.epcTag : "";
        e.tagId = s.tagId;
        e.itemId = s.itemId;
        e.itemCode = s.itemCode;
        e.itemName = s.itemName;
        e.location = s.location;
        return e;
    }
}
