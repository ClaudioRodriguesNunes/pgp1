@file:OptIn(kotlinx.serialization.InternalSerializationApi::class)
package com.example.pgp_1resgate

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.zxing.integration.android.IntentIntegrator
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data class CheckinDto(
    val eventId: Int,
    val nameOrNick: String,
    val baleeira: String,
    val empresa: String,
    val leito: String,
    val mode: String,
    val present: Boolean
)

class MainActivity : ComponentActivity() {
    private val client = HttpClient(OkHttp)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Solicita permissão da câmera (para QR Code)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 0)
        }

        setContent {
            MaterialTheme {
                CheckinScreen(client, this)
            }
        }
    }
}
enum class CheckMode { AUTO, MANUAL }

data class UltimoCheck(
    val nome: String,
    val nomeGuerra: String,
    val baleeira: String,
    val empresa: String,
    val horario: String
)
@Composable
fun TelaCheckin(
    mode: CheckMode,
    onModeChange: (CheckMode) -> Unit,
    onQrScan: (() -> Unit)? = null,      // em AUTO chamamos isso ao ler QR/biometria
    onConfirmManual: (matricula: String, baleeira: String) -> Unit,
    ultimo: UltimoCheck?
) {
    var matricula by remember { mutableStateOf("") }
    var baleeira by remember { mutableStateOf("") }

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)) {

        // Seletor de modo
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Modo:", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(12.dp))
            FilterChip(
                selected = mode == CheckMode.AUTO,
                onClick = { onModeChange(CheckMode.AUTO) },
                label = { Text("Automático (Bio/QR)") }
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = mode == CheckMode.MANUAL,
                onClick = { onModeChange(CheckMode.MANUAL) },
                label = { Text("Manual (Matrícula)") }
            )
        }

        Spacer(Modifier.height(16.dp))

        // Cartão do último check (sempre leitura)
        Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Último check-in", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                if (ultimo != null) {
                    Text("Nome: ${ultimo.nome}")
                    Text("Nome de guerra: ${ultimo.nomeGuerra}")
                    Text("Baleeira: ${ultimo.baleeira}")
                    Text("Empresa: ${ultimo.empresa}")
                    Text("Horário: ${ultimo.horario}")
                } else {
                    Text("Ainda não há check-ins registrados.")
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Área manual (só edita em MANUAL)
        val manualEnabled = mode == CheckMode.MANUAL

        OutlinedTextField(
            value = matricula,
            onValueChange = { matricula = it },
            enabled = manualEnabled,
            label = { Text("Matrícula") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = baleeira,
            onValueChange = { baleeira = it.uppercase() },
            enabled = manualEnabled,
            label = { Text("Baleeira") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        Row {
            // Em AUTO, nada de confirmar. Em alguns aparelhos você pode exibir um status só para debug.
            if (mode == CheckMode.MANUAL) {
                Button(
                    onClick = {
                        if (matricula.isNotBlank() && baleeira.isNotBlank()) {
                            onConfirmManual(matricula.trim(), baleeira.trim())
                        }
                    },
                    enabled = manualEnabled && matricula.isNotBlank() && baleeira.isNotBlank()
                ) {
                    Text("Confirmar presença")
                }
            } else {
                // Opcional: botão para disparam leitura QR durante desenvolvimento
                if (onQrScan != null) {
                    OutlinedButton(onClick = onQrScan) { Text("Forçar leitura QR (dev)") }
                }
            }
        }
    }
}
@Composable
fun CheckinScreen(client: HttpClient, activity: ComponentActivity) {
    val scope = rememberCoroutineScope()

    var nome by remember { mutableStateOf(TextFieldValue("")) }
    var nomeGuerra by remember { mutableStateOf(TextFieldValue("")) }
    var baleeira by remember { mutableStateOf(TextFieldValue("")) }
    var empresa by remember { mutableStateOf(TextFieldValue("")) }
    var leito by remember { mutableStateOf(TextFieldValue("")) }
    var mensagem by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Confirmação de Presença", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(value = nome, onValueChange = { nome = it }, label = { Text("Nome Completo") })
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(value = nomeGuerra, onValueChange = { nomeGuerra = it }, label = { Text("Nome de Guerra") })
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(value = baleeira, onValueChange = { baleeira = it }, label = { Text("Baleeira") })
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(value = empresa, onValueChange = { empresa = it }, label = { Text("Empresa") })
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(value = leito, onValueChange = { leito = it }, label = { Text("Leito / Camarote") })
        Spacer(Modifier.height(16.dp))

        // Botão para leitura do QR Code
        Button(onClick = {
            val integrator = IntentIntegrator(activity)
            integrator.setPrompt("Aproxime o QR Code do tripulante")
            integrator.setOrientationLocked(true)
            integrator.initiateScan()
        }) {
            Text("📷 Ler QR Code")
        }

        Spacer(Modifier.height(16.dp))

        // Botão de confirmação manual
        Button(onClick = {
            scope.launch {
                val dto = CheckinDto(
                    eventId = 1,
                    nameOrNick = if (nome.text.isNotBlank()) nome.text else nomeGuerra.text,
                    baleeira = baleeira.text,
                    empresa = empresa.text,
                    leito = leito.text,
                    mode = "manual",
                    present = true
                )
                try {
                    val response = client.post("http://10.0.2.2:5275/events/${dto.eventId}/checkins") {
                        contentType(ContentType.Application.Json)
                        setBody(dto)
                    }
                    mensagem = if (response.status == HttpStatusCode.OK)
                        "✅ Presença registrada com sucesso!"
                    else
                        "❌ Erro: ${response.status}"
                } catch (e: Exception) {
                    mensagem = "⚠️ Falha na comunicação: ${e.message}"
                }
            }
        }) {
            Text("✅ Confirmar Presença Manualmente")
        }

        Spacer(Modifier.height(16.dp))
        Text(mensagem)
    }
}
