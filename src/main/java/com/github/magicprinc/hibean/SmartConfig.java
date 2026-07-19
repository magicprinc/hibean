package com.github.magicprinc.hibean;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.util.PropertyElf;
import io.smallrye.config.SmallRyeConfig;
import lombok.val;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jspecify.annotations.Nullable;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.*;

/**
 @see HikariEbeanDataSourcePool
 @see org.eclipse.microprofile.config.Config
 */
interface SmartConfig {
	org.eclipse.microprofile.config.Config MICROPROFILE_CONFIG = ConfigProvider.getConfig();

	/// get all MicroProfile properties = conf keys
	static Iterable<String> propertyNames () {
		try {
			return ((SmallRyeConfig) MICROPROFILE_CONFIG).getLatestPropertyNames();// reload and get recent
		} catch (Throwable ignored){
			return MICROPROFILE_CONFIG.getPropertyNames();
		}
	}

	/** Property name aliases: ebean property name → hikari property name. E.g. url → jdbcUrl*/
	static Map<String,String> alias () {
		try {
			InputStream is = SmartConfig.class.getResourceAsStream("/ebean/ehpalias.properties");
			if (is == null){ return Collections.emptyMap(); }// can't be! There is the file!

			try (is){
				val p = new Properties();
				p.load(new InputStreamReader(is, UTF_8));
				val m = new HashMap<String,String>(p.size()*4/3+11);

				for (var e : p.entrySet()){
					m.put(stripKey(e.getKey()), trim(e.getValue()));
				}
				return m;
			}

		} catch (Throwable ignore){
			LoggerFactory.getLogger(SmartConfig.class).warn("Failed to load ebean alias properties file: /ebean/ehpalias.properties", ignore);
			return Collections.emptyMap();
		}
	}

	/** trim, lowerCase(EN), remove - and _ */
	static String stripKey (@Nullable Object key){
		return trim(key).toLowerCase(Locale.ENGLISH).replace("-", "").replace("_", "");
	}

	static String trim (@Nullable Object s) {
		return s == null ? ""
				: s.toString().trim().strip();
	}

	/** Is "+1,000_000.17" numeric? 0x is not supported by ConfigPropertiesHelper.getInt */
	static boolean isNumeric (CharSequence s) {
		int digits = 0;
		for (int i = 0, len = s.length(); i < len; i++){
			int c = s.charAt(i);
			if (Character.isDigit(c)){
				digits++;
				continue;
			}
			boolean numeric = Character.isSpaceChar(c) || Character.isWhitespace(c)
				|| c == '+' || c == '-' || c == '.' || c == ',' || c == '_' || c == 'e' || c == 'E';

			if (!numeric){ return false; }
		}//f
		return digits > 0;
	}

	/** 1) NFC 2) is numeric → extra cleanup */
	static String normValue (@Nullable Object value) {
		if (value == null){ return ""; }
		String s = Normalizer.normalize(value.toString(), Normalizer.Form.NFC);

		return isNumeric(s) ? SPACE_AND_UNDERSCORE.matcher(s.trim().strip()).replaceAll("")
				: s;
	}
	Pattern SPACE_AND_UNDERSCORE = Pattern.compile("[\\s_]", Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CHARACTER_CLASS);

	/**
	 Determine and return the default server name checking system environment variables and then global properties.
	 @see io.ebean.DbPrimary#determineDefaultServerName
	*/
	static String determineDefaultServerName () {
		String defaultServerName = trim(System.getProperty("ebean_db", System.getenv("EBEAN_DB")));
		if (!defaultServerName.isEmpty())
				return defaultServerName;

		defaultServerName = opt("datasource.default");
		if (defaultServerName.isEmpty())
				defaultServerName = opt("ebean.default.datasource", "db");

		return defaultServerName;
	}

	static String opt (String confKey, String defaultValue) {
		Optional<String> o = MICROPROFILE_CONFIG.getOptionalValue(confKey, String.class);
		if (o.isEmpty())
				return defaultValue;
		String s = trim(o.get());
		return s.isEmpty() ? defaultValue
				: s;
	}

	static String opt (String confKey) {
		return opt(confKey, "");
	}

