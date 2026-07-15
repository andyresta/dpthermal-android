package com.maxpoin.maxthermal.bluetooth;

/**
 * Enum status koneksi Bluetooth ke printer thermal.
 */
public enum PrinterConnectionState {
    /** Belum terhubung ke printer. */
    DISCONNECTED,
    /** Sedang mencoba menghubungkan ke printer. */
    CONNECTING,
    /** Berhasil terhubung ke printer. */
    CONNECTED,
    /** Koneksi gagal atau terputus dengan error. */
    FAILED
}
