package View;

import Dao.*;
import Model.*;
import Principal.ImportadorCAN;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** Painel da aplicação; tarefas de disco e banco nunca executam na thread da tela. */
public class PainelFiltragem extends JPanel {
    private final JFrame janela;
    private final DefaultListModel<ArquivoCAN> arquivos = new DefaultListModel<>();
    private final JList<ArquivoCAN> lista;
    private final Map<Long, Set<String>> marcados = new HashMap<>();
    private final Set<Long> todosMarcados = new HashSet<>();
    private final Map<Long,List<MensagemDAO.IdDisponivel>> cache = new HashMap<>();
    private final Map<Long, ValvulaCAN> valvulasPorArquivo = new HashMap<>();
    private final JButton selecionar;
    private final JButton existentes;
    private final JButton fecharValvula;
    private final JButton remover;
    private final JButton filtrar;
    private final JButton adicionarValvula;
    private final JButton valvulasSalvas;
    private final JButton verTodosIds;
    private final JLabel valvulaAtual;
    private final JCheckBox todos;
    private final JSpinner linhas;
    private final JCheckBox filtrarPrimeiroByte;
    private final JLabel status;
    private final JProgressBar progresso;
    private final DefaultTableModel ids = new DefaultTableModel(new Object[]{"Filtrar", "ID CAN", "Nome do ID", "Mensagens", "Cor"},0) {
        public Class<?> getColumnClass(int c) { return c == 0 ? Boolean.class : c == 3 ? Long.class : c == 4 ? java.awt.Color.class : String.class; }
        public boolean isCellEditable(int r,int c) { return c == 0 && !ocupado && arquivoAtual != 0; }
    };
    private final JTable tabela;
    private final JTextField pesquisaId;
    private boolean ocupado, atualizando;
    private long arquivoAtual;
    private int versaoIds;
    private ValvulaCAN valvulaSemArquivo;

