// ═══════════════════════════════════════════════════════════════════════════
// calcular-stats-semanais/index.ts  · Fase 4.4
//
// Roda toda segunda-feira às 04:00 UTC.
// Agrega consultas da semana anterior (seg–dom) para cada profissional.
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
    const inicio   = Date.now();

    // Calcular intervalo da semana anterior (seg–dom)
    const hoje        = new Date();
    const diaSemana   = hoje.getUTCDay(); // 0=dom, 1=seg...
    const diasAteLast = diaSemana === 0 ? 7 : diaSemana;
    const ultimaDom   = new Date(hoje);
    ultimaDom.setUTCDate(hoje.getUTCDate() - diaSemana);
    ultimaDom.setUTCHours(23, 59, 59, 999);

    const ultimaSeg = new Date(ultimaDom);
    ultimaSeg.setUTCDate(ultimaDom.getUTCDate() - 6);
    ultimaSeg.setUTCHours(0, 0, 0, 0);

    const semanaRef = ultimaSeg.toISOString().split("T")[0]; // yyyy-MM-dd

    console.log(`Calculando stats para semana: ${semanaRef}`);

    // Buscar todos os profissionais
    const { data: profissionais } = await supabase
        .from("profissionais")
        .select("id");

    if (!profissionais?.length) return resp({ processados: 0 });

    // Buscar consultas da semana anterior
    const { data: consultas } = await supabase
        .from("consultas")
        .select("id, profissional_id, status, valor, data_agendada, criado_em")
        .gte("data_agendada", ultimaSeg.toISOString())
        .lte("data_agendada", ultimaDom.toISOString());

    // Buscar avaliações da semana
    const { data: avaliacoes } = await supabase
        .from("avaliacoes")
        .select("profissional_id, nota")
        .gte("criado_em", ultimaSeg.toISOString())
        .lte("criado_em", ultimaDom.toISOString());

    // Buscar urgências (solicitações) da semana — para taxa de conversão
    const { data: urgencias } = await supabase
        .from("urgencias")
        .select("professional_id, status")
        .gte("criado_em", ultimaSeg.toISOString())
        .lte("criado_em", ultimaDom.toISOString());

    let processados = 0;

    for (const prof of profissionais) {
        const id = prof.id;

        const consultasProf = (consultas ?? []).filter(c => c.profissional_id === id);
        const concluidas    = consultasProf.filter(c => c.status === "concluida");
        const avaliacoesP   = (avaliacoes ?? []).filter(a => a.profissional_id === id);
        const urgenciasP    = (urgencias ?? []).filter(u => u.professional_id === id);

        // Métricas
        const totalAtend     = concluidas.length;
        const totalSolic     = urgenciasP.length + consultasProf.length;
        const taxaConversao  = totalSolic > 0 ? (totalAtend / totalSolic) * 100 : 0;
        const notaMedia      = avaliacoesP.length > 0
            ? avaliacoesP.reduce((s, a) => s + Number(a.nota), 0) / avaliacoesP.length
            : 0;
        const totalGanho     = concluidas.reduce((s, c) => s + Number(c.valor ?? 0), 0);

        // Tempo médio de resposta (criado_em → data_agendada em minutos)
        const tempos = consultasProf
            .filter(c => c.criado_em && c.data_agendada)
            .map(c => {
                const diff = new Date(c.data_agendada).getTime() - new Date(c.criado_em).getTime();
                return Math.max(0, diff / 60_000);
            });
        const tempoMedio = tempos.length > 0
            ? tempos.reduce((a, b) => a + b, 0) / tempos.length
            : 0;

        // Hora de pico — hora com mais atendimentos concluídos
        const contagemHoras: Record<number, number> = {};
        concluidas.forEach(c => {
            const hora = new Date(c.data_agendada).getUTCHours();
            contagemHoras[hora] = (contagemHoras[hora] ?? 0) + 1;
        });
        const horaPico = Object.entries(contagemHoras)
            .sort(([, a], [, b]) => b - a)[0]?.[0];

        if (totalAtend === 0 && totalSolic === 0) continue; // sem atividade

        // Upsert stats semanais
        const { data: statsInserido, error: errStats } = await supabase
            .from("profissional_stats_semanal")
            .upsert({
                profissional_id:      id,
                semana_referencia:    semanaRef,
                total_atendimentos:   totalAtend,
                total_solicitacoes:   totalSolic,
                taxa_conversao:       Math.round(taxaConversao * 100) / 100,
                tempo_medio_resposta: Math.round(tempoMedio * 100) / 100,
                nota_media:           Math.round(notaMedia * 100) / 100,
                total_ganho:          Math.round(totalGanho * 100) / 100,
                hora_pico:            horaPico ? Number(horaPico) : null,
            }, { onConflict: "profissional_id,semana_referencia" })
            .select("id")
            .single();

        if (errStats || !statsInserido) {
            console.error(`Erro ao salvar stats prof=${id}:`, errStats?.message);
            continue;
        }

        // Inserir horários de pico (deletar anteriores primeiro)
        await supabase
            .from("profissional_horarios_pico")
            .delete()
            .eq("stats_id", statsInserido.id);

        if (Object.keys(contagemHoras).length > 0) {
            const horarios = Object.entries(contagemHoras).map(([hora, total]) => ({
                stats_id: statsInserido.id,
                hora:     Number(hora),
                total,
            }));
            await supabase.from("profissional_horarios_pico").insert(horarios);
        }

        processados++;
    }

    const duracao = Date.now() - inicio;
    console.log(`Stats calculadas: ${processados} profissionais em ${duracao}ms`);

    return resp({ sucesso: true, processados, semana: semanaRef, duracao_ms: duracao });
});

function resp(body: unknown, status = 200): Response {
    return new Response(JSON.stringify(body), {
        status,
        headers: { "Content-Type": "application/json" },
    });
}