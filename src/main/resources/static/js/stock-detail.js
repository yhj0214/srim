/**
 * stock-detail.js
 * - 페이지 오케스트레이션(로딩 정책/탭 wiring)만 담당
 * - 공통 유틸(api/format/dom) 및 단일 상태(Store) 제공
 *
 * 주의: 템플릿에서 이 파일이 가장 먼저 로딩되므로(현재 stock-detail.html),
 *       다른 모듈(stock-chart/financial/srim)은 여기서 만든 StockApp을 사용한다.
 */

(function (window, document) {
  'use strict';

  /** -----------------------------
   * StockApp (공통 유틸)
   * ------------------------------*/
  const StockApp = (window.StockApp = window.StockApp || {});

  // DOM helper
  StockApp.dom = {
    qs(sel, root) {
      return (root || document).querySelector(sel);
    },
    qsa(sel, root) {
      return Array.from((root || document).querySelectorAll(sel));
    },
    setHTML(el, html) {
      if (el) el.innerHTML = html;
    },
    setText(el, text) {
      if (el) el.textContent = text;
    },
    renderAlert(containerEl, type, message) {
      if (!containerEl) return;
      const icon =
        type === 'danger'
          ? 'bi-exclamation-triangle'
          : type === 'warning'
            ? 'bi-exclamation-triangle'
            : type === 'info'
              ? 'bi-info-circle'
              : 'bi-check-circle';
      containerEl.innerHTML = `
        <div class="alert alert-${type}">
          <i class="bi ${icon}"></i> ${StockApp.format.escapeHtml(message)}
        </div>
      `;
    }
  };

  // Format helper (표기/단위 규칙은 여기로 고정)
  StockApp.format = {
    escapeHtml(str) {
      if (str == null) return '';
      return String(str)
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&#39;');
    },
    number(value, opts) {
      if (value == null || value === '') return '-';
      const n = Number(value);
      if (!Number.isFinite(n)) return '-';
      const maximumFractionDigits = opts?.maximumFractionDigits ?? 0;
      return n.toLocaleString('ko-KR', { maximumFractionDigits });
    },
    percent(value, fractionDigits = 2) {
      if (value == null || value === '') return '-';
      const n = Number(value);
      if (!Number.isFinite(n)) return '-';
      return n.toFixed(fractionDigits) + '%';
    },
    // 입력값이 '원' 단위라고 가정하고, 화면에서는 '억원'으로 표기
    // (백엔드가 이미 억원 단위라면 이 함수를 쓰지 말고 number()+ '억' 등으로 분리)
    krwToEokwon(value) {
      if (value == null || value === '') return '-';
      const n = Number(value);
      if (!Number.isFinite(n)) return '-';
      const eok = n / 100000000;
      return eok.toLocaleString('ko-KR', { maximumFractionDigits: 0 }) + '억원';
    },
    // 값이 '억원' 단위(이미 스케일링된 값)일 때
    eokwon(value) {
      return this.number(value, { maximumFractionDigits: 0 }) + '억원';
    },
    ratio(value, fractionDigits = 2) {
      if (value == null || value === '') return '-';
      const n = Number(value);
      if (!Number.isFinite(n)) return '-';
      return n.toFixed(fractionDigits) + '배';
    },
    isoDate(d) {
      // YYYY-MM-DD
      const date = d instanceof Date ? d : new Date(d);
      if (Number.isNaN(date.getTime())) return '';
      return date.toISOString().split('T')[0];
    }
  };

  // API helper (에러 표준화 + 타임아웃)
  StockApp.api = {
    async request(url, options) {
      const timeoutMs = options?.timeoutMs ?? 20000;
      const controller = new AbortController();
      const id = setTimeout(() => controller.abort(), timeoutMs);

      try {
        const resp = await fetch(url, { ...options, signal: controller.signal });
        const contentType = resp.headers.get('content-type') || '';
        const isJson = contentType.includes('application/json');
        const payload = isJson ? await resp.json().catch(() => null) : await resp.text().catch(() => null);

        if (!resp.ok) {
          const message =
            (payload && payload.message) ||
            (typeof payload === 'string' ? payload : null) ||
            `HTTP ${resp.status}`;
          return { ok: false, status: resp.status, data: null, message };
        }

        return { ok: true, status: resp.status, data: payload, message: null };
      } catch (e) {
        const message = e?.name === 'AbortError' ? '요청 시간이 초과되었습니다.' : (e?.message || '네트워크 오류가 발생했습니다.');
        return { ok: false, status: 0, data: null, message };
      } finally {
        clearTimeout(id);
      }
    },

    getJSON(url, opts) {
      return this.request(url, { method: 'GET', headers: { Accept: 'application/json' }, ...opts });
    },

    postJSON(url, body, opts) {
      return this.request(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
        body: JSON.stringify(body ?? {}),
        ...opts
      });
    }
  };

  // 아주 작은 Store (단일 상태 + 구독)
  StockApp.store = (function () {
    let state = {
      stockId: null,
      companyId: null,
      financial: { loaded: false, data: null, error: null },
      chart: { loaded: false, data: null, error: null },
      srim: { loaded: false, data: null, error: null }
    };
    const listeners = new Set();

    function getState() {
      return state;
    }

    function setState(patch) {
      state = { ...state, ...patch };
      listeners.forEach((fn) => {
        try {
          fn(state);
        } catch (_) {
          // ignore
        }
      });
    }

    function update(path, value) {
      // shallow path: 'financial'|'chart'|'srim'
      if (!['financial', 'chart', 'srim'].includes(path)) return;
      state = { ...state, [path]: { ...state[path], ...value } };
      listeners.forEach((fn) => {
        try {
          fn(state);
        } catch (_) {
          // ignore
        }
      });
    }

    function subscribe(fn) {
      listeners.add(fn);
      return () => listeners.delete(fn);
    }

    return { getState, setState, update, subscribe };
  })();

  /** -----------------------------
   * StockDetail (오케스트레이터)
   * ------------------------------*/
  const StockDetail = (window.StockDetail = window.StockDetail || {});

  Object.assign(StockDetail, {
    init(stockId, companyId) {
      StockApp.store.setState({ stockId, companyId });
      // Lazy-load 정책:
      // - 페이지 진입 시에는 추가 API 호출 없이(가능하면) 개요만 즉시 표시
      // - 각 탭 클릭(shown.bs.tab) 시점에만 백엔드 요청을 보내 데이터 로드
      // - 한 번 로드한 탭 데이터는 store에 캐시하여 재진입 시 재요청하지 않음(필요 시 새로고침 버튼으로 확장)

      this.setupEventListeners();
      this.renderInitialPlaceholders();

      // companyId가 템플릿에서 내려오지 않는 케이스를 대비해 최소 1회 보정
      // (단, 다른 탭 데이터 로드 시에도 재확인하므로 실패해도 치명적이지 않다.)
      this.ensureCompanyIdResolved().catch(() => {
        /* ignore */
      });
    },

    getCompanyId() {
      return StockApp.store.getState().companyId;
    },

    // (기존) startDataLoading 제거: 이제 탭 클릭 시점에만 요청한다.

    async ensureCompanyIdResolved() {
      const st = StockApp.store.getState();
      if (st.companyId) return st.companyId;
      if (!st.stockId) return null;

      const res = await StockApp.api.getJSON(`/api/stocks/${st.stockId}`);
      if (res.ok && res.data?.success && res.data?.data?.companyId) {
        StockApp.store.setState({ companyId: res.data.data.companyId });
        return res.data.data.companyId;
      }
      return null;
    },

    async ensureFinancialLoaded() {
      const st = StockApp.store.getState();
      if (st.financial.loaded) return st.financial.data;
      if (!st.stockId) throw new Error('stockId가 없습니다');

      // UI
      if (window.StockFinancial?.showLoading) window.StockFinancial.showLoading();

      const res = await StockApp.api.getJSON(`/api/stocks/${st.stockId}/financial/annual`, { timeoutMs: 120000 });
      if (!res.ok) {
        StockApp.store.update('financial', { loaded: false, error: res.message });
        window.StockFinancial?.showError?.(res.message);
        throw new Error(res.message);
      }

      if (!res.data?.success) {
        const msg = res.data?.message || '재무 데이터 로드 실패';
        StockApp.store.update('financial', { loaded: false, error: msg });
        window.StockFinancial?.showError?.(msg);
        throw new Error(msg);
      }

      StockApp.store.update('financial', { loaded: true, data: res.data.data, error: null });
      // Financial 모듈에 data 주입
      window.StockFinancial?.setData?.(res.data.data);
      window.StockFinancial?.renderTable?.(res.data.data);
      return res.data.data;
    },

    async ensureChartLoaded(startDate, endDate) {
        const companyId = (await this.ensureCompanyIdResolved()) || StockApp.store.getState().companyId;
        const container = document.getElementById('priceChartContainer');
        if (!companyId) {
            StockApp.dom.renderAlert(container, 'info', '회사 정보가 등록되지 않아 주가 데이터를 조회할 수 없습니다.');
            return null;
        }

        let url = `/api/stocks/${companyId}/price-chart`;

        // start/end가 들어온 경우에만 쿼리스트링 생성
        if (startDate || endDate) {
            const params = new URLSearchParams();
            if (startDate) params.set('startDate', startDate);
            if (endDate) params.set('endDate', endDate);
            url += `?${params.toString()}`;
        }
      window.StockChart?.showLoading?.();
      const res = await StockApp.api.getJSON(url, { timeoutMs: 30000 });
      if (!res.ok) {
        StockApp.store.update('chart', { loaded: false, error: res.message });
        window.StockChart?.showError?.(res.message);
        return null;
      }

      if (!res.data?.success) {
        const msg = res.data?.message || '주가 데이터 로드 실패';
        StockApp.store.update('chart', { loaded: false, error: msg });
        window.StockChart?.showError?.(msg);
        return null;
      }

      StockApp.store.update('chart', { loaded: true, data: res.data.data, error: null });
      window.StockChart?.render?.(res.data.data, { companyId });
      return res.data.data;
    },

    async ensureSrimLoaded() {
      const companyId = (await this.ensureCompanyIdResolved()) || StockApp.store.getState().companyId;
      const container = document.getElementById('srimResultContainer');
      if (!companyId) {
        StockApp.dom.renderAlert(container, 'info', '회사 정보가 등록되지 않아 S-RIM을 계산할 수 없습니다.');
        return null;
      }

      window.StockSrim?.showLoading?.();
      const res = await StockApp.api.getJSON(`/api/stocks/${companyId}/srim?basis=YEAR`, { timeoutMs: 30000 });
      if (!res.ok) {
        StockApp.store.update('srim', { loaded: false, error: res.message });
        window.StockSrim?.showError?.(res.message);
        return null;
      }

      if (!res.data?.success) {
        const msg = res.data?.message || 'S-RIM 계산 실패';
        StockApp.store.update('srim', { loaded: false, error: msg });
        window.StockSrim?.showError?.(msg);
        return null;
      }

      StockApp.store.update('srim', { loaded: true, data: res.data.data, error: null });
      window.StockSrim?.renderResult?.(res.data.data);
      return res.data.data;
    },

    _defaultStartDate() {
      const end = new Date();
      const start = new Date();
      start.setFullYear(end.getFullYear() - 1);
      return StockApp.format.isoDate(start);
    },

    renderInitialPlaceholders() {
      // 탭 클릭 전에는 "탭을 선택하면 로드" 안내만 표시
      const fin = document.getElementById('financialTableContainer');
      if (fin) {
        StockApp.dom.renderAlert(fin, 'info', '재무정보 탭을 열면 데이터를 불러옵니다.');
      }

      const chart = document.getElementById('priceChartContainer');
      if (chart) {
        StockApp.dom.renderAlert(chart, 'info', '주가 그래프 탭을 열면 데이터를 불러옵니다.');
      }

      const srim = document.getElementById('srimResultContainer');
      if (srim) {
        StockApp.dom.renderAlert(srim, 'info', 'S-RIM 평가 탭을 열면 계산 결과를 불러옵니다.');
      }
    },

    setupEventListeners() {
      // 탭 클릭(shown.bs.tab) 시점에만 백엔드 요청
      const financialTab = document.getElementById('financial-tab');
      financialTab?.addEventListener('shown.bs.tab', () => {
        this.ensureFinancialLoaded().catch(() => {
          /* tab 자체에서 에러 UI 처리 */
        });
      });

      const chartTab = document.getElementById('chart-tab');
      chartTab?.addEventListener('shown.bs.tab', () => {
        // 기본 기간(1년)로 1회 로드
        const st = StockApp.store.getState();
        if (!st.chart.loaded) {
            this.ensureChartLoaded().catch(() => {});
        }
      });

      const srimTab = document.getElementById('srim-tab');
      srimTab?.addEventListener('shown.bs.tab', () => {
        this.ensureSrimLoaded().catch(() => {
          /* ignore */
        });
      });
    },

    activateOverviewTab() {
      const overviewTab = document.getElementById('overview-tab');
      if (overviewTab && typeof bootstrap !== 'undefined') new bootstrap.Tab(overviewTab).show();
    }
  });

  document.addEventListener('DOMContentLoaded', function () {
    StockDetail.init(window.STOCK_ID, window.COMPANY_ID);
  });
})(window, document);
