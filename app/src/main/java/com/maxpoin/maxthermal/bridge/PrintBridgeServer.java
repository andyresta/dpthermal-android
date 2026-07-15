package com.maxpoin.maxthermal.bridge;

import com.maxpoin.maxthermal.bluetooth.BluetoothPrinterManager;
import com.maxpoin.maxthermal.bluetooth.PrinterPreferenceStore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * HTTP server lokal berbasis NanoHTTPD untuk menerima perintah cetak dari website/aplikasi lain.
 * Berjalan di localhost dengan port yang dapat dikonfigurasi.
 *
 * <p>Endpoint utama: {@code POST /print}</p>
 * <p>Payload JSON:
 * <pre>
 * {
 *   "logo": "&lt;base64 string gambar&gt;",   (opsional)
 *   "text": "teks yang akan dicetak"        (opsional)
 * }
 * </pre>
 * </p>
 */
public class PrintBridgeServer extends NanoHTTPD {

    private final BluetoothPrinterManager printerManager;
    private final PrinterPreferenceStore preferenceStore;

    /**
     * Membuat HTTP server bridge pada port tertentu.
     *
     * @param port           nomor port server
     * @param printerManager manager Bluetooth printer yang aktif
     * @param preferenceStore penyimpanan preferensi untuk setting cetak
     */
    public PrintBridgeServer(int port, BluetoothPrinterManager printerManager,
                              PrinterPreferenceStore preferenceStore) {
        super(port);
        this.printerManager = printerManager;
        this.preferenceStore = preferenceStore;
    }

    @Override
    public Response serve(IHTTPSession session) {
        String method = session.getMethod().name();
        String uri = session.getUri();

        // Handle preflight CORS
        if ("OPTIONS".equals(method)) {
            return corsResponse(newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, ""));
        }

        // GET / — halaman UI web
        if ("GET".equals(method) && ("/".equals(uri) || "".equals(uri))) {
            return corsResponse(newFixedLengthResponse(
                    Response.Status.OK, "text/html; charset=utf-8", buildWebUi()));
        }

        // GET /status — cek koneksi printer
        if ("GET".equals(method) && "/status".equals(uri)) {
            boolean connected = printerManager.isConnected();
            String name = printerManager.getConnectedName();
            String json = "{\"connected\":" + connected
                    + ",\"printer\":" + (name != null ? "\"" + name + "\"" : "null") + "}";
            return corsResponse(newFixedLengthResponse(Response.Status.OK, "application/json", json));
        }

        // GET /check-bridge-print — health check service bridge (selaras maxpoin-uniprint)
        if ("/check-bridge-print".equals(uri)) {
            return handleCheckBridgePrint();
        }

        // POST /print
        if ("POST".equals(method) && "/print".equals(uri)) {
            return handlePrint(session);
        }

        // POST /print/image
        if ("POST".equals(method) && "/print/image".equals(uri)) {
            return handlePrintImage(session);
        }

        // POST /print/receipt
        if ("POST".equals(method) && "/print/receipt".equals(uri)) {
            return handlePrintReceipt(session);
        }

        return corsResponse(
                newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found"));
    }

    /**
     * Menangani request GET /check-bridge-print.
     * Mengembalikan status kesiapan layanan Print Bridge (ready check).
     * Path, method, dan response selaras dengan maxpoin-uniprint.
     *
     * <p>Response: {@code {"code":0,"msg":"ready"}} HTTP 200</p>
     *
     * @return response HTTP JSON ready check
     */
    private Response handleCheckBridgePrint() {
        return corsResponse(newFixedLengthResponse(
                Response.Status.OK, "application/json",
                "{\"code\":0,\"msg\":\"ready\"}"));
    }

