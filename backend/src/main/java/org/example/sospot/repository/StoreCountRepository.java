package org.example.sospot.repository;

import java.util.List;
import org.example.sospot.domain.StoreCount;
import org.example.sospot.domain.StoreCountId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreCountRepository extends JpaRepository<StoreCount, StoreCountId> {

  List<StoreCount> findByDongCodeAndCatCodeOrderByPeriodIdAsc(
      String dongCode, String catCode);

  List<StoreCount> findByCatCodeAndPeriodId(String catCode, String periodId);
}
