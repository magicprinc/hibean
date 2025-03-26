package com.github.magicprinc.hibean;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariConfigMXBean;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import com.zaxxer.hikari.metrics.micrometer.MicrometerMetricsTracker;
import com.zaxxer.hikari.pool.HikariPool;
import io.ebean.config.CurrentUserProvider;
import io.ebean.config.DatabaseConfig;
import io.ebean.datasource.DataSourceConfig;
import io.ebean.datasource.DataSourcePool;
import io.ebean.datasource.PoolStatus;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.jspecify.annotations.Nullable;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Objects;
import java.util.logging.Logger;

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

 @see com.github.magicprinc.hibean.HiBeanUtils#database(String, DataSource, CurrentUserProvider)

 @see io.ebean.datasource.pool.ConnectionPool#ConnectionPool(String, DataSourceConfig)
 @see DataSourceConfig
 @see DataSourceConfig#loadSettings(io.ebean.datasource.ConfigPropertiesHelper)
 @see DatabaseConfig#loadFromProperties()
 @see io.ebean.datasource.DataSourceBuilder.Settings

 @author a.fink
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HikariEbeanDataSourceWrapper implements DataSourcePool {
  HikariDataSource ds;

	public HikariEbeanDataSourceWrapper (HikariDataSource dataSource){ this.ds = dataSource; }//new

	/**
	 If wrapper is used with Spring DataSource, then one can have 2-in-1:
	 • Spring DataSource autoCommit == true (to make JdbcTemplate work)
	 • EbeanDataSourceWrapper autoCommit == false (as Ebean likes)

	 null == don't override, use delegate DataSource setting
	 @see com.github.magicprinc.hibean.HikariEbeanConnectionPoolFactory#autoCommitOverrideEbeanConfig
	*/
	@Getter @Setter @Accessors(fluent = true, chain = true)
	@Nullable Boolean connectionAutoCommitOverride = HikariEbeanConnectionPoolFactory.getAutoCommitOverrideEbeanConfig();

	public static DataSource wrap (DataSource dataSource) {
		if (dataSource instanceof HikariEbeanDataSourceWrapper)
				return dataSource;// as-is

		if (dataSource instanceof HikariDataSource hikariDataSource){
			val wrapper = new HikariEbeanDataSourceWrapper(hikariDataSource);

			if (hikariDataSource.isAutoCommit())
					wrapper.connectionAutoCommitOverride(false);// fix default Spring HikariDataSource autoCommit==true to false

			return wrapper;
		}
		log.error("Unknown DataSource type, can't wrap, use as-is: ({}) {}", dataSource.getClass().getName(), dataSource);
		return dataSource;
	}

  @Override public String name (){ return ds.getPoolName(); }

  @Override
	public int size () {
    HikariPoolMXBean hPool = ds.getHikariPoolMXBean();
    return hPool.getTotalConnections();
  }

  @Override
	public boolean isAutoCommit () {
		return connectionAutoCommitOverride != null ? connectionAutoCommitOverride
				: ds.isAutoCommit();
	}

  @Override public boolean isOnline (){ return ds.isRunning() && !ds.isClosed(); }

  @Override public boolean isDataSourceUp (){ return isOnline(); }

  @Override public void online (){ ds.getHikariPoolMXBean().resumePool(); }

  @Override public void offline (){ ds.getHikariPoolMXBean().suspendPool(); }

  @Override public void shutdown (){ ds.close(); }

  @Override
	public PoolStatus status (boolean reset) {
    HikariConfigMXBean cfg = ds.getHikariConfigMXBean();
    HikariPoolMXBean pool = ds.getHikariPoolMXBean();

    return new PoolStatus(){
      @Override public int minSize (){ return cfg.getMinimumIdle(); }
      @Override public int maxSize (){ return cfg.getMaximumPoolSize(); }
      @Override public int free (){ return pool.getIdleConnections(); }
      @Override public int busy (){ return pool.getActiveConnections(); }
      @Override public int waiting (){ return pool.getThreadsAwaitingConnection(); }
      /** todo {@link MicrometerMetricsTracker} */
      @Override public int highWaterMark () {
        return pool.getActiveConnections();
      }
      @Override public int waitCount (){ return 0; }
      @Override public int hitCount (){ return 0; }
			@Override public long maxAcquireMicros (){ return 0; }
			/** todo {@link MicrometerMetricsTracker} */
			@Override public long meanAcquireNanos (){ return 0; }
		};
  }

  @Override public SQLException dataSourceDownReason (){ return null; }

  @Override
	public void setMaxSize (int max) {
    HikariConfigMXBean cfg = ds.getHikariConfigMXBean();
    cfg.setMaximumPoolSize(max);
  }

  @Override
	public Connection getConnection () throws SQLException {
		Connection con = ds.getConnection();

		if (connectionAutoCommitOverride != null
			&& connectionAutoCommitOverride != con.getAutoCommit()
		){
			con.setAutoCommit(connectionAutoCommitOverride);
		}
		return con;
	}

  @Override
	public Connection getConnection (String username, String password) throws SQLException {
		Connection con = ds.getConnection(username, password);

		if (connectionAutoCommitOverride != null
			&& connectionAutoCommitOverride != con.getAutoCommit()
		){
			con.setAutoCommit(connectionAutoCommitOverride);
		}
		return con;
  }

  @Override public PrintWriter getLogWriter () throws SQLException { return ds.getLogWriter(); }

  @Override public void setLogWriter (PrintWriter out) throws SQLException { ds.setLogWriter(out); }

  @Override public void setLoginTimeout (int seconds) throws SQLException { ds.setLoginTimeout(seconds); }

  @Override public int getLoginTimeout () throws SQLException { return ds.getLoginTimeout(); }

  @Override public Logger getParentLogger () throws SQLFeatureNotSupportedException { return ds.getParentLogger(); }

	@Override  @SuppressWarnings("unchecked")
  public <T> T unwrap (Class<T> iface) throws SQLException {
    if (iface == null || iface == HikariDataSource.class || iface == HikariConfig.class)
	      return (T) ds;

		if (iface == HikariEbeanDataSourceWrapper.class)
				return (T) this;

    if (DataSource.class.equals(iface)){
      val p = (HikariPool) ds.getHikariPoolMXBean();// hack!
      return (T) p.getUnwrappedDataSource();
    }

    return ds.unwrap(iface);
  }

  @Override
	public boolean isWrapperFor (Class<?> iface) throws SQLException {
		if (iface == null || iface == HikariDataSource.class || iface == HikariConfig.class)
				return true;
		else
				return ds.isWrapperFor(iface);
  }

	@Override public String toString (){ return "HikariEbeanDataSourcePool("+ ds + ')'; }

	@Override
	public final boolean equals (Object o) {
		if (this == o){ return true; }
		if (!(o instanceof HikariEbeanDataSourceWrapper wrapper)){ return false; }

		return Objects.equals(ds, wrapper.ds);
	}

	@Override public int hashCode (){ return Objects.hashCode(ds); }
}