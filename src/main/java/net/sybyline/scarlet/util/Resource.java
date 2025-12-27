package net.sybyline.scarlet.util;

import java.io.Closeable;

public interface Resource extends Closeable
{

    @Override
    public abstract void close();

}
