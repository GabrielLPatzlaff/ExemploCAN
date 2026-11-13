package View;

/** JTable com cabeçalho da marca, visível também no editor de formulários. */
public class TabelaCAN extends javax.swing.JTable {
    private int colunaCor = -1;

    /** Índice da coluna do modelo usada para agrupar as cores, ou -1 para desativar. */
    public int getColunaCor() { return colunaCor; }
    public void setColunaCor(int coluna) { colunaCor=coluna; repaint(); }

    @Override public java.awt.Component prepareRenderer(javax.swing.table.TableCellRenderer renderer, int linha, int coluna) {
        java.awt.Component componente=super.prepareRenderer(renderer,linha,coluna);
        if(colunaCor<0 || colunaCor>=getModel().getColumnCount()) return componente;
        Object valor=getModel().getValueAt(convertRowIndexToModel(linha),colunaCor);
        java.awt.Color cor=TemaAplicativo.corLinha(corDaChave(valor==null ? "" : valor.toString()));
        if(isRowSelected(linha)) {
            cor=new java.awt.Color(Math.min(255,cor.getRed()+27),Math.min(255,cor.getGreen()+27),Math.min(255,cor.getBlue()+27));
        }
        componente.setBackground(cor);
        componente.setForeground(TemaAplicativo.texto(CoresIds.texto(cor)));
        if(componente instanceof javax.swing.JComponent c) c.setOpaque(true);
        return componente;
    }

    private java.awt.Color corDaChave(String chave) {
        return chave.isBlank() ? getBackground() : CoresIds.obter(chave);
    }

    public TabelaCAN() {
        setFillsViewportHeight(true);
        setShowVerticalLines(false);
        setGridColor(new java.awt.Color(53,55,58));
        getTableHeader().setDefaultRenderer(new Cabecalho());
        getTableHeader().setPreferredSize(new java.awt.Dimension(0,34));
    }
    @Override protected void configureEnclosingScrollPane() {
        super.configureEnclosingScrollPane();
        if (getParent() instanceof javax.swing.JViewport viewport) {
            viewport.setBackground(getBackground());
            if (viewport.getParent() instanceof javax.swing.JScrollPane rolagem) {
                rolagem.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(74,72,70)));
                javax.swing.JPanel canto = new javax.swing.JPanel();
                canto.setBackground(TemaAplicativo.fundo(new java.awt.Color(54,52,50)));
                rolagem.setCorner(javax.swing.JScrollPane.UPPER_RIGHT_CORNER,canto);
            }
        }
    }
    private static class Cabecalho extends javax.swing.table.DefaultTableCellRenderer {
        Cabecalho() { setHorizontalAlignment(LEFT); setOpaque(true); }
        @Override public java.awt.Component getTableCellRendererComponent(javax.swing.JTable tabela, Object valor,
                boolean selecionado, boolean foco, int linha, int coluna) {
            super.getTableCellRendererComponent(tabela,valor,false,false,linha,coluna);
            setBackground(TemaAplicativo.fundo(new java.awt.Color(54,52,50)));
            setForeground(TemaAplicativo.texto(new java.awt.Color(224,184,67)));
            setFont(new java.awt.Font("Segoe UI",java.awt.Font.BOLD,12));
            setBorder(javax.swing.BorderFactory.createEmptyBorder(8,10,8,10));
            return this;
        }
    }
}
