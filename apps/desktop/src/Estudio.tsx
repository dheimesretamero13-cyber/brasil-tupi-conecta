import { useState, useEffect } from 'react'
import { supabase } from './supabase'
import './Estudio.css'

interface EstudioProps {
  userId: string
  profissionalId?: string // se passado, mostra vitrine do profissional
  modo: 'dashboard' | 'vitrine' | 'busca'
  onVoltar?: () => void
}

interface ItemEstudio {
  id: string
  profissional_id: string
  titulo: string
  descricao: string
  tipo: string
  preco: number
  preco_original?: number
  capa_url?: string
  video_url?: string
  arquivo_url?: string
  link_externo?: string
  tem_entrega: boolean
  ativo: boolean
  destaque: boolean
  total_vendas: number
  avaliacao_media: number
  criado_em: string
  perfis?: { nome: string }
}

const tipoLabel: Record<string, string> = {
  aula: '🎓 Aula',
  curso: '📚 Curso',
  pdf: '📄 PDF',
  produto_fisico: '📦 Produto físico',
  produto_digital: '💾 Produto digital',
  consulta_avulsa: '💬 Consulta avulsa',
}

const tipoIcon: Record<string, string> = {
  aula: '🎓',
  curso: '📚',
  pdf: '📄',
  produto_fisico: '📦',
  produto_digital: '💾',
  consulta_avulsa: '💬',
}

