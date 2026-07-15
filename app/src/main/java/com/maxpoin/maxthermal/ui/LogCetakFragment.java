package com.maxpoin.maxthermal.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.maxpoin.maxthermal.R;
import com.maxpoin.maxthermal.bluetooth.BluetoothPrinterManager;
import com.maxpoin.maxthermal.bluetooth.PrinterPreferenceStore;
import com.maxpoin.maxthermal.bridge.BridgePrintEngine;
import com.maxpoin.maxthermal.bridge.PrintBridgeQueue;
import com.maxpoin.maxthermal.bridge.PrintLogEntry;
import com.maxpoin.maxthermal.bridge.PrintLogStore;

import java.util.List;

/**
 * Fragment halaman Log Cetak: menampilkan riwayat job Print Bridge dan cetak ulang.
 */
public class LogCetakFragment extends Fragment implements PrintLogAdapter.LogItemListener {

    private PrintLogAdapter adapter;
    private TextView textEmpty;
    private RecyclerView recyclerLogs;

    private int currentPage = 0;
    private boolean isLoading = false;
    private boolean isLastPage = false;
    private static final int PAGE_SIZE = 20;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_log_cetak, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        textEmpty = view.findViewById(R.id.textLogEmpty);
        recyclerLogs = view.findViewById(R.id.recyclerPrintLogs);
        MaterialButton buttonClear = view.findViewById(R.id.buttonClearLogs);

        adapter = new PrintLogAdapter(this);
        recyclerLogs.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerLogs.setAdapter(adapter);

