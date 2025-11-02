# PGP‑1 — Contagem de POB em Emergência

Sistema de apoio à evacuação da plataforma **PGP‑1**:
- **Servidor .NET** para criar eventos, importar lista de tripulantes e expor o **painel (dashboard)**.
- **Aplicativo Android** (posto do Líder de Baleeira) para **confirmar presença** de tripulantes.

> **Objetivo**: registrar e acompanhar, em tempo real, o **POB** (Personnel On Board) durante **simulados** e **emergências**, reduzindo erros operacionais e o tempo de conferência.

---

## 👤 Autores

- **Claudio Rodrigues Nunes** — [GitHub](https://github.com/ClaudioRodriguesNunes) • [LinkedIn](https://www.linkedin.com/in/-claudionunes-/)
- Contribuidores: abra um PR adicionando seu nome aqui.

---

## 🚦 Status do Projeto

- **MVP** em funcionamento local: app Android → **POST /events/{id}/checkins** → painel reflete em tempo real.
- Lista de tripulantes carregada via **CSV** em `wwwroot/data/tripulantes_pgp1.csv`.

> Próximas evoluções: validação por cadastro/baleeira, busca assistida (autocomplete), modo off‑line, cadastro na recepção, integração biométrica Bluetooth.

---

## 🧱 Arquitetura (alto nível)

```
.
├─ server/
│  └─ Pgp1.Server/              # ASP.NET Core (.NET 8)
│     ├─ Program.cs             # Endpoints mínimos + CORS + arquivos estáticos
│     └─ wwwroot/
│        ├─ dashboard.html      # Painel (TV do centro de controle)
│        └─ data/
│           └─ tripulantes_pgp1.csv
└─ app/                         # Android (Kotlin + Jetpack Compose)
   └─ (projeto Android)
      ├─ app/build.gradle.kts
      ├─ src/main/AndroidManifest.xml
      └─ src/main/java/.../MainActivity.kt
```

- Comunicação: **HTTP** (Ktor no Android) → **API** do servidor .NET
- Atualização do painel: leitura periódica do resumo (`/events/{id}/summary`) e/ou arquivos gerados pelo servidor em `wwwroot/data`.

---

## 🔧 Como executar

### 1) Servidor (.NET)

**Pré‑requisito:** [.NET 8 SDK](https://dotnet.microsoft.com/download)

```powershell
cd server/Pgp1.Server
dotnet run
# Saída esperada: Now listening on: http://localhost:5275
```

> Dica: para expor na rede local (ex.: testar no celular) use:
>
> ```powershell
> dotnet run --urls "http://0.0.0.0:5000"
> ```
> e acesse via `http://SEU_IP_LOCAL:5000` (libere a porta no firewall).

**CSV de tripulantes**
Coloque o arquivo em `server/Pgp1.Server/wwwroot/data/tripulantes_pgp1.csv` com o **cabeçalho** abaixo:

```csv
nome,nome_guerra,matricula,baleeira,empresa,camarote,leito
Maria da Silva,Mari,120001,2,Petrobras,302,A
João Souza,JJ,120002,2,Terceirizada X,305,B
```

**Criar evento & testar check‑in (PowerShell):**

```powershell
# Criar evento
Invoke-RestMethod -Method POST http://localhost:5275/events

# Registrar check-in (exemplo para o evento 1)
Invoke-RestMethod -Method POST `
  -Uri http://localhost:5275/events/1/checkins `
  -ContentType "application/json" `
  -Body '{"EventId":1,"NameOrNick":"Mari","Baleeira":"2","Mode":"manual","Present":true}'
```

**Painel (TV):**
```
http://localhost:5275/dashboard.html
```

---

### 2) Aplicativo Android

**Pré‑requisitos:** Android Studio (AGP 8.5+), Kotlin 2.0.x, Compose.

- `AndroidManifest.xml` precisa das permissões:
  ```xml
  <uses-permission android:name="android.permission.INTERNET"/>
  <uses-permission android:name="android.permission.CAMERA"/>
  ```
- **Base URL** no emulador: `http://10.0.2.2:5275` (ou a porta que você usou no servidor).
  Em aparelho físico: `http://SEU_IP_LOCAL:5275`.

**Build & run** no Android Studio (emulador recomendado para os testes iniciais).

---

## 🧪 Fluxo de uso (MVP)

1. Inicie o servidor (`dotnet run`).
2. Garanta o CSV em `wwwroot/data/tripulantes_pgp1.csv`.
3. Crie o evento (`POST /events`).
4. Abra o painel: `http://localhost:5275/dashboard.html`.
5. No app Android, selecione um tripulante e **Confirmar Presença**.
6. O painel deve refletir o check‑in em até alguns segundos.

---

## 🧰 Stack / Dependências principais

- **Servidor**: .NET 8, ASP.NET Core (arquivos estáticos + Minimal APIs)
- **App Android**: Kotlin 2.0.x, Jetpack Compose, Ktor (client‑okhttp), kotlinx‑serialization‑json, kotlinx‑coroutines, ZXing

> Observação de build: com Kotlin 2.x o plugin `org.jetbrains.kotlin.plugin.compose` dispensa `kotlinCompilerExtensionVersion`; use BOM do Compose.
> Avisos de `@OptIn(ExperimentalMaterial3Api::class)` podem aparecer — são esperados.

---

## 🩺 Solução de problemas (curto)

- **Emulador não conecta ao servidor**
  Use `http://10.0.2.2:<porta>` (em vez de `localhost`), confirme `dotnet run` e liberação de porta.
- **Erro “Cleartext HTTP not permitted”**
  Defina `networkSecurityConfig` permitindo HTTP local ou use HTTPS (para desenvolvimento, o HTTP local é suficiente).
- **Tela preta no app**
  Verifique o **Logcat** e se a tela realmente está sendo **composada** (`setContent { ... }`). Teste com um `Text("Hello")` para isolar.
- **PowerShell não envia JSON**
  Prefira `Invoke‑RestMethod` com `-ContentType` e `-Body` (exemplos acima).

---

## 🗺️ Roadmap resumido

- ✅ Integração app ⇄ servidor (check‑in em tempo real)
- ⏭️ Validação do nome/baleeira contra o CSV importado
- ⏭️ Busca assistida (autocomplete)
- ⏭️ Modo off‑line (fila + sincronização)
- ⏭️ Cadastro na recepção (tela no servidor)
- ⏭️ Integração biométrica Bluetooth

---

## 📒 Registro de Sprints

> Atualize apenas esta seção a cada entrega, mantendo o histórico técnico do projeto.

### Sprint 0 — MVP integrado (out/nov‑2025)
- **Objetivo:** provar fluxo ponta‑a‑ponta (app → servidor → painel).
- **Entregas:** check‑in via app; CSV em `wwwroot/data`; painel lendo resumo; comandos de teste via PowerShell.
- **Dificuldades:** comunicação em emulador (resolver com `10.0.2.2`), avisos de API experimental no Compose.
- **Decisões:** manter tráfego HTTP local em desenvolvimento; `@OptIn(ExperimentalMaterial3Api)` quando necessário.

> **Modelo para as próximas sprints**
>
> **Sprint N — título (aaaa‑mm‑dd a aaaa‑mm‑dd)**
> • Objetivo: …
> • Entregas: …
> • Dificuldades/Erros: …
> • Decisões/Trade‑offs: …

---

## 📝 Licença

Definir (MIT/Apache‑2.0, etc.) conforme as políticas internas.

---

## 🙌 Créditos

Projeto **PGP‑1 — Contagem de POB em Emergência**.
Servidor: .NET 8 + ASP.NET Core • App: Android (Kotlin/Compose, Ktor, ZXing)
