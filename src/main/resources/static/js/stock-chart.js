/**
 * stock-chart.js
 * - 주가 차트 탭 전용 모듈
 * - 데이터 로딩은 StockDetail.ensureChartLoaded()가 기본(오케스트레이터)
 * - 본 모듈은 render + UI 이벤트 바인딩 + 차트(Chart.js)만 담당
 */

(function (window, document) {
  'use strict';

  const StockApp = window.StockApp;
  const format = StockApp?.format;

  const StockChart = (window.StockChart = window.StockChart || {});

  // internal state
  const state = {
    chart: null,
    chartData: null,
    filteredData: null,
    range: null,
    hoveredCandleIndex: null,
    mouseX: null,
    mouseY: null,
    companyId: null,
    enabledScenarios: new Set()
  };

  const SCENARIOS = [
    { key: 'scenario0', label: '초과이익 지속', field: 'fvScenario0' },
    { key: 'scenario10', label: '10% 감소', field: 'fvScenario10' },
    { key: 'scenario20', label: '20% 감소', field: 'fvScenario20' },
    { key: 'scenario30', label: '30% 감소', field: 'fvScenario30' },
    { key: 'scenario50', label: '50% 감소', field: 'fvScenario50' }
  ];

  function getContainer() {
    return document.getElementById('priceChartContainer');
  }

  function parseISODateInput(el) {
    const v = el?.value;
    if (!v) return null;
    const d = new Date(v);
    if (Number.isNaN(d.getTime())) return null;
    return v;
  }
//
  Object.assign(StockChart, {
//     /**
//      * 오케스트레이터가 render(data, {companyId}) 형태로 호출
//      */
    render(data, opts) {
      state.companyId = opts?.companyId ?? state.companyId;

      if (!data?.priceData || data.priceData.length === 0) {
        this.showError('주가 데이터가 없습니다.');
        return;
      }

      const firstDate = data.priceData[0].date;
      const lastDate = data.priceData[data.priceData.length - 1].date;
      const prevStart = document.getElementById('startDate')?.value;
      const prevEnd   = document.getElementById('endDate')?.value;

      const startValue = prevStart || firstDate;
      const endValue   = prevEnd || lastDate;

      const container = getContainer();
      if (!container) return;

      // UI는 통째 렌더(간단). 이벤트는 모듈 내부에서 위임 바인딩한다.
      container.innerHTML = `
        <div class="mb-3 d-flex justify-content-end" id="chartRangeControls">
          <input type="date" id="startDate" class="form-control form-control-sm d-inline-block" style="width:150px;" value="${StockApp.format.escapeHtml(startValue)}">
          <span class="mx-2">~</span>
          <input type="date" id="endDate" class="form-control form-control-sm d-inline-block" style="width:150px;" value="${StockApp.format.escapeHtml(endValue)}">
          <button class="btn btn-primary btn-sm ms-2" type="button" data-action="applyCustomPeriod">조회</button>
        </div>
        <div style="height:500px;position:relative;">
          <canvas id="stockPriceChart"></canvas>
        </div>
        <div class="mt-3 p-3 bg-light rounded" id="scenarioControls">
          <h6 class="mb-3">적정주가 표시</h6>
          ${SCENARIOS.map(
            (s) => `
            <div class="form-check form-check-inline">
              <input class="form-check-input" type="checkbox" id="${s.key}" value="${s.key}" data-action="toggleScenario" />
              <label class="form-check-label" for="${s.key}">${s.label}</label>
            </div>
          `
          ).join('')}
        </div>
      `;

      state.chartData = data;
      this._bindEvents(container);

      const startIso = document.getElementById('startDate')?.value || firstDate;
      const endIso   = document.getElementById('endDate')?.value || lastDate;

      this._createCandlestick(data, startIso, endIso);
    },

    /**
     * (선택) 기존 방식처럼 직접 로드하고 싶을 때
     */
    async loadData(companyId, startDate, endDate) {
      if (!companyId) {
        this.showError('회사 정보가 없습니다.');
        return;
      }

      this.showLoading();

      let url = `/api/stocks/${companyId}/price-chart?`;
      if (startDate) url += `startDate=${encodeURIComponent(startDate)}&`;
      if (endDate) url += `endDate=${encodeURIComponent(endDate)}`;

      const res = await StockApp.api.getJSON(url);
      if (!res.ok) {
        this.showError(res.message);
        return;
      }
      if (!res.data?.success) {
        this.showError(res.data?.message || '주가 데이터 로드 실패');
        return;
      }
      this.render(res.data.data, { companyId });
    },
//
//     /**
//      * 기간 버튼(외부 fragment)에서 호출하기 위한 API
//      */
    async setPeriodDays(days) {
      const companyId = state.companyId || window.StockDetail?.getCompanyId?.() || window.StockDetail?.companyId;
      if (!companyId) {
        this.showError('회사 정보가 없습니다.');
        return;
      }

      const end = new Date();
      const start = new Date();
      start.setDate(end.getDate() - Number(days));
      const s = StockApp.format.isoDate(start);
      const e = StockApp.format.isoDate(end);

      // UI input 동기화
      const startEl = document.getElementById('startDate');
      const endEl = document.getElementById('endDate');
      if (startEl) startEl.value = s;
      if (endEl) endEl.value = e;

      // const group = document.querySelector('[data-chart-period-group]');
      // if (group) {
      //     group.querySelectorAll('button[data-days]').forEach(b => b.classList.remove('active'));
      // }

      // 오케스트레이터를 통해 로드(권장)
      if (window.StockDetail?.ensureChartLoaded) {
        await window.StockDetail.ensureChartLoaded(s, e);
      } else {
        await this.loadData(companyId, s, e);
      }
    },

    async applyCustomPeriodFromUI() {
      let s = parseISODateInput(document.getElementById('startDate'));
      let e = parseISODateInput(document.getElementById('endDate'));
      if (!s || !e) {
        alert('날짜를 선택하세요.');
        return;
      }
        const todayIso = StockApp.format.isoDate(new Date());
        if (e > todayIso) {
            e = todayIso;
            const endEl = document.getElementById('endDate');
            if (endEl) endEl.value = todayIso;
        }

        if (new Date(s) > new Date(e)) {
            alert('시작일이 종료일보다 늦습니다.');
            return;
        }

        // 최대 10년 클램프: start >= (end - 10년)
        const endD = new Date(e);
        const hardMin = new Date(endD);
        hardMin.setFullYear(hardMin.getFullYear() - 10);
        const hardMinIso = StockApp.format.isoDate(hardMin);

        if (s < hardMinIso) {
            s = hardMinIso;
            const startEl = document.getElementById('startDate');
            if (startEl) startEl.value = hardMinIso;
        }
        const g = document.querySelector('[data-chart-period-group]');
        if (g) g.querySelectorAll('button[data-days]').forEach(b => b.classList.remove('active'));

        if (window.StockDetail?.ensureChartLoaded) {
            await window.StockDetail.ensureChartLoaded(s, e);
        } else {
            const companyId = state.companyId || window.StockDetail?.getCompanyId?.() || window.StockDetail?.companyId;
            await this.loadData(companyId, s, e);
        }
    },

    showLoading() {
      const container = getContainer();
      if (!container) return;
      container.innerHTML =
        '<div class="text-center py-5"><div class="spinner-border text-primary"></div><p class="mt-3">로딩중...</p></div>';
    },

    showError(msg) {
      const container = getContainer();
      StockApp.dom.renderAlert(container, 'warning', msg);
    },

    /** -----------------------------
     * internal: 이벤트/차트
     * ------------------------------*/
    _bindEvents(container) {
      // range controls
      const range = container.querySelector('#chartRangeControls');
      range?.addEventListener('click', (e) => {
        const btn = e.target.closest('button[data-action]');
        if (!btn) return;
        if (btn.dataset.action === 'applyCustomPeriod') {
          this.applyCustomPeriodFromUI();
        }
      });

      // scenario toggles
      const scenarioWrap = container.querySelector('#scenarioControls');
      scenarioWrap?.addEventListener('change', (e) => {
        const cb = e.target;
        if (!(cb instanceof HTMLInputElement)) return;
        if (cb.dataset.action !== 'toggleScenario') return;
        this._toggleScenarioLine(cb.value, cb.checked);
      });

      // mouseleave: tooltip hide
      const canvas = container.querySelector('#stockPriceChart');
      canvas?.addEventListener('mouseleave', () => {
        state.hoveredCandleIndex = null;
        state.mouseX = null;
        state.mouseY = null;
        state.chart?.update('none');
        const tip = document.getElementById('chartjs-tooltip');
        if (tip) tip.style.display = 'none';
      });

    },



    _createCandlestick(data, startIso, endIso) {
      const canvas = document.getElementById('stockPriceChart');
      if (!canvas) return;

      const src = data?.priceData ?? [];
      const filtered = filterPriceDataByRange(src, startIso, endIso);

      if (filtered.length === 0) {
        this.showError('해당 기간에 주가 데이터가 없습니다.');
        return;
      }

        console.log('filtered price data:', startIso);
        console.log('filtered price data:', endIso);

        const filteredData = { ...data, priceData: filtered };

      state.filteredData = filteredData;
      state.range = { startIso, endIso };

      if (state.chart) {
        state.hoveredCandleIndex = null;
        state.mouseX = null;
        state.mouseY = null;

        state.chart.destroy();
        state.chart = null;
      }

      const dates = filteredData.priceData.map((d) => d.date);

      const allPrices = filteredData.priceData
        .flatMap((d) => [d.open, d.high, d.low, d.close, d.fvScenario0, d.fvScenario10, d.fvScenario20, d.fvScenario30, d.fvScenario50])
        .filter((v) => v != null);

      const minPrice = Math.min(...allPrices);
      const maxPrice = Math.max(...allPrices);
      const padding = (maxPrice - minPrice) * 0.05;

      state.chart = new Chart(canvas, {
        type: 'scatter',
        data: { datasets: [{ label: '주가', data: [], pointRadius: 0 }] },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          interaction: { intersect: false, mode: 'index' },
          layout: { padding: { left: 10, right: 80, top: 10, bottom: 10 } },
          plugins: {
            legend: { display: false },
            tooltip: { enabled: false, external: this._createTooltip.bind(this, filteredData, dates) }
          },
          scales: {
            x: {
              type: 'linear',
              min: 0,
              max: dates.length - 1,
              grid: { color: 'rgba(0,0,0,0.05)', drawBorder: false },
              ticks: { maxTicksLimit: 10, callback: (v) => dates[Math.round(v)]?.substring(5) || '' }
            },
            y: {
              min: minPrice - padding,
              max: maxPrice + padding,
              grid: { color: 'rgba(0,0,0,0.05)', drawBorder: false },
              ticks: { callback: (v) => Number(v).toLocaleString() + '원' }
            }
          },
          onHover: this._handleHover.bind(this, filteredData, dates)
        },
        plugins: [
          { id: 'crosshair', afterDatasetsDraw: this._drawCrosshair.bind(this, filteredData, dates) },
          { id: 'candles', afterDatasetsDraw: this._drawCandles.bind(this, filteredData, dates) }
        ]
      });
        for (const key of state.enabledScenarios) {
            this._toggleScenarioLine(key, true);
        }
    },

    _handleHover(data, dates, event, activeElements, chart) {
      if (!chart?.chartArea) return;

      const pos = Chart.helpers.getRelativePosition(event, chart);
      state.mouseX = pos.x;
      state.mouseY = pos.y;

      const dataX = chart.scales.x.getValueForPixel(pos.x);
      const idx = Math.round(dataX);

      if (idx >= 0 && idx < dates.length) {
        if (state.hoveredCandleIndex !== idx) {
          state.hoveredCandleIndex = idx;
          chart.update('none');
        }
      } else if (state.hoveredCandleIndex !== null) {
        state.hoveredCandleIndex = null;
        chart.update('none');
      }

      chart.update('none');
    },

    _drawCrosshair(data, dates, chart) {
      if (state.mouseX === null || state.mouseY === null) return;

      const ctx = chart.ctx;
      const area = chart.chartArea;
      const xScale = chart.scales.x;
      const yScale = chart.scales.y;

      if (state.mouseX < area.left || state.mouseX > area.right || state.mouseY < area.top || state.mouseY > area.bottom) return;

      const x = state.mouseX;
      const y = state.mouseY;

      ctx.save();
      ctx.strokeStyle = 'rgba(75,192,192,0.7)';
      ctx.lineWidth = 1;
      ctx.setLineDash([5, 5]);

      ctx.beginPath();
      ctx.moveTo(x, area.top);
      ctx.lineTo(x, area.bottom);
      ctx.stroke();

      ctx.beginPath();
      ctx.moveTo(area.left, y);
      ctx.lineTo(area.right, y);
      ctx.stroke();
      ctx.setLineDash([]);

      const dateIdx = Math.round(xScale.getValueForPixel(x));
      if (dateIdx >= 0 && dateIdx < dates.length) {
        const date = dates[dateIdx];
        ctx.fillStyle = 'rgba(75,192,192,0.9)';
        ctx.fillRect(x - 30, area.bottom + 5, 60, 20);
        ctx.fillStyle = 'white';
        ctx.font = '12px Arial';
        ctx.textAlign = 'center';
        ctx.fillText(date.substring(5), x, area.bottom + 18);
      }

      const priceValue = yScale.getValueForPixel(y);
      const priceText = Math.round(priceValue).toLocaleString() + '원';
      ctx.font = '12px Arial';
      const textWidth = ctx.measureText(priceText).width;
      ctx.fillStyle = 'rgba(75,192,192,0.9)';
      ctx.fillRect(area.right + 5, y - 10, textWidth + 10, 20);
      ctx.fillStyle = 'white';
      ctx.textAlign = 'left';
      ctx.fillText(priceText, area.right + 10, y + 4);

      ctx.restore();
    },

    _drawCandles(data, dates, chart) {
      const ctx = chart.ctx;
      const xScale = chart.scales.x;
      const yScale = chart.scales.y;
      const candleWidth = Math.max(1, Math.min(10, ((chart.chartArea.right - chart.chartArea.left) / dates.length) * 0.7));

      data.priceData.forEach((p, i) => {
        const isUp = p.close >= p.open;
        const isHovered = i === state.hoveredCandleIndex;
        const color = isUp ? 'rgb(255,82,82)' : 'rgb(54,162,235)';
        const width = isHovered ? candleWidth * 1.3 : candleWidth;

        const x = xScale.getPixelForValue(i);
        const yH = yScale.getPixelForValue(p.high);
        const yL = yScale.getPixelForValue(p.low);
        const yO = yScale.getPixelForValue(p.open);
        const yC = yScale.getPixelForValue(p.close);

        ctx.globalAlpha = isHovered ? 1.0 : 0.7;
        ctx.strokeStyle = color;
        ctx.lineWidth = isHovered ? 2 : 1;
        ctx.beginPath();
        ctx.moveTo(x, yH);
        ctx.lineTo(x, yL);
        ctx.stroke();

        ctx.fillStyle = color;
        if (isHovered) {
          ctx.shadowColor = color;
          ctx.shadowBlur = 10;
        }
        const h = Math.max(Math.abs(yC - yO), 1);
        const top = Math.min(yO, yC);
        ctx.fillRect(x - width / 2, top, width, h);
        ctx.strokeRect(x - width / 2, top, width, h);
        if (isHovered) ctx.shadowBlur = 0;
        ctx.globalAlpha = 1.0;
      });
    },

    _createTooltip(data, dates, context) {
      let tip = document.getElementById('chartjs-tooltip');
      if (!tip) {
        tip = document.createElement('div');
        tip.id = 'chartjs-tooltip';
        tip.style.cssText =
          'position:absolute;background:rgba(0,0,0,0.85);color:white;padding:12px;border-radius:6px;pointer-events:none;font-size:13px;z-index:10000;box-shadow:0 2px 8px rgba(0,0,0,0.3);';
        document.body.appendChild(tip);
      }

      const idx = state.hoveredCandleIndex;
      if (idx >= 0 && idx < data.priceData.length) {
        const p = data.priceData[idx];
        const change = p.close - p.open;
        const pct = ((change / p.open) * 100).toFixed(2);
        const color = change >= 0 ? '#ff5252' : '#36a2eb';

        tip.innerHTML = `
          <div style="margin-bottom:8px;"><strong style="font-size:14px;">${StockApp.format.escapeHtml(p.date)}</strong></div>
          <div style="line-height:1.6;">
            <div>시가: <strong>${Number(p.open).toLocaleString()}원</strong></div>
            <div>고가: <strong>${Number(p.high).toLocaleString()}원</strong></div>
            <div>저가: <strong>${Number(p.low).toLocaleString()}원</strong></div>
            <div>종가: <strong>${Number(p.close).toLocaleString()}원</strong></div>
            <div style="color:${color};margin-top:4px;">변동: <strong>${change > 0 ? '+' : ''}${Number(change).toLocaleString()}원 (${pct}%)</strong></div>
          </div>
        `;

        const rect = context.chart.canvas.getBoundingClientRect();
        let left = rect.left + window.pageXOffset + context.tooltip.caretX + 15;
        let top = rect.top + window.pageYOffset + context.tooltip.caretY - 80;
        if (left + 250 > window.innerWidth) left -= 280;
        if (top < window.pageYOffset) top += 160;
        tip.style.left = left + 'px';
        tip.style.top = top + 'px';
        tip.style.display = 'block';
      } else {
        tip.style.display = 'none';
      }
    },

    _toggleScenarioLine(scenarioKey, checked) {
      if (!state.chart) return;
      const base = state.filteredData || state.chartData;
      if (!base?.priceData) return;

      const scenario = SCENARIOS.find((s) => s.key === scenarioKey);
      if (!scenario) return;

      const colors = {
          scenario0:  'rgb(255,99,132)',
          scenario10: 'rgb(255,159,64)',
          scenario20: 'rgb(255,205,86)',
          scenario30: 'rgb(153,102,255)',
          scenario50: 'rgb(201,203,207)'
      };
      if (checked) {
          // ✅ 중복 추가 방지
          const exists = state.chart.data.datasets.some(ds => ds.label === scenario.label);
          if (exists) return;

          state.chart.data.datasets.push({
              label: scenario.label,
              data: base.priceData
                  .map((d, i) => ({ x: i, y: d[scenario.field] }))
                  .filter(p => p.y != null),
              type: 'line',
              borderColor: colors[scenario.key],
              backgroundColor: 'transparent',
              borderWidth: 2,
              borderDash: [5, 5],
              pointRadius: 0,
              showLine: true
          });
          state.enabledScenarios.add(scenarioKey);
      } else {
          const idx = state.chart.data.datasets.findIndex(ds => ds.label === scenario.label);
          if (idx > -1) state.chart.data.datasets.splice(idx, 1);
          state.enabledScenarios.delete(scenarioKey);
      }
      state.chart.update('none');
    },
    rerenderRange(startIso, endIso) {
        if (!state.chartData) {
            this.showError('차트 데이터가 없습니다.');
            return;
        }
        this._createCandlestick(state.chartData, startIso, endIso);
    }
  });

})(window, document);
document.addEventListener('DOMContentLoaded', () => {
    // 차트 탭 내 기간 버튼 바인딩
    bindChartPeriodButtons();
});

