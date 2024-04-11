package com.github.magicprinc.hibean.example;

import io.ebean.Model;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;


@MappedSuperclass
public abstract class BaseDomain extends Model {

	@Id Long id;
	@Version Long version;

	public Long getId () { return id; }
	public Long getVersion () {	return version; }
	public void setId (Long id) { this.id = id; }
	public void setVersion (Long version) { this.version = version; }
}