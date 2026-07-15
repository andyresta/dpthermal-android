package com.maxpoin.maxthermal.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
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
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.maxpoin.maxthermal.R;
import com.maxpoin.maxthermal.bluetooth.BluetoothPrinterManager;
import com.maxpoin.maxthermal.bluetooth.PaperWidth;
import com.maxpoin.maxthermal.bluetooth.PrinterConnectionState;
import com.maxpoin.maxthermal.bluetooth.PrinterPreferenceStore;

import java.util.List;

/**
 * Fragment untuk menghubungkan dan mengelola koneksi printer thermal Bluetooth.
 */
public class PrinterConnectionFragment extends Fragment
        implements BluetoothPrinterManager.ConnectionListener {

    private static final int REQUEST_ENABLE_BT = 1001;

    private BluetoothPrinterManager printerManager;
    private PrinterPreferenceStore preferenceStore;
    private PairedDeviceAdapter deviceAdapter;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextView textConnectionStatus;
    private TextView textLastPrinter;
    private TextView textEmptyDevices;
    private ProgressBar progressConnecting;
    private ProgressBar progressRefresh;
    private Button buttonReconnect;
    private Button buttonDisconnect;
    private Button buttonRefresh;
    private RecyclerView recyclerDevices;
    private RadioGroup radioGroupPaperWidth;
    private RadioButton radioPaper58;
    private RadioButton radioPaper80;

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
                            Toast.makeText(requireContext(), R.string.permission_bluetooth_denied, Toast.LENGTH_LONG).show();
                        }
                    });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_printer_connection, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        printerManager = BluetoothPrinterManager.getInstance();
        preferenceStore = printerManager.getPreferenceStore();

        bindViews(view);
        setupRecyclerView();
        setupButtons();
        setupPaperWidthSelector();
        updateLastPrinterInfo();
        updateUiForState(printerManager.getConnectionState());
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) {
            getActivity().setTitle(R.string.pengaturan_koneksi_title);
        }
        printerManager.addConnectionListener(this);
        ensureBluetoothReady();
    }

    @Override
    public void onPause() {
        printerManager.removeConnectionListener(this);
        super.onPause();
    }

    /**
     * Mengikat view dari layout ke variabel fragment.
     */
    private void bindViews(View view) {
        textConnectionStatus = view.findViewById(R.id.textConnectionStatus);
        textLastPrinter = view.findViewById(R.id.textLastPrinter);
        textEmptyDevices = view.findViewById(R.id.textEmptyDevices);
        progressConnecting = view.findViewById(R.id.progressConnecting);
        progressRefresh = view.findViewById(R.id.progressRefresh);
        buttonReconnect = view.findViewById(R.id.buttonReconnect);
        buttonDisconnect = view.findViewById(R.id.buttonDisconnect);
        buttonRefresh = view.findViewById(R.id.buttonRefresh);
        recyclerDevices = view.findViewById(R.id.recyclerDevices);
        radioGroupPaperWidth = view.findViewById(R.id.radioGroupPaperWidth);
        radioPaper58 = view.findViewById(R.id.radioPaper58);
        radioPaper80 = view.findViewById(R.id.radioPaper80);
    }

    /**
     * Menyiapkan pemilih lebar kertas 58 mm / 80 mm dan memuat nilai tersimpan.
     */
    private void setupPaperWidthSelector() {
        int savedWidthMm = preferenceStore.getPaperWidthMm();
        if (savedWidthMm == PaperWidth.MM_80) {
            radioPaper80.setChecked(true);
        } else {
            radioPaper58.setChecked(true);
        }

        radioGroupPaperWidth.setOnCheckedChangeListener((group, checkedId) -> {
            int paperWidthMm = checkedId == R.id.radioPaper80
                    ? PaperWidth.MM_80
                    : PaperWidth.MM_58;
            preferenceStore.setPaperWidthMm(paperWidthMm);
            Toast.makeText(
                    requireContext(),
                    getString(R.string.paper_width_saved, paperWidthMm),
                    Toast.LENGTH_SHORT
            ).show();
        });
    }

    /**
     * Menyiapkan RecyclerView daftar perangkat Bluetooth paired.
     */
    private void setupRecyclerView() {
        deviceAdapter = new PairedDeviceAdapter(this::onDeviceSelected, device -> {});
        recyclerDevices.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerDevices.setAdapter(deviceAdapter);
    }

    /**
     * Menyiapkan listener tombol aksi koneksi printer.
     */
    private void setupButtons() {
        buttonReconnect.setOnClickListener(v -> printerManager.reconnectLastPrinter());
        buttonDisconnect.setOnClickListener(v -> printerManager.disconnect());
        buttonRefresh.setOnClickListener(v -> refreshPairedDevicesWithLoading());
    }

    /**
     * Memuat ulang daftar perangkat Bluetooth dengan indikator loading.
     */
    private void refreshPairedDevicesWithLoading() {
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
        textEmptyDevices.setVisibility(View.GONE);
        recyclerDevices.setVisibility(View.GONE);

        handler.postDelayed(() -> {
            loadPairedDevices();
            progressRefresh.setVisibility(View.GONE);
            buttonRefresh.setEnabled(true);
        }, 800);
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
     * Dipanggil setelah izin Bluetooth siap; mengaktifkan BT dan memuat perangkat.
     */
    @SuppressLint("MissingPermission")
    private void onPermissionsReady() {
        if (!printerManager.isBluetoothEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            return;
        }
        loadPairedDevices();
        tryAutoReconnectLastPrinter();
    }

    /**
     * Mengecek apakah semua izin Bluetooth yang diperlukan sudah diberikan.
     */
    private boolean hasRequiredPermissions() {
        for (String permission : getRequiredPermissions()) {
            if (ContextCompat.checkSelfPermission(requireContext(), permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * Mengambil daftar izin Bluetooth sesuai versi Android.
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
     * Memuat ulang daftar perangkat Bluetooth yang sudah dipasangkan.
     */
    @SuppressLint("MissingPermission")
    private void loadPairedDevices() {
        if (!hasRequiredPermissions() || !printerManager.isBluetoothEnabled()) {
            return;
        }
        List<BluetoothDevice> paired = printerManager.getPairedDevices();
        deviceAdapter.setDevices(paired);

        boolean empty = paired.isEmpty();
        textEmptyDevices.setVisibility(empty ? View.VISIBLE : View.GONE);
        recyclerDevices.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    /**
     * Mencoba otomatis menghubungkan ke printer terakhir jika belum terhubung.
     */
    private void tryAutoReconnectLastPrinter() {
        if (!preferenceStore.hasLastPrinter()) {
            return;
        }
        if (printerManager.isConnected()) {
            return;
        }
        PrinterConnectionState state = printerManager.getConnectionState();
        if (state == PrinterConnectionState.CONNECTING) {
            return;
        }
        printerManager.reconnectLastPrinter();
    }

    /**
     * Menangani pemilihan perangkat dari daftar paired.
     */
    @SuppressLint("MissingPermission")
    private void onDeviceSelected(BluetoothDevice device) {
        if (!hasRequiredPermissions()) {
            requestRequiredPermissions();
            return;
        }
        printerManager.connect(device.getAddress());
    }

    /**
     * Memperbarui teks informasi printer terakhir dari penyimpanan.
     */
    private void updateLastPrinterInfo() {
        if (preferenceStore.hasLastPrinter()) {
            String name = preferenceStore.getLastPrinterName();
            String address = preferenceStore.getLastPrinterAddress();
            textLastPrinter.setText(getString(R.string.last_printer_info, name, address));
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
            if (state == PrinterConnectionState.CONNECTED
                    || state == PrinterConnectionState.FAILED
                    || state == PrinterConnectionState.DISCONNECTED) {
                updateLastPrinterInfo();
            }
            if (state == PrinterConnectionState.FAILED) {
                String error = printerManager.getLastErrorMessage();
                if (error != null) {
                    Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    /**
     * Memperbarui tampilan UI sesuai status koneksi printer.
     */
    private void updateUiForState(PrinterConnectionState state) {
        switch (state) {
            case CONNECTED:
                progressConnecting.setVisibility(View.GONE);
                textConnectionStatus.setText(
                        getString(R.string.status_connected, safeName(printerManager.getConnectedName())));
                textConnectionStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_connected));
                buttonDisconnect.setEnabled(true);
                buttonReconnect.setEnabled(false);
                break;
            case CONNECTING:
                progressConnecting.setVisibility(View.VISIBLE);
                textConnectionStatus.setText(R.string.status_connecting);
                textConnectionStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_connecting));
                buttonDisconnect.setEnabled(false);
                buttonReconnect.setEnabled(false);
                break;
            case FAILED:
                progressConnecting.setVisibility(View.GONE);
                textConnectionStatus.setText(R.string.status_failed);
                textConnectionStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_failed));
                buttonDisconnect.setEnabled(false);
                buttonReconnect.setEnabled(preferenceStore.hasLastPrinter());
                break;
            case DISCONNECTED:
            default:
                progressConnecting.setVisibility(View.GONE);
                textConnectionStatus.setText(R.string.status_disconnected);
                textConnectionStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_disconnected));
                buttonDisconnect.setEnabled(false);
                buttonReconnect.setEnabled(preferenceStore.hasLastPrinter());
                break;
        }
    }

    /**
     * Mengembalikan nama printer yang aman untuk ditampilkan.
     */
    private String safeName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return getString(R.string.printer_unknown_name);
        }
        return name;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == -1) {
                loadPairedDevices();
                tryAutoReconnectLastPrinter();
            } else {
                Toast.makeText(requireContext(), R.string.bluetooth_enable_required, Toast.LENGTH_LONG).show();
            }
        }
    }
}
