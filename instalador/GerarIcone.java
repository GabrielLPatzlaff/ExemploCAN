import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import javax.imageio.ImageIO;

/** Ícone genérico desenhado pelo código; não utiliza imagens de terceiros. */
public class GerarIcone {
    private static BufferedImage desenhar(int tamanho){
        BufferedImage imagem=new BufferedImage(tamanho,tamanho,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=imagem.createGraphics();g.scale(tamanho/256d,tamanho/256d);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(37,79,115));g.fill(new RoundRectangle2D.Double(10,10,236,236,42,42));
        g.setColor(new Color(225,237,245));g.setStroke(new BasicStroke(9,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        g.drawLine(52,68,204,68);g.drawLine(52,190,204,190);
        for(int x:new int[]{62,128,194}){g.fillOval(x-8,60,16,16);g.fillOval(x-8,182,16,16);}
        g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,64));FontMetrics m=g.getFontMetrics();
        g.drawString("CAN",(256-m.stringWidth("CAN"))/2,150);g.dispose();return imagem;
    }
    public static void main(String[] args)throws Exception{
        Path raiz=Path.of(args[0]),recursos=raiz.resolve("src/recursos");Files.createDirectories(recursos);
        BufferedImage imagem=desenhar(256);ImageIO.write(imagem,"png",recursos.resolve("can-exemplo.png").toFile());
        ImageIO.write(desenhar(76),"png",recursos.resolve("can-exemplo-76.png").toFile());
        ByteArrayOutputStream png=new ByteArrayOutputStream();ImageIO.write(imagem,"png",png);byte[] dados=png.toByteArray();
        ByteBuffer ico=ByteBuffer.allocate(22+dados.length).order(ByteOrder.LITTLE_ENDIAN);
        ico.putShort((short)0).putShort((short)1).putShort((short)1);
        ico.put((byte)0).put((byte)0).put((byte)0).put((byte)0).putShort((short)1).putShort((short)32).putInt(dados.length).putInt(22).put(dados);
        Files.write(raiz.resolve("can-exemplo.ico"),ico.array());
    }
}
