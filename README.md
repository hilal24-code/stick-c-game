# GamepadBridge (stick-c-game)

Ubah HP Android jadi **gamepad virtual untuk PC Windows lewat Bluetooth**. PC mengenali HP sebagai controller **Xbox 360** sungguhan, jadi bisa dipakai di game yang mendukung controller tanpa perlu controller fisik.

Proyek ini terdiri dari dua program yang saling berkomunikasi:

```
[HP Android: aplikasi gamepad] --Bluetooth RFCOMM--> [PC: aplikasi GamepadBridge] --> ViGEmBus --> Game
```

## Fitur

**Aplikasi Android**
- Tata letak **PlayStation** (▲ ● ✕ ■) atau **Xbox** (Y B A X) yang bisa ditukar
- D-pad, dua joystick analog dengan deadzone, L1/L2/R1/R2, L3/R3, Select/Start, tombol Guide
- **Multitouch**: bisa menekan beberapa tombol sambil menggerakkan analog
- **Mode atur**: geser kontrol dan ubah ukuran tombol serta joystick, tersimpan per layout
- **Latar belakang gambar** dari galeri dengan opasitas yang bisa diatur
- Getaran HP saat game di PC mengirim rumble
- Mengingat perangkat terakhir yang dipakai

**Aplikasi PC (Windows)**
- Jendela status dengan tampilan input langsung (analog, trigger, tombol) dan log
- **Ikon tray**, tanpa jendela terminal
- Opsi **jalan otomatis saat Windows menyala** (langsung bersembunyi di tray)
- Mencoba lagi sendiri kalau Bluetooth belum siap saat boot
- Controller virtual otomatis kembali ke netral kalau data berhenti lebih dari 500 ms

## Syarat

