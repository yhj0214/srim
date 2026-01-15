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
        const tabs = qsa('.nav-tabs a', root);
        tabs.forEach((btn) => {
            btn.addEventListener('click', async () =>{
                const name = btn.dataset.tab;
                this._activateTab(root, name);

                if(name === "srim"){
                    await this._loadAndRenderSrim(root);
                }
            });
        });
    },

    _activateTab(root, name){
        qsa(".tab", root).forEach((tab) =>
        tab.classList.toggle("is-active", tab.dataset.tab === name));
        qsa(".tabPanel", root).forEach((panel) => (panel.hidden = panel.dataset.panel !== name));
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

        container.datset.loaded = "true";
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

function escapeHtml(str) {
    return String(str ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#39;");
}