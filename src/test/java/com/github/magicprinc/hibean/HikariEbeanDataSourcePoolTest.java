package com.github.magicprinc.hibean;

import com.zaxxer.hikari.HikariDataSource;
import io.ebean.DB;
import io.ebean.Database;
import io.ebean.SqlRow;
import io.ebean.datasource.DataSourceConfig;
import io.ebean.datasource.DataSourceFactory;
import lombok.val;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static com.github.magicprinc.hibean.SmartConfig.isNumeric;
import static com.github.magicprinc.hibean.SmartConfig.normValue;
import static com.github.magicprinc.hibean.SmartConfig.trim;
import static org.junit.jupiter.api.Assertions.*;

/**
 @see HikariEbeanDataSourcePool
 @see com.zaxxer.hikari.HikariConfig
 */
class HikariEbeanDataSourcePoolTest {
	static {
		SmartConfigTest.configureSmallRyeConfig();
	}
  @Test
	void numeric () {
    assertFalse(isNumeric(""));
    assertFalse(isNumeric(" _"));

    assertTrue(isNumeric("42"));
    assertTrue(isNumeric("+1_2.4,4-"));
    assertTrue(isNumeric(" +-42_  1 , 2 . 3 eE "));
    //
    assertEquals("+-421,2.3eE", normValue(" +-42_  1 , 2 . 3 eE "));
  }

  @Test void externalConfFile () throws SQLException {
    Database external = DB.byName("external");
    assertEquals("external", external.name());
    assertEquals("ebean.external", ((HikariEbeanDataSourcePool) external.dataSource()).name());
    assertEquals(23, ((HikariEbeanDataSourcePool) external.dataSource()).status(true).maxSize());

    Object ds = external.dataSource().unwrap(null);
		assertInstanceOf(HikariDataSource.class, ds);
    assertEquals("ebean.external", ((HikariDataSource) ds).getPoolName());

    Connection con = external.dataSource().getConnection();
    PreparedStatement st = con.prepareStatement("select 42");
    ResultSet rs = st.executeQuery();
    assertTrue(rs.next());
    assertEquals(42, rs.getInt(1));
    rs.close();
    st.close();
    con.close();
  }

  @Test
	void testNoConfigFile () {
    val config = new DataSourceConfig()
        .setUrl("jdbc:h2:mem:tests7")
        .setUsername("foo")
        .setPassword("bar")
        .setAutoCommit(false)
        .setMaxConnections(51)
        .setMinConnections(19)
        .setMaxAgeMinutes(7);
    DataSource pool = DataSourceFactory.create("app", config);

		assertInstanceOf(HikariEbeanDataSourcePool.class, pool);
    var p = (HikariEbeanDataSourcePool) pool;
    assertEquals("ebean.app", p.name());
    assertEquals("ebean.app", p.getHikariDataSource().getPoolName());
    assertEquals("jdbc:h2:mem:tests7", p.getHikariDataSource().getJdbcUrl());
    assertEquals("foo", p.getHikariDataSource().getUsername());
    assertEquals("bar", p.getHikariDataSource().getPassword());
    assertFalse(p.getHikariDataSource().isAutoCommit());
    assertEquals(51, p.getHikariDataSource().getMaximumPoolSize());
    assertEquals(19, p.getHikariDataSource().getMinimumIdle());
    assertEquals(7*60*1000, p.getHikariDataSource().getMaxLifetime());
    p.shutdown();
  }

  @Test
	void testMixProp () throws SQLException {
		SmartConfigTest.PROPERTIES.clear();
		System.getProperties().keySet().removeIf(k->trim(k).startsWith("spring."));

    Database db = DB.byName("mix");
    val ds = (HikariEbeanDataSourcePool) db.dataSource();
    HikariDataSource hds = ds.unwrap(null);
    assertEquals(-1, hds.getInitializationFailTimeout());
    assertEquals("jdbc:h2:mem:testMix", hds.getJdbcUrl());
    assertEquals(61000, hds.getIdleTimeout());
    assertEquals(31, hds.getMaximumPoolSize());
    assertEquals(7, hds.getMinimumIdle());
    Properties p = hds.getDataSourceProperties();
    String s = p.entrySet().stream().sorted(Comparator.comparing(c->c.getKey().toString())).map(Object::toString).collect(Collectors.joining("|"));
    assertEquals("NETWORK_TIMEOUT=51000|NO_UPGRADE=true|RECOVER_TEST=true", s);
    assertEquals("select 1;\r\nselect 2", hds.getConnectionInitSql());
  }

