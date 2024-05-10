package com.github.magicprinc.hibean;

import io.ebean.datasource.DataSourceConfig;
import io.ebean.datasource.DataSourceFactory;
import io.ebean.datasource.DataSourcePool;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

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
    return new HikariEbeanDataSourcePool(name, config, asProperties());
  }

	/**
	 @see io.avaje.config.Config
	 @see io.avaje.config.Configuration
	 */
	static Map<String,String> asProperties () {
		var configuration = io.avaje.config.Config.asConfiguration();
		//configuration.reloadSources();

		Properties confProp = configuration.asProperties();
		Map<String,String> env = System.getenv();
		Properties sysProp = System.getProperties();
		var result = new LinkedHashMap<String,String>(sysProp.size()*4/3 + env.size()*4/3 + confProp.size()*4/3 + 9);

		for (var e : confProp.entrySet()){
			if (e.getKey() != null && e.getValue() != null){
				result.put(e.getKey().toString(), e.getValue().toString());
			}
		}

		for (var e : env.entrySet()){
			if (e.getKey() != null && e.getValue() != null){
				result.put(e.getKey(), e.getValue());
			}
		}

		for (var e : sysProp.entrySet()){
			if (e.getKey() != null && e.getValue() != null){
				result.put(e.getKey().toString(), e.getValue().toString());
			}
		}
		return result;
	}
}