    public PainelFiltragem(TelaFiltragem janela) {
        this.janela = janela;
        this.lista = janela.lstArquivos;
        this.selecionar = janela.btnSelecionar;
        this.existentes = janela.btnExistentes;
        this.fecharValvula = janela.btnFecharValvula;
        this.remover = janela.btnRemover;
        this.filtrar = janela.btnFiltrar;
        this.adicionarValvula = janela.btnAdicionarValvula;
        this.valvulasSalvas = janela.btnValvulasSalvas;
        this.verTodosIds = janela.btnVerTodosIds;
        this.valvulaAtual = janela.lblValvulaAtual;
        this.todos = janela.chkTodos;
        this.linhas = janela.spnLinhas;
        this.filtrarPrimeiroByte = janela.chkPrimeiroByte;
        this.status = janela.lblStatus;
        this.progresso = janela.barraProgresso;
        this.tabela = janela.tblIds;
        this.pesquisaId = janela.txtPesquisaId;
        lista.setModel(arquivos);
        tabela.setModel(ids);
        setLayout(new BorderLayout());
        add(janela.painelPrincipal, BorderLayout.CENTER);
        progresso.setIndeterminate(true);
        progresso.setVisible(false);
        lista.setCellRenderer(new javax.swing.ListCellRenderer<ArquivoCAN>() {
            public Component getListCellRendererComponent(JList<? extends ArquivoCAN> l,ArquivoCAN valor,int indice,boolean selecionada,boolean foco) {
                JPanel p=new JPanel(new BorderLayout(8,0));
                p.setBackground(selecionada?l.getSelectionBackground():l.getBackground());
                JLabel nome=new JLabel(valor.getNomeArquivo());nome.setForeground(l.getForeground());nome.setFont(l.getFont());
                JLabel x=new JLabel("  ×  ");x.setForeground(new Color(220,135,125));x.setToolTipText("Excluir este arquivo do banco");
                p.add(nome);p.add(x,BorderLayout.EAST);p.setPreferredSize(new Dimension(100,32));p.setToolTipText(valor.getNomeArquivo());return p;
            }
        });
        lista.addMouseListener(new MouseAdapter(){public void mouseClicked(MouseEvent e){
            int linha=lista.locationToIndex(e.getPoint());
            Rectangle r=linha<0?null:lista.getCellBounds(linha,linha);
            if(SwingUtilities.isLeftMouseButton(e)&&r!=null&&r.contains(e.getPoint())&&e.getX()>=lista.getVisibleRect().x+lista.getVisibleRect().width-38)
                excluirArquivo(lista.getModel().getElementAt(linha));
        }});
        tabela.getColumnModel().getColumn(4).setMaxWidth(54);
        tabela.getColumnModel().getColumn(4).setCellRenderer((t,v,sel,foco,lin,col)->{
            JLabel quadrado=new JLabel("■",SwingConstants.CENTER);
            quadrado.setFont(new Font("Segoe UI",Font.BOLD,22));quadrado.setForeground((Color)v);
            quadrado.setOpaque(true);quadrado.setBackground(sel?t.getSelectionBackground():t.getBackground());
            quadrado.setToolTipText("Clique para alterar a cor deste ID");return quadrado;
        });
        tabela.addMouseListener(new MouseAdapter(){public void mouseClicked(MouseEvent e){
            int lin=tabela.rowAtPoint(e.getPoint()),col=tabela.columnAtPoint(e.getPoint());
            if(!ocupado&&lin>=0&&col>=0&&tabela.convertColumnIndexToModel(col)==4) {
                int modeloLinha=tabela.convertRowIndexToModel(lin);
                String id=(String)ids.getValueAt(modeloLinha,1);
                Color cor=JColorChooser.showDialog(janela,"Cor do ID "+id,CoresIds.obter(id));
                if(cor!=null)try{CoresIds.salvar(id,cor);ids.setValueAt(cor,modeloLinha,4);}catch(Exception ex){erro(ex);}
            }
        }});
        tabela.setRowHeight(25); tabela.getColumnModel().getColumn(0).setMaxWidth(65); TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(ids); tabela.setRowSorter(sorter);
        tabela.getColumnModel().getColumn(2).setPreferredWidth(180);
        pesquisaId.getDocument().addDocumentListener(new DocumentListener() {
            private void atualizar() {
                String termo = pesquisaId.getText().trim().toUpperCase(Locale.ROOT);
                sorter.setRowFilter(termo.isEmpty() ? null : new RowFilter<DefaultTableModel,Integer>() {
                    public boolean include(Entry<? extends DefaultTableModel,? extends Integer> e) {
                        return e.getStringValue(1).toUpperCase(Locale.ROOT).contains(termo)
                                || e.getStringValue(2).toUpperCase(Locale.ROOT).contains(termo);
                    }
                });
            }
            public void insertUpdate(DocumentEvent e) { atualizar(); } public void removeUpdate(DocumentEvent e) { atualizar(); } public void changedUpdate(DocumentEvent e) { atualizar(); }
        });
        filtrarPrimeiroByte.setToolTipText("Permite apenas "+Util.FiltroHex.DESCRICAO+" no primeiro byte.");
        selecionar.addActionListener(e -> selecionarTxt()); existentes.addActionListener(e -> abrirExistentes()); remover.addActionListener(e -> remover()); filtrar.addActionListener(e -> filtrar());
        fecharValvula.addActionListener(e -> fecharVisualizacao());
        janela.btnCorFundo.addActionListener(e->TemaAplicativo.escolher(janela,true));
        janela.btnCorLetras.addActionListener(e->TemaAplicativo.escolher(janela,false));
        fecharValvula.setToolTipText("Fecha a visualização e mantém os logs, IDs e vínculos salvos.");
        adicionarValvula.addActionListener(e -> editarValvula(null));
        valvulasSalvas.addActionListener(e -> abrirValvulas());
        verTodosIds.addActionListener(e -> usarValvula(null));
        lista.addListSelectionListener(e -> { if(!e.getValueIsAdjusting()) carregarIds(); });
        todos.addActionListener(e -> {
            if (arquivoAtual == 0) return;
            if(todos.isSelected() && !valvulasPorArquivo.containsKey(arquivoAtual)) todosMarcados.add(arquivoAtual);
            else todosMarcados.remove(arquivoAtual);
            Set<String> selecao = marcados.computeIfAbsent(arquivoAtual,k -> new HashSet<>()); selecao.clear();
            atualizando = true;
            for(int i=0;i<ids.getRowCount();i++) { ids.setValueAt(todos.isSelected(),i,0); if(todos.isSelected()) selecao.add((String)ids.getValueAt(i,1)); }
            atualizando = false; sincronizarSelecaoDaValvula(); atualizarResumo();
        });
        ids.addTableModelListener(e -> {
            if(atualizando || e.getColumn()!=0 || arquivoAtual==0) return;
            int r=e.getFirstRow(); if(r<0 || r>=ids.getRowCount()) return;
            Set<String> selecao=marcados.computeIfAbsent(arquivoAtual,k->new HashSet<>());
            String id=(String)ids.getValueAt(r,1);
            if(Boolean.TRUE.equals(ids.getValueAt(r,0))) selecao.add(id); else selecao.remove(id);
            todosMarcados.remove(arquivoAtual); todos.setSelected(false); sincronizarSelecaoDaValvula(); atualizarResumo();
        });
        EstiloAplicativo.arredondarBotoes(this);
        atualizarControlesValvula();
        todos.setEnabled(false);
        janela.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        janela.addWindowListener(new WindowAdapter(){ public void windowClosing(WindowEvent e){
            if(ocupado) JOptionPane.showMessageDialog(janela,"Aguarde a operação terminar antes de fechar."); else janela.dispose();
        }});
    }
    private void fecharVisualizacao() {
        if(ocupado) return;
        if(tabela.isEditing()) tabela.getCellEditor().cancelCellEditing();
        ++versaoIds;
        arquivoAtual=0;
        valvulaSemArquivo=null;
        marcados.clear();
        todosMarcados.clear();
        valvulasPorArquivo.clear();
        cache.clear();
        arquivos.clear();
        lista.clearSelection();
        pesquisaId.setText("");
        carregarIds();
        atualizarControlesValvula();
        status.setText("Visualização fechada. Os cadastros e logs continuam salvos.");
    }

