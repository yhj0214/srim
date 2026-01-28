package org.yhj.srim.fixture;

import org.yhj.srim.client.dto.DartFsRow;

import java.math.BigDecimal;
import java.util.List;

public class CrawlFixture {

    public static List<DartFsRow> createFsRows(){
        // 1) 유동자산 (ord=1)
        DartFsRow r1 = new DartFsRow();
        r1.setRceptNo("20220308000798");
        r1.setReprtCode("11011");
        r1.setBsnsYear(2021);
        r1.setFsDiv(null);
        r1.setRceptDt(null);
        r1.setSjDiv("BS");
        r1.setSjNm("재무상태표");
        r1.setAccountId("ifrs-full_CurrentAssets");
        r1.setAccountNm("유동자산");
        r1.setAccountDetail(null);
        r1.setOrd(1);
        r1.setThstrmNm("제 53 기");
        r1.setThstrmAmount(new BigDecimal("218163185000000"));
        r1.setThstrmAddAmount(null);
        r1.setFrmtrmNm("제 52 기");
        r1.setFrmtrmAmount(new BigDecimal("198215579000000"));
        r1.setBfefrmtrmNm("제 51 기");
        r1.setBfefrmtrmAmount(new BigDecimal("181385260000000"));
        r1.setCurrency("KRW");
        r1.setRawJson("{\"rcept_no\":\"20220308000798\",\"reprt_code\":\"11011\",\"bsns_year\":\"2021\",\"corp_code\":\"00126380\",\"sj_div\":\"BS\",\"sj_nm\":\"재무상태표\",\"account_id\":\"ifrs-full_CurrentAssets\",\"account_nm\":\"유동자산\",\"account_detail\":\"-\",\"thstrm_nm\":\"제 53 기\",\"thstrm_amount\":\"218163185000000\",\"frmtrm_nm\":\"제 52 기\",\"frmtrm_amount\":\"198215579000000\",\"bfefrmtrm_nm\":\"제 51 기\",\"bfefrmtrm_amount\":\"181385260000000\",\"ord\":\"1\",\"currency\":\"KRW\"}");

        // 2) 유동자산 (ord=1) - 로그에 동일 row가 한 번 더 찍힌 케이스 반영
        DartFsRow r2 = new DartFsRow();
        r2.setRceptNo("20220308000798");
        r2.setReprtCode("11011");
        r2.setBsnsYear(2021);
        r2.setFsDiv(null);
        r2.setRceptDt(null);
        r2.setSjDiv("BS");
        r2.setSjNm("재무상태표");
        r2.setAccountId("ifrs-full_CurrentAssets");
        r2.setAccountNm("유동자산");
        r2.setAccountDetail(null);
        r2.setOrd(1);
        r2.setThstrmNm("제 53 기");
        r2.setThstrmAmount(new BigDecimal("218163185000000"));
        r2.setThstrmAddAmount(null);
        r2.setFrmtrmNm("제 52 기");
        r2.setFrmtrmAmount(new BigDecimal("198215579000000"));
        r2.setBfefrmtrmNm("제 51 기");
        r2.setBfefrmtrmAmount(new BigDecimal("181385260000000"));
        r2.setCurrency("KRW");
        r2.setRawJson("{\"rcept_no\":\"20220308000798\",\"reprt_code\":\"11011\",\"bsns_year\":\"2021\",\"corp_code\":\"00126380\",\"sj_div\":\"BS\",\"sj_nm\":\"재무상태표\",\"account_id\":\"ifrs-full_CurrentAssets\",\"account_nm\":\"유동자산\",\"account_detail\":\"-\",\"thstrm_nm\":\"제 53 기\",\"thstrm_amount\":\"218163185000000\",\"frmtrm_nm\":\"제 52 기\",\"frmtrm_amount\":\"198215579000000\",\"bfefrmtrm_nm\":\"제 51 기\",\"bfefrmtrm_amount\":\"181385260000000\",\"ord\":\"1\",\"currency\":\"KRW\"}");

        // 3) 현금및현금성자산 (ord=2)
        DartFsRow r3 = new DartFsRow();
        r3.setRceptNo("20220308000798");
        r3.setReprtCode("11011");
        r3.setBsnsYear(2021);
        r3.setFsDiv(null);
        r3.setRceptDt(null);
        r3.setSjDiv("BS");
        r3.setSjNm("재무상태표");
        r3.setAccountId("ifrs-full_CashAndCashEquivalents");
        r3.setAccountNm("현금및현금성자산");
        r3.setAccountDetail(null);
        r3.setOrd(2);
        r3.setThstrmNm("제 53 기");
        r3.setThstrmAmount(new BigDecimal("39031415000000"));
        r3.setThstrmAddAmount(null);
        r3.setFrmtrmNm("제 52 기");
        r3.setFrmtrmAmount(new BigDecimal("29382578000000"));
        r3.setBfefrmtrmNm("제 51 기");
        r3.setBfefrmtrmAmount(new BigDecimal("26885999000000"));
        r3.setCurrency("KRW");
        r3.setRawJson("{\"rcept_no\":\"20220308000798\",\"reprt_code\":\"11011\",\"bsns_year\":\"2021\",\"corp_code\":\"00126380\",\"sj_div\":\"BS\",\"sj_nm\":\"재무상태표\",\"account_id\":\"ifrs-full_CashAndCashEquivalents\",\"account_nm\":\"현금및현금성자산\",\"account_detail\":\"-\",\"thstrm_nm\":\"제 53 기\",\"thstrm_amount\":\"39031415000000\",\"frmtrm_nm\":\"제 52 기\",\"frmtrm_amount\":\"29382578000000\",\"bfefrmtrm_nm\":\"제 51 기\",\"bfefrmtrm_amount\":\"26885999000000\",\"ord\":\"2\",\"currency\":\"KRW\"}");

        return List.of(r1, r2, r3);

    }

}
