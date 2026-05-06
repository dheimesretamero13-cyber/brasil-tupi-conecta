package br.com.brasiltupi.conecta

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.brasiltupi.conecta.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun OnboardingProfissionalScreen(
    onConcluido: () -> Unit,
    onPular: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    var bio            by remember { mutableStateOf("") }
    var area           by remember { mutableStateOf("") }
    var conselho       by remember { mutableStateOf("") }
    var numeroConselho by remember { mutableStateOf("") }
    var precoNormal    by remember { mutableStateOf("") }
    var precoUrgente   by remember { mutableStateOf("") }

    var loading        by remember { mutableStateOf(false) }
    var erro           by remember { mutableStateOf("") }
    var erroArea       by remember { mutableStateOf("") }
    var erroPreco      by remember { mutableStateOf("") }

    fun validar(): Boolean {
        erroArea  = if (area.isBlank()) "Informe sua área de atuação" else ""
        erroPreco = if (precoNormal.isBlank() || (precoNormal.toIntOrNull() ?: 0) <= 0) "Informe um valor válido" else ""
        return erroArea.isEmpty() && erroPreco.isEmpty()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceWarm)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(52.dp))

        Text("Brasil Tupi", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Azul)
        Text("Conecta",    fontSize = 22.sp, fontWeight = FontWeight.Black, color = Verde)

        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .size(64.dp)
                .background(VerdeClaro, RoundedCornerShape(50)),
            contentAlignment = Alignment.Center
        ) { Text("👨‍⚕️", fontSize = 28.sp) }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            "Complete seu perfil profissional",
            fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink,
            textAlign = TextAlign.Center
        )
        Text(
            "Essas informações serão exibidas para clientes ao buscar profissionais na plataforma.",
            fontSize = 13.sp, color = InkMuted, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp, bottom = 28.dp)
        )

        // Área de atuação
        OnboardingCampo(
            label       = "Área de atuação *",
            valor       = area,
            onChange    = { area = it; erroArea = "" },
            placeholder = "Ex: Psicologia, Advocacia, Nutrição...",
            erro        = erroArea,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Conselho profissional
        OnboardingCampo(
            label       = "Conselho profissional",
            valor       = conselho,
            onChange    = { conselho = it },
            placeholder = "Ex: CRP, OAB, CRN...",
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Número do registro
        OnboardingCampo(
            label       = "Número do registro",
            valor       = numeroConselho,
            onChange    = { numeroConselho = it },
            placeholder = "Ex: CRP 06/123456",
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Biografia
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("Biografia / Apresentação", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value         = bio,
                onValueChange = { if (it.length <= 400) bio = it },
                modifier      = Modifier.fillMaxWidth().height(116.dp),
                placeholder   = { Text("Sua experiência, especialidades e diferenciais...", color = InkMuted, fontSize = 13.sp) },
                shape         = RoundedCornerShape(8.dp),
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = Verde,
                    unfocusedBorderColor = Color(0xFFE0E0E0)
                ),
                maxLines = 5,
            )
            Text(
                "${bio.length}/400", fontSize = 11.sp, color = InkMuted,
                modifier = Modifier.align(Alignment.End).padding(top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Preços lado a lado
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Consulta normal (R$) *", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value         = precoNormal,
                    onValueChange = { precoNormal = it.filter(Char::isDigit); erroPreco = "" },
                    modifier      = Modifier.fillMaxWidth(),
                    placeholder   = { Text("80", color = InkMuted) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError       = erroPreco.isNotEmpty(),
                    shape         = RoundedCornerShape(8.dp),
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = Verde,
                        unfocusedBorderColor = Color(0xFFE0E0E0),
                        errorBorderColor     = Urgente
                    ),
                    singleLine = true,
                    prefix = { Text("R$ ", color = InkMuted, fontSize = 13.sp) }
                )
                if (erroPreco.isNotEmpty()) {
                    Text(erroPreco, color = Urgente, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Consulta urgente (R$)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value         = precoUrgente,
                    onValueChange = { precoUrgente = it.filter(Char::isDigit) },
                    modifier      = Modifier.fillMaxWidth(),
                    placeholder   = { Text("120", color = InkMuted) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape         = RoundedCornerShape(8.dp),
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = Verde,
                        unfocusedBorderColor = Color(0xFFE0E0E0)
                    ),
                    singleLine = true,
                    prefix = { Text("R$ ", color = InkMuted, fontSize = 13.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Erro geral
        if (erro.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFDE8E8), RoundedCornerShape(8.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(erro, color = Urgente, fontSize = 13.sp)
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Botão salvar
        Button(
            onClick = {
                if (!validar()) return@Button
                loading = true
                erro    = ""
                scope.launch {
                    val uid = currentUserId ?: run {
                        erro    = "Sessão expirada. Faça login novamente."
                        loading = false
                        return@launch
                    }
                    val ok = atualizarPerfilProfissional(
                        userId         = uid,
                        bio            = bio.trim(),
                        area           = area.trim(),
                        conselho       = conselho.trim(),
                        numeroConselho = numeroConselho.trim(),
                        precoNormal    = precoNormal.toIntOrNull() ?: 80,
                        precoUrgente   = precoUrgente.toIntOrNull() ?: 0,
                    )
                    loading = false
                    if (ok) onConcluido()
                    else erro = "Erro ao salvar. Verifique sua conexão e tente novamente."
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = Verde, contentColor = Color.White),
            shape    = RoundedCornerShape(10.dp),
            enabled  = !loading
        ) {
            if (loading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text("Salvar e acessar dashboard", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(onClick = onPular, modifier = Modifier.fillMaxWidth()) {
            Text("Pular por agora", color = InkMuted, fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(36.dp))
    }
}

@Composable
private fun OnboardingCampo(
    label: String,
    valor: String,
    onChange: (String) -> Unit,
    placeholder: String,
    erro: String = "",
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value         = valor,
            onValueChange = onChange,
            modifier      = Modifier.fillMaxWidth(),
            placeholder   = { Text(placeholder, color = InkMuted, fontSize = 13.sp) },
            isError       = erro.isNotEmpty(),
            shape         = RoundedCornerShape(8.dp),
            colors        = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = Verde,
                unfocusedBorderColor = Color(0xFFE0E0E0),
                errorBorderColor     = Urgente
            ),
            singleLine = true
        )
        if (erro.isNotEmpty()) {
            Text(erro, color = Urgente, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}