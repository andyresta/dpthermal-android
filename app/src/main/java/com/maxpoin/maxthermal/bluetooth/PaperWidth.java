package com.maxpoin.maxthermal.bluetooth;

/**
 * Konstanta lebar kertas thermal dan konversi ke lebar cetak dalam piksel (dot).
 */
public final class PaperWidth {

    /** Lebar kertas thermal 58 mm. */
    public static final int MM_58 = 58;

    /** Lebar kertas thermal 80 mm. */
    public static final int MM_80 = 80;

    /** Lebar cetak dalam dot untuk kertas 58 mm (203 DPI). */
    public static final int DOTS_58 = 384;

    /** Lebar cetak dalam dot untuk kertas 80 mm (203 DPI). */
    public static final int DOTS_80 = 576;

    private PaperWidth() {
    }

    /**
     * Mengonversi lebar kertas (mm) ke lebar gambar cetak dalam piksel.
     *
     * @param paperWidthMm lebar kertas 58 atau 80
     * @return lebar dalam dot/piksel
     */
    public static int toPrintWidthPx(int paperWidthMm) {
        if (paperWidthMm == MM_80) {
            return DOTS_80;
        }
        return DOTS_58;
    }

    /**
     * Mengonversi lebar kertas ke jumlah karakter per baris (font normal, size 1).
     *
     * @param paperWidthMm lebar kertas 58 atau 80
     * @return 32 untuk 58mm, 48 untuk 80mm
     */
    public static int charsPerLine(int paperWidthMm) {
        return paperWidthMm == MM_80 ? 48 : 32;
    }

    /**
     * Mengecek apakah nilai lebar kertas didukung.
     *
     * @param paperWidthMm lebar kertas dalam mm
     * @return true jika 58 atau 80
     */
    public static boolean isSupported(int paperWidthMm) {
        return paperWidthMm == MM_58 || paperWidthMm == MM_80;
    }
}
