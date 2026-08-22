package org.example.sospot.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(name = "fact_store_count")
@IdClass(StoreCountId.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoreCount {

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
}
