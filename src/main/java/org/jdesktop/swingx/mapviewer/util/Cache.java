package org.jdesktop.swingx.mapviewer.util;

import java.util.Collection;
import java.util.Set;

/**
 * Key-Value cache interface
 *
 * @author fgotusso <fgotusso@swissms.ch>
 */
public interface Cache<K,V> {
    public void put(final K key, final V value);
    public boolean contains(final K key);
    public V get(final K key);
    public boolean remove(final K key);
    public void clear();
    public int getLimit();
    public Set<K> getKeys();
    public Collection<V> getValues();
}