    private void ocupado(boolean valor) {
        ocupado=valor; selecionar.setEnabled(!valor); existentes.setEnabled(!valor); remover.setEnabled(!valor); filtrar.setEnabled(!valor); lista.setEnabled(!valor); todos.setEnabled(!valor && arquivoAtual!=0); progresso.setVisible(valor);
        atualizarControlesValvula();
    }

    private void atualizarControlesValvula() {
        ValvulaCAN valvula = arquivoAtual == 0 ? valvulaSemArquivo : valvulasPorArquivo.get(arquivoAtual);
        valvulaAtual.setText(valvula == null ? "Marque os IDs do arquivo destacado" : "Válvula selecionada: " + valvula.nome() + " · IDs de todos os logs");
        todos.setText(valvula == null ? "Todos os IDs deste arquivo" : "Todos os IDs desta válvula");
        boolean temLog = lista.getSelectedValue()!=null && !hashArquivo(lista.getSelectedValue()).isEmpty();
        adicionarValvula.setEnabled(!ocupado && temLog);
        adicionarValvula.setText(temLog ? "Adicionar válvula" : "🔒 Adicionar válvula");
        adicionarValvula.setToolTipText(temLog ? "Criar uma válvula vinculada ao log destacado."
                : "Selecione um log importado para adicionar uma válvula.");
        remover.setText(valvulasPorArquivo.containsKey(arquivoAtual) ? "Remover log da válvula" : "Remover da seleção");
        boolean temValvula = valvulaSemArquivo!=null || !valvulasPorArquivo.isEmpty();
        fecharValvula.setText(temValvula ? "Fechar válvula" : "Limpar tela");
        fecharValvula.setEnabled(!ocupado && (temValvula || !arquivos.isEmpty()));
        valvulasSalvas.setEnabled(!ocupado);
        verTodosIds.setEnabled(!ocupado && valvula != null);
    }

    private void abrirValvulas() {
        ocupado(true);
        status.setText("Buscando válvulas salvas…");
        new SwingWorker<List<ValvulaCAN>, Void>() {
            @Override protected List<ValvulaCAN> doInBackground() throws Exception {
                return new ValvulaDAO().listar();
            }

            @Override protected void done() {
                ocupado(false);
                atualizarResumo();
                try {
                    List<ValvulaCAN> salvas = get();
                    if (salvas.isEmpty()) {
                        JOptionPane.showMessageDialog(janela, "Ainda não há válvulas salvas. Use Adicionar válvula.");
                        return;
                    }
                    CatalogoValvulas.Escolha escolha = CatalogoValvulas.escolher(janela, salvas);
                    if (escolha == null) return;
                    switch (escolha.acao()) {
                        case EDITAR -> editarValvula(escolha.valvula());
                        case USAR -> abrirValvula(escolha.valvula());
                        case APAGAR -> apagarValvula(escolha.valvula());
                        case LOGS -> gerenciarLogs(escolha.valvula());
                    }
                } catch (Exception e) { erro(e); }
            }
        }.execute();
    }

