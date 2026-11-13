package View;

import Dao.ConsultaDadosHex;
import Model.SelecaoFiltro;
import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.DefaultTableModel;

/** Catálogo paginado dos valores HEX do resultado, sem carregar todas as mensagens. */
final class FiltrarDadosDialogo extends JDialog {
    private final List<SelecaoFiltro> selecoes;
    private final boolean primeiroByte;
    private final Set<String> ocultos;
    private final JTextField pesquisa=new JTextField(24);
    private final JLabel status=new JLabel("Consultando dados HEX…");
    private final JLabel quantidadeOculta=new JLabel();
    private final JButton anterior=new JButton("Anterior"),proxima=new JButton("Próxima"),aplicar=new JButton("Aplicar"),restaurar=new JButton("Restaurar todos"),cancelar=new JButton("Cancelar");
    private final DefaultTableModel modelo=new DefaultTableModel(new String[]{"Ocultar","Dados HEX","Ocorrências"},0){
        public Class<?> getColumnClass(int c){return c==0?Boolean.class:c==2?Long.class:String.class;}
        public boolean isCellEditable(int r,int c){return c==0;}
    };
    private final JTable tabela=new TabelaCAN();
    private final JScrollPane rolagem=new JScrollPane(tabela);
    private final List<String> inicios=new ArrayList<>(Collections.singletonList(null));
    private int pagina,versao;
    private String ultimo;
    private boolean atualizando,confirmado;
    private SwingWorker<List<ConsultaDadosHex.DadoHex>,Void> consulta;
    private static final int TAMANHO=200;

