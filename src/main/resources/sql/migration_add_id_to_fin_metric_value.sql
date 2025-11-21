-- ===============================================================
-- fin_metric_value 테이블 마이그레이션
-- 복합 기본키를 AUTO_INCREMENT ID로 변경
-- ===============================================================

USE srimdb;

-- 1. 기존 기본키 제약조건 삭제
ALTER TABLE `fin_metric_value` 
DROP PRIMARY KEY;

-- 2. ID 컬럼 추가 (AUTO_INCREMENT)
ALTER TABLE `fin_metric_value` 
ADD COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK: 내부 식별자' FIRST,
ADD PRIMARY KEY (`id`);

-- 3. 기존 복합키를 유니크 인덱스로 변경
ALTER TABLE `fin_metric_value`
ADD CONSTRAINT `UN_FIN_METRIC_VALUE` UNIQUE (`company_id`, `period_id`, `metric_code`);

-- 마이그레이션 완료
SELECT '마이그레이션 완료: fin_metric_value 테이블에 AUTO_INCREMENT ID가 추가되었습니다.' AS message;
