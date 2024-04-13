package com.github.magicprinc.hibean.example;


import com.github.magicprinc.hibean.FinderMixin;
import io.ebean.Finder;
import io.ebean.Model;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import javax.annotation.ParametersAreNullableByDefault;
import java.time.LocalDateTime;

@Entity
@Table(name = "smmo")
@ParametersAreNullableByDefault
@Data
@ToString(doNotUseGetters = true, callSuper = false)
@EqualsAndHashCode(doNotUseGetters = true, callSuper = false)
@NoArgsConstructor  @AllArgsConstructor  @Builder(toBuilder = true)
public class Smmo extends Model implements FinderMixin<Smmo> {

	public static final Finder<Long,Smmo> FIND = new Finder<>(Smmo.class);

	@Id
	@Column(name = "smmo_id")
	private Long smmoId;

	@Column(name = "user_id")
	private Integer userId;

	@Column(name = "done_dt")
	private LocalDateTime doneDt;

	@Column(name = "TimeStamp")
	private LocalDateTime timeStamp;

	@Column(name = "SvcType")
	private String svcType;

	@Column(name = "SrcTOA")
	@Min(0) @Max(255)
	private Short srcToa;

	@Column(name = "SrcAddr")
	private String srcAddr;

	@Column(name = "DstTOA")
	@Min(0) @Max(255)
	private Short dstToa;

	@Column(name = "DstAddr")
	private String dstAddr;

	@Column(name = "Esm_Class")
	@Min(0) @Max(255)
	private Short esmClass;

	@Column(name = "PID")
	@Min(0) @Max(255)
	private Short pid;

	@Column(name = "Priority")
	@Min(0) @Max(255)
	private Short priority;

	@Column(name = "RegDeliv")
	@Min(0) @Max(255)
	private Short regDeliv;

	@Column(name = "DCS")
	@Min(0) @Max(255)
	private Short dcs;

	@Column(name = "SM")
	private byte[] sm;

	@Column(name = "sar_msg_ref_num")
	@Min(0) @Max(255)
	private Short sarMsgRefNum;

	@Column(name = "sar_total_segments")
	@Min(0) @Max(255)
	private Short sarTotalSegments;

	@Column(name = "sar_segment_seqnum")
	@Min(0) @Max(255)
	private Short sarSegmentSeqnum;

	@Column(name = "SrcPort")
	@Min(0) @Max(0x7F_FF)
	private Integer srcPort;

	@Column(name = "DstPort")
	@Min(0) @Max(0x7F_FF)
	private Integer dstPort;

	@Column(name = "Opt1")
	private Integer opt1;

	@Column(name = "sm_crc")
	private Integer smCrc;

	@Column(name = "gate_id")
	private Short gateId;

	@Column(name = "sm_text")
	private String smText;

	@Column(name = "target_group_id")
	private Long targetGroupId;
}