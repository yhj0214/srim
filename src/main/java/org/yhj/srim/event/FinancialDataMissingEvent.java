package org.yhj.srim.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 재무 데이터 누락 시 발생하는 이벤트
 */
@Getter
public class FinancialDataMissingEvent extends ApplicationEvent {
    
    private final Long companyId;
    private final String tickerKrx;
    
    public FinancialDataMissingEvent(Object source, Long companyId, String tickerKrx) {
        super(source);
        this.companyId = companyId;
        this.tickerKrx = tickerKrx;
    }
}
