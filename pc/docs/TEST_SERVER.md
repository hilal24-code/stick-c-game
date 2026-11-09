# Uji server Bluetooth dengan Serial Bluetooth Terminal

1. PC: `dotnet run --project GamepadBridge.Server` (biarkan terbuka)
2. HP: buka Serial Bluetooth Terminal > menu (tiga garis) > Devices > tab "Bluetooth classic" > pilih nama PC.
   Catatan: aplikasi ini default mencari layanan serial standar. Jika gagal terhubung, buka Settings > Bluetooth settings di aplikasi tsb. dan isi UUID custom: 6f1c2a40-5b7e-4a53-9c1e-3b8d2f4a7c10
3. Ubah mode kirim ke HEX (Settings > Send: HEX), lalu kirim paket berikut SATU PER SATU, berurutan (nomor urut harus naik):

1. Netral (semua lepas)
   AA 01 00 00 00 00 00 00 00 00 00 00 00 00 49
2. Tombol A ditekan
   AA 02 01 00 00 00 00 00 00 00 00 00 00 00 AF
3. Tombol A + B ditekan
   AA 03 03 00 00 00 00 00 00 00 00 00 00 00 81
4. Analog kiri penuh ke kanan
   AA 04 00 00 00 00 FF 7F 00 00 00 00 00 00 96
5. Semua dilepas
   AA 05 00 00 00 00 00 00 00 00 00 00 00 00 17

4. Amati di hardwaretester.com/gamepad. Paket valid memicu tombol/analog bergerak.
