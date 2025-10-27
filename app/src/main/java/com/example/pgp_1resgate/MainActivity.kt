package com.example.pgp_1resgate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader

import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import android.util.Log
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import io.ktor.client.engine.android.Android
import io.ktor.client.call.*

// Modelo de dados — segue padrão camelCase
@Serializable
data class Tripulante(
    val nome: String,
    @SerialName("nome_guerra") val nomeGuerra: String,
    val matricula: String,
    val baleeira: String,
    val empresa: String,
    val camarote: String,
    val leito: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                TelaPrincipal()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaPrincipal() {
    var tripulantes by remember { mutableStateOf(listOf<Tripulante>()) }
    var erro by remember { mutableStateOf("") }
    var carregado by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // Carrega os dados do CSV local (simulação offline)
    LaunchedEffect(Unit) {
        try {
            withContext(Dispatchers.IO) {
                val url = "http://10.0.2.2:5275/data/tripulantes_pgp1.csv"
                val csvText = java.net.URL(url).readText(Charsets.UTF_8)

                val linhas = csvText.lines().filter { it.isNotBlank() }.drop(1)
                val lista = linhas.map { linha ->
                    val partes = linha.split(",")
                    Tripulante(
                        nome = partes.getOrNull(0)?.trim() ?: "",
                        nomeGuerra = partes.getOrNull(1)?.trim() ?: "",
                        matricula = partes.getOrNull(2)?.trim() ?: "",
                        baleeira = partes.getOrNull(3)?.trim() ?: "",
                        empresa = partes.getOrNull(4)?.trim() ?: "",
                        camarote = partes.getOrNull(5)?.trim() ?: "",
                        leito = partes.getOrNull(6)?.trim() ?: ""
                    )
                }
                withContext(Dispatchers.Main) {
                    tripulantes = lista
                    carregado = true
                }
            }
        } catch (e: Exception) {
            erro = "Falha ao carregar dados. Verifique se o servidor está rodando em 10.0.2.2:5275"

        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PGP-1 — Controle de POB", fontSize = 18.sp) }
            )
        },
        content = { padding ->
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                when {
                    erro.isNotEmpty() -> Text("⚠️ $erro", color = MaterialTheme.colorScheme.error)
                    !carregado -> CircularProgressIndicator()
                    else -> ListaTripulantes(tripulantes, coroutineScope, context)
                }
            }
        }
    )
}

@Composable
fun ListaTripulantes(
    tripulantes: List<Tripulante>,
    coroutineScope: CoroutineScope,
    context: android.content.Context
) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        items(tripulantes) { t ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("🪪 Matrícula: ${t.matricula}", fontSize = 15.sp)
                    Text("👤 ${t.nome}", fontSize = 16.sp)
                    if (t.nomeGuerra.isNotEmpty()) {
                        Text("📛 Nome de guerra: ${t.nomeGuerra}", fontSize = 14.sp)
                    }
                    Text("🏢 Empresa: ${t.empresa}", fontSize = 14.sp)
                    Text("🛶 Baleeira: ${t.baleeira}", fontSize = 14.sp)
                    Text("🛏️ Camarote ${t.camarote} | Leito ${t.leito}", fontSize = 14.sp)

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val sucesso = registrarPresenca(1, t)
                                if (sucesso) {
                                    Toast.makeText(
                                        context,
                                        "Presença confirmada para ${t.nome}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Falha ao registrar ${t.nome}.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) {
                        Text("Confirmar Presença", color = Color.White)
                    }
                }
            }
        }
    }
}

suspend fun registrarPresenca(
    eventId: Int,
    tripulante: Tripulante,
    modo: String = "manual"
): Boolean {
    return try {
        val client = HttpClient(Android) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        Log.i("Checkin", "Enviando: ${tripulante.nome} → ${tripulante.baleeira}")

        val response = client.post("http://10.0.2.2:5275/events/$eventId/checkins") {
            contentType(ContentType.Application.Json)
            setBody(
               """
               {
                 "EventId": $eventId,
                 "NameOrNick": "${tripulante.nomeGuerra.ifBlank { tripulante.nome }}",
                 "Baleeira": "${tripulante.baleeira}",
                 "Mode": "$modo",
                 "Present": true
               }
               """.trimIndent()
            )

        }

        Log.i("Checkin", "Resposta: ${response.status}")

        if (response.status == HttpStatusCode.OK) {
            println("✅ Presença registrada para ${tripulante.nomeGuerra}")
            true
        } else {
            println("❌ Falha: ${response.status}")
            false
        }

    } catch (e: Exception) {
        Log.e("Checkin", "Erro ao enviar presença", e)
        false
    }
}