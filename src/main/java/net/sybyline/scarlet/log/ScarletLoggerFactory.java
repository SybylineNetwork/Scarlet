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
        return new ScarletLogger(name);
    }

}
