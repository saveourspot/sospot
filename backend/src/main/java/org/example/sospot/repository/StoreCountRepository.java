package org.example.sospot.repository;

import java.util.List;
import org.example.sospot.domain.StoreCount;
import org.example.sospot.domain.StoreCountId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoreCountRepository extends JpaRepository<StoreCount, StoreCountId> {

  List<StoreCount> findByDongCodeAndCatCodeOrderByPeriodIdAsc(
      String dongCode, String catCode);

  List<StoreCount> findByCatCodeAndPeriodId(String catCode, String periodId);

  List<StoreCount> findByPeriodIdAndCatLevel(String periodId, String catLevel);

  @Query("""
      select sum(count.storeCount)
      from StoreCount count
      where count.periodId = :periodId and count.catLevel = :catLevel
      """)
  Long sumStoreCountByPeriodIdAndCatLevel(
      @Param("periodId") String periodId, @Param("catLevel") String catLevel);
}
