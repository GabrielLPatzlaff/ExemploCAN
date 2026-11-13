package Util;

import java.util.*;

/** Regras compartilhadas pela consulta, exportação e textos da interface. */
public final class FiltroHex {
    public static final String DESCRICAO="00 a 07, 10, 20 a 2F ou 30";
    public static final Set<String> PRIMEIROS_BYTES;
    static {
        Set<String> bytes=new LinkedHashSet<>(List.of("00","01","02","03","04","05","06","07","10","30"));
        for(int b=0x20;b<=0x2f;b++)bytes.add(String.format(Locale.ROOT,"%02X",b));
        PRIMEIROS_BYTES=Collections.unmodifiableSet(bytes);
    }
    private FiltroHex(){}
    public static boolean permite(String dados) {
        if(dados==null)return false;
        String hex=dados.stripLeading().toUpperCase(Locale.ROOT);
        return hex.length()>=2&&PRIMEIROS_BYTES.contains(hex.substring(0,2));
    }
    public static String listaSQL() {
        StringJoiner lista=new StringJoiner(",");for(String b:PRIMEIROS_BYTES)lista.add("'"+b+"'");return lista.toString();
    }
}
