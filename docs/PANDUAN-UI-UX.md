# Panduan UI/UX SIAP — MA Nurul Islam

Standar tampilan agar semua halaman terlihat seperti satu aplikasi:
bersih, konsisten, dan mudah dipakai Staf TU maupun Guru.

## 1. Prinsip

1. Satu layar, satu tujuan — tiap halaman punya aksi utama yang jelas.
2. Bahasa Indonesia untuk semua teks pengguna.
3. Label tombol jujur: tulis sesuai halaman/aksi yang dibuka
   (contoh: "Lihat Laporan", bukan "Export Laporan" untuk tombol navigasi).
4. Aksi destruktif (hapus, tolak, nonaktifkan, logout) selalu pakai
   dialog konfirmasi dan tombol `btn-danger`.
5. Jangan tampilkan databohong: tanpa badge/persen dummy, tanpa tanggal basi.

## 2. Token warna (`style.css`)

| Token           | Nilai     | Pakai untuk                              |
|-----------------|-----------|------------------------------------------|
| Primary         | `#006a65` | Tombol utama, ikon brand, menu aktif     |
| Primary hover   | `#20b2aa` | Hover tombol utama                       |
| Latar layar     | `#f8f9fb` | `dashboard-root`, `dashboard-scroll`     |
| Kartu/sidebar   | putih     | `card`, `stat-card`, `sidebar`           |
| Border          | `#e1e2e4` | Input, tabel, pemisah                    |
| Teks utama      | `#191c1e` | Judul, angka, isi tabel                  |
| Teks sekunder   | `#6b7076` | Caption, subtitle, placeholder           |
| Danger          | `#d64545` | Tombol hapus/tolak + `error-label`       |

Aksen kartu statistik: hijau `#e0f6f4`, teal `#e6f5f4`,
oranye `#ffe9df`, merah `#ffdad6`.

## 3. Tipografi & layout

- Judul halaman: `topbar-title` (24px bold) + caption `greeting-caption`.
- Judul kartu: `card-title`, subjudul: `card-subtitle`.
- Angka statistik: `stat-value` (28px bold), label: `stat-label`, keterangan: `stat-caption`.
- Sidebar lebar 240; brand "SIAP" + sub "Sistem Informasi Absensi Pelajar" (wrap).
- Isi halaman: padding `24 32 32 32`; jarak antar-seksi 20, antar-kartu 14.
- Topbar kanan: chip tanggal + nama/peran + avatar lingkaran 40px.
  Avatar diklik membuka menu **Profil Saya | Logout** (`ProfileMenu`).
  Belum login/loading: nama "Memuat…" dan role kosong.

## 4. Komponen baku

- Tombol: `btn-primary` (aksi utama, 1 per baris aksi), `btn-secondary`
  (pendamping: Muat Ulang, Batal, Kembali), `btn-danger` (hapus/tolak/logout).
- Input: `text-field-rounded`, `combo-rounded` + `promptText` Bahasa Indonesia.
- Tabel: `tabel-akun`, selalu ada `Muat Ulang` + kolom pencarian + teks kosong:
  "Belum ada …" (data master) atau "Tidak ada … pada filter ini." (laporan).
- Tab filter: `filter-tab`, tab aktif + `filter-tab-active` (atur via kode).
- Pesan error: `error-label` merah + kalimat arahan ("… Coba lagi.",
  "Ubah filter terlebih dahulu."). Sukses: dialog informasi, bukan label merah.

## 5. Format data (tampilan)

- Tanggal: `dd MMM yyyy` (contoh: 22 Sep 2026).
- Jam: `HH:mm`. Sapaan/tanggal panjang: `d MMMM yyyy` locale `id-ID`.
- Input jam manual tetap memakai `HH.mm` sesuai hint di dialognya.
- DatePicker selalu lewat `TanggalUtil` (`dd-MM-yyyy`, prompt `tt-bb-tttt`).
- ALFA otomatis: murid AKTIF tanpa catatan → ALFA `otomatis=TRUE` (harian saat
  laporan dibuka; mapel saat laporan dibuka untuk sesi yang sudah lewat dan
  cocok harinya). Scan susulan meng-upgrade baris otomatis; baris manual tidak.
- Laporan guru read-only: tanpa kolom Aksi (Ubah/Hapus hanya di TU).

## 6. Polish global (otomatis via `style.css`)

- Tombol nonaktif pudar (`:disabled`), input fokus bergaris teal.
- Tabel `tabel-akun`: header terang, hover baris, seleksi teal, teks tetap gelap.
- Scrollbar ramping; menu popup avatar + tooltip + dialog konfirmasi mengikuti tema.
- Ikon kartu statistik: vektor `SVGPath` (Material-style, 24 unit) dengan
  `fill` mengikuti warna kartu (teal `#006a65`, oranye `#a43c12`,
  merah `#ba1a1a`) — dilarang memakai glyph font/emoji agar tajam dan
  selalu center di kotak 34px.
- Aksi per-baris tabel: `tabel-btn-primer` (soft teal) / `tabel-btn-danger`
  (soft merah) — tanpa style inline, tanpa merah solid menyala.
- Kartu pelajar (`KartuPelajarView`): template resmi `images/kartu-depan.png`
  (815x980) & `kartu-belakang.png` (730x980) dipakai apa adanya; kode hanya
  menempel foto + data + QR pada koordinat pas. `logo-ma.png` cadangan.

## 7. Halaman baru (checklist)

- [ ] Ikut shell sidebar + topbar standar (avatar `avatarBox` + `imgAvatar`).
- [ ] Daftarkan di `SceneManager` dan pasang `ProfileMenu` + `AvatarUtil`.
- [ ] Guard role TU/Guru dengan dialog "Akses Ditolak" yang ramah.
- [ ] Semua `fx:id` di FXML ada pasangannya di controller (cek: `check_all_fxml.py`).
- [ ] Teks Indonesia, tombol jujur, konfirmasi untuk aksi destruktif.
