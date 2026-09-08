package com.zyn.trianglify.anim;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;

/**
 * GIF animado escrito com o codificador do próprio JDK — sem depender de nada
 * instalado na máquina.
 *
 * <p>O {@code javax.imageio} sabe gravar uma sequência GIF, mas não expõe atalho
 * para o tempo de cada quadro nem para a repetição infinita: os dois vivem em
 * extensões do formato que só dá para preencher montando a árvore de metadados na
 * mão, que é o que {@link #metadata} faz.
 *
 * <p>Cada quadro ganha a própria paleta de 256 cores, escolhida pelo JDK. Isso
 * segura bem os degradês do Trianglify, mas sem <em>dithering</em> — em gradientes
 * muito suaves aparecem faixas. Quando o {@code ffmpeg} está disponível,
 * {@link FfmpegEncoder} produz um GIF melhor.
 */
final class GifEncoder implements AnimationEncoder {

    /**
     * O GIF conta o tempo em centésimos de segundo, então a taxa pedida quase
     * nunca cabe exata. Em vez de arredondar o atraso de cada quadro e acumular o
     * erro ao longo do arquivo, o atraso sai da diferença entre dois instantes
     * arredondados — o total fecha com a duração pedida.
     */
    private final int fps;

    private final ImageWriter writer;
    private final ImageOutputStream output;
    private int index;

    GifEncoder(Path file, int fps) throws IOException {
        this.fps = fps;

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("gif");
        if (!writers.hasNext()) throw new IOException("nenhum codificador GIF disponível");
        this.writer = writers.next();

        this.output = ImageIO.createImageOutputStream(file.toFile());
        if (output == null) throw new IOException("não foi possível escrever em " + file);

        writer.setOutput(output);
        writer.prepareWriteSequence(null);
    }

    @Override
    public void addFrame(BufferedImage frame) throws IOException {
        ImageWriteParam param = writer.getDefaultWriteParam();
        IIOMetadata metadata = writer.getDefaultImageMetadata(
                ImageTypeSpecifier.createFromRenderedImage(frame), param);
        metadata(metadata, delayCentiseconds(index), index == 0);
        writer.writeToSequence(new IIOImage(frame, null, metadata), param);
        index++;
    }

    @Override
    public String describe() {
        return "GIF (codificador do JDK, paleta por quadro)";
    }

    @Override
    public void close() throws IOException {
        try {
            writer.endWriteSequence();
        } finally {
            try {
                output.close();
            } finally {
                writer.dispose();
            }
        }
    }

    /** Atraso do quadro {@code i}, sem deixar o arredondamento acumular. */
    private int delayCentiseconds(int i) {
        long end = Math.round((i + 1) * 100.0 / fps);
        long start = Math.round(i * 100.0 / fps);
        // Muitos leitores tratam atraso 0 ou 1 como "o mais rápido possível", que
        // na prática vira 10; 2 centésimos (50 fps) é o menor valor confiável.
        return (int) Math.max(2, end - start);
    }

    /**
     * Preenche as duas extensões que o GIF usa para animar: a de controle gráfico,
     * com o tempo do quadro, e a de aplicação NETSCAPE 2.0, que é o que faz o
     * arquivo repetir para sempre — esta última só no primeiro quadro.
     */
    private static void metadata(IIOMetadata metadata, int delay, boolean first)
            throws IOException {
        String format = metadata.getNativeMetadataFormatName();
        IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(format);

        IIOMetadataNode control = child(root, "GraphicControlExtension");
        control.setAttribute("disposalMethod", "none");
        control.setAttribute("userInputFlag", "FALSE");
        control.setAttribute("transparentColorFlag", "FALSE");
        control.setAttribute("transparentColorIndex", "0");
        control.setAttribute("delayTime", String.valueOf(delay));

        if (first) {
            IIOMetadataNode application = new IIOMetadataNode("ApplicationExtension");
            application.setAttribute("applicationID", "NETSCAPE");
            application.setAttribute("authenticationCode", "2.0");
            // sub-bloco 1 = "repetir"; os dois bytes seguintes são o número de
            // repetições em little-endian, e 0 significa infinitas.
            application.setUserObject(new byte[]{0x1, 0x0, 0x0});
            child(root, "ApplicationExtensions").appendChild(application);
        }

        metadata.setFromTree(format, root);
    }

    /** O filho de nome {@code name}, criando-o se ainda não existir. */
    private static IIOMetadataNode child(IIOMetadataNode parent, String name) {
        for (int i = 0; i < parent.getLength(); i++) {
            if (parent.item(i).getNodeName().equalsIgnoreCase(name)) {
                return (IIOMetadataNode) parent.item(i);
            }
        }
        IIOMetadataNode created = new IIOMetadataNode(name);
        parent.appendChild(created);
        return created;
    }
}
