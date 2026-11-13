package Principal;

import javax.swing.*;
import View.TelaFiltragem;

public class Principal {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); } catch (Exception ignored) {}
            new TelaFiltragem().setVisible(true);
        });
    }
}
