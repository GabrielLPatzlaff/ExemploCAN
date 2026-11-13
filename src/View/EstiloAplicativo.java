package View;

import java.awt.*;
import java.net.URL;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.plaf.basic.BasicButtonUI;

/** Paleta inspirada na identidade visual da Exemplo CAN. */
public final class EstiloAplicativo {
    private static final Color FUNDO = new Color(22, 23, 25);
    private static final Color PAINEL = new Color(34, 36, 39);
    private static final Color TEXTO = new Color(242, 241, 237);
    private static final Color DOURADO = new Color(224, 184, 67);
    private static final Color VERMELHO = new Color(190, 48, 41);
    private static final URL LOGO = EstiloAplicativo.class.getResource("/recursos/can-exemplo.png");
    private EstiloAplicativo() {}

    /** Mantém as cores, fontes e margens configuradas no formulário visual. */
    public static void arredondarBotoes(Component componente) {
        if (componente instanceof JButton botao) {
            botao.setOpaque(false);
            botao.setContentAreaFilled(false);
            botao.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            botao.setUI(new BasicButtonUI() {
                @Override protected void paintText(Graphics g,JComponent c,Rectangle r,String texto) {
                    if(c.isEnabled()){super.paintText(g,c,r,texto);return;}
                    g.setColor(TemaAplicativo.textoDesabilitado());
                    javax.swing.plaf.basic.BasicGraphicsUtils.drawStringUnderlineCharAt(g,texto,((JButton)c).getDisplayedMnemonicIndex(),r.x,r.y+g.getFontMetrics().getAscent());
                }
                @Override public void paint(Graphics g, JComponent c) {
                    JButton b = (JButton) c;
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    Color cor = b.isEnabled() ? b.getBackground() : TemaAplicativo.fundo(new Color(48,49,51));
                    if (b.isEnabled() && b.getModel().isRollover()) cor = cor.brighter();
                    if (b.isEnabled() && b.getModel().isPressed()) cor = cor.darker();
                    g2.setColor(cor);
                    g2.fillRoundRect(0,0,c.getWidth(),c.getHeight(),14,14);
                    g2.dispose();
                    super.paint(g,c);
                }
            });
        }
        if (componente instanceof Container grupo)
            for (Component filho : grupo.getComponents()) arredondarBotoes(filho);
    }

    public static void aplicarIcone(JFrame janela) {
        if (LOGO != null) janela.setIconImage(new ImageIcon(LOGO).getImage());
    }

