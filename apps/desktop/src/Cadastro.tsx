import { useState } from 'react'
import './Cadastro.css'
import { signUp } from './supabase'

// ── TIPOS ──────────────────────────────────────────────
type TipoCadastro = 'certificado' | 'liberal' | 'cliente' | null

interface FormData {
  // compartilhado
  nome: string
  email: string
  senha: string
  confirmarSenha: string
  telefone: string
  cpf: string
  dataNascimento: string
  foto: File | null

  // profissional
  areaAtuacao: string
  descricaoProfissional: string
  cidade: string
  estado: string

  // certificado
  diploma: File | null
  instituicao: string
  anoFormacao: string
  conselho: string
  numeroConselho: string
  estadoConselho: string
  cnpj: string
  portfolio: string
  linkedin: string

  // liberal
  cursosLivres: { nome: string; instituicao: string; ano: string }[]
  tempoExperiencia: string
  site: string

  // cliente
  cep: string
  endereco: string
  numero: string
  complemento: string
  bairro: string
  cidadeCliente: string
  estadoCliente: string
}

const initialForm: FormData = {
  nome: '', email: '', senha: '', confirmarSenha: '', telefone: '', cpf: '',
  dataNascimento: '', foto: null, areaAtuacao: '', descricaoProfissional: '',
  cidade: '', estado: '', diploma: null, instituicao: '', anoFormacao: '',
  conselho: '', numeroConselho: '', estadoConselho: '', cnpj: '', portfolio: '',
  linkedin: '', cursosLivres: [{ nome: '', instituicao: '', ano: '' }],
  tempoExperiencia: '', site: '', cep: '', endereco: '', numero: '',
  complemento: '', bairro: '', cidadeCliente: '', estadoCliente: '',
}

const estados = ['AC','AL','AP','AM','BA','CE','DF','ES','GO','MA','MT','MS',
  'MG','PA','PB','PR','PE','PI','RJ','RN','RS','RO','RR','SC','SP','SE','TO']

const conselhos = ['CRM (Medicina)','CRO (Odontologia)','OAB (Advocacia)',
  'CREA (Engenharia)','CRP (Psicologia)','CRN (Nutrição)','COREN (Enfermagem)',
  'CFF (Farmácia)','CFO (Fisioterapia)','CFC (Contabilidade)','Outro']

const areas = ['Saúde e Bem-estar','Direito e Jurídico','Engenharia e Tecnologia',
  'Educação e Tutoria','Finanças e Contabilidade','Psicologia e Terapia',
  'Arquitetura e Design','Comunicação e Marketing','Consultoria Empresarial','Outro']

// ── HELPERS ────────────────────────────────────────────
function maskCPF(v: string) {
  return v.replace(/\D/g,'').slice(0,11)
    .replace(/(\d{3})(\d)/,'$1.$2')
    .replace(/(\d{3})(\d)/,'$1.$2')
    .replace(/(\d{3})(\d{1,2})$/,'$1-$2')
}
function maskCNPJ(v: string) {
  return v.replace(/\D/g,'').slice(0,14)
    .replace(/(\d{2})(\d)/,'$1.$2')
    .replace(/(\d{3})(\d)/,'$1.$2')
    .replace(/(\d{3})(\d)/,'$1/$2')
    .replace(/(\d{4})(\d{1,2})$/,'$1-$2')
}
function maskPhone(v: string) {
  return v.replace(/\D/g,'').slice(0,11)
    .replace(/(\d{2})(\d)/,'($1) $2')
    .replace(/(\d{5})(\d{1,4})$/,'$1-$2')
}
function maskCEP(v: string) {
  return v.replace(/\D/g,'').slice(0,8).replace(/(\d{5})(\d{1,3})$/,'$1-$2')
}
function senhaForte(s: string) {
  return s.length >= 8 && /[A-Z]/.test(s) && /[0-9]/.test(s) && /[^A-Za-z0-9]/.test(s)
}
function senhaForca(s: string) {
  let score = 0
  if (s.length >= 8) score++
  if (s.length >= 12) score++
  if (/[A-Z]/.test(s)) score++
  if (/[0-9]/.test(s)) score++
  if (/[^A-Za-z0-9]/.test(s)) score++
  return score
}

// ── COMPONENTES AUXILIARES ─────────────────────────────
function ProgressBar({ step, total, labels }: { step: number; total: number; labels: string[] }) {
  return (
    <div className="progress-wrap">
      <div className="progress-steps">
        {labels.map((l, i) => (
          <div key={i} className={`progress-step ${i < step ? 'done' : i === step - 1 ? 'active' : ''}`}>
            <div className="progress-dot">{i < step - 1 ? '✓' : i + 1}</div>
            <span className="progress-label">{l}</span>
          </div>
        ))}
      </div>
      <div className="progress-bar">
        <div className="progress-fill" style={{ width: `${((step - 1) / (total - 1)) * 100}%` }} />
      </div>
    </div>
  )
}

function Field({ label, error, children, hint }: { label: string; error?: string; children: React.ReactNode; hint?: string }) {
  return (
    <div className={`field ${error ? 'field-error' : ''}`}>
      <label className="field-label">{label}</label>
      {children}
      {hint && !error && <span className="field-hint">{hint}</span>}
      {error && <span className="field-err-msg">{error}</span>}
    </div>
  )
}

