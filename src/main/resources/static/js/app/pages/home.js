import { qs } from "../ui/dom.js";

export const HomePage = {
    mount() {
        console.log("[home] mounted");

        const form = qs("#homeSearchForm");
        const input = qs("#homeSearchInput");
        const hint = qs("#homeSearchHint");
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

    },
};