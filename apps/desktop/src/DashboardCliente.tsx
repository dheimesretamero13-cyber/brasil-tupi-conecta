import { useState } from 'react'
import './DashboardCliente.css'

interface DashboardClienteProps {
  onSair: () => void
  onUrgencia?: () => void
}

type AbaType = 'visao' | 'consultas' | 'busca' | 'perfil'

const cliente = {
  nome: 'Juliana Ferreira',
  email: 'juliana@email.com',
  cpf: '123.456.789-00',
  telefone: '(11) 98765-4321',
  cidade: 'São Paulo, SP',
  membroDesde: 'Março 2025',
  foto: null as null,
}

const consultas = [
  { id: 1, profissional: 'Dr. Carlos Henrique', area: 'Saúde e Bem-estar', data: '18/04/2025', hora: '14:30', tipo: 'Urgente', status: 'concluida', avaliada: true, avaliacao: 5, valor: 'R$ 120' },
  { id: 2, profissional: 'Dra. Mariana Costa', area: 'Psicologia e Terapia', data: '15/04/2025', hora: '10:00', tipo: 'Normal', status: 'concluida', avaliada: false, avaliacao: 0, valor: 'R$ 80' },
  { id: 3, profissional: 'Dr. Rafael Souza', area: 'Direito e Jurídico', data: '20/04/2025', hora: '16:00', tipo: 'Normal', status: 'agendada', avaliada: false, avaliacao: 0, valor: 'R$ 90' },
  { id: 4, profissional: 'Dr. Carlos Henrique', area: 'Saúde e Bem-estar', data: '10/04/2025', hora: '09:00', tipo: 'Normal', status: 'concluida', avaliada: true, avaliacao: 5, valor: 'R$ 80' },
]

const profissionaisDestaque = [
  { id: 1, nome: 'Dr. Carlos Henrique', area: 'Saúde e Bem-estar', avaliacao: 4.8, atendimentos: 47, isPMP: false, disponivelUrgente: true, cidade: 'São Paulo, SP', iniciais: 'CH' },
  { id: 2, nome: 'Dra. Mariana Costa', area: 'Psicologia e Terapia', avaliacao: 5.0, atendimentos: 63, isPMP: true, disponivelUrgente: false, cidade: 'São Paulo, SP', iniciais: 'MC' },
  { id: 3, nome: 'Dr. Rafael Souza', area: 'Direito e Jurídico', avaliacao: 4.9, atendimentos: 31, isPMP: true, disponivelUrgente: true, cidade: 'Campinas, SP', iniciais: 'RS' },
  { id: 4, nome: 'Eng. Patricia Lima', area: 'Engenharia e Tecnologia', avaliacao: 4.7, atendimentos: 28, isPMP: false, disponivelUrgente: false, cidade: 'São Paulo, SP', iniciais: 'PL' },
  { id: 5, nome: 'Dra. Sandra Reis', area: 'Finanças e Contabilidade', avaliacao: 4.9, atendimentos: 55, isPMP: true, disponivelUrgente: true, cidade: 'Rio de Janeiro, RJ', iniciais: 'SR' },
  { id: 6, nome: 'Dr. Bruno Alves', area: 'Saúde e Bem-estar', avaliacao: 4.6, atendimentos: 19, isPMP: false, disponivelUrgente: true, cidade: 'Belo Horizonte, MG', iniciais: 'BA' },
]

const areas = ['Todas as áreas', 'Saúde e Bem-estar', 'Direito e Jurídico', 'Engenharia e Tecnologia',
  'Educação e Tutoria', 'Finanças e Contabilidade', 'Psicologia e Terapia',
  'Arquitetura e Design', 'Comunicação e Marketing', 'Consultoria Empresarial']

function EstrelasFill({ n, interativo, onChange }: { n: number; interativo?: boolean; onChange?: (v: number) => void }) {
  const [hover, setHover] = useState(0)
  return (
    <span className="estrelas">
      {[1,2,3,4,5].map(i => (
        <span key={i}
          style={{ color: i <= (hover || n) ? '#e8b832' : '#e5e7eb', cursor: interativo ? 'pointer' : 'default', fontSize: interativo ? 28 : 14 }}
          onMouseEnter={() => interativo && setHover(i)}
          onMouseLeave={() => interativo && setHover(0)}
          onClick={() => interativo && onChange?.(i)}
        >★</span>
      ))}
    </span>
  )
}

