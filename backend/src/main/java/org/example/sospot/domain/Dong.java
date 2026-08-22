package org.example.sospot.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(name = "dim_dong")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Dong {

  @Id
  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "dong_code", columnDefinition = "char(8)", nullable = false)
  private String dongCode;

  @Column(length = 20, nullable = false)
  private String sigungu;

  @Column(name = "dong_name", length = 40, nullable = false)
  private String dongName;

  @Column(name = "center_lat", precision = 10, scale = 7)
  private BigDecimal centerLat;

  @Column(name = "center_lng", precision = 10, scale = 7)
  private BigDecimal centerLng;
}
