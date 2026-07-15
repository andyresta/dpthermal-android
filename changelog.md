## 11/06/2026
- bug print center, port range, splash screen,
## 10/06/2026
- Paritas payload `/print/receipt`: proses parameter `cut_paper`, `width_mm`, dan `copies` (1–10 salinan) selaras maxpoin-uniprint
- Tambah antrian cetak FIFO (`PrintBridgeQueue`) — semua endpoint bridge diproses berurutan tanpa race condition
- Tambah halaman **Log Cetak** di sidebar: riwayat job bridge, cetak ulang, footer `--- CETAK ULANG ---` rata tengah di akhir struk
- Tambah pengaturan codepage teks ESC/POS: UTF-8, CP437, CP852 (Pengaturan Nota)
- Tambah widget home screen **DPThermal Bridge** — Quick Action mulai/hentikan Print Bridge server
- Refactor logic cetak ke `BridgePrintEngine` + penyimpanan log `PrintLogStore` (maks 100 entri)
- Update sampel & dokumentasi receipt di web UI bridge (field `cut_paper`, `width_mm`, `copies`)

## 26/05/2026 - 10:23
- Tambah dependency ZXing 3.5.3 untuk QR code generation
- Tambah method ESC/POS: alignRight, align(string), setSize(1-8), setBold, setUnderline, resetFormatting
- Tambah method generateQrCode() untuk generate QR code dari teks/URL menjadi Bitmap
- Implementasi endpoint POST /print/receipt — cetak nota terstruktur dengan array item
- Tipe item yang didukung: text (align, size, style), qr, image (base64), line (single/double/dotted), feed
- Tambah tab "Receipt" di web UI — form builder interaktif dengan contoh payload JSON lengkap
- Tambah tab "Dokumentasi" di web UI — spek API lengkap semua endpoint + contoh implementasi JavaScript
- Tambah fitur upload gambar di tab Receipt yang otomatis menyisipkan base64 ke payload JSON
- Tambah contoh gambar base64 valid di sampel payload Receipt
- Fix error "bad base-64" pada contoh image di payload Receipt

## 25/05/2026 - 10:50
- Tambah fitur Scan & Pairing perangkat Bluetooth baru langsung dari aplikasi
- Tambah fitur Unpair/Hapus perangkat yang sudah pernah dipasangkan
- Fix feed line berlebihan di Tes Cetak (sekarang cetak teks apa adanya)
- Fix printer "not available" di Chrome Print Dialog (tambah PrinterCapabilitiesInfo)
- Tambah menu Bridge Print di sidebar (HTTP server lokal untuk cetak dari website)
- Implementasi endpoint POST /print (logo + teks) dengan logo otomatis rata tengah
- Implementasi endpoint POST /print/image (cetak gambar penuh)
- Implementasi endpoint GET /status (cek koneksi printer)
- Tambah halaman web UI di localhost:{port} dengan tab Tes Cetak, Tes Gambar, dan Contoh Kode
- Status koneksi printer di halaman web auto-refresh setiap 3 detik
- Port server bisa dikustomisasi dan tersimpan otomatis
- Sinkronisasi status server (running/stopped) dengan SharedPreferences agar konsisten saat app dibuka ulang
- Optimasi decode gambar besar dengan inSampleSize (mencegah OutOfMemory)
- Limit tinggi gambar maksimum 2400 dot agar tidak cetak terlalu panjang
- Resize gambar di sisi web (JavaScript) sebelum kirim ke server (max 800px lebar)
