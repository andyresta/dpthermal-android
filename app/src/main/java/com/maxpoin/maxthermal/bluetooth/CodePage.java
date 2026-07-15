package com.maxpoin.maxthermal.bluetooth;

/**
 * Daftar codepage yang didukung untuk cetak teks ESC/POS.
 */
public enum CodePage {

    /** UTF-8 — default, tanpa perintah ESC t. */
    UTF8("UTF-8", -1),

    /** IBM Code Page 437 (USA, Standard Europe) — ESC t 0. */
    CP437("Cp437", 0),

    /** IBM Code Page 852 (Central Europe) — ESC t 18. */
    CP852("Cp852", 18);

    private final String charsetName;
    private final int escPosN;

    CodePage(String charsetName, int escPosN) {
        this.charsetName = charsetName;
        this.escPosN = escPosN;
    }

    /**
     * Mengambil nama charset Java untuk encoding teks.
     *
     * @return nama charset (mis. UTF-8, Cp437)
     */
    public String getCharsetName() {
        return charsetName;
    }

    /**
     * Mengambil nilai n untuk perintah ESC t n.
     *
     * @return nilai n ESC/POS, atau -1 jika tidak perlu dikirim (UTF-8)
     */
    public int getEscPosN() {
        return escPosN;
    }

    /**
     * Mengonversi string penyimpanan ke enum CodePage.
     *
     * @param value nilai tersimpan (UTF8, CP437, CP852)
     * @return enum CodePage; default UTF8 jika tidak dikenali
     */
    public static CodePage fromStoredValue(String value) {
        if (value == null) return UTF8;
        try {
            return valueOf(value);
        } catch (IllegalArgumentException e) {
            return UTF8;
        }
    }
}
