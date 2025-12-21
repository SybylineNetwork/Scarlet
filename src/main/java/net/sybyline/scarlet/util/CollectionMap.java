package net.sybyline.scarlet.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.Spliterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

public interface CollectionMap<K, V, C extends Collection<V>> extends Map<K, C>
{

    public static <K, V> OfSets<K, V> setsDefault()
    {
        return new DefaultSetMap<>();
    }

    public static <K, V> OfSets<K, V> setsConcurrent()
    {
        return new ConcurrentSetMap<>();
    }

    public static <K, V extends Enum<V>> OfSets<K, V> setsEnum(Class<V> enumClass)
    {
        return new EnumSetMap<>(enumClass);
    }

    public static <K, V> OfSets<K, V> setsProxied(Map<K, Set<V>> proxied, Supplier<Set<V>> newSet)
    {
        return new ProxiedSetMap<>(proxied, newSet);
    }

    public static <K, V> OfLists<K, V> listsDefault()
    {
        return new DefaultListMap<>();
    }

    public static <K, V> OfLists<K, V> listsConcurrent()
    {
        return new ConcurrentListMap<>();
    }

    public static <K, V> OfLists<K, V> slistsProxied(Map<K, List<V>> proxied, Supplier<List<V>> newSet)
    {
        return new ProxiedListMap<>(proxied, newSet);
    }

    // Iterable

    public default Iterator<V> valuesIterator(K key)
    {
        return this.valuesGetOrEmpty(key).iterator();
    }

    public default void valuesForEach(K key, Consumer<? super V> consumer)
    {
        this.valuesGetOrEmpty(key).forEach(consumer);
    }

    public default Spliterator<V> valuesSpliterator(K key)
    {
        return this.valuesGetOrEmpty(key).spliterator();
    }

    // Collection

    public default int valuesSize(K key)
    {
        return this.valuesGetOrEmpty(key).size();
    }

    public default boolean valuesIsEmpty(K key)
    {
        return this.valuesGetOrEmpty(key).isEmpty();
    }

    public default boolean valuesContains(K key, Object value)
    {
        return this.valuesGetOrEmpty(key).contains(value);
    }

    public default Object[] valuesToArray(K key)
    {
        return this.valuesGetOrEmpty(key).toArray();
    }

    public default <T> T[] valuesToArray(K key, T[] array)
    {
        return this.valuesGetOrEmpty(key).toArray(array);
    }

    public default boolean valuesAdd(K key, V value)
    {
        return this.valuesGetOrCreate(key).add(value);
    }

    public default boolean valuesRemove(K key, V value)
    {
        return this.valuesEditChanged(key, values -> values.remove(value));
    }

    public default boolean valuesContainsAll(K key, Collection<?> values)
    {
        return this.valuesGetOrEmpty(key).containsAll(values);
    }

    public default boolean valuesAddAll(K key, Collection<? extends V> values)
    {
        return this.valuesGetOrCreate(key).addAll(values);
    }

    public default boolean valuesRemoveAll(K key, Collection<?> values)
    {
        return this.valuesEditChanged(key, $values -> $values.removeAll(values));
    }

    public default boolean valuesRemoveIf(K key, Predicate<? super V> filter)
    {
        return this.valuesEditChanged(key, $values -> $values.removeIf(filter));
    }

    public default boolean valuesRetainAll(K key, Collection<?> values)
    {
        return this.valuesEditChanged(key, $values -> $values.retainAll(values));
    }

    public default boolean valuesClear(K key)
    {
        return this.remove(key) != null;
    }

    public default Stream<V> valuesStream(K key)
    {
        return this.valuesGetOrEmpty(key).stream();
    }

    public default Stream<V> valuesParallelStream(K key)
    {
        return this.valuesGetOrEmpty(key).parallelStream();
    }

    // Custom

    public default void valuesForEach(BiConsumer<? super K, ? super V> biConsumer)
    {
        this.forEach((key, values) -> values.forEach(value -> biConsumer.accept(key, value)));
    }

    public default void valuesForEachElement(K key, BiConsumer<? super K, ? super V> biConsumer)
    {
        C $values = this.get(key);
        if ($values == null)
            return;
        for (V value : $values)
            biConsumer.accept(key, value);
    }

    public default boolean valuesEditChanged(K key, Predicate<? super C> predicate)
    {
        boolean[] ret = new boolean[1];
        this.compute(key, (key_, $values) -> $values != null && (ret[0] = predicate.test($values)) && $values.isEmpty() ? null : $values);
        return ret[0];
    }

    public default <T> T valuesEditRetrieve(K key, Function<? super C, ? extends T> function)
    {
        Object[] ret = new Object[1];
        this.compute(key, (key_, $values) ->
        {
            if ($values == null)
                return null;
            ret[0] = function.apply($values);
            return $values.isEmpty() ? null : $values;
        });
        @SuppressWarnings("unchecked")
        T value = (T)ret[0];
        return value;
    }

    public default C valuesGetOrCreate(K key)
    {
        return this.computeIfAbsent(key, $ -> this.valuesProvideNew());
    }

