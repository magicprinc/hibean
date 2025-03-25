package com.github.magicprinc.hibean;

import io.ebean.Database;
import io.ebean.DatabaseBuilder;
import io.ebean.DatabaseFactory;
import io.ebean.annotation.Platform;
import io.ebean.config.CurrentUserProvider;
import io.ebean.config.DatabaseConfig;
import io.ebean.config.MatchingNamingConvention;
import jakarta.persistence.PersistenceException;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

import javax.sql.DataSource;

/**

 https://github.com/ebean-orm-examples

 <pre>{@code
	PostgresContainer.builder("15")
		.port(5532) // Note: defaults to 6432
		.dbName("my_app")
		.user("my_app")
		.password("silly")
		.containerName("pg15")
		.extensions("hstore,pgcrypto")
		.build()
		.start();
 }</pre>

 <pre>{@code
 	 // GenerateDbMigration
	DbMigration dbMigration = DbMigration.create();
	dbMigration.setPlatform(Platform.POSTGRES);
	dbMigration.setVersion("20221101.0");
	dbMigration.setName("initial");

	dbMigration.generateMigration();
 }</pre>

 <pre>{@code
  logging.level.io.ebean.SQL=DEBUG
  logging.level.io.ebean.TXN=DEBUG
 }</pre>

 https://ebean.io/docs/transactions/spring
 <pre>{@code
   io.ebean:ebean-spring-txn:latest.release

   databaseConfig.setExternalTransactionManager(new SpringJdbcTransactionManager());
 }</pre>

 @see io.ebean.BeanRepository
 @see io.ebean.annotation.Transactional

 @see io.ebean.spring.txn.SpringJdbcTransactionManager

 @see jakarta.persistence.Entity
 @see jakarta.persistence.Table
 @see io.ebean.Model
 @see FinderMixin

 @see io.ebean.Database
 */
public final class HiBeanUtils {
	/**
	 oracle, h2, postgres, mysql, sqlserver16, sqlserver17

	 @see DatabaseBuilder#databasePlatformName(String)
	 @see io.ebean.annotation.Platform
	 @see io.ebean.config.dbplatform.DatabasePlatform
	 @see io.ebean.platform.sqlserver.SqlServerPlatformProvider#create(Platform)
	 */
	@Getter @Setter private static String databasePlatformName = "sqlserver17";

	/**

	 {@code -Debean.migration.run=true}

	 @see DatabaseConfig#ddlGenerate(boolean)
	 @see DatabaseConfig#ddlRun(boolean)
	 @see DatabaseConfig#ddlCreateOnly(boolean)
	 @see DatabaseConfig#runMigration(boolean)
	 */
	@Getter @Setter private static boolean ddl = false;

	public static Database database (
		String ebeanDatabaseName,
		@NonNull DataSource dataSource,
		@Nullable CurrentUserProvider currentUserProvider
	){
		var config = new DatabaseConfig();// config.loadFromProperties();
		config.dataSource(HikariEbeanDataSourceWrapper.wrap(dataSource));
		config.register(true);// register in DB singleton too (in addition to Spring bean)

		if (ebeanDatabaseName == null || ebeanDatabaseName.isEmpty() || "db".equals(ebeanDatabaseName)){
			config.name("db");// db is the default name
			config.defaultDatabase(true);
		} else {
			config.name(ebeanDatabaseName);
			config.defaultDatabase(false);
		}

		if (currentUserProvider != null)
				config.currentUserProvider(currentUserProvider);

		if (ddl){
			config.ddlGenerate(true);
			config.ddlRun(true);
			config.ddlCreateOnly(true);
		}

		//config.persistBatch(PersistBatch.ALL);// use JDBC batch by default vs NONE
		//config.persistBatchSize(100);// default batch size
		config.namingConvention(new MatchingNamingConvention());// vs UnderscoreNamingConvention

		try {
			return DatabaseFactory.create(config);
		} catch (PersistenceException | IllegalArgumentException e){
			// For SqlServer please explicitly choose either sqlserver16 or sqlserver17 as the platform via DatabaseConfig.setDatabasePlatformName. Refer to issue #1340 for more details
			config.databasePlatformName(databasePlatformName);
			return DatabaseFactory.create(config);
		}
	}
}