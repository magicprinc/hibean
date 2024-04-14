package com.github.magicprinc.hibean.example;

import io.ebean.Model;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 !!! https://ebean.io/docs/best-practice/
 • DON'T! @Data, @EqualsAndHashCode (Ebean generates hashCode and equals)
 • avoid getters! e.g. in toString()
 • instead of @Builder(toBuilder = true) use fluent chained setters

 jakarta.annotation.@Nonnull is a friend of @PostConstruct , @Resource and @Nullable

 @see lombok.NonNull
 @see javax.annotation.Nonnull
 @see jakarta.annotation.Nonnull
 @see io.ebean.annotation.NotNull
 @see jakarta.validation.constraints.NotNull

 @see javax.annotation.Nullable
 @see jakarta.annotation.Nullable
 */
@MappedSuperclass
@Getter  @Setter
@ToString(doNotUseGetters = true, callSuper = false) // avoid getters!
@Accessors(fluent = true, chain = true) // instead of @Builder(toBuilder = true)
@NoArgsConstructor  @AllArgsConstructor
public abstract class BaseDomain extends Model {

	@Id @NonNull Long id;
	@Version Long version;

}