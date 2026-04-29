import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { createHmac } from "https://deno.land/std@0.168.0/node/crypto.ts";

const SUPABASE_URL         = Deno.env.get("SUPABASE_URL")!;
const SUPABASE_SERVICE_KEY = Deno.env.get("SUP_SERVICE_KEY")!;
const MP_ACCESS_TOKEN      = Deno.env.get("MP_ACCESS_TOKEN")!;
const MP_WEBHOOK_SECRET    = Deno.env.get("MP_WEBHOOK_SECRET")!;

// ─── Normaliza status do MP para os 3 estados internos do app ───────────────
function normalizarStatus(mpStatus: string): "approved" | "pending" | "rejected" {
  if (mpStatus === "approved")                             return "approved";
  if (mpStatus === "pending" || mpStatus === "in_process") return "pending";
  return "rejected"; // cancelled, refunded, charged_back, rejected
}

serve(async (req) => {
  if (req.method !== "POST") {
    return new Response("Method not allowed", { status: 405 });
  }

  const requestId  = req.headers.get("x-request-id") ?? "";
  const xSignature = req.headers.get("x-signature")  ?? "";
  console.log(`[WEBHOOK] request_id=${requestId}`);

  // ─── Ler body como texto para validar assinatura antes de parsear ──────────
  const rawBody = await req.text();

  // ─── Validação de assinatura HMAC-SHA256 do Mercado Pago ──────────────────
  const ts = xSignature.split(",").find(p => p.startsWith("ts="))?.split("=")[1] ?? "";
  const v1 = xSignature.split(",").find(p => p.startsWith("v1="))?.split("=")[1] ?? "";

  if (!ts || !v1) {
    console.warn(`[WEBHOOK] x-signature ausente ou malformado`);
    return new Response("Forbidden", { status: 403 });
  }

  const manifest = `${ts}.${rawBody}`;
  const expected = createHmac("sha256", MP_WEBHOOK_SECRET)
    .update(manifest)
    .digest("hex");

  if (expected !== v1) {
    console.warn(`[WEBHOOK] Assinatura inválida — possível forjamento`);
    return new Response("Forbidden", { status: 403 });
  }

  // ─── Parsear body ──────────────────────────────────────────────────────────
  let body: Record<string, unknown>;
  try {
    body = JSON.parse(rawBody);
  } catch {
    return new Response("Body inválido", { status: 400 });
  }

  const tipo = body["type"] as string;
  const data = body["data"] as { id?: string } | undefined;

  // MP envia outros tipos de evento (ex: "plan", "subscription") — ignorar com 200
  if (tipo !== "payment" || !data?.id) {
    console.log(`[WEBHOOK] Evento ignorado: tipo=${tipo}`);
    return new Response("OK", { status: 200 });
  }

  const mpPaymentId = String(data.id);
  console.log(`[WEBHOOK] Processando mp_payment_id=${mpPaymentId}`);

  // ─── Buscar detalhes do pagamento na API do MP ────────────────────────────
  const mpResp = await fetch(
    `https://api.mercadopago.com/v1/payments/${mpPaymentId}`,
    { headers: { Authorization: `Bearer ${MP_ACCESS_TOKEN}` } }
  );

  if (!mpResp.ok) {
    console.error(`[WEBHOOK] MP retornou ${mpResp.status}`);
    return new Response("MP error", { status: 502 });
  }

  const mp         = await mpResp.json();
  const mpStatus   = mp.status as string;
  const urgenciaId = mp.external_reference as string;
  const valor      = mp.transaction_amount as number;

  if (!urgenciaId) {
    console.warn(`[WEBHOOK] external_reference ausente para mp_payment_id=${mpPaymentId}`);
    return new Response("OK", { status: 200 });
  }

  const statusNormalizado = normalizarStatus(mpStatus);
  console.log(`[WEBHOOK] urgencia=${urgenciaId} mp_status=${mpStatus} → interno=${statusNormalizado} valor=${valor}`);

  const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_KEY);

  // ─── Atualizar payments — idempotente: só toca registros ainda "pending" ──
  const { error: errPay } = await supabase
    .from("payments")
    .update({
      status:        statusNormalizado,
      mp_payment_id: mpPaymentId,
      atualizado_em: new Date().toISOString(),
    })
    .eq("urgencia_id", urgenciaId)
    .eq("status", "pending");

  if (errPay) {
    console.error(`[WEBHOOK] Erro ao atualizar payments: ${errPay.message}`);
  }

  // ─── Se aprovado, registrar apenas em payments ────────────────────────────
  // urgencias.status não recebe "paid" — constraint só permite:
  // aguardando | chamando | em_chamada | concluida | cancelada
  // O status financeiro vive exclusivamente na tabela payments.
  if (statusNormalizado === "approved") {
    console.log(`[WEBHOOK] Pagamento aprovado — urgencia=${urgenciaId} registrado em payments`);
  }

  return new Response("OK", { status: 200 });
});