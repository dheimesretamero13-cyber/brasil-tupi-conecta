import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const MP_ACCESS_TOKEN      = Deno.env.get("MP_ACCESS_TOKEN")!;
const SUPABASE_URL         = Deno.env.get("SUPABASE_URL")!;
const SUPABASE_SERVICE_KEY = Deno.env.get("SUP_SERVICE_KEY")!;

const SUCCESS_URL = "brasiltupi://pagamento?status=approved";
const PENDING_URL = "brasiltupi://pagamento?status=pending";
const FAILURE_URL = "brasiltupi://pagamento?status=failure";

function json(data: unknown, status: number): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

serve(async (req) => {
  if (req.method !== "POST") return new Response("Method not allowed", { status: 405 });

  const authHeader = req.headers.get("Authorization");
  if (!authHeader) return json({ error: "Unauthorized" }, 401);

  const supabaseUser = createClient(SUPABASE_URL, SUPABASE_SERVICE_KEY, {
    global: { headers: { Authorization: authHeader } },
  });

  const { data: { user }, error: authError } = await supabaseUser.auth.getUser();
  if (authError || !user) return json({ error: "Token invalido" }, 401);
  const uid = user.id;

  let body: { urgencia_id?: string; idempotency_key?: string };
  try { body = await req.json(); }
  catch { return json({ error: "Body invalido" }, 400); }

  const { urgencia_id, idempotency_key } = body;
  if (!urgencia_id || !idempotency_key) {
    return json({ error: "urgencia_id e idempotency_key sao obrigatorios" }, 400);
  }

  const supabaseAdmin = createClient(SUPABASE_URL, SUPABASE_SERVICE_KEY);

  const { data: urgencia, error: urgErr } = await supabaseAdmin
    .from("urgencias")
    .select("id, cliente_id, profissional_id, status, offer")
    .eq("id", urgencia_id)
    .single();

  console.log(`[DEBUG] urgencia=${JSON.stringify(urgencia)} urgErr=${JSON.stringify(urgErr)}`);

  if (urgErr || !urgencia) return json({ error: "Urgencia nao encontrada" }, 404);
  if (urgencia.cliente_id !== uid) return json({ error: "Acesso negado" }, 403);
  if (urgencia.status !== "concluida") {
    return json({ error: `Status invalido: ${urgencia.status}` }, 409);
  }

  const offer = (urgencia.offer ?? {}) as Record<string, unknown>;
  const valor = (offer.valor as number) ?? 0;
  const especialidade = (offer.especialidade as string) ?? "Especialidade";

  const { data: pagExistente } = await supabaseAdmin
    .from("payments")
    .select("mp_preference_id, mp_init_point, valor")
    .eq("urgencia_id", urgencia_id)
    .in("status", ["pending", "approved"])
    .single();

  if (pagExistente?.mp_init_point) {
    console.log(`[IDEMPOTENCIA] Retornando preferencia existente para ${urgencia_id}`);
    return json({
      init_point:    pagExistente.mp_init_point,
      preference_id: pagExistente.mp_preference_id,
      valor:         pagExistente.valor,
      descricao:     `Consulta urgente - ${especialidade}`,
    }, 200);
  }

  if (valor <= 0) return json({ error: "Valor invalido" }, 422);

  const mpBody = {
    items: [{
      id:          urgencia_id,
      title:       `Consulta urgente - ${especialidade}`,
      description: "Atendimento via Brasil Tupi Conecta",
      quantity:    1,
      currency_id: "BRL",
      unit_price:  valor,
    }],
    external_reference: urgencia_id,
    back_urls: { success: SUCCESS_URL, pending: PENDING_URL, failure: FAILURE_URL },
    metadata: { urgencia_id, cliente_id: urgencia.cliente_id, profissional_id: urgencia.profissional_id },
  };

  const mpResponse = await fetch("https://api.mercadopago.com/checkout/preferences", {
    method:  "POST",
    headers: {
      "Content-Type":      "application/json",
      "Authorization":     `Bearer ${MP_ACCESS_TOKEN}`,
      "X-Idempotency-Key": idempotency_key,
    },
    body: JSON.stringify(mpBody),
  });

  if (!mpResponse.ok) {
    const mpError = await mpResponse.text();
    console.error(`[MP_ERROR] ${mpResponse.status}: ${mpError}`);
    return json({ error: `Mercado Pago erro: ${mpResponse.status}` }, 502);
  }

  const mpData       = await mpResponse.json();
  const initPoint    = mpData.sandbox_init_point ?? mpData.init_point;
  const preferenceId = mpData.id;

  console.log(`[PRE_INSERT] urgencia_id=${urgencia_id} client_id=${urgencia.cliente_id} valor=${valor}`);

  const { error: insertError } = await supabaseAdmin.from("payments").insert({
    urgencia_id,
    client_id:        urgencia.cliente_id,
    professional_id:  urgencia.profissional_id,
    valor,
    status:           "pending",
    mp_preference_id: preferenceId,
    mp_init_point:    initPoint,
  });

  console.log(`[INSERT_ERROR] ${JSON.stringify(insertError)}`);

  return json({
    init_point:    initPoint,
    preference_id: preferenceId,
    valor,
    descricao: `Consulta urgente - ${especialidade}`,
  }, 200);
});
