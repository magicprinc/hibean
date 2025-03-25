package com.github.magicprinc.hibean.example;

import io.ebean.Finder;
import io.ebean.Model;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

import javax.annotation.ParametersAreNullableByDefault;
import java.time.LocalDateTime;

@Entity
@Table(name = "smmo")
@ParametersAreNullableByDefault
// DON'T! @Data, @EqualsAndHashCode  See https://ebean.io/docs/best-practice/
@Getter  @Setter
@ToString(doNotUseGetters = true) // avoid getters! callSuper = false
@NoArgsConstructor  @AllArgsConstructor
@Accessors(fluent = true, chain = true) // instead of @Builder(toBuilder = true)
public class Smmo extends Model {
	public static final Finder<Long,Smmo> FINDER = new Finder<>(Smmo.class);

	public Smmo (String dbName) {
		super(dbName);
	}//new

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

	@Column(name = "SrcTOA") private @Min(0) @Max(255) Short srcToa;

	@Column(name = "SrcAddr")
	private String srcAddr;

	@Column(name = "DstTOA") private @Min(0) @Max(255) Short dstToa;

	@Column(name = "DstAddr")
	private String dstAddr;

	@Column(name = "Esm_Class") private @Min(0) @Max(255) Short esmClass;

	@Column(name = "PID") private @Min(0) @Max(255) Short pid;

	@Column(name = "Priority") private @Min(0) @Max(255) Short priority;

	@Column(name = "RegDeliv") private @Min(0) @Max(255) Short regDeliv;

	@Column(name = "DCS") private @Min(0) @Max(255) Short dcs;

	@Column(name = "SM")
	private byte[] sm;

	@Column(name = "sar_msg_ref_num") private @Min(0) @Max(255) Short sarMsgRefNum;

	@Column(name = "sar_total_segments") private @Min(0) @Max(255) Short sarTotalSegments;

	@Column(name = "sar_segment_seqnum") private @Min(0) @Max(255) Short sarSegmentSeqnum;

	@Column(name = "SrcPort") private @Min(0) @Max(0x7F_FF) Integer srcPort;

	@Column(name = "DstPort") private @Min(0) @Max(0x7F_FF) Integer dstPort;

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