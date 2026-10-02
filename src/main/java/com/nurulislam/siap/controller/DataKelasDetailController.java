package com.nurulislam.siap.controller;

import com.nurulislam.siap.app.Main;
import com.nurulislam.siap.dao.KelasDAO;
import com.nurulislam.siap.dao.MuridDAO;
import com.nurulislam.siap.model.Kelas;
import com.nurulislam.siap.model.Murid;
import com.nurulislam.siap.model.Pengguna;
import com.nurulislam.siap.model.Role;
import com.nurulislam.siap.model.Statusmurid;
import com.nurulislam.siap.util.SceneManager;
import com.nurulislam.siap.util.SessionManager;
import com.nurulislam.siap.util.ProfileMenu;
import com.nurulislam.siap.util.AvatarUtil;
import com.nurulislam.siap.util.BrandLogo;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.layout.StackPane;
import javafx.scene.image.ImageView;
import javafx.scene.control.*;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.util.Callback;

import java.io.IOException;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Halaman detail satu kelas.
 *
 * Fungsi:
 * - menampilkan semua murid dalam kelas yang dipilih;
 * - ringkasan total murid, wali kelas, laki-laki, dan perempuan;
 * - pencarian cepat NIS/nama;
 * - akses cepat tambah/edit murid dengan kelas sudah dipilih;
 * - tersedia shortcut enam kelas dari sidebar.
 */
public class DataKelasDetailController {

    private static String kelasTarget;

    public static void setKelasTarget(String namaKelas) {
        kelasTarget = namaKelas;
    }

    @FXML private Button btnNavDashboard;
    @FXML private Button btnNavVerifikasi;
    @FXML private Button btnNavScanQr;
    @FXML private Button btnNavDataMurid;
    @FXML private Button btnNavDataKelas;
    @FXML private VBox boxSubKelas;
    @FXML private Button btnKelasXIPA;
    @FXML private Button btnKelasXIPS;
    @FXML private Button btnKelasXIIPA;
    @FXML private Button btnKelasXIIPS;
    @FXML private Button btnKelasXIIIPA;
    @FXML private Button btnKelasXIIIPS;
    @FXML private Button btnNavMataPelajaran;
    @FXML private Button btnNavCetakKartu;
    @FXML private Button btnNavLaporan;
    @FXML private StackPane avatarBox;
    @FXML private ImageView imgAvatar;
    @FXML private ImageView imgLogo;

    @FXML private Label labelNamaUser;
    @FXML private Label labelRoleUser;
    @FXML private Label labelInisialUser;

    @FXML private Label labelNamaKelas;
    @FXML private Label labelTingkatJurusan;
    @FXML private Label labelTotalMurid;
    @FXML private Label labelWaliKelas;
    @FXML private Label labelLakiLaki;
    @FXML private Label labelPerempuan;
    @FXML private Label labelStatus;

    @FXML private TextField fieldPencarian;
    @FXML private Button btnRefresh;
    @FXML private Button btnTambahMurid;
    @FXML private Button btnUbahMurid;
    @FXML private Button btnKembali;

    @FXML private TableView<Murid> tabelMurid;
    @FXML private TableColumn<Murid, String> kolomNis;
    @FXML private TableColumn<Murid, String> kolomNama;
    @FXML private TableColumn<Murid, String> kolomJenisKelamin;
    @FXML private TableColumn<Murid, String> kolomTanggalLahir;
    @FXML private TableColumn<Murid, String> kolomStatus;
    @FXML private TableColumn<Murid, Void> kolomAksi;

    private final KelasDAO kelasDAO = new KelasDAO();
    private final MuridDAO muridDAO = new MuridDAO();

    private Kelas kelasAktif;
    private final javafx.collections.ObservableList<Murid> semuaMurid =
            FXCollections.observableArrayList();

    private static final DateTimeFormatter FORMAT_TANGGAL =
            DateTimeFormatter.ofPattern("dd MMM yyyy");
    private boolean subKelasTerbuka = true;

