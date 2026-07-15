package com.maxpoin.maxthermal.bridge;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Entri log cetak Print Bridge untuk ditampilkan dan dicetak ulang.
 */
public class PrintLogEntry {

    private final String id;
    private final long timestamp;
    private final String endpoint;
    private final String payload;
    private final String summary;
    private final boolean success;

    /**
     * Membuat entri log cetak baru.
     *
     * @param id        identifikator unik
     * @param timestamp waktu cetak (millis)
     * @param endpoint  path endpoint (mis. /print/receipt)
     * @param payload   body JSON asli
     * @param summary   ringkasan singkat untuk UI
     * @param success   true jika cetak berhasil
     */
    public PrintLogEntry(String id, long timestamp, String endpoint,
                         String payload, String summary, boolean success) {
        this.id = id;
        this.timestamp = timestamp;
        this.endpoint = endpoint;
        this.payload = payload;
        this.summary = summary;
        this.success = success;
    }

    public String getId() {
        return id;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getPayload() {
        return payload;
    }

    public String getSummary() {
        return summary;
    }

    public boolean isSuccess() {
        return success;
    }

    /**
     * Memformat timestamp untuk tampilan daftar log.
     *
     * @return string tanggal/waktu lokal
     */
    public String getFormattedTime() {
        SimpleDateFormat fmt = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault());
        return fmt.format(new Date(timestamp));
    }

    /**
     * Mengonversi entri ke JSONObject untuk penyimpanan.
     *
     * @return objek JSON
     * @throws JSONException jika gagal serialisasi
     */
    JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("id", id);
        obj.put("timestamp", timestamp);
        obj.put("endpoint", endpoint);
        obj.put("payload", payload);
        obj.put("summary", summary);
        obj.put("success", success);
        return obj;
    }

    /**
     * Membuat entri dari JSONObject tersimpan.
     *
     * @param obj objek JSON
     * @return PrintLogEntry
     * @throws JSONException jika field tidak valid
     */
    static PrintLogEntry fromJson(JSONObject obj) throws JSONException {
        return new PrintLogEntry(
                obj.getString("id"),
                obj.getLong("timestamp"),
                obj.getString("endpoint"),
                obj.getString("payload"),
                obj.optString("summary", ""),
                obj.optBoolean("success", true)
        );
    }
}
