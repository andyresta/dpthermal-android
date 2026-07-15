package com.maxpoin.maxthermal.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.view.Menu;
import android.view.MenuItem;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.navigation.NavigationView;
import com.maxpoin.maxthermal.R;

/**
 * Activity utama dengan Navigation Drawer (sidebar) untuk navigasi antar halaman.
 */
public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences prefs = newBase.getSharedPreferences("dpthermal_prefs", Context.MODE_PRIVATE);
        String language = prefs.getString("app_language", "id");
        
        Locale locale = new Locale(language);
        Locale.setDefault(locale);
        Configuration config = new Configuration(newBase.getResources().getConfiguration());
        config.setLocale(locale);
        
        Context context = newBase.createConfigurationContext(config);
        super.attachBaseContext(context);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        drawerLayout = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navigationView);
        navigationView.setNavigationItemSelectedListener(this);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar,
                R.string.nav_drawer_open, R.string.nav_drawer_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        int white = ContextCompat.getColor(this, R.color.white);
        toolbar.setNavigationIconTint(white);
        if (toggle.getDrawerArrowDrawable() != null) {
            toggle.getDrawerArrowDrawable().setColor(white);
        }
        
        Menu menu = navigationView.getMenu();
        MenuItem languageItem = menu.findItem(R.id.nav_language_toggle);
        if (languageItem != null) {
            SpannableString s = new SpannableString(languageItem.getTitle());
            s.setSpan(new UnderlineSpan(), 0, s.length(), 0);
            s.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, s.length(), 0);
            s.setSpan(new ForegroundColorSpan(ContextCompat.getColor(this, R.color.orange_500)), 0, s.length(), 0);
            languageItem.setTitle(s);
        }

        if (savedInstanceState == null) {
            navigateToFragment(new HomeFragment(), R.id.nav_home);
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        Fragment fragment = null;

        if (id == R.id.nav_home) {
            fragment = new HomeFragment();
        } else if (id == R.id.nav_pengaturan_koneksi) {
            fragment = new PengaturanKoneksiFragment();
        } else if (id == R.id.nav_pengaturan_nota) {
            fragment = new PengaturanNotaFragment();
        } else if (id == R.id.nav_tes_cetak) {
            fragment = new TesCetakFragment();
        } else if (id == R.id.nav_bridge_print) {
            fragment = new BridgePrintFragment();
        } else if (id == R.id.nav_log_cetak) {
            fragment = new LogCetakFragment();
        } else if (id == R.id.nav_help) {
            fragment = new HelpFragment();
        } else if (id == R.id.nav_language_toggle) {
            SharedPreferences prefs = getSharedPreferences("dpthermal_prefs", Context.MODE_PRIVATE);
            String currentLang = prefs.getString("app_language", "id");
            String newLang = currentLang.equals("id") ? "en" : "id";
            prefs.edit().putString("app_language", newLang).apply();
            
            // Recreate activity to apply the new locale
            recreate();
            return true;
        }

        if (fragment != null) {
            navigateToFragment(fragment, id);
        }
        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    /**
     * Mengganti fragment yang ditampilkan dan menandai item aktif di sidebar.
     *
     * @param fragment fragment baru
     * @param menuId   ID menu sidebar yang aktif
     */
    private void navigateToFragment(Fragment fragment, int menuId) {
        navigationView.setCheckedItem(menuId);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit();
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }
}
