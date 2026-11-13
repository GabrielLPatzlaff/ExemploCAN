package Dao;

import Model.ValvulaCAN;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

public class ValvulaDAO {
    private record Cadastro(String nome, Map<String, String> identificadores) {}

    public void apagar(long idValvula) throws SQLException {
        try (Connection conexao = Conexao.conectar();
             PreparedStatement comando = conexao.prepareStatement("DELETE FROM valvulas_can WHERE id = ?")) {
            comando.setLong(1, idValvula);
            comando.executeUpdate();
        }
    }

    public Set<String> arquivosVinculados(long idValvula) throws SQLException {
        Set<String> hashes = new HashSet<>();
        try (Connection conexao = Conexao.conectar();
             PreparedStatement consulta = conexao.prepareStatement(
                     "SELECT sha256 FROM arquivos_valvula WHERE valvula_id = ?")) {
            consulta.setLong(1, idValvula);
            try (ResultSet linhas = consulta.executeQuery()) {
                while (linhas.next()) hashes.add(linhas.getString(1).toUpperCase(Locale.ROOT));
            }
        }
        return hashes;
    }

    public void salvarVinculos(long idValvula, Set<String> disponiveis, Set<String> escolhidos) throws SQLException {
        salvarVinculos(idValvula,disponiveis,escolhidos,Set.of());
    }
    public void salvarVinculos(long idValvula, Set<String> disponiveis, Set<String> escolhidos, Set<String> idsRemover) throws SQLException {
        if (!disponiveis.containsAll(escolhidos)) throw new IllegalArgumentException("Arquivo indisponível.");
        try (Connection conexao = Conexao.conectar()) {
            conexao.setAutoCommit(false);
            try (PreparedStatement apagar = conexao.prepareStatement(
                         "DELETE FROM arquivos_valvula WHERE valvula_id = ? AND sha256 = ?");
                 PreparedStatement inserir = conexao.prepareStatement(
                         "INSERT OR IGNORE INTO arquivos_valvula (valvula_id, sha256) VALUES (?, ?)")) {
                for (String hash : disponiveis) {
                    PreparedStatement comando = escolhidos.contains(hash) ? inserir : apagar;
                    comando.setLong(1, idValvula);
                    comando.setString(2, hash);
                    comando.addBatch();
                }
                apagar.executeBatch();
                inserir.executeBatch();
                try(PreparedStatement apagarId=conexao.prepareStatement("DELETE FROM ids_valvula WHERE valvula_id=? AND id_can=?")) {
                    for(String id:idsRemover) {
                        apagarId.setLong(1,idValvula); apagarId.setString(2,id); apagarId.addBatch();
                    }
                    apagarId.executeBatch();
                }
                conexao.commit();
            } catch (SQLException erro) {
                try { conexao.rollback(); } catch (SQLException rollback) { erro.addSuppressed(rollback); }
                throw erro;
            }
        }
    }

    public List<ValvulaCAN> listar() throws SQLException {
        Map<Long, Cadastro> registros = new LinkedHashMap<>();
        try (Connection conexao = Conexao.conectar();
             PreparedStatement consulta = conexao.prepareStatement("""
                     SELECT v.id, v.nome, i.id_can, i.descricao
                     FROM valvulas_can v LEFT JOIN ids_valvula i ON i.valvula_id = v.id
                     ORDER BY v.nome COLLATE NOCASE, i.rowid
                     """);
             ResultSet linhas = consulta.executeQuery()) {
            while (linhas.next()) {
                long id = linhas.getLong("id");
                Cadastro cadastro = registros.get(id);
                if (cadastro == null) {
                    cadastro = new Cadastro(linhas.getString("nome"), new LinkedHashMap<>());
                    registros.put(id, cadastro);
                }
                String idCan = linhas.getString("id_can");
                if (idCan != null) cadastro.identificadores().put(idCan, linhas.getString("descricao"));
            }
        }
        return registros.entrySet().stream()
                .map(e -> new ValvulaCAN(e.getKey(), e.getValue().nome(), e.getValue().identificadores()))
                .toList();
    }