    public static void aplicar(Component c) {
        if (c instanceof JComponent j && !(c instanceof JTable)) j.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        if (c instanceof JPanel p) { p.setBackground(FUNDO); p.setForeground(TEXTO); }
        else if (c instanceof JButton b) {
            b.setBackground(b.getText().startsWith("Filtrar") ? VERMELHO : PAINEL);
            b.setForeground(TEXTO); b.setFocusPainted(false); b.setOpaque(false);
            b.setContentAreaFilled(false); b.setBorder(new EmptyBorder(10,16,10,16));
            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            b.setUI(new BasicButtonUI() {
                @Override protected void paintText(Graphics g,JComponent c,Rectangle r,String texto) {
                    if(c.isEnabled()){super.paintText(g,c,r,texto);return;}
                    g.setColor(TemaAplicativo.textoDesabilitado());
                    javax.swing.plaf.basic.BasicGraphicsUtils.drawStringUnderlineCharAt(g,texto,((JButton)c).getDisplayedMnemonicIndex(),r.x,r.y+g.getFontMetrics().getAscent());
                }
                @Override public void paint(Graphics g, JComponent c) {
                    JButton botao = (JButton)c;
                    Graphics2D g2=(Graphics2D)g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    Color cor=botao.isEnabled() ? botao.getBackground() : TemaAplicativo.fundo(new Color(48,49,51));
                    if (botao.isEnabled() && botao.getModel().isRollover()) cor=cor.brighter();
                    if (botao.isEnabled() && botao.getModel().isPressed()) cor=cor.darker();
                    g2.setColor(cor); g2.fillRoundRect(0,0,c.getWidth(),c.getHeight(),14,14);
                    g2.dispose(); super.paint(g,c);
                }
            });
        } else if (c instanceof JTable t) {
            t.setBackground(PAINEL); t.setForeground(TEXTO); t.setGridColor(new Color(53,55,58));
            t.setFont(new Font("Segoe UI",Font.PLAIN,13)); t.setRowHeight(29); t.setShowVerticalLines(false);
            t.setSelectionBackground(new Color(113,38,34)); t.setSelectionForeground(Color.WHITE);
            t.getTableHeader().setBackground(new Color(54,52,50)); t.getTableHeader().setForeground(DOURADO);
            t.getTableHeader().setFont(new Font("Segoe UI",Font.BOLD,12)); t.getTableHeader().setPreferredSize(new Dimension(0,34)); t.getTableHeader().setOpaque(true);
        } else if (c instanceof JList<?> l) {
            l.setBackground(PAINEL); l.setForeground(TEXTO); l.setSelectionBackground(new Color(113,38,34)); l.setSelectionForeground(Color.WHITE); l.setFixedCellHeight(32);
        } else if (c instanceof JTextField t) { t.setBackground(new Color(48,50,53)); t.setForeground(TEXTO); t.setCaretColor(TEXTO); t.setBorder(new CompoundBorder(new LineBorder(new Color(83,84,85)),new EmptyBorder(7,9,7,9))); }
        else if (c instanceof JCheckBox b) { b.setBackground(FUNDO); b.setForeground(TEXTO); b.setOpaque(true); }
        else if (c instanceof JLabel l) { l.setForeground("marca".equals(l.getName()) ? DOURADO : TEXTO); if ("marca".equals(l.getName())) l.setFont(new Font("Segoe UI",Font.BOLD,19)); }
        else if (c instanceof JScrollPane s) { s.setBorder(new LineBorder(new Color(74,72,70))); s.getViewport().setBackground(PAINEL); }
        else if (c instanceof JSplitPane s) { s.setBackground(FUNDO); s.setBorder(null); }
        else if (c instanceof JProgressBar b) { b.setForeground(DOURADO); b.setBackground(PAINEL); }
        if (c instanceof Container cont) for (Component filho : cont.getComponents()) aplicar(filho);
    }
    public static JPanel cabecalho(String titulo, String subtitulo) {
        JPanel p = new JPanel(new BorderLayout(10, 0)); p.setBackground(FUNDO);
        if (LOGO != null) {
            Image imagem = new ImageIcon(LOGO).getImage().getScaledInstance(76, 76, Image.SCALE_SMOOTH);
            p.add(new JLabel(new ImageIcon(imagem)), BorderLayout.WEST);
        }
        JPanel texto = new JPanel(new GridBagLayout());
        JPanel titulos = new JPanel(new BorderLayout(0, 5));
        JLabel marca = new JLabel("EXEMPLO CAN  /  " + titulo); marca.setName("marca");
        marca.setFont(marca.getFont().deriveFont(Font.BOLD, 18f)); marca.setForeground(DOURADO);
        JLabel descricao = new JLabel(subtitulo); descricao.setForeground(TEXTO);
        titulos.add(marca, BorderLayout.NORTH);
        titulos.add(descricao, BorderLayout.SOUTH);
        GridBagConstraints posicao = new GridBagConstraints();
        posicao.weightx = 1;
        posicao.anchor = GridBagConstraints.WEST;
        texto.add(titulos, posicao);
        p.add(texto, BorderLayout.CENTER);
        p.setBorder(new CompoundBorder(new MatteBorder(0,0,1,0,new Color(76,57,49)),new EmptyBorder(4,0,16,0)));
        return p;
    }
}
