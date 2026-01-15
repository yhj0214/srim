export function detectPage() {
    const el = document.querySelector("[data-page]");
    return el?.dataset?.page ?? null;
}