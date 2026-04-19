import { useState, useEffect } from 'react'
import './BuscaPublica.css'
import { supabase } from './supabase'

interface BuscaPublicaProps {
  onLogin: () => void
  onCadastro: () => void
  onEstudio?: (profissionalId: string) => void
}

const areas = ['Todas as áreas', 'Saúde e Bem-estar', 'Direito e Jurídico',
  'Engenharia e Tecnologia', 'Educação e Tutoria', 'Finanças e Contabilidade',
  'Psicologia e Terapia', 'Arquitetura e Design', 'Comunicação e Marketing']

function useProfissionaisPMP(somenteUrgente: boolean, busca: string, area: string) {
  const [profissionais, setProfissionais] = useState<any[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    async function carregar() {
      setLoading(true)
      let query = supabase
        .from('profissionais')
        .select('*, perfis(nome, email, cidade, estado)')
        .eq('is_pmp', true)
        .eq('verificado', true)
        .gte('credibilidade', 80)
        .order('credibilidade', { ascending: false })

      if (somenteUrgente) query = query.eq('disponivel_urgente', true)
      if (area !== 'Todas as áreas') query = query.eq('area', area)

      const { data } = await query
      if (data) {
        const formatados = data.map((p: any) => ({
          id: p.id,
          iniciais: p.perfis?.nome?.split(' ').map((n: string) => n[0]).join('').slice(0, 2) || 'XX',
          nome: p.perfis?.nome || 'Profissional',
          area: p.area,
          cidade: `${p.perfis?.cidade || ''}, ${p.perfis?.estado || ''}`,
          avaliacao: 5.0,
          atendimentos: Math.floor(p.credibilidade / 2),
          disponivelUrgente: p.disponivel_urgente,
          valorNormal: p.valor_normal,
          valorUrgente: p.disponivel_urgente ? p.valor_urgente : null,
          conselho: p.conselho ? `${p.conselho} ${p.numero_conselho}` : '',
          descricao: p.descricao || '',
          tempoResposta: p.disponivel_urgente ? '~45min' : '~2h',
          especialidades: [p.area],
        })).filter((p: any) =>
          !busca ||
          p.nome.toLowerCase().includes(busca.toLowerCase()) ||
          p.area.toLowerCase().includes(busca.toLowerCase()) ||
          p.cidade.toLowerCase().includes(busca.toLowerCase())
        )
        setProfissionais(formatados)
      }
      setLoading(false)
    }
    carregar()
  }, [somenteUrgente, busca, area])

  return { profissionais, loading }
}