    private static String hashArquivo(ArquivoCAN arquivo) {
        return arquivo == null || arquivo.getSha256() == null
                ? "" : arquivo.getSha256().toUpperCase(Locale.ROOT);
    }

    private void abrirValvula(ValvulaCAN valvula) {
        ocupado(true);
        status.setText("Abrindo válvula…");
        new SwingWorker<List<ArquivoCAN>, Void>() {
            @Override protected List<ArquivoCAN> doInBackground() throws Exception {
                ValvulaDAO dao = new ValvulaDAO();
                Set<String> hashes = dao.arquivosVinculados(valvula.id());
                if (!hashes.isEmpty()) {
                    List<ArquivoCAN> vinculados = new ArquivoDAO().listarConcluidos().stream()
                            .filter(a -> hashes.contains(hashArquivo(a))).toList();
                    if (!vinculados.isEmpty()) return vinculados;
                }

                return List.of();
            }

            @Override protected void done() {
                ocupado(false);
                try { mostrarValvula(valvula, get()); }
                catch (Exception e) { erro(e); }
            }
        }.execute();
    }

    private void gerenciarLogs(ValvulaCAN valvula) {
        if (GerenciadorLogsValvula.abrir(janela, valvula, (caminho, progresso, pai) ->
                new ImportadorCAN((cabecalho, reconhecidas) ->
                        escolherColunas(pai, caminho.getFileName().toString(), cabecalho, reconhecidas))
                        .importar(caminho, progresso))) {
            cache.clear();
            recarregarValvula(valvula.id());
        }
    }

    private void recarregarValvula(long id) {
        ocupado(true);
        new SwingWorker<ValvulaCAN,Void>() {
            protected ValvulaCAN doInBackground() throws Exception {
                return new ValvulaDAO().listar().stream().filter(v->v.id()==id).findFirst()
                        .orElseThrow(()->new IllegalStateException("Válvula não encontrada."));
            }
            protected void done() {
                ocupado(false);
                try { abrirValvula(get()); } catch(Exception e) { erro(e); }
            }
        }.execute();
    }

