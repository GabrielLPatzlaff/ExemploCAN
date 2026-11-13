package Principal;
import Dao.*;
import Model.*;
import java.util.*;

/** Exclui o log importado e desfaz seus vínculos em todas as válvulas. */
public final class ExclusaoArquivo {
    public void excluir(ArquivoCAN arquivo)throws Exception{
        ValvulaDAO dao=new ValvulaDAO();
        var disponiveis=new ArquivoDAO().listarConcluidos();
        Map<ValvulaCAN,Set<String>> anteriores=new LinkedHashMap<>();
        String hash=arquivo.getSha256().toUpperCase(Locale.ROOT);
        for(var v:dao.listar()){var vinculos=dao.arquivosVinculados(v.id());if(vinculos.contains(hash))anteriores.put(v,vinculos);}
        try{
            for(var entrada:anteriores.entrySet()){
                Set<String> restantes=new HashSet<>(entrada.getValue());restantes.remove(hash);
                new VinculosValvula().salvar(entrada.getKey().id(),disponiveis,restantes);
            }
            new ArquivoDAO().excluir(arquivo);
        }catch(Exception erro){
            for(var entrada:anteriores.entrySet())try{
                dao.salvar(entrada.getKey());
                dao.salvarVinculos(entrada.getKey().id(),Set.of(hash),Set.of(hash));
            }catch(Exception restaurar){erro.addSuppressed(restaurar);}
            throw erro;
        }
    }
}
