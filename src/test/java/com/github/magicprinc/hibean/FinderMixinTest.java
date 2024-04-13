package com.github.magicprinc.hibean;

import io.ebean.Model;
import org.apiguardian.api.API;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 @see FinderMixin
 @see io.ebean.Finder
*/
public class FinderMixinTest {

	@API(status = API.Status.INTERNAL)
	private static final class Example<ID,X> extends Model {
	}

	@Test
	void classNames () {
		var e = "com.github.magicprinc.hibean.FinderMixinTest.Example";
		var e$ = "com.github.magicprinc.hibean.FinderMixinTest$Example";
		assertEquals("class "+e$, Example.class.toString());
		assertEquals(e$, Example.class.getName());
		assertEquals(e$, Example.class.getTypeName());
		assertEquals(e, Example.class.getCanonicalName());
		// private static final class com.github.magicprinc.hibean.FinderMixinTest$Example<ID,X>
		assertEquals("private static final class "+ e$ +"<ID,X>", Example.class.toGenericString());

		var t = "com.github.magicprinc.hibean.FinderMixinTest";
		assertEquals("class "+t, getClass().toString());
		assertEquals(t, getClass().getName());
		assertEquals(t, getClass().getTypeName());
		assertEquals(t, getClass().getCanonicalName());
		// public class com.github.magicprinc.hibean.FinderMixinTest
		assertEquals("public class "+ t, getClass().toGenericString());
	}
}