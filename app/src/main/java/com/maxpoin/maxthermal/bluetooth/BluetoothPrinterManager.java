package com.maxpoin.maxthermal.bluetooth;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Mengelola koneksi Bluetooth SPP ke printer thermal dan mempertahankan socket selama aplikasi aktif.
 */
public class BluetoothPrinterManager {

    /** UUID standar Serial Port Profile (SPP) untuk printer Bluetooth thermal. */
    private static final UUID SPP_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private static BluetoothPrinterManager instance;

    private final Context appContext;
    private final PrinterPreferenceStore preferenceStore;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<ConnectionListener> listeners = new CopyOnWriteArrayList<>();

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket activeSocket;
    private PrinterConnectionState state = PrinterConnectionState.DISCONNECTED;
    private String connectedAddress;
    private String connectedName;
    private String lastErrorMessage;
    private final Object connectionLock = new Object();

    /**
     * Listener untuk perubahan status koneksi printer.
     */
    public interface ConnectionListener {
        /**
         * Dipanggil ketika status koneksi berubah.
         *
         * @param state status koneksi terbaru
         */
        void onConnectionStateChanged(PrinterConnectionState state);
    }

    /**
     * Inisialisasi singleton manager dari Application.
     *
     * @param context konteks aplikasi
     */
    public static synchronized void init(Context context) {
        if (instance == null) {
            instance = new BluetoothPrinterManager(context.getApplicationContext());
        }
    }

