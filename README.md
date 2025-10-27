# PGP-1 — Contagem de POB em Emergência (MVP)

Este repositório reúne o **servidor .NET** do centro de controle (painel/TV e API) e o **aplicativo Android** (posto do líder de baleeira) para confirmação de presença durante uma evacuação na plataforma PGP-1.

> **Atualização — Outubro 2025**  
> MVP funcional completo: o aplicativo Android comunica corretamente com o servidor .NET, registrando check-ins em tempo real.  
> Próxima etapa: validação automática de nomes/baleeiras e busca assistida.

> **Status:** MVP funcional (on-line). Já permite:
> 1) Criar evento de evacuação  
> 2) Importar lista de tripulantes (CSV: `nome,nome_guerra,matricula,baleeira,empresa,camarote,leito`)  
> 3) Marcar presença por **digitação** ou **QR code** no app Android  
> 4) Acompanhar em tempo real no **Dashboard** (TV do centro de controle)

Próximas sprints previstas: validação por cadastro/baleeira, sugestões de nomes (autocomplete), operação off-line com fila/sincronização, cadastro na recepção, integração biométrica Bluetooth.

---

## 🧩 Integração confirmada
- Comunicação **app ⇄ servidor** validada com sucesso (`POST /events/{id}/checkins`)  
- Dados refletem imediatamente no painel web  
- Logs de evento exibidos no console do servidor  
- CSV de tripulantes carregado via `wwwroot/data/tripulantes_pgp1.csv`

---

## Arquitetura (alto nível)

- **server/Pgp1.Server** — ASP.NET Core (.NET 8)  
  - Endpoints REST: criar evento, importar CSV, registrar presença, resumo  
  - Dashboard web em `http://<host>:<porta>/dashboard` (atualização em tempo real)
