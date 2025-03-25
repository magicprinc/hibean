package com.github.magicprinc.hibean;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.util.IsolationLevel;
import com.zaxxer.hikari.util.PropertyElf;
import io.ebean.config.DatabaseConfig;
import io.ebean.datasource.DataSourceConfig;
import io.ebean.datasource.DataSourcePool;
import io.micrometer.core.instrument.Metrics;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;

import static com.github.magicprinc.hibean.HikariEbeanConnectionPoolFactory.autoCommitOverrideEbeanConfig;
import static com.github.magicprinc.hibean.SmartConfig.alias;
import static com.github.magicprinc.hibean.SmartConfig.collectPrefixes;
import static com.github.magicprinc.hibean.SmartConfig.determineDefaultServerName;
import static com.github.magicprinc.hibean.SmartConfig.makeDatabaseNamePrefix;
import static com.github.magicprinc.hibean.SmartConfig.normValue;
import static com.github.magicprinc.hibean.SmartConfig.opt;
import static com.github.magicprinc.hibean.SmartConfig.trim;
import static io.ebean.util.CamelCaseHelper.toCamelFromUnderscore;
import static java.util.Objects.requireNonNullElseGet;

/**
 {@link DataSourcePool} implementation that uses {@link HikariDataSource}.

 <a href="https://github.com/brettwooldridge/HikariCP#gear-configuration-knobs-baby">Hikari settings</a>

 <a href="https://docs.spring.io/spring-boot/docs/current/api/org/springframework/boot/autoconfigure/jdbc/DataSourceProperties.html">Spring Boot</a>
 <pre>{@code
	spring.datasource.type=com.zaxxer.hikari.HikariDataSource
	spring.datasource.url=jdbc:mysql://localhost:3306/myDb
	spring.datasource.username=myUsername
	spring.datasource.password=myPassword
	spring.datasource.hikari.maximum-pool-size=10
	spring.datasource.hikari.connection-timeout=30000
  spring.datasource.hikari.data-source-properties.cachePrepStmts=true
 }</pre>

 @see io.ebean.datasource.pool.ConnectionPool#ConnectionPool(String, DataSourceConfig)
 @see DataSourceConfig
 @see DataSourceConfig#loadSettings(io.ebean.datasource.ConfigPropertiesHelper)
 @see DatabaseConfig#loadFromProperties()
 @see io.ebean.datasource.DataSourceBuilder.Settings

 @author a.fink
 */
@SuppressWarnings("UseOfPropertiesAsHashtable")
@Slf4j
class HikariEbeanDataSourcePool extends HikariEbeanDataSourceWrapper {
	public HikariEbeanDataSourcePool (String callerPoolName, DataSourceConfig config) {
		val defaultDatabaseName = determineDefaultServerName();// usually "db"
    val tmpTrimPoolName = trim(callerPoolName);
    val hikariPoolName = tmpTrimPoolName.isEmpty() || tmpTrimPoolName.equals(defaultDatabaseName) ? "ebean"
        : "ebean."+ tmpTrimPoolName;
		val databaseName = makeDatabaseNamePrefix(tmpTrimPoolName, defaultDatabaseName);
		Map<String,String> aliasMap = alias();
    val prefixes = collectPrefixes();
    val dst = new Properties(127);

    //1. search settings with our db_name
    filter(aliasMap, dst, databaseName, prefixes, defaultDatabaseName);
    //2. use also settings of another db_name
		for (var db : trim(dst.getProperty("copyFrom")).split("[;,]")){
			db = trim(db);
			if (!db.isEmpty()){
				log.debug("EbeanPool '{}' with database name '{}' also USES settings from '{}'", hikariPoolName, databaseName, db);
				val tmp = new Properties(127);
    	  filter(aliasMap, tmp, db, prefixes, defaultDatabaseName);
				tmp.forEach(dst::putIfAbsent);
			}
    }
		//3. add settings from another "template" db_name. They override our own settings!
		for (var db : trim(dst.getProperty("appendFrom")).split("[;,]")){
			db = trim(db);
			if (!db.isEmpty()){
				log.debug("EbeanPool '{}' with database name '{}' appends-over settings from '{}'", hikariPoolName, databaseName, db);
				filter(aliasMap, dst, db, prefixes, defaultDatabaseName);
			}
		}
		//4. load from another <hikari>.properties file == full delegation!
		String confFile = trim(dst.getProperty("confFile"));
		if (!confFile.isEmpty()){
			ds = createDataSource(new HikariConfig(confFile), hikariPoolName);
			return;
		}
    val hc = new HikariConfig();
    hc.setPoolName(hikariPoolName);// helps to know poolName during init phase
    mergeFromDataSourceConfig(hc, config);//1.ebean
    setTargetFromProperties(hc, dst);//2.hikari (overrides)
    ds = createDataSource(hc, hikariPoolName);
  }//new

  protected HikariDataSource createDataSource (HikariConfig hc, String poolName){
    try {// setupMonitoring
      hc.setMetricRegistry(Metrics.globalRegistry);
    } catch (Throwable ignore){}// no Micrometer in classPath; see also hikariConfig.setRegisterMbeans(true)
    hc.setPoolName(poolName);
    return new HikariDataSource(hc);
  }

