# Hi! Bean!
[HikariCP](https://github.com/brettwooldridge/HikariCP) module for [Ebean ORM](https://ebean.io/)

Seamless drop-in replacement for Ebean's default connection pool.

[![Java CI with Gradle](https://github.com/magicprinc/hibean/actions/workflows/gradle.yml/badge.svg)](https://github.com/magicprinc/hibean/actions/workflows/gradle.yml)

## 1. Install
```
$ gradle clean build publishToMavenLocal
```

## 2. Import

`build.gradle`

```
runtimeOnly("com.github.magicprinc:hibean:latest.release")

...

// https://tomgregory.com/how-to-exclude-gradle-dependencies/
configurations.all { 
  exclude group: 'io.ebean', module: 'ebean-datasource' 
}
```

## 3. Use
`application.properties`

```
# const
default-idle-timeout = + 31_002

# datasource.<database_name>.<propertyName>
datasource.db.username = test_login
datasource.db.password = test12345
# or simply <database_name>.<propertyName>
db.idle-timeout = ${default-idle-timeout}
# all hikari and some ebean properyNames are supported (in conf file - easy to add more)
db.url=jdbc:sqlserver://127.0.0.1;databaseName=ebean;trustServerCertificate=true
# spring boot naming convention is supported
db.connection-timeout = 3_0_0 001
db.maximum-pool-size = 12

# global ebean
ebean.db.databasePlatformName=sqlserver16
```
or
```
my_external_conf_db.confFile = /nativeHikari.conf
```

You can replace configuration with configuration of another db (useful in tests, where you have mix of main and test settings)

```
db.copyFrom = test_db
```

You can merge (reuse) several db configs
```
datasource.db.appendFrom = template1, extra
```

Get it!
https://jitpack.io/