function ModalAvaliacao({ consulta, onFechar }: { consulta: any; onFechar: () => void }) {
  const [nota, setNota] = useState(0)
  const [comentario, setComentario] = useState('')
  const [enviado, setEnviado] = useState(false)

  if (enviado) return (
    <div className="modal-overlay" onClick={onFechar}>
      <div className="modal-card" onClick={e => e.stopPropagation()}>
        <div className="modal-sucesso">
          <div className="modal-sucesso-icon">✓</div>
          <h3>Avaliação enviada!</h3>
          <p>Obrigada por contribuir com a credibilidade da plataforma.</p>
          <button className="btn-modal-primary" onClick={onFechar}>Fechar</button>
        </div>
      </div>
    </div>
  )

  return (
    <div className="modal-overlay" onClick={onFechar}>
      <div className="modal-card" onClick={e => e.stopPropagation()}>
        <button className="modal-fechar" onClick={onFechar}>✕</button>
        <h3>Avaliar atendimento</h3>
        <p className="modal-sub">Como foi sua consulta com <strong>{consulta.profissional}</strong>?</p>
        <div className="modal-estrelas">
          <EstrelasFill n={nota} interativo onChange={setNota} />
          <div className="modal-nota-label">
            {nota === 0 ? 'Selecione uma nota' : ['', 'Muito ruim', 'Ruim', 'Regular', 'Bom', 'Excelente'][nota]}
          </div>
        </div>
        <div className="modal-field">
          <label>Comentário (opcional)</label>
          <textarea value={comentario} onChange={e => setComentario(e.target.value)} placeholder="Descreva sua experiência..." rows={3} />
        </div>
        <button className="btn-modal-primary" disabled={nota === 0} onClick={() => setEnviado(true)}>
          Enviar avaliação
        </button>
      </div>
    </div>
  )
}

function AbaVisaoGeral({ setAba, onUrgencia }: { setAba: (a: AbaType) => void; onUrgencia?: () => void }) {
  const pendentes = consultas.filter(c => c.status === 'concluida' && !c.avaliada)
  const proximas  = consultas.filter(c => c.status === 'agendada')
  const [avaliarConsulta, setAvaliarConsulta] = useState<any>(null)

  return (
    <div className="aba-content">
      <div className="cli-metrics-grid">
        <div className="cli-metric-card">
          <div className="cli-metric-icon azul">📋</div>
          <div className="cli-metric-num">{consultas.filter(c => c.status === 'concluida').length}</div>
          <div className="cli-metric-label">Consultas realizadas</div>
        </div>
        <div className="cli-metric-card">
          <div className="cli-metric-icon verde">⭐</div>
          <div className="cli-metric-num">
            {(consultas.filter(c => c.avaliacao > 0).reduce((a, c) => a + c.avaliacao, 0) / consultas.filter(c => c.avaliacao > 0).length).toFixed(1)}
          </div>
          <div className="cli-metric-label">Média das suas consultas</div>
        </div>
        <div className="cli-metric-card">
          <div className="cli-metric-icon ouro">📅</div>
          <div className="cli-metric-num">{proximas.length}</div>
          <div className="cli-metric-label">Consultas agendadas</div>
        </div>
        <div className="cli-metric-card urgente-card" onClick={onUrgencia || (() => setAba('busca'))}>
          <div className="cli-metric-icon urgente">⚡</div>
          <div className="cli-metric-num">45min</div>
          <div className="cli-metric-label">Atendimento urgente</div>
          <div className="cli-metric-cta">Solicitar agora →</div>
        </div>
      </div>

      <div className="cli-visao-row">
        {pendentes.length > 0 && (
          <div className="cli-card">
            <div className="cli-card-header">
              <h3>Avaliações pendentes</h3>
              <span className="cli-badge-pendente">{pendentes.length}</span>
            </div>
            <div className="pendentes-list">
              {pendentes.map(c => (
                <div className="pendente-item" key={c.id}>
                  <div className="pendente-avatar">{c.profissional.split(' ').map((n: string) => n[0]).join('').slice(0,2)}</div>
                  <div className="pendente-info">
                    <div className="pendente-nome">{c.profissional}</div>
                    <div className="pendente-data">{c.data} · {c.tipo}</div>
                  </div>
                  <button className="btn-avaliar" onClick={() => setAvaliarConsulta(c)}>Avaliar</button>
                </div>
              ))}
            </div>
          </div>
        )}

        <div className="cli-card">
          <div className="cli-card-header">
            <h3>Próximas consultas</h3>
            <button className="link-btn" onClick={() => setAba('consultas')}>Ver todas →</button>
          </div>
          {proximas.length > 0 ? proximas.map(c => (
            <div className="proxima-item" key={c.id}>
              <div className="proxima-data">
                <span className="proxima-dia">{c.data.split('/')[0]}</span>
                <span className="proxima-mes">abr</span>
              </div>
              <div className="proxima-info">
                <div className="proxima-prof">{c.profissional}</div>
                <div className="proxima-hora">{c.hora} · {c.area}</div>
              </div>
              <span className={`tipo-badge ${c.tipo === 'Urgente' ? 'urgente' : 'normal'}`}>{c.tipo}</span>
            </div>
          )) : (
            <div className="cli-empty">Nenhuma consulta agendada</div>
          )}
          <button className="btn-buscar-prof" onClick={() => setAba('busca')}>+ Agendar nova consulta</button>
        </div>
      </div>

      <div className="cli-card">
        <div className="cli-card-header">
          <h3>Profissionais que você consultou</h3>
          <button className="link-btn" onClick={() => setAba('busca')}>Buscar mais →</button>
        </div>
        <div className="recentes-grid">
          {[...new Map(consultas.filter(c => c.status === 'concluida').map(c => [c.profissional, c])).values()].map(c => (
            <div className="recente-card" key={c.profissional}>
              <div className="recente-avatar">{c.profissional.split(' ').map((n: string) => n[0]).join('').slice(0,2)}</div>
              <div className="recente-nome">{c.profissional}</div>
              <div className="recente-area">{c.area}</div>
              <button className="btn-agendar-novamente">Agendar novamente</button>
            </div>
          ))}
        </div>
      </div>

      {avaliarConsulta && <ModalAvaliacao consulta={avaliarConsulta} onFechar={() => setAvaliarConsulta(null)} />}
    </div>
  )
}

