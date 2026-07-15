package com.maxpoin.maxthermal.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.maxpoin.maxthermal.R;
import com.maxpoin.maxthermal.bluetooth.BluetoothPrinterManager;
import com.maxpoin.maxthermal.bridge.PrintLogStore;

import java.util.Calendar;
import java.util.Locale;

public class HomeFragment extends Fragment {

    private TextView textConnectionStatus;
    private TextView textTotalToday;
    private LinearLayout layoutChartContainer;
    private MaterialButton buttonConnectPrinter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        textConnectionStatus = view.findViewById(R.id.textConnectionStatus);
        textTotalToday = view.findViewById(R.id.textTotalToday);
        layoutChartContainer = view.findViewById(R.id.layoutChartContainer);
        buttonConnectPrinter = view.findViewById(R.id.buttonConnectPrinter);

        buttonConnectPrinter.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).onNavigationItemSelected(
                        ((com.google.android.material.navigation.NavigationView) 
                        getActivity().findViewById(R.id.navigationView)).getMenu().findItem(R.id.nav_pengaturan_koneksi)
                );
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        updateDashboard();
    }

    private void updateDashboard() {
        // 1. Status Koneksi
        BluetoothPrinterManager manager = BluetoothPrinterManager.getInstance();
        if (manager.isConnected()) {
            String name = manager.getConnectedName();
            textConnectionStatus.setText(getString(R.string.home_printer_connected, name != null ? name : "Printer"));
            textConnectionStatus.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.log_success));
            buttonConnectPrinter.setVisibility(View.GONE);
        } else {
            textConnectionStatus.setText(R.string.home_printer_disconnected);
            textConnectionStatus.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.log_failed));
            buttonConnectPrinter.setVisibility(View.VISIBLE);
        }

        // 2. Statistik Hari Ini
        int todayCount = PrintLogStore.getTodayPrintCount();
        textTotalToday.setText(String.valueOf(todayCount));

        // 3. Grafik 7 Hari Terakhir
        renderSimpleBarChart();
    }

    private void renderSimpleBarChart() {
        layoutChartContainer.removeAllViews();
        int[] stats = PrintLogStore.getWeeklyStats();
        
        // Cari nilai maksimum untuk skala
        int maxVal = 0;
        for (int val : stats) {
            if (val > maxVal) maxVal = val;
        }
        if (maxVal == 0) maxVal = 1; // hindari pembagian nol

        Calendar cal = Calendar.getInstance();
        String[] dayNames = new String[]{"Min", "Sen", "Sel", "Rab", "Kam", "Jum", "Sab"};
        int currentDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1; // 0-based

        // render dari 6 hari yang lalu hingga hari ini (index 6 down to 0)
        for (int i = 6; i >= 0; i--) {
            int val = stats[i];
            
            // Hitung nama hari
            int dayIndex = (currentDayOfWeek - i) % 7;
            if (dayIndex < 0) dayIndex += 7;
            String dayName = dayNames[dayIndex];
            if (i == 0) dayName = "Hari\nIni";

            // Buat kontainer untuk satu bar
            LinearLayout barContainer = new LinearLayout(requireContext());
            barContainer.setOrientation(LinearLayout.VERTICAL);
            barContainer.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));
            barContainer.setGravity(android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL);

            // Label angka
            TextView textValue = new TextView(requireContext());
            textValue.setText(String.valueOf(val));
            textValue.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            textValue.setTextSize(12f);
            textValue.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), android.R.color.darker_gray));

            // Bar visual (View)
            View bar = new View(requireContext());
            // Tinggi diatur menggunakan weight terbalik
            float weight = (float) val / maxVal;
            // jika nol, beri minimal sedikit tinggi agar garis terlihat
            if (val == 0) weight = 0.02f;
            
            LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                    (int) (getResources().getDisplayMetrics().density * 24), 0, weight);
            barParams.setMargins(0, 4, 0, 4);
            bar.setLayoutParams(barParams);
            
            bar.setBackgroundColor(androidx.core.content.ContextCompat.getColor(requireContext(), 
                    i == 0 ? R.color.orange_500 : R.color.deep_orange_200));
            
            // Label nama hari
            TextView textDay = new TextView(requireContext());
            textDay.setText(dayName);
            textDay.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            textDay.setTextSize(10f);
            textDay.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), android.R.color.tab_indicator_text));
            if (i == 0) {
                textDay.setTypeface(null, android.graphics.Typeface.BOLD);
                textDay.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.orange_500));
            }

            // Dummy view di atas untuk mendorong bar ke bawah (reverse weight)
            View spacer = new View(requireContext());
            spacer.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f - weight));

            barContainer.addView(spacer);
            barContainer.addView(textValue);
            barContainer.addView(bar);
            barContainer.addView(textDay);

            layoutChartContainer.addView(barContainer);
        }
    }
}
