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
        if(tabName === "chart") return this._loadChart(root);
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

        const apiUrl = root.dataset.apiSrim || `/api/stocks/${companyId}/srim`;
        const res = await apiGetJSON(apiUrl);
        if (!res.ok) {
            container.innerHTML = `<div class="alert alert--error">${escapeHtml(res.message || "요청에 실패했습니다.")}</div>`;
            return;
        }

        const body = res.data;
        if (!body?.success) {
            container.innerHTML = `<div class="alert alert--error">${escapeHtml(body?.error?.message || "S-RIM 조회에 실패했습니다.")}</div>`;
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
        const scenarioData = scenarios.slice(0, 5);
        const fairValues = scenarioData.map((s) => Number(s?.fairValuePerShare ?? 0));
        const excessValues = scenarioData.map((s) => Number(s?.excessEarnings ?? 0));

        const scenarioRows = scenarioData.map((s, i) => {
            const label = labels[i] ?? `시나리오 ${i + 1}`;
            const fairValue = formatNumber(s?.fairValuePerShare);
            const enterpriseValue = formatNumber(s?.enterpriseValue);
            return `
                <tr>
                    <th class="srimTable__rowHead">${escapeHtml(label)}</th>
                    <td class="srimTable__cell">${fairValue}</td>
                    <td class="srimTable__cell">${enterpriseValue}</td>
                </tr>
            `;
        }).join("");

        const buildBarChart = (values, labelList, opts = {}) => {
            const safeValues = values.map((v) => (Number.isFinite(v) ? v : 0));
            const maxValue = Math.max(1, ...safeValues, Number(opts.lineValue ?? 0));
            const bars = safeValues.map((v, i) => {
                const pct = Math.max(0, Math.min(100, (v / maxValue) * 100));
                const label = labelList[i] ?? `시나리오 ${i + 1}`;
                return `
                    <div class="srimBarWrap">
                        <div class="srimBar" style="height:${pct}%"></div>
                        <div class="srimBarLabel">${escapeHtml(label)}</div>
                        <div class="srimBarValue">${formatNumber(v)}</div>
                    </div>
                `;
            }).join("");

            const lineValue = Number(opts.lineValue);
            const showLine = Number.isFinite(lineValue) && lineValue > 0;
            const linePct = showLine ? (100 - (lineValue / maxValue) * 100) : null;
            const lineLabel = showLine
                ? `현재가 ${formatNumber(lineValue)}원${opts.lineNote ? ` (${escapeHtml(opts.lineNote)})` : ""}`
                : "";

            return `
                <div class="srimChart">
                    ${showLine ? `<div class="srimLine" style="top:${linePct}%"><span>${lineLabel}</span></div>` : ""}
                    <div class="srimBars">${bars}</div>
                </div>
            `;
        };

        const roeDetails = Array.isArray(data?.roeDetails) ? data.roeDetails : [];
        const roeYears = roeDetails.slice(0, 3).map((r) => r?.fiscalYear ?? "-");
        const roeValues = roeDetails.slice(0, 3).map((r) => formatPercent(r?.roePercent));
        const equityValues = roeDetails.slice(0, 3).map((r) => formatNumber(r?.equityOwner));
        while (roeYears.length < 3) roeYears.push("-");
        while (roeValues.length < 3) roeValues.push("-");
        while (equityValues.length < 3) equityValues.push("-");
        const roeAvg = formatPercent(data?.roePercent);
        const baseScenario = scenarioData[0] ?? {};
        const baseScenarioLabel = labels[0] ?? "시나리오 1";
        const calcEnterpriseValue = formatNumber(baseScenario?.enterpriseValue);
        const calcExcessEarnings = formatNumber(baseScenario?.excessEarnings);
        const calcFairValue = formatNumber(baseScenario?.fairValuePerShare);
        const calcEquity = formatNumber(data?.equity);
        const calcKe = formatPercent(data?.ke);
        const calcRoe = formatPercent(data?.roePercent);
        const calcShares = formatNumber(data?.sharesOutstanding);

        const keChip = `<span class="chip chip--neutral">Ke ${formatPercent(data?.ke)}</span>`;
        const yearChip = data?.year ? `<span class="chip chip--muted">${escapeHtml(data.year)}년</span>` : "";
        const priceChip = data?.currentPrice
            ? `<span class="chip chip--dark">현재가 ${formatNumber(data?.currentPrice)}원</span>`
            : "";

        container.innerHTML = `
        <div class="srimGrid">
            <div class="srimCard">
                <div class="srimTitle">
                    <h3>S-RIM 적정주가</h3>
                    <div class="srimChips">
                        ${keChip}
                        ${yearChip}
                        ${priceChip}
                    </div>
                </div>
                <div class="srimSubTitle">시나리오별 적정주가</div>
                <div class="srimScenarioLayout">
                    <div class="srimTableWrap srimTableWrap--flush">
                        <table class="srimTable srimTable--scenario">
                            <thead>
                                <tr>
                                    <th class="srimTable__head">시나리오</th>
                                    <th class="srimTable__head">적정주가(원)</th>
                                    <th class="srimTable__head">기업가치(백만원)</th>
                                </tr>
                            </thead>
                            <tbody>
                                ${scenarioRows}
                            </tbody>
                        </table>
                    </div>
                    ${buildBarChart(
            fairValues,
            labels,
            { lineValue: Number(data?.currentPrice ?? 0), lineNote: data?.currentPriceDate }
        )}
                </div>
                <div class="srimSubTitle">계산식 요약</div>
                <div class="srimTableWrap">
                    <table class="srimTable srimTable--calc">
                        <thead>
                            <tr>
                                <th class="srimTable__head">식</th>
                                <th class="srimTable__head">값</th>
                            </tr>
                        </thead>
                        <tbody>
                            <tr>
                                <th class="srimTable__rowHead">기업가치 = 지배주주지분 + (초과이익 / 할인율)</th>
                                <td class="srimTable__cell">${calcEnterpriseValue} (백만원)</td>
                            </tr>
                            <tr>
                                <th class="srimTable__rowHead">초과이익 = 자기자본 × (가중평균 ROE − 할인율)</th>
                                <td class="srimTable__cell">${calcExcessEarnings} (백만원)</td>
                            </tr>
                            <tr>
                                <th class="srimTable__rowHead">ROE = 당기순이익 / 평균자기자본</th>
                                <td class="srimTable__cell">${calcRoe}</td>
                            </tr>
                            <tr>
                                <th class="srimTable__rowHead">적정주가 = 기업가치 / 유통주식수</th>
                                <td class="srimTable__cell">${calcFairValue}원</td>
                            </tr>
                        </tbody>
                    </table>
                </div>
                <div class="srimCalcNote">
                    기준 시나리오: ${escapeHtml(baseScenarioLabel)} ·
                    지배주주지분 ${calcEquity} (백만원) ·
                    할인율 ${calcKe} ·
                    유통주식수 ${calcShares}주
                </div>
            </div>
        
            <div class="srimCard">
                <div class="srimTitle">
                    <h3>입력 요약</h3>
                    <span class="srimHint">S-RIM 계산 입력값</span>
                </div>
                <div class="srimSummaryGrid">
                    <div class="srimTableWrap">
                        <table class="srimTable srimTable--center srimTable--compact">
                            <tbody>
                                <tr>
                                    <th class="srimTable__rowHead">발행주식수</th>
                                    <td class="srimTable__cell">${formatNumber(data?.sharesOutstanding)}주</td>
                                </tr>
                                <tr>
                                    <th class="srimTable__rowHead">지배주주지분</th>
                                    <td class="srimTable__cell">${formatNumber(data?.equity)} (백만원)</td>
                                </tr>
                                <tr>
                                    <th class="srimTable__rowHead">할인율(Ke)</th>
                                    <td class="srimTable__cell">${formatPercent(data?.ke)}</td>
                                </tr>
                            </tbody>
                        </table>
                    </div>
                    <div class="srimTableWrap">
                        <table class="srimTable srimTable--center srimTable--rowLabels">
                            <thead>
                                <tr>
                                    <th class="srimTable__head"></th>
                                    <th class="srimTable__head">${escapeHtml(roeYears[0])}</th>
                                    <th class="srimTable__head">${escapeHtml(roeYears[1])}</th>
                                    <th class="srimTable__head">${escapeHtml(roeYears[2])}</th>
                                    <th class="srimTable__head">가중평균</th>
                                </tr>
                            </thead>
                            <tbody>
                                <tr>
                                    <th class="srimTable__rowHead">ROE</th>
                                    <td class="srimTable__cell">${escapeHtml(roeValues[0])}</td>
                                    <td class="srimTable__cell">${escapeHtml(roeValues[1])}</td>
                                    <td class="srimTable__cell">${escapeHtml(roeValues[2])}</td>
                                    <td class="srimTable__cell srimTable__cell--em">${escapeHtml(roeAvg)}</td>
                                </tr>
                                <tr>
                                    <th class="srimTable__rowHead">지배주주지분</th>
                                    <td class="srimTable__cell">${escapeHtml(equityValues[0])}</td>
                                    <td class="srimTable__cell">${escapeHtml(equityValues[1])}</td>
                                    <td class="srimTable__cell">${escapeHtml(equityValues[2])}</td>
                                    <td class="srimTable__cell srimTable__cell--muted">-</td>
                                </tr>
                            </tbody>
                        </table>
                    </div>
                </div>
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
            container.innerHTML = `<div class="alert alert--error">${escapeHtml(body?.error?.message || "재무 조회에 실패했습니다.")}</div>`;
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
    },

    async _loadChart(root) {
        const container = qs("#priceChartContainer", root);
        if(!container) return;

        if(container.dataset.loaded === "true") return;

        const companyId = root.dataset.companyId;
        if(!companyId) {
            container.innerHTML = `<div class="alert alert--error">회사 정보(companyId)가 없어 차트를 조회할 수 없습니다.</div>`;
            return;
        }

        container.dataset.loaded = "true";
        this._renderChartShell(container, "2w");
        this._bindChartRangeButtons(root, container);
        this._bindChartToggle(container);
        this._bindChartInteractions(container);
        this._initChartState(container, "2w");
        container.__chartRoot = root;
        this._renderMetricShell();
        await this._fetchAndRenderChart(root, container, "3y", "2w");
    },

    _renderChartShell(container, activeRange) {
        const ranges = [
            { key: "2w", label: "2주" },
            { key: "1m", label: "1개월" },
            { key: "3m", label: "3개월" },
            { key: "6m", label: "6개월" },
            { key: "1y", label: "1년" },
            { key: "3y", label: "3년" },
            { key: "5y", label: "5년" },
            { key: "10y", label: "10년" }
        ];
        const controls = ranges.map((r) => {
            const active = r.key === activeRange ? "is-active" : "";
            return `<button class="chartRangeBtn ${active}" type="button" data-range="${r.key}">${r.label}</button>`;
        }).join("");

        container.innerHTML = `
            <div class="chartCard">
                <div class="chartHead">
                    <h3 class="chartTitle">주가 캔들 차트</h3>
                    <div class="chartControls">
                        <label class="chartToggle">
                            <input type="checkbox" data-chart-toggle="srim" checked>
                            <span>S-RIM 적정주가</span>
                        </label>
                        ${controls}
                    </div>
                </div>
                <span class="chartMeta" data-chart-meta>불러오는 중...</span>
                <div class="chartCanvasWrap">
                    <canvas class="chartCanvas" data-chart="price-candle"></canvas>
                    <div class="chartTooltip" data-chart-tooltip hidden></div>
                </div>
                <div class="chartError alert alert--error" data-chart-error hidden></div>
                <div class="chartNote">일별 시가/고가/저가/종가와 S-RIM 적정주가(감소율 0%)를 함께 표시합니다.</div>
            </div>
        `;
    },

    _renderMetricShell() {
        const container = qs("#metricChartContainer");
        if (!container) return;
        if (container.dataset.loaded === "true") return;
        container.dataset.loaded = "true";
        container.innerHTML = `
            <div class="metricChartCard">
                <h3 class="metricChartTitle">PER 추이</h3>
                <div class="chartCanvasWrap">
                    <canvas class="metricChartCanvas" data-chart="per-line"></canvas>
                </div>
                <div class="chartNote">일별 PER = 종가 / 전년도 EPS</div>
            </div>
        `;
    },

    _bindChartRangeButtons(root, container) {
        const buttons = qsa(".chartRangeBtn", container);
        if (buttons.length === 0) return;

        buttons.forEach((btn) => {
            btn.addEventListener("click", async () => {
                const range = btn.dataset.range || "1y";
                if (container.dataset.range === range) return;
                buttons.forEach((b) => b.classList.toggle("is-active", b === btn));
                await this._fetchAndRenderChart(root, container, range);
            });
        });
    },

    async _fetchAndRenderChart(root, container, range, viewRangeKey = null) {
        const companyId = root.dataset.companyId;
        const apiBase = root.dataset.apiPricechart || `/api/stocks/${companyId}/price-chart`;
        const { startDate, endDate } = buildDateRange(range);
        const apiUrl = `${apiBase}?startDate=${startDate}&endDate=${endDate}`;

        container.dataset.range = range;
        this._setChartMeta(container, "불러오는 중...");
        this._hideChartError(container);

        const res = await apiGetJSON(apiUrl);

        if (!res.ok) {
            this._showChartError(container, res.message || "요청에 실패했습니다.");
            return;
        }

        const body = res.data;
        if (!body?.success) {
            this._showChartError(container, body?.error?.message || "주가 차트 조회에 실패했습니다.");
            return;
        }

        const priceData = Array.isArray(body?.data?.priceData) ? body.data.priceData : [];
        if (priceData.length === 0) {
            this._showChartError(container, "표시할 주가 데이터가 없습니다.");
            return;
        }

        this._mergeChartData(container, priceData);
        container.__chartSelectedIndex = null;

        if (viewRangeKey) {
            const viewRange = buildDateRangeFromEnd(endDate, viewRangeKey);
            this._setChartViewRange(container, viewRange.startDate, viewRange.endDate);
        } else {
            this._setChartViewRange(container, startDate, endDate);
        }
        this._clampViewToLoaded(container);
        this._updateChartMetaWithView(container);

        const canvas = qs('canvas[data-chart="price-candle"]', container);
        if(!canvas) return;

        const render = () => {
            this._renderCandleChart(canvas, container);
            this._renderPerChart(container);
        };
        render();

        if (!container.__chartObserver) {
            const observer = new ResizeObserver(() => render());
            observer.observe(canvas);
            container.__chartObserver = observer;
        }

        await this._maybePrefetchChart(root, container);
    },

    _initChartState(container, rangeKey) {
        // 캐시/뷰 상태 초기화
        container.__chartCache = [];
        container.__chartCacheMap = new Map();
        container.__chartViewStart = null;
        container.__chartViewEnd = null;
        container.__chartRangeKey = rangeKey;
        container.__chartMinRangeDays = 14;
        container.__chartInflight = new Set();
        container.__chartFetchedRanges = new Set();
        container.__chartLastPrefetchAt = 0;
        container.__chartShowSrim = true;
    },

    _bindChartToggle(container) {
        const toggle = qs('[data-chart-toggle="srim"]', container);
        if (!toggle || toggle.dataset.bound === "true") return;

        toggle.checked = true;
        toggle.addEventListener("change", () => {
            container.__chartShowSrim = toggle.checked;
            this._renderCharts(container);
        });
        toggle.dataset.bound = "true";
    },

    _mergeChartData(container, priceData) {
        if (!Array.isArray(priceData)) return;
        const cache = container.__chartCache || [];
        const cacheMap = container.__chartCacheMap || new Map();

        // date 기준 중복 제거 후 병합
        priceData.forEach((item) => {
            const key = String(item?.date || "");
            if (!key || cacheMap.has(key)) return;
            cacheMap.set(key, item);
            cache.push(item);
        });

        // 날짜 오름차순 정렬 유지
        cache.sort((a, b) => dateToMs(a?.date) - dateToMs(b?.date));
        container.__chartCache = cache;
        container.__chartCacheMap = cacheMap;
    },

    _setChartViewRange(container, startDate, endDate) {
        const start = new Date(startDate);
        const end = new Date(endDate);
        if (!Number.isFinite(start.getTime()) || !Number.isFinite(end.getTime())) return;
        // 현재 뷰 범위 설정
        container.__chartViewStart = start;
        container.__chartViewEnd = end;
    },

    _updateChartMetaWithView(container) {
        const start = container.__chartViewStart;
        const end = container.__chartViewEnd;
        if (!start || !end) return;
        // 뷰 범위를 메타에 표시
        const label = `${formatDateLabel(start)} ~ ${formatDateLabel(end)}`;
        this._setChartMeta(container, label);
    },

    _clampViewToLoaded(container) {
        const cache = container.__chartCache || [];
        if (cache.length === 0) return;
        const minDate = parseDate(cache[0]?.date);
        const maxDate = parseDate(cache[cache.length - 1]?.date);
        const viewStart = container.__chartViewStart;
        const viewEnd = container.__chartViewEnd;
        if (!minDate || !maxDate || !viewStart || !viewEnd) return;

        // 최소 범위(2주) 보장
        const minRangeMs = container.__chartMinRangeDays * 24 * 60 * 60 * 1000;
        if (viewEnd.getTime() - viewStart.getTime() < minRangeMs) {
            viewEnd.setTime(viewStart.getTime() + minRangeMs);
        }

        // 로드된 구간 밖으로 나가면 다시 안쪽으로 끌어오기
        if (viewStart < minDate) {
            const delta = viewEnd.getTime() - viewStart.getTime();
            viewStart.setTime(minDate.getTime());
            viewEnd.setTime(minDate.getTime() + delta);
        }
        if (viewEnd > maxDate) {
            const delta = viewEnd.getTime() - viewStart.getTime();
            viewEnd.setTime(maxDate.getTime());
            viewStart.setTime(maxDate.getTime() - delta);
        }

        if (viewStart < minDate) viewStart.setTime(minDate.getTime());
        if (viewEnd > maxDate) viewEnd.setTime(maxDate.getTime());
    },

    _getViewData(container) {
        const cache = container.__chartCache || [];
        if (cache.length === 0) return [];
        const viewStart = container.__chartViewStart;
        const viewEnd = container.__chartViewEnd;
        if (!viewStart || !viewEnd) return cache;

        // 현재 뷰 범위에 해당하는 데이터만 추출
        const startMs = viewStart.getTime();
        const endMs = viewEnd.getTime();
        return cache.filter((item) => {
            const t = dateToMs(item?.date);
            return t >= startMs && t <= endMs;
        });
    },

    _bindChartInteractions(container) {
        if (container.__chartInteractionBound) return;
        const canvas = qs('canvas[data-chart="price-candle"]', container);
        if (!canvas) return;

        // 좌우 드래그로 팬 이동
        canvas.addEventListener("mousedown", (evt) => {
            if (evt.button !== 0) return;
            const layout = canvas.__chartLayout;
            if (!layout) return;
            const rect = canvas.getBoundingClientRect();
            container.__chartDrag = {
                startX: evt.clientX - rect.left,
                startViewStart: container.__chartViewStart ? new Date(container.__chartViewStart) : null,
                startViewEnd: container.__chartViewEnd ? new Date(container.__chartViewEnd) : null
            };
        });

        window.addEventListener("mouseup", () => {
            if (!container.__chartDrag) return;
            container.__chartDrag = null;
        });

        window.addEventListener("mousemove", (evt) => {
            if (!container.__chartDrag) return;
            const layout = canvas.__chartLayout;
            if (!layout) return;
            const rect = canvas.getBoundingClientRect();
            const currentX = evt.clientX - rect.left;
            const dx = currentX - container.__chartDrag.startX;
            const viewStart = container.__chartDrag.startViewStart;
            const viewEnd = container.__chartDrag.startViewEnd;
            if (!viewStart || !viewEnd) return;

            const viewMs = viewEnd.getTime() - viewStart.getTime();
            const plotWidth = layout.width - layout.padding.left - layout.padding.right;
            const msPerPx = viewMs / Math.max(1, plotWidth);
            const shiftMs = -dx * msPerPx;
            container.__chartViewStart = new Date(viewStart.getTime() + shiftMs);
            container.__chartViewEnd = new Date(viewEnd.getTime() + shiftMs);

            this._clampViewToLoaded(container);
            this._updateChartMetaWithView(container);
            this._renderCharts(container);
            this._maybePrefetchChartFromContainer(container);
        });

        // 휠로 줌 (마우스 위치 기준)
        canvas.addEventListener("wheel", (evt) => {
            const layout = canvas.__chartLayout;
            if (!layout) return;
            evt.preventDefault();
            const rect = canvas.getBoundingClientRect();
            const x = evt.clientX - rect.left;

            const viewStart = container.__chartViewStart;
            const viewEnd = container.__chartViewEnd;
            if (!viewStart || !viewEnd) return;

            const plotLeft = layout.padding.left;
            const plotRight = layout.width - layout.padding.right;
            const clampedX = Math.min(plotRight, Math.max(plotLeft, x));
            const ratio = (clampedX - plotLeft) / Math.max(1, plotRight - plotLeft);

            const zoomFactor = evt.deltaY < 0 ? 0.9 : 1.1;
            const viewMs = viewEnd.getTime() - viewStart.getTime();
            let newMs = viewMs * zoomFactor;
            const minMs = container.__chartMinRangeDays * 24 * 60 * 60 * 1000;
            newMs = Math.max(minMs, newMs);

            const focusMs = viewStart.getTime() + viewMs * ratio;
            const newStart = new Date(focusMs - newMs * ratio);
            const newEnd = new Date(focusMs + newMs * (1 - ratio));

            container.__chartViewStart = newStart;
            container.__chartViewEnd = newEnd;

            this._clampViewToLoaded(container);
            this._updateChartMetaWithView(container);
            this._renderCharts(container);
            this._maybePrefetchChartFromContainer(container);
        }, { passive: false });

        canvas.addEventListener("click", (evt) => {
            const idx = this._getChartIndexFromEvent(canvas, container, evt);
            if (idx === null) return;
            container.__chartSelectedIndex = idx;
            this._renderCharts(container);
        });

        canvas.addEventListener("mousemove", (evt) => {
            const idx = this._getChartIndexFromEvent(canvas, container, evt);
            if (idx === null) return;
            if (container.__chartHoverIndex === idx) return;
            container.__chartHoverIndex = idx;
            this._renderCharts(container);
        });

        canvas.addEventListener("mouseleave", () => {
            if (container.__chartHoverIndex == null) return;
            container.__chartHoverIndex = null;
            this._renderCharts(container);
        });

        container.__chartInteractionBound = true;
    },

    _getChartIndexFromEvent(canvas, container, evt) {
        const layout = canvas.__chartLayout;
        const data = container.__chartViewData;
        if (!layout || !Array.isArray(data) || data.length === 0) return null;

        const rect = canvas.getBoundingClientRect();
        const x = evt.clientX - rect.left;
        const { padding, gap, width } = layout;
        const plotLeft = padding.left;
        const plotRight = width - padding.right;
        if (x < plotLeft || x > plotRight) return null;

        return Math.max(0, Math.min(data.length - 1, Math.floor((x - plotLeft) / gap)));
    },

    async _maybePrefetchChart(root, container) {
        if (!root) return;
        await this._maybePrefetchChartFromContainer(container, root);
    },

    async _maybePrefetchChartFromContainer(container, rootRef) {
        const root = rootRef || container.__chartRoot;
        if (!root) return;
        const cache = container.__chartCache || [];
        if (cache.length === 0) return;
        const viewStart = container.__chartViewStart;
        const viewEnd = container.__chartViewEnd;
        if (!viewStart || !viewEnd) return;

        const minDate = parseDate(cache[0]?.date);
        const maxDate = parseDate(cache[cache.length - 1]?.date);
        if (!minDate || !maxDate) return;

        const prefetchStartThreshold = addMonths(minDate, 6);
        const prefetchEndThreshold = addMonths(maxDate, -6);
        const today = new Date();

        // 너무 잦은 호출 방지 (짧은 쓰로틀)
        const now = Date.now();
        if (now - (container.__chartLastPrefetchAt || 0) < 600) return;
        container.__chartLastPrefetchAt = now;

        // 오래된 쪽: 6개월 내 접근 시 과거 3년 추가
        if (viewStart <= prefetchStartThreshold) {
            await this._prefetchRange(container, root, addYears(minDate, -3), addDays(minDate, -1));
        }
        // 최신 쪽: 6개월 내 접근 시 미래 3년 추가 (오늘까지만)
        if (viewEnd >= prefetchEndThreshold) {
            const nextStart = addDays(maxDate, 1);
            const nextEnd = addYears(maxDate, 3);
            const cappedEnd = nextEnd > today ? today : nextEnd;
            if (nextStart <= cappedEnd) {
                await this._prefetchRange(container, root, nextStart, cappedEnd);
            }
        }
    },

    async _prefetchRange(container, root, startDate, endDate) {
        if (!root || !startDate || !endDate) return;
        if (endDate < startDate) return;

        const key = `${formatDateParam(startDate)}:${formatDateParam(endDate)}`;
        // 같은 구간 중복 요청 방지
        if (container.__chartFetchedRanges?.has(key)) return;
        if (container.__chartInflight?.has(key)) return;
        container.__chartInflight?.add(key);

        const companyId = root.dataset.companyId;
        const apiBase = root.dataset.apiPricechart || `/api/stocks/${companyId}/price-chart`;
        const apiUrl = `${apiBase}?startDate=${formatDateParam(startDate)}&endDate=${formatDateParam(endDate)}`;
        const res = await apiGetJSON(apiUrl);
        container.__chartInflight?.delete(key);

        if (!res.ok) return;
        const body = res.data;
        if (!body?.success) return;
        const priceData = Array.isArray(body?.data?.priceData) ? body.data.priceData : [];
        // 비어 있어도 재요청 방지용으로 기록
        container.__chartFetchedRanges?.add(key);
        if (priceData.length === 0) return;
        this._mergeChartData(container, priceData);
    },

    _setChartMeta(container, text) {
        const meta = qs("[data-chart-meta]", container);
        if (meta) meta.textContent = text;
    },

    _showChartError(container, message) {
        const error = qs("[data-chart-error]", container);
        if (!error) return;
        error.textContent = message;
        error.hidden = false;
    },

    _hideChartError(container) {
        const error = qs("[data-chart-error]", container);
        if (error) {
            error.hidden = true;
            error.textContent = "";
        }
    },

    _renderCandleChart(canvas, container) {
        const ctx = canvas.getContext("2d");
        if (!ctx) return;

        const rect = canvas.getBoundingClientRect();
        const dpr = window.devicePixelRatio || 1;
        const width = Math.max(1, Math.floor(rect.width));
        const height = Math.max(1, Math.floor(rect.height));

        canvas.width = Math.floor(width * dpr);
        canvas.height = Math.floor(height * dpr);
        ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
        ctx.clearRect(0, 0, width, height);

        const padding = { left: 50, right: 16, top: 16, bottom: 26 };
        const plotWidth = width - padding.left - padding.right;
        const plotHeight = height - padding.top - padding.bottom;
        if (plotWidth <= 0 || plotHeight <= 0) return;

        const viewData = this._getViewData(container);
        container.__chartViewData = viewData;

        const valid = viewData.filter((d) =>
            Number.isFinite(Number(d?.high)) &&
            Number.isFinite(Number(d?.low)) &&
            Number.isFinite(Number(d?.open)) &&
            Number.isFinite(Number(d?.close))
        );

        if (valid.length === 0) {
            ctx.fillStyle = "#6b7280";
            ctx.font = "12px sans-serif";
            ctx.fillText("표시할 데이터가 없습니다.", padding.left, padding.top + 12);
            return;
        }

        const showSrim = container.__chartShowSrim !== false;
        const highs = valid.map((d) => Number(d.high));
        const lows = valid.map((d) => Number(d.low));
        const srimValues = showSrim
            ? viewData.map((d) => Number(d?.fvScenario0)).filter((v) => Number.isFinite(v))
            : [];
        const maxValue = Math.max(...highs, ...srimValues);
        const minValue = Math.min(...lows, ...srimValues);
        const range = Math.max(1, maxValue - minValue);

        const yFor = (v) =>
            padding.top + ((maxValue - v) / range) * plotHeight;

        const count = viewData.length;
        const gap = plotWidth / Math.max(1, count);
        canvas.__chartLayout = {
            padding,
            gap,
            width,
            height
        };

        ctx.strokeStyle = "#e5e7eb";
        ctx.lineWidth = 1;
        const gridCount = 4;
        for (let i = 0; i <= gridCount; i += 1) {
            const y = padding.top + (plotHeight / gridCount) * i;
            ctx.beginPath();
            ctx.moveTo(padding.left, y);
            ctx.lineTo(width - padding.right, y);
            ctx.stroke();
        }

        ctx.fillStyle = "#64748b";
        ctx.font = "11px sans-serif";
        const labelValues = [maxValue, (maxValue + minValue) / 2, minValue];
        labelValues.forEach((v, i) => {
            const y = yFor(v);
            const text = formatNumber(v);
            ctx.fillText(text, 6, y + (i === 0 ? 4 : 3));
        });

        const bodyWidth = Math.max(2, gap * 0.98);
        const wickColor = "#6b7280";
        const upColor = "#16a34a";
        const downColor = "#dc2626";
        const highlightColor = "#111827";

        viewData.forEach((d, idx) => {
            const open = Number(d?.open);
            const close = Number(d?.close);
            const high = Number(d?.high);
            const low = Number(d?.low);
            if (!Number.isFinite(open) || !Number.isFinite(close) || !Number.isFinite(high) || !Number.isFinite(low)) {
                return;
            }

            const x = padding.left + idx * gap + gap / 2;
            const yOpen = yFor(open);
            const yClose = yFor(close);
            const yHigh = yFor(high);
            const yLow = yFor(low);

            ctx.strokeStyle = wickColor;
            ctx.beginPath();
            ctx.moveTo(x, yHigh);
            ctx.lineTo(x, yLow);
            ctx.stroke();

            const isUp = close >= open;
            ctx.fillStyle = isUp ? upColor : downColor;
            const bodyTop = isUp ? yClose : yOpen;
            const bodyBottom = isUp ? yOpen : yClose;
            const bodyHeight = Math.max(1, bodyBottom - bodyTop);
            ctx.fillRect(x - bodyWidth / 2, bodyTop, bodyWidth, bodyHeight);

            const highlight = idx === container?.__chartHoverIndex || idx === container?.__chartSelectedIndex;
            if (highlight) {
                ctx.strokeStyle = highlightColor;
                ctx.lineWidth = 1;
                ctx.strokeRect(x - bodyWidth / 2, bodyTop, bodyWidth, bodyHeight);
            }
        });

        if (showSrim) {
            this._drawSrimLine(ctx, viewData, padding, gap, yFor);
        }

        this._renderYearBoundaries(ctx, viewData, padding, gap, height);

        const startLabel = formatDateLabel(viewData[0]?.date);
        const endLabel = formatDateLabel(viewData[count - 1]?.date);
        ctx.fillStyle = "#6b7280";
        ctx.font = "11px sans-serif";
        ctx.fillText(startLabel, padding.left, height - 6);
        const endTextWidth = ctx.measureText(endLabel).width;
        ctx.fillText(endLabel, width - padding.right - endTextWidth, height - 6);

        this._renderCandleSelection(canvas, viewData, container, padding, gap, yFor);
    },

    _renderCharts(container) {
        const canvas = qs('canvas[data-chart="price-candle"]', container);
        if (!canvas) return;
        this._renderCandleChart(canvas, container);
        this._renderPerChart(container);
    },

    _renderPerChart(container) {
        const metricContainer = qs("#metricChartContainer");
        if (!metricContainer) return;
        const canvas = qs('canvas[data-chart="per-line"]', metricContainer);
        if (!canvas) return;

        const viewData = container.__chartViewData || [];
        if (viewData.length === 0) return;

        const rect = canvas.getBoundingClientRect();
        const dpr = window.devicePixelRatio || 1;
        const width = Math.max(1, Math.floor(rect.width));
        const height = Math.max(1, Math.floor(rect.height));

        canvas.width = Math.floor(width * dpr);
        canvas.height = Math.floor(height * dpr);
        const ctx = canvas.getContext("2d");
        if (!ctx) return;
        ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
        ctx.clearRect(0, 0, width, height);

        const padding = { left: 50, right: 16, top: 16, bottom: 24 };
        const plotWidth = width - padding.left - padding.right;
        const plotHeight = height - padding.top - padding.bottom;
        if (plotWidth <= 0 || plotHeight <= 0) return;

        const perValues = viewData
            .map((d) => Number(d?.per))
            .filter((v) => Number.isFinite(v));
        if (perValues.length === 0) {
            ctx.fillStyle = "#6b7280";
            ctx.font = "12px sans-serif";
            ctx.fillText("PER 데이터 없음", padding.left, padding.top + 12);
            return;
        }

        const maxValue = Math.max(...perValues);
        const minValue = Math.min(...perValues);
        const range = Math.max(1, maxValue - minValue);

        const yFor = (v) =>
            padding.top + ((maxValue - v) / range) * plotHeight;

        ctx.strokeStyle = "#e5e7eb";
        ctx.lineWidth = 1;
        const gridCount = 3;
        for (let i = 0; i <= gridCount; i += 1) {
            const y = padding.top + (plotHeight / gridCount) * i;
            ctx.beginPath();
            ctx.moveTo(padding.left, y);
            ctx.lineTo(width - padding.right, y);
            ctx.stroke();
        }

        ctx.fillStyle = "#64748b";
        ctx.font = "11px sans-serif";
        const labelValues = [maxValue, (maxValue + minValue) / 2, minValue];
        labelValues.forEach((v, i) => {
            const y = yFor(v);
            const text = formatPerValue(v);
            ctx.fillText(text, 6, y + (i === 0 ? 4 : 3));
        });

        const count = viewData.length;
        const gap = plotWidth / Math.max(1, count);
        ctx.strokeStyle = "#2563eb";
        ctx.lineWidth = 2;
        ctx.beginPath();
        let started = false;
        viewData.forEach((d, idx) => {
            const per = Number(d?.per);
            if (!Number.isFinite(per)) return;
            const x = padding.left + idx * gap + gap / 2;
            const y = yFor(per);
            if (!started) {
                ctx.moveTo(x, y);
                started = true;
            } else {
                ctx.lineTo(x, y);
            }
        });
        ctx.stroke();

        const startLabel = formatDateLabel(viewData[0]?.date);
        const endLabel = formatDateLabel(viewData[count - 1]?.date);
        ctx.fillStyle = "#6b7280";
        ctx.font = "11px sans-serif";
        ctx.fillText(startLabel, padding.left, height - 6);
        const endTextWidth = ctx.measureText(endLabel).width;
        ctx.fillText(endLabel, width - padding.right - endTextWidth, height - 6);
    },

    _renderYearBoundaries(ctx, viewData, padding, gap, height) {
        if (!ctx || !Array.isArray(viewData) || viewData.length < 2) return;
        ctx.save();
        ctx.setLineDash([4, 4]);
        ctx.strokeStyle = "#cbd5f5";
        ctx.lineWidth = 1;

        for (let i = 1; i < viewData.length; i += 1) {
            const prev = viewData[i - 1]?.date;
            const curr = viewData[i]?.date;
            const prevYear = getYearFromDate(prev);
            const currYear = getYearFromDate(curr);
            if (!prevYear || !currYear || prevYear === currYear) continue;
            const x = padding.left + i * gap;
            ctx.beginPath();
            ctx.moveTo(x, padding.top);
            ctx.lineTo(x, height - padding.bottom);
            ctx.stroke();
        }

        ctx.restore();
    },

    _drawSrimLine(ctx, viewData, padding, gap, yFor) {
        ctx.save();
        ctx.strokeStyle = "#2563eb";
        ctx.lineWidth = 2;
        ctx.beginPath();

        let started = false;
        viewData.forEach((point, idx) => {
            const fairValue = Number(point?.fvScenario0);
            if (!Number.isFinite(fairValue)) {
                started = false;
                return;
            }

            const x = padding.left + idx * gap + gap / 2;
            const y = yFor(fairValue);
            if (!started) {
                ctx.moveTo(x, y);
                started = true;
            } else {
                ctx.lineTo(x, y);
            }
        });

        ctx.stroke();
        ctx.restore();
    },

    _renderCandleSelection(canvas, data, container, padding, gap, yFor) {
        const ctx = canvas.getContext("2d");
        if (!ctx || !container) return;

        const idx = Number.isInteger(container.__chartSelectedIndex)
            ? container.__chartSelectedIndex
            : null;
        const tooltip = qs("[data-chart-tooltip]", container);

        const hoverIdx = Number.isInteger(container.__chartHoverIndex)
            ? container.__chartHoverIndex
            : null;
        const targetIdx = hoverIdx ?? idx;

        if (targetIdx === null || !data[targetIdx]) {
            if (tooltip) tooltip.hidden = true;
            return;
        }

        const point = data[targetIdx];
        const x = padding.left + targetIdx * gap + gap / 2;
        const yHigh = yFor(Number(point?.high));
        const yLow = yFor(Number(point?.low));

        ctx.strokeStyle = "#111827";
        ctx.lineWidth = 1;
        ctx.beginPath();
        ctx.moveTo(x, yHigh);
        ctx.lineTo(x, yLow);
        ctx.stroke();

        if (tooltip) {
            tooltip.hidden = false;
            tooltip.innerHTML = buildCandleTooltipHtml(point, container.__chartShowSrim !== false);
            tooltip.style.left = `${x}px`;
            tooltip.style.top = `${padding.top + 6}px`;
        }
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

function formatDateLabel(value) {
    if (!value) return "-";
    if (value instanceof Date) {
        return formatDateParam(value).replaceAll("-", ".");
    }
    return String(value).replaceAll("-", ".");
}

function buildCandleTooltipHtml(point, showSrim = true) {
    const date = formatDateLabel(point?.date);
    const open = formatNumber(point?.open);
    const high = formatNumber(point?.high);
    const low = formatNumber(point?.low);
    const close = formatNumber(point?.close);
    const srim = Number.isFinite(Number(point?.fvScenario0))
        ? `<div>S-RIM ${formatNumber(point?.fvScenario0)}</div>`
        : "";

    return `
        <div>${date}</div>
        <div>시 ${open}</div>
        <div>고 ${high}</div>
        <div>저 ${low}</div>
        <div>종 ${close}</div>
        ${showSrim ? srim : ""}
    `;
}

function formatPerValue(value) {
    const n = Number(value);
    if (!Number.isFinite(n)) return "-";
    return n.toFixed(2);
}

function buildDateRange(rangeKey) {
    const today = new Date();
    const endDate = formatDateParam(today);
    const start = new Date(today);
    const key = String(rangeKey || "1y").toLowerCase();
    const value = Number(key.slice(0, -1)) || 1;
    const unit = key.slice(-1);

    if (unit === "w") {
        start.setDate(start.getDate() - value * 7);
    } else if (unit === "m") {
        start.setMonth(start.getMonth() - value);
    } else {
        start.setFullYear(start.getFullYear() - value);
    }
    return {
        startDate: formatDateParam(start),
        endDate
    };
}

function buildDateRangeFromEnd(endDate, rangeKey) {
    const end = new Date(endDate);
    if (!Number.isFinite(end.getTime())) {
        return buildDateRange(rangeKey);
    }
    const start = new Date(end);
    const key = String(rangeKey || "1y").toLowerCase();
    const value = Number(key.slice(0, -1)) || 1;
    const unit = key.slice(-1);

    if (unit === "w") {
        start.setDate(start.getDate() - value * 7);
    } else if (unit === "m") {
        start.setMonth(start.getMonth() - value);
    } else {
        start.setFullYear(start.getFullYear() - value);
    }
    return {
        startDate: formatDateParam(start),
        endDate: formatDateParam(end)
    };
}

function formatDateParam(date) {
    const y = date.getFullYear();
    const m = String(date.getMonth() + 1).padStart(2, "0");
    const d = String(date.getDate()).padStart(2, "0");
    return `${y}-${m}-${d}`;
}

function parseDate(value) {
    if (!value) return null;
    const d = new Date(value);
    if (!Number.isFinite(d.getTime())) return null;
    return d;
}

function dateToMs(value) {
    const d = parseDate(value);
    return d ? d.getTime() : 0;
}

function addMonths(date, months) {
    const d = new Date(date);
    d.setMonth(d.getMonth() + months);
    return d;
}

function addYears(date, years) {
    const d = new Date(date);
    d.setFullYear(d.getFullYear() + years);
    return d;
}

function addDays(date, days) {
    const d = new Date(date);
    d.setDate(d.getDate() + days);
    return d;
}

function getYearFromDate(value) {
    const d = parseDate(value);
    if (!d) return null;
    return d.getFullYear();
}

function escapeHtml(str) {
    return String(str ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#39;");
}
