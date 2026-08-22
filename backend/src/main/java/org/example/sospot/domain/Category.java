package org.example.sospot.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "dim_category")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category {

  @Id
  @Column(name = "cat_code", length = 6, nullable = false)
  private String catCode;

  @Column(name = "cat_name", length = 60, nullable = false)
  private String catName;

  @Column(name = "parent_code", length = 6)
  private String parentCode;

  @Column(name = "cat_level", length = 6, nullable = false)
  private String catLevel;
}