    private FiltrarDadosDialogo(JFrame pai,List<SelecaoFiltro> selecoes,boolean primeiroByte,Set<String> ocultos){
        super(pai,"Filtrar dados HEX",true);this.selecoes=List.copyOf(selecoes);this.primeiroByte=primeiroByte;this.ocultos=new HashSet<>(ocultos);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);setMinimumSize(new Dimension(660,430));
        tabela.setModel(modelo);tabela.getColumnModel().getColumn(0).setMaxWidth(65);tabela.getColumnModel().getColumn(1).setPreferredWidth(360);
        JPanel raiz=new JPanel(new BorderLayout(10,10));raiz.setBorder(BorderFactory.createEmptyBorder(16,16,16,16));
        JPanel topo=new JPanel(new BorderLayout(5,8));
        topo.add(new JLabel("Marque os valores para ocultar de todas as páginas e do CSV. Os logs serão preservados."),BorderLayout.NORTH);
        JPanel busca=new JPanel(new BorderLayout(8,0));busca.add(new JLabel("Pesquisar HEX:"),BorderLayout.WEST);busca.add(pesquisa);topo.add(busca);topo.add(quantidadeOculta,BorderLayout.SOUTH);
        raiz.add(topo,BorderLayout.NORTH);raiz.add(rolagem);
        JPanel rodape=new JPanel(new BorderLayout(4,8));rodape.add(status,BorderLayout.NORTH);
        JPanel botoes=new JPanel(new FlowLayout(FlowLayout.RIGHT,6,0));for(JButton b:List.of(anterior,proxima,restaurar,cancelar,aplicar))botoes.add(b);rodape.add(botoes);raiz.add(rodape,BorderLayout.SOUTH);
        setContentPane(raiz);EstiloAplicativo.aplicar(raiz);TemaAplicativo.aplicar(this);
        modelo.addTableModelListener(e->{if(atualizando||e.getColumn()!=0)return;int r=e.getFirstRow();if(r<0||r>=modelo.getRowCount())return;String hex=(String)modelo.getValueAt(r,1);if(Boolean.TRUE.equals(modelo.getValueAt(r,0)))this.ocultos.add(hex);else this.ocultos.remove(hex);resumo();});
        aplicar.addActionListener(e->{confirmarEdicao();confirmado=true;dispose();});cancelar.addActionListener(e->dispose());
        restaurar.addActionListener(e->{confirmarEdicao();this.ocultos.clear();atualizando=true;for(int i=0;i<modelo.getRowCount();i++)modelo.setValueAt(false,i,0);atualizando=false;resumo();});
        anterior.addActionListener(e->carregar(pagina-1,inicios.get(pagina-1)));
        proxima.addActionListener(e->carregar(pagina+1,ultimo));
        javax.swing.Timer atraso=new javax.swing.Timer(350,e->{inicios.clear();inicios.add(null);carregar(0,null);});atraso.setRepeats(false);
        pesquisa.getDocument().addDocumentListener(new DocumentListener(){public void insertUpdate(DocumentEvent e){atraso.restart();}public void removeUpdate(DocumentEvent e){atraso.restart();}public void changedUpdate(DocumentEvent e){atraso.restart();}});
        addWindowListener(new java.awt.event.WindowAdapter(){public void windowClosed(java.awt.event.WindowEvent e){atraso.stop();if(consulta!=null)consulta.cancel(true);}});
        resumo();setSize(820,520);setLocationRelativeTo(pai);carregar(0,null);
    }
    private void confirmarEdicao(){if(tabela.isEditing())tabela.getCellEditor().stopCellEditing();}
    private void resumo(){quantidadeOculta.setText(ocultos.size()+" valor(es) HEX oculto(s). Você pode desmarcar para recuperar.");}
    private void carregar(int destino,String inicio){
        confirmarEdicao();int requisicao=++versao;if(consulta!=null)consulta.cancel(true);
        anterior.setEnabled(false);proxima.setEnabled(false);aplicar.setEnabled(false);status.setText("Consultando valores distintos…");
        String termo=pesquisa.getText();
        try{ConsultaDadosHex.pesquisa(termo);}catch(IllegalArgumentException e){status.setText(e.getMessage());aplicar.setEnabled(true);return;}
        consulta=new SwingWorker<>(){
            protected List<ConsultaDadosHex.DadoHex> doInBackground()throws Exception{return ConsultaDadosHex.listar(selecoes,primeiroByte,termo,inicio,TAMANHO+1);}
            protected void done(){
                if(requisicao!=versao||!isDisplayable())return;
                aplicar.setEnabled(true);
                try{
                    var todos=get();boolean mais=todos.size()>TAMANHO;var dados=mais?todos.subList(0,TAMANHO):todos;
                    pagina=destino;if(inicios.size()<=pagina)inicios.add(inicio);else inicios.set(pagina,inicio);
                    atualizando=true;modelo.setRowCount(0);for(var d:dados)modelo.addRow(new Object[]{ocultos.contains(d.hex()),d.hex(),d.quantidade()});atualizando=false;
                    ultimo=dados.isEmpty()?inicio:dados.getLast().hex();anterior.setEnabled(pagina>0);proxima.setEnabled(mais);
                    status.setText("Página "+(pagina+1)+" · "+dados.size()+" valor(es) distinto(s)"+(mais?" · Há mais valores":""));rolagem.getVerticalScrollBar().setValue(0);
                }catch(Exception e){atualizando=false;status.setText("Falha: "+PainelFiltragem.mensagem(e));anterior.setEnabled(pagina>0);}
            }
        };consulta.execute();
    }
    static Set<String> abrir(JFrame pai,List<SelecaoFiltro> selecoes,boolean primeiroByte,Set<String> ocultos){
        FiltrarDadosDialogo d=new FiltrarDadosDialogo(pai,selecoes,primeiroByte,ocultos);
        try{d.setVisible(true);return d.confirmado?Set.copyOf(d.ocultos):null;}finally{d.dispose();}
    }
    static Set<String> recuperar(JFrame pai,Set<String> ocultos){
        DefaultListModel<String> modelo=new DefaultListModel<>();ocultos.stream().sorted().forEach(modelo::addElement);
        JList<String> lista=new JList<>(modelo);lista.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        JScrollPane rolagem=new JScrollPane(lista);rolagem.setPreferredSize(new Dimension(480,260));
        JPanel painel=new JPanel(new BorderLayout(8,8));painel.add(new JLabel("Selecione os valores que deseja trazer de volta ao resultado e ao CSV."),BorderLayout.NORTH);painel.add(rolagem);
        Object[] opcoes={"Recuperar selecionados","Recuperar todos","Cancelar"};
        int resposta=JOptionPane.showOptionDialog(pai,painel,"Dados ocultos",JOptionPane.DEFAULT_OPTION,JOptionPane.PLAIN_MESSAGE,null,opcoes,opcoes[0]);
        if(resposta==1)return Set.of();
        if(resposta!=0)return null;
        Set<String> restantes=new HashSet<>(ocultos);restantes.removeAll(lista.getSelectedValuesList());return Set.copyOf(restantes);
    }
}
