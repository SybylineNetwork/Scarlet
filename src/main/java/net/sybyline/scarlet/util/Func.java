package net.sybyline.scarlet.util;

@SuppressWarnings({"unchecked","rawtypes"})
public interface Func
{

    public static class Nothing extends RuntimeException
    {
        private static final long serialVersionUID = 2859863324238761658L;
        private Nothing()
        {
            throw this;
        }
    }
    public static <From, To> To uncheckedCast(From object)
    {
        return (To)object;
    }

    @FunctionalInterface
    interface V0<X extends Throwable> extends Func
    {
        void invoke() throws X;
        default NE asUnchecked() { return this instanceof NE ? (NE)this : ((V0<Nothing>)this)::invoke; }
        default V0 asRaw() { return this; }
        interface NE extends V0<Nothing>
        {
            @Override
            default NE asRaw() { return this; }
        }
    }

    @FunctionalInterface
    interface F0<X extends Throwable, R> extends Func
    {
        R invoke() throws X;
        default NE<R> asUnchecked() { return this instanceof NE ? (NE)this : ((F0<Nothing, R>)this)::invoke; }
        default F0 asRaw() { return this; }
        default <RR> F0<X, RR> then(F1<? extends X, RR, R> func) { return () -> func.invoke(this.invoke()); }
        interface NE<R> extends F0<Nothing, R>
        {
            @Override
            default NE asRaw() { return this; }
            default <RR> NE<RR> thenUnchecked(F1.NE<RR, R> func) { return () -> func.invoke(this.invoke()); }
        }
    }

    @FunctionalInterface
    interface V1<X extends Throwable, A> extends Func
    {
        void invoke(A a) throws X;
        default NE<A> asUnchecked() { return this instanceof NE ? (NE)this : ((V1<Nothing, A>)this)::invoke; }
        default V1 asRaw() { return this; }
        interface NE<A> extends V1<Nothing, A>
        {
            @Override
            default NE asRaw() { return this; }
        }
    }

    @FunctionalInterface
    interface F1<X extends Throwable, R, A> extends Func
    {
        R invoke(A a) throws X;
        default NE<R, A> asUnchecked() { return this instanceof NE ? (NE)this : ((F1<Nothing, R, A>)this)::invoke; }
        default F1 asRaw() { return this; }
        default <RR> F1<X, RR, A> then(F1<? extends X, RR, R> func) { return (a) -> func.invoke(this.invoke(a)); }
        interface NE<R, A> extends F1<Nothing, R, A>
        {
            @Override
            default NE asRaw() { return this; }
            default <RR> NE<RR, A> then(F1.NE<RR, R> func) { return (a) -> func.invoke(this.invoke(a)); }
        }
    }

    @FunctionalInterface
    interface V2<X extends Throwable, A, B> extends Func
    {
        void invoke(A a, B b) throws X;
        default NE<A, B> asUnchecked() { return this instanceof NE ? (NE)this : ((V2<Nothing, A, B>)this)::invoke; }
        default V2 asRaw() { return this; }
        interface NE<A, B> extends V2<Nothing, A, B>
        {
            @Override
            default NE asRaw() { return this; }
        }
    }

    @FunctionalInterface
    interface F2<X extends Throwable, R, A, B> extends Func
    {
        R invoke(A a, B b) throws X;
        default NE<R, A, B> asUnchecked() { return this instanceof NE ? (NE)this : ((F2<Nothing, R, A, B>)this)::invoke; }
        default F2 asRaw() { return this; }
        interface NE<R, A, B> extends F2<Nothing, R, A, B>
        {
            @Override
            default NE asRaw() { return this; }
            default <RR> NE<RR, A, B> then(F1.NE<RR, R> func) { return (a, b) -> func.invoke(this.invoke(a, b)); }
        }
    }

    @FunctionalInterface
    interface V3<X extends Throwable, A, B, C> extends Func
    {
        void invoke(A a, B b, C c) throws X;
        default NE<A, B, C> asUnchecked() { return this instanceof NE ? (NE)this : ((V3<Nothing, A, B, C>)this)::invoke; }
        default V3 asRaw() { return this; }
        interface NE<A, B, C> extends V3<Nothing, A, B, C>
        {
            @Override
            default NE asRaw() { return this; }
        }
    }

    @FunctionalInterface
    interface F3<X extends Throwable, R, A, B, C> extends Func
    {
        R invoke(A a, B b, C c) throws X;
        default NE<R, A, B, C> asUnchecked() { return this instanceof NE ? (NE)this : ((F3<Nothing, R, A, B, C>)this)::invoke; }
        default F3 asRaw() { return this; }
        default <RR> F3<X, RR, A, B, C> then(F1<? extends X, RR, R> func) { return (a, b, c) -> func.invoke(this.invoke(a, b, c)); }
        interface NE<R, A, B, C> extends F3<Nothing, R, A, B, C>
        {
            @Override
            default NE asRaw() { return this; }
            default <RR> NE<RR, A, B, C> then(F1.NE<RR, R> func) { return (a, b, c) -> func.invoke(this.invoke(a, b, c)); }
        }
    }

}
