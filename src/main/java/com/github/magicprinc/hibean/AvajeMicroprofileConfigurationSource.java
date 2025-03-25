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

 @see io.avaje.config.ConfigurationSource
 @see io.avaje.config.CoreConfiguration#loadSources
 @see SmartConfig#MICROPROFILE_CONFIG
*/
public class AvajeMicroprofileConfigurationSource implements ConfigurationSource {
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