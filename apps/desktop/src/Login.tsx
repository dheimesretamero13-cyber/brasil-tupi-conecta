import { useState } from 'react'
import './Login.css'
import { signIn, getPerfil } from './supabase'

type TipoLogin = 'profissional' | 'cliente'

interface LoginProps {
  onVoltar: () => void
  onEntrar: (tipo: TipoLogin) => void
}

export default function Login({ onVoltar, onEntrar }: LoginProps) {
  const [tipo, setTipo] = useState<TipoLogin>('profissional')
  const [email, setEmail] = useState('')
  const [senha, setSenha] = useState('')
  const [mostrarSenha, setMostrarSenha] = useState(false)
  const [lembrar, setLembrar] = useState(false)
  const [errors, setErrors] = useState<any>({})
  const [loading, setLoading] = useState(false)
  const [erroGeral, setErroGeral] = useState('')
  const [esqueceu, setEsqueceu] = useState(false)
  const [emailRecupera, setEmailRecupera] = useState('')
  const [recuperaEnviado, setRecuperaEnviado] = useState(false)

  function validate() {
    const e: any = {}
    if (!email.includes('@')) e.email = 'E-mail inválido'
    if (senha.length < 6) e.senha = 'Senha muito curta'
    setErrors(e)
    return Object.keys(e).length === 0
  }

  async function handleLogin() {
    if (!validate()) return
    setLoading(true)
    setErroGeral('')
    try {
      const data = await signIn(email, senha)
      if (data.user) {
        const perfil = await getPerfil(data.user.id)
        if (perfil) {
          const isProfissional = perfil.tipo === 'profissional_certificado' || perfil.tipo === 'profissional_liberal'
          onEntrar(isProfissional ? 'profissional' : 'cliente')
        } else {
          onEntrar(tipo)
        }
      }
    } catch (err: any) {
      setErroGeral('E-mail ou senha incorretos. Verifique e tente novamente.')
    } finally {
      setLoading(false)
    }
  }

  function handleRecupera() {
    if (!emailRecupera.includes('@')) return
    setRecuperaEnviado(true)
  }

  if (esqueceu) {
    return (
      <div className="login-page">
        <div className="login-right" style={{ width: '100%' }}>
          <button className="login-back" onClick={() => { setEsqueceu(false); setRecuperaEnviado(false) }}>
            ← Voltar ao login
          </button>
          <div className="login-card">
            <div className="login-logo">Brasil Tupi <span>Conecta</span></div>
            {!recuperaEnviado ? (
              <>
                <div className="login-title-block">
                  <h1>Recuperar acesso</h1>
                  <p>Digite seu e-mail cadastrado. Enviaremos um link para redefinir sua senha.</p>
                </div>
                <div className="login-field">
                  <label>E-mail cadastrado</label>
                  <input
                    type="email"
                    value={emailRecupera}
                    onChange={e => setEmailRecupera(e.target.value)}
                    placeholder="seu@email.com"
                    autoFocus
                  />
                </div>
                <button className="login-btn-primary" onClick={handleRecupera}>
                  Enviar link de recuperação
                </button>
              </>
            ) : (
              <div className="login-recupera-ok">
                <div className="login-recupera-icon">✉</div>
                <h2>E-mail enviado!</h2>
                <p>Verifique sua caixa de entrada em <strong>{emailRecupera}</strong>. O link expira em 30 minutos.</p>
                <button className="login-btn-ghost" onClick={() => { setEsqueceu(false); setRecuperaEnviado(false) }}>
                  Voltar ao login
                </button>
              </div>
            )}
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="login-page">
      <div className="login-left">
        <div className="login-left-content">
          <div className="login-logo-left">Brasil Tupi <span>Conecta</span></div>
          <h2>Bem-vindo de volta.</h2>
          <p>Sua credibilidade continua crescendo. Entre e veja o que mudou desde sua última visita.</p>
          <div className="login-stats">
            <div className="login-stat">
              <div className="login-stat-num">45min</div>
              <div className="login-stat-label">Tempo máximo de resposta urgente</div>
            </div>
            <div className="login-stat">
              <div className="login-stat-num">PMP</div>
              <div className="login-stat-label">Selo de máxima performance</div>
            </div>
          </div>
        </div>
      </div>

      <div className="login-right">
        <button className="login-back" onClick={onVoltar}>← Voltar ao início</button>

        <div className="login-card">
          <div className="login-logo">Brasil Tupi <span>Conecta</span></div>

          <div className="login-title-block">
            <h1>Entrar na plataforma</h1>
            <p>Acesse sua conta para continuar.</p>
          </div>

          <div className="login-tipo-tabs">
            <button
              className={`login-tipo-tab ${tipo === 'profissional' ? 'active' : ''}`}
              onClick={() => setTipo('profissional')}
            >
              Sou profissional
            </button>
            <button
              className={`login-tipo-tab ${tipo === 'cliente' ? 'active' : ''}`}
              onClick={() => setTipo('cliente')}
            >
              Sou cliente
            </button>
          </div>

          <div className="login-fields">
            <div className={`login-field ${errors.email ? 'error' : ''}`}>
              <label>E-mail</label>
              <input
                type="email"
                value={email}
                onChange={e => { setEmail(e.target.value); setErroGeral('') }}
                placeholder="seu@email.com"
                onKeyDown={e => e.key === 'Enter' && handleLogin()}
              />
              {errors.email && <span className="login-field-err">{errors.email}</span>}
            </div>

            <div className={`login-field ${errors.senha ? 'error' : ''}`}>
              <div className="login-field-header">
                <label>Senha</label>
                <button className="login-esqueceu" onClick={() => setEsqueceu(true)}>
                  Esqueci minha senha
                </button>
              </div>
              <div className="login-senha-wrap">
                <input
                  type={mostrarSenha ? 'text' : 'password'}
                  value={senha}
                  onChange={e => { setSenha(e.target.value); setErroGeral('') }}
                  placeholder="Sua senha"
                  onKeyDown={e => e.key === 'Enter' && handleLogin()}
                />
                <button
                  className="login-ver-senha"
                  onClick={() => setMostrarSenha(v => !v)}
                  type="button"
                >
                  {mostrarSenha ? '🙈' : '👁'}
                </button>
              </div>
              {errors.senha && <span className="login-field-err">{errors.senha}</span>}
            </div>
          </div>

          {erroGeral && (
            <div style={{
              background: '#fde8e8', border: '1px solid #c0392b',
              borderRadius: 6, padding: '10px 14px',
              fontSize: 13, color: '#c0392b', marginBottom: 8
            }}>
              {erroGeral}
            </div>
          )}

          <label className="login-lembrar">
            <input type="checkbox" checked={lembrar} onChange={e => setLembrar(e.target.checked)} />
            <span>Manter conectado por 30 dias</span>
          </label>

          <button
            className={`login-btn-primary ${loading ? 'loading' : ''}`}
            onClick={handleLogin}
            disabled={loading}
          >
            {loading ? <span className="login-spinner" /> : `Entrar como ${tipo === 'profissional' ? 'profissional' : 'cliente'}`}
          </button>

          <div className="login-divider"><span>ou</span></div>

          <div className="login-seguranca">
            <div className="login-seguranca-item">
              <span>🔒</span>
              <p>Conexão criptografada</p>
            </div>
            <div className="login-seguranca-item">
              <span>✓</span>
              <p>Dados protegidos</p>
            </div>
            <div className="login-seguranca-item">
              <span>🛡</span>
              <p>2FA disponível</p>
            </div>
          </div>

          <div className="login-cadastro-link">
            Não tem conta?{' '}
            <button onClick={onVoltar} className="login-link-btn">
              Criar conta gratuita
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}