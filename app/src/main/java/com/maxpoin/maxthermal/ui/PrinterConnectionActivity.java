package com.maxpoin.maxthermal.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.maxpoin.maxthermal.R;
import com.maxpoin.maxthermal.bluetooth.BluetoothPrinterManager;
import com.maxpoin.maxthermal.bluetooth.PaperWidth;
import com.maxpoin.maxthermal.bluetooth.PrinterConnectionState;
import com.maxpoin.maxthermal.bluetooth.PrinterPreferenceStore;

import java.util.List;

/**
 * Halaman utama untuk menghubungkan dan mengelola koneksi printer thermal Bluetooth.
 */
public class PrinterConnectionActivity extends AppCompatActivity
        implements BluetoothPrinterManager.ConnectionListener {

    private static final int REQUEST_ENABLE_BT = 1001;

    private BluetoothPrinterManager printerManager;
    private PrinterPreferenceStore preferenceStore;
    private PairedDeviceAdapter deviceAdapter;

    private TextView textConnectionStatus;
    private TextView textLastPrinter;
    private TextView textEmptyDevices;
    private ProgressBar progressConnecting;
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
                            Toast.makeText(this, R.string.permission_bluetooth_denied, Toast.LENGTH_LONG).show();
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_printer_connection);

        printerManager = BluetoothPrinterManager.getInstance();
        preferenceStore = printerManager.getPreferenceStore();

        bindViews();
        setupRecyclerView();
        setupButtons();
        setupPaperWidthSelector();
        updateLastPrinterInfo();
        updateUiForState(printerManager.getConnectionState());
    }

    @Override
    protected void onStart() {
        super.onStart();
        printerManager.addConnectionListener(this);
        ensureBluetoothReady();
    }

    @Override
    protected void onStop() {
        printerManager.removeConnectionListener(this);
        super.onStop();
    }

    /**
     * Mengikat view dari layout ke variabel activity.
     */
    private void bindViews() {
        textConnectionStatus = findViewById(R.id.textConnectionStatus);
        textLastPrinter = findViewById(R.id.textLastPrinter);
        textEmptyDevices = findViewById(R.id.textEmptyDevices);
        progressConnecting = findViewById(R.id.progressConnecting);
        buttonReconnect = findViewById(R.id.buttonReconnect);
        buttonDisconnect = findViewById(R.id.buttonDisconnect);
        buttonRefresh = findViewById(R.id.buttonRefresh);
        recyclerDevices = findViewById(R.id.recyclerDevices);
        radioGroupPaperWidth = findViewById(R.id.radioGroupPaperWidth);
        radioPaper58 = findViewById(R.id.radioPaper58);
        radioPaper80 = findViewById(R.id.radioPaper80);
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
                    this,
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
        recyclerDevices.setLayoutManager(new LinearLayoutManager(this));
        recyclerDevices.setAdapter(deviceAdapter);
    }

    /**
     * Menyiapkan listener tombol aksi koneksi printer.
     */
    private void setupButtons() {
        buttonReconnect.setOnClickListener(v -> printerManager.reconnectLastPrinter());
        buttonDisconnect.setOnClickListener(v -> printerManager.disconnect());
        buttonRefresh.setOnClickListener(v -> loadPairedDevices());
    }

    /**
     * Memastikan Bluetooth tersedia, izin diberikan, dan Bluetooth aktif.
     */
    private void ensureBluetoothReady() {
        if (!printerManager.isBluetoothAvailable()) {
            Toast.makeText(this, R.string.bluetooth_not_supported, Toast.LENGTH_LONG).show();
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
    private void onPermissionsReady() {
        if (!printerManager.isBluetoothEnabled()) {
            @SuppressLint("MissingPermission")
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            return;
        }
        loadPairedDevices();
        tryAutoReconnectLastPrinter();
    }

    /**
     * Mengecek apakah semua izin Bluetooth yang diperlukan sudah diberikan.
     *
     * @return true jika izin lengkap
     */
    private boolean hasRequiredPermissions() {
        for (String permission : getRequiredPermissions()) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * Mengambil daftar izin Bluetooth sesuai versi Android.
     *
     * @return array nama permission
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
     *
     * @param device perangkat yang dipilih pengguna
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
        updateUiForState(state);
        if (state == PrinterConnectionState.CONNECTED
                || state == PrinterConnectionState.FAILED
                || state == PrinterConnectionState.DISCONNECTED) {
            updateLastPrinterInfo();
        }
        if (state == PrinterConnectionState.FAILED) {
            String error = printerManager.getLastErrorMessage();
            if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Memperbarui tampilan UI sesuai status koneksi printer.
     *
     * @param state status koneksi saat ini
     */
    private void updateUiForState(PrinterConnectionState state) {
        switch (state) {
            case CONNECTED:
                progressConnecting.setVisibility(View.GONE);
                textConnectionStatus.setText(
                        getString(R.string.status_connected, safeName(printerManager.getConnectedName())));
                textConnectionStatus.setTextColor(ContextCompat.getColor(this, R.color.status_connected));
                buttonDisconnect.setEnabled(true);
                buttonReconnect.setEnabled(false);
                break;
            case CONNECTING:
                progressConnecting.setVisibility(View.VISIBLE);
                textConnectionStatus.setText(R.string.status_connecting);
                textConnectionStatus.setTextColor(ContextCompat.getColor(this, R.color.status_connecting));
                buttonDisconnect.setEnabled(false);
                buttonReconnect.setEnabled(false);
                break;
            case FAILED:
                progressConnecting.setVisibility(View.GONE);
                textConnectionStatus.setText(R.string.status_failed);
                textConnectionStatus.setTextColor(ContextCompat.getColor(this, R.color.status_failed));
                buttonDisconnect.setEnabled(false);
                buttonReconnect.setEnabled(preferenceStore.hasLastPrinter());
                break;
            case DISCONNECTED:
            default:
                progressConnecting.setVisibility(View.GONE);
                textConnectionStatus.setText(R.string.status_disconnected);
                textConnectionStatus.setTextColor(ContextCompat.getColor(this, R.color.status_disconnected));
                buttonDisconnect.setEnabled(false);
                buttonReconnect.setEnabled(preferenceStore.hasLastPrinter());
                break;
        }
    }

    /**
     * Mengembalikan nama printer yang aman untuk ditampilkan.
     *
     * @param name nama printer atau null
     * @return nama yang tidak null
     */
    private String safeName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return getString(R.string.printer_unknown_name);
        }
        return name;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                loadPairedDevices();
                tryAutoReconnectLastPrinter();
            } else {
                Toast.makeText(this, R.string.bluetooth_enable_required, Toast.LENGTH_LONG).show();
            }
        }
    }
}
