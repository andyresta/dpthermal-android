package com.maxpoin.maxthermal.print;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Merender dokumen PDF dari Print Framework menjadi daftar bitmap siap cetak.
 */
public class PdfDocumentRenderer {

    /**
     * Merender semua halaman PDF ke bitmap dengan lebar thermal tertentu.
     *
     * @param descriptor file descriptor dokumen PDF
     * @param maxWidth   lebar maksimum bitmap hasil (px)
     * @return daftar bitmap per halaman
     * @throws IOException jika PDF tidak bisa dibaca
     */
    public static List<Bitmap> renderPages(ParcelFileDescriptor descriptor, int maxWidth)
            throws IOException {
        List<Bitmap> pages = new ArrayList<>();
        PdfRenderer pdfRenderer = new PdfRenderer(descriptor);
        try {
            int pageCount = pdfRenderer.getPageCount();
            for (int i = 0; i < pageCount; i++) {
                PdfRenderer.Page page = pdfRenderer.openPage(i);
                try {
                    pages.add(renderPage(page, maxWidth));
                } finally {
                    page.close();
                }
            }
        } finally {
            pdfRenderer.close();
        }
        return pages;
    }

    /**
     * Merender satu halaman PDF ke bitmap.
     *
     * @param page     halaman PDF terbuka
     * @param maxWidth lebar maksimum hasil render
     * @return bitmap halaman
     */
    private static Bitmap renderPage(PdfRenderer.Page page, int maxWidth) {
        float scale = (float) maxWidth / page.getWidth();
        int height = Math.max(1, Math.round(page.getHeight() * scale));
        Bitmap bitmap = Bitmap.createBitmap(maxWidth, height, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.WHITE);

        Matrix matrix = new Matrix();
        matrix.postScale(scale, scale);
        page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_PRINT);
        return bitmap;
    }
}
