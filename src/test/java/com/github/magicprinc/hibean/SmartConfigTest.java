package com.github.magicprinc.hibean;

import io.smallrye.config.AbstractLocationConfigSourceLoader;
import io.smallrye.config.DotEnvConfigSourceProvider;
import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import io.smallrye.config.SmallRyeConfigProviderResolver;
import io.smallrye.config.common.MapBackedConfigSource;
import io.smallrye.config.source.yaml.YamlConfigSourceLoader;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.eclipse.microprofile.config.spi.ConfigSourceProvider;
import org.junit.jupiter.api.Test;

import javax.annotation.Nonnegative;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.StreamSupport;

import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.*;

/**
 @see SmartConfig */
@Slf4j
public class SmartConfigTest {
	public static final ConcurrentMap<String,String> PROPERTIES = new ConcurrentHashMap<>();
	static {
		SmartConfigTest.configureSmallRyeConfig();
	}

	public static void configureSmallRyeConfig () {
		val builder = new SmallRyeConfigBuilder()
			.forClassLoader(Thread.currentThread().getContextClassLoader())
			.addDefaultInterceptors()// e.g: io.smallrye.config.ExpressionConfigSourceInterceptor
			.addDefaultSources()// EnvConfigSource, SysPropConfigSource, SmallRyeConfigBuilder#META_INF_MICROPROFILE_CONFIG_PROPERTIES = META-INF/microprofile-config.properties
			.addPropertiesSources()
			.addSystemSources()

			.addDiscoveredConverters()
			.addDiscoveredInterceptors()
			.addDiscoveredSources()
			.addDiscoveredSecretKeysHandlers()
			//.addDiscoveredValidator() → явно выставляем сами, проверив, что создаётся (есть зависимости)
			.addDiscoveredCustomizers()
			.withSources(new DotEnvConfigSourceProvider())

			.withSources(new CustomizedJPropertiesYamlConfigSourceLoader())
			.withSources(JPropertiesConfigSourceProvider.of(250, "application.properties"))
			.withSources(JPropertiesConfigSourceProvider.of(312, "application-test.properties"))

			.withSources(JPropertiesConfigSourceProvider.of(260, "config/application.properties"))
			.withSources(JPropertiesConfigSourceProvider.of(250, "application.properties"))
			.withSources(JPropertiesConfigSourceProvider.of(312, "application-test.properties"))
			.withSources(new MapBackedConfigSource("SmartConfigTest.PROPERTIES", PROPERTIES, 120, false){});
		SmallRyeConfig config = builder.build();
		val resolver = new SmallRyeConfigProviderResolver();
		ConfigProviderResolver.setInstance(resolver);// replace existing (probably already initialized)
		resolver.registerConfig(config, null);
		//ConfigProviderResolver.instance().registerConfig(config, null);
	}
	static class JPropertiesConfigSourceProvider extends AbstractLocationConfigSourceLoader {
		static final String[] EXTENSIONS_PROVIDER = {"properties", "conf"};
		@Getter private static final JPropertiesConfigSourceProvider instance = new JPropertiesConfigSourceProvider();
		public static List<ConfigSource> of (@Nonnegative int ordinal, @NonNull String location) {
			try {
				return getInstance().loadConfigSources(location, ordinal);
			} catch (Throwable e){
				log.warn("Can't read properties: {}", location, e);
				return emptyList();
			}
		}
		@Override protected String[] getFileExtensions (){ return EXTENSIONS_PROVIDER; }
		@Override
		protected ConfigSource loadConfigSource (URL url, int ordinal) throws IOException {
			return new PropertiesConfigSource(url, ordinal);
		}
	}//JPropertiesConfigSourceProvider
	public static class CustomizedJPropertiesYamlConfigSourceLoader extends YamlConfigSourceLoader implements ConfigSourceProvider {
		@Override
		public List<ConfigSource> getConfigSources (ClassLoader classLoader) {
			val sources = new ArrayList<ConfigSource>(8);
			sources.addAll(loadConfigSources(new String[]{"config/application.yaml", "config/application.yml"}, 266, classLoader));
			sources.addAll(loadConfigSources(new String[]{"application.yaml", "application.yml"}, 256, classLoader));// "override" InClassPath 1) 255 2) CL only
			sources.addAll(loadConfigSources(new String[]{"application-test.yaml", "application-test.yml"}, 316, classLoader));
			// ➕ META-INF/microprofile-config.y[a]ml в InClassPath @ 110
			return sources;
		}
		@Override public String toString (){ return "YamlConfigSourceProvider"+getConfigSources(null); }
	}//CustomizedJPropertiesYamlConfigSourceLoader
	@Test
	void sysEnv () {
		SmartConfigTest.PROPERTIES.put("a", "111");
		SmartConfigTest.PROPERTIES.put("foo.bar.Zoo", " xXx ");
		//System.out.println(cfg.getPropertyNames());

		assertEquals("111", SmartConfig.opt("a"));
		assertEquals("xXx", SmartConfig.opt("foo.bar.Zoo"));

		assertEquals(" Zzz ", SmartConfig.opt(" ---Xzzz."," Zzz "));
	}

	@Test
	void _trim () {
		assertEquals("", SmartConfig.trim(null));
		assertEquals("", SmartConfig.trim(""));
		assertEquals("", SmartConfig.trim(" \r\n\t"));
		assertEquals("x y", SmartConfig.trim(" x y \n"));
		assertEquals(".", SmartConfig.trim(" . \n"));
	}

	@Test
	void _alias () {
		Map<String,String> alias = SmartConfig.alias();
		assertEquals(5, alias.size());
		assertEquals("jdbcUrl", alias.get("url"));
	}

	@Test
	void _opt () {
		PROPERTIES.clear();
		SmartConfig.propertyNames().forEach(k->assertFalse(k.contains("$tst.KEY!")));
		assertEquals("", SmartConfig.opt("$tst.KEY!"));

		PROPERTIES.put("$tst.KEY!", "");
		assertTrue(StreamSupport.stream(SmartConfig.propertyNames().spliterator(),false).anyMatch(k->k.contains("$tst.KEY!")));
		assertEquals("", SmartConfig.opt("$tst.KEY!"));

		PROPERTIES.put("$tst.KEY!", " 42 ");
		assertTrue(StreamSupport.stream(SmartConfig.propertyNames().spliterator(),false).anyMatch(k->k.contains("$tst.KEY!")));
		assertEquals("42", SmartConfig.opt("$tst.KEY!"));
		PROPERTIES.clear();
	}

	@Test
	void _prefixes () {
		PROPERTIES.clear();
		System.getProperties().keySet().removeIf(k->k.toString().startsWith("ebean."));

		PROPERTIES.put("ebean.hikari.prefix.d", "foo_%db%_bar");

		List<String> list = SmartConfig.collectPrefixes();
		assertEquals(5, list.size());
		assertEquals("[spring.datasource.%db%hikari., spring.datasource.%db%, datasource.%db%, foo_%db%_bar, %db%]", list.toString());
		PROPERTIES.clear();
	}

	@Test
	void _normValue () {
		assertEquals("", SmartConfig.normValue(null));
		assertEquals("", SmartConfig.normValue(""));
		assertEquals("+420", SmartConfig.normValue(" +4_2 0 \r"));
	}
}