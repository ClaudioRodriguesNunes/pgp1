# PGP-1 — Contagem de POB em Situações de Simulado e Emergência (MVP)

Aplicação composta por **Servidor .NET** (PC do Centro de Controle) e **App Android** (líderes de baleeira) para registrar presenças em pontos de encontro, consolidar POB e destacar faltantes na **Plataforma PGP-1 (Garoupa)**.

> **Status atual (MVP em desenvolvimento):**
> - Criar evento
> - Importar lista de tripulantes (CSV)
> - App Android lista tripulantes e envia **check-in** ao servidor
> - **Dashboard** web mostra consolidação em tempo quase real
> - Operação local em rede interna (sem internet pública)

---

## 1) Objetivo

**Primário:** disponibilizar um app *offline-first* para marcar presença em simulados/emergências, consolidando o POB por baleeira e destacando faltantes.

**Secundários (resumo):** reduzir erros de transcrição, gerar relatórios (CSV/PDF) ao final, manter trilha de auditoria e apoiar lições aprendidas.

---

## 2) Arquitetura (alto nível)

- **Servidor (PC/Windows ou Linux/macOS)** — ASP.NET Core  
  - Endpoints REST para eventos e check-ins  
  - Arquivos estáticos (dashboard em `wwwroot`)  
  - CORS habilitado
- **App Android (Kotlin/Compose)** — Dispositivo do líder de baleeira  
  - Lista de tripulantes (CSV) + check-in via HTTP  
  - Emulador usa `10.0.2.2:<porta>` para alcançar o host

Comunicação: HTTP (Ktor no Android) → API do servidor (.NET).  
Modo *offline* planejado para próxima(s) sprints.

---

## 3) Como Executar

### 3.1 Servidor (.NET)

Pré-requisito: **.NET 8 SDK**

```bash
cd server/Pgp1.Server
dotnet run
```

Saída esperada:
```
Now listening on: http://0.0.0.0:5275
```

- **Porta** (opcional, execução única):
  ```bash
  dotnet run --urls "http://0.0.0.0:5000"
  ```
- **Porta** (persistente): edite `Properties/launchSettings.json`
  ```json
  "applicationUrl": "http://0.0.0.0:5275"
  ```

> Para acessar de outro dispositivo (celular físico), use o **IP local** do PC e libere a porta no **firewall**.

**URLs úteis**
- Dashboard (TV): `http://<host>:<porta>/dashboard.html`
- Resumo (API): `GET /events/{id}/summary`

**Teste rápido (PowerShell – Windows):**
```powershell
# criar evento
$resp = Invoke-RestMethod -Method POST http://localhost:5275/events
$resp

# registrar check-in
Invoke-RestMethod -Method POST `
  -ContentType "application/json" `
  -Body '{"eventId":1,"nameOrNick":"Teste","baleeira":"2","mode":"manual","present":true}' `
  http://localhost:5275/events/1/checkins
```

### 3.2 App Android

Pré-requisitos (Android Studio):
- AGP 8.5+, **Kotlin 2.0.x** (com `org.jetbrains.kotlin.plugin.compose`)
- Compose (via BOM)
- Dependências principais:  
  `Ktor (client-okhttp, content-negotiation)`,  
  `kotlinx-serialization-json 1.7.x`,  
  `kotlinx-coroutines-android 1.8.x`,  
  `ZXing (journeyapps + core)`.

**Permissões (AndroidManifest.xml):**
```xml
<uses-permission android:name="android.permission.INTERNET"/>
<uses-permission android:name="android.permission.CAMERA"/>
```

**Base URL (exemplos):**
```kotlin
// Emulador Android
val baseUrl = "http://10.0.2.2:5275"

// Aparelho físico na mesma rede
val baseUrl = "http://SEU_IP_LOCAL:5275"
```

> Se usar HTTP (não-TLS), garanta *cleartext* permitido em `networkSecurityConfig`.

---

## 4) Formato dos Dados

### 4.1 CSV de Tripulantes (atual)
Cabeçalho obrigatório:
```csv
nome,nome_guerra,matricula,baleeira,empresa,camarote,leito
```

Exemplo:
```csv
nome,nome_guerra,matricula,baleeira,empresa,camarote,leito
João da Silva,Silva,120345,2,TRANSPETRO,302,A
Maria Santos,Mary,998877,3,PRESTSERV,210,B
```

> O servidor pode servir este arquivo em `wwwroot/data/tripulantes_pgp1.csv` e o app consome via HTTP.

### 4.2 Check-in (POST `/events/{eventId}/checkins`)
```json
{
  "eventId": 1,
  "nameOrNick": "Silva",
  "baleeira": "2",
  "mode": "manual|qr|biometria",
  "present": true
}
```

### 4.3 Resumo (GET `/events/{eventId}/summary`)
Retorna IDs/nicks presentes (versão simples do MVP).

---

## 5) Estrutura do Repositório

```
.
├─ server/
│  └─ Pgp1.Server/
│     ├─ Program.cs
│     ├─ Properties/
│     │  └─ launchSettings.json
│     └─ wwwroot/
│        ├─ dashboard.html
│        └─ data/
│           ├─ tripulantes_pgp1.csv
│           └─ event_1_summary.json
└─ app/
   └─ (projeto Android)
      ├─ app/build.gradle.kts
      ├─ src/main/AndroidManifest.xml
      └─ src/main/java/.../MainActivity.kt
