package Dao;

import Model.ArquivoCAN;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ArquivoDAO {
    public void excluir(Model.ArquivoCAN arquivo) throws SQLException {
        try(Connection c=Conexao.conectar()) {
            c.setAutoCommit(false);
            try {
                try(PreparedStatement checar=c.prepareStatement("SELECT sha256 FROM arquivos_can WHERE id=?")) {
                    checar.setLong(1,arquivo.getId());
                    try(ResultSet r=checar.executeQuery()) {
                        if(!r.next() || !r.getString(1).equalsIgnoreCase(arquivo.getSha256())) throw new SQLException("O arquivo não está mais disponível para exclusão.");
                    }
                }
                try(PreparedStatement p=c.prepareStatement("DELETE FROM mensagens_can WHERE arquivo_id=?")) {
                    p.setLong(1,arquivo.getId());p.executeUpdate();
                }
                try(PreparedStatement p=c.prepareStatement("DELETE FROM arquivos_can WHERE id=? AND sha256=?")) {
                    p.setLong(1,arquivo.getId());p.setString(2,arquivo.getSha256());
                    if(p.executeUpdate()!=1) throw new SQLException("O cadastro do arquivo foi alterado.");
                }
                c.commit();
            }catch(SQLException e){try{c.rollback();}catch(SQLException falha){e.addSuppressed(falha);}throw e;}
        }
    }

    public List<ArquivoCAN> listarConcluidos() throws SQLException {
        List<ArquivoCAN> arquivos = new ArrayList<>();
        try (Connection conexao = Conexao.conectar();
             PreparedStatement consulta = conexao.prepareStatement("SELECT id, nome_arquivo, sha256 FROM arquivos_can ORDER BY id");
             ResultSet linhas = consulta.executeQuery()) {
            while (linhas.next()) arquivos.add(lerArquivo(linhas));
        }
        return arquivos;
    }

    public ArquivoCAN procurarPorHash(Connection conexao, String sha) throws SQLException {
        try (PreparedStatement consulta = conexao.prepareStatement(
                "SELECT id, nome_arquivo, sha256 FROM arquivos_can WHERE sha256 = ?")) {
            consulta.setString(1, sha);
            try (ResultSet linhas = consulta.executeQuery()) {
                return linhas.next() ? lerArquivo(linhas) : null;
            }
        }
    }

    public ArquivoCAN cadastrar(Connection conexao, String nome, String sha) throws SQLException {
        try (PreparedStatement comando = conexao.prepareStatement(
                "INSERT INTO arquivos_can (nome_arquivo, sha256) VALUES (?, ?) RETURNING id, nome_arquivo, sha256")) {
            comando.setString(1, nome);
            comando.setString(2, sha);
            try (ResultSet linhas = comando.executeQuery()) {
                if (!linhas.next()) throw new SQLException("O cadastro do arquivo não retornou um ID.");
                return lerArquivo(linhas);
            }
        }
    }

    private static ArquivoCAN lerArquivo(ResultSet linha) throws SQLException {
        ArquivoCAN arquivo = new ArquivoCAN();
        arquivo.setId(linha.getLong("id"));
        arquivo.setNomeArquivo(linha.getString("nome_arquivo"));
        arquivo.setSha256(linha.getString("sha256"));
        arquivo.setStatusImportacao("CONCLUIDO");
        return arquivo;
    }
}
