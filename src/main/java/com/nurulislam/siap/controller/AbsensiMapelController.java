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
import com.nurulislam.siap.dao.AbsensiMapelDAO;
import com.nurulislam.siap.dao.AbsensiSudahAdaException;
import com.nurulislam.siap.dao.JadwalMengajarDAO;
import com.nurulislam.siap.dao.MuridDAO;
import com.nurulislam.siap.model.AbsensiMapel;
import com.nurulislam.siap.model.HariMengajar;
import com.nurulislam.siap.model.JadwalMengajar;
import com.nurulislam.siap.model.Murid;
import com.nurulislam.siap.model.Pengguna;
import com.nurulislam.siap.model.Role;
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
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.shape.Rectangle;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

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
 * Controller halaman "Absensi Mata Pelajaran" - absensi per sesi mengajar,
 * dilakukan oleh Guru (atau dipantau oleh Staf TU) lewat pemindaian QR Code
 * murid pada kelas yang sedang diajar.
 * <p>
 * Alur: pengguna memilih sesi mengajar hari ini lewat {@code comboJadwal}
 * (Guru hanya melihat jadwalnya sendiri; Staf TU melihat semua jadwal hari
 * ini). Setiap murid yang dipindai divalidasi harus berasal dari kelas pada
 * jadwal terpilih sebelum dicatat ke tb_absensi_mapel. Status HADIR/TERLAMBAT
 * ditentukan dari jam_mulai sesi ditambah toleransi keterlambatan.
 */
public class AbsensiMapelController {

    // FIX BUG 37: feedback kamera dibuat lebih jelas saat gagal dibuka/terputus.

    private static final int LEBAR_FRAME = 640;
    private static final int TINGGI_FRAME = 480;
    private static final long JEDA_ANTAR_SCAN_MS = 4000;
    private static final int TOLERANSI_TERLAMBAT_MENIT = 15;

    // --- Sidebar ---
    @FXML private Label labelSidebarSub;
    @FXML private Button btnNavBeranda;
    @FXML private Button btnNavJadwal;
    @FXML private Button btnNavAbsensiMapel;
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

    // --- Pilihan sesi ---
    @FXML private ComboBox<JadwalMengajar> comboJadwal;
    @FXML private Button btnRefreshJadwal;

    // --- Kartu info sesi ---
    @FXML private Label labelInfoMapel;
    @FXML private Label labelInfoJam;
    @FXML private Label labelInfoKelasGuru;
    @FXML private Label labelInfoStatus;

    // --- Rekap ---
    @FXML private Label labelRekapHadir;
    @FXML private Label labelRekapIzinSakit;
    @FXML private Label labelRekapTerlambat;
    @FXML private Label labelRekapAlfa;
    @FXML private Label labelRekapBelumAbsen;

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
    @FXML private Label labelJamHasil;
    @FXML private Label labelBadgeHasil;

    // --- Absen masuk ---
    @FXML private VBox absenMasukBox;

    private final JadwalMengajarDAO jadwalMengajarDAO = new JadwalMengajarDAO();
    private final AbsensiMapelDAO absensiMapelDAO = new AbsensiMapelDAO();
    private final AbsensiDAO absensiDAO = new AbsensiDAO();
    private final MuridDAO muridDAO = new MuridDAO();