function AbaConsultas() {
  const [filtro, setFiltro] = useState<'todas' | 'concluida' | 'agendada'>('todas')
  const [avaliarConsulta, setAvaliarConsulta] = useState<any>(null)
  const lista = filtro === 'todas' ? consultas : consultas.filter(c => c.status === filtro)

  return (
    <div className="aba-content">
      <div className="aba-header-row">
        <h3>Minhas consultas</h3>
        <div className="filtro-tabs">
          {(['todas','concluida','agendada'] as const).map(f => (
            <button key={f} className={`filtro-tab ${filtro === f ? 'active' : ''}`} onClick={() => setFiltro(f)}>
              {f === 'todas' ? 'Todas' : f === 'concluida' ? 'Concluídas' : 'Agendadas'}
            </button>
          ))}
        </div>
      </div>
      <div className="consultas-list">
        {lista.map(c => (
          <div className="consulta-item" key={c.id}>
            <div className="consulta-avatar">{c.profissional.split(' ').map((n: string) => n[0]).join('').slice(0,2)}</div>
            <div className="consulta-info">
              <div className="consulta-prof">{c.profissional}</div>
              <div className="consulta-area">{c.area}</div>
              <div className="consulta-meta">{c.data} às {c.hora} · {c.valor}</div>
            </div>
            <div className="consulta-right">
              <span className={`tipo-badge ${c.tipo === 'Urgente' ? 'urgente' : 'normal'}`}>{c.tipo}</span>
              <span className={`status-badge ${c.status}`}>{c.status === 'concluida' ? 'Concluída' : 'Agendada'}</span>
              {c.status === 'concluida' && (
                c.avaliada
                  ? <div className="consulta-avaliada"><EstrelasFill n={c.avaliacao} /></div>
                  : <button className="btn-avaliar" onClick={() => setAvaliarConsulta(c)}>Avaliar</button>
              )}
            </div>
          </div>
        ))}
        {lista.length === 0 && <div className="cli-empty">Nenhuma consulta encontrada</div>}
      </div>
      {avaliarConsulta && <ModalAvaliacao consulta={avaliarConsulta} onFechar={() => setAvaliarConsulta(null)} />}
    </div>
  )
}

