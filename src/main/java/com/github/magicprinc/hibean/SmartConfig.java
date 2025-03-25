package com.github.magicprinc.hibean;

import io.smallrye.config.SmallRyeConfig;
import lombok.val;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jspecify.annotations.Nullable;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
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

/**
 @see HikariEbeanDataSourcePool
 @see org.eclipse.microprofile.config.Config
 */
interface SmartConfig {
	org.eclipse.microprofile.config.Config MICROPROFILE_CONFIG = ConfigProvider.getConfig();

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
				p.load(new InputStreamReader(is, StandardCharsets.UTF_8));
				val m = new HashMap<String,String>(p.size()*4/3+11);
				for (var e : p.entrySet())
						m.put(stripKey(e.getKey()), trim(e.getValue()));
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
			if (!numeric)
					return false;
		}
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
}