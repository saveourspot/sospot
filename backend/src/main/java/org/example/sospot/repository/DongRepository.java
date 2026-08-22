package org.example.sospot.repository;

import java.util.List;
import org.example.sospot.domain.Dong;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DongRepository extends JpaRepository<Dong, String> {

  List<Dong> findBySigunguOrderByDongNameAsc(String sigungu);
}
