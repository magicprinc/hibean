package com.github.magicprinc.hibean.example;

import io.ebean.annotation.NotNull;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;

import java.time.LocalDate;

@Entity
public class Customer extends BaseDomain {

  @NotNull String name;
  LocalDate startDate;
  @Lob String comments;

  public Customer(String name) {
    this.name = name;
  }

  public String getName () { return name;}
  public LocalDate getStartDate () { return startDate;}
  public String getComments () { return comments;}

  public void setName (String name) { this.name = name;}
  public void setStartDate (LocalDate startDate) { this.startDate = startDate;}
  public void setComments (String comments) { this.comments = comments;}
}