const StockChart = {
    chart: null,
    chartData: null,
    hoveredCandleIndex: null,
    mouseX: null,
    mouseY: null,
    
    loadData: function(companyId, startDate, endDate) {
        if (!companyId) { this.showError('회사 정보가 없습니다.'); return; }
        this.showLoading();
        
        let url = `/api/stocks/${companyId}/price-chart?`;
        if (startDate) url += `startDate=${startDate}&`;
        if (endDate) url += `endDate=${endDate}`;
        
        fetch(url)
            .then(r => r.json())
            .then(result => result.success ? this.render(result.data) : this.showError(result.message))
            .catch(e => this.showError('오류: ' + e.message));
    },
    
    render: function(data) {
        document.getElementById('priceChartContainer').innerHTML = `
            <div class="mb-3 d-flex justify-content-end">
                <input type="date" id="startDate" class="form-control form-control-sm d-inline-block" style="width:150px;" value="${data.priceData[0].date}">
                <span class="mx-2">~</span>
                <input type="date" id="endDate" class="form-control form-control-sm d-inline-block" style="width:150px;" value="${data.priceData[data.priceData.length-1].date}">
                <button class="btn btn-primary btn-sm ms-2" onclick="applyCustomPeriod()">조회</button>
            </div>
            <div style="height:500px;position:relative;">
                <canvas id="stockPriceChart"></canvas>
            </div>
            <div class="mt-3 p-3 bg-light rounded">
                <h6 class="mb-3">적정주가 표시</h6>
                ${['scenario0', 'scenario10', 'scenario20', 'scenario30', 'scenario50'].map((s, i) => `
                    <div class="form-check form-check-inline">
                        <input class="form-check-input" type="checkbox" id="${s}" value="${s}" onchange="toggleScenarioLine(this)">
                        <label class="form-check-label" for="${s}">${['초과이익 지속', '10% 감소', '20% 감소', '30% 감소', '50% 감소'][i]}</label>
                    </div>
                `).join('')}
            </div>
        `;
        this.createCandlestick(data);
    },
    
    createCandlestick: function(data) {
        const ctx = document.getElementById('stockPriceChart');
        if (this.chart) this.chart.destroy();

        const dates = data.priceData.map(d => d.date);
        const allPrices = data.priceData.flatMap(d => [d.open, d.high, d.low, d.close, 
            d.fairValues.scenario0, d.fairValues.scenario10, d.fairValues.scenario20, 
            d.fairValues.scenario30, d.fairValues.scenario50]);
        const minPrice = Math.min(...allPrices);
        const maxPrice = Math.max(...allPrices);
        const padding = (maxPrice - minPrice) * 0.05;

        this.chart = new Chart(ctx, {
            type: 'scatter',
            data: { datasets: [{ label: '주가', data: [], pointRadius: 0 }] },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                interaction: { intersect: false, mode: 'index' },
                layout: {
                    padding: {
                        left: 10,
                        right: 80,  // 오른쪽에 가격 라벨 공간
                        top: 10,
                        bottom: 10
                    }
                },
                plugins: { 
                    legend: { display: false }, 
                    tooltip: { enabled: false, external: this.createTooltip.bind(this, data, dates) } 
                },
                scales: {
                    x: { 
                        type: 'linear', 
                        min: 0, 
                        max: dates.length - 1, 
                        grid: { 
                            color: 'rgba(0,0,0,0.05)',
                            drawBorder: false
                        },
                        ticks: { 
                            maxTicksLimit: 10, 
                            callback: v => dates[Math.round(v)]?.substring(5) || '' 
                        }
                    },
                    y: { 
                        min: minPrice - padding, 
                        max: maxPrice + padding, 
                        grid: { 
                            color: 'rgba(0,0,0,0.05)',
                            drawBorder: false
                        },
                        ticks: { callback: v => v.toLocaleString() + '원' }
                    }
                },
                onHover: this.handleHover.bind(this, data, dates)
            },
            plugins: [
                { id: 'crosshair', afterDatasetsDraw: this.drawCrosshair.bind(this, data, dates) },
                { id: 'candles', afterDatasetsDraw: this.drawCandles.bind(this, data, dates) }
            ]
        });
        
        this.chartData = data;
        window.chartData = data;
        
        ctx.addEventListener('mouseleave', () => {
            this.hoveredCandleIndex = null;
            this.mouseX = null;
            this.mouseY = null;
            if (this.chart) this.chart.update('none');
            const tip = document.getElementById('chartjs-tooltip');
            if (tip) tip.style.display = 'none';
        });
    },
    
    handleHover: function(data, dates, event, activeElements, chart) {
        if (!chart?.chartArea) return;
        
        const pos = Chart.helpers.getRelativePosition(event, chart);
        
        // 마우스 위치 저장 (crosshair용)
        this.mouseX = pos.x;
        this.mouseY = pos.y;
        
        // 가장 가까운 캔들 찾기 (캔들 강조용)
        const dataX = chart.scales.x.getValueForPixel(pos.x);
        const idx = Math.round(dataX);
        
        if (idx >= 0 && idx < dates.length) {
            if (this.hoveredCandleIndex !== idx) {
                this.hoveredCandleIndex = idx;
                chart.update('none');
            }
        } else if (this.hoveredCandleIndex !== null) {
            this.hoveredCandleIndex = null;
            chart.update('none');
        }
        
        // crosshair는 항상 업데이트
        chart.update('none');
    },
    
    drawCrosshair: function(data, dates, chart) {
        // 마우스가 차트 영역에 없으면 그리지 않음
        if (this.mouseX === null || this.mouseY === null) return;
        
        const ctx = chart.ctx;
        const area = chart.chartArea;
        const xScale = chart.scales.x;
        const yScale = chart.scales.y;
        
        // 차트 영역 내부인지 확인
        if (this.mouseX < area.left || this.mouseX > area.right || 
            this.mouseY < area.top || this.mouseY > area.bottom) return;
        
        const x = this.mouseX;
        const y = this.mouseY;
        
        ctx.save();
        ctx.strokeStyle = 'rgba(75,192,192,0.7)';
        ctx.lineWidth = 1;
        ctx.setLineDash([5, 5]);
        
        // 수직선 (마우스 X 위치)
        ctx.beginPath();
        ctx.moveTo(x, area.top);
        ctx.lineTo(x, area.bottom);
        ctx.stroke();
        
        // 수평선 (마우스 Y 위치)
        ctx.beginPath();
        ctx.moveTo(area.left, y);
        ctx.lineTo(area.right, y);
        ctx.stroke();
        ctx.setLineDash([]);
        
        // X축 날짜 라벨
        const dataX = xScale.getValueForPixel(x);
        const dateIdx = Math.round(dataX);
        if (dateIdx >= 0 && dateIdx < dates.length) {
            const date = dates[dateIdx];
            ctx.fillStyle = 'rgba(75,192,192,0.9)';
            ctx.fillRect(x - 30, area.bottom + 5, 60, 20);
            ctx.fillStyle = 'white';
            ctx.font = '12px Arial';
            ctx.textAlign = 'center';
            ctx.fillText(date.substring(5), x, area.bottom + 18);
        }
        
        // Y축 가격 라벨
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
    
    drawCandles: function(data, dates, chart) {
        const ctx = chart.ctx;
        const xScale = chart.scales.x;
        const yScale = chart.scales.y;
        const candleWidth = Math.max(1, Math.min(10, (chart.chartArea.right - chart.chartArea.left) / dates.length * 0.7));
        
        data.priceData.forEach((p, i) => {
            const isUp = p.close >= p.open;
            const isHovered = i === this.hoveredCandleIndex;
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
            ctx.fillRect(x - width/2, top, width, h);
            ctx.strokeRect(x - width/2, top, width, h);
            if (isHovered) ctx.shadowBlur = 0;
            ctx.globalAlpha = 1.0;
        });
    },
    
    createTooltip: function(data, dates, context) {
        let tip = document.getElementById('chartjs-tooltip');
        if (!tip) {
            tip = document.createElement('div');
            tip.id = 'chartjs-tooltip';
            tip.style.cssText = 'position:absolute;background:rgba(0,0,0,0.85);color:white;padding:12px;border-radius:6px;pointer-events:none;font-size:13px;z-index:10000;box-shadow:0 2px 8px rgba(0,0,0,0.3);';
            document.body.appendChild(tip);
        }
        
        const idx = this.hoveredCandleIndex;
        if (idx >= 0 && idx < data.priceData.length) {
            const p = data.priceData[idx];
            const change = p.close - p.open;
            const pct = ((change / p.open) * 100).toFixed(2);
            const color = change >= 0 ? '#ff5252' : '#36a2eb';
            
            tip.innerHTML = `
                <div style="margin-bottom:8px;"><strong style="font-size:14px;">${p.date}</strong></div>
                <div style="line-height:1.6;">
                    <div>시가: <strong>${p.open.toLocaleString()}원</strong></div>
                    <div>고가: <strong>${p.high.toLocaleString()}원</strong></div>
                    <div>저가: <strong>${p.low.toLocaleString()}원</strong></div>
                    <div>종가: <strong>${p.close.toLocaleString()}원</strong></div>
                    <div style="color:${color};margin-top:4px;">변동: <strong>${change > 0 ? '+' : ''}${change.toLocaleString()}원 (${pct}%)</strong></div>
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
    
    showLoading: function() {
        document.getElementById('priceChartContainer').innerHTML = '<div class="text-center py-5"><div class="spinner-border text-primary"></div><p class="mt-3">로딩중...</p></div>';
    },
    
    showError: function(msg) {
        document.getElementById('priceChartContainer').innerHTML = `<div class="alert alert-warning">${msg}</div>`;
    }
};

function changePeriod(days) {
    const end = new Date();
    const start = new Date();
    start.setDate(end.getDate() - days);
    document.getElementById('startDate').value = start.toISOString().split('T')[0];
    document.getElementById('endDate').value = end.toISOString().split('T')[0];
    StockChart.loadData(StockDetail.companyId, start.toISOString().split('T')[0], end.toISOString().split('T')[0]);
}

function applyCustomPeriod() {
    const s = document.getElementById('startDate').value;
    const e = document.getElementById('endDate').value;
    if (!s || !e) { alert('날짜를 선택하세요.'); return; }
    if (new Date(s) > new Date(e)) { alert('시작일이 종료일보다 늦습니다.'); return; }
    StockChart.loadData(StockDetail.companyId, s, e);
}

function toggleScenarioLine(cb) {
    if (!StockChart.chart || !window.chartData) return;
    const colors = { scenario0: 'rgb(255,99,132)', scenario10: 'rgb(255,159,64)', scenario20: 'rgb(255,205,86)', scenario30: 'rgb(153,102,255)', scenario50: 'rgb(201,203,207)' };
    const labels = { scenario0: '초과이익 지속', scenario10: '10% 감소', scenario20: '20% 감소', scenario30: '30% 감소', scenario50: '50% 감소' };
    
    if (cb.checked) {
        StockChart.chart.data.datasets.push({
            label: labels[cb.value],
            data: window.chartData.priceData.map((d, i) => ({ x: i, y: d.fairValues[cb.value] })),
            type: 'line',
            borderColor: colors[cb.value],
            backgroundColor: 'transparent',
            borderWidth: 2,
            borderDash: [5, 5],
            pointRadius: 0,
            showLine: true
        });
    } else {
        const idx = StockChart.chart.data.datasets.findIndex(ds => ds.label === labels[cb.value]);
        if (idx > -1) StockChart.chart.data.datasets.splice(idx, 1);
    }
    StockChart.chart.update();
}
