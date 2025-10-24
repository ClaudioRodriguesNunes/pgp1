using Microsoft.AspNetCore.Builder;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;

var builder = WebApplication.CreateBuilder(args);

// Habilita os controladores (nossos endpoints)
builder.Services.AddControllers();

// Cria o app
var app = builder.Build();

// Habilita o acesso a arquivos estáticos (wwwroot)
app.UseStaticFiles();

// Mapeia os endpoints dos controllers
app.MapControllers();

// Porta padrão (caso não venha de launchSettings.json)
app.Urls.Add("http://localhost:5275");

app.Run();
