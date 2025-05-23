package com.github.magicprinc.hibean.util;

import com.github.magicprinc.hibean.SmartConfigTest;
import com.github.magicprinc.hibean.example.Smmo;
import com.zaxxer.hikari.HikariDataSource;
import io.ebean.DB;
import io.ebean.config.DatabaseConfig;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 @see HiBeanUtils */
class HiBeanUtilsTest {
	static {
		SmartConfigTest.configureSmallRyeConfig();
	}
	@Test
	void basic () throws SQLException {
		val springDataSource = new HikariDataSource();
		//springDataSource.setJdbcUrl("jdbc:sqlserver://127.0.0.1;databaseName=front;trustServerCertificate=true");
		//springDataSource.setUsername("and password");
		springDataSource.setJdbcUrl("jdbc:h2:mem:fooBarZooDb");

		var calls = new AtomicInteger();

		HiBeanUtils.setDdl(true);
		var db = HiBeanUtils.database("fooBarZoo", springDataSource, null, cfg->{
			assertInstanceOf(DatabaseConfig.class, cfg);
			calls.incrementAndGet();
		});
		assertEquals(1, calls.get());

		assertEquals(new HikariEbeanDataSourceWrapper(springDataSource), db.dataSource());
		assertSame(springDataSource, db.dataSource().unwrap(HikariDataSource.class));
		assertEquals("fooBarZoo", db.name());
		assertSame(db, DB.byName("fooBarZoo"));

		var mo = new Smmo(db.name());
		mo.srcAddr("from")
			.dstAddr("1917393791")
			.save();

		Smmo mo2 = HiBeanUtils.finder(mo).query().where().eq("SrcAddr", "from").and().eq("dstAddr", "1917393791").findOne();
		assertNotSame(mo, mo2);
		assertEquals(mo, mo2);

		assertSame(db, mo.db());
		//assertSame(db, mo2.db()); finder (пока?) не протягивает свою DB в Model

		Smmo mo3 = CachedFinder.finder(mo).query().where().eq("SrcAddr", "from").and().eq("dstAddr", "1917393791").findOne();
		assertNotSame(mo, mo3);
		assertEquals(mo, mo3);
	}
}