package Principal;

import Dao.*;
import Model.*;
import java.sql.SQLException;
import java.util.*;

/** Retira IDs exclusivos dos logs removidos, preservando IDs compartilhados e manuais. */
public final class VinculosValvula {
    public ValvulaCAN salvar(long valvula, List<ArquivoCAN> arquivos, Set<String> escolhidos) throws SQLException {
        ValvulaDAO dao=new ValvulaDAO();
        Set<String> anteriores=dao.arquivosVinculados(valvula);
        Set<String> disponiveis=new HashSet<>();
        for(ArquivoCAN a:arquivos) disponiveis.add(a.getSha256().toUpperCase(Locale.ROOT));
        Set<String> selecionados=new HashSet<>(escolhidos);
        selecionados.retainAll(disponiveis);
        Set<String> removidos=new HashSet<>(anteriores);
        removidos.retainAll(disponiveis); removidos.removeAll(selecionados);
        Set<String> indisponiveis=new HashSet<>(anteriores); indisponiveis.removeAll(disponiveis);
        Set<String> idsRemover=new HashSet<>(), idsManter=new HashSet<>();
        // Um vínculo de outra base pode não estar disponível nesta versão. Nesse caso,
        // não há informação suficiente para apagar um ID com segurança.
        if(!removidos.isEmpty() && indisponiveis.isEmpty()) {
            MensagemDAO mensagens=new MensagemDAO();
            for(ArquivoCAN a:arquivos) {
                String hash=a.getSha256().toUpperCase(Locale.ROOT);
                if(!removidos.contains(hash) && !selecionados.contains(hash)) continue;
                for(var id:mensagens.listarIds(a.getId()))
                    (removidos.contains(hash)?idsRemover:idsManter).add(id.id().toUpperCase(Locale.ROOT));
            }
            idsRemover.removeAll(idsManter);
        }
        dao.salvarVinculos(valvula,disponiveis,selecionados,idsRemover);
        return dao.listar().stream().filter(v->v.id()==valvula).findFirst()
                .orElseThrow(()->new SQLException("A válvula não está mais cadastrada."));
    }
}
