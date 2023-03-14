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
import org.slf4j.LoggerFactory;

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

 @see io.ebean.datasource.pool.ConnectionPool#ConnectionPool(String, DataSourceConfig)
 @see DataSourceConfig
 @see DataSourceConfig#loadSettings(io.ebean.datasource.ConfigPropertiesHelper)
 @see DatabaseConfig#loadFromProperties()

 @author a.fink
 */
public class HikariEbeanDataSourcePool implements DataSourcePool {

  final HikariDataSource ds;

  public HikariEbeanDataSourcePool (HikariDataSource ds) {
    this.ds = ds;
  }//new

  public HikariEbeanDataSourcePool (String poolName, DataSourceConfig config) {
    poolName = trim(poolName);
    Map<String,String> aliasMap = alias();

    Properties configProperties = Config.asProperties();
    Properties sysProp = System.getProperties();
    Properties dst = new Properties(configProperties.size() + 31);

    filter(aliasMap, configProperties,  dst, poolName);
    filter(aliasMap, sysProp, dst, poolName);

    String copyFromDb = trim(dst.getProperty("copyFrom"));
    if (!copyFromDb.isEmpty()){// useful in tests: ^ workaround for main/test settings collisions
      dst.clear();// mix of main and test configs
      filter(aliasMap, configProperties,  dst, copyFromDb);
      filter(aliasMap, sysProp, dst, copyFromDb);
    }
    String[] appendFrom = trim(dst.getProperty("appendFrom")).split("[;,]");
    for (String db : appendFrom){// add settings from another db
      db = trim(db);
      if (!db.isEmpty()){
        filter(aliasMap, configProperties,  dst, db);
        filter(aliasMap, sysProp, dst, db);
      }
    }

    String confFile = trim(dst.getProperty("confFile"));
    if (!confFile.isEmpty()){// load from another ^ <hikari>.properties file:  full delegation!
      ds = createDataSource(new HikariConfig(confFile), poolName);
      return;
    }

    HikariConfig hc = new HikariConfig();
    String propertyNames = normValue(trim(dst.getProperty("propertyNames")))
        .replace("-","").replace("then","").replace("only","").replace("than","");
    // hikari-ebean, ebean-hikari (default), hikari, ebean
    if ("hikari".equalsIgnoreCase(propertyNames)){
      setTargetFromProperties(hc, dst);
      ds = createDataSource(hc, poolName);// only hikari property names are used

    } else if ("hikariebean".equalsIgnoreCase(propertyNames)){
      setTargetFromProperties(hc, dst);//1. hikari
      mergeFromDataSourceConfig(hc, config);//2. ebean (overrides)
      ds = createDataSource(hc, poolName);

    } else if ("ebean".equalsIgnoreCase(propertyNames)){
      mergeFromDataSourceConfig(hc, config);
      ds = createDataSource(hc, poolName);

    } else {// everything else: ebean-hikari
      mergeFromDataSourceConfig(hc, config);//1.ebean
      setTargetFromProperties(hc, dst);//2.hikari (overrides)
      ds = createDataSource(hc, poolName);
    }
  }//new

  protected HikariDataSource createDataSource (HikariConfig hc, String poolName){
    try {// setupMonitoring
      hc.setMetricRegistry(Metrics.globalRegistry);
    } catch (Throwable ignore){
      // no Micrometer in classPath; see also hikariConfig.setRegisterMbeans(true)
    }
    hc.setPoolName(poolName.isEmpty() ? "ebean" : "ebean."+ poolName);
    return new HikariDataSource(hc);
  }

