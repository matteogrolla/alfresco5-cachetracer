/*
 * Copyright (C) 2005-2012 Alfresco Software Limited.
 *
 * This file is part of Alfresco
 *
 * Alfresco is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Alfresco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 */
package org.alfresco.repo.cache;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;
import com.googlecode.concurrentlinkedhashmap.EvictionListener;
import com.googlecode.concurrentlinkedhashmap.Weighers;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.BeanNameAware;

import java.io.Serializable;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicLong;

/**
 * {@link SimpleCache} implementation backed by a {@link com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap}.
 * 
 * @author Matt Ward
 */
public final class DefaultSimpleCache<K extends Serializable, V extends Object>
    implements SimpleCache<K, V>, BeanNameAware
{
    private static final Log log = LogFactory.getLog(DefaultSimpleCache.class);
    private static final int DEFAULT_CAPACITY = 200000;
    private ConcurrentLinkedHashMap<K, AbstractMap.SimpleImmutableEntry<K, V>> map;
    private String cacheName;
    private AtomicLong hits = new AtomicLong(0);
    private AtomicLong readAccesses = new AtomicLong(0);
    private AtomicLong writeAccesses = new AtomicLong(0);
    
    /**
     * Construct a cache using the specified capacity and name.
     * 
     * @param maxItems The cache capacity.
     */
    public DefaultSimpleCache(int maxItems, final String cacheName)
    {
        if (maxItems < 1)
        {
            throw new IllegalArgumentException("maxItems must be a positive integer, but was " + maxItems);
        }
        
        setBeanName(cacheName);

        EvictionListener<K, AbstractMap.SimpleImmutableEntry<K, V>> listener = new EvictionListener<K, AbstractMap.SimpleImmutableEntry<K, V>>() {
          AtomicLong numEvictions = new AtomicLong(0);

          @Override public void onEviction(K key, AbstractMap.SimpleImmutableEntry<K, V> value) {
            float hitRatio = ((float)(hits.get())) / readAccesses.get();
            if (numEvictions.get() % 1000 == 0) {
              log.debug("Cache: " + DefaultSimpleCache.this.cacheName + " evictions: " + (numEvictions.getAndIncrement()) + " hitRatio: " + hitRatio);
            }
          }
        };
        
        // The map will have a bounded size determined by the maxItems member variable.
        map = new ConcurrentLinkedHashMap.Builder<K, AbstractMap.SimpleImmutableEntry<K, V>>()
                    .maximumWeightedCapacity(maxItems)
                    .concurrencyLevel(32)
                    .weigher(Weighers.singleton())
                    .listener(listener)
                    .build();
    }
    
    /**
     * Default constructor. Initialises the cache with a default capacity {@link #DEFAULT_CAPACITY}
     * and no name.
     */
    public DefaultSimpleCache()
    {
        this(DEFAULT_CAPACITY, null);
    }
    
    @Override
    public boolean contains(K key)
    {
        return map.containsKey(key);
    }

    @Override
    public Collection<K> getKeys()
    {
        return map.keySet();
    }

    @Override
    public V get(K key)
    {
        readAccesses.getAndIncrement();
        if (readAccesses.get() % 1000 == 0 && !getCacheName().equals("compiledModelsCache")  && !getCacheName().equals("prefixesCache")){
          log.debug("Get Cache: "+getCacheName()+" size: "+map.size()+" capacity: "+map.capacity()+" hitRatio: "+getHitRatio());
        }
        AbstractMap.SimpleImmutableEntry<K, V> kvp = map.get(key);
        if (kvp == null)
        {
            return null;
        } else {
          hits.getAndIncrement();
        }
        return kvp.getValue();
    }

    float getHitRatio(){
      return ((float)hits.get()) / readAccesses.get();
    }

    @Override
    public void put(K key, V value)
    {
        writeAccesses.getAndIncrement();
        if (writeAccesses.get() % 1000 == 0){
          log.debug("Put Cache: "+getCacheName()+"size: "+map.size()+" capacity: "+map.capacity()+" hitRatio: "+getHitRatio());
        }
        putAndCheckUpdate(key, value);
    }

    /**
     * <code>put</code> method that may be used to check for updates in a thread-safe manner.
     * 
     * @return <code>true</code> if the put resulted in a change in value, <code>false</code> otherwise.
     */
    public boolean putAndCheckUpdate(K key, V value)
    {
        AbstractMap.SimpleImmutableEntry<K, V> kvp = new AbstractMap.SimpleImmutableEntry<K, V>(key, value);
        AbstractMap.SimpleImmutableEntry<K, V> priorKVP = map.put(key, kvp);
        return priorKVP != null && (! priorKVP.equals(kvp));
    }
    
    @Override
    public void remove(K key)
    {
        map.remove(key);
    }

    @Override
    public void clear()
    {
        map.clear();
    }

    @Override
    public String toString()
    {
        return "DefaultSimpleCache[maxItems=" + map.capacity() + ", cacheName=" + cacheName + "]";
    }

    /**
     * Sets the maximum number of items that the cache will hold.
     * 
     * @param maxItems
     */
    public void setMaxItems(int maxItems)
    {
        map.setCapacity(maxItems);
    }
    
    /**
     * Gets the maximum number of items that the cache will hold.
     * 
     * @return maxItems
     */
    public int getMaxItems()
    {
        return map.capacity();
    }
    
    
    /**
     * Retrieve the name of this cache.
     * 
     * @see #setCacheName(String)
     * @return the cacheName
     */
    public String getCacheName()
    {
        return this.cacheName;
    }

    /**
     * Since there are many cache instances, it is useful to be able to associate
     * a name with each one.
     * 
     * @see #setBeanName(String)
     * @param cacheName
     */
    public void setCacheName(String cacheName)
    {
        this.cacheName = cacheName;
    }

    /**
     * Since there are many cache instances, it is useful to be able to associate
     * a name with each one.
     * 
     * @param cacheName Set automatically by Spring, but can be set manually if required.
     */
    @Override
    public void setBeanName(String cacheName)
    {
        this.cacheName = cacheName;
    }
}
