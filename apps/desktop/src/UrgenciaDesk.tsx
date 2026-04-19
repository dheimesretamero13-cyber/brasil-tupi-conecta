import { useState, useEffect, useRef, useCallback } from 'react'
import { supabase } from './supabase'

interface UrgenciaDeskProps {
  userId: string
  perfilTipo: 'profissional' | 'cliente'
  onVoltar: () => void
}

interface Slot {
  hora: string
  timestamp: number
  status: 'disponivel' | 'ocupado' | 'passado'
  urgenciaId?: string
  clienteNome?: string
  clienteId?: string
}

interface Urgencia {
  id: string
  profissional_id: string
  cliente_id: string
  status: string
  slot_hora: string
  iniciada_em?: string
  encerrada_em?: string
  duracao_segundos?: number
  offer?: any
  answer?: any
  ice_candidates_pro?: any[]
  ice_candidates_cli?: any[]
}

interface MensagemChat {
  id: string
  de: 'profissional' | 'cliente'
  texto: string
  hora: string
}

const CALL_DURATION = 15 * 60 // 15 minutos em segundos
const WARN_SEC = 2 * 60
const CRIT_SEC = 30

function fmtTime(sec: number) {
  const m = Math.floor(sec / 60)
  const s = sec % 60
  return `${m < 10 ? '0' : ''}${m}:${s < 10 ? '0' : ''}${s}`
}

function getSlots(): Slot[] {
  const slots: Slot[] = []
  const now = Date.now()
  const hojeInicio = new Date()
  hojeInicio.setHours(0, 0, 0, 0)

  for (let i = 0; i < 32; i++) {
    const ts = hojeInicio.getTime() + i * 45 * 60 * 1000
    const hora = new Date(ts).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })
    slots.push({
      hora,
      timestamp: ts,
      status: ts < now - 600000 ? 'passado' : 'disponivel',
    })
  }
  return slots
}

