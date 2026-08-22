package org.example.sospot.repository;

import java.util.List;
import org.example.sospot.domain.Period;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PeriodRepository extends JpaRepository<Period, String> {

  List<Period> findAllByOrderByPeriodIdAsc();
}
