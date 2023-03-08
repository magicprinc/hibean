package com.github.magicprinc.hibean;

import com.zaxxer.hikari.HikariDataSource;
import io.ebean.DB;
import io.ebean.Database;
import org.junit.jupiter.api.Test;

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
}