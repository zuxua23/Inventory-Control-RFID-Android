package com.example.inventory_system_ht.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "tb_location_cache")
public class LocationCacheEntity {
    @PrimaryKey @NonNull
    @ColumnInfo(name = "loc_id") public String locId;
    @ColumnInfo(name = "loc_name") public String locName;
    @ColumnInfo(name = "cached_at") public long cachedAt;
}