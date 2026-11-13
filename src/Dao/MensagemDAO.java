package Dao;

import Model.MensagemCAN;
import Model.SelecaoFiltro;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

public class MensagemDAO {
    private static final DateTimeFormatter HORARIO = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    public record IdDisponivel(String id, long quantidade) {}

    public List<IdDisponivel> listarIds(long arquivo) throws SQLException {
        List<IdDisponivel> ids = new ArrayList<>();
        try (Connection conexao = Conexao.conectar();
             PreparedStatement consulta = conexao.prepareStatement(
                     "SELECT id_can, COUNT(*) FROM mensagens_can WHERE arquivo_id = ? GROUP BY id_can ORDER BY id_can")) {
            consulta.setLong(1, arquivo);
            try (ResultSet linhas = consulta.executeQuery()) {
                while (linhas.next()) ids.add(new IdDisponivel(linhas.getString(1), linhas.getLong(2)));
            }
        }
        return ids;
    }

    public List<MensagemCAN> filtrar(List<SelecaoFiltro> selecoes, int limite, long ultimo) throws SQLException {
        return filtrar(selecoes, limite, ultimo, false);
    }

    public List<MensagemCAN> filtrar(List<SelecaoFiltro> selecoes, int limite, long ultimo,
            boolean filtrarPrimeiroByte) throws SQLException {
        if (selecoes.isEmpty() || limite < 1 || limite > 10000 || ultimo < 0) {
            throw new IllegalArgumentException("Selecione IDs e uma quantidade de 1 a 10.000 linhas.");
        }
        StringJoiner json = new StringJoiner(",", "[", "]");
        for (SelecaoFiltro selecao : selecoes) {
            String idCan = selecao.getIdCan();
            if (selecao.getArquivoId() < 1 || (idCan != null && !idCan.matches("[0-9A-Za-z]{1,8}"))) {
                throw new IllegalArgumentException("Seleção inválida.");
            }
            json.add("{\"arquivo\":" + selecao.getArquivoId() + ",\"id\":"
                    + (idCan == null ? "null" : "\"" + idCan + "\"") + "}");
        }
        String sql = """
                WITH selecao AS (
                    SELECT json_extract(value, '$.arquivo') AS arquivo,
                           json_extract(value, '$.id') AS id_can
                    FROM json_each(?)
                )
                SELECT m.*
                FROM mensagens_can m
                WHERE m.id > ?
                  AND m.arquivo_id IN (SELECT arquivo FROM selecao)
                  AND (m.arquivo_id IN (SELECT arquivo FROM selecao WHERE id_can IS NULL)
                       OR (m.arquivo_id, m.id_can) IN (SELECT arquivo, id_can FROM selecao WHERE id_can IS NOT NULL))
                """;
        if (filtrarPrimeiroByte) {
            sql += """
                    AND substr(ltrim(m.dados_hex, ' ' || char(9) || char(10) || char(13)), 1, 2)
                        IN (%s)
                    """.formatted(Util.FiltroHex.listaSQL());
        }
        sql += " ORDER BY m.id LIMIT ?";
        List<MensagemCAN> mensagens = new ArrayList<>();
        try (Connection conexao = Conexao.conectar(); PreparedStatement consulta = conexao.prepareStatement(sql)) {
            consulta.setString(1, json.toString());
            consulta.setLong(2, ultimo);
            consulta.setInt(3, limite);
            try (ResultSet linhas = consulta.executeQuery()) {
                while (linhas.next()) {
                    String horario = linhas.getString("horario");
                    int dlc = linhas.getInt("dlc");
                    Integer tamanho = linhas.wasNull() ? null : dlc;
                    mensagens.add(new MensagemCAN(linhas.getLong("id"), linhas.getInt("numero_registro"),
                            horario == null ? null : LocalTime.parse(horario), linhas.getString("direcao"),
                            linhas.getString("id_can"), tamanho, linhas.getString("dados_hex"),
                            linhas.getString("ascii_dados"), linhas.getString("delta_dt"), linhas.getLong("arquivo_id")));
                }
            }
        }
        return mensagens;
    }

    public List<MensagemCAN> filtrar(List<SelecaoFiltro> selecoes, int limite, long ultimo,
            boolean primeiroByte, java.util.Set<String> ocultos) throws SQLException {
        if(ocultos.isEmpty()) return filtrar(selecoes,limite,ultimo,primeiroByte);
        return ConsultaDadosHex.filtrar(selecoes,limite,ultimo,primeiroByte,ocultos);
    }

    public PreparedStatement prepararInsercao(Connection conexao) throws SQLException {
        return conexao.prepareStatement("""
                INSERT INTO mensagens_can (numero_registro, horario, direcao, id_can, dlc,
                    dados_hex, ascii_dados, delta_dt, arquivo_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """);
    }

    public void adicionarLote(PreparedStatement insercao, MensagemCAN mensagem, long arquivo) throws SQLException {
        insercao.setInt(1, mensagem.getNumeroRegistro());
        insercao.setString(2, mensagem.getHorario() == null ? null : mensagem.getHorario().format(HORARIO));
        insercao.setString(3, mensagem.getDirecao());
        insercao.setString(4, mensagem.getIdCan());
        if (mensagem.getDlc() == null) insercao.setNull(5, Types.INTEGER);
        else insercao.setInt(5, mensagem.getDlc());
        insercao.setString(6, mensagem.getDadosHex());
        insercao.setString(7, mensagem.getAsciiDados());
        insercao.setString(8, mensagem.getDeltaDt());
        insercao.setLong(9, arquivo);
        insercao.addBatch();
    }
}
