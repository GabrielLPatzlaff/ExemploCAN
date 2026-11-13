package Util;

import Model.MensagemCAN;
import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.text.Normalizer;
import java.time.LocalTime;
import java.util.*;
import java.util.regex.*;

/** Leitura sequencial; nunca carrega o TXT completo na memória. */
public final class LeitorArquivoCAN {
    @FunctionalInterface public interface EscolherColunas {
        Map<String,Integer> escolher(String[] cabecalho, Map<String,Integer> reconhecidas) throws Exception;
    }
    @FunctionalInterface public interface Consumidor { void receber(MensagemCAN mensagem) throws Exception; }
    public record Totais(long mensagens, long marcadores) {}
    private final EscolherColunas escolha;
    public LeitorArquivoCAN() { this(null); }
    public LeitorArquivoCAN(EscolherColunas escolha) { this.escolha = escolha; }
    // O ASCII dos logs antigos pode conter ponto e vírgula: preservar o campo inteiro.
    private static final Pattern CLASSICO = Pattern.compile(
        "^\\s*(\\d+)\\s*;\\s*(\\d{1,2}:\\d{2}:\\d{2}(?:[.,]\\d{1,9})?)\\s*;([^;]*);([^;]*);([^;]*);([^;]*);(.*);([^;]*)$");
    private static final int MAX_LINHA = 1_048_576;
    private static final Map<String,Integer> MAPA_CLASSICO=Map.of("registro",0,"hora",1,"direcao",2,"id",3,"dlc",4,"hex",5,"ascii",6,"delta",7);
    private static final Pattern SEPARADOR_VISUAL=Pattern.compile("[-=+_|\\s]+");
    private static final Pattern ID_VALIDO=Pattern.compile("[0-9A-Z]{1,8}");
    private static final Pattern HEX_COMPACTO=Pattern.compile("(?:0X)?[0-9A-F]+");
    private static final Pattern BYTE_HEX=Pattern.compile("(?:0X)?[0-9A-F]{2}");
    private static final Pattern SEPARADOR_BYTES=Pattern.compile("[\\s,:\\-]+");

