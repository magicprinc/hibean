package com.github.magicprinc.hibean;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariConfigMXBean;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import com.zaxxer.hikari.metrics.micrometer.MicrometerMetricsTracker;
import io.avaje.config.Config;
import io.ebean.config.DatabaseConfig;
import io.ebean.datasource.DataSourceConfig;
import io.ebean.datasource.DataSourcePool;
import io.ebean.datasource.PoolStatus;
import io.micrometer.core.instrument.Metrics;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.text.Normalizer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.function.Supplier;
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
    Properties dst = new Properties(configProperties.size() + 31);
    filter(aliasMap, configProperties,  dst, poolName);
    filter(aliasMap, System.getProperties(), dst, poolName);

    String name = poolName.isEmpty() ? "ebean" : "ebean."+ poolName;
    dst.setProperty("poolName", name);

    String copyFromDb = trim(dst.getProperty("copyFrom"));// useful in tests: workaround for main/test settings collisions
    if (!copyFromDb.isEmpty()){
      dst.clear();// mix of main and test configs
      filter(aliasMap, configProperties,  dst, copyFromDb);
      filter(aliasMap, System.getProperties(), dst, copyFromDb);
    }

    HikariConfig hc;
    String confFile = trim(dst.getProperty("confFile"));// load from another .properties file
    if (confFile.isEmpty()){
      hc = new HikariConfig(dst);
      mergeFromDataSourceConfig(hc, config);
    } else {
      hc = new HikariConfig(confFile);// full delegation
    }

    setupMonitoring(hc);
    hc.setPoolName(name);// to be sure
    ds = new HikariDataSource(hc);
  }//new

  protected void mergeFromDataSourceConfig (HikariConfig hc, DataSourceConfig config){
    hc.setReadOnly(config.isReadOnly());
    copyIf(hc::getJdbcUrl, config::getUrl, hc::setJdbcUrl);
    copyIf(hc::getDriverClassName, config::getDriver, hc::setDriverClassName);
    copyIf(hc::getUsername, config::getUsername, hc::setUsername);
    copyIf(hc::getUsername, config::getOwnerUsername, hc::setUsername);
    copyIf(hc::getPassword, config::getPassword, hc::setUsername);
    copyIf(hc::getPassword, config::getOwnerPassword, hc::setPassword);
    copyIf(hc::getSchema, config::getSchema, hc::setSchema);
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
          String key = trim(keyObj).toLowerCase().replace("-", "").replace("_", "");
          m.put(key, trim(valueObj));
        });
        return m;
      }
    } catch (Throwable ignore){
      return Collections.emptyMap();
    }
  }

  protected void setupMonitoring (HikariConfig hikariConfig){
    try {
      hikariConfig.setMetricRegistry(Metrics.globalRegistry);
    } catch (Throwable ignore){
      // no Micrometer in classPath; see also hikariConfig.setRegisterMbeans(true)
    }
  }

  void copyIf (Supplier<String> getterHkCfg, Supplier<String> config, Consumer<String> setter){
    if ( trim(getterHkCfg.get()).isEmpty() ){// absent property
      String s = trim(config.get());
      if ( !s.isEmpty() ){
        setter.accept(s);
      }
    }
  }

  static String trim (Object s){
    return s == null ? "" : s.toString().trim().strip();
  }

  /** Is "+1,000_000.17" numeric? */
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

  private static final Pattern SPACE_AND_UNDERSCORE = Pattern.compile("[\\s_]");

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
    String p1 =  dbName.isEmpty() ? "db." : dbName.toLowerCase()+'.'; // db. or mydb.
    String p2 = "datasource." + p1; // datasource.db. or datasource.mydb.

    src.forEach((keyObj,value)->{
      String fullKey = trim(keyObj);
      String k = fullKey.toLowerCase().replace("-","").replace("_","");

      String propertyName;
      if (k.startsWith(p1)){
        propertyName = fullKey.substring(p1.length());
        k = k.substring(p1.length());

      } else if (k.startsWith(p2)){
        propertyName = fullKey.substring(p2.length());
        k = k.substring(p2.length());
      } else {
        return;// == continue
      }

      String alias = trim(aliasMap.get(k));
      if (!alias.isEmpty()){
        propertyName = alias; // e.g. url (ebean name) → jdbcUrl (hikari name)
      }

      propertyName = toCamelFromUnderscore(propertyName.replace('-', '_'));// spring.boot-key_fmt
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
    if (iface == null){ return (T) ds; }
    return ds.unwrap(iface);
  }

  @Override public boolean isWrapperFor (Class<?> iface) throws SQLException {
    return ds.isWrapperFor(iface);
  }
}