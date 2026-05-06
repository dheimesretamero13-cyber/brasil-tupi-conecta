// supabase/functions/stream-token-regular/index.ts
import { serve } from 'https://deno.land/std@0.168.0/http/server.ts';
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2';
import { StreamChat } from 'https://esm.sh/stream-chat@8.4.0';

// ============================================
// CONFIGURAÇÃO
// ============================================
const SUPABASE_URL = Deno.env.get('SUPABASE_URL')!;
const SUPABASE_ANON_KEY = Deno.env.get('SUPABASE_ANON_KEY')!;
const STREAM_API_KEY = Deno.env.get('STREAM_API_KEY')!;
const STREAM_API_SECRET = Deno.env.get('STREAM_API_SECRET')!;

// Cliente Supabase com permissões administrativas (para validações)
const supabaseAdmin = createClient(SUPABASE_URL, SUPABASE_ANON_KEY, {
  auth: { persistSession: false },
});

// ============================================
// HANDLER PRINCIPAL
// ============================================
serve(async (req) => {
  // Apenas POST é permitido
  if (req.method !== 'POST') {
    return new Response(JSON.stringify({ error: 'Método não permitido' }), {
      status: 405,
      headers: { 'Content-Type': 'application/json' },
    });
  }

  try {
    // 1. Parse do body
    const { agendamento_id, user_id, tipo_usuario } = await req.json();
    if (!agendamento_id || !user_id || !tipo_usuario) {
      return new Response(
        JSON.stringify({ error: 'Campos obrigatórios: agendamento_id, user_id, tipo_usuario' }),
        { status: 400, headers: { 'Content-Type': 'application/json' } }
      );
    }

    if (!['cliente', 'profissional'].includes(tipo_usuario)) {
      return new Response(
        JSON.stringify({ error: 'tipo_usuario deve ser "cliente" ou "profissional"' }),
        { status: 400, headers: { 'Content-Type': 'application/json' } }
      );
    }

    // 2. Buscar dados do agendamento regular
    const { data: agendamento, error: fetchError } = await supabaseAdmin
      .from('agendamentos_regulares')
      .select(`
        id,
        status,
        sala_stream,
        cliente_id,
        profissional_id,
        horario
      `)
      .eq('id', agendamento_id)
      .single();

    if (fetchError || !agendamento) {
      console.error('Erro ao buscar agendamento:', fetchError);
      return new Response(
        JSON.stringify({ error: 'Agendamento não encontrado' }),
        { status: 404, headers: { 'Content-Type': 'application/json' } }
      );
    }

    // 3. Validações de acordo com o tipo de usuário
    if (tipo_usuario === 'profissional') {
      if (agendamento.profissional_id !== user_id) {
        return new Response(
          JSON.stringify({ error: 'Você não é o profissional responsável por este agendamento' }),
          { status: 403, headers: { 'Content-Type': 'application/json' } }
        );
      }
    } else if (tipo_usuario === 'cliente') {
      if (agendamento.cliente_id !== user_id) {
        return new Response(
          JSON.stringify({ error: 'Você não é o cliente associado a este agendamento' }),
          { status: 403, headers: { 'Content-Type': 'application/json' } }
        );
      }
    }

    // 4. Verificar status "em_andamento"
    if (agendamento.status !== 'em_andamento') {
      return new Response(
        JSON.stringify({ error: `Agendamento não está em andamento (status: ${agendamento.status})` }),
        { status: 409, headers: { 'Content-Type': 'application/json' } }
      );
    }

    // 5. Garantir que sala_stream existe (se não, gerar uma)
    let salaId = agendamento.sala_stream;
    if (!salaId) {
      salaId = `regular_${agendamento_id}`;
      const { error: updateError } = await supabaseAdmin
        .from('agendamentos_regulares')
        .update({ sala_stream: salaId })
        .eq('id', agendamento_id);
      if (updateError) {
        console.error('Erro ao atualizar sala_stream:', updateError);
        return new Response(
          JSON.stringify({ error: 'Não foi possível criar a sala de vídeo' }),
          { status: 500, headers: { 'Content-Type': 'application/json' } }
        );
      }
    }

    // 6. Gerar token JWT do Stream
    const serverClient = StreamChat.getInstance(STREAM_API_KEY, STREAM_API_SECRET);
    const token = serverClient.createToken(user_id);

    // 7. Retornar sucesso
    return new Response(
      JSON.stringify({
        token,
        user_id,
        sala_id: salaId,
        api_key: STREAM_API_KEY,
      }),
      { status: 200, headers: { 'Content-Type': 'application/json' } }
    );
  } catch (err) {
    console.error('Erro interno:', err);
    return new Response(
      JSON.stringify({ error: 'Erro interno do servidor' }),
      { status: 500, headers: { 'Content-Type': 'application/json' } }
    );
  }
});