function AbaBusca({ onUrgencia }: { onUrgencia?: () => void }) {
  const [busca, setBusca] = useState('')
  const [area, setArea] = useState('Todas as áreas')
  const [somentePMP, setSomentePMP] = useState(false)
  const [somenteUrgente, setSomenteUrgente] = useState(false)
  const [profSelecionado, setProfSelecionado] = useState<any>(null)

  const resultado = profissionaisDestaque.filter(p => {
    const matchBusca = p.nome.toLowerCase().includes(busca.toLowerCase()) ||
      p.area.toLowerCase().includes(busca.toLowerCase()) ||
      p.cidade.toLowerCase().includes(busca.toLowerCase())
    const matchArea = area === 'Todas as áreas' || p.area === area
    const matchPMP = !somentePMP || p.isPMP
    const matchUrgente = !somenteUrgente || p.disponivelUrgente
    return matchBusca && matchArea && matchPMP && matchUrgente
  })

  if (profSelecionado) return <PerfilProfissional prof={profSelecionado} onVoltar={() => setProfSelecionado(null)} onUrgencia={onUrgencia} />

  return (
    <div className="aba-content">
      <div className="urgente-destaque">
        <div className="urgente-destaque-left">
          <div className="urgente-destaque-icon">⚡</div>
          <div>
            <div className="urgente-destaque-titulo">Precisa de atendimento agora?</div>
            <div className="urgente-destaque-sub">Profissionais disponíveis respondem em até 45 minutos</div>
          </div>
        </div>
        <button className="btn-urgente-busca" onClick={onUrgencia || (() => setSomenteUrgente(true))}>
          Acessar área urgente →
        </button>
      </div>

      <div className="busca-filtros">
        <input className="busca-input" value={busca} onChange={e => setBusca(e.target.value)} placeholder="Buscar por nome, área ou cidade..." />
        <select className="busca-select" value={area} onChange={e => setArea(e.target.value)}>
          {areas.map(a => <option key={a}>{a}</option>)}
        </select>
        <label className="busca-check">
          <input type="checkbox" checked={somentePMP} onChange={e => setSomentePMP(e.target.checked)} />
          <span>Apenas PMP</span>
        </label>
        <label className="busca-check">
          <input type="checkbox" checked={somenteUrgente} onChange={e => setSomenteUrgente(e.target.checked)} />
          <span>Disponível agora</span>
        </label>
        {(somentePMP || somenteUrgente || area !== 'Todas as áreas' || busca) && (
          <button className="btn-limpar" onClick={() => { setBusca(''); setArea('Todas as áreas'); setSomentePMP(false); setSomenteUrgente(false) }}>
            Limpar filtros
          </button>
        )}
      </div>

      <div className="busca-resultado-info">
        {resultado.length} profissional{resultado.length !== 1 ? 'is' : ''} encontrado{resultado.length !== 1 ? 's' : ''}
      </div>

      <div className="profissionais-grid">
        {resultado.map(p => (
          <div className="prof-card" key={p.id} onClick={() => setProfSelecionado(p)}>
            <div className="prof-card-top">
              <div className="prof-card-avatar">{p.iniciais}</div>
              <div className="prof-card-badges">
                {p.isPMP && <span className="badge-pmp">🏆 PMP</span>}
                {p.disponivelUrgente && <span className="badge-urgente-dot" title="Disponível para urgências" />}
              </div>
            </div>
            <div className="prof-card-nome">{p.nome}</div>
            <div className="prof-card-area">{p.area}</div>
            <div className="prof-card-cidade">📍 {p.cidade}</div>
            <div className="prof-card-stats">
              <span>⭐ {p.avaliacao}</span><span>·</span><span>{p.atendimentos} atendimentos</span>
            </div>
            <button className="btn-ver-perfil">Ver perfil →</button>
          </div>
        ))}
        {resultado.length === 0 && (
          <div className="cli-empty" style={{ gridColumn: '1/-1' }}>Nenhum profissional encontrado com esses filtros.</div>
        )}
      </div>
    </div>
  )
}

