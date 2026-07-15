package com.maxpoin.maxthermal.bluetooth;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Menyimpan dan memuat data printer Bluetooth terakhir yang terhubung.
 */
public class PrinterPreferenceStore {

    private static final String PREFS_NAME = "maxthermal_printer_prefs";
    private static final String KEY_LAST_ADDRESS = "last_printer_address";
    private static final String KEY_LAST_NAME = "last_printer_name";
    private static final String KEY_PAPER_WIDTH_MM = "paper_width_mm";
    private static final String KEY_AUTO_CUTTER = "auto_cutter";
    private static final String KEY_AUTO_CASHDRAWER = "auto_cashdrawer";
    private static final String KEY_BRIDGE_PORT = "bridge_port";
    private static final String KEY_BRIDGE_RUNNING = "bridge_running";
    private static final String KEY_CODE_PAGE = "code_page";

    /** Port default untuk Print Bridge server. */
    public static final int DEFAULT_BRIDGE_PORT = 8080;

    /** Port minimum Print Bridge (inklusif). */
    public static final int BRIDGE_PORT_MIN = 8080;

    /** Port maksimum Print Bridge (inklusif). */
    public static final int BRIDGE_PORT_MAX = 9090;

    /** Kelipatan port Print Bridge. */
    public static final int BRIDGE_PORT_STEP = 5;

    private final SharedPreferences preferences;

