package com.maxpoin.maxthermal.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.maxpoin.maxthermal.R;
import com.maxpoin.maxthermal.bluetooth.BluetoothPrinterManager;
import com.maxpoin.maxthermal.bluetooth.PrinterPreferenceStore;
import com.maxpoin.maxthermal.bridge.PrintBridgeService;

/**
 * Fragment halaman Bridge Print.
 * Memungkinkan pengguna mengaktifkan HTTP server lokal untuk menerima
 * perintah cetak dari website atau aplikasi lain melalui localhost.
 */
public class BridgePrintFragment extends Fragment {

    private PrinterPreferenceStore preferenceStore;
    private boolean serverRunning = false;

    private TextView textBridgeStatus;
    private TextView textBridgeUrl;
    private TextView textBridgeExampleUrl;
    private TextView textBridgePort;
    private ProgressBar progressBridgeStart;
    private Button buttonStartBridge;
    private Button buttonStopBridge;
    private Button buttonDecrementPort;
    private Button buttonIncrementPort;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_bridge_print, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        preferenceStore = BluetoothPrinterManager.getInstance().getPreferenceStore();

        textBridgeStatus = view.findViewById(R.id.textBridgeStatus);
        textBridgeUrl = view.findViewById(R.id.textBridgeUrl);
        textBridgeExampleUrl = view.findViewById(R.id.textBridgeExampleUrl);
        textBridgePort = view.findViewById(R.id.textBridgePort);
        progressBridgeStart = view.findViewById(R.id.progressBridgeStart);
        buttonStartBridge = view.findViewById(R.id.buttonStartBridge);
        buttonStopBridge = view.findViewById(R.id.buttonStopBridge);
        buttonDecrementPort = view.findViewById(R.id.buttonDecrementPort);
        buttonIncrementPort = view.findViewById(R.id.buttonIncrementPort);

        int savedPort = preferenceStore.getBridgePort();
        preferenceStore.setBridgePort(savedPort);
        applyPortUi(savedPort);

        buttonStartBridge.setOnClickListener(v -> startServer());
        buttonStopBridge.setOnClickListener(v -> stopServer());
        buttonDecrementPort.setOnClickListener(v -> changePort(-1));
        buttonIncrementPort.setOnClickListener(v -> changePort(1));
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) getActivity().setTitle(R.string.bridge_print_title);
        serverRunning = preferenceStore.isBridgeRunning();
        applyPortUi(preferenceStore.getBridgePort());
        updateUi(serverRunning, preferenceStore.getBridgePort());
    }

    /**
     * Mengubah port dengan increment atau decrement (kelipatan 5).
     *
     * @param direction -1 turun, +1 naik
     */
    private void changePort(int direction) {
        int port = preferenceStore.getBridgePort();
        if (direction > 0) {
            port = preferenceStore.getNextBridgePort();
        } else if (direction < 0) {
            port = preferenceStore.getPrevBridgePort();
        }
        preferenceStore.setBridgePort(port);
        applyPortUi(port);
    }

    /**
     * Memperbarui tampilan port dan tombol +/− sesuai nilai saat ini.
     *
     * @param port nomor port aktif
     */
    private void applyPortUi(int port) {
        if (textBridgePort != null) {
            textBridgePort.setText(String.valueOf(port));
        }
        updateExampleUrl(port);
        updatePortButtons();
    }

    /**
     * Mengaktifkan/menonaktifkan tombol increment dan decrement di ujung rentang.
     */
    private void updatePortButtons() {
        if (buttonDecrementPort == null || buttonIncrementPort == null) return;
        boolean portEditable = !serverRunning;
        buttonDecrementPort.setEnabled(portEditable && preferenceStore.canDecrementBridgePort());
        buttonIncrementPort.setEnabled(portEditable && preferenceStore.canIncrementBridgePort());
    }

    /**
     * Memulai HTTP server bridge dengan port yang tersimpan di preferensi.
     */
    private void startServer() {
        int port = preferenceStore.getBridgePort();
        startServerWithPort(port);
    }

    /**
     * Memulai HTTP server bridge pada port tertentu dan memperbarui tampilan UI.
     * Jika service sudah berjalan di port yang sama, tidak ada aksi duplikat.
     *
     * @param port nomor port server
     */
    private void startServerWithPort(int port) {
        Intent intent = new Intent(requireContext(), PrintBridgeService.class);
        intent.setAction(PrintBridgeService.ACTION_START);
        intent.putExtra(PrintBridgeService.EXTRA_PORT, port);
        ContextCompat.startForegroundService(requireContext(), intent);

        serverRunning = true;
        updateUi(true, port);
    }

    /**
     * Menghentikan HTTP server bridge dan memperbarui tampilan UI.
     */
    private void stopServer() {
        Intent intent = new Intent(requireContext(), PrintBridgeService.class);
        intent.setAction(PrintBridgeService.ACTION_STOP);
        requireContext().startService(intent);

        serverRunning = false;
        updateUi(false, preferenceStore.getBridgePort());
    }

    /**
     * Memperbarui tampilan UI sesuai status server.
     *
     * @param running true jika server sedang berjalan
     * @param port    port yang digunakan
     */
    private void updateUi(boolean running, int port) {
        if (!isAdded()) return;
        progressBridgeStart.setVisibility(View.GONE);

        if (running) {
            textBridgeStatus.setText(getString(R.string.bridge_status_running, port));
            textBridgeStatus.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.status_connected));
            textBridgeUrl.setText(getString(R.string.bridge_url_format, port));
            textBridgeUrl.setVisibility(View.VISIBLE);
            textBridgeUrl.setTextColor(ContextCompat.getColor(requireContext(), R.color.orange_500));
            textBridgeUrl.setPaintFlags(textBridgeUrl.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
            textBridgeUrl.setOnClickListener(v -> {
                String url = "http://127.0.0.1:" + port + "/";
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url));
                startActivity(browserIntent);
            });
        } else {
            textBridgeStatus.setText(R.string.bridge_status_stopped);
            textBridgeStatus.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.status_disconnected));
            textBridgeUrl.setVisibility(View.GONE);
        }

        buttonStartBridge.setEnabled(!running);
        buttonStopBridge.setEnabled(running);
        updatePortButtons();
    }

    /**
     * Memperbarui contoh URL di bagian panduan berdasarkan port yang dipilih.
     *
     * @param port nomor port
     */
    private void updateExampleUrl(int port) {
        if (textBridgeExampleUrl == null) return;
        textBridgeExampleUrl.setText(
                "POST http://localhost:" + port + "/print\n"
                + "Content-Type: application/json\n\n"
                + "{\n"
                + "  \"logo\": \"<base64>\",\n"
                + "  \"text\": \"Teks yang dicetak\"\n"
                + "}"
        );
    }
}
