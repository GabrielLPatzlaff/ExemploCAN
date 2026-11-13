package View;

import java.awt.Color;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Preferências locais de cor por ID, compartilhadas entre as telas. */
public final class CoresIds {
    private static final Properties cores=new Properties();
    private static final Path arquivo=pasta().resolve("cores-ids.properties");
    static {
        if(Files.exists(arquivo)) try(var r=Files.newBufferedReader(arquivo)){cores.load(r);}
        catch(IOException e){java.util.logging.Logger.getLogger(CoresIds.class.getName()).warning("Não foi possível ler as cores salvas.");}
    }
    private CoresIds(){}
    private static Path pasta(){String teste=System.getProperty("exemplocan.dados");if(teste!=null)return Path.of(teste);String local=System.getenv("LOCALAPPDATA");return Path.of(local==null?System.getProperty("user.home"):local,"Exemplo CAN");}
    public static synchronized Color obter(String id){
        String chave=id.strip().toUpperCase(Locale.ROOT);
        String salva=cores.getProperty(chave);
        if(salva!=null)try{return new Color(Integer.parseInt(salva));}catch(NumberFormatException ignorado){}
        int h=chave.hashCode();h^=h>>>16;h*=0x7feb352d;h^=h>>>15;h*=0x846ca68b;h^=h>>>16;
        return Color.getHSBColor((h&0xffff)/65536f,.24f+((h>>>16)&15)/100f,.24f+((h>>>20)&7)/100f);
    }
    public static synchronized void salvar(String id,Color cor)throws IOException{
        Properties novas=new Properties();novas.putAll(cores);novas.setProperty(id.strip().toUpperCase(Locale.ROOT),Integer.toString(cor.getRGB()&0xffffff));
        Files.createDirectories(arquivo.getParent());Path tmp=Files.createTempFile(arquivo.getParent(),"cores-", ".tmp");
        try{try(var w=Files.newBufferedWriter(tmp)){novas.store(w,"Cores dos IDs CAN");}Files.move(tmp,arquivo,StandardCopyOption.REPLACE_EXISTING);cores.clear();cores.putAll(novas);}finally{Files.deleteIfExists(tmp);}
    }
    public static Color texto(Color fundo){double brilho=.2126*fundo.getRed()+.7152*fundo.getGreen()+.0722*fundo.getBlue();return brilho>150?new Color(22,23,25):new Color(242,241,237);}
}
