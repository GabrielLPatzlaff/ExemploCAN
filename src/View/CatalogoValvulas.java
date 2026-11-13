package View;

import Model.ValvulaCAN;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;

final class CatalogoValvulas {
    private CatalogoValvulas() {}

    enum Acao { USAR, EDITAR, APAGAR, LOGS }

    record Escolha(ValvulaCAN valvula, Acao acao) {}

    static Escolha escolher(Component pai, List<ValvulaCAN> valvulas) {
        JList<ValvulaCAN> lista = new JList<>(valvulas.toArray(ValvulaCAN[]::new));
        lista.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        lista.setSelectedIndex(0);
        JScrollPane rolagem = new JScrollPane(lista);
        rolagem.setPreferredSize(new Dimension(420, 220));
        Object[] opcoes = {"Usar válvula", "Editar IDs", "Apagar válvula", "Gerenciar logs", "Fechar"};
        JOptionPane janela = new JOptionPane(rolagem, JOptionPane.PLAIN_MESSAGE,
                JOptionPane.DEFAULT_OPTION, null, opcoes, opcoes[0]);
        lista.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent evento) {
                int indice = lista.locationToIndex(evento.getPoint());
                if (evento.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(evento)
                        && indice >= 0 && lista.getCellBounds(indice, indice).contains(evento.getPoint())) {
                    janela.setValue(opcoes[0]);
                }
            }
        });
        JDialog dialogo = janela.createDialog(pai, "Válvulas salvas");
        try { dialogo.setVisible(true); } finally { dialogo.dispose(); }
        Object acao = janela.getValue();
        ValvulaCAN escolhida = lista.getSelectedValue();
        if (escolhida == null) return null;
        if (opcoes[0].equals(acao)) return new Escolha(escolhida, Acao.USAR);
        if (opcoes[1].equals(acao)) return new Escolha(escolhida, Acao.EDITAR);
        if (opcoes[2].equals(acao)) return new Escolha(escolhida, Acao.APAGAR);
        if (opcoes[3].equals(acao)) return new Escolha(escolhida, Acao.LOGS);
        return null;
    }

    static boolean confirmarExclusao(Component pai, ValvulaCAN valvula) {
        Object[] primeiroAviso = {"Continuar", "Cancelar"};
        if (JOptionPane.showOptionDialog(pai, "Apagar a válvula “" + valvula.nome()
                + "” e os nomes de seus IDs?", "Apagar válvula", JOptionPane.DEFAULT_OPTION,
                JOptionPane.WARNING_MESSAGE, null, primeiroAviso, primeiroAviso[1]) != 0) return false;

        Object[] segundoAviso = {"Apagar definitivamente", "Cancelar"};
        return JOptionPane.showOptionDialog(pai,
                "O cadastro de “" + valvula.nome() + "” será apagado em definitivo.\n"
                + "Os logs importados continuarão disponíveis.",
                "Confirmação final", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE,
                null, segundoAviso, segundoAviso[1]) == 0;
    }

    static ValvulaCAN editar(Component pai, ValvulaCAN existente) {
        return editar(pai, existente, List.of(), 0);
    }

    static ValvulaCAN editar(Component pai, ValvulaCAN existente, List<String> disponiveis, int totalLogs) {
        JTextField nome = new JTextField(existente == null ? "" : existente.nome(), 24);
        JComboBox<String> escolhaId = new JComboBox<>(disponiveis.toArray(String[]::new));
        escolhaId.setEditable(true);
        escolhaId.setMaximumRowCount(12);
        escolhaId.setSelectedItem(null);
        JTextField id = (JTextField) escolhaId.getEditor().getEditorComponent();
        id.setColumns(12);
        escolhaId.setToolTipText("Escolha um ID dos logs ou digite um ID manualmente.");
        id.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent evento) {
                if (SwingUtilities.isLeftMouseButton(evento) && escolhaId.getItemCount()>0)
                    SwingUtilities.invokeLater(() -> { if(escolhaId.isShowing()) escolhaId.showPopup(); });
            }
        });
        JTextField descricao = new JTextField(20);
        JButton adicionar = new JButton("Adicionar ID");
        DefaultTableModel modelo = new DefaultTableModel(new String[]{"ID CAN", "Nome do ID", "Ação"}, 0) {
            @Override public boolean isCellEditable(int linha, int coluna) { return coluna == 2; }
        };
        if (existente != null) {
            existente.identificadores().forEach((chave, valor) -> modelo.addRow(new String[]{chave, valor, "Remover"}));
        }
        JTable tabela = new JTable(modelo);
        tabela.setRowHeight(30);
        tabela.getColumnModel().getColumn(0).setPreferredWidth(110);
        tabela.getColumnModel().getColumn(1).setPreferredWidth(280);
        tabela.getColumnModel().getColumn(2).setMaxWidth(100);
        tabela.getColumnModel().getColumn(2).setCellRenderer((t, valor, selecionada, foco, linha, coluna) -> new JButton("Remover"));
        tabela.getColumnModel().getColumn(2).setCellEditor(new RemoverID(modelo));

        JPanel campos = new JPanel(new GridLayout(0, 2, 8, 6));
        campos.add(new JLabel("Nome da válvula")); campos.add(nome);
        campos.add(new JLabel("ID CAN dos logs")); campos.add(escolhaId);
        campos.add(new JLabel("Nome do ID")); campos.add(descricao);
        campos.add(new JLabel()); campos.add(adicionar);
        if (totalLogs>0) {
            campos.add(new JLabel("Origem da lista"));
            campos.add(new JLabel(totalLogs+" log(s) · "+disponiveis.size()+" ID(s) distintos"));
        }
        JScrollPane rolagem = new JScrollPane(tabela);
        rolagem.setPreferredSize(new Dimension(500, 200));
        JPanel painel = new JPanel(new BorderLayout(0, 10));
        painel.add(campos, BorderLayout.NORTH);
        painel.add(rolagem, BorderLayout.CENTER);

        adicionar.addActionListener(e -> {
            String chave = id.getText().strip().toUpperCase(Locale.ROOT);
            String valor = descricao.getText().strip();
            if (!chave.matches("[0-9A-Z]{1,8}")) {
                JOptionPane.showMessageDialog(painel, "Use de 1 a 8 letras de A a Z ou números de 0 a 9, sem espaços. Exemplos: 001345 e ABCDEFGH.");
                return;
            }
            if (valor.isEmpty()) {
                JOptionPane.showMessageDialog(painel, "Informe o nome do ID.");
                return;
            }
            for (int i = 0; i < modelo.getRowCount(); i++) {
                if (chave.equals(modelo.getValueAt(i, 0))) {
                    JOptionPane.showMessageDialog(painel, "Esse ID já está na lista. Remova-o antes de cadastrar outro nome.");
                    return;
                }
            }
            modelo.addRow(new String[]{chave, valor, "Remover"});
            escolhaId.setSelectedItem(null);
            id.setText("");
            descricao.setText("");
            id.requestFocusInWindow();
        });

        Object[] opcoes = {"Salvar válvula", "Cancelar"};
        String titulo = existente == null ? "Adicionar válvula" : "Editar válvula";
        while (JOptionPane.showOptionDialog(pai, painel, titulo, JOptionPane.DEFAULT_OPTION,
                JOptionPane.PLAIN_MESSAGE, null, opcoes, opcoes[0]) == 0) {
            if (nome.getText().isBlank()) {
                JOptionPane.showMessageDialog(painel, "Informe o nome da válvula.");
                continue;
            }
            if (!id.getText().isBlank() || !descricao.getText().isBlank()) {
                JOptionPane.showMessageDialog(painel, "Clique em Adicionar ID para incluir os dados digitados ou limpe esses campos.");
                continue;
            }
            Map<String, String> ids = new LinkedHashMap<>();
            for (int i = 0; i < modelo.getRowCount(); i++) {
                ids.put((String) modelo.getValueAt(i, 0), (String) modelo.getValueAt(i, 1));
            }
            return new ValvulaCAN(existente == null ? 0 : existente.id(), nome.getText().strip(), ids);
        }
        return null;
    }

    private static final class RemoverID extends AbstractCellEditor implements TableCellEditor {
        private final JButton botao = new JButton("Remover");
        private int linha;

        RemoverID(DefaultTableModel modelo) {
            botao.addActionListener(e -> {
                int indice = linha;
                fireEditingStopped();
                modelo.removeRow(indice);
            });
        }

        @Override public Object getCellEditorValue() { return "Remover"; }

        @Override public Component getTableCellEditorComponent(JTable tabela, Object valor, boolean selecionada,
                int linha, int coluna) {
            this.linha = tabela.convertRowIndexToModel(linha);
            return botao;
        }
    }
}
