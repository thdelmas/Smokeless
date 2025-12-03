package com.smokless.smokeless.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.smokless.smokeless.data.dao.SmokingSessionDao;
import com.smokless.smokeless.data.entity.SmokingSession;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(entities = {SmokingSession.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    
    public abstract SmokingSessionDao smokingSessionDao();
    
    private static volatile AppDatabase INSTANCE;
    
    public static final ExecutorService databaseExecutor = 
            Executors.newFixedThreadPool(4);
    
    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "smokeless_database"
                    )
                    .addCallback(new Callback() {
                        @Override
                        public void onCreate(@NonNull SupportSQLiteDatabase db) {
                            super.onCreate(db);
                            // Insert initial timestamp when database is first created
                            databaseExecutor.execute(() -> {
                                SmokingSessionDao dao = INSTANCE.smokingSessionDao();
                                if (dao.getSessionCount() == 0) {
                                    dao.insert(new SmokingSession(System.currentTimeMillis()));
                                }
                            });
                        }
                    })
                    .build();
                }
            }
        }
        return INSTANCE;
    }
}

