import { createClient } from '@supabase/supabase-js'

const SUPABASE_URL = 'https://qfzdchrlbqcvewjivaqz.supabase.co'
const SUPABASE_KEY = 'sb_publishable_SM-UHBh_5lzTSBZ2YPUIYw_Sw1i8qeq'

export const supabase = createClient(SUPABASE_URL, SUPABASE_KEY)

// ── TIPOS ──────────────────────────────────────────────
export type TipoUsuario = 'profissional_certificado' | 'profissional_liberal' | 'cliente'

export interface Perfil {
  id: string
  nome: string
  email: string
  telefone?: string
  cpf?: string
  tipo: TipoUsuario
  foto_url?: string
  cidade?: string
  estado?: string
  criado_em: string
}

export interface Profissional {
  id: string
  area: string
  descricao?: string
  conselho?: string
  numero_conselho?: string
  cnpj?: string
  portfolio?: string
  linkedin?: string
  credibilidade: number
  is_pmp: boolean
  disponivel_urgente: boolean
  valor_normal: number
  valor_urgente: number
  verificado: boolean
  perfis?: Perfil
}

export interface Consulta {
  id: string
  cliente_id: string
  profissional_id: string
  tipo: 'normal' | 'urgente'
  status: 'agendada' | 'em_andamento' | 'concluida' | 'cancelada'
  data_agendada?: string
  valor: number
  criado_em: string
}

export interface Avaliacao {
  id: string
  consulta_id: string
  cliente_id: string
  profissional_id: string
  nota: number
  comentario?: string
  criado_em: string
}

// ── AUTH ───────────────────────────────────────────────
export async function signUp(email: string, senha: string, dados: {
  nome: string
  telefone: string
  cpf?: string
  tipo: TipoUsuario
  cidade?: string
  estado?: string
}) {
  const { data, error } = await supabase.auth.signUp({
    email,
    password: senha,
    options: {
      data: { nome: dados.nome }
    }
  })
  if (error) throw error

  if (data.user) {
    const { error: perfilError } = await supabase.from('perfis').insert({
      id: data.user.id,
      nome: dados.nome,
      email,
      telefone: dados.telefone,
      cpf: dados.cpf,
      tipo: dados.tipo,
      cidade: dados.cidade,
      estado: dados.estado,
    })
    if (perfilError) throw perfilError
  }

  return data
}

export async function signIn(email: string, senha: string) {
  const { data, error } = await supabase.auth.signInWithPassword({ email, password: senha })
  if (error) throw error
  return data
}

export async function signOut() {
  const { error } = await supabase.auth.signOut()
  if (error) throw error
}

export async function getSession() {
  const { data } = await supabase.auth.getSession()
  return data.session
}

export async function getPerfil(userId: string): Promise<Perfil | null> {
  const { data, error } = await supabase
    .from('perfis')
    .select('*')
    .eq('id', userId)
    .single()
  if (error) return null
  return data
}

// ── PROFISSIONAIS PMP ──────────────────────────────────
export async function getProfissionaisPMP(): Promise<Profissional[]> {
  const { data, error } = await supabase
    .from('profissionais')
    .select('*, perfis(*)')
    .eq('is_pmp', true)
    .eq('verificado', true)
    .gte('credibilidade', 80)
    .order('credibilidade', { ascending: false })
  if (error) return []
  return data
}

// ── CONSULTAS ──────────────────────────────────────────
export async function criarConsulta(consulta: {
  cliente_id: string
  profissional_id: string
  tipo: 'normal' | 'urgente'
  valor: number
  data_agendada?: string
}) {
  const { data, error } = await supabase.from('consultas').insert(consulta).select().single()
  if (error) throw error
  return data
}

export async function getConsultasCliente(clienteId: string): Promise<Consulta[]> {
  const { data, error } = await supabase
    .from('consultas')
    .select('*')
    .eq('cliente_id', clienteId)
    .order('criado_em', { ascending: false })
  if (error) return []
  return data
}

export async function getConsultasProfissional(profissionalId: string): Promise<Consulta[]> {
  const { data, error } = await supabase
    .from('consultas')
    .select('*')
    .eq('profissional_id', profissionalId)
    .order('criado_em', { ascending: false })
  if (error) return []
  return data
}

// ── AVALIAÇÕES ─────────────────────────────────────────
export async function criarAvaliacao(avaliacao: {
  consulta_id: string
  cliente_id: string
  profissional_id: string
  nota: number
  comentario?: string
}) {
  const { data, error } = await supabase.from('avaliacoes').insert(avaliacao).select().single()
  if (error) throw error
  return data
}