    @FXML
    public void initialize() {
        if (!pastikanAksesTU()) {
            return;
        }

        isiInfoPengguna();
        siapkanSidebar();
        siapkanTabel();

        fieldPencarian.textProperty().addListener((obs, lama, baru) -> terapkanFilter());

        btnRefresh.setOnAction(e -> muatDataKelas());
        btnTambahMurid.setOnAction(e -> bukaTambahMurid());
        btnUbahMurid.setOnAction(e -> bukaEditMurid());
        btnKembali.setOnAction(e -> bukaDataKelas());
        ProfileMenu.pasang(avatarBox, this::bukaProfil, this::handleLogout);
        AvatarUtil.tampilkan(avatarBox, imgAvatar, labelInisialUser);
        BrandLogo.pasang(imgLogo);

        // Sidebar kelas dibuat sebagai submenu yang dapat dibuka/tutup.
        setSubKelasTerbuka(true);

        tabelMurid.getSelectionModel().selectedItemProperty().addListener(
                (obs, lama, baru) -> btnUbahMurid.setDisable(baru == null)
        );

        String target = kelasTarget;
        kelasTarget = null;

        if (target == null || target.isBlank()) {
            labelStatus.setText("Kelas belum dipilih.");
            return;
        }

        muatDataKelas(target);
    }

    private boolean pastikanAksesTU() {
        Pengguna sesi = SessionManager.getPenggunaAktif();
        if (sesi != null && sesi.getRole() == Role.TU) {
            return true;
        }

        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Akses Ditolak");
        alert.setHeaderText("Halaman khusus Staf TU");
        alert.setContentText("Akun Guru tidak memiliki hak akses ke halaman detail kelas.");
        alert.showAndWait();

        try {
            SceneManager.switchTo(
                    "/com/nurulislam/siap/fxml/DashboardGuru.fxml",
                    Main.APP_TITLE + " - Beranda"
            );
        } catch (IOException ignored) {
        }
        return false;
    }

    private void isiInfoPengguna() {
        Pengguna pengguna = SessionManager.getPenggunaAktif();
        String nama = pengguna != null && pengguna.getNama() != null
                ? pengguna.getNama()
                : "Pengguna";
        String peran = pengguna != null && pengguna.getRole() != null
                ? pengguna.getRole().getLabel()
                : "";
        if (labelNamaUser != null) {
            labelNamaUser.setText(nama);
        }
        if (labelRoleUser != null) {
            labelRoleUser.setText(peran);
        }

        String[] bagian = nama.trim().split("\\s+");
        StringBuilder inisial = new StringBuilder();
        for (int i = 0; i < Math.min(2, bagian.length); i++) {
            if (!bagian[i].isBlank()) {
                inisial.append(Character.toUpperCase(bagian[i].charAt(0)));
            }
        }
        if (labelInisialUser != null) {
            labelInisialUser.setText(inisial.isEmpty() ? "?" : inisial.toString());
        }
    }

    private void setSubKelasTerbuka(boolean terbuka) {
        subKelasTerbuka = terbuka;
        boxSubKelas.setVisible(terbuka);
        boxSubKelas.setManaged(terbuka);
        btnNavDataKelas.setText(terbuka ? "Data Kelas   ⌄" : "Data Kelas   ›");
    }

    private void toggleSubKelas() {
        setSubKelasTerbuka(!subKelasTerbuka);
    }

    private void tandaiKelasAktif(String namaKelas) {
        Button[] semuaTombol = {
            btnKelasXIPS, btnKelasXIPA, btnKelasXIIPS,
            btnKelasXIIPA, btnKelasXIIIPS, btnKelasXIIIPA
        };
        for (Button b : semuaTombol) {
            b.getStyleClass().remove("nav-item-sub-active");
            b.setStyle("-fx-padding: 7 10 7 16; -fx-font-size: 13px; -fx-background-color: transparent;");
        }

        String target = namaKelas == null ? "" : namaKelas;
        if ("X IPS".equalsIgnoreCase(target)) { btnKelasXIPS.getStyleClass().add("nav-item-sub-active"); btnKelasXIPS.setStyle("-fx-padding: 7 10 7 16; -fx-font-size: 13px; -fx-background-color: #dff3f1; -fx-text-fill: #006a65; -fx-font-weight: bold;"); }
        if ("X IPA".equalsIgnoreCase(target)) { btnKelasXIPA.getStyleClass().add("nav-item-sub-active"); btnKelasXIPA.setStyle("-fx-padding: 7 10 7 16; -fx-font-size: 13px; -fx-background-color: #dff3f1; -fx-text-fill: #006a65; -fx-font-weight: bold;"); }
        if ("XI IPS".equalsIgnoreCase(target)) { btnKelasXIIPS.getStyleClass().add("nav-item-sub-active"); btnKelasXIIPS.setStyle("-fx-padding: 7 10 7 16; -fx-font-size: 13px; -fx-background-color: #dff3f1; -fx-text-fill: #006a65; -fx-font-weight: bold;"); }
        if ("XI IPA".equalsIgnoreCase(target)) { btnKelasXIIPA.getStyleClass().add("nav-item-sub-active"); btnKelasXIIPA.setStyle("-fx-padding: 7 10 7 16; -fx-font-size: 13px; -fx-background-color: #dff3f1; -fx-text-fill: #006a65; -fx-font-weight: bold;"); }
        if ("XII IPS".equalsIgnoreCase(target)) { btnKelasXIIIPS.getStyleClass().add("nav-item-sub-active"); btnKelasXIIIPS.setStyle("-fx-padding: 7 10 7 16; -fx-font-size: 13px; -fx-background-color: #dff3f1; -fx-text-fill: #006a65; -fx-font-weight: bold;"); }
        if ("XII IPA".equalsIgnoreCase(target)) { btnKelasXIIIPA.getStyleClass().add("nav-item-sub-active"); btnKelasXIIIPA.setStyle("-fx-padding: 7 10 7 16; -fx-font-size: 13px; -fx-background-color: #dff3f1; -fx-text-fill: #006a65; -fx-font-weight: bold;"); }
    }

