package com.example.inventory_system_ht.database;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import com.example.inventory_system_ht.entity.AppLogEntity;
import com.example.inventory_system_ht.entity.DeliveryOrderEntity;
import com.example.inventory_system_ht.entity.ItemCacheEntity;
import com.example.inventory_system_ht.entity.PendingSubmitEntity;
import com.example.inventory_system_ht.entity.PendingTagRegistrationEntity;
import com.example.inventory_system_ht.entity.ScanQueueEntity;
import com.example.inventory_system_ht.entity.SearchItemEntity;
import com.example.inventory_system_ht.entity.SessionItemEntity;
import com.example.inventory_system_ht.entity.StockInScanEntity;
import com.example.inventory_system_ht.entity.TagCacheEntity;
import com.example.inventory_system_ht.entity.TagLocalEntity;

@Database(
        entities = {
                AppLogEntity.class,
                DeliveryOrderEntity.class,
                TagLocalEntity.class,
                SearchItemEntity.class,
                TagCacheEntity.class,
                PendingSubmitEntity.class,
                PendingTagRegistrationEntity.class,
                ScanQueueEntity.class,
                SessionItemEntity.class,
                StockInScanEntity.class,
                ItemCacheEntity.class
        },
        version = 12,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {
    public abstract AppDao appDao();

    private static volatile AppDatabase INSTANCE;

    static final Migration MIGRATION_11_12 = new Migration(11, 12) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL(
                    "ALTER TABLE tb_Tag_Local ADD COLUMN location_id TEXT NOT NULL DEFAULT ''"
            );
        }
    };

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "sato_inventory_db")
                            .addMigrations(MIGRATION_11_12)
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}