**PC**
- Windows 10 (versi 2004 ke atas) atau Windows 11, 64-bit
- **Bluetooth** (bawaan laptop, atau adapter USB Bluetooth 4.0+ untuk PC desktop)
- **[ViGEmBus](https://github.com/nefarius/ViGEmBus/releases)** terpasang (driver controller virtual). Perlu hak administrator dan restart
- [.NET 10 Desktop Runtime](https://dotnet.microsoft.com/download/dotnet/10.0), kecuali memakai versi yang sudah membawa .NET sendiri

**HP**
- Android 8.0 (API 26) ke atas
- Sudah **pairing** dengan PC lewat pengaturan Bluetooth Windows

## Cara pakai

1. **Pairing** HP dengan PC: Windows *Settings > Bluetooth & devices > Add device*, lalu pilih PC dari daftar Bluetooth di HP dan konfirmasi kodenya di kedua sisi.
2. Pasang **ViGEmBus** di PC, lalu restart.
3. Jalankan aplikasi PC (`GamepadBridge.exe`). Status berubah jadi **Menunggu HP**. Centang **Jalankan saat Windows menyala** kalau mau otomatis.
4. Buka aplikasi di HP, izinkan akses Bluetooth, lalu ketuk nama PC di daftar.
5. Status di PC berubah jadi **Terhubung**. Buka game atau gamepad tester (misalnya hardwaretester.com/gamepad) dan coba tombolnya.

> Peringatan **"Windows protected your PC"** (SmartScreen) bisa muncul saat pertama membuka aplikasi PC karena aplikasinya belum ditandatangani. Klik *More info > Run anyway*.
>
> Android juga akan meminta izin **instal dari sumber tidak dikenal** untuk file APK di luar Play Store.

## Struktur repo

| Folder | Isi |
|---|---|
| `android/` | Aplikasi Android (Kotlin + Jetpack Compose) |
| `pc/GamepadBridge.Protocol` | Pembuat dan pembaca paket data, CRC, parser aliran byte |
| `pc/GamepadBridge.Core` | Abstraksi controller virtual dan implementasi ViGEm |
| `pc/GamepadBridge.Desktop` | Aplikasi PC (WPF): jendela, tray, layanan Bluetooth |
| `pc/GamepadBridge.Server` | Versi konsol dari server (untuk pengujian dan debugging) |
| `pc/GamepadBridge.Simulator` | Penguji tanpa HP: menggerakkan controller virtual dengan data palsu |
| `pc/GamepadBridge.Protocol.Tests` | Tes unit protokol (xUnit) |
| `pc/docs/` | Spesifikasi protokol dan panduan pengujian |

## Protokol singkat

Transport: Bluetooth Classic RFCOMM. Semua nilai little-endian. Layanan memakai UUID `6f1c2a40-5b7e-4a53-9c1e-3b8d2f4a7c10` (dan Serial Port standar untuk aplikasi uji).

**Paket input (HP → PC), 15 byte**

| Byte | Isi |
|---|---|
| 0 | Header `0xAA` |
| 1 | Nomor urut (0-255, berputar) |
| 2-3 | Bitmask tombol |
| 4 | Trigger kiri (0-255) |
| 5 | Trigger kanan (0-255) |
| 6-13 | Analog kiri X/Y dan kanan X/Y (int16 masing-masing) |
| 14 | CRC-8 (poly 0x07) atas byte 0-13 |

**Paket rumble (PC → HP), 5 byte**: header `0x55`, nomor urut, motor besar, motor kecil, CRC-8.

Spesifikasi lengkap, termasuk bitmask tombol dan aturan pengirim dan penerima, ada di [`pc/docs/PROTOCOL.md`](pc/docs/PROTOCOL.md).

## Build dari source

**PC** (butuh [.NET 10 SDK](https://dotnet.microsoft.com/download/dotnet/10.0))

```
cd pc
dotnet test
dotnet publish GamepadBridge.Desktop -c Release -r win-x64 --self-contained false -o ..\publish\App
```

Untuk versi yang bisa dibawa ke PC lain tanpa memasang .NET, ganti `--self-contained false` dengan `--self-contained true`. ViGEmBus tetap harus dipasang di PC tujuan.

Uji jalur PC tanpa HP:

```
dotnet run --project GamepadBridge.Simulator
```

Lalu buka gamepad tester di browser: tombol dan analog bergerak sendiri.

**Android** (butuh [Android Studio](https://developer.android.com/studio))

1. Buka folder `android/` di Android Studio dan tunggu Gradle sync selesai.
2. Colok HP, nyalakan **USB debugging**, pilih HP di toolbar, lalu klik **Run**.
3. Untuk membuat file APK: *Build > Build Bundle(s) / APK(s) > Build APK(s)*.

## Pemecahan masalah

| Masalah | Yang dicek |
|---|---|
| Daftar perangkat di HP kosong | Nyalakan Bluetooth dan pastikan HP sudah pairing dengan PC |
| Gagal terhubung | Pastikan aplikasi PC berjalan dan statusnya **Menunggu HP**. Coba **Mulai ulang Bluetooth** di aplikasi PC |
| Status PC "Ada masalah" | ViGEmBus belum terpasang atau PC belum direstart setelah memasangnya |
| Controller tidak terdeteksi di game | Pastikan game mendukung controller Xbox (XInput). Cek dulu di gamepad tester |
| Tombol kembali netral sendiri | Itu pengaman: data berhenti lebih dari 500 ms. Aplikasi HP mengirim sinyal secara berkala selama tersambung |

## Batasan saat ini

- **Hanya Windows** di sisi PC. macOS dan Linux tidak didukung (ViGEmBus dan API Bluetooth yang dipakai khusus Windows).
- **Hanya Android** di sisi HP. iPhone tidak didukung.
- Satu HP dalam satu waktu. Kalau HP kedua tersambung, yang pertama diputus.
- Controller yang dikenali adalah **Xbox 360**. Mode DualShock 4 dan gyro belum ada.
- Baru diuji di **Realme 13** dengan laptop **ASUS TUF** dan gamepad tester. Belum diuji luas di berbagai game dan perangkat. Laporan hasil di perangkat lain sangat membantu lewat Issues.
- Latensi belum diukur dan belum dioptimalkan. Sebagian jeda berasal dari Bluetooth Classic itu sendiri.
- Proyek ViGEmBus sudah **tidak dikembangkan lagi** oleh pembuatnya (repo diarsipkan). Driver ini masih berfungsi di Windows 10/11, tetapi kelanjutannya tidak terjamin. Kode ini memisahkan lapisan controller virtual (`IVirtualController`) supaya backend bisa diganti nanti.

## Rencana

- Mode DualShock 4 dan gyro
- Pengukuran dan optimasi latensi
- Rilis siap pakai (zip Windows dan APK) lewat GitHub Releases

## Terima kasih

- [ViGEmBus](https://github.com/nefarius/ViGEmBus) dan [ViGEm.Client](https://github.com/nefarius/ViGEm.NET) oleh Nefarius, untuk controller virtual

## Lisensi

Belum ditentukan. Tambahkan file `LICENSE` sebelum orang lain memakai atau mengubah kode ini.
