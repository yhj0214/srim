-- ===============================================================
-- DB 초기화 + 스키마 생성 + 샘플 데이터 한 번에 실행
-- ===============================================================

DROP DATABASE IF EXISTS srimdb;
CREATE DATABASE srimdb CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE srimdb;

/* 1) 종목 코드 마스터 */
CREATE TABLE `stock_code` (
    `stock_id`               BIGINT        NOT NULL AUTO_INCREMENT COMMENT 'PK: 내부 식별자(숫자형)',
    `ticker_krx`             VARCHAR(6)    NOT NULL COMMENT 'KRX 6자리 종목코드(예: 005930)',
    `dart_corp_code`         VARCHAR(8)    NULL COMMENT 'DART corp_code',
    `company_name`           VARCHAR(200)  NOT NULL COMMENT '회사명(국문)',
    `industry`               VARCHAR(200)  NULL COMMENT '업종(수집원 기준 분류명)',
    `listing_date`           DATE          NULL COMMENT '상장일',
    `fiscal_year_end_month`  TINYINT       NULL COMMENT '결산월(1~12)',
    `homepage_url`           VARCHAR(300)  NULL COMMENT '회사 홈페이지 URL',
    `region`                 VARCHAR(100)  NULL COMMENT '지역(본사 소재지 등)',
    `market`                 VARCHAR(20)   NULL COMMENT '시장(KOSPI/KOSDAQ/KONEX 등)',
    `isin`                   VARCHAR(20)   NULL COMMENT 'ISIN(국제증권식별번호, KRX JSON: ISU_CD)',
    `created_at`             DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '행 생성시각',
    `updated_at`             DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '행 수정시각',
    CONSTRAINT `PK_STOCK_CODE`             PRIMARY KEY (`stock_id`),
    CONSTRAINT `UN_STOCK_CODE_MARKET_TICKER` UNIQUE (`market`, `ticker_krx`),
    CONSTRAINT `CK_STOCK_FYEND`            CHECK (`fiscal_year_end_month` BETWEEN 1 AND 12)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='KRX 종목코드 마스터(기업 식별 기본정보)';

/* 2) 회사 메타 */
CREATE TABLE `company` (
    `company_id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT 'PK: 내부 식별자(숫자형)',
    `stock_id`             BIGINT        NOT NULL COMMENT 'FK → stock_code.stock_id',
    `shares_outstanding`   BIGINT        NULL COMMENT '상장/유통 주식수(기본 단위: 주)',
    `face_value`           DECIMAL(15,2) NULL COMMENT '액면가(원)',
    `currency`             VARCHAR(3)    NOT NULL DEFAULT 'KRW' COMMENT '표준 통화코드(기본 KRW)',
    `sector`               VARCHAR(100)  NULL COMMENT '섹터(대분류, 선택)',
    `notes`                VARCHAR(500)  NULL COMMENT '비고',
    `created_at`           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '행 생성시각',
    `updated_at`           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '행 수정시각',
    CONSTRAINT `PK_COMPANY`               PRIMARY KEY (`company_id`),
    CONSTRAINT `FK_COMPANY_STOCK_CODE`    FOREIGN KEY (`stock_id`) REFERENCES `stock_code`(`stock_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='회사 메타(상장주식수/액면가/통화 등)';

/* 3) 시세 스냅샷 */
CREATE TABLE `market_snapshot` (
    `snapshot_id`    BIGINT         NOT NULL AUTO_INCREMENT COMMENT 'PK: 스냅샷 ID',
    `company_id`     BIGINT         NOT NULL COMMENT 'FK → company.company_id',
    `as_of`          DATETIME       NOT NULL COMMENT '수집 시각(현지시간)',
    `price`          DECIMAL(18,2)  NULL COMMENT '현재가/종가(원)',
    `open_price`     DECIMAL(18,2)  NULL COMMENT '시가(원)',
    `high_price`     DECIMAL(18,2)  NULL COMMENT '고가(원)',
    `low_price`      DECIMAL(18,2)  NULL COMMENT '저가(원)',
    `volume`         BIGINT         NULL COMMENT '거래량(주)',
    `market_cap`     DECIMAL(22,2)  NULL COMMENT '시가총액(원)',
    `per`            DECIMAL(10,4)  NULL COMMENT 'PER(배)',
    `pbr`            DECIMAL(10,4)  NULL COMMENT 'PBR(배)',
    `div_yield`      DECIMAL(10,4)  NULL COMMENT '현금배당수익률(소수, 0.045 = 4.5%)',
    `source`         VARCHAR(20)    NOT NULL COMMENT '수집원(NAVER/KRX/FNG/CSV/MANUAL)',
    `created_at`     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '행 생성시각',
    CONSTRAINT `PK_MARKET_SNAPSHOT` PRIMARY KEY (`snapshot_id`),
    CONSTRAINT `FK_MS_COMPANY`      FOREIGN KEY (`company_id`) REFERENCES `company`(`company_id`),
    CONSTRAINT `UN_MS_UNIQ`         UNIQUE (`company_id`, `as_of`, `source`),
    CONSTRAINT `CK_MS_SOURCE`       CHECK (`source` IN ('NAVER','KRX','FNG','CSV','MANUAL'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='시세 스냅샷(가격/시총/밸류 지표 히스토리)';

/* 4) 재무 기간 */
CREATE TABLE `fin_period` (
    `period_id`      BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'PK: 기간 ID',
    `company_id`     BIGINT       NOT NULL COMMENT 'FK → company.company_id',
    `period_type`    VARCHAR(8)   NOT NULL COMMENT '기간 유형: YEAR(연간) / QTR(분기)',
    `fiscal_year`    INT          NOT NULL COMMENT '회계연도(예: 2024)',
    `fiscal_quarter` TINYINT      NULL COMMENT '분기(1~4, 연간이면 NULL)',
    `is_estimate`    TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '추정치 여부(E)',
    `label`          VARCHAR(20)  NOT NULL COMMENT '표시용 라벨(YYYY/12, YYYY.Q#)',
    `period_start`   DATE         NULL COMMENT '기간 시작일(선택)',
    `period_end`     DATE         NULL COMMENT '기간 종료일(선택)',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '행 생성시각',
    `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '행 수정시각',
    CONSTRAINT `PK_FIN_PERIOD`    PRIMARY KEY (`period_id`),
    CONSTRAINT `FK_FP_COMPANY`    FOREIGN KEY (`company_id`) REFERENCES `company`(`company_id`),
    CONSTRAINT `CK_FP_TYPE`       CHECK (`period_type` IN ('YEAR','QTR')),
    CONSTRAINT `CK_FP_QTR`        CHECK (`fiscal_quarter` IS NULL OR (`fiscal_quarter` BETWEEN 1 AND 4))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='재무 기간 정의(연/분기/추정 포함)';

CREATE UNIQUE INDEX `UN_FIN_PERIOD`
    ON `fin_period` (
        `company_id`,
        `period_type`,
        `fiscal_year`,
        ((IFNULL(`fiscal_quarter`, 0))),
        `is_estimate`
    );

/* 5) 재무 지표 정의 */
CREATE TABLE `fin_metric_def` (
    `metric_code`   VARCHAR(32)   NOT NULL COMMENT '지표 코드(SALES/OP_INC/NET_INC/ROE/EPS/BPS/PER/PBR 등)',
    `name_kor`      VARCHAR(100)  NOT NULL COMMENT '지표 명칭(국문)',
    `unit`          VARCHAR(20)   NULL COMMENT '표시 단위(KRW/%/배/주 등)',
    `description`   VARCHAR(500)  NULL COMMENT '지표 설명(산식/출처 등)',
    `display_order` INT           NOT NULL COMMENT '화면 표기 순서(보고서/대시보드 정렬 기준)',
    `created_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성시각',
    `updated_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정시각',
    CONSTRAINT `PK_FIN_METRIC_DEF` PRIMARY KEY (`metric_code`),
    CONSTRAINT `UN_FMD_NAME`       UNIQUE (`name_kor`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='재무 지표 마스터(카탈로그: 코드/단위/순서)';

/* 6) 재무 지표 값 */
CREATE TABLE `fin_metric_value` (
    `value_id`           BIGINT         NOT NULL AUTO_INCREMENT COMMENT 'PK: 내부 식별자',
    `company_id`   BIGINT         NOT NULL COMMENT 'FK → company.company_id',
    `period_id`    BIGINT         NOT NULL COMMENT 'FK → fin_period.period_id',
    `metric_code`  VARCHAR(32)    NOT NULL COMMENT 'FK → fin_metric_def.metric_code',
    `value_num`    DECIMAL(28,6)  NULL COMMENT '지표 수치(소수/원 단위 통일 저장)',
    `source`       VARCHAR(20)    NULL COMMENT '수집원(KRX/NAVER/FNG/CSV/MANUAL)',
    `updated_at`   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '갱신 시각',
    CONSTRAINT `PK_FIN_METRIC_VALUE` PRIMARY KEY (`value_id`),
    CONSTRAINT `UN_FIN_METRIC_VALUE` UNIQUE (`company_id`, `period_id`, `metric_code`),
    CONSTRAINT `FK_FMV_COMPANY`    FOREIGN KEY (`company_id`)  REFERENCES `company`(`company_id`),
    CONSTRAINT `FK_FMV_PERIOD`     FOREIGN KEY (`period_id`)   REFERENCES `fin_period`(`period_id`),
    CONSTRAINT `FK_FMV_METRIC`     FOREIGN KEY (`metric_code`) REFERENCES `fin_metric_def`(`metric_code`),
    CONSTRAINT `CK_FMV_SOURCE`     CHECK (`source` IS NULL OR `source` IN ('KRX','NAVER','FNG','CSV','MANUAL', 'DART'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='재무 지표 값(연/분기/추정 전체 커버)';

/* 7) 지분구조 스냅샷 */
CREATE TABLE `shareholding_snapshot` (
    `sh_snapshot_id`  BIGINT        NOT NULL AUTO_INCREMENT COMMENT 'PK: 지분 스냅샷 ID',
    `company_id`      BIGINT        NOT NULL COMMENT 'FK → company.company_id',
    `as_of`           DATE          NOT NULL COMMENT '기준일(공시일/최종변동일)',
    `remarks`         VARCHAR(300)  NULL COMMENT '비고',
    `created_at`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '행 생성시각',
    CONSTRAINT `PK_SH_SNAPSHOT`     PRIMARY KEY (`sh_snapshot_id`),
    CONSTRAINT `FK_SHS_COMPANY`     FOREIGN KEY (`company_id`) REFERENCES `company`(`company_id`),
    CONSTRAINT `UN_SHS_UNIQ`        UNIQUE (`company_id`, `as_of`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='지분구조 스냅샷(회사/기준일 헤더)';

CREATE TABLE `shareholding_detail` (
    `sh_snapshot_id`  BIGINT        NOT NULL COMMENT 'FK → shareholding_snapshot.sh_snapshot_id',
    `item_code`       VARCHAR(32)   NOT NULL COMMENT '항목코드(LARGEST_HOLDER/GT10_HOLDER/GT5_HOLDER/EXECUTIVE_LT5/TREASURY_STOCK/UNION 등)',
    `holders_count`   INT           NULL COMMENT '보유자 수(대표주주수/임원수 등)',
    `common_shares`   BIGINT        NULL COMMENT '보통주 보유 수(주)',
    `stake_pct`       DECIMAL(10,4) NULL COMMENT '지분율(소수, 0.0367=3.67%)',
    `last_change_date` DATE         NULL COMMENT '해당 항목 최종 변동일',
    CONSTRAINT `PK_SH_DETAIL`       PRIMARY KEY (`sh_snapshot_id`, `item_code`),
    CONSTRAINT `FK_SHD_SNAPSHOT`    FOREIGN KEY (`sh_snapshot_id`) REFERENCES `shareholding_snapshot`(`sh_snapshot_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='지분구조 상세(항목별 값)';

/* 8) 회사채 수익률 */
CREATE TABLE `bond_yield_curve` (
    `curve_id`      BIGINT        NOT NULL AUTO_INCREMENT COMMENT 'PK: 곡선 ID',
    `as_of`         DATE          NOT NULL COMMENT '기준일(YYYY-MM-DD)',
    `rating`        VARCHAR(8)    NOT NULL COMMENT '신용등급(AAA/AA+/AA/A/BBB..., 국고채는 KTB 등으로 표기)',
    `tenor_months`  SMALLINT      NOT NULL COMMENT '만기(월 단위: 3,6,9,12,18,24,36,60 등)',
    `yield_rate`    DECIMAL(10,4) NOT NULL COMMENT '수익률(소수, 0.0286 = 2.86%)',
    `source`        VARCHAR(20)   NULL COMMENT '수집원(KOFIA/KRX/ETC)',
    `created_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '행 생성시각',
    CONSTRAINT `PK_BOND_YIELD_CURVE` PRIMARY KEY (`curve_id`),
    CONSTRAINT `UN_BOND_CURVE_UNIQ`  UNIQUE (`as_of`, `rating`, `tenor_months`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='회사채/국고채 수익률 곡선(등급×만기×일자)';

/* 9) 다트 전자공시 */
CREATE TABLE dart_corp_map (
   corp_code   VARCHAR(8)   NOT NULL COMMENT 'DART corp_code',
   corp_name   VARCHAR(200) NULL     COMMENT '법인명',
   stock_code  VARCHAR(6)   NULL     COMMENT '상장 종목코드(6자리), 비상장은 NULL',
   PRIMARY KEY (corp_code),
   KEY IX_DART_STOCK (stock_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='DART corpCode.xml 매핑 임시 테이블';

/* 10) 연도별 주식 현황 */
CREATE TABLE `stock_share_status` (
      `stock_status_id`  BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'PK',
      `company_id`       BIGINT       NOT NULL COMMENT 'FK: company.company_id',
      `bsns_year`        INT          NOT NULL COMMENT '사업연도(예: 2024)',
      `stlm_dt`          DATE         NOT NULL COMMENT '결산일(예: 2018-12-31)',
      `se`               VARCHAR(20)  NOT NULL COMMENT '주식 종류(보통주/우선주/합계 등)',

      `isu_stock_totqy`  BIGINT       NULL COMMENT '발행할 주식의 총수(정관상 한도)',
      `istc_totqy`       BIGINT       NULL COMMENT '발행주식의 총수(Ⅱ-Ⅲ, DART istc_totqy)',
      `tesstk_co`        BIGINT       NULL COMMENT '자기주식수',
      `distb_stock_co`   BIGINT       NULL COMMENT '유통주식수(발행주식 - 자기주식)',

      `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성시각',
      `updated_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정시각',

      CONSTRAINT `PK_STOCK_SHARE_STATUS` PRIMARY KEY (`stock_status_id`),

      CONSTRAINT `FK_SSS_COMPANY`
          FOREIGN KEY (`company_id`) REFERENCES `company` (`company_id`)
              ON DELETE CASCADE ON UPDATE CASCADE,

      CONSTRAINT `UNQ_SSS_COMPANY_YEAR_SE`
          UNIQUE (`company_id`, `bsns_year`, `se`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='연도별 주식수(발행/자기/유통) 현황';
/* ===============================================================
 * 9-A) DART 재무제표 공시 헤더
 *   - fnlttSinglAcntAll.json 등으로 가져온 재무제표 공시 1건(접수번호 단위)
 *   - 회사/연도/보고서 코드/재무제표 구분(CFS/DFS) 기준 메타 정보 저장
 * =============================================================== */
CREATE TABLE `dart_fs_filing` (
                                  `fs_filing_id`   BIGINT        NOT NULL AUTO_INCREMENT COMMENT 'PK: 재무제표 공시 ID',
                                  `corp_code`      VARCHAR(8)    NOT NULL COMMENT 'DART corp_code (dart_corp_map.corp_code)',
                                  `company_id`     BIGINT        NULL COMMENT 'FK: company.company_id (상장사 매핑 완료 시)',
                                  `rcept_no`       VARCHAR(14)   NOT NULL COMMENT '접수번호(예: 20230320001054)',
                                  `reprt_code`     VARCHAR(5)    NOT NULL COMMENT '보고서 코드(11011=사업, 11012=반기, 11013=분기 등)',
                                  `bsns_year`      INT           NOT NULL COMMENT '사업연도(예: 2022)',
                                  `fs_div`         VARCHAR(4)    NULL COMMENT '재무제표 구분(CFS:연결, OFS/DFS:별도 등)',
                                  `report_tp`      VARCHAR(20)   NULL COMMENT '보고서 유형(연간/반기/분기 등, 필요시 자체 정의)',
                                  `rcept_dt`       DATE          NULL COMMENT '접수일(YYYY-MM-DD)',
                                  `currency`       VARCHAR(3)    NULL COMMENT '표시 통화(KRW 등)',
                                  `note`           VARCHAR(300)  NULL COMMENT '비고(파싱 옵션/특이사항 메모용)',
                                  `created_at`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '행 생성시각',
                                  `updated_at`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
                                      ON UPDATE CURRENT_TIMESTAMP COMMENT '행 수정시각',

                                  CONSTRAINT `PK_DART_FS_FILING`  PRIMARY KEY (`fs_filing_id`),

    /* 동일 공시가 중복으로 쌓이지 않도록 방지용 제약 */
                                  CONSTRAINT `UN_DART_FS_RCEPT`   UNIQUE (`rcept_no`, `reprt_code`, `fs_div`),

                                  CONSTRAINT `FK_DFF_COMPANY`
                                      FOREIGN KEY (`company_id`) REFERENCES `company`(`company_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='DART 재무제표 공시 헤더(접수번호/연도 단위 메타 정보)';


/* 조회 최적화를 위한 인덱스 */
-- CREATE INDEX `IX_DFF_CORP_YEAR`
--     ON `dart_fs_filing` (`corp_code`, `bsns_year`, `reprt_code`, `fs_div`);
--
-- CREATE INDEX `IX_DFF_COMPANY_YEAR`
--     ON `dart_fs_filing` (`company_id`, `bsns_year`, `reprt_code`, `fs_div`);

/* ===============================================================
 * 9-B) DART 재무제표 상세 라인
 *   - 각 공시(fs_filing_id) 안에 포함된 계정별 재무제표 라인 저장
 *   - BS/CIS/CF별로 account_id 단위 원천 금액 보존
 * =============================================================== */
CREATE TABLE `dart_fs_line` (
                                `fs_line_id`         BIGINT        NOT NULL AUTO_INCREMENT COMMENT 'PK: 재무제표 라인 ID',
                                `fs_filing_id`       BIGINT        NOT NULL COMMENT 'FK: dart_fs_filing.fs_filing_id',
                                `company_id`         BIGINT        NULL COMMENT 'FK: company.company_id (조인 최적화용, 선택)',

                                `sj_div`             VARCHAR(4)    NOT NULL COMMENT '재무제표 양식 구분(BS/CIS/CF 등, sj_div)',
                                `sj_nm`              VARCHAR(200)  NULL COMMENT '양식 명칭(재무상태표/포괄손익계산서/현금흐름표 등)',

                                `account_id`         VARCHAR(200)  NOT NULL COMMENT '계정 ID(ifrs-full_Revenue 등, account_id)',
                                `account_nm`         VARCHAR(200)  NULL COMMENT '계정명(account_nm)',
                                `account_detail`     VARCHAR(200)  NULL COMMENT '계정 세부 구분(account_detail)',

                                `ord`                INT           NULL COMMENT '표시 순서(ord)',

                                `thstrm_nm`          VARCHAR(50)   NULL COMMENT '당기명(제 24 기 등, thstrm_nm)',
                                `thstrm_amount`      DECIMAL(28,0) NULL COMMENT '당기 금액(thstrm_amount, 원 단위 정수)',
                                `thstrm_add_amount`  DECIMAL(28,0) NULL COMMENT '당기 누계/추가 금액(thstrm_add_amount)',

                                `frmtrm_nm`          VARCHAR(50)   NULL COMMENT '전기명(frmtrm_nm)',
                                `frmtrm_amount`      DECIMAL(28,0) NULL COMMENT '전기 금액(frmtrm_amount)',

                                `bfefrmtrm_nm`       VARCHAR(50)   NULL COMMENT '전전기명(bfefrmtrm_nm)',
                                `bfefrmtrm_amount`   DECIMAL(28,0) NULL COMMENT '전전기 금액(bfefrmtrm_amount)',

                                `currency`           VARCHAR(3)    NULL COMMENT '통화(KRW, currency)',
                                `row_hash`           VARCHAR(64)   NULL COMMENT '원본 라인 해시(중복 방지/변경감지 등 선택)',
                                `raw_json`           TEXT          NULL COMMENT '원본 JSON 라인 전체(디버깅/재파싱용, 선택)',

                                `created_at`         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '행 생성시각',

                                CONSTRAINT `PK_DART_FS_LINE`   PRIMARY KEY (`fs_line_id`),

                                CONSTRAINT `FK_DFL_FILING`
                                    FOREIGN KEY (`fs_filing_id`) REFERENCES `dart_fs_filing`(`fs_filing_id`)
                                        ON DELETE CASCADE,

                                CONSTRAINT `FK_DFL_COMPANY`
                                    FOREIGN KEY (`company_id`)   REFERENCES `company`(`company_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='DART 재무제표 상세(계정 단위 원천 데이터)';


-- /* (회사 + 양식 + 계정) 기준으로 자주 조회할 가능성이 높음 */
-- CREATE INDEX `IX_DFL_COMPANY_SJ_ACC`
--     ON `dart_fs_line` (`company_id`, `sj_div`, `account_id`);
--
-- /* 한 공시 안에서 계정 라인을 빠르게 스캔하기 위한 인덱스 */
-- CREATE INDEX `IX_DFL_FILING_ORD`
--     ON `dart_fs_line` (`fs_filing_id`, `sj_div`, `ord`);

/* ===============================================================
 * 9-C) DART 계정코드 ↔ 내부 지표코드 매핑
 *   - dart_fs_line.account_id → fin_metric_def.metric_code 매핑용
 *   - SALES, NET_INC, TOTAL_EQUITY_OWNER, CFO, CFI, CFF 등으로 연결
 *   - 비즈니스 룰/매핑을 SQL로 관리하고 싶을 때 유용
 * =============================================================== */
CREATE TABLE `fs_account_map` (
      `account_id`   VARCHAR(200)  NOT NULL COMMENT 'DART account_id (ifrs-full_Revenue 등)',
      `sj_div`       VARCHAR(4)    NOT NULL COMMENT '재무제표 양식 구분(BS/CIS/CF 등)',
      `metric_code`  VARCHAR(32)   NOT NULL COMMENT 'FK: fin_metric_def.metric_code',
      `priority`     TINYINT       NOT NULL DEFAULT 1 COMMENT '여러 매핑 존재 시 우선순위(1=기본)',
      `notes`        VARCHAR(300)  NULL COMMENT '비고/매핑 기준 설명',
      `created_at`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성시각',
      `updated_at`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
          ON UPDATE CURRENT_TIMESTAMP COMMENT '수정시각',

      CONSTRAINT `PK_FS_ACCOUNT_MAP`
          PRIMARY KEY (`account_id`, `sj_div`, `metric_code`),

      CONSTRAINT `FK_FAM_METRIC`
          FOREIGN KEY (`metric_code`) REFERENCES `fin_metric_def`(`metric_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='DART 계정코드 ↔ 재무 지표코드 매핑 테이블';


-- CREATE INDEX `IX_FAM_METRIC`
--     ON `fs_account_map` (`metric_code`, `sj_div`);

/* ===============================================================
 * 9-D) (선택) 필수 재무제표 계정 마스터
 *   - "이 회사/이 공시에는 반드시 있어야 하는 계정" 정의
 *   - 누락 검증용 (LEFT JOIN으로 빠진 항목 체크)
 * =============================================================== */
CREATE TABLE `fs_required_account` (
       `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT 'PK',
       `sj_div`       VARCHAR(4)    NOT NULL COMMENT '재무제표 양식(BS/CIS/CF 등)',
       `account_id`   VARCHAR(200)  NOT NULL COMMENT '필수로 봐야 하는 DART account_id',
       `account_nm`   VARCHAR(200)  NULL COMMENT '계정명(설명용)',
       `is_required`  TINYINT(1)    NOT NULL DEFAULT 1 COMMENT '필수 여부(1=필수, 0=선택)',
       `notes`        VARCHAR(300)  NULL COMMENT '비고(적용 조건 등)',
       `created_at`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성시각',
       `updated_at`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
           ON UPDATE CURRENT_TIMESTAMP COMMENT '수정시각',

       CONSTRAINT `PK_FS_REQUIRED_ACCOUNT` PRIMARY KEY (`id`),
       CONSTRAINT `UNQ_FS_REQUIRED_ACC`    UNIQUE (`sj_div`, `account_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='필수 재무제표 계정 정의(BS/CIS/CF별 필수 계정 목록)';

-- 누락 조회 인덱스
-- CREATE INDEX IX_FS_REQ_SJ_REQ
--     ON fs_required_account (sj_div, is_required, account_id);

-- ===============================================================
-- 샘플 데이터 삽입
-- ===============================================================

-- -- 1) StockCode
-- INSERT INTO stock_code (ticker_krx, company_name, industry, listing_date, fiscal_year_end_month, market, region) VALUES
-- ('005930', '삼성전자',   '전기전자', '1975-06-11', 12, 'KOSPI', '경기'),
-- ('000660', 'SK하이닉스', '전기전자', '1996-12-26', 12, 'KOSPI', '경기'),
-- ('035420', 'NAVER',      '서비스업', '2002-10-29', 12, 'KOSPI', '경기'),
-- ('005380', '현대자동차', '운수장비', '1974-10-02', 12, 'KOSPI', '서울'),
-- ('051910', 'LG화학',     '화학',     '2001-04-27', 12, 'KOSPI', '서울');
--
-- -- 2) Company (삼성전자만)
-- INSERT INTO company (stock_id, shares_outstanding, face_value, currency, sector)
-- SELECT stock_id, 5969782550, 100, 'KRW', 'IT'
-- FROM stock_code WHERE ticker_krx = '005930';

-- 3) 재무 지표 정의
INSERT INTO fin_metric_def (metric_code, name_kor, unit, display_order, description) VALUES
    ('SALES',           '매출액',       'KRW',  10,  '연결 기준 매출액'),
    ('OP_INC',          '영업이익',     'KRW',  20,  '영업이익'),
    ('NET_INC',         '당기순이익',   'KRW',  30,  '지배주주 기준 당기순이익'),
    ('NET_INC_OWNER', '당기순이익(지배)', 'KRW', 31, '지배기업 소유주에게 귀속되는 당기순이익'),
    ('NET_INC_NONCONT', '당기순이익(비지배)', 'KRW', 32, '비지배지분에 귀속되는 당기순이익'),
    ('OPM',             '영업이익률',   '%',    40,  '영업이익 / 매출액'),
    ('DEBT_RATIO',      '부채비율',     '%',    50,  '부채총계 / 자본총계'),
    ('ROE',             'ROE',          '%',    60,  '당기순이익 / 자본총계(지배주주지분)'),
    ('ROA',             'ROA',          '%',    70,  '당기순이익 / 자산총계'),
    ('NET_MARGIN',      '순이익률',     '%',    80,  '당기순이익 / 매출액'),
    ('EPS',             'EPS',          'KRW',  90,  '주당순이익'),
    ('BPS',             'BPS',          'KRW', 100,  '주당순자산'),
    ('PER',             'PER',          '배',   110, '주가 / EPS'),
    ('PBR',             'PBR',          '배',   120, '주가 / BPS'),
    ('QUICK_RATIO',     '유동비율',     '%',   130, '유동자산 / 유동부채'),
    ('DPS',             'DPS',          'KRW', 140, '주당배당금'),
    ('DIVIDEND_YIELD',  '시가배당률',   '%',   150, 'DPS / 주가 * 100'),
    ('PAYOUT_RATIO',    '배당성향',     '%',   160, '배당금총액 / 당기순이익 * 100'),
    ('RETENTION_RATIO', '유보율',       '%',   170, '이익잉여금 / 자본총계 또는 (1-배당성향)'),
    ('TOTAL_EQUITY',     '자본총계',     'KRW',  180, '자본총계(지배주주지분 + 비지배주주지분)'),
    ('TOTAL_EQUITY_OWNER', '자본총계(지배)', 'KRW',185, '자본총계(지배주주지분, Equity attributable to owners of parent)');
-- 4) 삼성전자 재무 기간 (2020~2024)
-- INSERT INTO fin_period (company_id, period_type, fiscal_year, fiscal_quarter, is_estimate, label)
-- SELECT
--     c.company_id,
--     'YEAR',
--     y.yr,
--     NULL,
--     0,
--     CONCAT(y.yr, '/12')
-- FROM company c
-- JOIN stock_code sc ON sc.stock_id = c.stock_id AND sc.ticker_krx = '005930'
-- CROSS JOIN (
--     SELECT 2024 AS yr UNION ALL
--     SELECT 2023 UNION ALL
--     SELECT 2022 UNION ALL
--     SELECT 2021 UNION ALL
--     SELECT 2020
-- ) AS y;
--
-- -- 5) 삼성전자 재무 데이터 (2024년)
-- INSERT INTO fin_metric_value (company_id, period_id, metric_code, value_num, source)
-- SELECT
--     c.company_id,
--     p.period_id,
--     m.metric_code,
--     CASE m.metric_code
--         WHEN 'SALES'      THEN 302000000000000
--         WHEN 'OP_INC'     THEN  54000000000000
--         WHEN 'NET_INC'    THEN  35000000000000
--         WHEN 'OPM'        THEN 0.18
--         WHEN 'DEBT_RATIO' THEN 0.45
--         WHEN 'ROE'        THEN 0.085
--         WHEN 'ROA'        THEN 0.055
--         WHEN 'EPS'        THEN 5800
--         WHEN 'BPS'        THEN 68000
--         WHEN 'PER'        THEN 12.5
--         WHEN 'PBR'        THEN 1.1
--     END,
--     'MANUAL'
-- FROM company c
-- JOIN stock_code sc ON sc.stock_id = c.stock_id AND sc.ticker_krx = '005930'
-- JOIN fin_period p ON p.company_id = c.company_id AND p.fiscal_year = 2024 AND p.period_type = 'YEAR'
-- JOIN fin_metric_def m ON m.metric_code IN ('SALES','OP_INC','NET_INC','OPM','DEBT_RATIO','ROE','ROA','EPS','BPS','PER','PBR');
--
-- -- 6) 삼성전자 재무 데이터 (2023년)
-- INSERT INTO fin_metric_value (company_id, period_id, metric_code, value_num, source)
-- SELECT
--     c.company_id,
--     p.period_id,
--     m.metric_code,
--     CASE m.metric_code
--         WHEN 'SALES'      THEN 258000000000000
--         WHEN 'OP_INC'     THEN  62000000000000
--         WHEN 'NET_INC'    THEN  41000000000000
--         WHEN 'OPM'        THEN 0.24
--         WHEN 'DEBT_RATIO' THEN 0.42
--         WHEN 'ROE'        THEN 0.095
--         WHEN 'ROA'        THEN 0.062
--         WHEN 'EPS'        THEN 6850
--         WHEN 'BPS'        THEN 72000
--         WHEN 'PER'        THEN 11.2
--         WHEN 'PBR'        THEN 1.05
--     END,
--     'MANUAL'
-- FROM company c
-- JOIN stock_code sc ON sc.stock_id = c.stock_id AND sc.ticker_krx = '005930'
-- JOIN fin_period p ON p.company_id = c.company_id AND p.fiscal_year = 2023 AND p.period_type = 'YEAR'
-- JOIN fin_metric_def m ON m.metric_code IN ('SALES','OP_INC','NET_INC','OPM','DEBT_RATIO','ROE','ROA','EPS','BPS','PER','PBR');
--
-- -- 7) 삼성전자 재무 데이터 (2022년)
-- INSERT INTO fin_metric_value (company_id, period_id, metric_code, value_num, source)
-- SELECT
--     c.company_id,
--     p.period_id,
--     m.metric_code,
--     CASE m.metric_code
--         WHEN 'SALES'      THEN 302000000000000
--         WHEN 'OP_INC'     THEN  43000000000000
--         WHEN 'NET_INC'    THEN  55000000000000
--         WHEN 'OPM'        THEN 0.14
--         WHEN 'DEBT_RATIO' THEN 0.38
--         WHEN 'ROE'        THEN 0.125
--         WHEN 'ROA'        THEN 0.075
--         WHEN 'EPS'        THEN 9200
--         WHEN 'BPS'        THEN 74000
--         WHEN 'PER'        THEN  8.2
--         WHEN 'PBR'        THEN  1.02
--     END,
--     'MANUAL'
-- FROM company c
-- JOIN stock_code sc ON sc.stock_id = c.stock_id AND sc.ticker_krx = '005930'
-- JOIN fin_period p ON p.company_id = c.company_id AND p.fiscal_year = 2022 AND p.period_type = 'YEAR'
-- JOIN fin_metric_def m ON m.metric_code IN ('SALES','OP_INC','NET_INC','OPM','DEBT_RATIO','ROE','ROA','EPS','BPS','PER','PBR');
-- 추가: 회사채 수익률 데이터
INSERT INTO bond_yield_curve (as_of, rating, tenor_months, yield_rate, source) VALUES
-- 2024-11-06 기준 5년물 수익률 (표의 5년 컬럼 값 / 100)
('2024-11-06', 'KTB',  60, 0.0323, 'KOFIA'),  -- 국고채 3.23%
('2024-11-06', 'AAA',  60, 0.0355, 'KOFIA'),  -- 3.55%
('2024-11-06', 'AA+',  60, 0.0362, 'KOFIA'),  -- 3.62%
('2024-11-06', 'AA',   60, 0.0369, 'KOFIA'),  -- 3.69%
('2024-11-06', 'AA-',  60, 0.0379, 'KOFIA'),  -- 3.79%
('2024-11-06', 'A+',   60, 0.0451, 'KOFIA'),  -- 4.51%
('2024-11-06', 'A',    60, 0.0495, 'KOFIA'),  -- 4.95%
('2024-11-06', 'A-',   60, 0.0555, 'KOFIA'),  -- 5.55%
('2024-11-06', 'BBB+', 60, 0.0744, 'KOFIA'),  -- 7.44%
('2024-11-06', 'BBB',  60, 0.0849, 'KOFIA'),  -- 8.49%
('2024-11-06', 'BBB-', 60, 0.0991, 'KOFIA');  -- 9.91%