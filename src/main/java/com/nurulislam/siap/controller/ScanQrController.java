package com.nurulislam.siap.controller;

import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_videoio.VideoCapture;
import static org.bytedeco.opencv.global.opencv_videoio.CAP_DSHOW;
import static org.bytedeco.opencv.global.opencv_videoio.CAP_MSMF;
import static org.bytedeco.opencv.global.opencv_videoio.CAP_ANY;
import static org.bytedeco.opencv.global.opencv_videoio.CAP_PROP_FRAME_WIDTH;
import static org.bytedeco.opencv.global.opencv_videoio.CAP_PROP_FRAME_HEIGHT;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.nurulislam.siap.app.Main;
import com.nurulislam.siap.dao.AbsensiDAO;
import com.nurulislam.siap.dao.AbsensiSudahAdaException;
import com.nurulislam.siap.dao.MuridDAO;
import com.nurulislam.siap.dao.SesiAbsensiDAO;
import com.nurulislam.siap.model.Absensi;
import com.nurulislam.siap.model.Murid;
import com.nurulislam.siap.model.Pengguna;
import com.nurulislam.siap.model.Sesiabsensi;
import com.nurulislam.siap.model.StatusAbsensi;
import com.nurulislam.siap.util.SceneManager;
import com.nurulislam.siap.util.SessionManager;
import com.nurulislam.siap.util.ProfileMenu;
import com.nurulislam.siap.util.AvatarUtil;
import com.nurulislam.siap.util.BrandLogo;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.shape.Rectangle;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Controller halaman "Scan QR" - absensi harian di gerbang sekolah.
 * <p>
 * Alur: kamera laptop menyala di background thread ({@link #mulaiThreadKamera()}),
 * setiap frame dikonversi ke {@link javafx.scene.image.Image} untuk preview,
 * lalu dicoba dibaca kode QR-nya lewat ZXing. Jika ditemukan token yang cocok
 * dengan tb_murid.qr_token, sistem mencatat absensi ke tb_absensi (status
 * HADIR/TERLAMBAT ditentukan dari tb_sesi_absensi yang berlaku saat itu).
 * <p>
 * Tersedia juga input manual (NIS atau kode QR) sebagai jalur cadangan saat
 * kamera tidak tersedia di perangkat, atau untuk keperluan uji coba tanpa
 * mencetak kartu pelajar fisik.
 */
public class ScanQrController {

    private static final int LEBAR_FRAME = 640;
    private static final int TINGGI_FRAME = 480;
    private static final long JEDA_ANTAR_SCAN_MS = 4000; // cegah entri berulang saat QR sama masih di depan kamera

    // --- Sidebar ---
    @FXML private Label labelSidebarSub;
    @FXML private Button btnNavDashboard;
    @FXML private Button btnNavVerifikasi;
    @FXML private Button btnNavScanQr;
    @FXML private Button btnNavDataMurid;
    @FXML private Button btnNavDataKelas;
    @FXML private Button btnNavMataPelajaran;
    @FXML private Button btnNavCetakKartu;
    @FXML private Button btnNavLaporan;
    @FXML private Label labelStatusKamera;
    @FXML private StackPane avatarBox;
    @FXML private ImageView imgAvatar;
    @FXML private ImageView imgLogo;

    // --- Top bar ---
    @FXML private Label labelTanggal;
    @FXML private Label labelNamaUser;
    @FXML private Label labelRoleUser;
    @FXML private Label labelInisialUser;

    // --- Rekap ---
    @FXML private Label labelRekapHadir;
    @FXML private Label labelRekapTerlambat;
    @FXML private Label labelRekapIzinSakit;
    @FXML private Label labelRekapGagal;

    // --- Kamera ---
    @FXML private ImageView imageViewKamera;
    @FXML private Label labelKameraOverlay;
    @FXML private StackPane dotStatusKamera;
    @FXML private Label labelChipKamera;
    @FXML private TextField fieldInputManual;
    @FXML private Button btnInputManual;

    // --- Hasil pemindaian terakhir ---
    @FXML private Label labelWaktuScanTerakhir;
    @FXML private Label labelInisialHasil;
    @FXML private Label labelNamaHasil;
    @FXML private Label labelNisHasil;
    @FXML private Label labelKelasHasil;
    @FXML private Label labelJamHasil;
    @FXML private Label labelBadgeHasil;

    // --- Riwayat ---
    @FXML private VBox riwayatScanBox;

    private final MuridDAO muridDAO = new MuridDAO();
    private final AbsensiDAO absensiDAO = new AbsensiDAO();
    private final SesiAbsensiDAO sesiAbsensiDAO = new SesiAbsensiDAO();

    private VideoCapture videoCapture;
    private OpenCVFrameConverter.ToMat matConverter;
    private Java2DFrameConverter java2dConverter;
    private Thread threadKamera;
    private final AtomicBoolean kameraJalan = new AtomicBoolean(false);
    /**
     * Dua buffer gambar preview kamera yang DIPAKAI ULANG bergantian (double buffering),
     * bukan dibuat baru tiap frame. PENTING untuk mencegah native memory leak:
     * SwingFXUtils.toFXImage(img, null) selalu mengalokasikan Image/WritableImage BARU jika
     * argumen kedua null. Dipanggil ~8x/detik selama kamera menyala, alokasi baru terus-menerus
     * ini terbukti membuat native memory penuh dan JVM crash (Out of Memory native) setelah
     * aplikasi berjalan cukup lama (lihat hs_err_pid*.log).
     * <p>
     * CATATAN UNTUK PERBAIKAN BERIKUTNYA: fix ini sudah 3x hilang lagi tiap kali ada
     * perubahan lain di file ini. Kalau mengedit ulang ScanQrController.java, JANGAN hapus
     * bufferKameraA/bufferKameraB/tulisKeBufferA ini atau kembalikan toFXImage(frame, null).
     */
    private WritableImage bufferKameraA;
    private WritableImage bufferKameraB;
    private boolean tulisKeBufferA = true;

    private final AtomicReference<String> tokenTerakhir = new AtomicReference<>("");
    private final AtomicLong waktuScanTerakhirMs = new AtomicLong(0);

    private int hitungGagalLokal = 0;

    @FXML
    public void initialize() {
        isiInfoPengguna();
        isiTanggalHariIni();
        muatRekapDanRiwayat();
        wireNavigasi();
        terapkanClipBundarKamera();

        btnInputManual.setOnAction(e -> prosesInputManual());
        fieldInputManual.setOnAction(e -> prosesInputManual());

        mulaiKamera();
    }

    /**
     * ImageView tidak ikut membaca -fx-background-radius milik StackPane induknya
     * (scanqr-camera-frame), sehingga video kamera selalu tampil bersudut tajam
     * menutupi sudut bundar frame. Diakali dengan memasang clip Rectangle bersudut
     * melengkung langsung ke ImageView, radius disamakan dengan CSS (24px -> arc 48).
     */
    private void terapkanClipBundarKamera() {
        double lebar = imageViewKamera.getFitWidth();
        double tinggi = imageViewKamera.getFitHeight();
        Rectangle clip = new Rectangle(lebar, tinggi);
        clip.setArcWidth(48);
        clip.setArcHeight(48);
        imageViewKamera.setClip(clip);
    }

    // ========================= Info pengguna & tanggal =========================

    private void isiInfoPengguna() {
        Pengguna pengguna = SessionManager.getPenggunaAktif();
        String nama = (pengguna != null && pengguna.getNama() != null) ? pengguna.getNama() : "Pengguna";
        String peran = (pengguna != null && pengguna.getRole() != null) ? pengguna.getRole().getLabel() : "";

        labelNamaUser.setText(nama);
        labelRoleUser.setText(peran);
        labelInisialUser.setText(inisial(nama));
        labelSidebarSub.setText("Sistem Informasi Absensi Pelajar");
    }

    private String inisial(String nama) {
        if (nama == null || nama.isBlank()) {
            return "?";
        }
        String[] bagian = nama.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(2, bagian.length); i++) {
            if (!bagian[i].isEmpty()) {
                sb.append(Character.toUpperCase(bagian[i].charAt(0)));
            }
        }
        return sb.toString();
    }

    private void isiTanggalHariIni() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d MMMM yyyy", new Locale("id", "ID"));
        LocalDate hariIni = LocalDate.now();
        String namaHari = hariIni.getDayOfWeek().getDisplayName(TextStyle.FULL, new Locale("id", "ID"));
        labelTanggal.setText(Character.toUpperCase(namaHari.charAt(0)) + namaHari.substring(1)
                + ", " + hariIni.format(formatter));
    }

    // ========================= Navigasi sidebar =========================

    private void wireNavigasi() {
        btnNavDashboard.setOnAction(e -> navigasiKe("/com/nurulislam/siap/fxml/Dashboard.fxml", "Dashboard"));
        btnNavVerifikasi.setOnAction(e -> navigasiKe("/com/nurulislam/siap/fxml/VerifikasiAkun.fxml", "Verifikasi Akun"));
        btnNavScanQr.setOnAction(e -> { /* sudah di halaman Scan QR */ });
        btnNavDataMurid.setOnAction(e -> navigasiKe("/com/nurulislam/siap/fxml/ManajemenDataMurid.fxml", "Data Murid"));
        btnNavDataKelas.setOnAction(e -> navigasiKe("/com/nurulislam/siap/fxml/ManajemenDataKelas.fxml", "Data Kelas"));
        btnNavMataPelajaran.setOnAction(e -> navigasiKe("/com/nurulislam/siap/fxml/ManajemenMataPelajaran.fxml", "Mata Pelajaran"));
        btnNavCetakKartu.setOnAction(e -> navigasiKe("/com/nurulislam/siap/fxml/ManajemenCetakKartu.fxml", "Cetak Kartu Pelajar"));
        btnNavLaporan.setOnAction(e -> navigasiKe("/com/nurulislam/siap/fxml/LaporanKehadiran.fxml", "Laporan"));
        ProfileMenu.pasang(avatarBox, this::bukaProfil, this::handleLogout);
        AvatarUtil.tampilkan(avatarBox, imgAvatar, labelInisialUser);
        BrandLogo.pasang(imgLogo);
    }

    private void navigasiKe(String fxml, String judul) {
        hentikanKamera();
        try {
            SceneManager.switchTo(fxml, Main.APP_TITLE + " - " + judul);
        } catch (IOException e) {
            System.err.println("[ScanQrController] Gagal berpindah halaman: " + e.getMessage());
        }
    }

    private void tampilkanBelumTersedia(String namaFitur) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(namaFitur);
        alert.setHeaderText(null);
        alert.setContentText("Fitur \"" + namaFitur + "\" akan dibuat pada tahap pengembangan berikutnya.");
        alert.showAndWait();
    }

    /** Membuka halaman Profil Saya (diakses dari menu avatar kanan atas). */
    private void bukaProfil() {
        try {
            SceneManager.switchTo("/com/nurulislam/siap/fxml/PengaturanAkun.fxml",
                    Main.APP_TITLE + " - Profil Saya");
        } catch (IOException e) {
            System.err.println("[ScanQrController] Gagal membuka Profil Saya: " + e.getMessage());
        }
    }

    private void handleLogout() {
        hentikanKamera();
        SessionManager.logout();
        try {
            SceneManager.switchTo("/com/nurulislam/siap/fxml/Login.fxml", Main.APP_TITLE + " - Masuk");
        } catch (IOException e) {
            System.err.println("[ScanQrController] Gagal kembali ke halaman Login: " + e.getMessage());
        }
    }

    // ========================= Kamera & pemindaian QR =========================

    private void mulaiKamera() {
        labelKameraOverlay.setText("Menyiapkan kamera...");
        // Deteksi & buka kamera di background agar UI tidak freeze saat driver kamera lambat merespons.
        // PENTING: pakai OpenCV (JavaCV), BUKAN webcam-capture (Sarxos) - Sarxos terbukti
        // sering menghasilkan frame rusak/warna solid (mis. oranye polos) di banyak kamera
        // Windows modern (MJPEG/YUY2).
        // Backend dicoba berurutan: MSMF -> DSHOW -> ANY. MSMF didahulukan karena DSHOW
        // butuh COM diinisialisasi sebagai STA pada thread yang memanggilnya; dipanggil dari
        // background thread biasa (bukan STA), DSHOW sering gagal dengan
        // "raised unknown C++ exception" walau kamera sebenarnya ada.
        Thread pembuka = new Thread(() -> {
            VideoCapture capture = bukaKameraDenganFallback();
            boolean berhasil = capture != null && capture.isOpened();
            if (berhasil) {
                capture.set(CAP_PROP_FRAME_WIDTH, LEBAR_FRAME);
                capture.set(CAP_PROP_FRAME_HEIGHT, TINGGI_FRAME);
            }
            if (capture == null) {
                capture = new VideoCapture();
            }

            boolean finalBerhasil = berhasil;
            VideoCapture finalCapture = capture;
            Platform.runLater(() -> {
                if (!finalBerhasil) {
                    labelKameraOverlay.setText("Kamera tidak ditemukan. Gunakan input manual di bawah.");
                    labelChipKamera.setText("KAMERA TIDAK AKTIF");
                    dotStatusKamera.getStyleClass().add("scanqr-status-dot-off");
                    labelStatusKamera.setText("Kamera: Tidak tersedia");
                    finalCapture.release();
                } else {
                    this.videoCapture = finalCapture;
                    labelKameraOverlay.setText("");
                    labelChipKamera.setText("KAMERA AKTIF");
                    labelStatusKamera.setText("Kamera: Aktif");
                    mulaiThreadKamera();
                }
            });
        }, "scanqr-pembuka-kamera");
        pembuka.setDaemon(true);
        pembuka.start();
    }

    /** Coba buka kamera index 0 berurutan lewat beberapa backend OpenCV sampai berhasil. */
    private VideoCapture bukaKameraDenganFallback() {
        int[] backendYangDicoba = { CAP_MSMF, CAP_DSHOW, CAP_ANY };
        for (int backend : backendYangDicoba) {
            VideoCapture capture = null;
            try {
                capture = new VideoCapture();
                capture.open(0, backend);
                if (capture.isOpened()) {
                    return capture;
                }
            } catch (UnsatisfiedLinkError err) {
                return null;
            } catch (Exception ignored) {
                // lanjut coba backend berikutnya
            }
            if (capture != null) {
                try { capture.release(); } catch (Throwable ignored) {}
            }
        }
        return null;
    }

    private void mulaiThreadKamera() {
        kameraJalan.set(true);

        // FIX 43: converter OpenCV dibuat hanya setelah halaman berhasil dibuka.
        // Ini mencegah UnsatisfiedLinkError saat FXMLLoader membuat controller.
        try {
            matConverter = new OpenCVFrameConverter.ToMat();
            java2dConverter = new Java2DFrameConverter();
        } catch (UnsatisfiedLinkError err) {
            String detail = err.getMessage() == null ? err.toString() : err.getMessage();
            kameraJalan.set(false);
            String detailKegagalanKamera = "Library OpenCV Windows tidak dapat dimuat: " + detail;
            Platform.runLater(() -> {
                try {
                    tampilkanStatusKameraGagal(
                            detailKegagalanKamera + " Gunakan input NIS manual atau pasang Microsoft Visual C++ Redistributable x64.",
                            "Kamera: Library OpenCV bermasalah"
                    );
                } catch (Exception ignored) {
                    // controller sedang ditutup
                }
            });
            return;
        }
        threadKamera = new Thread(() -> {
            Mat mat = new Mat();
            while (kameraJalan.get()) {
                try {
                    if (videoCapture == null || !videoCapture.isOpened()) {
                        break;
                    }
                    if (!videoCapture.read(mat) || mat.empty()) {
                        continue;
                    }

                    Frame frame = matConverter.convert(mat);
                    BufferedImage bufferedFrame = java2dConverter.convert(frame);
                    if (bufferedFrame == null) {
                        continue;
                    }

                    // Pakai ulang salah satu dari 2 buffer (bergantian, bukan null) supaya tidak
                    // alokasi Image/native buffer baru tiap frame - lihat catatan di deklarasi field.
                    WritableImage bufferTulis = tulisKeBufferA ? bufferKameraA : bufferKameraB;
                    if (bufferTulis == null
                            || (int) bufferTulis.getWidth() != bufferedFrame.getWidth()
                            || (int) bufferTulis.getHeight() != bufferedFrame.getHeight()) {
                        bufferTulis = new WritableImage(bufferedFrame.getWidth(), bufferedFrame.getHeight());
                        if (tulisKeBufferA) {
                            bufferKameraA = bufferTulis;
                        } else {
                            bufferKameraB = bufferTulis;
                        }
                    }
                    javafx.scene.image.Image fxImage = SwingFXUtils.toFXImage(bufferedFrame, bufferTulis);
                    tulisKeBufferA = !tulisKeBufferA;
                    Platform.runLater(() -> imageViewKamera.setImage(fxImage));

                    String token = coba_decode_qr(bufferedFrame);
                    if (token != null) {
                        long sekarang = System.currentTimeMillis();
                        boolean tokenBaru = !token.equals(tokenTerakhir.get());
                        boolean jedaLewat = (sekarang - waktuScanTerakhirMs.get()) > JEDA_ANTAR_SCAN_MS;
                        if (tokenBaru || jedaLewat) {
                            tokenTerakhir.set(token);
                            waktuScanTerakhirMs.set(sekarang);
                            Platform.runLater(() -> prosesHasilScan(token));
                        }
                    }
                } catch (Exception e) {
                    // Frame gagal dibaca/didekode - abaikan, lanjut ke frame berikutnya.
                }

                try {
                    Thread.sleep(120);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
            mat.release();
        }, "scanqr-kamera-thread");
        threadKamera.setDaemon(true);
        threadKamera.start();
    }

    private String coba_decode_qr(BufferedImage frame) {
        try {
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(frame)));
            Result result = new MultiFormatReader().decode(bitmap);
            return result.getText();
        } catch (NotFoundException nf) {
            return null;
        } catch (Exception ex) {
            return null;
        }
    }

    private void hentikanKamera() {
        kameraJalan.set(false);
        if (threadKamera != null) {
            threadKamera.interrupt();
        }
        if (videoCapture != null && videoCapture.isOpened()) {
            videoCapture.release();
        }
        videoCapture = null;
        bufferKameraA = null;
        bufferKameraB = null;
    }

    // ========================= Input manual (fallback) =========================

    private void prosesInputManual() {
        String teks = fieldInputManual.getText();
        if (teks == null || teks.isBlank()) {
            return;
        }
        fieldInputManual.clear();
        prosesHasilScan(teks.trim());
    }

    // ========================= Proses hasil scan =========================

    private void prosesHasilScan(String kodeMentah) {
        try {
            Optional<Murid> muridOpt = muridDAO.findByQrToken(kodeMentah);
            if (muridOpt.isEmpty()) {
                muridOpt = muridDAO.findByNis(kodeMentah);
            }

            if (muridOpt.isEmpty()) {
                hitungGagalLokal++;
                labelRekapGagal.setText(String.valueOf(hitungGagalLokal));
                tampilkanHasilGagal("QR Tidak Aktif", "QR/NIS tidak terdaftar atau siswa sudah berstatus PINDAH, LULUS, atau NONAKTIF.");
                tambahRiwayatGagal("QR Tidak Aktif", LocalTime.now());
                return;
            }

            Murid murid = muridOpt.get();
            LocalTime sekarang = LocalTime.now();

            // Tetap gunakan sesi terakhir yang sudah dimulai.
            // Setelah batas_terlambat, sesi tidak hilang: siswa tetap dapat
            // melakukan absensi dan statusnya menjadi TERLAMBAT.
            List<Sesiabsensi> semuaSesi = sesiAbsensiDAO.findAll();
            if (semuaSesi.isEmpty()) {
                tampilkanHasilGagal(
                        "Sesi Belum Diatur",
                        "Belum ada Sesi Absensi terdaftar di sistem."
                );
                return;
            }

            Sesiabsensi sesi = pilihSesiUntukWaktu(sekarang, semuaSesi);

            // Sebelum jam masuk, absensi belum boleh dicatat.
            if (sekarang.isBefore(sesi.getJamMasuk())) {
                tampilkanHasilGagal(
                        "Absensi Belum Dibuka",
                        "Sesi " + sesi.getNamaSesi()
                                + " belum dimulai. Absensi dibuka pukul "
                                + sesi.getJamMasuk() + "."
                );
                return;
            }

            // Setelah batas terlambat, tentukanStatus() menghasilkan TERLAMBAT.
            StatusAbsensi status = sesi.tentukanStatus(sekarang);

            Absensi absensi = new Absensi();
            absensi.setMuridId(murid.getMuridId());
            absensi.setKelasId(murid.getKelasId());
            absensi.setSesiAbsensiId(sesi.getSesiAbsensiId());
            Pengguna pengguna = SessionManager.getPenggunaAktif();
            absensi.setPenggunaId(pengguna != null ? pengguna.getPenggunaId() : 0);
            absensi.setTanggal(LocalDate.now());
            absensi.setWaktuMasuk(sekarang);
            absensi.setStatus(status);

            absensiDAO.insert(absensi);

            tampilkanHasilBerhasil(murid, sekarang, status);
            tambahRiwayatBerhasil(murid, sekarang, status);
            perbaruiRekap(status, 1);

        } catch (AbsensiSudahAdaException sudahAda) {
            if (!cobaUpgradeAlfaOtomatis(kodeMentah)) {
                tampilkanHasilGagal("Sudah Absen", "Murid ini sudah tercatat absen untuk sesi hari ini.");
            }
        } catch (SQLException sql) {
            tampilkanHasilGagal("Gagal Menyimpan", "Terjadi kendala koneksi database.");
            System.err.println("[ScanQrController] SQLException: " + sql.getMessage());
        }
    }

    /**
     * Scan susulan: bila murid sudah punya baris ALFA otomatis hari ini
     * (dibuat sistem karena belum absen), upgrade baris itu menjadi status
     * hasil scan (HADIR/TERLAMBAT) alih-alih menolak dengan "Sudah Absen".
     * Baris ALFA manual TU tidak ikut di-upgrade.
     *
     * @return true bila upgrade berhasil (hasil sudah ditampilkan)
     */
    private boolean cobaUpgradeAlfaOtomatis(String kodeMentah) {
        try {
            Optional<Murid> muridOpt = muridDAO.findByQrToken(kodeMentah);
            if (muridOpt.isEmpty()) {
                muridOpt = muridDAO.findByNis(kodeMentah);
            }
            if (muridOpt.isEmpty()) {
                return false;
            }
            Murid murid = muridOpt.get();
            LocalTime sekarang = LocalTime.now();
            List<Sesiabsensi> semuaSesi = sesiAbsensiDAO.findAll();
            if (semuaSesi.isEmpty()) {
                return false;
            }
            Sesiabsensi sesi = pilihSesiUntukWaktu(sekarang, semuaSesi);
            if (sekarang.isBefore(sesi.getJamMasuk())) {
                return false;
            }
            Optional<Absensi> ada = absensiDAO.findHarian(
                    murid.getMuridId(), sesi.getSesiAbsensiId(), LocalDate.now());
            if (ada.isEmpty() || !ada.get().isOtomatis()
                    || ada.get().getStatus() != StatusAbsensi.ALFA) {
                return false;
            }

            StatusAbsensi status = sesi.tentukanStatus(sekarang);
            Pengguna pengguna = SessionManager.getPenggunaAktif();
            int penggunaId = pengguna != null ? pengguna.getPenggunaId() : 0;
            boolean ok = absensiDAO.upgradeOtomatisKeHadir(
                    ada.get().getAbsensiId(), status, sekarang, penggunaId);
            if (!ok) {
                return false;
            }
            tampilkanHasilBerhasil(murid, sekarang, status);
            tambahRiwayatBerhasil(murid, sekarang, status);
            perbaruiRekap(status, 1);
            return true;
        } catch (SQLException e) {
            System.err.println("[ScanQrController] Gagal upgrade ALFA otomatis: " + e.getMessage());
            return false;
        }
    }

    /**
     * Memilih sesi terakhir yang sudah dimulai.
     * Contoh Sesi Pagi: 06:30-08:00.
     * Scan pukul 10:18 tetap memilih Sesi Pagi sehingga status menjadi TERLAMBAT.
     */    private Sesiabsensi pilihSesiUntukWaktu(LocalTime waktu, List<Sesiabsensi> daftarSesi) {
        Sesiabsensi terpilih = null;

        for (Sesiabsensi sesi : daftarSesi) {
            if (sesi == null || sesi.getJamMasuk() == null) {
                continue;
            }

            if (!waktu.isBefore(sesi.getJamMasuk())) {
                if (terpilih == null
                        || sesi.getJamMasuk().isAfter(terpilih.getJamMasuk())) {
                    terpilih = sesi;
                }
            }
        }

        if (terpilih != null) {
            return terpilih;
        }

        // Belum ada sesi yang dimulai: kembalikan sesi dengan jam paling awal.
        Sesiabsensi palingAwal = null;
        for (Sesiabsensi sesi : daftarSesi) {
            if (sesi == null || sesi.getJamMasuk() == null) {
                continue;
            }
            if (palingAwal == null
                    || sesi.getJamMasuk().isBefore(palingAwal.getJamMasuk())) {
                palingAwal = sesi;
            }
        }
        return palingAwal != null ? palingAwal : daftarSesi.get(0);
    }

    private void perbaruiRekap(StatusAbsensi status, int delta) {
        switch (status) {
            case HADIR -> labelRekapHadir.setText(String.valueOf(Integer.parseInt(labelRekapHadir.getText()) + delta));
            case TERLAMBAT -> labelRekapTerlambat.setText(String.valueOf(Integer.parseInt(labelRekapTerlambat.getText()) + delta));
            case IZIN, SAKIT -> labelRekapIzinSakit.setText(String.valueOf(Integer.parseInt(labelRekapIzinSakit.getText()) + delta));
            default -> { /* ALFA tidak berasal dari scan */ }
        }
    }

    // ========================= Kartu "Hasil Pemindaian Terakhir" =========================

    private void tampilkanHasilBerhasil(Murid murid, LocalTime waktu, StatusAbsensi status) {
        labelWaktuScanTerakhir.setText(waktu.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        labelInisialHasil.setText(inisial(murid.getNama()));
        labelNamaHasil.setText(murid.getNama());
        labelNisHasil.setText("NIS: " + murid.getNis());
        labelKelasHasil.setText("Kelas: " + (murid.getNamaKelas() != null ? murid.getNamaKelas() : "-"));
        labelJamHasil.setText(waktu.format(DateTimeFormatter.ofPattern("HH:mm")) + " WIB");

        labelBadgeHasil.getStyleClass().removeAll("status-badge-hadir", "status-badge-terlambat", "status-badge-izin", "status-badge-alfa");
        if (status == StatusAbsensi.TERLAMBAT) {
            labelBadgeHasil.setText("TERLAMBAT");
            labelBadgeHasil.getStyleClass().add("status-badge-terlambat");
        } else {
            labelBadgeHasil.setText("BERHASIL");
            labelBadgeHasil.getStyleClass().add("status-badge-hadir");
        }
    }

    private void tampilkanHasilGagal(String judul, String keterangan) {
        labelWaktuScanTerakhir.setText(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        labelInisialHasil.setText("!");
        labelNamaHasil.setText(judul);
        labelNisHasil.setText(keterangan);
        labelKelasHasil.setText("");
        labelJamHasil.setText("--:--");

        labelBadgeHasil.getStyleClass().removeAll("status-badge-hadir", "status-badge-terlambat", "status-badge-izin", "status-badge-alfa");
        labelBadgeHasil.setText("GAGAL");
        labelBadgeHasil.getStyleClass().add("status-badge-alfa");
    }

    // ========================= Riwayat scan hari ini =========================

    private void muatRekapDanRiwayat() {
        try {
            LocalDate hariIni = LocalDate.now();
            var rekap = absensiDAO.hitungRekapHariIni(hariIni);
            labelRekapHadir.setText(String.valueOf(rekap.getOrDefault(StatusAbsensi.HADIR, 0)));
            labelRekapTerlambat.setText(String.valueOf(rekap.getOrDefault(StatusAbsensi.TERLAMBAT, 0)));
            int izinSakit = rekap.getOrDefault(StatusAbsensi.IZIN, 0) + rekap.getOrDefault(StatusAbsensi.SAKIT, 0);
            labelRekapIzinSakit.setText(String.valueOf(izinSakit));
            labelRekapGagal.setText("0");

            List<Absensi> riwayat = absensiDAO.findRiwayatHariIni(hariIni);
            riwayatScanBox.getChildren().clear();
            if (riwayat.isEmpty()) {
                Label kosong = new Label("Belum ada data absen masuk hari ini.");
                kosong.getStyleClass().add("log-kelas");
                riwayatScanBox.getChildren().add(kosong);
            } else {
                for (Absensi a : riwayat) {
                    riwayatScanBox.getChildren().add(buatBarisRiwayat(
                            a.getNamaMurid(), a.getNamaKelas(), a.getWaktuMasuk(), a.getStatus()));
                }
            }
        } catch (SQLException e) {
            System.err.println("[ScanQrController] Gagal memuat rekap/riwayat: " + e.getMessage());
        }
    }

    private void tambahRiwayatBerhasil(Murid murid, LocalTime waktu, StatusAbsensi status) {
        HBox baris = buatBarisRiwayat(murid.getNama(), murid.getNamaKelas(), waktu, status);
        riwayatScanBox.getChildren().add(0, baris);
    }

    private void tambahRiwayatGagal(String judul, LocalTime waktu) {
        Label namaLabel = new Label(judul);
        namaLabel.getStyleClass().add("log-nama");
        Label waktuLabel = new Label(waktu.format(DateTimeFormatter.ofPattern("HH:mm")) + " WIB");
        waktuLabel.getStyleClass().add("log-kelas");
        VBox infoBox = new VBox(2, namaLabel, waktuLabel);
        HBox.setHgrow(infoBox, Priority.ALWAYS);

        Label iconGagal = new Label("\u2716");
        iconGagal.getStyleClass().add("status-badge-alfa");

        HBox baris = new HBox(10, infoBox, iconGagal);
        baris.setAlignment(Pos.CENTER_LEFT);
        baris.getStyleClass().add("scanqr-history-item-error");
        riwayatScanBox.getChildren().add(0, baris);
    }

    private HBox buatBarisRiwayat(String nama, String kelas, LocalTime waktu, StatusAbsensi status) {
        Label namaLabel = new Label(nama != null ? nama : "-");
        namaLabel.getStyleClass().add("log-nama");

        String jamText = waktu != null
                ? waktu.format(DateTimeFormatter.ofPattern("HH:mm")) + " WIB"
                : "-";

        Label kelasJamLabel = new Label((kelas != null ? kelas : "-") + " \u2022 " + jamText);
        kelasJamLabel.getStyleClass().add("log-kelas");

        VBox infoBox = new VBox(2, namaLabel, kelasJamLabel);
        HBox.setHgrow(infoBox, Priority.ALWAYS);

        String ikon;
        String style;

        if (status == StatusAbsensi.TERLAMBAT) {
            ikon = "\u23F1";
            style = "status-badge-terlambat";
        } else if (status == StatusAbsensi.HADIR) {
            ikon = "\u2713";
            style = "status-badge-hadir";
        } else if (status == StatusAbsensi.IZIN) {
            ikon = "I";
            style = "status-badge-alfa";
        } else if (status == StatusAbsensi.SAKIT) {
            ikon = "S";
            style = "status-badge-alfa";
        } else {
            ikon = "A";
            style = "status-badge-alfa";
        }

        Label statusIcon = new Label(ikon);
        statusIcon.getStyleClass().add(style);

        HBox baris = new HBox(10, infoBox, statusIcon);
        baris.setAlignment(Pos.CENTER_LEFT);
        baris.getStyleClass().add("scanqr-history-item");
        return baris;
    }

    private void tampilkanStatusKameraGagal(String overlay, String status) {
        Platform.runLater(() -> {
            labelKameraOverlay.setText(overlay);
            labelChipKamera.setText("KAMERA TIDAK AKTIF");
            if (!dotStatusKamera.getStyleClass().contains("scanqr-status-dot-off")) {
                dotStatusKamera.getStyleClass().add("scanqr-status-dot-off");
            }
            labelStatusKamera.setText(status + ". Gunakan input NIS manual bila diperlukan.");
        });
    }
}