package org.example.sospot.repository;

import java.util.List;
import org.example.sospot.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, String> {

  List<Category> findByCatLevelOrderByCatCodeAsc(String catLevel);

  List<Category> findByParentCodeOrderByCatCodeAsc(String parentCode);
}
