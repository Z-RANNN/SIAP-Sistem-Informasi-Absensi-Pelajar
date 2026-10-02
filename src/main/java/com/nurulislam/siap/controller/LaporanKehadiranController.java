package com.nurulislam.siap.controller;

import com.nurulislam.siap.app.Main;
import com.nurulislam.siap.dao.AbsensiDAO;
import com.nurulislam.siap.dao.AbsensiMapelDAO;
import com.nurulislam.siap.dao.KelasDAO;
import com.nurulislam.siap.dao.MataPelajaranDAO;
import com.nurulislam.siap.model.Absensi;
import com.nurulislam.siap.model.AbsensiMapel;
import com.nurulislam.siap.model.Kelas;
import com.nurulislam.siap.model.MataPelajaran;
import com.nurulislam.siap.model.Pengguna;
import com.nurulislam.siap.model.Role;
import com.nurulislam.siap.model.StatusAbsensi;
import com.nurulislam.siap.util.SceneManager;
import com.nurulislam.siap.util.SessionManager;
import com.nurulislam.siap.util.TanggalUtil;
import com.nurulislam.siap.util.JamRealtime;
import com.nurulislam.siap.util.PdfExportUtil;
import com.nurulislam.siap.util.ProfileMenu;
import com.nurulislam.siap.util.AvatarUtil;
import com.nurulislam.siap.util.BrandLogo;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.layout.StackPane;
import javafx.scene.image.ImageView;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javafx.scene.layout.HBox;

/**
 * Controller untuk halaman "Laporan Kehadiran", dapat digunakan oleh Staf TU dan Guru.
 * Mengikuti desain "laporan_rekap_kehadiran_siap": filter rentang
 * tanggal/kelas/status, empat kartu ringkasan (Total Hadir, Izin/Sakit,
 * Terlambat, Alfa), dan tabel rekapitulasi dengan pencarian.
 * <p>
 * Ada DUA jenis rekap, dipilih lewat tab "Absensi Harian" / "Absensi Mata
 * Pelajaran" di atas kartu filter:
 * <ul>
 *   <li><b>Absensi Harian</b> - absensi gerbang hasil Scan QR oleh Staf TU
 *       sendiri (tabel tb_absensi, lewat {@link AbsensiDAO}).</li>
 *   <li><b>Absensi Mata Pelajaran</b> - absensi per mata pelajaran yang
 *       dicatat GURU lewat halaman Absensi Mata Pelajaran (tabel
 *       tb_absensi_mapel, lewat {@link AbsensiMapelDAO}). Sebelum tab ini
 *       ditambahkan, hasil absensi Guru tidak pernah terlihat oleh Admin di
 *       luar sesi mengajar guru yang bersangkutan - tab ini yang membuatnya
 *       ikut masuk ke rekap Admin, lengkap dengan nama guru pengajarnya.</li>
 * </ul>
 * Kedua tabel berbagi kartu filter tanggal/kelas/status dan empat kartu
 * ringkasan yang sama; filter "Mata Pelajaran" hanya muncul saat tab kedua
 * aktif.
 * <p>
 * Tombol "Cetak / Ekspor PDF" menyimpan laporan langsung ke file PDF
 * berbentuk tabel (lihat {@link PdfExportUtil}) lewat dialog "Simpan" -
 * tidak lagi memakai dialog printer.
 */
public class LaporanKehadiranController {

    private static final DateTimeFormatter FORMAT_TANGGAL = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter FORMAT_JAM = DateTimeFormatter.ofPattern("HH:mm");

    // --- Sidebar ---
    @FXML private Button btnNavDashboard;
    @FXML private Button btnNavVerifikasi;
    @FXML private Button btnNavScanQr;
    @FXML private Button btnNavDataMurid;
    @FXML private Button btnNavDataKelas;
    @FXML private Button btnNavMataPelajaran;
    @FXML private Button btnNavCetakKartu;
    @FXML private Button btnNavLaporan;
    @FXML private StackPane avatarBox;
    @FXML private ImageView imgAvatar;
    @FXML private Label labelJamRealtime;
    @FXML private ImageView imgLogo;

    // --- Top bar ---
    @FXML private Label labelNamaUser;
    @FXML private Label labelRoleUser;
    @FXML private Label labelInisialUser;
    @FXML private Label errorLabel;
    @FXML private Label labelSubjudul;

    // --- Tab jenis rekap ---
    @FXML private ToggleButton tabHarian;
    @FXML private ToggleButton tabMapel;

    // --- Filter ---
    @FXML private DatePicker datePickerMulai;
    @FXML private DatePicker datePickerAkhir;
    @FXML private ComboBox<Kelas> comboKelasFilter;
    @FXML private ComboBox<StatusAbsensi> comboStatusFilter;
    @FXML private VBox boxMapelFilter;
    @FXML private ComboBox<MataPelajaran> comboMapelFilter;
    @FXML private Button btnTerapkanFilter;
    @FXML private Button btnEksporPdf;

    // --- Ringkasan ---
    @FXML private Label labelTotalHadir;
    @FXML private Label labelIzinSakit;
    @FXML private Label labelTerlambat;
    @FXML private Label labelAlfa;

