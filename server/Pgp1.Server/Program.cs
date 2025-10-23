using System.Globalization;
using CsvHelper;
using Microsoft.AspNetCore.SignalR;

var builder = WebApplication.CreateBuilder(args);

// SignalR + Swagger + CORS dev
builder.Services.AddSignalR();
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen();
builder.Services.AddCors(o => o.AddDefaultPolicy(p =>
    p.AllowAnyHeader().AllowAnyMethod().AllowAnyOrigin()));

builder.Services.AddSingleton<Repo>();

var app = builder.Build();
app.UseCors();
app.UseSwagger();
app.UseSwaggerUI();

// arquivos estáticos p/ servir dashboard.html
app.UseDefaultFiles();
app.UseStaticFiles();

// HUB tempo real
app.MapHub<EventHub>("/hub");

// API mínima
app.MapPost("/events", (Repo repo) =>
{
    var ev = repo.CreateEvent();
    return Results.Ok(new { ev.Id });
}).WithTags("Events");

app.MapPost("/events/{id:int}/import", async (int id, HttpRequest req, Repo repo) =>
{
    using var reader = new StreamReader(req.Body);
    using var csv = new CsvReader(reader, CultureInfo.InvariantCulture);
    var rows = csv.GetRecords<PessoaCsv>().ToList();

    var ok = repo.ImportCsv(id, rows, out var error);
    return ok ? Results.Ok(new { imported = rows.Count }) : Results.BadRequest(new { error });
}).WithTags("Events");

app.MapGet("/events/{id:int}/summary", (int id, Repo repo) =>
{
    var sum = repo.Summary(id);
    return sum is null ? Results.NotFound() : Results.Ok(sum);
}).WithTags("Dashboard");

app.MapPost("/events/{id:int}/checkins", async (int id, CheckinDto dto, Repo repo, IHubContext<EventHub> hub) =>
{
    var ok = repo.MarkPresent(id, dto);
    if (!ok) return Results.NotFound(new { message = "Pessoa não encontrada na baleeira informada." });

    var summary = repo.Summary(id);
    await hub.Clients.All.SendAsync("SummaryUpdated", summary);
    return Results.Ok(new { status = "ok" });
}).WithTags("Checkins");

// rota da TV
app.MapGet("/dashboard", () => Results.Redirect("/dashboard.html"));

app.Run();

record PessoaCsv(string nome, string nome_guerra, string baleeira, string empresa);
record Pessoa(int Id, string Nome, string NomeGuerra, string Baleeira, string Empresa, bool Presente);
record Event(int Id, List<Pessoa> Pessoas);
record CheckinDto(int EventId, string NameOrNick, string Baleeira, string Mode, bool Present);

class Repo
{
    private readonly Dictionary<int, Event> _events = new();
    private int _nextEventId = 1;
    private int _nextPersonId = 1;

    public Event CreateEvent()
    {
        var ev = new Event(_nextEventId++, new List<Pessoa>());
        _events[ev.Id] = ev;
        return ev;
    }

    public bool ImportCsv(int eventId, IEnumerable<PessoaCsv> rows, out string error)
    {
        error = "";
        if (!_events.TryGetValue(eventId, out var ev)) { error = "Evento não existe."; return false; }

        foreach (var r in rows)
        {
            if (string.IsNullOrWhiteSpace(r.nome) || string.IsNullOrWhiteSpace(r.baleeira))
                continue;
            ev.Pessoas.Add(new Pessoa(
                Id: _nextPersonId++,
                Nome: r.nome.Trim(),
                NomeGuerra: (r.nome_guerra ?? "").Trim(),
                Baleeira: r.baleeira.Trim().ToUpperInvariant(),
                Empresa: (r.empresa ?? "").Trim(),
                Presente: false
            ));
        }
        return true;
    }

    public object? Summary(int eventId)
    {
        if (!_events.TryGetValue(eventId, out var ev)) return null;
        var porBaleeira = ev.Pessoas.GroupBy(p => p.Baleeira)
            .Select(g => new {
                Baleeira = g.Key,
                Total = g.Count(),
                Presentes = g.Count(p => p.Presente),
                Faltantes = g.Count(p => !p.Presente)
            })
            .OrderBy(x => x.Baleeira)
            .ToList();

        return new
        {
            EventId = eventId,
            POB = ev.Pessoas.Count,
            Presentes = ev.Pessoas.Count(p => p.Presente),
            Faltantes = ev.Pessoas.Count(p => !p.Presente),
            PorBaleeira = porBaleeira
        };
    }

    public bool MarkPresent(int eventId, CheckinDto dto)
    {
        if (!_events.TryGetValue(eventId, out var ev)) return false;
        var alvo = ev.Pessoas.FirstOrDefault(p =>
            p.Baleeira.Equals(dto.Baleeira, StringComparison.OrdinalIgnoreCase) &&
            (string.Equals(p.Nome, dto.NameOrNick, StringComparison.OrdinalIgnoreCase) ||
             string.Equals(p.NomeGuerra, dto.NameOrNick, StringComparison.OrdinalIgnoreCase)));

        if (alvo is null) return false;

        var atualizado = alvo with { Presente = dto.Present };
        var idx = ev.Pessoas.FindIndex(p => p.Id == alvo.Id);
        ev.Pessoas[idx] = atualizado;
        return true;
    }
}

class EventHub : Hub { }
