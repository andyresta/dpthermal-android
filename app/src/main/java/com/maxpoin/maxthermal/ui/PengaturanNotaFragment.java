package com.maxpoin.maxthermal.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.maxpoin.maxthermal.R;
import com.maxpoin.maxthermal.bluetooth.BluetoothPrinterManager;
import com.maxpoin.maxthermal.bluetooth.CodePage;
import com.maxpoin.maxthermal.bluetooth.PaperWidth;
import com.maxpoin.maxthermal.bluetooth.PrinterPreferenceStore;

/**
 * Fragment pengaturan nota: lebar kertas, auto cutter, auto cash drawer.
 * Semua perubahan langsung disimpan ke SharedPreferences.
 */
public class PengaturanNotaFragment extends Fragment {

    private PrinterPreferenceStore preferenceStore;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_pengaturan_nota, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        preferenceStore = BluetoothPrinterManager.getInstance().getPreferenceStore();

        setupPaperWidth(view);
        setupAutoCutter(view);
        setupAutoCashDrawer(view);
        setupCodePage(view);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) getActivity().setTitle(R.string.pengaturan_nota_title);
    }

    /**
     * Menyiapkan selector lebar kertas 58mm / 80mm dengan nilai tersimpan.
     */
    private void setupPaperWidth(View view) {
        RadioGroup radioGroup = view.findViewById(R.id.radioGroupPaperWidth);
        RadioButton radio58 = view.findViewById(R.id.radioPaper58);
        RadioButton radio80 = view.findViewById(R.id.radioPaper80);

        if (preferenceStore.getPaperWidthMm() == PaperWidth.MM_80) {
            radio80.setChecked(true);
        } else {
            radio58.setChecked(true);
        }

        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            int mm = (checkedId == R.id.radioPaper80) ? PaperWidth.MM_80 : PaperWidth.MM_58;
            preferenceStore.setPaperWidthMm(mm);
            Toast.makeText(requireContext(),
                    getString(R.string.paper_width_saved, mm), Toast.LENGTH_SHORT).show();
        });
    }

    /**
     * Menyiapkan toggle auto cutter dengan nilai tersimpan.
     */
    private void setupAutoCutter(View view) {
        SwitchMaterial switchCutter = view.findViewById(R.id.switchAutoCutter);
        switchCutter.setChecked(preferenceStore.isAutoCutter());
        switchCutter.setOnCheckedChangeListener((btn, checked) -> {
            preferenceStore.setAutoCutter(checked);
            Toast.makeText(requireContext(), R.string.setting_saved, Toast.LENGTH_SHORT).show();
        });
    }

    /**
     * Menyiapkan toggle auto cash drawer dengan nilai tersimpan.
     */
    private void setupAutoCashDrawer(View view) {
        SwitchMaterial switchCashDrawer = view.findViewById(R.id.switchAutoCashDrawer);
        switchCashDrawer.setChecked(preferenceStore.isAutoCashDrawer());
        switchCashDrawer.setOnCheckedChangeListener((btn, checked) -> {
            preferenceStore.setAutoCashDrawer(checked);
            Toast.makeText(requireContext(), R.string.setting_saved, Toast.LENGTH_SHORT).show();
        });
    }

    /**
     * Menyiapkan selector codepage teks ESC/POS (UTF-8, CP437, CP852).
     */
    private void setupCodePage(View view) {
        RadioGroup radioGroup = view.findViewById(R.id.radioGroupCodePage);
        RadioButton radioUtf8 = view.findViewById(R.id.radioCodePageUtf8);
        RadioButton radioCp437 = view.findViewById(R.id.radioCodePageCp437);
        RadioButton radioCp852 = view.findViewById(R.id.radioCodePageCp852);

        CodePage saved = preferenceStore.getCodePage();
        if (saved == CodePage.CP437) {
            radioCp437.setChecked(true);
        } else if (saved == CodePage.CP852) {
            radioCp852.setChecked(true);
        } else {
            radioUtf8.setChecked(true);
        }

        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            CodePage cp;
            if (checkedId == R.id.radioCodePageCp437) {
                cp = CodePage.CP437;
            } else if (checkedId == R.id.radioCodePageCp852) {
                cp = CodePage.CP852;
            } else {
                cp = CodePage.UTF8;
            }
            preferenceStore.setCodePage(cp);
            Toast.makeText(requireContext(), R.string.setting_saved, Toast.LENGTH_SHORT).show();
        });
    }
}