    // --- Tabel: Absensi Harian ---
    @FXML private Label labelJumlahData;
    @FXML private TextField fieldPencarian;
    @FXML private TableView<Absensi> tabelLaporan;
    @FXML private TableColumn<Absensi, String> kolomNamaSiswa;
    @FXML private TableColumn<Absensi, String> kolomNis;
    @FXML private TableColumn<Absensi, String> kolomKelas;
    @FXML private TableColumn<Absensi, String> kolomTanggal;
    @FXML private TableColumn<Absensi, String> kolomStatus;
    @FXML private TableColumn<Absensi, String> kolomWaktuScan;
    @FXML private TableColumn<Absensi, String> kolomStatusMurid;
    @FXML private TableColumn<Absensi, Void> kolomAksi;

    // --- Tabel: Absensi Mata Pelajaran ---
    @FXML private TableView<AbsensiMapel> tabelLaporanMapel;
    @FXML private TableColumn<AbsensiMapel, String> kolomMapelNamaSiswa;
    @FXML private TableColumn<AbsensiMapel, String> kolomMapelNis;
    @FXML private TableColumn<AbsensiMapel, String> kolomMapelKelas;
    @FXML private TableColumn<AbsensiMapel, String> kolomMapelMapel;
    @FXML private TableColumn<AbsensiMapel, String> kolomMapelGuru;
    @FXML private TableColumn<AbsensiMapel, String> kolomMapelTanggal;
    @FXML private TableColumn<AbsensiMapel, String> kolomMapelStatus;
    @FXML private TableColumn<AbsensiMapel, String> kolomMapelWaktuScan;
    @FXML private TableColumn<AbsensiMapel, String> kolomMapelStatusMurid;
    @FXML private TableColumn<AbsensiMapel, Void> kolomMapelAksi;

    private final AbsensiDAO absensiDAO = new AbsensiDAO();
    private final AbsensiMapelDAO absensiMapelDAO = new AbsensiMapelDAO();
    private final KelasDAO kelasDAO = new KelasDAO();
    private final MataPelajaranDAO mataPelajaranDAO = new MataPelajaranDAO();

    /** Hasil query sesuai filter tanggal/kelas/status (sebelum pencarian nama/NIS diterapkan). */
    private final ObservableList<Absensi> hasilFilter = FXCollections.observableArrayList();
    /** Yang benar-benar ditampilkan di tabel, setelah pencarian nama/NIS diterapkan. */
    private final ObservableList<Absensi> dataTampil = FXCollections.observableArrayList();

    /** Versi Absensi Mata Pelajaran dari dua ObservableList di atas. */
    private final ObservableList<AbsensiMapel> hasilFilterMapel = FXCollections.observableArrayList();
    private final ObservableList<AbsensiMapel> dataTampilMapel = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        errorLabel.setText("");

        isiInfoPengguna();
        siapkanSidebar();
        siapkanFilter();
        siapkanTabel();
        siapkanTabelMapel();
        siapkanTabJenis();

        fieldPencarian.textProperty().addListener((obs, lama, baru) -> terapkanPencarian());
        btnTerapkanFilter.setOnAction(e -> muatData());
        btnEksporPdf.setOnAction(e -> handleEksporPdf());
        ProfileMenu.pasang(avatarBox, this::bukaProfil, this::handleLogout);
        AvatarUtil.tampilkan(avatarBox, imgAvatar, labelInisialUser);
        JamRealtime.mulai(labelJamRealtime);
        BrandLogo.pasang(imgLogo);