	/**
	 Default database has no name: "": What prefix should we use in config to filter/find its settings (default: db.)
	 If you choose empty "" name: it will disable "db_name.property_name" keys.
	*/
	static String makeDatabaseNamePrefix (String trimCallerPoolName, String defaultDatabaseName) {
		if (trimCallerPoolName.isEmpty() || trimCallerPoolName.equals(defaultDatabaseName)){// default database
			String db = opt("ebean.hikari.defaultdb",
					opt("ebean.hikari.default-db",
							opt("ebean.hikari.defaultDb")));

			return db.isEmpty() ? defaultDatabaseName +'.'// default prefix for default database (usually db)
					: db + '.';
		} else // named database e.g: my-DB_Name
				return trimCallerPoolName +'.';// e.g: my-db_name.
	}

	/** Prefixes to filter our (hikari) property names. Default: db. → datasource.db → spring.datasource.db.hikari. */
	static List<String> collectPrefixes (){
		val p = new String[16];
		p[1] = "%db%";
		p[3] = "datasource.%db%";
		p[5] = "spring.datasource.%db%hikari.";
		p[7] = opt("ebean.hikari.prefix");
		p[9] = "spring.datasource.%db%";
		//p[11] = "quarkus.datasource.%db%";

		for (int i=0; i<p.length; i++){
			String key = "ebean.hikari.prefix."+ Integer.toHexString(i);// .0..f
			p[i] = trim(opt(key, p[i])).toLowerCase(Locale.ENGLISH);
		}
		return Arrays.stream(p)
			.map(SmartConfig::trim)
			.filter(pre->pre.length() > 1)
			.sorted(Comparator.comparing(String::length).reversed())
			.toList();
	}


	/// TODO keep in sync with {@link PropertyElf#setProperty} !! 🔥
	/// Reflection-based setter that maps configuration property names to actual JavaBean setters on a target object (typically [HikariConfig]).
	/// It's called from [#setTargetFromProperties]
	///
	/// Resolution logic:
	/// 1. Converts the property name to set + CapitalizedName (e.g: maximumPoolSize → setMaximumPoolSize)
	/// 2. If no method found, tries a SHOUTY_CASE fallback: setMAXIMUMPOOLSIZE (line 163–164) — for historical HikariCP compatibility
	/// 3. If still no match, logs a warning and returns false — the property is silently ignored (typically an Ebean property, not Hikari)
	///
	/// Type coercion (lines 175–204): inspects the setter's parameter type and converts the string value accordingly:
	/// - int/Integer → Integer.decode(value) (supports hex 0xFF, octal 077)
	/// - long/Long → Long.decode(value)
	/// - short/Short → Short.decode(value)
	/// - boolean/Boolean → Boolean.parseBoolean(value)
	/// - String/CharSequence → raw string
	/// - Anything else → tries Class.forName(value).newInstance() first (useful for enum/class-typed properties like IsolationLevel), falls back to passing the raw string value
	static boolean setProperty (Object target, String propName, Object propValue, List<Method> methods) {
		// use the english locale to avoid the infamous turkish locale bug
		val methodName = "set" + propName.substring(0, 1).toUpperCase(Locale.ENGLISH) + propName.substring(1);
		var writeMethod = methods.stream().filter(m -> m.getName().equals(methodName) && m.getParameterCount() == 1).findFirst().orElse(null);

		if (writeMethod == null){
			val methodName2 = "set" + propName.toUpperCase(Locale.ENGLISH);
			writeMethod = methods.stream().filter(m -> m.getName().equals(methodName2) && m.getParameterCount() == 1).findFirst().orElse(null);
		}

		if (writeMethod == null){
			LoggerFactory.getLogger(SmartConfig.class).warn("Property {} does not exist on target {}", propName, target.getClass());// ~ ebean property
			return false;
		}

		try {
			Class<?> paramClass = writeMethod.getParameterTypes()[0];
			String value = trim(propValue);
			if (paramClass == int.class || paramClass == Integer.class){
				writeMethod.invoke(target, Integer.decode(value));

			}
			else if (paramClass == long.class || paramClass == Long.class){
				writeMethod.invoke(target, parseDuration(value).map(Duration::toMillis).orElseGet(() -> Long.decode(value)));

			}
			else if (paramClass == short.class || paramClass == Short.class){
				writeMethod.invoke(target, Short.decode(value));

			}
			else if (paramClass == boolean.class || paramClass == Boolean.class){
				writeMethod.invoke(target, Boolean.parseBoolean(value));
			}
			else if (paramClass.isArray() && char.class.isAssignableFrom(paramClass.getComponentType())) {
				writeMethod.invoke(target, value.toCharArray());
			}
			else if (paramClass.isArray() && int.class.isAssignableFrom(paramClass.getComponentType())) {
				writeMethod.invoke(target, parseIntArray(value));
			}
			else if (paramClass.isArray() && String.class.isAssignableFrom(paramClass.getComponentType())) {
				writeMethod.invoke(target, new Object[]{parseStringArray(value)});
			}
			else if (paramClass == String.class || paramClass == CharSequence.class){
				writeMethod.invoke(target, propValue.toString());

			}
			else {
				try {
					LoggerFactory.getLogger(SmartConfig.class).debug("Try to create a new instance of \"{}\"", propValue);
					writeMethod.invoke(target, Class.forName(propValue.toString()).getDeclaredConstructor().newInstance());

				} catch (InstantiationException | ClassNotFoundException | NoClassDefFoundError e){
					LoggerFactory.getLogger(SmartConfig.class).debug("Class \"{}\" not found or could not instantiate it (Default constructor) ‹ {}", propValue, e.toString());
					writeMethod.invoke(target, propValue);
				}
			}
			return true;// success

		} catch (Throwable e){
			LoggerFactory.getLogger(SmartConfig.class).error("Failed to set property {} on target {}", propName, target.getClass(), e);
			return false;
		}
	}