function FileUpload({ label, hint, onChange, value }: { label: string; hint: string; onChange: (f: File | null) => void; value: File | null }) {
  return (
    <div className="file-upload" onClick={() => document.getElementById('fu-' + label)?.click()}>
      <input id={'fu-' + label} type="file" accept=".pdf,.jpg,.jpeg,.png" style={{ display: 'none' }}
        onChange={e => onChange(e.target.files?.[0] ?? null)} />
      {value ? (
        <div className="file-selected">
          <span className="file-icon">📄</span>
          <div>
            <div className="file-name">{value.name}</div>
            <div className="file-size">{(value.size / 1024).toFixed(0)} KB</div>
          </div>
          <button className="file-remove" onClick={e => { e.stopPropagation(); onChange(null) }}>✕</button>
        </div>
      ) : (
        <div className="file-placeholder">
          <span className="file-upload-icon">⬆</span>
          <div className="file-upload-label">{label}</div>
          <div className="file-upload-hint">{hint}</div>
        </div>
      )}
    </div>
  )
}

function SenhaStrength({ senha }: { senha: string }) {
  if (!senha) return null
  const score = senhaForca(senha)
  const labels = ['', 'Muito fraca', 'Fraca', 'Razoável', 'Boa', 'Forte']
  const colors = ['', '#c0392b', '#e67e22', '#f1c40f', '#2ecc71', '#1a5c3a']
  return (
    <div className="senha-strength">
      <div className="senha-bars">
        {[1,2,3,4,5].map(i => (
          <div key={i} className="senha-bar" style={{ background: i <= score ? colors[score] : '#e5e7eb' }} />
        ))}
      </div>
      <span className="senha-label" style={{ color: colors[score] }}>{labels[score]}</span>
    </div>
  )
}

// ── SELEÇÃO DE TIPO ────────────────────────────────────
function TipoSelector({ onSelect }: { onSelect: (t: TipoCadastro) => void }) {
  return (
    <div className="tipo-wrap">
      <div className="tipo-header">
        <div className="cadastro-logo">Brasil Tupi <span>Conecta</span></div>
        <h1>Como você quer se cadastrar?</h1>
        <p>Escolha o tipo de conta que melhor descreve você. Sua escolha define o processo de verificação.</p>
      </div>
      <div className="tipo-cards">
        <button className="tipo-card" onClick={() => onSelect('certificado')}>
          <div className="tipo-card-icon certificado">🎓</div>
          <div className="tipo-card-badge certificado">Verificação completa</div>
          <h2>Profissional Certificado</h2>
          <p>Possui diploma de ensino superior ou registro em conselho de classe (CRM, OAB, CREA, CRP e outros).</p>
          <ul className="tipo-card-list">
            <li>Diploma ou certificado de formação</li>
            <li>Registro em conselho de classe</li>
            <li>CNPJ ou CPF profissional</li>
            <li>Portfólio ou experiência comprovada</li>
          </ul>
          <div className="tipo-card-cta">Começar cadastro →</div>
        </button>

        <button className="tipo-card" onClick={() => onSelect('liberal')}>
          <div className="tipo-card-icon liberal">💼</div>
          <div className="tipo-card-badge liberal">Verificação por experiência</div>
          <h2>Profissional Liberal</h2>
          <p>Atua com base em experiência prática, cursos livres e histórico comprovável — sem necessidade de diploma formal.</p>
          <ul className="tipo-card-list">
            <li>CNPJ ou CPF profissional</li>
            <li>Cursos livres e certificados</li>
            <li>Portfólio ou redes profissionais</li>
          </ul>
          <div className="tipo-card-cta">Começar cadastro →</div>
        </button>

        <button className="tipo-card" onClick={() => onSelect('cliente')}>
          <div className="tipo-card-icon cliente">🔍</div>
          <div className="tipo-card-badge cliente">Acesso imediato</div>
          <h2>Cliente</h2>
          <p>Quero encontrar profissionais verificados e contratar serviços com segurança e histórico real.</p>
          <ul className="tipo-card-list">
            <li>Dados pessoais verificados</li>
            <li>CPF e telefone confirmados</li>
            <li>Endereço e foto de perfil</li>
          </ul>
          <div className="tipo-card-cta">Criar conta →</div>
        </button>
      </div>
    </div>
  )
}

