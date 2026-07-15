package com.maxpoin.maxthermal.bridge;

import android.graphics.Bitmap;
import android.util.Base64;

import com.maxpoin.maxthermal.bluetooth.BluetoothPrinterManager;
import com.maxpoin.maxthermal.bluetooth.CodePage;
import com.maxpoin.maxthermal.bluetooth.PaperWidth;
import com.maxpoin.maxthermal.bluetooth.PrinterPreferenceStore;
import com.maxpoin.maxthermal.print.EscPosPrinter;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

/**
 * Mesin eksekusi cetak Print Bridge: teks, gambar, dan receipt ESC/POS.
 */
public final class BridgePrintEngine {

    private static final String REPRINT_FOOTER = "--- CETAK ULANG ---";

    /**
     * Konteks layout receipt: lebar logis (width_mm) vs lebar fisik printer.
     */
    private static final class ReceiptLayout {
        private final int logicalCharsPerLine;
        private final int physicalCharsPerLine;

        /**
         * @param logicalWidthMm  lebar dari payload width_mm
         * @param physicalWidthMm lebar kertas dari pengaturan printer
         */
        ReceiptLayout(int logicalWidthMm, int physicalWidthMm) {
            logicalCharsPerLine = PaperWidth.charsPerLine(logicalWidthMm);
            physicalCharsPerLine = PaperWidth.charsPerLine(physicalWidthMm);
        }

        /**
         * Jumlah kolom efektif per baris setelah skala ukuran font (GS !).
         */
        int effectiveChars(int size) {
            int s = Math.max(1, Math.min(8, size));
            return Math.max(1, logicalCharsPerLine / s);
        }

        /**
         * Offset spasi kiri untuk menempatkan zona cetak logis di tengah kertas fisik.
         */
        int zoneOffset(int size) {
            if (logicalCharsPerLine >= physicalCharsPerLine) {
                return 0;
            }
            int s = Math.max(1, Math.min(8, size));
            int physicalEffective = Math.max(1, physicalCharsPerLine / s);
            int logicalEffective = Math.max(1, logicalCharsPerLine / s);
            return Math.max(0, (physicalEffective - logicalEffective) / 2);
        }
    }

    private BridgePrintEngine() {
    }

    /**
     * Mencetak payload POST /print (logo + teks).
     *
     * @param manager   manager printer Bluetooth
     * @param prefs     preferensi cetak
     * @param json      body JSON request
     * @param isReprint true jika cetak ulang dari log
     * @throws Exception jika gagal cetak
     */
    public static void executePrintText(BluetoothPrinterManager manager,
                                        PrinterPreferenceStore prefs,
                                        JSONObject json,
                                        boolean isReprint) throws Exception {
        String logoBase64 = json.optString("logo", null);
        String text = json.optString("text", null);

        boolean hasLogo = logoBase64 != null && !logoBase64.trim().isEmpty();
        boolean hasText = text != null && !text.trim().isEmpty();
        if (!hasLogo && !hasText) {
            throw new IllegalArgumentException("Tidak ada konten untuk dicetak");
        }

        int printWidthPx = prefs.getPrintWidthPx();
        CodePage codePage = prefs.getCodePage();

        if (hasLogo) {
            printLogo(manager, logoBase64, printWidthPx);
        }
        if (hasText) {
            manager.write(EscPosPrinter.selectCodePage(codePage));
            byte[] textData = EscPosPrinter.buildTextData(text, codePage);
            manager.write(textData);
        }
        if (isReprint) {
            appendReprintFooter(manager, codePage, null);
        }
        finishJob(manager, prefs, prefs.isAutoCutter(), prefs.isAutoCashDrawer());
    }

    /**
     * Mencetak payload POST /print/image.
     *
     * @param manager   manager printer Bluetooth
     * @param prefs     preferensi cetak
     * @param json      body JSON request
     * @param isReprint true jika cetak ulang dari log
     * @throws Exception jika gagal cetak
     */
    public static void executePrintImage(BluetoothPrinterManager manager,
                                         PrinterPreferenceStore prefs,
                                         JSONObject json,
                                         boolean isReprint) throws Exception {
        String imageBase64 = json.optString("image", null);
        if (imageBase64 == null || imageBase64.trim().isEmpty()) {
            throw new IllegalArgumentException("Field 'image' tidak ditemukan atau kosong");
        }

        int printWidthPx = prefs.getPrintWidthPx();
        CodePage codePage = prefs.getCodePage();

        printBase64Image(manager, imageBase64, printWidthPx, "center", true);

        if (isReprint) {
            appendReprintFooter(manager, codePage, null);
        }
        finishJob(manager, prefs, prefs.isAutoCutter(), prefs.isAutoCashDrawer());
    }

