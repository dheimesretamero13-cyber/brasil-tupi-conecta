import { useState } from 'react'
import './Dashboard.css'

interface DashboardProps {
  onSair: () => void
}

type AbaType = 'visao' | 'atendimentos' | 'credibilidade' | 'urgente' | 'perfil'

// ── DADOS MOCK (substituir por API real) ──────────────
const profissional = {
  nome: 'Dr. Carlos Henrique',
  area: 'Saúde e Bem-estar',
  tipo: 'Profissional Certificado',
  conselho: 'CRM 45.231/SP',
  cidade: 'São Paulo, SP',
  credibilidade: 78,
  isPMP: false,
  atendimentosTotal: 47,
  avaliacaoMedia: 4.8,
  disponivelUrgente: true,
  membroDesde: 'Janeiro 2025',
  foto: null as null,
}

const atendimentos = [
  { id: 1, cliente: 'Ana Souza', data: '18/04/2025', hora: '14:30', tipo: 'Urgente', status: 'concluido', avaliacao: 5, valor: 'R$ 120' },
  { id: 2, cliente: 'Marcos Lima', data: '17/04/2025', hora: '10:00', tipo: 'Normal', status: 'concluido', avaliacao: 5, valor: 'R$ 80' },
  { id: 3, cliente: 'Fernanda Costa', data: '16/04/2025', hora: '16:00', tipo: 'Normal', status: 'concluido', avaliacao: 4, valor: 'R$ 80' },
  { id: 4, cliente: 'Roberto Alves', data: '15/04/2025', hora: '09:00', tipo: 'Urgente', status: 'concluido', avaliacao: 5, valor: 'R$ 120' },
  { id: 5, cliente: 'Juliana Peres', data: '20/04/2025', hora: '11:00', tipo: 'Normal', status: 'agendado', avaliacao: 0, valor: 'R$ 80' },
]

const notificacoes = [
  { id: 1, tipo: 'avaliacao', texto: 'Ana Souza avaliou seu atendimento com 5 estrelas', tempo: '2h atrás', lida: false },
  { id: 2, tipo: 'credibilidade', texto: 'Sua credibilidade subiu para 78 pontos!', tempo: '1 dia atrás', lida: false },
  { id: 3, tipo: 'urgente', texto: 'Você recebeu uma solicitação urgente', tempo: '2 dias atrás', lida: true },
  { id: 4, tipo: 'sistema', texto: 'Seu perfil foi verificado com sucesso', tempo: '3 dias atrás', lida: true },
]

// ── COMPONENTES ───────────────────────────────────────
function CredibilidadeRing({ valor }: { valor: number }) {
  const raio = 54
  const circ = 2 * Math.PI * raio
  const progresso = (valor / 100) * circ
  return (
    <div className="cred-ring-wrap">
      <svg width="140" height="140" viewBox="0 0 140 140">
        <circle cx="70" cy="70" r={raio} fill="none" stroke="#f0f0f0" strokeWidth="10" />
        <circle
          cx="70" cy="70" r={raio} fill="none"
          stroke={valor >= 80 ? '#c49a2a' : valor >= 50 ? '#1a5c3a' : '#0c2d6b'}
          strokeWidth="10"
          strokeDasharray={`${progresso} ${circ}`}
          strokeLinecap="round"
          transform="rotate(-90 70 70)"
          style={{ transition: 'stroke-dasharray 1s ease' }}
        />
        <text x="70" y="65" textAnchor="middle" fontSize="28" fontWeight="700" fill="#111827" fontFamily="Fraunces, Georgia, serif">{valor}</text>
        <text x="70" y="84" textAnchor="middle" fontSize="11" fill="#6b7280" fontFamily="Mulish, sans-serif">pontos</text>
      </svg>
      <div className="cred-ring-label">
        {valor >= 80 ? '🏆 Elegível ao PMP' : valor >= 50 ? '📈 Crescendo' : '🌱 Iniciando'}
      </div>
    </div>
  )
}

function EstrelasFill({ n }: { n: number }) {
  return (
    <span className="estrelas">
      {[1,2,3,4,5].map(i => (
        <span key={i} style={{ color: i <= n ? '#e8b832' : '#e5e7eb' }}>★</span>
      ))}
    </span>
  )
}