```

---

## 6) Tecnologias

- **Backend:** .NET 8 (ASP.NET Core), arquivos estáticos, CORS habilitado
- **Frontend (TV):** HTML/JS simples (dashboard em `wwwroot`)
- **Android:** Kotlin, Jetpack Compose, Ktor, Coroutines, ZXing
- **Empacotamento:** Gradle (Android), dotnet CLI (Servidor)

---

## 7) Guia de Operação (Fluxo MVP)

1. **Subir servidor** (`dotnet run`) e confirmar porta.  
2. **Garantir CSV** em `wwwroot/data/tripulantes_pgp1.csv`.  
3. **Criar evento** (POST `/events`).  
4. **Abrir Dashboard** em `http://<host>:<porta>/dashboard.html`.  
5. **Abrir App Android**, conferir lista e **enviar check-ins**.  
6. **Acompanhar consolidação** no Dashboard.  

---

## 8) Sprints & Diário de Projeto

> Esta seção é o **log vivo** do desenvolvimento (atualize continuamente).

### Sprint atual
- **Objetivo:** MVP funcional com check-in manual/QR e dashboard com resumo.
- **Início:** 2025-10-xx • **Conclusão prevista:** 2025-11-xx
- **Entregas:**  
  - Servidor com `/events`, `/events/{id}/checkins`, `/events/{id}/summary`  
  - Dashboard em `wwwroot/dashboard.html`  
  - App Android com listagem do CSV e envio de check-in
- **Problemas & Soluções:**  
  - Cleartext HTTP → habilitado via `networkSecurityConfig`  
  - Emulador não atinge host → usar `10.0.2.2:<porta>`  
  - Compose/Serialization *warnings* → tratados com `@OptIn(...)` quando necessário
- **Pendências:**  
  - Validação por cadastro/baleeira no servidor  
  - Autocomplete/busca assistida no app  
  - Fila local para modo offline e sincronização

### Próximas sprints (backlog)
1. **Validação por cadastro**: check-in apenas para nomes do CSV; opção de forçar baleeira.  
2. **Busca assistida (autocomplete)** no app e/ou endpoint de sugestão.  
3. **Modo offline** (persistência local + fila/sync).  
4. **Cadastro na recepção** (PC/servidor).  
5. **Relatório final PDF/CSV** com logs e métricas.  
6. **Biometria Bluetooth** (template & match local) e fallback automático para QR/matrícula.

---

## 9) Problemas Comuns (e soluções rápidas)

- **App (emulador) não conecta ao servidor**
  - Use `http://10.0.2.2:<porta>` no baseURL.
  - Verifique se o servidor está ouvindo na porta e se o firewall permite a conexão.

- **“Cleartext HTTP traffic … not permitted”**
  - Habilite `usesCleartextTraffic` e `networkSecurityConfig` no Android.

- **Dashboard não mostra mudanças**
  - Atualize a página; confirme `GET /events/{id}/summary` no navegador.

- **Erros de Compose/Serialization**
  - Kotlin 2.0.x com `org.jetbrains.kotlin.plugin.compose`.
  - Use BOM do Compose, `kotlinx-serialization-json 1.7.x` e `@OptIn(...)` quando exigido.

---

## 10) Autores

- **Claudio Rodrigues Nunes** — Operador de Produção (PGP-1/Transpetro), Estudante de Ciência da Computação (UFF)  
  GitHub: https://github.com/ClaudioRodriguesNunes  
  LinkedIn: https://www.linkedin.com/in/-claudionunes-/

> Contribuições futuras: abrir *issues* e *pull requests* com descrições objetivas e reproduções mínimas.

---

## 11) Licença

Definir conforme política interna (ex.: MIT/Apache-2.0).  
Enquanto não definida, considerar **uso interno** para testes/piloto.
