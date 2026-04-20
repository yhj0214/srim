package org.yhj.srim.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.yhj.srim.repository.entity.XbrlFact;

public interface XbrlFactRepository extends JpaRepository<XbrlFact, Long> {
    long deleteByDocument_XbrlDocumentId(Long xbrlDocumentId);
}
