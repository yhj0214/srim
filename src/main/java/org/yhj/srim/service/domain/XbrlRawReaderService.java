package org.yhj.srim.service.domain;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yhj.srim.repository.XbrlContextRepository;
import org.yhj.srim.repository.XbrlDocumentRepository;
import org.yhj.srim.repository.XbrlFactRepository;
import org.yhj.srim.repository.entity.XbrlDocument;
import org.yhj.srim.service.dto.XbrlContextView;
import org.yhj.srim.service.dto.XbrlDocumentView;
import org.yhj.srim.service.dto.XbrlFactView;
import org.yhj.srim.service.dto.XbrlRawBundle;

import java.util.List;

@Service
@RequiredArgsConstructor
public class XbrlRawReaderService {

    private final XbrlDocumentRepository xbrlDocumentRepository;
    private final XbrlContextRepository xbrlContextRepository;
    private final XbrlFactRepository xbrlFactRepository;

    @Transactional(readOnly = true)
    public XbrlRawBundle getDocumentBundle(Long xbrlDocumentId) {
        XbrlDocument document = xbrlDocumentRepository.findById(xbrlDocumentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "XBRL 문서를 찾을 수 없습니다. xbrlDocumentId=" + xbrlDocumentId
                ));

        List<XbrlContextView> contexts = xbrlContextRepository
                .findByDocument_XbrlDocumentIdOrderByXbrlContextIdAsc(xbrlDocumentId)
                .stream()
                .map(XbrlContextView::of)
                .toList();

        List<XbrlFactView> facts = xbrlFactRepository
                .findByDocument_XbrlDocumentIdOrderByOrderHintAscXbrlFactIdAsc(xbrlDocumentId)
                .stream()
                .map(XbrlFactView::of)
                .toList();

        return new XbrlRawBundle(XbrlDocumentView.of(document), contexts, facts);
    }
}
