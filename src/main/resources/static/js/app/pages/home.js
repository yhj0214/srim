import { qs } from "../ui/dom.js";
import { apiGetJSON } from "../api/client.js";

export const HomePage = {
    async mount() {
        console.log("[home] mounted");

        const root = qs('[data-page="home"]');
        const form = qs("#homeSearchForm");
        const input = qs("#homeSearchInput");
        const hint = qs("#homeSearchHint");
        const popularContainer = qs("#popularStocksContainer");
        // console.log("form=", form, "input=", input, "hint=", hint);
        if(!form || !input) return;

        // 빈 값인 경우 힌트 표시
        form.addEventListener("submit", (e) => {
            const value = (input.value || "").trim();
            input.value = value;

            if(!value) {
                e.preventDefault();

                if(hint) hint.style.display = "";
                input.focus();
                return;
            }

            // 정상인 경우 힌트 숨김
            if(hint) hint.style.display = "none";
        });

        // 입력 시작 시 힌트 숨김
        input.addEventListener('input', () => {
            if(hint) hint.style.display = "none";
        });

        await this._loadPopularStocks(root, popularContainer);
    },

    async _loadPopularStocks(root, container) {
        if (!root || !container) return;

        const apiUrl = root.dataset.apiPopular || "/api/stocks/popular?days=7&limit=10";
        const res = await apiGetJSON(apiUrl);

        if (!res.ok || !res.data?.success) {
            container.innerHTML = `<div class="popularStocks__empty">인기 종목을 불러오지 못했습니다.</div>`;
            return;
        }

        const items = Array.isArray(res.data.data) ? res.data.data : [];
        if (items.length === 0) {
            container.innerHTML = `<div class="popularStocks__empty">아직 집계된 인기 종목이 없습니다.</div>`;
            return;
        }

        container.innerHTML = items.map((item, index) => {
            const rank = index + 1;
            const href = `/stocks/${encodeURIComponent(item.market)}-${encodeURIComponent(item.tickerKrx)}`;
            const companyName = this._escapeHtml(item.companyName || "-");
            const ticker = this._escapeHtml(item.tickerKrx || "-");
            const market = this._escapeHtml(item.market || "-");
            const viewCount = this._formatNumber(item.viewCount);

            return `
                <a class="popularStocks__item" href="${href}">
                    <div class="popularStocks__rank">${rank}</div>
                    <div class="popularStocks__body">
                        <div class="popularStocks__name">${companyName}</div>
                        <div class="popularStocks__meta">
                            <span>${ticker}</span>
                            <span>${market}</span>
                        </div>
                    </div>
                    <div class="popularStocks__count">${viewCount}</div>
                </a>
            `;
        }).join("");
    },

    _formatNumber(value) {
        const num = Number(value);
        if (!Number.isFinite(num)) return "-";
        return num.toLocaleString("ko-KR");
    },

    _escapeHtml(value) {
        return String(value)
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll("\"", "&quot;")
            .replaceAll("'", "&#39;");
    },
};
