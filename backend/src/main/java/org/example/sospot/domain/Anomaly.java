package org.example.sospot.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(name = "fact_anomaly")
@IdClass(AnomalyId.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Anomaly {

  @Id
  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "dong_code", columnDefinition = "char(8)", nullable = false)
  private String dongCode;

  @Id
  @Column(name = "cat_code", length = 6, nullable = false)
  private String catCode;

  @Id
  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "period_id", columnDefinition = "char(6)", nullable = false)
  private String periodId;

  @Column(name = "cat_level", length = 6, nullable = false)
  private String catLevel;

  @Column(name = "store_count", nullable = false)
  private Integer storeCount;

  @Column(name = "growth_rate", precision = 8, scale = 5)
  private BigDecimal growthRate;

  @Column(name = "city_growth_rate", precision = 8, scale = 5)
  private BigDecimal cityGrowthRate;

  @Column(name = "relative_gap", precision = 8, scale = 5)
  private BigDecimal relativeGap;

  @Column(name = "cum_change_rate", precision = 8, scale = 5)
  private BigDecimal cumChangeRate;

  @Column(name = "consecutive_decline", nullable = false)
  private Boolean consecutiveDecline;

  @Column(name = "sample_size_flag", length = 4, nullable = false)
  private String sampleSizeFlag;

  @Column(precision = 6, scale = 3)
  private BigDecimal score;

  @Column(length = 10)
  private String grade;
}