    /**
     * Mencetak payload POST /print/receipt dengan dukungan cut_paper, width_mm, copies.
     *
     * @param manager   manager printer Bluetooth
     * @param prefs     preferensi cetak
     * @param json      body JSON request
     * @param isReprint true jika cetak ulang dari log
     * @throws Exception jika gagal cetak
     */
    public static void executePrintReceipt(BluetoothPrinterManager manager,
                                           PrinterPreferenceStore prefs,
                                           JSONObject json,
                                           boolean isReprint) throws Exception {
        JSONArray items = json.optJSONArray("items");
        if (items == null || items.length() == 0) {
            throw new IllegalArgumentException("Field 'items' tidak ditemukan atau kosong");
        }

        boolean cutPaper = json.has("cut_paper")
                ? json.optBoolean("cut_paper")
                : prefs.isAutoCutter();

        int widthMm = json.has("width_mm")
                ? json.optInt("width_mm")
                : prefs.getPaperWidthMm();
        if (!PaperWidth.isSupported(widthMm)) {
            widthMm = prefs.getPaperWidthMm();
        }
        int printWidthPx = PaperWidth.toPrintWidthPx(widthMm);
        int charsPerLine = PaperWidth.charsPerLine(widthMm);
        ReceiptLayout layout = new ReceiptLayout(widthMm, prefs.getPaperWidthMm());

        int copies = json.optInt("copies", 1);
        copies = Math.max(1, Math.min(10, copies));

        CodePage codePage = prefs.getCodePage();
        boolean cashDrawer = prefs.isAutoCashDrawer();

        for (int copy = 0; copy < copies; copy++) {
            manager.write(EscPosPrinter.initPrinter());
            manager.write(EscPosPrinter.selectCodePage(codePage));

            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                String type = item.optString("type", "").toLowerCase();
                switch (type) {
                    case "text":
                        processTextItem(manager, item, codePage, layout);
                        break;
                    case "qr":
                        processQrItem(manager, item, printWidthPx);
                        break;
                    case "image":
                        processImageItem(manager, item, printWidthPx);
                        break;
                    case "line":
                        processLineItem(manager, item, charsPerLine, codePage, layout);
                        break;
                    case "feed":
                        processFeedItem(manager, item);
                        break;
                    default:
                        break;
                }
            }

            manager.write(EscPosPrinter.resetFormatting());

            if (isReprint) {
                appendReprintFooter(manager, codePage, layout);
            }

            if (cutPaper) {
                manager.write(EscPosPrinter.partialCut());
            }
            if (cashDrawer) {
                manager.write(EscPosPrinter.cashDrawerPulse());
            }
        }
    }

    /**
     * Mengeksekusi cetak ulang berdasarkan entri log tersimpan.
     *
     * @param manager manager printer Bluetooth
     * @param prefs   preferensi cetak
     * @param entry   entri log
     * @throws Exception jika gagal cetak
     */
    public static void executeReprint(BluetoothPrinterManager manager,
                                      PrinterPreferenceStore prefs,
                                      PrintLogEntry entry) throws Exception {
        JSONObject json = new JSONObject(entry.getPayload());
        String endpoint = entry.getEndpoint();
        if ("/print/receipt".equals(endpoint)) {
            executePrintReceipt(manager, prefs, json, true);
        } else if ("/print/image".equals(endpoint)) {
            executePrintImage(manager, prefs, json, true);
        } else if ("/print".equals(endpoint)) {
            executePrintText(manager, prefs, json, true);
        } else {
            throw new IllegalArgumentException("Endpoint tidak didukung untuk cetak ulang");
        }
    }

    /**
     * Membuat ringkasan singkat untuk log dari endpoint dan payload.
     *
     * @param endpoint path endpoint
     * @param body     body JSON mentah
     * @return teks ringkasan
     */
    public static String buildSummary(String endpoint, String body) {
        try {
            JSONObject json = new JSONObject(body);
            if ("/print/receipt".equals(endpoint)) {
                JSONArray items = json.optJSONArray("items");
                int count = items != null ? items.length() : 0;
                int copies = Math.max(1, Math.min(10, json.optInt("copies", 1)));
                return "Receipt (" + count + " item, " + copies + " salinan)";
            }
            if ("/print/image".equals(endpoint)) {
                return "Cetak gambar";
            }
            if ("/print".equals(endpoint)) {
                String text = json.optString("text", "");
                boolean hasLogo = json.has("logo") && !json.optString("logo", "").trim().isEmpty();
                if (hasLogo && !text.isEmpty()) {
                    return "Logo + teks";
                }
                if (hasLogo) {
                    return "Cetak logo";
                }
                if (text.length() > 40) {
                    return text.substring(0, 40) + "…";
                }
                return text.isEmpty() ? "Cetak teks" : text;
            }
        } catch (Exception ignored) {
        }
        return endpoint;
    }

    /**
     * Menambahkan footer cetak ulang di akhir struk (rata tengah).
     *
     * @param layout konteks layout receipt; null untuk cetak non-receipt
     */
    private static void appendReprintFooter(BluetoothPrinterManager manager,
                                            CodePage codePage,
                                            ReceiptLayout layout) throws IOException {
        manager.write(EscPosPrinter.feedLines(2));
        manager.write(EscPosPrinter.setBold(true));
        if (layout != null) {
            manager.write(EscPosPrinter.alignLeft());
            String padded = padTextLine(REPRINT_FOOTER, "center", 1, layout) + "\n";
            manager.write(EscPosPrinter.encodeText(padded, codePage));
        } else {
            manager.write(EscPosPrinter.alignCenter());
            manager.write(EscPosPrinter.encodeText(REPRINT_FOOTER + "\n", codePage));
        }
        manager.write(EscPosPrinter.setBold(false));
        manager.write(EscPosPrinter.alignLeft());
    }

    /**
     * Menyelesaikan job cetak dengan cutter dan cash drawer sesuai preferensi.
     *
     * @param manager     manager printer
     * @param prefs       preferensi
     * @param cutPaper    potong kertas
     * @param cashDrawer  buka laci kasir
     * @throws IOException jika gagal kirim ke printer
     */
    private static void finishJob(BluetoothPrinterManager manager,
                                  PrinterPreferenceStore prefs,
                                  boolean cutPaper,
                                  boolean cashDrawer) throws IOException {
        if (cutPaper) {
            manager.write(EscPosPrinter.partialCut());
        }
        if (cashDrawer) {
            manager.write(EscPosPrinter.cashDrawerPulse());
        }
    }

    /**
     * Mencetak logo base64 (rata tengah).
     */
    private static void printLogo(BluetoothPrinterManager manager,
                                  String logoBase64,
                                  int printWidthPx) throws IOException {
        String clean = logoBase64.trim();
        if (clean.contains(",")) {
            clean = clean.substring(clean.indexOf(',') + 1);
        }
        byte[] imageBytes = Base64.decode(clean, Base64.DEFAULT);
        Bitmap bmp = EscPosPrinter.decodeSampled(imageBytes, printWidthPx);
        if (bmp != null) {
            manager.write(EscPosPrinter.alignCenter());
            EscPosPrinter printer = new EscPosPrinter(printWidthPx);
            byte[] imgData = printer.buildPrintData(bmp);
            manager.write(imgData);
            manager.write(EscPosPrinter.alignLeft());
            bmp.recycle();
        }
    }

    /**
     * Mencetak gambar base64 dengan alignment tertentu.
     *
     * @param includeInit true untuk sertakan ESC @ (job standalone); false di tengah receipt
     */
    private static void printBase64Image(BluetoothPrinterManager manager,
                                         String imageBase64,
                                         int printWidthPx,
                                         String align,
                                         boolean includeInit) throws IOException {
        String clean = imageBase64.trim();
        if (clean.contains(",")) {
            clean = clean.substring(clean.indexOf(',') + 1);
        }
        byte[] imageBytes = Base64.decode(clean, Base64.DEFAULT);
        Bitmap bmp = EscPosPrinter.decodeSampled(imageBytes, printWidthPx);
        if (bmp == null) {
            throw new IllegalArgumentException("Gambar tidak valid atau format tidak didukung");
        }
        manager.write(EscPosPrinter.align(align));
        EscPosPrinter printer = new EscPosPrinter(printWidthPx);
        byte[] imgData = printer.buildPrintData(bmp, includeInit);
        manager.write(imgData);
        manager.write(EscPosPrinter.alignLeft());
        bmp.recycle();
    }

    /**
     * Memproses item bertipe "text" dengan padding manual agar align mengikuti width_mm dan size.
     */
    private static void processTextItem(BluetoothPrinterManager manager,
                                        JSONObject item,
                                        CodePage codePage,
                                        ReceiptLayout layout) throws IOException {
        String align = item.optString("align", "left");
        int size = item.optInt("size", 1);
        String style = item.optString("style", "");
        String data = item.optString("data", "");
        if (data.isEmpty()) return;

        manager.write(EscPosPrinter.alignLeft());
        manager.write(EscPosPrinter.setSize(size));
        manager.write(EscPosPrinter.setBold(style.contains("bold")));
        manager.write(EscPosPrinter.setUnderline(style.contains("underline")));

        String normalized = data.endsWith("\n") ? data : data + "\n";
        String[] lines = normalized.split("\n", -1);
        StringBuilder output = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i == lines.length - 1 && lines[i].isEmpty()) {
                continue;
            }
            output.append(padTextLine(lines[i], align, size, layout)).append('\n');
        }
        manager.write(EscPosPrinter.encodeText(output.toString(), codePage));

        manager.write(EscPosPrinter.setSize(1));
        manager.write(EscPosPrinter.setBold(false));
        manager.write(EscPosPrinter.setUnderline(false));
    }

    /**
     * Menambahkan spasi kiri pada satu baris teks sesuai align dan lebar kertas logis.
     *
     * @param line   teks baris tanpa newline
     * @param align  left, center, atau right
     * @param size   skala font 1–8
     * @param layout konteks layout receipt
     * @return baris dengan padding spasi
     */
    private static String padTextLine(String line, String align, int size, ReceiptLayout layout) {
        int textLen = line.length();
        int effective = layout.effectiveChars(size);
        int zoneOffset = layout.zoneOffset(size);
        int pad;
        String alignLower = align != null ? align.toLowerCase() : "left";
        switch (alignLower) {
            case "center":
                pad = zoneOffset + Math.max(0, (effective - textLen) / 2);
                break;
            case "right":
                pad = zoneOffset + Math.max(0, effective - textLen);
                break;
            default:
                pad = zoneOffset;
                break;
        }
        if (pad <= 0) {
            return line;
        }
        StringBuilder sb = new StringBuilder(pad + textLen);
        for (int i = 0; i < pad; i++) {
            sb.append(' ');
        }
        sb.append(line);
        return sb.toString();
    }

    /**
     * Memproses item bertipe "qr".
     */
    private static void processQrItem(BluetoothPrinterManager manager,
                                      JSONObject item,
                                      int printWidthPx) throws IOException {
        String align = item.optString("align", "center");
        int size = item.optInt("size", 200);
        String data = item.optString("data", "");
        if (data.isEmpty()) return;

        Bitmap qr = EscPosPrinter.generateQrCode(data, size);
        if (qr != null) {
            manager.write(EscPosPrinter.align(align));
            EscPosPrinter printer = new EscPosPrinter(printWidthPx);
            byte[] imgData = printer.buildPrintData(qr, false);
            manager.write(imgData);
            manager.write(EscPosPrinter.alignLeft());
            qr.recycle();
        }
    }

    /**
     * Memproses item bertipe "image".
     */
    private static void processImageItem(BluetoothPrinterManager manager,
                                         JSONObject item,
                                         int printWidthPx) throws IOException {
        String align = item.optString("align", "center");
        String data = item.optString("data", "");
        if (data.isEmpty()) return;
        printBase64Image(manager, data, printWidthPx, align, false);
    }

    /**
     * Memproses item bertipe "line" dengan offset zona width_mm.
     */
    private static void processLineItem(BluetoothPrinterManager manager,
                                        JSONObject item,
                                        int charsPerLine,
                                        CodePage codePage,
                                        ReceiptLayout layout) throws IOException {
        String style = item.optString("style", "single");
        char c;
        switch (style.toLowerCase()) {
            case "double":
                c = '=';
                break;
            case "dotted":
                c = '.';
                break;
            default:
                c = '-';
                break;
        }
        StringBuilder sb = new StringBuilder();
        int offset = layout.zoneOffset(1);
        for (int i = 0; i < offset; i++) {
            sb.append(' ');
        }
        for (int i = 0; i < charsPerLine; i++) {
            sb.append(c);
        }
        sb.append('\n');
        manager.write(EscPosPrinter.encodeText(sb.toString(), codePage));
    }

    /**
     * Memproses item bertipe "feed".
     */
    private static void processFeedItem(BluetoothPrinterManager manager,
                                        JSONObject item) throws IOException {
        int lines = 1;
        String data = item.optString("data", "1");
        try {
            lines = Integer.parseInt(data);
        } catch (NumberFormatException ignored) {
        }
        lines = Math.max(1, Math.min(10, lines));
        manager.write(EscPosPrinter.feedLines(lines));
    }
}
