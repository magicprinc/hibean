

Q: ddl-generator: Identity for PK is always generated
https://github.com/ebean-orm/ebean/issues/3399#issuecomment-2106376644

A:
Use `ebean.idGeneratorAutomatic=false` or set it via `DatabaseConfig.setIdGeneratorAutomatic(false)`

This defaults to `true` and means that `@Id` properties use either `Identity` or `Sequence` based on the DatabasePlatform.
Turning this off means that when those are needed, an explicit `@GeneratedValue` is required.

The above *globally* turns off Ebean's implicit behavior.

An alternative for the case that MOST entities use `Identity` or `Sequence` and only a few are application controlled identity values,
then we can use Ebean specific `@Identity(type=APPLICATION)` so:

```java
@Id @Identity(type=APPLICATION)
Long id;
```