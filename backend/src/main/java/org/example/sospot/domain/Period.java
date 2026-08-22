package org.example.sospot.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(name = "dim_period")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Period {

  @Id
  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "period_id", columnDefinition = "char(6)", nullable = false)
  private String periodId;

  @Column(nullable = false)
  private Short year;

  @Column(nullable = false)
  private Short quarter;

  @Column(name = "base_date", nullable = false)
  private LocalDate baseDate;
}
