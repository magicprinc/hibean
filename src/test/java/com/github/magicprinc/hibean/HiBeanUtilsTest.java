package com.github.magicprinc.hibean;

import com.github.magicprinc.hibean.example.Smmo;
import com.zaxxer.hikari.HikariDataSource;
import io.ebean.DB;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 @see HiBeanUtils */
class HiBeanUtilsTest {
	@Test
	void basic () {
		var springDataSource = new HikariDataSource();
		//springDataSource.setJdbcUrl("jdbc:sqlserver://127.0.0.1;databaseName=front;trustServerCertificate=true");
		//springDataSource.setUsername("and password");
		springDataSource.setJdbcUrl("jdbc:h2:mem:fooBarZooDb");

		HiBeanUtils.setDdl(true);
		var db = HiBeanUtils.database("fooBarZoo", springDataSource, null);

		assertSame(springDataSource, db.dataSource());
		assertEquals("fooBarZoo", db.name());
		assertSame(db, DB.byName("fooBarZoo"));

		var mo = new Smmo(db.name());
		mo.setSrcAddr("from");
		mo.setDstAddr("1917393791");
		mo.save();

		var mo2 = mo.finder().query().where().eq("SrcAddr", "from").and().eq("dstAddr", "1917393791").findOne();
		assertNotSame(mo, mo2);
		assertEquals(mo, mo2);

		assertSame(db, mo.db());
	}
}