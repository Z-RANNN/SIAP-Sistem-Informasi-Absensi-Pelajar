package com.nurulislam.siap.model;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Representasi baris tb_absensi_mapel (absensi PER MATA PELAJARAN, dicatat Guru).
 * <p>
 * Field {@code namaMurid} dan {@code nis} bersifat transient, diisi oleh
 * AbsensiMapelDAO lewat JOIN ke tb_murid untuk keperluan tampilan tabel
 * "Absen Masuk" pada halaman Absensi Mata Pelajaran.
 * <p>
 * Field {@code namaKelas}, {@code namaMapel}, dan {@code namaGuru} juga
 * bersifat transient, diisi lewat JOIN tambahan ke tb_jadwal_mengajar /
 * tb_kelas / tb_mata_pelajaran / tb_pengguna, khusus dipakai oleh query
 * rekap/laporan lintas kelas (halaman Laporan Kehadiran - Staf TU) supaya
 * Admin bisa melihat semua hasil absensi mata pelajaran yang dicatat Guru,
 * bukan hanya guru yang bersangkutan.
 */
public class AbsensiMapel {

    private int absensiMapelId;
    private int muridId;
    private int jadwalId;
    private int penggunaId;
    private LocalDate tanggal;
    private LocalTime waktuScan;
    private StatusAbsensi status;

    private String namaMurid;
    private String nis;
    private String namaKelas;
    private String namaMapel;
    private String namaGuru;
    private String statusMurid;
    /**
     * TRUE bila baris ini dibuat otomatis oleh sistem (ALFA otomatis untuk
     * murid yang belum absen sampai sesi selesai). Boleh di-upgrade scan susulan.
     */
    private boolean otomatis;

    public AbsensiMapel() {
    }

    public int getAbsensiMapelId() {
        return absensiMapelId;
    }

    public void setAbsensiMapelId(int absensiMapelId) {
        this.absensiMapelId = absensiMapelId;
    }

    public int getMuridId() {
        return muridId;
    }

    public void setMuridId(int muridId) {
        this.muridId = muridId;
    }

    public int getJadwalId() {
        return jadwalId;
    }

    public void setJadwalId(int jadwalId) {
        this.jadwalId = jadwalId;
    }

    public int getPenggunaId() {
        return penggunaId;
    }

    public void setPenggunaId(int penggunaId) {
        this.penggunaId = penggunaId;
    }

    public LocalDate getTanggal() {
        return tanggal;
    }

    public void setTanggal(LocalDate tanggal) {
        this.tanggal = tanggal;
    }

    public LocalTime getWaktuScan() {
        return waktuScan;
    }

    public void setWaktuScan(LocalTime waktuScan) {
        this.waktuScan = waktuScan;
    }

    public StatusAbsensi getStatus() {
        return status;
    }

    public void setStatus(StatusAbsensi status) {
        this.status = status;
    }

    public String getNamaMurid() {
        return namaMurid;
    }

    public void setNamaMurid(String namaMurid) {
        this.namaMurid = namaMurid;
    }

    public String getNis() {
        return nis;
    }

    public void setNis(String nis) {
        this.nis = nis;
    }

    public String getNamaKelas() {
        return namaKelas;
    }

    public void setNamaKelas(String namaKelas) {
        this.namaKelas = namaKelas;
    }

    public String getNamaMapel() {
        return namaMapel;
    }

    public void setNamaMapel(String namaMapel) {
        this.namaMapel = namaMapel;
    }

    public String getNamaGuru() {
        return namaGuru;
    }

    public void setNamaGuru(String namaGuru) {
        this.namaGuru = namaGuru;
    }

    public String getStatusMurid() {
        return statusMurid;
    }

    public void setStatusMurid(String statusMurid) {
        this.statusMurid = statusMurid;
    }

    public boolean isOtomatis() {
        return otomatis;
    }

    public void setOtomatis(boolean otomatis) {
        this.otomatis = otomatis;
    }
}