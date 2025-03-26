package com.github.magicprinc.hibean.util;

import io.ebean.BeanRepository;
import io.ebean.Database;
import io.ebean.Finder;
import io.ebean.Query;
import io.ebean.UpdateQuery;
import org.jspecify.annotations.NullMarked;

/**
 Template 👍

 https://ebean.io/docs/best-practice/

 Finder in a Model
 <pre>{@code
	 public static final Finder<ID,MODEL_CLAS> finder = new Finder<>(klass);// default database
 }</pre>
 <pre>{@code
	 public static final Finder<ID,MODEL_CLAS> finder = new Finder<>(klass, databaseName);
 }</pre>

 @see BeanRepository
 @see io.ebean.Finder
 @see io.ebean.Model
 @see io.ebean.typequery.IQueryBean
*/
@NullMarked
public abstract class FBeanRepository<ID,T> extends BeanRepository<ID,T> {
	/**
	 {@link BeanRepository} has all functionality, but with different method names. To make life easier...

	 Remember about Q‹class› e.g. QCustomer implements {@link io.ebean.typequery.QueryBean}/{@link io.ebean.typequery.IQueryBean}

	 @see io.ebean.Finder
	*/
	public final Finder<ID,T> find;

	/**
	 Create with the given bean type and Database instance.
	 <p>
	 Typically users would extend BeanRepository rather than BeanFinder.
	 </p>
	 <pre>{@code

	@Inject public CustomerRepository(Database server) {
	super(Customer.class, server);
	}

	}</pre>

	 @param type The bean type
	 @param database The Database instance typically created via Spring factory or equivalent
	 */
	public FBeanRepository (Class<T> type, Database database) {
		super(type, database);
		find = new Finder<>(type, database.name());
	}//new

	@Override public UpdateQuery<T> updateQuery (){ return super.updateQuery(); }

	@Override public Query<T> query (){ return super.query(); }

	@Override public Query<T> nativeSql (String nativeSql){ return super.nativeSql(nativeSql); }
}