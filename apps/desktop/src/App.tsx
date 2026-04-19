import { useState } from 'react'
import './App.css'
import Cadastro from './Cadastro'
import Login from './Login'
import Dashboard from './Dashboard'
import DashboardCliente from './DashboardCliente'
import BuscaPublica from './BuscaPublica'
import UrgenciaDesk from './UrgenciaDesk'
import Estudio from './Estudio'

type Pagina = 'home' | 'cadastro' | 'login' | 'dashboard-pro' | 'dashboard-cli' | 'busca' | 'urgencia' | 'estudio' | 'estudio-busca' | 'estudio-vitrine'

export default function App() {
  const [pagina, setPagina] = useState<Pagina>('home')
  const [userId, setUserId] = useState('')
  const [perfilTipo, setPerfilTipo] = useState<'profissional' | 'cliente'>('cliente')
const [estudioProfId, setEstudioProfId] = useState('')
  function handleEntrar(tipo: 'profissional' | 'cliente', uid = '') {
    setPerfilTipo(tipo)
    setUserId(uid)
    setPagina(tipo === 'profissional' ? 'dashboard-pro' : 'dashboard-cli')
  }

  if (pagina === 'cadastro')      return <Cadastro onVoltar={() => setPagina('home')} />
  if (pagina === 'login')         return <Login onVoltar={() => setPagina('home')} onEntrar={tipo => handleEntrar(tipo)} />
  if (pagina === 'dashboard-pro') return <Dashboard onSair={() => setPagina('home')} onUrgencia={() => setPagina('urgencia')} onEstudio={() => setPagina('estudio')} />
  if (pagina === 'dashboard-cli') return <DashboardCliente onSair={() => setPagina('home')} onUrgencia={() => setPagina('urgencia')} onEstudio={(id) => { setEstudioProfId(id); setPagina('estudio-vitrine') }} />
  if (pagina === 'busca') return <BuscaPublica onLogin={() => setPagina('login')} onCadastro={() => setPagina('cadastro')} onEstudio={(id) => { setEstudioProfId(id); setPagina('estudio-vitrine') }} />
if (pagina === 'estudio-vitrine') return <Estudio userId={userId} profissionalId={estudioProfId} modo="vitrine" onVoltar={() => setPagina('busca')} />
  if (pagina === 'urgencia')      return <UrgenciaDesk userId={userId} perfilTipo={perfilTipo} onVoltar={() => setPagina(perfilTipo === 'profissional' ? 'dashboard-pro' : 'dashboard-cli')} />
  if (pagina === 'estudio')       return <Estudio userId={userId} modo="dashboard" onVoltar={() => setPagina('dashboard-pro')} />
  if (pagina === 'estudio-busca') return <Estudio userId={userId} modo="busca" onVoltar={() => setPagina('home')} />

  return (
    <>
      {/* ── NAV ── */}
      <nav className="nav">
        <div className="nav-inner">
          <a href="#" className="nav-logo">Brasil Tupi <span>Conecta</span></a>
          <a href="#profissional" className="nav-link">Para profissionais</a>
          <a href="#busca" className="nav-link" onClick={e => { e.preventDefault(); setPagina('busca') }}>Buscar profissional</a>
          <a href="#cliente" className="nav-link">Para clientes</a>
          <a href="#urgente" className="nav-link">Consultas urgentes</a>
          <a href="#pmp" className="nav-link">Programa PMP</a>
          <a href="#estudio" className="nav-link" onClick={e => { e.preventDefault(); setPagina('estudio-busca') }}>Estúdio</a>
          <a href="#login" className="nav-link" onClick={e => { e.preventDefault(); setPagina('login') }}>Entrar</a>
          <a href="#cadastro" className="nav-cta" onClick={e => { e.preventDefault(); setPagina('cadastro') }}>Criar perfil</a>
        </div>
      </nav>

      {/* ── HERO ── */}
      <section className="hero">
        <div className="hero-texture" />
        <div className="hero-lines" />
        <div className="hero-content">
          <div className="hero-eyebrow">Brasil Tupi Conecta</div>
          <h1>Você é visto<br />pelo que <em>você é.</em><br />Não pelo que vende.</h1>
          <p className="hero-sub">
            A Brasil Tupi Conecta resolve o maior problema dos profissionais brasileiros:
            ser encontrado, reconhecido e valorizado — sem precisar virar vendedor de si mesmo.
          </p>
          <div className="hero-btns">
            <a href="#cadastro" className="btn-hero-primary" onClick={e => { e.preventDefault(); setPagina('cadastro') }}>Quero criar meu perfil</a>
            <a href="#buscar" className="btn-hero-ghost">Preciso de um profissional</a>
          </div>
        </div>
      </section>

      {/* ── STRIP NÚMEROS ── */}
      <div className="strip">
        <div className="strip-inner">
          <div className="strip-item">
            <div className="strip-num">45min</div>
            <div className="strip-label">Tempo máximo para iniciar<br />uma consulta urgente</div>
          </div>
          <div className="strip-item">
            <div className="strip-num">15min</div>
            <div className="strip-label">Consulta objetiva,<br />focada na sua dor</div>
          </div>
          <div className="strip-item">
            <div className="strip-num">PMP</div>
            <div className="strip-label">Selo de verificação<br />e credibilidade máxima</div>
          </div>
          <div className="strip-item">
            <div className="strip-num">100%</div>
            <div className="strip-label">Profissionais reais,<br />avaliações reais</div>
          </div>
        </div>
      </div>

      {/* ── QUEM SOMOS ── */}
      <section className="section" id="sobre">
        <div className="about-grid">
          <div>
            <div className="label">Nossa razão de existir</div>
            <h2 className="title">Uma plataforma construída para resolver dores reais — dos dois lados</h2>
            <p className="body-text">
              A Brasil Tupi Conecta nasceu de uma frustração dupla que todo brasileiro conhece:
              o profissional competente que ninguém encontra, e o cliente desesperado que não sabe em quem confiar.
            </p>
            <p className="body-text" style={{ marginTop: 16 }}>
              Criamos um ecossistema onde a credibilidade é construída pelo trabalho real —
              cada atendimento, cada avaliação, cada compromisso honrado eleva o profissional.
              Aqui, quem aparece primeiro é quem mais merece aparecer.
            </p>
            <div className="about-dores">
              <div className="dor-item">
                <div className="dor-icon before">✗</div>
                <div className="dor-text">
                  <strong>Antes: invisibilidade e desconfiança</strong>
                  Profissionais excelentes perdidos na multidão, clientes sem saber a quem recorrer.
                </div>
              </div>
              <div className="dor-item">
                <div className="dor-icon after">✓</div>
                <div className="dor-text">
                  <strong>Agora: visibilidade conquistada e confiança verificada</strong>
                  Cada atendimento constrói seu histórico. Trabalho fácil, seguro e reconhecido.
                </div>
              </div>
            </div>
          </div>
          <div>
            <div className="about-manifesto">
              <blockquote>
                "Chega de ser reduzido a um catálogo de cursos. Na Brasil Tupi Conecta,
                você é valorizado pela profundidade do que sabe — e pela seriedade com que atende."
              </blockquote>
              <cite>— Manifesto Brasil Tupi Conecta</cite>
            </div>
          </div>
        </div>
      </section>

      {/* ── DUAS DORES ── */}
      <div className="duas-dores">
        <div className="duas-dores-inner">
          <div className="dores-card" id="profissional">
            <span className="dores-card-tag pro">Para o profissional</span>
            <div className="dores-divider pro" />
            <h3>Trabalhar com dignidade não deveria ser difícil</h3>
            <p>
              Você passou anos se especializando. Construiu um conhecimento genuíno.
              Mas o mercado digital te colocou na vitrine errada — ao lado de quem vende promessas, não resultados.
            </p>
            <p>
              Na Brasil Tupi Conecta, você <strong>cria seu perfil profissional completo</strong>,
              demonstra sua área de atuação e deixa que seu histórico de atendimentos fale por você.
              Quanto mais você atende com excelência, mais credibilidade você acumula.
            </p>
            <p>Sem algoritmos de engajamento. Sem comprar atenção. Apenas <strong>trabalho real gerando reconhecimento real.</strong></p>
          </div>
          <div className="dores-card" id="cliente">
            <span className="dores-card-tag cli">Para o cliente</span>
            <div className="dores-divider cli" />
            <h3>O profissional certo, no momento em que você precisa</h3>
            <p>
              Você já perdeu tempo — e paciência — tentando encontrar alguém confiável.
              Perfis sem histórico. Promessas sem comprovação.
            </p>
            <p>
              Aqui, cada profissional tem um perfil verificado com <strong>avaliações reais de atendimentos reais</strong>.
              Você navega pelos perfis, lê o histórico e toma a decisão com segurança.
            </p>
            <p>E quando a necessidade é urgente: <strong>resposta garantida em até 45 minutos.</strong></p>
          </div>
        </div>
      </div>

      {/* ── URGENTE ── */}
      <div className="urgente-section" id="urgente">
        <div className="urgente-inner">
          <div className="label">O grande diferencial</div>
          <h2 className="title">Consultas Urgentes:<br />quando não dá para esperar</h2>
          <p className="body-text">
            Uma área exclusiva da plataforma com regras rígidas — e recompensas proporcionais.
            Profissionais que entram aqui assinam um compromisso de prontidão. E cumprem.
          </p>
          <div className="urgente-grid">
            <div className="urgente-card">
              <div className="urgente-number red">45</div>
              <div className="urgente-unit">minutos</div>
              <h4>Prontidão obrigatória</h4>
              <p>O profissional tem até 45 minutos para iniciar o atendimento. Não há exceções.</p>
            </div>
            <div className="urgente-card">
              <div className="urgente-number amber">15</div>
              <div className="urgente-unit">minutos</div>
              <h4>Objetividade que resolve</h4>
              <p>Uma consulta focada, direta ao ponto. O cliente sai com respostas reais.</p>
            </div>
            <div className="urgente-card">
              <div className="urgente-number green">↑</div>
              <div className="urgente-unit">credibilidade</div>
              <h4>Acesso que se conquista</h4>
              <p>Apenas profissionais com credibilidade acumulada participam desta área.</p>
            </div>
          </div>
          <div className="urgente-aviso">
            <div className="urgente-aviso-icon">⚠</div>
            <p>
              Esta não é uma área aberta para todos. <strong>O profissional precisa assinar o acordo de conduta
              e manter sua credibilidade ativa</strong> para permanecer nela.
            </p>
          </div>
        </div>
      </div>

      {/* ── ESTÚDIO ── */}
      <div className="urgente-section" id="estudio" style={{ background: '#fdf8f0' }}>
        <div className="urgente-inner">
          <div className="label">Conhecimento que gera renda</div>
          <h2 className="title">Estúdio:<br />seu conhecimento em produtos</h2>
          <p className="body-text">
            Transforme sua expertise em aulas, cursos, PDFs e produtos. Venda diretamente na plataforma
            e alcance clientes que já confiam em você.
          </p>
          <div className="urgente-grid">
            <div className="urgente-card">
              <div className="urgente-number" style={{ color: '#c49a2a' }}>🎓</div>
              <div className="urgente-unit">Aulas e cursos</div>
              <h4>Ensine o que sabe</h4>
              <p>Publique aulas em vídeo, PDFs e cursos completos diretamente no seu perfil.</p>
            </div>
            <div className="urgente-card">
              <div className="urgente-number" style={{ color: '#c49a2a' }}>📦</div>
              <div className="urgente-unit">Produtos físicos e digitais</div>
              <h4>Venda seu acervo</h4>
              <p>Livros, e-books, planilhas, templates — tudo em um só lugar.</p>
            </div>
            <div className="urgente-card">
              <div className="urgente-number" style={{ color: '#c49a2a' }}>8%</div>
              <div className="urgente-unit">comissão reduzida</div>
              <h4>Itens completos ganham mais</h4>
              <p>Configure título, descrição, preço e mídia para obter a menor comissão da plataforma.</p>
            </div>
          </div>
          <div style={{ textAlign: 'center', marginTop: 24 }}>
            <button
              onClick={() => setPagina('estudio-busca')}
              style={{
                background: '#c49a2a', color: '#fff', border: 'none',
                padding: '14px 32px', borderRadius: 10, fontSize: 15,
                fontWeight: 700, cursor: 'pointer',
              }}
            >
              Explorar Estúdios →
            </button>
          </div>
        </div>
      </div>

      {/* ── COMO FUNCIONA ── */}
      <section className="section como-funciona" id="como-funciona">
        <div className="cf-layout">
          <div>
            <div className="label">Como funciona</div>
            <h2 className="title">Credibilidade que cresce com o trabalho</h2>
            <p className="body-text">
              Na Brasil Tupi Conecta, sua reputação é construída de dentro para fora —
              não por marketing, mas por consistência.
            </p>
          </div>
          <div className="cf-steps">
            {[
              { n: '01', title: 'Crie seu perfil profissional completo', desc: 'Descreva sua área de atuação, formação e o que você resolve. Seu perfil é sua identidade.' },
              { n: '02', title: 'Comece a atender e acumular avaliações', desc: 'Cada atendimento concluído gera uma avaliação verificada. O algoritmo valoriza quem entrega.' },
              { n: '03', title: 'Acesse áreas exclusivas conforme cresce', desc: 'Com reputação estabelecida, você desbloqueia as Consultas Urgentes — maior demanda da plataforma.' },
              { n: '04', title: 'Candidate-se ao Programa PMP', desc: 'O topo da credibilidade: verificação, selo visível e vantagens exclusivas por atendimento.' },
            ].map(s => (
              <div className="cf-step" key={s.n}>
                <div className="cf-step-num">{s.n}</div>
                <div className="cf-step-content">
                  <h4>{s.title}</h4>
                  <p>{s.desc}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ── PMP ── */}
      <div className="pmp-section" id="pmp">
        <div className="pmp-inner">
          <div className="pmp-layout">
            <div>
              <div className="label">Programa de Máxima Performance</div>
              <h2 className="title">O Selo PMP: a elite da confiança</h2>
              <p className="body-text">
                O Programa PMP é o reconhecimento máximo dentro da Brasil Tupi Conecta.
                Profissionais verificados ganham um selo visível — compromisso, histórico e excelência comprovada.
              </p>
              <div className="pmp-vantagens">
                {[
                  'Selo de verificação visível em todas as buscas',
                  'Melhores condições de porcentagem por atendimento',
                  'Prioridade de exibição na plataforma',
                  'Acesso antecipado a novas funcionalidades',
                  'Reconhecimento como referência na sua área',
                ].map(v => (
                  <div className="pmp-vantagem" key={v}>{v}</div>
                ))}
              </div>
            </div>
            <div className="pmp-badge-visual">
              <div className="pmp-badge-inner">
                <div className="pmp-shield" />
                <div className="pmp-badge-title">Profissional PMP</div>
                <div className="pmp-badge-sub">Verificado · Brasil Tupi Conecta</div>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* ── CTA FINAL ── */}
      <div className="cta-final">
        <div className="cta-final-inner">
          <div className="cta-final-text">
            <div className="label">Seu próximo passo</div>
            <h2 className="title">Você está do lado de cá ou do lado de lá?</h2>
            <p className="body-text">
              Seja você o profissional que merece ser visto, ou o cliente que merece ser bem atendido —
              a Brasil Tupi Conecta foi construída para os dois lados dessa equação.
            </p>
          </div>
          <div>
            <div className="cta-btns">
              <a href="#cadastro-profissional" className="cta-btn-pro" onClick={e => { e.preventDefault(); setPagina('cadastro') }}>
                <div>
                  <div className="cta-btn-pro-label">Para profissionais</div>
                  <div className="cta-btn-pro-title">Criar meu perfil e ser encontrado →</div>
                </div>
              </a>
              <a href="#buscar" className="cta-btn-cli" onClick={e => { e.preventDefault(); setPagina('cadastro') }}>
                <div>
                  <div className="cta-btn-cli-label">Para clientes</div>
                  <div className="cta-btn-cli-title">Encontrar o profissional certo →</div>
                </div>
              </a>
            </div>
            <div className="cta-valores" style={{ marginTop: 12 }}>
              {[
                { dot: 'azul', text: 'Cadastro gratuito para profissionais', sub: 'Comece agora, sem compromisso' },
                { dot: 'verde', text: 'Sem mensalidade para clientes', sub: 'Pague apenas pelo atendimento que usar' },
                { dot: 'dourado', text: '100% brasileiro, 100% seguro', sub: 'Plataforma desenvolvida para a realidade do Brasil' },
              ].map(v => (
                <div className="cta-valor" key={v.text}>
                  <div className={`cta-valor-dot ${v.dot}`} />
                  <div>
                    <div className="cta-valor-text">{v.text}</div>
                    <div className="cta-valor-sub">{v.sub}</div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>

      {/* ── FOOTER ── */}
      <footer className="footer">
        <div className="footer-inner">
          <div className="footer-brand">Brasil Tupi <span>Conecta</span></div>
          <div className="footer-tagline">
            Onde o profissional é valorizado pelo que é. Onde o cliente encontra em quem confiar.
          </div>
          <div className="footer-hr" />
          <div className="footer-bottom">
            <span className="footer-copy">© 2025 Brasil Tupi Conecta. Todos os direitos reservados.</span>
            <div className="footer-links">
              <a href="#">Termos de uso</a>
              <a href="#">Privacidade</a>
              <a href="mailto:brasiltupi@brasiltupi.org">Contato</a>
            </div>
          </div>
        </div>
      </footer>
    </>
  )
}