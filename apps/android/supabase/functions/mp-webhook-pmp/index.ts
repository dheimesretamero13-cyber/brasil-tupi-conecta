import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

serve(async (req: Request) => {
  try {
    const body = await req.json();
    const paymentId = body.data?.id;
    if (!paymentId) return new Response("OK", { status: 200 });

    const mpResp = await fetch(`https://api.mercadopago.com/v1/payments/${paymentId}`, {
      headers: { Authorization: `Bearer ${Deno.env.get("MP_ACCESS_TOKEN")}` },
    });
    const payment = await mpResp.json();

    if (payment.status === "approved") {
      const supabaseAdmin = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY);
      const { profissional_id } = payment.metadata;

      // Contrata o plano PMP (função com apenas 1 parâmetro)
      await supabaseAdmin.rpc("contratar_plano_pmp", {
        p_profissional_id: profissional_id,
      });

      // Notifica o profissional
      await supabaseAdmin.rpc("notificar_usuario", {
        p_user_id: profissional_id,
        p_titulo: "🎉 Plano PMP Ativado!",
        p_corpo: "Seu Programa de Maestria Profissional está ativo. Aproveite todos os benefícios!",
        p_data: { tela: "dashboard-profissional", tipo: "pmp" },
      });
    }

    return new Response("OK", { status: 200 });
  } catch {
    return new Response("OK", { status: 200 });
  }
});