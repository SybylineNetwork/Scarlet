package net.sybyline.scarlet.util;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.stream.IntStream;

import javax.imageio.ImageIO;

import com.madgag.gif.fmsware.GifDecoder;

import net.dv8tion.jda.api.utils.FileUpload;


public interface Gifs
{

    static String resolveTenorGifUrl(String url) throws IOException
    {
        try (HttpURLInputStream in = HttpURLInputStream.get(url))
        {
            return Hypertext.scrapeMetaNameContent(new InputStreamReader(in)).getOrDefault("twitter:image", url);
        }
    }

    static GifDecoder decode(String url) throws IOException
    {
        byte[] bytes;
        try (HttpURLInputStream in = HttpURLInputStream.get(url, $ -> $.addRequestProperty("Accept", "image/gif")))
        {
            bytes = MiscUtils.readAllBytes(in);
        }
        GifDecoder decoder = new GifDecoder();
        decoder.read(new ByteArrayInputStream(bytes));
        return decoder;
    }

    class VrcSpriteSheet
    {
        public static VrcSpriteSheet decode(String url) throws IOException
        {
            return decode(URLs.getNameFromPath(url), url);
        }
        public static VrcSpriteSheet decode(String name, String url) throws IOException
        {
            if (url.startsWith("https://tenor.com/view/"))
                url = resolveTenorGifUrl(url);
            return of(name, Gifs.decode(url));
        }
        public static VrcSpriteSheet of(String name, GifDecoder decoder) throws IOException
        {
            if (name.toLowerCase().endsWith(".gif"))
                name = name.substring(0, name.length() - 4);
            int count = decoder.getFrameCount(),
                width = decoder.getFrameSize().width,
                height = decoder.getFrameSize().height;
            BufferedImage out = new BufferedImage(1024, 1024, BufferedImage.TYPE_INT_ARGB);
            boolean scrunch = count > 64;
            int cells = count > 16 ? 8 : count > 4 ? 4 : count > 1 ? 2 : 1,
                stride = 1024 / cells,
                rsWidth = stride, rsHeight = stride,
                osX = 0, osY = 0;
            int totalTime = IntStream.range(0, count).map(decoder::getDelay).sum();
            if (width > height)
            {
                rsHeight = Math.round((((float)height) / ((float)width)) * ((float)stride));
                osY = (stride - rsHeight) / 2;
            }
            else if (width < height)
            {
                rsWidth = Math.round((((float)width) / ((float)height)) * ((float)stride));
                osX = (stride - rsWidth) / 2;
            }
            for (int outIndex = 0, end = scrunch ? 64 : count; outIndex < end; outIndex++)
            {
                int index = scrunch ? Math.round((((float)outIndex) / 63.0F) * ((float)(count - 1))) : outIndex;
                BufferedImage frame = decoder.getFrame(index);
                int outX = (outIndex % cells) * stride + osX,
                    outY = (outIndex / cells) * stride + osY;
                out.getGraphics().drawImage(frame.getScaledInstance(rsWidth, rsHeight, Image.SCALE_DEFAULT), outX, outY, null);
            }
            int renderedFrames = scrunch ? 64 : count;
            float fps = 1000.0F * ((float)renderedFrames) / ((float)totalTime);
            Rectangle renderedFrameBounds = new Rectangle(osX, osY, rsWidth, rsHeight);
            return new VrcSpriteSheet(name, out, count, renderedFrames, fps, stride, renderedFrameBounds);
        }
        public VrcSpriteSheet(String name, BufferedImage image, int originalFrames, int renderedFrames, float fps, int stride, Rectangle renderedFrameBounds)
        {
            this.name = name;
            this.image = image;
            this.originalFrames = originalFrames;
            this.renderedFrames = renderedFrames;
            this.fps = fps;
            this.stride = stride;
            this.renderedFrameBounds = renderedFrameBounds;
        }
        public final String name;
        public final BufferedImage image;
        public final int originalFrames;
        public final int renderedFrames;
        public final float fps;
        public final int stride;
        public final Rectangle renderedFrameBounds;
        public String getInfo()
        {
            return String.format("%s (%d/%d frames, %.1f fps)", this.name, this.renderedFrames, this.originalFrames, this.fps);
        }
        public FileUpload toDiscordAttachment() throws IOException
        {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            if (!ImageIO.write(this.image, "png", bytes))
                throw new IOException("No PNG encoder found :(");
            return FileUpload.fromData(bytes.toByteArray(), this.name+".png");
        }
    }

}
