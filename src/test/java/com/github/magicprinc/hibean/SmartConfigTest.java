package com.github.magicprinc.hibean;

import org.junit.jupiter.api.Test;

import java.util.Objects;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 @see SmartConfig */
class SmartConfigTest {
	@Test
	void sysEnv () {
		var ebeanConfig = new Properties();
		ebeanConfig.setProperty("a", "111");
		ebeanConfig.setProperty("foo.bar.Zoo", " xXx ");

		var cfg = new SmartConfig.SmartConfigSysEnv(ebeanConfig);

		//System.out.println(cfg.getPropertyNames());

		assertTrue(cfg.getPropertyNames().size() > 10);
		assertEquals("111", cfg.getProperty("a"));
		assertEquals(" xXx ", cfg.getProperty("foo.bar.Zoo"));
		assertTrue(Objects.requireNonNull(cfg.getProperty("java.version")).length() > 3);
		assertFalse(Objects.requireNonNull(cfg.getProperty(System.getenv().keySet().iterator().next())).isEmpty());
		assertTrue(cfg.toString().startsWith("SmartConfigSysEnv:{"));
	}

	@Test
	void create () {
		assertInstanceOf(SmartConfig.SmartConfigSysEnv.class, SmartConfig.of(new Properties()));
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