package com.github.magicprinc.hibean;

import com.github.magicprinc.hibean.example.Smmo;
import com.github.magicprinc.hibean.util.FBeanRepository;
import io.ebean.DB;
import io.ebean.Model;
import lombok.val;
import org.apiguardian.api.API;
import org.jeasy.random.EasyRandom;
import org.jeasy.random.EasyRandomParameters;
import org.jeasy.random.FieldPredicates;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 @see FBeanRepository
 @see io.ebean.Finder
*/
public class FinderMixinTest {
	static {
		SmartConfigTest.configureSmallRyeConfig();
	}

	@API(status = API.Status.INTERNAL)
	private static final class Example<ID,X> extends Model {
	}

	@Test
	void classNames () {
		var e = "com.github.magicprinc.hibean.FinderMixinTest.Example";
		var e$ = "com.github.magicprinc.hibean.FinderMixinTest$Example";
		assertEquals("class "+e$, Example.class.toString());
		assertEquals(e$, Example.class.getName());
		assertEquals(e$, Example.class.getTypeName());
		assertEquals(e, Example.class.getCanonicalName());
		// private static final class com.github.magicprinc.hibean.FinderMixinTest$Example<ID,X>
		assertEquals("private static final class "+ e$ +"<ID,X>", Example.class.toGenericString());

		var t = "com.github.magicprinc.hibean.FinderMixinTest";
		assertEquals("class "+t, getClass().toString());
		assertEquals(t, getClass().getName());
		assertEquals(t, getClass().getTypeName());
		assertEquals(t, getClass().getCanonicalName());
		// public class com.github.magicprinc.hibean.FinderMixinTest
		assertEquals("public class "+ t, getClass().toGenericString());
	}

	@Test
	void _smmo () {
		val parameters = new EasyRandomParameters()
			.bypassSetters(true)
			.excludeField(FieldPredicates.named("_ebean_.*"))
			.excludeField(FieldPredicates.named("smmoId"))// autoinc column × smmo.setSmmoId(null)
			.excludeField(FieldPredicates.named("_\\$.*"));
		val r = new EasyRandom(parameters);// Instancio

		assertInstanceOf(HikariEbeanDataSourcePool.class, DB.getDefault().dataSource());
		assertEquals("db", DB.getDefault().name());
		assertEquals("jdbc:h2:mem:my_app", ((HikariEbeanDataSourcePool) DB.getDefault().dataSource()).getHikariDataSource().getJdbcUrl());
		assertEquals("sa", ((HikariEbeanDataSourcePool) DB.getDefault().dataSource()).getHikariDataSource().getUsername());
		assertNull(((HikariEbeanDataSourcePool) DB.getDefault().dataSource()).getHikariDataSource().getPassword());

		for (int i=0; i<100; i++){
			var smmo = r.nextObject(Smmo.class);
			assertEquals("db", smmo.db().name());
			DB.save(smmo);
		}
		assertEquals(100, Smmo.FINDER.query().findCount());

		Smmo.FINDER.all().forEach(mo->{
			assertTrue(mo.pid() >= 0 && mo.pid() < 255);
			if (mo.smmoId() % 20 == 1)
					System.out.println(mo);// some examples
		});
	}
}