    public Totais ler(Path arquivo, Consumidor consumidor) throws Exception {
        try (BufferedReader leitor = Files.newBufferedReader(arquivo, detectarCodificacao(arquivo))) {
            String linha; long numero=0, mensagens=0, marcadores=0;
            Map<String,Integer> colunas=null; String[] cabecalho=null; char separador=';';int totalColunas=0;
            while ((linha=leitor.readLine())!=null) {
                numero++;
                if(Thread.currentThread().isInterrupted()) throw new IOException("Leitura interrompida.");
                linha=limpar(linha, false);
                if(linha.isBlank() || SEPARADOR_VISUAL.matcher(linha).matches()) continue;
                long inicio=numero;
                try {
                    boolean comentario=linha.startsWith("#") || linha.startsWith("//");
                    String candidata=comentario ? linha.replaceFirst("^(?:#|//)\\s*", "") : linha;
                    if(comentario && !cabecalhoCompleto(candidata)) continue;
                    if(comentario && !linha.matches("^#\\s*;.*")) linha=candidata;
                    if(colunas==null) {
                        Matcher antigo=CLASSICO.matcher(linha);
                        if(antigo.matches()) {
                            String[] valores=new String[8];for(int i=0;i<8;i++)valores[i]=antigo.group(i+1);
                            if(marcador(valores, mapaClassico())) { marcadores++; continue; }
                            consumidor.receber(mensagem(valores,mapaClassico(),numero));mensagens++;continue;
                        }
                        separador=detectarSeparador(linha);
                    }
                    Matcher legado=CLASSICO.matcher(linha);
                    if(colunas!=null && colunas.equals(mapaClassico()) && legado.matches()) {
                        String[] valores=new String[8];for(int i=0;i<8;i++)valores[i]=legado.group(i+1);
                        if(marcador(valores,colunas)){marcadores++;continue;}
                        consumidor.receber(mensagem(valores,colunas,numero));mensagens++;continue;
                    }
                    // Aspas permitem separadores e quebras de linha em campos auxiliares.
                    while(aspasAbertas(linha,separador)) {
                        String proxima=leitor.readLine();
                        if(proxima==null) throw new IllegalArgumentException("campo entre aspas não foi fechado");
                        numero++; linha += "\n" + proxima;
                        if(linha.length()>MAX_LINHA) throw new IllegalArgumentException("linha excede 1 MB");
                    }
                    if(linha.length()>MAX_LINHA) throw new IllegalArgumentException("linha excede 1 MB");
                    String[] valores=dividir(linha,separador);
                    if(Arrays.stream(valores).allMatch(String::isBlank))continue;
                    if(colunas==null) {
                        try {colunas=reconhecer(valores);}
                        catch(IllegalArgumentException duplicada) {
                            if(escolha==null)throw duplicada;
                            colunas=new HashMap<>(); // Cabeçalho ambíguo exige confirmação do usuário.
                        }
                        totalColunas=valores.length;
                        cabecalho=valores.clone();
                        if(!colunas.containsKey("id") || !colunas.containsKey("hex")) {
                            if(escolha==null) throw new IllegalArgumentException("Não foi possível identificar as colunas ID e dados HEX. Use a escolha de colunas.");
                            colunas=escolha.escolher(valores,new HashMap<>(colunas));
                            if(colunas==null) throw new IllegalArgumentException("Escolha das colunas cancelada.");
                        }
                        conferirMapa(colunas,valores.length);
                        if(!colunas.containsKey("primeira_linha_dados")) continue;
                        cabecalho=null;
                    } else if(cabecalhoIgual(valores,cabecalho)) {
                        continue;
                    }
                    for(int i=totalColunas;i<valores.length;i++)
                        if(!valores[i].isBlank())throw new IllegalArgumentException("mais colunas que no cabeçalho: confira o separador e use aspas em campos que contêm esse separador");
                    if(marcador(valores,colunas)) { marcadores++;continue; }
                    consumidor.receber(mensagem(valores,colunas,inicio));mensagens++;
                } catch(Exception e) {
                    throw new IOException("Linha " + inicio + " de " + arquivo.getFileName() + ": " + e.getMessage(),e);
                }
            }
            if(mensagens==0) throw new IOException("O arquivo não contém mensagens CAN válidas.");
            return new Totais(mensagens,marcadores);
        }
    }
    private static Map<String,Integer> mapaClassico() {
        return MAPA_CLASSICO;
    }
    private static String limpar(String s) { return limpar(s,true); }
    private static String limpar(String s,boolean campo) {
        s=s.replace("\uFEFF", "").replace("ï»¿", "").replace('\u00A0',' ').replace('\u202F',' ');
        if(campo)return s.strip();
        int inicio=0,fim=s.length();while(inicio<fim&&s.charAt(inicio)==' ')inicio++;
        while(fim>inicio&&s.charAt(fim-1)==' ')fim--;
        return s.substring(inicio,fim);
    }
    private static String chave(String s) {
        return Normalizer.normalize(limpar(s).toLowerCase(Locale.ROOT),Normalizer.Form.NFD)
            .replaceAll("\\p{M}","").replaceAll("[^a-z0-9]","");
    }
    private static String nome(String texto) {
        if(texto.strip().equals("#"))return "registro";
        return switch(chave(texto)) {
            case "id","idcan","canid","identifier","identificador","identificadorcan","arbitrationid","messageid","msgid","frameid" -> "id";
            case "data","dados","dadoshex","datahex","hexdata","hex","payload","bytes","databytes","conteudo","conteudohex","dadoscan","messagedata" -> "hex";
            case "timestamp","horario","hora","time","times" -> "hora";
            case "txrx","rxtx","direcao","direction","dir" -> "direcao";
            case "dlc","length","len","datalength","comprimento","tamanho" -> "dlc";
            case "numeroregistro","registro","numero","record","index","n" -> "registro";
            case "ascii","asciidados","dadosascii" -> "ascii";
            case "delta","deltadt","deltatime" -> "delta";
            default -> null;
        };
    }
    private static Map<String,Integer> reconhecer(String[] campos) {
        Map<String,Integer> mapa=new HashMap<>();
        for(int i=0;i<campos.length;i++) {
            String n=nome(campos[i]);
            if(n!=null && mapa.putIfAbsent(n,i)!=null) throw new IllegalArgumentException("Cabeçalho com coluna repetida: "+campos[i]);
        }
        return mapa;
    }
    private static void conferirMapa(Map<String,Integer> mapa,int tamanho) {
        if(!mapa.containsKey("id") || !mapa.containsKey("hex")) throw new IllegalArgumentException("ID e dados HEX são obrigatórios");
        Set<Integer> usados=new HashSet<>();
        for(var e:mapa.entrySet()) {
            if(e.getKey().equals("primeira_linha_dados")) continue;
            Integer i=e.getValue();
            if(i==null || i<0 || i>=tamanho || !usados.add(i)) throw new IllegalArgumentException("Selecione colunas diferentes e existentes para cada campo.");
        }
    }
    private static boolean cabecalhoCompleto(String linha) {
        try {var mapa=reconhecer(dividir(linha,detectarSeparador(linha)));return mapa.containsKey("id")&&mapa.containsKey("hex");}
        catch(IllegalArgumentException e){return false;}
    }
    private static boolean cabecalhoIgual(String[] valores,String[] anterior) {
        if(anterior==null || valores.length!=anterior.length)return false;
        for(int i=0;i<valores.length;i++)if(!chave(valores[i]).equals(chave(anterior[i])))return false;
        return true;
    }
    private static char detectarSeparador(String linha) {
        char melhor=';';int pontos=-1;
        for(char c:new char[]{';','\t','|',','}) {
            try {
                String[] campos=dividir(linha,c);
                if(campos.length<2)continue;
                int reconhecidas=0;for(String s:campos)if(nome(s)!=null)reconhecidas++;
                int vazias=0;for(String s:campos)if(s.isBlank())vazias++;
                int valor=reconhecidas*1000+Math.min(campos.length-vazias,99)-vazias*10;
                if(valor>pontos){pontos=valor;melhor=c;}
            } catch(IllegalArgumentException e) { /* Outra opção pode corresponder ao formato. */ }
        }
        if(pontos<0)return linha.matches(".*\\S {2,}\\S.*")?'\u0001':'\u0002';
        return melhor;
    }
    // U+0001 representa colunas alinhadas; U+0002, ID e HEX separados por espaço.
    private static String[] dividir(String linha,char sep) {
        if(sep=='\u0001')return linha.strip().split(" {2,}",-1);
        if(sep=='\u0002')return linha.strip().split("\\s+",2);
        List<String> campos=new ArrayList<>();StringBuilder campo=new StringBuilder();
        boolean aspas=false,fechado=false;
        for(int i=0;i<linha.length();i++) {
            char c=linha.charAt(i);
            if(c=='"') {
                if(aspas && i+1<linha.length()&&linha.charAt(i+1)=='"'){campo.append('"');i++;}
                else if(aspas){aspas=false;fechado=true;}
                else if(campo.toString().isBlank()&&!fechado){campo.setLength(0);aspas=true;}
                else throw new IllegalArgumentException("aspas fora do início de uma coluna");
            } else if(c==sep&&!aspas){campos.add(limpar(campo.toString()));campo.setLength(0);fechado=false;}
            else {if(fechado&&!Character.isWhitespace(c))throw new IllegalArgumentException("texto após o fechamento das aspas");campo.append(c);}
        }
        if(aspas)throw new IllegalArgumentException("campo entre aspas não foi fechado");
        campos.add(limpar(campo.toString()));return campos.toArray(String[]::new);
    }
    private static boolean aspasAbertas(String linha,char sep) {
        if(sep=='\u0001'||sep=='\u0002')return false;
        boolean aberta=false,inicio=true;
        for(int i=0;i<linha.length();i++) {
            char c=linha.charAt(i);
            if(c=='"'&&(aberta||inicio)) {
                if(aberta&&i+1<linha.length()&&linha.charAt(i+1)=='"'){i++;continue;}
                aberta=!aberta;inicio=false;
            } else if(c==sep&&!aberta)inicio=true;
            else if(!Character.isWhitespace(c))inicio=false;
        }
        return aberta;
    }
    private static String campo(String[] valores,Map<String,Integer> mapa,String nome) {
        Integer i=mapa.get(nome);
        if(i==null)return "";
        if(i>=valores.length) {
            if(nome.equals("id")||nome.equals("hex"))throw new IllegalArgumentException("coluna ausente: "+nome);
            return "";
        }
        return limpar(valores[i]);
    }
    private static boolean ausente(String s) {return s.isEmpty()||s.equals("-")||s.equals("--")||s.equalsIgnoreCase("null")||s.equalsIgnoreCase("n/a");}
    private static boolean marcador(String[] v,Map<String,Integer> m) {
        return campo(v,m,"direcao").equals("--") && campo(v,m,"id").equals("-") && campo(v,m,"dlc").equals("-");
    }
    private static MensagemCAN mensagem(String[] v,Map<String,Integer> m,long linha) {
        String id=campo(v,m,"id").toUpperCase(Locale.ROOT);
        if(!ID_VALIDO.matcher(id).matches())throw new IllegalArgumentException("ID inválido: use de 1 a 8 letras/números, sem espaços internos");
        String hex=normalizarHex(campo(v,m,"hex"));
        String d=campo(v,m,"dlc");Integer dlc=ausente(d)?null:Integer.valueOf(d);
        if(hex.isEmpty()&&!Integer.valueOf(0).equals(dlc))throw new IllegalArgumentException("dados HEX são obrigatórios");
        if(dlc!=null&&(dlc<0||dlc>8||hex.replace(" ","").length()!=dlc*2))throw new IllegalArgumentException("dados HEX incompatíveis com o DLC");
        String direcao=campo(v,m,"direcao").toUpperCase(Locale.ROOT);
        if(ausente(direcao))direcao=null;
        else if(!direcao.equals("RX")&&!direcao.equals("TX"))throw new IllegalArgumentException("direção inválida; escolha Não informado se essa coluna não é RX/TX");
        String hora=campo(v,m,"hora"),registro=campo(v,m,"registro");
        LocalTime horario=null;
        if(!ausente(hora)) {
            hora=hora.replace(',','.');if(hora.matches("\\d:\\d{2}:.*"))hora="0"+hora;
            horario=LocalTime.parse(hora);
            if(horario.getNano()%1_000_000!=0)throw new IllegalArgumentException("horário com precisão superior a milissegundos");
        }
        long n=ausente(registro)?linha-1:Long.parseLong(registro);
        if(n<0||n>Integer.MAX_VALUE)throw new IllegalArgumentException("número de registro fora do limite");
        String ascii=campo(v,m,"ascii"),delta=campo(v,m,"delta");
        if(ascii.length()>100||delta.length()>30)throw new IllegalArgumentException("campo auxiliar excede o tamanho aceito; ajuste a escolha das colunas");
        return new MensagemCAN(0,(int)n,horario,direcao,id,dlc,hex,ascii,delta,0);
    }
    private static String normalizarHex(String texto) {
        String s=texto.toUpperCase(Locale.ROOT).strip();
        if((s.startsWith("[")&&s.endsWith("]"))||(s.startsWith("{")&&s.endsWith("}")))s=s.substring(1,s.length()-1).strip();
        if(s.isEmpty())return "";
        String compacto;
        if(HEX_COMPACTO.matcher(s).matches())compacto=s.startsWith("0X")?s.substring(2):s;
        else {
            String[] bytes=SEPARADOR_BYTES.split(s,-1);StringBuilder b=new StringBuilder();
            for(String valor:bytes){if(!BYTE_HEX.matcher(valor).matches())throw new IllegalArgumentException("dados HEX inválidos: cada byte deve ter dois caracteres de 0 a F");b.append(valor.startsWith("0X")?valor.substring(2):valor);}
            compacto=b.toString();
        }
        if(compacto.length()%2!=0||compacto.length()>16)throw new IllegalArgumentException("dados HEX inválidos: esperado de 0 a 8 bytes CAN clássico");
        StringBuilder resultado=new StringBuilder();
        for(int i=0;i<compacto.length();i+=2){if(i>0)resultado.append(' ');resultado.append(compacto,i,i+2);}
        return resultado.toString();
    }
    private static Charset detectarCodificacao(Path arquivo) throws IOException {
        try(InputStream entrada=Files.newInputStream(arquivo)) {
            int a=entrada.read(),b=entrada.read();
            if(a==255&&b==254)return StandardCharsets.UTF_16LE;
            if(a==254&&b==255)return StandardCharsets.UTF_16BE;
        }
        try(Reader entrada=new InputStreamReader(Files.newInputStream(arquivo),StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT))) {
            char[] bloco=new char[16384];while(entrada.read(bloco)!=-1)
                if(Thread.currentThread().isInterrupted())throw new IOException("Leitura interrompida.");
            return StandardCharsets.UTF_8;
        } catch(CharacterCodingException erro){return Charset.forName("windows-1252");}
    }
}
