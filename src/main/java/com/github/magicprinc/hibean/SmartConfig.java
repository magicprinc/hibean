package com.github.magicprinc.hibean;

import io.ebean.datasource.DataSourceConfig;
import org.eclipse.microprofile.config.ConfigProvider;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 @see HikariEbeanDataSourcePool
 @see org.eclipse.microprofile.config.Config
 */
interface SmartConfig {

	/**
	 Merge initial avaje Config properties with MicroProfile Config (if present)
	 */
	static Map<String,String> of (Map<String,String> avajeConfig) {
		try {
			org.eclipse.microprofile.config.Config config = ConfigProvider.getConfig();

			var merge = new LinkedHashMap<>(avajeConfig);

			for (var key : config.getPropertyNames()){
				config.getOptionalValue(key, String.class)
						.ifPresent(s->merge.put(key, s));
			}
			merge.put("org.eclipse.microprofile.config.Config", config.getClass().getName());// to detect in test: what was used
			return merge;

		} catch (Throwable e){// NoClassDefFoundError | IllegalStateException
			return avajeConfig;
		}
	}//new

	/**
	 Get all avaje Config properties as Map key→value (starting set of properties)
	 @see HikariEbeanConnectionPoolFactory#createPool(String, DataSourceConfig)
	 @see io.avaje.config.Config
	 @see io.avaje.config.Configuration
	 */
	static Map<String,String> asProperties () {
		var configuration = io.avaje.config.Config.asConfiguration();
		//configuration.reloadSources();

		Properties confProp = configuration.asProperties();
		Map<String,String> env = System.getenv();
		Properties sysProp = System.getProperties();
		var result = new LinkedHashMap<String,String>(sysProp.size()*4/3 + env.size()*4/3 + confProp.size()*4/3 + 9);

		for (var e : confProp.entrySet()){
			if (e.getKey() != null && e.getValue() != null){
				result.put(e.getKey().toString(), e.getValue().toString());
			}
		}//1 avaje

		for (var e : env.entrySet()){
			if (e.getKey() != null && e.getValue() != null){
				result.put(e.getKey(), e.getValue());
			}
		}//2 env

		for (var e : sysProp.entrySet()){
			if (e.getKey() != null && e.getValue() != null){
				result.put(e.getKey().toString(), e.getValue().toString());
			}
		}//3 sys prop
		return result;
	}


	/** Property name aliases: ebean property name → hikari property name. E.g. url → jdbcUrl*/
	static Map<String,String> alias () {
		try {
			InputStream is = SmartConfig.class.getResourceAsStream("/ebean/ehpalias.properties");
			if (is == null){ return Collections.emptyMap(); }// can't be! There is the file!
			try (is){
				Properties p = new Properties();
				p.load(new InputStreamReader(is, StandardCharsets.UTF_8));
				HashMap<String,String> m = new HashMap<>(p.size()+31);
				for (var e : p.entrySet()){
					m.put(stripKey(e.getKey()), trim(e.getValue()));
				}
				return m;
			}
		} catch (Throwable ignore){
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
			char c = s.charAt(i);
			if (Character.isDigit(c)){
				digits++;
				continue;
			}
			boolean numeric = Character.isSpaceChar(c) || Character.isWhitespace(c)
				|| c == '+' || c == '-' || c == '.' || c == ',' || c == '_' || c == 'e' || c == 'E';
			if (!numeric){
				return false;
			}
		}
		return digits > 0;
	}

	static String normValue (Object value) {
		if (value == null){
			return "";
		}
		String s = Normalizer.normalize(value.toString(), Normalizer.Form.NFC);

		if (isNumeric(s)){
			return SPACE_AND_UNDERSCORE.matcher(s.trim().strip()).replaceAll("");
		}
		return s;
	}
	Pattern SPACE_AND_UNDERSCORE = Pattern.compile("[\\s_]", Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CHARACTER_CLASS);
}