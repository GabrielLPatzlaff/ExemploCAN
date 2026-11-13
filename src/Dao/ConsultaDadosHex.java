package Dao;

import Model.*;
import Util.FiltroHex;
import java.sql.*;
import java.time.LocalTime;
import java.util.*;

/** Filtros temporários de visualização; não executa DELETE nem UPDATE. */
public final class ConsultaDadosHex {
    public record DadoHex(String hex,long quantidade){}
    private ConsultaDadosHex(){}
    private static String selecoes(List<SelecaoFiltro> selecoes){
        if(selecoes.isEmpty())throw new IllegalArgumentException("Selecione pelo menos um arquivo/ID.");
        StringJoiner json=new StringJoiner(",","[","]");
        for(SelecaoFiltro s:selecoes){
            String id=s.getIdCan();if(s.getArquivoId()<1||(id!=null&&!id.matches("[0-9A-Za-z]{1,8}")))throw new IllegalArgumentException("Seleção inválida.");
            json.add("{\"arquivo\":"+s.getArquivoId()+",\"id\":"+(id==null?"null":"\""+id.toUpperCase(Locale.ROOT)+"\"")+"}");
        }
        return json.toString();
    }
    private static boolean sqlite(Connection c)throws SQLException{return c.getMetaData().getDatabaseProductName().equalsIgnoreCase("SQLite");}
    private static String cte(boolean sqlite){
        return sqlite ? "WITH selecao AS (SELECT json_extract(value,'$.arquivo') AS arquivo,json_extract(value,'$.id') AS id_can FROM json_each(?)) "
            : "WITH selecao AS (SELECT arquivo,id_can FROM JSON_TABLE(?, '$[*]' COLUMNS(arquivo BIGINT PATH '$.arquivo',id_can VARCHAR(8) PATH '$.id' NULL ON EMPTY NULL ON ERROR)) jt) ";
    }
    private static String onde(boolean primeiroByte){
        String sql=" WHERE m.arquivo_id IN (SELECT arquivo FROM selecao) AND (m.arquivo_id IN (SELECT arquivo FROM selecao WHERE id_can IS NULL) OR (m.arquivo_id,m.id_can) IN (SELECT arquivo,id_can FROM selecao WHERE id_can IS NOT NULL)) ";
        if(primeiroByte)sql+=" AND UPPER(SUBSTR(LTRIM(m.dados_hex),1,2)) IN ("+FiltroHex.listaSQL()+") ";
        return sql;
    }
    public static Set<String> conferirOcultos(Set<String> valores){
        Set<String> normalizados=new LinkedHashSet<>();
        for(String valor:valores){
            if(valor==null||!valor.matches("(?:[0-9A-F]{2}(?: [0-9A-F]{2}){0,7})?"))throw new IllegalArgumentException("Dados HEX ocultos inválidos.");
            normalizados.add(valor);
        }
        return Set.copyOf(normalizados);
    }
    private static String jsonOcultos(Set<String> valores){StringJoiner j=new StringJoiner(",","[","]");for(String s:conferirOcultos(valores))j.add("\""+s+"\"");return j.toString();}
    public static String pesquisa(String valor){
        String p=valor.strip().toUpperCase(Locale.ROOT).replace("0X","").replaceAll("[\\s:\\-\\[\\]{},]","");
        if(!p.matches("[0-9A-F]{0,16}"))throw new IllegalArgumentException("Pesquise usando somente caracteres HEX de 0 a 9 e A a F (até 8 bytes).");
        return p;
    }
    public static List<DadoHex> listar(List<SelecaoFiltro> selecoes,boolean primeiroByte,String pesquisa,String apos,int limite)throws SQLException{
        if(limite<1||limite>501)throw new IllegalArgumentException("Limite de dados inválido.");
        String json=selecoes(selecoes),termo=pesquisa(pesquisa);
        try(Connection c=Conexao.conectar()){
            String sql=cte(sqlite(c))+" SELECT m.dados_hex,COUNT(*) AS quantidade FROM mensagens_can m "+onde(primeiroByte);
            if(apos!=null)sql+=" AND m.dados_hex > ? ";
            sql+=" AND REPLACE(UPPER(m.dados_hex),' ','') LIKE ? GROUP BY m.dados_hex ORDER BY m.dados_hex LIMIT ?";
            try(PreparedStatement p=c.prepareStatement(sql)){
                p.setQueryTimeout(60);int n=1;p.setString(n++,json);if(apos!=null)p.setString(n++,apos);p.setString(n++,"%"+termo+"%");p.setInt(n,limite);
                List<DadoHex> r=new ArrayList<>();try(ResultSet linhas=p.executeQuery()){while(linhas.next())r.add(new DadoHex(linhas.getString(1),linhas.getLong(2)));}return r;
            }
        }
    }
    public static List<MensagemCAN> filtrar(List<SelecaoFiltro> selecoes,int limite,long ultimo,boolean primeiroByte,Set<String> ocultos)throws SQLException{
        if(limite<1||limite>10000||ultimo<0)throw new IllegalArgumentException("Página inválida.");
        String json=selecoes(selecoes),ocultosJson=jsonOcultos(ocultos);
        try(Connection c=Conexao.conectar()){
            boolean sqlite=sqlite(c);
            String sql=cte(sqlite)+" SELECT m.* FROM mensagens_can m "+onde(primeiroByte)+" AND m.id > ? ";
            sql+=sqlite?" AND m.dados_hex NOT IN (SELECT value FROM json_each(?)) ":" AND NOT EXISTS (SELECT 1 FROM JSON_TABLE(?, '$[*]' COLUMNS(hex VARCHAR(50) PATH '$')) h WHERE h.hex=m.dados_hex) ";
            sql+=" ORDER BY m.id LIMIT ?";
            try(PreparedStatement p=c.prepareStatement(sql)){
                p.setQueryTimeout(60);p.setString(1,json);p.setLong(2,ultimo);p.setString(3,ocultosJson);p.setInt(4,limite);
                List<MensagemCAN> resultado=new ArrayList<>();
                try(ResultSet r=p.executeQuery()){while(r.next()){
                    int dlc=r.getInt("dlc");Integer tamanho=r.wasNull()?null:dlc;String hora=r.getString("horario");
                    resultado.add(new MensagemCAN(r.getLong("id"),r.getInt("numero_registro"),hora==null?null:LocalTime.parse(hora),r.getString("direcao"),r.getString("id_can"),tamanho,r.getString("dados_hex"),r.getString("ascii_dados"),r.getString("delta_dt"),r.getLong("arquivo_id")));
                }}return resultado;
            }
        }
    }
}
