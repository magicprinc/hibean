package com.github.magicprinc.hibean.example;

import com.github.magicprinc.hibean.FinderMixin;
import io.ebean.annotation.NotNull;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDate;

@Entity
@Data
@EqualsAndHashCode(doNotUseGetters = true, callSuper = true)
@ToString(doNotUseGetters = true, callSuper = true)
@NoArgsConstructor  @AllArgsConstructor  @Builder(toBuilder = true)
public class Customer extends BaseDomain implements FinderMixin<Customer> {

  @NotNull String name;
  LocalDate startDate;
  @Lob String comments;

  public Customer (String name) {
    this.name = name;
  }
}