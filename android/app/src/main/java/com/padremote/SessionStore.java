package com.padremote;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/** Walking-session history, kept on the phone only. */
public final class SessionStore extends SQLiteOpenHelper {
    private static SessionStore instance;

    public static synchronized SessionStore get(Context ctx) {
        if (instance == null) instance = new SessionStore(ctx.getApplicationContext());
        return instance;
    }

    private SessionStore(Context ctx) { super(ctx, "sessions.db", null, 2); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE sessions (id INTEGER PRIMARY KEY AUTOINCREMENT, start_ms INTEGER NOT NULL, "
                + "end_ms INTEGER NOT NULL, dur_s INTEGER NOT NULL, dist_m INTEGER NOT NULL, steps INTEGER NOT NULL, "
                + "calories INTEGER NOT NULL, max_mph REAL NOT NULL, ended_by TEXT NOT NULL, est_flags INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX sessions_start ON sessions(start_ms)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int from, int to) {
        if (from < 2) db.execSQL("ALTER TABLE sessions ADD COLUMN est_flags INTEGER NOT NULL DEFAULT 0");
    }

    /**
     * Insert or update. A session with no id that starts within 90 s of a saved one is the same walk
     * (the app reconnected mid-walk), so it takes over that row instead of creating a duplicate.
     * Steps/calories are saved as shown: the pad's counts, or estimates when it sent 0.
     */
    public synchronized void save(SessionTracker.Session s, int weightKg, int heightCm) {
        Estimates.Shown e = Estimates.pick(s.steps, s.calories, s.distM, s.durSec, weightKg, heightCm);
        SQLiteDatabase db = getWritableDatabase();
        if (s.id < 0) {
            try (Cursor c = db.rawQuery("SELECT id FROM sessions WHERE ABS(start_ms - ?) < 90000 ORDER BY start_ms DESC LIMIT 1",
                    new String[]{String.valueOf(s.startMs)})) {
                if (c.moveToFirst()) s.id = c.getLong(0);
            }
        }
        ContentValues v = new ContentValues();
        v.put("start_ms", s.startMs); v.put("end_ms", s.endMs); v.put("dur_s", s.durSec);
        v.put("dist_m", s.distM); v.put("steps", e.steps); v.put("calories", e.calories);
        v.put("est_flags", (e.stepsEstimated ? 1 : 0) | (e.caloriesEstimated ? 2 : 0));
        v.put("max_mph", s.maxMph); v.put("ended_by", s.endedBy == null ? "" : s.endedBy);
        if (s.id < 0) s.id = db.insert("sessions", null, v);
        else db.update("sessions", v, "id = ?", new String[]{String.valueOf(s.id)});
    }

    public synchronized List<SessionTracker.Session> all() {
        List<SessionTracker.Session> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT id,start_ms,end_ms,dur_s,dist_m,steps,calories,max_mph,ended_by,est_flags FROM sessions ORDER BY start_ms DESC", null)) {
            while (c.moveToNext()) {
                SessionTracker.Session s = new SessionTracker.Session();
                s.id = c.getLong(0); s.startMs = c.getLong(1); s.endMs = c.getLong(2); s.durSec = c.getLong(3);
                s.distM = c.getLong(4); s.steps = c.getLong(5); s.calories = c.getInt(6); s.maxMph = c.getDouble(7);
                s.endedBy = c.getString(8); s.estFlags = c.getInt(9);
                out.add(s);
            }
        }
        return out;
    }

    public synchronized void delete(long id) {
        getWritableDatabase().delete("sessions", "id = ?", new String[]{String.valueOf(id)});
    }
}
