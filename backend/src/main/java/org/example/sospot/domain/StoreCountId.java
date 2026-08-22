package org.example.sospot.domain;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class StoreCountId implements Serializable {

  private String dongCode;
  private String catCode;
  private String periodId;
}