function PerfilProfissional({ prof, onVoltar, onUrgencia }: { prof: any; onVoltar: () => void; onUrgencia?: () => void }) {
  const [tipoConsulta, setTipoConsulta] = useState<'normal' | 'urgente'>('normal')
  const [agendado, setAgendado] = useState(false)

  if (agendado) return (
    <div className="aba-content">
      <div className="agendado-sucesso">
        <div className="agendado-icon">✓</div>
        <h2>Consulta solicitada!</h2>
        <p>Você solicitou uma consulta {tipoConsulta === 'urgente' ? 'urgente' : ''} com <strong>{prof.nome}</strong>.</p>
        {tipoConsulta === 'urgente' && <p className="agendado-urgente-aviso">O profissional tem até <strong>45 minutos</strong> para confirmar o atendimento.</p>}
        <button className="btn-modal-primary" onClick={onVoltar}>Voltar à busca</button>
      </div>
    </div>
  )

  return (
    <div className="aba-content">
      <button className="btn-voltar-busca" onClick={onVoltar}>← Voltar à busca</button>
      <div className="perfil-prof-grid">
        <div className="cli-card perfil-prof-main">
          <div className="perfil-prof-header">
            <div className="perfil-prof-avatar">{prof.iniciais}</div>
            <div>
              <div className="perfil-prof-nome">{prof.nome}</div>
              <div className="perfil-prof-area">{prof.area}</div>
              <div className="perfil-prof-cidade">📍 {prof.cidade}</div>
              <div className="perfil-prof-badges">
                {prof.isPMP && <span className="badge-pmp">🏆 Profissional PMP</span>}
                {prof.disponivelUrgente && <span className="badge-disponivel">🟢 Disponível para urgências</span>}
              </div>
            </div>
          </div>
          <div className="perfil-prof-stats">
            <div className="pp-stat"><div className="pp-stat-num">⭐ {prof.avaliacao}</div><div className="pp-stat-label">Avaliação média</div></div>
            <div className="pp-stat"><div className="pp-stat-num">{prof.atendimentos}</div><div className="pp-stat-label">Atendimentos</div></div>
            <div className="pp-stat"><div className="pp-stat-num">100%</div><div className="pp-stat-label">Pontualidade</div></div>
          </div>
          <div className="perfil-prof-desc">
            <h4>Sobre o profissional</h4>
            <p>Profissional verificado com histórico comprovado na plataforma.</p>
          </div>
        </div>

        <div className="cli-card agendar-card">
          <h3>Agendar consulta</h3>
          <div className="agendar-tipo">
            <button className={`agendar-tipo-btn ${tipoConsulta === 'normal' ? 'active' : ''}`} onClick={() => setTipoConsulta('normal')}>
              <div className="agendar-tipo-nome">Consulta normal</div>
              <div className="agendar-tipo-desc">Agendada com antecedência</div>
              <div className="agendar-tipo-preco">R$ 80</div>
            </button>
            {prof.disponivelUrgente && (
              <button className={`agendar-tipo-btn urgente ${tipoConsulta === 'urgente' ? 'active' : ''}`} onClick={() => setTipoConsulta('urgente')}>
                <div className="agendar-tipo-nome">⚡ Consulta urgente</div>
                <div className="agendar-tipo-desc">Resposta em até 45 minutos</div>
                <div className="agendar-tipo-preco">R$ 120</div>
              </button>
            )}
          </div>
          {tipoConsulta === 'normal' && (
            <div className="agendar-data">
              <label>Data preferencial</label>
              <input type="date" min={new Date().toISOString().split('T')[0]} />
              <label style={{ marginTop: 12 }}>Horário</label>
              <select><option>09:00</option><option>10:00</option><option>11:00</option><option>14:00</option><option>15:00</option><option>16:00</option></select>
            </div>
          )}
          <div className="agendar-aviso"><span>🔒</span><span>Pagamento seguro. Processado após a consulta.</span></div>
          <button className="btn-confirmar-consulta" onClick={() => tipoConsulta === 'urgente' && onUrgencia ? onUrgencia() : setAgendado(true)}>
            {tipoConsulta === 'urgente' ? '⚡ Acessar área urgente' : 'Confirmar agendamento'}
          </button>
        </div>
      </div>
    </div>
  )
}

