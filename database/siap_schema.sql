-- =========================================================================
-- SIAP - Sistem Informasi Absensi Pelajar MA Nurul Islam
-- Skema database sesuai ERD pada Proposal Tugas Akhir (Bab III, Gambar 3.4)
-- Dibuat oleh: Muhammad Rafik - 24302037 - D3 Teknik Informatika Politeknik Hasnur
-- =========================================================================

CREATE DATABASE IF NOT EXISTS db_siap_nurul_islam
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE db_siap_nurul_islam;

-- -------------------------------------------------------------------------
-- 1. tb_pengguna
--    Gabungan aktor Staf TU dan Guru Mata Pelajaran, dibedakan lewat kolom role
-- -------------------------------------------------------------------------
CREATE TABLE tb_pengguna (
    pengguna_id     INT AUTO_INCREMENT PRIMARY KEY,
    email           VARCHAR(100) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    nama            VARCHAR(100) NOT NULL,
    role            ENUM('TU', 'GURU') NOT NULL,
    status_akun     ENUM('MENUNGGU_VERIFIKASI', 'AKTIF', 'NONAKTIF') NOT NULL DEFAULT 'MENUNGGU_VERIFIKASI',
    foto            VARCHAR(255),                  -- path file foto profil (diatur lewat halaman Profil Saya)
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- -------------------------------------------------------------------------
-- 2. tb_tahun_ajaran
-- -------------------------------------------------------------------------
CREATE TABLE tb_tahun_ajaran (
    tahun_ajaran_id INT AUTO_INCREMENT PRIMARY KEY,
    tahun           VARCHAR(9)  NOT NULL,          -- contoh: 2025/2026
    semester        ENUM('GANJIL', 'GENAP') NOT NULL,
    status_aktif    BOOLEAN NOT NULL DEFAULT FALSE
) ENGINE=InnoDB;

-- -------------------------------------------------------------------------
-- 3. tb_kelas
-- -------------------------------------------------------------------------
CREATE TABLE tb_kelas (
    kelas_id        INT AUTO_INCREMENT PRIMARY KEY,
    nama_kelas      VARCHAR(50) NOT NULL,
    tingkat         VARCHAR(10) NOT NULL,          -- contoh: X, XI, XII
    jurusan         VARCHAR(50),
    wali_kelas_id   INT NULL,
    tahun_ajaran_id INT NOT NULL,
    CONSTRAINT fk_kelas_wali FOREIGN KEY (wali_kelas_id) REFERENCES tb_pengguna(pengguna_id) ON DELETE SET NULL,
    CONSTRAINT fk_kelas_tahun_ajaran FOREIGN KEY (tahun_ajaran_id) REFERENCES tb_tahun_ajaran(tahun_ajaran_id) ON DELETE RESTRICT
) ENGINE=InnoDB;

-- -------------------------------------------------------------------------
-- 4. tb_murid
--    qr_token: token acak tersembunyi (bukan hanya NIS) sebagai lapisan
--    keamanan tambahan pada QR Code, sesuai batasan masalah di proposal.
-- -------------------------------------------------------------------------
CREATE TABLE tb_murid (
    murid_id        INT AUTO_INCREMENT PRIMARY KEY,
    nis             VARCHAR(20) NOT NULL UNIQUE,
    nama            VARCHAR(100) NOT NULL,
    jenis_kelamin   ENUM('L', 'P') NOT NULL,
    tanggal_lahir   DATE,
    foto            VARCHAR(255),                  -- path/nama file foto
    kelas_id        INT NOT NULL,
    qr_token        VARCHAR(64) NOT NULL UNIQUE,
    status          ENUM('AKTIF', 'PINDAH', 'LULUS', 'NONAKTIF') NOT NULL DEFAULT 'AKTIF',
    CONSTRAINT fk_murid_kelas FOREIGN KEY (kelas_id) REFERENCES tb_kelas(kelas_id) ON DELETE RESTRICT
) ENGINE=InnoDB;

-- -------------------------------------------------------------------------
-- 5. tb_sesi_absensi (sesi absensi harian, misal: Sesi Pagi)
-- -------------------------------------------------------------------------
CREATE TABLE tb_sesi_absensi (
    sesi_absensi_id INT AUTO_INCREMENT PRIMARY KEY,
    nama_sesi       VARCHAR(50) NOT NULL,
    jam_masuk       TIME NOT NULL,
    batas_terlambat TIME NOT NULL
) ENGINE=InnoDB;

-- -------------------------------------------------------------------------
-- 6. tb_jabatan_kelas (tabel penghubung murid-kelas per tahun ajaran)
-- -------------------------------------------------------------------------
CREATE TABLE tb_jabatan_kelas (
    jabatan_id      INT AUTO_INCREMENT PRIMARY KEY,
    murid_id        INT NOT NULL,
    kelas_id        INT NOT NULL,
    jabatan         VARCHAR(50) NOT NULL,           -- contoh: Ketua Kelas
    tahun_ajaran_id INT NOT NULL,
    CONSTRAINT fk_jabatan_murid FOREIGN KEY (murid_id) REFERENCES tb_murid(murid_id) ON DELETE CASCADE,
    CONSTRAINT fk_jabatan_kelas FOREIGN KEY (kelas_id) REFERENCES tb_kelas(kelas_id) ON DELETE CASCADE,
    CONSTRAINT fk_jabatan_tahun_ajaran FOREIGN KEY (tahun_ajaran_id) REFERENCES tb_tahun_ajaran(tahun_ajaran_id) ON DELETE RESTRICT
) ENGINE=InnoDB;

-- -------------------------------------------------------------------------
-- 7. tb_absensi (absensi HARIAN - gerbang sekolah, dicatat oleh Staf TU)
-- -------------------------------------------------------------------------
CREATE TABLE tb_absensi (
    absensi_id      INT AUTO_INCREMENT PRIMARY KEY,
    murid_id        INT NOT NULL,
    kelas_id        INT NOT NULL,
    sesi_absensi_id INT NOT NULL,
    pengguna_id     INT NOT NULL,                   -- Staf TU yang men-scan
    tanggal         DATE NOT NULL,
    waktu_masuk     TIME,
    status          ENUM('HADIR', 'TERLAMBAT', 'IZIN', 'SAKIT', 'ALFA') NOT NULL,
    otomatis        BOOLEAN NOT NULL DEFAULT FALSE, -- TRUE = ALFA buatan sistem (bisa di-upgrade scan susulan)
    CONSTRAINT fk_absensi_murid FOREIGN KEY (murid_id) REFERENCES tb_murid(murid_id) ON DELETE CASCADE,
    CONSTRAINT fk_absensi_kelas FOREIGN KEY (kelas_id) REFERENCES tb_kelas(kelas_id) ON DELETE RESTRICT,
    CONSTRAINT fk_absensi_sesi FOREIGN KEY (sesi_absensi_id) REFERENCES tb_sesi_absensi(sesi_absensi_id) ON DELETE RESTRICT,
    CONSTRAINT fk_absensi_pengguna FOREIGN KEY (pengguna_id) REFERENCES tb_pengguna(pengguna_id) ON DELETE RESTRICT,
    UNIQUE KEY uq_absensi_harian (murid_id, sesi_absensi_id, tanggal)
) ENGINE=InnoDB;

-- -------------------------------------------------------------------------
-- 8. tb_reset_password_token
-- -------------------------------------------------------------------------
CREATE TABLE tb_reset_password_token (
    token_id        INT AUTO_INCREMENT PRIMARY KEY,
    pengguna_id     INT NOT NULL,
    token           VARCHAR(255) NOT NULL,
    expired_at      DATETIME NOT NULL,
    CONSTRAINT fk_reset_pengguna FOREIGN KEY (pengguna_id) REFERENCES tb_pengguna(pengguna_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- -------------------------------------------------------------------------
-- 9. tb_mata_pelajaran
-- -------------------------------------------------------------------------
CREATE TABLE tb_mata_pelajaran (
    mapel_id        INT AUTO_INCREMENT PRIMARY KEY,
    kode_mapel      VARCHAR(20) NOT NULL UNIQUE,
    nama_mapel      VARCHAR(100) NOT NULL
) ENGINE=InnoDB;

-- -------------------------------------------------------------------------
-- 10. tb_jadwal_mengajar
-- -------------------------------------------------------------------------
CREATE TABLE tb_jadwal_mengajar (
    jadwal_id       INT AUTO_INCREMENT PRIMARY KEY,
    mapel_id        INT NOT NULL,
    kelas_id        INT NOT NULL,
    guru_id         INT NOT NULL,                   -- FK ke tb_pengguna (role = GURU)
    tahun_ajaran_id INT NOT NULL,
    hari            ENUM('SENIN','SELASA','RABU','KAMIS','JUMAT','SABTU') NOT NULL,
    jam_mulai       TIME NOT NULL,
    jam_selesai     TIME NOT NULL,
    CONSTRAINT fk_jadwal_mapel FOREIGN KEY (mapel_id) REFERENCES tb_mata_pelajaran(mapel_id) ON DELETE RESTRICT,
    CONSTRAINT fk_jadwal_kelas FOREIGN KEY (kelas_id) REFERENCES tb_kelas(kelas_id) ON DELETE RESTRICT,
    CONSTRAINT fk_jadwal_guru FOREIGN KEY (guru_id) REFERENCES tb_pengguna(pengguna_id) ON DELETE RESTRICT,
    CONSTRAINT fk_jadwal_tahun_ajaran FOREIGN KEY (tahun_ajaran_id) REFERENCES tb_tahun_ajaran(tahun_ajaran_id) ON DELETE RESTRICT
) ENGINE=InnoDB;

-- -------------------------------------------------------------------------
-- 11. tb_absensi_mapel (absensi PER MATA PELAJARAN, dicatat oleh Guru)
-- -------------------------------------------------------------------------
CREATE TABLE tb_absensi_mapel (
    absensi_mapel_id INT AUTO_INCREMENT PRIMARY KEY,
    murid_id         INT NOT NULL,
    jadwal_id        INT NOT NULL,
    pengguna_id      INT NOT NULL,                  -- Guru yang men-scan
    tanggal          DATE NOT NULL,
    waktu_scan       TIME,
    status           ENUM('HADIR', 'TERLAMBAT', 'IZIN', 'SAKIT', 'ALFA') NOT NULL,
    CONSTRAINT fk_absensimapel_murid FOREIGN KEY (murid_id) REFERENCES tb_murid(murid_id) ON DELETE CASCADE,
    CONSTRAINT fk_absensimapel_jadwal FOREIGN KEY (jadwal_id) REFERENCES tb_jadwal_mengajar(jadwal_id) ON DELETE RESTRICT,
    CONSTRAINT fk_absensimapel_pengguna FOREIGN KEY (pengguna_id) REFERENCES tb_pengguna(pengguna_id) ON DELETE RESTRICT,
    UNIQUE KEY uq_absensi_mapel (murid_id, jadwal_id, tanggal)
) ENGINE=InnoDB;

-- -------------------------------------------------------------------------
-- INDEX tambahan untuk mempercepat query laporan/rekap
-- -------------------------------------------------------------------------
CREATE INDEX idx_absensi_tanggal ON tb_absensi(tanggal);
CREATE INDEX idx_absensi_status ON tb_absensi(status);
CREATE INDEX idx_absensimapel_tanggal ON tb_absensi_mapel(tanggal);
CREATE INDEX idx_absensimapel_status ON tb_absensi_mapel(status);
CREATE INDEX idx_murid_kelas ON tb_murid(kelas_id);

-- =========================================================================
-- DATA AWAL (seed) - opsional, memudahkan testing tahap berikutnya
-- =========================================================================

-- Tahun ajaran aktif
INSERT INTO tb_tahun_ajaran (tahun, semester, status_aktif) VALUES ('2025/2026', 'GANJIL', TRUE);

-- Sesi absensi harian
INSERT INTO tb_sesi_absensi (nama_sesi, jam_masuk, batas_terlambat) VALUES ('Sesi Pagi', '07:00:00', '07:15:00');

-- Akun awal Staf TU (password default: "admin123", HASH di bawah wajib
-- di-generate ulang lewat aplikasi/BCrypt saat modul login sudah jadi.
-- Nilai di bawah ini hanya placeholder agar tabel tidak kosong.)
INSERT INTO tb_pengguna (email, password_hash, nama, role, status_akun)
VALUES ('admin@nurulislam.sch.id', 'GANTI_DENGAN_HASH_BCRYPT', 'Staf Tata Usaha', 'TU', 'AKTIF');