// ── ETAPAS CERTIFICADO ─────────────────────────────────
function CertificadoStep1({ form, set, next }: any) {
  const [errors, setErrors] = useState<any>({})
  function validate() {
    const e: any = {}
    if (!form.nome.trim()) e.nome = 'Nome obrigatório'
    if (!form.email.includes('@')) e.email = 'E-mail inválido'
    if (!senhaForte(form.senha)) e.senha = 'Mín. 8 caracteres, 1 maiúscula, 1 número, 1 símbolo'
    if (form.senha !== form.confirmarSenha) e.confirmarSenha = 'Senhas não coincidem'
    if (form.cpf.replace(/\D/g,'').length < 11) e.cpf = 'CPF inválido'
    if (form.telefone.replace(/\D/g,'').length < 10) e.telefone = 'Telefone inválido'
    if (!form.areaAtuacao) e.areaAtuacao = 'Selecione uma área'
    setErrors(e)
    return Object.keys(e).length === 0
  }
  return (
    <div className="step-content">
      <div className="step-title-block">
        <h2>Dados pessoais</h2>
        <p>Informações básicas do seu perfil público na plataforma.</p>
      </div>
      <div className="fields-grid">
        <Field label="Nome completo *" error={errors.nome}>
          <input value={form.nome} onChange={e => set('nome', e.target.value)} placeholder="Seu nome completo" />
        </Field>
        <Field label="E-mail profissional *" error={errors.email}>
          <input type="email" value={form.email} onChange={e => set('email', e.target.value)} placeholder="seu@email.com" />
        </Field>
        <Field label="CPF *" error={errors.cpf}>
          <input value={form.cpf} onChange={e => set('cpf', maskCPF(e.target.value))} placeholder="000.000.000-00" />
        </Field>
        <Field label="Telefone / WhatsApp *" error={errors.telefone}>
          <input value={form.telefone} onChange={e => set('telefone', maskPhone(e.target.value))} placeholder="(00) 00000-0000" />
        </Field>
        <Field label="Data de nascimento">
          <input type="date" value={form.dataNascimento} onChange={e => set('dataNascimento', e.target.value)} />
        </Field>
        <Field label="Área de atuação *" error={errors.areaAtuacao}>
          <select value={form.areaAtuacao} onChange={e => set('areaAtuacao', e.target.value)}>
            <option value="">Selecione...</option>
            {areas.map(a => <option key={a}>{a}</option>)}
          </select>
        </Field>
        <Field label="Senha *" error={errors.senha}>
          <input type="password" value={form.senha} onChange={e => set('senha', e.target.value)} placeholder="Mín. 8 caracteres" />
          <SenhaStrength senha={form.senha} />
        </Field>
        <Field label="Confirmar senha *" error={errors.confirmarSenha}>
          <input type="password" value={form.confirmarSenha} onChange={e => set('confirmarSenha', e.target.value)} placeholder="Repita a senha" />
        </Field>
        <Field label="Descrição profissional" hint="Aparece no seu perfil público">
          <textarea value={form.descricaoProfissional} onChange={e => set('descricaoProfissional', e.target.value)}
            placeholder="Descreva sua experiência e especialidade..." rows={3} />
        </Field>
        <div className="fields-row">
          <Field label="Cidade">
            <input value={form.cidade} onChange={e => set('cidade', e.target.value)} placeholder="Sua cidade" />
          </Field>
          <Field label="Estado">
            <select value={form.estado} onChange={e => set('estado', e.target.value)}>
              <option value="">UF</option>
              {estados.map(e => <option key={e}>{e}</option>)}
            </select>
          </Field>
        </div>
      </div>
      <div className="step-actions">
        <button className="btn-next" onClick={() => validate() && next()}>Continuar →</button>
      </div>
    </div>
  )
}

function CertificadoStep2({ form, set, next, back }: any) {
  const [errors, setErrors] = useState<any>({})
  function validate() {
    const e: any = {}
    if (!form.instituicao.trim()) e.instituicao = 'Instituição obrigatória'
    if (!form.anoFormacao) e.anoFormacao = 'Ano obrigatório'
    if (!form.conselho) e.conselho = 'Selecione o conselho'
    if (!form.numeroConselho.trim()) e.numeroConselho = 'Número de registro obrigatório'
    if (!form.estadoConselho) e.estadoConselho = 'Estado obrigatório'
    setErrors(e)
    return Object.keys(e).length === 0
  }
  return (
    <div className="step-content">
      <div className="step-title-block">
        <h2>Formação e registro profissional</h2>
        <p>Comprove sua qualificação acadêmica e registro em conselho de classe.</p>
      </div>
      <div className="fields-grid">
        <Field label="Instituição de ensino *" error={errors.instituicao}>
          <input value={form.instituicao} onChange={e => set('instituicao', e.target.value)} placeholder="Nome da universidade ou faculdade" />
        </Field>
        <Field label="Ano de formação *" error={errors.anoFormacao}>
          <input type="number" min="1950" max="2025" value={form.anoFormacao}
            onChange={e => set('anoFormacao', e.target.value)} placeholder="Ex: 2015" />
        </Field>
        <Field label="Upload do diploma *" hint="PDF, JPG ou PNG até 10MB">
          <FileUpload label="Diploma ou certificado" hint="Arraste ou clique para selecionar (PDF, JPG, PNG)"
            value={form.diploma} onChange={f => set('diploma', f)} />
        </Field>
        <div className="section-divider"><span>Conselho de classe</span></div>
        <Field label="Conselho profissional *" error={errors.conselho}>
          <select value={form.conselho} onChange={e => set('conselho', e.target.value)}>
            <option value="">Selecione o conselho...</option>
            {conselhos.map(c => <option key={c}>{c}</option>)}
          </select>
        </Field>
        <Field label="Número de registro *" error={errors.numeroConselho}>
          <input value={form.numeroConselho} onChange={e => set('numeroConselho', e.target.value)}
            placeholder="Ex: 12345/SP" />
        </Field>
        <Field label="Estado do registro *" error={errors.estadoConselho}>
          <select value={form.estadoConselho} onChange={e => set('estadoConselho', e.target.value)}>
            <option value="">UF</option>
            {estados.map(e => <option key={e}>{e}</option>)}
          </select>
        </Field>
      </div>
      <div className="step-actions">
        <button className="btn-back" onClick={back}>← Voltar</button>
        <button className="btn-next" onClick={() => validate() && next()}>Continuar →</button>
      </div>
    </div>
  )
}

