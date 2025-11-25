package org.yhj.srim.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.yhj.srim.repository.entity.DartFsFiling;

import java.util.Optional;

public interface DartFsFilingRepository extends JpaRepository<DartFsFiling, Long> {
    Optional<DartFsFiling> findByRceptNoAndReprtCodeAndFsDiv(String rceptNo, String reprtCode, String fsDiv);
}
