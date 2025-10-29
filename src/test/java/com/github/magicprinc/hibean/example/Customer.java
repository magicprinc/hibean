package com.github.magicprinc.hibean.example;

import io.ebean.Finder;
import io.ebean.annotation.NotNull;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.jspecify.annotations.NullMarked;

import java.time.LocalDate;

@Entity
// DON'T! @Data, @EqualsAndHashCode  See https://ebean.io/docs/best-practice/
@Getter  @Setter
@ToString(doNotUseGetters = true, callSuper = true) // avoid getters!
@NoArgsConstructor  @AllArgsConstructor
@Accessors(fluent = true, chain = true) // instead of @Builder(toBuilder = true)
@NullMarked
public class Customer extends BaseDomain {
  @NotNull String name;
  LocalDate startDate;
  @Lob String comments;

	public static final Finder<Long,Customer> finder = new Finder<>(Customer.class);// default database

  public Customer (@NonNull String name) {
    this.name = name;
  }//new
}