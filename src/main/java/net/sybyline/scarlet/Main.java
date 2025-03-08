package net.sybyline.scarlet;

import net.sybyline.scarlet.util.MavenDepsLoader;

public abstract class Main
{

    static
    {
        MavenDepsLoader.init();
    }

    public static void main(String[] args) throws Throwable
    {
        Scarlet.main(args);
    }

    private Main()
    {
        throw new UnsupportedOperationException();
    }

}