    /**
     * Membuat instance penyimpanan preferensi printer.
     *
     * @param context konteks aplikasi
     */
    public PrinterPreferenceStore(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Menyimpan alamat dan nama printer terakhir yang berhasil terhubung.
     *
     * @param address alamat MAC Bluetooth printer
     * @param name    nama perangkat printer
     */
    public void saveLastPrinter(String address, String name) {
        preferences.edit()
                .putString(KEY_LAST_ADDRESS, address)
                .putString(KEY_LAST_NAME, name)
                .apply();
    }

    /**
     * Mengambil alamat MAC printer terakhir yang tersimpan.
     *
     * @return alamat MAC atau null jika belum pernah tersimpan
     */
    public String getLastPrinterAddress() {
        return preferences.getString(KEY_LAST_ADDRESS, null);
    }

    /**
     * Mengambil nama printer terakhir yang tersimpan.
     *
     * @return nama printer atau null jika belum pernah tersimpan
     */
    public String getLastPrinterName() {
        return preferences.getString(KEY_LAST_NAME, null);
    }

    /**
     * Menghapus data printer terakhir dari penyimpanan.
     */
    public void clearLastPrinter() {
        preferences.edit()
                .remove(KEY_LAST_ADDRESS)
                .remove(KEY_LAST_NAME)
                .apply();
    }

    /**
     * Mengecek apakah sudah ada data printer terakhir yang tersimpan.
     *
     * @return true jika alamat printer tersimpan
     */
    public boolean hasLastPrinter() {
        String address = getLastPrinterAddress();
        return address != null && !address.isEmpty();
    }

    /**
     * Menyimpan pilihan lebar kertas thermal (58 atau 80 mm).
     *
     * @param paperWidthMm lebar kertas dalam mm
     */
    public void setPaperWidthMm(int paperWidthMm) {
        int value = PaperWidth.isSupported(paperWidthMm) ? paperWidthMm : PaperWidth.MM_58;
        preferences.edit().putInt(KEY_PAPER_WIDTH_MM, value).apply();
    }

    /**
     * Mengambil lebar kertas yang tersimpan.
     *
     * @return 58 atau 80 (mm); default 58 jika belum diset
     */
    public int getPaperWidthMm() {
        int saved = preferences.getInt(KEY_PAPER_WIDTH_MM, PaperWidth.MM_58);
        return PaperWidth.isSupported(saved) ? saved : PaperWidth.MM_58;
    }

    /**
     * Mengambil lebar cetak dalam piksel sesuai kertas yang dipilih.
     *
     * @return 384 untuk 58mm, 576 untuk 80mm
     */
    public int getPrintWidthPx() {
        return PaperWidth.toPrintWidthPx(getPaperWidthMm());
    }

    /**
     * Menyimpan status Auto Cutter.
     *
     * @param enabled true jika auto cutter aktif
     */
    public void setAutoCutter(boolean enabled) {
        preferences.edit().putBoolean(KEY_AUTO_CUTTER, enabled).apply();
    }

    /**
     * Mengambil status Auto Cutter.
     *
     * @return true jika auto cutter aktif (default: true)
     */
    public boolean isAutoCutter() {
        return preferences.getBoolean(KEY_AUTO_CUTTER, true);
    }

    /**
     * Menyimpan status Auto Open Cash Drawer.
     *
     * @param enabled true jika auto cash drawer aktif
     */
    public void setAutoCashDrawer(boolean enabled) {
        preferences.edit().putBoolean(KEY_AUTO_CASHDRAWER, enabled).apply();
    }

    /**
     * Mengambil status Auto Open Cash Drawer.
     *
     * @return true jika auto cash drawer aktif (default: false)
     */
    public boolean isAutoCashDrawer() {
        return preferences.getBoolean(KEY_AUTO_CASHDRAWER, false);
    }

    /**
     * Menormalisasi nomor port ke rentang dan kelipatan yang diizinkan (8080–9090, step 5).
     *
     * @param port port mentah
     * @return port valid terdekat ke bawah dalam rentang
     */
    public static int normalizeBridgePort(int port) {
        int clamped = Math.max(BRIDGE_PORT_MIN, Math.min(BRIDGE_PORT_MAX, port));
        return BRIDGE_PORT_MIN
                + ((clamped - BRIDGE_PORT_MIN) / BRIDGE_PORT_STEP) * BRIDGE_PORT_STEP;
    }

    /**
     * Menyimpan port yang digunakan untuk Print Bridge server.
     *
     * @param port nomor port (dinormalisasi ke 8080–9090 kelipatan 5)
     */
    public void setBridgePort(int port) {
        preferences.edit().putInt(KEY_BRIDGE_PORT, normalizeBridgePort(port)).apply();
    }

    /**
     * Mengambil port Print Bridge server yang tersimpan.
     *
     * @return nomor port valid, default {@value DEFAULT_BRIDGE_PORT}
     */
    public int getBridgePort() {
        return normalizeBridgePort(
                preferences.getInt(KEY_BRIDGE_PORT, DEFAULT_BRIDGE_PORT));
    }

    /**
     * Mengambil port berikutnya (increment) dalam rentang yang diizinkan.
     *
     * @return port + 5, atau port saat ini jika sudah maksimum
     */
    public int getNextBridgePort() {
        int current = getBridgePort();
        if (current >= BRIDGE_PORT_MAX) {
            return current;
        }
        return Math.min(BRIDGE_PORT_MAX, current + BRIDGE_PORT_STEP);
    }

    /**
     * Mengambil port sebelumnya (decrement) dalam rentang yang diizinkan.
     *
     * @return port - 5, atau port saat ini jika sudah minimum
     */
    public int getPrevBridgePort() {
        int current = getBridgePort();
        if (current <= BRIDGE_PORT_MIN) {
            return current;
        }
        return Math.max(BRIDGE_PORT_MIN, current - BRIDGE_PORT_STEP);
    }

    /**
     * Mengecek apakah port masih bisa dinaikkan.
     *
     * @return true jika belum mencapai {@link #BRIDGE_PORT_MAX}
     */
    public boolean canIncrementBridgePort() {
        return getBridgePort() < BRIDGE_PORT_MAX;
    }

    /**
     * Mengecek apakah port masih bisa diturunkan.
     *
     * @return true jika belum mencapai {@link #BRIDGE_PORT_MIN}
     */
    public boolean canDecrementBridgePort() {
        return getBridgePort() > BRIDGE_PORT_MIN;
    }

    /**
     * Menyimpan status apakah Print Bridge server sedang berjalan.
     * Dipanggil oleh {@code PrintBridgeService} saat start/stop.
     *
     * @param running true jika server aktif
     */
    public void setBridgeRunning(boolean running) {
        preferences.edit().putBoolean(KEY_BRIDGE_RUNNING, running).apply();
    }

    /**
     * Mengambil status terakhir Print Bridge server.
     * Digunakan Fragment untuk sinkronisasi UI saat app dibuka ulang.
     *
     * @return true jika service tercatat sedang berjalan
     */
    public boolean isBridgeRunning() {
        return preferences.getBoolean(KEY_BRIDGE_RUNNING, false);
    }

    /**
     * Menyimpan codepage teks ESC/POS (UTF8, CP437, CP852).
     *
     * @param codePage enum codepage
     */
    public void setCodePage(CodePage codePage) {
        CodePage value = codePage != null ? codePage : CodePage.UTF8;
        preferences.edit().putString(KEY_CODE_PAGE, value.name()).apply();
    }

    /**
     * Mengambil codepage teks yang tersimpan.
     *
     * @return codepage; default UTF8
     */
    public CodePage getCodePage() {
        return CodePage.fromStoredValue(preferences.getString(KEY_CODE_PAGE, CodePage.UTF8.name()));
    }
}
