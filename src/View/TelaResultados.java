package View;

import Dao.MensagemDAO;
import Model.*;
import Util.ExportadorCSV;
import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;

public class TelaResultados extends JFrame {
    private final List<SelecaoFiltro> selecoes;
    private final Map<Long,String> nomes;
    private final int limite;
    private final boolean filtrarPrimeiroByte;
    private final List<Long> inicios = new ArrayList<>(List.of(0L));
    private int pagina;
    private long ultimo;
    private DefaultTableModel modelo;
    private boolean exportando;
    private boolean carregando;
    private int versaoConsulta;
    private Set<String> dadosOcultos=Set.of();

    public TelaResultados() { this(List.of(), Map.of(), 100, false); }

    public TelaResultados(List<SelecaoFiltro> selecoes,Map<Long,String> nomes,int limite) {
        this(selecoes, nomes, limite, false);
    }

    public TelaResultados(List<SelecaoFiltro> selecoes, Map<Long,String> nomes, int limite, boolean filtrarPrimeiroByte) {
        this.selecoes=List.copyOf(selecoes); this.nomes=Map.copyOf(nomes); this.limite=limite;
        this.filtrarPrimeiroByte = filtrarPrimeiroByte;
        TemaAplicativo.instalar();
        initComponents();
        modelo = (DefaultTableModel) tblResultados.getModel();
        EstiloAplicativo.aplicarIcone(this);
        EstiloAplicativo.arredondarBotoes(painelPrincipal);
        setLocationByPlatform(true);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) {
                if(exportando) JOptionPane.showMessageDialog(TelaResultados.this,"Aguarde a exportação do CSV terminar antes de fechar.");
                else dispose();
            }
        });
        tblResultados.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        tblResultados.setColunaCor(4);
        int[] larguras={340,95,120,70,105,50,240,100};
        for(int i=0;i<larguras.length;i++) tblResultados.getColumnModel().getColumn(i).setPreferredWidth(larguras[i]);
        lblSubtitulo.setText(filtrarPrimeiroByte ? "Filtro HEX ativo · Primeiro byte: "+Util.FiltroHex.DESCRICAO+"."
                : "Mensagens filtradas. Navegue com Alt + ← e Alt + → ou exporte a seleção.");
        exportar.setToolTipText("Salva todas as mensagens da seleção, incluindo as outras páginas.");
        exportar.addActionListener(e -> escolherCSV());
        btnFiltrarDados.addActionListener(e -> {
            if(carregando||exportando)return;
            Set<String> resposta=FiltrarDadosDialogo.abrir(this,this.selecoes,filtrarPrimeiroByte,dadosOcultos);
            if(resposta!=null) aplicarOcultos(resposta);
        });
        btnDadosOcultos.addActionListener(e -> {
            if(carregando||exportando)return;
            Set<String> resposta=FiltrarDadosDialogo.recuperar(this,dadosOcultos);
            if(resposta!=null) aplicarOcultos(resposta);
        });
        anterior.addActionListener(e->carregar(pagina-1,inicios.get(pagina-1)));
        proxima.addActionListener(e->carregar(pagina+1,ultimo));
        JRootPane raizTeclas = getRootPane();
        InputMap teclas = raizTeclas.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        teclas.put(KeyStroke.getKeyStroke("alt LEFT"), "paginaAnterior");
        teclas.put(KeyStroke.getKeyStroke("alt RIGHT"), "proximaPagina");
        raizTeclas.getActionMap().put("paginaAnterior", new AbstractAction() { public void actionPerformed(java.awt.event.ActionEvent e) { if (anterior.isEnabled()) anterior.doClick(); } });
        raizTeclas.getActionMap().put("proximaPagina", new AbstractAction() { public void actionPerformed(java.awt.event.ActionEvent e) { if (proxima.isEnabled()) proxima.doClick(); } });
        if (!this.selecoes.isEmpty()) carregar(0,0);
        else { anterior.setEnabled(false); proxima.setEnabled(false); exportar.setEnabled(false); }
        atualizarAcoesDados();
        TemaAplicativo.aplicar(this);
    }

    private void atualizarAcoesDados() {
        boolean livre=!carregando&&!exportando;
        btnFiltrarDados.setEnabled(livre&&!selecoes.isEmpty());
        btnDadosOcultos.setEnabled(livre&&!dadosOcultos.isEmpty());
        btnDadosOcultos.setText("Dados ocultos ("+dadosOcultos.size()+")");
        exportar.setEnabled(livre&&!selecoes.isEmpty());
    }
    private void aplicarOcultos(Set<String> valores) {
        if(carregando||exportando)return;
        Set<String> novas=Dao.ConsultaDadosHex.conferirOcultos(valores);
        if(novas.equals(dadosOcultos))return;
        dadosOcultos=novas;inicios.clear();inicios.add(0L);pagina=0;ultimo=0;
        carregar(0,0);
    }

    private void escolherCSV() {
        if(carregando||exportando)return;
        JFileChooser seletor = new JFileChooser();
        seletor.setDialogTitle("Salvar resultados filtrados");
        seletor.setFileFilter(new FileNameExtensionFilter("Arquivo CSV (*.csv)", "csv"));
        seletor.setSelectedFile(new java.io.File("resultados-can.csv"));
        if (seletor.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        Path escolhido = seletor.getSelectedFile().toPath();
        String nome = escolhido.getFileName().toString();
        Path destino = nome.toLowerCase(Locale.ROOT).endsWith(".csv")
                ? escolhido : escolhido.resolveSibling(nome + ".csv");
        boolean substituir = Files.exists(destino);
        if (substituir && JOptionPane.showConfirmDialog(this, "Substituir o arquivo " + destino.getFileName() + "?",
                "Salvar CSV", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;

        exportando=true;
        atualizarAcoesDados();
        exportar.setEnabled(false);
        statusExportacao.setText("Preparando CSV…");
        NumberFormat numeros = NumberFormat.getIntegerInstance(Locale.forLanguageTag("pt-BR"));
        new SwingWorker<Long, Long>() {
            @Override protected Long doInBackground() throws Exception {
                return ExportadorCSV.exportar(destino, substituir, selecoes, nomes, filtrarPrimeiroByte, dadosOcultos, total -> publish(total));
            }

            @Override protected void process(List<Long> totais) {
                statusExportacao.setText("Exportando… " + numeros.format(totais.getLast()) + " mensagens");
            }

            @Override protected void done() {
                exportando=false;
                atualizarAcoesDados();
                try {
                    String resumo = numeros.format(get()) + " mensagens exportadas.";
                    statusExportacao.setText(resumo);
                    if (isDisplayable()) JOptionPane.showMessageDialog(TelaResultados.this,
                            resumo + "\n" + destino.toAbsolutePath(), "CSV salvo", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception erro) {
                    statusExportacao.setText("Falha ao exportar.");
                    if (isDisplayable()) JOptionPane.showMessageDialog(TelaResultados.this, PainelFiltragem.mensagem(erro),
                            "Falha ao exportar CSV", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }
    private void carregar(int destino,long inicio) {
        carregando=true;int requisicao=++versaoConsulta;Set<String> ocultosDaConsulta=dadosOcultos;
        atualizarAcoesDados();
        anterior.setEnabled(false); proxima.setEnabled(false); status.setText("Carregando resultados…");
        new SwingWorker<List<MensagemCAN>,Void>() {
            protected List<MensagemCAN> doInBackground() throws Exception { return new MensagemDAO().filtrar(selecoes, limite, inicio, filtrarPrimeiroByte,ocultosDaConsulta); }
            protected void done() {
                if(requisicao!=versaoConsulta)return;
                carregando=false;atualizarAcoesDados();
                if(!isDisplayable()) return;
                try {
                    List<MensagemCAN> linhas=get();
                    if(linhas.isEmpty() && destino>pagina) { status.setText("Fim dos resultados. Esta é a última página."); anterior.setEnabled(pagina>0); return; }
                    pagina=destino; if(inicios.size()<=pagina) inicios.add(inicio); else inicios.set(pagina,inicio);
                    modelo.setRowCount(0); ultimo=inicio;
                    for(MensagemCAN m:linhas) { modelo.addRow(new Object[]{nomes.getOrDefault(m.getArquivoId(),"Arquivo "+m.getArquivoId()),m.getNumeroRegistro(),m.getHorario()==null ? "" : m.getHorario().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS")),m.getDirecao(),m.getIdCan(),m.getDlc(),m.getDadosHex(),m.getDeltaDt()}); ultimo=m.getId(); }
                    SwingUtilities.invokeLater(() -> rolagem.getVerticalScrollBar().setValue(0));
                    status.setText("Página "+(pagina+1)+" • "+linhas.size()+" mensagem(ns)"+(linhas.size()<limite?" • Fim dos resultados":""));
                    if(linhas.isEmpty()&&!dadosOcultos.isEmpty())status.setText("Nenhuma mensagem visível. Use Dados ocultos para recuperar valores.");
                    anterior.setEnabled(pagina>0); proxima.setEnabled(linhas.size()==limite);
                } catch(Exception e) { status.setText("Falha na consulta."); anterior.setEnabled(pagina>0); proxima.setEnabled(modelo.getRowCount()==limite); JOptionPane.showMessageDialog(TelaResultados.this,PainelFiltragem.mensagem(e),"Falha na consulta",JOptionPane.ERROR_MESSAGE); }
            }
        }.execute();
    }


    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        painelPrincipal = new javax.swing.JPanel();
        cabecalho = new javax.swing.JPanel();
        lblLogo = new javax.swing.JLabel();
        titulos = new javax.swing.JPanel();
        lblTitulo = new javax.swing.JLabel();
        lblSubtitulo = new javax.swing.JLabel();
        acoesDados = new javax.swing.JPanel();
        btnFiltrarDados = new javax.swing.JButton();
        btnDadosOcultos = new javax.swing.JButton();
        rolagem = new javax.swing.JScrollPane();
        tblResultados = new View.TabelaCAN();
        rodape = new javax.swing.JPanel();
        avisos = new javax.swing.JPanel();
        status = new javax.swing.JLabel();
        statusExportacao = new javax.swing.JLabel();
        botoesPaginas = new javax.swing.JPanel();
        anterior = new javax.swing.JButton();
        proxima = new javax.swing.JButton();
        exportar = new javax.swing.JButton();
        setDefaultCloseOperation(2);
        setTitle("Exemplo CAN — Resultados CAN");
        setPreferredSize(new java.awt.Dimension(1250, 760));
        setMinimumSize(new java.awt.Dimension(950, 600));
        painelPrincipal.setBackground(new java.awt.Color(22, 23, 25));
        painelPrincipal.setForeground(new java.awt.Color(242, 241, 237));
        painelPrincipal.setBorder(javax.swing.BorderFactory.createEmptyBorder(18, 18, 18, 18));
        painelPrincipal.setLayout(new java.awt.BorderLayout(10, 12));
        painelPrincipal.add(cabecalho, java.awt.BorderLayout.NORTH);
        painelPrincipal.add(rolagem, java.awt.BorderLayout.CENTER);
        painelPrincipal.add(rodape, java.awt.BorderLayout.SOUTH);
        cabecalho.setBackground(new java.awt.Color(22, 23, 25));
        cabecalho.setForeground(new java.awt.Color(242, 241, 237));
        cabecalho.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 12, 0));
        cabecalho.setLayout(new java.awt.BorderLayout(14, 0));
        cabecalho.add(lblLogo, java.awt.BorderLayout.WEST);
        cabecalho.add(titulos, java.awt.BorderLayout.CENTER);
        cabecalho.add(acoesDados, java.awt.BorderLayout.SOUTH);
        acoesDados.setBackground(new java.awt.Color(22,23,25));
        acoesDados.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT,8,8));
        acoesDados.add(btnFiltrarDados);
        acoesDados.add(btnDadosOcultos);
        btnFiltrarDados.setFont(new java.awt.Font("Segoe UI",0,13));
        btnFiltrarDados.setBackground(new java.awt.Color(34,36,39));
        btnFiltrarDados.setForeground(new java.awt.Color(242,241,237));
        btnFiltrarDados.setText("Filtrar dados");
        btnFiltrarDados.setFocusPainted(false);
        btnFiltrarDados.setBorder(javax.swing.BorderFactory.createEmptyBorder(10,16,10,16));
        btnDadosOcultos.setFont(new java.awt.Font("Segoe UI",0,13));
        btnDadosOcultos.setBackground(new java.awt.Color(34,36,39));
        btnDadosOcultos.setForeground(new java.awt.Color(242,241,237));
        btnDadosOcultos.setText("Dados ocultos (0)");
        btnDadosOcultos.setFocusPainted(false);
        btnDadosOcultos.setBorder(javax.swing.BorderFactory.createEmptyBorder(10,16,10,16));

        lblLogo.setIcon(new javax.swing.ImageIcon(getClass().getResource("/recursos/can-exemplo-76.png")));
        titulos.setBackground(new java.awt.Color(22, 23, 25));
        titulos.setForeground(new java.awt.Color(242, 241, 237));
        titulos.setBorder(javax.swing.BorderFactory.createEmptyBorder(12, 0, 12, 0));
        titulos.setLayout(new java.awt.GridLayout(2, 1, 0, 6));
        titulos.add(lblTitulo);
        titulos.add(lblSubtitulo);
        lblTitulo.setFont(new java.awt.Font("Segoe UI", 0, 13));
        lblTitulo.setForeground(new java.awt.Color(242, 241, 237));
        lblTitulo.setText("EXEMPLO CAN  /  RESULTADOS CAN");
        lblTitulo.setFont(new java.awt.Font("Segoe UI", 1, 20));
        lblTitulo.setForeground(new java.awt.Color(224, 184, 67));
        lblSubtitulo.setFont(new java.awt.Font("Segoe UI", 0, 13));
        lblSubtitulo.setForeground(new java.awt.Color(242, 241, 237));
        lblSubtitulo.setText("Mensagens filtradas. Navegue com Alt + ← e Alt + → ou exporte a seleção.");
        rolagem.setBackground(new java.awt.Color(34, 36, 39));
        rolagem.setViewportView(tblResultados);
        tblResultados.setFont(new java.awt.Font("Segoe UI", 0, 13));
        tblResultados.setBackground(new java.awt.Color(34, 36, 39));
        tblResultados.setForeground(new java.awt.Color(242, 241, 237));
        tblResultados.setFillsViewportHeight(true);
        tblResultados.setRowHeight(29);
        tblResultados.setSelectionBackground(new java.awt.Color(113, 38, 34));
        tblResultados.setSelectionForeground(new java.awt.Color(255, 255, 255));
        tblResultados.setModel(new javax.swing.table.DefaultTableModel(new Object[][] {}, new String[] {"Arquivo", "Registro", "Horário", "Direção", "ID CAN", "DLC", "Dados HEX", "Delta (dt)"}) { @Override public boolean isCellEditable(int row, int column) { return false; } });
        rodape.setBackground(new java.awt.Color(22, 23, 25));
        rodape.setForeground(new java.awt.Color(242, 241, 237));
        rodape.setLayout(new java.awt.BorderLayout(8, 8));
        rodape.add(avisos, java.awt.BorderLayout.CENTER);
        rodape.add(botoesPaginas, java.awt.BorderLayout.EAST);
        avisos.setBackground(new java.awt.Color(22, 23, 25));
        avisos.setForeground(new java.awt.Color(242, 241, 237));
        avisos.setLayout(new java.awt.GridLayout(2, 1, 0, 4));
        avisos.add(status);
        avisos.add(statusExportacao);
        status.setFont(new java.awt.Font("Segoe UI", 0, 13));
        status.setForeground(new java.awt.Color(242, 241, 237));
        status.setText("Página 1 · Nenhuma mensagem exibida");
        statusExportacao.setFont(new java.awt.Font("Segoe UI", 0, 13));
        statusExportacao.setForeground(new java.awt.Color(242, 241, 237));
        statusExportacao.setText("");
        botoesPaginas.setBackground(new java.awt.Color(22, 23, 25));
        botoesPaginas.setForeground(new java.awt.Color(242, 241, 237));
        botoesPaginas.setLayout(new java.awt.FlowLayout(2, 8, 0));
        botoesPaginas.add(anterior);
        botoesPaginas.add(proxima);
        botoesPaginas.add(exportar);
        anterior.setFont(new java.awt.Font("Segoe UI", 0, 13));
        anterior.setBackground(new java.awt.Color(34, 36, 39));
        anterior.setForeground(new java.awt.Color(242, 241, 237));
        anterior.setText("Página anterior (Alt + ←)");
        anterior.setFocusPainted(false);
        anterior.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 16, 10, 16));
        proxima.setFont(new java.awt.Font("Segoe UI", 0, 13));
        proxima.setBackground(new java.awt.Color(34, 36, 39));
        proxima.setForeground(new java.awt.Color(242, 241, 237));
        proxima.setText("Próxima página (Alt + →)");
        proxima.setFocusPainted(false);
        proxima.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 16, 10, 16));
        exportar.setFont(new java.awt.Font("Segoe UI", 0, 13));
        exportar.setBackground(new java.awt.Color(190, 48, 41));
        exportar.setForeground(new java.awt.Color(242, 241, 237));
        exportar.setText("Exportar CSV");
        exportar.setFocusPainted(false);
        exportar.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 16, 10, 16));
        getContentPane().setLayout(new java.awt.BorderLayout());
        getContentPane().add(painelPrincipal, java.awt.BorderLayout.CENTER);
        pack();
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    javax.swing.JPanel painelPrincipal;
    javax.swing.JPanel cabecalho;
    javax.swing.JLabel lblLogo;
    javax.swing.JPanel titulos;
    javax.swing.JLabel lblTitulo;
    javax.swing.JLabel lblSubtitulo;
    javax.swing.JPanel acoesDados;
    javax.swing.JButton btnFiltrarDados;
    javax.swing.JButton btnDadosOcultos;
    javax.swing.JScrollPane rolagem;
    View.TabelaCAN tblResultados;
    javax.swing.JPanel rodape;
    javax.swing.JPanel avisos;
    javax.swing.JLabel status;
    javax.swing.JLabel statusExportacao;
    javax.swing.JPanel botoesPaginas;
    javax.swing.JButton anterior;
    javax.swing.JButton proxima;
    javax.swing.JButton exportar;
    // End of variables declaration//GEN-END:variables
}