function AbaPerfil() {
  const [editando, setEditando] = useState(false)
  const [nome, setNome] = useState(cliente.nome)
  const [telefone, setTelefone] = useState(cliente.telefone)
  const [cidade, setCidade] = useState(cliente.cidade)

  return (
    <div className="aba-content">
      <div className="cli-perfil-grid">
        <div className="cli-card">
          <div className="cli-perfil-header">
            <div className="cli-perfil-avatar">{cliente.nome.split(' ').map(n => n[0]).join('').slice(0,2)}</div>
            <div className="cli-perfil-info">
              <div className="cli-perfil-nome">{nome}</div>
              <div className="cli-perfil-sub">Cliente · Membro desde {cliente.membroDesde}</div>
            </div>
            <button className="btn-editar" onClick={() => setEditando(v => !v)}>{editando ? 'Cancelar' : '✏ Editar'}</button>
          </div>
          {editando ? (
            <div className="cli-perfil-form">
              <div className="modal-field"><label>Nome completo</label><input value={nome} onChange={e => setNome(e.target.value)} /></div>
              <div className="modal-field"><label>Telefone</label><input value={telefone} onChange={e => setTelefone(e.target.value)} /></div>
              <div className="modal-field"><label>Cidade / Estado</label><input value={cidade} onChange={e => setCidade(e.target.value)} /></div>
              <button className="btn-confirmar-consulta" onClick={() => setEditando(false)}>Salvar alterações</button>
            </div>
          ) : (
            <div className="cli-perfil-dados">
              {[
                { label: 'E-mail', valor: cliente.email },
                { label: 'CPF', valor: cliente.cpf },
                { label: 'Telefone', valor: telefone },
                { label: 'Cidade', valor: cidade },
                { label: 'Membro desde', valor: cliente.membroDesde },
              ].map(d => (
                <div className="cli-dado-item" key={d.label}>
                  <span className="cli-dado-label">{d.label}</span>
                  <span className="cli-dado-valor">{d.valor}</span>
                </div>
              ))}
            </div>
          )}
        </div>
        <div className="cli-card">
          <h3>Segurança da conta</h3>
          <div className="cli-perfil-dados">
            {[
              { label: 'Senha', valor: '••••••••••', acao: 'Alterar' },
              { label: 'Autenticação 2FA', valor: 'Desativado', acao: 'Ativar' },
              { label: 'E-mail de recuperação', valor: cliente.email, acao: 'Alterar' },
            ].map(d => (
              <div className="cli-dado-item" key={d.label}>
                <div><div className="cli-dado-label">{d.label}</div><div className="cli-dado-valor">{d.valor}</div></div>
                <button className="link-btn">{d.acao}</button>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  )
}

export default function DashboardCliente({ onSair, onUrgencia }: DashboardClienteProps) {
  const [aba, setAba] = useState<AbaType>('visao')
  const pendentes = consultas.filter(c => c.status === 'concluida' && !c.avaliada).length

  const abas: { id: AbaType; label: string; icon: string }[] = [
    { id: 'visao',     label: 'Visão geral',        icon: '🏠' },
    { id: 'consultas', label: 'Minhas consultas',    icon: '📋' },
    { id: 'busca',     label: 'Buscar profissional', icon: '🔍' },
    { id: 'perfil',    label: 'Meu perfil',          icon: '👤' },
  ]

  return (
    <div className="dashboard-wrap">
      <div className="dash-topbar">
        <div className="dash-logo">Brasil Tupi <span>Conecta</span></div>
        <div className="dash-topbar-right">
          {pendentes > 0 && (
            <button className="notif-btn" onClick={() => setAba('consultas')} style={{ position:'relative' }}>
              🔔<span className="notif-badge">{pendentes}</span>
            </button>
          )}
          <div className="dash-user">
            <div className="dash-avatar" style={{ background: '#1a5c3a' }}>JF</div>
            <div className="dash-user-info">
              <div className="dash-user-nome">{cliente.nome}</div>
              <div className="dash-user-tipo">Cliente</div>
            </div>
          </div>
          <button className="dash-sair" onClick={onSair}>Sair</button>
        </div>
      </div>

      <div className="dash-body">
        <aside className="dash-sidebar">
          <nav className="dash-nav">
            {abas.map(a => (
              <button key={a.id} className={`dash-nav-item ${aba === a.id ? 'active' : ''}`} onClick={() => setAba(a.id)}>
                <span className="dash-nav-icon">{a.icon}</span>
                <span>{a.label}</span>
                {a.id === 'consultas' && pendentes > 0 && <span className="dash-nav-badge">{pendentes}</span>}
              </button>
            ))}
          </nav>
          <div className="dash-sidebar-bottom">
            {/* BOTÃO URGENTE NA SIDEBAR */}
            <button className="btn-urgente-sidebar" onClick={onUrgencia || (() => setAba('busca'))}>
              ⚡ Consulta urgente
            </button>
          </div>
        </aside>

        <main className="dash-main">
          <div className="dash-page-header">
            <div>
              <h1>{abas.find(a => a.id === aba)?.label}</h1>
              {aba === 'visao' && <p>Olá, {cliente.nome.split(' ')[0]}. Bem-vinda de volta.</p>}
            </div>
          </div>
          {aba === 'visao'     && <AbaVisaoGeral setAba={setAba} onUrgencia={onUrgencia} />}
          {aba === 'consultas' && <AbaConsultas />}
          {aba === 'busca'     && <AbaBusca onUrgencia={onUrgencia} />}
          {aba === 'perfil'    && <AbaPerfil />}
        </main>
      </div>
    </div>
  )
}