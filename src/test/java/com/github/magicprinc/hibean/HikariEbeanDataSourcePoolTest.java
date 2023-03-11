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

import static com.github.magicprinc.hibean.HikariEbeanDataSourcePool.isNumeric;
import static com.github.magicprinc.hibean.HikariEbeanDataSourcePool.normValue;
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

  @Test void testProgrammicNoConfigFile () {
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
  }
}