        muatDaftarKelasFilter();
        muatDaftarMapelFilter();
        muatData();
    }

    /** true jika tab "Absensi Mata Pelajaran" yang sedang aktif. */
    private boolean modeMapel() {
        return tabMapel.isSelected();
    }

    private void siapkanTabJenis() {
        ToggleGroup grupTab = new ToggleGroup();
        tabHarian.setToggleGroup(grupTab);
        tabMapel.setToggleGroup(grupTab);
        tabHarian.setSelected(true);

        grupTab.selectedToggleProperty().addListener((obs, lama, baru) -> {
            // Minimal satu tab harus tetap terpilih (tidak boleh semua ter-unselect).
            if (baru == null) {
                grupTab.selectToggle(lama != null ? lama : tabHarian);
                return;
            }

            perbaruiGayaTabJenis();

            boolean mapel = modeMapel();
            tabelLaporan.setVisible(!mapel);
            tabelLaporan.setManaged(!mapel);
            tabelLaporanMapel.setVisible(mapel);
            tabelLaporanMapel.setManaged(mapel);
            boxMapelFilter.setVisible(mapel);
            boxMapelFilter.setManaged(mapel);
            labelSubjudul.setText(mapel
                    ? "Rekap absensi mata pelajaran hasil pencatatan Guru, dengan filter tanggal, kelas, mata pelajaran, dan status"
                    : "Rekap absensi harian hasil Scan QR, dengan filter tanggal, kelas, dan status");

            fieldPencarian.clear();
            muatData();
        });

        perbaruiGayaTabJenis();
    }

    private void perbaruiGayaTabJenis() {
        for (ToggleButton tab : List.of(tabHarian, tabMapel)) {
            tab.getStyleClass().remove("filter-tab-active");
            if (tab.isSelected()) {
                tab.getStyleClass().add("filter-tab-active");
            }
        }
    }

    // ================= Info pengguna & sidebar =================

    private void isiInfoPengguna() {
        Pengguna pengguna = SessionManager.getPenggunaAktif();
        String nama = (pengguna != null && pengguna.getNama() != null) ? pengguna.getNama() : "Pengguna";
        String peran = (pengguna != null && pengguna.getRole() != null) ? pengguna.getRole().getLabel() : "";

        labelNamaUser.setText(nama);
        labelRoleUser.setText(peran);

        String[] bagian = nama.trim().split("\\s+");
        StringBuilder inisial = new StringBuilder();
        for (int i = 0; i < Math.min(2, bagian.length); i++) {
            if (!bagian[i].isEmpty()) {
                inisial.append(Character.toUpperCase(bagian[i].charAt(0)));
            }
        }
        labelInisialUser.setText(inisial.length() > 0 ? inisial.toString() : "?");
    }

    private void siapkanSidebar() {
        btnNavDashboard.setOnAction(e -> navigasiKe("/com/nurulislam/siap/fxml/Dashboard.fxml", "Dashboard"));
        btnNavVerifikasi.setOnAction(e -> navigasiKe("/com/nurulislam/siap/fxml/VerifikasiAkun.fxml", "Verifikasi Akun"));
        btnNavScanQr.setOnAction(e -> navigasiKe("/com/nurulislam/siap/fxml/ScanQr.fxml", "Scan QR"));
        btnNavDataMurid.setOnAction(e -> navigasiKe("/com/nurulislam/siap/fxml/ManajemenDataMurid.fxml", "Data Murid"));
        btnNavDataKelas.setOnAction(e -> navigasiKe("/com/nurulislam/siap/fxml/ManajemenDataKelas.fxml", "Data Kelas"));
        btnNavMataPelajaran.setOnAction(e -> navigasiKe("/com/nurulislam/siap/fxml/ManajemenMataPelajaran.fxml", "Mata Pelajaran"));
        btnNavCetakKartu.setOnAction(e -> navigasiKe("/com/nurulislam/siap/fxml/ManajemenCetakKartu.fxml", "Cetak Kartu Pelajar"));
        btnNavLaporan.setOnAction(e -> { /* sudah di halaman Laporan */ });
    }

    private void navigasiKe(String fxml, String judul) {
        try {
            SceneManager.switchTo(fxml, Main.APP_TITLE + " - " + judul);
        } catch (IOException e) {
            errorLabel.setText("Gagal membuka halaman " + judul + ".");
            System.err.println("[LaporanKehadiranController] IOException: " + e.getMessage());
        }
    }

    // ================= Filter =================

    private void siapkanFilter() {
        // Default: dari awal bulan berjalan sampai hari ini (sesuai contoh pada desain).
        LocalDate hariIni = LocalDate.now();
        datePickerMulai.setValue(hariIni.withDayOfMonth(1));
        datePickerAkhir.setValue(hariIni);
        TanggalUtil.pasangFormatIndonesia(datePickerMulai);
        TanggalUtil.pasangFormatIndonesia(datePickerAkhir);

        comboKelasFilter.setConverter(new StringConverter<>() {
            @Override
            public String toString(Kelas k) {
                return k == null ? "Semua Kelas" : k.getNamaKelas();
            }

            @Override
            public Kelas fromString(String s) {
                return null;
            }
        });

        // Item null merepresentasikan "Semua Status" - StatusAbsensi tidak punya nilai sentinel sendiri.
        comboStatusFilter.setItems(FXCollections.observableArrayList(
                null, StatusAbsensi.HADIR, StatusAbsensi.TERLAMBAT,
                StatusAbsensi.IZIN, StatusAbsensi.SAKIT, StatusAbsensi.ALFA));
        comboStatusFilter.getSelectionModel().selectFirst(); // null = Semua Status
        comboStatusFilter.setConverter(new StringConverter<>() {
            @Override
            public String toString(StatusAbsensi s) {
                return s == null ? "Semua Status" : capitalisasi(s.name());
            }

            @Override
            public StatusAbsensi fromString(String s) {
                return null;
            }
        });
    }

    private void muatDaftarKelasFilter() {
        try {
            ObservableList<Kelas> daftar = FXCollections.observableArrayList();
            daftar.add(null); // null = "Semua Kelas"
            daftar.addAll(kelasDAO.findAll());
            comboKelasFilter.setItems(daftar);
            comboKelasFilter.getSelectionModel().selectFirst();
        } catch (SQLException e) {
            errorLabel.setText("Gagal memuat daftar kelas untuk filter.");
            System.err.println("[LaporanKehadiranController] SQLException (kelas filter): " + e.getMessage());
        }
    }

    private void muatDaftarMapelFilter() {
        try {
            ObservableList<MataPelajaran> daftar = FXCollections.observableArrayList();
            daftar.add(null); // null = "Semua Mata Pelajaran"
            daftar.addAll(mataPelajaranDAO.findAll());
            comboMapelFilter.setItems(daftar);
            comboMapelFilter.getSelectionModel().selectFirst();
            comboMapelFilter.setConverter(new StringConverter<>() {
                @Override
                public String toString(MataPelajaran m) {
                    return m == null ? "Semua Mata Pelajaran" : m.getNamaMapel();
                }

                @Override
                public MataPelajaran fromString(String s) {
                    return null;
                }
            });
        } catch (SQLException e) {
            errorLabel.setText("Gagal memuat daftar mata pelajaran untuk filter.");
            System.err.println("[LaporanKehadiranController] SQLException (mapel filter): " + e.getMessage());
        }
    }

    // ================= Tabel & data =================

    private void siapkanTabel() {
        kolomNamaSiswa.setCellValueFactory(new PropertyValueFactory<>("namaMurid"));
        kolomNis.setCellValueFactory(new PropertyValueFactory<>("nis"));
        kolomKelas.setCellValueFactory(new PropertyValueFactory<>("namaKelas"));
        kolomTanggal.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
                data.getValue().getTanggal() != null ? data.getValue().getTanggal().format(FORMAT_TANGGAL) : "-"));
        kolomWaktuScan.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
                data.getValue().getWaktuMasuk() != null ? data.getValue().getWaktuMasuk().format(FORMAT_JAM) : "-"));
        kolomStatus.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
                capitalisasi(data.getValue().getStatus().name())));

        kolomStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean kosong) {
                super.updateItem(status, kosong);
                if (kosong || status == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(status);
                    setStyle(warnaStatus(status));
                }
            }
        });

        kolomStatusMurid.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
                data.getValue().getStatusMurid() != null ? data.getValue().getStatusMurid() : "-"));
        kolomStatusMurid.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean kosong) {
                super.updateItem(status, kosong);
                if (kosong || status == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(capitalisasi(status));
                    setStyle("AKTIF".equalsIgnoreCase(status)
                            ? "-fx-text-fill: #006a65; -fx-font-weight: bold;"
                            : "-fx-text-fill: #a43c12; -fx-font-weight: bold;");
                }
            }
        });

        kolomAksi.setCellFactory(col -> new TableCell<>() {
            private final Button btnUbah = new Button("Ubah");
            private final Button btnHapus = new Button("Hapus");
            private final HBox box = new HBox(6, btnUbah, btnHapus);

            {
                btnUbah.getStyleClass().add("laporan-btn-ubah");
                btnHapus.getStyleClass().add("laporan-btn-hapus");
                box.setAlignment(Pos.CENTER);
                btnUbah.setOnAction(e -> {
                    Absensi row = getTableView().getItems().get(getIndex());
                    handleUbahHarian(row);
                });
                btnHapus.setOnAction(e -> {
                    Absensi row = getTableView().getItems().get(getIndex());
                    handleHapusHarian(row);
                });
            }

            @Override
            protected void updateItem(Void item, boolean kosong) {
                super.updateItem(item, kosong);
                setText(null);
                setGraphic(kosong ? null : box);
            }
        });

        // Kolom mengisi penuh lebar tabel sehingga tidak ada kolom hantu / scroll horizontal.
        tabelLaporan.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        tabelLaporan.setItems(dataTampil);
        tabelLaporan.setPlaceholder(new Label("Tidak ada data untuk filter yang dipilih."));
    }

    private String warnaStatus(String status) {
        return switch (status) {
            case "Hadir" -> "-fx-text-fill: #006a62; -fx-font-weight: bold;";
            case "Terlambat" -> "-fx-text-fill: #a5580a; -fx-font-weight: bold;";
            case "Izin", "Sakit" -> "-fx-text-fill: #3d5afe; -fx-font-weight: bold;";
            case "Alfa" -> "-fx-text-fill: #ba1a1a; -fx-font-weight: bold;";
            default -> "";
        };
    }

    private void siapkanTabelMapel() {
        kolomMapelNamaSiswa.setCellValueFactory(new PropertyValueFactory<>("namaMurid"));
        kolomMapelNis.setCellValueFactory(new PropertyValueFactory<>("nis"));
        kolomMapelKelas.setCellValueFactory(new PropertyValueFactory<>("namaKelas"));
        kolomMapelMapel.setCellValueFactory(new PropertyValueFactory<>("namaMapel"));
        kolomMapelGuru.setCellValueFactory(new PropertyValueFactory<>("namaGuru"));
        kolomMapelTanggal.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
                data.getValue().getTanggal() != null ? data.getValue().getTanggal().format(FORMAT_TANGGAL) : "-"));
        kolomMapelWaktuScan.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
                data.getValue().getWaktuScan() != null ? data.getValue().getWaktuScan().format(FORMAT_JAM) : "-"));
        kolomMapelStatus.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
                capitalisasi(data.getValue().getStatus().name())));

        kolomMapelStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean kosong) {
                super.updateItem(status, kosong);
                if (kosong || status == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(status);
                    setStyle(warnaStatus(status));
                }
            }
        });

        kolomMapelStatusMurid.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
                data.getValue().getStatusMurid() != null ? data.getValue().getStatusMurid() : "-"));
        kolomMapelStatusMurid.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean kosong) {
                super.updateItem(status, kosong);
                if (kosong || status == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(capitalisasi(status));
                    setStyle("AKTIF".equalsIgnoreCase(status)
                            ? "-fx-text-fill: #006a65; -fx-font-weight: bold;"
                            : "-fx-text-fill: #a43c12; -fx-font-weight: bold;");
                }
            }
        });

        kolomMapelAksi.setCellFactory(col -> new TableCell<>() {
            private final Button btnUbah = new Button("Ubah");
            private final Button btnHapus = new Button("Hapus");
            private final HBox box = new HBox(6, btnUbah, btnHapus);

            {
                btnUbah.getStyleClass().add("laporan-btn-ubah");
                btnHapus.getStyleClass().add("laporan-btn-hapus");
                box.setAlignment(Pos.CENTER);
                btnUbah.setOnAction(e -> {
                    AbsensiMapel row = getTableView().getItems().get(getIndex());
                    handleUbahMapel(row);
                });
                btnHapus.setOnAction(e -> {
                    AbsensiMapel row = getTableView().getItems().get(getIndex());
                    handleHapusMapel(row);
                });
            }

            @Override
            protected void updateItem(Void item, boolean kosong) {
                super.updateItem(item, kosong);
                setText(null);
                setGraphic(kosong ? null : box);
            }
        });

        // Kolom mengisi penuh lebar tabel sehingga tidak ada kolom hantu / scroll horizontal.
        tabelLaporanMapel.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        tabelLaporanMapel.setItems(dataTampilMapel);
        tabelLaporanMapel.setPlaceholder(new Label(
                "Tidak ada data absensi mata pelajaran untuk filter yang dipilih."));
    }

    private void muatData() {
        if (modeMapel()) {
            muatDataMapel();
        } else {
            muatDataHarian();
        }
    }

    private void muatDataHarian() {
        LocalDate tglMulai = datePickerMulai.getValue();
        LocalDate tglAkhir = datePickerAkhir.getValue();

        if (tglMulai == null || tglAkhir == null) {
            errorLabel.setText("Tanggal mulai dan tanggal akhir wajib diisi.");
            return;
        }
        if (tglMulai.isAfter(tglAkhir)) {
            errorLabel.setText("Tanggal mulai tidak boleh setelah tanggal akhir.");
            return;
        }

        Kelas kelasDipilih = comboKelasFilter.getSelectionModel().getSelectedItem();
        StatusAbsensi statusDipilih = comboStatusFilter.getSelectionModel().getSelectedItem();
        Integer kelasId = kelasDipilih != null ? kelasDipilih.getKelasId() : null;

        try {
            // Murid aktif yang belum tercatat otomatis ditandai ALFA supaya
            // laporan selalu lengkap walau tidak ada yang scan hari itu.
            Pengguna sesi = SessionManager.getPenggunaAktif();
            if (sesi != null && sesi.getPenggunaId() != null) {
                absensiDAO.tandaiAlfaOtomatis(tglMulai, tglAkhir, sesi.getPenggunaId());
            }

            hasilFilter.setAll(absensiDAO.findFiltered(tglMulai, tglAkhir, kelasId, statusDipilih, null));
            terapkanPencarian();

            Map<StatusAbsensi, Integer> rekap = absensiDAO.hitungRekap(tglMulai, tglAkhir, kelasId);
            labelTotalHadir.setText(String.valueOf(rekap.getOrDefault(StatusAbsensi.HADIR, 0)));
            labelIzinSakit.setText(String.valueOf(
                    rekap.getOrDefault(StatusAbsensi.IZIN, 0) + rekap.getOrDefault(StatusAbsensi.SAKIT, 0)));
            labelTerlambat.setText(String.valueOf(rekap.getOrDefault(StatusAbsensi.TERLAMBAT, 0)));
            labelAlfa.setText(String.valueOf(rekap.getOrDefault(StatusAbsensi.ALFA, 0)));

            errorLabel.setText("");
        } catch (SQLException e) {
            errorLabel.setText("Gagal memuat data laporan. Coba lagi.");
            System.err.println("[LaporanKehadiranController] SQLException (muat data): " + e.getMessage());
        }
    }

    /**
     * Versi "Absensi Mata Pelajaran" dari {@link #muatDataHarian()}: sumber data
     * dan rekapnya lewat {@link AbsensiMapelDAO}, ditambah filter Mata Pelajaran.
     * Inilah yang membuat hasil absensi Guru akhirnya ikut terlihat di Laporan Admin.
     */
    private void muatDataMapel() {
        LocalDate tglMulai = datePickerMulai.getValue();
        LocalDate tglAkhir = datePickerAkhir.getValue();

        if (tglMulai == null || tglAkhir == null) {
            errorLabel.setText("Tanggal mulai dan tanggal akhir wajib diisi.");
            return;
        }
        if (tglMulai.isAfter(tglAkhir)) {
            errorLabel.setText("Tanggal mulai tidak boleh setelah tanggal akhir.");
            return;
        }

        Kelas kelasDipilih = comboKelasFilter.getSelectionModel().getSelectedItem();
        StatusAbsensi statusDipilih = comboStatusFilter.getSelectionModel().getSelectedItem();
        MataPelajaran mapelDipilih = comboMapelFilter.getSelectionModel().getSelectedItem();
        Integer kelasId = kelasDipilih != null ? kelasDipilih.getKelasId() : null;
        Integer mapelId = mapelDipilih != null ? mapelDipilih.getMapelId() : null;

        try {
            Pengguna pengguna = SessionManager.getPenggunaAktif();
            boolean guru = pengguna != null && pengguna.getRole() == Role.GURU;

            if (guru) {
                hasilFilterMapel.setAll(
                        absensiMapelDAO.findFiltered(tglMulai, tglAkhir, kelasId, mapelId,
                                pengguna.getPenggunaId(), statusDipilih, null));
            } else {
                hasilFilterMapel.setAll(
                        absensiMapelDAO.findFiltered(tglMulai, tglAkhir, kelasId, mapelId, statusDipilih, null));
            }
            terapkanPencarian();

            Map<StatusAbsensi, Integer> rekap = guru
                    ? absensiMapelDAO.hitungRekap(tglMulai, tglAkhir, kelasId, mapelId, pengguna.getPenggunaId())
                    : absensiMapelDAO.hitungRekap(tglMulai, tglAkhir, kelasId, mapelId);
            labelTotalHadir.setText(String.valueOf(rekap.getOrDefault(StatusAbsensi.HADIR, 0)));
            labelIzinSakit.setText(String.valueOf(
                    rekap.getOrDefault(StatusAbsensi.IZIN, 0) + rekap.getOrDefault(StatusAbsensi.SAKIT, 0)));
            labelTerlambat.setText(String.valueOf(rekap.getOrDefault(StatusAbsensi.TERLAMBAT, 0)));
            labelAlfa.setText(String.valueOf(rekap.getOrDefault(StatusAbsensi.ALFA, 0)));

            errorLabel.setText("");
        } catch (SQLException e) {
            errorLabel.setText("Gagal memuat data laporan. Coba lagi.");
            System.err.println("[LaporanKehadiranController] SQLException (muat data mapel): " + e.getMessage());
        }
    }

    private void terapkanPencarian() {
        if (modeMapel()) {
            terapkanPencarianMapel();
        } else {
            terapkanPencarianHarian();
        }
    }

    private void terapkanPencarianHarian() {
        String kataKunci = fieldPencarian.getText() == null ? "" : fieldPencarian.getText().trim().toLowerCase();
        if (kataKunci.isEmpty()) {
            dataTampil.setAll(hasilFilter);
        } else {
            dataTampil.setAll(hasilFilter.stream()
                    .filter(a -> (a.getNamaMurid() != null && a.getNamaMurid().toLowerCase().contains(kataKunci))
                            || (a.getNis() != null && a.getNis().toLowerCase().contains(kataKunci)))
                    .toList());
        }
        labelJumlahData.setText("Menampilkan " + dataTampil.size() + " dari " + hasilFilter.size() + " data");
    }

    private void terapkanPencarianMapel() {
        String kataKunci = fieldPencarian.getText() == null ? "" : fieldPencarian.getText().trim().toLowerCase();
        if (kataKunci.isEmpty()) {
            dataTampilMapel.setAll(hasilFilterMapel);
        } else {
            dataTampilMapel.setAll(hasilFilterMapel.stream()
                    .filter(a -> (a.getNamaMurid() != null && a.getNamaMurid().toLowerCase().contains(kataKunci))
                            || (a.getNis() != null && a.getNis().toLowerCase().contains(kataKunci)))
                    .toList());
        }
        labelJumlahData.setText(
                "Menampilkan " + dataTampilMapel.size() + " dari " + hasilFilterMapel.size() + " data");
    }

    // ================= Aksi data laporan =================

    private int penggunaAktifId() {
        Pengguna pengguna = SessionManager.getPenggunaAktif();
        return pengguna != null ? pengguna.getPenggunaId() : 0;
    }

    private LocalTime parseWaktu(String teks) {
        if (teks == null || teks.isBlank()) {
            return null;
        }
        return LocalTime.parse(
                teks.trim(),
                DateTimeFormatter.ofPattern("HH.mm")
        );
    }

    private Optional<StatusWaktu> tampilkanDialogKoreksi(String judul, StatusAbsensi statusAwal, LocalTime waktuAwal) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(judul);
        dialog.setHeaderText("Koreksi status dan waktu absensi");

        ComboBox<StatusAbsensi> comboStatus = new ComboBox<>(
                FXCollections.observableArrayList(StatusAbsensi.values()));
        comboStatus.setConverter(new StringConverter<>() {
            @Override public String toString(StatusAbsensi s) { return s == null ? "" : capitalisasi(s.name()); }
            @Override public StatusAbsensi fromString(String s) { return null; }
        });
        comboStatus.setValue(statusAwal);
        comboStatus.setMaxWidth(Double.MAX_VALUE);

        TextField fieldWaktu = new TextField(waktuAwal != null ? waktuAwal.format(FORMAT_JAM) : "");
        fieldWaktu.setPromptText("HH.mm");
        fieldWaktu.setMaxWidth(Double.MAX_VALUE);

        Label hint = new Label("Untuk status Alfa, waktu scan boleh dikosongkan. Format waktu: HH.mm, contoh 07.15.");
        hint.getStyleClass().add("greeting-caption");
        hint.setWrapText(true);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(12));
        grid.add(new Label("Status"), 0, 0);
        grid.add(comboStatus, 1, 0);
        grid.add(new Label("Waktu Scan"), 0, 1);
        grid.add(fieldWaktu, 1, 1);
        grid.add(hint, 1, 2);
        GridPane.setHgrow(comboStatus, Priority.ALWAYS);
        GridPane.setHgrow(fieldWaktu, Priority.ALWAYS);

        dialog.getDialogPane().setContent(grid);
        ButtonType simpan = new ButtonType("Simpan", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(simpan, ButtonType.CANCEL);

        dialog.setResultConverter(button -> button == simpan ? simpan : null);

        while (true) {
            Optional<ButtonType> hasil = dialog.showAndWait();
            if (hasil.isEmpty() || hasil.get() != simpan) {
                return Optional.empty();
            }

            StatusAbsensi status = comboStatus.getValue();
            if (status == null) {
                tampilkanPeringatan("Status belum dipilih.");
                continue;
            }

            try {
                LocalTime waktu = parseWaktu(fieldWaktu.getText());
                if (status != StatusAbsensi.ALFA && waktu == null) {
                    tampilkanPeringatan("Waktu scan wajib diisi untuk status " + capitalisasi(status.name()) + ".");
                    continue;
                }
                return Optional.of(new StatusWaktu(status, waktu));
            } catch (RuntimeException ex) {
                tampilkanPeringatan("Format waktu tidak valid. Gunakan HH.mm, contoh 10.18.");
            }
        }
    }

    private void tampilkanPeringatan(String pesan) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Validasi Data");
        alert.setHeaderText(null);
        alert.setContentText(pesan);
        alert.showAndWait();
    }

    private void handleUbahHarian(Absensi row) {
        if (row == null) return;
        Optional<StatusWaktu> koreksi = tampilkanDialogKoreksi(
                "Ubah Absensi - " + row.getNamaMurid(), row.getStatus(), row.getWaktuMasuk());
        if (koreksi.isEmpty()) return;

        try {
            row.setStatus(koreksi.get().status());
            row.setWaktuMasuk(koreksi.get().waktu());
            absensiDAO.updateFromLaporan(row, penggunaAktifId());
            errorLabel.setText("Data absensi " + row.getNamaMurid() + " berhasil diperbarui.");
            muatData();
        } catch (SQLException e) {
            errorLabel.setText("Gagal memperbarui data absensi.");
            System.err.println("[LaporanKehadiranController] SQLException (ubah harian): " + e.getMessage());
        }
    }

    private void handleHapusHarian(Absensi row) {
        if (row == null) return;
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Hapus Absensi");
        alert.setHeaderText("Hapus data absensi?");
        alert.setContentText("Data absensi " + row.getNamaMurid() + " pada "
                + (row.getTanggal() != null ? row.getTanggal().format(FORMAT_TANGGAL) : "tanggal ini")
                + " akan dihapus dari database.");
        Optional<ButtonType> jawaban = alert.showAndWait();
        if (jawaban.isEmpty() || jawaban.get() != ButtonType.OK) return;

        try {
            absensiDAO.deleteById(row.getAbsensiId());
            errorLabel.setText("Data absensi " + row.getNamaMurid() + " berhasil dihapus.");
            muatData();
        } catch (SQLException e) {
            errorLabel.setText("Gagal menghapus data absensi.");
            System.err.println("[LaporanKehadiranController] SQLException (hapus harian): " + e.getMessage());
        }
    }

    private void handleUbahMapel(AbsensiMapel row) {
        if (row == null) return;
        Optional<StatusWaktu> koreksi = tampilkanDialogKoreksi(
                "Ubah Absensi Mapel - " + row.getNamaMurid(), row.getStatus(), row.getWaktuScan());
        if (koreksi.isEmpty()) return;

        try {
            row.setStatus(koreksi.get().status());
            row.setWaktuScan(koreksi.get().waktu());
            absensiMapelDAO.updateFromLaporan(row, penggunaAktifId());
            errorLabel.setText("Data absensi " + row.getNamaMurid() + " berhasil diperbarui.");
            muatData();
        } catch (SQLException e) {
            errorLabel.setText("Gagal memperbarui data absensi mata pelajaran.");
            System.err.println("[LaporanKehadiranController] SQLException (ubah mapel): " + e.getMessage());
        }
    }

    private void handleHapusMapel(AbsensiMapel row) {
        if (row == null) return;
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Hapus Absensi Mata Pelajaran");
        alert.setHeaderText("Hapus data absensi?");
        alert.setContentText("Data absensi " + row.getNamaMurid() + " untuk "
                + (row.getNamaMapel() != null ? row.getNamaMapel() : "mata pelajaran ini")
                + " pada " + (row.getTanggal() != null ? row.getTanggal().format(FORMAT_TANGGAL) : "tanggal ini")
                + " akan dihapus dari database.");
        Optional<ButtonType> jawaban = alert.showAndWait();
        if (jawaban.isEmpty() || jawaban.get() != ButtonType.OK) return;

        try {
            absensiMapelDAO.deleteById(row.getAbsensiMapelId());
            errorLabel.setText("Data absensi " + row.getNamaMurid() + " berhasil dihapus.");
            muatData();
        } catch (SQLException e) {
            errorLabel.setText("Gagal menghapus data absensi mata pelajaran.");
            System.err.println("[LaporanKehadiranController] SQLException (hapus mapel): " + e.getMessage());
        }
    }

    private record StatusWaktu(StatusAbsensi status, LocalTime waktu) { }

    // ================= Cetak / Ekspor PDF =================

    private void handleEksporPdf() {
        if (modeMapel()) {
            handleEksporPdfMapel();
        } else {
            handleEksporPdfHarian();
        }
    }

    private void handleEksporPdfHarian() {
        if (dataTampil.isEmpty()) {
            errorLabel.setText("Tidak ada data untuk diekspor. Ubah filter terlebih dahulu.");
            return;
        }

        File tujuan = pilihFileTujuan("Laporan-Kehadiran-Harian");
        if (tujuan == null) {
            return;
        }

        String[] header = {"No", "Nama Siswa", "NIS", "Kelas", "Tanggal", "Status", "Waktu"};
        float[] lebar = {0.5f, 2.6f, 1.1f, 0.9f, 1.2f, 1.0f, 0.9f};
        List<String[]> baris = new ArrayList<>();
        int nomor = 1;
        for (Absensi a : dataTampil) {
            baris.add(new String[]{
                    String.valueOf(nomor++),
                    a.getNamaMurid() != null ? a.getNamaMurid() : "-",
                    a.getNis() != null ? a.getNis() : "-",
                    a.getNamaKelas() != null ? a.getNamaKelas() : "-",
                    a.getTanggal() != null ? a.getTanggal().format(FORMAT_TANGGAL) : "-",
                    capitalisasi(a.getStatus().name()),
                    a.getWaktuMasuk() != null ? a.getWaktuMasuk().format(FORMAT_JAM) : "-"
            });
        }

        try {
            PdfExportUtil.eksporTabel(tujuan,
                    "Laporan Kehadiran Harian - MA Nurul Islam",
                    buatRingkasanFilter(), header, baris, lebar, false);
            errorLabel.setText("");
            infoEkspor(tujuan);
        } catch (Exception e) {
            errorLabel.setText("Gagal menyimpan file PDF. Coba lagi.");
            System.err.println("[LaporanKehadiranController] Gagal ekspor PDF: " + e.getMessage());
        }
    }

    /** Versi "Absensi Mata Pelajaran" dari {@link #handleEksporPdfHarian()}, kolom ditambah Mapel & Guru. */
    private void handleEksporPdfMapel() {
        if (dataTampilMapel.isEmpty()) {
            errorLabel.setText("Tidak ada data untuk diekspor. Ubah filter terlebih dahulu.");
            return;
        }

        File tujuan = pilihFileTujuan("Laporan-Kehadiran-Mapel");
        if (tujuan == null) {
            return;
        }

        String[] header = {"No", "Nama Siswa", "NIS", "Kelas", "Mata Pelajaran",
                "Guru Pengajar", "Tanggal", "Status", "Waktu"};
        float[] lebar = {0.5f, 2.0f, 1.0f, 0.8f, 1.8f, 1.8f, 1.2f, 1.0f, 0.9f};
        List<String[]> baris = new ArrayList<>();
        int nomor = 1;
        for (AbsensiMapel a : dataTampilMapel) {
            baris.add(new String[]{
                    String.valueOf(nomor++),
                    a.getNamaMurid() != null ? a.getNamaMurid() : "-",
                    a.getNis() != null ? a.getNis() : "-",
                    a.getNamaKelas() != null ? a.getNamaKelas() : "-",
                    a.getNamaMapel() != null ? a.getNamaMapel() : "-",
                    a.getNamaGuru() != null ? a.getNamaGuru() : "-",
                    a.getTanggal() != null ? a.getTanggal().format(FORMAT_TANGGAL) : "-",
                    capitalisasi(a.getStatus().name()),
                    a.getWaktuScan() != null ? a.getWaktuScan().format(FORMAT_JAM) : "-"
            });
        }

        try {
            PdfExportUtil.eksporTabel(tujuan,
                    "Laporan Kehadiran Mata Pelajaran - MA Nurul Islam",
                    buatRingkasanFilterMapel(), header, baris, lebar, true);
            errorLabel.setText("");
            infoEkspor(tujuan);
        } catch (Exception e) {
            errorLabel.setText("Gagal menyimpan file PDF. Coba lagi.");
            System.err.println("[LaporanKehadiranController] Gagal ekspor PDF (mapel): " + e.getMessage());
        }
    }

    /** Dialog "Simpan" untuk memilih lokasi file PDF hasil ekspor. */
    private File pilihFileTujuan(String awalan) {
        FileChooser pemilih = new FileChooser();
        pemilih.setTitle("Simpan Laporan PDF");
        pemilih.getExtensionFilters().add(new FileChooser.ExtensionFilter("File PDF", "*.pdf"));
        pemilih.setInitialFileName(awalan + "-"
                + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".pdf");
        return pemilih.showSaveDialog(btnEksporPdf.getScene().getWindow());
    }

    private void infoEkspor(File tujuan) {
        Alert sukses = new Alert(Alert.AlertType.INFORMATION);
        sukses.setTitle("Ekspor PDF");
        sukses.setHeaderText(null);
        sukses.setContentText("File PDF berhasil disimpan:\n" + tujuan.getAbsolutePath());
        sukses.showAndWait();
    }

    private String buatRingkasanFilter() {
        StringBuilder sb = new StringBuilder();
        sb.append(datePickerMulai.getValue().format(FORMAT_TANGGAL))
                .append(" - ")
                .append(datePickerAkhir.getValue().format(FORMAT_TANGGAL));
        Kelas kelas = comboKelasFilter.getSelectionModel().getSelectedItem();
        sb.append(" | Kelas: ").append(kelas != null ? kelas.getNamaKelas() : "Semua Kelas");
        StatusAbsensi status = comboStatusFilter.getSelectionModel().getSelectedItem();
        sb.append(" | Status: ").append(status != null ? capitalisasi(status.name()) : "Semua Status");
        return sb.toString();
    }

    private String buatRingkasanFilterMapel() {
        StringBuilder sb = new StringBuilder(buatRingkasanFilter());
        MataPelajaran mapel = comboMapelFilter.getSelectionModel().getSelectedItem();
        sb.append(" | Mata Pelajaran: ").append(mapel != null ? mapel.getNamaMapel() : "Semua Mata Pelajaran");
        return sb.toString();
    }

    // ================= Util =================

    private String capitalisasi(String teksEnum) {
        if (teksEnum == null || teksEnum.isEmpty()) {
            return "";
        }
        return teksEnum.charAt(0) + teksEnum.substring(1).toLowerCase();
    }

    /** Membuka halaman Profil Saya (diakses dari menu avatar kanan atas). */
    private void bukaProfil() {
        try {
            SceneManager.switchTo("/com/nurulislam/siap/fxml/PengaturanAkun.fxml",
                    Main.APP_TITLE + " - Profil Saya");
        } catch (IOException e) {
            System.err.println("[LaporanKehadiranController] Gagal membuka Profil Saya: " + e.getMessage());
        }
    }

    private void handleLogout() {
        SessionManager.logout();
        try {
            SceneManager.switchTo("/com/nurulislam/siap/fxml/Login.fxml", Main.APP_TITLE + " - Masuk");
        } catch (IOException e) {
            errorLabel.setText("Gagal membuka halaman Login.");
            System.err.println("[LaporanKehadiranController] IOException: " + e.getMessage());
        }
    }
}