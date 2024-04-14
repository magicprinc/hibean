package com.github.magicprinc.hibean;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariConfigMXBean;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import com.zaxxer.hikari.metrics.micrometer.MicrometerMetricsTracker;
import com.zaxxer.hikari.pool.HikariPool;
import com.zaxxer.hikari.util.IsolationLevel;
import com.zaxxer.hikari.util.PropertyElf;
import io.avaje.config.Config;
import io.ebean.config.DatabaseConfig;
import io.ebean.datasource.DataSourceConfig;
import io.ebean.datasource.DataSourcePool;
import io.ebean.datasource.PoolStatus;
import io.micrometer.core.instrument.Metrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.sql.DataSource;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import static io.ebean.util.CamelCaseHelper.toCamelFromUnderscore;

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
@Slf4j  @RequiredArgsConstructor
public class HikariEbeanDataSourcePool implements DataSourcePool {

  final HikariDataSource ds;

  public HikariEbeanDataSourcePool (String callerPoolName, DataSourceConfig config) {
    String tmpTrimPoolName = trim(callerPoolName);
    var defaultDatabaseName = determineDefaultServerName();
    String hikariPoolName = tmpTrimPoolName.isEmpty() || tmpTrimPoolName.equals(defaultDatabaseName)
				? "ebean"
        : "ebean."+ tmpTrimPoolName;

		var cfg = SmartConfig.of(Config.asProperties());// System.properties overwrite file.properties
		Map<String,String> aliasMap = alias();
    val prefixes = collectPrefixes(cfg);

    Properties dst = new Properties(127);
    {//1. search settings with our db_name
      String databaseName = makeDatabaseNamePrefix(tmpTrimPoolName, defaultDatabaseName, cfg);
      filter(aliasMap, cfg, dst, databaseName, prefixes);
    }{//2. use settings of another db_name
      String copyFromDb = trim(dst.getProperty("copyFrom"));
      if (!copyFromDb.isEmpty()){// useful in tests: ^ workaround for main/test settings collisions
        dst.clear();// mix of main and test configs
        filter(aliasMap, cfg, dst, copyFromDb, prefixes);
      }
    }{//3. add settings from "template" db_name
      String[] appendFrom = trim(dst.getProperty("appendFrom")).split("[;,]");
      for (String db : appendFrom){// add settings from another db
        db = trim(db);
        if (!db.isEmpty()){
          filter(aliasMap, cfg, dst, db, prefixes);
        }
      }
    }
		//4. load from another <hikari>.properties file == full delegation!
		String confFile = trim(dst.getProperty("confFile"));
		if (!confFile.isEmpty()){
			ds = createDataSource(new HikariConfig(confFile), hikariPoolName);
			return;
		}

    HikariConfig hc = new HikariConfig();
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

  /** Default database has no name: "". What prefix should we use in config to filter/find its settings (default: db.)
   If you choose empty "" name: it will disable "db_name.property_name" keys. */
  private String makeDatabaseNamePrefix (String trimCallerPoolName, String defaultDatabaseName, SmartConfig cfg){
    if (trimCallerPoolName.isEmpty() || trimCallerPoolName.equals(defaultDatabaseName)){// default database
			String db = cfg.getProperty("ebean.hikari.defaultdb",
					cfg.getProperty("ebean.hikari.default-db",
							cfg.getProperty("ebean.hikari.defaultDb")
					));

			if (db == null){
				return defaultDatabaseName;// default prefix for default database (usually db)
			} else {
				db = trim(db);
				return db.isEmpty() ? ""
						: db + '.';
			}
    } else {// named database e.g: my-DB_Name
      return trimCallerPoolName + '.';//e.g: my-db_name.
    }
  }

  /**
   * Determine and return the default server name checking system environment variables and then global properties.
   * @see io.ebean.DbPrimary#determineDefaultServerName
   */
  private static String determineDefaultServerName () {
    String defaultServerName = trim(System.getProperty("ebean_db", System.getenv("EBEAN_DB")));
		if (!defaultServerName.isEmpty()){
			return defaultServerName;
		}

    defaultServerName = Config.getOptional("datasource.default").orElse(null);
    if (trim(defaultServerName).isEmpty()){
    	defaultServerName = Config.getOptional("ebean.default.datasource").orElse(null);
    }

    return defaultServerName == null ? "db"
     	 : trim(defaultServerName);
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
      if (!success && !javaPropertyName.equals(key)){// fallback to property_name as-is
				setProperty(hc, key, value, methods);
			}
    });
  }
  /** TO DO keep in sync with {@link PropertyElf#setProperty} */
  static boolean setProperty (Object target, String propName, Object propValue, List<Method> methods) {
    // use the english locale to avoid the infamous turkish locale bug
    var methodName = "set" + propName.substring(0, 1).toUpperCase(Locale.ENGLISH) + propName.substring(1);
    var writeMethod = methods.stream().filter(m -> m.getName().equals(methodName) && m.getParameterCount() == 1).findFirst().orElse(null);

    if (writeMethod == null) {
      var methodName2 = "set" + propName.toUpperCase(Locale.ENGLISH);
      writeMethod = methods.stream().filter(m -> m.getName().equals(methodName2) && m.getParameterCount() == 1).findFirst().orElse(null);
    }

    if (writeMethod == null) {
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
    hc.setAutoCommit(dsc.isAutoCommit());
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
    if (dsc.getInitSql() != null && !dsc.getInitSql().isEmpty()){
      sets(String.join(";\r\n", dsc.getInitSql()), hc::setConnectionInitSql);
    }
  }

  /** Property name aliases: ebean property name → hikari property name. E.g. url → jdbcUrl*/
  protected Map<String,String> alias (){
    try {
      InputStream is = getClass().getResourceAsStream("/ebean/ehpalias.properties");
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
  private String stripKey (Object key){
    return trim(key).toLowerCase(Locale.ENGLISH).replace("-", "").replace("_", "");
  }

  void sets (String dataSourceConfig, Consumer<String> setter){
    String s = trim(dataSourceConfig);
    if ( !s.isEmpty() ){// e.g. driver can't be ""
      setter.accept(s);
    }
  }
  void seti (int dataSourceConfig, IntConsumer setter){
    if ( dataSourceConfig > 0 ){
      setter.accept(dataSourceConfig);
    }
  }
  void setl (long dataSourceConfig, LongConsumer setter){
    if ( dataSourceConfig > 0 ){
      setter.accept(dataSourceConfig);
    }
  }

  static String trim (Object s){
    return s == null ? "" : s.toString().trim().strip();
  }

  /** Is "+1,000_000.17" numeric? 0x is not supported by ConfigPropertiesHelper.getInt */
  static boolean isNumeric (CharSequence s){
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
    String s = Normalizer.normalize(value.toString(), Normalizer.Form.NFC);

    if (isNumeric(s)){
      return SPACE_AND_UNDERSCORE.matcher(s.trim().strip()).replaceAll("");
    }
    return s;
  }
  private static final Pattern SPACE_AND_UNDERSCORE = Pattern.compile("[\\s_]", Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CHARACTER_CLASS);

  /** Prefixes to filter our (hikari) property names. Default: db. → datasource.db → spring.datasource.db.hikari. */
  private List<String> collectPrefixes (SmartConfig cfg){
    String[] p = new String[16];
    p[1] = "%db%";
    p[3] = "datasource.%db%";
    p[5] = "spring.datasource.%db%hikari.";
    p[7] = cfg.getProperty("ebean.hikari.prefix");
    p[9] = "spring.datasource.%db%";
    p[11] = "quarkus.datasource.%db%";

    for (int i=0; i<p.length; i++){
      String key = "ebean.hikari.prefix."+ Integer.toHexString(i);// .0..f
      p[i] = trim(cfg.getProperty(key, p[i])).toLowerCase(Locale.ENGLISH);
    }
		return Arrays.stream(p)
			.map(HikariEbeanDataSourcePool::trim)
				.filter(pre -> pre.length() > 1)
				.sorted(Comparator.comparing(String::length).reversed())
				.toList();
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
  void filter (Map<String,String> aliasMap, SmartConfig src, Properties dst, String dbName, List<String> prefixTemplates){
    dbName = trim(dbName).toLowerCase(Locale.ENGLISH);// "", "db", "myCoolBase"
    val db = dbName + (dbName.isEmpty() || dbName.endsWith(".") ? "" : ".");// "", "db.", "myCoolBase."

    val prefixes = prefixTemplates.stream()
        .map(pre -> trim(pre).toLowerCase(Locale.ENGLISH).replace("%db%", db))
        .filter(pre -> pre.length() > 1)
        .sorted(Comparator.comparing(String::length).reversed())
				.toArray(String[]::new);

		// datasource.db.url [= jdbc:h2:mem:testMix]
    for (val fullKey : src.getPropertyNames()){
			String k = fullKey.toLowerCase(Locale.ENGLISH);

			String propertyName = "";
			for (String pre : prefixes){// 1) somedb. 2) datasource.somedb.  E.g.:  db., datasource.db.
				if (k.startsWith(pre)){
					propertyName = fullKey.substring(pre.length());
					k = k.substring(pre.length());
					break;// found
				}
			}
			if (propertyName.isEmpty()){
				continue;// skip lines without prefix
			}

			k = k.replace("-", "").replace("_", ""); // [datasource.db.] max-Connections → maxconnections
			String alias = trim(aliasMap.get(k));
			if (!alias.isEmpty()){
				propertyName = alias; // e.g. url (ebean name) → jdbcUrl (hikari name)
			}

			String value = src.getProperty(fullKey);
			if (value != null){
				dst.setProperty(propertyName, normValue(value));
			} else {
				log.warn("filter: Config has property name {} without value @ {}.{}", fullKey, db, propertyName);
			}
		}
  }

  @Override public String name () { return ds.getPoolName(); }

  @Override public int size () {
    HikariPoolMXBean hPool = ds.getHikariPoolMXBean();
    return hPool.getTotalConnections();
  }

  @Override public boolean isAutoCommit () { return ds.isAutoCommit(); }

  @Override public boolean isOnline () {
    return ds.isRunning() && !ds.isClosed();
  }

  @Override public boolean isDataSourceUp () {
    return isOnline();
  }

  @Override public void online () {
    ds.getHikariPoolMXBean().resumePool();
  }

  @Override public void offline () {
    ds.getHikariPoolMXBean().suspendPool();
  }

  @Override public void shutdown () {
    ds.close();
  }

  @Override public PoolStatus status (boolean reset) {
    HikariConfigMXBean cfg = ds.getHikariConfigMXBean();
    HikariPoolMXBean pool = ds.getHikariPoolMXBean();

    return new PoolStatus() {
      @Override public int minSize () {
        return cfg.getMinimumIdle();
      }
      @Override public int maxSize () {
        return cfg.getMaximumPoolSize();
      }
      @Override public int free () {
        return pool.getIdleConnections();
      }
      @Override public int busy () {
        return pool.getActiveConnections();
      }
      @Override public int waiting () {
        return pool.getThreadsAwaitingConnection();
      }
      /** todo {@link MicrometerMetricsTracker} */
      @Override public int highWaterMark () {
        return pool.getActiveConnections();
      }
      @Override public int waitCount () {
        return 0;
      }
      @Override public int hitCount () {
        return 0;
      }
    };
  }

  @Override public SQLException dataSourceDownReason () { return null; }

  @Override public void setMaxSize (int max) {
    HikariConfigMXBean cfg = ds.getHikariConfigMXBean();
    cfg.setMaximumPoolSize(max);
  }

  /** @deprecated see {@link DataSourcePool#setWarningSize(int)} */
  @Deprecated @Override public void setWarningSize (int warningSize) {
    // 1. Deprecated 2. No similar functionality in Hikari
  }

  /** @deprecated see {@link DataSourcePool#getWarningSize()} */
  @Deprecated @Override public int getWarningSize (){ return 0; }


  @Override public Connection getConnection () throws SQLException {
    return ds.getConnection();
  }

  @Override public Connection getConnection (String username, String password) throws SQLException {
    return ds.getConnection(username, password);
  }

  @Override public PrintWriter getLogWriter () throws SQLException {
    return ds.getLogWriter();
  }

  @Override public void setLogWriter (PrintWriter out) throws SQLException {
    ds.setLogWriter(out);
  }

  @Override public void setLoginTimeout (int seconds) throws SQLException {
    ds.setLoginTimeout(seconds);
  }

  @Override public int getLoginTimeout () throws SQLException {
    return ds.getLoginTimeout();
  }

  @Override public Logger getParentLogger () throws SQLFeatureNotSupportedException {
    return ds.getParentLogger();
  }

  @SuppressWarnings("unchecked") @Override
  public <T> T unwrap (Class<T> iface) throws SQLException {
    if (iface == null || iface == HikariDataSource.class || iface == HikariConfig.class){
      return (T) ds;
    }

    if (DataSource.class.equals(iface)){
      HikariPool p = (HikariPool) ds.getHikariPoolMXBean();// hack!
      return (T) p.getUnwrappedDataSource();
    }

    return ds.unwrap(iface);
  }

  @Override public boolean isWrapperFor (Class<?> iface) throws SQLException {
    return ds.isWrapperFor(iface);
  }

	@Override	public String toString (){ return "HikariEbeanDataSourcePool:"+ds; }
}