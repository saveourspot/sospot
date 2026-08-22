package org.example.sospot.repository;

import java.util.Collection;
import java.util.List;
import org.example.sospot.domain.Anomaly;
import org.example.sospot.domain.AnomalyId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnomalyRepository extends JpaRepository<Anomaly, AnomalyId> {

  List<Anomaly> findByPeriodIdAndCatLevelAndGradeInOrderByScoreDesc(
      String periodId, String catLevel, Collection<String> grades);

  List<Anomaly> findByDongCodeAndPeriodIdAndCatLevelOrderByScoreDesc(
      String dongCode, String periodId, String catLevel);

  List<Anomaly> findByPeriodIdAndCatLevelAndCatCodeOrderByScoreDesc(
      String periodId, String catLevel, String catCode);
}