    /*
     * Kamera pakai OpenCV lewat JavaCV, pola & fix yang sama persis dengan
     * ScanQrController (halaman Scan QR gerbang) supaya konsisten:
     * - bukaKameraDenganFallback(): coba backend MSMF -> DSHOW -> ANY berurutan.
     * - Preview pakai 2 buffer WritableImage dipakai bergantian (double buffering),
     *   BUKAN toFXImage(frame, null) - itu alokasi native memory baru tiap frame
     *   (~8x/detik) dan pernah terbukti bikin JVM crash kalau kamera dibiarkan lama.
     * Kalau di komputer ini DLL native OpenCV gagal load (UnsatisfiedLinkError,
     * biasanya karena Visual C++ Redistributable belum terpasang di Windows),
     * bukaKameraDenganFallback() akan return null dan halaman tetap terbuka
     * normal dengan fallback ke input NIS manual - tidak membuat FXML gagal dimuat.
     */
    private VideoCapture videoCapture;
    private OpenCVFrameConverter.ToMat matConverter;
    private Java2DFrameConverter java2dConverter;
    private Thread threadKamera;
    private final AtomicBoolean kameraJalan = new AtomicBoolean(false);
    private WritableImage bufferKameraA;
    private WritableImage bufferKameraB;
    private boolean tulisKeBufferA = true;

    private final AtomicReference<String> tokenTerakhir = new AtomicReference<>("");
    private final AtomicLong waktuScanTerakhirMs = new AtomicLong(0);

    private JadwalMengajar jadwalTerpilih;

    @FXML
    public void initialize() {
        isiInfoPengguna();
        isiTanggalHariIni();
        wireNavigasi();
        siapkanComboJadwal();
        terapkanClipBundarKamera();

        btnInputManual.setOnAction(e -> prosesInputManual());
        btnRefreshJadwal.setOnAction(e -> muatDaftarJadwalHariIni());
        fieldInputManual.setOnAction(e -> prosesInputManual());

        muatDaftarJadwalHariIni();
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
        btnNavBeranda.setOnAction(e -> navigasiKe(halamanBerandaSesuaiRole(), "Dashboard"));
        btnNavJadwal.setOnAction(e -> bukaJadwalMengajar());
        btnNavAbsensiMapel.setOnAction(e -> { /* sudah di halaman Absensi Mata Pelajaran */ });
        btnNavLaporan.setOnAction(e -> bukaLaporan());
        ProfileMenu.pasang(avatarBox, this::bukaProfil, this::handleLogout);
        AvatarUtil.tampilkan(avatarBox, imgAvatar, labelInisialUser);
        BrandLogo.pasang(imgLogo);
    }

    private String halamanBerandaSesuaiRole() {
        Pengguna pengguna = SessionManager.getPenggunaAktif();
        boolean guru = pengguna != null && pengguna.getRole() == Role.GURU;
        return guru ? "/com/nurulislam/siap/fxml/DashboardGuru.fxml" : "/com/nurulislam/siap/fxml/Dashboard.fxml";
    }

    private void navigasiKe(String fxml, String judul) {
        hentikanKamera();
        try {
            SceneManager.switchTo(fxml, Main.APP_TITLE + " - " + judul);
        } catch (IOException e) {
            System.err.println("[AbsensiMapelController] Gagal berpindah halaman: " + e.getMessage());
        }
    }

