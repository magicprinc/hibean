package com.github.magicprinc.hibean;

import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 @see HikariEbeanDataSourcePool
 @see org.eclipse.microprofile.config.Config
 */
interface SmartConfig {

	List<String> getPropertyNames ();

	@Nullable	String getProperty (String name);

	default String getProperty (String name, String defaultValue) {
		var value = getProperty(name);
		if (value != null) {
			return value;
		}
		return defaultValue;
	}

	static SmartConfig of (Map<String,String> avajeConfig) {
		try {
			org.eclipse.microprofile.config.Config config = ConfigProvider.getConfig();
			return new SmartConfigMicroprofile(config, avajeConfig);
		} catch (Throwable e){// NoClassDefFoundError | IllegalStateException
			return new SmartConfigAvajeConfig(avajeConfig);
		}
	}//new


	@RequiredArgsConstructor  @ToString
  class SmartConfigMicroprofile implements SmartConfig {
		private final Config config;
		private final Map<String,String> ebeanConfig;// fallback

		@Override
		@Nullable	public String getProperty (String name) {
			var value = config.getOptionalValue(name, String.class);
			if (value != null && value.isPresent()){
				return value.get();
			}
			return ebeanConfig.get(name);
		}

		@Override
		public List<String> getPropertyNames () {
			var names = new ArrayList<String>(ebeanConfig.size() + 127);

			names.addAll(ebeanConfig.keySet().stream().filter(Objects::nonNull).map(Object::toString).toList());

			config.getPropertyNames().forEach(names::add);

			return names;
		}
	}//SmartConfigMicroprofile


	@RequiredArgsConstructor  @ToString
	class SmartConfigAvajeConfig implements SmartConfig {
		final Map<String,String> ebeanConfig;

		@Override
		@Nullable	public String getProperty (String name) {
			//avaje Config.getOptional(key), Config.getNullable(name)
			return ebeanConfig.get(name);
		}

		@Override
		public List<String> getPropertyNames () {
			return ebeanConfig.keySet().stream().filter(Objects::nonNull).map(Object::toString).toList();
		}
	}//SmartConfigAvajeConfig
}