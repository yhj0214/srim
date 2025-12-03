/**
 * 재무정보 관련 로직
 */

const StockFinancial = {
    
    renderTable: function(data) {
        console.log('=== renderFinancialTable 호출 ===');
        
        if (!data.headers || data.headers.length === 0) {
            this.showError('재무 데이터가 없습니다.');
            return;
        }

        const reversedHeaders = [...data.headers].reverse();
        
        let html = '<div class="financial-table-wrapper">';
        html += '<table class="table table-sm table-bordered financial-table">';
        
        html += '<thead class="table-light"><tr>';
        html += '<th style="min-width: 200px; width: 200px; white-space: nowrap;">지표</th>';
        reversedHeaders.forEach(header => {
            html += `<th class="text-end" style="min-width: 150px; width: 150px; white-space: nowrap;">${header.label}${header.isEstimate ? '(E)' : ''}</th>`;
        });
        html += '</tr></thead>';
        
        html += '<tbody>';
        data.rows.forEach(row => {
            html += '<tr>';
            html += `<td style="min-width: 200px; width: 200px; white-space: nowrap;"><strong>${row.metricName}</strong></td>`;
            reversedHeaders.forEach(header => {
                const value = row.values[header.periodId];
                html += `<td class="text-end" style="min-width: 150px; width: 150px; white-space: nowrap;">${this.formatValue(value, row.unit)}</td>`;
            });
            html += '</tr>';
        });
        html += '</tbody></table></div>';

        // 재무정보 탭 컨테이너에 표시
        const container = document.getElementById('financialTableContainer');
        if (container) {
            container.innerHTML = html;
            console.log('✅ 재무정보 테이블 렌더링 완료');
        } else {
            console.error('❌ financialTableContainer를 찾을 수 없습니다');
        }
    },
    
    formatValue: function(value, unit) {
        if (value == null) return '-';
        
        const num = parseFloat(value);
        
        if (unit === 'KRW' || unit === '원') {
            return (num).toLocaleString('ko-KR', {maximumFractionDigits: 0}) + '억';
        } else if (unit === '%') {
            return (num).toFixed(2) + '%';
        } else if (unit === '배') {
            return num.toFixed(2) + '배';
        } else {
            return num.toLocaleString('ko-KR', {maximumFractionDigits: 2});
        }
    },
    
    showLoading: function() {
        const container = document.getElementById('financialTableContainer');
        if (container) {
            container.innerHTML = `
                <div class="text-center py-5">
                    <div class="spinner-border text-primary" role="status">
                        <span class="visually-hidden">로딩중...</span>
                    </div>
                    <p class="mt-3 text-muted">재무 데이터를 불러오는 중입니다...</p>
                    <p class="text-muted"><small>데이터가 없으면 자동으로 크롤링을 시작합니다.</small></p>
                </div>
            `;
        }
    },
    
    showError: function(message) {
        const container = document.getElementById('financialTableContainer');
        if (container) {
            container.innerHTML = `
                <div class="alert alert-warning">
                    <i class="bi bi-exclamation-triangle"></i> ${message}
                </div>
            `;
        }
    },
    
    getMetric: function(metricName, periodIndex = 0) {
        if (!StockDetail.financialData || !StockDetail.financialData.rows) return null;
        
        const row = StockDetail.financialData.rows.find(r => r.metricName === metricName);
        if (!row) return null;
        
        const headers = [...StockDetail.financialData.headers].reverse();
        if (periodIndex >= headers.length) return null;
        
        const periodId = headers[periodIndex].periodId;
        return row.values[periodId];
    }
};
