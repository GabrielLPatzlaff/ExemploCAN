package Principal;

import Dao.ArquivoDAO;
import Dao.Conexao;
import Dao.MensagemDAO;
import Model.ArquivoCAN;
import Util.CalculadorSHA256;
import Util.LeitorArquivoCAN;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.function.Consumer;

public class ImportadorCAN {
    private final LeitorArquivoCAN.EscolherColunas escolha;

    public ImportadorCAN() { this(null); }

    public ImportadorCAN(LeitorArquivoCAN.EscolherColunas escolha) { this.escolha = escolha; }

    public ArquivoCAN importar(Path caminho, Consumer<String> progresso) throws Exception {
        String nome = caminho.getFileName().toString();
        progresso.accept("Verificando " + nome + "…");
        String sha = CalculadorSHA256.calcular(caminho);
        ArquivoDAO arquivos = new ArquivoDAO();
        MensagemDAO mensagens = new MensagemDAO();

        try (Connection conexao = Conexao.conectar()) {
            conexao.setAutoCommit(false);
            try {
                ArquivoCAN existente = arquivos.procurarPorHash(conexao, sha);
                if (existente != null) {
                    conexao.commit();
                    return existente;
                }
                ArquivoCAN arquivo = arquivos.cadastrar(conexao, nome, sha);
                long[] total = {0};
                try (PreparedStatement insercao = mensagens.prepararInsercao(conexao)) {
                    new LeitorArquivoCAN(escolha).ler(caminho, mensagem -> {
                        mensagens.adicionarLote(insercao, mensagem, arquivo.getId());
                        total[0]++;
                        if (total[0] % 1000 == 0) {
                            insercao.executeBatch();
                            insercao.clearBatch();
                            progresso.accept(nome + " · " + String.format("%,d", total[0]) + " mensagens processadas");
                        }
                    });
                    insercao.executeBatch();
                }
                if (!sha.equals(CalculadorSHA256.calcular(caminho))) {
                    throw new IllegalStateException("O TXT mudou durante a importação. Selecione uma cópia estável.");
                }
                conexao.commit();
                return arquivo;
            } catch (Exception erro) {
                try { conexao.rollback(); } catch (SQLException rollback) { erro.addSuppressed(rollback); }
                throw erro;
            }
        }
    }
}
