package br.com.brasiltupi.conecta

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.brasiltupi.conecta.ui.theme.*
import kotlinx.coroutines.launch

// ── DADOS DOS PLANOS ──────────────────────────────────
// Sincronizado com a tabela `planos` no Supabase:
// Básico: grátis, 1 prof/mês, taxa 15%
// Bronze: R$29,90, 5 profs/mês, taxa 10%
// Ouro:   R$49,90, ilimitado, taxa 8%

data class PlanoInfo(
    val id: String,           // chave usada na tabela planos
    val nome: String,
    val preco: String,
    val precoDecimal: Double, // valor numérico para registro
    val descricao: String,
    val beneficios: List<String>,
    val destaque: Boolean = false,
)

val planosDisponiveis = listOf(
    PlanoInfo(
        id           = "bronze",
        nome         = "Plano Bronze",
        preco        = "R$ 29,90/mês",
        precoDecimal = 29.90,
        descricao    = "Ideal para consultas regulares",
        beneficios   = listOf("Até 5 profissionais/mês", "Taxa reduzida de 10%", "Acesso ao chat", "Suporte prioritário"),
    ),
    PlanoInfo(
        id           = "ouro",
        nome         = "Plano Ouro",
        preco        = "R$ 49,90/mês",
        precoDecimal = 49.90,
        descricao    = "Para quem usa com frequência",
        beneficios   = listOf("Profissionais ilimitados/mês", "Taxa de apenas 8%", "Acesso ao chat", "Suporte VIP", "Acesso ao Estúdio"),
        destaque     = true,
    ),
)

// ── ESTADOS DA TELA ───────────────────────────────────
private sealed class EstadoPagamento {
    object Selecionando : EstadoPagamento()
    data class Processando(val plano: PlanoInfo) : EstadoPagamento()
    data class Sucesso(val plano: PlanoInfo) : EstadoPagamento()
    data class Erro(val mensagem: String) : EstadoPagamento()
}

// ── TELA PRINCIPAL ────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PagamentoScreen(
    onVoltar: () -> Unit,
    onConcluido: () -> Unit,
) {
    var estado by remember { mutableStateOf<EstadoPagamento>(EstadoPagamento.Selecionando) }
    val scope  = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Planos & Assinatura", style = MaterialTheme.typography.titleLarge)
                },
                navigationIcon = {
                    TextButton(onClick = onVoltar) {
                        Text("← Voltar", fontSize = 14.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { innerPadding ->
        when (val s = estado) {
            is EstadoPagamento.Selecionando -> {
                TelaSelecionarPlano(
                    modifier = Modifier.padding(innerPadding),
                    onAssinar = { plano ->
                        estado = EstadoPagamento.Processando(plano)
                        scope.launch {
                            val ok = criarAssinaturaAndroid(plano)
                            estado = if (ok) EstadoPagamento.Sucesso(plano)
                            else EstadoPagamento.Erro("Não foi possível processar sua assinatura. Tente novamente.")
                        }
                    }
                )
            }
            is EstadoPagamento.Processando -> {
                TelaProcessando(modifier = Modifier.padding(innerPadding), plano = s.plano)
            }
            is EstadoPagamento.Sucesso -> {
                TelaSucesso(
                    modifier  = Modifier.padding(innerPadding),
                    plano     = s.plano,
                    onContinuar = onConcluido,
                )
            }
            is EstadoPagamento.Erro -> {
                TelaErro(
                    modifier = Modifier.padding(innerPadding),
                    mensagem = s.mensagem,
                    onTentar = { estado = EstadoPagamento.Selecionando },
                    onVoltar = onVoltar,
                )
            }
        }
    }
}

// ── SELEÇÃO DE PLANO ──────────────────────────────────
@Composable
private fun TelaSelecionarPlano(
    modifier: Modifier = Modifier,
    onAssinar: (PlanoInfo) -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Escolha seu plano", style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp))
        Text("Cancele a qualquer momento. Sem fidelidade.",
            fontSize = 13.sp, color = InkMuted, modifier = Modifier.padding(bottom = 8.dp))

        planosDisponiveis.forEach { plano ->
            PlanoCard(plano = plano, onAssinar = { onAssinar(plano) })
        }

        // Aviso: MercadoPago pendente
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFFFF8E1), RoundedCornerShape(10.dp))
                .padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text("⚠️", fontSize = 14.sp)
            Spacer(modifier = Modifier.width(10.dp))
            Text("Integração de pagamento em implementação. Ao assinar, sua conta será ativada para testes.",
                fontSize = 12.sp, color = Color(0xFF795548), lineHeight = 17.sp)
        }
    }
}

