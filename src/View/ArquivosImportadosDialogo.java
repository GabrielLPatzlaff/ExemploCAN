package View;
import Model.ArquivoCAN;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

final class ArquivosImportadosDialogo {
    static void abrir(JFrame pai,List<ArquivoCAN> arquivos,Consumer<ArquivoCAN> carregar,Consumer<ArquivoCAN> excluir){
        JDialog dialogo=new JDialog(pai,"Arquivos importados",true);
        DefaultTableModel modelo=new DefaultTableModel(new String[]{"Carregar","Arquivo","Excluir"},0){
            public Class<?> getColumnClass(int c){return c==0?Boolean.class:String.class;}
            public boolean isCellEditable(int r,int c){return c==0;}
        };
        for(var arquivo:arquivos)modelo.addRow(new Object[]{false,arquivo.getNomeArquivo(),"×"});
        JTable tabela=new TabelaCAN();tabela.setModel(modelo);
        tabela.getColumnModel().getColumn(0).setMaxWidth(70);tabela.getColumnModel().getColumn(2).setMaxWidth(65);
        tabela.getColumnModel().getColumn(2).setCellRenderer((t,v,s,f,r,c)->{JButton b=new JButton("×");b.setForeground(new Color(210,100,90));b.setToolTipText("Excluir este log do banco");return b;});
        tabela.addMouseListener(new MouseAdapter(){public void mouseClicked(MouseEvent e){int r=tabela.rowAtPoint(e.getPoint()),c=tabela.columnAtPoint(e.getPoint());if(SwingUtilities.isLeftMouseButton(e)&&r>=0&&c==2){dialogo.dispose();excluir.accept(arquivos.get(tabela.convertRowIndexToModel(r)));}}});
        JButton usar=new JButton("Carregar selecionados"),fechar=new JButton("Fechar");
        usar.addActionListener(e->{if(tabela.isEditing())tabela.getCellEditor().stopCellEditing();dialogo.dispose();for(int i=0;i<arquivos.size();i++)if(Boolean.TRUE.equals(modelo.getValueAt(i,0)))carregar.accept(arquivos.get(i));});
        fechar.addActionListener(e->dialogo.dispose());
        JPanel raiz=new JPanel(new BorderLayout(10,10));raiz.setBorder(BorderFactory.createEmptyBorder(14,14,14,14));
        raiz.add(new JScrollPane(tabela));JPanel botoes=new JPanel(new FlowLayout(FlowLayout.RIGHT));botoes.add(usar);botoes.add(fechar);raiz.add(botoes,BorderLayout.SOUTH);
        dialogo.setContentPane(raiz);EstiloAplicativo.aplicar(raiz);dialogo.setSize(820,400);dialogo.setLocationRelativeTo(pai);dialogo.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);dialogo.setVisible(true);
    }
}