function CertificadoStep3({ form, set, next, back }: any) {
  const [errors, setErrors] = useState<any>({})
  function validate() {
    const e: any = {}
    if (form.cnpj && form.cnpj.replace(/\D/g,'').length > 0 && form.cnpj.replace(/\D/g,'').length < 14)
      e.cnpj = 'CNPJ inválido'
    setErrors(e)
    return Object.keys(e).length === 0
  }
  return (
    <div className="step-content">
      <div className="step-title-block">
        <h2>Documentos e portfólio</h2>
        <p>Informações fiscais e comprovação de experiência prática.</p>
      </div>
      <div className="fields-grid">
        <div className="info-box">
          <span className="info-box-icon">ℹ</span>
          <p>O CNPJ não é obrigatório, mas profissionais com CNPJ ativo têm prioridade de exibição e acesso ao Programa PMP.</p>
        </div>
        <Field label="CNPJ (opcional)" error={errors.cnpj} hint="Pessoa Jurídica ou MEI">
          <input value={form.cnpj} onChange={e => set('cnpj', maskCNPJ(e.target.value))} placeholder="00.000.000/0000-00" />
        </Field>
        <div className="section-divider"><span>Portfólio e experiência</span></div>
        <Field label="Link do portfólio ou site (opcional)">
          <input value={form.portfolio} onChange={e => set('portfolio', e.target.value)}
            placeholder="https://seuportfolio.com" />
        </Field>
        <Field label="LinkedIn (opcional)">
          <input value={form.linkedin} onChange={e => set('linkedin', e.target.value)}
            placeholder="https://linkedin.com/in/seuperfil" />
        </Field>
        <Field label="Comprovante de experiência (opcional)" hint="PDF, JPG ou PNG até 10MB">
          <FileUpload label="Comprovante de experiência"
            hint="Carta de recomendação, contrato ou declaração"
            value={form.diploma} onChange={f => set('comprovanteExp', f)} />
        </Field>
        <div className="security-notice">
          <div className="security-notice-icon">🔒</div>
          <div>
            <strong>Seus documentos estão seguros</strong>
            <p>Todos os arquivos são criptografados e usados exclusivamente para verificação da sua conta. Não compartilhamos seus dados com terceiros.</p>
          </div>
        </div>
      </div>
      <div className="step-actions">
        <button className="btn-back" onClick={back}>← Voltar</button>
        <button className="btn-next" onClick={() => validate() && next()}>Continuar →</button>
      </div>
    </div>
  )
}

// ── ETAPAS LIBERAL ─────────────────────────────────────
function LiberalStep1({ form, set, next }: any) {
  const [errors, setErrors] = useState<any>({})
  function validate() {
    const e: any = {}
    if (!form.nome.trim()) e.nome = 'Nome obrigatório'
    if (!form.email.includes('@')) e.email = 'E-mail inválido'
    if (!senhaForte(form.senha)) e.senha = 'Mín. 8 caracteres, 1 maiúscula, 1 número, 1 símbolo'
    if (form.senha !== form.confirmarSenha) e.confirmarSenha = 'Senhas não coincidem'
    if (form.cpf.replace(/\D/g,'').length < 11) e.cpf = 'CPF inválido'
    if (form.telefone.replace(/\D/g,'').length < 10) e.telefone = 'Telefone inválido'
    if (!form.areaAtuacao) e.areaAtuacao = 'Selecione uma área'
    setErrors(e)
    return Object.keys(e).length === 0
  }
  return (
    <div className="step-content">
      <div className="step-title-block">
        <h2>Dados pessoais</h2>
        <p>Informações básicas do seu perfil público na plataforma.</p>
      </div>
      <div className="fields-grid">
        <Field label="Nome completo *" error={errors.nome}>
          <input value={form.nome} onChange={e => set('nome', e.target.value)} placeholder="Seu nome completo" />
        </Field>
        <Field label="E-mail *" error={errors.email}>
          <input type="email" value={form.email} onChange={e => set('email', e.target.value)} placeholder="seu@email.com" />
        </Field>
        <Field label="CPF *" error={errors.cpf}>
          <input value={form.cpf} onChange={e => set('cpf', maskCPF(e.target.value))} placeholder="000.000.000-00" />
        </Field>
        <Field label="Telefone / WhatsApp *" error={errors.telefone}>
          <input value={form.telefone} onChange={e => set('telefone', maskPhone(e.target.value))} placeholder="(00) 00000-0000" />
        </Field>
        <Field label="Área de atuação *" error={errors.areaAtuacao}>
          <select value={form.areaAtuacao} onChange={e => set('areaAtuacao', e.target.value)}>
            <option value="">Selecione...</option>
            {areas.map(a => <option key={a}>{a}</option>)}
          </select>
        </Field>
        <Field label="Tempo de experiência na área">
          <select value={form.tempoExperiencia} onChange={e => set('tempoExperiencia', e.target.value)}>
            <option value="">Selecione...</option>
            <option>Menos de 1 ano</option>
            <option>1 a 3 anos</option>
            <option>3 a 5 anos</option>
            <option>5 a 10 anos</option>
            <option>Mais de 10 anos</option>
          </select>
        </Field>
        <Field label="Senha *" error={errors.senha}>
          <input type="password" value={form.senha} onChange={e => set('senha', e.target.value)} placeholder="Mín. 8 caracteres" />
          <SenhaStrength senha={form.senha} />
        </Field>
        <Field label="Confirmar senha *" error={errors.confirmarSenha}>
          <input type="password" value={form.confirmarSenha} onChange={e => set('confirmarSenha', e.target.value)} placeholder="Repita a senha" />
        </Field>
        <Field label="Descrição profissional" hint="Aparece no seu perfil público">
          <textarea value={form.descricaoProfissional} onChange={e => set('descricaoProfissional', e.target.value)}
            placeholder="Descreva sua experiência e o que você oferece..." rows={3} />
        </Field>
      </div>
      <div className="step-actions">
        <button className="btn-next" onClick={() => validate() && next()}>Continuar →</button>
      </div>
    </div>
  )
}

