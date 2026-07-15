package com.maxpoin.maxthermal.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.maxpoin.maxthermal.R;
import com.maxpoin.maxthermal.bluetooth.BluetoothPrinterManager;
import com.maxpoin.maxthermal.bluetooth.PrinterConnectionState;
import com.maxpoin.maxthermal.bluetooth.PrinterPreferenceStore;

import java.util.List;

/**
 * Fragment pengaturan koneksi printer thermal Bluetooth.
 * Mendukung listing, koneksi, scan perangkat baru, pairing, dan unpair.
 */
public class PengaturanKoneksiFragment extends Fragment
        implements BluetoothPrinterManager.ConnectionListener {

    private static final int REQUEST_ENABLE_BT = 1001;

    private BluetoothPrinterManager printerManager;
    private PrinterPreferenceStore preferenceStore;
    private PairedDeviceAdapter pairedAdapter;
    private DiscoveryDeviceAdapter discoveryAdapter;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean receiverRegistered = false;

    // Views
    private TextView textConnectionStatus;
    private TextView textLastPrinter;
    private TextView textEmptyDevices;
    private TextView textEmptyScan;
    private ProgressBar progressConnecting;
    private ProgressBar progressRefresh;
    private ProgressBar progressScan;
    private Button buttonReconnect;
    private Button buttonDisconnect;
    private Button buttonRefresh;
    private Button buttonScan;
    private Button buttonStopScan;
    private RecyclerView recyclerPaired;
    private RecyclerView recyclerDiscovered;

    /**
     * BroadcastReceiver untuk event discovery dan perubahan status pairing.
     */
    private final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!isAdded()) return;
            String action = intent.getAction();
            if (action == null) return;

            switch (action) {
                case BluetoothDevice.ACTION_FOUND: {
                    BluetoothDevice device = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                            ? intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class)
                            : intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (device != null && device.getBondState() == BluetoothDevice.BOND_NONE) {
                        discoveryAdapter.addDevice(device);
                        recyclerDiscovered.setVisibility(View.VISIBLE);
                        textEmptyScan.setVisibility(View.GONE);
                    }
                    break;
                }
                case BluetoothAdapter.ACTION_DISCOVERY_FINISHED:
                    onDiscoveryFinished();
                    break;

                case BluetoothDevice.ACTION_BOND_STATE_CHANGED: {
                    BluetoothDevice device = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                            ? intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class)
                            : intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE,
                            BluetoothDevice.ERROR);
                    if (device != null) {
                        onBondStateChanged(device, bondState);
                    }
                    break;
                }
            }
        }
    };

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(),
                    result -> {
                        boolean allGranted = true;
                        for (Boolean granted : result.values()) {
                            if (!Boolean.TRUE.equals(granted)) {
                                allGranted = false;
                                break;
                            }
                        }
                        if (allGranted) {
                            onPermissionsReady();
                        } else {
                            Toast.makeText(requireContext(),
                                    R.string.permission_bluetooth_denied, Toast.LENGTH_LONG).show();
                        }
                    });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_pengaturan_koneksi, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        printerManager = BluetoothPrinterManager.getInstance();
        preferenceStore = printerManager.getPreferenceStore();

        bindViews(view);
        setupPairedRecycler();
        setupDiscoveryRecycler();
        setupButtons();
        updateLastPrinterInfo();
        updateUiForState(printerManager.getConnectionState());
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) getActivity().setTitle(R.string.pengaturan_koneksi_title);
        printerManager.addConnectionListener(this);
        registerBluetoothReceiver();
        ensureBluetoothReady();
    }

    @Override
    public void onPause() {
        printerManager.removeConnectionListener(this);
        unregisterBluetoothReceiver();
        printerManager.stopDiscovery();
        setScanUiState(false);
        super.onPause();
    }

    /**
     * Mengikat semua view dari layout.
     */
    private void bindViews(View view) {
        textConnectionStatus = view.findViewById(R.id.textConnectionStatus);
        textLastPrinter = view.findViewById(R.id.textLastPrinter);
        textEmptyDevices = view.findViewById(R.id.textEmptyDevices);
        textEmptyScan = view.findViewById(R.id.textEmptyScan);
        progressConnecting = view.findViewById(R.id.progressConnecting);
        progressRefresh = view.findViewById(R.id.progressRefresh);
        progressScan = view.findViewById(R.id.progressScan);
        buttonReconnect = view.findViewById(R.id.buttonReconnect);
        buttonDisconnect = view.findViewById(R.id.buttonDisconnect);
        buttonRefresh = view.findViewById(R.id.buttonRefresh);
        buttonScan = view.findViewById(R.id.buttonScan);
        buttonStopScan = view.findViewById(R.id.buttonStopScan);
        recyclerPaired = view.findViewById(R.id.recyclerPaired);
        recyclerDiscovered = view.findViewById(R.id.recyclerDiscovered);
    }

    /**
     * Menyiapkan RecyclerView daftar perangkat paired.
     */
    private void setupPairedRecycler() {
        pairedAdapter = new PairedDeviceAdapter(
                this::onPairedDeviceClicked,
                this::confirmUnpair
        );
        recyclerPaired.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerPaired.setAdapter(pairedAdapter);
    }

    /**
     * Menyiapkan RecyclerView hasil scan.
     */
    private void setupDiscoveryRecycler() {
        discoveryAdapter = new DiscoveryDeviceAdapter(this::onDiscoveredDevicePair);
        recyclerDiscovered.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerDiscovered.setAdapter(discoveryAdapter);
    }

    /**
     * Menyiapkan listener tombol.
     */
    private void setupButtons() {
        buttonReconnect.setOnClickListener(v -> printerManager.reconnectLastPrinter());
        buttonDisconnect.setOnClickListener(v -> printerManager.disconnect());
        buttonRefresh.setOnClickListener(v -> refreshPairedWithLoading());
        buttonScan.setOnClickListener(v -> startScan());
        buttonStopScan.setOnClickListener(v -> stopScan());
    }

    // ────────────────────────────── Scan ──────────────────────────────

    /**
     * Memulai scan perangkat Bluetooth baru.
     */
    private void startScan() {
        if (!hasRequiredPermissions()) {
            requestRequiredPermissions();
            return;
        }
        if (!printerManager.isBluetoothEnabled()) {
            ensureBluetoothReady();
            return;
        }
        discoveryAdapter.clearDevices();
        recyclerDiscovered.setVisibility(View.GONE);
        textEmptyScan.setVisibility(View.GONE);
        setScanUiState(true);

        boolean started = printerManager.startDiscovery();
        if (!started) {
            setScanUiState(false);
            Toast.makeText(requireContext(), R.string.scan_failed, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Menghentikan scan yang sedang berjalan.
     */
    private void stopScan() {
        printerManager.stopDiscovery();
        onDiscoveryFinished();
    }

    /**
     * Dipanggil saat discovery selesai (timeout atau dihentikan).
     */
    private void onDiscoveryFinished() {
        setScanUiState(false);
        if (discoveryAdapter.getItemCount() == 0) {
            textEmptyScan.setVisibility(View.VISIBLE);
            recyclerDiscovered.setVisibility(View.GONE);
        }
    }

    /**
     * Mengubah tampilan UI saat scanning aktif/selesai.
     *
     * @param scanning true jika sedang scanning
     */
    private void setScanUiState(boolean scanning) {
        if (!isAdded()) return;
        progressScan.setVisibility(scanning ? View.VISIBLE : View.GONE);
        buttonScan.setVisibility(scanning ? View.GONE : View.VISIBLE);
        buttonStopScan.setVisibility(scanning ? View.VISIBLE : View.GONE);
    }

    // ──────────────────────────── Pairing ────────────────────────────

    /**
     * Memulai proses pairing ke perangkat hasil scan.
     *
     * @param device perangkat yang ingin di-pair
     */
    private void onDiscoveredDevicePair(BluetoothDevice device) {
        if (!hasRequiredPermissions()) {
            requestRequiredPermissions();
            return;
        }
        boolean started = printerManager.bondDevice(device);
        if (!started) {
            Toast.makeText(requireContext(), R.string.pair_failed, Toast.LENGTH_SHORT).show();
            discoveryAdapter.updateDeviceState(device.getAddress(), null);
        }
    }

    /**
     * Dipanggil saat status bond perangkat berubah (ACTION_BOND_STATE_CHANGED).
     *
     * @param device    perangkat Bluetooth
     * @param bondState status bond baru
     */
    @SuppressLint("MissingPermission")
    private void onBondStateChanged(BluetoothDevice device, int bondState) {
        if (!isAdded()) return;
        switch (bondState) {
            case BluetoothDevice.BOND_BONDING:
                discoveryAdapter.updateDeviceState(device.getAddress(),
                        getString(R.string.pairing_in_progress));
                break;

            case BluetoothDevice.BOND_BONDED:
                // Berhasil pair → hapus dari scan, refresh paired, tawarkan koneksi
                discoveryAdapter.updateDeviceState(device.getAddress(), null);
                loadPairedDevices();
                String name = device.getName();
                if (name == null || name.isEmpty()) name = device.getAddress();
                String finalName = name;
                new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.pair_success_title)
                        .setMessage(getString(R.string.pair_success_connect_ask, finalName))
                        .setPositiveButton(R.string.action_connect, (d, w) ->
                                printerManager.connect(device.getAddress()))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
                break;

            case BluetoothDevice.BOND_NONE:
                // Gagal pair atau berhasil unpair
                discoveryAdapter.updateDeviceState(device.getAddress(), null);
                loadPairedDevices();
                break;
        }
    }

    // ──────────────────────────── Unpair ────────────────────────────

    /**
     * Menampilkan dialog konfirmasi sebelum menghapus pairing.
     *
     * @param device perangkat yang ingin di-unpair
     */
    @SuppressLint("MissingPermission")
    private void confirmUnpair(BluetoothDevice device) {
        String name = device.getName();
        if (name == null || name.isEmpty()) name = device.getAddress();
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.unpair_confirm_title)
                .setMessage(getString(R.string.unpair_confirm_message, name))
                .setPositiveButton(R.string.action_unpair, (d, w) -> doUnpair(device))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * Melakukan proses unpair perangkat.
     *
     * @param device perangkat yang akan di-unpair
     */
    private void doUnpair(BluetoothDevice device) {
        if (device.getAddress().equals(printerManager.getConnectedAddress())) {
            printerManager.disconnect();
        }
        if (device.getAddress().equals(preferenceStore.getLastPrinterAddress())) {
            preferenceStore.clearLastPrinter();
        }
        boolean ok = printerManager.unbondDevice(device);
        if (ok) {
            Toast.makeText(requireContext(), R.string.unpair_success, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(requireContext(), R.string.unpair_failed, Toast.LENGTH_SHORT).show();
        }
        loadPairedDevices();
        updateLastPrinterInfo();
    }

    // ────────────────────────── Paired list ──────────────────────────

    /**
     * Memuat ulang daftar perangkat paired dengan loading indicator.
     */
    private void refreshPairedWithLoading() {
        if (!hasRequiredPermissions()) {
            requestRequiredPermissions();
            return;
        }
        if (!printerManager.isBluetoothEnabled()) {
            ensureBluetoothReady();
            return;
        }
        progressRefresh.setVisibility(View.VISIBLE);
        buttonRefresh.setEnabled(false);
        handler.postDelayed(() -> {
            if (!isAdded()) return;
            loadPairedDevices();
            progressRefresh.setVisibility(View.GONE);
            buttonRefresh.setEnabled(true);
        }, 700);
    }

    /**
     * Memuat daftar perangkat yang sudah dipasangkan.
     */
    @SuppressLint("MissingPermission")
    private void loadPairedDevices() {
        if (!hasRequiredPermissions() || !printerManager.isBluetoothEnabled()) return;
        List<BluetoothDevice> paired = printerManager.getPairedDevices();
        pairedAdapter.setDevices(paired);
        pairedAdapter.setConnectedAddress(printerManager.getConnectedAddress());
        boolean empty = paired.isEmpty();
        textEmptyDevices.setVisibility(empty ? View.VISIBLE : View.GONE);
        recyclerPaired.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    /**
     * Menangani ketukan pada perangkat paired untuk dihubungkan.
     */
    @SuppressLint("MissingPermission")
    private void onPairedDeviceClicked(BluetoothDevice device) {
        if (!hasRequiredPermissions()) {
            requestRequiredPermissions();
            return;
        }
        printerManager.connect(device.getAddress());
    }

    // ─────────────────────── Bluetooth helpers ───────────────────────

    /**
     * Mendaftarkan BroadcastReceiver untuk event Bluetooth.
     */
    private void registerBluetoothReceiver() {
        if (receiverRegistered || getContext() == null) return;
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        requireContext().registerReceiver(bluetoothReceiver, filter);
        receiverRegistered = true;
    }

    /**
     * Membatalkan registrasi BroadcastReceiver.
     */
    private void unregisterBluetoothReceiver() {
        if (!receiverRegistered || getContext() == null) return;
        try {
            requireContext().unregisterReceiver(bluetoothReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        receiverRegistered = false;
    }

    /**
     * Memastikan Bluetooth tersedia, izin diberikan, dan Bluetooth aktif.
     */
    private void ensureBluetoothReady() {
        if (!printerManager.isBluetoothAvailable()) {
            Toast.makeText(requireContext(), R.string.bluetooth_not_supported, Toast.LENGTH_LONG).show();
            return;
        }
        if (!hasRequiredPermissions()) {
            requestRequiredPermissions();
            return;
        }
        onPermissionsReady();
    }

    /**
     * Dipanggil setelah semua izin Bluetooth siap.
     */
    @SuppressLint("MissingPermission")
    private void onPermissionsReady() {
        if (!printerManager.isBluetoothEnabled()) {
            Intent enableBt = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBt, REQUEST_ENABLE_BT);
            return;
        }
        loadPairedDevices();
        tryAutoReconnect();
    }

    /**
     * Mengecek apakah semua izin Bluetooth sudah diberikan.
     */
    private boolean hasRequiredPermissions() {
        for (String perm : getRequiredPermissions()) {
            if (ContextCompat.checkSelfPermission(requireContext(), perm)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * Mengambil daftar izin sesuai versi Android.
     */
    private String[] getRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return new String[]{
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
            };
        }
        return new String[]{
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
        };
    }

    /**
     * Meminta izin Bluetooth ke pengguna.
     */
    private void requestRequiredPermissions() {
        permissionLauncher.launch(getRequiredPermissions());
    }

    /**
     * Mencoba otomatis menghubungkan ke printer terakhir jika belum terhubung.
     */
    private void tryAutoReconnect() {
        if (!preferenceStore.hasLastPrinter() || printerManager.isConnected()) return;
        if (printerManager.getConnectionState() == PrinterConnectionState.CONNECTING) return;
        printerManager.reconnectLastPrinter();
    }

    /**
     * Memperbarui teks informasi printer terakhir.
     */
    private void updateLastPrinterInfo() {
        if (preferenceStore.hasLastPrinter()) {
            textLastPrinter.setText(getString(R.string.last_printer_info,
                    preferenceStore.getLastPrinterName(), preferenceStore.getLastPrinterAddress()));
            buttonReconnect.setEnabled(true);
        } else {
            textLastPrinter.setText(R.string.last_printer_none);
            buttonReconnect.setEnabled(false);
        }
    }

    @Override
    public void onConnectionStateChanged(PrinterConnectionState state) {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(() -> {
            updateUiForState(state);
            pairedAdapter.setConnectedAddress(printerManager.getConnectedAddress());
            if (state != PrinterConnectionState.CONNECTING) updateLastPrinterInfo();
            if (state == PrinterConnectionState.FAILED) {
                String err = printerManager.getLastErrorMessage();
                if (err != null) Toast.makeText(requireContext(), err, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Memperbarui tampilan UI sesuai status koneksi.
     */
    private void updateUiForState(PrinterConnectionState state) {
        switch (state) {
            case CONNECTED:
                progressConnecting.setVisibility(View.GONE);
                textConnectionStatus.setText(getString(R.string.status_connected,
                        printerManager.getConnectedName() != null
                                ? printerManager.getConnectedName()
                                : getString(R.string.printer_unknown_name)));
                textConnectionStatus.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.status_connected));
                buttonDisconnect.setEnabled(true);
                buttonReconnect.setEnabled(false);
                break;
            case CONNECTING:
                progressConnecting.setVisibility(View.VISIBLE);
                textConnectionStatus.setText(R.string.status_connecting);
                textConnectionStatus.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.status_connecting));
                buttonDisconnect.setEnabled(false);
                buttonReconnect.setEnabled(false);
                break;
            case FAILED:
                progressConnecting.setVisibility(View.GONE);
                textConnectionStatus.setText(R.string.status_failed);
                textConnectionStatus.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.status_failed));
                buttonDisconnect.setEnabled(false);
                buttonReconnect.setEnabled(preferenceStore.hasLastPrinter());
                break;
            default:
                progressConnecting.setVisibility(View.GONE);
                textConnectionStatus.setText(R.string.status_disconnected);
                textConnectionStatus.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.status_disconnected));
                buttonDisconnect.setEnabled(false);
                buttonReconnect.setEnabled(preferenceStore.hasLastPrinter());
                break;
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT && resultCode == -1) {
            loadPairedDevices();
            tryAutoReconnect();
        }
    }
}
