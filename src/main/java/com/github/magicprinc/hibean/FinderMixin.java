package com.github.magicprinc.hibean;

import io.ebean.Finder;
import io.ebean.Model;

import java.io.Serializable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 Universal Finder

 @see FBeanRepository

 @see io.ebean.Finder
 @see io.ebean.Model
 @see jakarta.persistence.Entity
 */
@SuppressWarnings({"PublicStaticCollectionField", "CollectionWithoutInitialCapacity", "rawtypes", "unchecked"})
public interface FinderMixin<APPLIED_TO_CLAS> {
	ConcurrentMap<Serializable,Finder> FINDER_CACHE = new ConcurrentHashMap<>();

	@SuppressWarnings({"InstanceofThis"})
	default <ID> Finder<ID,APPLIED_TO_CLAS> finder () {
		String databaseName = this instanceof Model m
				? m.db().name()
				: null;

		return finder(databaseName);
	}

	default <ID> Finder<ID,APPLIED_TO_CLAS> finder (String databaseName) {
		return (Finder<ID,APPLIED_TO_CLAS>) finder(getClass(), databaseName);
	}

	static <ID,T> Finder<ID,T> finder (Class<T> klass) {
		return finder(klass, null);
	}

	static <ID,T> Finder<ID,T> finder (Class<T> klass, String databaseName) {
		return (Finder<ID,T>)(databaseName == null || databaseName.isEmpty() || "db".equals(databaseName)
			? FINDER_CACHE.computeIfAbsent(klass, k ->
					new Finder(klass))// default database
			: FINDER_CACHE.computeIfAbsent(klass.getName()+':'+databaseName, k ->
					new Finder(klass, databaseName))
		);
	}
}