function LiberalStep2({ form, set, next, back }: any) {
  function addCurso() { set('cursosLivres', [...form.cursosLivres, { nome: '', instituicao: '', ano: '' }]) }
  function removeCurso(i: number) { set('cursosLivres', form.cursosLivres.filter((_: any, idx: number) => idx !== i)) }
  function updateCurso(i: number, field: string, value: string) {
    const updated = form.cursosLivres.map((c: any, idx: number) => idx === i ? { ...c, [field]: value } : c)
    set('cursosLivres', updated)
  }
  return (
    <div className="step-content">
      <div className="step-title-block">
        <h2>Cursos e experiência</h2>
        <p>Liste seus cursos livres e certificados. Quanto mais completo, maior sua credibilidade.</p>
      </div>
      <div className="fields-grid">
        <div className="cursos-header">
          <span>Cursos livres e certificações</span>
          <button className="btn-add-curso" onClick={addCurso}>+ Adicionar curso</button>
        </div>
        {form.cursosLivres.map((c: any, i: number) => (
          <div className="curso-item" key={i}>
            <div className="curso-num">#{i + 1}</div>
            <div className="curso-fields">
              <input value={c.nome} onChange={e => updateCurso(i, 'nome', e.target.value)}
                placeholder="Nome do curso / certificação" />
              <input value={c.instituicao} onChange={e => updateCurso(i, 'instituicao', e.target.value)}
                placeholder="Instituição ou plataforma (Udemy, Coursera...)" />
              <input value={c.ano} onChange={e => updateCurso(i, 'ano', e.target.value)}
                placeholder="Ano de conclusão" style={{ width: 140 }} />
            </div>
            {form.cursosLivres.length > 1 && (
              <button className="curso-remove" onClick={() => removeCurso(i)}>✕</button>
            )}
          </div>
        ))}
        <div className="section-divider"><span>Presença online</span></div>
        <Field label="Portfólio ou site (opcional)" hint="Seu trabalho publicado online">
          <input value={form.portfolio} onChange={e => set('portfolio', e.target.value)}
            placeholder="https://seuportfolio.com" />
        </Field>
        <Field label="LinkedIn (opcional)">
          <input value={form.linkedin} onChange={e => set('linkedin', e.target.value)}
            placeholder="https://linkedin.com/in/seuperfil" />
        </Field>
        <Field label="Outro (Instagram profissional, GitHub, Behance...)">
          <input value={form.site} onChange={e => set('site', e.target.value)}
            placeholder="https://" />
        </Field>
      </div>
      <div className="step-actions">
        <button className="btn-back" onClick={back}>← Voltar</button>
        <button className="btn-next" onClick={next}>Continuar →</button>
      </div>
    </div>
  )
}

function LiberalStep3({ form, set, next, back }: any) {
  const [errors, setErrors] = useState<any>({})
  function validate() {
    const e: any = {}
    if (!form.cnpj.trim()) e.cnpj = 'CNPJ ou CPF profissional é obrigatório para profissionais liberais'
    setErrors(e)
    return Object.keys(e).length === 0
  }
  return (
    <div className="step-content">
      <div className="step-title-block">
        <h2>Documento fiscal</h2>
        <p>Necessário para emissão de recibos e garantia de pagamentos na plataforma.</p>
      </div>
      <div className="fields-grid">
        <div className="info-box warning">
          <span className="info-box-icon">⚠</span>
          <p>Para profissionais liberais, o CNPJ ou CPF profissional (MEI) é <strong>obrigatório</strong>. Ele garante a legalidade dos pagamentos e aumenta a confiança dos clientes.</p>
        </div>
        <Field label="CNPJ / MEI *" error={errors.cnpj}>
          <input value={form.cnpj} onChange={e => set('cnpj', maskCNPJ(e.target.value))} placeholder="00.000.000/0000-00" />
        </Field>
        <Field label="Comprovante de CNPJ (opcional)" hint="Cartão CNPJ ou comprovante de MEI">
          <FileUpload label="Comprovante CNPJ"
            hint="PDF, JPG ou PNG até 10MB"
            value={form.diploma} onChange={f => set('comprovanteCnpj', f)} />
        </Field>
        <div className="security-notice">
          <div className="security-notice-icon">🔒</div>
          <div>
            <strong>Seus documentos estão seguros</strong>
            <p>Todos os arquivos são criptografados e usados exclusivamente para verificação da sua conta.</p>
          </div>
        </div>
      </div>
      <div className="step-actions">
        <button className="btn-back" onClick={back}>← Voltar</button>
        <button className="btn-next" onClick={() => validate() && next()}>Continuar →</button>
      </div>
    </div>
  )
}