    /**
     * Mengambil instance singleton manager.
     *
     * @return instance BluetoothPrinterManager
     */
    public static synchronized BluetoothPrinterManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("BluetoothPrinterManager belum diinisialisasi");
        }
        return instance;
    }

    private BluetoothPrinterManager(Context context) {
        appContext = context;
        preferenceStore = new PrinterPreferenceStore(context);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    /**
     * Mendaftarkan listener perubahan status koneksi.
     *
     * @param listener listener yang akan dipanggil
     */
    public void addConnectionListener(ConnectionListener listener) {
        listeners.add(listener);
        listener.onConnectionStateChanged(state);
    }

    /**
     * Menghapus listener perubahan status koneksi.
     *
     * @param listener listener yang akan dihapus
     */
    public void removeConnectionListener(ConnectionListener listener) {
        listeners.remove(listener);
    }

    /**
     * Mengecek apakah perangkat mendukung Bluetooth.
     *
     * @return true jika adapter Bluetooth tersedia
     */
    public boolean isBluetoothAvailable() {
        return bluetoothAdapter != null;
    }

    /**
     * Mengecek apakah Bluetooth sedang aktif.
     *
     * @return true jika Bluetooth menyala
     */
    public boolean isBluetoothEnabled() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }

    /**
     * Mengambil daftar perangkat Bluetooth yang sudah dipasangkan (paired).
     *
     * @return daftar perangkat paired
     */
    @SuppressLint("MissingPermission")
    public List<BluetoothDevice> getPairedDevices() {
        List<BluetoothDevice> devices = new ArrayList<>();
        if (bluetoothAdapter == null) {
            return devices;
        }
        Set<BluetoothDevice> bonded = bluetoothAdapter.getBondedDevices();
        if (bonded != null) {
            devices.addAll(bonded);
        }
        return devices;
    }

    /**
     * Mengambil status koneksi saat ini.
     *
     * @return status koneksi printer
     */
    public PrinterConnectionState getConnectionState() {
        return state;
    }

    /**
     * Mengecek apakah saat ini terhubung ke printer.
     *
     * @return true jika socket aktif dan terhubung
     */
    public boolean isConnected() {
        return state == PrinterConnectionState.CONNECTED
                && activeSocket != null
                && activeSocket.isConnected();
    }

    /**
     * Mengambil alamat MAC printer yang sedang terhubung.
     *
     * @return alamat MAC atau null
     */
    public String getConnectedAddress() {
        return connectedAddress;
    }

    /**
     * Mengambil nama printer yang sedang terhubung.
     *
     * @return nama printer atau null
     */
    public String getConnectedName() {
        return connectedName;
    }

    /**
     * Mengambil pesan error terakhir dari percobaan koneksi.
     *
     * @return pesan error atau null
     */
    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    /**
     * Mengambil penyimpanan preferensi printer.
     *
     * @return instance PrinterPreferenceStore
     */
    public PrinterPreferenceStore getPreferenceStore() {
        return preferenceStore;
    }

    /**
     * Mengambil socket Bluetooth aktif untuk keperluan pengiriman data cetak.
     *
     * @return socket aktif atau null jika belum terhubung
     */
    public BluetoothSocket getActiveSocket() {
        return activeSocket;
    }

    /**
     * Menghubungkan ke printer berdasarkan alamat MAC secara asynchronous.
     *
     * @param address alamat MAC printer
     */
    @SuppressLint("MissingPermission")
    public void connect(String address) {
        if (address == null || address.isEmpty()) {
            return;
        }
        if (state == PrinterConnectionState.CONNECTING) {
            return;
        }
        if (isConnected() && address.equals(connectedAddress)) {
            return;
        }

        executor.execute(() -> {
            disconnectInternal(false);
            updateState(PrinterConnectionState.CONNECTING);

            if (bluetoothAdapter == null) {
                failConnection("Bluetooth tidak tersedia di perangkat ini");
                return;
            }
            if (!bluetoothAdapter.isEnabled()) {
                failConnection("Bluetooth belum diaktifkan");
                return;
            }

            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
            String deviceName = resolveDeviceName(device);

            try {
                BluetoothSocket socket = device.createRfcommSocketToServiceRecord(SPP_UUID);
                bluetoothAdapter.cancelDiscovery();
                socket.connect();

                activeSocket = socket;
                connectedAddress = address;
                connectedName = deviceName;
                preferenceStore.saveLastPrinter(address, deviceName);
                lastErrorMessage = null;
                updateState(PrinterConnectionState.CONNECTED);
            } catch (IOException e) {
                failConnection("Gagal terhubung: " + e.getMessage());
                disconnectInternal(false);
            }
        });
    }

    /**
     * Memastikan printer terhubung secara sinkron (dipakai saat mencetak dari Print Service).
     *
     * @param timeoutMs batas waktu tunggu koneksi dalam milidetik
     * @return true jika berhasil terhubung
     */
    public boolean ensureConnectedForPrint(long timeoutMs) {
        if (isConnected()) {
            return true;
        }
        String address = preferenceStore.getLastPrinterAddress();
        if (address == null || address.isEmpty()) {
            return false;
        }
        long deadline = System.currentTimeMillis() + timeoutMs;
        long remaining = timeoutMs;
        while (remaining > 0 && !isConnected()) {
            if (connectBlocking(address, remaining)) {
                return true;
            }
            remaining = deadline - System.currentTimeMillis();
        }
        return isConnected();
    }

    /**
     * Menulis data biner ke printer thermal melalui socket Bluetooth aktif.
     *
     * @param data byte data ESC/POS atau raw
     * @throws IOException jika printer tidak terhubung atau gagal menulis
     */
    public void write(byte[] data) throws IOException {
        synchronized (connectionLock) {
            if (!isConnected() || activeSocket == null) {
                throw new IOException("Printer tidak terhubung");
            }
            OutputStream outputStream = activeSocket.getOutputStream();
            outputStream.write(data);
            outputStream.flush();
        }
    }

    /**
     * Menghubungkan ke printer secara sinkron di thread pemanggil.
     *
     * @param address   alamat MAC printer
     * @param timeoutMs batas waktu (belum dipakai penuh, satu percobaan langsung)
     * @return true jika koneksi berhasil
     */
    @SuppressLint("MissingPermission")
    public boolean connectBlocking(String address, long timeoutMs) {
        synchronized (connectionLock) {
            if (isConnected() && address.equals(connectedAddress)) {
                return true;
            }
            disconnectInternalSync(false);
            updateState(PrinterConnectionState.CONNECTING);

            if (bluetoothAdapter == null) {
                failConnection("Bluetooth tidak tersedia di perangkat ini");
                return false;
            }
            if (!bluetoothAdapter.isEnabled()) {
                failConnection("Bluetooth belum diaktifkan");
                return false;
            }

            try {
                BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
                String deviceName = resolveDeviceName(device);
                BluetoothSocket socket = device.createRfcommSocketToServiceRecord(SPP_UUID);
                bluetoothAdapter.cancelDiscovery();
                socket.connect();

                activeSocket = socket;
                connectedAddress = address;
                connectedName = deviceName;
                preferenceStore.saveLastPrinter(address, deviceName);
                lastErrorMessage = null;
                updateState(PrinterConnectionState.CONNECTED);
                return true;
            } catch (IOException e) {
                failConnection("Gagal terhubung: " + e.getMessage());
                disconnectInternalSync(false);
                return false;
            }
        }
    }

    /**
     * Mencoba menghubungkan kembali ke printer terakhir yang tersimpan.
     */
    public void reconnectLastPrinter() {
        String address = preferenceStore.getLastPrinterAddress();
        if (address != null && !address.isEmpty()) {
            connect(address);
        }
    }

    /**
     * Memutuskan koneksi ke printer secara asynchronous.
     */
    public void disconnect() {
        executor.execute(() -> disconnectInternal(true));
    }

    /**
     * Memutuskan koneksi internal dan mengupdate status.
     *
     * @param notifyDisconnected apakah perlu mengirim event DISCONNECTED
     */
    private void disconnectInternal(boolean notifyDisconnected) {
        synchronized (connectionLock) {
            disconnectInternalSync(notifyDisconnected);
        }
    }

    /**
     * Memutuskan koneksi secara sinkron (tanpa executor).
     *
     * @param notifyDisconnected apakah perlu mengirim event DISCONNECTED
     */
    private void disconnectInternalSync(boolean notifyDisconnected) {
        if (activeSocket != null) {
            try {
                activeSocket.close();
            } catch (IOException ignored) {
            }
            activeSocket = null;
        }
        connectedAddress = null;
        connectedName = null;
        if (notifyDisconnected) {
            updateState(PrinterConnectionState.DISCONNECTED);
        }
    }

    /**
     * Menandai koneksi gagal dan menyimpan pesan error.
     *
     * @param message pesan error
     */
    private void failConnection(String message) {
        lastErrorMessage = message;
        updateState(PrinterConnectionState.FAILED);
    }

    /**
     * Mengupdate status koneksi dan memberitahu semua listener di main thread.
     *
     * @param newState status koneksi baru
     */
    private void updateState(PrinterConnectionState newState) {
        state = newState;
        mainHandler.post(() -> {
            for (ConnectionListener listener : listeners) {
                listener.onConnectionStateChanged(newState);
            }
        });
    }

    /**
     * Mengambil nama perangkat Bluetooth yang aman untuk ditampilkan.
     *
     * @param device perangkat Bluetooth
     * @return nama perangkat atau alamat MAC jika nama kosong
     */
    @SuppressLint("MissingPermission")
    private String resolveDeviceName(BluetoothDevice device) {
        String name = device.getName();
        if (name == null || name.trim().isEmpty()) {
            return device.getAddress();
        }
        return name;
    }

    // ───────────────────────── Discovery & Bonding ─────────────────────────

    /**
     * Memulai pemindaian perangkat Bluetooth di sekitar.
     * BroadcastReceiver {@code BluetoothDevice.ACTION_FOUND} harus didaftarkan di Fragment/Activity.
     *
     * @return true jika discovery berhasil dimulai
     */
    @SuppressLint("MissingPermission")
    public boolean startDiscovery() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            return false;
        }
        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }
        return bluetoothAdapter.startDiscovery();
    }

    /**
     * Menghentikan pemindaian perangkat Bluetooth.
     */
    @SuppressLint("MissingPermission")
    public void stopDiscovery() {
        if (bluetoothAdapter != null && bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }
    }

    /**
     * Memulai proses pairing ke perangkat Bluetooth.
     * Hasil pairing dikembalikan via {@code BluetoothDevice.ACTION_BOND_STATE_CHANGED}.
     *
     * @param device perangkat yang ingin di-pair
     * @return true jika proses pairing berhasil dimulai
     */
    @SuppressLint("MissingPermission")
    public boolean bondDevice(BluetoothDevice device) {
        stopDiscovery();
        return device.createBond();
    }

    /**
     * Menghapus pairing (unpair) perangkat Bluetooth via reflection.
     * Metode ini tidak ada di public SDK Android, namun konsisten di semua versi.
     *
     * @param device perangkat yang ingin di-unpair
     * @return true jika proses unpair berhasil dimulai
     */
    public boolean unbondDevice(BluetoothDevice device) {
        try {
            java.lang.reflect.Method removeBond =
                    device.getClass().getMethod("removeBond");
            return Boolean.TRUE.equals(removeBond.invoke(device));
        } catch (Exception e) {
            return false;
        }
    }
}
