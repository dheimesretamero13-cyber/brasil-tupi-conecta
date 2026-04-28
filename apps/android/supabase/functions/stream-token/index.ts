// ═══════════════════════════════════════════════════════════════════════════
// supabase/functions/stream-token/index.ts
//
// DEPLOY:
//   supabase functions deploy stream-token --no-verify-jwt
//
// SECRETS NECESSÁRIOS (Project Settings > Edge Functions > Secrets):
//   STREAM_API_SECRET  → sua chave secreta do Stream (nunca exposta no app)
//   STREAM_API_KEY     → 38awru586kub (chave pública, mas centralizada aqui)
//
// CHAMADA ESPERADA (feita pelo StreamVideoRepository.kt via Ktor):
//   POST /functions/v1/stream-token
//   Headers:
//     Authorization: Bearer <supabase_user_jwt>
//     Content-Type:  application/json
//   Body:
//     { "urgencia_id": "<uuid>" }
//
// RESPOSTA EM SUCESSO (HTTP 200):
//   { "token": "<stream_jwt>", "user_id": "<uuid>", "call_id": "<uuid>" }
//
//   • call_id = urgencia_id  (o app usa o ID da urgência como ID da sala)
//   • user_id = UUID do Supabase Auth (mesmo valor passado em connectUser())
//
// RESPOSTAS DE ERRO:
//   401 → JWT do Supabase inválido ou ausente
//   400 → urgencia_id ausente ou urgência com status inválido
//   403 → usuário não é cliente nem profissional desta urgência
//   500 → secrets não configurados ou falha interna
//
// VALIDAÇÃO DE ACESSO:
//   Antes de emitir o token, a função verifica na tabela `urgencias` se o
//   user_id autenticado é o cliente_id ou o profissional_id da urgência.
//   Isso impede que qualquer usuário logado gere token para qualquer call.
// ═══════════════════════════════════════════════════════════════════════════

import { serve } from "https://deno.land/std@0.177.0/http/server.ts";
import { create, getNumericDate } from "https://deno.land/x/djwt@v2.8/mod.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin":  "*",
  "Access-Control-Allow-Headers": "authorization, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

serve(async (req: Request) => {

  // ── PREFLIGHT CORS ────────────────────────────────────────────────────
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {

    // ── 1. VERIFICAR JWT DO SUPABASE ────────────────────────────────────
    const authHeader = req.headers.get("Authorization");
    if (!authHeader?.startsWith("Bearer ")) {
      return json401("Token de autorização ausente");
    }

    const supabaseJwt = authHeader.replace("Bearer ", "");

    // Cliente autenticado com o JWT do usuário
    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_ANON_KEY")!,
      { global: { headers: { Authorization: `Bearer ${supabaseJwt}` } } },
    );

    const { data: { user }, error: authError } = await supabase.auth.getUser();

    if (authError || !user) {
      return json401("Usuário não autenticado");
    }

    // ── 2. VALIDAR BODY ─────────────────────────────────────────────────
    const body = await req.json().catch(() => ({}));
    const urgenciaId: string | undefined = body.urgencia_id;

    if (!urgenciaId || typeof urgenciaId !== "string") {
      return json400("urgencia_id é obrigatório");
    }

    // ── 3. VALIDAR ACESSO À URGÊNCIA ────────────────────────────────────
    // Garante que apenas o cliente ou o profissional da urgência
    // possam gerar token para esta sala específica.
    // O cliente Supabase autenticado aplica RLS automaticamente.

    const { data: urgencia, error: urgenciaError } = await supabase
      .from("urgencias")
      .select("id, cliente_id, profissional_id, status")
      .eq("id", urgenciaId)
      .single();

    if (urgenciaError || !urgencia) {
      console.error(`[stream-token] urgência não encontrada: ${urgenciaId}`);
      return json400("Chamada não encontrada ou acesso negado");
    }

    // Verificar se o usuário é participante desta chamada
    const isParticipante =
      urgencia.cliente_id      === user.id ||
      urgencia.profissional_id === user.id;

    if (!isParticipante) {
      console.warn(`[stream-token] acesso negado: user=${user.id} urgencia=${urgenciaId}`);
      return json403("Você não está autorizado para esta chamada");
    }

    // Verificar status válido para gerar token de vídeo
    const statusValidos = ["aceita", "em_andamento", "em_chamada"];
    if (!statusValidos.includes(urgencia.status)) {
      console.warn(`[stream-token] status inválido: ${urgencia.status} urgencia=${urgenciaId}`);
      return json400(`Esta chamada não está disponível (status: ${urgencia.status})`);
    }

    // ── 4. CARREGAR SECRETS ─────────────────────────────────────────────
    const streamSecret = Deno.env.get("STREAM_API_SECRET");
    const streamApiKey = Deno.env.get("STREAM_API_KEY");

    if (!streamSecret || !streamApiKey) {
      console.error("[stream-token] STREAM_API_SECRET ou STREAM_API_KEY não configurados");
      return json500("Configuração do servidor incompleta");
    }

    // ── 5. GERAR STREAM USER TOKEN (JWT HS256) ──────────────────────────
    // Payload exigido pelo Stream SDK:
    //   sub       → user_id (UUID do Supabase = user_id no Stream)
    //   iss       → API key pública do Stream
    //   iat       → issued at (agora)
    //   exp       → expira em 1 hora
    //   call_cids → restringe o token à sala específica da urgência
    //               Formato: "<call_type>:<call_id>" → "default:<urgencia_id>"

    const agora  = getNumericDate(0);
    const expira = getNumericDate(60 * 60); // 1 hora

    const encoder = new TextEncoder();
    const keyData = encoder.encode(streamSecret);
    const cryptoKey = await crypto.subtle.importKey(
      "raw",
      keyData,
      { name: "HMAC", hash: "SHA-256" },
      false,
      ["sign"],
    );

    const streamToken = await create(
      { alg: "HS256", typ: "JWT" },
      {
        sub:       user.id,
        iss:       streamApiKey,
        iat:       agora,
        exp:       expira,
        call_cids: [`default:${urgenciaId}`],
      },
      cryptoKey,
    );

    // ── 6. LOG DE AUDITORIA ──────────────────────────────────────────────
    // NUNCA logar o streamToken — é uma credencial de acesso
    console.log(
      `[stream-token] token emitido user=${user.id} urgencia=${urgenciaId} status=${urgencia.status}`
    );

    // ── 7. RETORNAR TOKEN + METADADOS ────────────────────────────────────
    return new Response(
      JSON.stringify({
        token:   streamToken,
        user_id: user.id,
        call_id: urgenciaId,   // app usa urgencia_id como room ID no Stream
      }),
      {
        status:  200,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      },
    );

  } catch (err) {
    console.error("[stream-token] erro interno:", err);
    return json500("Erro interno do servidor");
  }
});

// ── HELPERS DE RESPOSTA ───────────────────────────────────────────────────

function json400(msg: string): Response {
  return new Response(
    JSON.stringify({ error: msg }),
    { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } },
  );
}

function json401(msg: string): Response {
  return new Response(
    JSON.stringify({ error: msg }),
    { status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" } },
  );
}

function json403(msg: string): Response {
  return new Response(
    JSON.stringify({ error: msg }),
    { status: 403, headers: { ...corsHeaders, "Content-Type": "application/json" } },
  );
}

function json500(msg: string): Response {
  return new Response(
    JSON.stringify({ error: msg }),
    { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } },
  );
}