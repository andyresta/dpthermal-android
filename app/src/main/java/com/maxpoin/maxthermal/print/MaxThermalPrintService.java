package com.maxpoin.maxthermal.print;

import android.printservice.PrintJob;
import android.printservice.PrintService;
import android.printservice.PrinterDiscoverySession;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Print Service Android agar MaxThermal muncul di dialog cetak (Chrome, browser, dll).
 */
public class MaxThermalPrintService extends PrintService {

    /** ID lokal printer thermal Bluetooth di dalam service ini. */
    public static final String PRINTER_LOCAL_ID = "maxthermal_bluetooth_printer";

    private final ExecutorService printJobExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected PrinterDiscoverySession onCreatePrinterDiscoverySession() {
        return new MaxThermalPrinterDiscoverySession(this);
    }

    @Override
    protected void onPrintJobQueued(PrintJob printJob) {
        printJobExecutor.execute(() -> ThermalPrintExecutor.execute(this, printJob));
    }

    @Override
    protected void onRequestCancelPrintJob(PrintJob printJob) {
        if (printJob != null) {
            printJob.cancel();
        }
    }

    @Override
    public void onDestroy() {
        printJobExecutor.shutdown();
        super.onDestroy();
    }
}
