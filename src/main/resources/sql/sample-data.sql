-- ===============================================================
-- 샘플 데이터 삽입
-- ===============================================================

-- 1) StockCode
INSERT INTO stock_code (ticker_krx, company_name, industry, listing_date, fiscal_year_end_month, market, region) VALUES
                                                                                                                     ('005930', '삼성전자',   '전기전자', '1975-06-11', 12, 'KOSPI', '경기'),
                                                                                                                     ('000660', 'SK하이닉스', '전기전자', '1996-12-26', 12, 'KOSPI', '경기'),
                                                                                                                     ('035420', 'NAVER',      '서비스업', '2002-10-29', 12, 'KOSPI', '경기'),
                                                                                                                     ('005380', '현대자동차', '운수장비', '1974-10-02', 12, 'KOSPI', '서울'),
                                                                                                                     ('051910', 'LG화학',     '화학',     '2001-04-27', 12, 'KOSPI', '서울');

-- 2) Company (삼성전자만)
INSERT INTO company (stock_id, shares_outstanding, face_value, currency, sector)
SELECT stock_id, 5969782550, 100, 'KRW', 'IT'
FROM stock_code WHERE ticker_krx = '005930';

-- 3) 재무 지표 정의
INSERT INTO fin_metric_def (metric_code, name_kor, unit, display_order, description) VALUES
                                                                                         ('SALES',      '매출액',      'KRW',  10, '연결 기준 매출액'),
                                                                                         ('OP_INC',     '영업이익',    'KRW',  20, '영업이익'),
                                                                                         ('NET_INC',    '당기순이익',  'KRW',  30, '지배주주 기준 당기순이익'),
                                                                                         ('OPM',        '영업이익률',  '%',    40, '영업이익 / 매출액'),
                                                                                         ('DEBT_RATIO', '부채비율',    '%',    50, '부채총계 / 자본총계'),
                                                                                         ('ROE',        '',         '%',    60, '당기순이익 / 자본총계(지배주주지분)'),
                                                                                         ('ROA',        'ROA',         '%',    70, '당기순이익 / 자산총계'),
                                                                                         ('EPS',        'EPS',         'KRW',  90, '주당순이익'),
                                                                                         ('BPS',        'BPS',         'KRW', 100, '주당순자산'),
                                                                                         ('PER',        'PER',         '배',  110, '주가 / EPS'),
                                                                                         ('PBR',        'PBR',         '배',  120, '주가 / BPS');

-- 4) 삼성전자 재무 기간 (2020~2024)
INSERT INTO fin_period (company_id, period_type, fiscal_year, fiscal_quarter, is_estimate, label)
SELECT
    c.company_id,
    'YEAR',
    y.yr,
    NULL,
    0,
    CONCAT(y.yr, '/12')
FROM company c
         JOIN stock_code sc ON sc.stock_id = c.stock_id AND sc.ticker_krx = '005930'
         CROSS JOIN (
    SELECT 2024 AS yr UNION ALL
    SELECT 2023 UNION ALL
    SELECT 2022 UNION ALL
    SELECT 2021 UNION ALL
    SELECT 2020
) AS y;

-- 5) 삼성전자 재무 데이터 (2024년)
INSERT INTO fin_metric_value (company_id, period_id, metric_code, value_num, source)
SELECT
    c.company_id,
    p.period_id,
    m.metric_code,
    CASE m.metric_code
        WHEN 'SALES'      THEN 302000000000000
        WHEN 'OP_INC'     THEN  54000000000000
        WHEN 'NET_INC'    THEN  35000000000000
        WHEN 'OPM'        THEN 0.18
        WHEN 'DEBT_RATIO' THEN 0.45
        WHEN ''        THEN 0.085
        WHEN 'ROA'        THEN 0.055
        WHEN 'EPS'        THEN 5800
        WHEN 'BPS'        THEN 68000
        WHEN 'PER'        THEN 12.5
        WHEN 'PBR'        THEN 1.1
        END,
    'MANUAL'
FROM company c
         JOIN stock_code sc ON sc.stock_id = c.stock_id AND sc.ticker_krx = '005930'
         JOIN fin_period p ON p.company_id = c.company_id AND p.fiscal_year = 2024 AND p.period_type = 'YEAR'
         JOIN fin_metric_def m ON m.metric_code IN ('SALES','OP_INC','NET_INC','OPM','DEBT_RATIO','ROE','ROA','EPS','BPS','PER','PBR');

-- 6) 삼성전자 재무 데이터 (2023년)
INSERT INTO fin_metric_value (company_id, period_id, metric_code, value_num, source)
SELECT
    c.company_id,
    p.period_id,
    m.metric_code,
    CASE m.metric_code
        WHEN 'SALES'      THEN 258000000000000
        WHEN 'OP_INC'     THEN  62000000000000
        WHEN 'NET_INC'    THEN  41000000000000
        WHEN 'OPM'        THEN 0.24
        WHEN 'DEBT_RATIO' THEN 0.42
        WHEN 'ROE'        THEN 0.095
        WHEN 'ROA'        THEN 0.062
        WHEN 'EPS'        THEN 6850
        WHEN 'BPS'        THEN 72000
        WHEN 'PER'        THEN 11.2
        WHEN 'PBR'        THEN 1.05
        END,
    'MANUAL'
FROM company c
         JOIN stock_code sc ON sc.stock_id = c.stock_id AND sc.ticker_krx = '005930'
         JOIN fin_period p ON p.company_id = c.company_id AND p.fiscal_year = 2023 AND p.period_type = 'YEAR'
         JOIN fin_metric_def m ON m.metric_code IN ('SALES','OP_INC','NET_INC','OPM','DEBT_RATIO','ROE','ROA','EPS','BPS','PER','PBR');

-- 7) 삼성전자 재무 데이터 (2022년)
INSERT INTO fin_metric_value (company_id, period_id, metric_code, value_num, source)
SELECT
    c.company_id,
    p.period_id,
    m.metric_code,
    CASE m.metric_code
        WHEN 'SALES'      THEN 302000000000000
        WHEN 'OP_INC'     THEN  43000000000000
        WHEN 'NET_INC'    THEN  55000000000000
        WHEN 'OPM'        THEN 0.14
        WHEN 'DEBT_RATIO' THEN 0.38
        WHEN 'ROE'        THEN 0.125
        WHEN 'ROA'        THEN 0.075
        WHEN 'EPS'        THEN 9200
        WHEN 'BPS'        THEN 74000
        WHEN 'PER'        THEN  8.2
        WHEN 'PBR'        THEN  1.02
        END,
    'MANUAL'
FROM company c
         JOIN stock_code sc ON sc.stock_id = c.stock_id AND sc.ticker_krx = '005930'
         JOIN fin_period p ON p.company_id = c.company_id AND p.fiscal_year = 2022 AND p.period_type = 'YEAR'
         JOIN fin_metric_def m ON m.metric_code IN ('SALES','OP_INC','NET_INC','OPM','DEBT_RATIO','ROE','ROA','EPS','BPS','PER','PBR');
