package com.maxpoin.maxthermal.print;

import android.print.PrinterCapabilitiesInfo;
import android.print.PrinterId;
import android.print.PrinterInfo;
import android.printservice.PrinterDiscoverySession;

import com.maxpoin.maxthermal.R;
import com.maxpoin.maxthermal.bluetooth.BluetoothPrinterManager;
import com.maxpoin.maxthermal.bluetooth.PrinterConnectionState;
import com.maxpoin.maxthermal.bluetooth.PrinterPreferenceStore;

import java.util.ArrayList;
import java.util.List;

/**
 * Sesi penemuan printer untuk menampilkan printer thermal Bluetooth di dialog cetak sistem.
 */
public class MaxThermalPrinterDiscoverySession extends PrinterDiscoverySession
        implements BluetoothPrinterManager.ConnectionListener {

    private final MaxThermalPrintService printService;
    private final BluetoothPrinterManager printerManager;

    /**
     * Membuat sesi discovery printer untuk Print Service.
     *
     * @param printService service cetak induk
     */
    public MaxThermalPrinterDiscoverySession(MaxThermalPrintService printService) {
        this.printService = printService;
        this.printerManager = BluetoothPrinterManager.getInstance();
    }

    @Override
    public void onConnectionStateChanged(PrinterConnectionState state) {
        publishAvailablePrinters();
    }

    @Override
    public void onStartPrinterDiscovery(List<PrinterId> priorityList) {
        printerManager.addConnectionListener(this);
        publishAvailablePrinters();
    }

    @Override
    public void onStopPrinterDiscovery() {
        printerManager.removeConnectionListener(this);
    }

    @Override
    public void onValidatePrinters(List<PrinterId> printerIds) {
        publishAvailablePrinters();
    }

    @Override
    public void onStartPrinterStateTracking(PrinterId printerId) {
        publishAvailablePrinters();
        publishPrinterCapabilities(printerId);
    }

    @Override
    public void onStopPrinterStateTracking(PrinterId printerId) {
        // Tidak perlu aksi khusus
    }

    @Override
    public void onDestroy() {
        printerManager.removeConnectionListener(this);
    }

    /**
     * Mempublikasikan kemampuan printer (capabilities) agar Android tidak menandainya "not available".
     * Wajib dipanggil saat {@code onStartPrinterStateTracking} agar dialog cetak menganggap printer siap.
     *
     * @param printerId ID printer yang sedang dilacak
     */
    private void publishPrinterCapabilities(PrinterId printerId) {
        PrinterCapabilitiesInfo capabilities = new PrinterCapabilitiesInfo.Builder(printerId)
                .setMinMargins(new android.print.PrintAttributes.Margins(0, 0, 0, 0))
                .addMediaSize(android.print.PrintAttributes.MediaSize.ISO_A4, true)
                .addResolution(
                        new android.print.PrintAttributes.Resolution("default", "203dpi", 203, 203),
                        true)
                .setColorModes(
                        android.print.PrintAttributes.COLOR_MODE_MONOCHROME,
                        android.print.PrintAttributes.COLOR_MODE_MONOCHROME)
                .build();

        PrinterInfo updated = new PrinterInfo.Builder(
                printerId,
                printerManager.getPreferenceStore().getLastPrinterName() != null
                        ? printerManager.getPreferenceStore().getLastPrinterName()
                        : printService.getString(R.string.printer_unknown_name),
                resolvePrinterStatus())
                .setDescription(printService.getString(R.string.print_service_printer_desc))
                .setCapabilities(capabilities)
                .build();

        List<PrinterInfo> printers = new ArrayList<>();
        printers.add(updated);
        addPrinters(printers);
    }


    private void publishAvailablePrinters() {
        PrinterPreferenceStore prefs = printerManager.getPreferenceStore();
        List<PrinterInfo> printers = new ArrayList<>();

        if (prefs.hasLastPrinter()) {
            String name = prefs.getLastPrinterName();
            if (name == null || name.trim().isEmpty()) {
                name = printService.getString(R.string.printer_unknown_name);
            }

            PrinterId printerId = printService.generatePrinterId(MaxThermalPrintService.PRINTER_LOCAL_ID);
            int status = resolvePrinterStatus();

            PrinterInfo printerInfo = new PrinterInfo.Builder(printerId, name, status)
                    .setDescription(printService.getString(R.string.print_service_printer_desc))
                    .build();
            printers.add(printerInfo);
        }

        addPrinters(printers);
    }

    /**
     * Menentukan status printer untuk dialog cetak sistem.
     *
     * @return STATUS_IDLE jika siap, STATUS_UNAVAILABLE jika belum dikonfigurasi
     */
    private int resolvePrinterStatus() {
        if (printerManager.isConnected()) {
            return PrinterInfo.STATUS_IDLE;
        }
        if (printerManager.getPreferenceStore().hasLastPrinter()) {
            return PrinterInfo.STATUS_IDLE;
        }
        return PrinterInfo.STATUS_UNAVAILABLE;
    }
}
