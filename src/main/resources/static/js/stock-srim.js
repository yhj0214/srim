/**
 * stock-srim.js
 * - S-RIM 탭 전용 모듈
 * - 데이터 로딩은 StockDetail.ensureSrimLoaded()가 기본(오케스트레이터)
 * - render + 차트 + (재계산 버튼 이벤트) 담당
 */

(function (window, document) {
  'use strict';

  const StockApp = window.StockApp;

  const StockSrim = (window.StockSrim = window.StockSrim || {});

  const state = {
    chart: null
  };

  function container() {
    return document.getElementById('srimResultContainer');
  }

  Object.assign(StockSrim, {
    async calculate(companyId) {
      const cid = companyId || window.StockDetail?.getCompanyId?.() || window.StockDetail?.companyId;
      if (!cid) {
        this.showError('회사 정보가 아직 등록되지 않았습니다.');
        return;
      }
      this.showLoading();
      // 오케스트레이터 우선
      if (window.StockDetail?.ensureSrimLoaded) {
        await window.StockDetail.ensureSrimLoaded();
        return;
      }

      const res = await StockApp.api.getJSON(`/api/stocks/${cid}/srim?basis=YEAR`);
      if (!res.ok) {
        this.showError(res.message);
        return;
      }
      if (!res.data?.success) {
        this.showError(res.data?.message || 'S-RIM 계산에 실패했습니다.');
        return;
      }
      this.renderResult(res.data.data);
    },

    renderResult(data) {
      try {
        if (!data?.scenarios || data.scenarios.length < 5) {
          this.showError('S-RIM 결과 형식이 올바르지 않습니다.');
          return;
        }

        // 재무 데이터는 최신 3개를 가져오되, 연도 기반 매핑이 가능하면 우선 사용
        const fin = window.StockFinancial;
        const nowYear = new Date().getFullYear();
        const equity2024 = fin?.getMetricByYear?.('지배주주지분', nowYear) ?? fin?.getMetric?.('지배주주지분', 0);
        const equity2023 = fin?.getMetricByYear?.('지배주주지분', nowYear - 1) ?? fin?.getMetric?.('지배주주지분', 1);
        const equity2022 = fin?.getMetricByYear?.('지배주주지분', nowYear - 2) ?? fin?.getMetric?.('지배주주지분', 2);

        const roe2024 = fin?.getMetricByYear?.('ROE', nowYear) ?? fin?.getMetric?.('ROE', 0);
        const roe2023 = fin?.getMetricByYear?.('ROE', nowYear - 1) ?? fin?.getMetric?.('ROE', 1);
        const roe2022 = fin?.getMetricByYear?.('ROE', nowYear - 2) ?? fin?.getMetric?.('ROE', 2);

        const c = container();
        if (!c) return;

        c.innerHTML = `
          <div class="row g-3" id="srimRoot">
            <div class="col-md-4">
              <div class="card h-100">
                <div class="card-body">
                  <div class="d-flex justify-content-between align-items-center mb-4">
                    <h5 class="mb-0">적정주가 요약</h5>
                    <button class="btn btn-sm btn-primary" type="button" data-action="recalc">
                      <i class="bi bi-calculator"></i> 재계산
                    </button>
                  </div>

                  <div class="mb-4">
                    <h6 class="text-muted mb-3">적정주가</h6>
                    ${this._renderFairValueList(data)}
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
                      <span>${StockApp.format.number(data.sharesOutstanding, { maximumFractionDigits: 0 })}</span>
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
                    <h6 class="mb-3">지배주주지분</h6>
                    ${this._render3y(nowYear, equity2022, equity2023, equity2024, (v) => StockApp.format.number(v, { maximumFractionDigits: 0 }))}
                  </div>

                  <div class="border-top pt-3 mt-3 small">
                    <h6 class="mb-3">ROE (%)</h6>
                    ${this._render3y(nowYear, roe2022, roe2023, roe2024, (v) => StockApp.format.percent(v, 2))}
                  </div>

                  <div class="border-top pt-3 mt-3">
                    <div class="d-flex justify-content-between mb-1">
                      <span class="text-muted">회사채 수익률 (%)</span>
                      <span>${StockApp.format.percent(data.ke, 2)}</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <div class="col-md-8">
              <div class="card">
                <div class="card-header">
                  <h6 class="mb-0"><i class="bi bi-bar-chart"></i> 시나리오별 적정주가 비교</h6>
                </div>
                <div class="card-body">
                  <canvas id="srimChart" height="120"></canvas>
                </div>
              </div>

              ${this._renderScenarioTable(data)}

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

        // 이벤트 바인딩(전역 onclick 제거)
        const root = c.querySelector('#srimRoot');
        root?.addEventListener('click', (e) => {
          const btn = e.target.closest('button[data-action]');
          if (!btn) return;
          if (btn.dataset.action === 'recalc') {
            this.calculate();
          }
        });

        this.renderChart(data);
      } catch (error) {
        console.error('renderSrimResult 에러:', error);
        this.showError('결과를 표시하는 중 오류가 발생했습니다: ' + (error?.message || error));
      }
    },

    _renderFairValueList(data) {
      const labels = ['초과이익 지속시', '10% 감소시', '20% 감소시', '30% 감소시', '50% 감소시'];
      return data.scenarios
        .slice(0, 5)
        .map((s, idx) => {
          const v = StockApp.format.number(s.fairValuePerShare, { maximumFractionDigits: 0 });
          const isPrimary = idx === 0;
          return `
            <div class="mb-2">
              <small class="text-muted">적정주가 (${labels[idx]})</small>
              <${isPrimary ? 'h5' : 'h6'} class="mb-0 ${isPrimary ? 'text-primary' : ''}">${v}원</${isPrimary ? 'h5' : 'h6'}>
            </div>
          `;
        })
        .join('');
    },

    _render3y(nowYear, v2022, v2023, v2024, formatter) {
      const safe = (v) => (v == null || v === '???' ? '???' : formatter(v));
      return `
        <div class="d-flex justify-content-between mb-1"><span class="text-muted">${nowYear - 2}/12</span><span>${safe(v2022)}</span></div>
        <div class="d-flex justify-content-between mb-1"><span class="text-muted">${nowYear - 1}/12</span><span>${safe(v2023)}</span></div>
        <div class="d-flex justify-content-between mb-1"><span class="text-muted">${nowYear}/12</span><span>${safe(v2024)}</span></div>
      `;
    },

    _renderScenarioTable(data) {
      const rows = [
        { title: '기준 (0%)', dec: '0%', i: 0 },
        { title: '보수적 (-10%)', dec: '-10%', i: 1 },
        { title: '매우 보수적 (-20%)', dec: '-20%', i: 2 },
        { title: '비관적 (-30%)', dec: '-30%', i: 3 },
        { title: '극단적 (-50%)', dec: '-50%', i: 4 }
      ];

      const toEok = (v) => {
        // S-RIM 시나리오의 excessEarnings/enterpriseValue가 '원' 단위라고 가정
        return StockApp.format.krwToEokwon(v);
      };

      return `
        <div class="card mt-3">
          <div class="card-header"><h6 class="mb-0">시나리오별 비교표</h6></div>
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
                  ${rows
                    .map((r) => {
                      const s = data.scenarios[r.i];
                      return `
                        <tr>
                          <td><strong>${r.title}</strong></td>
                          <td class="text-end">${r.dec}</td>
                          <td class="text-end">${toEok(s.excessEarnings)}</td>
                          <td class="text-end">${toEok(s.enterpriseValue)}</td>
                          <td class="text-end"><strong>${StockApp.format.number(s.fairValuePerShare, { maximumFractionDigits: 0 })}원</strong></td>
                        </tr>
                      `;
                    })
                    .join('')}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      `;
    },

    renderChart(data) {
      try {
        const ctx = document.getElementById('srimChart');
        if (!ctx) return;

        if (state.chart) {
          state.chart.destroy();
          state.chart = null;
        }

        const labels = ['지속시', '10% 감소', '20% 감소', '30% 감소', '50% 감소'];
        const values = data.scenarios.slice(0, 5).map((s) => Number(s.fairValuePerShare));

        state.chart = new Chart(ctx, {
          type: 'bar',
          data: {
            labels,
            datasets: [
              {
                label: '적정주가 (원)',
                data: values,
                borderWidth: 1
              }
            ]
          },
          options: {
            responsive: true,
            maintainAspectRatio: true,
            plugins: {
              legend: { display: false },
              tooltip: {
                callbacks: {
                  label(context) {
                    return '적정주가: ' + Number(context.parsed.y).toLocaleString('ko-KR') + '원';
                  }
                }
              }
            },
            scales: {
              y: {
                beginAtZero: false,
                ticks: {
                  callback(value) {
                    return Number(value).toLocaleString('ko-KR') + '원';
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

    showLoading() {
      const c = container();
      if (!c) return;
      c.innerHTML = `
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

    showError(message) {
      const c = container();
      StockApp.dom.renderAlert(c, 'danger', message);
    }
  });
})(window, document);