// ── ETAPAS CLIENTE ─────────────────────────────────────
function ClienteStep1({ form, set, next }: any) {
  const [errors, setErrors] = useState<any>({})
  function validate() {
    const e: any = {}
    if (!form.nome.trim()) e.nome = 'Nome obrigatório'
    if (!form.email.includes('@')) e.email = 'E-mail inválido'
    if (!senhaForte(form.senha)) e.senha = 'Mín. 8 caracteres, 1 maiúscula, 1 número, 1 símbolo'
    if (form.senha !== form.confirmarSenha) e.confirmarSenha = 'Senhas não coincidem'
    setErrors(e)
    return Object.keys(e).length === 0
  }
  return (
    <div className="step-content">
      <div className="step-title-block">
        <h2>Dados da conta</h2>
        <p>Crie seu acesso à plataforma.</p>
      </div>
      <div className="fields-grid">
        <Field label="Nome completo *" error={errors.nome}>
          <input value={form.nome} onChange={e => set('nome', e.target.value)} placeholder="Seu nome completo" />
        </Field>
        <Field label="E-mail *" error={errors.email}>
          <input type="email" value={form.email} onChange={e => set('email', e.target.value)} placeholder="seu@email.com" />
        </Field>
        <Field label="Senha *" error={errors.senha}>
          <input type="password" value={form.senha} onChange={e => set('senha', e.target.value)} placeholder="Mín. 8 caracteres" />
          <SenhaStrength senha={form.senha} />
        </Field>
        <Field label="Confirmar senha *" error={errors.confirmarSenha}>
          <input type="password" value={form.confirmarSenha} onChange={e => set('confirmarSenha', e.target.value)} placeholder="Repita a senha" />
        </Field>
      </div>
      <div className="step-actions">
        <button className="btn-next" onClick={() => validate() && next()}>Continuar →</button>
      </div>
    </div>
  )
}

function ClienteStep2({ form, set, next, back }: any) {
  const [errors, setErrors] = useState<any>({})
  function validate() {
    const e: any = {}
    if (form.cpf.replace(/\D/g,'').length < 11) e.cpf = 'CPF inválido'
    if (form.telefone.replace(/\D/g,'').length < 10) e.telefone = 'Telefone inválido'
    setErrors(e)
    return Object.keys(e).length === 0
  }
  return (
    <div className="step-content">
      <div className="step-title-block">
        <h2>Verificação de identidade</h2>
        <p>Usamos essas informações para garantir a segurança de todos na plataforma.</p>
      </div>
      <div className="fields-grid">
        <Field label="CPF *" error={errors.cpf}>
          <input value={form.cpf} onChange={e => set('cpf', maskCPF(e.target.value))} placeholder="000.000.000-00" />
        </Field>
        <Field label="Telefone / WhatsApp *" error={errors.telefone} hint="Usado para autenticação em 2 fatores">
          <input value={form.telefone} onChange={e => set('telefone', maskPhone(e.target.value))} placeholder="(00) 00000-0000" />
        </Field>
        <Field label="Data de nascimento">
          <input type="date" value={form.dataNascimento} onChange={e => set('dataNascimento', e.target.value)} />
        </Field>
      </div>
      <div className="step-actions">
        <button className="btn-back" onClick={back}>← Voltar</button>
        <button className="btn-next" onClick={() => validate() && next()}>Continuar →</button>
      </div>
    </div>
  )
}

function ClienteStep3({ form, set, next, back }: any) {
  const [errors, setErrors] = useState<any>({})
  async function buscarCEP(cep: string) {
    const raw = cep.replace(/\D/g,'')
    if (raw.length === 8) {
      try {
        const r = await fetch(`https://viacep.com.br/ws/${raw}/json/`)
        const d = await r.json()
        if (!d.erro) {
          set('endereco', d.logradouro)
          set('bairro', d.bairro)
          set('cidadeCliente', d.localidade)
          set('estadoCliente', d.uf)
        }
      } catch {}
    }
  }
  function validate() {
    const e: any = {}
    if (!form.endereco.trim()) e.endereco = 'Endereço obrigatório'
    if (!form.numero.trim()) e.numero = 'Número obrigatório'
    if (!form.cidadeCliente.trim()) e.cidadeCliente = 'Cidade obrigatória'
    setErrors(e)
    return Object.keys(e).length === 0
  }
  return (
    <div className="step-content">
      <div className="step-title-block">
        <h2>Endereço e foto de perfil</h2>
        <p>Seu endereço ajuda a encontrar profissionais próximos a você.</p>
      </div>
      <div className="fields-grid">
        <Field label="Foto de perfil (opcional)">
          <div className="foto-upload">
            <div className="foto-preview">
              {form.foto ? (
                <img src={URL.createObjectURL(form.foto)} alt="preview" />
              ) : (
                <span>👤</span>
              )}
            </div>
            <div>
              <button className="btn-foto" onClick={() => document.getElementById('foto-input')?.click()}>
                Escolher foto
              </button>
              <input id="foto-input" type="file" accept="image/*" style={{ display: 'none' }}
                onChange={e => set('foto', e.target.files?.[0] ?? null)} />
              <p className="foto-hint">JPG ou PNG, máximo 5MB</p>
            </div>
          </div>
        </Field>
        <Field label="CEP">
          <input value={form.cep} onChange={e => { set('cep', maskCEP(e.target.value)); buscarCEP(e.target.value) }}
            placeholder="00000-000" />
        </Field>
        <Field label="Endereço *" error={errors.endereco}>
          <input value={form.endereco} onChange={e => set('endereco', e.target.value)} placeholder="Rua, Avenida..." />
        </Field>
        <div className="fields-row">
          <Field label="Número *" error={errors.numero}>
            <input value={form.numero} onChange={e => set('numero', e.target.value)} placeholder="123" />
          </Field>
          <Field label="Complemento">
            <input value={form.complemento} onChange={e => set('complemento', e.target.value)} placeholder="Apto, Bloco..." />
          </Field>
        </div>
        <Field label="Bairro">
          <input value={form.bairro} onChange={e => set('bairro', e.target.value)} placeholder="Seu bairro" />
        </Field>
        <div className="fields-row">
          <Field label="Cidade *" error={errors.cidadeCliente}>
            <input value={form.cidadeCliente} onChange={e => set('cidadeCliente', e.target.value)} placeholder="Sua cidade" />
          </Field>
          <Field label="Estado">
            <select value={form.estadoCliente} onChange={e => set('estadoCliente', e.target.value)}>
              <option value="">UF</option>
              {estados.map(e => <option key={e}>{e}</option>)}
            </select>
          </Field>
        </div>
      </div>
      <div className="step-actions">
        <button className="btn-back" onClick={back}>← Voltar</button>
        <button className="btn-next" onClick={() => validate() && next()}>Continuar →</button>
      </div>
    </div>
  )
}