  private static final String[] VENDOR_SETTINGS_PREFIX = {"datasource.", "driver."};

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
  static boolean setProperty(final Object target, final String propName, final Object propValue, final List<Method> methods) {
    final var logger = LoggerFactory.getLogger("com.zaxxer.hikari.util.PropertyElf.HikariEbeanDataSourcePool");

    // use the english locale to avoid the infamous turkish locale bug
    var methodName = "set" + propName.substring(0, 1).toUpperCase(Locale.ENGLISH) + propName.substring(1);
    var writeMethod = methods.stream().filter(m -> m.getName().equals(methodName) && m.getParameterCount() == 1).findFirst().orElse(null);

    if (writeMethod == null) {
      var methodName2 = "set" + propName.toUpperCase(Locale.ENGLISH);
      writeMethod = methods.stream().filter(m -> m.getName().equals(methodName2) && m.getParameterCount() == 1).findFirst().orElse(null);
    }

    if (writeMethod == null) {
      logger.warn("Property {} does not exist on target {}", propName, target.getClass());// ~ ebean property
      return false;
    }

    try {
      var paramClass = writeMethod.getParameterTypes()[0];
      if (paramClass == int.class) {
        writeMethod.invoke(target, Integer.parseInt(propValue.toString()));
      }
      else if (paramClass == long.class) {
        writeMethod.invoke(target, Long.parseLong(propValue.toString()));
      }
      else if (paramClass == short.class) {
        writeMethod.invoke(target, Short.parseShort(propValue.toString()));
      }
      else if (paramClass == boolean.class || paramClass == Boolean.class) {
        writeMethod.invoke(target, Boolean.parseBoolean(propValue.toString()));
      }
      else if (paramClass == String.class) {
        writeMethod.invoke(target, propValue.toString());
      }
      else {
        try {
          logger.debug("Try to create a new instance of \"{}\"", propValue);
          writeMethod.invoke(target, Class.forName(propValue.toString()).getDeclaredConstructor().newInstance());
        }
        catch (InstantiationException | ClassNotFoundException e) {
          logger.debug("Class \"{}\" not found or could not instantiate it (Default constructor)", propValue);
          writeMethod.invoke(target, propValue);
        }
      }
      return true;// success
    } catch (Throwable e){
      logger.error("Failed to set property {} on target {}", propName, target.getClass(), e);
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
      if (is == null){
        return Collections.emptyMap();
      }
      try (is){
        Properties p = new Properties();
        p.load(new InputStreamReader(is, StandardCharsets.UTF_8));
        HashMap<String,String> m = new HashMap<>(p.size()+31);
        p.forEach((keyObj,valueObj)->{
          String key = trim(keyObj).toLowerCase(Locale.ENGLISH).replace("-", "").replace("_", "");
          m.put(key, trim(valueObj));
        });
        return m;
      }
    } catch (Throwable ignore){
      return Collections.emptyMap();
    }
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
    if (value == null){ return null; }
    String s = Normalizer.normalize(value.toString(), Normalizer.Form.NFC);

    if (isNumeric(s)){
      return SPACE_AND_UNDERSCORE.matcher(s.trim().strip()).replaceAll("");
    }
    return s;
  }

  private static final Pattern SPACE_AND_UNDERSCORE = Pattern.compile("[\\s_]", Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CHARACTER_CLASS);
  static final String[] SETTINGS_PREFIX = { "%db%", System.getProperty("hibean.prefix", "datasource.%db%") };

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
  void filter (Map<String,String> aliasMap, Properties src, Properties dst, String dbName) {
    String db =  dbName.isEmpty() ? "db." : dbName.toLowerCase()+'.'; // db. or mydb.

    src.forEach((keyObj,value)->{// datasource.db.url = jdbc:h2:mem:testMix
      String fullKey = trim(keyObj);
      String k = fullKey.toLowerCase(Locale.ENGLISH);

      String propertyName = "";
      for (String pre : SETTINGS_PREFIX){// 1) somedb. 2) datasource.somedb.  E.g.:  db., datasource.db.
        pre = trim(pre).toLowerCase(Locale.ENGLISH).replace("%db%", db);
        if (k.startsWith(pre)){
          propertyName = fullKey.substring(pre.length());
          k = k.substring(pre.length());
          break;
        }
      }
      if (propertyName.isEmpty()){
        return;// == continue == skip lines without prefix
      }

      k = k.replace("-","").replace("_",""); // [datasource.db.] max-Connections → maxconnections
      String alias = trim(aliasMap.get(k));
      if (!alias.isEmpty()){
        propertyName = alias; // e.g. url (ebean name) → jdbcUrl (hikari name)
      }

      dst.put(propertyName, normValue(value));
    });
  }

  @Override public String name () {
    return ds.getPoolName();
  }

  @Override public int size () {
    HikariPoolMXBean hPool = ds.getHikariPoolMXBean();
    return hPool.getTotalConnections();
  }


  @Override public boolean isAutoCommit () {
    return ds.isAutoCommit();
  }

  @Override public boolean isOnline () {
    return ds.isRunning() && !ds.isClosed();
  }

  @Override public boolean isDataSourceUp () {
    return isOnline();
  }

  @Override public void online () throws SQLException {
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


  @Override public SQLException dataSourceDownReason () {
    return null;
  }

  @Override public void setMaxSize (int max) {
    HikariConfigMXBean cfg = ds.getHikariConfigMXBean();
    cfg.setMaximumPoolSize(max);
  }

  /** @deprecated see {@link DataSourcePool#setWarningSize(int)} */
  @Deprecated @Override public void setWarningSize (int warningSize) {
    // 1. Deprecated 2. No similar functionality in Hikari
  }

  /** @deprecated see {@link DataSourcePool#getWarningSize()} */
  @Deprecated @Override public int getWarningSize () { return 0; }


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
}