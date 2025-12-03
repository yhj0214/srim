/**
 * S-RIM 계산 관련 로직
 */

const StockSrim = {
    chart: null,
    
    calculate: function(companyId) {
        if (!companyId) {
            this.showError('회사 정보가 아직 등록되지 않았습니다.');
            return;
        }

        this.showLoading();

        const url = `/api/stocks/${companyId}/srim?basis=YEAR`;
        console.log('S-RIM API 호출:', url);
        
        fetch(url)
            .then(response => {
                console.log('S-RIM 응답 상태:', response.status);
                if (!response.ok) {
                    throw new Error(`HTTP error! status: ${response.status}`);
                }
                return response.json();
            })
            .then(result => {
                console.log('S-RIM 응답:', result);
                if (result.success) {
                    StockDetail.srimResult = result.data;
                    this.renderResult(result.data);
                } else {
                    this.showError(result.message || 'S-RIM 계산에 실패했습니다.');
                }
            })
            .catch(error => {
                console.error('S-RIM Error:', error);
                this.showError('네트워크 오류가 발생했습니다: ' + error.message);
            });
    },
    
    renderResult: function(data) {
        console.log('=== renderSrimResult 호출 ===');
        console.log('data:', data);
        
        try {
            const equity2022 = StockFinancial.getMetric('지배주주지분', 2) || '???';
            const equity2023 = StockFinancial.getMetric('지배주주지분', 1) || '???';
            const equity2024 = StockFinancial.getMetric('지배주주지분', 0) || '???';
            
            const roe2022 = StockFinancial.getMetric('ROE', 2) || '???';
            const roe2023 = StockFinancial.getMetric('ROE', 1) || '???';
            const roe2024 = StockFinancial.getMetric('ROE', 0) || '???';

            const html = `
                <div class="row g-3">
                    <div class="col-md-4">
                        <div class="card h-100">
                            <div class="card-body">
                                <div class="d-flex justify-content-between align-items-center mb-4">
                                    <h5 class="mb-0">적정주가 요약</h5>
                                    <button class="btn btn-sm btn-primary" onclick="StockSrim.calculate(StockDetail.companyId)">
                                        <i class="bi bi-calculator"></i> 재계산
                                    </button>
                                </div>
                                
                                <div class="mb-4">
                                    <h6 class="text-muted mb-3">적정주가</h6>
                                    <div class="mb-2">
                                        <small class="text-muted">적정주가 (초과이익 지속시)</small>
                                        <h5 class="mb-0 text-primary">${formatNumber(data.scenarios[0].fairValuePerShare)}원</h5>
                                    </div>
                                    <div class="mb-2">
                                        <small class="text-muted">적정주가 (10% 감소시)</small>
                                        <h6 class="mb-0">${formatNumber(data.scenarios[1].fairValuePerShare)}원</h6>
                                    </div>
                                    <div class="mb-2">
                                        <small class="text-muted">적정주가 (20% 감소시)</small>
                                        <h6 class="mb-0">${formatNumber(data.scenarios[2].fairValuePerShare)}원</h6>
                                    </div>
                                    <div class="mb-2">
                                        <small class="text-muted">적정주가 (30% 감소시)</small>
                                        <h6 class="mb-0">${formatNumber(data.scenarios[3].fairValuePerShare)}원</h6>
                                    </div>
                                    <div class="mb-2">
                                        <small class="text-muted">적정주가 (50% 감소시)</small>
                                        <h6 class="mb-0">${formatNumber(data.scenarios[4].fairValuePerShare)}원</h6>
                                    </div>
                                    <div class="mt-3">
                                        <small class="text-muted">적정주가 대비 괴리율</small>
                                        <h6 class="text-primary mb-0">???%</h6>
                                    </div>
                                </div>
                                
                                <div class="border-top pt-3 mt-3 small">
                                    <div class="d-flex justify-content-between mb-1">
                                        <span class="text-muted">시가총액</span>
                                        <span>???원</span>
                                    </div>
                                    <div class="d-flex justify-content-between mb-1">
                                        <span class="text-muted">발행 주식 수</span>
                                        <span>${formatLargeNumber(data.sharesOutstanding)}</span>
                                    </div>
                                    <div class="d-flex justify-content-between mb-1">
                                        <span class="text-muted">지급 주식 수</span>
                                        <span>???</span>
                                    </div>
                                    <div class="d-flex justify-content-between mb-1">
                                        <span class="text-muted">유통 주식 수</span>
                                        <span>???</span>
                                    </div>
                                </div>
                                
                                <div class="border-top pt-3 mt-3 small">
                                    <h6 class="mb-3">지배주주지분 (연평균)</h6>
                                    <div class="d-flex justify-content-between mb-1">
                                        <span class="text-muted">2022/12</span>
                                        <span>${equity2022 === '???' ? '???' : formatNumber(equity2022)}</span>
                                    </div>
                                    <div class="d-flex justify-content-between mb-1">
                                        <span class="text-muted">2023/12</span>
                                        <span>${equity2023 === '???' ? '???' : formatNumber(equity2023)}</span>
                                    </div>
                                    <div class="d-flex justify-content-between mb-1">
                                        <span class="text-muted">2024/12</span>
                                        <span>${equity2024 === '???' ? '???' : formatNumber(equity2024)}</span>
                                    </div>
                                </div>
                                
                                <div class="border-top pt-3 mt-3 small">
                                    <h6 class="mb-3">3년 가중평균 ROE (%)</h6>
                                    <div class="d-flex justify-content-between mb-1">
                                        <span class="text-muted">2022/12</span>
                                        <span class="${roe2022 > 10 ? 'text-danger' : ''}">${roe2022 === '???' ? '???' : formatPercent(roe2022)}</span>
                                    </div>
                                    <div class="d-flex justify-content-between mb-1">
                                        <span class="text-muted">2023/12</span>
                                        <span class="${roe2023 > 10 ? 'text-danger' : ''}">${roe2023 === '???' ? '???' : formatPercent(roe2023)}</span>
                                    </div>
                                    <div class="d-flex justify-content-between mb-1">
                                        <span class="text-muted">2024/12</span>
                                        <span>${roe2024 === '???' ? '???' : formatPercent(roe2024)}</span>
                                    </div>
                                </div>
                                
                                <div class="border-top pt-3 mt-3">
                                    <div class="d-flex justify-content-between mb-1">
                                        <span class="text-muted">회사채 수익률 (%)</span>
                                        <span>${formatPercent(data.ke)}</span>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                    
                    <div class="col-md-8">
                        <div class="card">
                            <div class="card-header">
                                <h6 class="mb-0">
                                    <i class="bi bi-bar-chart"></i> 시나리오별 적정주가 비교
                                </h6>
                            </div>
                            <div class="card-body">
                                <canvas id="srimChart" height="120"></canvas>
                            </div>
                        </div>
                        
                        <div class="card mt-3">
                            <div class="card-header">
                                <h6 class="mb-0">시나리오별 비교표</h6>
                            </div>
                            <div class="card-body p-0">
                                <div class="table-responsive">
                                    <table class="table table-sm table-hover mb-0">
                                        <thead class="table-light">
                                            <tr>
                                                <th>시나리오</th>
                                                <th class="text-end">감소율</th>
                                                <th class="text-end">초과이익</th>
                                                <th class="text-end">기업가치</th>
                                                <th class="text-end">적정주가</th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            <tr>
                                                <td><strong>기준 (0%)</strong></td>
                                                <td class="text-end">0%</td>
                                                <td class="text-end">${formatBillion(data.scenarios[0].excessEarnings)}</td>
                                                <td class="text-end">${formatBillion(data.scenarios[0].enterpriseValue)}</td>
                                                <td class="text-end"><strong>${formatNumber(data.scenarios[0].fairValuePerShare)}원</strong></td>
                                            </tr>
                                            <tr>
                                                <td><strong>보수적 (-10%)</strong></td>
                                                <td class="text-end">-10%</td>
                                                <td class="text-end">${formatBillion(data.scenarios[1].excessEarnings)}</td>
                                                <td class="text-end">${formatBillion(data.scenarios[1].enterpriseValue)}</td>
                                                <td class="text-end"><strong>${formatNumber(data.scenarios[1].fairValuePerShare)}원</strong></td>
                                            </tr>
                                            <tr>
                                                <td><strong>매우 보수적 (-20%)</strong></td>
                                                <td class="text-end">-20%</td>
                                                <td class="text-end">${formatBillion(data.scenarios[2].excessEarnings)}</td>
                                                <td class="text-end">${formatBillion(data.scenarios[2].enterpriseValue)}</td>
                                                <td class="text-end"><strong>${formatNumber(data.scenarios[2].fairValuePerShare)}원</strong></td>
                                            </tr>
                                            <tr>
                                                <td><strong>비관적 (-30%)</strong></td>
                                                <td class="text-end">-30%</td>
                                                <td class="text-end">${formatBillion(data.scenarios[3].excessEarnings)}</td>
                                                <td class="text-end">${formatBillion(data.scenarios[3].enterpriseValue)}</td>
                                                <td class="text-end"><strong>${formatNumber(data.scenarios[3].fairValuePerShare)}원</strong></td>
                                            </tr>
                                            <tr>
                                                <td><strong>극단적 (-50%)</strong></td>
                                                <td class="text-end">-50%</td>
                                                <td class="text-end">${formatBillion(data.scenarios[4].excessEarnings)}</td>
                                                <td class="text-end">${formatBillion(data.scenarios[4].enterpriseValue)}</td>
                                                <td class="text-end"><strong>${formatNumber(data.scenarios[4].fairValuePerShare)}원</strong></td>
                                            </tr>
                                        </tbody>
                                    </table>
                                </div>
                            </div>
                        </div>
                        
                        <div class="alert alert-info mt-3">
                            <small>
                                <i class="bi bi-info-circle"></i>
                                <strong>계산 방식:</strong> 
                                기업가치 = 자기자본 + (초과이익 / Ke), 
                                초과이익 = 자기자본 × (ROE - Ke) × (1 + 감소율)
                            </small>
                        </div>
                    </div>
                </div>
            `;

            document.getElementById('srimResultContainer').innerHTML = html;
            
            this.renderChart(data);
        } catch (error) {
            console.error('renderSrimResult 에러:', error);
            this.showError('결과를 표시하는 중 오류가 발생했습니다: ' + error.message);
        }
    },
    
    renderChart: function(data) {
        try {
            const ctx = document.getElementById('srimChart');
            
            if (this.chart) {
                this.chart.destroy();
            }

            const labels = ['지속시', '10% 감소', '20% 감소', '30% 감소', '50% 감소'];
            const values = data.scenarios.map(s => parseFloat(s.fairValuePerShare));

            this.chart = new Chart(ctx, {
                type: 'bar',
                data: {
                    labels: labels,
                    datasets: [{
                        label: '적정주가 (원)',
                        data: values,
                        backgroundColor: [
                            'rgba(54, 162, 235, 0.8)',
                            'rgba(75, 192, 192, 0.8)',
                            'rgba(255, 206, 86, 0.8)',
                            'rgba(255, 159, 64, 0.8)',
                            'rgba(255, 99, 132, 0.8)'
                        ],
                        borderColor: [
                            'rgba(54, 162, 235, 1)',
                            'rgba(75, 192, 192, 1)',
                            'rgba(255, 206, 86, 1)',
                            'rgba(255, 159, 64, 1)',
                            'rgba(255, 99, 132, 1)'
                        ],
                        borderWidth: 1
                    }]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: true,
                    plugins: {
                        legend: {
                            display: false
                        },
                        tooltip: {
                            callbacks: {
                                label: function(context) {
                                    return '적정주가: ' + context.parsed.y.toLocaleString('ko-KR') + '원';
                                }
                            }
                        }
                    },
                    scales: {
                        y: {
                            beginAtZero: false,
                            ticks: {
                                callback: function(value) {
                                    return value.toLocaleString('ko-KR') + '원';
                                }
                            }
                        }
                    }
                }
            });
        } catch (error) {
            console.error('renderSrimChart 에러:', error);
        }
    },
    
    showLoading: function() {
        document.getElementById('srimResultContainer').innerHTML = `
            <div class="card">
                <div class="card-body text-center py-5">
                    <div class="spinner-border text-primary" role="status">
                        <span class="visually-hidden">계산중...</span>
                    </div>
                    <p class="mt-3 text-muted">S-RIM을 계산하는 중입니다...</p>
                </div>
            </div>
        `;
    },
    
    showError: function(message) {
        document.getElementById('srimResultContainer').innerHTML = `
            <div class="alert alert-danger">
                <i class="bi bi-exclamation-triangle"></i> ${message}
            </div>
        `;
    }
};

function formatNumber(value) {
    if (!value && value !== 0) return '-';
    return parseFloat(value).toLocaleString('ko-KR', {maximumFractionDigits: 0});
}

function formatPercent(value) {
    if (!value && value !== 0) return '-';
    return (parseFloat(value)).toFixed(2) + '%';
}

function formatBillion(value) {
    if (!value && value !== 0) return '-';
    return (parseFloat(value) / 100000000).toLocaleString('ko-KR', {maximumFractionDigits: 0}) + '억원';
}

function formatLargeNumber(value) {
    if (!value && value !== 0) return '-';
    return parseFloat(value).toLocaleString('ko-KR', {maximumFractionDigits: 0});
}
