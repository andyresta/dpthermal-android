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
 * Adapter RecyclerView untuk menampilkan daftar printer Bluetooth yang sudah dipasangkan.
 * Setiap item bisa diklik untuk konek, dan ada tombol Hapus Pair.
 */
public class PairedDeviceAdapter extends RecyclerView.Adapter<PairedDeviceAdapter.DeviceViewHolder> {

    /** Callback ketika item perangkat diklik untuk dihubungkan. */
    public interface OnDeviceClickListener {
        /**
         * Dipanggil saat pengguna memilih perangkat.
         *
         * @param device perangkat yang dipilih
         */
        void onDeviceClick(BluetoothDevice device);
    }

    /** Callback ketika tombol Hapus Pair diklik. */
    public interface OnUnpairClickListener {
        /**
         * Dipanggil saat pengguna menekan tombol hapus pair.
         *
         * @param device perangkat yang ingin di-unpair
         */
        void onUnpairClick(BluetoothDevice device);
    }

    private final List<BluetoothDevice> devices = new ArrayList<>();
    private final OnDeviceClickListener connectListener;
    private final OnUnpairClickListener unpairListener;
    private String connectedAddress;

    /**
     * Membuat adapter daftar perangkat paired.
     *
     * @param connectListener callback klik koneksi
     * @param unpairListener  callback klik hapus pair
     */
    public PairedDeviceAdapter(OnDeviceClickListener connectListener,
                               OnUnpairClickListener unpairListener) {
        this.connectListener = connectListener;
        this.unpairListener = unpairListener;
    }

    /**
     * Mengganti daftar perangkat yang ditampilkan.
     *
     * @param newDevices daftar perangkat paired terbaru
     */
    @SuppressLint("NotifyDataSetChanged")
    public void setDevices(List<BluetoothDevice> newDevices) {
        devices.clear();
        if (newDevices != null) {
            devices.addAll(newDevices);
        }
        notifyDataSetChanged();
    }

    /**
     * Memperbarui alamat perangkat yang sedang terhubung untuk tampilan status.
     *
     * @param address MAC address perangkat yang aktif, atau null
     */
    @SuppressLint("NotifyDataSetChanged")
    public void setConnectedAddress(String address) {
        connectedAddress = address;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public DeviceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_bluetooth_device, parent, false);
        return new DeviceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DeviceViewHolder holder, int position) {
        holder.bind(devices.get(position), connectedAddress, connectListener, unpairListener);
    }

    @Override
    public int getItemCount() {
        return devices.size();
    }

    static class DeviceViewHolder extends RecyclerView.ViewHolder {

        private final TextView textName;
        private final TextView textAddress;
        private final TextView textStatus;
        private final View layoutInfo;
        private final MaterialButton buttonUnpair;

        DeviceViewHolder(@NonNull View itemView) {
            super(itemView);
            textName = itemView.findViewById(R.id.textDeviceName);
            textAddress = itemView.findViewById(R.id.textDeviceAddress);
            textStatus = itemView.findViewById(R.id.textDeviceStatus);
            layoutInfo = itemView.findViewById(R.id.layoutDeviceInfo);
            buttonUnpair = itemView.findViewById(R.id.buttonUnpair);
        }

        @SuppressLint("MissingPermission")
        void bind(BluetoothDevice device, String connectedAddress,
                  OnDeviceClickListener connectListener, OnUnpairClickListener unpairListener) {
            String name = device.getName();
            if (name == null || name.trim().isEmpty()) {
                name = itemView.getContext().getString(R.string.printer_unknown_name);
            }
            textName.setText(name);
            textAddress.setText(device.getAddress());

            boolean isConnected = device.getAddress().equals(connectedAddress);
            if (isConnected) {
                textStatus.setText(itemView.getContext().getString(R.string.status_connected_short));
                textStatus.setVisibility(View.VISIBLE);
            } else {
                textStatus.setVisibility(View.GONE);
            }

            layoutInfo.setOnClickListener(v -> connectListener.onDeviceClick(device));
            buttonUnpair.setOnClickListener(v -> unpairListener.onUnpairClick(device));
        }
    }
}
