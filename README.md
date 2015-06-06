# alfresco5-cachetracer
## Goal
Monitor alfresco caches making it possible to evaluate the effectiveness of cache tuning operations.

## What it does
every 1000 get or put on a cache it logs
- cache name
- cache size
- cache capacity
- cache hit ratio
logs cache evictions

## Applies to
Alfresco >= 4.2.c using local caches, which means it's not running in cluster mode.
In this case Alfresco is using google implementation of ConcurrentLinkedHashMap for it's caches.

## How to install
build with
mvn clean package
copy compiled classes to a corresponding path within alfresco/WEB-INF/classes
(currently the two .class must be copied to alfresco/WEB-INF/classes/org/alfresco/repo/cache)
in your log4j conf put a line like this
log4j.logger.org.alfresco.repo.cache.DefaultSimpleCache=debug

## How to uninstall
remove the .class files

## What could be done
The code covers my current bare necessities but it could be nice to
- make it a bean to monitor using jmx
- allow the caches to be resized at runtime
feel free to push and pull!!!
