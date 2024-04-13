package com.github.magicprinc.hibean;

import lombok.NonNull;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

/**
 @see HikariEbeanDataSourcePool
 @see io.avaje.config.Config
 @see org.eclipse.microprofile.config.Config
 */
interface SmartConfig {

	List<String> getPropertyNames ();

	String getProperty (String name);

	default String getProperty (String name, String defaultValue) {
		var value = getProperty(name);
		if (value != null) {
			return value;
		}
		return defaultValue;
	}

	static SmartConfig of (@NonNull Properties ebeanConfig) {
		try {
			Config config = ConfigProvider.getConfig();
			return new SmartConfigMicroprofile(config, ebeanConfig);
		} catch (Throwable e){// NoClassDefFoundError | IllegalStateException
			return new SmartConfigSysEnv(ebeanConfig);
		}
	}


  class SmartConfigMicroprofile implements SmartConfig {
		private final Config config;
		private final Properties ebeanConfig;

		public SmartConfigMicroprofile (@NonNull Config config, Properties ebeanConfig) {
			this.config = config;  this.ebeanConfig = ebeanConfig;
		}//new

		@Override
		public String toString () {
			return "SmartConfigMicroprofile:"+config+":"+ebeanConfig;
		}

		@Override
		@Nullable	public String getProperty (String name) {
			var value = config.getOptionalValue(name, String.class);
			if (value != null && value.isPresent()){
				return value.get();
			}
			return ebeanConfig.getProperty(name);
		}

		@Override
		public List<String> getPropertyNames () {
			var names = new ArrayList<String>(ebeanConfig.size() + 127);

			names.addAll(ebeanConfig.keySet().stream().filter(Objects::nonNull).map(Object::toString).toList());

			config.getPropertyNames().forEach(names::add);

			return names;
		}
	}//SmartConfigMicroprofile

	class SmartConfigSysEnv implements SmartConfig {
		private final Properties ebeanConfig;

		public SmartConfigSysEnv (Properties ebeanConfig) {
			this.ebeanConfig = ebeanConfig;
		}//new

		@Override
		public String toString () {
			return "SmartConfigSysEnv:"+ebeanConfig;
		}

		@Override
		@Nullable	public String getProperty (String name) {
			var value = System.getProperty(name);
			if (value != null){
				return value;
			}

			value = System.getenv(name);
			if (value != null){
				return value;
			}

			return ebeanConfig.getProperty(name);
		}

		@Override
		public List<String> getPropertyNames () {
			var names = new ArrayList<String>(System.getenv().size() + System.getProperties().size() + ebeanConfig.size());

			names.addAll(ebeanConfig.keySet().stream().filter(Objects::nonNull).map(Object::toString).toList());

			names.addAll(System.getenv().keySet());

			names.addAll(System.getProperties().keySet().stream().filter(Objects::nonNull).map(Object::toString).toList());

			return names;
		}
	}//SmartConfigSysEnv
}