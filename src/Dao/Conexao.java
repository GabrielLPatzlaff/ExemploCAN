package Dao;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.sqlite.SQLiteConfig;

public final class Conexao {
    private static final Path PASTA = pastaDados();
    private static boolean inicializado;

    private Conexao() {}

    private static Path pastaDados() {
        String pasta = System.getProperty("exemplocan.dados");
        if (pasta != null && !pasta.isBlank()) return Path.of(pasta).toAbsolutePath();
        String local = System.getenv("LOCALAPPDATA");
        Path base = local == null || local.isBlank()
                ? Path.of(System.getProperty("user.home")) : Path.of(local);
        return base.resolve("Exemplo CAN");
    }

    public static synchronized Connection conectar() throws SQLException {
        try {
            Files.createDirectories(PASTA);
        } catch (IOException erro) {
            throw new SQLException("Não foi possível criar a pasta de dados do Exemplo CAN.", erro);
        }
        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        config.setBusyTimeout(10000);
        config.setTransactionMode(SQLiteConfig.TransactionMode.IMMEDIATE);
        Connection conexao = DriverManager.getConnection(
                "jdbc:sqlite:" + PASTA.resolve("filtragem-can.sqlite"), config.toProperties());
        if (inicializado) return conexao;

        try (Statement sql = conexao.createStatement()) {
            sql.execute("PRAGMA journal_mode=WAL");
            sql.execute("""
                CREATE TABLE IF NOT EXISTS arquivos_can (
                    id INTEGER PRIMARY KEY,
                    nome_arquivo TEXT NOT NULL,
                    sha256 TEXT NOT NULL UNIQUE
                )
                """);
            sql.execute("""
                CREATE TABLE IF NOT EXISTS mensagens_can (
                    id INTEGER PRIMARY KEY,
                    numero_registro INTEGER NOT NULL,
                    horario TEXT,
                    direcao TEXT CHECK (direcao IN ('RX', 'TX')),
                    id_can TEXT NOT NULL COLLATE NOCASE,
                    dlc INTEGER CHECK (dlc BETWEEN 0 AND 8),
                    dados_hex TEXT,
                    ascii_dados TEXT,
                    delta_dt TEXT,
                    arquivo_id INTEGER NOT NULL REFERENCES arquivos_can(id)
                )
                """);
            sql.execute("CREATE INDEX IF NOT EXISTS idx_mensagens_arquivo_id ON mensagens_can (arquivo_id, id_can, id)");
            sql.execute("CREATE INDEX IF NOT EXISTS idx_mensagens_arquivo_ordem ON mensagens_can (arquivo_id, id)");
            sql.execute("""
                CREATE TABLE IF NOT EXISTS valvulas_can (
                    id INTEGER PRIMARY KEY,
                    nome TEXT NOT NULL COLLATE NOCASE UNIQUE
                )
                """);
            sql.execute("""
                CREATE TABLE IF NOT EXISTS ids_valvula (
                    valvula_id INTEGER NOT NULL REFERENCES valvulas_can(id) ON DELETE CASCADE,
                    id_can TEXT NOT NULL COLLATE NOCASE,
                    descricao TEXT NOT NULL,
                    PRIMARY KEY (valvula_id, id_can)
                )
                """);
            sql.execute("""
                CREATE TABLE IF NOT EXISTS arquivos_valvula (
                    valvula_id INTEGER NOT NULL REFERENCES valvulas_can(id) ON DELETE CASCADE,
                    sha256 TEXT NOT NULL COLLATE NOCASE,
                    PRIMARY KEY (valvula_id, sha256)
                )
                """);
            inicializado = true;
            return conexao;
        } catch (SQLException erro) {
            try { conexao.close(); } catch (SQLException fechamento) { erro.addSuppressed(fechamento); }
            throw erro;
        }
    }
}