export default function UrgenciaDesk({ userId, perfilTipo, onVoltar }: UrgenciaDeskProps) {
  const isPro = perfilTipo === 'profissional'
 const [aba, setAba] = useState<'fila' | 'timer' | 'chat' | 'historico'>('fila')
  const [slots, setSlots] = useState<Slot[]>(getSlots())
  const [urgencias, setUrgencias] = useState<Urgencia[]>([])
  const [meuAgendamento, setMeuAgendamento] = useState<Urgencia | null>(null)
  const [historico, setHistorico] = useState<Urgencia[]>([])
  const [callAtiva, setCallAtiva] = useState(false)
  const [urgenciaAtiva, setUrgenciaAtiva] = useState<Urgencia | null>(null)
  const [tempoRestante, setTempoRestante] = useState(CALL_DURATION)
  const [tempoDecorrido, setTempoDecorrido] = useState(0)
  const [mensagens, setMensagens] = useState<MensagemChat[]>([])
  const [msgInput, setMsgInput] = useState('')
  const [notifChamada, setNotifChamada] = useState<{ urgenciaId: string; proNome: string } | null>(null)
  const [totalHoje, setTotalHoje] = useState(0)
  const [loading, setLoading] = useState(false)
  const [toast, setToast] = useState<{ msg: string; tipo: string } | null>(null)

  // WebRTC
  const pcRef = useRef<RTCPeerConnection | null>(null)
  const localVideoRef = useRef<HTMLVideoElement>(null)
  const remoteVideoRef = useRef<HTMLVideoElement>(null)
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null)
  const localStreamRef = useRef<MediaStream | null>(null)
  const chatEndRef = useRef<HTMLDivElement>(null)

  const showToast = (msg: string, tipo = 'verde') => {
    setToast({ msg, tipo })
    setTimeout(() => setToast(null), 3500)
  }

  // Carregar urgências do banco
  const carregarUrgencias = useCallback(async () => {
    const { data } = await supabase
      .from('urgencias')
      .select('*, perfis!urgencias_cliente_id_fkey(nome), profissional:perfis!urgencias_profissional_id_fkey(nome)')
      .or(`profissional_id.eq.${userId},cliente_id.eq.${userId}`)
      .in('status', ['aguardando', 'chamando', 'em_chamada'])
      .gte('slot_hora', new Date().toISOString().split('T')[0])

    if (data) {
      setUrgencias(data)

      // Atualizar slots com ocupados
      setSlots(prev => prev.map(slot => {
        const ocupado = data.find(u => {
          const slotHora = new Date(u.slot_hora).getTime()
          return Math.abs(slotHora - slot.timestamp) < 60000
        })
        if (ocupado) {
          return { ...slot, status: 'ocupado', urgenciaId: ocupado.id, clienteNome: (ocupado as any).perfis?.nome, clienteId: ocupado.cliente_id }
        }
        return slot
      }))

      // Meu agendamento (cliente)
      if (!isPro) {
        const meu = data.find(u => u.cliente_id === userId)
        setMeuAgendamento(meu || null)
        if (meu && (meu.status === 'chamando' || meu.status === 'em_chamada')) {
          setNotifChamada({ urgenciaId: meu.id, proNome: (meu as any).profissional?.nome || 'Profissional' })
        }
      }
    }

    // Histórico
    const { data: hist } = await supabase
      .from('urgencias')
      .select('*')
      .or(`profissional_id.eq.${userId},cliente_id.eq.${userId}`)
      .eq('status', 'concluida')
      .order('criado_em', { ascending: false })
      .limit(30)

    if (hist) {
      setHistorico(hist)
      const hoje = new Date().toISOString().split('T')[0]
      setTotalHoje(hist.filter(h => h.slot_hora?.startsWith(hoje)).length)
    }
  }, [userId, isPro])

  useEffect(() => {
    carregarUrgencias()

    // Realtime subscription
    const channel = supabase
      .channel(`urgencias_${userId}`)
      .on('postgres_changes', {
        event: '*',
        schema: 'public',
        table: 'urgencias',
      }, (payload) => {
        carregarUrgencias()

        // Notificar cliente quando chamado
        if (!isPro && payload.eventType === 'UPDATE') {
          const updated = payload.new as Urgencia
          if (updated.cliente_id === userId && updated.status === 'chamando') {
            // Som de notificação
            try {
              const ctx = new AudioContext()
              const osc = ctx.createOscillator()
              const gain = ctx.createGain()
              osc.connect(gain); gain.connect(ctx.destination)
              osc.frequency.value = 880
              gain.gain.setValueAtTime(0.08, ctx.currentTime)
              gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.5)
              osc.start(ctx.currentTime); osc.stop(ctx.currentTime + 0.5)
            } catch (e) {}
          }
        }
      })
      .subscribe()

    return () => { supabase.removeChannel(channel) }
  }, [carregarUrgencias, userId, isPro])

  // Agendar slot (cliente)
  async function agendarSlot(slot: Slot) {
    if (!userId) return showToast('Faça login para agendar.', 'verm')
    setLoading(true)
    try {
      const slotHora = new Date(slot.timestamp).toISOString()
      const { error } = await supabase.from('urgencias').insert({
        cliente_id: userId,
        profissional_id: '00000000-0000-0000-0000-000000000000', // será preenchido pelo profissional ativo
        slot_hora: slotHora,
        status: 'aguardando',
      })
      if (error) throw error
      showToast(`✅ Agendado para ${slot.hora}!`)
      carregarUrgencias()
    } catch {
      showToast('Erro ao agendar. Tente novamente.', 'verm')
    } finally {
      setLoading(false)
    }
  }

  // Chamar cliente (profissional)
  async function chamarCliente(urgencia: Urgencia) {
    setLoading(true)
    try {
      await supabase.from('urgencias').update({ status: 'chamando' }).eq('id', urgencia.id)
      setUrgenciaAtiva(urgencia)
      showToast(`📞 Chamando cliente...`)
      iniciarWebRTC(urgencia, true)
    } catch {
      showToast('Erro ao chamar cliente.', 'verm')
    } finally {
      setLoading(false)
    }
  }

  // Aceitar chamada (cliente)
  async function aceitarChamada() {
    if (!notifChamada) return
    const { data } = await supabase.from('urgencias').select('*').eq('id', notifChamada.urgenciaId).single()
    if (data) {
      setUrgenciaAtiva(data)
      setNotifChamada(null)
      iniciarWebRTC(data, false)
    }
  }

  // WebRTC
  async function iniciarWebRTC(urgencia: Urgencia, souPro: boolean) {
    const pc = new RTCPeerConnection({
      iceServers: [
        { urls: 'stun:stun.l.google.com:19302' },
        { urls: 'stun:stun1.l.google.com:19302' },
      ]
    })
    pcRef.current = pc

    // Mídia local
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ video: true, audio: true })
      localStreamRef.current = stream
      if (localVideoRef.current) localVideoRef.current.srcObject = stream
      stream.getTracks().forEach(track => pc.addTrack(track, stream))
    } catch {
      showToast('Câmera/microfone não disponível.', 'verm')
    }

    // Mídia remota
    pc.ontrack = (e) => {
      if (remoteVideoRef.current) remoteVideoRef.current.srcObject = e.streams[0]
    }

    // ICE candidates
    const iceCandidates: RTCIceCandidateInit[] = []
    pc.onicecandidate = async (e) => {
      if (e.candidate) {
        iceCandidates.push(e.candidate.toJSON())
        const field = souPro ? 'ice_candidates_pro' : 'ice_candidates_cli'
        await supabase.from('urgencias').update({ [field]: iceCandidates }).eq('id', urgencia.id)
      }
    }

    if (souPro) {
      // Criar offer
      const offer = await pc.createOffer()
      await pc.setLocalDescription(offer)
      await supabase.from('urgencias').update({ offer: offer, status: 'em_chamada' }).eq('id', urgencia.id)

      // Aguardar answer do cliente
       supabase.channel(`webrtc_${urgencia.id}`)
        .on('postgres_changes', { event: 'UPDATE', schema: 'public', table: 'urgencias', filter: `id=eq.${urgencia.id}` }, 
            async (payload) => {
            const upd = payload.new as Urgencia
            if (upd.answer && !pc.remoteDescription) {
              await pc.setRemoteDescription(upd.answer)
              // Adicionar ICE do cliente
              if (upd.ice_candidates_cli) {
                for (const c of upd.ice_candidates_cli) {
                  await pc.addIceCandidate(c).catch(() => {})
                }
              }
            }
          })
        .subscribe()
    } else {
      // Aguardar offer do profissional
      const { data: urgAtual } = await supabase.from('urgencias').select('*').eq('id', urgencia.id).single()
      if (urgAtual?.offer) {
        await pc.setRemoteDescription(urgAtual.offer)
        const answer = await pc.createAnswer()
        await pc.setLocalDescription(answer)
        await supabase.from('urgencias').update({ answer: answer }).eq('id', urgencia.id)

        if (urgAtual.ice_candidates_pro) {
          for (const c of urgAtual.ice_candidates_pro) {
            await pc.addIceCandidate(c).catch(() => {})
          }
        }
      }
    }

    // Iniciar timer
    setCallAtiva(true)
    setTempoRestante(CALL_DURATION)
    setTempoDecorrido(0)
    timerRef.current = setInterval(() => {
      setTempoDecorrido(prev => prev + 1)
      setTempoRestante(prev => {
        if (prev <= 1) { encerrarChamada(urgencia); return 0 }
        return prev - 1
      })
    }, 1000)
  }

  // Encerrar chamada
  async function encerrarChamada(urgencia?: Urgencia) {
    if (timerRef.current) clearInterval(timerRef.current)

    if (pcRef.current) { pcRef.current.close(); pcRef.current = null }
    if (localStreamRef.current) { localStreamRef.current.getTracks().forEach(t => t.stop()); localStreamRef.current = null }
    if (localVideoRef.current) localVideoRef.current.srcObject = null
    if (remoteVideoRef.current) remoteVideoRef.current.srcObject = null

    const alvo = urgencia || urgenciaAtiva
    if (alvo) {
      await supabase.from('urgencias').update({
        status: 'concluida',
        encerrada_em: new Date().toISOString(),
        duracao_segundos: tempoDecorrido,
      }).eq('id', alvo.id)
    }

    setCallAtiva(false)
    setUrgenciaAtiva(null)
    setTempoRestante(CALL_DURATION)
    setTempoDecorrido(0)
    setMensagens([])
    carregarUrgencias()
    showToast('✅ Chamada encerrada e registrada.', 'verde')
  }

  // Chat
  function enviarMensagem() {
    if (!msgInput.trim()) return
    const msg: MensagemChat = {
      id: Date.now().toString(),
      de: isPro ? 'profissional' : 'cliente',
      texto: msgInput.trim(),
      hora: new Date().toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' }),
    }
    setMensagens(prev => [...prev, msg])
    setMsgInput('')
    setTimeout(() => chatEndRef.current?.scrollIntoView({ behavior: 'smooth' }), 100)
  }

  const pct = tempoRestante / CALL_DURATION
  const circumference = 2 * Math.PI * 45
  const dashOffset = circumference * (1 - pct)
  const ringColor = pct > 0.5 ? '#009c3b' : pct > 0.2 ? '#ff6b35' : '#d32f2f'

  return (
    <div style={{
      display: 'flex', flexDirection: 'column', height: '100vh',
      background: '#060f1e', color: '#e8eef8',
      fontFamily: "'DM Sans', sans-serif",
    }}>
      {/* TOPBAR */}
      <div style={{
        height: 52, background: '#0a1628',
        borderBottom: '1px solid rgba(255,255,255,0.07)',
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        padding: '0 20px', flexShrink: 0,
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <button onClick={onVoltar} style={{
            background: 'none', border: 'none', color: '#8a99b0',
            cursor: 'pointer', fontSize: 13,
          }}>← Voltar</button>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <div style={{ width: 26, height: 26, background: '#009c3b', borderRadius: 5, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 13 }}>⚡</div>
            <span style={{ fontFamily: "'Syne', sans-serif", fontWeight: 700, fontSize: 13 }}>
              Brasil Tupi <span style={{ color: '#ffdf00' }}>Urgente</span>
            </span>
          </div>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <div style={{
            display: 'flex', alignItems: 'center', gap: 6,
            background: callAtiva ? 'rgba(0,156,59,0.1)' : 'rgba(138,153,176,0.1)',
            border: `1px solid ${callAtiva ? 'rgba(0,156,59,0.25)' : 'rgba(138,153,176,0.2)'}`,
            borderRadius: 20, padding: '3px 10px',
          }}>
            <div style={{
              width: 6, height: 6, borderRadius: '50%',
              background: callAtiva ? '#009c3b' : '#8a99b0',
            }} />
            <span style={{ fontSize: 10, fontWeight: 700, letterSpacing: '0.07em', color: callAtiva ? '#009c3b' : '#8a99b0' }}>
              {callAtiva ? 'EM CHAMADA' : 'AGUARDANDO'}
            </span>
          </div>

          {callAtiva && (
            <div style={{
              fontFamily: "'Syne', sans-serif", fontSize: 17, fontWeight: 800,
              color: tempoRestante <= CRIT_SEC ? '#d32f2f' : tempoRestante <= WARN_SEC ? '#ff6b35' : '#ffdf00',
              minWidth: 54, textAlign: 'center',
            }}>
              {fmtTime(tempoRestante)}
            </div>
          )}

          <span style={{
            fontSize: 9, fontWeight: 700, letterSpacing: '0.1em',
            padding: '3px 9px', borderRadius: 4,
            background: isPro ? 'rgba(255,223,0,0.13)' : 'rgba(0,156,59,0.13)',
            color: isPro ? '#ffdf00' : '#009c3b',
            border: `1px solid ${isPro ? 'rgba(255,223,0,0.25)' : 'rgba(0,156,59,0.25)'}`,
          }}>
            {isPro ? 'PROFISSIONAL' : 'CLIENTE'}
          </span>
        </div>
      </div>

      {/* BODY */}
      <div style={{ display: 'flex', flex: 1, overflow: 'hidden' }}>

        {/* ÁREA DE VÍDEO / CONTEÚDO PRINCIPAL */}
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column', background: '#000', position: 'relative' }}>

          {/* Vídeos */}
          <div style={{ flex: 1, position: 'relative', background: '#060f1e', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            {callAtiva ? (
              <>
                {/* Vídeo remoto (principal) */}
                <video ref={remoteVideoRef} autoPlay playsInline style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                {/* Vídeo local (miniatura) */}
                <div style={{ position: 'absolute', bottom: 16, right: 16, width: 160, height: 110, borderRadius: 10, overflow: 'hidden', border: '2px solid rgba(255,255,255,0.2)', boxShadow: '0 4px 20px rgba(0,0,0,0.5)' }}>
                  <video ref={localVideoRef} autoPlay playsInline muted style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                </div>
              </>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 20, textAlign: 'center', padding: 40 }}>
                <div style={{ fontSize: 64 }}>📡</div>
                <div>
                  <div style={{ fontFamily: "'Syne', sans-serif", fontSize: 18, fontWeight: 700, color: '#e8eef8' }}>
                    {isPro ? 'Sala pronta' : meuAgendamento ? 'Aguardando o profissional' : 'Agende uma consulta'}
                  </div>
                  <div style={{ fontSize: 13, color: '#8a99b0', marginTop: 8, maxWidth: 300, lineHeight: 1.6 }}>
                    {isPro
                      ? 'Selecione um cliente na aba Fila e clique em Chamar.'
                      : meuAgendamento
                        ? 'Você receberá um alerta sonoro assim que o profissional iniciar.'
                        : 'Escolha um horário disponível na lista à direita.'}
                  </div>
                </div>
              </div>
            )}
          </div>

          {/* Controles */}
          <div style={{
            height: 62, background: '#0a1628',
            borderTop: '1px solid rgba(255,255,255,0.07)',
            display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 10, padding: '0 16px',
          }}>
            {['🎙', '📷', '🖥'].map((icon, i) => (
              <button key={i} style={{
                width: 42, height: 42, borderRadius: '50%',
                border: '1px solid rgba(255,255,255,0.12)',
                background: '#0f1e36', color: '#e8eef8',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                cursor: 'pointer', fontSize: 16,
              }}>{icon}</button>
            ))}
            {callAtiva && (
              <button onClick={() => encerrarChamada()} style={{
                padding: '0 18px', height: 42, borderRadius: 21,
                background: '#d32f2f', border: 'none', color: '#fff',
                fontFamily: "'Syne', sans-serif", fontSize: 11, fontWeight: 700,
                cursor: 'pointer',
              }}>📵 ENCERRAR</button>
            )}
          </div>
        </div>

        {/* SIDEBAR */}
        <div style={{
          width: 300, background: '#0a1628',
          borderLeft: '1px solid rgba(255,255,255,0.07)',
          display: 'flex', flexDirection: 'column', overflow: 'hidden',
        }}>
          {/* Tabs */}
          <div style={{ display: 'flex', borderBottom: '1px solid rgba(255,255,255,0.07)', flexShrink: 0 }}>
            {[
              { id: 'fila', label: isPro ? 'Fila' : 'Slots' },
              { id: 'timer', label: 'Timer' },
              { id: 'chat', label: 'Chat' },
              { id: 'historico', label: 'Histórico' },
            ].map(t => (
              <button key={t.id} onClick={() => setAba(t.id as any)} style={{
                flex: 1, padding: '11px 4px',
                fontSize: 9, fontWeight: 700, letterSpacing: '0.08em', textTransform: 'uppercase',
                color: aba === t.id ? '#ffdf00' : '#8a99b0',
                background: 'none', border: 'none', cursor: 'pointer',
                borderBottom: `2px solid ${aba === t.id ? '#ffdf00' : 'transparent'}`,
              }}>{t.label}</button>
            ))}
          </div>

          {/* Painel Fila / Slots */}
          {aba === 'fila' && (
            <div style={{ flex: 1, overflowY: 'auto', padding: 14 }}>
              {isPro ? (
                <>
                  <div style={{ fontSize: 9, fontWeight: 700, letterSpacing: '0.1em', color: '#8a99b0', marginBottom: 10 }}>
                    FILA DE HOJE — {urgencias.length} agendamento(s)
                  </div>
                  {urgencias.length === 0 ? (
                    <div style={{ textAlign: 'center', padding: '30px 0', color: '#8a99b0', fontSize: 12, fontStyle: 'italic' }}>
                      Nenhum cliente na fila.
                    </div>
                  ) : urgencias.map(u => (
                    <div key={u.id} style={{
                      background: '#0f1e36', border: '1px solid rgba(255,255,255,0.07)',
                      borderRadius: 10, padding: '11px 12px', marginBottom: 8,
                      display: 'flex', alignItems: 'center', gap: 9,
                    }}>
                      <div style={{
                        width: 34, height: 34, borderRadius: '50%',
                        background: 'rgba(12,45,107,0.5)', color: '#ffdf00',
                        display: 'flex', alignItems: 'center', justifyContent: 'center',
                        fontFamily: "'Syne', sans-serif", fontSize: 12, fontWeight: 700, flexShrink: 0,
                      }}>
                        {((u as any).perfis?.nome || 'CL').split(' ').map((n: string) => n[0]).join('').slice(0, 2)}
                      </div>
                      <div style={{ flex: 1, minWidth: 0 }}>
                        <div style={{ fontSize: 12, fontWeight: 500, color: '#e8eef8' }}>
                          {(u as any).perfis?.nome || 'Cliente'}
                        </div>
                        <div style={{ fontSize: 10, color: '#8a99b0', marginTop: 1 }}>
                          {new Date(u.slot_hora).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })}
                        </div>
                      </div>
                      <button onClick={() => chamarCliente(u)} style={{
                        width: 28, height: 28, borderRadius: '50%',
                        background: '#009c3b', border: 'none', color: '#fff',
                        cursor: 'pointer', fontSize: 13,
                      }}>📞</button>
                    </div>
                  ))}
                </>
              ) : (
                <>
                  {meuAgendamento ? (
                    <div style={{ textAlign: 'center', padding: 20 }}>
                      <div style={{
                        background: '#009c3b', color: '#fff', fontSize: 10, fontWeight: 700,
                        letterSpacing: '0.1em', padding: '5px 14px', borderRadius: 6, display: 'inline-block', marginBottom: 14,
                      }}>CONSULTA CONFIRMADA</div>
                      <div style={{ fontFamily: "'Syne', sans-serif", fontSize: 22, fontWeight: 800, color: '#e8eef8', marginBottom: 8 }}>
                        SUA VEZ: <span style={{ color: '#ffdf00' }}>
                          {new Date(meuAgendamento.slot_hora).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })}
                        </span>
                      </div>
                      <div style={{ fontSize: 12, color: '#8a99b0', lineHeight: 1.6, marginBottom: 16 }}>
                        Permaneça nesta página. Você receberá um alerta sonoro quando o profissional iniciar a chamada.
                      </div>
                      <button onClick={() => encerrarChamada(meuAgendamento)} style={{
                        background: '#d32f2f', color: '#fff', border: 'none',
                        padding: '10px 20px', borderRadius: 30, fontSize: 11, fontWeight: 700,
                        fontFamily: "'Syne', sans-serif", cursor: 'pointer', width: '100%',
                      }}>Cancelar agendamento</button>
                    </div>
                  ) : (
                    <>
                      <div style={{ fontSize: 9, fontWeight: 700, letterSpacing: '0.1em', color: '#8a99b0', marginBottom: 10 }}>
                        HORÁRIOS DISPONÍVEIS
                      </div>
                      <div style={{
                        background: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,223,0,0.15)',
                        borderRadius: 10, padding: 10, marginBottom: 12, fontSize: 11, color: '#8a99b0', lineHeight: 1.6,
                      }}>
                        ⚡ Slots de 45min · Profissional tem até <strong style={{ color: '#ffdf00' }}>45min</strong> para iniciar · Chamada de <strong style={{ color: '#ffdf00' }}>15min</strong>
                      </div>
                      <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                        {slots.map((slot, i) => (
                          <button key={i} disabled={slot.status !== 'disponivel' || loading}
                            onClick={() => slot.status === 'disponivel' && agendarSlot(slot)}
                            style={{
                              display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                              padding: '12px 16px', borderRadius: 8, border: 'none',
                              background: slot.status === 'disponivel' ? '#009c3b' : slot.status === 'ocupado' ? '#d32f2f' : '#2a2a2a',
                              color: '#fff', cursor: slot.status === 'disponivel' ? 'pointer' : 'not-allowed',
                              opacity: slot.status === 'passado' ? 0.4 : 1,
                              fontFamily: "'Syne', sans-serif", fontWeight: 700,
                            }}>
                            <span style={{ fontSize: 15 }}>{slot.hora}</span>
                            <span style={{ fontSize: 9, letterSpacing: '0.08em' }}>
                              {slot.status === 'disponivel' ? 'DISPONÍVEL' : slot.status === 'ocupado' ? 'OCUPADO' : 'ENCERRADO'}
                            </span>
                          </button>
                        ))}
                      </div>
                    </>
                  )}
                </>
              )}
            </div>
          )}

          {/* Painel Timer */}
          {aba === 'timer' && (
            <div style={{ flex: 1, overflowY: 'auto', padding: 14 }}>
              <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 14, paddingTop: 10 }}>
                {/* Ring SVG */}
                <div style={{ position: 'relative', width: 120, height: 120 }}>
                  <svg width="120" height="120" style={{ transform: 'rotate(-90deg)' }}>
                    <circle cx="60" cy="60" r="45" fill="none" stroke="rgba(255,255,255,0.07)" strokeWidth="8" />
                    <circle cx="60" cy="60" r="45" fill="none" stroke={ringColor} strokeWidth="8"
                      strokeLinecap="round" strokeDasharray={circumference} strokeDashoffset={dashOffset}
                      style={{ transition: 'stroke-dashoffset 1s linear, stroke 1s ease' }} />
                  </svg>
                  <div style={{ position: 'absolute', inset: 0, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center' }}>
                    <div style={{ fontFamily: "'Syne', sans-serif", fontSize: 22, fontWeight: 800, color: '#e8eef8', lineHeight: 1 }}>
                      {fmtTime(tempoRestante)}
                    </div>
                    <div style={{ fontSize: 8, color: '#8a99b0', letterSpacing: '0.08em', marginTop: 3 }}>RESTANTE</div>
                  </div>
                </div>

                <div style={{ display: 'flex', gap: 10, width: '100%' }}>
                  {[
                    { num: fmtTime(tempoDecorrido), label: 'Decorrido' },
                    { num: String(totalHoje), label: 'Hoje' },
                  ].map(item => (
                    <div key={item.label} style={{
                      flex: 1, background: '#0f1e36', border: '1px solid rgba(255,255,255,0.07)',
                      borderRadius: 8, padding: 10, textAlign: 'center',
                    }}>
                      <div style={{ fontFamily: "'Syne', sans-serif", fontSize: 18, fontWeight: 800, color: '#ffdf00' }}>{item.num}</div>
                      <div style={{ fontSize: 8, color: '#8a99b0', textTransform: 'uppercase', letterSpacing: '0.06em', marginTop: 3 }}>{item.label}</div>
                    </div>
                  ))}
                </div>

                <div style={{ width: '100%', background: '#0f1e36', border: '1px solid rgba(255,255,255,0.07)', borderRadius: 10, padding: 16 }}>
                  <div style={{ fontSize: 10, fontWeight: 700, color: '#8a99b0', marginBottom: 10, letterSpacing: '0.08em' }}>REGRAS DO ATENDIMENTO</div>
                  {[
                    ['⏱', '45 minutos', 'Tempo máximo para iniciar após o agendamento'],
                    ['📋', '15 minutos', 'Duração máxima da consulta urgente'],
                    ['⚠️', 'Emergências', 'Crises, ataques, urgências reais de trabalho'],
                    ['🚫', 'Não é aula', 'Sem cursos, aulas ou conteúdo extenso'],
                  ].map(([icon, titulo, desc]) => (
                    <div key={titulo} style={{ display: 'flex', gap: 10, marginBottom: 10, alignItems: 'flex-start' }}>
                      <span style={{ fontSize: 16, flexShrink: 0 }}>{icon}</span>
                      <div>
                        <div style={{ fontSize: 12, fontWeight: 700, color: '#e8eef8' }}>{titulo}</div>
                        <div style={{ fontSize: 11, color: '#8a99b0', lineHeight: 1.5 }}>{desc}</div>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          )}

          {/* Painel Chat */}
          {aba === 'chat' && (
            <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
              <div style={{ flex: 1, overflowY: 'auto', padding: 12, display: 'flex', flexDirection: 'column', gap: 8 }}>
                {mensagens.length === 0 ? (
                  <div style={{ textAlign: 'center', color: '#8a99b0', fontSize: 12, fontStyle: 'italic', paddingTop: 40 }}>
                    {callAtiva ? 'Nenhuma mensagem ainda.' : 'O chat fica disponível durante a chamada.'}
                  </div>
                ) : mensagens.map(m => (
                  <div key={m.id} style={{
                    alignSelf: m.de === (isPro ? 'profissional' : 'cliente') ? 'flex-end' : 'flex-start',
                    maxWidth: '80%',
                  }}>
                    <div style={{
                      background: m.de === (isPro ? 'profissional' : 'cliente') ? '#009c3b' : '#0f1e36',
                      border: '1px solid rgba(255,255,255,0.07)',
                      borderRadius: 10, padding: '8px 12px',
                      fontSize: 13, color: '#e8eef8', lineHeight: 1.5,
                    }}>{m.texto}</div>
                    <div style={{ fontSize: 10, color: '#8a99b0', marginTop: 3, textAlign: m.de === (isPro ? 'profissional' : 'cliente') ? 'right' : 'left' }}>
                      {m.hora}
                    </div>
                  </div>
                ))}
                <div ref={chatEndRef} />
              </div>
              <div style={{ padding: 10, borderTop: '1px solid rgba(255,255,255,0.07)', display: 'flex', gap: 8 }}>
                <input value={msgInput} onChange={e => setMsgInput(e.target.value)}
                  onKeyDown={e => e.key === 'Enter' && enviarMensagem()}
                  disabled={!callAtiva}
                  placeholder={callAtiva ? 'Digite uma mensagem...' : 'Aguarde a chamada iniciar'}
                  style={{
                    flex: 1, background: '#0f1e36', border: '1px solid rgba(255,255,255,0.1)',
                    borderRadius: 8, padding: '8px 12px', color: '#e8eef8', fontSize: 13,
                    outline: 'none',
                  }} />
                <button onClick={enviarMensagem} disabled={!callAtiva} style={{
                  background: '#009c3b', border: 'none', borderRadius: 8, padding: '8px 12px',
                  color: '#fff', cursor: 'pointer', fontSize: 16,
                }}>→</button>
              </div>
            </div>
          )}

          {/* Painel Histórico */}
          {aba === 'historico' && (
            <div style={{ flex: 1, overflowY: 'auto', padding: 14 }}>
              <div style={{ fontSize: 9, fontWeight: 700, letterSpacing: '0.1em', color: '#8a99b0', marginBottom: 10 }}>
                CHAMADAS CONCLUÍDAS
              </div>
              {historico.length === 0 ? (
                <div style={{ textAlign: 'center', padding: '30px 0', color: '#8a99b0', fontSize: 12, fontStyle: 'italic' }}>
                  Nenhuma chamada registrada.
                </div>
              ) : historico.map(h => (
                <div key={h.id} style={{
                  background: '#0f1e36', border: '1px solid rgba(255,255,255,0.07)',
                  borderRadius: 8, padding: '10px 12px', marginBottom: 8,
                  display: 'flex', alignItems: 'center', gap: 9,
                }}>
                  <div style={{
                    width: 26, height: 26, borderRadius: '50%',
                    background: 'rgba(0,156,59,0.15)',
                    display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 12, flexShrink: 0,
                  }}>✅</div>
                  <div style={{ flex: 1 }}>
                    <div style={{ fontSize: 12, fontWeight: 500, color: '#e8eef8' }}>
                      {new Date(h.slot_hora).toLocaleDateString('pt-BR')}
                    </div>
                    <div style={{ fontSize: 10, color: '#8a99b0', marginTop: 1 }}>
                      {new Date(h.slot_hora).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })}
                    </div>
                  </div>
                  <div style={{ fontSize: 10, fontWeight: 700, color: '#ffdf00' }}>
                    {h.duracao_segundos ? fmtTime(h.duracao_segundos) : '—'}
                  </div>
                </div>
              ))}
            </div>
          )}

          {/* Footer profissional */}
          {isPro && callAtiva && (
            <div style={{ padding: 12, borderTop: '1px solid rgba(255,255,255,0.07)', flexShrink: 0 }}>
              <button onClick={() => encerrarChamada()} style={{
                width: '100%', background: '#d32f2f', color: '#fff', border: 'none',
                padding: 10, borderRadius: 8,
                fontFamily: "'Syne', sans-serif", fontSize: 10, fontWeight: 700,
                letterSpacing: '0.06em', cursor: 'pointer',
              }}>✔ FINALIZAR MISSÃO</button>
            </div>
          )}
        </div>
      </div>

      {/* NOTIFICAÇÃO DE CHAMADA (cliente) */}
      {notifChamada && (
        <div style={{
          position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.88)',
          backdropFilter: 'blur(8px)', zIndex: 9999,
          display: 'flex', alignItems: 'center', justifyContent: 'center',
        }}>
          <div style={{
            background: '#0a1628', border: '1px solid #009c3b',
            borderRadius: 20, padding: '34px 30px', maxWidth: 390, width: '90%',
            textAlign: 'center', boxShadow: '0 0 40px rgba(0,156,59,0.3)',
          }}>
            <div style={{ fontSize: 52, marginBottom: 14 }}>📞</div>
            <div style={{ fontFamily: "'Syne', sans-serif", fontSize: 20, fontWeight: 800, color: '#e8eef8', marginBottom: 6 }}>
              Profissional chamando!
            </div>
            <div style={{ fontSize: 15, fontWeight: 500, color: '#009c3b', marginBottom: 10 }}>
              {notifChamada.proNome}
            </div>
            <div style={{ fontSize: 13, color: '#8a99b0', lineHeight: 1.6, marginBottom: 22 }}>
              Sua consulta urgente está prestes a iniciar.<br />
              A chamada será encerrada automaticamente em <strong>15 minutos</strong>.
            </div>
            <div style={{ display: 'flex', gap: 10 }}>
              <button onClick={aceitarChamada} style={{
                flex: 2, background: '#009c3b', color: '#fff', border: 'none',
                padding: 12, borderRadius: 10,
                fontFamily: "'Syne', sans-serif", fontSize: 13, fontWeight: 700, cursor: 'pointer',
              }}>▶ Entrar na chamada</button>
              <button onClick={() => setNotifChamada(null)} style={{
                flex: 1, background: 'rgba(255,255,255,0.05)', color: '#8a99b0',
                border: '1px solid rgba(255,255,255,0.1)',
                padding: 12, borderRadius: 10, fontSize: 12, cursor: 'pointer',
              }}>Agora não</button>
            </div>
          </div>
        </div>
      )}

      {/* TOAST */}
      {toast && (
        <div style={{
          position: 'fixed', bottom: 24, left: '50%', transform: 'translateX(-50%)',
          background: '#0a1628',
          border: `1px solid ${toast.tipo === 'verde' ? '#009c3b' : toast.tipo === 'verm' ? '#d32f2f' : '#c49a2a'}`,
          color: toast.tipo === 'verde' ? '#009c3b' : toast.tipo === 'verm' ? '#d32f2f' : '#ffdf00',
          padding: '10px 22px', borderRadius: 8, fontSize: 13, fontWeight: 600,
          zIndex: 99999, boxShadow: '0 4px 20px rgba(0,0,0,0.4)', pointerEvents: 'none',
        }}>
          {toast.msg}
        </div>
      )}
    </div>
  )
}