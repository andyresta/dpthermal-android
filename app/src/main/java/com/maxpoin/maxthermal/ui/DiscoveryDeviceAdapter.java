package com.maxpoin.maxthermal.ui;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.maxpoin.maxthermal.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter RecyclerView untuk menampilkan hasil scan perangkat Bluetooth yang belum dipasangkan.
 */
public class DiscoveryDeviceAdapter
        extends RecyclerView.Adapter<DiscoveryDeviceAdapter.DiscoveredViewHolder> {

    /** Callback ketika tombol Pair diklik pada item hasil scan. */
    public interface OnPairClickListener {
        /**
         * Dipanggil saat pengguna menekan tombol Pair.
         *
         * @param device perangkat yang ingin di-pair
         */
        void onPairClick(BluetoothDevice device);
    }

    private final List<BluetoothDevice> devices = new ArrayList<>();
    private final OnPairClickListener pairListener;

    /**
     * Membuat adapter hasil scan.
     *
     * @param pairListener callback klik pair
     */
    public DiscoveryDeviceAdapter(OnPairClickListener pairListener) {
        this.pairListener = pairListener;
    }

    /**
     * Menambahkan satu perangkat hasil scan jika belum ada di daftar.
     *
     * @param device perangkat yang ditemukan
     */
    public void addDevice(BluetoothDevice device) {
        for (BluetoothDevice existing : devices) {
            if (existing.getAddress().equals(device.getAddress())) {
                return;
            }
        }
        devices.add(device);
        notifyItemInserted(devices.size() - 1);
    }

    /**
     * Memperbarui status pairing sebuah perangkat (menampilkan teks "Sedang pairing…").
     *
     * @param address MAC address perangkat
     * @param state   teks status yang ingin ditampilkan, null untuk sembunyikan
     */
    @SuppressLint("NotifyDataSetChanged")
    public void updateDeviceState(String address, String state) {
        for (int i = 0; i < devices.size(); i++) {
            if (devices.get(i).getAddress().equals(address)) {
                notifyItemChanged(i, state);
                return;
            }
        }
    }

    /**
     * Menghapus semua perangkat dari daftar scan.
     */
    @SuppressLint("NotifyDataSetChanged")
    public void clearDevices() {
        devices.clear();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public DiscoveredViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_discovered_device, parent, false);
        return new DiscoveredViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DiscoveredViewHolder holder, int position) {
        holder.bind(devices.get(position), pairListener);
    }

    @Override
    public void onBindViewHolder(@NonNull DiscoveredViewHolder holder, int position,
                                 @NonNull List<Object> payloads) {
        if (!payloads.isEmpty() && payloads.get(0) instanceof String) {
            holder.setStateText((String) payloads.get(0));
        } else {
            super.onBindViewHolder(holder, position, payloads);
        }
    }

    @Override
    public int getItemCount() {
        return devices.size();
    }

    static class DiscoveredViewHolder extends RecyclerView.ViewHolder {

        private final TextView textName;
        private final TextView textAddress;
        private final TextView textState;
        private final MaterialButton buttonPair;

        DiscoveredViewHolder(@NonNull View itemView) {
            super(itemView);
            textName = itemView.findViewById(R.id.textDiscoveredName);
            textAddress = itemView.findViewById(R.id.textDiscoveredAddress);
            textState = itemView.findViewById(R.id.textDiscoveredState);
            buttonPair = itemView.findViewById(R.id.buttonPair);
        }

        @SuppressLint("MissingPermission")
        void bind(BluetoothDevice device, OnPairClickListener pairListener) {
            String name = device.getName();
            if (name == null || name.trim().isEmpty()) {
                name = itemView.getContext().getString(R.string.printer_unknown_name);
            }
            textName.setText(name);
            textAddress.setText(device.getAddress());
            textState.setVisibility(View.GONE);
            buttonPair.setEnabled(true);
            buttonPair.setOnClickListener(v -> {
                buttonPair.setEnabled(false);
                setStateText(itemView.getContext().getString(R.string.pairing_in_progress));
                pairListener.onPairClick(device);
            });
        }

        /**
         * Menampilkan teks status pairing di bawah alamat.
         *
         * @param state teks status, null atau kosong untuk sembunyikan
         */
        void setStateText(String state) {
            if (state == null || state.isEmpty()) {
                textState.setVisibility(View.GONE);
            } else {
                textState.setText(state);
                textState.setVisibility(View.VISIBLE);
            }
        }
    }
}