- **app/** — Android (Kotlin, Jetpack Compose)  
  - Tela do **Líder de Baleeira**: seleciona baleeira e evento, confirma presença por digitação ou QR

Comunicação: HTTP (Ktor no Android) → API do servidor.

---

## Requisitos

- **Servidor (Windows/Linux/macOS)**
  - [.NET 8 SDK](https://dotnet.microsoft.com/download)
- **Android**
  - Android Studio (AGP 8.5+), Kotlin 2.0.x, Compose  
  - Emulador Android **ou** aparelho físico na mesma rede do servidor

---

## Estrutura do repositório

```
.
├─ server/
│  └─ Pgp1.Server/
│     ├─ Program.cs
│     ├─ Properties/launchSettings.json
│     └─ wwwroot/
│        ├─ data/tripulantes_pgp1.csv
│        └─ dashboard.html
└─ app/
   └─ (projeto Android)
      ├─ app/build.gradle.kts
      ├─ src/main/AndroidManifest.xml
      └─ src/main/java/.../MainActivity.kt
```

---

## Executando o Servidor (.NET)

No terminal, a partir de `server/Pgp1.Server/`:

```bash
dotnet run
```

Você verá algo como:

```
Now listening on: http://localhost:5275
```

Ajuste de porta (opcional):

- Só nesta execução:
  ```bash
  dotnet run --urls "http://0.0.0.0:5000"
  ```
- Persistente via `launchSettings.json` (`Properties/launchSettings.json`):
  ```json
  "applicationUrl": "http://0.0.0.0:5000"
  ```

> Se for acessar a partir de outro dispositivo (ex.: celular físico), use `0.0.0.0` ou o IP da máquina e libere a porta no firewall do sistema.

### Endereços úteis

- Swagger (testes manuais): `http://<host>:<porta>/swagger`  
- Dashboard (TV): `http://<host>:<porta>/dashboard`

---

## Fluxo de Operação (MVP)

1. **Criar evento**  
   Via Swagger ou `curl`:
   ```bash
   curl -sX POST http://<host>:<porta>/events
   ```
   A resposta inclui o `id` do evento (ex.: `1`).

2. **Importar CSV (lista de tripulantes)**  
   Formato obrigatório (cabeçalho na primeira linha):
   ```csv
   nome,nome_guerra,matricula,baleeira,empresa,camarote,leito
   Maria da Silva,Mari,120001,2,Petrobras,302,A
   João Souza,JJ,120002,2,Terceirizada X,305,B
   ```
   Envie:
   ```bash
   curl -X POST -H "Content-Type: text/csv"         --data-binary "@caminho/arquivo.csv"         http://<host>:<porta>/events/1/import
   ```

3. **Acompanhar Painel**  
   Abra `http://<host>:<porta>/dashboard` em uma TV/monitor do centro de controle.

4. **Confirmar presenças no App Android**  
   - Lista de tripulantes carregada automaticamente via CSV  
   - Toque em **Confirmar Presença**  
   - O registro é enviado via HTTP POST → servidor (.NET)

5. **Resumo do evento**  
   ```bash
   curl http://<host>:<porta>/events/1/summary
   ```

---

## Aplicativo Android

### BaseURL

No emulador Android, use o host especial `10.0.2.2` apontando para a porta do servidor:

```kotlin
val baseUrl = "http://10.0.2.2:5275" // ou 5000, se configurado
```

No **aparelho físico**, use o IP da máquina do servidor:

```kotlin
val baseUrl = "http://SEU_IP_LOCAL:5275"
```

> Garanta que a porta está aberta no firewall e que o servidor escuta em `0.0.0.0`.

### Permissões

No `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET"/>
<uses-permission android:name="android.permission.CAMERA"/>
```

### Build (Android Studio)

- Requisitos do projeto:
  - Kotlin 2.0.x com plugin `org.jetbrains.kotlin.plugin.compose`
  - Compose via BOM
  - Dependências principais do app:  
    `Ktor (client-okhttp, content-negotiation, kotlinx-json)`  
    `kotlinx-serialization-json 1.7.x`  
    `kotlinx-coroutines-android 1.8.x`  
    `ZXing (journeyapps + core)`

---

## Endpoints principais (servidor)

- `POST /events`  
  Cria um novo evento de evacuação. Retorna `{ "id": <int> }`.

- `POST /events/{eventId}/import` (Content-Type: `text/csv`)  
  Importa a lista de tripulantes.

- `POST /events/{eventId}/checkins` (JSON)  
  Marca presença. Corpo:
  ```json
  {
    "EventId": 1,
    "NameOrNick": "Mari",
    "Baleeira": "2",
    "Mode": "manual",
    "Present": true
  }
  ```

- `GET /events/{eventId}/summary`  
  Retorna o resumo de presentes/ausentes por baleeira.

- `GET /dashboard`  
  Painel em tempo real para a TV do centro de controle.

> Observação: no MVP, a validação de nomes ainda é permissiva (qualquer texto).  
> A próxima sprint restringirá para **apenas** nomes presentes no cadastro importado e na **mesma baleeira**.

---

## Problemas comuns (e soluções)

- **App no emulador não conecta**  
  - Use `http://10.0.2.2:<porta>` como `baseUrl`.  
  - Confirme que o servidor está “ouvindo” (`dotnet run` log).  
  - Ajuste porta (5275/5000) e firewall local.

- **QR no emulador não funciona**  
  Emuladores às vezes falham com câmera/QR.  
  Use a confirmação por digitação para testes.

- **Dashboard não atualiza**  
  - Verifique `GET /events/{id}/summary` para confirmar o registro.  
  - Atualize o navegador ou limpe cache.

- **Kotlin/Gradle com erro de Compose/Serialization**  
  - Garanta o plugin `org.jetbrains.kotlin.plugin.compose` (Kotlin 2.0.x).  
  - Use `kotlinx-serialization-json 1.7.x`.  
  - Evite `composeOptions { kotlinCompilerExtensionVersion = ... }` no Kotlin 2.x.  
  - Use **Invalidate Caches / Restart** no Android Studio se persistir.

---

## Roadmap (próximas sprints)

1. **Validação por cadastro**  
   Check-in só se `NameOrNick` existir no CSV importado (e na mesma baleeira).

2. **Busca assistida (autocomplete)**  
   `GET /events/{id}/baleeiras/{code}/membros?query=xxx` para sugerir nomes a partir de 3 letras.

3. **Modo off-line (app)**  
   Fila local de check-ins + sincronização quando a rede voltar.

4. **Cadastro na recepção (PC/servidor)**  
   Tela / endpoint para “novos a bordo” no ato do embarque.

5. **Integração biométrica Bluetooth**  
   Leitor digital conectado ao device do líder de baleeira.

---

## Licença

Defina a licença do projeto (ex.: MIT, Apache-2.0) de acordo com as políticas internas.

---

## Créditos

Projeto PGP-1 — Contagem de POB em Emergência  
Servidor: .NET 8 + ASP.NET Core • App: Android (Kotlin/Compose, Ktor, ZXing)
