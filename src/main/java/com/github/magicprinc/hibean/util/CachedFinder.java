package com.github.magicprinc.hibean.util;

import io.ebean.Finder;
import io.ebean.Model;

import java.io.Serializable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 @see FBeanRepository
 @see HiBeanUtils
 @see io.ebean.Finder
 @see io.ebean.Model
*/
@SuppressWarnings({"rawtypes", "unchecked"})
public class CachedFinder {
	private static final ConcurrentMap<Serializable,Finder> FINDER_CACHE = new ConcurrentHashMap<>();

	public static <ID,ENTITY> Finder<ID,ENTITY> finder (ENTITY usuallyAModel) {
		String databaseName = usuallyAModel instanceof Model m ? m.db().name()
				: null;

		return (Finder<ID,ENTITY>) finder(usuallyAModel.getClass(), databaseName);
	}

	public static <ID,ENTITY> Finder<ID,ENTITY> finder (Class<ENTITY> klass) {
		return finder(klass, null/*default database*/);
	}

	public static <ID,ENTITY> Finder<ID,ENTITY> finder (Class<ENTITY> klass, String databaseName) {
		return (Finder<ID,ENTITY>)(databaseName == null || databaseName.isEmpty() || "db".equals(databaseName)
			? FINDER_CACHE.computeIfAbsent(klass, k ->
					new Finder(klass))// default database
			: FINDER_CACHE.computeIfAbsent(klass.getName()+':'+databaseName, k ->
					new Finder(klass, databaseName))
		);
	}
}