  @Test
	void testAnotherPrefix () {
    try {
      System.setProperty("ebean.hikari.prefix", "spring.datasource.testAnotherPrefix.");

      var db = DB.byName("test_spring");
      var pool = (HikariEbeanDataSourcePool) db.dataSource();
      assertEquals("jdbc:h2:mem:fromSprng", pool.getHikariDataSource().getJdbcUrl());
      assertEquals("ufoo", pool.getHikariDataSource().getUsername());
      assertEquals("pbar", pool.getHikariDataSource().getPassword());
      assertTrue(pool.getHikariDataSource().isReadOnly());
      assertEquals(54321, pool.getHikariDataSource().getMaxLifetime());

      System.setProperty("ebean.hikari.prefix.9", "");// disable spring
      System.setProperty("ebean.hikari.prefix", "quarkus.datasource.%db%");
      System.setProperty("ebean.hikari.default-Db", "");
      var dataSourcePool = (HikariEbeanDataSourcePool) new HikariEbeanConnectionPoolFactory().createPool("myQuarkusDS", new DataSourceConfig());
      assertEquals("jdbc:h2:mem:evenQuarkus", dataSourcePool.getHikariDataSource().getJdbcUrl());
      assertEquals("org.h2.Driver", dataSourcePool.getHikariDataSource().getDriverClassName());
      assertEquals("test", dataSourcePool.getHikariDataSource().getUsername());
      assertEquals("1234", dataSourcePool.getHikariDataSource().getPassword());
      assertEquals(93, dataSourcePool.getHikariDataSource().getMaximumPoolSize());

    } finally {
      Properties p = System.getProperties();
      p.remove("ebean.hikari.prefix.9");
      p.remove("ebean.hikari.prefix");
      p.remove("ebean.hikari.default-Db");
    }
  }

  @Test
	void _emptyPropertyIsNotAbsentProperty () {
    System.setProperty("fake_empty_prop", "");
    assertTrue(System.getProperties().containsKey("fake_empty_prop"));
    assertEquals("", System.getProperty("fake_empty_prop"));
    assertEquals("", System.getProperty("fake_empty_prop", "default"));
  }

  @Test
	void _checkDefaultDbProperties () {
    var ds = (HikariEbeanDataSourcePool) DB.getDefault().dataSource();
    assertEquals("jdbc:h2:mem:my_app", ds.getHikariDataSource().getJdbcUrl());// from ebean-test platform
    assertEquals("org.h2.Driver", ds.getHikariDataSource().getDriverClassName());
    assertNull(ds.getHikariDataSource().getDataSourceClassName());
    assertEquals("sa", ds.getHikariDataSource().getUsername());
    assertNull(ds.getHikariDataSource().getPassword());
    assertEquals("ebean", ds.getHikariDataSource().getPoolName());
    assertEquals(1_800_000, ds.getHikariDataSource().getMaxLifetime());// default 30 mi
    assertEquals(31, ds.getHikariDataSource().getMaximumPoolSize());
    assertEquals(2, ds.getHikariDataSource().getMinimumIdle());
    assertEquals(61_000, ds.getHikariDataSource().getIdleTimeout());
    assertNull(ds.getHikariDataSource().getConnectionInitSql());
  }