// ── REVISÃO FINAL ──────────────────────────────────────
function Revisao({ form, tipo, back, onSubmit }: any) {
  const [aceito, setAceito] = useState(false)
  const [loading, setLoading] = useState(false)
  const [erro, setErro] = useState('')
  const tipoLabel = { certificado: 'Profissional Certificado', liberal: 'Profissional Liberal', cliente: 'Cliente' }

  async function handleSubmit() {
    setLoading(true)
    setErro('')
    try {
      const tipoSupabase = tipo === 'certificado'
        ? 'profissional_certificado'
        : tipo === 'liberal'
        ? 'profissional_liberal'
        : 'cliente'

      await signUp(form.email, form.senha, {
        nome: form.nome,
        telefone: form.telefone,
        cpf: form.cpf,
        tipo: tipoSupabase,
        cidade: tipo === 'cliente' ? form.cidadeCliente : form.cidade,
        estado: tipo === 'cliente' ? form.estadoCliente : form.estado,
      })
      onSubmit()
    } catch (err: any) {
      if (err.message?.includes('already registered')) {
        setErro('Este e-mail já está cadastrado. Tente fazer login.')
      } else {
        setErro('Erro ao criar conta. Tente novamente.')
      }
    } finally {
      setLoading(false)
    }
  }
  return (
    <div className="step-content">
      <div className="step-title-block">
        <h2>Revisão do cadastro</h2>
        <p>Confirme seus dados antes de enviar.</p>
      </div>
      <div className="revisao-grid">
        <div className="revisao-section">
          <div className="revisao-label">Tipo de conta</div>
          <div className="revisao-badge">{tipoLabel[tipo as keyof typeof tipoLabel]}</div>
        </div>
        <div className="revisao-section">
          <div className="revisao-label">Dados pessoais</div>
          <div className="revisao-item"><span>Nome</span><strong>{form.nome || '—'}</strong></div>
          <div className="revisao-item"><span>E-mail</span><strong>{form.email || '—'}</strong></div>
          <div className="revisao-item"><span>CPF</span><strong>{form.cpf || '—'}</strong></div>
          <div className="revisao-item"><span>Telefone</span><strong>{form.telefone || '—'}</strong></div>
        </div>
        {(tipo === 'certificado' || tipo === 'liberal') && (
          <div className="revisao-section">
            <div className="revisao-label">Dados profissionais</div>
            <div className="revisao-item"><span>Área</span><strong>{form.areaAtuacao || '—'}</strong></div>
            {tipo === 'certificado' && <>
              <div className="revisao-item"><span>Instituição</span><strong>{form.instituicao || '—'}</strong></div>
              <div className="revisao-item"><span>Conselho</span><strong>{form.conselho || '—'}</strong></div>
              <div className="revisao-item"><span>Registro</span><strong>{form.numeroConselho || '—'}</strong></div>
            </>}
            {tipo === 'liberal' && <>
              <div className="revisao-item"><span>Experiência</span><strong>{form.tempoExperiencia || '—'}</strong></div>
              <div className="revisao-item"><span>Cursos</span><strong>{form.cursosLivres.filter((c: any) => c.nome).length} cadastrado(s)</strong></div>
            </>}
            <div className="revisao-item"><span>CNPJ</span><strong>{form.cnpj || '—'}</strong></div>
          </div>
        )}
        {tipo === 'cliente' && (
          <div className="revisao-section">
            <div className="revisao-label">Endereço</div>
            <div className="revisao-item"><span>Cidade</span><strong>{form.cidadeCliente || '—'}</strong></div>
            <div className="revisao-item"><span>Estado</span><strong>{form.estadoCliente || '—'}</strong></div>
          </div>
        )}
        <div className="revisao-section">
          <div className="revisao-label">Documentos enviados</div>
          <div className="revisao-item">
            <span>Diploma / Comprovante</span>
            <strong>{form.diploma ? `✓ ${form.diploma.name}` : 'Não enviado'}</strong>
          </div>
        </div>
      </div>
      <div className="termos-wrap">
        <label className="termos-label">
          <input type="checkbox" checked={aceito} onChange={e => setAceito(e.target.checked)} />
          <span>Li e aceito os <a href="#">Termos de Uso</a> e a <a href="#">Política de Privacidade</a> da Brasil Tupi Conecta. Declaro que as informações fornecidas são verdadeiras e autênticas.</span>
        </label>
      </div>
      {erro && (
        <div style={{ background: '#fde8e8', border: '1px solid #c0392b', borderRadius: 6, padding: '10px 14px', fontSize: 13, color: '#c0392b', marginBottom: 8 }}>
          {erro}
        </div>
      )}
      <div className="step-actions">
        <button className="btn-back" onClick={back}>← Voltar</button>
        <button className="btn-submit" disabled={!aceito || loading} onClick={handleSubmit}>
          {loading ? 'Enviando...' : 'Enviar cadastro para verificação'}
        </button>
      </div>
    </div>
  )
}

