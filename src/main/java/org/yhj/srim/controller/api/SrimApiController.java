package org.yhj.srim.controller.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.yhj.srim.controller.dto.ApiResponse;
import org.yhj.srim.service.SrimService;
import org.yhj.srim.service.dto.SrimResultDto;

@RestController
@RequestMapping("/api/stocks/{companyId}/srim")
@RequiredArgsConstructor
@Slf4j
public class SrimApiController {

    private final SrimService srimService;

    /**
     * S-RIM 계산 API
     * 
     * @param companyId 회사 ID
     * @param basis 기준 (YEAR/QTR)
     * @param rating 신용등급 (기본 BBB-)
     * @param tenor 만기(월) (기본 60)
     * @return S-RIM 계산 결과
     */
    @GetMapping
    public ResponseEntity<ApiResponse<SrimResultDto>> calculate(
            @PathVariable Long companyId,
            @RequestParam(defaultValue = "YEAR") String basis,
            @RequestParam(required = false) String rating,
            @RequestParam(required = false) Integer tenor) {
        
        try {
            SrimResultDto result = srimService.calculate(companyId, basis, rating, tenor);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (IllegalArgumentException e) {
            log.warn("S-RIM 계산 실패: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("S-RIM 계산 오류: companyId={}", companyId, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("S-RIM 계산 중 오류가 발생했습니다."));
        }
    }
}
