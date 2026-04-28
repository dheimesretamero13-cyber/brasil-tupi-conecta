package br.com.brasiltupi.conecta

// ═══════════════════════════════════════════════════════════════════════════
// TermosUsoScreen.kt + PoliticaPrivacidadeScreen.kt
//
// Telas de leitura dos documentos legais.
// Acessadas pelos links azuis na LegalOnboardingScreen.
// Também acessíveis pelo menu de perfil (Fase futura).
// ═══════════════════════════════════════════════════════════════════════════

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.brasiltupi.conecta.ui.theme.*

// ═══════════════════════════════════════════════════════════════════════════
// TERMOS DE USO
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun TermosUsoScreen(onVoltar: () -> Unit) {
    DocumentoLegalScreen(
        titulo    = "Termos de Uso",
        subtitulo = "Última atualização: abril de 2025 — Versão 1.0",
        onVoltar  = onVoltar,
        secoes    = listOf(

            SecaoDoc(
                titulo = "1. Sobre a Plataforma",
                corpo  = "O Brasil Tupi Conecta é uma plataforma de intermediação que conecta clientes a profissionais especializados para realização de consultas online por videochamada. A plataforma não presta serviços profissionais diretamente — somos um canal de conexão entre as partes.",
            ),

            SecaoDoc(
                titulo = "2. Cadastro e Conta",
                corpo  = "Para usar a plataforma, você deve fornecer informações verídicas durante o cadastro. É sua responsabilidade manter a senha segura e não compartilhar o acesso com terceiros. Você deve ter pelo menos 18 anos de idade.",
            ),

            SecaoDoc(
                titulo = "3. Responsabilidades do Profissional",
                corpo  = "Profissionais cadastrados declaram que possuem habilitação legal para exercer a atividade informada. O profissional é o único responsável pelo conteúdo e qualidade do atendimento prestado. A plataforma poderá solicitar comprovação de habilitação a qualquer momento.",
            ),

            SecaoDoc(
                titulo = "4. Responsabilidades do Cliente",
                corpo  = "O cliente reconhece que as consultas realizadas pela plataforma têm caráter informativo e de orientação, não substituindo consultas presenciais ou tratamentos médicos quando necessário. O cliente é responsável pelas informações fornecidas ao profissional.",
            ),

            SecaoDoc(
                titulo = "5. Pagamentos e Reembolsos",
                corpo  = "Os pagamentos são processados pelo Mercado Pago. O valor da consulta é cobrado após a avaliação. Cancelamentos realizados até 2 horas antes têm direito a reembolso total. Cancelamentos com menos de 2 horas não geram reembolso. Cancelamentos pelo profissional geram reembolso total ao cliente.",
            ),

            SecaoDoc(
                titulo = "6. Videochamadas",
                corpo  = "As videochamadas têm duração padrão de 15 minutos. Extensões de tempo são cobradas por minuto adicional, mediante autorização prévia do profissional. As chamadas não são gravadas pela plataforma.",
            ),

            SecaoDoc(
                titulo = "7. Conduta Proibida",
                corpo  = "É proibido usar a plataforma para fins ilegais, assediar outros usuários, fornecer informações falsas, tentar burlar o sistema de pagamentos, ou compartilhar conteúdo ofensivo. Violações podem resultar em suspensão permanente da conta.",
            ),

            SecaoDoc(
                titulo = "8. Limitação de Responsabilidade",
                corpo  = "A plataforma não se responsabiliza por danos decorrentes do uso dos serviços prestados pelos profissionais, por falhas de conexão fora de nosso controle, ou por decisões tomadas com base nas orientações recebidas.",
            ),

            SecaoDoc(
                titulo = "9. Alterações dos Termos",
                corpo  = "Podemos atualizar estes Termos periodicamente. Você será notificado e solicitado a aceitar os novos termos ao acessar a plataforma. O uso continuado após as alterações implica aceitação.",
            ),

            SecaoDoc(
                titulo = "10. Foro",
                corpo  = "Fica eleito o foro da comarca de São Paulo/SP para dirimir quaisquer disputas decorrentes destes Termos, com renúncia a qualquer outro, por mais privilegiado que seja.",
            ),
        ),
    )
}

