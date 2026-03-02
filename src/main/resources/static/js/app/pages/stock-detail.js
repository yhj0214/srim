import { qs, qsa } from "../ui/dom.js"
import { apiGetJSON } from "../api/client.js";

export const StockDetailPage = {
    async mount(){
        const root = qs('[data-page="stock-detail"]');
        if(!root) return;

        this._bindTabs(root);


        // 기본 탭: srim
        this._activateTab(root, "srim");

        // SRIM 즉시 로드
        await this._loadSrim(root);
    },

    _bindTabs(root){
        const tabs = qsa('.tab', root);
        tabs.forEach((btn) => {
            btn.addEventListener('click', async () =>{
                const name = btn.dataset.tab;
                this._activateTab(root, name);

                await this._ensureLoaded(root, name);
            });
        });
    },

    async _ensureLoaded(root, tabName) {
        if(tabName === "srim") return this._loadSrim(root);
        if(tabName === "financial") return this._loadFinancial(root);
    },

    _activateTab(root, name){
        // tab active 표시
        qsa(".tab", root).forEach((tab) =>
            tab.classList.toggle("is-active", tab.dataset.tab === name)
        );

        // 패널 전환(data-panel 값과 탭 이름 맞추기)
        qsa(".tabPanel", root).forEach((panel) =>
            (panel.hidden = panel.dataset.panel !== name)
        );
    },

    async _loadSrim(root) {
        const container = qs("#srimResultContainer", root);
        if(!container) return;

        if(container.dataset.loaded === "true") return;

        const companyId = root.dataset.companyId;
        if(!companyId) {
            container.innerHTML = `<div class="alert alert--error">회사 정보(companyId)가 없어 S-RIM을 조회할 수 없습니다.</div>`;
            return;
        }

        container.innerHTML = `<div class="alert">S-RIM 데이터를 불러오는 중입니다...</div>`;

        const apiUrl = root.dataset.apiUrl;
        const res = await apiGetJSON(`/api/stocks/${companyId}/srim?basis=YEAR`);
        if (!res.ok) {
            container.innerHTML = `<div class="alert alert--error">${escapeHtml(res.message || "요청에 실패했습니다.")}</div>`;
            return;
        }

        const body = res.data;
        if (!body?.success) {
            container.innerHTML = `<div class="alert alert--error">${escapeHtml(body?.message || "S-RIM 조회에 실패했습니다.")}</div>`;
            return;
        }

        container.dataset.loaded = "true";
        this._renderSrim(container,body.data);
    },

    _renderSrim(container, data) {
        // data.scenarios: 시나리오별 fairValuePerShare 목록이 있다고 가정(기존 코드와 동일)
        const scenarios = Array.isArray(data?.scenarios) ? data.scenarios : [];
        if (scenarios.length === 0) {
            container.innerHTML = `<div class="alert alert--error">S-RIM 결과가 비어 있습니다.</div>`;
            return;
        }

        const labels = ["초과이익 지속", "10% 감소", "20% 감소", "30% 감소", "50% 감소"];

        const listHtml = scenarios.slice(0, 5).map((s, i) => {
            const v = formatNumber(s.fairValuePerShare);
            return `<li>${labels[i] ?? `시나리오 ${i+1}`}: <strong>${v}원</strong></li>`;
        }).join("");

        container.innerHTML = `
        <div class="srimGrid">
            <div class="srimCard">
                <div class="srimTitle">
                    <h3>S-RIM 적정주가</h3>
                    <span class="srimHint">Ke: ${formatPercent(data?.ke)} / 기준: YEAR</span>
                </div>
                 <ul class="srimList">${listHtml}</ul>
            </div>
        
            <div class="srimCard">
                <div class="srimTitle">
                    <h3>입력 요약</h3>
                    <span class="srimHint">참고용</span>
                </div>
                <div class="muted">발행주식수: ${formatNumber(data?.sharesOutstanding)}주</div>
            </div>
        
            <div class="alert">
            계산 결과는 “탭 로딩 방식”으로 확장 예정입니다. (차트/재무 연결 시 근거 데이터 함께 표기)
            </div>
        </div>
        `;
    },

    async _loadFinancial(root) {
        const container = qs("#financialContainer", root);
        if(!container) return;

        if(container.dataset.loaded === "true") return;

        const companyId = root.dataset.companyId;
        if (!companyId) {
            container.innerHTML = `<div class="alert alert--error">회사 정보(companyId)가 없어 재무를 조회할 수 없습니다.</div>`;
            return;
        }


        container.innerHTML = `<div class="alert">재무 데이터를 불러오는 중입니다...</div>`;

        const apiUrl = root.dataset.apiFinancial;
        const res = await apiGetJSON(apiUrl);

        if (!res.ok) {
            container.innerHTML = `<div class="alert alert--error">${escapeHtml(res.message || "요청에 실패했습니다.")}</div>`;
            return;
        }

        const body = res.data;
        if (!body?.success) {
            container.innerHTML = `<div class="alert alert--error">${escapeHtml(body?.message || "재무 조회에 실패했습니다.")}</div>`;
            return;
        }

        container.dataset.loaded = "true";
        this._renderFinancial(container, body.data);
    },
    _renderFinancial(container, data) {
        const headers = Array.isArray(data?.headers) ? data.headers : [];
        const rows = Array.isArray(data?.rows) ? data.rows : [];

        if (headers.length === 0 || rows.length === 0) {
            container.innerHTML = `<div class="alert">재무 데이터가 없습니다.</div>`;
            return;
        }

        const headerCells = headers.map((h) => {
            const label = h?.label ?? "-";
            return `<th class="finTable__head">${escapeHtml(label)}</th>`;
        }).join("");

        const isCalcMetric = (row) => {
            const order = Number(row?.displayOrder);
            return Number.isFinite(order) && order > 100;
        };

        const rawRows = rows.filter((row) => !isCalcMetric(row));
        const calcRows = rows.filter((row) => isCalcMetric(row));

        const renderTable = (sectionTitle, tableRows) => {
            if (!tableRows || tableRows.length === 0) {
                return `
                    <div class="finSection">
                        <h4 class="finSection__title">${escapeHtml(sectionTitle)}</h4>
                        <div class="alert">표시할 지표가 없습니다.</div>
                    </div>
                `;
            }

            const bodyRows = tableRows.map((row) => {
                const name = row?.metricName ?? row?.metricCode ?? "-";
                const unit = row?.unit ? ` <span class="finTable__unit">(${escapeHtml(row.unit)})</span>` : "";

                const valueCells = headers.map((h) => {
                    const periodId = String(h?.periodId ?? "");
                    const raw = row?.values?.[periodId];
                    return `<td class="finTable__cell">${formatFinancialValue(raw)}</td>`;
                }).join("");

                return `
                    <tr>
                        <th class="finTable__rowHead">${escapeHtml(name)}${unit}</th>
                        ${valueCells}
                    </tr>
                `;
            }).join("");

            return `
                <div class="finSection">
                    <h4 class="finSection__title">${escapeHtml(sectionTitle)}</h4>
                    <div class="finTableWrap">
                        <table class="finTable">
                            <thead>
                                <tr>
                                    <th class="finTable__head finTable__head--sticky">지표</th>
                                    ${headerCells}
                                </tr>
                            </thead>
                            <tbody>
                                ${bodyRows}
                            </tbody>
                        </table>
                    </div>
                </div>
            `;
        };

        container.innerHTML = `
            <div class="card">
                <div class="finHeader">
                    <h3>재무정보</h3>
                </div>
                ${renderTable("원천 지표", rawRows)}
                ${renderTable("계산 지표", calcRows)}
            </div>
        `;
    }
}

function formatNumber(v) {
    const n = Number(v);
    if (!Number.isFinite(n)) return "-";
    return n.toLocaleString("ko-KR", { maximumFractionDigits: 0 });
}

function formatPercent(v) {
    const n = Number(v);
    if (!Number.isFinite(n)) return "-";
    return n.toFixed(2) + "%";
}

function formatFinancialValue(v) {
    const n = Number(v);
    if (!Number.isFinite(n)) return "-";
    return n.toLocaleString("ko-KR");
}

function escapeHtml(str) {
    return String(str ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#39;");
}
