/**
 * stock-financial.js
 * - 재무정보 탭 전용 모듈
 * - 데이터 로딩은 StockDetail이 담당(ensureFinancialLoaded)
 * - 본 모듈은 render + metric 조회 API 제공
 */

(function (window, document) {
  'use strict';

  const StockApp = window.StockApp;
  const format = StockApp?.format;

  const StockFinancial = (window.StockFinancial = window.StockFinancial || {});

  let _data = null;

  Object.assign(StockFinancial, {
    setData(data) {
      _data = data || null;
    },

    getData() {
      return _data;
    },

    renderTable(data) {
      const d = data || _data;
      if (!d || !d.headers || d.headers.length === 0) {
        this.showError('재무 데이터가 없습니다.');
        return;
      }

      const reversedHeaders = [...d.headers].reverse();

      let html = '<div class="financial-table-wrapper">';
      html += '<table class="table table-sm table-bordered financial-table">';
      html += '<thead class="table-light"><tr>';
      html += '<th style="min-width: 200px; width: 200px; white-space: nowrap;">지표</th>';
      reversedHeaders.forEach((header) => {
        const label = StockApp.format.escapeHtml(header.label);
        html += `<th class="text-end" style="min-width: 150px; width: 150px; white-space: nowrap;">${label}${header.isEstimate ? '(E)' : ''}</th>`;
      });
      html += '</tr></thead>';

      html += '<tbody>';
      (d.rows || []).forEach((row) => {
        html += '<tr>';
        html += `<td style="min-width: 200px; width: 200px; white-space: nowrap;"><strong>${StockApp.format.escapeHtml(row.metricName)}</strong></td>`;
        reversedHeaders.forEach((header) => {
          const value = row.values?.[header.periodId];
          html += `<td class="text-end" style="min-width: 150px; width: 150px; white-space: nowrap;">${this.formatValue(value, row.unit)}</td>`;
        });
        html += '</tr>';
      });
      html += '</tbody></table></div>';

      const container = document.getElementById('financialTableContainer');
      if (container) container.innerHTML = html;
    },

    formatValue(value, unit) {
      if (value == null || value === '') return '-';
      const num = Number(value);
      if (!Number.isFinite(num)) return '-';

      if (unit === 'KRW' || unit === '원') {
        return StockApp.format.number(num, { maximumFractionDigits: 0 }) + '억원';
      }
      if (unit === '%') {
        return StockApp.format.percent(num, 2);
      }
      if (unit === '배') {
        return StockApp.format.ratio(num, 2);
      }
      return StockApp.format.number(num, { maximumFractionDigits: 2 });
    },

    showLoading() {
      const container = document.getElementById('financialTableContainer');
      if (!container) return;
      container.innerHTML = `
        <div class="text-center py-5">
          <div class="spinner-border text-primary" role="status">
            <span class="visually-hidden">로딩중...</span>
          </div>
          <p class="mt-3 text-muted">재무 데이터를 불러오는 중입니다...</p>
          <p class="text-muted"><small>데이터가 없으면 자동으로 크롤링을 시작합니다.</small></p>
        </div>
      `;
    },

    showError(message) {
      const container = document.getElementById('financialTableContainer');
      StockApp.dom.renderAlert(container, 'warning', message);
    },

    /**
     * Metric 조회
     * - periodIndex: 최신(0) → 과거(1,2...)
     */
    getMetric(metricName, periodIndex = 0) {
      const d = _data;
      if (!d || !d.rows) return null;

      const row = d.rows.find((r) => r.metricName === metricName);
      if (!row) return null;

      const headers = [...d.headers].reverse();
      if (periodIndex >= headers.length) return null;

      const periodId = headers[periodIndex].periodId;
      return row.values?.[periodId] ?? null;
    },

    /**
     * 연도 기반 조회(안전성 강화)
     * - header.label이 "2024/12" 형태라고 가정
     */
    getMetricByYear(metricName, fiscalYear) {
      const d = _data;
      if (!d || !d.rows || !d.headers) return null;
      const row = d.rows.find((r) => r.metricName === metricName);
      if (!row) return null;

      const header = d.headers.find((h) => String(h.label).startsWith(String(fiscalYear)));
      if (!header) return null;
      return row.values?.[header.periodId] ?? null;
    }
  });
})(window, document);