	int[] EMPTY_INTS = new int[0];
	String[] EMPTY_STRINGS = new String[0];
	char ESCAPE_CHAR = '\\';
	char SEPARATOR_CHAR = ',';
	Pattern DURATION_PATTERN = Pattern.compile("^(?<number>\\d+)(?<unit>ms|s|m|h|d)$");

	private static int[] parseIntArray (String value) {
		if (value == null || value.isEmpty()){
			return EMPTY_INTS;
		}

		String[] split = value.split(",");
		var intArray = new int[split.length];
		for (int i = 0; i < split.length; i++){
			intArray[i] = Integer.decode(split[i]);
		}
		return intArray;
	}

	private static String[] parseStringArray(String value) {
		if (value == null || value.isEmpty()){
			return EMPTY_STRINGS;
		}

		var resultList = new ArrayList<String>();
		var inEscape = false;
		var currentField = new StringBuilder();
		for (char c : value.toCharArray()){
			if (inEscape){
				currentField.append(c);
				inEscape = false;
			}
			else if (c == ESCAPE_CHAR) {
				inEscape = true;
			}
			else if (c == SEPARATOR_CHAR) {
				resultList.add(currentField.toString());
				currentField.setLength(0);
			}
			else {
				currentField.append(c);
			}
		}

		if (inEscape) {
			throw new IllegalArgumentException(String.format("Unterminated escape sequence in property value: %s", value));
		}

		resultList.add(currentField.toString());
		return resultList.toArray(EMPTY_STRINGS);
	}

	private static Optional<Duration> parseDuration (String value) {
		var matcher = DURATION_PATTERN.matcher(value);
		if (matcher.matches()){
			long number = Long.parseLong(matcher.group("number"));
			String unit = matcher.group("unit");
			return switch (unit){
				case "ms" -> Optional.of(Duration.ofMillis(number));
				case "s"  -> Optional.of(Duration.ofSeconds(number));
				case "m"  -> Optional.of(Duration.ofMinutes(number));
				case "h"  -> Optional.of(Duration.ofHours(number));
				case "d"  -> Optional.of(Duration.ofDays(number));
				default   -> throw new IllegalStateException(String.format("Could not match unit, got %s (from given value %s)", unit, value));
			};
		}
		else {
			return Optional.empty();
		}
	}
}