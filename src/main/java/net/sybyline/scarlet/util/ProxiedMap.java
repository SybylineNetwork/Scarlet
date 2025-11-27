package net.sybyline.scarlet.util;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

public interface ProxiedMap<K, V> extends Proxied<Map<K, V>>, Map<K, V>
{
    class Impl<K, V> extends Proxied.Impl<Map<K, V>> implements ProxiedMap<K, V>
    {
        public Impl()
        {
            super();
        }
        public Impl(Map<K, V> proxied)
        {
            super(proxied);
        }
    }
    @Override
    public default int size()
    {
        return this.getProxiedObject().size();
    }
    @Override
    public default boolean isEmpty()
    {
        return this.getProxiedObject().isEmpty();
    }
    @Override
    public default boolean containsKey(Object key)
    {
        return this.getProxiedObject().containsKey(key);
    }
    @Override
    public default boolean containsValue(Object value)
    {
        return this.getProxiedObject().containsValue(value);
    }
    @Override
    public default V get(Object key)
    {
        return this.getProxiedObject().get(key);
    }
    @Override
    public default V put(K key, V value)
    {
        return this.getProxiedObject().put(key, value);
    }
    @Override
    public default V remove(Object key)
    {
        return this.getProxiedObject().remove(key);
    }
    @Override
    public default void putAll(Map<? extends K, ? extends V> m)
    {
        this.getProxiedObject().putAll(m);
    }
    @Override
    public default void clear()
    {
        this.getProxiedObject().clear();
    }
    @Override
    public default Set<K> keySet()
    {
        return this.getProxiedObject().keySet();
    }
    @Override
    public default Collection<V> values()
    {
        return this.getProxiedObject().values();
    }
    @Override
    public default Set<Entry<K, V>> entrySet()
    {
        return this.getProxiedObject().entrySet();
    }
    @Override
    public default V getOrDefault(Object key, V defaultValue)
    {
        return this.getProxiedObject().getOrDefault(key, defaultValue);
    }
    @Override
    public default void forEach(BiConsumer<? super K, ? super V> action)
    {
        this.getProxiedObject().forEach(action);
    }
    @Override
    public default void replaceAll(BiFunction<? super K, ? super V, ? extends V> function)
    {
        this.getProxiedObject().replaceAll(function);
    }
    @Override
    public default V putIfAbsent(K key, V value)
    {
        return this.getProxiedObject().putIfAbsent(key, value);
    }
    @Override
    public default boolean remove(Object key, Object value)
    {
        return this.getProxiedObject().remove(key, value);
    }
    @Override
    public default boolean replace(K key, V oldValue, V newValue)
    {
        return this.getProxiedObject().replace(key, oldValue, newValue);
    }
    @Override
    public default V replace(K key, V value)
    {
        return this.getProxiedObject().replace(key, value);
    }
    @Override
    public default V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction)
    {
        return this.getProxiedObject().computeIfAbsent(key, mappingFunction);
    }
    @Override
    public default V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction)
    {
        return this.getProxiedObject().computeIfPresent(key, remappingFunction);
    }
    @Override
    public default V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction)
    {
        return this.getProxiedObject().compute(key, remappingFunction);
    }
    @Override
    public default V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction)
    {
        return this.getProxiedObject().merge(key, value, remappingFunction);
    }
}
