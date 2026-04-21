package org.yhj.srim.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.yhj.srim.repository.entity.XbrlDocument;

import java.util.Optional;

public interface XbrlDocumentRepository extends JpaRepository<XbrlDocument, Long> {
    Optional<XbrlDocument> findByRceptNoAndReprtCodeAndFsDiv(String rceptNo, String reprtCode, String fsDiv);

    Optional<XbrlDocument> findTopByCompanyIdAndBsnsYearAndReprtCodeAndFsDivOrderByParsedAtDescRceptNoDesc(
            Long companyId,
            Integer bsnsYear,
            String reprtCode,
            String fsDiv
    );
}
