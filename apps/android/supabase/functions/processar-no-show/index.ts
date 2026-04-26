// ═══════════════════════════════════════════════════════════════════════════
// processar-no-show/index.ts  · Fase 4.3
//
// Disparada pelo cron a cada 10 minutos.
// Busca agendamentos no_show sem estorno processado e:
//   1. Cria crédito de reembolso para o cliente
//   2. Penaliza o profissional no ranking_score (-10 pontos)
//   3. Marca estorno_pago = true
// ═══════════════════════════════════════════════════════════════════════════

import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_KEY  = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const CRON_SECRET  = Deno.env.get("CRON_SECRET") ?? "";

serve(async (req) => {
    const auth = req.headers.get("Authorization") ?? "";
    if (CRON_SECRET && auth !== `Bearer ${CRON_SECRET}`) {
        return resp({ erro: "Unauthorized" }, 401);
    }

    const supabase = createClient(SUPABASE_URL, SERVICE_KEY);

    // Buscar agendamentos no_show sem estorno
    const { data: noShows, error } = await supabase
        .from("agendamentos")
        .select("id, client_id, professional_id, valor")
        .eq("no_show", true)
        .eq("estorno_pago", false)
        .limit(50);

    if (error) return resp({ erro: error.message }, 500);
    if (!noShows?.length) return resp({ processados: 0 });

    let processados = 0;

    for (const ag of noShows) {
        try {
            // 1. Crédito de reembolso para o cliente
            await supabase.from("credits").insert({
                user_id    : ag.client_id,
                amount     : ag.valor ?? 0,
                reason     : "no_show_reembolso",
                expires_at : null,  // sem expiração — é um reembolso
            });

            // 2. Penalizar profissional no ranking_score
            await supabase.rpc("penalizar_profissional_no_show", {
                p_profissional_id: ag.professional_id,
            });

            // 3. Marcar estorno processado
            await supabase
                .from("agendamentos")
                .update({ estorno_pago: true })
                .eq("id", ag.id);

            processados++;
        } catch (e) {
            console.error(`Erro ao processar no_show ${ag.id}:`, e);
        }
    }

    return resp({ processados, total: noShows.length });
});

function resp(body: unknown, status = 200): Response {
    return new Response(JSON.stringify(body), {
        status,
        headers: { "Content-Type": "application/json" },
    });
}