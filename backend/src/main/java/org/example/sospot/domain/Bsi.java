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
@Table(name = "fact_bsi")
@IdClass(BsiId.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Bsi {

  @Id
  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "period_month", columnDefinition = "char(7)", nullable = false)
  private String periodMonth;

  @Id
  @Column(name = "metric_name", length = 40, nullable = false)
  private String metricName;

  @Column(precision = 6, scale = 2)
  private BigDecimal value;
}