  /**
	 sub-prefix for real-vendor-jdbc-driver settings (e.g. MSSQL statementPoolingCacheSize)
	 @see HikariConfig#addDataSourceProperty(String, Object)
	 */
  private static final String[] VENDOR_SETTINGS_PREFIX = { "datasourceproperties.", // for dataSourceProperties
		"data-source-properties.", // spring boot official
		"datasource." // Hikari way to pass driver-specific properties, see https://github.com/brettwooldridge/HikariCP/wiki/MySQL-Configuration + PropertyElf#setTargetFromProperties
	};

  /** @see PropertyElf#setTargetFromProperties*/
  static void setTargetFromProperties (HikariConfig hc, Properties p){
    p.remove("appendFrom");  p.remove("copyFrom");  p.remove("confFile");

    List<Method> methods = Arrays.asList(hc.getClass().getMethods());
    p.forEach((keyObj, value) -> {
      String key = trim(keyObj);
      String k = key.toLowerCase(Locale.ENGLISH);

      for (String prefix : VENDOR_SETTINGS_PREFIX){
        if (k.startsWith(prefix)){
          hc.addDataSourceProperty(key.substring(prefix.length()), value);
          return;// ~ break
        }
      }//else: HikariConfig.setter
      String javaPropertyName = toCamelFromUnderscore(key.replace('-', '_'));// spring.boot-key_fmt
      boolean success = setProperty(hc, javaPropertyName, value, methods);
      if (!success && !javaPropertyName.equals(key))// fallback to property_name as-is
					setProperty(hc, key, value, methods);
    });
  }
  /** TO DO keep in sync with {@link PropertyElf#setProperty} */
  static boolean setProperty (Object target, String propName, Object propValue, List<Method> methods) {
    // use the english locale to avoid the infamous turkish locale bug
    val methodName = "set" + propName.substring(0, 1).toUpperCase(Locale.ENGLISH) + propName.substring(1);
    var writeMethod = methods.stream().filter(m -> m.getName().equals(methodName) && m.getParameterCount() == 1).findFirst().orElse(null);

    if (writeMethod == null){
      val methodName2 = "set" + propName.toUpperCase(Locale.ENGLISH);
      writeMethod = methods.stream().filter(m -> m.getName().equals(methodName2) && m.getParameterCount() == 1).findFirst().orElse(null);
    }

    if (writeMethod == null){
      log.warn("Property {} does not exist on target {}", propName, target.getClass());// ~ ebean property
      return false;
    }

    try {
      Class<?> paramClass = writeMethod.getParameterTypes()[0];
      if (paramClass == int.class || paramClass == Integer.class){
        writeMethod.invoke(target, Integer.decode(propValue.toString().trim()));

      } else if (paramClass == long.class || paramClass == Long.class){
        writeMethod.invoke(target, Long.decode(propValue.toString().trim()));

      } else if (paramClass == short.class || paramClass == Short.class){
        writeMethod.invoke(target, Short.decode(propValue.toString().trim()));

      } else if (paramClass == boolean.class || paramClass == Boolean.class){
        writeMethod.invoke(target, Boolean.parseBoolean(propValue.toString().trim()));

      } else if (paramClass == String.class || paramClass == CharSequence.class){
        writeMethod.invoke(target, propValue.toString());

      } else {
        try {
          log.debug("Try to create a new instance of \"{}\"", propValue);
          writeMethod.invoke(target, Class.forName(propValue.toString()).getDeclaredConstructor().newInstance());
        } catch (InstantiationException | ClassNotFoundException | NoClassDefFoundError e){
          log.debug("Class \"{}\" not found or could not instantiate it (Default constructor) ‹ {}", propValue, e.toString());
          writeMethod.invoke(target, propValue);
        }
      }
      return true;// success
    } catch (Throwable e){
      log.error("Failed to set property {} on target {}", propName, target.getClass(), e);
      return false;
    }
  }

