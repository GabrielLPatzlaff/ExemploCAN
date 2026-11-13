package Util;

import Dao.MensagemDAO;
import Model.MensagemCAN;
import Model.SelecaoFiltro;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.LongConsumer;

public final class ExportadorCSV {
    private static final int TAMANHO_LOTE = 5000;
    private static final DateTimeFormatter HORARIO = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private ExportadorCSV() {}

    public static long exportar(Path destino, boolean substituir, List<SelecaoFiltro> selecoes,
            Map<Long, String> nomes, boolean filtrarPrimeiroByte, LongConsumer progresso) throws IOException, SQLException {
        return exportar(destino,substituir,selecoes,nomes,filtrarPrimeiroByte,java.util.Set.of(),progresso);
    }

    public static long exportar(Path destino, boolean substituir, List<SelecaoFiltro> selecoes,
            Map<Long, String> nomes, boolean filtrarPrimeiroByte, java.util.Set<String> ocultos,
            LongConsumer progresso) throws IOException, SQLException {
        ocultos=Dao.ConsultaDadosHex.conferirOcultos(ocultos);
        destino = destino.toAbsolutePath();
        Path temporario = Files.createTempFile(destino.getParent(), ".can-exemplo-", ".tmp");
        long total = 0;
        try {
            MensagemDAO dao = new MensagemDAO();
            try (BufferedWriter csv = Files.newBufferedWriter(temporario, StandardCharsets.UTF_8)) {
                csv.write('\uFEFF');
                csv.write("Arquivo;Registro;Horário;Direção;ID CAN;DLC;Dados HEX;Delta (dt)\r\n");
                long ultimo = 0;
                while (true) {
                    List<MensagemCAN> lote = dao.filtrar(selecoes, TAMANHO_LOTE, ultimo, filtrarPrimeiroByte,ocultos);
                    for (MensagemCAN mensagem : lote) {
                        String arquivo = nomes.getOrDefault(mensagem.getArquivoId(), "Arquivo " + mensagem.getArquivoId());
                        escreverLinha(csv, textoPlanilha(arquivo), mensagem.getNumeroRegistro(),
                                mensagem.getHorario() == null ? null : HORARIO.format(mensagem.getHorario()),
                                mensagem.getDirecao(), mensagem.getIdCan(), mensagem.getDlc(),
                                mensagem.getDadosHex(), textoPlanilha(mensagem.getDeltaDt()));
                        ultimo = mensagem.getId();
                    }
                    total += lote.size();
                    progresso.accept(total);
                    if (lote.size() < TAMANHO_LOTE) break;
                }
            }
            if (substituir) {
                Files.move(temporario, destino, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.move(temporario, destino);
            }
            return total;
        } finally {
            Files.deleteIfExists(temporario);
        }
    }

    private static void escreverLinha(BufferedWriter csv, Object... valores) throws IOException {
        StringJoiner linha = new StringJoiner(";");
        for (Object valor : valores) {
            String texto = valor == null ? "" : valor.toString();
            linha.add("\"" + texto.replace("\"", "\"\"") + "\"");
        }
        csv.write(linha.toString());
        csv.write("\r\n");
    }

    private static String textoPlanilha(String texto) {
        if (texto == null) return null;
        String inicio = texto.stripLeading();
        // Protege textos livres interpretáveis como fórmulas ao abrir em uma planilha.
        if (!inicio.isEmpty() && "=+-@".indexOf(inicio.charAt(0)) >= 0
                && !inicio.equals("-") && !inicio.matches("[+-][0-9]+([.,][0-9]+)?")) {
            return "'" + texto;
        }
        return texto;
    }
}
