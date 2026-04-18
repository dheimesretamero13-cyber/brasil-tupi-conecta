import { useState } from 'react'
import './BuscaPublica.css'

interface BuscaPublicaProps {
  onLogin: () => void
  onCadastro: () => void
}

// ── DADOS MOCK PMP ────────────────────────────────────
const profissionaisPMP = [
  {
    id: 1, iniciais: 'MC', nome: 'Dra. Mariana Costa', area: 'Psicologia e Terapia',
    cidade: 'São Paulo, SP', avaliacao: 5.0, atendimentos: 63,
    conselho: 'CRP 12.345/SP', tipo: 'Certificado',
    descricao: 'Psicóloga clínica com foco em ansiedade, burnout e relações interpessoais. Atendimento humanizado e baseado em evidências.',
    disponivelUrgente: false, tempoResposta: '~2h', valorNormal: 120, valorUrgente: null,
    especialidades: ['Ansiedade', 'Burnout', 'Terapia Cognitiva'],
    avaliacoes: [
      { cliente: 'Ana S.', nota: 5, texto: 'Profissional incrível, me ajudou muito.' },
      { cliente: 'Pedro L.', nota: 5, texto: 'Atendimento excelente, muito atenciosa.' },
    ]
  },
  {
    id: 2, iniciais: 'RS', nome: 'Dr. Rafael Souza', area: 'Direito e Jurídico',
    cidade: 'Campinas, SP', avaliacao: 4.9, atendimentos: 31,
    conselho: 'OAB 98.765/SP', tipo: 'Certificado',
    descricao: 'Advogado especialista em direito do consumidor, trabalhista e contratos. Consultas objetivas com soluções práticas.',
    disponivelUrgente: true, tempoResposta: '~45min', valorNormal: 90, valorUrgente: 150,
    especialidades: ['Direito do Consumidor', 'Trabalhista', 'Contratos'],
    avaliacoes: [
      { cliente: 'Carla M.', nota: 5, texto: 'Resolveu meu problema rapidamente.' },
      { cliente: 'João R.', nota: 5, texto: 'Muito competente e claro nas explicações.' },
    ]
  },
  {
    id: 3, iniciais: 'SR', nome: 'Dra. Sandra Reis', area: 'Finanças e Contabilidade',
    cidade: 'Rio de Janeiro, RJ', avaliacao: 4.9, atendimentos: 55,
    conselho: 'CFC 54.321/RJ', tipo: 'Certificado',
    descricao: 'Contadora e consultora financeira especializada em MEI, pequenas empresas e planejamento tributário.',
    disponivelUrgente: true, tempoResposta: '~45min', valorNormal: 100, valorUrgente: 160,
    especialidades: ['MEI', 'Planejamento Tributário', 'Contabilidade'],
    avaliacoes: [
      { cliente: 'Marcos T.', nota: 5, texto: 'Economizei muito com as dicas dela.' },
      { cliente: 'Lúcia F.', nota: 5, texto: 'Muito profissional e pontual.' },
    ]
  },
  {
    id: 4, iniciais: 'CH', nome: 'Dr. Carlos Henrique', area: 'Saúde e Bem-estar',
    cidade: 'São Paulo, SP', avaliacao: 4.8, atendimentos: 47,
    conselho: 'CRM 45.231/SP', tipo: 'Certificado',
    descricao: 'Médico clínico geral com 12 anos de experiência. Especialista em medicina preventiva e orientação de saúde.',
    disponivelUrgente: true, tempoResposta: '~45min', valorNormal: 80, valorUrgente: 120,
    especialidades: ['Clínica Geral', 'Medicina Preventiva', 'Check-up'],
    avaliacoes: [
      { cliente: 'Ana S.', nota: 5, texto: 'Atendimento muito cuidadoso.' },
      { cliente: 'Roberto A.', nota: 5, texto: 'Explicou tudo com clareza.' },
    ]
  },
  {
    id: 5, iniciais: 'PL', nome: 'Eng. Patricia Lima', area: 'Engenharia e Tecnologia',
    cidade: 'Belo Horizonte, MG', avaliacao: 4.7, atendimentos: 28,
    conselho: 'CREA 33.210/MG', tipo: 'Certificado',
    descricao: 'Engenheira civil especializada em laudos, perícias e projetos residenciais. Atendimento técnico e preciso.',
    disponivelUrgente: false, tempoResposta: '~4h', valorNormal: 110, valorUrgente: null,
    especialidades: ['Laudos Técnicos', 'Perícia', 'Projetos Residenciais'],
    avaliacoes: [
      { cliente: 'Fernando B.', nota: 5, texto: 'Laudo entregue no prazo, muito detalhado.' },
      { cliente: 'Silvia N.', nota: 4, texto: 'Ótima profissional.' },
    ]
  },
  {
    id: 6, iniciais: 'GT', nome: 'Dr. Gustavo Torres', area: 'Educação e Tutoria',
    cidade: 'Porto Alegre, RS', avaliacao: 4.8, atendimentos: 42,
    conselho: '', tipo: 'Liberal',
    descricao: 'Tutor especializado em matemática, física e preparação para vestibulares e concursos públicos.',
    disponivelUrgente: true, tempoResposta: '~45min', valorNormal: 70, valorUrgente: 100,
    especialidades: ['Matemática', 'Física', 'Vestibular', 'Concursos'],
    avaliacoes: [
      { cliente: 'Beatriz C.', nota: 5, texto: 'Passou meu filho no vestibular!' },
      { cliente: 'Paulo M.', nota: 5, texto: 'Explicação excelente, muito didático.' },
    ]
  },
]

