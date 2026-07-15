package com.maxpoin.maxthermal.bridge;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.maxpoin.maxthermal.R;
import com.maxpoin.maxthermal.bluetooth.BluetoothPrinterManager;
import com.maxpoin.maxthermal.bluetooth.PrinterPreferenceStore;
import com.maxpoin.maxthermal.ui.MainActivity;

import java.io.IOException;

/**
 * Foreground Service yang menjalankan {@link PrintBridgeServer} di background.
 * Service ini menampilkan notifikasi permanen agar tidak di-kill oleh sistem.
 *
 * <p>Kirim Intent dengan aksi {@link #ACTION_START} untuk memulai,
 * dan {@link #ACTION_STOP} untuk menghentikan server.</p>
 */
public class PrintBridgeService extends Service {

    /** Intent action untuk memulai server bridge. */
    public static final String ACTION_START = "com.maxpoin.maxthermal.bridge.START";

    /** Intent action untuk menghentikan server bridge. */
    public static final String ACTION_STOP = "com.maxpoin.maxthermal.bridge.STOP";

    /** Extra int untuk nomor port. */
    public static final String EXTRA_PORT = "port";

    private static final String CHANNEL_ID = "print_bridge_channel";
    private static final int NOTIF_ID = 1001;

    private PrintBridgeServer server;
    private int currentPort;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        String action = intent.getAction();
        if (ACTION_START.equals(action)) {
            int port = intent.getIntExtra(EXTRA_PORT, PrinterPreferenceStore.DEFAULT_BRIDGE_PORT);
            startBridge(port);
        } else if (ACTION_STOP.equals(action)) {
            stopBridge();
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    /**
     * Memulai HTTP server pada port yang diminta.
     * Jika server sebelumnya sudah berjalan di port berbeda, server lama dihentikan dulu.
     *
     * @param port nomor port yang ingin digunakan
     */
    private void startBridge(int port) {
        if (server != null && server.wasStarted()) {
            if (currentPort == port) return;
            server.stop();
        }
        try {
            BluetoothPrinterManager manager = BluetoothPrinterManager.getInstance();
            server = new PrintBridgeServer(port, manager, manager.getPreferenceStore());
            server.start();
            currentPort = port;
            manager.getPreferenceStore().setBridgeRunning(true);
            startForeground(NOTIF_ID, buildNotification(port, true));
            BridgePrintWidgetProvider.updateAll(this);
        } catch (IOException e) {
            BluetoothPrinterManager.getInstance().getPreferenceStore().setBridgeRunning(false);
            BridgePrintWidgetProvider.updateAll(this);
            startForeground(NOTIF_ID, buildNotification(port, false));
        }
    }

    /**
     * Menghentikan HTTP server yang sedang berjalan.
     */
    private void stopBridge() {
        if (server != null) {
            server.stop();
            server = null;
        }
        try {
            BluetoothPrinterManager.getInstance().getPreferenceStore().setBridgeRunning(false);
        } catch (Exception ignored) {}
        BridgePrintWidgetProvider.updateAll(this);
        stopForeground(true);
    }

    /**
     * Membuat notifikasi foreground service untuk ditampilkan di status bar.
     *
     * @param port    port server
     * @param running true jika server berhasil berjalan
     * @return objek Notification
     */
    private Notification buildNotification(int port, boolean running) {
        Intent openApp = new Intent(this, MainActivity.class);
        PendingIntent pendingOpen = PendingIntent.getActivity(this, 0, openApp,
                PendingIntent.FLAG_IMMUTABLE);

        Intent stopIntent = new Intent(this, PrintBridgeService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent pendingStop = PendingIntent.getService(this, 0, stopIntent,
                PendingIntent.FLAG_IMMUTABLE);

        String title = running
                ? getString(R.string.bridge_notif_running_title)
                : getString(R.string.bridge_notif_error_title);
        String content = running
                ? getString(R.string.bridge_notif_running_content, port)
                : getString(R.string.bridge_notif_error_content, port);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_send)
                .setContentTitle(title)
                .setContentText(content)
                .setContentIntent(pendingOpen)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel,
                        getString(R.string.bridge_notif_action_stop), pendingStop)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    /**
     * Membuat NotificationChannel untuk Android 8+.
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.bridge_notif_channel_name),
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(getString(R.string.bridge_notif_channel_desc));
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        stopBridge();
        super.onDestroy();
    }}
