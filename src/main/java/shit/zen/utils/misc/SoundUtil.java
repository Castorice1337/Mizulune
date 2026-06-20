package shit.zen.utils.misc;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.Control;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.swing.JOptionPane;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import shit.zen.manager.ConfigManager;
import shit.zen.utils.math.MathUtil;

public final class SoundUtil {
    private static final Logger LOGGER = LogManager.getLogger("Mizulune");

    public static void playSound(String title, String message) {
        JOptionPane.showMessageDialog(null, message, title, 0);
    }

    public static void playSound(String fileName, float gain) {
        File file = new File(ConfigManager.CONFIG_DIR, fileName);
        if (!file.exists()) {
            System.out.println("Failed to find target file!");
            return;
        }
        new Thread(() -> {
            try {
                AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(new BufferedInputStream(new FileInputStream(file)));
                Clip clip = AudioSystem.getClip();
                clip.open(audioInputStream);
                clip.start();
                FloatControl floatControl = (FloatControl)clip.getControl(FloatControl.Type.MASTER_GAIN);
                floatControl.setValue(gain);
                clip.start();
            } catch (IOException | LineUnavailableException | UnsupportedAudioFileException ex) {
                LOGGER.warn("Failed to play sound {}", fileName, ex);
            }
        }, "Netty Client IO #" + MathUtil.randomInt(0, 100)).start();
    }
}
