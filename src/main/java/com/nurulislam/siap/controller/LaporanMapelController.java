package com.nurulislam.siap.controller;

import com.nurulislam.siap.app.Main;
import com.nurulislam.siap.dao.AbsensiMapelDAO;
import com.nurulislam.siap.dao.JadwalMengajarDAO;
import com.nurulislam.siap.model.AbsensiMapel;
import com.nurulislam.siap.model.JadwalMengajar;
import com.nurulislam.siap.model.MataPelajaran;
import com.nurulislam.siap.model.Pengguna;
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
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.geometry.Pos;
import javafx.geometry.Insets;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Controller untuk halaman "Laporan" versi akun Guru: rekap absensi mata
 * pelajaran (tb_absensi_mapel) khusus untuk jadwal mengajar milik guru yang
 * sedang login. Berbeda dengan {@link LaporanKehadiranController} milik
 * Admin/Staf TU yang bisa melihat lintas guru, di sini semua query DAO
 * (lewat {@link AbsensiMapelDAO}) selalu disaring dengan guru_id pengguna
 * aktif, sehingga satu guru tidak pernah melihat data guru lain.
 * <p>
 * Filter Kelas dan Mata Pelajaran hanya diisi dari jadwal mengajar guru yang
 * bersangkutan (lewat {@link JadwalMengajarDAO#findAllByGuru(int)}), bukan
 * seluruh kelas/mapel di sekolah.
 * <p>
 * Tombol "Cetak / Ekspor PDF" menyimpan laporan langsung ke file PDF
 * berbentuk tabel (lihat {@link PdfExportUtil}) lewat dialog "Simpan".
 */
public class LaporanMapelController {

    private static final DateTimeFormatter FORMAT_TANGGAL = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter FORMAT_JAM = DateTimeFormatter.ofPattern("HH:mm:ss");

    // --- Sidebar ---
    @FXML private Button btnNavBeranda;
    @FXML private Button btnNavJadwal;
    @FXML private Button btnNavAbsensiMapel;
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

    // --- Filter ---
    @FXML private DatePicker datePickerMulai;
    @FXML private DatePicker datePickerAkhir;
    @FXML private ComboBox<KelasFilterItem> comboKelasFilter;
    @FXML private ComboBox<MataPelajaran> comboMapelFilter;
    @FXML private ComboBox<StatusAbsensi> comboStatusFilter;
    @FXML private Button btnTerapkanFilter;
    @FXML private Button btnEksporPdf;

    // --- Kartu ringkasan ---
    @FXML private Label labelTotalHadir;
    @FXML private Label labelIzinSakit;
    @FXML private Label labelTerlambat;
    @FXML private Label labelAlfa;

    // --- Tabel ---
    @FXML private Label labelJumlahData;
    @FXML private TextField fieldPencarian;
    @FXML private TableView<AbsensiMapel> tabelLaporan;
    @FXML private TableColumn<AbsensiMapel, String> kolomNamaSiswa;
    @FXML private TableColumn<AbsensiMapel, String> kolomNis;
    @FXML private TableColumn<AbsensiMapel, String> kolomKelas;
    @FXML private TableColumn<AbsensiMapel, String> kolomMapel;
    @FXML private TableColumn<AbsensiMapel, String> kolomTanggal;
    @FXML private TableColumn<AbsensiMapel, String> kolomStatus;
    @FXML private TableColumn<AbsensiMapel, String> kolomWaktuScan;
    @FXML private TableColumn<AbsensiMapel, String> kolomStatusSiswa;
    @FXML private TableColumn<AbsensiMapel, Void> kolomAksi;

    private final AbsensiMapelDAO absensiMapelDAO = new AbsensiMapelDAO();
    private final JadwalMengajarDAO jadwalMengajarDAO = new JadwalMengajarDAO();

    /** Hasil query sesuai filter tanggal/kelas/mapel/status (sebelum pencarian nama/NIS diterapkan). */
    private final ObservableList<AbsensiMapel> hasilFilter = FXCollections.observableArrayList();
    /** Yang benar-benar ditampilkan di tabel, setelah pencarian nama/NIS diterapkan. */
    private final ObservableList<AbsensiMapel> dataTampil = FXCollections.observableArrayList();

    private int guruId;

    @FXML
    public void initialize() {
        errorLabel.setText("");

        Pengguna pengguna = SessionManager.getPenggunaAktif();
        guruId = (pengguna != null && pengguna.getPenggunaId() != null) ? pengguna.getPenggunaId() : 0;

        isiInfoPengguna();
        siapkanSidebar();
        siapkanFilter();
        siapkanTabel();

        fieldPencarian.textProperty().addListener((obs, lama, baru) -> terapkanPencarian());
        btnTerapkanFilter.setOnAction(e -> muatData());
        btnEksporPdf.setOnAction(e -> handleEksporPdf());
        ProfileMenu.pasang(avatarBox, this::bukaProfil, this::handleLogout);
        AvatarUtil.tampilkan(avatarBox, imgAvatar, labelInisialUser);
        JamRealtime.mulai(labelJamRealtime);
        BrandLogo.pasang(imgLogo);

        muatDaftarFilterJadwal();
        muatData();
    }

    // ================= Info pengguna & navigasi =================

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
        btnNavBeranda.setOnAction(e -> navigasiKe("/com/nurulislam/siap/fxml/DashboardGuru.fxml", "Dashboard"));
        btnNavJadwal.setOnAction(e -> navigasiKe("/com/nurulislam/siap/fxml/JadwalGuru.fxml", "Jadwal Mengajar"));
        btnNavAbsensiMapel.setOnAction(e -> navigasiKe("/com/nurulislam/siap/fxml/AbsensiMapel.fxml", "Absensi Mata Pelajaran"));
        btnNavLaporan.setOnAction(e -> { /* sudah di halaman Laporan */ });
    }

    private void navigasiKe(String fxml, String judul) {
        try {
            SceneManager.switchTo(fxml, Main.APP_TITLE + " - " + judul);
        } catch (IOException e) {
            errorLabel.setText("Gagal membuka halaman " + judul + ".");
            System.err.println("[LaporanMapelController] IOException: " + e.getMessage());
        }
    }

    /** Membuka halaman Profil Saya (diakses dari menu avatar kanan atas). */
    private void bukaProfil() {
        try {
            SceneManager.switchTo("/com/nurulislam/siap/fxml/PengaturanAkun.fxml",
                    Main.APP_TITLE + " - Profil Saya");
        } catch (IOException e) {
            System.err.println("[LaporanMapelController] Gagal membuka Profil Saya: " + e.getMessage());
        }
    }

    private void handleLogout() {
        SessionManager.logout();
        try {
            SceneManager.switchTo("/com/nurulislam/siap/fxml/Login.fxml", Main.APP_TITLE + " - Masuk");
        } catch (IOException e) {
            errorLabel.setText("Gagal membuka halaman Login.");
            System.err.println("[LaporanMapelController] IOException: " + e.getMessage());
        }
    }

    // ================= Filter =================

    private void siapkanFilter() {
        // Default: dari awal bulan berjalan sampai hari ini.
        LocalDate hariIni = LocalDate.now();
        datePickerMulai.setValue(hariIni.withDayOfMonth(1));
        datePickerAkhir.setValue(hariIni);
        TanggalUtil.pasangFormatIndonesia(datePickerMulai);
        TanggalUtil.pasangFormatIndonesia(datePickerAkhir);

        comboKelasFilter.setConverter(new StringConverter<>() {
            @Override
            public String toString(KelasFilterItem k) {
                return k == null ? "Semua Kelas" : k.namaKelas();
            }

            @Override
            public KelasFilterItem fromString(String s) {
                return null;
            }
        });

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

    /**
     * Mengisi filter Kelas & Mata Pelajaran HANYA dari kelas/mapel yang benar-benar
     * diajar guru ini (lewat jadwal mengajarnya), bukan seluruh kelas/mapel sekolah -
     * supaya guru tidak bisa "mengintip" filter untuk kelas/mapel yang bukan miliknya.
     */
    private void muatDaftarFilterJadwal() {
        try {
            List<JadwalMengajar> jadwalGuru = jadwalMengajarDAO.findAllByGuru(guruId);

            // LinkedHashMap supaya urutan tampil konsisten & otomatis unik per kelas_id/mapel_id.
            Map<Integer, KelasFilterItem> kelasUnik = new LinkedHashMap<>();
            Map<Integer, MataPelajaran> mapelUnik = new LinkedHashMap<>();
            for (JadwalMengajar j : jadwalGuru) {
                kelasUnik.putIfAbsent(j.getKelasId(), new KelasFilterItem(j.getKelasId(), j.getNamaKelas()));
                MataPelajaran mp = new MataPelajaran();
                mp.setMapelId(j.getMapelId());
                mp.setNamaMapel(j.getNamaMapel());
                mp.setKodeMapel(j.getKodeMapel());
                mapelUnik.putIfAbsent(j.getMapelId(), mp);
            }

            ObservableList<KelasFilterItem> daftarKelas = FXCollections.observableArrayList();
            daftarKelas.add(null); // null = "Semua Kelas"
            daftarKelas.addAll(kelasUnik.values());
            comboKelasFilter.setItems(daftarKelas);
            comboKelasFilter.getSelectionModel().selectFirst();

            ObservableList<MataPelajaran> daftarMapel = FXCollections.observableArrayList();
            daftarMapel.add(null); // null = "Semua Mata Pelajaran"
            daftarMapel.addAll(mapelUnik.values());
            comboMapelFilter.setItems(daftarMapel);
            comboMapelFilter.getSelectionModel().selectFirst();
        } catch (SQLException e) {
            errorLabel.setText("Gagal memuat daftar kelas/mata pelajaran untuk filter.");
            System.err.println("[LaporanMapelController] SQLException (filter jadwal): " + e.getMessage());
        }
    }

    /** Item ringan untuk ComboBox filter Kelas, dibangun dari JadwalMengajar (bukan query Kelas terpisah). */
    private record KelasFilterItem(int kelasId, String namaKelas) {
    }

    // ================= Tabel & data =================

    private void siapkanTabel() {
        kolomNamaSiswa.setCellValueFactory(new PropertyValueFactory<>("namaMurid"));
        kolomNis.setCellValueFactory(new PropertyValueFactory<>("nis"));
        kolomKelas.setCellValueFactory(new PropertyValueFactory<>("namaKelas"));
        kolomMapel.setCellValueFactory(new PropertyValueFactory<>("namaMapel"));
        kolomTanggal.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
                data.getValue().getTanggal() != null ? data.getValue().getTanggal().format(FORMAT_TANGGAL) : "-"));
        kolomWaktuScan.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
                data.getValue().getWaktuScan() != null ? data.getValue().getWaktuScan().format(FORMAT_JAM) : "-"));
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

        kolomStatusSiswa.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
                data.getValue().getStatusMurid() != null ? data.getValue().getStatusMurid() : "-"));
        kolomStatusSiswa.setCellFactory(col -> new TableCell<>() {
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
                    AbsensiMapel row = barisIni();
                    handleUbahMapel(row);
                });
                btnHapus.setOnAction(e -> {
                    AbsensiMapel row = barisIni();
                    handleHapusMapel(row);
                });
            }

            private AbsensiMapel barisIni() {
                int idx = getIndex();
                if (idx < 0 || idx >= getTableView().getItems().size()) {
                    return null;
                }
                return getTableView().getItems().get(idx);
            }

            @Override
            protected void updateItem(Void item, boolean kosong) {
                super.updateItem(item, kosong);
                setText(null);
                setGraphic(kosong ? null : box);
            }
        });

        // Kolom mengisi penuh lebar tabel sehingga tidak ada kolom hantu di kanan.
        tabelLaporan.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        tabelLaporan.setItems(dataTampil);
        tabelLaporan.setPlaceholder(new Label("Tidak ada data absensi mata pelajaran untuk filter yang dipilih."));
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

    private void muatData() {
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

        KelasFilterItem kelasDipilih = comboKelasFilter.getSelectionModel().getSelectedItem();
        MataPelajaran mapelDipilih = comboMapelFilter.getSelectionModel().getSelectedItem();
        StatusAbsensi statusDipilih = comboStatusFilter.getSelectionModel().getSelectedItem();
        Integer kelasId = kelasDipilih != null ? kelasDipilih.kelasId() : null;
        Integer mapelId = mapelDipilih != null ? mapelDipilih.getMapelId() : null;

        try {
            hasilFilter.setAll(absensiMapelDAO.findFiltered(
                    tglMulai, tglAkhir, kelasId, mapelId, guruId, statusDipilih, null));
            terapkanPencarian();

            Map<StatusAbsensi, Integer> rekap = absensiMapelDAO.hitungRekap(tglMulai, tglAkhir, kelasId, mapelId, guruId);
            labelTotalHadir.setText(String.valueOf(rekap.getOrDefault(StatusAbsensi.HADIR, 0)));
            labelIzinSakit.setText(String.valueOf(
                    rekap.getOrDefault(StatusAbsensi.IZIN, 0) + rekap.getOrDefault(StatusAbsensi.SAKIT, 0)));
            labelTerlambat.setText(String.valueOf(rekap.getOrDefault(StatusAbsensi.TERLAMBAT, 0)));
            labelAlfa.setText(String.valueOf(rekap.getOrDefault(StatusAbsensi.ALFA, 0)));

            errorLabel.setText("");
        } catch (SQLException e) {
            errorLabel.setText("Gagal memuat data laporan. Coba lagi.");
            System.err.println("[LaporanMapelController] SQLException (muat data): " + e.getMessage());
        }
    }

    private void terapkanPencarian() {
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

    // ================= Cetak / Ekspor PDF =================

    private void handleEksporPdf() {
        if (dataTampil.isEmpty()) {
            errorLabel.setText("Tidak ada data untuk diekspor. Ubah filter terlebih dahulu.");
            return;
        }

        FileChooser pemilih = new FileChooser();
        pemilih.setTitle("Simpan Laporan PDF");
        pemilih.getExtensionFilters().add(new FileChooser.ExtensionFilter("File PDF", "*.pdf"));
        pemilih.setInitialFileName("Laporan-Mapel-"
                + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".pdf");
        File tujuan = pemilih.showSaveDialog(btnEksporPdf.getScene().getWindow());
        if (tujuan == null) {
            return;
        }

        Pengguna pengguna = SessionManager.getPenggunaAktif();
        String namaGuru = pengguna != null && pengguna.getNama() != null ? pengguna.getNama() : "Guru";

        String[] header = {"No", "Nama Siswa", "NIS", "Kelas", "Mata Pelajaran", "Tanggal", "Status", "Waktu"};
        float[] lebar = {0.5f, 2.2f, 1.0f, 0.9f, 1.8f, 1.2f, 1.0f, 1.0f};
        List<String[]> baris = new ArrayList<>();
        int nomor = 1;
        for (AbsensiMapel a : dataTampil) {
            baris.add(new String[]{
                    String.valueOf(nomor++),
                    a.getNamaMurid() != null ? a.getNamaMurid() : "-",
                    a.getNis() != null ? a.getNis() : "-",
                    a.getNamaKelas() != null ? a.getNamaKelas() : "-",
                    a.getNamaMapel() != null ? a.getNamaMapel() : "-",
                    a.getTanggal() != null ? a.getTanggal().format(FORMAT_TANGGAL) : "-",
                    capitalisasi(a.getStatus().name()),
                    a.getWaktuScan() != null ? a.getWaktuScan().format(FORMAT_JAM) : "-"
            });
        }

        try {
            PdfExportUtil.eksporTabel(tujuan,
                    "Laporan Kehadiran Mata Pelajaran - " + namaGuru,
                    buatRingkasanFilter(), header, baris, lebar, false);
            errorLabel.setText("");
            Alert sukses = new Alert(Alert.AlertType.INFORMATION);
            sukses.setTitle("Ekspor PDF");
            sukses.setHeaderText(null);
            sukses.setContentText("File PDF berhasil disimpan:\n" + tujuan.getAbsolutePath());
            sukses.showAndWait();
        } catch (Exception e) {
            errorLabel.setText("Gagal menyimpan file PDF. Coba lagi.");
            System.err.println("[LaporanMapelController] Gagal ekspor PDF: " + e.getMessage());
        }
    }

    private String buatRingkasanFilter() {
        StringBuilder sb = new StringBuilder();
        sb.append(datePickerMulai.getValue().format(FORMAT_TANGGAL))
                .append(" - ")
                .append(datePickerAkhir.getValue().format(FORMAT_TANGGAL));
        KelasFilterItem kelas = comboKelasFilter.getSelectionModel().getSelectedItem();
        sb.append(" | Kelas: ").append(kelas != null ? kelas.namaKelas() : "Semua Kelas");
        MataPelajaran mapel = comboMapelFilter.getSelectionModel().getSelectedItem();
        sb.append(" | Mata Pelajaran: ").append(mapel != null ? mapel.getNamaMapel() : "Semua Mata Pelajaran");
        StatusAbsensi status = comboStatusFilter.getSelectionModel().getSelectedItem();
        sb.append(" | Status: ").append(status != null ? capitalisasi(status.name()) : "Semua Status");
        return sb.toString();
    }

    private String capitalisasi(String teksEnum) {
        if (teksEnum == null || teksEnum.isEmpty()) {
            return "";
        }
        return teksEnum.charAt(0) + teksEnum.substring(1).toLowerCase();
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
            System.err.println("[LaporanMapelController] SQLException (ubah mapel): " + e.getMessage());
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
            System.err.println("[LaporanMapelController] SQLException (hapus mapel): " + e.getMessage());
        }
    }

    private record StatusWaktu(StatusAbsensi status, LocalTime waktu) { }
}