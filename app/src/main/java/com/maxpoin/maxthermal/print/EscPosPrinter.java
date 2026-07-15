package com.maxpoin.maxthermal.print;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;

import com.maxpoin.maxthermal.bluetooth.CodePage;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;

/**
 * Mengonversi gambar ke perintah ESC/POS dan mengirimkannya ke printer thermal.
 * Mendukung decode efisien dengan inSampleSize dan pembatasan tinggi maksimum.
 */
public class EscPosPrinter {

    /** Lebar cetak default untuk kertas thermal 58mm. */
    public static final int DEFAULT_PRINT_WIDTH_PX = com.maxpoin.maxthermal.bluetooth.PaperWidth.DOTS_58;

    /** Tinggi maksimum raster per perintah agar buffer printer tidak overflow. */
    private static final int MAX_RASTER_CHUNK_HEIGHT = 256;

    /** Tinggi maksimum output gambar dalam dot. Gambar lebih tinggi dari ini akan di-crop. */
    public static final int MAX_PRINT_HEIGHT_PX = 2400;

    private final int printWidthPx;

    /**
     * Membuat helper ESC/POS dengan lebar cetak tertentu.
     *
     * @param printWidthPx lebar gambar dalam piksel
     */
    public EscPosPrinter(int printWidthPx) {
        this.printWidthPx = printWidthPx;
    }

