package org.example.sospot.repository;

import java.util.List;
import java.util.Optional;
import org.example.sospot.domain.DongScore;
import org.example.sospot.domain.DongScoreId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DongScoreRepository extends JpaRepository<DongScore, DongScoreId> {

  List<DongScore> findByPeriodIdOrderByPctScoreDesc(String periodId);

  Optional<DongScore> findByDongCodeAndPeriodId(String dongCode, String periodId);

  @Query("select max(score.periodId) from DongScore score")
  Optional<String> findLatestAnalyzedPeriodId();
}