    private void tampilkanBelumTersedia(String namaFitur) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(namaFitur);
        alert.setHeaderText(null);
        alert.setContentText("Fitur \"" + namaFitur + "\" akan dibuat pada tahap pengembangan berikutnya.");
        alert.showAndWait();
    }

    /** Laporan rekap absensi mata pelajaran saat ini khusus untuk akun Guru. */
    /** Jadwal Mengajar khusus akun Guru (bukan TU) - lihat JadwalGuruController. */
    private void bukaJadwalMengajar() {
        Pengguna pengguna = SessionManager.getPenggunaAktif();
        if (pengguna != null && pengguna.getRole() == Role.GURU) {
            navigasiKe("/com/nurulislam/siap/fxml/JadwalGuru.fxml", "Jadwal Mengajar");
        } else {
            tampilkanBelumTersedia("Jadwal Mengajar");
        }
    }

    private void bukaLaporan() {
        Pengguna pengguna = SessionManager.getPenggunaAktif();
        if (pengguna != null && pengguna.getRole() == Role.GURU) {
            navigasiKe("/com/nurulislam/siap/fxml/LaporanMapel.fxml", "Laporan");
        } else {
            tampilkanBelumTersedia("Laporan");
        }
    }

    /** Membuka halaman Profil Saya (diakses dari menu avatar kanan atas). */
    private void bukaProfil() {
        try {
            SceneManager.switchTo("/com/nurulislam/siap/fxml/PengaturanAkun.fxml",
                    Main.APP_TITLE + " - Profil Saya");
        } catch (IOException e) {
            System.err.println("[AbsensiMapelController] Gagal membuka Profil Saya: " + e.getMessage());
        }
    }

    private void handleLogout() {
        hentikanKamera();
        SessionManager.logout();
        try {
            SceneManager.switchTo("/com/nurulislam/siap/fxml/Login.fxml", Main.APP_TITLE + " - Masuk");
        } catch (IOException e) {
            System.err.println("[AbsensiMapelController] Gagal kembali ke halaman Login: " + e.getMessage());
        }
    }

    // ========================= Pemilihan sesi mengajar =========================

    private void siapkanComboJadwal() {
        comboJadwal.setConverter(new StringConverter<>() {
            @Override
            public String toString(JadwalMengajar j) {
                if (j == null) {
                    return "";
                }
                String jam = j.getJamMulai() + " - " + j.getJamSelesai();
                Pengguna pengguna = SessionManager.getPenggunaAktif();
                boolean tampilkanGuru = pengguna == null || pengguna.getRole() != Role.GURU;
                return j.getNamaMapel() + " - " + j.getNamaKelas() + " (" + jam + ")"
                        + (tampilkanGuru ? " - " + j.getNamaGuru() : "");
            }

            @Override
            public JadwalMengajar fromString(String string) {
                return null;
            }
        });

        comboJadwal.getSelectionModel().selectedItemProperty().addListener((obs, lama, baru) -> {
            if (baru != null) {
                pilihJadwal(baru);
            }
        });
    }

    private void muatDaftarJadwalHariIni() {
        HariMengajar hariIni = HariMengajar.dariDayOfWeek(LocalDate.now().getDayOfWeek());
        if (hariIni == null) {
            tampilkanTidakAdaJadwal("Tidak ada jadwal mengajar pada hari Minggu.");
            return;
        }

        try {
            Pengguna pengguna = SessionManager.getPenggunaAktif();
            List<JadwalMengajar> daftar;
            if (pengguna != null && pengguna.getRole() == Role.GURU) {
                daftar = jadwalMengajarDAO.findHariIniByGuru(pengguna.getPenggunaId(), hariIni);
            } else {
                daftar = jadwalMengajarDAO.findHariIniSemua(hariIni);
            }

            if (daftar.isEmpty()) {
                tampilkanTidakAdaJadwal("Tidak ada jadwal mengajar hari ini.");
                return;
            }

            comboJadwal.getItems().setAll(daftar);
            comboJadwal.setDisable(false);

            JadwalMengajar dipilih = cariSesiPalingRelevan(daftar);
            comboJadwal.getSelectionModel().select(dipilih);
            // Jika sesi yang dipilih sama dengan item pertama, listener tidak otomatis
            // terpicu untuk pemilihan pertama pada beberapa versi JavaFX - panggil langsung.
            pilihJadwal(dipilih);

        } catch (SQLException e) {
            tampilkanTidakAdaJadwal("Gagal memuat jadwal mengajar dari database.");
            System.err.println("[AbsensiMapelController] SQLException muatDaftarJadwalHariIni: " + e.getMessage());
        }
    }

    /** Memilih sesi yang sedang AKTIF; jika tidak ada, sesi berikutnya yang belum mulai; jika tidak ada, sesi terakhir. */
    private JadwalMengajar cariSesiPalingRelevan(List<JadwalMengajar> daftar) {
        LocalTime sekarang = LocalTime.now();
        for (JadwalMengajar j : daftar) {
            if (j.statusPada(sekarang) == JadwalMengajar.StatusSesi.AKTIF) {
                return j;
            }
        }
        for (JadwalMengajar j : daftar) {
            if (j.statusPada(sekarang) == JadwalMengajar.StatusSesi.BELUM_MULAI) {
                return j;
            }
        }
        return daftar.get(daftar.size() - 1);
    }

    private void tampilkanTidakAdaJadwal(String pesan) {
        comboJadwal.getItems().clear();
        comboJadwal.setDisable(true);
        jadwalTerpilih = null;

        labelInfoMapel.setText("-");
        labelInfoJam.setText("-");
        labelInfoKelasGuru.setText("-");
        setStatusBadge("TIDAK ADA JADWAL", "status-badge-izin");

        labelRekapHadir.setText("0");
        labelRekapIzinSakit.setText("0");
        labelRekapTerlambat.setText("0");
        labelRekapAlfa.setText("0");
        labelRekapBelumAbsen.setText("0");

        absenMasukBox.getChildren().clear();
        Label kosong = new Label(pesan);
        kosong.getStyleClass().add("log-kelas");
        absenMasukBox.getChildren().add(kosong);

        fieldInputManual.setDisable(true);
        btnInputManual.setDisable(true);
        labelKameraOverlay.setText(pesan);
    }

    private void pilihJadwal(JadwalMengajar jadwal) {
        this.jadwalTerpilih = jadwal;

        JadwalMengajar.StatusSesi status = jadwal.statusPada(LocalTime.now());
        boolean sesiAktif = status == JadwalMengajar.StatusSesi.AKTIF;
        fieldInputManual.setDisable(!sesiAktif);
        btnInputManual.setDisable(!sesiAktif);

        labelInfoMapel.setText(jadwal.getNamaMapel());
        labelInfoJam.setText(jadwal.getJamMulai() + " - " + jadwal.getJamSelesai());
        labelInfoKelasGuru.setText(jadwal.getNamaKelas() + " - " + jadwal.getNamaGuru());

        String styleClass = switch (status) {
            case AKTIF -> "status-badge-hadir";
            case BELUM_MULAI -> "status-badge-izin";
            case SELESAI -> "status-badge-alfa";
        };
        setStatusBadge(status.getLabel().toUpperCase(Locale.ROOT), styleClass);

        muatRekapDanAbsenMasuk();
    }

    private void setStatusBadge(String teks, String styleClass) {
        labelInfoStatus.setText(teks);
        labelInfoStatus.getStyleClass().removeAll(
                "status-badge-hadir", "status-badge-terlambat", "status-badge-izin", "status-badge-alfa");
        labelInfoStatus.getStyleClass().add(styleClass);
    }

    // ========================= Kamera & pemindaian QR =========================

    // ========================= Kamera & pemindaian QR =========================

    /**
     * Buka kamera di background thread (biar UI tidak freeze kalau driver kamera lambat
     * merespons), lalu kalau berhasil lanjut ke {@link #mulaiThreadKamera()}. Kalau gagal
     * di semua backend (mis. DLL native OpenCV belum lengkap di komputer ini), halaman
     * tetap terbuka normal - cuma kamera yang tidak aktif, TU/Guru tetap bisa pakai
     * input NIS manual di bawah kolom kamera.
     */
    private void mulaiKamera() {
        labelKameraOverlay.setText("Menyiapkan kamera...");
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
                    tampilkanStatusKameraGagal(
                            "Kamera tidak ditemukan. Gunakan input manual di bawah.",
                            "Kamera: Tidak tersedia");
                    finalCapture.release();
                } else {
                    this.videoCapture = finalCapture;
                    labelKameraOverlay.setText("");
                    labelChipKamera.setText("KAMERA AKTIF");
                    dotStatusKamera.getStyleClass().removeAll("scanqr-status-dot-off");
                    if (!dotStatusKamera.getStyleClass().contains("scanqr-status-dot-on")) {
                        dotStatusKamera.getStyleClass().add("scanqr-status-dot-on");
                    }
                    labelStatusKamera.setText("Kamera: Aktif");
                    mulaiThreadKamera();
                }
            });
        }, "absensimapel-pembuka-kamera");
        pembuka.setDaemon(true);
        pembuka.start();
    }

    /** Coba buka kamera index 0 berurutan lewat beberapa backend OpenCV sampai berhasil. */
    private VideoCapture bukaKameraDenganFallback() {
        int[] backendYangDicoba = { CAP_MSMF, CAP_DSHOW, CAP_ANY };
        for (int backend : backendYangDicoba) {
            VideoCapture capture = new VideoCapture();
            try {
                capture.open(0, backend);
                if (capture.isOpened()) {
                    return capture;
                }
            } catch (Throwable ignored) {
                // Termasuk UnsatisfiedLinkError/NoClassDefFoundError kalau DLL native
                // OpenCV belum lengkap di komputer ini - jangan sampai membuat
                // FXMLLoader gagal memuat seluruh halaman, cukup kamera saja yang mati.
            }
            try {
                capture.release();
            } catch (Throwable ignored) {
                // idem
            }
        }
        return null;
    }

    /**
     * Loop pembacaan frame di background thread. Preview kamera memakai 2 buffer
     * WritableImage yang dipakai ULANG bergantian (double buffering) - lihat catatan
     * di deklarasi field di atas kenapa ini penting untuk mencegah native memory leak.
     */
    private void mulaiThreadKamera() {
        kameraJalan.set(true);

        // Converter OpenCV dibuat hanya setelah halaman berhasil dibuka dan kamera
        // terbukti bisa diakses (BUKAN sebagai field initializer) - supaya konstruksi
        // AbsensiMapelController oleh FXMLLoader tidak ikut gagal kalau DLL native
        // OpenCV bermasalah di komputer ini. Pola sama persis dengan ScanQrController.
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
        }, "absensimapel-kamera-thread");
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

    private void tampilkanStatusKameraGagal(String overlay, String status) {
        Platform.runLater(() -> {
            labelKameraOverlay.setText(overlay);
            labelChipKamera.setText("KAMERA TIDAK AKTIF");
            dotStatusKamera.getStyleClass().removeAll("scanqr-status-dot-on");
            if (!dotStatusKamera.getStyleClass().contains("scanqr-status-dot-off")) {
                dotStatusKamera.getStyleClass().add("scanqr-status-dot-off");
            }
            labelStatusKamera.setText(status + ". Gunakan input NIS manual bila diperlukan.");
        });
    }

    private void hentikanKamera() {
        kameraJalan.set(false);
        if (threadKamera != null) {
            threadKamera.interrupt();
            threadKamera = null;
        }
        if (videoCapture != null && videoCapture.isOpened()) {
            videoCapture.release();
        }
        videoCapture = null;
        bufferKameraA = null;
        bufferKameraB = null;
        imageViewKamera.setImage(null);
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
        if (jadwalTerpilih == null) {
            tampilkanHasilGagal("Belum Ada Sesi", "Pilih sesi mengajar terlebih dahulu.");
            return;
        }

        // Validasi waktu dilakukan SEBELUM mencari murid/menyimpan absensi.
        // Dengan demikian QR kamera dan input NIS manual sama-sama wajib berada
        // di dalam rentang jam_mulai sampai jam_selesai jadwal terpilih.
        JadwalMengajar.StatusSesi statusSesi = jadwalTerpilih.statusPada(LocalTime.now());
        if (statusSesi == JadwalMengajar.StatusSesi.BELUM_MULAI) {
            tampilkanHasilGagal(
                    "Absensi Belum Dibuka",
                    "Sesi " + jadwalTerpilih.getNamaMapel() + " belum dimulai. "
                            + "Absensi dibuka pukul " + jadwalTerpilih.getJamMulai() + "."
            );
            return;
        }
        if (statusSesi == JadwalMengajar.StatusSesi.SELESAI) {
            tampilkanHasilGagal(
                    "Sesi Sudah Selesai",
                    "Absensi " + jadwalTerpilih.getNamaMapel() + " sudah ditutup pukul "
                            + jadwalTerpilih.getJamSelesai() + "."
            );
            return;
        }

        try {
            Optional<Murid> muridOpt = muridDAO.findByQrToken(kodeMentah);
            if (muridOpt.isEmpty()) {
                muridOpt = muridDAO.findByNis(kodeMentah);
            }

            if (muridOpt.isEmpty()) {
                tampilkanHasilGagal("QR Tak Dikenal", "Kode tidak terdaftar di data murid.");
                return;
            }

            Murid murid = muridOpt.get();
            if (murid.getKelasId() != jadwalTerpilih.getKelasId()) {
                tampilkanHasilGagal("Bukan Kelas Ini",
                        murid.getNama() + " terdaftar di kelas " + murid.getNamaKelas()
                                + ", bukan " + jadwalTerpilih.getNamaKelas() + ".");
                return;
            }

            // Kunci anti titip absen: ikuti status absensi harian (gerbang) hari ini.
            // Bila TU/Admin menetapkan ALFA/IZIN/SAKIT dan belum diubah, scan mapel
            // otomatis tercatat dengan status yang sama - kartu orang lain yang
            // di-scan-kan tidak bisa menghasilkan HADIR.
            LocalDate hariIni = LocalDate.now();
            StatusAbsensi statusHarian = null;
            try {
                statusHarian = absensiDAO.findStatusHarian(murid.getMuridId(), hariIni)
                        .orElse(null);
            } catch (SQLException e) {
                System.err.println("[AbsensiMapelController] Gagal cek status harian: "
                        + e.getMessage());
            }
            boolean terkunciHarian = statusHarian != null
                    && statusHarian != StatusAbsensi.HADIR
                    && statusHarian != StatusAbsensi.TERLAMBAT;

            LocalTime sekarang = LocalTime.now();
            StatusAbsensi status = terkunciHarian ? statusHarian : tentukanStatus(sekarang);

            AbsensiMapel absensi = new AbsensiMapel();
            absensi.setMuridId(murid.getMuridId());
            absensi.setJadwalId(jadwalTerpilih.getJadwalId());
            Pengguna pengguna = SessionManager.getPenggunaAktif();
            absensi.setPenggunaId(pengguna != null ? pengguna.getPenggunaId() : 0);
            absensi.setTanggal(hariIni);
            absensi.setWaktuScan(sekarang);
            absensi.setStatus(status);

            absensiMapelDAO.insert(absensi);

            tampilkanHasilBerhasil(murid, sekarang, status);
            tambahBarisAbsenMasuk(murid.getNama(), murid.getNis(), sekarang, status);
            perbaruiRekap(status, 1);

            if (terkunciHarian) {
                Alert terkunci = new Alert(Alert.AlertType.WARNING);
                terkunci.setTitle("Status Mengikuti Absensi Harian");
                terkunci.setHeaderText(null);
                terkunci.setContentText("Status harian " + murid.getNama() + " hari ini adalah "
                        + capitalisasi(statusHarian.name()) + " (ditetapkan Staf TU), sehingga "
                        + "scan mata pelajaran ini tercatat " + capitalisasi(statusHarian.name())
                        + ", bukan Hadir.");
                terkunci.showAndWait();
            }

        } catch (AbsensiSudahAdaException sudahAda) {
            tampilkanHasilGagal("Sudah Absen", sudahAda.getMessage());
        } catch (SQLException sql) {
            tampilkanHasilGagal("Gagal Menyimpan", "Terjadi kendala koneksi database.");
            System.err.println("[AbsensiMapelController] SQLException: " + sql.getMessage());
        }
    }

    /** HADIR jika masih dalam toleransi (jam_mulai + 15 menit), TERLAMBAT jika melewati itu. */
    private StatusAbsensi tentukanStatus(LocalTime waktuScan) {
        LocalTime batasTerlambat = jadwalTerpilih.getJamMulai().plusMinutes(TOLERANSI_TERLAMBAT_MENIT);
        return waktuScan.isAfter(batasTerlambat) ? StatusAbsensi.TERLAMBAT : StatusAbsensi.HADIR;
    }

    private String capitalisasi(String teksEnum) {
        if (teksEnum == null || teksEnum.isEmpty()) {
            return "";
        }
        return teksEnum.charAt(0) + teksEnum.substring(1).toLowerCase();
    }

    private void perbaruiRekap(StatusAbsensi status, int delta) {
        switch (status) {
            case HADIR -> labelRekapHadir.setText(String.valueOf(Integer.parseInt(labelRekapHadir.getText()) + delta));
            case TERLAMBAT -> labelRekapTerlambat.setText(String.valueOf(Integer.parseInt(labelRekapTerlambat.getText()) + delta));
            case IZIN, SAKIT -> labelRekapIzinSakit.setText(String.valueOf(Integer.parseInt(labelRekapIzinSakit.getText()) + delta));
            case ALFA -> labelRekapAlfa.setText(String.valueOf(Integer.parseInt(labelRekapAlfa.getText()) + delta));
        }
    }

    // ========================= Kartu "Hasil Pemindaian Terakhir" =========================

    private void tampilkanHasilBerhasil(Murid murid, LocalTime waktu, StatusAbsensi status) {
        labelWaktuScanTerakhir.setText(waktu.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        labelInisialHasil.setText(inisial(murid.getNama()));
        labelNamaHasil.setText(murid.getNama());
        labelNisHasil.setText("NIS: " + murid.getNis());
        labelJamHasil.setText(waktu.format(DateTimeFormatter.ofPattern("HH:mm")) + " WIB");

        labelBadgeHasil.getStyleClass().removeAll("status-badge-hadir", "status-badge-terlambat", "status-badge-izin", "status-badge-alfa");
        switch (status) {
            case TERLAMBAT -> {
                labelBadgeHasil.setText("TERLAMBAT");
                labelBadgeHasil.getStyleClass().add("status-badge-terlambat");
            }
            case ALFA -> {
                labelBadgeHasil.setText("ALFA");
                labelBadgeHasil.getStyleClass().add("status-badge-alfa");
            }
            case IZIN, SAKIT -> {
                labelBadgeHasil.setText(status.name());
                labelBadgeHasil.getStyleClass().add("status-badge-izin");
            }
            default -> {
                labelBadgeHasil.setText("HADIR");
                labelBadgeHasil.getStyleClass().add("status-badge-hadir");
            }
        }
    }

    private void tampilkanHasilGagal(String judul, String keterangan) {
        labelWaktuScanTerakhir.setText(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        labelInisialHasil.setText("!");
        labelNamaHasil.setText(judul);
        labelNisHasil.setText(keterangan);
        labelJamHasil.setText("--:--");

        labelBadgeHasil.getStyleClass().removeAll("status-badge-hadir", "status-badge-terlambat", "status-badge-izin", "status-badge-alfa");
        labelBadgeHasil.setText("GAGAL");
        labelBadgeHasil.getStyleClass().add("status-badge-alfa");
    }

    // ========================= Daftar "Absen Masuk" =========================

    private void muatRekapDanAbsenMasuk() {
        if (jadwalTerpilih == null) {
            return;
        }
        try {
            LocalDate hariIni = LocalDate.now();
            var rekap = absensiMapelDAO.hitungRekap(jadwalTerpilih.getJadwalId(), hariIni);
            labelRekapHadir.setText(String.valueOf(rekap.getOrDefault(StatusAbsensi.HADIR, 0)));
            labelRekapTerlambat.setText(String.valueOf(rekap.getOrDefault(StatusAbsensi.TERLAMBAT, 0)));
            int izinSakit = rekap.getOrDefault(StatusAbsensi.IZIN, 0) + rekap.getOrDefault(StatusAbsensi.SAKIT, 0);
            labelRekapIzinSakit.setText(String.valueOf(izinSakit));
            labelRekapAlfa.setText(String.valueOf(rekap.getOrDefault(StatusAbsensi.ALFA, 0)));

            int totalSiswaAktif = absensiMapelDAO.hitungJumlahSiswaAktifUntukJadwal(jadwalTerpilih.getJadwalId());
            int totalSudahAbsen = daftarStatusTerhitung(rekap);
            labelRekapBelumAbsen.setText(String.valueOf(Math.max(0, totalSiswaAktif - totalSudahAbsen)));

            List<AbsensiMapel> daftarAbsen = absensiMapelDAO.findByJadwalTanggal(jadwalTerpilih.getJadwalId(), hariIni);
            absenMasukBox.getChildren().clear();
            if (daftarAbsen.isEmpty()) {
                Label kosong = new Label("Belum ada data absen masuk untuk sesi ini.");
                kosong.getStyleClass().add("log-kelas");
                absenMasukBox.getChildren().add(kosong);
            } else {
                for (AbsensiMapel a : daftarAbsen) {
                    absenMasukBox.getChildren().add(buatBarisAbsen(
                            a.getNamaMurid(), a.getNis(), a.getWaktuScan(), a.getStatus()));
                }
            }
        } catch (SQLException e) {
            System.err.println("[AbsensiMapelController] Gagal memuat rekap/absen masuk: " + e.getMessage());
        }
    }

    private int daftarStatusTerhitung(java.util.Map<StatusAbsensi, Integer> rekap) {
        int total = 0;
        for (Integer jumlah : rekap.values()) {
            if (jumlah != null) {
                total += jumlah;
            }
        }
        return total;
    }

    private void tambahBarisAbsenMasuk(String nama, String nis, LocalTime waktu, StatusAbsensi status) {
        if (absenMasukBox.getChildren().size() == 1
                && absenMasukBox.getChildren().get(0) instanceof Label placeholder
                && !placeholder.getStyleClass().contains("log-nama")) {
            absenMasukBox.getChildren().clear();
        }
        absenMasukBox.getChildren().add(0, buatBarisAbsen(nama, nis, waktu, status));
    }

    private HBox buatBarisAbsen(String nama, String nis, LocalTime waktu, StatusAbsensi status) {
        Label namaLabel = new Label(nama != null ? nama : "-");
        namaLabel.getStyleClass().add("log-nama");
        String jamText = waktu != null ? waktu.format(DateTimeFormatter.ofPattern("HH:mm")) + " WIB" : "-";
        Label nisJamLabel = new Label("NIS " + (nis != null ? nis : "-") + " \u2022 " + jamText);
        nisJamLabel.getStyleClass().add("log-kelas");
        VBox infoBox = new VBox(2, namaLabel, nisJamLabel);
        HBox.setHgrow(infoBox, Priority.ALWAYS);

        Label statusLabel = new Label(status.name());
        statusLabel.getStyleClass().addAll("status-badge", statusStyleClass(status));

        HBox baris = new HBox(10, infoBox, statusLabel);
        baris.setAlignment(Pos.CENTER_LEFT);
        baris.getStyleClass().add("scanqr-history-item");
        return baris;
    }

    private String statusStyleClass(StatusAbsensi status) {
        return switch (status) {
            case HADIR -> "status-badge-hadir";
            case TERLAMBAT -> "status-badge-terlambat";
            case ALFA -> "status-badge-alfa";
            default -> "status-badge-izin";
        };
    }
}