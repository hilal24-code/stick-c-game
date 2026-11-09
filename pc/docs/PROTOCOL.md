# Protokol GamepadBridge v1

Transport: Bluetooth Classic RFCOMM (aliran byte). Semua nilai multi-byte **little-endian**.
Karena RFCOMM adalah stream, penerima wajib memakai parser yang sinkron ulang ke header (lihat `PacketStreamParser`).

## Paket input (HP -> PC), 15 byte

| Offset | Ukuran | Isi |
|---|---|---|
| 0 | 1 | Header `0xAA` |
| 1 | 1 | Nomor urut (0-255, wrap). Penerima membuang paket yang tidak lebih baru (`SequenceComparer.IsNewer`) |
| 2 | 2 | Bitmask tombol (uint16) |
| 4 | 1 | Trigger kiri 0-255 |
| 5 | 1 | Trigger kanan 0-255 |
| 6 | 2 | Analog kiri X (int16) |
| 8 | 2 | Analog kiri Y (int16, positif = atas) |
| 10 | 2 | Analog kanan X (int16) |
| 12 | 2 | Analog kanan Y (int16, positif = atas) |
| 14 | 1 | CRC-8 (poly 0x07, init 0x00) atas byte 0-13 |

## Bitmask tombol

| Bit | Tombol Xbox | Padanan PlayStation |
|---|---|---|
| 0 | A | Cross (X) |
| 1 | B | Circle |
| 2 | X | Square |
| 3 | Y | Triangle |
| 4 | LB | L1 |
| 5 | RB | R1 |
| 6 | L3 | L3 |
| 7 | R3 | R3 |
| 8 | Start | Start |
| 9 | Back | Select |
| 10 | Guide | PS |
| 11 | D-pad atas | |
| 12 | D-pad bawah | |
| 13 | D-pad kiri | |
| 14 | D-pad kanan | |
| 15 | dicadangkan (kirim 0) | |

L2/R2 dikirim sebagai trigger analog (byte 4-5), bukan bit tombol.

## Paket feedback rumble (PC -> HP), 5 byte

| Offset | Ukuran | Isi |
|---|---|---|
| 0 | 1 | Header `0x55` |
| 1 | 1 | Nomor urut |
| 2 | 1 | Motor besar 0-255 |
| 3 | 1 | Motor kecil 0-255 |
| 4 | 1 | CRC-8 atas byte 0-3 |

## Aturan pengirim (HP)
- Kirim saat state berubah, dan sebagai heartbeat minimal tiap 100 ms saat idle.
- Naikkan nomor urut tiap paket.

## Aturan penerima (PC)
- Jika tidak ada paket valid selama 500 ms: reset controller virtual ke netral (semua tombol lepas, analog 0).
- Paket dengan CRC salah dibuang tanpa menghentikan koneksi.

## Rencana v2 (belum diimplementasi)
Paket handshake (versi protokol, nama perangkat, mode layout PS/Xbox/DS4), data gyro, dan paket konfigurasi.