// ═══════════════════════════════════════════════════════════════════════════
// POLÍTICA DE PRIVACIDADE
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun PoliticaPrivacidadeScreen(onVoltar: () -> Unit) {
    DocumentoLegalScreen(
        titulo    = "Política de Privacidade",
        subtitulo = "Última atualização: abril de 2025 — Versão 1.0",
        onVoltar  = onVoltar,
        secoes    = listOf(

            SecaoDoc(
                titulo = "1. Controlador dos Dados",
                corpo  = "O Brasil Tupi Conecta é o controlador dos dados pessoais coletados nesta plataforma, nos termos da Lei Geral de Proteção de Dados (Lei 13.709/2018 — LGPD).",
            ),

            SecaoDoc(
                titulo = "2. Dados que Coletamos",
                corpo  = "Coletamos: nome completo, e-mail, foto de perfil e número de telefone para identificação. Histórico de consultas para emissão de comprovantes. Documentos profissionais (CRM, CRP, OAB, etc.) para verificação de habilitação. Dados de uso da plataforma para melhoria do serviço. Não armazenamos dados de cartão de crédito — esses dados são processados diretamente pelo Mercado Pago.",
            ),

            SecaoDoc(
                titulo = "3. Como Usamos Seus Dados",
                corpo  = "Seus dados são usados para: operar e melhorar a plataforma, conectar clientes a profissionais, processar pagamentos e emitir comprovantes, enviar notificações sobre suas consultas, cumprir obrigações legais e regulatórias, e prevenir fraudes.",
            ),

            SecaoDoc(
                titulo = "4. Compartilhamento de Dados",
                corpo  = "Não vendemos seus dados a terceiros. Compartilhamos dados apenas com: Mercado Pago (processamento de pagamentos), Stream (infraestrutura de videochamadas), Firebase (notificações e monitoramento de erros), e autoridades competentes mediante ordem judicial.",
            ),

            SecaoDoc(
                titulo = "5. Retenção de Dados",
                corpo  = "Mantemos seus dados enquanto sua conta estiver ativa. Após a exclusão da conta, os dados são removidos em até 30 dias, exceto dados de transações financeiras que são mantidos por 5 anos conforme exigência fiscal.",
            ),

            SecaoDoc(
                titulo = "6. Seus Direitos (LGPD)",
                corpo  = "Você tem direito a: acessar todos os seus dados que mantemos, corrigir dados incorretos ou desatualizados, solicitar a exclusão dos seus dados, revogar o consentimento a qualquer momento, solicitar portabilidade dos seus dados em formato legível, e ser informado sobre o uso dos seus dados. Para exercer esses direitos, acesse Configurações > Minha Conta > Privacidade.",
            ),

            SecaoDoc(
                titulo = "7. Segurança",
                corpo  = "Adotamos medidas técnicas e organizacionais para proteger seus dados: criptografia em trânsito (HTTPS/TLS), armazenamento seguro no Supabase com criptografia em repouso, controle de acesso por autenticação, e monitoramento contínuo de acessos.",
            ),

            SecaoDoc(
                titulo = "8. Cookies e Rastreamento",
                corpo  = "O aplicativo não usa cookies. Usamos o Firebase Analytics para coletar dados de uso de forma agregada e anônima, para melhorar a experiência do usuário. Você pode desativar a coleta de analytics nas configurações do aplicativo.",
            ),

            SecaoDoc(
                titulo = "9. Menores de Idade",
                corpo  = "Nossa plataforma é destinada a maiores de 18 anos. Não coletamos intencionalmente dados de menores de idade. Se identificarmos que uma conta pertence a um menor, ela será imediatamente encerrada.",
            ),

            SecaoDoc(
                titulo = "10. Contato",
                corpo  = "Para dúvidas sobre privacidade ou para exercer seus direitos, entre em contato pelo e-mail: privacidade@brasiltupi.com.br. Responderemos em até 15 dias úteis.",
            ),
        ),
    )
}

// ═══════════════════════════════════════════════════════════════════════════
// COMPONENTE BASE — reutilizado pelas duas telas
// ═══════════════════════════════════════════════════════════════════════════

private data class SecaoDoc(val titulo: String, val corpo: String)

@Composable
private fun DocumentoLegalScreen(
    titulo:    String,
    subtitulo: String,
    onVoltar:  () -> Unit,
    secoes:    List<SecaoDoc>,
) {
    Scaffold(
        containerColor = SurfaceWarm,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Surface)
            ) {
                Row(
                    modifier          = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text     = "←",
                        fontSize = 20.sp,
                        modifier = Modifier
                            .clickable { onVoltar() }
                            .padding(end = 12.dp),
                        color    = Ink,
                    )
                    Column {
                        Text(
                            text       = titulo,
                            fontSize   = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color      = Ink,
                        )
                        Text(
                            text     = subtitulo,
                            fontSize = 11.sp,
                            color    = InkMuted,
                        )
                    }
                }
                HorizontalDivider(color = SurfaceOff)
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            secoes.forEach { secao ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(10.dp),
                    colors   = CardDefaults.cardColors(containerColor = Surface),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text       = secao.titulo,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 14.sp,
                            color      = Ink,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text       = secao.corpo,
                            fontSize   = 13.sp,
                            color      = InkMuted,
                            lineHeight = 20.sp,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}