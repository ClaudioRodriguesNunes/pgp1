package com.example.pgp_1resgate

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// -----------------------------
// Models
// -----------------------------
@SuppressLint("UnsafeOptInUsageError")
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

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class CheckinRequest(
    val eventId: Int,
    val nameOrNick: String,
    val baleeira: String,
    val mode: String = "manual",
    val present: Boolean = true
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class CheckinResponse(
    val sucesso: Boolean? = null,
    val erro: String? = null
)

// -----------------------------
// Configuração HTTP
// -----------------------------
private const val BASE_URL = "http://10.0.2.2:5275"
private const val EVENT_ID = 1

private val httpClient by lazy {
    HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    encodeDefaults = true
                }
            )
        }
        expectSuccess = false
    }
}

// -----------------------------
// MainActivity
// -----------------------------
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                AppScreen()
            }
        }
    }
}

// -----------------------------
// AppScreen principal
// -----------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var tripulantes by remember { mutableStateOf<List<Tripulante>>(emptyList()) }
    var filtrados by remember { mutableStateOf<List<Tripulante>>(emptyList()) }
    var carregando by remember { mutableStateOf(true) }
    var erro by remember { mutableStateOf<String?>(null) }

    var query by remember { mutableStateOf("") }
    var baleeiraFiltro by remember { mutableStateOf("Todas") }

    // ✅ lista de Strings, não lista de listas
    val confirmados = remember { mutableStateListOf<String>() }

    println("📦 Variáveis de estado inicializadas")

    println("📦 Variáveis de estado inicializadas") // log de progresso

    // Carregar CSV
    LaunchedEffect(Unit) {
        println("🌐 Iniciando carregamento do CSV...")

        carregando = true

        try {
            val csv = withContext(Dispatchers.IO) {
                println("🔗 Baixando CSV de $BASE_URL/data/tripulantes_pgp1.csv")
                httpClient.get("$BASE_URL/data/tripulantes_pgp1.csv").bodyAsText()
            }
            println("✅ CSV carregado com sucesso (${csv.length} bytes)")
            val linhas = csv.lines().filter { it.isNotBlank() }
            val dados = linhas.drop(1).mapNotNull { linha ->
                val p = linha.split(',').map { it.trim() }
                if (p.size < 7) null else Tripulante(
                    nome = p[0],
                    nomeGuerra = p[1],
                    matricula = p[2],
                    baleeira = p[3],
                    empresa = p[4],
                    camarote = p[5],
                    leito = p[6]
                )
            }
            tripulantes = dados.sortedBy { it.nome.lowercase() }
            filtrados = tripulantes
        } catch (e: Exception) {
            erro = "Erro ao carregar CSV: ${e.message}"
            println("💥 ERRO AO CARREGAR CSV: ${e.message}")
        } finally {
            carregando = false
            println("🧹 Finalizando carregamento — carregando=$carregando")
        }
    }

    // Filtro dinâmico
    LaunchedEffect(query, baleeiraFiltro, tripulantes) {
        filtrados = tripulantes
            .filter {
                val q = query.lowercase()
                q.isBlank() || it.nome.lowercase().contains(q)
                        || it.nomeGuerra.lowercase().contains(q)
                        || it.matricula.lowercase().contains(q)
            }
            .filter { if (baleeiraFiltro == "Todas") true else it.baleeira == baleeiraFiltro }
    }

    // Envio do check-in
    fun enviarCheckin(t: Tripulante) {
        scope.launch {
            try {
                val response: CheckinResponse = httpClient.post("$BASE_URL/events/$EVENT_ID/checkins") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        CheckinRequest(
                            eventId = EVENT_ID,
                            nameOrNick = t.nomeGuerra.ifBlank { t.nome },
                            baleeira = t.baleeira,
                            present = true
                        )
                    )
                }.body()

                if (response.sucesso != false) {
                    confirmados.add(t.matricula)
                    Toast.makeText(context, "Presença confirmada: ${t.nome}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Falha: ${response.erro ?: "Erro desconhecido"}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Erro: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // -----------------------------
    // UI
    // -----------------------------
    Scaffold(
        topBar = { TopAppBar(title = { Text("PGP-1 — Controle de POB", fontSize = 18.sp) }) }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when {
                carregando -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("Carregando tripulantes…")
                    }
                }

                erro != null -> {
                    Text(
                        "⚠ ${erro}",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                else -> {
                    Column(Modifier.fillMaxSize()) {
                        // Filtros
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                label = { Text("Buscar nome / matrícula") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            BaleeiraFiltro(selecionado = baleeiraFiltro, onChange = { baleeiraFiltro = it })
                        }

                        // Lista
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFFF5F5F5))
                        ) {
                            items(filtrados, key = { it.matricula }) { t ->
                                TripulanteCard(
                                    t = t,
                                    confirmado = confirmados.contains(t.matricula),
                                    onConfirmar = { enviarCheckin(t) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------
// Componentes visuais
// -----------------------------
@Composable
private fun BaleeiraFiltro(selecionado: String, onChange: (String) -> Unit) {
    val opcoes = listOf("Todas", "2", "3", "4", "5", "6")
    var expanded by remember { mutableStateOf(false) }

    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text("Baleeira: $selecionado")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            opcoes.forEach { op ->
                DropdownMenuItem(
                    text = { Text(op) },
                    onClick = {
                        onChange(op)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun TripulanteCard(
    t: Tripulante,
    confirmado: Boolean,
    onConfirmar: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("🪪 Matrícula: ${t.matricula}", fontWeight = FontWeight.Medium)
            Text("👤 ${t.nome}", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            if (t.nomeGuerra.isNotBlank())
                Text("📛 Nome de guerra: ${t.nomeGuerra}", fontSize = 13.sp)
            Text("🏢 Empresa: ${t.empresa}", fontSize = 13.sp)
            Text("🛶 Baleeira: ${t.baleeira}", fontSize = 13.sp)
            Text("🛏️ Camarote ${t.camarote} | Leito ${t.leito}", fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                if (confirmado)
                    Text("✔ Presente", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                else
                    Button(onClick = onConfirmar) { Text("Confirmar") }
            }
        }
    }
}
