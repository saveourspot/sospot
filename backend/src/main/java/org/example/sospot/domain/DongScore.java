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
@Table(name = "fact_dong_score")
@IdClass(DongScoreId.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DongScore {

  @Id
  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "dong_code", columnDefinition = "char(8)", nullable = false)
  private String dongCode;

  @Id
  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "period_id", columnDefinition = "char(6)", nullable = false)
  private String periodId;

  @Column(name = "raw_score", precision = 6, scale = 3)
  private BigDecimal rawScore;

  @Column(name = "pct_score", precision = 6, scale = 3)
  private BigDecimal pctScore;

  @Column(length = 10)
  private String grade;

  @Column(name = "anomaly_cat_count", nullable = false)
  private Short anomalyCatCount;

  @Column(name = "valid_cat_count", nullable = false)
  private Short validCatCount;
}
