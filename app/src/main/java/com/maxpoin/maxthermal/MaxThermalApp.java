package com.maxpoin.maxthermal;

import android.app.Application;

import com.maxpoin.maxthermal.bluetooth.BluetoothPrinterManager;

/**
 * Kelas Application utama untuk inisialisasi komponen global aplikasi MaxThermal.
 */
public class MaxThermalApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        BluetoothPrinterManager.init(this);
        com.maxpoin.maxthermal.bridge.PrintLogStore.init(this);
    }
}
