import {detectPage} from "./page.js";
import {HomePage} from "../pages/home.js";
import {StockDetailPage} from "../pages/stock-detail.js";


const pageMap = {
    home: HomePage,
    "stock-detail": StockDetailPage,
};

export function boot() {
    const page = detectPage();
    console.log("[app] page = ", page);

    const handler = pageMap[page];
    if (!handler) return;

    handler.mount();
}