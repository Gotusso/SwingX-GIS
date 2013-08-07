package org.jdesktop.swingx.mapviewer.util;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

/**
 * General purpose Key-Value cache with LRU algorithm. Note that this class is
 * not thread safe.
 *
 * @author fgotusso <fgotusso@swissms.ch>
 */
public class LeastRecentlyUsedCache<K, V> implements Cache<K,V> {
    public static final int DEFAULT_SIZE = 128;

    private final LinkedList<K> accessControl = new LinkedList<K>();
    private Map<K, V> storage;
    private int limit;

    public LeastRecentlyUsedCache() {
        this(DEFAULT_SIZE);
    }

    public LeastRecentlyUsedCache(final int limit) {
        storage = createStorage(limit);
        this.limit = limit;
    }

    protected Map<K,V> createStorage(final int limit) {
        return new HashMap<K, V>(limit, 1f);
    }

    protected void updateAccess(final K key) {
        accessControl.remove(key);
        accessControl.addFirst(key);
    }

    @Override
    public void put(final K key, final V value) {
        updateAccess(key);
        if (storage.size() + 1 > limit) {
            final K last = accessControl.removeLast();
            if (last != null) {
                storage.remove(last);
            }
        }
        storage.put(key, value);
    }

    @Override
    public boolean contains(final K key) {
        final boolean result = storage.containsKey(key);
        if (result) {
            updateAccess(key);
        }
        return result;
    }

    @Override
    public V get(final K key) {
        if (storage.containsKey(key)) {
            updateAccess(key);
            return storage.get(key);
        }
        return null;
    }

    @Override
    public boolean remove(final K key) {
        final boolean result = storage.remove(key) != null;
        if (result) {
            accessControl.remove(key);
        }
        return result;
    }

    @Override
    public void clear() {
        accessControl.clear();
        storage.clear();
    }

    @Override
    public int getLimit() {
        return limit;
    }

    @Override
    public Set<K> getKeys() {
        return storage.keySet();
    }

    @Override
    public Collection<V> getValues() {
        return storage.values();
    }
}
