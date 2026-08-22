package org.example.sospot.repository;

import java.util.List;
import java.util.Optional;
import org.example.sospot.domain.Bsi;
import org.example.sospot.domain.BsiId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BsiRepository extends JpaRepository<Bsi, BsiId> {

  List<Bsi> findByPeriodMonthOrderByMetricNameAsc(String periodMonth);

  List<Bsi> findByMetricNameAndValueIsNotNullOrderByPeriodMonthAsc(String metricName);

  Optional<Bsi> findFirstByMetricNameAndValueIsNotNullAndPeriodMonthLessThanEqualOrderByPeriodMonthDesc(
      String metricName, String periodMonth);

  @Query("select max(bsi.periodMonth) from Bsi bsi")
  Optional<String> findLatestPeriodMonth();
}
