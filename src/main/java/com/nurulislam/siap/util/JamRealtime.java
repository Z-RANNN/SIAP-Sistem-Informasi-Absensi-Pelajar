package com.nurulislam.siap.util;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.control.Label;
import javafx.util.Duration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Jam berjalan (tanggal + jam, tiap detik) untuk top bar halaman.
 * Berhenti sendiri saat halaman ditinggalkan agar tidak bocor.
 */
public final class JamRealtime {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern(
            "EEEE, d MMM yyyy • HH:mm:ss", new Locale("id", "ID"));

    private JamRealtime() {
    }

    /** @param target label tujuan (boleh null, aman diabaikan) */
    public static void mulai(Label target) {
        if (target == null) {
            return;
        }
        target.setText(FORMAT.format(LocalDateTime.now()));
        final Timeline[] ref = new Timeline[1];
        ref[0] = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            if (target.getScene() == null
                    || target.getScene().getWindow() == null
                    || !target.getScene().getWindow().isShowing()) {
                ref[0].stop();
                return;
            }
            target.setText(FORMAT.format(LocalDateTime.now()));
        }));
        ref[0].setCycleCount(Animation.INDEFINITE);
        ref[0].play();
    }
}
