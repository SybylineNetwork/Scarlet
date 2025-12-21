package net.sybyline.scarlet.util;

public interface Proxied<O>
{
    public abstract O getProxiedObject();
    class Impl<O> implements Proxied<O>
    {
        public Impl()
        {
            this(null);
        }
        public Impl(O proxied)
        {
            this.proxied = proxied;
        }
        protected O proxied;
        @Override
        public O getProxiedObject()
        {
            return this.proxied;
        }
        @Override
        public boolean equals(Object o)
        {
            return this.proxied.equals(o);
        }
        @Override
        public int hashCode()
        {
            return this.proxied.hashCode();
        }
        @Override
        public String toString()
        {
            return this.proxied.toString();
        }
    }
}
