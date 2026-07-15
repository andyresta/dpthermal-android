package com.maxpoin.maxthermal.bridge;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Menyimpan riwayat cetak Print Bridge (maks 100 entri) di SharedPreferences.
 */
public final class PrintLogStore {

    private static final String PREFS_NAME = "maxthermal_print_logs";
    private static final String KEY_LOGS = "logs";
    private static final int MAX_ENTRIES = 5000;

    private static Context appContext;

    private PrintLogStore() {
    }

    /**
     * Inisialisasi store dengan konteks aplikasi.
     *
     * @param context konteks aplikasi
     */
    public static void init(Context context) {
        appContext = context.getApplicationContext();
    }

    /**
     * Menambahkan entri log cetak baru (entri terbaru di depan).
     *
     * @param endpoint path endpoint
     * @param payload  body JSON
     * @param summary  ringkasan singkat
     * @param success  status sukses
     */
    public static synchronized void add(String endpoint, String payload, String summary, boolean success) {
        if (appContext == null) return;
        try {
            JSONArray arr = loadArray();
            JSONObject entry = new PrintLogEntry(
                    UUID.randomUUID().toString(),
                    System.currentTimeMillis(),
                    endpoint,
                    payload,
                    summary,
                    success
            ).toJson();
            JSONArray updated = new JSONArray();
            updated.put(entry);
            int limit = Math.min(arr.length(), MAX_ENTRIES - 1);
            for (int i = 0; i < limit; i++) {
                updated.put(arr.getJSONObject(i));
            }
            saveArray(updated);
        } catch (JSONException ignored) {
        }
    }

    /**
     * Mengambil semua entri log (terbaru di depan).
     *
     * @return daftar entri log
     */
    public static synchronized List<PrintLogEntry> getAll() {
        if (appContext == null) return Collections.emptyList();
        List<PrintLogEntry> list = new ArrayList<>();
        try {
            JSONArray arr = loadArray();
            for (int i = 0; i < arr.length(); i++) {
                list.add(PrintLogEntry.fromJson(arr.getJSONObject(i)));
            }
        } catch (JSONException ignored) {
        }
        return list;
    }

    /**
     * Mengambil entri log dengan pagination.
     *
     * @param offset indeks awal
     * @param limit jumlah maksimal yang diambil
     * @return daftar entri log untuk halaman tersebut
     */
    public static synchronized List<PrintLogEntry> getPaged(int offset, int limit) {
        if (appContext == null) return Collections.emptyList();
        List<PrintLogEntry> list = new ArrayList<>();
        try {
            JSONArray arr = loadArray();
            int start = offset;
            int end = Math.min(offset + limit, arr.length());
            for (int i = start; i < end; i++) {
                list.add(PrintLogEntry.fromJson(arr.getJSONObject(i)));
            }
        } catch (JSONException ignored) {
        }
        return list;
    }

    /**
     * Mengambil entri log berdasarkan ID.
     *
     * @param id identifikator entri
     * @return entri atau null jika tidak ditemukan
     */
    public static synchronized PrintLogEntry getById(String id) {
        if (id == null) return null;
        for (PrintLogEntry entry : getAll()) {
            if (id.equals(entry.getId())) {
                return entry;
            }
        }
        return null;
    }

    /**
     * Menghapus semua log cetak.
     */
    public static synchronized void clearAll() {
        if (appContext == null) return;
        prefs().edit().remove(KEY_LOGS).apply();
    }

    /**
     * Menghitung total cetak yang berhasil hari ini.
     */
    public static synchronized int getTodayPrintCount() {
        if (appContext == null) return 0;
        int count = 0;
        try {
            JSONArray arr = loadArray();
            java.util.Calendar today = java.util.Calendar.getInstance();
            today.set(java.util.Calendar.HOUR_OF_DAY, 0);
            today.set(java.util.Calendar.MINUTE, 0);
            today.set(java.util.Calendar.SECOND, 0);
            today.set(java.util.Calendar.MILLISECOND, 0);
            long startOfToday = today.getTimeInMillis();

            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject obj = arr.getJSONObject(i);
                long timestamp = obj.optLong("timestamp", 0);
                boolean success = obj.optBoolean("success", false);
                if (success && timestamp >= startOfToday) {
                    count++;
                }
                if (timestamp < startOfToday) {
                    break; // Log diurutkan dari yang terbaru
                }
            }
        } catch (JSONException ignored) {
        }
        return count;
    }

    /**
     * Mengambil statistik jumlah cetak sukses 7 hari terakhir.
     * Index 0 = hari ini, Index 6 = 6 hari yang lalu.
     */
    public static synchronized int[] getWeeklyStats() {
        int[] stats = new int[7];
        if (appContext == null) return stats;
        
        try {
            JSONArray arr = loadArray();
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
            cal.set(java.util.Calendar.MINUTE, 0);
            cal.set(java.util.Calendar.SECOND, 0);
            cal.set(java.util.Calendar.MILLISECOND, 0);
            long startOfToday = cal.getTimeInMillis();
            long msPerDay = 24L * 60 * 60 * 1000L;
            long startOf7DaysAgo = startOfToday - (6 * msPerDay);

            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject obj = arr.getJSONObject(i);
                long timestamp = obj.optLong("timestamp", 0);
                boolean success = obj.optBoolean("success", false);
                
                if (timestamp < startOf7DaysAgo) {
                    break;
                }
                
                if (success) {
                    long diff = startOfToday - timestamp;
                    int daysAgo = diff <= 0 ? 0 : (int) (diff / msPerDay) + 1;
                    if (daysAgo >= 0 && daysAgo < 7) {
                        stats[daysAgo]++;
                    }
                }
            }
        } catch (JSONException ignored) {
        }
        return stats;
    }

    private static SharedPreferences prefs() {
        return appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static JSONArray loadArray() {
        String raw = prefs().getString(KEY_LOGS, "[]");
        try {
            return new JSONArray(raw);
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    private static void saveArray(JSONArray arr) {
        prefs().edit().putString(KEY_LOGS, arr.toString()).apply();
    }
}
