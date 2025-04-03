package net.sybyline.scarlet.util;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.UnaryOperator;

public final class Box<T>
{

    public static <T> Box<T> of()
    {
        return new Box<>();
    }
    public static <T> Box<T> of(T value)
    {
        return new Box<>(value);
    }

    public Box()
    {
        this(null);
    }
    public Box(T value)
    {
        this.value = value;
    }

    private T value;

    public boolean isEmpty()
    {
        return this.value == null;
    }

    public boolean isFilled()
    {
        return this.value != null;
    }

    public T get()
    {
        return this.value;
    }

    public <R> R get(Function<T, R> func)
    {
        T value = this.value;
        return value == null ? null : func.apply(value);
    }

    public synchronized T set(T value)
    {
        try
        {
            return this.value;
        }
        finally
        {
            this.value = value;
        }
    }

    public T setIfVal(T value, T expected)
    {
        return this.edit($ -> Objects.equals($, expected) ? value : $);
    }

    public T setIfRef(T value, T expected)
    {
        return this.edit($ -> $ == expected ? value : $);
    }

    public synchronized T edit(UnaryOperator<T> editor)
    {
        try
        {
            return this.value;
        }
        finally
        {
            this.value = editor.apply(this.value);
        }
    }

    public int hashCode()
    {
        return Objects.hashCode(this.value);
    }

    public boolean equals(Object other)
    {
        return other instanceof Box && Objects.equals(this.value, ((Box<?>)other).value);
    }

    public String toString()
    {
        return String.valueOf(this.value);
    }

}
