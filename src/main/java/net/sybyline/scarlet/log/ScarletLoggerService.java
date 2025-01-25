package net.sybyline.scarlet.log;

import org.slf4j.ILoggerFactory;
import org.slf4j.IMarkerFactory;
import org.slf4j.helpers.BasicMDCAdapter;
import org.slf4j.helpers.BasicMarkerFactory;
import org.slf4j.spi.MDCAdapter;
import org.slf4j.spi.SLF4JServiceProvider;

public class ScarletLoggerService implements SLF4JServiceProvider
{

    public ScarletLoggerService()
    {
    }

    final ILoggerFactory loggerFactory = new ScarletLoggerFactory();
    final IMarkerFactory markerFactory = new BasicMarkerFactory();
    final MDCAdapter mdcAdapter = new BasicMDCAdapter();

    @Override
    public ILoggerFactory getLoggerFactory()
    {
        return this.loggerFactory;
    }

    @Override
    public IMarkerFactory getMarkerFactory()
    {
        return this.markerFactory;
    }

    @Override
    public MDCAdapter getMDCAdapter()
    {
        return this.mdcAdapter;
    }

    @Override
    public String getRequestedApiVersion()
    {
        return "2.0.13";
    }

    @Override
    public void initialize()
    {
    }

}
