package com.maxpoin.maxthermal.bridge;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import com.maxpoin.maxthermal.R;
import com.maxpoin.maxthermal.bluetooth.BluetoothPrinterManager;
import com.maxpoin.maxthermal.bluetooth.PrinterPreferenceStore;
import com.maxpoin.maxthermal.ui.MainActivity;

/**
 * Widget home screen Quick Action untuk start/stop Print Bridge server.
 */
public class BridgePrintWidgetProvider extends AppWidgetProvider {

    /** Action intent untuk memulai bridge dari widget. */
    public static final String ACTION_WIDGET_START =
            "com.maxpoin.maxthermal.bridge.WIDGET_START";

    /** Action intent untuk menghentikan bridge dari widget. */
    public static final String ACTION_WIDGET_STOP =
            "com.maxpoin.maxthermal.bridge.WIDGET_STOP";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (ACTION_WIDGET_START.equals(action)) {
            startBridge(context);
        } else if (ACTION_WIDGET_STOP.equals(action)) {
            stopBridge(context);
        }
        super.onReceive(context, intent);
        updateAll(context);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int id : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, id);
        }
    }

    /**
     * Memperbarui semua instance widget di home screen.
     *
     * @param context konteks aplikasi
     */
    public static void updateAll(Context context) {
        Context app = context.getApplicationContext();
        AppWidgetManager mgr = AppWidgetManager.getInstance(app);
        ComponentName cn = new ComponentName(app, BridgePrintWidgetProvider.class);
        int[] ids = mgr.getAppWidgetIds(cn);
        for (int id : ids) {
            updateAppWidget(app, mgr, id);
        }
    }

    /**
     * Memperbarui tampilan satu instance widget.
     *
     * @param context          konteks
     * @param appWidgetManager manager widget
     * @param appWidgetId      ID widget
     */
    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        boolean running = false;
        int port = PrinterPreferenceStore.DEFAULT_BRIDGE_PORT;
        try {
            PrinterPreferenceStore prefs = BluetoothPrinterManager.getInstance().getPreferenceStore();
            running = prefs.isBridgeRunning();
            port = prefs.getBridgePort();
        } catch (IllegalStateException ignored) {
        }

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_bridge);
        views.setTextViewText(R.id.textWidgetTitle, context.getString(R.string.widget_bridge_title));
        views.setTextViewText(R.id.textWidgetStatus, running
                ? context.getString(R.string.widget_bridge_running, port)
                : context.getString(R.string.widget_bridge_stopped));

        Intent openApp = new Intent(context, MainActivity.class);
        PendingIntent openPending = PendingIntent.getActivity(context, 0, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.textWidgetTitle, openPending);

        Intent startIntent = new Intent(context, BridgePrintWidgetProvider.class);
        startIntent.setAction(ACTION_WIDGET_START);
        PendingIntent startPending = PendingIntent.getBroadcast(context, 1, startIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.buttonWidgetStart, startPending);

        Intent stopIntent = new Intent(context, BridgePrintWidgetProvider.class);
        stopIntent.setAction(ACTION_WIDGET_STOP);
        PendingIntent stopPending = PendingIntent.getBroadcast(context, 2, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.buttonWidgetStop, stopPending);

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    /**
     * Memulai Print Bridge service dari widget.
     *
     * @param context konteks
     */
    private static void startBridge(Context context) {
        PrinterPreferenceStore prefs = BluetoothPrinterManager.getInstance().getPreferenceStore();
        if (prefs.isBridgeRunning()) return;
        Intent intent = new Intent(context, PrintBridgeService.class);
        intent.setAction(PrintBridgeService.ACTION_START);
        intent.putExtra(PrintBridgeService.EXTRA_PORT, prefs.getBridgePort());
        context.startForegroundService(intent);
    }

    /**
     * Menghentikan Print Bridge service dari widget.
     *
     * @param context konteks
     */
    private static void stopBridge(Context context) {
        Intent intent = new Intent(context, PrintBridgeService.class);
        intent.setAction(PrintBridgeService.ACTION_STOP);
        context.startService(intent);
    }
}
