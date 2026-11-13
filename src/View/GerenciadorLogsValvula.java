package View;

import Dao.*;
import Model.*;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;

/** Edita somente os vínculos, sem apagar os logs importados. */
final class GerenciadorLogsValvula extends JDialog {
    interface Importar { ArquivoCAN executar(Path arquivo, Consumer<String> progresso, Component pai) throws Exception; }
    private final ValvulaCAN valvula;
    private final Importar importar;
    private final DefaultTableModel modelo = new DefaultTableModel(new String[]{"Na válvula", "Log importado"},0) {
        @Override public Class<?> getColumnClass(int coluna) { return coluna==0 ? Boolean.class : String.class; }
        @Override public boolean isCellEditable(int linha,int coluna) { return coluna==0 && !ocupado; }
    };
    private final JTable tabela = new TabelaCAN();
    private final JButton novo = new JButton("Importar e adicionar TXT…"), salvar = new JButton("Carregar logs da válvula"), cancelar = new JButton("Cancelar");
    private final JLabel status = new JLabel("Carregando logs…");
    private List<ArquivoCAN> arquivos = List.of();
    private Set<String> escolhas = new HashSet<>();
    private boolean ocupado, salvo;

    static boolean abrir(JFrame pai, ValvulaCAN valvula, Importar importar) {
        GerenciadorLogsValvula dialogo = new GerenciadorLogsValvula(pai,valvula,importar);
        dialogo.setVisible(true);
        return dialogo.salvo;
    }
    private GerenciadorLogsValvula(JFrame pai,ValvulaCAN valvula,Importar importar) {
        super(pai,"Logs da válvula — "+valvula.nome(),true);
        this.valvula=valvula; this.importar=importar;
        JPanel raiz=new JPanel(new BorderLayout(10,10));
        raiz.setBorder(BorderFactory.createEmptyBorder(16,16,16,16));
        raiz.add(new JLabel("Ao retirar um log, os IDs exclusivos dele também serão retirados da válvula."),BorderLayout.NORTH);
        tabela.setModel(modelo); tabela.getColumnModel().getColumn(0).setMaxWidth(90);
        raiz.add(new JScrollPane(tabela));
        JPanel rodape=new JPanel(new BorderLayout(8,8));
        JPanel botoes=new JPanel(new FlowLayout(FlowLayout.RIGHT)); botoes.add(novo); botoes.add(salvar); botoes.add(cancelar);
        rodape.add(status,BorderLayout.NORTH); rodape.add(botoes,BorderLayout.SOUTH); raiz.add(rodape,BorderLayout.SOUTH);
        setContentPane(raiz); EstiloAplicativo.aplicar(raiz);
        setSize(820,470); setMinimumSize(new Dimension(700,400)); setLocationRelativeTo(pai);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter(){ @Override public void windowClosing(WindowEvent e){ if(!ocupado) dispose(); }});
        cancelar.addActionListener(e->dispose()); novo.addActionListener(e->importarTxt()); salvar.addActionListener(e->salvar());
        carregar(true);
    }
    private static String hash(ArquivoCAN arquivo) { return arquivo.getSha256().toUpperCase(Locale.ROOT); }
    private void ocupado(boolean valor) { ocupado=valor; novo.setEnabled(!valor); salvar.setEnabled(!valor); cancelar.setEnabled(!valor); tabela.setEnabled(!valor); }
    private void lerEscolhas() {
        if(tabela.isEditing()) tabela.getCellEditor().stopCellEditing();
        for(int i=0;i<arquivos.size();i++) {
            String hash=hash(arquivos.get(i));
            if(Boolean.TRUE.equals(modelo.getValueAt(i,0))) escolhas.add(hash); else escolhas.remove(hash);
        }
    }
    private void carregar(boolean inicial) {
        ocupado(true);
        new SwingWorker<List<ArquivoCAN>,Void>() {
            @Override protected List<ArquivoCAN> doInBackground() throws Exception {
                if(inicial) escolhas=new HashSet<>(new ValvulaDAO().arquivosVinculados(valvula.id()));
                return new ArquivoDAO().listarConcluidos();
            }
            @Override protected void done() {
                ocupado(false);
                try {
                    arquivos=get(); modelo.setRowCount(0);
                    for(ArquivoCAN arquivo:arquivos) modelo.addRow(new Object[]{escolhas.contains(hash(arquivo)),arquivo.getNomeArquivo()});
                    status.setText("Marque ou desmarque os logs e clique em Carregar logs da válvula.");
                } catch(Exception e) { falha(e); salvar.setEnabled(false); }
            }
        }.execute();
    }
    private void importarTxt() {
        JFileChooser seletor=new JFileChooser(); seletor.setMultiSelectionEnabled(true);
        seletor.setFileFilter(new FileNameExtensionFilter("Logs TXT (*.txt)","txt"));
        if(seletor.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION) return;
        lerEscolhas(); java.io.File[] escolhidos=seletor.getSelectedFiles(); ocupado(true);
        new SwingWorker<List<String>,String>() {
            @Override protected List<String> doInBackground() {
                List<String> erros=new ArrayList<>();
                for(java.io.File f:escolhidos) {
                    try { escolhas.add(hash(importar.executar(f.toPath(),s->publish(s),GerenciadorLogsValvula.this))); }
                    catch(Exception e) { erros.add(f.getName()+": "+PainelFiltragem.mensagem(e)); }
                }
                return erros;
            }
            @Override protected void process(List<String> textos) { status.setText(textos.getLast()); }
            @Override protected void done() {
                try { var erros=get(); if(!erros.isEmpty()) JOptionPane.showMessageDialog(GerenciadorLogsValvula.this,String.join("\n",erros),"Alguns logs não foram importados",JOptionPane.WARNING_MESSAGE); }
                catch(Exception e) { falha(e); }
                carregar(false);
            }
        }.execute();
    }
    private void salvar() {
        lerEscolhas(); Set<String> disponiveis=new HashSet<>();
        for(ArquivoCAN arquivo:arquivos) disponiveis.add(hash(arquivo));
        Set<String> selecionados=new HashSet<>(escolhas); selecionados.retainAll(disponiveis);
        ocupado(true);
        new SwingWorker<Void,Void>() {
            @Override protected Void doInBackground() throws Exception { new Principal.VinculosValvula().salvar(valvula.id(),List.copyOf(arquivos),selecionados); return null; }
            @Override protected void done() {
                ocupado(false);
                try { get(); salvo=true; dispose(); } catch(Exception e) { falha(e); }
            }
        }.execute();
    }
    private void falha(Exception erro) { status.setText("Não foi possível concluir."); JOptionPane.showMessageDialog(this,PainelFiltragem.mensagem(erro),"Logs da válvula",JOptionPane.ERROR_MESSAGE); }
}