function MiniCadastro({ profissional, tipoConsulta, onConcluido, onCancelar }: {
  profissional: any
  tipoConsulta: 'normal' | 'urgente'
  onConcluido: () => void
  onCancelar: () => void
}) {
  const [etapa, setEtapa] = useState<'conta' | 'confirmar' | 'sucesso'>('conta')
  const [nome, setNome] = useState('')
  const [email, setEmail] = useState('')
  const [senha, setSenha] = useState('')
  const [telefone, setTelefone] = useState('')
  const [errors, setErrors] = useState<any>({})
  const [carregando, setCarregando] = useState(false)
  const [erro, setErro] = useState('')

  function maskPhone(v: string) {
    return v.replace(/\D/g, '').slice(0, 11)
      .replace(/(\d{2})(\d)/, '($1) $2')
      .replace(/(\d{5})(\d{1,4})$/, '$1-$2')
  }

  function validate() {
    const e: any = {}
    if (!nome.trim()) e.nome = 'Nome obrigatório'
    if (!email.includes('@')) e.email = 'E-mail inválido'
    if (senha.length < 6) e.senha = 'Mínimo 6 caracteres'
    if (telefone.replace(/\D/g, '').length < 10) e.telefone = 'Telefone inválido'
    setErrors(e)
    return Object.keys(e).length === 0
  }

  async function handleCriarConta() {
    if (!validate()) return
    setCarregando(true)
    setErro('')
    try {
      const { data, error } = await supabase.auth.signUp({ email, password: senha })
      if (error) throw error
      if (data.user) {
        await supabase.from('perfis').insert({
          id: data.user.id, nome, email, telefone, tipo: 'cliente'
        })
      }
      setEtapa('confirmar')
    } catch {
      setErro('Erro ao criar conta. Tente novamente.')
    } finally {
      setCarregando(false)
    }
  }

  if (etapa === 'sucesso') {
    return (
      <div className="mini-cadastro-overlay" onClick={onCancelar}>
        <div className="mini-cadastro-card" onClick={e => e.stopPropagation()}>
          <div className="mc-sucesso">
            <div className="mc-sucesso-icon">✓</div>
            <h3>Consulta solicitada!</h3>
            <p>Conta criada e consulta {tipoConsulta === 'urgente' ? 'urgente ' : ''}agendada com <strong>{profissional.nome}</strong>.</p>
            {tipoConsulta === 'urgente' && (
              <div className="mc-urgente-aviso">
                ⚡ O profissional tem até <strong>45 minutos</strong> para confirmar.
              </div>
            )}
            <p className="mc-email-aviso">Enviamos os detalhes para <strong>{email}</strong>.</p>
            <button className="btn-mc-primary" onClick={onConcluido}>Acessar minha conta</button>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="mini-cadastro-overlay" onClick={onCancelar}>
      <div className="mini-cadastro-card" onClick={e => e.stopPropagation()}>
        <button className="modal-fechar" onClick={onCancelar}>✕</button>
        <div className="mc-header">
          <div className="mc-logo">Brasil Tupi <span>Conecta</span></div>
          <div className="mc-steps">
            <div className={`mc-step ${etapa === 'conta' ? 'active' : 'done'}`}>1</div>
            <div className="mc-step-line" />
            <div className={`mc-step ${etapa === 'confirmar' ? 'active' : ''}`}>2</div>
          </div>
        </div>

        <div className="mc-resumo">
          <div className="mc-resumo-avatar">{profissional.iniciais}</div>
          <div>
            <div className="mc-resumo-prof">{profissional.nome}</div>
            <div className="mc-resumo-tipo">
              {tipoConsulta === 'urgente'
                ? `⚡ Consulta urgente · R$ ${profissional.valorUrgente}`
                : `Consulta normal · R$ ${profissional.valorNormal}`}
            </div>
          </div>
        </div>

        {etapa === 'conta' && (
          <>
            <h3>Criar conta gratuita</h3>
            <p className="mc-sub">Você será cadastrado como cliente e sua consulta será agendada automaticamente.</p>
            <div className="mc-fields">
              <div className={`mc-field ${errors.nome ? 'error' : ''}`}>
                <label>Nome completo *</label>
                <input value={nome} onChange={e => setNome(e.target.value)} placeholder="Seu nome" />
                {errors.nome && <span className="mc-err">{errors.nome}</span>}
              </div>
              <div className={`mc-field ${errors.email ? 'error' : ''}`}>
                <label>E-mail *</label>
                <input type="email" value={email} onChange={e => setEmail(e.target.value)} placeholder="seu@email.com" />
                {errors.email && <span className="mc-err">{errors.email}</span>}
              </div>
              <div className={`mc-field ${errors.telefone ? 'error' : ''}`}>
                <label>Telefone / WhatsApp *</label>
                <input value={telefone} onChange={e => setTelefone(maskPhone(e.target.value))} placeholder="(00) 00000-0000" />
                {errors.telefone && <span className="mc-err">{errors.telefone}</span>}
              </div>
              <div className={`mc-field ${errors.senha ? 'error' : ''}`}>
                <label>Criar senha *</label>
                <input type="password" value={senha} onChange={e => setSenha(e.target.value)} placeholder="Mínimo 6 caracteres" />
                {errors.senha && <span className="mc-err">{errors.senha}</span>}
              </div>
            </div>
            {erro && <div style={{ color: '#c0392b', fontSize: 13, marginBottom: 8 }}>{erro}</div>}
            <button className="btn-mc-primary" onClick={handleCriarConta} disabled={carregando}>
              {carregando ? 'Criando conta...' : 'Continuar →'}
            </button>
            <div className="mc-ja-tem">
              Já tem conta? <button className="mc-link" onClick={onCancelar}>Entrar</button>
            </div>
          </>
        )}

        {etapa === 'confirmar' && (
          <>
            <h3>Confirmar agendamento</h3>
            <div className="mc-confirmacao">
              {[
                { label: 'Profissional', valor: profissional.nome },
                { label: 'Área', valor: profissional.area },
                { label: 'Tipo', valor: tipoConsulta === 'urgente' ? '⚡ Urgente (45min)' : 'Normal' },
                { label: 'Valor', valor: `R$ ${tipoConsulta === 'urgente' ? profissional.valorUrgente : profissional.valorNormal}` },
                { label: 'Sua conta', valor: email },
              ].map(i => (
                <div className="mc-conf-item" key={i.label}>
                  <span>{i.label}</span><strong>{i.valor}</strong>
                </div>
              ))}
            </div>
            <div className="mc-seguranca">
              🔒 Pagamento processado após a consulta. Sem cobranças antecipadas.
            </div>
            <div className="mc-acoes">
              <button className="btn-mc-ghost" onClick={() => setEtapa('conta')}>← Voltar</button>
              <button className="btn-mc-primary" onClick={() => setEtapa('sucesso')}>
                {tipoConsulta === 'urgente' ? '⚡ Confirmar agora' : 'Confirmar agendamento'}
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  )
}

function PerfilPublico({ prof, onVoltar, onAgendar, onEstudio }: {
  prof: any
  onVoltar: () => void
  onAgendar: (tipo: 'normal' | 'urgente') => void
  onEstudio?: (id: string) => void
}) {
  return (
    <div className="perfil-publico-wrap">
      <div className="bp-container">
        <button className="btn-voltar-lista" onClick={onVoltar}>← Voltar à lista</button>
        <div className="perfil-publico-grid">
          <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
            <div className="pp-card">
              <div className="pp-header">
                <div className="pp-avatar-grande">{prof.iniciais}</div>
                <div className="pp-info">
                  <div className="pp-nome">{prof.nome}</div>
                  <div className="pp-area">{prof.area}</div>
                  <div className="pp-cidade">📍 {prof.cidade}</div>
                  {prof.conselho && <div className="pp-conselho">🏛 {prof.conselho}</div>}
                  <div className="pp-badges">
                    <span className="pp-badge pmp">🏆 PMP Verificado</span>
                    {prof.disponivelUrgente && <span className="pp-badge urgente">⚡ Disponível agora</span>}
                    <span className="pp-badge tipo cert">🎓 Certificado</span>
                  </div>
                </div>
              </div>
              <div className="pp-stats-row">
                <div className="pp-stat">
                  <div className="pp-stat-num">⭐ {prof.avaliacao}</div>
                  <div className="pp-stat-label">Avaliação</div>
                </div>
                <div className="pp-stat">
                  <div className="pp-stat-num">{prof.atendimentos}</div>
                  <div className="pp-stat-label">Atendimentos</div>
                </div>
                <div className="pp-stat">
                  <div className="pp-stat-num">0</div>
                  <div className="pp-stat-label">Negativos</div>
                </div>
                <div className="pp-stat">
                  <div className="pp-stat-num">{prof.tempoResposta}</div>
                  <div className="pp-stat-label">Tempo resposta</div>
                </div>
              </div>
            </div>

            <div className="pp-card">
              <h4>Sobre</h4>
              <p className="pp-descricao">{prof.descricao}</p>
              <div className="pp-especialidades">
                {prof.especialidades.map((e: string) => (
                  <span key={e} className="pp-especialidade">{e}</span>
                ))}
              </div>
            </div>

            <div className="pp-card pp-criterios">
              <h4>Por que este profissional aparece aqui?</h4>
              <p>Apenas profissionais que cumprem os 3 critérios do Programa PMP são exibidos na busca pública.</p>
              <div className="pp-criterios-lista">
                {[
                  ['Mínimo de 10 atendimentos', `${prof.atendimentos} atendimentos realizados`],
                  ['Zero avaliações negativas', 'Histórico 100% positivo'],
                  ['Plano PMP ativo', 'Mensalidade + porcentagem por atendimento'],
                ].map(([titulo, detalhe]) => (
                  <div className="pp-criterio ok" key={titulo}>
                    <span className="pp-criterio-check">✓</span>
                    <div><strong>{titulo}</strong><span>{detalhe}</span></div>
                  </div>
                ))}
              </div>
            </div>
          </div>

          <div className="pp-agendar-col">
            <div className="pp-agendar-card">
              <h3>Agendar consulta</h3>
              {onEstudio && (
  <button onClick={() => onEstudio(prof.id)} style={{ width: '100%', marginBottom: 14, background: '#fdf3d8', border: '1px solid #c49a2a', color: '#b07d00', borderRadius: 8, padding: '10px 0', fontSize: 13, fontWeight: 700, cursor: 'pointer' }}>
    🎨 Ver Estúdio deste profissional
  </button>
)}
              <div className="pp-opcoes">
                <div className="pp-opcao normal">
                  <div className="pp-opcao-header">
                    <div className="pp-opcao-titulo">Consulta normal</div>
                    <div className="pp-opcao-preco">R$ {prof.valorNormal}</div>
                  </div>
                  <div className="pp-opcao-desc">Agendada com antecedência · 15 minutos</div>
                  <button className="btn-agendar-normal" onClick={() => onAgendar('normal')}>Agendar →</button>
                </div>
                {prof.disponivelUrgente && (
                  <div className="pp-opcao urgente">
                    <div className="pp-opcao-header">
                      <div className="pp-opcao-titulo">⚡ Urgente</div>
                      <div className="pp-opcao-preco">R$ {prof.valorUrgente}</div>
                    </div>
                    <div className="pp-opcao-desc">Resposta em até 45 minutos · 15 minutos</div>
                    <button className="btn-agendar-urgente" onClick={() => onAgendar('urgente')}>Solicitar agora →</button>
                  </div>
                )}
              </div>
              <div className="pp-agendar-aviso">🔒 Sem cobranças antecipadas. Pague só após a consulta.</div>
            </div>
            <div className="pp-ja-tem-conta">
              Já tem conta? <button className="mc-link" onClick={() => {}}>Entrar para agendar</button>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

export default function BuscaPublica({ onLogin, onCadastro, onEstudio }: BuscaPublicaProps) {
  const [busca, setBusca] = useState('')
  const [area, setArea] = useState('Todas as áreas')
  const [somenteUrgente, setSomenteUrgente] = useState(false)
  const [profSelecionado, setProfSelecionado] = useState<any>(null)
  const [agendando, setAgendando] = useState<{ prof: any; tipo: 'normal' | 'urgente' } | null>(null)

  const { profissionais, loading } = useProfissionaisPMP(somenteUrgente, busca, area)

  function handleAgendar(prof: any, tipo: 'normal' | 'urgente') {
    setProfSelecionado(null)
    setAgendando({ prof, tipo })
  }

  if (profSelecionado) {
    return (
      <div className="bp-page">
        <BuscaNav onLogin={onLogin} onCadastro={onCadastro} />
        <PerfilPublico
  prof={profSelecionado}
  onVoltar={() => setProfSelecionado(null)}
  onAgendar={tipo => handleAgendar(profSelecionado, tipo)}
  onEstudio={onEstudio}
/>
        {agendando && (
          <MiniCadastro
            profissional={agendando.prof}
            tipoConsulta={agendando.tipo}
            onConcluido={() => setAgendando(null)}
            onCancelar={() => setAgendando(null)}
          />
        )}
      </div>
    )
  }

  return (
    <div className="bp-page">
      <BuscaNav onLogin={onLogin} onCadastro={onCadastro} />

      <div className="bp-hero">
        <div className="bp-hero-inner">
          <div className="bp-hero-tag">Profissionais verificados</div>
          <h1>Encontre o profissional <br />certo para você.</h1>
          <p>Somente profissionais com o Selo PMP aparecem aqui — verificados, avaliados e com histórico comprovado.</p>
          <div className="bp-search-bar">
            <input
              className="bp-search-input"
              value={busca}
              onChange={e => setBusca(e.target.value)}
              placeholder="Buscar por nome, área ou cidade..."
            />
            <select className="bp-search-select" value={area} onChange={e => setArea(e.target.value)}>
              {areas.map(a => <option key={a}>{a}</option>)}
            </select>
            <label className="bp-search-check">
              <input type="checkbox" checked={somenteUrgente} onChange={e => setSomenteUrgente(e.target.checked)} />
              <span>⚡ Disponível agora</span>
            </label>
          </div>
          <div className="bp-criterios-strip">
            <div className="bp-criterio-item">✓ Mínimo 10 atendimentos</div>
            <div className="bp-criterio-sep">·</div>
            <div className="bp-criterio-item">✓ Zero avaliações negativas</div>
            <div className="bp-criterio-sep">·</div>
            <div className="bp-criterio-item">✓ Plano PMP ativo</div>
          </div>
        </div>
      </div>

      <div className="bp-container" style={{ marginTop: 32 }}>
        <div className="bp-urgente-banner">
          <div className="bp-urgente-left">
            <div className="bp-urgente-icon">⚡</div>
            <div>
              <div className="bp-urgente-titulo">Precisa de atendimento urgente?</div>
              <div className="bp-urgente-sub">
                {profissionais.filter(p => p.disponivelUrgente).length} profissionais disponíveis agora · Resposta em até 45 minutos
              </div>
            </div>
          </div>
          <button className="btn-urgente-banner" onClick={() => setSomenteUrgente(true)}>Ver disponíveis →</button>
        </div>

        <div className="bp-resultado-header">
          <div className="bp-resultado-count">
            {loading
              ? 'Carregando...'
              : <><strong>{profissionais.length}</strong> profissional{profissionais.length !== 1 ? 'is' : ''} PMP encontrado{profissionais.length !== 1 ? 's' : ''}</>
            }
            {(busca || area !== 'Todas as áreas' || somenteUrgente) && (
              <button className="btn-limpar-bp" onClick={() => { setBusca(''); setArea('Todas as áreas'); setSomenteUrgente(false) }}>
                Limpar filtros ✕
              </button>
            )}
          </div>
          <div className="bp-ordenar">
            <span>Ordenar por:</span>
            <select>
              <option>Melhor avaliação</option>
              <option>Mais atendimentos</option>
              <option>Disponível agora</option>
            </select>
          </div>
        </div>

        {loading ? (
          <div style={{ textAlign: 'center', padding: '60px 0', color: 'var(--ink-muted)' }}>
            <div style={{ fontSize: 32, marginBottom: 12 }}>⏳</div>
            <p>Carregando profissionais...</p>
          </div>
        ) : (
          <div className="bp-grid">
            {profissionais.map(p => (
              <div className="bp-card" key={p.id}>
                <div className="bp-card-top">
                  <div className="bp-card-avatar">{p.iniciais}</div>
                  <div className="bp-card-badges">
                    <span className="bp-badge-pmp">🏆 PMP</span>
                    {p.disponivelUrgente && <span className="bp-badge-urgente">⚡ Disponível</span>}
                  </div>
                </div>
                <div className="bp-card-nome">{p.nome}</div>
                <div className="bp-card-area">{p.area}</div>
                <div className="bp-card-cidade">📍 {p.cidade}</div>
                <div className="bp-card-stats">
                  <span>⭐ {p.avaliacao}</span>
                  <span className="bp-dot">·</span>
                  <span>{p.atendimentos} atendimentos</span>
                </div>
                <div className="bp-card-especialidades">
                  {p.especialidades.slice(0, 2).map((e: string) => (
                    <span key={e} className="bp-especialidade-mini">{e}</span>
                  ))}
                </div>
                <div className="bp-card-precos">
                  <div className="bp-preco-item">
                    <span>Normal</span><strong>R$ {p.valorNormal}</strong>
                  </div>
                  {p.valorUrgente && (
                    <div className="bp-preco-item urgente">
                      <span>⚡ Urgente</span><strong>R$ {p.valorUrgente}</strong>
                    </div>
                  )}
                </div>
                <div className="bp-card-acoes">
  <button className="btn-bp-perfil" onClick={() => setProfSelecionado(p)}>Ver perfil</button>
  <button className="btn-bp-agendar" onClick={() => handleAgendar(p, 'normal')}>Agendar →</button>
</div>
{onEstudio && (
  <button
    className="btn-bp-estudio"
    onClick={() => onEstudio(p.id)}
    style={{ width: '100%', marginTop: 8, background: '#fdf3d8', border: '1px solid #c49a2a', color: '#b07d00', borderRadius: 8, padding: '8px 0', fontSize: 12, fontWeight: 700, cursor: 'pointer' }}
  >
    🎨 Ver Estúdio
  </button>
)}
</div>
            ))}
            {profissionais.length === 0 && (
              <div className="bp-empty">
                <div className="bp-empty-icon">🔍</div>
                <h3>Nenhum profissional encontrado</h3>
                <p>Tente outros termos ou remova alguns filtros.</p>
                <button className="btn-limpar-bp" onClick={() => { setBusca(''); setArea('Todas as áreas'); setSomenteUrgente(false) }}>
                  Limpar filtros
                </button>
              </div>
            )}
          </div>
        )}

        <div className="bp-footer-info">
          <div className="bp-footer-info-item">
            <strong>Por que só PMPs aparecem aqui?</strong>
            <p>A Brasil Tupi Conecta acredita que visibilidade deve ser conquistada pelo trabalho. Apenas profissionais verificados, com histórico real e plano ativo têm acesso à vitrine pública.</p>
          </div>
          <div className="bp-footer-info-item">
            <strong>É profissional? Apareça aqui.</strong>
            <p>Cadastre-se, complete seus 10 primeiros atendimentos com excelência e ative o Programa PMP para aparecer nesta página.</p>
            <button className="btn-cadastro-prof" onClick={onCadastro}>Criar meu perfil →</button>
          </div>
        </div>
      </div>

      {agendando && (
        <MiniCadastro
          profissional={agendando.prof}
          tipoConsulta={agendando.tipo}
          onConcluido={() => setAgendando(null)}
          onCancelar={() => setAgendando(null)}
        />
      )}
    </div>
  )
}

function BuscaNav({ onLogin, onCadastro }: { onLogin: () => void; onCadastro: () => void }) {
  return (
    <nav className="bp-nav">
      <div className="bp-nav-inner">
        <div className="bp-nav-logo">Brasil Tupi <span>Conecta</span></div>
        <div className="bp-nav-actions">
          <button className="btn-bp-nav-ghost" onClick={onLogin}>Entrar</button>
          <button className="btn-bp-nav-primary" onClick={onCadastro}>Criar conta</button>
        </div>
      </div>
    </nav>
  )
}