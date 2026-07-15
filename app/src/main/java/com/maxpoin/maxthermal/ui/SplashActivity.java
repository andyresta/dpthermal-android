package com.maxpoin.maxthermal.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;

import com.maxpoin.maxthermal.R;

/**
 * Halaman splash screen yang ditampilkan saat aplikasi pertama kali dibuka.
 */
public class SplashActivity extends AppCompatActivity {

    private static final long SPLASH_DELAY_MS = 2000L;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable navigateRunnable = () -> {
        startActivity(new Intent(SplashActivity.this, MainActivity.class));
        finish();
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        handler.postDelayed(navigateRunnable, SPLASH_DELAY_MS);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(navigateRunnable);
        super.onDestroy();
    }
}
