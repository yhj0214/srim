package org.yhj.srim.service.domain;

public enum MetricStage {

    BASE,
    DERIVED,
    PER_SHARE,
    MARKET


    /**
     * 원천치표 BASE
     * - SALES
     * - OP_INC
     * - NET_INC
     * - NET_INC_OWNER
     * - NET_INC_NONCONT
     * - TOTAL_EQUITY
     * - TOTAL_EQUITY_OWNER
     *
     * 원천값 조합 DERIVED
     * - OPM 영업이익/매출액
     * - NET_MARGIN 당기순이익/매출액
     * - DEBT_RATIO 부채총계/자본총계
     * - ROE 순이익 / 평균자본
     * - ROA 당기순이익 / 자산총계
     * - QUICK_RATIO 유동자산 / 유동부채
     *
     * 주당지표 PER_SHARE
     * - EPS
     * - BPS
     *
     * 주식가격과 주당지표 MARKET
     * - PER
     * - PBR
     */


}
