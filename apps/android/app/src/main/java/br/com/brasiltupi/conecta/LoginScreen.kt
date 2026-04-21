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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.brasiltupi.conecta.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    onVoltar: () -> Unit,
    onEntrarProfissional: (String) -> Unit,
    onEntrarCliente: (String) -> Unit,
    onCadastro: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var tipoConta by remember { mutableStateOf("profissional") }
    var email by remember { mutableStateOf("") }
    var senha by remember { mutableStateOf("") }
    var mostrarSenha by remember { mutableStateOf(false) }
    var emailError by remember { mutableStateOf("") }
    var senhaError by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var erroGeral by remember { mutableStateOf("") }
    var mostrarModalRecover by remember { mutableStateOf(false) }
    var emailRecover by remember { mutableStateOf("") }
    var recoverMsg by remember { mutableStateOf("") }

    fun validar(): Boolean {
        emailError = if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) "E-mail inválido" else ""
        senhaError = if (senha.length < 6) "Mínimo 6 caracteres" else ""
        return emailError.isEmpty() && senhaError.isEmpty()
    }

    fun handleLogin() {
        if (!validar()) return
        loading = true
        erroGeral = ""
        scope.launch {
            try {
                val perfil = signInAndroid(email, senha)
                loading = false
                val uid = currentUserId ?: ""
                if (perfil != null) {
                    val isProfissional = perfil.tipo == "profissional_certificado" ||
                            perfil.tipo == "profissional_liberal"
                    if (isProfissional) onEntrarProfissional(uid)
                    else onEntrarCliente(uid)} else {
                    if (uid.isNotEmpty()) {
                        val perfilReal = getPerfilAndroid(uid)
                        val ehProfissional = perfilReal?.tipo == "profissional_certificado" ||
                                perfilReal?.tipo == "profissional_liberal"
                        if (ehProfissional) onEntrarProfissional(uid)
                        else onEntrarCliente(uid)
                    } else {
                        erroGeral = "E-mail ou senha incorretos."
                    }
                }
            } catch (e: Exception) {
                loading = false
                erroGeral = "E-mail ou senha incorretos."
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceWarm)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onVoltar) {
                Text("← Voltar", color = InkMuted, fontSize = 14.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Brasil Tupi", fontSize = 26.sp, fontWeight = FontWeight.Black, color = Azul)
        Text("Conecta", fontSize = 26.sp, fontWeight = FontWeight.Black, color = Verde)

        Spacer(modifier = Modifier.height(24.dp))

        Text("Entrar na plataforma", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink, textAlign = TextAlign.Center)
        Text("Acesse sua conta para continuar.", fontSize = 14.sp, color = InkMuted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))

        Spacer(modifier = Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth().background(SurfaceOff, RoundedCornerShape(10.dp)).padding(4.dp)) {
            listOf("profissional" to "Sou profissional", "cliente" to "Sou cliente").forEach { (tipo, label) ->
                Button(
                    onClick = { tipoConta = tipo },
                    modifier = Modifier.weight(1f).height(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (tipoConta == tipo) Surface else Color.Transparent, contentColor = if (tipoConta == tipo) Verde else InkMuted),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = if (tipoConta == tipo) 2.dp else 0.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Column(modifier = Modifier.fillMaxWidth()) {
            Text("E-mail", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it; emailError = ""; erroGeral = "" },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("seu@email.com", color = InkMuted) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                isError = emailError.isNotEmpty(),
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Verde, unfocusedBorderColor = Color(0xFFE0E0E0), errorBorderColor = Urgente),
                singleLine = true
            )
            if (emailError.isNotEmpty()) Text(emailError, color = Urgente, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Senha", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                TextButton(onClick = { mostrarModalRecover = true; emailRecover = email; recoverMsg = "" }) {
                    Text("Esqueci minha senha", color = Verde, fontSize = 12.sp)
                }
            }
            OutlinedTextField(
                value = senha,
                onValueChange = { senha = it; senhaError = ""; erroGeral = "" },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Sua senha", color = InkMuted) },
                visualTransformation = if (mostrarSenha) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                isError = senhaError.isNotEmpty(),
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Verde, unfocusedBorderColor = Color(0xFFE0E0E0), errorBorderColor = Urgente),
                trailingIcon = {
                    TextButton(onClick = { mostrarSenha = !mostrarSenha }) {
                        Text(if (mostrarSenha) "Ocultar" else "Ver", color = InkMuted, fontSize = 12.sp)
                    }
                },
                singleLine = true
            )
            if (senhaError.isNotEmpty()) Text(senhaError, color = Urgente, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (erroGeral.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth().background(Color(0xFFFDE8E8), RoundedCornerShape(8.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(erroGeral, color = Urgente, fontSize = 13.sp)
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        Button(
            onClick = { handleLogin() },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Verde, contentColor = Color.White),
            shape = RoundedCornerShape(10.dp),
            enabled = !loading
        ) {
            if (loading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text("Entrar como ${if (tipoConta == "profissional") "profissional" else "cliente"}", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth().background(VerdeClaro, RoundedCornerShape(8.dp)).padding(12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text("🔒  Conexão segura e criptografada", fontSize = 12.sp, color = Verde)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Não tem conta? ", fontSize = 13.sp, color = InkMuted)
            TextButton(onClick = onCadastro) {
                Text("Criar conta gratuita", color = Verde, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
    if (mostrarModalRecover) {
        AlertDialog(
            onDismissRequest = { mostrarModalRecover = false },
            containerColor = Surface,
            shape = RoundedCornerShape(16.dp),
            title = { Text("Recuperar senha", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink) },
            text = {
                Column {
                    Text("Digite seu e-mail para receber o link de recuperação.", fontSize = 13.sp, color = InkMuted)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = emailRecover,
                        onValueChange = { emailRecover = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("seu@email.com") },
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )
                    if (recoverMsg.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(recoverMsg, fontSize = 12.sp, color = Verde)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val ok = resetSenhaAndroid(emailRecover)
                            recoverMsg = if (ok) "E-mail enviado! Verifique sua caixa de entrada." else "Erro ao enviar. Tente novamente."
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Verde, contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Enviar", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { mostrarModalRecover = false }) {
                    Text("Fechar", color = InkMuted, fontSize = 13.sp)
                }
            }
        )
    }
}