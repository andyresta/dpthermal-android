package com.maxpoin.maxthermal.bridge;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Antrian cetak FIFO berbasis single-thread executor untuk Print Bridge.
 * Memastikan job cetak diproses berurutan tanpa race condition ke printer.
 */
public final class PrintBridgeQueue {

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "PrintBridgeQueue");
        t.setDaemon(true);
        return t;
    });

    private static final long JOB_TIMEOUT_SEC = 120;

    private PrintBridgeQueue() {
    }

    /**
     * Hasil eksekusi job antrian cetak.
     */
    public static final class Result {
        public final boolean success;
        public final String errorMessage;

        Result(boolean success, String errorMessage) {
            this.success = success;
            this.errorMessage = errorMessage;
        }
    }

    /**
     * Menambahkan job cetak ke antrian dan menunggu hingga selesai.
     *
     * @param task runnable cetak yang dijalankan di thread antrian
     * @return hasil sukses atau pesan error
     */
    public static Result submitAndWait(Callable<Void> task) {
        Future<Void> future = EXECUTOR.submit(task);
        try {
            future.get(JOB_TIMEOUT_SEC, TimeUnit.SECONDS);
            return new Result(true, null);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            String msg = cause.getMessage();
            if (msg == null || msg.isEmpty()) {
                msg = "Gagal mencetak";
            }
            return new Result(false, msg);
        } catch (TimeoutException e) {
            future.cancel(true);
            return new Result(false, "Timeout antrian cetak");
        } catch (Exception e) {
            String msg = e.getMessage();
            return new Result(false, msg != null ? msg : "Gagal mencetak");
        }
    }
}
