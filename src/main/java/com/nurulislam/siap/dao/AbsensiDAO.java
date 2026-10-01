package com.nurulislam.siap.dao;

import com.nurulislam.siap.model.Absensi;
import com.nurulislam.siap.model.StatusAbsensi;
import com.nurulislam.siap.util.DatabaseConnection;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Time;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Data Access Object untuk tabel tb_absensi (absensi harian di gerbang,
 * hasil Scan QR). Query SELECT JOIN ke tb_murid & tb_kelas agar nama murid
 * dan nama kelas siap dipakai langsung oleh layar Scan QR.
 */
public class AbsensiDAO {

    private static final String SELECT_JOIN =
            "SELECT a.*, m.nama AS nama_murid, m.nis AS nis, k.nama_kelas AS nama_kelas, m.status AS status_murid "
                    + "FROM tb_absensi a "
                    + "JOIN tb_murid m ON a.murid_id = m.murid_id "
                    + "JOIN tb_kelas k ON a.kelas_id = k.kelas_id ";

    /**
     * Menyimpan satu baris absensi hasil scan QR.
     *
     * @throws AbsensiSudahAdaException jika murid tersebut sudah absen pada sesi & tanggal yang sama
     */
    public Absensi insert(Absensi absensi) throws SQLException, AbsensiSudahAdaException {
        String sql = "INSERT INTO tb_absensi (murid_id, kelas_id, sesi_absensi_id, pengguna_id, tanggal, waktu_masuk, status) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, absensi.getMuridId());
            ps.setInt(2, absensi.getKelasId());
            ps.setInt(3, absensi.getSesiAbsensiId());
            ps.setInt(4, absensi.getPenggunaId());
            ps.setDate(5, Date.valueOf(absensi.getTanggal()));
            ps.setTime(6, Time.valueOf(absensi.getWaktuMasuk()));
            ps.setString(7, absensi.getStatus().name());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    absensi.setAbsensiId(keys.getInt(1));
                }
            }
        } catch (SQLIntegrityConstraintViolationException e) {
            throw new AbsensiSudahAdaException("Murid ini sudah tercatat absen untuk sesi hari ini.");
        }
        return absensi;
    }

    /** Riwayat scan hari ini, terbaru di atas. Dipakai panel "Riwayat Scan Hari Ini". */    public List<Absensi> findRiwayatHariIni(java.time.LocalDate tanggal) throws SQLException {
        String sql = SELECT_JOIN + "WHERE a.tanggal = ? ORDER BY a.waktu_masuk DESC, a.absensi_id DESC";
        List<Absensi> result = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(tanggal));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        }
        return result;
    }

    /** Rekap jumlah per status hari ini, dipakai kartu "Rekap hari ini". */
    public Map<StatusAbsensi, Integer> hitungRekapHariIni(java.time.LocalDate tanggal) throws SQLException {
        Map<StatusAbsensi, Integer> rekap = new EnumMap<>(StatusAbsensi.class);
        for (StatusAbsensi s : StatusAbsensi.values()) {
            rekap.put(s, 0);
        }
        String sql = "SELECT status, COUNT(*) AS jumlah FROM tb_absensi WHERE tanggal = ? GROUP BY status";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(tanggal));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rekap.put(StatusAbsensi.valueOf(rs.getString("status")), rs.getInt("jumlah"));
                }
            }
        }
        return rekap;
    }

    /**
     * Status absensi harian (gerbang) seorang murid pada tanggal tertentu.
     * Dipakai halaman Absensi Mata Pelajaran sebagai kunci anti titip absen:
     * bila murid berstatus ALFA/IZIN/SAKIT hari itu, scan mapel otomatis
     * mengikuti status tersebut (bukan HADIR).
     */
    public Optional<StatusAbsensi> findStatusHarian(int muridId, java.time.LocalDate tanggal)
            throws SQLException {
        String sql = "SELECT status FROM tb_absensi WHERE murid_id = ? AND tanggal = ? LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, muridId);
            ps.setDate(2, Date.valueOf(tanggal));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(StatusAbsensi.valueOf(rs.getString("status")));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Riwayat absensi dalam rentang tanggal, dengan filter opsional kelas, status,
     * dan kata kunci (nama murid/NIS). Dipakai halaman "Laporan & Rekap Kehadiran".
     * Parameter kelasId/status/kataKunci boleh null yang berarti filter itu tidak
     * diterapkan (mis. "semua kelas", "semua status", tanpa pencarian).
     */
    public List<Absensi> findFiltered(java.time.LocalDate tglMulai, java.time.LocalDate tglAkhir,
                                       Integer kelasId, StatusAbsensi statusDipilih, String kataKunci) throws SQLException {
        StringBuilder sql = new StringBuilder(SELECT_JOIN);
        sql.append("WHERE a.tanggal BETWEEN ? AND ? ");
        if (kelasId != null) {
            sql.append("AND a.kelas_id = ? ");
        }
        if (statusDipilih != null) {
            sql.append("AND a.status = ? ");
        }
        if (kataKunci != null && !kataKunci.isBlank()) {
            sql.append("AND (m.nama LIKE ? OR m.nis LIKE ?) ");
        }
        sql.append("ORDER BY a.tanggal DESC, a.waktu_masuk DESC");

        List<Absensi> result = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            ps.setDate(idx++, Date.valueOf(tglMulai));
            ps.setDate(idx++, Date.valueOf(tglAkhir));
            if (kelasId != null) {
                ps.setInt(idx++, kelasId);
            }
            if (statusDipilih != null) {
                ps.setString(idx++, statusDipilih.name());
            }
            if (kataKunci != null && !kataKunci.isBlank()) {
                String pola = "%" + kataKunci.trim() + "%";
                ps.setString(idx++, pola);
                ps.setString(idx++, pola);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        }
        return result;
    }

    /** Rekap jumlah per status dalam rentang tanggal (+ filter kelas opsional), untuk kartu ringkasan Laporan. */
    public Map<StatusAbsensi, Integer> hitungRekap(java.time.LocalDate tglMulai, java.time.LocalDate tglAkhir,
                                                     Integer kelasId) throws SQLException {
        Map<StatusAbsensi, Integer> rekap = new EnumMap<>(StatusAbsensi.class);
        for (StatusAbsensi s : StatusAbsensi.values()) {
            rekap.put(s, 0);
        }
        StringBuilder sql = new StringBuilder(
                "SELECT status, COUNT(*) AS jumlah FROM tb_absensi WHERE tanggal BETWEEN ? AND ? ");
        if (kelasId != null) {
            sql.append("AND kelas_id = ? ");
        }
        sql.append("GROUP BY status");

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            ps.setDate(idx++, Date.valueOf(tglMulai));
            ps.setDate(idx++, Date.valueOf(tglAkhir));
            if (kelasId != null) {
                ps.setInt(idx++, kelasId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rekap.put(StatusAbsensi.valueOf(rs.getString("status")), rs.getInt("jumlah"));
                }
            }
        }
        return rekap;
    }

    /** Memperbarui status/waktu absensi dari halaman laporan. */
    public void updateFromLaporan(Absensi absensi, int penggunaId) throws SQLException {
        String sql = "UPDATE tb_absensi SET waktu_masuk = ?, status = ?, pengguna_id = ? WHERE absensi_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (absensi.getWaktuMasuk() == null) {
                ps.setNull(1, java.sql.Types.TIME);
            } else {
                ps.setTime(1, Time.valueOf(absensi.getWaktuMasuk()));
            }
            ps.setString(2, absensi.getStatus().name());
            ps.setInt(3, penggunaId);
            ps.setInt(4, absensi.getAbsensiId());
            ps.executeUpdate();
        }
    }

    /** Menghapus satu record absensi dari halaman laporan. */
    public void deleteById(int absensiId) throws SQLException {
        String sql = "DELETE FROM tb_absensi WHERE absensi_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, absensiId);
            ps.executeUpdate();
        }
    }

    private Absensi mapRow(ResultSet rs) throws SQLException {
        Absensi a = new Absensi();
        a.setAbsensiId(rs.getInt("absensi_id"));
        a.setMuridId(rs.getInt("murid_id"));
        a.setKelasId(rs.getInt("kelas_id"));
        a.setSesiAbsensiId(rs.getInt("sesi_absensi_id"));
        a.setPenggunaId(rs.getInt("pengguna_id"));
        a.setTanggal(rs.getDate("tanggal").toLocalDate());
        a.setWaktuMasuk(rs.getTime("waktu_masuk") != null ? rs.getTime("waktu_masuk").toLocalTime() : null);
        a.setStatus(StatusAbsensi.valueOf(rs.getString("status")));
        a.setNamaMurid(rs.getString("nama_murid"));
        a.setNis(rs.getString("nis"));
        a.setNamaKelas(rs.getString("nama_kelas"));
        a.setStatusMurid(rs.getString("status_murid"));
        return a;
    }
}