	@Test
	void testSpringCompatability () {
		SmartConfigTest.PROPERTIES.clear();
		// val prev = (Properties) System.getProperties().clone();
		var s = """
	spring.datasource.ccbbaa.type=com.zaxxer.hikari.HikariDataSource
	spring.datasource.ccbbaa.url=jdbc:h2:mem:FooBazYum
	spring.datasource.ccbbaa.username=myLogin
	spring.datasource.ccbbaa.password=mySecret
	spring.datasource.ccbbaa.hikari.maximum-pool-size=10
	spring.datasource.ccbbaa.hikari.connection-timeout=+30000
	spring.datasource.ccbbaa.hikari.data-source-properties.DB_CLOSE_ON_EXIT=true
	spring.datasource.ccbbaa.hikari.data-source-properties.NETWORK_TIMEOUT=42
	""";
		for (var line : s.split("\n")){
			line = line.trim();
			if (line.isEmpty()){ continue; }
			var keyValue = line.split("=");
			if (keyValue.length != 2){ continue; }
			System.setProperty(keyValue[0].trim(), keyValue[1].trim());
		}
		assertEquals("10", System.getProperty("spring.datasource.ccbbaa.hikari.maximum-pool-size"));
		assertEquals("jdbc:h2:mem:FooBazYum", System.getProperty("spring.datasource.ccbbaa.url"));
		assertEquals("10", SmartConfig.opt("spring.datasource.ccbbaa.hikari.maximum-pool-size"));
		assertEquals("jdbc:h2:mem:FooBazYum", SmartConfig.opt("spring.datasource.ccbbaa.url"));
		SmartConfig.propertyNames().forEach(x->{
			if (trim(x).toLowerCase().contains("spring.datasource.")){ System.out.println(x); }
		});
		assertTrue(StreamSupport.stream(SmartConfig.MICROPROFILE_CONFIG.getPropertyNames().spliterator(),false).anyMatch(x->x.startsWith("spring.datasource.ccbbaa.")));

		var db = DB.byName("CcBbAa");
		assertEquals("HikariEbeanDataSourcePool(HikariDataSource (ebean.CcBbAa))", db.dataSource().toString());
		assertEquals("CcBbAa", db.name());
		var ds = (HikariEbeanDataSourcePool) db.dataSource();
		assertEquals("ebean.CcBbAa", ds.name());
		assertEquals("jdbc:h2:mem:FooBazYum", ds.getHikariDataSource().getJdbcUrl());
		assertEquals("myLogin", ds.getHikariDataSource().getUsername());
		assertEquals("mySecret", ds.getHikariDataSource().getPassword());
		assertEquals(10, ds.getHikariDataSource().getMaximumPoolSize());
		assertEquals(30_000, ds.getHikariDataSource().getConnectionTimeout());
		assertEquals("{DB_CLOSE_ON_EXIT=true, NETWORK_TIMEOUT=42}", ds.getHikariDataSource().getDataSourceProperties().toString());
		assertNull(ds.getHikariDataSource().getDataSource());
		assertNull(ds.getHikariDataSource().getDataSourceClassName());
		assertNull(ds.getHikariDataSource().getDriverClassName());
		var sqlRow = db.sqlQuery("select 123").findOne();
		assertEquals("{123=123}", sqlRow.toString());
		assertNull(ds.getHikariDataSource().getDataSource());
		assertNull(ds.getHikariDataSource().getDataSourceClassName());
		assertNull(ds.getHikariDataSource().getDriverClassName());

		// "default" We can't replace the already created, but we can check how it could be
		System.getProperties().forEach((key, value)->{
			var propertyName = trim(key);
			if (propertyName.startsWith("spring.datasource.ccbbaa.")){
				propertyName = propertyName.replace(".ccbbaa", "");
				SmartConfigTest.PROPERTIES.put(propertyName, value.toString());
			}
		});
		assertEquals("10", SmartConfigTest.PROPERTIES.get("spring.datasource.hikari.maximum-pool-size"));
		assertEquals("jdbc:h2:mem:FooBazYum", SmartConfigTest.PROPERTIES.get("spring.datasource.url"));
		assertEquals("10", SmartConfig.opt("spring.datasource.hikari.maximum-pool-size"));
		assertEquals("jdbc:h2:mem:FooBazYum", SmartConfig.opt("spring.datasource.url"));

		// DatabaseFactory.create("db" or "")
		ds = new HikariEbeanDataSourcePool("db", new DataSourceConfig());

		assertEquals("HikariEbeanDataSourcePool(HikariDataSource (ebean))", ds.toString());
		assertEquals("ebean", ds.name());
		assertEquals("jdbc:h2:mem:FooBazYum", ds.getHikariDataSource().getJdbcUrl());
		assertEquals("myLogin", ds.getHikariDataSource().getUsername());
		assertEquals("mySecret", ds.getHikariDataSource().getPassword());
		assertEquals(31, ds.getHikariDataSource().getMaximumPoolSize());
		assertEquals(30_000, ds.getHikariDataSource().getConnectionTimeout());
		assertEquals("{DB_CLOSE_ON_EXIT=true, NETWORK_TIMEOUT=42}", ds.getHikariDataSource().getDataSourceProperties().toString());
		assertNull(ds.getHikariDataSource().getDataSource());
		assertNull(ds.getHikariDataSource().getDataSourceClassName());
		assertNull(ds.getHikariDataSource().getDriverClassName());

		// remove settings
		System.getProperties().keySet().removeIf(k->{
			val propertyName = trim(k);
			return propertyName.startsWith("spring.datasource.");
		});
		assertNull(System.getProperty("spring.datasource.ccbbaa.hikari.maximum-pool-size"));
		assertNull(System.getProperty("spring.datasource.ccbbaa.url"));
		assertNull(System.getProperty("spring.datasource.hikari.maximum-pool-size"));
		assertNull(System.getProperty("spring.datasource.url"));
	}

	@Test
	void _moreTests () throws SQLException {
		Database db = DB.byName("moreTests");
		val eds = (HikariEbeanDataSourcePool) db.dataSource();
		HikariDataSource ds = eds.unwrap(HikariDataSource.class);
		assertEquals("jdbc:h2:mem:moreTests", ds.getJdbcUrl());
		assertEquals("sa", ds.getUsername());
		assertNull(ds.getPassword());
		assertEquals("ebean.moreTests", ds.getPoolName());
		assertEquals("SELECT 2", ds.getConnectionTestQuery());
		assertEquals(301000, ds.getConnectionTimeout());
		assertEquals(32000, ds.getIdleTimeout());
		assertEquals(27, ds.getMaximumPoolSize());

		SqlRow row = db.sqlQuery("select 1 as AAa,2 as Bbb").findOne();
		assertEquals("{aaa=1, bbb=2}", row.toString());
	}
}