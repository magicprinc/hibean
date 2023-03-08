package com.github.magicprinc.hibean.example;


import io.ebean.DB;
import io.ebean.datasource.DataSourceConfig;
import io.ebean.util.CamelCaseHelper;
import org.junit.Test;

import java.time.LocalDate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;


/**
 https://github.com/ebean-orm-examples/example-springboot
 @see DataSourceConfig#loadSettings(io.ebean.datasource.ConfigPropertiesHelper)
 */
public class CustomerTest {

  @Test public void saveAndFind() {
    Customer customer = new Customer("Hello Rob");
    customer.setStartDate(LocalDate.now());
    customer.setComments("What is this good for?");

    customer.save();

    assertNotNull(customer.getId());

    Customer found = DB.find(Customer.class).where().idEq(customer.getId()).findOne();

    assertNotNull(found);
    assertEquals(found.getId(), customer.getId());
    assertEquals(found.getName(), customer.getName());
  }

  @Test public void misc () {
    assertEquals("mama-mylaRamuOk", CamelCaseHelper.toCamelFromUnderscore("Mama-myla_Ramu_ok"));
    assertEquals("niceDbColName", CamelCaseHelper.toCamelFromUnderscore("nice_db_col_name"));
    assertEquals("niceDbColName", CamelCaseHelper.toCamelFromUnderscore("NICE_DB_COL_NAME"));
  }
}