package View;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.text.JTextComponent;

/** Preferências do usuário; somente as janelas deste processo são atualizadas. */
public final class TemaAplicativo {
    private static Color fundo, letras;
    private static boolean instalado,aplicando;
    private static final Color FUNDO_PADRAO=new Color(22,23,25),TEXTO_PADRAO=new Color(242,241,237);
    private static final Path ARQUIVO=pasta().resolve("aparencia.properties");
    static {
        if(Files.isRegularFile(ARQUIVO))try(var r=Files.newBufferedReader(ARQUIVO)) {
            Properties p=new Properties();p.load(r);fundo=lerCor(p,"fundo");letras=lerCor(p,"letras");
        }catch(Exception e){java.util.logging.Logger.getLogger(TemaAplicativo.class.getName()).warning("Não foi possível ler as preferências de aparência.");}
    }
    private TemaAplicativo(){}
    private static Path pasta(){String teste=System.getProperty("exemplocan.dados");if(teste!=null)return Path.of(teste);String local=System.getenv("LOCALAPPDATA");return Path.of(local==null?System.getProperty("user.home"):local,"Exemplo CAN");}
    private static Color lerCor(Properties p,String nome){String valor=p.getProperty(nome);return valor==null?null:new Color(Integer.parseInt(valor));}
    public static boolean fundoPersonalizado(){return fundo!=null;}
    public static boolean textoPersonalizado(){return letras!=null;}
    public static Color fundo(Color padrao){return fundo==null?padrao:fundo;}
    public static Color texto(Color padrao){return letras==null?padrao:letras;}
    public static Color textoDesabilitado(){return misturar(fundo(FUNDO_PADRAO),texto(TEXTO_PADRAO),.60);}
    public static Color corLinha(Color corId){
        if(fundo==null)return corId;
        return misturar(fundo,corId,.20);
    }
    private static Color misturar(Color a,Color b,double peso){return new Color((int)(a.getRed()*(1-peso)+b.getRed()*peso),(int)(a.getGreen()*(1-peso)+b.getGreen()*peso),(int)(a.getBlue()*(1-peso)+b.getBlue()*peso));}
    public static void instalar(){
        if(instalado)return;instalado=true;configurarDefaults();
        Toolkit.getDefaultToolkit().addAWTEventListener(e->{
            if(e instanceof WindowEvent w&&w.getID()==WindowEvent.WINDOW_OPENED)aplicar(w.getWindow());
            else if(e instanceof ContainerEvent c&&c.getID()==ContainerEvent.COMPONENT_ADDED&&!aplicando) {
                Component novo=c.getChild();
                if(SwingUtilities.getWindowAncestor(novo)!=null)SwingUtilities.invokeLater(()->aplicar(novo));
            }
        },AWTEvent.WINDOW_EVENT_MASK|AWTEvent.CONTAINER_EVENT_MASK);
    }
    private static void configurarDefaults(){
        Color bg=fundo(FUNDO_PADRAO),fg=texto(TEXTO_PADRAO);
        for(String prefixo:new String[]{"Panel","OptionPane","Label","Button","ToggleButton","CheckBox","RadioButton","ComboBox","List","Table","TableHeader","TextField","TextArea","TextPane","EditorPane","FormattedTextField","Spinner","Viewport","ScrollPane","Menu","MenuItem","PopupMenu","TabbedPane","ToolTip"}) {
            UIManager.put(prefixo+".background",bg);UIManager.put(prefixo+".foreground",fg);
            UIManager.put(prefixo+".selectionBackground",misturar(bg,fg,.20));UIManager.put(prefixo+".selectionForeground",fg);
            UIManager.put(prefixo+".disabledForeground",misturar(bg,fg,.60));
        }
        UIManager.put("OptionPane.messageForeground",fg);UIManager.put("TextField.caretForeground",fg);
        UIManager.put("ComboBox.disabledForeground",misturar(bg,fg,.60));
        UIManager.put("Button.disabledText",misturar(bg,fg,.60));
        UIManager.put("CheckBox.disabledText",misturar(bg,fg,.60));
    }
    public static void escolher(Component pai,boolean alterarFundo){
        Object[] opcoes={"Escolher cor","Restaurar padrão","Cancelar"};
        int opcao=JOptionPane.showOptionDialog(pai,"A alteração vale para todas as telas e será salva neste computador.",
            alterarFundo?"Cor de fundo":"Cor das letras",JOptionPane.DEFAULT_OPTION,JOptionPane.PLAIN_MESSAGE,null,opcoes,opcoes[0]);
        if(opcao<0||opcao==2)return;
        Color nova=null;
        if(opcao==0){nova=JColorChooser.showDialog(pai,alterarFundo?"Escolher cor de fundo":"Escolher cor das letras",alterarFundo?fundo(FUNDO_PADRAO):texto(TEXTO_PADRAO));if(nova==null)return;}
        try{salvar(alterarFundo?nova:fundo,alterarFundo?letras:nova);}
        catch(IOException e){JOptionPane.showMessageDialog(pai,"Não foi possível salvar a aparência: "+e.getMessage(),"Aparência",JOptionPane.ERROR_MESSAGE);}
    }
    public static void salvar(Color novoFundo,Color novasLetras)throws IOException {
        Properties p=new Properties();if(novoFundo!=null)p.setProperty("fundo",Integer.toString(novoFundo.getRGB()&0xffffff));
        if(novasLetras!=null)p.setProperty("letras",Integer.toString(novasLetras.getRGB()&0xffffff));
        Files.createDirectories(ARQUIVO.getParent());Path temp=Files.createTempFile(ARQUIVO.getParent(),"aparencia-",".tmp");
        try{try(var w=Files.newBufferedWriter(temp)){p.store(w,"Aparência Exemplo CAN");}Files.move(temp,ARQUIVO,StandardCopyOption.REPLACE_EXISTING);}
        finally{Files.deleteIfExists(temp);}
        fundo=novoFundo;letras=novasLetras;configurarDefaults();
        Runnable atualizar=()->{for(Window janela:Window.getWindows())if(janela.isDisplayable())aplicar(janela);};
        if(SwingUtilities.isEventDispatchThread())atualizar.run();else SwingUtilities.invokeLater(atualizar);
    }
    public static void aplicar(Component c){
        if(c instanceof CellRendererPane||SwingUtilities.getAncestorOfClass(CellRendererPane.class,c)!=null
                ||SwingUtilities.getAncestorOfClass(JColorChooser.class,c)!=null)return;
        boolean anterior=aplicando;aplicando=true;
        try{aplicarRecursivo(c);c.repaint();}finally{aplicando=anterior;}
    }
    private static void aplicarRecursivo(Component c){
        // Os quadrados de seleção da paleta precisam conservar suas próprias cores.
        if(c instanceof JColorChooser)return;
        if(c instanceof JComponent j){
            if(j.getClientProperty("tema.fundoOriginal")==null&&j.getBackground()!=null)j.putClientProperty("tema.fundoOriginal",j.getBackground().equals(fundo)?FUNDO_PADRAO:j.getBackground());
            if(j.getClientProperty("tema.letrasOriginal")==null&&j.getForeground()!=null)j.putClientProperty("tema.letrasOriginal",j.getForeground().equals(letras)?TEXTO_PADRAO:j.getForeground());
            Color bg=fundo!=null?(j instanceof JButton?misturar(fundo,letras==null?CoresIds.texto(fundo):letras,.08):fundo):(Color)j.getClientProperty("tema.fundoOriginal");
            Color fg=letras!=null?letras:(Color)j.getClientProperty("tema.letrasOriginal");
            if(bg!=null)j.setBackground(bg);if(fg!=null)j.setForeground(fg);
            boolean personalizado=fundo!=null||letras!=null;
            Color selecao=misturar(fundo(FUNDO_PADRAO),texto(TEXTO_PADRAO),.20);
            if(j instanceof JTextComponent t){t.setCaretColor(fg);t.setSelectionColor(corSelecao(j,"selecaoOriginal",t.getSelectionColor(),personalizado?selecao:null));t.setSelectedTextColor(corSelecao(j,"textoSelecaoOriginal",t.getSelectedTextColor(),personalizado?texto(TEXTO_PADRAO):null));}
            if(j instanceof JTable t){
                t.setSelectionBackground(corSelecao(j,"selecaoOriginal",t.getSelectionBackground(),personalizado?selecao:null));
                t.setSelectionForeground(corSelecao(j,"textoSelecaoOriginal",t.getSelectionForeground(),personalizado?texto(TEXTO_PADRAO):null));
                t.setGridColor(corSelecao(j,"gradeOriginal",t.getGridColor(),personalizado?misturar(fundo(FUNDO_PADRAO),texto(TEXTO_PADRAO),.25):null));
                aplicarRecursivo(t.getTableHeader());
            }
            if(j instanceof JList<?> l){l.setSelectionBackground(corSelecao(j,"selecaoOriginal",l.getSelectionBackground(),personalizado?selecao:null));l.setSelectionForeground(corSelecao(j,"textoSelecaoOriginal",l.getSelectionForeground(),personalizado?texto(TEXTO_PADRAO):null));}
            if(j.getBorder() instanceof TitledBorder t)t.setTitleColor(texto(TEXTO_PADRAO));
        }
        if(c instanceof Container grupo)for(Component filho:grupo.getComponents())aplicarRecursivo(filho);
    }
    private static Color corSelecao(JComponent componente,String chave,Color original,Color nova){
        if(componente.getClientProperty("tema."+chave)==null)componente.putClientProperty("tema."+chave,original);
        return nova!=null?nova:(Color)componente.getClientProperty("tema."+chave);
    }
}