// ── ABA: VISÃO GERAL ──────────────────────────────────
function AbaVisaoGeral({ setAba }: { setAba: (a: AbaType) => void }) {
  const ganhosMes = atendimentos
    .filter(a => a.status === 'concluido')
    .reduce((acc, a) => acc + parseInt(a.valor.replace(/\D/g,'')), 0)

  return (
    <div className="aba-content">
      {/* Cards de métricas */}
      <div className="metrics-grid">
        <div className="metric-card">
          <div className="metric-icon verde">📋</div>
          <div className="metric-num">{profissional.atendimentosTotal}</div>
          <div className="metric-label">Atendimentos realizados</div>
          <div className="metric-sub">+4 este mês</div>
        </div>
        <div className="metric-card">
          <div className="metric-icon ouro">⭐</div>
          <div className="metric-num">{profissional.avaliacaoMedia}</div>
          <div className="metric-label">Avaliação média</div>
          <div className="metric-sub">Baseada em 47 avaliações</div>
        </div>
        <div className="metric-card">
          <div className="metric-icon azul">💰</div>
          <div className="metric-num">R$ {ganhosMes}</div>
          <div className="metric-label">Ganhos este mês</div>
          <div className="metric-sub">4 atendimentos concluídos</div>
        </div>
        <div className="metric-card">
          <div className="metric-icon verde">🎯</div>
          <div className="metric-num">{profissional.credibilidade}</div>
          <div className="metric-label">Credibilidade</div>
          <div className="metric-sub">22 pontos para o PMP</div>
        </div>
      </div>

      {/* Linha: credibilidade + próximos */}
      <div className="visao-row">
        <div className="visao-card">
          <div className="visao-card-header">
            <h3>Sua credibilidade</h3>
            <button className="link-btn" onClick={() => setAba('credibilidade')}>Ver detalhes →</button>
          </div>
          <div className="cred-visual">
            <CredibilidadeRing valor={profissional.credibilidade} />
            <div className="cred-info">
              <div className="cred-info-item">
                <div className="cred-info-bar-wrap">
                  <div className="cred-info-label-row">
                    <span>Atendimentos</span><span>30 pts</span>
                  </div>
                  <div className="cred-bar"><div className="cred-bar-fill" style={{ width: '60%', background: '#1a5c3a' }} /></div>
                </div>
              </div>
              <div className="cred-info-item">
                <div className="cred-info-bar-wrap">
                  <div className="cred-info-label-row">
                    <span>Avaliações</span><span>28 pts</span>
                  </div>
                  <div className="cred-bar"><div className="cred-bar-fill" style={{ width: '56%', background: '#0c2d6b' }} /></div>
                </div>
              </div>
              <div className="cred-info-item">
                <div className="cred-info-bar-wrap">
                  <div className="cred-info-label-row">
                    <span>Pontualidade</span><span>20 pts</span>
                  </div>
                  <div className="cred-bar"><div className="cred-bar-fill" style={{ width: '40%', background: '#c49a2a' }} /></div>
                </div>
              </div>
              <div className="cred-pmp-progress">
                <div className="cred-pmp-label">
                  <span>Progresso para PMP</span>
                  <span>{profissional.credibilidade}/100</span>
                </div>
                <div className="cred-bar" style={{ height: 8 }}>
                  <div className="cred-bar-fill" style={{ width: `${profissional.credibilidade}%`, background: 'linear-gradient(90deg, #1a5c3a, #c49a2a)' }} />
                </div>
              </div>
            </div>
          </div>
        </div>

        <div className="visao-card">
          <div className="visao-card-header">
            <h3>Próximos atendimentos</h3>
            <button className="link-btn" onClick={() => setAba('atendimentos')}>Ver todos →</button>
          </div>
          <div className="proximos-list">
            {atendimentos.filter(a => a.status === 'agendado').map(a => (
              <div className="proximo-item" key={a.id}>
                <div className="proximo-data">
                  <span className="proximo-dia">{a.data.split('/')[0]}</span>
                  <span className="proximo-mes">abr</span>
                </div>
                <div className="proximo-info">
                  <div className="proximo-cliente">{a.cliente}</div>
                  <div className="proximo-hora">{a.hora} · {a.tipo}</div>
                </div>
                <div className={`proximo-badge ${a.tipo === 'Urgente' ? 'urgente' : 'normal'}`}>
                  {a.tipo}
                </div>
              </div>
            ))}
            {atendimentos.filter(a => a.status === 'agendado').length === 0 && (
              <div className="empty-state">Nenhum atendimento agendado</div>
            )}
          </div>

          {/* Status urgente */}
          <div className={`urgente-status ${profissional.disponivelUrgente ? 'ativo' : 'inativo'}`}>
            <div className="urgente-status-dot" />
            <div>
              <div className="urgente-status-title">
                {profissional.disponivelUrgente ? 'Disponível para urgências' : 'Indisponível para urgências'}
              </div>
              <div className="urgente-status-sub">
                {profissional.disponivelUrgente ? 'Você aparece na área de Consultas Urgentes' : 'Ative para receber consultas urgentes'}
              </div>
            </div>
            <button className="link-btn" onClick={() => setAba('urgente')}>Gerenciar →</button>
          </div>
        </div>
      </div>

      {/* Últimas avaliações */}
      <div className="visao-card">
        <div className="visao-card-header">
          <h3>Últimas avaliações</h3>
          <button className="link-btn" onClick={() => setAba('atendimentos')}>Ver todas →</button>
        </div>
        <div className="avaliacoes-list">
          {atendimentos.filter(a => a.avaliacao > 0).slice(0,3).map(a => (
            <div className="avaliacao-item" key={a.id}>
              <div className="avaliacao-avatar">{a.cliente.split(' ').map(n => n[0]).join('').slice(0,2)}</div>
              <div className="avaliacao-info">
                <div className="avaliacao-nome">{a.cliente}</div>
                <div className="avaliacao-data">{a.data} · {a.tipo}</div>
              </div>
              <EstrelasFill n={a.avaliacao} />
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}

// ── ABA: ATENDIMENTOS ─────────────────────────────────
function AbaAtendimentos() {
  const [filtro, setFiltro] = useState<'todos' | 'concluido' | 'agendado'>('todos')
  const lista = filtro === 'todos' ? atendimentos : atendimentos.filter(a => a.status === filtro)

  return (
    <div className="aba-content">
      <div className="aba-header-row">
        <h3>Histórico de atendimentos</h3>
        <div className="filtro-tabs">
          {(['todos','concluido','agendado'] as const).map(f => (
            <button key={f} className={`filtro-tab ${filtro === f ? 'active' : ''}`} onClick={() => setFiltro(f)}>
              {f === 'todos' ? 'Todos' : f === 'concluido' ? 'Concluídos' : 'Agendados'}
            </button>
          ))}
        </div>
      </div>
      <div className="atendimentos-table">
        <div className="at-header">
          <span>Cliente</span>
          <span>Data</span>
          <span>Tipo</span>
          <span>Valor</span>
          <span>Avaliação</span>
          <span>Status</span>
        </div>
        {lista.map(a => (
          <div className="at-row" key={a.id}>
            <div className="at-cliente">
              <div className="at-avatar">{a.cliente.split(' ').map(n => n[0]).join('').slice(0,2)}</div>
              <span>{a.cliente}</span>
            </div>
            <span className="at-data">{a.data} às {a.hora}</span>
            <span className={`at-tipo ${a.tipo === 'Urgente' ? 'urgente' : 'normal'}`}>{a.tipo}</span>
            <span className="at-valor">{a.valor}</span>
            <span>{a.avaliacao > 0 ? <EstrelasFill n={a.avaliacao} /> : <span className="at-sem-av">—</span>}</span>
            <span className={`at-status ${a.status}`}>
              {a.status === 'concluido' ? 'Concluído' : 'Agendado'}
            </span>
          </div>
        ))}
      </div>
      {lista.length === 0 && <div className="empty-state">Nenhum atendimento encontrado</div>}
    </div>
  )
}

// ── ABA: CREDIBILIDADE ────────────────────────────────
function AbaCredibilidade() {
  return (
    <div className="aba-content">
      <div className="cred-page-grid">
        <div className="visao-card">
          <h3>Sua pontuação atual</h3>
          <div style={{ display: 'flex', justifyContent: 'center', margin: '24px 0' }}>
            <CredibilidadeRing valor={profissional.credibilidade} />
          </div>
          <div className="cred-nivel">
            <div className="cred-nivel-item">
              <div className="cred-nivel-dot" style={{ background: '#e5e7eb' }} />
              <span>0–49</span><strong>Iniciante</strong>
            </div>
            <div className="cred-nivel-item">
              <div className="cred-nivel-dot" style={{ background: '#1a5c3a' }} />
              <span>50–79</span><strong>Consolidado</strong>
            </div>
            <div className="cred-nivel-item">
              <div className="cred-nivel-dot" style={{ background: '#c49a2a' }} />
              <span>80–100</span><strong>Elegível PMP</strong>
            </div>
          </div>
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          <div className="visao-card">
            <h3>Como sua pontuação é calculada</h3>
            <div className="cred-fatores">
              {[
                { label: 'Volume de atendimentos', pts: 30, max: 40, cor: '#1a5c3a', desc: 'Cada atendimento concluído vale pontos' },
                { label: 'Qualidade das avaliações', pts: 28, max: 35, cor: '#0c2d6b', desc: 'Média de estrelas dos seus clientes' },
                { label: 'Pontualidade', pts: 20, max: 25, cor: '#c49a2a', desc: 'Cumprimento dos prazos acordados' },
              ].map(f => (
                <div className="cred-fator" key={f.label}>
                  <div className="cred-fator-header">
                    <span>{f.label}</span>
                    <strong style={{ color: f.cor }}>{f.pts}/{f.max} pts</strong>
                  </div>
                  <div className="cred-bar" style={{ height: 6 }}>
                    <div className="cred-bar-fill" style={{ width: `${(f.pts/f.max)*100}%`, background: f.cor }} />
                  </div>
                  <div className="cred-fator-desc">{f.desc}</div>
                </div>
              ))}
            </div>
          </div>

          <div className="visao-card pmp-card-dash">
            <div className="pmp-card-header">
              <div>
                <div className="pmp-card-label">Programa PMP</div>
                <h3>Faltam {100 - profissional.credibilidade} pontos</h3>
                <p>Alcance 80 pontos de credibilidade para se candidatar ao Selo de Máxima Performance.</p>
              </div>
              <div className="pmp-shield-mini">✓</div>
            </div>
            <div className="pmp-beneficios">
              {['Prioridade nas buscas', 'Melhores comissões', 'Acesso antecipado', 'Selo visível'].map(b => (
                <div className="pmp-beneficio" key={b}>
                  <span>→</span>{b}
                </div>
              ))}
            </div>
            <button className="btn-pmp" disabled={profissional.credibilidade < 80}>
              {profissional.credibilidade >= 80 ? 'Candidatar-me ao PMP' : `Disponível com 80 pontos (${profissional.credibilidade}/80)`}
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}

// ── ABA: URGENTE ──────────────────────────────────────
function AbaUrgente() {
  const [ativo, setAtivo] = useState(profissional.disponivelUrgente)
  return (
    <div className="aba-content">
      <div className="urgente-page">
        <div className="visao-card urgente-toggle-card">
          <div className="urgente-toggle-header">
            <div>
              <h3>Área de Consultas Urgentes</h3>
              <p>Quando ativo, você aparece para clientes que precisam de atendimento imediato. O prazo máximo é de <strong>45 minutos</strong> para iniciar e <strong>15 minutos</strong> de duração.</p>
            </div>
            <div
              className={`toggle ${ativo ? 'on' : 'off'}`}
              onClick={() => setAtivo(v => !v)}
            >
              <div className="toggle-knob" />
            </div>
          </div>
          <div className={`urgente-aviso-dash ${ativo ? 'ativo' : 'inativo'}`}>
            <span>{ativo ? '🟢' : '🔴'}</span>
            <span>{ativo ? 'Você está disponível para consultas urgentes agora' : 'Você está indisponível para consultas urgentes'}</span>
          </div>
        </div>

        <div className="urgente-regras-grid">
          <div className="visao-card">
            <h3>Regras do Acordo de Prontidão</h3>
            <div className="regras-list">
              {[
                { icon: '⏱', titulo: '45 minutos', desc: 'Tempo máximo para iniciar o atendimento após a solicitação do cliente.' },
                { icon: '📋', titulo: '15 minutos', desc: 'Duração máxima da consulta — objetiva e focada na resolução.' },
                { icon: '⚠', titulo: 'Descumprimento', desc: 'Atrasos resultam em perda automática de pontos de credibilidade.' },
                { icon: '🚫', titulo: 'Reincidência', desc: 'Múltiplos descumprimentos resultam em suspensão da área urgente.' },
              ].map(r => (
                <div className="regra-item" key={r.titulo}>
                  <div className="regra-icon">{r.icon}</div>
                  <div>
                    <div className="regra-titulo">{r.titulo}</div>
                    <div className="regra-desc">{r.desc}</div>
                  </div>
                </div>
              ))}
            </div>
          </div>

          <div className="visao-card">
            <h3>Seu histórico na área urgente</h3>
            <div className="urgente-historico">
              <div className="uh-item">
                <div className="uh-num verde">3</div>
                <div className="uh-label">Consultas urgentes realizadas</div>
              </div>
              <div className="uh-item">
                <div className="uh-num azul">100%</div>
                <div className="uh-label">Taxa de pontualidade</div>
              </div>
              <div className="uh-item">
                <div className="uh-num ouro">0</div>
                <div className="uh-label">Descumprimentos registrados</div>
              </div>
            </div>
            <div className="urgente-ok">
              <span>✓</span> Histórico limpo — você mantém acesso integral à área urgente.
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

// ── ABA: PERFIL ───────────────────────────────────────
function AbaPerfil() {
  const [editando, setEditando] = useState(false)
  const [nome, setNome] = useState(profissional.nome)
  const [desc, setDesc] = useState('Médico especialista com 12 anos de experiência em clínica geral e medicina preventiva.')
  const [cidade, setCidade] = useState(profissional.cidade)

  return (
    <div className="aba-content">
      <div className="perfil-grid">
        <div className="visao-card perfil-card-principal">
          <div className="perfil-header">
            <div className="perfil-avatar-grande">
              {profissional.foto ? null : profissional.nome.split(' ').map(n => n[0]).join('').slice(0,2)}
            </div>
            <div className="perfil-info-principal">
              <div className="perfil-nome">{nome}</div>
              <div className="perfil-area">{profissional.area}</div>
              <div className="perfil-badges">
                <span className="perfil-badge certificado">{profissional.tipo}</span>
                {profissional.isPMP && <span className="perfil-badge pmp">🏆 PMP</span>}
              </div>
            </div>
            <button className="btn-editar" onClick={() => setEditando(v => !v)}>
              {editando ? 'Cancelar' : '✏ Editar perfil'}
            </button>
          </div>

          {editando ? (
            <div className="perfil-form">
              <div className="login-field">
                <label>Nome de exibição</label>
                <input value={nome} onChange={e => setNome(e.target.value)} />
              </div>
              <div className="login-field">
                <label>Descrição profissional</label>
                <textarea value={desc} onChange={e => setDesc(e.target.value)} rows={3} style={{ width:'100%', padding:'10px 14px', border:'1.5px solid rgba(0,0,0,0.1)', borderRadius:6, fontFamily:'inherit', fontSize:14, resize:'vertical' }} />
              </div>
              <div className="login-field">
                <label>Cidade / Estado</label>
                <input value={cidade} onChange={e => setCidade(e.target.value)} />
              </div>
              <button className="login-btn-primary" style={{ marginTop: 8 }} onClick={() => setEditando(false)}>
                Salvar alterações
              </button>
            </div>
          ) : (
            <div className="perfil-descricao">
              <p>{desc}</p>
              <div className="perfil-meta">
                <span>📍 {cidade}</span>
                <span>📅 Membro desde {profissional.membroDesde}</span>
                <span>⭐ {profissional.avaliacaoMedia} de média</span>
              </div>
            </div>
          )}
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          <div className="visao-card">
            <h3>Dados profissionais</h3>
            <div className="perfil-dados">
              {[
                { label: 'Conselho', valor: profissional.conselho },
                { label: 'Tipo de conta', valor: profissional.tipo },
                { label: 'Área de atuação', valor: profissional.area },
                { label: 'Atendimentos', valor: `${profissional.atendimentosTotal} realizados` },
              ].map(d => (
                <div className="perfil-dado-item" key={d.label}>
                  <span className="perfil-dado-label">{d.label}</span>
                  <span className="perfil-dado-valor">{d.valor}</span>
                </div>
              ))}
            </div>
          </div>

          <div className="visao-card">
            <h3>Segurança da conta</h3>
            <div className="perfil-dados">
              {[
                { label: 'Senha', valor: '••••••••••', acao: 'Alterar' },
                { label: 'E-mail', valor: 'carlos@email.com', acao: 'Alterar' },
                { label: 'Autenticação 2FA', valor: 'Desativado', acao: 'Ativar' },
              ].map(d => (
                <div className="perfil-dado-item" key={d.label}>
                  <div>
                    <span className="perfil-dado-label">{d.label}</span>
                    <span className="perfil-dado-valor">{d.valor}</span>
                  </div>
                  <button className="link-btn">{d.acao}</button>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

// ── COMPONENTE PRINCIPAL ──────────────────────────────
export default function Dashboard({ onSair }: DashboardProps) {
  const [aba, setAba] = useState<AbaType>('visao')
  const [notifAberta, setNotifAberta] = useState(false)
  const naoLidas = notificacoes.filter(n => !n.lida).length

  const abas: { id: AbaType; label: string; icon: string }[] = [
    { id: 'visao', label: 'Visão geral', icon: '📊' },
    { id: 'atendimentos', label: 'Atendimentos', icon: '📋' },
    { id: 'credibilidade', label: 'Credibilidade', icon: '🎯' },
    { id: 'urgente', label: 'Área Urgente', icon: '⚡' },
    { id: 'perfil', label: 'Meu perfil', icon: '👤' },
  ]

  return (
    <div className="dashboard-wrap">
      {/* ── TOPBAR ── */}
      <div className="dash-topbar">
        <div className="dash-logo">Brasil Tupi <span>Conecta</span></div>
        <div className="dash-topbar-right">
          <div className="notif-wrap">
            <button className="notif-btn" onClick={() => setNotifAberta(v => !v)}>
              🔔
              {naoLidas > 0 && <span className="notif-badge">{naoLidas}</span>}
            </button>
            {notifAberta && (
              <div className="notif-dropdown">
                <div className="notif-header">Notificações</div>
                {notificacoes.map(n => (
                  <div className={`notif-item ${n.lida ? 'lida' : ''}`} key={n.id}>
                    <div className="notif-dot" style={{ opacity: n.lida ? 0 : 1 }} />
                    <div>
                      <div className="notif-texto">{n.texto}</div>
                      <div className="notif-tempo">{n.tempo}</div>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
          <div className="dash-user">
            <div className="dash-avatar">CH</div>
            <div className="dash-user-info">
              <div className="dash-user-nome">{profissional.nome}</div>
              <div className="dash-user-tipo">{profissional.tipo}</div>
            </div>
          </div>
          <button className="dash-sair" onClick={onSair}>Sair</button>
        </div>
      </div>

      <div className="dash-body">
        {/* ── SIDEBAR ── */}
        <aside className="dash-sidebar">
          <nav className="dash-nav">
            {abas.map(a => (
              <button
                key={a.id}
                className={`dash-nav-item ${aba === a.id ? 'active' : ''}`}
                onClick={() => setAba(a.id)}
              >
                <span className="dash-nav-icon">{a.icon}</span>
                <span>{a.label}</span>
                {a.id === 'urgente' && profissional.disponivelUrgente && (
                  <span className="dash-nav-dot" />
                )}
              </button>
            ))}
          </nav>

          <div className="dash-sidebar-bottom">
            <div className="dash-cred-mini">
              <div className="dash-cred-mini-label">Credibilidade</div>
              <div className="dash-cred-mini-bar">
                <div className="dash-cred-mini-fill" style={{ width: `${profissional.credibilidade}%` }} />
              </div>
              <div className="dash-cred-mini-num">{profissional.credibilidade}/100</div>
            </div>
          </div>
        </aside>

        {/* ── CONTEÚDO ── */}
        <main className="dash-main">
          <div className="dash-page-header">
            <div>
              <h1>{abas.find(a => a.id === aba)?.label}</h1>
              {aba === 'visao' && <p>Olá, {profissional.nome.split(' ')[1]}. Aqui está um resumo da sua conta.</p>}
            </div>
            {aba === 'visao' && (
              <div className="dash-data">
                {new Date().toLocaleDateString('pt-BR', { weekday:'long', day:'numeric', month:'long' })}
              </div>
            )}
          </div>

          {aba === 'visao'         && <AbaVisaoGeral setAba={setAba} />}
          {aba === 'atendimentos'  && <AbaAtendimentos />}
          {aba === 'credibilidade' && <AbaCredibilidade />}
          {aba === 'urgente'       && <AbaUrgente />}
          {aba === 'perfil'        && <AbaPerfil />}
        </main>
      </div>
    </div>
  )
}
