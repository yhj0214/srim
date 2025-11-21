package org.yhj.srim.controller.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.yhj.srim.service.DartCorpCodeSyncService;

@RestController
@RequestMapping("/admin/dart")
@RequiredArgsConstructor
@Slf4j
public class DartAdminController {

    private final DartCorpCodeSyncService dartCorpCodeSyncService;

    /**
     * CORPCODE.xml 기반으로 dart_corp_map 적재 + stock_code.dart_corp_code 갱신
     */
    @GetMapping("/sync-corp-codes")
    public ResponseEntity<String> syncCorpCodes() {
        log.info("관리자 DART corpCode 동기화 요청 수신");

        try {
            dartCorpCodeSyncService.syncFromXml();
            return ResponseEntity.ok("DART corpCode 동기화 완료");
        } catch (Exception e) {
            log.error("DART corpCode 동기화 실패", e);
            return ResponseEntity.internalServerError()
                    .body("동기화 실패: " + e.getMessage());
        }
    }
}