function bindChartPeriodButtons() {
    const group = document.querySelector('[data-chart-period-group]')
        || document.querySelector('#chart .btn-group');
    if (!group) return;

    group.querySelectorAll('button[data-days]').forEach(btn => {
        btn.addEventListener('click', async () => {
            const days = parseInt(btn.dataset.days, 10);
            if (!days || Number.isNaN(days)) return;

            // active 토글
            group.querySelectorAll('button[data-days]').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');

            // 날짜 계산: end=오늘, start=end-days
            const end = new Date();
            const start = new Date();
            start.setDate(end.getDate() - days);

            const s = StockApp.format.isoDate(start);
            const e = StockApp.format.isoDate(end);

            // 달력 값도 동기화
            const startEl = document.getElementById('startDate');
            const endEl = document.getElementById('endDate');
            if (startEl) startEl.value = s;
            if (endEl) endEl.value = e;

            // ✅ 항상 StockDetail.ensureChartLoaded로 요청 통일
            // if (window.StockDetail?.ensureChartLoaded) {
            //     await window.StockDetail.ensureChartLoaded(s, e);
            // } else if (window.StockChart?.loadData) {
            //     // 혹시 StockDetail이 없을 때의 fallback
            //     const companyId = StockApp.store?.getState?.().companyId;
            //     await window.StockChart.loadData(companyId, s, e);
            // }
            window.StockChart?.rerenderRange?.(s, e);

        });
    });
}

function filterPriceDataByRange(priceData, startIso, endIso) {
    if (!Array.isArray(priceData)) return [];

    // 안전장치: 값 없으면 원본 그대로
    if (!startIso || !endIso) return priceData;

    // ISO(YYYY-MM-DD)면 문자열 비교로도 정렬/비교가 안전합니다.
    // 단, date 포맷이 반드시 YYYY-MM-DD여야 합니다.
    return priceData.filter(d => d?.date >= startIso && d?.date <= endIso);
}