package net.sybyline.scarlet.log;

import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;

class ScarletLoggerFactory implements ILoggerFactory
{

    ScarletLoggerFactory()
    {
    }

    @Override
    public Logger getLogger(String name)
    {
        if (name.startsWith("net.dv8tion.jda.api."))
        {
            name = "JDA/" + name.substring(20).replace('.', '/');
        }
        else if (name.startsWith("net.dv8tion.jda.internal."))
        {
            name = "JDA-I/" + name.substring(25).replace('.', '/');
        }
        return new ScarletLogger(name);
    }

}
