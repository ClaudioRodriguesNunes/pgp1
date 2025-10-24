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

// Modelo de dados — segue padrão camelCase
@Serializable
data class Tripulante(
    val nome: String,
    @SerialName("nome_guerra") val nomeGuerra: String,
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

    // Carrega os dados do CSV local (simulação offline)
    LaunchedEffect(Unit) {
        try {
            withContext(Dispatchers.IO) {
                val url = "http://10.0.2.2:5275/data/tripulantes_pgp1.csv"
                val csvText = java.net.URL(url).readText(Charsets.UTF_8)

                val linhas = csvText.lines().filter { it.isNotBlank() }.drop(1)
                val lista = linhas.map { linha ->
                    val partes = linha.split(";")
                    Tripulante(
                        nome = partes.getOrNull(0)?.trim() ?: "",
                        nomeGuerra = partes.getOrNull(1)?.trim() ?: "",
                        baleeira = partes.getOrNull(2)?.trim() ?: "",
                        empresa = partes.getOrNull(3)?.trim() ?: "",
                        camarote = partes.getOrNull(4)?.trim() ?: "",
                        leito = partes.getOrNull(5)?.trim() ?: ""
                    )
                }
                withContext(Dispatchers.Main) {
                    tripulantes = lista
                    carregado = true
                }
            }
        } catch (e: Exception) {
            erro = "Erro ao carregar dados: ${e.message}"
            println("⚠️ ERRO DETALHADO: ${e}")
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
                    else -> ListaTripulantes(tripulantes)
                }
            }
        }
    )
}

@Composable
fun ListaTripulantes(tripulantes: List<Tripulante>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        Text(
            text = "Lista de Tripulantes",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(tripulantes) { t ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("👤 ${t.nome} (${t.nomeGuerra})", fontSize = 16.sp)
                        Text("🏢 ${t.empresa}")
                        Text("🛶 Baleeira: ${t.baleeira}")
                        Text("🛏️ Camarote ${t.camarote} — Leito ${t.leito}")
                    }
                }
            }
        }
    }
}