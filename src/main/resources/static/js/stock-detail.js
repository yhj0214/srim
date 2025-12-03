/**
 * 종목 상세 페이지 메인 로직 - 기존 HTML 보존 버전
 */

const StockDetail = {
    stockId: null,
    companyId: null,
    financialData: null,
    
    init: function(stockId, companyId) {
        this.stockId = stockId;
        this.companyId = companyId;
        
        console.log('=== StockDetail 초기화 ===');
        console.log('stockId:', this.stockId);
        console.log('companyId:', this.companyId);
        
        this.showFullPageLoading();
        this.startDataLoading();
    },
    
    showFullPageLoading: function() {
        console.log('🔄 전체 로딩 표시');
        
        // 탭 숨기기
        const tabsContainer = document.querySelector('.nav-tabs');
        if (tabsContainer) {
            tabsContainer.style.display = 'none';
        }
        
        // 기존 탭 컨텐츠 숨기기 (삭제하지 않음!)
        const tabPanes = document.querySelectorAll('.tab-pane');
        tabPanes.forEach(pane => {
            pane.style.display = 'none';
        });
        
        // 로딩 오버레이 추가
        const tabContent = document.querySelector('.tab-content');
        if (tabContent) {
            let loadingOverlay = document.getElementById('loadingOverlay');
            if (!loadingOverlay) {
                loadingOverlay = document.createElement('div');
                loadingOverlay.id = 'loadingOverlay';
                loadingOverlay.style.cssText = 'position: absolute; top: 0; left: 0; right: 0; bottom: 0; background: white; z-index: 9999; min-height: 500px;';
                loadingOverlay.innerHTML = `
                    <div class="d-flex flex-column justify-content-center align-items-center" style="min-height: 500px;">
                        <div class="spinner-border text-primary" style="width: 4rem; height: 4rem;" role="status">
                            <span class="visually-hidden">로딩중...</span>
                        </div>
                        <h4 class="mt-4 text-primary">데이터를 불러오는 중입니다...</h4>
                        <p class="text-muted" id="fullPageLoadingStatus">재무정보 조회 및 크롤링 중...</p>
                        <p class="text-muted"><small>최대 1-2분 정도 소요됩니다. 잠시만 기다려주세요.</small></p>
                    </div>
                `;
                
                tabContent.style.position = 'relative';
                tabContent.appendChild(loadingOverlay);
            }
        }
    },
    
    updateFullPageLoadingStatus: function(message) {
        const statusEl = document.getElementById('fullPageLoadingStatus');
        if (statusEl) {
            statusEl.textContent = message;
        }
    },
    
    hideFullPageLoading: function() {
        console.log('✅ 로딩 숨김, 기존 탭 표시');
        
        // 로딩 오버레이 제거
        const loadingOverlay = document.getElementById('loadingOverlay');
        if (loadingOverlay) {
            loadingOverlay.remove();
        }
        
        // 탭 표시
        const tabsContainer = document.querySelector('.nav-tabs');
        if (tabsContainer) {
            tabsContainer.style.display = '';
        }
        
        // 기존 탭 컨텐츠 표시
        const tabPanes = document.querySelectorAll('.tab-pane');
        tabPanes.forEach(pane => {
            pane.style.display = '';
        });
        
        console.log('✅ 컨테이너 확인:', {
            financialTableContainer: !!document.getElementById('financialTableContainer'),
            priceChartContainer: !!document.getElementById('priceChartContainer'),
            srimResultContainer: !!document.getElementById('srimResultContainer')
        });
        
        this.setupEventListeners();
    },
    
    setupEventListeners: function() {
        const financialTab = document.getElementById('financial-tab');
        if (financialTab) {
            financialTab.addEventListener('shown.bs.tab', () => {
                console.log('=== 재무정보 탭 표시됨 ===');
            });
        }
        
        const chartTab = document.getElementById('chart-tab');
        if (chartTab) {
            chartTab.addEventListener('shown.bs.tab', () => {
                console.log('=== 주가 그래프 탭 표시됨 ===');
            });
        }
        
        const srimTab = document.getElementById('srim-tab');
        if (srimTab) {
            srimTab.addEventListener('shown.bs.tab', () => {
                console.log('=== S-RIM 탭 표시됨 ===');
            });
        }
    },
    
    startDataLoading: function() {
        console.log('=== 데이터 로드 프로세스 시작 ===');
        
        this.updateFullPageLoadingStatus('재무정보 조회 및 크롤링 중... (최대 1-2분 소요)');
        
        this.loadFinancialData()
            .then((financialResult) => {
                console.log('✅ 재무정보 로드 완료');
                
                // 재무정보 응답에서 companyId 추출
                if (financialResult.companyId) {
                    const oldCompanyId = this.companyId;
                    this.companyId = financialResult.companyId;
                    console.log('🔄 companyId 업데이트 (재무정보):', oldCompanyId, '→', this.companyId);
                }
                
                return this.tryRefreshCompanyId();
            })
            .then(() => {
                console.log('✅ 최종 companyId:', this.companyId);
                
                // 로딩 숨기고 기존 탭 표시
                this.hideFullPageLoading();
                
                // 재무정보 렌더링
                if (this.financialData) {
                    console.log('📊 재무정보 렌더링 시작');
                    StockFinancial.renderTable(this.financialData);
                }
                
                this.updateFullPageLoadingStatus('주가 데이터 및 S-RIM 계산 중...');
                
                // 주가 & S-RIM 로드
                return Promise.allSettled([
                    this.loadChartData(),
                    this.loadSrimData()
                ]);
            })
            .then((results) => {
                console.log('=== 모든 API 호출 완료 ===');
                results.forEach((result, index) => {
                    const name = index === 0 ? '주가 차트' : 'S-RIM';
                    if (result.status === 'fulfilled') {
                        console.log(`✅ ${name} 성공`);
                    } else {
                        console.warn(`⚠️ ${name} 실패:`, result.reason);
                    }
                });
                
                console.log('🎉 모든 데이터 로드 완료!');
                
                // 개요 탭 활성화
                this.activateOverviewTab();
            })
            .catch(error => {
                console.error('❌ 치명적 에러:', error);
                this.hideFullPageLoading();
                alert('데이터를 불러오는 중 오류가 발생했습니다: ' + error.message);
            });
    },
    
    tryRefreshCompanyId: function() {
        return new Promise((resolve) => {
            if (this.companyId) {
                console.log('ℹ️ companyId 이미 있음, 재조회 스킵:', this.companyId);
                resolve();
                return;
            }
            
            console.log('🔄 companyId 재조회 시도...');
            
            if (!this.stockId) {
                console.warn('⚠️ stockId가 없어 companyId 재조회 불가');
                resolve();
                return;
            }
            
            fetch(`/api/stocks/${this.stockId}`)
                .then(response => {
                    if (!response.ok) throw new Error(`HTTP ${response.status}`);
                    return response.json();
                })
                .then(result => {
                    if (result.success && result.data?.companyId) {
                        this.companyId = result.data.companyId;
                        console.log('🔄 companyId 업데이트 (API):', this.companyId);
                    }
                    resolve();
                })
                .catch(error => {
                    console.warn('⚠️ companyId 재조회 실패 (무시):', error.message);
                    resolve();
                });
        });
    },
    
    activateOverviewTab: function() {
        const overviewTab = document.getElementById('overview-tab');
        if (overviewTab && typeof bootstrap !== 'undefined') {
            const tab = new bootstrap.Tab(overviewTab);
            tab.show();
        }
    },
    
    loadFinancialData: function() {
        return new Promise((resolve, reject) => {
            if (!this.stockId) {
                reject(new Error('stockId가 없습니다'));
                return;
            }

            console.log('📊 재무정보 로드 중...');

            fetch(`/api/stocks/${this.stockId}/financial/annual`)
                .then(response => {
                    if (!response.ok) throw new Error(`HTTP ${response.status}`);
                    return response.json();
                })
                .then(result => {
                    if (result.success) {
                        this.financialData = result.data;
                        console.log('✅ 재무 데이터 저장 완료');
                        resolve(result.data);
                    } else {
                        reject(new Error(result.message || '재무 데이터 로드 실패'));
                    }
                })
                .catch(error => {
                    console.error('❌ 재무정보 로드 실패:', error);
                    reject(error);
                });
        });
    },
    
    loadChartData: function() {
        return new Promise((resolve, reject) => {
            if (!this.companyId) {
                console.warn('⚠️ companyId 없음, 주가 차트 스킵');
                const container = document.getElementById('priceChartContainer');
                if (container) {
                    container.innerHTML = '<div class="alert alert-info">회사 정보가 등록되지 않아 주가 데이터를 조회할 수 없습니다.</div>';
                }
                resolve();
                return;
            }

            console.log('📈 주가 차트 로드 중... (companyId:', this.companyId, ')');
            
            const endDate = new Date();
            const startDate = new Date();
            startDate.setFullYear(endDate.getFullYear() - 1);
            
            const url = `/api/stocks/${this.companyId}/price-chart?startDate=${startDate.toISOString().split('T')[0]}&endDate=${endDate.toISOString().split('T')[0]}`;
            
            fetch(url)
                .then(response => {
                    if (!response.ok) throw new Error(`HTTP ${response.status}`);
                    return response.json();
                })
                .then(result => {
                    if (result.success) {
                        console.log('✅ 주가 데이터 저장 완료');
                        if (typeof StockChart !== 'undefined' && StockChart.render) {
                            StockChart.render(result.data);
                        }
                        resolve(result.data);
                    } else {
                        throw new Error(result.message || '주가 데이터 로드 실패');
                    }
                })
                .catch(error => {
                    console.error('❌ 주가 차트 로드 실패:', error);
                    const container = document.getElementById('priceChartContainer');
                    if (container) {
                        container.innerHTML = `<div class="alert alert-warning">${error.message}</div>`;
                    }
                    reject(error);
                });
        });
    },
    
    loadSrimData: function() {
        return new Promise((resolve, reject) => {
            if (!this.companyId) {
                console.warn('⚠️ companyId 없음, S-RIM 스킵');
                const container = document.getElementById('srimResultContainer');
                if (container) {
                    container.innerHTML = '<div class="alert alert-info">회사 정보가 등록되지 않아 S-RIM을 계산할 수 없습니다.</div>';
                }
                resolve();
                return;
            }

            console.log('🧮 S-RIM 계산 중... (companyId:', this.companyId, ')');
            
            fetch(`/api/stocks/${this.companyId}/srim?basis=YEAR`)
                .then(response => {
                    if (!response.ok) throw new Error(`HTTP ${response.status}`);
                    return response.json();
                })
                .then(result => {
                    if (result.success) {
                        console.log('✅ S-RIM 데이터 저장 완료');
                        if (typeof StockSrim !== 'undefined' && StockSrim.renderResult) {
                            StockSrim.renderResult(result.data);
                        }
                        resolve(result.data);
                    } else {
                        throw new Error(result.message || 'S-RIM 계산 실패');
                    }
                })
                .catch(error => {
                    console.error('❌ S-RIM 로드 실패:', error);
                    const container = document.getElementById('srimResultContainer');
                    if (container) {
                        container.innerHTML = `<div class="alert alert-danger">${error.message}</div>`;
                    }
                    reject(error);
                });
        });
    }
};

document.addEventListener('DOMContentLoaded', function() {
    const stockId = window.STOCK_ID;
    const companyId = window.COMPANY_ID;
    
    StockDetail.init(stockId, companyId);
});