    public default C valuesGetOrEmpty(K key)
    {
        return this.getOrDefault(key, this.valuesProvideEmpty());
    }

    public abstract C valuesProvideNew();

    public abstract C valuesProvideEmpty();

    interface OfSets<K, V> extends CollectionMap<K, V, Set<V>>
    {

        @Override
        public default Set<V> valuesProvideEmpty()
        {
            return Collections.emptySet();
        }

    }

    interface OfLists<K, V> extends CollectionMap<K, V, List<V>>
    {

        @Override
        public default List<V> valuesProvideEmpty()
        {
            return Collections.emptyList();
        }

        public default boolean valuesAddAll(K key, int index, Collection<? extends V> values)
        {
            return this.valuesGetOrCreate(key).addAll(index, values);
        }

        public default void valuesReplaceAll(K key, UnaryOperator<V> operator)
        {
            this.valuesGetOrEmpty(key).replaceAll(operator);
        }

        public default void valuesSort(K key, Comparator<? super V> comparator)
        {
            this.valuesGetOrEmpty(key).sort(comparator);
        }

        public default V valuesGet(K key, int index)
        {
            return this.valuesGetOrEmpty(key).get(index);
        }

        public default V valuesSet(K key, int index, V value)
        {
            return this.valuesGetOrEmpty(key).set(index, value);
        }

        public default void valuesAdd(K key, int index, V value)
        {
            this.valuesGetOrCreate(key).add(index, value);
        }

        public default V valuesRemove(K key, int index)
        {
            return this.valuesEditRetrieve(key, list -> list.remove(index));
        }

        public default int valuesIndexOf(K key, Object value)
        {
            return this.valuesGetOrEmpty(key).indexOf(value);
        }

        public default int valuesLastIndexOf(K key, Object value)
        {
            return this.valuesGetOrEmpty(key).lastIndexOf(value);
        }

        public default ListIterator<V> valuesListIterator(K key)
        {
            return this.valuesGetOrEmpty(key).listIterator();
        }

        public default ListIterator<V> valuesListIterator(K key, int index)
        {
            return this.valuesGetOrEmpty(key).listIterator(index);
        }

        public default List<V> valuesSubList(K key, int fromIndex, int toIndex)
        {
            return this.valuesGetOrEmpty(key).subList(fromIndex, toIndex);
        }

    }

}

class DefaultSetMap<K, V> extends ProxiedMap.Impl<K, Set<V>> implements CollectionMap.OfSets<K, V>
{
    public DefaultSetMap()
    {
        super(new HashMap<>());
    }
    @Override
    public Set<V> valuesProvideNew()
    {
        return new HashSet<>();
    }
}

class EnumSetMap<K, V extends Enum<V>> extends ProxiedMap.Impl<K, Set<V>> implements CollectionMap.OfSets<K, V>
{
    public EnumSetMap(Class<V> enumClass)
    {
        super(new HashMap<>());
        this.enumClass = enumClass;
    }
    final Class<V> enumClass;
    @Override
    public Set<V> valuesProvideNew()
    {
        return EnumSet.noneOf(this.enumClass);
    }
}

class ConcurrentSetMap<K, V> extends ProxiedMap.Impl<K, Set<V>> implements CollectionMap.OfSets<K, V>
{
    public ConcurrentSetMap()
    {
        super(new ConcurrentHashMap<>());
    }
    @Override
    public Set<V> valuesProvideNew()
    {
        return new CopyOnWriteArraySet<>();
    }
}

class ProxiedSetMap<K, V> extends ProxiedMap.Impl<K, Set<V>> implements CollectionMap.OfSets<K, V>
{
    public ProxiedSetMap(Map<K, Set<V>> proxied, Supplier<Set<V>> newSet)
    {
        super(proxied);
        this.newSet = newSet;
    }
    final Supplier<Set<V>> newSet;
    @Override
    public Set<V> valuesProvideNew()
    {
        return this.newSet.get();
    }
}

class DefaultListMap<K, V> extends ProxiedMap.Impl<K, List<V>> implements CollectionMap.OfLists<K, V>
{
    DefaultListMap()
    {
        super(new HashMap<>());
    }
    @Override
    public List<V> valuesProvideNew()
    {
        return new ArrayList<>();
    }
}

class ConcurrentListMap<K, V> extends ProxiedMap.Impl<K, List<V>> implements CollectionMap.OfLists<K, V>
{
    ConcurrentListMap()
    {
        super(new ConcurrentHashMap<>());
    }
    @Override
    public List<V> valuesProvideNew()
    {
        return new CopyOnWriteArrayList<>();
    }
}

class ProxiedListMap<K, V> extends ProxiedMap.Impl<K, List<V>> implements CollectionMap.OfLists<K, V>
{
    ProxiedListMap(Map<K, List<V>> proxied, Supplier<List<V>> newList)
    {
        super(proxied);
        this.newList = newList;
    }
    final Supplier<List<V>> newList;
    @Override
    public List<V> valuesProvideNew()
    {
        return this.newList.get();
    }
}
