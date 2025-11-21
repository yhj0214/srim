/* 1) 종목 코드 마스터 */
CREATE TABLE `stock_code` (
                              `stock_id`               BIGINT        NOT NULL AUTO_INCREMENT COMMENT 'PK: 내부 식별자(숫자형)',
                              `ticker_krx`             VARCHAR(6)    NOT NULL COMMENT 'KRX 6자리 종목코드(예: 005930)',
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
                                    `id`           BIGINT         NOT NULL AUTO_INCREMENT COMMENT 'PK: 내부 식별자',
                                    `company_id`   BIGINT         NOT NULL COMMENT 'FK → company.company_id',
                                    `period_id`    BIGINT         NOT NULL COMMENT 'FK → fin_period.period_id',
                                    `metric_code`  VARCHAR(32)    NOT NULL COMMENT 'FK → fin_metric_def.metric_code',
                                    `value_num`    DECIMAL(28,6)  NULL COMMENT '지표 수치(소수/원 단위 통일 저장)',
                                    `source`       VARCHAR(20)    NULL COMMENT '수집원(KRX/NAVER/FNG/CSV/MANUAL)',
                                    `updated_at`   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '갱신 시각',
                                    CONSTRAINT `PK_FIN_METRIC_VALUE` PRIMARY KEY (`id`),
                                    CONSTRAINT `UN_FIN_METRIC_VALUE` UNIQUE (`company_id`, `period_id`, `metric_code`),
                                    CONSTRAINT `FK_FMV_COMPANY`    FOREIGN KEY (`company_id`)  REFERENCES `company`(`company_id`),
                                    CONSTRAINT `FK_FMV_PERIOD`     FOREIGN KEY (`period_id`)   REFERENCES `fin_period`(`period_id`),
                                    CONSTRAINT `FK_FMV_METRIC`     FOREIGN KEY (`metric_code`) REFERENCES `fin_metric_def`(`metric_code`),
                                    CONSTRAINT `CK_FMV_SOURCE`     CHECK (`source` IS NULL OR `source` IN ('KRX','NAVER','FNG','CSV','MANUAL'))
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
