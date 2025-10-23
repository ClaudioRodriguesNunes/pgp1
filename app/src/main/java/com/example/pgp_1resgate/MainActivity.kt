package com.example.pgp_1resgate

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    val mode: String,
    val present: Boolean
)

class MainActivity : ComponentActivity() {
    private val client = HttpClient(OkHttp)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Solicita permissão da câmera ao iniciar
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

@Composable
fun CheckinScreen(client: HttpClient, activity: ComponentActivity) {
    val scope = rememberCoroutineScope()
    var nome by remember { mutableStateOf(TextFieldValue("")) }
    var nomeGuerra by remember { mutableStateOf(TextFieldValue("")) }
    var baleeira by remember { mutableStateOf(TextFieldValue("")) }
    var mensagem by remember { mutableStateOf("") }

    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Confirmação de Presença", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(value = nome, onValueChange = { nome = it }, label = { Text("Nome") })
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(value = nomeGuerra, onValueChange = { nomeGuerra = it }, label = { Text("Nome de Guerra") })
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(value = baleeira, onValueChange = { baleeira = it }, label = { Text("Baleeira") })
        Spacer(Modifier.height(16.dp))

        // Botão de leitura QR Code
        Button(onClick = {
            val integrator = IntentIntegrator(activity)
            integrator.setPrompt("Aproxime o QR Code do tripulante")
            integrator.setOrientationLocked(true)
            integrator.initiateScan()
        }) {
            Text("Ler QR Code")
        }

        Spacer(Modifier.height(16.dp))

        // Botão de confirmação manual
        Button(onClick = {
            scope.launch {
                val dto = CheckinDto(
                    eventId = 1,
                    nameOrNick = if (nome.text.isNotBlank()) nome.text else nomeGuerra.text,
                    baleeira = baleeira.text,
                    mode = "manual",
                    present = true
                )
                try {
                    val response = client.post("http://10.0.2.2:5275/events/${dto.eventId}/checkins") {
                        contentType(ContentType.Application.Json)
                        setBody(dto)
                    }
                    mensagem = if (response.status == HttpStatusCode.OK)
                        "Presença registrada manualmente!"
                    else
                        "Erro: ${response.status}"
                } catch (e: Exception) {
                    mensagem = "Erro ao enviar presença: ${e.message}"
                }
            }
        }) {
            Text("Confirmar Presença")
        }

        Spacer(Modifier.height(16.dp))
        Text(mensagem)
    }
}
