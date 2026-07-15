package com.maxpoin.maxthermal.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.maxpoin.maxthermal.R;
import com.maxpoin.maxthermal.bridge.PrintLogEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter RecyclerView untuk daftar log cetak Print Bridge.
 */
public class PrintLogAdapter extends RecyclerView.Adapter<PrintLogAdapter.LogViewHolder> {

    /**
     * Listener aksi dari item log.
     */
    public interface LogItemListener {
        /**
         * Dipanggil saat pengguna menekan tombol cetak ulang.
         *
         * @param entry entri log terkait
         */
        void onReprint(PrintLogEntry entry);

        /**
         * Dipanggil saat pengguna menekan kotak item log.
         *
         * @param entry entri log terkait
         */
        void onItemClick(PrintLogEntry entry);
    }

    private final LogItemListener listener;
    private final List<PrintLogEntry> entries = new ArrayList<>();

    /**
     * Membuat adapter log cetak.
     *
     * @param listener callback aksi item
     */
    public PrintLogAdapter(LogItemListener listener) {
        this.listener = listener;
    }

    /**
     * Mengganti isi daftar log.
     *
     * @param newEntries entri terbaru
     */
    void setEntries(List<PrintLogEntry> newEntries) {
        entries.clear();
        if (newEntries != null) {
            entries.addAll(newEntries);
        }
        notifyDataSetChanged();
    }

    /**
     * Menambahkan data log baru (pagination).
     *
     * @param newEntries entri tambahan
     */
    void addEntries(List<PrintLogEntry> newEntries) {
        if (newEntries != null && !newEntries.isEmpty()) {
            int startPos = entries.size();
            entries.addAll(newEntries);
            notifyItemRangeInserted(startPos, newEntries.size());
        }
    }

    @NonNull
    @Override
    public LogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_print_log, parent, false);
        return new LogViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
        holder.bind(entries.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    static class LogViewHolder extends RecyclerView.ViewHolder {

        private final TextView textTime;
        private final TextView textEndpoint;
        private final TextView textSummary;
        private final TextView textStatus;
        private final MaterialButton buttonReprint;

        LogViewHolder(@NonNull View itemView) {
            super(itemView);
            textTime = itemView.findViewById(R.id.textLogTime);
            textEndpoint = itemView.findViewById(R.id.textLogEndpoint);
            textSummary = itemView.findViewById(R.id.textLogSummary);
            textStatus = itemView.findViewById(R.id.textLogStatus);
            buttonReprint = itemView.findViewById(R.id.buttonReprint);
        }

        /**
         * Mengisi tampilan item log cetak.
         *
         * @param entry    entri log
         * @param listener callback cetak ulang
         */
        void bind(PrintLogEntry entry, LogItemListener listener) {
            textTime.setText(entry.getFormattedTime());
            textEndpoint.setText(entry.getEndpoint());
            textSummary.setText(entry.getSummary());

            int color = ContextCompat.getColor(itemView.getContext(),
                    entry.isSuccess() ? R.color.log_success : R.color.log_failed);
            textStatus.setText(entry.isSuccess()
                    ? itemView.getContext().getString(R.string.log_cetak_status_ok)
                    : itemView.getContext().getString(R.string.log_cetak_status_failed));
            textStatus.setTextColor(color);

            buttonReprint.setEnabled(entry.isSuccess());
            buttonReprint.setOnClickListener(v -> listener.onReprint(entry));
            itemView.setOnClickListener(v -> listener.onItemClick(entry));
        }
    }
}
