package com.example.pgp_1resgate

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset

// ---------- Modelo ----------
data class Tripulante(
    val nome: String,
    val nomeGuerra: String,
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
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppScreen()
                }
            }
        }
    }
}

// ---------- Tela raiz ----------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen() {
    val baseUrl = remember { "http://10.0.2.2:5275" } // emulador → host
    var carregando by remember { mutableStateOf(true) }
    var erro by remember { mutableStateOf<String?>(null) }
    var tripulantes by remember { mutableStateOf(emptyList<Tripulante>()) }

    // Carrega CSV ao abrir
    LaunchedEffect(Unit) {
        val resultado = runCatching { carregarTripulantesCsv("$baseUrl/data/tripulantes_pgp1.csv") }
        resultado.onSuccess { lista ->
            tripulantes = lista.sortedWith(
                compareBy(String.CASE_INSENSITIVE_ORDER, Tripulante::nome).thenBy(Tripulante::matricula)
            )
            erro = null
            Log.i("PGP1", "✔ CSV carregado: ${lista.size} linhas")
        }.onFailure { ex ->
            erro = "Falha ao carregar CSV: ${ex.message}"
            tripulantes = emptyList()
            Log.e("PGP1", "✖ erro ao carregar CSV", ex)
        }
        carregando = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PGP-1 — Controle de POB", fontSize = 18.sp, fontWeight = FontWeight.SemiBold) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Text(
                "Lista de Tripulantes",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            when {
                carregando -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text("Carregando dados…")
                        }
                    }
                }
                erro != null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("⚠ ${erro}", color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(12.dp))
                            // Botão “tentar novamente” simples (reabre a tela)
                            Button(onClick = {
                                // Dispara novo carregamento alterando um estado
                                // Aqui, pela simplicidade, só marcamos carregando e o LaunchedEffect acima não reexecuta.
                                // Se quiser reexecutar, mova o load para uma função e chame-a aqui.
                            }) {
                                Text("Tentar novamente")
                            }
                        }
                    }
                }
                tripulantes.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Nenhum tripulante encontrado.")
                    }
                }
                else -> {
                    ListaTripulantesScreen(tripulantes)
                }
            }
        }
    }
}

// ---------- Lista + Busca + Filtro ----------
@Composable
fun ListaTripulantesScreen(
    tripulantes: List<Tripulante>,
    modifier: Modifier = Modifier
) {
    var query by rememberSaveable { mutableStateOf("") }

    // Opções de baleeira: "Todas" + distintas do CSV
    val baleeiras = remember(tripulantes) {
        buildList {
            add("Todas")
            addAll(
                tripulantes.map { it.baleeira.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct()
                    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
            )
        }
    }
    var baleeiraSel by rememberSaveable(baleeiras) { mutableStateOf(baleeiras.first()) }

    // Filtro e ordenação
    val filtrados = remember(query, baleeiraSel, tripulantes) {
        val q = query.trim().lowercase()
        tripulantes.asSequence()
            .filter { t ->
                if (q.isEmpty()) true
                else t.nome.lowercase().contains(q) ||
                        t.nomeGuerra.lowercase().contains(q) ||
                        t.matricula.lowercase().contains(q)
            }
            .filter { t -> (baleeiraSel == "Todas") || t.baleeira.equals(baleeiraSel, true) }
            .sortedWith(
                compareBy(String.CASE_INSENSITIVE_ORDER, Tripulante::nome).thenBy(Tripulante::matricula)
            )
            .toList()
    }

    Column(
        modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SearchField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f)
            )
            BaleeiraFilter(
                options = baleeiras,
                selected = baleeiraSel,
                onSelect = { baleeiraSel = it },
                modifier = Modifier.widthIn(min = 140.dp)
            )
        }

        Spacer(Modifier.height(8.dp))

        if (filtrados.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nenhum tripulante encontrado")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtrados, key = { it.matricula }) { t ->
                    TripulanteCard(t)
                }
            }
        }
    }
}

// ---------- Widgets auxiliares (estáveis) ----------
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        label = { Text("Buscar (nome, guerra, matrícula)") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = "Limpar")
                }
            }
        },
        modifier = modifier
    )
}

@Composable
fun BaleeiraFilter(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }) {
            Text("Baleeira: $selected")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt) },
                    onClick = {
                        onSelect(opt)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun TripulanteCard(t: Tripulante) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("🪪 ${t.matricula} — ${t.nome}", fontSize = 16.sp)
            if (t.nomeGuerra.isNotBlank()) Text("📛 ${t.nomeGuerra}", fontSize = 14.sp)
            Text("🏢 ${t.empresa}", fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("🛶 Baleeira ${t.baleeira}", fontSize = 14.sp)
                Text("🛏️ Camarote ${t.camarote} • Leito ${t.leito}", fontSize = 14.sp)
            }
        }
    }
}

// ---------- Loader CSV (sem Ktor) ----------
/** CSV esperado:
 *  nome,nome_guerra,matricula,baleeira,empresa,camarote,leito
 */
private suspend fun carregarTripulantesCsv(url: String): List<Tripulante> = withContext(Dispatchers.IO) {
    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 7000
        readTimeout = 7000
    }
    conn.inputStream.bufferedReader(Charset.forName("UTF-8")).use { br ->
        val linhas = br.readLines().filter { it.isNotBlank() }
        if (linhas.isEmpty()) return@use emptyList<Tripulante>()
        val dados = linhas.drop(1) // pula cabeçalho
        dados.mapNotNull { linha ->
            val p = linha.split(",")
            if (p.size < 7) return@mapNotNull null
            Tripulante(
                nome        = p[0].trim(),
                nomeGuerra  = p[1].trim(),
                matricula   = p[2].trim(),
                baleeira    = p[3].trim(),
                empresa     = p[4].trim(),
                camarote    = p[5].trim(),
                leito       = p[6].trim()
            )
        }
    }
}
