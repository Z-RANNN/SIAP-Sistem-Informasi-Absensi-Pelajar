package com.nurulislam.siap.model;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Representasi baris tb_absensi (absensi harian di gerbang, hasil Scan QR).
 * <p>
 * Field {@code namaMurid}, {@code nis}, {@code namaKelas} bersifat transient,
 * diisi oleh AbsensiDAO lewat JOIN untuk keperluan tampilan (riwayat scan,
 * hasil pemindaian terakhir) tanpa query tambahan.
 */
public class Absensi {

    private int absensiId;
    private int muridId;
    private int kelasId;
    private int sesiAbsensiId;
    private int penggunaId;
    private LocalDate tanggal;
    private LocalTime waktuMasuk;
    private StatusAbsensi status;
    /**
     * TRUE bila baris ini dibuat otomatis oleh sistem (ALFA otomatis untuk
     * murid yang belum absen). Baris otomatis boleh di-upgrade oleh scan
     * susulan; baris manual (FALSE) tidak.
     */
    private boolean otomatis;

    private String namaMurid;
    private String nis;
    private String namaKelas;
    private String statusMurid;

    public Absensi() {
    }

    public int getAbsensiId() {
        return absensiId;
    }

    public void setAbsensiId(int absensiId) {
        this.absensiId = absensiId;
    }

    public int getMuridId() {
        return muridId;
    }

    public void setMuridId(int muridId) {
        this.muridId = muridId;
    }

    public int getKelasId() {
        return kelasId;
    }

    public void setKelasId(int kelasId) {
        this.kelasId = kelasId;
    }

    public int getSesiAbsensiId() {
        return sesiAbsensiId;
    }

    public void setSesiAbsensiId(int sesiAbsensiId) {
        this.sesiAbsensiId = sesiAbsensiId;
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

    public LocalTime getWaktuMasuk() {
        return waktuMasuk;
    }

    public void setWaktuMasuk(LocalTime waktuMasuk) {
        this.waktuMasuk = waktuMasuk;
    }

    public StatusAbsensi getStatus() {
        return status;
    }

    public void setStatus(StatusAbsensi status) {
        this.status = status;
    }

    public boolean isOtomatis() {
        return otomatis;
    }

    public void setOtomatis(boolean otomatis) {
        this.otomatis = otomatis;
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

    public String getStatusMurid() {
        return statusMurid;
    }

    public void setStatusMurid(String statusMurid) {
        this.statusMurid = statusMurid;
    }
}