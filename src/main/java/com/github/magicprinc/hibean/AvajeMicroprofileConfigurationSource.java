package com.github.magicprinc.hibean;

import io.avaje.config.Configuration;
import io.avaje.config.ConfigurationSource;
import lombok.val;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 [private {@link java.util.ServiceLoader ServiceLoader} Implementation] Avaje ConfigurationSource backed by MicroProfile Config
 🤖 resources/META-INF/services/io.avaje.config.ConfigurationSource

 This class is a bridge/adapter that feeds MicroProfile Config properties into Avaje's Configuration system.

 How it works:
 - Implements Avaje's ConfigurationSource SPI interface
 - Registered via Java ServiceLoader (META-INF/services/io.avaje.config.ConfigurationSource:20)
 - load() iterates all property names from SmartConfig.MICROPROFILE_CONFIG (a SmallRyeConfig instance, which is the MicroProfile Config implementation)
 			and copies them into Avaje's Configuration via putAll()

 Why it exists:
 the HiBean library sits between MicroProfile Config (SmallRye) and Avaje Config ecosystems.
 This class ensures that values configured through MicroProfile (e.g., microprofile-config.properties, env vars, etc.) are visible to Avaje Config consumers
 — particularly Ebean/HikariCP datasource configuration — without duplicating config.

 @see io.avaje.config.ConfigurationSource
 @see io.avaje.config.CoreConfiguration#loadSources
 @see SmartConfig#MICROPROFILE_CONFIG
*/
public class AvajeMicroprofileConfigurationSource implements ConfigurationSource {
	/// @see io.avaje.config.Configuration
	@Override
	public void load (Configuration configuration) {
		val map = StreamSupport.stream(
				SmartConfig.propertyNames().spliterator(),
				false/*not parallel*/
			)
			.filter(Objects::nonNull)
			.map(key ->Map.entry(key, SmartConfig.opt(key)))
			.collect(Collectors.toMap(
				e->e.getKey().replace("\"", ""),
				Map.Entry::getValue
			));

		configuration.putAll(map);
	}
}