package com.github.magicprinc.hibean.example;

import io.ebean.Model;

import javax.persistence.Id;
import javax.persistence.MappedSuperclass;
import javax.persistence.Version;

@MappedSuperclass
public abstract class BaseDomain extends Model {

	@Id	Long id;
	@Version Long version;

	public Long getId () { return id; }
	public Long getVersion () {	return version; }
	public void setId (Long id) { this.id = id; }
	public void setVersion (Long version) { this.version = version; }
}