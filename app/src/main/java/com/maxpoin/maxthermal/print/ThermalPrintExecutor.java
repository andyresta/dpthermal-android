package com.maxpoin.maxthermal.print;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.ParcelFileDescriptor;
import android.print.PrintDocumentInfo;
import android.printservice.PrintJob;

import com.maxpoin.maxthermal.bluetooth.BluetoothPrinterManager;
import com.maxpoin.maxthermal.bluetooth.PrinterPreferenceStore;

import java.io.IOException;
import java.util.List;

/**
 * Menjalankan job cetak dari Android Print Framework ke printer thermal Bluetooth.
 */
public class ThermalPrintExecutor {

    private static final long CONNECT_TIMEOUT_MS = 20_000L;

    private ThermalPrintExecutor() {
    }

    /**
     * Memproses dan mencetak satu PrintJob ke printer Bluetooth.
     *
     * @param context  konteks dari service (untuk akses cache)
     * @param printJob job cetak dari sistem Android
     */
    public static void execute(android.content.Context context, PrintJob printJob) {
        if (printJob.isCancelled()) {
            return;
        }

        printJob.start();

        BluetoothPrinterManager manager = BluetoothPrinterManager.getInstance();
        int printWidthPx = manager.getPreferenceStore().getPrintWidthPx();
        EscPosPrinter escPosPrinter = new EscPosPrinter(printWidthPx);

        try {
            if (!manager.ensureConnectedForPrint(CONNECT_TIMEOUT_MS)) {
                failJob(printJob, "Printer Bluetooth belum terhubung. Buka DPThermal dan hubungkan printer.");
                return;
            }

            ParcelFileDescriptor documentData = printJob.getDocument().getData();
            if (documentData == null) {
                failJob(printJob, "Dokumen cetak kosong");
                return;
            }

            try {
                printDocument(context, printJob, manager, escPosPrinter, documentData);
            } finally {
                try {
                    documentData.close();
                } catch (IOException ignored) {
                }
            }

            if (printJob.isCancelled()) {
                printJob.cancel();
            } else {
                PrinterPreferenceStore prefs = manager.getPreferenceStore();
                if (prefs.isAutoCutter()) {
                    manager.write(EscPosPrinter.partialCut());
                }
                if (prefs.isAutoCashDrawer()) {
                    manager.write(EscPosPrinter.cashDrawerPulse());
                }
                printJob.complete();
                
                String docName = "Document";
                if (printJob.getDocument() != null && printJob.getDocument().getInfo() != null) {
                    docName = printJob.getDocument().getInfo().getName();
                }
                com.maxpoin.maxthermal.bridge.PrintLogStore.add("/spooler/" + docName, "PDF/Image Data", "Dicetak dari Android Print Spooler", true);
            }
        } catch (Exception e) {
            failJob(printJob, e.getMessage() != null ? e.getMessage() : "Gagal mencetak");
        }
    }

    /**
     * Mencetak isi dokumen sesuai tipe konten (PDF atau gambar).
     *
     * @param context       konteks untuk cache file
     * @param printJob      job cetak aktif
     * @param manager       manager koneksi Bluetooth
     * @param escPosPrinter helper ESC/POS
     * @param documentData  file descriptor dokumen
     * @throws IOException jika gagal membaca atau menulis
     */
    private static void printDocument(
            android.content.Context context,
            PrintJob printJob,
            BluetoothPrinterManager manager,
            EscPosPrinter escPosPrinter,
            ParcelFileDescriptor documentData) throws IOException {

        PrintDocumentInfo info = printJob.getDocument().getInfo();
        int contentType = info != null
                ? info.getContentType()
                : PrintDocumentInfo.CONTENT_TYPE_UNKNOWN;

        if (contentType == PrintDocumentInfo.CONTENT_TYPE_PHOTO) {
            printPhoto(manager, escPosPrinter, documentData, printJob);
            return;
        }

        // Cache documentData ke lokal untuk memastikan ParcelFileDescriptor bersifat "seekable" (wajib untuk PdfRenderer)
        java.io.File tempFile = new java.io.File(context.getCacheDir(), "temp_print_" + System.currentTimeMillis() + ".pdf");
        ParcelFileDescriptor cachedDescriptor = null;
        try {
            try (java.io.InputStream in = new java.io.FileInputStream(documentData.getFileDescriptor());
                 java.io.OutputStream out = new java.io.FileOutputStream(tempFile)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }

            cachedDescriptor = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY);

            try {
                printPdf(manager, escPosPrinter, cachedDescriptor, printJob);
            } catch (IOException pdfError) {
                printPhoto(manager, escPosPrinter, cachedDescriptor, printJob);
            }
        } finally {
            if (cachedDescriptor != null) {
                try {
                    cachedDescriptor.close();
                } catch (IOException ignored) {
                }
            }
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    /**
     * Mencetak dokumen PDF (format umum dari Chrome).
     *
     * @param manager       manager Bluetooth
     * @param escPosPrinter helper ESC/POS
     * @param documentData  file descriptor PDF
     * @param printJob      job untuk cek pembatalan
     * @throws IOException jika render atau kirim gagal
     */
    private static void printPdf(
            BluetoothPrinterManager manager,
            EscPosPrinter escPosPrinter,
            ParcelFileDescriptor documentData,
            PrintJob printJob) throws IOException {

        int printWidthPx = manager.getPreferenceStore().getPrintWidthPx();
        List<Bitmap> pages = PdfDocumentRenderer.renderPages(documentData, printWidthPx);
        try {
            for (Bitmap page : pages) {
                if (printJob.isCancelled()) {
                    return;
                }
                byte[] printData = escPosPrinter.buildPrintData(page);
                manager.write(printData);
            }
        } finally {
            for (Bitmap page : pages) {
                if (page != null && !page.isRecycled()) {
                    page.recycle();
                }
            }
        }
    }

    /**
     * Mencetak dokumen sebagai gambar bitmap (PNG/JPEG).
     *
     * @param manager       manager Bluetooth
     * @param escPosPrinter helper ESC/POS
     * @param documentData  file descriptor gambar
     * @param printJob      job untuk cek pembatalan
     * @throws IOException jika decode atau kirim gagal
     */
    private static void printPhoto(
            BluetoothPrinterManager manager,
            EscPosPrinter escPosPrinter,
            ParcelFileDescriptor documentData,
            PrintJob printJob) throws IOException {

        Bitmap bitmap = BitmapFactory.decodeFileDescriptor(documentData.getFileDescriptor());
        if (bitmap == null) {
            throw new IOException("Format dokumen tidak didukung");
        }
        try {
            if (printJob.isCancelled()) {
                return;
            }
            byte[] printData = escPosPrinter.buildPrintData(bitmap);
            manager.write(printData);
        } finally {
            bitmap.recycle();
        }
    }

    /**
     * Menandai job cetak gagal dengan pesan error.
     *
     * @param printJob job cetak
     * @param message  pesan error untuk pengguna
     */
    private static void failJob(PrintJob printJob, String message) {
        if (!printJob.isCancelled()) {
            printJob.fail(message);
        }
        String docName = "Document";
        try {
            if (printJob != null && printJob.getDocument() != null && printJob.getDocument().getInfo() != null) {
                docName = printJob.getDocument().getInfo().getName();
            }
        } catch (Exception ignored) {
        }
        com.maxpoin.maxthermal.bridge.PrintLogStore.add("/spooler/" + docName, message, "Gagal dicetak dari Spooler", false);
    }
}
