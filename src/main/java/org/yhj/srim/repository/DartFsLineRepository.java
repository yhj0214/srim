package org.yhj.srim.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.yhj.srim.repository.entity.DartFsFiling;
import org.yhj.srim.repository.entity.DartFsLine;

import java.util.List;

public interface DartFsLineRepository extends JpaRepository<DartFsLine, Long> {

    List<DartFsLine> findByFiling_CompanyIdAndFiling_BsnsYearAndFiling_ReprtCode(Long companyId, int bsnsYear, String reprtCode);


    void deleteByFiling_FsFilingId(Long fsFilingId);

    long deleteByCompanyId(Long companyId);
}
