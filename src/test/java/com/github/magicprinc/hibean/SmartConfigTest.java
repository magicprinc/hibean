package com.github.magicprinc.hibean;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 @see SmartConfig */
class SmartConfigTest {
	@Test
	void sysEnv () {
		var ebeanConfig = new LinkedHashMap<String,String>();
		ebeanConfig.put("a", "111");
		ebeanConfig.put("foo.bar.Zoo", " xXx ");
		var cfg = new SmartConfig.SmartConfigAvajeConfig(ebeanConfig);
		//System.out.println(cfg.getPropertyNames());

		assertEquals(2, cfg.getPropertyNames().size());
		assertEquals("111", cfg.getProperty("a"));
		assertEquals(" xXx ", cfg.getProperty("foo.bar.Zoo"));
		assertEquals("SmartConfig.SmartConfigAvajeConfig(ebeanConfig={a=111, foo.bar.Zoo= xXx })", cfg.toString());

		assertEquals(" Zzz ", cfg.getProperty(" ---Xzzz."," Zzz "));
	}

	@Test
	void _asProperties () {
		var ebeanConfig = HikariEbeanConnectionPoolFactory.asProperties();
		ebeanConfig.put("a", "111");
		ebeanConfig.put("foo.bar.Zoo", " xXx ");
		var cfg = new SmartConfig.SmartConfigAvajeConfig(ebeanConfig);
		//System.out.println(cfg.getPropertyNames());

		assertTrue(cfg.getPropertyNames().size() > 10);
		assertEquals("111", cfg.getProperty("a"));
		assertEquals(" xXx ", cfg.getProperty("foo.bar.Zoo"));
		assertTrue(Objects.requireNonNull(cfg.getProperty("java.version")).length() > 3);
		assertFalse(Objects.requireNonNull(cfg.getProperty(System.getenv().keySet().iterator().next())).isEmpty());
		assertTrue(cfg.toString().startsWith("SmartConfig.SmartConfigAvajeConfig(ebeanConfig={"));

		assertEquals(" Zzz ", cfg.getProperty(" ---Xzzz."," Zzz "));
	}

	@Test
	void create () {
		assertInstanceOf(SmartConfig.SmartConfigAvajeConfig.class, SmartConfig.of(new HashMap<>()));
	}

//	@Test
//	void microprofile () {
//		var ebeanConfig = new Properties();
//		ebeanConfig.setProperty("a", "111");
//		ebeanConfig.setProperty("foo.bar.Zoo", " xXx ");
//
//		var cfg = new SmartConfig.SmartConfigMicroprofile(new SmallRyeConfigProviderResolver().getConfig(), ebeanConfig);
//
//		//System.out.println(cfg.getPropertyNames());
//
//		assertTrue(cfg.getPropertyNames().size() > 10);
//		assertEquals("111", cfg.getProperty("a"));
//		assertEquals(" xXx ", cfg.getProperty("foo.bar.Zoo"));
//		assertTrue(Objects.requireNonNull(cfg.getProperty("java.version")).length() > 3);
//		assertFalse(Objects.requireNonNull(cfg.getProperty(System.getenv().keySet().iterator().next())).isEmpty());
//		assertTrue(cfg.toString().startsWith("SmartConfigMicroprofile:io.smallrye.config.SmallRyeConfig@"));
//	}
}