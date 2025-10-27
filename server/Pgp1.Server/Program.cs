using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Http;
using Microsoft.Extensions.Hosting;
using Microsoft.OpenApi.Models;
using System.Text.Json;
using System.Collections.Concurrent;

var builder = WebApplication.CreateBuilder(args);

// === Serviços ===
builder.Services.AddCors(o => o.AddDefaultPolicy(p => p.AllowAnyOrigin().AllowAnyHeader().AllowAnyMethod()));
builder.Services.AddControllers();
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen(c =>
{
    c.SwaggerDoc("v1", new OpenApiInfo { Title = "PGP-1 API", Version = "v1" });
});

var app = builder.Build();
app.UseCors();
app.UseStaticFiles();

// === Habilita Swagger em ambiente Development ===
if (app.Environment.IsDevelopment())
{
    app.UseSwagger();
    app.UseSwaggerUI();
}

// === Estado em memória (MVP) ===
var eventos = new ConcurrentDictionary<int, ConcurrentDictionary<string, bool>>();

// === Criar evento ===
app.MapPost("/events", () =>
{
    int id = eventos.Count + 1;
    eventos[id] = new ConcurrentDictionary<string, bool>();
    Console.WriteLine($"🟢 Evento criado: {id}");
    return Results.Ok(new { id });
});

// === Importar CSV (simulado) ===
app.MapPost("/events/{eventId}/import", async (int eventId, HttpContext context) =>
{
    using var reader = new StreamReader(context.Request.Body);
    string csv = await reader.ReadToEndAsync();

    eventos.TryAdd(eventId, new ConcurrentDictionary<string, bool>());

    Console.WriteLine($"📄 CSV recebido para evento {eventId}: {csv.Split('\n').Length - 1} linhas");
    return Results.Ok(new { sucesso = true, linhas = csv.Split('\n').Length - 1 });
});

// === Registrar presença ===
app.MapPost("/events/{eventId}/checkins", (int eventId, CheckinDto dto) =>
{
    try
    {
        Console.WriteLine($"✅ Check-in recebido: {dto.NameOrNick} (Evento {eventId})");

        var lista = eventos.GetOrAdd(eventId, new ConcurrentDictionary<string, bool>());
        lista[dto.NameOrNick] = dto.Present;

        var path = Path.Combine(app.Environment.WebRootPath ?? ".", "data", $"event_{eventId}_summary.json");
        Directory.CreateDirectory(Path.GetDirectoryName(path)!);
        File.WriteAllText(path, JsonSerializer.Serialize(lista.Keys));

        return Results.Ok(new { sucesso = true });
    }
    catch (Exception ex)
    {
        Console.WriteLine($"❌ Erro no check-in: {ex.Message}");
        return Results.BadRequest(new { sucesso = false, erro = ex.Message });
    }
});

// === Resumo para o Dashboard ===
app.MapGet("/events/{eventId}/summary", (int eventId) =>
{
    if (eventos.TryGetValue(eventId, out var lista))
        return Results.Ok(new { eventId, presentes = lista.Keys });
    return Results.NotFound(new { erro = "Evento não encontrado" });
});

app.Urls.Add("http://0.0.0.0:5275");
app.Run();

// === Declaração de tipos (precisa ficar no final) ===
record CheckinDto(int EventId, string NameOrNick, string Baleeira, string Mode, bool Present);
