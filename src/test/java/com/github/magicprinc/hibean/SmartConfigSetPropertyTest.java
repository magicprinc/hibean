package com.github.magicprinc.hibean;

import com.zaxxer.hikari.HikariConfig;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SmartConfigSetPropertyTest {

	static class DummyTarget {
		private int poolSize;
		private long timeoutMs;
		private String name;
		private boolean active;
		private short port;
		private int SHOUTY_VAL;
		private char[] chars;
		private int[] ints;
		private String[] strings;

		public void setPoolSize (int poolSize) { this.poolSize = poolSize; }
		public void setTimeoutMs (long timeoutMs) { this.timeoutMs = timeoutMs; }
		public void setName (String name) { this.name = name; }
		public void setActive (boolean active) { this.active = active; }
		public void setPort (short port) { this.port = port; }
		public void setSHOUTY_VAL (int v) { this.SHOUTY_VAL = v; }
		public void setChars (char[] chars) { this.chars = chars; }
		public void setInts (int[] ints) { this.ints = ints; }
		public void setStrings (String[] strings) { this.strings = strings; }

		public int getPoolSize () { return poolSize; }
		public long getTimeoutMs () { return timeoutMs; }
		public String getName () { return name; }
		public boolean isActive () { return active; }
		public short getPort () { return port; }
		public int getSHOUTY_VAL () { return SHOUTY_VAL; }
		public char[] getChars () { return chars; }
		public int[] getInts () { return ints; }
		public String[] getStrings () { return strings; }
	}

	private static List<Method> methodsOf (Object target) {
		return Arrays.asList(target.getClass().getMethods());
	}

	@Test
	void intParam () {
		DummyTarget target = new DummyTarget();
		List<Method> methods = methodsOf(target);
		assertTrue(SmartConfig.setProperty(target, "poolSize", "42", methods));
		assertEquals(42, target.getPoolSize());
	}

	@Test
	void intParamHex () {
		DummyTarget target = new DummyTarget();
		List<Method> methods = methodsOf(target);
		assertTrue(SmartConfig.setProperty(target, "poolSize", "0xFF", methods));
		assertEquals(255, target.getPoolSize());
	}

	@Test
	void intParamOctal () {
		DummyTarget target = new DummyTarget();
		List<Method> methods = methodsOf(target);
		assertTrue(SmartConfig.setProperty(target, "poolSize", "077", methods));
		assertEquals(63, target.getPoolSize());
	}

	@Test
	void longParam () {
		DummyTarget target = new DummyTarget();
		List<Method> methods = methodsOf(target);
		assertTrue(SmartConfig.setProperty(target, "timeoutMs", "30000", methods));
		assertEquals(30000L, target.getTimeoutMs());
	}

	@Test
	void longParamDuration_s () {
		DummyTarget target = new DummyTarget();
		List<Method> methods = methodsOf(target);
		assertTrue(SmartConfig.setProperty(target, "timeoutMs", "30s", methods));
		assertEquals(30000L, target.getTimeoutMs());
	}

	@Test
	void longParamDuration_ms () {
		DummyTarget target = new DummyTarget();
		List<Method> methods = methodsOf(target);
		assertTrue(SmartConfig.setProperty(target, "timeoutMs", "500ms", methods));
		assertEquals(500L, target.getTimeoutMs());
	}

	@Test
	void longParamDuration_m () {
		DummyTarget target = new DummyTarget();
		List<Method> methods = methodsOf(target);
		assertTrue(SmartConfig.setProperty(target, "timeoutMs", "5m", methods));
		assertEquals(5 * 60 * 1000L, target.getTimeoutMs());
	}

	@Test
	void longParamDuration_h () {
		DummyTarget target = new DummyTarget();
		List<Method> methods = methodsOf(target);
		assertTrue(SmartConfig.setProperty(target, "timeoutMs", "2h", methods));
		assertEquals(2 * 60 * 60 * 1000L, target.getTimeoutMs());
	}

	@Test
	void longParamDuration_d () {
		DummyTarget target = new DummyTarget();
		List<Method> methods = methodsOf(target);
		assertTrue(SmartConfig.setProperty(target, "timeoutMs", "1d", methods));
		assertEquals(24 * 60 * 60 * 1000L, target.getTimeoutMs());
	}

	@Test
	void longParamDuration_fallbackToDecode () {
		DummyTarget target = new DummyTarget();
		List<Method> methods = methodsOf(target);
		assertTrue(SmartConfig.setProperty(target, "timeoutMs", "123456", methods));
		assertEquals(123456L, target.getTimeoutMs());
	}

	@Test
	void shortParam () {
		DummyTarget target = new DummyTarget();
		List<Method> methods = methodsOf(target);
		assertTrue(SmartConfig.setProperty(target, "port", "8080", methods));
		assertEquals((short) 8080, target.getPort());
	}

	@Test
	void booleanParamTrue () {
		DummyTarget target = new DummyTarget();
		List<Method> methods = methodsOf(target);
		assertTrue(SmartConfig.setProperty(target, "active", "true", methods));
		assertTrue(target.isActive());
	}

	@Test
	void booleanParamFalse () {
		DummyTarget target = new DummyTarget();
		List<Method> methods = methodsOf(target);
		assertTrue(SmartConfig.setProperty(target, "active", "false", methods));
		assertFalse(target.isActive());
	}

	@Test
	void stringParam () {
		DummyTarget target = new DummyTarget();
		List<Method> methods = methodsOf(target);
		assertTrue(SmartConfig.setProperty(target, "name", "myPool", methods));
		assertEquals("myPool", target.getName());
	}

	@Test
	void shoutyCaseFallback () {
		DummyTarget target = new DummyTarget();
		List<Method> methods = methodsOf(target);
		assertTrue(SmartConfig.setProperty(target, "shouty_val", "99", methods));
		assertEquals(99, target.getSHOUTY_VAL());
	}

	@Test
	void propertyNotFound () {
		DummyTarget target = new DummyTarget();
		List<Method> methods = methodsOf(target);
		assertFalse(SmartConfig.setProperty(target, "noSuchProperty", "123", methods));
	}

	@Test
	void valueIsTrimmed () {
		DummyTarget target = new DummyTarget();
		List<Method> methods = methodsOf(target);
		assertTrue(SmartConfig.setProperty(target, "poolSize", "  42  ", methods));
		assertEquals(42, target.getPoolSize());
	}

	@Test
	void nullValue () {
		DummyTarget target = new DummyTarget();
		List<Method> methods = methodsOf(target);
		assertFalse(SmartConfig.setProperty(target, "name", null, methods));
	}

	@Test
	void againstHikariConfig_string () {
		HikariConfig hc = new HikariConfig();
		List<Method> methods = methodsOf(hc);
		assertTrue(SmartConfig.setProperty(hc, "poolName", "testPool", methods));
		assertEquals("testPool", hc.getPoolName());
	}

	@Test
	void againstHikariConfig_int () {
		HikariConfig hc = new HikariConfig();
		List<Method> methods = methodsOf(hc);
		assertTrue(SmartConfig.setProperty(hc, "maximumPoolSize", "10", methods));
		assertEquals(10, hc.getMaximumPoolSize());
	}

	@Test
	void againstHikariConfig_long () {
		HikariConfig hc = new HikariConfig();
		List<Method> methods = methodsOf(hc);
		assertTrue(SmartConfig.setProperty(hc, "connectionTimeout", "30000", methods));
		assertEquals(30000L, hc.getConnectionTimeout());
	}

	@Test
	void againstHikariConfig_longDuration () {
		HikariConfig hc = new HikariConfig();
		List<Method> methods = methodsOf(hc);
		assertTrue(SmartConfig.setProperty(hc, "connectionTimeout", "30s", methods));
		assertEquals(30000L, hc.getConnectionTimeout());
	}

	@Test
	void againstHikariConfig_boolean () {
		HikariConfig hc = new HikariConfig();
		List<Method> methods = methodsOf(hc);
		assertTrue(SmartConfig.setProperty(hc, "readOnly", "true", methods));
		assertTrue(hc.isReadOnly());
	}

	@Test
	void againstHikariConfig_unknownProperty () {
		HikariConfig hc = new HikariConfig();
		List<Method> methods = methodsOf(hc);
		assertFalse(SmartConfig.setProperty(hc, "ebeanServerName", "main", methods));
	}

	@Test
	void charArrayParam () {
		DummyTarget target = new DummyTarget();
		List<Method> methods = methodsOf(target);
		assertTrue(SmartConfig.setProperty(target, "chars", "hello", methods));
		assertArrayEquals(new char[]{'h', 'e', 'l', 'l', 'o'}, target.getChars());
	}

	@Test
	void intArrayParam () {
		DummyTarget target = new DummyTarget();
		List<Method> methods = methodsOf(target);
		assertTrue(SmartConfig.setProperty(target, "ints", "1,2,3", methods));
		assertArrayEquals(new int[]{1, 2, 3}, target.getInts());
	}

	@Test
	void intArrayParamHex () {
		DummyTarget target = new DummyTarget();
		List<Method> methods = methodsOf(target);
		assertTrue(SmartConfig.setProperty(target, "ints", "0xA,0xB", methods));
		assertArrayEquals(new int[]{10, 11}, target.getInts());
	}

	@Test
	void stringArrayParam () {
		DummyTarget target = new DummyTarget();
		List<Method> methods = methodsOf(target);
		assertTrue(SmartConfig.setProperty(target, "strings", "foo,bar", methods));
		assertArrayEquals(new String[]{"foo", "bar"}, target.getStrings());
	}
}
