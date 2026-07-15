<div align="center">
  <img src="images/banner.png" alt="DPThermal Banner" width="100%">
</div>

# DPThermal - Bluetooth Thermal Printer App & HTTP Bridge

**DPThermal** is a versatile and robust Android application designed to bridge the gap between Point of Sale (POS) systems, web applications, and Bluetooth thermal receipt printers. 

With its elegant Orange-themed Material Design interface and built-in HTTP server, printing receipts has never been easier!

## 🚀 Key Features & What It Is Used For

1. **Bluetooth Thermal Printer Manager**
   - Easily pair, connect, and manage your ESC/POS Bluetooth thermal printers.
   - Automatically remembers and reconnects to your last used printer.
   
2. **Local HTTP Print Bridge (Web POS Integration)**
   - Start a local HTTP server directly on your Android device (e.g., `http://127.0.0.1:8080/print`).
   - Seamlessly print receipts from Web-based POS systems (like PHP, React, Vue, etc.) running on Chrome/Android browsers without needing native app wrappers!
   - Send raw JSON payloads containing text and base64 images directly to the printer.

3. **Android Print Spooler Support**
   - Works natively as an Android Print Service!
   - Print any PDF document, webpage, or image directly from other apps using the native Android "Print" menu.

4. **Multi-Language & Customization**
   - Supports dual languages: **English** and **Indonesian**.
   - Configurable paper widths (58mm & 80mm).
   - Auto Cutter & Auto Cash Drawer kick support.
   - Legacy text encoding (Codepage 437/852) for older Chinese printers.

## 📥 Download APK

You can download the latest, ready-to-install `.apk` file directly from the GitHub Releases page.

👉 **[Download Latest APK Here](../../releases/latest)**

*(Simply download the file to your Android device, allow installation from unknown sources, and install!)*

## 🛠️ How to Use

### 1. Connecting to a Printer
1. Open the **Connection Settings** from the sidebar.
2. Turn on your Android Bluetooth and Location services.
3. Tap **Start Scan** to find your thermal printer (or select from Paired Devices).
4. Tap the printer name to connect. Once the status shows "Connected", you are ready to print!

### 2. Printing via Android Spooler (PDF / Webpages)
1. Go to your Android Settings ➔ Print ➔ Print Services.
2. Enable **DPThermal**.
3. Open any PDF or Webpage in Chrome, tap **Share ➔ Print**.
4. Select **DPThermal** as your destination printer.

### 3. Printing via HTTP Bridge (For Web POS Developers)
1. Open the **Bridge Print** menu in the DPThermal app and click **Start Server**.
2. From your web application running on the same device, make an AJAX `POST` request to the provided local URL (e.g., `http://127.0.0.1:8080/print`).
3. Payload format:
```json
{
  "text": "Your receipt text here...",
  "logo": "data:image/png;base64,iVBORw0KGgoAAA..." // Optional Base64 Image
}
```
*Note: You can view the live API documentation by clicking the URL link directly inside the app while the server is running.*

### 4. Viewing Print Logs
- All print jobs sent via the HTTP Bridge are recorded in the **Print Logs** menu.
- You can preview the receipt layout visually before printing.
- Failed or successful jobs can easily be **Reprinted** with a single click.

## 💻 Tech Stack
- **Language:** Java
- **UI Framework:** Android Material Components (Day/Night modes)
- **Local Server:** NanoHTTPD
- **Printer Protocol:** ESC/POS Commands

## 📄 License
This project is open-source. Feel free to fork, modify, and contribute!
