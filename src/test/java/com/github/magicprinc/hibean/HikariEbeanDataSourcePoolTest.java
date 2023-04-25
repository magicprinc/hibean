package com.github.magicprinc.hibean;

import com.zaxxer.hikari.HikariDataSource;
import io.ebean.DB;
import io.ebean.Database;
import io.ebean.datasource.DataSourceConfig;
import io.ebean.datasource.DataSourceFactory;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.Properties;

import static com.github.magicprinc.hibean.HikariEbeanDataSourcePool.isNumeric;
import static com.github.magicprinc.hibean.HikariEbeanDataSourcePool.normValue;
import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.*;

class HikariEbeanDataSourcePoolTest {

  @Test void numeric () {
    assertFalse(isNumeric(""));
    assertFalse(isNumeric(" _"));

    assertTrue(isNumeric("42"));
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
    assertTrue(ds instanceof HikariDataSource);
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

  @Test void testNoConfigFile () {
    DataSourceConfig config = new DataSourceConfig()
        .setUrl("jdbc:h2:mem:tests7")
        .setUsername("foo")
        .setPassword("bar")
        .setAutoCommit(false)
        .setMaxConnections(51)
        .setMinConnections(19)
        .setMaxAgeMinutes(7);
    DataSource pool = DataSourceFactory.create("app", config);

    assertTrue(pool instanceof HikariEbeanDataSourcePool);
    var p = (HikariEbeanDataSourcePool) pool;
    assertEquals("ebean.app", p.name());
    assertEquals("ebean.app", p.ds.getPoolName());
    assertEquals("jdbc:h2:mem:tests7", p.ds.getJdbcUrl());
    assertEquals("foo", p.ds.getUsername());
    assertEquals("bar", p.ds.getPassword());
    assertFalse(p.ds.isAutoCommit());
    assertEquals(51, p.ds.getMaximumPoolSize());
    assertEquals(19, p.ds.getMinimumIdle());
    assertEquals(7*60*1000, p.ds.getMaxLifetime());
    p.shutdown();
  }

  @Test void testMixProp () throws SQLException {
    Database db = DB.byName("mix");
    HikariEbeanDataSourcePool ds = (HikariEbeanDataSourcePool) db.dataSource();
    HikariDataSource hds = ds.unwrap(null);
    assertEquals(-1, hds.getInitializationFailTimeout());
    assertEquals("jdbc:h2:mem:testMix", hds.getJdbcUrl());
    assertEquals(61000, hds.getIdleTimeout());
    assertEquals(31, hds.getMaximumPoolSize());
    assertEquals(7, hds.getMinimumIdle());
    Properties p = hds.getDataSourceProperties();
    String s = p.entrySet().stream().sorted(Comparator.comparing(c->c.getKey().toString())).map(Object::toString).collect(joining("|"));
    assertEquals("NETWORK_TIMEOUT=51000|NO_UPGRADE=true|RECOVER_TEST=true", s);
    assertEquals("select 1;\r\nselect 2", hds.getConnectionInitSql());
  }


  @Test void testAnotherPrefix () {
    try {
      System.setProperty("ebean.hikari.prefix", "spring.datasource.");

      var db = DB.byName("test_spring");
      var pool = (HikariEbeanDataSourcePool) db.dataSource();
      assertEquals("jdbc:h2:mem:fromSprng", pool.ds.getJdbcUrl());
      assertEquals("ufoo", pool.ds.getUsername());
      assertEquals("pbar", pool.ds.getPassword());
      assertTrue(pool.ds.isReadOnly());
      assertEquals(54321, pool.ds.getMaxLifetime());

      System.setProperty("ebean.hikari.prefix.9", "");// disable spring
      System.setProperty("ebean.hikari.prefix", "quarkus.datasource.");
      System.setProperty("ebean.hikari.default-Db", "");
      var dataSourcePool = (HikariEbeanDataSourcePool) new HikariEbeanConnectionPoolFactory().createPool(null, new DataSourceConfig());
      assertEquals("jdbc:h2:mem:evenQuarkus", dataSourcePool.ds.getJdbcUrl());
      assertEquals("org.h2.Driver", dataSourcePool.ds.getDriverClassName());
      assertEquals("test", dataSourcePool.ds.getUsername());
      assertEquals("1234", dataSourcePool.ds.getPassword());
      assertEquals(93, dataSourcePool.ds.getMaximumPoolSize());

    } finally {
      Properties p = System.getProperties();
      p.remove("ebean.hikari.prefix");
      p.remove("ebean.hikari.default-Db");
    }
  }


  @Test void _emptyPropertyIsNotAbsentProperty () {
    System.setProperty("fake_empty_prop", "");
    assertTrue(System.getProperties().containsKey("fake_empty_prop"));
    assertEquals("", System.getProperty("fake_empty_prop"));
    assertEquals("", System.getProperty("fake_empty_prop", "default"));
  }


  @Test void _checkDefaultDbProperties () {
    var ds = (HikariEbeanDataSourcePool) DB.getDefault().dataSource();
    assertEquals("jdbc:h2:mem:my_app", ds.ds.getJdbcUrl());// from ebean-test platform
    assertEquals("org.h2.Driver", ds.ds.getDriverClassName());
    assertNull(ds.ds.getDataSourceClassName());
    assertEquals("sa", ds.ds.getUsername());
    assertNull(ds.ds.getPassword());
    assertEquals("ebean", ds.ds.getPoolName());
    assertEquals(1_800_000, ds.ds.getMaxLifetime());// default 30 mi
    assertEquals(31, ds.ds.getMaximumPoolSize());
    assertEquals(2, ds.ds.getMinimumIdle());
    assertEquals(61_000, ds.ds.getIdleTimeout());
    assertNull(ds.ds.getConnectionInitSql());
  }
}