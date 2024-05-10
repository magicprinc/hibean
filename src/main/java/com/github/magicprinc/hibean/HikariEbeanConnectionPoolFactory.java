package com.github.magicprinc.hibean;

import io.ebean.datasource.DataSourceConfig;
import io.ebean.datasource.DataSourceFactory;
import io.ebean.datasource.DataSourcePool;

/**
 HikariCP backed DataSourceFactory.
 It is service loaded → Do have only one implementation in ClassPath!
 @see DataSourceFactory
 @see HikariEbeanDataSourcePool
 @see java.util.ServiceLoader
 */
public class HikariEbeanConnectionPoolFactory implements DataSourceFactory {

  @Override
  public DataSourcePool createPool (String name, DataSourceConfig config) {
    return new HikariEbeanDataSourcePool(name, config, SmartConfig.asProperties());
  }
}