export async function apiGetJSON(url) {
    try{
        const resp = await fetch(url, {headers: {"Accept": "application/json"}});
        const data = await resp.json().catch(() => null);

        if(!resp.ok) {
            return {
                ok: false,
                status: resp.status,
                data: null,
                message: (data && data.message) || `HTTP ${resp.status}`
            };
        }
        return {
            ok: true,
            status: resp.status,
            data: data,
            message: null
        };
    } catch (e) {
        return {
            ok: false,
            status: 500,
            data: null,
            message: e?.message || "네트워크 오류 발생"
        };
    }
}