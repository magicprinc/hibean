package com.github.magicprinc.hibean;

import io.ebean.datasource.DataSourceConfig;
import io.ebean.datasource.DataSourceFactory;
import io.ebean.datasource.DataSourcePool;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

/**
 [private {@link java.util.ServiceLoader ServiceLoader} Implementation] HikariCP backed {@link DataSourceFactory}.
 It is service loaded → Do have only one implementation in ClassPath!

 🤖 resources/META-INF/services/io.ebean.datasource.DataSourceFactory

 @see DataSourceFactory
 @see HikariEbeanDataSourcePool
 @see java.util.ServiceLoader
 */
public class HikariEbeanConnectionPoolFactory implements DataSourceFactory {
	/**
	 By default, Ebean pool autoCommit == false.
	 It can be overridden in config or globally here (if not null).
	 @see com.github.magicprinc.hibean.HikariEbeanDataSourceWrapper#connectionAutoCommitOverride
	*/
	@Getter @Setter static @Nullable Boolean autoCommitOverrideEbeanConfig = null;

  @Override
  public DataSourcePool createPool (String name, DataSourceConfig config) {
    return new HikariEbeanDataSourcePool(name, config);
  }
}