    /**
     * Menangani request POST /print.
     * Membaca body JSON, mengekstrak logo dan/atau text, lalu mengirimkan ke printer.
     *
     * @param session sesi HTTP masuk
     * @return response HTTP dengan status berhasil atau error
     */
    private Response handlePrint(IHTTPSession session) {
        if (!printerManager.isConnected()) {
            return corsResponse(newFixedLengthResponse(
                    Response.Status.SERVICE_UNAVAILABLE, "application/json",
                    "{\"error\":\"Printer tidak terhubung\"}"));
        }

        try {
            Map<String, String> files = new HashMap<>();
            session.parseBody(files);
            String body = files.get("postData");
            if (body == null || body.trim().isEmpty()) {
                return corsResponse(newFixedLengthResponse(
                        Response.Status.BAD_REQUEST, "application/json",
                        "{\"error\":\"Body kosong\"}"));
            }

            JSONObject json = new JSONObject(body);
            String logoBase64 = json.optString("logo", null);
            String text = json.optString("text", null);

            boolean hasLogo = logoBase64 != null && !logoBase64.trim().isEmpty();
            boolean hasText = text != null && !text.trim().isEmpty();

            if (!hasLogo && !hasText) {
                return corsResponse(newFixedLengthResponse(
                        Response.Status.BAD_REQUEST, "application/json",
                        "{\"error\":\"Tidak ada konten untuk dicetak\"}"));
            }

            final String payload = body;
            PrintBridgeQueue.Result result = PrintBridgeQueue.submitAndWait(() -> {
                BridgePrintEngine.executePrintText(printerManager, preferenceStore, json, false);
                return null;
            });

            if (result.success) {
                PrintLogStore.add("/print", payload,
                        BridgePrintEngine.buildSummary("/print", payload), true);
                return corsResponse(newFixedLengthResponse(
                        Response.Status.OK, "application/json", "{\"success\":true}"));
            }
            PrintLogStore.add("/print", payload,
                    BridgePrintEngine.buildSummary("/print", payload), false);
            return corsResponse(newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR, "application/json",
                    "{\"error\":\"" + escapeJson(result.errorMessage) + "\"}"));

        } catch (Exception e) {
            return errorResponse(e);
        }
    }

    /**
     * Menangani request POST /print/image.
     * Mencetak gambar penuh lebar kertas dari payload base64.
     * Payload: {@code { "image": "<base64>" }}
     *
     * @param session sesi HTTP masuk
     * @return response HTTP
     */
    private Response handlePrintImage(IHTTPSession session) {
        if (!printerManager.isConnected()) {
            return corsResponse(newFixedLengthResponse(
                    Response.Status.SERVICE_UNAVAILABLE, "application/json",
                    "{\"error\":\"Printer tidak terhubung\"}"));
        }

        try {
            Map<String, String> files = new HashMap<>();
            session.parseBody(files);
            String body = files.get("postData");
            if (body == null || body.trim().isEmpty()) {
                return corsResponse(newFixedLengthResponse(
                        Response.Status.BAD_REQUEST, "application/json",
                        "{\"error\":\"Body kosong\"}"));
            }

            JSONObject json = new JSONObject(body);
            String imageBase64 = json.optString("image", null);
            if (imageBase64 == null || imageBase64.trim().isEmpty()) {
                return corsResponse(newFixedLengthResponse(
                        Response.Status.BAD_REQUEST, "application/json",
                        "{\"error\":\"Field 'image' tidak ditemukan atau kosong\"}"));
            }

            final String payload = body;
            PrintBridgeQueue.Result result = PrintBridgeQueue.submitAndWait(() -> {
                BridgePrintEngine.executePrintImage(printerManager, preferenceStore, json, false);
                return null;
            });

            if (result.success) {
                PrintLogStore.add("/print/image", payload,
                        BridgePrintEngine.buildSummary("/print/image", payload), true);
                return corsResponse(newFixedLengthResponse(
                        Response.Status.OK, "application/json", "{\"success\":true}"));
            }
            PrintLogStore.add("/print/image", payload,
                    BridgePrintEngine.buildSummary("/print/image", payload), false);
            return corsResponse(newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR, "application/json",
                    "{\"error\":\"" + escapeJson(result.errorMessage) + "\"}"));

        } catch (Exception e) {
            return errorResponse(e);
        }
    }

    /**
     * Menangani request POST /print/receipt.
     * Mencetak nota/struk berdasarkan array item dengan formatting.
     *
     * <p>Payload JSON:
     * <pre>
     * {
     *   "items": [
     *     { "type": "text", "align": "center", "size": 2, "style": "bold", "data": "NAMA TOKO" },
     *     { "type": "line" },
     *     { "type": "qr", "align": "center", "size": 200, "data": "https://..." },
     *     { "type": "image", "align": "center", "data": "&lt;base64&gt;" },
     *     { "type": "feed", "data": "3" }
     *   ]
     * }
     * </pre>
     * </p>
     *
     * <p>Type yang didukung:
     * <ul>
     *   <li><b>text</b> — cetak teks (align, size 1-8, style: bold/underline/bold,underline)</li>
     *   <li><b>qr</b> — generate & cetak QR code (align, size dalam px, data berisi konten QR)</li>
     *   <li><b>image</b> — cetak gambar base64 (align)</li>
     *   <li><b>line</b> — cetak garis pemisah (style: "single"/"double"/"dotted")</li>
     *   <li><b>feed</b> — baris kosong (data: jumlah baris, default 1)</li>
     * </ul>
     * </p>
     *
     * @param session sesi HTTP masuk
     * @return response HTTP
     */
    private Response handlePrintReceipt(IHTTPSession session) {
        if (!printerManager.isConnected()) {
            return corsResponse(newFixedLengthResponse(
                    Response.Status.SERVICE_UNAVAILABLE, "application/json",
                    "{\"error\":\"Printer tidak terhubung\"}"));
        }

        try {
            Map<String, String> files = new HashMap<>();
            session.parseBody(files);
            String body = files.get("postData");
            if (body == null || body.trim().isEmpty()) {
                return corsResponse(newFixedLengthResponse(
                        Response.Status.BAD_REQUEST, "application/json",
                        "{\"error\":\"Body kosong\"}"));
            }

            JSONObject json = new JSONObject(body);
            JSONArray items = json.optJSONArray("items");
            if (items == null || items.length() == 0) {
                return corsResponse(newFixedLengthResponse(
                        Response.Status.BAD_REQUEST, "application/json",
                        "{\"error\":\"Field 'items' tidak ditemukan atau kosong\"}"));
            }

            final String payload = body;
            PrintBridgeQueue.Result result = PrintBridgeQueue.submitAndWait(() -> {
                BridgePrintEngine.executePrintReceipt(printerManager, preferenceStore, json, false);
                return null;
            });

            if (result.success) {
                PrintLogStore.add("/print/receipt", payload,
                        BridgePrintEngine.buildSummary("/print/receipt", payload), true);
                return corsResponse(newFixedLengthResponse(
                        Response.Status.OK, "application/json", "{\"success\":true}"));
            }
            PrintLogStore.add("/print/receipt", payload,
                    BridgePrintEngine.buildSummary("/print/receipt", payload), false);
            return corsResponse(newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR, "application/json",
                    "{\"error\":\"" + escapeJson(result.errorMessage) + "\"}"));

        } catch (Exception e) {
            return errorResponse(e);
        }
    }

    /**
     * Membuat response error JSON dari exception.
     *
     * @param e exception sumber
     * @return response HTTP error
     */
    private Response errorResponse(Exception e) {
        String msg = e.getMessage() != null ? e.getMessage() : "unknown error";
        return corsResponse(newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR, "application/json",
                "{\"error\":\"" + escapeJson(msg) + "\"}"));
    }

    /**
     * Meng-escape string untuk aman dimasukkan ke JSON.
     *
     * @param value teks mentah
     * @return teks escaped
     */
    private static String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "'");
    }

    /**
     * Membangun halaman HTML untuk UI test print di browser.
     * Berisi form input teks, upload logo, status printer, dan sample payload.
     *
     * @return string HTML lengkap
     */
    private String buildWebUi() {
        boolean connected = printerManager.isConnected();
        String printerName = printerManager.getConnectedName();
        String statusColor = connected ? "#4CAF50" : "#F44336";
        String statusText = connected
                ? "● Terhubung" + (printerName != null ? ": " + printerName : "")
                : "● Tidak terhubung";

        return "<!DOCTYPE html><html lang='id'><head>"
                + "<meta charset='UTF-8'>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<title>DPThermal Print Bridge</title>"
                + "<style>"
                + "body{font-family:sans-serif;margin:0;padding:16px;background:#f5f5f5;color:#333}"
                + "h1{font-size:20px;margin:0 0 4px}"
                + ".subtitle{color:#888;font-size:13px;margin-bottom:20px}"
                + ".card{background:#fff;border-radius:12px;padding:16px;margin-bottom:16px;"
                + "box-shadow:0 2px 6px rgba(0,0,0,.1)}"
                + ".status{font-weight:bold;color:" + statusColor + ";font-size:15px;margin-bottom:4px}"
                + "label{display:block;font-size:13px;font-weight:bold;margin:12px 0 4px}"
                + "textarea,input[type=text],input[type=file]{"
                + "width:100%;box-sizing:border-box;border:1px solid #ddd;border-radius:8px;"
                + "padding:10px;font-size:14px;font-family:inherit}"
                + "textarea{min-height:80px;resize:vertical}"
                + ".btn{display:block;width:100%;padding:14px;border:none;border-radius:8px;"
                + "font-size:16px;font-weight:bold;cursor:pointer;margin-top:12px}"
                + ".btn-primary{background:#6200EE;color:#fff}"
                + ".btn-primary:disabled{background:#ccc;cursor:not-allowed}"
                + ".btn-secondary{background:#fff;color:#6200EE;border:2px solid #6200EE}"
                + ".result{margin-top:10px;padding:10px;border-radius:8px;font-size:14px;display:none}"
                + ".ok{background:#E8F5E9;color:#2E7D32}"
                + ".err{background:#FFEBEE;color:#C62828}"
                + "pre{background:#1e1e1e;color:#d4d4d4;padding:14px;border-radius:8px;"
                + "font-size:12px;overflow-x:auto;white-space:pre-wrap;word-break:break-all}"
                + "img#logoPreview{max-width:100%;max-height:120px;margin-top:8px;"
                + "display:none;border-radius:8px;border:1px solid #eee}"
                + "img#imgPreview{max-width:100%;margin-top:8px;"
                + "display:none;border-radius:8px;border:1px solid #eee}"
                + ".divider{border:none;border-top:1px solid #eee;margin:16px 0}"
                + ".tab-bar{display:flex;gap:8px;margin-bottom:16px}"
                + ".tab{flex:1;padding:10px;border:2px solid #6200EE;border-radius:8px;"
                + "background:#fff;color:#6200EE;font-weight:bold;cursor:pointer;font-size:14px}"
                + ".tab.active{background:#6200EE;color:#fff}"
                + ".tab-content{display:none}.tab-content.active{display:block}"
                + "</style></head><body>"

                // Header
                + "<h1>DPThermal Print Bridge</h1>"
                + "<div class='subtitle'>HTTP server aktif untuk cetak thermal dari website</div>"

                // Card status
                + "<div class='card'>"
                + "<div class='status' id='statusText'>" + statusText + "</div>"
                + "<div style='font-size:12px;color:#888'>Endpoints: "
                + "<code>GET /check-bridge-print</code> · "
                + "<code>POST /print</code> · <code>POST /print/image</code> · <code>POST /print/receipt</code></div>"
                + "</div>"

                // Tab bar
                + "<div class='tab-bar'>"
                + "<button class='tab active' onclick='switchTab(0)'>Teks+Logo</button>"
                + "<button class='tab' onclick='switchTab(1)'>Gambar</button>"
                + "<button class='tab' onclick='switchTab(2)'>Receipt</button>"
                + "<button class='tab' onclick='switchTab(3)'>Dokumentasi</button>"
                + "</div>"

                // Tab 0: form /print
                + "<div class='tab-content active' id='tab0'>"
                + "<div class='card'>"

                + "<label>Logo (opsional)</label>"
                + "<input type='file' id='logoFile' accept='image/*' onchange='previewLogo(this)'>"
                + "<img id='logoPreview'>"

                + "<label>Teks</label>"
                + "<textarea id='printText' placeholder='Contoh:\nNama Toko\n================\nItem 1   Rp 10.000\nTotal    Rp 10.000\n\nTerima Kasih!'></textarea>"

                + "<button class='btn btn-primary' id='btnPrint' onclick='doTestPrint()'"
                + (connected ? "" : " disabled") + ">Cetak Sekarang</button>"
                + "<div id='printResult' class='result'></div>"
                + "</div></div>"

                // Tab 1: form /print/image
                + "<div class='tab-content' id='tab1'>"
                + "<div class='card'>"
                + "<label>Pilih Gambar</label>"
                + "<input type='file' id='imgFile' accept='image/*' onchange='previewImg(this)'>"
                + "<img id='imgPreview'>"
                + "<button class='btn btn-primary' id='btnPrintImg' onclick='doTestPrintImage()'"
                + (connected ? "" : " disabled") + ">Cetak Gambar</button>"
                + "<div id='printImgResult' class='result'></div>"
                + "</div></div>"

                // Tab 2: Receipt Builder (test /print/receipt)
                + "<div class='tab-content' id='tab2'>"
                + "<div class='card'>"
                + "<label>Payload JSON</label>"
                + "<textarea id='receiptPayload' style='min-height:220px;font-family:monospace;font-size:12px'>"
                + "{\n"
                + "  \"cut_paper\": true,\n"
                + "  \"width_mm\": 58,\n"
                + "  \"copies\": 1,\n"
                + "  \"items\": [\n"
                + "    { \"type\": \"text\", \"align\": \"center\", \"size\": 2, \"style\": \"bold\", \"data\": \"NAMA TOKO\" },\n"
                + "    { \"type\": \"text\", \"align\": \"center\", \"data\": \"Jl. Contoh No. 123, Kota\" },\n"
                + "    { \"type\": \"text\", \"align\": \"center\", \"data\": \"Telp: 021-1234567\" },\n"
                + "    { \"type\": \"line\", \"style\": \"double\" },\n"
                + "    { \"type\": \"text\", \"data\": \"Item 1          x2   20.000\" },\n"
                + "    { \"type\": \"text\", \"data\": \"Item 2          x1   15.000\" },\n"
                + "    { \"type\": \"text\", \"data\": \"Item 3          x3   45.000\" },\n"
                + "    { \"type\": \"line\" },\n"
                + "    { \"type\": \"text\", \"align\": \"right\", \"size\": 2, \"style\": \"bold\", \"data\": \"TOTAL: Rp 80.000\" },\n"
                + "    { \"type\": \"line\" },\n"
                + "    { \"type\": \"text\", \"align\": \"center\", \"data\": \"Bayar: Rp 100.000\" },\n"
                + "    { \"type\": \"text\", \"align\": \"center\", \"data\": \"Kembali: Rp 20.000\" },\n"
                + "    { \"type\": \"feed\", \"data\": \"1\" },\n"
                + "    { \"type\": \"qr\", \"align\": \"center\", \"size\": 200, \"data\": \"https://toko.example.com/inv/12345\" },\n"
                + "    { \"type\": \"feed\", \"data\": \"1\" },\n"
                + "    { \"type\": \"image\", \"align\": \"center\", \"data\": \"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAADAAAAAwCAYAAABXAvmHAAAAAXNSR0IArs4c6QAAAARnQU1BAACxjwv8YQUAAAAJcEhZcwAADsMAAA7DAcdvqGQAAAElSURBVGhD7ZZhCsMwCEZzPA/kcbxLrpKbOLq1m9EUyij4FXyQH+u+go+amKYPp/kHT6MEsimBbEogmxLIpgSy+V+gs7bW4uLukz+GKPn8tkh0+OxF7hdorGcKQ2iRhxNoevYROscspADJqpyuvMhCCiwLMnki10qr/EXuEyBWpuN33Ae2/5n9uxACot0WORkMla8cqUh8F0Jg2GeTgel/n4MSsOe8LcqL+XkAI+BbZa/KHp/vEwpSYG+ZUOxKCllgmrahXfbTCVkgFGxzR6HQAtPEJaVv+9gJ7aYyloDt+Xn9TlZAAXv/Wd867XQGF/D/xSLRBfwm3dbZZIYUWOyD+W4ELzAPNDuVPzjBFAEQSiCbEsimBLIpgWxKIJsSyKYEsnm8wAsSetggOsSZ/wAAAABJRU5ErkJggg==\" },\n"
                + "    { \"type\": \"feed\", \"data\": \"1\" },\n"
                + "    { \"type\": \"text\", \"align\": \"center\", \"style\": \"bold\", \"data\": \"Terima kasih!\" },\n"
                + "    { \"type\": \"feed\", \"data\": \"3\" }\n"
                + "  ]\n"
                + "}"
                + "</textarea>"
                + "<label>Sisipkan Gambar (opsional — base64 akan otomatis masuk ke item image di payload)</label>"
                + "<input type='file' id='receiptImgFile' accept='image/*' onchange='insertImageToPayload(this)'>"
                + "<img id='receiptImgPreview' style='max-width:100%;max-height:120px;margin-top:8px;display:none;border-radius:8px;border:1px solid #eee'>"
                + "<button class='btn btn-primary' id='btnReceipt' onclick='doTestReceipt()'"
                + (connected ? "" : " disabled") + ">Cetak Receipt</button>"
                + "<div id='receiptResult' class='result'></div>"
                + "</div></div>"

                // Tab 3: Dokumentasi API
                + "<div class='tab-content' id='tab3'>"
                + "<div class='card'>"
                + "<h2 style='margin-top:0'>API Dokumentasi</h2>"
                + "<p style='color:#666;font-size:13px'>Spesifikasi teknis DPThermal Print Bridge API</p>"

                + "<hr class='divider'>"
                + "<h3>1. POST /print</h3>"
                + "<p>Cetak teks sederhana dengan logo (opsional).</p>"
                + "<pre>{\n"
                + "  \"logo\": \"data:image/png;base64,...\",  // opsional\n"
                + "  \"text\": \"Teks yang akan dicetak\"\n"
                + "}</pre>"

                + "<hr class='divider'>"
                + "<h3>2. POST /print/image</h3>"
                + "<p>Cetak gambar full-width.</p>"
                + "<pre>{\n"
                + "  \"image\": \"data:image/png;base64,...\"\n"
                + "}</pre>"

                + "<hr class='divider'>"
                + "<h3>3. POST /print/receipt <span style='background:#6200EE;color:#fff;padding:2px 8px;border-radius:4px;font-size:11px'>NEW</span></h3>"
                + "<p>Cetak nota/struk dengan format terstruktur. Mendukung berbagai tipe item.</p>"
                + "<pre>{\n"
                + "  \"cut_paper\": true,     // opsional, default: pengaturan app\n"
                + "  \"width_mm\": 58,        // opsional: 58 | 80\n"
                + "  \"copies\": 1,           // opsional: 1-10\n"
                + "  \"items\": [\n"
                + "    { \"type\": \"...\", ... },\n"
                + "    ...\n"
                + "  ]\n"
                + "}</pre>"

                + "<hr class='divider'>"
                + "<h3>Tipe Item yang Didukung</h3>"

                + "<h4>📝 text</h4>"
                + "<p>Cetak baris teks dengan formatting.</p>"
                + "<pre>{\n"
                + "  \"type\": \"text\",\n"
                + "  \"align\": \"left|center|right\",  // default: left\n"
                + "  \"size\": 1-8,                    // default: 1 (normal)\n"
                + "  \"style\": \"bold|underline|bold,underline\",  // default: (kosong)\n"
                + "  \"data\": \"Teks yang dicetak\"\n"
                + "}</pre>"
                + "<table style='width:100%;font-size:12px;border-collapse:collapse;margin:8px 0'>"
                + "<tr style='background:#f0f0f0'><th style='padding:6px;text-align:left;border:1px solid #ddd'>Size</th>"
                + "<th style='padding:6px;text-align:left;border:1px solid #ddd'>Keterangan</th></tr>"
                + "<tr><td style='padding:6px;border:1px solid #ddd'>1</td><td style='padding:6px;border:1px solid #ddd'>Normal (default)</td></tr>"
                + "<tr><td style='padding:6px;border:1px solid #ddd'>2</td><td style='padding:6px;border:1px solid #ddd'>2x (Double width+height)</td></tr>"
                + "<tr><td style='padding:6px;border:1px solid #ddd'>3-8</td><td style='padding:6px;border:1px solid #ddd'>Semakin besar angka, semakin besar teks</td></tr>"
                + "</table>"

                + "<h4>📱 qr</h4>"
                + "<p>Generate dan cetak QR Code.</p>"
                + "<pre>{\n"
                + "  \"type\": \"qr\",\n"
                + "  \"align\": \"left|center|right\",  // default: center\n"
                + "  \"size\": 100-500,                // ukuran QR dalam piksel, default: 200\n"
                + "  \"data\": \"https://contoh.com\"    // konten QR\n"
                + "}</pre>"

                + "<h4>🖼 image</h4>"
                + "<p>Cetak gambar dari base64.</p>"
                + "<pre>{\n"
                + "  \"type\": \"image\",\n"
                + "  \"align\": \"left|center|right\",  // default: center\n"
                + "  \"data\": \"data:image/png;base64,...\"  // base64 gambar\n"
                + "}</pre>"

                + "<h4>➖ line</h4>"
                + "<p>Cetak garis pemisah horizontal.</p>"
                + "<pre>{\n"
                + "  \"type\": \"line\",\n"
                + "  \"style\": \"single|double|dotted\"  // default: single\n"
                + "  // single = --------\n"
                + "  // double = ========\n"
                + "  // dotted = ........\n"
                + "}</pre>"

                + "<h4>⬇ feed</h4>"
                + "<p>Cetak baris kosong.</p>"
                + "<pre>{\n"
                + "  \"type\": \"feed\",\n"
                + "  \"data\": \"3\"  // jumlah baris, default: 1, max: 10\n"
                + "}</pre>"

                + "<hr class='divider'>"
                + "<h3>Response Format</h3>"
                + "<pre>// Sukses\n{ \"success\": true }\n\n// Gagal\n{ \"error\": \"Pesan error\" }</pre>"

                + "<hr class='divider'>"
                + "<h3>GET /check-bridge-print</h3>"
                + "<p>Cek apakah service Print Bridge sudah berjalan (health check).</p>"
                + "<pre>// Response HTTP 200\n{ \"code\": 0, \"msg\": \"ready\" }</pre>"

                + "<hr class='divider'>"
                + "<h3>GET /status</h3>"
                + "<p>Cek status koneksi printer.</p>"
                + "<pre>// Response\n{ \"connected\": true, \"printer\": \"Nama Printer\" }</pre>"

                + "<hr class='divider'>"
                + "<h3>Contoh Implementasi (JavaScript)</h3>"
                + "<pre>async function printReceipt(items) {\n"
                + "  const response = await fetch('http://DEVICE_IP:PORT/print/receipt', {\n"
                + "    method: 'POST',\n"
                + "    headers: { 'Content-Type': 'application/json' },\n"
                + "    body: JSON.stringify({ items })\n"
                + "  });\n"
                + "  return await response.json();\n"
                + "}\n"
                + "\n"
                + "// Contoh penggunaan\n"
                + "printReceipt([\n"
                + "  { type: 'text', align: 'center', size: 2, style: 'bold', data: 'WARUNG MAKAN' },\n"
                + "  { type: 'text', align: 'center', data: 'Jl. Raya No. 1' },\n"
                + "  { type: 'line', style: 'double' },\n"
                + "  { type: 'text', data: 'Nasi Goreng  x1   15.000' },\n"
                + "  { type: 'text', data: 'Es Teh       x2   10.000' },\n"
                + "  { type: 'line' },\n"
                + "  { type: 'text', align: 'right', size: 2, style: 'bold', data: 'TOTAL Rp 25.000' },\n"
                + "  { type: 'qr', align: 'center', size: 200, data: 'https://pay.example/123' },\n"
                + "  { type: 'feed', data: '3' }\n"
                + "]);</pre>"
                + "</div></div>"

                // Script
                + "<script>"
                + "function switchTab(i){"
                + "  document.querySelectorAll('.tab').forEach(function(t,idx){t.className='tab'+(idx===i?' active':'');});"
                + "  document.querySelectorAll('.tab-content').forEach(function(t,idx){t.className='tab-content'+(idx===i?' active':'');});}"

                + "function previewLogo(input){"
                + "  var f=input.files[0];if(!f)return;"
                + "  var r=new FileReader();r.onload=function(e){"
                + "    var img=document.getElementById('logoPreview');"
                + "    img.src=e.target.result;img.style.display='block';"
                + "  };r.readAsDataURL(f);}"

                + "function previewImg(input){"
                + "  var f=input.files[0];if(!f)return;"
                + "  var r=new FileReader();r.onload=function(e){"
                + "    var img=document.getElementById('imgPreview');"
                + "    img.src=e.target.result;img.style.display='block';"
                + "  };r.readAsDataURL(f);}"

                + "var isPrinting=false;"

                + "async function refreshStatus(){"
                + "  try{"
                + "    var r=await fetch('/status');var j=await r.json();"
                + "    var el=document.getElementById('statusText');"
                + "    var btn=document.getElementById('btnPrint');"
                + "    var btnImg=document.getElementById('btnPrintImg');"
                + "    var btnRcpt=document.getElementById('btnReceipt');"
                + "    if(j.connected){"
                + "      el.style.color='#4CAF50';"
                + "      el.textContent='● Terhubung'+(j.printer?': '+j.printer:'');"
                + "      if(!isPrinting){btn.disabled=false;btnImg.disabled=false;btnRcpt.disabled=false;}"
                + "    }else{"
                + "      el.style.color='#F44336';"
                + "      el.textContent='● Tidak terhubung — hubungkan printer di aplikasi DPThermal';"
                + "      btn.disabled=true;btnImg.disabled=true;btnRcpt.disabled=true;"
                + "    }"
                + "  }catch(e){}"
                + "}"

                + "async function doTestPrint(){"
                + "  var btn=document.getElementById('btnPrint');"
                + "  var res=document.getElementById('printResult');"
                + "  isPrinting=true;btn.disabled=true;btn.textContent='Mencetak...';"
                + "  res.style.display='none';"
                + "  var payload={};"
                + "  var text=document.getElementById('printText').value;"
                + "  if(text.trim())payload.text=text;"
                + "  var file=document.getElementById('logoFile').files[0];"
                + "  if(file){var b64=await toBase64(file);payload.logo=b64;}"
                + "  try{"
                + "    var r=await fetch('/print',{method:'POST',"
                + "      headers:{'Content-Type':'application/json'},"
                + "      body:JSON.stringify(payload)});"
                + "    var j=await r.json();"
                + "    if(j.success){res.className='result ok';res.textContent='✓ Berhasil dikirim ke printer';}"
                + "    else{res.className='result err';res.textContent='✗ '+j.error;}"
                + "  }catch(e){res.className='result err';res.textContent='✗ Gagal: '+e.message;}"
                + "  res.style.display='block';"
                + "  isPrinting=false;btn.textContent='Cetak Sekarang';refreshStatus();}"

                + "async function doTestPrintImage(){"
                + "  var btn=document.getElementById('btnPrintImg');"
                + "  var res=document.getElementById('printImgResult');"
                + "  var file=document.getElementById('imgFile').files[0];"
                + "  if(!file){res.className='result err';res.textContent='✗ Pilih gambar terlebih dahulu';res.style.display='block';return;}"
                + "  isPrinting=true;btn.disabled=true;btn.textContent='Mencetak...';"
                + "  res.style.display='none';"
                + "  try{"
                + "    var b64=await toBase64(file);"
                + "    var r=await fetch('/print/image',{method:'POST',"
                + "      headers:{'Content-Type':'application/json'},"
                + "      body:JSON.stringify({image:b64})});"
                + "    var j=await r.json();"
                + "    if(j.success){res.className='result ok';res.textContent='✓ Gambar berhasil dicetak';}"
                + "    else{res.className='result err';res.textContent='✗ '+j.error;}"
                + "  }catch(e){res.className='result err';res.textContent='✗ Gagal: '+e.message;}"
                + "  res.style.display='block';"
                + "  isPrinting=false;btn.textContent='Cetak Gambar';refreshStatus();}"

                + "async function doTestReceipt(){"
                + "  var btn=document.getElementById('btnReceipt');"
                + "  var res=document.getElementById('receiptResult');"
                + "  isPrinting=true;btn.disabled=true;btn.textContent='Mencetak...';"
                + "  res.style.display='none';"
                + "  try{"
                + "    var payload=document.getElementById('receiptPayload').value;"
                + "    var parsed=JSON.parse(payload);"
                + "    var r=await fetch('/print/receipt',{method:'POST',"
                + "      headers:{'Content-Type':'application/json'},"
                + "      body:JSON.stringify(parsed)});"
                + "    var j=await r.json();"
                + "    if(j.success){res.className='result ok';res.textContent='✓ Receipt berhasil dicetak';}"
                + "    else{res.className='result err';res.textContent='✗ '+j.error;}"
                + "  }catch(e){res.className='result err';res.textContent='✗ '+e.message;}"
                + "  res.style.display='block';"
                + "  isPrinting=false;btn.textContent='Cetak Receipt';refreshStatus();}"

                + "async function insertImageToPayload(input){"
                + "  var f=input.files[0];if(!f)return;"
                + "  var prev=document.getElementById('receiptImgPreview');"
                + "  var b64=await toBase64(f);"
                + "  prev.src=b64;prev.style.display='block';"
                + "  var ta=document.getElementById('receiptPayload');"
                + "  var val=ta.value;"
                + "  var re=/\"type\":\\s*\"image\",\\s*\"align\":\\s*\"center\",\\s*\"data\":\\s*\"[^\"]*\"/;"
                + "  val=val.replace(re,'\"type\": \"image\", \"align\": \"center\", \"data\": \"'+b64+'\"');"
                + "  ta.value=val;}"

                + "function toBase64(file,maxW){"
                + "  maxW=maxW||800;"
                + "  return new Promise((res,rej)=>{"
                + "    var r=new FileReader();"
                + "    r.onerror=rej;"
                + "    r.onload=function(){"
                + "      var img=new Image();img.onerror=rej;"
                + "      img.onload=function(){"
                + "        var w=img.width,h=img.height;"
                + "        if(w>maxW){h=Math.round(h*(maxW/w));w=maxW;}"
                + "        var c=document.createElement('canvas');c.width=w;c.height=h;"
                + "        c.getContext('2d').drawImage(img,0,0,w,h);"
                + "        res(c.toDataURL('image/png'));"
                + "      };"
                + "      img.src=r.result;"
                + "    };"
                + "    r.readAsDataURL(file);})}"

                + "refreshStatus();"
                + "setInterval(refreshStatus,3000);"
                + "</script></body></html>";
    }

    /**
     * Menambahkan header CORS ke response agar dapat diakses dari website lintas origin.
     *
     * @param response response yang akan ditambahkan header CORS
     * @return response dengan header CORS
     */
    private Response corsResponse(Response response) {
        response.addHeader("Access-Control-Allow-Origin", "*");
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        response.addHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
        response.addHeader("Access-Control-Max-Age", "86400");
        return response;
    }
}
