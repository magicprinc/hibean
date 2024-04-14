package com.github.magicprinc.hibean.example;


import com.github.magicprinc.hibean.FinderMixin;
import io.ebean.DB;
import io.ebean.datasource.DataSourceConfig;
import io.ebean.util.CamelCaseHelper;
import org.junit.Test;

import java.time.LocalDate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;


/**
 https://github.com/ebean-orm-examples/example-springboot

 @see Customer
 @see BaseDomain
 @see io.ebean.Model
 @see DataSourceConfig#loadSettings(io.ebean.datasource.ConfigPropertiesHelper)
 */
public class CustomerTest {

  @Test
  public void saveAndFind() {
    Customer customer = new Customer("Hello Rob");
    customer.startDate(LocalDate.now())
			.comments("What is this good for?");

    customer.save();

    assertNotNull(customer.id());

    Customer found = DB.find(Customer.class).where().idEq(customer.id()).findOne();

    assertNotNull(found);
    assertEquals(found.id(), customer.id());
    assertEquals(found.name(), customer.name());

		Customer f1 = found.finder().query().where().idEq(customer.id()).findOne();
		assertNotNull(f1);
		assertEquals(customer.id(), f1.id());
		assertEquals(customer.name(), f1.name());

		Customer f2 = FinderMixin.finder(Customer.class).query().where().idEq(customer.id()).findOne();
		assertNotNull(f2);
		assertEquals(customer.id(), f2.id());
		assertEquals(customer.name(), f2.name());

		System.out.println(FinderMixin.FINDER_CACHE);
		assertEquals(1, FinderMixin.FINDER_CACHE.size());
	}

  @Test
	public void misc () {
    assertEquals("mama-mylaRamuOk", CamelCaseHelper.toCamelFromUnderscore("Mama-myla_Ramu_ok"));
    assertEquals("niceDbColName", CamelCaseHelper.toCamelFromUnderscore("nice_db_col_name"));
    assertEquals("niceDbColName", CamelCaseHelper.toCamelFromUnderscore("NICE_DB_COL_NAME"));
  }
}