    private void apagarValvula(ValvulaCAN valvula) {
        if (!CatalogoValvulas.confirmarExclusao(janela, valvula)) return;
        ocupado(true);
        status.setText("Apagando válvula…");
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws Exception {
                new ValvulaDAO().apagar(valvula.id());
                return null;
            }

            @Override protected void done() {
                ocupado(false);
                try {
                    get();
                    boolean atualizar = (valvulaSemArquivo != null && valvulaSemArquivo.id() == valvula.id())
                            || (valvulasPorArquivo.containsKey(arquivoAtual)
                                    && valvulasPorArquivo.get(arquivoAtual).id() == valvula.id());
                    if (valvulaSemArquivo != null && valvulaSemArquivo.id() == valvula.id()) valvulaSemArquivo = null;
                    valvulasPorArquivo.entrySet().removeIf(entrada -> {
                        if (entrada.getValue().id() != valvula.id()) return false;
                        marcados.remove(entrada.getKey());
                        todosMarcados.remove(entrada.getKey());
                        return true;
                    });
                    if (atualizar) {
                        pesquisaId.setText("");
                        carregarIds();
                    }
                    status.setText("Válvula apagada: " + valvula.nome());
                } catch (Exception e) { erro(e); }
            }
        }.execute();
    }

    private void mostrarValvula(ValvulaCAN valvula, List<ArquivoCAN> vinculados) {
        if (tabela.isEditing()) tabela.getCellEditor().cancelCellEditing();
        valvulaSemArquivo = vinculados.isEmpty() ? valvula : null;
        pesquisaId.setText("");
        lista.getSelectionModel().setValueIsAdjusting(true);
        try {
            arquivos.clear();
            marcados.clear();
            todosMarcados.clear();
            valvulasPorArquivo.clear();
            for (ArquivoCAN arquivo : vinculados) {
                valvulasPorArquivo.put(arquivo.getId(), valvula);
                arquivos.addElement(arquivo);
            }
        } finally {
            lista.getSelectionModel().setValueIsAdjusting(false);
        }
        if (vinculados.isEmpty()) {
            carregarIds();
            status.setText("Válvula sem logs disponíveis. Use Válvulas salvas → Gerenciar logs para adicionar TXT.");
        } else {
            lista.setSelectedIndex(0);
            atualizarResumo();
        }
    }

    private void editarValvula(ValvulaCAN existente) {
        if (existente==null && (lista.getSelectedValue()==null || hashArquivo(lista.getSelectedValue()).isEmpty())) {
            JOptionPane.showMessageDialog(janela,"Selecione um log importado antes de adicionar uma válvula.",
                    "Adicionar válvula",JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        List<ArquivoCAN> selecionados = new ArrayList<>();
        for(int i=0;i<arquivos.size();i++) selecionados.add(arquivos.get(i));
        ocupado(true);
        status.setText("Buscando IDs dos logs…");
        new SwingWorker<OpcoesIds,Void>() {
            protected OpcoesIds doInBackground() throws Exception {
                List<ArquivoCAN> fontes=selecionados;
                if(existente!=null) {
                    Set<String> vinculados=new ValvulaDAO().arquivosVinculados(existente.id());
                    fontes=new ArquivoDAO().listarConcluidos().stream()
                            .filter(a->vinculados.contains(hashArquivo(a))).toList();
                }
                Set<String> hashes=new HashSet<>();
                Set<String> idsDisponiveis=new TreeSet<>();
                MensagemDAO dao=new MensagemDAO();
                for(ArquivoCAN a:fontes) {
                    hashes.add(hashArquivo(a));
                    for(var valor:dao.listarIds(a.getId())) idsDisponiveis.add(valor.id().toUpperCase(Locale.ROOT));
                }
                return new OpcoesIds(List.copyOf(idsDisponiveis),Set.copyOf(hashes),fontes.size());
            }
            protected void done() {
                ocupado(false);
                try { editarValvulaComIds(existente,get()); }
                catch(Exception e) { erro(e); }
            }
        }.execute();
    }
    private record OpcoesIds(List<String> ids, Set<String> hashes, int logs) {}
    private void editarValvulaComIds(ValvulaCAN existente, OpcoesIds opcoes) {
        ValvulaCAN rascunho = CatalogoValvulas.editar(janela, existente, opcoes.ids(), opcoes.logs());
        if (rascunho == null) return;
        ocupado(true);
        status.setText("Salvando válvula…");
        new SwingWorker<ValvulaCAN, Void>() {
            @Override protected ValvulaCAN doInBackground() throws Exception {
                return new ValvulaDAO().salvarComLogs(rascunho, opcoes.hashes());
            }

            @Override protected void done() {
                ocupado(false);
                try {
                    ValvulaCAN salva = get();
                    valvulasPorArquivo.replaceAll((arquivo, valvula) -> {
                        if (valvula.id() != salva.id()) return valvula;
                        marcados.computeIfAbsent(arquivo, k -> new HashSet<>()).retainAll(salva.identificadores().keySet());
                        return salva;
                    });
                    abrirValvula(salva);
                    status.setText("Válvula salva: " + salva.nome());
                } catch (Exception e) { erro(e); }
            }
        }.execute();
    }

    private void usarValvula(ValvulaCAN valvula) {
        if (arquivoAtual == 0) {
            valvulaSemArquivo = valvula;
            carregarIds();
            atualizarResumo();
            return;
        }
        if (tabela.isEditing()) tabela.getCellEditor().stopCellEditing();
        ValvulaCAN anterior = valvula == null ? valvulasPorArquivo.remove(arquivoAtual)
                : valvulasPorArquivo.put(arquivoAtual, valvula);
        if (!Objects.equals(anterior, valvula)) {
            marcados.remove(arquivoAtual);
            todosMarcados.remove(arquivoAtual);
        }
        pesquisaId.setText("");
        carregarIds();
    }
    private void adicionar(ArquivoCAN a) {
        for(int i=0;i<arquivos.size();i++) if(arquivos.get(i).getId()==a.getId()) { lista.setSelectedIndex(i); return; }
        if (valvulaSemArquivo != null) {
            valvulasPorArquivo.put(a.getId(), valvulaSemArquivo);
            valvulaSemArquivo = null;
        }
        arquivos.addElement(a); lista.setSelectedValue(a,true);
    }
    private void selecionarTxt() {
        JFileChooser chooser = new JFileChooser(); chooser.setDialogTitle("Selecionar arquivos CAN"); chooser.setMultiSelectionEnabled(true); chooser.setFileFilter(new FileNameExtensionFilter("Logs CAN (*.txt)","txt"));
        if(chooser.showOpenDialog(janela)!=JFileChooser.APPROVE_OPTION) return;
        File[] escolhidos=chooser.getSelectedFiles(); ocupado(true);
        new SwingWorker<Void,Object>() {
            protected Void doInBackground() {
                for(File f:escolhidos) try { publish(new ImportadorCAN((cabecalho,reconhecidas)->escolherColunas(f.getName(),cabecalho,reconhecidas)).importar(f.toPath(),s->publish(s))); }
                catch(Exception e) { publish(new Falha(f.getName()+": "+mensagem(e))); }
                return null;
            }
            protected void process(List<Object> eventos) { for(Object o:eventos) {
                if(o instanceof ArquivoCAN a) adicionar(a); else if(o instanceof Falha f) JOptionPane.showMessageDialog(janela,f.texto(),"Importação não concluída",JOptionPane.ERROR_MESSAGE); else status.setText(o.toString());
            }}
            protected void done() { ocupado(false); atualizarResumo(); }
        }.execute();
    }
    private record Falha(String texto) {}
    private Map<String,Integer> escolherColunas(String arquivo, String[] cabecalho, Map<String,Integer> reconhecidas) throws Exception {
        return escolherColunas(janela, arquivo, cabecalho, reconhecidas);
    }
    private Map<String,Integer> escolherColunas(Component pai, String arquivo, String[] cabecalho, Map<String,Integer> reconhecidas) throws Exception {
        final Map<String,Integer>[] resposta = new Map[]{null};
        SwingUtilities.invokeAndWait(() -> {
            String[] campos = {"id", "hex", "hora", "direcao", "dlc"};
            String[] titulos = {"ID CAN (obrigatório)", "Dados HEX (obrigatório)", "Horário (opcional)", "Direção RX/TX (opcional)", "DLC (opcional)"};
            String[] opcoes = new String[cabecalho.length+1]; opcoes[0] = "Não informado";
            for(int i=0;i<cabecalho.length;i++) opcoes[i+1] = (i+1) + " — " + cabecalho[i].trim();
            JPanel painel = new JPanel(new GridLayout(0,2,8,8));
            painel.add(new JLabel("Colunas em:")); painel.add(new JLabel(arquivo));
            List<JComboBox<String>> seletores = new ArrayList<>();
            for(int i=0;i<campos.length;i++) {
                JComboBox<String> caixa = new JComboBox<>(opcoes);
                Integer indice = reconhecidas.get(campos[i]); if(indice!=null) caixa.setSelectedIndex(indice+1);
                seletores.add(caixa); painel.add(new JLabel(titulos[i])); painel.add(caixa);
            }
            JCheckBox primeiraDados = new JCheckBox("A primeira linha já contém dados");
            primeiraDados.setToolTipText("Marque se o TXT não possui cabeçalho, para importar também a primeira linha.");
            painel.add(primeiraDados); painel.add(new JLabel("(arquivo sem cabeçalho)"));
            while(JOptionPane.showConfirmDialog(pai,painel,"Identificar colunas",JOptionPane.OK_CANCEL_OPTION,JOptionPane.QUESTION_MESSAGE)==JOptionPane.OK_OPTION) {
                Map<String,Integer> mapa = new HashMap<>(reconhecidas); Set<Integer> usados = new HashSet<>(); boolean valido = true;
                for(int i=0;i<campos.length;i++) { int indice=seletores.get(i).getSelectedIndex()-1; mapa.remove(campos[i]);
                    if(indice>=0) { if(!usados.add(indice)) valido=false; mapa.put(campos[i],indice); }
                }
                if(!mapa.containsKey("id") || !mapa.containsKey("hex") || !valido) { JOptionPane.showMessageDialog(pai,"Escolha colunas diferentes para ID e dados HEX. Não repita uma coluna."); continue; }
                if(primeiraDados.isSelected()) mapa.put("primeira_linha_dados",1);
                resposta[0]=mapa; break;
            }
        });
        return resposta[0];
    }
    private void excluirArquivo(ArquivoCAN arquivo) {
        if(ocupado) return;
        Object[] opcoes={"Excluir definitivamente","Cancelar"};
        if(JOptionPane.showOptionDialog(janela,
                "Excluir "+arquivo.getNomeArquivo()+" e todas as suas mensagens do banco?\nEle será retirado de todas as válvulas. O TXT original no computador não será apagado.",
                "Excluir arquivo importado",JOptionPane.DEFAULT_OPTION,JOptionPane.WARNING_MESSAGE,null,opcoes,opcoes[1])!=0) return;
        ocupado(true);status.setText("Excluindo arquivo importado…");
        new SwingWorker<Void,Void>() {
            protected Void doInBackground() throws Exception {new Principal.ExclusaoArquivo().excluir(arquivo);return null;}
            protected void done(){ocupado(false);try{get();fecharVisualizacao();status.setText("Arquivo excluído do banco.");}catch(Exception e){erro(e);}}
        }.execute();
    }
    private void abrirExistentes() {
        ocupado(true); status.setText("Consultando arquivos importados…");
        new SwingWorker<List<ArquivoCAN>,Void>() {
            protected List<ArquivoCAN> doInBackground() throws Exception { return new ArquivoDAO().listarConcluidos(); }
            protected void done() { ocupado(false); try {
                List<ArquivoCAN> encontrados=get();
                if(encontrados.isEmpty()) { JOptionPane.showMessageDialog(janela,"Nenhum arquivo concluído no banco."); return; }
                ArquivosImportadosDialogo.abrir(janela,encontrados,PainelFiltragem.this::adicionar,PainelFiltragem.this::excluirArquivo);
                atualizarResumo();
            } catch(Exception e) { erro(e); }}
        }.execute();
    }
    private void remover() {
        ArquivoCAN a=lista.getSelectedValue(); if(a==null) return;
        ValvulaCAN valvula=valvulasPorArquivo.get(a.getId());
        if(valvula!=null) {
            Object[] opcoes={"Remover log","Cancelar"};
            if(JOptionPane.showOptionDialog(janela,
                    "Retirar este log da válvula e remover seus IDs exclusivos?\nO arquivo importado continuará disponível.",
                    "Remover log da válvula",JOptionPane.DEFAULT_OPTION,JOptionPane.WARNING_MESSAGE,
                    null,opcoes,opcoes[1])!=0) return;
            ocupado(true); status.setText("Removendo log e atualizando IDs…");
            new SwingWorker<ValvulaCAN,Void>() {
                protected ValvulaCAN doInBackground() throws Exception {
                    Set<String> escolhidos=new HashSet<>(new ValvulaDAO().arquivosVinculados(valvula.id()));
                    escolhidos.remove(hashArquivo(a));
                    return new Principal.VinculosValvula().salvar(valvula.id(),new ArquivoDAO().listarConcluidos(),escolhidos);
                }
                protected void done() {
                    ocupado(false);
                    try { cache.clear(); abrirValvula(get()); } catch(Exception e) { erro(e); }
                }
            }.execute();
            return;
        }
        marcados.remove(a.getId()); todosMarcados.remove(a.getId()); valvulasPorArquivo.remove(a.getId()); arquivos.removeElement(a); atualizarResumo();
    }
    private void carregarIds() {
        ArquivoCAN a=lista.getSelectedValue(); int versao=++versaoIds; arquivoAtual=a==null?0:a.getId();
        atualizarControlesValvula();
        atualizando=true; ids.setRowCount(0); todos.setSelected(false); atualizando=false; todos.setEnabled(false);
        if(a==null) {
            if (valvulaSemArquivo != null) mostrarIds(0, List.of());
            return;
        }
        ValvulaCAN valvula = valvulasPorArquivo.get(a.getId());
        List<Long> grupo = new ArrayList<>();
        for (int i=0;i<arquivos.size();i++) {
            long id = arquivos.get(i).getId();
            ValvulaCAN perfil = valvulasPorArquivo.get(id);
            if (id==a.getId() || (valvula!=null && perfil!=null && perfil.id()==valvula.id())) grupo.add(id);
        }
        Map<Long,List<MensagemDAO.IdDisponivel>> conhecidos = new HashMap<>(cache);
        new SwingWorker<Map<Long,List<MensagemDAO.IdDisponivel>>,Void>() {
            protected Map<Long,List<MensagemDAO.IdDisponivel>> doInBackground() throws Exception {
                Map<Long,List<MensagemDAO.IdDisponivel>> resultado = new LinkedHashMap<>();
                MensagemDAO dao = new MensagemDAO();
                for(long id:grupo) resultado.put(id, conhecidos.containsKey(id) ? conhecidos.get(id) : dao.listarIds(id));
                return resultado;
            }
            protected void done() {
                try {
                    var resultado=get(); cache.putAll(resultado);
                    if(versao!=versaoIds) return;
                    Map<String,Long> totais = new LinkedHashMap<>();
                    for(var valores:resultado.values()) for(var valor:valores)
                        totais.merge(valor.id().toUpperCase(Locale.ROOT),valor.quantidade(),Long::sum);
                    mostrarIds(a.getId(),totais.entrySet().stream()
                            .map(e->new MensagemDAO.IdDisponivel(e.getKey(),e.getValue())).toList());
                    if(valvula!=null) status.setText("Válvula "+valvula.nome()+" · "+grupo.size()+" log(s). Os IDs marcados serão filtrados em todos esses logs.");
                } catch(Exception e) { if(versao==versaoIds) erro(e); }
            }
        }.execute();
    }
    /** Uma marcação da válvula vale para todos os logs dela que estão abertos. */
    private void sincronizarSelecaoDaValvula() {
        ValvulaCAN valvula=valvulasPorArquivo.get(arquivoAtual);
        if(valvula==null) return;
        Set<String> selecao=new HashSet<>(marcados.getOrDefault(arquivoAtual,Set.of()));
        for(int i=0;i<arquivos.size();i++) {
            long arquivo=arquivos.get(i).getId();
            ValvulaCAN perfil=valvulasPorArquivo.get(arquivo);
            if(perfil!=null && perfil.id()==valvula.id()) {
                marcados.put(arquivo,new HashSet<>(selecao));
                todosMarcados.remove(arquivo);
            }
        }
    }
    private void mostrarIds(long arquivo,List<MensagemDAO.IdDisponivel> valores) {
        atualizando=true; ids.setRowCount(0); Set<String> selecao=arquivo == 0 ? Set.of() : marcados.computeIfAbsent(arquivo,k->new HashSet<>());
        ValvulaCAN valvula = arquivo == 0 ? valvulaSemArquivo : valvulasPorArquivo.get(arquivo);
        if (valvula == null) {
            for (var v : valores) ids.addRow(new Object[]{todosMarcados.contains(arquivo) || selecao.contains(v.id()), v.id(), "", v.quantidade(), CoresIds.obter(v.id())});
        } else {
            Map<String, Long> contagens = new HashMap<>();
            for (var v : valores) contagens.put(v.id().toUpperCase(Locale.ROOT), v.quantidade());
            valvula.identificadores().forEach((id, nome) ->
                    ids.addRow(new Object[]{selecao.contains(id), id, nome, contagens.getOrDefault(id, 0L), CoresIds.obter(id)}));
        }
        todos.setSelected(todosMarcados.contains(arquivo) || (valvula != null && !valvula.identificadores().isEmpty()
                && selecao.containsAll(valvula.identificadores().keySet())));
        todos.setEnabled(!ocupado && arquivo != 0 && ids.getRowCount() > 0);
        atualizando=false;
    }
    private void atualizarResumo() { status.setText(arquivos.size()+" arquivo(s). Marque os IDs em cada arquivo e clique em Filtrar."); }
    private void filtrar() {
        if(tabela.isEditing()) tabela.getCellEditor().stopCellEditing();
        try { linhas.commitEdit(); } catch(java.text.ParseException e) { erro(new IllegalArgumentException("Informe uma quantidade de linhas válida.")); return; }
        if((Integer)linhas.getValue()<1 || (Integer)linhas.getValue()>10000) { erro(new IllegalArgumentException("Escolha de 1 a 10.000 linhas por página.")); return; }
        List<SelecaoFiltro> selecoes=new ArrayList<>(); Map<Long,String> nomes=new HashMap<>();
        for(int i=0;i<arquivos.size();i++) { ArquivoCAN a=arquivos.get(i); nomes.put(a.getId(),a.getNomeArquivo());
            if(todosMarcados.contains(a.getId())) selecoes.add(new SelecaoFiltro(a.getId(),null));
            else for(String id:marcados.getOrDefault(a.getId(),Set.of())) selecoes.add(new SelecaoFiltro(a.getId(),id));
        }
        if(selecoes.isEmpty()) { JOptionPane.showMessageDialog(janela,"Marque pelo menos um ID ou a opção Todos os IDs."); return; }
        new TelaResultados(selecoes, nomes, (Integer) linhas.getValue(), filtrarPrimeiroByte.isSelected()).setVisible(true);
    }
    static String mensagem(Throwable e) {
        while(e instanceof java.util.concurrent.ExecutionException && e.getCause()!=null) e=e.getCause();
        return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();
    }
    private void erro(Exception e) { status.setText("Não foi possível concluir a operação."); JOptionPane.showMessageDialog(janela,mensagem(e),"Atenção",JOptionPane.ERROR_MESSAGE); }
}