// ── SUCESSO ────────────────────────────────────────────
function Sucesso({ tipo }: { tipo: TipoCadastro }) {
  return (
    <div className="sucesso-wrap">
      <div className="sucesso-icon">✓</div>
      <h2>Cadastro enviado com sucesso!</h2>
      {tipo !== 'cliente' ? (
        <>
          <p>Seu cadastro foi recebido e está em análise pela nossa equipe de verificação.</p>
          <div className="sucesso-steps">
            <div className="sucesso-step"><span>1</span>Análise dos documentos enviados (até 48h)</div>
            <div className="sucesso-step"><span>2</span>Confirmação por e-mail com resultado</div>
            <div className="sucesso-step"><span>3</span>Ativação do perfil e acesso à plataforma</div>
          </div>
        </>
      ) : (
        <p>Sua conta foi criada. Você já pode buscar profissionais verificados e agendar consultas.</p>
      )}
      <button className="btn-next" onClick={() => window.location.reload()}>Voltar ao início</button>
    </div>
  )
}

// ── COMPONENTE PRINCIPAL ───────────────────────────────
export default function Cadastro({ onVoltar }: { onVoltar?: () => void }) {
  const [tipo, setTipo] = useState<TipoCadastro>(null)
  const [step, setStep] = useState(1)
  const [form, setForm] = useState<FormData>(initialForm)
  const [concluido, setConcluido] = useState(false)

  function set(field: string, value: any) {
    setForm(f => ({ ...f, [field]: value }))
  }
  function next() { setStep(s => s + 1) }
  function back() { setStep(s => s - 1) }

  const labelsCertificado = ['Dados pessoais', 'Formação', 'Documentos', 'Revisão']
  const labelsLiberal     = ['Dados pessoais', 'Experiência', 'Documentos', 'Revisão']
  const labelsCliente     = ['Conta', 'Verificação', 'Endereço', 'Revisão']

  if (!tipo) return <TipoSelector onSelect={t => { setTipo(t); setStep(1) }} />
  if (concluido) return <Sucesso tipo={tipo} />

  const labels = tipo === 'certificado' ? labelsCertificado : tipo === 'liberal' ? labelsLiberal : labelsCliente

  return (
    <div className="cadastro-wrap">
      <div className="cadastro-header">
        <button className="cadastro-back-tipo" onClick={() => tipo ? setTipo(null) : onVoltar?.()}>← {tipo ? 'Trocar tipo de cadastro' : 'Voltar ao início'}</button>
        <div className="cadastro-logo">Brasil Tupi <span>Conecta</span></div>
      </div>
      <div className="cadastro-body">
        <div className="cadastro-sidebar">
          <div className="sidebar-tipo-badge">
            {tipo === 'certificado' ? '🎓 Profissional Certificado' : tipo === 'liberal' ? '💼 Profissional Liberal' : '🔍 Cliente'}
          </div>
          <ProgressBar step={step} total={4} labels={labels} />
          <div className="sidebar-security">
            <div className="sidebar-security-icon">🔒</div>
            <div>
              <strong>Cadastro seguro</strong>
              <p>Dados criptografados e protegidos. Verificação realizada por equipe especializada.</p>
            </div>
          </div>
        </div>
        <div className="cadastro-form">
          {tipo === 'certificado' && (
            <>
              {step === 1 && <CertificadoStep1 form={form} set={set} next={next} />}
              {step === 2 && <CertificadoStep2 form={form} set={set} next={next} back={back} />}
              {step === 3 && <CertificadoStep3 form={form} set={set} next={next} back={back} />}
              {step === 4 && <Revisao form={form} tipo={tipo} back={back} onSubmit={() => setConcluido(true)} />}
            </>
          )}
          {tipo === 'liberal' && (
            <>
              {step === 1 && <LiberalStep1 form={form} set={set} next={next} />}
              {step === 2 && <LiberalStep2 form={form} set={set} next={next} back={back} />}
              {step === 3 && <LiberalStep3 form={form} set={set} next={next} back={back} />}
              {step === 4 && <Revisao form={form} tipo={tipo} back={back} onSubmit={() => setConcluido(true)} />}
            </>
          )}
          {tipo === 'cliente' && (
            <>
              {step === 1 && <ClienteStep1 form={form} set={set} next={next} />}
              {step === 2 && <ClienteStep2 form={form} set={set} next={next} back={back} />}
              {step === 3 && <ClienteStep3 form={form} set={set} next={next} back={back} />}
              {step === 4 && <Revisao form={form} tipo={tipo} back={back} onSubmit={() => setConcluido(true)} />}
            </>
          )}
        </div>
      </div>
    </div>
  )
}