@Composable
private fun PlanoCard(plano: PlanoInfo, onAssinar: () -> Unit) {
    val borderColor = if (plano.destaque) Dourado else SurfaceOff
    val bgColor     = if (plano.destaque) Color(0xFFFDF8F0) else Color.White

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (plano.destaque) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .background(bgColor, RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            if (plano.destaque) {
                Box(
                    modifier = Modifier
                        .background(Dourado, RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Text("⭐ Mais popular", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
            Text(plano.nome, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text(plano.preco, fontSize = 22.sp, fontWeight = FontWeight.Black,
                color = if (plano.destaque) Dourado else Verde,
                modifier = Modifier.padding(top = 4.dp))
            Text(plano.descricao, fontSize = 13.sp, color = InkMuted, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))

            plano.beneficios.forEach { beneficio ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                    Text("✓", fontSize = 13.sp, color = Verde, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(beneficio, fontSize = 13.sp, color = Ink)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onAssinar,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (plano.destaque) Dourado else Verde,
                    contentColor = Color.White,
                ),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("Assinar ${plano.nome}", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── PROCESSANDO ───────────────────────────────────────
@Composable
private fun TelaProcessando(modifier: Modifier = Modifier, plano: PlanoInfo) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            CircularProgressIndicator(color = Verde, modifier = Modifier.size(48.dp))
            Text("Ativando ${plano.nome}...", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Text("Aguarde um momento", fontSize = 13.sp, color = InkMuted)
        }
    }
}

// ── SUCESSO ───────────────────────────────────────────
@Composable
private fun TelaSucesso(
    modifier: Modifier = Modifier,
    plano: PlanoInfo,
    onContinuar: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Spacer(modifier = Modifier.height(40.dp))
        Box(
            modifier = Modifier.size(80.dp).background(VerdeClaro, RoundedCornerShape(50)),
            contentAlignment = Alignment.Center
        ) {
            Text("✓", fontSize = 36.sp, color = Verde, fontWeight = FontWeight.Bold)
        }
        Text("Assinatura ativada!", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink, textAlign = TextAlign.Center)
        Text("Você agora tem acesso ao ${plano.nome}.", fontSize = 15.sp, color = InkSoft, textAlign = TextAlign.Center)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(VerdeClaro, RoundedCornerShape(12.dp))
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                plano.beneficios.forEach { beneficio ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("✓", fontSize = 13.sp, color = Verde, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(beneficio, fontSize = 13.sp, color = Ink)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onContinuar,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Verde, contentColor = Color.White),
            shape = RoundedCornerShape(10.dp),
        ) {
            Text("Continuar", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ── ERRO ─────────────────────────────────────────────
@Composable
private fun TelaErro(
    modifier: Modifier = Modifier,
    mensagem: String,
    onTentar: () -> Unit,
    onVoltar: () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("⚠️", fontSize = 48.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Algo deu errado", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(modifier = Modifier.height(8.dp))
        Text(mensagem, fontSize = 14.sp, color = InkMuted, textAlign = TextAlign.Center, lineHeight = 20.sp)
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onTentar,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Verde, contentColor = Color.White),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text("Tentar novamente", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(onClick = onVoltar) {
            Text("Voltar", color = InkMuted, fontSize = 14.sp)
        }
    }
}