  protected void mergeFromDataSourceConfig (HikariConfig hc, DataSourceConfig dsc) {
    hc.setReadOnly(dsc.isReadOnly());//!
    sets(dsc.getUrl(), hc::setJdbcUrl);
    sets(dsc.getDriver(), hc::setDriverClassName);
    sets(dsc.getUsername(), hc::setUsername);
    sets(dsc.getOwnerUsername(), hc::setUsername);
    sets(dsc.getPassword(), hc::setPassword);
    sets(dsc.getOwnerPassword(), hc::setPassword);
    sets(dsc.getSchema(), hc::setSchema);

    hc.setTransactionIsolation(IsolationLevel.values()[dsc.getIsolationLevel()].toString());
		hc.setAutoCommit(requireNonNullElseGet(autoCommitOverrideEbeanConfig, dsc::isAutoCommit));//!!! ebean default autoCommit==FALSE!!!
    seti(dsc.getMinConnections(), hc::setMinimumIdle);
    seti(dsc.getMaxConnections(), hc::setMaximumPoolSize);

    sets(dsc.getHeartbeatSql(), hc::setConnectionTestQuery);
    setl(dsc.getHeartbeatFreqSecs(), s->hc.setKeepaliveTime(s * 1000L));
    setl(dsc.getHeartbeatTimeoutSeconds(), s->hc.setValidationTimeout(s * 1000L));

    setl(dsc.getLeakTimeMinutes(), m->hc.setLeakDetectionThreshold(m * 60 * 1000L));
    setl(dsc.getWaitTimeoutMillis(), hc::setConnectionTimeout);
    setl(dsc.getMaxInactiveTimeSecs(), s->hc.setIdleTimeout(s * 1000L));
    setl(dsc.getMaxAgeMinutes(), m->hc.setMaxLifetime(m * 60 * 1000L));
    if (!dsc.isFailOnStart()){
      hc.setInitializationFailTimeout(-1);// or 0 is better?
    }
    Map<String,String> cp = dsc.getCustomProperties();
    if (cp != null && !cp.isEmpty()){
      Properties p = new Properties(cp.size() * 13 / 10);
      p.putAll(cp);
      hc.setDataSourceProperties(p);
    }
    if (dsc.getInitSql() != null && !dsc.getInitSql().isEmpty())
      	sets(String.join(";\r\n", dsc.getInitSql()), hc::setConnectionInitSql);
  }
  void sets (String dataSourceConfig, Consumer<String> setter){
    String s = trim(dataSourceConfig);
    if (!s.isEmpty())//e.g: driver can't be ""
	      setter.accept(s);
  }
  void seti (int dataSourceConfig, IntConsumer setter){
    if (dataSourceConfig > 0)
      	setter.accept(dataSourceConfig);
  }
  void setl (long dataSourceConfig, LongConsumer setter){
    if (dataSourceConfig > 0)
      	setter.accept(dataSourceConfig);
  }

  /**
   * Filter very wide application.properties using the prefix and db name (if supplied) to search for the hikari properties.
   * Filter conditions - key starts with:
   * <pre>{@code
   *
   *   datasource.dbName
   *   dbName.
   *
   * }</pre>
   */
  void filter (Map<String,String> aliasMap, Properties dst, String dbName, List<String> prefixTemplates, String defaultDatabaseName){
    dbName = trim(dbName).toLowerCase(Locale.ENGLISH);// "", "db", "mycoolbase", "TooSmart."
    val db = dbName + (dbName.isEmpty() || dbName.endsWith(".") ? "" : ".");// "", "db.", "mycoolbase.", "toosmart."

		final String[] prefixes;
		// for default db we must generate both variants: spring.datasource.url and spring.datasource.db.url
		if ((dbName.isEmpty() || dbName.equals(defaultDatabaseName) || dbName.equals(defaultDatabaseName+'.')) && !defaultDatabaseName.isEmpty()){
			val set = new LinkedHashSet<String>(prefixTemplates.size() * 8 / 3 + 1);

			prefixTemplates.stream()
				.map(pre->trim(pre).toLowerCase(Locale.ENGLISH).replace("%db%", ""))
				.filter(pre->pre.length() > 2)
				.forEach(set::add);

			prefixTemplates.stream()
				.map(pre->trim(pre).toLowerCase(Locale.ENGLISH).replace("%db%", defaultDatabaseName+'.'))
				.filter(pre->pre.length() > 1)
				.forEach(set::add);

			prefixes = set.stream()
				.sorted(Comparator.comparing(String::length).reversed())
				.toArray(String[]::new);

		} else {// normal database name OR default db == ""
			prefixes = prefixTemplates.stream()
				.map(pre->trim(pre).toLowerCase(Locale.ENGLISH).replace("%db%", db))
				.filter(pre->pre.length() > 1)
				.sorted(Comparator.comparing(String::length).reversed())
				.toArray(String[]::new);
		}
		// datasource.db.url [= jdbc:h2:mem:testMix]
    for (val verbatimConfKey : SmartConfig.propertyNames()){
			val confKey = trim(verbatimConfKey).replace("\"", "");// SmallRyeConfig yaml key "foo.bar" becomes "\"foo.bar\""
			String k = confKey.toLowerCase(Locale.ENGLISH);// fullKey

			String propertyName = "";
			for (String pre : prefixes){// 1) somedb. 2) datasource.somedb.  E.g.:  db., datasource.db.
				if (k.startsWith(pre)){
					propertyName = confKey.substring(pre.length());
					k = k.substring(pre.length());
					break;// found
				}
			}
			if (propertyName.isEmpty())
					continue;// skip lines without prefix

			k = k.replace("-", "").replace("_", ""); // [datasource.db.] max-Connections → maxconnections
			String alias = trim(aliasMap.get(k));
			if (!alias.isEmpty())
					propertyName = alias; // e.g. url (ebean name) → jdbcUrl (hikari name)

			String confValue = opt(verbatimConfKey);
			if (!confValue.isEmpty())
					dst.setProperty(propertyName, normValue(confValue));
			else
					log.warn("filter: Config has property name {} without value @ {}.{}", verbatimConfKey, db, propertyName);
		}//f
  }
}