    public ValvulaCAN salvar(ValvulaCAN valvula) throws SQLException {
        return salvar(valvula, "");
    }

    public ValvulaCAN salvar(ValvulaCAN valvula, String arquivoDestacado) throws SQLException {
        return salvarComLogs(valvula, arquivoDestacado.isEmpty() ? Set.of() : Set.of(arquivoDestacado));
    }
    public ValvulaCAN salvarComLogs(ValvulaCAN valvula, Set<String> arquivosDestacados) throws SQLException {
        String nome = valvula.nome().strip();
        if (nome.isEmpty()) throw new IllegalArgumentException("Informe o nome da válvula.");
        Map<String, String> ids = new LinkedHashMap<>();
        for (var entrada : valvula.identificadores().entrySet()) {
            String idCan = entrada.getKey().strip().toUpperCase(Locale.ROOT);
            String descricao = entrada.getValue().strip();
            if (!idCan.matches("[0-9A-Z]{1,8}")) {
                throw new IllegalArgumentException("ID CAN inválido: " + idCan);
            }
            if (descricao.isEmpty()) throw new IllegalArgumentException("Informe o nome do ID " + idCan + ".");
            if (ids.putIfAbsent(idCan, descricao) != null) {
                throw new IllegalArgumentException("ID CAN repetido: " + idCan);
            }
        }
        try (Connection conexao = Conexao.conectar()) {
            conexao.setAutoCommit(false);
            try {
                long id = salvarNome(conexao, valvula.id(), nome);
                try (PreparedStatement apagar = conexao.prepareStatement("DELETE FROM ids_valvula WHERE valvula_id = ?");
                     PreparedStatement inserir = conexao.prepareStatement(
                             "INSERT INTO ids_valvula (valvula_id, id_can, descricao) VALUES (?, ?, ?)")) {
                    apagar.setLong(1, id);
                    apagar.executeUpdate();
                    for (var entrada : ids.entrySet()) {
                        inserir.setLong(1, id);
                        inserir.setString(2, entrada.getKey());
                        inserir.setString(3, entrada.getValue());
                        inserir.addBatch();
                    }
                    inserir.executeBatch();
                }
                if (valvula.id() == 0 && !arquivosDestacados.isEmpty()) {
                    try (PreparedStatement vinculo = conexao.prepareStatement(
                            "INSERT INTO arquivos_valvula (valvula_id, sha256) VALUES (?, ?)")) {
                        for (String hash : arquivosDestacados) {
                            vinculo.setLong(1,id);
                            vinculo.setString(2,hash);
                            vinculo.addBatch();
                        }
                        vinculo.executeBatch();
                    }
                }
                conexao.commit();
                return new ValvulaCAN(id, nome, ids);
            } catch (SQLException | RuntimeException erro) {
                try { conexao.rollback(); } catch (SQLException rollback) { erro.addSuppressed(rollback); }
                throw erro;
            }
        }
    }

    private long salvarNome(Connection conexao, long id, String nome) throws SQLException {
        try (PreparedStatement consulta = conexao.prepareStatement("SELECT 1 FROM valvulas_can WHERE nome = ? AND id <> ?")) {
            consulta.setString(1, nome);
            consulta.setLong(2, id);
            try (ResultSet linhas = consulta.executeQuery()) {
                if (linhas.next()) throw new IllegalArgumentException("Já existe uma válvula com esse nome.");
            }
        }
        String sql = id == 0 ? "INSERT INTO valvulas_can (nome) VALUES (?) RETURNING id"
                : "UPDATE valvulas_can SET nome = ? WHERE id = ? RETURNING id";
        try (PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setString(1, nome);
            if (id != 0) comando.setLong(2, id);
            try (ResultSet linhas = comando.executeQuery()) {
                if (!linhas.next()) throw new SQLException("A válvula selecionada já não está disponível.");
                return linhas.getLong(1);
            }
        }
    }
}
