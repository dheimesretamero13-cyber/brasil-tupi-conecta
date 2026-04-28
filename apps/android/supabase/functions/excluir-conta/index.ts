// ═══════════════════════════════════════════════════════════════════════════
// supabase/functions/excluir-conta/index.ts
//
// DEPLOY:
//   supabase functions deploy excluir-conta --no-verify-jwt
//   (verifica JWT manualmente para controle explícito de erros)
//
// O QUE FAZ:
//   Soft delete conforme LGPD Art. 18:
//   1. Anonimiza dados pessoais em `perfis` (nome, email, cpf, telefone → valores genéricos)
//   2. Cancela assinaturas ativas
//   3. Marca conta com deleted_at = now() em `perfis`
//   4. Deleta o usuário do Supabase Auth (hard delete do auth.users)
//      → impossibilita re-login com as mesmas credenciais
//   5. Remove tokens FCM (privacidade)
//
//   Os dados de `consultas`, `avaliacoes` e `payments` são mantidos por
//   obrigação legal (registros financeiros — 5 anos por lei fiscal).
//   Os dados são anonimizados: cliente_id permanece como UUID mas o perfil
//   associado não contém mais dados pessoais identificáveis.
//
// SECRETS NECESSÁRIOS:
//   Nenhum novo secret — usa SUPABASE_URL + SERVICE_ROLE_KEY do runtime.
// ═══════════════════════════════════════════════════════════════════════════

import { serve } from "https://deno.land/std@0.177.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin":  "*",
  "Access-Control-Allow-Headers": "authorization, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    // ── 1. Verificar JWT do usuário ───────────────────────────────────────
    const authHeader = req.headers.get("Authorization");
    if (!authHeader?.startsWith("Bearer ")) {
      return json401("Token de autorização ausente");
    }

    const userJwt = authHeader.replace("Bearer ", "");

    // Cliente com JWT do usuário — para validar identidade
    const supabaseUser = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_ANON_KEY")!,
      { global: { headers: { Authorization: `Bearer ${userJwt}` } } },
    );

    const { data: { user }, error: authError } = await supabaseUser.auth.getUser();
    if (authError || !user) {
      return json401("Usuário não autenticado");
    }

    // Cliente com service_role — para operações privilegiadas (anonimizar + deletar auth)
    const supabaseAdmin = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );

    const userId = user.id;
    const agora  = new Date().toISOString();

    // ── 2. Anonimizar dados pessoais em `perfis` ──────────────────────────
    const { error: perfilError } = await supabaseAdmin
      .from("perfis")
      .update({
        nome:       `Usuário Excluído`,
        email:      `excluido_${userId.slice(0, 8)}@removido.brasiltupi`,
        cpf:        null,
        telefone:   null,
        foto_url:   null,
        capa_url:   null,
        cidade:     null,
        estado:     null,
        fcm_token:  null,
        deleted_at: agora,   // campo a adicionar via ALTER TABLE abaixo
      })
      .eq("id", userId);

    if (perfilError) {
      console.error(`[excluir-conta] erro ao anonimizar perfil user=${userId}:`, perfilError);
      return json500("Erro ao processar exclusão");
    }

    // ── 3. Cancelar assinaturas ativas ────────────────────────────────────
    await supabaseAdmin
      .from("assinaturas")
      .update({ status: "cancelada" })
      .eq("usuario_id", userId)
      .eq("status", "ativa");

    // ── 4. Remover tokens FCM (privacidade) ───────────────────────────────
    await supabaseAdmin
      .from("user_fcm_tokens")
      .delete()
      .eq("user_id", userId);

    // ── 5. Deletar usuário do Supabase Auth ───────────────────────────────
    // Hard delete do auth.users — impede re-login com as mesmas credenciais.
    // Feito por último para garantir que os passos anteriores concluam.
    const { error: authDeleteError } = await supabaseAdmin.auth.admin.deleteUser(userId);

    if (authDeleteError) {
      // Não bloqueia — dados já foram anonimizados. Log para investigação.
      console.error(`[excluir-conta] erro ao deletar auth user=${userId}:`, authDeleteError);
    }

    console.log(`[excluir-conta] conta excluída com sucesso user=${userId}`);

    return new Response(
      JSON.stringify({ sucesso: true }),
      { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } },
    );

  } catch (err) {
    console.error("[excluir-conta] erro interno:", err);
    return json500("Erro interno do servidor");
  }
});

function json401(msg: string): Response {
  return new Response(
    JSON.stringify({ error: msg }),
    { status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" } },
  );
}

function json500(msg: string): Response {
  return new Response(
    JSON.stringify({ error: msg }),
    { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } },
  );
}