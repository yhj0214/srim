package org.yhj.srim.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.yhj.srim.repository.entity.XbrlContext;

public interface XbrlContextRepository extends JpaRepository<XbrlContext, Long> {
    long deleteByDocument_XbrlDocumentId(Long xbrlDocumentId);
}
