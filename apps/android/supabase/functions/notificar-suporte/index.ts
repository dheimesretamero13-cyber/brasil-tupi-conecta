// ═══════════════════════════════════════════════════════════════════════════
// notificar-suporte/index.ts  · Fase 4.3
//
// Disparada por trigger via pg_net ao inserir em disputes.
// Envia email para a equipe de suporte via Resend.
// ═══════════════════════════════════════════════════════════════════════════

import { serve } from "https://deno.land/std@0.168.0/http/server.ts";

const RESEND_API_KEY   = Deno.env.get("RESEND_API_KEY")!;
const SUPORTE_EMAIL    = Deno.env.get("SUPORTE_EMAIL") ?? "suporte@brasiltupi.com.br";
const SUPABASE_WEBHOOK = Deno.env.get("WEBHOOK_SECRET") ?? "";

serve(async (req) => {
    // Validar webhook secret
    const secret = req.headers.get("x-webhook-secret") ?? "";
    if (SUPABASE_WEBHOOK && secret !== SUPABASE_WEBHOOK) {
        return new Response("Unauthorized", { status: 401 });
    }

    const body = await req.json();
    const disputa = body.record;

    if (!disputa) return new Response("Sem dados", { status: 400 });

    const categoriaLabel: Record<string, string> = {
        cobranca_indevida           : "Cobrança Indevida",
        profissional_nao_compareceu : "Profissional Não Compareceu",
        qualidade                   : "Qualidade do Atendimento",
        tecnico                     : "Problema Técnico",
        outro                       : "Outro",
    };

    const emailBody = {
        from   : "Brasil Tupi Conecta <noreply@brasiltupi.com.br>",
        to     : [SUPORTE_EMAIL],
        subject: `[DISPUTA] Nova solicitação — ${categoriaLabel[disputa.categoria] ?? disputa.categoria}`,
        html   : `
            <h2>Nova disputa aberta</h2>
            <table>
                <tr><td><b>ID:</b></td><td>${disputa.id}</td></tr>
                <tr><td><b>Categoria:</b></td><td>${categoriaLabel[disputa.categoria] ?? disputa.categoria}</td></tr>
                <tr><td><b>Status:</b></td><td>${disputa.status}</td></tr>
                <tr><td><b>Descrição:</b></td><td>${disputa.descricao}</td></tr>
                <tr><td><b>Agendamento:</b></td><td>${disputa.agendamento_id ?? "—"}</td></tr>
                <tr><td><b>Criado em:</b></td><td>${disputa.criado_em}</td></tr>
            </table>
            <p>
                <a href="https://supabase.com/dashboard/project/qfzdchrlbqcvewjivaqz/editor">
                    Abrir no Supabase
                </a>
            </p>
        `,
    };

    const resendResp = await fetch("https://api.resend.com/emails", {
        method  : "POST",
        headers : {
            "Authorization": `Bearer ${RESEND_API_KEY}`,
            "Content-Type" : "application/json",
        },
        body: JSON.stringify(emailBody),
    });

    if (!resendResp.ok) {
        const err = await resendResp.text();
        console.error("Resend error:", err);
        return new Response(err, { status: 500 });
    }

    return new Response(JSON.stringify({ ok: true }), { status: 200 });
});