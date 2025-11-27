package org.yhj.srim.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.yhj.srim.repository.entity.FinMetricDef;

import java.util.List;

@Repository
public interface FinMetricDefRepository extends JpaRepository<FinMetricDef, String> {

    /**
     * 표시 순서로 정렬하여 모든 지표 조회
     */
    List<FinMetricDef> findAllByOrderByDisplayOrder();

    List<FinMetricDef> findAllByOrderByDisplayOrderAsc();
}