const areas = ['Todas as áreas', 'Saúde e Bem-estar', 'Direito e Jurídico',
  'Engenharia e Tecnologia', 'Educação e Tutoria', 'Finanças e Contabilidade',
  'Psicologia e Terapia', 'Arquitetura e Design', 'Comunicação e Marketing']

// ── MINI CADASTRO CLIENTE ─────────────────────────────
function MiniCadastro({ profissional, tipoConsulta, onConcluido, onCancelar }: {
  profissional: any; tipoConsulta: 'normal' | 'urgente';
  onConcluido: () => void; onCancelar: () => void
}) {
  const [etapa, setEtapa] = useState<'conta' | 'confirmar' | 'sucesso'>('conta')
  const [nome, setNome] = useState('')
  const [email, setEmail] = useState('')
  const [senha, setSenha] = useState('')
  const [telefone, setTelefone] = useState('')
  const [errors, setErrors] = useState<any>({})

  function maskPhone(v: string) {
    return v.replace(/\D/g,'').slice(0,11)
      .replace(/(\d{2})(\d)/,'($1) $2')
      .replace(/(\d{5})(\d{1,4})$/,'$1-$2')
  }

  function validate() {
    const e: any = {}
    if (!nome.trim()) e.nome = 'Nome obrigatório'
    if (!email.includes('@')) e.email = 'E-mail inválido'
    if (senha.length < 6) e.senha = 'Mínimo 6 caracteres'
    if (telefone.replace(/\D/g,'').length < 10) e.telefone = 'Telefone inválido'
    setErrors(e)
    return Object.keys(e).length === 0
  }

  if (etapa === 'sucesso') return (
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

  return (
    <div className="mini-cadastro-overlay" onClick={onCancelar}>
      <div className="mini-cadastro-card" onClick={e => e.stopPropagation()}>
        <button className="modal-fechar" onClick={onCancelar}>✕</button>

        {/* Header */}
        <div className="mc-header">
          <div className="mc-logo">Brasil Tupi <span>Conecta</span></div>
          <div className="mc-steps">
            <div className={`mc-step ${etapa === 'conta' ? 'active' : 'done'}`}>1</div>
            <div className="mc-step-line" />
            <div className={`mc-step ${etapa === 'confirmar' ? 'active' : etapa === 'sucesso' ? 'done' : ''}`}>2</div>
          </div>
        </div>

        {/* Resumo da consulta */}
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
            <button className="btn-mc-primary" onClick={() => validate() && setEtapa('confirmar')}>
              Continuar →
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

// ── PERFIL PÚBLICO ────────────────────────────────────
function PerfilPublico({ prof, onVoltar, onAgendar }: {
  prof: any; onVoltar: () => void;
  onAgendar: (tipo: 'normal' | 'urgente') => void
}) {
  return (
    <div className="perfil-publico-wrap">
      <div className="bp-container">
        <button className="btn-voltar-lista" onClick={onVoltar}>← Voltar à lista</button>

        <div className="perfil-publico-grid">
          {/* COLUNA PRINCIPAL */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
            {/* Header */}
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
                    <span className={`pp-badge tipo ${prof.tipo === 'Certificado' ? 'cert' : 'lib'}`}>
                      {prof.tipo === 'Certificado' ? '🎓 Certificado' : '💼 Liberal'}
                    </span>
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
                  <div className="pp-stat-label">Avaliações negativas</div>
                </div>
                <div className="pp-stat">
                  <div className="pp-stat-num">{prof.tempoResposta}</div>
                  <div className="pp-stat-label">Tempo médio de resposta</div>
                </div>
              </div>
            </div>

            {/* Sobre */}
            <div className="pp-card">
              <h4>Sobre</h4>
              <p className="pp-descricao">{prof.descricao}</p>
              <div className="pp-especialidades">
                {prof.especialidades.map((e: string) => (
                  <span key={e} className="pp-especialidade">{e}</span>
                ))}
              </div>
            </div>

            {/* Avaliações */}
            <div className="pp-card">
              <h4>Avaliações recentes</h4>
              <div className="pp-avaliacoes">
                {prof.avaliacoes.map((a: any, i: number) => (
                  <div className="pp-avaliacao" key={i}>
                    <div className="pp-av-top">
                      <div className="pp-av-avatar">{a.cliente[0]}</div>
                      <strong>{a.cliente}</strong>
                      <span className="pp-av-estrelas">{'★'.repeat(a.nota)}</span>
                    </div>
                    <p className="pp-av-texto">"{a.texto}"</p>
                  </div>
                ))}
              </div>
            </div>

            {/* Critérios PMP */}
            <div className="pp-card pp-criterios">
              <h4>Por que este profissional aparece aqui?</h4>
              <p>Apenas profissionais que cumprem os 3 critérios do Programa PMP são exibidos na busca pública.</p>
              <div className="pp-criterios-lista">
                <div className="pp-criterio ok">
                  <span className="pp-criterio-check">✓</span>
                  <div>
                    <strong>Mínimo de 10 atendimentos</strong>
                    <span>{prof.atendimentos} atendimentos realizados</span>
                  </div>
                </div>
                <div className="pp-criterio ok">
                  <span className="pp-criterio-check">✓</span>
                  <div>
                    <strong>Zero avaliações negativas</strong>
                    <span>Histórico 100% positivo</span>
                  </div>
                </div>
                <div className="pp-criterio ok">
                  <span className="pp-criterio-check">✓</span>
                  <div>
                    <strong>Plano PMP ativo</strong>
                    <span>Mensalidade + porcentagem por atendimento</span>
                  </div>
                </div>
              </div>
            </div>
          </div>

          {/* COLUNA AGENDAR */}
          <div className="pp-agendar-col">
            <div className="pp-agendar-card">
              <h3>Agendar consulta</h3>
              <div className="pp-opcoes">
                <div className="pp-opcao normal">
                  <div className="pp-opcao-header">
                    <div className="pp-opcao-titulo">Consulta normal</div>
                    <div className="pp-opcao-preco">R$ {prof.valorNormal}</div>
                  </div>
                  <div className="pp-opcao-desc">Agendada com antecedência · 15 minutos</div>
                  <button className="btn-agendar-normal" onClick={() => onAgendar('normal')}>
                    Agendar →
                  </button>
                </div>
                {prof.disponivelUrgente && (
                  <div className="pp-opcao urgente">
                    <div className="pp-opcao-header">
                      <div className="pp-opcao-titulo">⚡ Urgente</div>
                      <div className="pp-opcao-preco">R$ {prof.valorUrgente}</div>
                    </div>
                    <div className="pp-opcao-desc">Resposta em até 45 minutos · 15 minutos</div>
                    <button className="btn-agendar-urgente" onClick={() => onAgendar('urgente')}>
                      Solicitar agora →
                    </button>
                  </div>
                )}
              </div>
              <div className="pp-agendar-aviso">
                🔒 Sem cobranças antecipadas. Pague só após a consulta.
              </div>
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

// ── COMPONENTE PRINCIPAL ──────────────────────────────
export default function BuscaPublica({ onLogin, onCadastro }: BuscaPublicaProps) {
  const [busca, setBusca] = useState('')
  const [area, setArea] = useState('Todas as áreas')
  const [somenteUrgente, setSomenteUrgente] = useState(false)
  const [profSelecionado, setProfSelecionado] = useState<any>(null)
  const [agendando, setAgendando] = useState<{ prof: any; tipo: 'normal' | 'urgente' } | null>(null)
  const [concluido, setConcluido] = useState(false)

  const resultado = profissionaisPMP.filter(p => {
    const matchBusca = !busca ||
      p.nome.toLowerCase().includes(busca.toLowerCase()) ||
      p.area.toLowerCase().includes(busca.toLowerCase()) ||
      p.cidade.toLowerCase().includes(busca.toLowerCase()) ||
      p.especialidades.some(e => e.toLowerCase().includes(busca.toLowerCase()))
    const matchArea = area === 'Todas as áreas' || p.area === area
    const matchUrgente = !somenteUrgente || p.disponivelUrgente
    return matchBusca && matchArea && matchUrgente
  })

  function handleAgendar(prof: any, tipo: 'normal' | 'urgente') {
    setProfSelecionado(null)
    setAgendando({ prof, tipo })
  }

  if (profSelecionado) return (
    <div className="bp-page">
      <BuscaNav onLogin={onLogin} onCadastro={onCadastro} />
      <PerfilPublico
        prof={profSelecionado}
        onVoltar={() => setProfSelecionado(null)}
        onAgendar={tipo => handleAgendar(profSelecionado, tipo)}
      />
      {agendando && (
        <MiniCadastro
          profissional={agendando.prof}
          tipoConsulta={agendando.tipo}
          onConcluido={() => { setAgendando(null); setConcluido(true) }}
          onCancelar={() => setAgendando(null)}
        />
      )}
    </div>
  )

  return (
    <div className="bp-page">
      <BuscaNav onLogin={onLogin} onCadastro={onCadastro} />

      {/* HERO */}
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
              placeholder="Buscar por nome, área ou especialidade..."
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

      {/* URGENTE BANNER */}
      <div className="bp-container" style={{ marginTop: 32 }}>
        <div className="bp-urgente-banner">
          <div className="bp-urgente-left">
            <div className="bp-urgente-icon">⚡</div>
            <div>
              <div className="bp-urgente-titulo">Precisa de atendimento urgente?</div>
              <div className="bp-urgente-sub">
                {profissionaisPMP.filter(p => p.disponivelUrgente).length} profissionais disponíveis agora · Resposta em até 45 minutos
              </div>
            </div>
          </div>
          <button className="btn-urgente-banner" onClick={() => setSomenteUrgente(true)}>
            Ver disponíveis →
          </button>
        </div>

        {/* RESULTADO */}
        <div className="bp-resultado-header">
          <div className="bp-resultado-count">
            <strong>{resultado.length}</strong> profissional{resultado.length !== 1 ? 'is' : ''} PMP encontrado{resultado.length !== 1 ? 's' : ''}
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

        {/* CARDS */}
        <div className="bp-grid">
          {resultado.map(p => (
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
                {p.especialidades.slice(0,2).map(e => (
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
                <button className="btn-bp-perfil" onClick={() => setProfSelecionado(p)}>
                  Ver perfil
                </button>
                <button className="btn-bp-agendar" onClick={() => handleAgendar(p, 'normal')}>
                  Agendar →
                </button>
              </div>
            </div>
          ))}
          {resultado.length === 0 && (
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

        {/* RODAPÉ INFO */}
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
          onConcluido={() => { setAgendando(null); onCadastro() }}
          onCancelar={() => setAgendando(null)}
        />
      )}
    </div>
  )
}

// ── NAV DA BUSCA ──────────────────────────────────────
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