        recyclerLogs.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (dy > 0 && !isLoading && !isLastPage) {
                    LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                    if (layoutManager != null && layoutManager.findLastVisibleItemPosition() == adapter.getItemCount() - 1) {
                        loadNextPage();
                    }
                }
            }
        });

        buttonClear.setOnClickListener(v -> {
            PrintLogStore.clearAll();
            refreshList();
            Toast.makeText(requireContext(), R.string.log_cetak_cleared, Toast.LENGTH_SHORT).show();
        });

        refreshList();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) {
            getActivity().setTitle(R.string.log_cetak_title);
        }
        refreshList();
    }

    /**
     * Memuat ulang daftar log dari penyimpanan (halaman pertama).
     */
    private void refreshList() {
        currentPage = 0;
        isLastPage = false;
        isLoading = true;
        List<PrintLogEntry> entries = PrintLogStore.getPaged(0, PAGE_SIZE);
        adapter.setEntries(entries);
        
        if (entries.size() < PAGE_SIZE) {
            isLastPage = true;
        }
        isLoading = false;

        boolean empty = entries.isEmpty();
        textEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        recyclerLogs.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    /**
     * Memuat halaman berikutnya saat scroll ke bawah.
     */
    private void loadNextPage() {
        isLoading = true;
        currentPage++;
        int offset = currentPage * PAGE_SIZE;
        List<PrintLogEntry> entries = PrintLogStore.getPaged(offset, PAGE_SIZE);
        
        if (entries.isEmpty() || entries.size() < PAGE_SIZE) {
            isLastPage = true;
        }
        
        if (!entries.isEmpty()) {
            adapter.addEntries(entries);
        }
        isLoading = false;
    }

    /**
     * Menangani permintaan cetak ulang dari adapter.
     *
     * @param entry entri log yang akan dicetak ulang
     */
    @Override
    public void onReprint(PrintLogEntry entry) {
        BluetoothPrinterManager manager = BluetoothPrinterManager.getInstance();
        if (!manager.isConnected()) {
            Toast.makeText(requireContext(), R.string.printer_not_connected_hint, Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(requireContext(), R.string.log_cetak_reprinting, Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            PrinterPreferenceStore prefs = manager.getPreferenceStore();
            PrintBridgeQueue.Result result = PrintBridgeQueue.submitAndWait(() -> {
                BridgePrintEngine.executeReprint(manager, prefs, entry);
                return null;
            });

            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                if (result.success) {
                    PrintLogStore.add(entry.getEndpoint(), entry.getPayload(),
                            "[Cetak Ulang] " + entry.getSummary(), true);
                    Toast.makeText(requireContext(), R.string.log_cetak_reprint_ok, Toast.LENGTH_SHORT).show();
                    refreshList();
                } else {
                    String msg = result.errorMessage != null ? result.errorMessage : "Gagal";
                    Toast.makeText(requireContext(),
                            getString(R.string.log_cetak_reprint_failed, msg), Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    @Override
    public void onItemClick(PrintLogEntry entry) {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_print_log_detail, null);
        
        TextView textTime = dialogView.findViewById(R.id.textDetailTime);
        TextView textEndpoint = dialogView.findViewById(R.id.textDetailEndpoint);
        TextView textStatus = dialogView.findViewById(R.id.textDetailStatus);
        TextView textPayload = dialogView.findViewById(R.id.textDetailPayload);
        
        com.google.android.material.button.MaterialButtonToggleGroup toggleGroupView = dialogView.findViewById(R.id.toggleGroupView);
        View scrollRawJson = dialogView.findViewById(R.id.scrollRawJson);
        View scrollPreviewReceipt = dialogView.findViewById(R.id.scrollPreviewReceipt);
        LinearLayout layoutReceiptPreview = dialogView.findViewById(R.id.layoutReceiptPreview);

        MaterialButton btnClose = dialogView.findViewById(R.id.buttonDetailClose);
        MaterialButton btnReprint = dialogView.findViewById(R.id.buttonDetailReprint);

        textTime.setText(entry.getFormattedTime());
        textEndpoint.setText(entry.getEndpoint());
        textPayload.setText(entry.getPayload());
        
        toggleGroupView.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.btnViewRaw) {
                    scrollRawJson.setVisibility(View.VISIBLE);
                    scrollPreviewReceipt.setVisibility(View.GONE);
                } else if (checkedId == R.id.btnViewPreview) {
                    scrollRawJson.setVisibility(View.GONE);
                    scrollPreviewReceipt.setVisibility(View.VISIBLE);
                }
            }
        });
        
        renderReceiptPreview(entry.getEndpoint(), entry.getPayload(), layoutReceiptPreview);

        int color = androidx.core.content.ContextCompat.getColor(requireContext(),
                entry.isSuccess() ? R.color.log_success : R.color.log_failed);
        textStatus.setText(entry.isSuccess()
                ? getString(R.string.log_cetak_status_ok)
                : getString(R.string.log_cetak_status_failed));
        textStatus.setTextColor(color);

        btnReprint.setEnabled(entry.isSuccess());

        com.google.android.material.dialog.MaterialAlertDialogBuilder builder = new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Detail Log Cetak")
                .setView(dialogView);
        
        androidx.appcompat.app.AlertDialog dialog = builder.create();
        
        btnClose.setOnClickListener(v -> dialog.dismiss());
        btnReprint.setOnClickListener(v -> {
            dialog.dismiss();
            onReprint(entry);
        });

        dialog.show();
    }

    private void renderReceiptPreview(String endpoint, String payload, LinearLayout container) {
        container.removeAllViews();
        
        if (endpoint.startsWith("/spooler/")) {
            TextView tv = new TextView(requireContext());
            tv.setText(R.string.log_cetak_preview_spooler_unsupported);
            tv.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), android.R.color.darker_gray));
            tv.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            container.addView(tv);
            return;
        }

        try {
            org.json.JSONObject json = new org.json.JSONObject(payload);
            
            if ("/print/receipt".equals(endpoint)) {
                org.json.JSONArray items = json.optJSONArray("items");
                if (items != null) {
                    for (int i = 0; i < items.length(); i++) {
                        org.json.JSONObject item = items.getJSONObject(i);
                        String type = item.optString("type", "").toLowerCase();
                        
                        if ("text".equals(type)) {
                            addPreviewText(container, item);
                        } else if ("line".equals(type)) {
                            addPreviewLine(container, item);
                        } else if ("feed".equals(type)) {
                            addPreviewFeed(container, item);
                        } else if ("image".equals(type) || "qr".equals(type)) {
                            addPreviewImage(container, item, type);
                        }
                    }
                }
            } else if ("/print/image".equals(endpoint)) {
                addPreviewImage(container, json, "image");
            } else if ("/print".equals(endpoint)) {
                if (json.has("logo")) {
                    addPreviewImage(container, json, "logo");
                }
                if (json.has("text")) {
                    addPreviewText(container, json);
                }
            } else {
                throw new Exception("Unknown endpoint");
            }
        } catch (Exception e) {
            TextView tv = new TextView(requireContext());
            tv.setText(R.string.log_cetak_preview_invalid);
            tv.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.log_failed));
            container.addView(tv);
        }
    }

    private void addPreviewText(LinearLayout container, org.json.JSONObject item) {
        String data = item.optString("text", item.optString("data", ""));
        if (data.isEmpty()) return;
        
        String align = item.optString("align", "left");
        int size = item.optInt("size", 1);
        String style = item.optString("style", "");

        TextView tv = new TextView(requireContext());
        tv.setText(data);
        tv.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), android.R.color.black));
        tv.setTypeface(android.graphics.Typeface.MONOSPACE, 
                style.contains("bold") ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        
        if (style.contains("underline")) {
            tv.setPaintFlags(tv.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        }
        
        float baseSize = 12f;
        tv.setTextSize(baseSize * Math.min(2.5f, size));
        
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        
        if ("center".equals(align)) {
            tv.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        } else if ("right".equals(align)) {
            tv.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);
        } else {
            tv.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        }
        
        container.addView(tv, params);
    }

    private void addPreviewLine(LinearLayout container, org.json.JSONObject item) {
        String style = item.optString("style", "single");
        View line = new View(requireContext());
        int height = (int) (getResources().getDisplayMetrics().density * 1.5f);
        if ("double".equals(style)) height *= 2;
        
        line.setBackgroundColor(androidx.core.content.ContextCompat.getColor(requireContext(), android.R.color.darker_gray));
        
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, height);
        params.setMargins(0, 8, 0, 8);
        container.addView(line, params);
    }

    private void addPreviewFeed(LinearLayout container, org.json.JSONObject item) {
        int lines = 1;
        try {
            lines = Integer.parseInt(item.optString("data", "1"));
        } catch (Exception ignored) {}
        
        View space = new View(requireContext());
        int height = (int) (getResources().getDisplayMetrics().density * 16f * lines);
        container.addView(space, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));
    }

    private void addPreviewImage(LinearLayout container, org.json.JSONObject item, String fieldName) {
        String base64 = fieldName.equals("logo") ? item.optString("logo", "") : item.optString("data", item.optString("image", ""));
        if (base64.isEmpty()) return;
        
        if (base64.contains(",")) {
            base64 = base64.substring(base64.indexOf(',') + 1);
        }
        
        try {
            byte[] bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
            android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            
            if (bmp != null) {
                android.widget.ImageView iv = new android.widget.ImageView(requireContext());
                iv.setImageBitmap(bmp);
                iv.setAdjustViewBounds(true);
                
                String align = item.optString("align", "center");
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                
                if ("left".equals(align)) params.gravity = android.view.Gravity.START;
                else if ("right".equals(align)) params.gravity = android.view.Gravity.END;
                else params.gravity = android.view.Gravity.CENTER_HORIZONTAL;
                
                params.setMargins(0, 8, 0, 8);
                container.addView(iv, params);
            }
        } catch (Exception ignored) {}
    }
}
