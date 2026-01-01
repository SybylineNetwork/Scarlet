package net.sybyline.scarlet.util;

import java.nio.charset.StandardCharsets;

import net.sybyline.scarlet.Scarlet;

public class Credits
{

    public Credits()
    {
    }

    public String name;
    public String url;
    public String role;

    public static Credits[] load()
    {
        try
        {
            return Scarlet.GSON_PRETTY.fromJson(new String(MiscUtils.readAllBytes(Credits.class.getResourceAsStream("credits.json")), StandardCharsets.UTF_8), Credits[].class);
        }
        catch (Exception e)
        {
            return null;
        }
    }

}
