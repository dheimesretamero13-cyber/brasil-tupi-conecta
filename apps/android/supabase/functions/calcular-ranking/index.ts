// ═══════════════════════════════════════════════════════════════════════════
// calcular-ranking/index.ts  · Fase 4.1
//
// Roda diariamente via Cron Job do Supabase.
// Calcula ranking_score para todos os profissionais usando:
//
//   score = (avg_rating        * 0.40)
//         + (completed_factor  * 0.30)
//         + (response_factor   * 0.20)
//         + (verified_bonus    * 0.10)
//
// Onde:
//   avg_rating       = média das avaliações (0–5) → normalizada para 0–1
//   completed_factor = atendimentos concluídos → normalizado pelo máximo do pool
//   response_factor  = 1 - (avg_response_min / max_response_min) → quanto menor, maior
//   verified_bonus   = 1.0 se verificado, 0.0 se não
//
// Score final multiplicado por 100 para facilitar leitura (0–100).
// ═══════════════════════════════════════════════════════════════════════════

import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const SUPABASE_URL    = Deno.env.get("SUPABASE_URL")!;
const SERVICE_KEY     = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
// Cron secret para rejeitar chamadas não autorizadas
const CRON_SECRET     = Deno.env.get("CRON_SECRET") ?? "";

serve(async (req) => {
    // ── Validar secret do cron ────────────────────────────────────────────
    const authHeader = req.headers.get("Authorization") ?? "";
    if (CRON_SECRET && authHeader !== `Bearer ${CRON_SECRET}`) {
       return new Response("Unauthorized", { status: 401 });
    }

    const supabase = createClient(SUPABASE_URL, SERVICE_KEY);
    const inicio   = Date.now();

    try {
        // ── 1. Buscar todos os profissionais com dados necessários ─────────
        const { data: profissionais, error: errProf } = await supabase
            .from("profissionais")
            .select("id, verificado");

        if (errProf || !profissionais?.length) {
            return jsonResp({ erro: errProf?.message ?? "Sem profissionais" }, 500);
        }

        // ── 2. Buscar métricas agregadas de uma vez (sem N+1) ─────────────

        // 2a. Média de avaliações por profissional
        const { data: avaliacoes } = await supabase
            .from("avaliacoes")
            .select("profissional_id, nota");

        // 2b. Contagem de atendimentos concluídos por profissional
        const { data: consultas } = await supabase
            .from("consultas")
            .select("profissional_id, status, criado_em, data_agendada")
            .eq("status", "concluida");

        // ── 3. Agregar métricas em memória ────────────────────────────────

        // Média de avaliações
        const ratingMap: Record<string, { soma: number; count: number }> = {};
        for (const av of avaliacoes ?? []) {
            if (!av.profissional_id || av.nota == null) continue;
            if (!ratingMap[av.profissional_id]) {
                ratingMap[av.profissional_id] = { soma: 0, count: 0 };
            }
            ratingMap[av.profissional_id].soma  += Number(av.nota);
            ratingMap[av.profissional_id].count += 1;
        }

        // Contagem de concluídos + tempo médio de resposta
        const completedMap: Record<string, number> = {};
        const responseMap:  Record<string, number[]> = {};

        for (const c of consultas ?? []) {
            if (!c.profissional_id) continue;
            completedMap[c.profissional_id] = (completedMap[c.profissional_id] ?? 0) + 1;

            // Tempo de resposta: diferença entre criado_em e data_agendada em minutos
            // Proxy: quanto antes o profissional agendou após a criação do pedido
            if (c.criado_em && c.data_agendada) {
                const criadoMs    = new Date(c.criado_em).getTime();
                const agendadoMs  = new Date(c.data_agendada).getTime();
                const diffMin     = Math.max(0, (agendadoMs - criadoMs) / 60_000);
                if (!responseMap[c.profissional_id]) responseMap[c.profissional_id] = [];
                responseMap[c.profissional_id].push(diffMin);
            }
        }

        // ── 4. Calcular fatores de normalização (max do pool) ─────────────
        const maxCompleted = Math.max(1, ...Object.values(completedMap));

        const avgResponsePerProf: Record<string, number> = {};
        for (const [profId, tempos] of Object.entries(responseMap)) {
            avgResponsePerProf[profId] = tempos.reduce((a, b) => a + b, 0) / tempos.length;
        }
        const maxResponse = Math.max(1, ...Object.values(avgResponsePerProf), 60);

        // ── 5. Calcular score para cada profissional ──────────────────────
        const updates: { id: string; ranking_score: number; avg_response_time_min: number }[] = [];

        for (const prof of profissionais) {
            const id = prof.id;

            // avg_rating: 0–5 → normalizado para 0–1
            const ratingInfo  = ratingMap[id];
            const avgRating   = ratingInfo
                ? ratingInfo.soma / ratingInfo.count
                : 0;
            const ratingNorm  = avgRating / 5.0;

            // completed_factor: normalizado pelo máximo do pool
            const completed        = completedMap[id] ?? 0;
            const completedFactor  = completed / maxCompleted;

            // response_factor: quanto menor o tempo, maior o fator
            const avgResponse      = avgResponsePerProf[id] ?? maxResponse;
            const responseFactor   = 1 - Math.min(1, avgResponse / maxResponse);

            // verified_bonus: binário
            const verifiedBonus = prof.verificado ? 1.0 : 0.0;

            // Score ponderado × 100
            const score =
                (ratingNorm     * 0.40 +
                 completedFactor * 0.30 +
                 responseFactor  * 0.20 +
                 verifiedBonus   * 0.10) * 100;

            updates.push({
                id,
                ranking_score:        Math.round(score * 100) / 100,
                avg_response_time_min: Math.round(avgResponse * 100) / 100,
            });
        }

        // ── 6. Upsert em lote (máx 500 por chamada) ───────────────────────
        const BATCH = 500;
        let   atualizados = 0;

        for (let i = 0; i < updates.length; i += BATCH) {
            const lote = updates.slice(i, i + BATCH);
            const { error: errUpdate } = await supabase
                .from("perfis")
                .upsert(lote, { onConflict: "id" });

            if (errUpdate) {
                console.error(`Erro no lote ${i / BATCH}:`, errUpdate.message);
            } else {
                atualizados += lote.length;
            }
        }

        const duracao = Date.now() - inicio;
        console.log(`Ranking calculado: ${atualizados} profissionais em ${duracao}ms`);

        return jsonResp({
            sucesso:      true,
            atualizados,
            duracao_ms:   duracao,
            processados:  profissionais.length,
        });

    } catch (e: unknown) {
        const msg = e instanceof Error ? e.message : String(e);
        console.error("Erro ao calcular ranking:", msg);
        return jsonResp({ erro: msg }, 500);
    }
});

function jsonResp(body: unknown, status = 200): Response {
    return new Response(JSON.stringify(body), {
        status,
        headers: { "Content-Type": "application/json" },
    });
}