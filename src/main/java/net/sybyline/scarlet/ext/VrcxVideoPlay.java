package net.sybyline.scarlet.ext;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sybyline.scarlet.Scarlet;

/**
 * <a href="https://github.com/vrcx-team/VRCX/blob/2a950abe1cd7f7fb3bba3fc742c2ef76a91b9003/src/stores/gameLog.js#L828">VRCX</a>
 */
public interface VrcxVideoPlay
{

    Pattern PyPyDance = Pattern.compile("\\\"(.+?)\\\",([\\d.]+),([\\d.]+),\\\"(.*)\\\""),
            VRDancing = Pattern.compile("\\\"(.+?)\\\",([\\d.]+),([\\d.]+),(-?[\\d.]+),\\\"(.+?)\\\",\\\"(.+?)\\\""),
            ZuwaZuwaDance = VRDancing,
            LSMedia = Pattern.compile("([\\d.]+),([\\d.]+),(.+?),(.*?),\\s+([^,]*?)$");
    class PopcornPalace
    {
        public String videoName = "";
        public double videoPos = 0.0D;
        public double videoLength = 0.0D;
        public String thumbnailUrl = "";
        public String displayName = "";
        public boolean isPaused = false;
        public boolean is3D = false;
        public boolean looping = false;
    }
    class Info
    {
        public String url = "";
        public String title = "";
        public double position = 0.0D;
        public double length = 0.0D;
        public String owner = "";
    }

    static Info parse(String string)
    {
        if (string.startsWith("[VRCX] "))
            string = string.substring(7);
        if (string.startsWith("VideoPlay(PyPyDance) "))
            return parsePyPyDance(string.substring(21));
        if (string.startsWith("VideoPlay(VRDancing) "))
            return parseVRDancing(string.substring(21));
        if (string.startsWith("VideoPlay(ZuwaZuwaDance) "))
            return parseZuwaZuwaDance(string.substring(25));
        if (string.startsWith("LSMedia "))
            return parsePyPyDance(string.substring(8));
        if (string.startsWith("VideoPlay(PopcornPalace) "))
            return parsePyPyDance(string.substring(25));
        return null;
    }

    static Info parsePyPyDance(String string)
    {
        Matcher m = PyPyDance.matcher(string);
        if (!m.find())
            return null;
        Info info = new Info();
        info.url = m.group(1);
   try{ info.position = Double.parseDouble(m.group(2)); }catch(NumberFormatException nfex){}
   try{ info.length = Double.parseDouble(m.group(3)); }catch(NumberFormatException nfex){}
        info.title = m.group(4);
        return info;
    }
    static Info parseVRDancing(String string)
    {
        Matcher m = VRDancing.matcher(string);
        if (!m.find())
            return null;
        Info info = new Info();
        info.url = m.group(1);
   try{ info.position = Double.parseDouble(m.group(2)); }catch(NumberFormatException nfex){}
   try{ info.length = Double.parseDouble(m.group(3)); }catch(NumberFormatException nfex){}
        info.owner = m.group(5);
        info.title = m.group(6);
        return info;
    }
    static Info parseZuwaZuwaDance(String string)
    {
        return parseVRDancing(string);
    }
    static Info parseLSMedia(String string)
    {
        Matcher m = LSMedia.matcher(string);
        if (!m.find())
            return null;
        Info info = new Info();
   try{ info.position = Double.parseDouble(m.group(1)); }catch(NumberFormatException nfex){}
   try{ info.length = Double.parseDouble(m.group(2)); }catch(NumberFormatException nfex){}
        info.owner = m.group(3);
        info.title = m.group(4);
        return info;
    }
    static Info parsePopcornPalace(String string)
    {
        try
        {
            PopcornPalace vp_pp = Scarlet.GSON.fromJson(string, VrcxVideoPlay.PopcornPalace.class);
            Info info = new Info();
            info.position = vp_pp.videoPos;
            info.length = vp_pp.videoLength;
            info.owner = vp_pp.displayName;
            info.title = vp_pp.videoName;
            return info;
        }
        catch (Exception ex)
        {
        }
        return null;
    }

}
