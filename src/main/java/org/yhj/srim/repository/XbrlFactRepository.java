package org.yhj.srim.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.yhj.srim.repository.entity.XbrlFact;

import java.util.List;

public interface XbrlFactRepository extends JpaRepository<XbrlFact, Long> {
    long deleteByDocument_XbrlDocumentId(Long xbrlDocumentId);

    @EntityGraph(attributePaths = "context")
    List<XbrlFact> findByDocument_XbrlDocumentIdOrderByOrderHintAscXbrlFactIdAsc(Long xbrlDocumentId);
}
