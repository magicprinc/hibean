package com.github.magicprinc.hibean;

import org.eclipse.microprofile.config.ConfigProvider;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 @see HikariEbeanDataSourcePool
 @see org.eclipse.microprofile.config.Config
 */
interface SmartConfig {

	static Map<String,String> of (Map<String,String> avajeConfig) {
		try {
			org.eclipse.microprofile.config.Config config = ConfigProvider.getConfig();

			var merge = new LinkedHashMap<>(avajeConfig);

			for (var key : config.getPropertyNames()){
				config.getOptionalValue(key, String.class)
						.ifPresent(s->merge.put(key, s));
			}
			merge.put("org.eclipse.microprofile.config.Config", config.getClass().getName());// to detect in test: what was used
			return merge;

		} catch (Throwable e){// NoClassDefFoundError | IllegalStateException
			return avajeConfig;
		}
	}//new

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