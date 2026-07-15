package com.maxpoin.maxthermal.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.chip.Chip;
import com.google.android.material.textfield.TextInputEditText;
import com.maxpoin.maxthermal.R;
import com.maxpoin.maxthermal.bluetooth.BluetoothPrinterManager;
import com.maxpoin.maxthermal.bluetooth.PrinterConnectionState;
import com.maxpoin.maxthermal.bluetooth.PrinterPreferenceStore;
import com.maxpoin.maxthermal.print.EscPosPrinter;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fragment tes cetak: mengirim teks atau gambar langsung ke printer Bluetooth.
 */
public class TesCetakFragment extends Fragment
        implements BluetoothPrinterManager.ConnectionListener {

    private BluetoothPrinterManager printerManager;
    private PrinterPreferenceStore preferenceStore;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private Chip chipStatus;
    private TextInputEditText editTextCetak;
    private Button buttonCetakTeks;
    private ImageView imagePreview;
    private Button buttonPilihGambar;
    private Button buttonCetakGambar;

    private Bitmap selectedBitmap;

    private final ActivityResultLauncher<String> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) loadImageFromUri(uri);
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_tes_cetak, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        printerManager = BluetoothPrinterManager.getInstance();
        preferenceStore = printerManager.getPreferenceStore();

        chipStatus = view.findViewById(R.id.chipPrinterStatus);
        editTextCetak = view.findViewById(R.id.editTextCetak);
        buttonCetakTeks = view.findViewById(R.id.buttonCetakTeks);
        imagePreview = view.findViewById(R.id.imagePreview);
        buttonPilihGambar = view.findViewById(R.id.buttonPilihGambar);
        buttonCetakGambar = view.findViewById(R.id.buttonCetakGambar);

        buttonCetakTeks.setOnClickListener(v -> handleCetakTeks());
        buttonPilihGambar.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));
        buttonCetakGambar.setOnClickListener(v -> handleCetakGambar());

        updateStatusChip(printerManager.getConnectionState());
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) getActivity().setTitle(R.string.tes_cetak_title);
        printerManager.addConnectionListener(this);
        updateStatusChip(printerManager.getConnectionState());
    }

    @Override
    public void onPause() {
        printerManager.removeConnectionListener(this);
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        executor.shutdown();
        if (selectedBitmap != null && !selectedBitmap.isRecycled()) {
            selectedBitmap.recycle();
        }
        super.onDestroyView();
    }

    /**
     * Memuat gambar dari URI yang dipilih pengguna dan menampilkan preview.
     *
     * @param uri URI gambar dari system picker
     */
    private void loadImageFromUri(Uri uri) {
        try {
            InputStream stream = requireContext().getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(stream);
            if (stream != null) stream.close();
            if (bitmap != null) {
                if (selectedBitmap != null && !selectedBitmap.isRecycled()) {
                    selectedBitmap.recycle();
                }
                selectedBitmap = bitmap;
                imagePreview.setImageBitmap(bitmap);
                buttonCetakGambar.setEnabled(true);
            }
        } catch (IOException e) {
            Toast.makeText(requireContext(), "Gagal memuat gambar", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Menangani aksi cetak teks: validasi, kirim ke printer di background thread.
     */
    private void handleCetakTeks() {
        if (!checkPrinterConnected()) return;
        String text = editTextCetak.getText() != null ? editTextCetak.getText().toString().trim() : "";
        if (TextUtils.isEmpty(text)) {
            Toast.makeText(requireContext(), R.string.tes_cetak_text_hint, Toast.LENGTH_SHORT).show();
            return;
        }
        setInputEnabled(false);
        Toast.makeText(requireContext(), R.string.printing_in_progress, Toast.LENGTH_SHORT).show();
        executor.execute(() -> {
            try {
                byte[] data = EscPosPrinter.buildTextData(text);
                printerManager.write(data);
                if (preferenceStore.isAutoCutter()) printerManager.write(EscPosPrinter.partialCut());
                if (preferenceStore.isAutoCashDrawer()) printerManager.write(EscPosPrinter.cashDrawerPulse());
                postToMain(() -> Toast.makeText(requireContext(), R.string.test_print_success, Toast.LENGTH_SHORT).show());
            } catch (IOException e) {
                postToMain(() -> Toast.makeText(requireContext(),
                        getString(R.string.test_print_failed, e.getMessage()), Toast.LENGTH_LONG).show());
            } finally {
                postToMain(() -> setInputEnabled(true));
            }
        });
    }

    /**
     * Menangani aksi cetak gambar: validasi, konversi ke ESC/POS, kirim ke printer.
     */
    private void handleCetakGambar() {
        if (!checkPrinterConnected()) return;
        if (selectedBitmap == null || selectedBitmap.isRecycled()) {
            Toast.makeText(requireContext(), R.string.no_image_selected, Toast.LENGTH_SHORT).show();
            return;
        }
        setInputEnabled(false);
        Toast.makeText(requireContext(), R.string.printing_in_progress, Toast.LENGTH_SHORT).show();
        final Bitmap bitmapToSend = selectedBitmap;
        executor.execute(() -> {
            try {
                EscPosPrinter printer = new EscPosPrinter(preferenceStore.getPrintWidthPx());
                byte[] data = printer.buildPrintData(bitmapToSend);
                printerManager.write(data);
                if (preferenceStore.isAutoCutter()) printerManager.write(EscPosPrinter.partialCut());
                if (preferenceStore.isAutoCashDrawer()) printerManager.write(EscPosPrinter.cashDrawerPulse());
                postToMain(() -> Toast.makeText(requireContext(), R.string.test_print_success, Toast.LENGTH_SHORT).show());
            } catch (IOException e) {
                postToMain(() -> Toast.makeText(requireContext(),
                        getString(R.string.test_print_failed, e.getMessage()), Toast.LENGTH_LONG).show());
            } finally {
                postToMain(() -> setInputEnabled(true));
            }
        });
    }

    /**
     * Mengecek status koneksi printer dan menampilkan pesan jika belum terhubung.
     *
     * @return true jika printer terhubung
     */
    private boolean checkPrinterConnected() {
        if (!printerManager.isConnected()) {
            Toast.makeText(requireContext(), R.string.printer_not_connected_hint, Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }

    /**
     * Mengaktifkan atau menonaktifkan input selama proses cetak berlangsung.
     *
     * @param enabled true untuk mengaktifkan
     */
    private void setInputEnabled(boolean enabled) {
        buttonCetakTeks.setEnabled(enabled);
        buttonPilihGambar.setEnabled(enabled);
        buttonCetakGambar.setEnabled(enabled && selectedBitmap != null);
        editTextCetak.setEnabled(enabled);
    }

    /**
     * Memperbarui chip status printer di UI.
     *
     * @param state status koneksi saat ini
     */
    private void updateStatusChip(PrinterConnectionState state) {
        if (!isAdded() || chipStatus == null) return;
        switch (state) {
            case CONNECTED:
                chipStatus.setText(getString(R.string.status_connected,
                        printerManager.getConnectedName() != null
                                ? printerManager.getConnectedName()
                                : getString(R.string.printer_unknown_name)));
                chipStatus.setChipBackgroundColorResource(R.color.status_connected);
                break;
            case CONNECTING:
                chipStatus.setText(R.string.status_connecting);
                chipStatus.setChipBackgroundColorResource(R.color.status_connecting);
                break;
            default:
                chipStatus.setText(R.string.status_disconnected);
                chipStatus.setChipBackgroundColorResource(R.color.status_disconnected);
                break;
        }
    }

    @Override
    public void onConnectionStateChanged(PrinterConnectionState state) {
        postToMain(() -> updateStatusChip(state));
    }

    /**
     * Menjalankan Runnable di main thread dengan pemeriksaan apakah fragment masih aktif.
     *
     * @param runnable aksi yang dijalankan di main thread
     */
    private void postToMain(Runnable runnable) {
        if (isAdded() && getActivity() != null) {
            requireActivity().runOnUiThread(runnable);
        }
    }
}
