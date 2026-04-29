// supabase/functions/criar-preferencia-regular/index.ts
//
// Edge Function para criar preferência de pagamento Mercado Pago
// para Atendimentos Regulares (não urgências).
//
// Contrato de entrada:  { "agendamento_regular_id": "uuid" }
// Contrato de saída:    { "init_point", "preference_id", "valor", "descricao" }
//
// Deploy: supabase functions deploy criar-preferencia-regular

import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const MP_ACCESS_TOKEN = Deno.env.get("MP_ACCESS_TOKEN")!;
const SUPABASE_URL    = Deno.env.get("SUPABASE_URL")!;
const SUPABASE_KEY    = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

// URLs de retorno — mesmo padrão da Edge Function de urgências
const SUCCESS_URL = "brasiltupi://pagamento/sucesso";
const FAILURE_URL = "brasiltupi://pagamento/falha";
const PENDING_URL = "brasiltupi://pagamento/pendente";

serve(async (req) => {
  if (req.method !== "POST") {
    return new Response("Method not allowed", { status: 405 });
  }

  // Autenticação — JWT do usuário logado
  const authHeader = req.headers.get("Authorization");
  if (!authHeader?.startsWith("Bearer ")) {
    return new Response(JSON.stringify({ error: "Unauthorized" }), {
      status: 401,
      headers: { "Content-Type": "application/json" },
    });
  }
  const userToken = authHeader.replace("Bearer ", "");

  const supabase = createClient(SUPABASE_URL, SUPABASE_KEY);

  // Verificar usuário autenticado
  const { data: { user }, error: authError } = await supabase.auth.getUser(userToken);
  if (authError || !user) {
    return new Response(JSON.stringify({ error: "Unauthorized" }), { status: 401 });
  }

  // Ler body
  const { agendamento_regular_id } = await req.json();
  if (!agendamento_regular_id) {
    return new Response(JSON.stringify({ error: "agendamento_regular_id obrigatório" }), {
      status: 400,
    });
  }

  // Buscar agendamento + profissional + modalidade
  const { data: agendamento, error: agErr } = await supabase
    .from("agendamentos_regulares")
    .select(`
      id, cliente_id, profissional_id, valor_cobrado, data_agendada,
      hora_inicio, hora_fim, status, payment_id,
      modalidades_atendimento ( titulo ),
      perfis:profissional_id ( nome )
    `)
    .eq("id", agendamento_regular_id)
    .eq("cliente_id", user.id)  // RLS: só o próprio cliente pode pagar
    .single();

  if (agErr || !agendamento) {
    return new Response(JSON.stringify({ error: "Agendamento não encontrado" }), { status: 404 });
  }

  if (agendamento.status !== "pendente") {
    return new Response(JSON.stringify({ error: `Status inválido: ${agendamento.status}` }), {
      status: 409,
    });
  }

  // Idempotência: se já tem payment_id, buscar preferência existente
  if (agendamento.payment_id) {
    const { data: payment } = await supabase
      .from("payments")
      .select("mp_preference_id, valor")
      .eq("id", agendamento.payment_id)
      .single();

    if (payment?.mp_preference_id) {
      // Buscar init_point no MP com o preference_id existente
      const mpRes = await fetch(
        `https://api.mercadopago.com/checkout/preferences/${payment.mp_preference_id}`,
        { headers: { Authorization: `Bearer ${MP_ACCESS_TOKEN}` } }
      );
      if (mpRes.ok) {
        const mpData = await mpRes.json();
        return new Response(JSON.stringify({
          init_point:    mpData.init_point,
          preference_id: payment.mp_preference_id,
          valor:         agendamento.valor_cobrado,
          descricao:     `${agendamento.modalidades_atendimento?.titulo} — ${agendamento.data_agendada}`,
        }), { status: 200 });
      }
    }
  }

  // Criar preferência no Mercado Pago
  const nomeProfissional = agendamento.perfis?.nome ?? "Profissional";
  const tituloModalidade = agendamento.modalidades_atendimento?.titulo ?? "Atendimento";
  const descricao = `${tituloModalidade} com ${nomeProfissional} — ${agendamento.data_agendada} ${agendamento.hora_inicio}`;
  const idempotencyKey = `regular_${agendamento_regular_id}`;

  const preferenceBody = {
    items: [{
      title:      descricao,
      quantity:   1,
      unit_price: agendamento.valor_cobrado,
      currency_id: "BRL",
    }],
    back_urls: {
      success: SUCCESS_URL,
      failure: FAILURE_URL,
      pending: PENDING_URL,
    },
    auto_return:    "approved",
    external_reference: agendamento_regular_id,
    metadata: {
      agendamento_regular_id,
      cliente_id:      user.id,
      profissional_id: agendamento.profissional_id,
    },
  };

  const mpRes = await fetch("https://api.mercadopago.com/checkout/preferences", {
    method:  "POST",
    headers: {
      "Authorization":  `Bearer ${MP_ACCESS_TOKEN}`,
      "Content-Type":   "application/json",
      "X-Idempotency-Key": idempotencyKey,
    },
    body: JSON.stringify(preferenceBody),
  });

  if (!mpRes.ok) {
    const mpErr = await mpRes.text();
    console.error("MP error:", mpErr);
    return new Response(JSON.stringify({ error: "Erro ao criar preferência no Mercado Pago" }), {
      status: 502,
    });
  }

  const mpData = await mpRes.json();

  // Registrar payment no banco via RPC
  const { error: rpcErr } = await supabase.rpc("criar_payment_regular", {
    p_agendamento_id:  agendamento_regular_id,
    p_cliente_id:      user.id,
    p_profissional_id: agendamento.profissional_id,
    p_valor:           agendamento.valor_cobrado,
    p_preference_id:   mpData.id,
    p_idempotency_key: idempotencyKey,
  });

  if (rpcErr) {
    console.error("RPC error:", rpcErr);
    // Não bloquear — retornar init_point mesmo assim (webhook corrige)
  }

  return new Response(JSON.stringify({
    init_point:    mpData.init_point,
    preference_id: mpData.id,
    valor:         agendamento.valor_cobrado,
    descricao,
  }), {
    status: 200,
    headers: { "Content-Type": "application/json" },
  });
});