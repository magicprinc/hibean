package com.github.magicprinc.hibean.example;

import io.ebean.Model;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@MappedSuperclass
@Data
@EqualsAndHashCode(doNotUseGetters = true, callSuper = false)
@ToString(doNotUseGetters = true, callSuper = false)
@NoArgsConstructor  @AllArgsConstructor
public abstract class BaseDomain extends Model {

	@Id Long id;
	@Version Long version;

}