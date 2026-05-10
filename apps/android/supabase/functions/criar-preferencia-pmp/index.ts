import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const MP_ACCESS_TOKEN = Deno.env.get("MP_ACCESS_TOKEN")!;
const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

serve(async (req: Request) => {
  try {
    const authHeader = req.headers.get("Authorization");
    if (!authHeader) return new Response(JSON.stringify({ erro: "Não autorizado" }), { status: 401 });

    const supabaseAdmin = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY);
    const { data: { user }, error: authError } = await supabaseAdmin.auth.getUser(authHeader.replace("Bearer ", ""));
    if (authError || !user) return new Response(JSON.stringify({ erro: "Token inválido" }), { status: 401 });

    const body = await req.json();
    const { profissional_id } = body;
    if (!profissional_id) {
      return new Response(JSON.stringify({ erro: "profissional_id obrigatório" }), { status: 400 });
    }

    // Verifica elegibilidade (a função agora retorna os dados do profissional)
    const { data: elegibilidade, error: rpcError } = await supabaseAdmin.rpc("verificar_elegibilidade_pmp", {
      p_profissional_id: profissional_id,
    });
    if (rpcError || !elegibilidade?.elegivel) {
      return new Response(JSON.stringify({ erro: "Profissional não elegível" }), { status: 403 });
    }

    // Busca o plano PMP (nome = 'pmp', tipo = 'profissional')
    const { data: plano, error: planoError } = await supabaseAdmin
      .from("planos")
      .select("id, nome, preco_mensal")
      .eq("nome", "pmp")
      .eq("tipo", "profissional")
      .single();

    if (planoError || !plano) {
      return new Response(JSON.stringify({ erro: "Plano PMP não encontrado" }), { status: 404 });
    }

    // Cria preferência no Mercado Pago
    const mpResp = await fetch("https://api.mercadopago.com/checkout/preferences", {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${MP_ACCESS_TOKEN}`,
        "Content-Type": "application/json",
        "X-Idempotency-Key": `pmp-${profissional_id}-${Date.now()}`,
      },
      body: JSON.stringify({
        items: [{
          id: plano.id,
          title: `Plano PMP - ${plano.nome}`,
          description: "Programa de Maestria Profissional",
          quantity: 1,
          currency_id: "BRL",
          unit_price: Number(plano.preco_mensal),
        }],
        payer: { email: user.email },
        back_urls: {
          success: "brasiltupi://pagamento-pmp-aprovado",
          failure: "brasiltupi://pagamento-pmp-recusado",
          pending: "brasiltupi://pagamento-pendente",
        },
        notification_url: `${SUPABASE_URL}/functions/v1/mp-webhook-pmp`,
        metadata: { profissional_id },
      }),
    });

    const mpData = await mpResp.json();
    if (!mpResp.ok) throw new Error(mpData.message || "Erro no Mercado Pago");

    return new Response(JSON.stringify({
      init_point: mpData.init_point,
      preference_id: mpData.id,
      valor: plano.preco_mensal,
      plano_nome: plano.nome,
    }), { status: 200 });

  } catch (err) {
    return new Response(JSON.stringify({ erro: err.message }), { status: 500 });
  }
});