export default function Estudio({ userId, profissionalId, modo, onVoltar }: EstudioProps) {
  const [itens, setItens] = useState<ItemEstudio[]>([])
  const [loading, setLoading] = useState(true)
  const [itemSelecionado, setItemSelecionado] = useState<ItemEstudio | null>(null)
  const [criando, setCriando] = useState(false)
  const [busca, setBusca] = useState('')
  const [filtroTipo, setFiltroTipo] = useState('todos')
  const [toast, setToast] = useState<string | null>(null)

  // Form novo item
  const [form, setForm] = useState({
    titulo: '',
    descricao: '',
    tipo: 'aula',
    preco: '',
    preco_original: '',
    video_url: '',
    arquivo_url: '',
    link_externo: '',
    tem_entrega: false,
    destaque: false,
  })

  const showToast = (msg: string) => {
    setToast(msg)
    setTimeout(() => setToast(null), 3500)
  }

  async function carregarItens() {
    setLoading(true)
    let query = supabase
      .from('estudio')
      .select('*, perfis(nome), profissionais!inner(is_pmp, verificado, credibilidade)')
      .eq('ativo', true)
      .eq('profissionais.is_pmp', true)
      .eq('profissionais.verificado', true)
      .gte('profissionais.credibilidade', 80)
      .order('destaque', { ascending: false })
      .order('criado_em', { ascending: false })

    if (modo === 'vitrine' && profissionalId) {
      query = query.eq('profissional_id', profissionalId)
    } else if (modo === 'dashboard') {
      query = query.eq('profissional_id', userId)
    } else if (modo === 'busca') {
      // Apenas itens de profissionais PMP verificados
      query = supabase
        .from('estudio')
        .select('*, perfis(nome), profissional_pmp:profissionais!inner(is_pmp, verificado, credibilidade)')
        .eq('ativo', true)
        .eq('profissional_pmp.is_pmp', true)
        .eq('profissional_pmp.verificado', true)
        .gte('profissional_pmp.credibilidade', 80)
        .order('destaque', { ascending: false })
        .order('criado_em', { ascending: false })
    }

    if (filtroTipo !== 'todos') query = query.eq('tipo', filtroTipo)

    const { data } = await query
    setItens(data || [])
    setLoading(false)
  }

  useEffect(() => { carregarItens() }, [filtroTipo, modo, profissionalId])

  async function salvarItem() {
    if (!form.titulo || !form.preco) return showToast('Preencha título e preço.')
    const { error } = await supabase.from('estudio').insert({
      profissional_id: userId,
      titulo: form.titulo,
      descricao: form.descricao,
      tipo: form.tipo,
      preco: parseFloat(form.preco),
      preco_original: form.preco_original ? parseFloat(form.preco_original) : null,
      video_url: form.video_url || null,
      arquivo_url: form.arquivo_url || null,
      link_externo: form.link_externo || null,
      tem_entrega: form.tem_entrega,
      destaque: form.destaque,
    })
    if (error) return showToast('Erro ao salvar item.')
    showToast('✅ Item publicado no Estúdio!')
    setCriando(false)
    setForm({ titulo: '', descricao: '', tipo: 'aula', preco: '', preco_original: '', video_url: '', arquivo_url: '', link_externo: '', tem_entrega: false, destaque: false })
    carregarItens()
  }

  async function comprarItem(item: ItemEstudio) {
    try {
      showToast('⏳ Gerando link de pagamento...')
      const response = await fetch('/api/criar-preferencia', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          titulo: item.titulo,
          descricao: item.descricao,
          preco: item.preco,
          itemId: item.id,
          clienteId: userId,
          profissionalId: item.profissional_id,
        }),
      })
      const data = await response.json()
      if (data.init_point) {
        window.open(data.init_point, '_blank')
      } else {
        showToast('❌ Erro ao gerar pagamento.')
      }
    } catch {
      showToast('❌ Erro ao conectar com MercadoPago.')
    }
  }

  async function toggleAtivo(item: ItemEstudio) {
    await supabase.from('estudio').update({ ativo: !item.ativo }).eq('id', item.id)
    carregarItens()
    showToast(item.ativo ? 'Item ocultado.' : 'Item reativado.')
  }

  const itensFiltrados = itens.filter(i =>
    !busca ||
    i.titulo.toLowerCase().includes(busca.toLowerCase()) ||
    i.descricao?.toLowerCase().includes(busca.toLowerCase())
  )

  // ── VITRINE / BUSCA ────────────────────────────────
  if (modo === 'vitrine' || modo === 'busca') {
    if (itemSelecionado) {
      return (
        <div className="estudio-page">
          <div className="estudio-container">
            <button className="est-back" onClick={() => setItemSelecionado(null)}>← Voltar ao Estúdio</button>
            <div className="est-detalhe-grid">
              <div className="est-detalhe-main">
                <div className="est-detalhe-capa">
                  {itemSelecionado.capa_url
                    ? <img src={itemSelecionado.capa_url} alt={itemSelecionado.titulo} />
                    : <div className="est-capa-placeholder">{tipoIcon[itemSelecionado.tipo]}</div>
                  }
                </div>
                <div className="est-tipo-badge">{tipoLabel[itemSelecionado.tipo]}</div>
                <h1 className="est-detalhe-titulo">{itemSelecionado.titulo}</h1>
                <p className="est-detalhe-desc">{itemSelecionado.descricao}</p>
                {itemSelecionado.video_url && (
                  <div className="est-video-wrap">
                    <h3>Prévia</h3>
                    <video src={itemSelecionado.video_url} controls className="est-video" />
                  </div>
                )}
                <div className="est-criterios">
                  <h4>Por que comprar aqui?</h4>
                  <div className="est-criterio-item">✓ Profissional verificado pela Brasil Tupi Conecta</div>
                  <div className="est-criterio-item">✓ Pagamento seguro via MercadoPago</div>
                  <div className="est-criterio-item">✓ Acesso imediato após confirmação</div>
                  {itemSelecionado.tem_entrega && <div className="est-criterio-item">✓ Entrega para todo o Brasil</div>}
                </div>
              </div>

              <div className="est-detalhe-sidebar">
                <div className="est-compra-card">
                  {itemSelecionado.preco_original && (
                    <div className="est-preco-original">R$ {itemSelecionado.preco_original.toFixed(2)}</div>
                  )}
                  <div className="est-preco-atual">R$ {itemSelecionado.preco.toFixed(2)}</div>
                  {itemSelecionado.preco_original && (
                    <div className="est-desconto">
                      {Math.round((1 - itemSelecionado.preco / itemSelecionado.preco_original) * 100)}% de desconto
                    </div>
                  )}
                  <button className="est-btn-comprar" onClick={() => comprarItem(itemSelecionado)}>
                    💳 Adquirir agora
                  </button>
                  <div className="est-pagamento-info">
                    <div>✓ PIX com desconto</div>
                    <div>✓ Cartão em até 12x</div>
                    <div>✓ Boleto bancário</div>
                  </div>
                  <div className="est-garantia">🔒 Pagamento 100% seguro</div>

                  {itemSelecionado.perfis && (
                    <div className="est-autor">
                      <div className="est-autor-avatar">
                        {itemSelecionado.perfis.nome.split(' ').map(n => n[0]).join('').slice(0, 2)}
                      </div>
                      <div>
                        <div className="est-autor-label">Criado por</div>
                        <div className="est-autor-nome">{itemSelecionado.perfis.nome}</div>
                      </div>
                    </div>
                  )}
                </div>
              </div>
            </div>
          </div>
        </div>
      )
    }

    return (
      <div className="estudio-page">
        {onVoltar && (
          <div className="est-header-back">
            <button className="est-back" onClick={onVoltar}>← Voltar</button>
          </div>
        )}
        <div className="est-vitrine-hero">
          <div className="est-vitrine-hero-inner">
            <div className="est-vitrine-tag">
              {modo === 'busca' ? '🔍 Explorar Estúdios' : '🎨 Estúdio'}
            </div>
            <h1>{modo === 'busca' ? 'Cursos, aulas e produtos' : 'Estúdio do Profissional'}</h1>
            <p>{modo === 'busca'
              ? 'Conteúdos criados por profissionais verificados da plataforma.'
              : 'Tudo que este profissional criou e produz para você.'
            }</p>
            <div className="est-busca-bar">
              <input
                value={busca}
                onChange={e => setBusca(e.target.value)}
                placeholder="Buscar cursos, aulas, produtos..."
                className="est-busca-input"
              />
            </div>
          </div>
        </div>

        <div className="estudio-container">
          {/* Filtros */}
          <div className="est-filtros">
            {['todos', 'aula', 'curso', 'pdf', 'produto_digital', 'produto_fisico', 'consulta_avulsa'].map(t => (
              <button key={t} className={`est-filtro-btn ${filtroTipo === t ? 'active' : ''}`} onClick={() => setFiltroTipo(t)}>
                {t === 'todos' ? 'Todos' : tipoLabel[t]}
              </button>
            ))}
          </div>

          {loading ? (
            <div className="est-loading">⏳ Carregando...</div>
          ) : itensFiltrados.length === 0 ? (
            <div className="est-empty">
              <div className="est-empty-icon">🎨</div>
              <h3>Nenhum item encontrado</h3>
              <p>Tente outros termos ou remova os filtros.</p>
            </div>
          ) : (
            <div className="est-grid">
              {itensFiltrados.map(item => (
                <div key={item.id} className={`est-card ${item.destaque ? 'destaque' : ''}`} onClick={() => setItemSelecionado(item)}>
                  <div className="est-card-capa">
                    {item.capa_url
                      ? <img src={item.capa_url} alt={item.titulo} />
                      : <div className="est-card-capa-placeholder">{tipoIcon[item.tipo]}</div>
                    }
                    {item.destaque && <div className="est-destaque-badge">⭐ Destaque</div>}
                    <div className="est-tipo-tag">{tipoLabel[item.tipo]}</div>
                  </div>
                  <div className="est-card-body">
                    <div className="est-card-titulo">{item.titulo}</div>
                    {item.perfis && <div className="est-card-autor">por {item.perfis.nome}</div>}
                    <div className="est-card-desc">{item.descricao?.slice(0, 80)}{item.descricao?.length > 80 ? '...' : ''}</div>
                    <div className="est-card-footer">
                      <div className="est-card-preco">
                        {item.preco_original && (
                          <span className="est-card-preco-original">R$ {item.preco_original.toFixed(2)}</span>
                        )}
                        <span className="est-card-preco-atual">R$ {item.preco.toFixed(2)}</span>
                      </div>
                      <button className="est-card-btn" onClick={e => { e.stopPropagation(); setItemSelecionado(item) }}>
                        Ver →
                      </button>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {toast && <div className="est-toast">{toast}</div>}
      </div>
    )
  }

  // ── DASHBOARD ──────────────────────────────────────
  return (
    <div className="estudio-dashboard">
      <div className="est-dash-header">
        <div>
          <h2>🎨 Meu Estúdio</h2>
          <p>Gerencie seus cursos, aulas e produtos</p>
        </div>
        <button className="est-btn-novo" onClick={() => setCriando(true)}>+ Novo item</button>
      </div>

      {/* Filtros */}
      <div className="est-filtros">
        {['todos', 'aula', 'curso', 'pdf', 'produto_digital', 'produto_fisico', 'consulta_avulsa'].map(t => (
          <button key={t} className={`est-filtro-btn ${filtroTipo === t ? 'active' : ''}`} onClick={() => setFiltroTipo(t)}>
            {t === 'todos' ? 'Todos' : tipoLabel[t]}
          </button>
        ))}
      </div>

      {/* Stats rápidas */}
      <div className="est-stats">
        <div className="est-stat-card">
          <div className="est-stat-num">{itens.length}</div>
          <div className="est-stat-lbl">Itens publicados</div>
        </div>
        <div className="est-stat-card">
          <div className="est-stat-num">{itens.reduce((a, i) => a + i.total_vendas, 0)}</div>
          <div className="est-stat-lbl">Vendas totais</div>
        </div>
        <div className="est-stat-card">
          <div className="est-stat-num">R$ {itens.reduce((a, i) => a + i.preco * i.total_vendas, 0).toFixed(2)}</div>
          <div className="est-stat-lbl">Receita estimada</div>
        </div>
        <div className="est-stat-card">
          <div className="est-stat-num">{itens.filter(i => i.destaque).length}</div>
          <div className="est-stat-lbl">Em destaque</div>
        </div>
      </div>

      {/* Lista */}
      {loading ? (
        <div className="est-loading">⏳ Carregando...</div>
      ) : itens.length === 0 ? (
        <div className="est-empty">
          <div className="est-empty-icon">🎨</div>
          <h3>Seu Estúdio está vazio</h3>
          <p>Publique seu primeiro item e comece a vender seu conhecimento.</p>
          <button className="est-btn-novo" onClick={() => setCriando(true)}>+ Criar primeiro item</button>
        </div>
      ) : (
        <div className="est-lista">
          {itens.map(item => (
            <div key={item.id} className={`est-lista-item ${!item.ativo ? 'inativo' : ''}`}>
              <div className="est-lista-capa">
                {item.capa_url
                  ? <img src={item.capa_url} alt={item.titulo} />
                  : <div className="est-lista-capa-ph">{tipoIcon[item.tipo]}</div>
                }
              </div>
              <div className="est-lista-info">
                <div className="est-lista-titulo">{item.titulo}</div>
                <div className="est-lista-tipo">{tipoLabel[item.tipo]}</div>
                <div className="est-lista-meta">
                  {item.total_vendas} vendas · R$ {item.preco.toFixed(2)}
                  {item.destaque && ' · ⭐ Destaque'}
                  {!item.ativo && ' · 🔴 Oculto'}
                </div>
              </div>
              <div className="est-lista-acoes">
                <button className="est-acao-btn" onClick={() => toggleAtivo(item)}>
                  {item.ativo ? '🙈 Ocultar' : '👁 Reativar'}
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Modal criar item */}
      {criando && (
        <div className="est-modal-overlay" onClick={() => setCriando(false)}>
          <div className="est-modal" onClick={e => e.stopPropagation()}>
            <div className="est-modal-header">
              <h3>Novo item no Estúdio</h3>
              <button onClick={() => setCriando(false)}>✕</button>
            </div>

            <div className="est-modal-body">
              <div className="est-form-field">
                <label>Tipo *</label>
                <select value={form.tipo} onChange={e => setForm(f => ({ ...f, tipo: e.target.value }))}>
                  {Object.entries(tipoLabel).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
                </select>
              </div>

              <div className="est-form-field">
                <label>Título *</label>
                <input value={form.titulo} onChange={e => setForm(f => ({ ...f, titulo: e.target.value }))} placeholder="Ex: Curso completo de Excel" />
              </div>

              <div className="est-form-field">
                <label>Descrição</label>
                <textarea value={form.descricao} onChange={e => setForm(f => ({ ...f, descricao: e.target.value }))} placeholder="Descreva o que o cliente vai encontrar..." rows={3} />
              </div>

              <div className="est-form-row">
                <div className="est-form-field">
                  <label>Preço (R$) *</label>
                  <input type="number" value={form.preco} onChange={e => setForm(f => ({ ...f, preco: e.target.value }))} placeholder="0,00" min="0" step="0.01" />
                </div>
                <div className="est-form-field">
                  <label>Preço original (R$)</label>
                  <input type="number" value={form.preco_original} onChange={e => setForm(f => ({ ...f, preco_original: e.target.value }))} placeholder="Para mostrar desconto" min="0" step="0.01" />
                </div>
              </div>

              {(form.tipo === 'aula' || form.tipo === 'curso') && (
                <div className="est-form-field">
                  <label>URL do vídeo</label>
                  <input value={form.video_url} onChange={e => setForm(f => ({ ...f, video_url: e.target.value }))} placeholder="https://..." />
                </div>
              )}

              {form.tipo === 'pdf' && (
                <div className="est-form-field">
                  <label>URL do arquivo PDF</label>
                  <input value={form.arquivo_url} onChange={e => setForm(f => ({ ...f, arquivo_url: e.target.value }))} placeholder="https://..." />
                </div>
              )}

              <div className="est-form-field">
                <label>Link externo (opcional)</label>
                <input value={form.link_externo} onChange={e => setForm(f => ({ ...f, link_externo: e.target.value }))} placeholder="Hotmart, Kiwify, Amazon..." />
              </div>

              <div className="est-form-checks">
                <label className="est-check">
                  <input type="checkbox" checked={form.tem_entrega} onChange={e => setForm(f => ({ ...f, tem_entrega: e.target.checked }))} />
                  Tem entrega física
                </label>
                <label className="est-check">
                  <input type="checkbox" checked={form.destaque} onChange={e => setForm(f => ({ ...f, destaque: e.target.checked }))} />
                  Marcar como destaque
                </label>
              </div>

              <div className="est-comissao-info">
                <div className="est-comissao-titulo">💡 Sua comissão</div>
                <div className="est-comissao-desc">
                  {form.video_url && form.descricao && form.preco
                    ? '✅ Item completo — comissão reduzida de 8%'
                    : '⚠️ Complete título, descrição, vídeo e preço para obter a melhor comissão (8%)'}
                </div>
              </div>
            </div>

            <div className="est-modal-footer">
              <button className="est-btn-cancelar" onClick={() => setCriando(false)}>Cancelar</button>
              <button className="est-btn-salvar" onClick={salvarItem}>Publicar no Estúdio</button>
            </div>
          </div>
        </div>
      )}

      {toast && <div className="est-toast">{toast}</div>}
    </div>
  )
}