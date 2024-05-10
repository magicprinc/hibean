package com.github.magicprinc.hibean;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
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
		var cfg = SmartConfig.of(ebeanConfig);
		//System.out.println(cfg.getPropertyNames());

		assertEquals(2, cfg.size());
		assertEquals("111", cfg.get("a"));
		assertEquals(" xXx ", cfg.get("foo.bar.Zoo"));
		assertEquals("{a=111, foo.bar.Zoo= xXx }", cfg.toString());

		assertEquals(" Zzz ", cfg.getOrDefault(" ---Xzzz."," Zzz "));
	}

	@Test
	void _asProperties () {
		var ebeanConfig = SmartConfig.asProperties();
		ebeanConfig.put("a", "111");
		ebeanConfig.put("foo.bar.Zoo", " xXx ");
		var cfg = SmartConfig.of(ebeanConfig);
		//System.out.println(cfg.getPropertyNames());

		assertTrue(cfg.size() > 10);
		assertEquals("111", cfg.get("a"));
		assertEquals(" xXx ", cfg.get("foo.bar.Zoo"));
		assertTrue(Objects.requireNonNull(cfg.get("java.version")).length() > 3);
		assertFalse(Objects.requireNonNull(cfg.get(System.getenv().keySet().iterator().next())).isEmpty());
		assertTrue(cfg.toString().startsWith("{"));

		assertEquals(" Zzz ", cfg.getOrDefault(" ---Xzzz."," Zzz "));
	}

	@Test
	void create () {
		assertInstanceOf(Map.class, SmartConfig.of(new HashMap<>()));
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