    /**
     * Men-decode byte array gambar secara efisien menggunakan inSampleSize.
     * Menghindari OOM dengan tidak memuat full-resolution ke memory jika tidak perlu.
     *
     * @param data         raw bytes gambar (PNG/JPEG/dll)
     * @param targetWidth  lebar target dalam piksel (printWidthPx)
     * @return Bitmap yang sudah di-downsample mendekati targetWidth, atau null jika gagal
     */
    public static Bitmap decodeSampled(byte[] data, int targetWidth) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, opts);

        int origWidth = opts.outWidth;
        if (origWidth <= 0) return null;

        int sampleSize = 1;
        while ((origWidth / sampleSize) > targetWidth * 2) {
            sampleSize *= 2;
        }

        opts.inJustDecodeBounds = false;
        opts.inSampleSize = sampleSize;
        return BitmapFactory.decodeByteArray(data, 0, data.length, opts);
    }

    /**
     * Membuat perintah ESC/POS lengkap dari bitmap (init + raster + feed).
     * Gambar di-scale ke lebar kertas dan dibatasi tinggi maksimum.
     *
     * @param source bitmap sumber halaman cetak
     * @return byte array perintah siap kirim ke printer
     */
    public byte[] buildPrintData(Bitmap source) {
        return buildPrintData(source, true);
    }

    /**
     * Membuat perintah ESC/POS dari bitmap dengan opsi inisialisasi printer.
     * Gunakan {@code includeInit=false} saat cetak raster di tengah job receipt
     * agar tidak mereset margin/print area yang sudah diset.
     *
     * @param source      bitmap sumber halaman cetak
     * @param includeInit true untuk sertakan ESC @ di awal
     * @return byte array perintah siap kirim ke printer
     */
    public byte[] buildPrintData(Bitmap source, boolean includeInit) {
        Bitmap scaled = scaleToPrintWidth(source);
        Bitmap limited = limitHeight(scaled);
        if (scaled != source && scaled != limited) {
            scaled.recycle();
        }
        Bitmap monochrome = toMonochrome(limited);
        if (limited != source && limited != monochrome) {
            limited.recycle();
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (includeInit) {
            output.write(initPrinter(), 0, initPrinter().length);
        }
        appendRasterChunks(output, monochrome);

        if (monochrome != source) {
            monochrome.recycle();
        }
        return output.toByteArray();
    }

    /**
     * Membuat byte perintah inisialisasi printer (reset).
     *
     * @return perintah ESC @
     */
    public static byte[] initPrinter() {
        return new byte[]{0x1B, 0x40};
    }

    /**
     * Mengatur left margin printer dalam dot (GS L nL nH).
     *
     * @param marginPx margin kiri dalam dot/piksel
     * @return perintah GS L
     */
    public static byte[] setLeftMarginDots(int marginPx) {
        int m = Math.max(0, marginPx);
        return new byte[]{0x1D, 0x4C, (byte) (m & 0xFF), (byte) ((m >> 8) & 0xFF)};
    }

    /**
     * Mengatur lebar print area printer dalam dot (GS W nL nH).
     *
     * @param widthPx lebar area cetak dalam dot/piksel
     * @return perintah GS W
     */
    public static byte[] setPrintAreaWidthDots(int widthPx) {
        int w = Math.max(1, widthPx);
        return new byte[]{0x1D, 0x57, (byte) (w & 0xFF), (byte) ((w >> 8) & 0xFF)};
    }

    /**
     * Menerapkan print area logis di tengah kertas fisik.
     * Jika lebar logis lebih kecil dari fisik, margin kiri di-center-kan
     * agar alignment teks/gambar mengikuti {@code width_mm} payload.
     *
     * @param physicalPx lebar fisik printer dalam dot (dari pengaturan app)
     * @param logicalPx  lebar logis dari payload width_mm dalam dot
     * @return byte array perintah GS L + GS W siap kirim
     */
    public static byte[] applyLogicalPrintArea(int physicalPx, int logicalPx) {
        int physical = Math.max(1, physicalPx);
        int logical = Math.max(1, Math.min(logicalPx, physical));
        int leftMargin = (physical - logical) / 2;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] marginCmd = setLeftMarginDots(leftMargin);
        byte[] widthCmd = setPrintAreaWidthDots(logical);
        out.write(marginCmd, 0, marginCmd.length);
        out.write(widthCmd, 0, widthCmd.length);
        return out.toByteArray();
    }

    /**
     * Membuat byte perintah alignment tengah (ESC a 1).
     *
     * @return perintah ESC/POS untuk rata tengah
     */
    public static byte[] alignCenter() {
        return new byte[]{0x1B, 0x61, 0x01};
    }

    /**
     * Membuat byte perintah alignment kiri (ESC a 0).
     *
     * @return perintah ESC/POS untuk rata kiri (default)
     */
    public static byte[] alignLeft() {
        return new byte[]{0x1B, 0x61, 0x00};
    }

    /**
     * Membuat byte perintah alignment kanan (ESC a 2).
     *
     * @return perintah ESC/POS untuk rata kanan
     */
    public static byte[] alignRight() {
        return new byte[]{0x1B, 0x61, 0x02};
    }

    /**
     * Membuat byte perintah alignment berdasarkan string ("left"/"center"/"right").
     *
     * @param align nama alignment
     * @return perintah ESC a
     */
    public static byte[] align(String align) {
        if (align == null) return alignLeft();
        switch (align.toLowerCase()) {
            case "center": return alignCenter();
            case "right":  return alignRight();
            default:       return alignLeft();
        }
    }

    /**
     * Mengatur ukuran karakter (GS ! n). Skala 1-8 untuk lebar dan tinggi.
     *
     * @param size skala 1 (normal) sampai 8 (8x besar)
     * @return perintah GS !
     */
    public static byte[] setSize(int size) {
        int s = Math.max(1, Math.min(8, size)) - 1;
        int n = (s << 4) | s;
        return new byte[]{0x1D, 0x21, (byte) n};
    }

    /**
     * Mengatur mode bold/tebal (ESC E n).
     *
     * @param on true untuk aktifkan bold
     * @return perintah ESC E
     */
    public static byte[] setBold(boolean on) {
        return new byte[]{0x1B, 0x45, (byte) (on ? 1 : 0)};
    }

    /**
     * Mengatur mode underline/garis bawah (ESC - n).
     *
     * @param on true untuk aktifkan underline
     * @return perintah ESC -
     */
    public static byte[] setUnderline(boolean on) {
        return new byte[]{0x1B, 0x2D, (byte) (on ? 1 : 0)};
    }

    /**
     * Mereset semua formatting ke default (ukuran normal, tanpa bold/underline, rata kiri).
     *
     * @return array perintah reset formatting
     */
    public static byte[] resetFormatting() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] s = setSize(1);
        byte[] b = setBold(false);
        byte[] u = setUnderline(false);
        byte[] a = alignLeft();
        out.write(s, 0, s.length);
        out.write(b, 0, b.length);
        out.write(u, 0, u.length);
        out.write(a, 0, a.length);
        return out.toByteArray();
    }

    /**
     * Membuat bitmap QR code dari string data menggunakan ZXing.
     *
     * @param data konten QR (URL, teks, dll)
     * @param sizePx ukuran QR dalam piksel (lebar = tinggi)
     * @return Bitmap QR code hitam-putih, atau null jika gagal
     */
    public static Bitmap generateQrCode(String data, int sizePx) {
        try {
            com.google.zxing.qrcode.QRCodeWriter writer = new com.google.zxing.qrcode.QRCodeWriter();
            java.util.Map<com.google.zxing.EncodeHintType, Object> hints = new java.util.EnumMap<>(com.google.zxing.EncodeHintType.class);
            hints.put(com.google.zxing.EncodeHintType.MARGIN, 1);
            com.google.zxing.common.BitMatrix matrix = writer.encode(data, com.google.zxing.BarcodeFormat.QR_CODE, sizePx, sizePx, hints);
            int w = matrix.getWidth();
            int h = matrix.getHeight();
            int[] pixels = new int[w * h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    pixels[y * w + x] = matrix.get(x, y) ? Color.BLACK : Color.WHITE;
                }
            }
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            bmp.setPixels(pixels, 0, w, 0, 0, w, h);
            return bmp;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Membuat byte perintah feed baris.
     *
     * @param lines jumlah baris kosong
     * @return perintah ESC d n
     */
    public static byte[] feedLines(int lines) {
        return new byte[]{0x1B, 0x64, (byte) lines};
    }

    /**
     * Membuat byte perintah partial cut (jika printer mendukung).
     *
     * @return perintah GS V
     */
    public static byte[] partialCut() {
        return new byte[]{0x1D, 0x56, 0x42, 0x00};
    }

    /**
     * Membuat byte perintah buka cash drawer (ESC p).
     *
     * @return perintah ESC p pin 50ms 250ms
     */
    public static byte[] cashDrawerPulse() {
        return new byte[]{0x1B, 0x70, 0x00, 0x32, (byte) 0xFA};
    }

    /**
     * Membuat perintah ESC/POS dari teks biasa (UTF-8).
     *
     * @param text teks yang ingin dicetak
     * @return byte array perintah siap kirim ke printer
     */
    public static byte[] buildTextData(String text) {
        return buildTextData(text, CodePage.UTF8);
    }

    /**
     * Membuat perintah ESC/POS dari teks dengan codepage tertentu.
     *
     * @param text     teks yang ingin dicetak
     * @param codePage codepage encoding
     * @return byte array perintah siap kirim ke printer
     */
    public static byte[] buildTextData(String text, CodePage codePage) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] init = initPrinter();
        output.write(init, 0, init.length);
        byte[] cp = selectCodePage(codePage);
        output.write(cp, 0, cp.length);
        try {
            String toPrint = text.endsWith("\n") ? text : text + "\n";
            byte[] textBytes = encodeText(toPrint, codePage);
            output.write(textBytes, 0, textBytes.length);
        } catch (Exception e) {
            byte[] fallback = (text + "\n").getBytes();
            output.write(fallback, 0, fallback.length);
        }
        return output.toByteArray();
    }

    /**
     * Mengirim perintah pemilihan codepage ESC t n (kosong jika UTF-8).
     *
     * @param codePage codepage target
     * @return byte perintah ESC t atau array kosong
     */
    public static byte[] selectCodePage(CodePage codePage) {
        if (codePage == null || codePage == CodePage.UTF8 || codePage.getEscPosN() < 0) {
            return new byte[0];
        }
        return new byte[]{0x1B, 0x74, (byte) codePage.getEscPosN()};
    }

    /**
     * Meng-encode teks ke byte array sesuai codepage.
     *
     * @param text     teks sumber
     * @param codePage codepage encoding
     * @return byte array teks
     * @throws UnsupportedEncodingException jika charset tidak didukung
     */
    public static byte[] encodeText(String text, CodePage codePage) throws UnsupportedEncodingException {
        CodePage cp = codePage != null ? codePage : CodePage.UTF8;
        return text.getBytes(cp.getCharsetName());
    }

    /**
     * Menskalakan bitmap agar lebarnya sesuai kertas thermal.
     *
     * @param source bitmap asli
     * @return bitmap ter-skala
     */
    private Bitmap scaleToPrintWidth(Bitmap source) {
        if (source.getWidth() == printWidthPx) {
            return source;
        }
        float scale = (float) printWidthPx / source.getWidth();
        Matrix matrix = new Matrix();
        matrix.postScale(scale, scale);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    /**
     * Membatasi tinggi bitmap agar tidak melebihi MAX_PRINT_HEIGHT_PX.
     * Jika gambar lebih tinggi, hanya bagian atas yang diambil (crop).
     *
     * @param source bitmap yang sudah di-scale ke lebar kertas
     * @return bitmap dengan tinggi terbatas, atau source asli jika sudah memenuhi
     */
    private Bitmap limitHeight(Bitmap source) {
        if (source.getHeight() <= MAX_PRINT_HEIGHT_PX) {
            return source;
        }
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), MAX_PRINT_HEIGHT_PX);
    }

    /**
     * Mengubah bitmap ke hitam-putih threshold untuk raster ESC/POS.
     *
     * @param source bitmap ter-skala
     * @return bitmap monochrome
     */
    private Bitmap toMonochrome(Bitmap source) {
        int width = source.getWidth();
        int height = source.getHeight();
        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        int[] pixels = new int[width * height];
        source.getPixels(pixels, 0, width, 0, 0, width, height);
        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            int gray = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3;
            pixels[i] = gray < 140 ? Color.BLACK : Color.WHITE;
        }
        result.setPixels(pixels, 0, width, 0, 0, width, height);
        return result;
    }

    /**
     * Menambahkan data raster ke stream dalam beberapa chunk jika gambar tinggi.
     *
     * @param output   stream tujuan
     * @param bitmap   bitmap monochrome
     */
    private void appendRasterChunks(ByteArrayOutputStream output, Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int bytesPerRow = (width + 7) / 8;
        int offsetY = 0;
        while (offsetY < height) {
            int chunkHeight = Math.min(MAX_RASTER_CHUNK_HEIGHT, height - offsetY);
            byte[] rasterData = extractRasterChunk(bitmap, offsetY, chunkHeight, bytesPerRow);
            byte[] command = buildRasterCommand(bytesPerRow, chunkHeight, rasterData);
            output.write(command, 0, command.length);
            offsetY += chunkHeight;
        }
    }

    /**
     * Mengekstrak potongan raster dari bitmap monochrome.
     *
     * @param bitmap      bitmap sumber
     * @param offsetY     baris awal
     * @param chunkHeight tinggi chunk
     * @param bytesPerRow byte per baris horizontal
     * @return data raster
     */
    private byte[] extractRasterChunk(Bitmap bitmap, int offsetY, int chunkHeight, int bytesPerRow) {
        int width = bitmap.getWidth();
        byte[] data = new byte[bytesPerRow * chunkHeight];
        for (int y = 0; y < chunkHeight; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = bitmap.getPixel(x, offsetY + y);
                if (pixel == Color.BLACK) {
                    int index = y * bytesPerRow + (x / 8);
                    data[index] |= (byte) (0x80 >> (x % 8));
                }
            }
        }
        return data;
    }

    /**
     * Membuat perintah GS v 0 beserta data raster.
     *
     * @param bytesPerRow lebar data per baris dalam byte
     * @param height      tinggi raster dalam dot
     * @param rasterData  data piksel
     * @return perintah lengkap
     */
    private byte[] buildRasterCommand(int bytesPerRow, int height, byte[] rasterData) {
        ByteArrayOutputStream command = new ByteArrayOutputStream();
        command.write(0x1D);
        command.write(0x76);
        command.write(0x30);
        command.write(0x00);
        command.write(bytesPerRow & 0xFF);
        command.write((bytesPerRow >> 8) & 0xFF);
        command.write(height & 0xFF);
        command.write((height >> 8) & 0xFF);
        command.write(rasterData, 0, rasterData.length);
        return command.toByteArray();
    }
}