    private void siapkanSidebar() {
        btnNavDashboard.setOnAction(e -> buka("/com/nurulislam/siap/fxml/Dashboard.fxml", "Dashboard"));
        btnNavVerifikasi.setOnAction(e -> buka("/com/nurulislam/siap/fxml/VerifikasiAkun.fxml", "Verifikasi Akun"));
        btnNavScanQr.setOnAction(e -> buka("/com/nurulislam/siap/fxml/ScanQr.fxml", "Scan QR"));
        btnNavDataMurid.setOnAction(e -> bukaDataMurid());
        btnNavDataKelas.setOnAction(e -> toggleSubKelas());
        btnNavMataPelajaran.setOnAction(e -> buka("/com/nurulislam/siap/fxml/ManajemenMataPelajaran.fxml", "Mata Pelajaran"));
        btnNavCetakKartu.setOnAction(e -> buka("/com/nurulislam/siap/fxml/ManajemenCetakKartu.fxml", "Cetak Kartu Pelajar"));
        btnNavLaporan.setOnAction(e -> buka("/com/nurulislam/siap/fxml/LaporanKehadiran.fxml", "Laporan"));

        btnKelasXIPS.setOnAction(e -> bukaKelas("X IPS", btnKelasXIPS));
        btnKelasXIPA.setOnAction(e -> bukaKelas("X IPA", btnKelasXIPA));
        btnKelasXIIPS.setOnAction(e -> bukaKelas("XI IPS", btnKelasXIIPS));
        btnKelasXIIPA.setOnAction(e -> bukaKelas("XI IPA", btnKelasXIIPA));
        btnKelasXIIIPS.setOnAction(e -> bukaKelas("XII IPS", btnKelasXIIIPS));
        btnKelasXIIIPA.setOnAction(e -> bukaKelas("XII IPA", btnKelasXIIIPA));
    }

    private void siapkanTabel() {
        kolomNis.setCellValueFactory(new PropertyValueFactory<>("nis"));
        kolomNama.setCellValueFactory(new PropertyValueFactory<>("nama"));
        kolomStatus.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(
                        data.getValue().getStatus() == Statusmurid.AKTIF ? "Aktif" : "Nonaktif"
                ));

        kolomStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                setText(status);
                if ("Aktif".equalsIgnoreCase(status)) {
                    setStyle("-fx-text-fill: #00796b; -fx-font-weight: bold;");
                } else {
                    setStyle("-fx-text-fill: #b3261e; -fx-font-weight: bold;");
                }
            }
        });

        kolomAksi.setCellFactory(col -> new TableCell<>() {
            private final Button btnUbah = new Button("Ubah");
            private final Button btnStatus = new Button();
            private final HBox box = new HBox(6, btnUbah, btnStatus);

            {
                box.setAlignment(Pos.CENTER);
                btnUbah.setStyle("-fx-font-size: 11px; -fx-padding: 6 9;");
                btnStatus.setStyle("-fx-font-size: 11px; -fx-padding: 6 8; -fx-background-color: #eeeeee; -fx-text-fill: #333333;");

                btnUbah.setOnAction(e -> {
                    Murid row = muridPadaBarisIni();
                    pilihMuridDanUbah(row);
                });

                btnStatus.setOnAction(e -> {
                    Murid row = muridPadaBarisIni();
                    toggleStatusMurid(row);
                });
            }

            private Murid muridPadaBarisIni() {
                int idx = getIndex();
                if (idx < 0 || idx >= getTableView().getItems().size()) {
                    return null;
                }
                return getTableView().getItems().get(idx);
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                    return;
                }

                Murid row = muridPadaBarisIni();
                if (row == null) {
                    setGraphic(null);
                    return;
                }
                btnStatus.setText(
                        row.getStatus() == Statusmurid.AKTIF ? "Nonaktifkan" : "Aktifkan"
                );
                setGraphic(box);
            }
        });

        kolomJenisKelamin.setCellFactory(angkat -> new TableCell<>() {
            @Override
            protected void updateItem(String ignored, boolean empty) {
                super.updateItem(ignored, empty);
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                    setText(null);
                    return;
                }
                Murid murid = getTableView().getItems().get(getIndex());
                if (murid == null) {
                    setText(null);
                    return;
                }
                String jk = murid.getJenisKelamin();
                setText("L".equals(jk) ? "Laki-laki" : "P".equals(jk) ? "Perempuan" : "-");
            }
        });

        kolomTanggalLahir.setCellFactory(angkat -> new TableCell<>() {
            @Override
            protected void updateItem(String ignored, boolean empty) {
                super.updateItem(ignored, empty);
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                    setText(null);
                    return;
                }
                Murid murid = getTableView().getItems().get(getIndex());
                if (murid == null) {
                    setText(null);
                    return;
                }
                setText(murid.getTanggalLahir() == null
                        ? "-"
                        : murid.getTanggalLahir().format(FORMAT_TANGGAL));
            }
        });

        tabelMurid.setItems(semuaMurid);
        tabelMurid.setPlaceholder(new Label("Belum ada murid pada kelas ini."));
        tabelMurid.setEditable(false);
        btnUbahMurid.setDisable(true);

        // Klik ganda = shortcut edit murid.
        tabelMurid.setRowFactory(tv -> {
            TableRow<Murid> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    bukaEditMurid();
                }
            });
            return row;
        });
    }

    private void muatDataKelas() {
        if (kelasAktif == null) {
            return;
        }
        muatDataKelas(kelasAktif.getNamaKelas());
    }

    private void muatDataKelas(String namaKelas) {
        try {
            List<Kelas> semuaKelas = kelasDAO.findAllDetail();
            Optional<Kelas> ditemukan = semuaKelas.stream()
                    .filter(k -> k.getNamaKelas() != null
                            && k.getNamaKelas().equalsIgnoreCase(namaKelas))
                    .findFirst();

            if (ditemukan.isEmpty()) {
                kelasAktif = null;
                labelNamaKelas.setText(namaKelas);
                labelTingkatJurusan.setText("-");
                labelTotalMurid.setText("0");
                labelWaliKelas.setText("-");
                labelLakiLaki.setText("0");
                labelPerempuan.setText("0");
                labelStatus.setText("Kelas tidak ditemukan.");
                semuaMurid.clear();
                return;
            }

            kelasAktif = ditemukan.get();
            setSubKelasTerbuka(true);
            tandaiKelasAktif(kelasAktif.getNamaKelas());
            labelNamaKelas.setText(kelasAktif.getNamaKelas());
            labelTingkatJurusan.setText(kelasAktif.getTingkatJurusan());
            labelWaliKelas.setText(
                    kelasAktif.getWaliKelasNama() == null || kelasAktif.getWaliKelasNama().isBlank()
                            ? "-"
                            : kelasAktif.getWaliKelasNama()
            );

            List<Murid> murid = muridDAO.findByKelas(kelasAktif.getKelasId());
            semuaMurid.setAll(murid);

            long laki = murid.stream().filter(m -> "L".equals(m.getJenisKelamin())).count();
            long perempuan = murid.stream().filter(m -> "P".equals(m.getJenisKelamin())).count();

            labelTotalMurid.setText(String.valueOf(murid.size()));
            labelLakiLaki.setText(String.valueOf(laki));
            labelPerempuan.setText(String.valueOf(perempuan));
            labelStatus.setText(
                    murid.isEmpty()
                            ? "Belum ada murid pada kelas ini."
                            : ""
            );

            fieldPencarian.clear();
            tabelMurid.getSelectionModel().clearSelection();
            btnUbahMurid.setDisable(true);
        } catch (SQLException e) {
            labelStatus.setText("Gagal memuat data. Periksa koneksi database.");
            semuaMurid.clear();
            labelTotalMurid.setText("0");
            labelLakiLaki.setText("0");
            labelPerempuan.setText("0");
            System.err.println("[DataKelasDetailController] SQLException: " + e.getMessage());
        }
    }

    private void terapkanFilter() {
        String kataKunci = fieldPencarian.getText() == null
                ? ""
                : fieldPencarian.getText().trim().toLowerCase(Locale.ROOT);

        if (kataKunci.isEmpty()) {
            tabelMurid.setItems(semuaMurid);
            return;
        }

        List<Murid> hasil = semuaMurid.stream()
                .filter(m -> (m.getNama() != null && m.getNama().toLowerCase(Locale.ROOT).contains(kataKunci))
                        || (m.getNis() != null && m.getNis().toLowerCase(Locale.ROOT).contains(kataKunci)))
                .toList();

        tabelMurid.setItems(FXCollections.observableArrayList(hasil));
    }

    private void pilihMuridDanUbah(Murid murid) {
        if (murid == null || kelasAktif == null) {
            return;
        }

        ManajemenDataMuridController.setMuridAwal(
                murid.getMuridId(),
                kelasAktif.getKelasId(),
                kelasAktif.getNamaKelas()
        );
        bukaDataMurid();
    }

    private void toggleStatusMurid(Murid murid) {
        if (murid == null) {
            return;
        }

        boolean aktif = murid.getStatus() == Statusmurid.AKTIF;
        String statusBaru = aktif ? "NONAKTIF" : "AKTIF";

        Alert konfirmasi = new Alert(Alert.AlertType.CONFIRMATION);
        konfirmasi.setTitle(aktif ? "Nonaktifkan Murid" : "Aktifkan Murid");
        konfirmasi.setHeaderText(null);
        konfirmasi.setContentText(
                "Ubah status " + murid.getNama() + " menjadi " + statusBaru + "?"
                + "\n\n" + (aktif
                        ? "Murid nonaktif tidak dapat melakukan absensi."
                        : "Murid aktif dapat kembali melakukan absensi.")
        );

        Optional<ButtonType> jawaban = konfirmasi.showAndWait();
        if (jawaban.isEmpty() || jawaban.get() != ButtonType.OK) {
            return;
        }

        try {
            murid.setStatus(Statusmurid.valueOf(statusBaru));
            muridDAO.update(murid);
            labelStatus.setText(
                    "Status " + murid.getNama() + " berhasil diubah menjadi " + statusBaru + "."
            );
            muatDataKelas();
        } catch (SQLException e) {
            labelStatus.setText("Gagal mengubah status murid.");
            System.err.println(
                    "[DataKelasDetailController] SQLException (status murid): "
                    + e.getMessage()
            );
        }
    }

    private void bukaTambahMurid() {
        if (kelasAktif == null) {
            return;
        }
        ManajemenDataMuridController.setKelasAwal(
                kelasAktif.getKelasId(),
                kelasAktif.getNamaKelas()
        );
        bukaDataMurid();
    }

    private void bukaEditMurid() {
        Murid terpilih = tabelMurid.getSelectionModel().getSelectedItem();
        if (terpilih == null || kelasAktif == null) {
            return;
        }
        ManajemenDataMuridController.setMuridAwal(
                terpilih.getMuridId(),
                kelasAktif.getKelasId(),
                kelasAktif.getNamaKelas()
        );
        bukaDataMurid();
    }

    private void bukaDataMurid() {
        buka("/com/nurulislam/siap/fxml/ManajemenDataMurid.fxml", "Data Murid");
    }

    private void bukaDataKelas() {
        buka("/com/nurulislam/siap/fxml/ManajemenDataKelas.fxml", "Data Kelas");
    }

    private void bukaKelas(String namaKelas, Button tombol) {
        DataKelasDetailController.setKelasTarget(namaKelas);
        // Sorot kelas aktif ditangani controller BARU lewat muatDataKelas() ->
        // tandaiKelasAktif(), bukan lewat instance lama ini.
        buka("/com/nurulislam/siap/fxml/DataKelasDetail.fxml", namaKelas);
    }

    private void buka(String fxml, String judul) {
        try {
            SceneManager.switchTo(fxml, Main.APP_TITLE + " - " + judul);
        } catch (IOException e) {
            labelStatus.setText("Gagal membuka " + judul + ".");
            System.err.println("[DataKelasDetailController] IOException: " + e.getMessage());
        }
    }

    /** Membuka halaman Profil Saya (diakses dari menu avatar kanan atas). */
    private void bukaProfil() {
        try {
            SceneManager.switchTo("/com/nurulislam/siap/fxml/PengaturanAkun.fxml",
                    Main.APP_TITLE + " - Profil Saya");
        } catch (IOException e) {
            System.err.println("[DataKelasDetailController] Gagal membuka Profil Saya: " + e.getMessage());
        }
    }

    private void handleLogout() {
        SessionManager.logout();
        buka("/com/nurulislam/siap/fxml/Login.fxml", "Masuk");
    }
}
