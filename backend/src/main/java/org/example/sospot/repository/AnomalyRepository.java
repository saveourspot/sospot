package org.example.sospot.repository;

import java.util.Collection;
import java.util.List;
import org.example.sospot.domain.Anomaly;
import org.example.sospot.domain.AnomalyId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnomalyRepository extends JpaRepository<Anomaly, AnomalyId> {

  List<Anomaly> findByPeriodIdAndCatLevelAndGradeInOrderByScoreDesc(
      String periodId, String catLevel, Collection<String> grades);

  List<Anomaly> findByDongCodeAndPeriodIdAndCatLevelOrderByScoreDesc(
      String dongCode, String periodId, String catLevel);

  List<Anomaly> findByPeriodIdAndCatLevelAndCatCodeOrderByScoreDesc(
      String periodId, String catLevel, String catCode);

  List<Anomaly> findByPeriodIdAndCatLevelAndCatCodeIn(
      String periodId, String catLevel, Collection<String> catCodes);

  @Query("""
      select anomaly
      from Anomaly anomaly
      where anomaly.periodId = :periodId
        and anomaly.catLevel = :catLevel
        and (:catCode is null or anomaly.catCode = :catCode)
        and (:grade is null or anomaly.grade = :grade)
        and (:consecutiveDecline is null
          or anomaly.consecutiveDecline = :consecutiveDecline)
      """)
  List<Anomaly> search(
      @Param("periodId") String periodId,
      @Param("catLevel") String catLevel,
      @Param("catCode") String catCode,
      @Param("grade") String grade,
      @Param("consecutiveDecline") Boolean consecutiveDecline,
      Pageable pageable);
}
