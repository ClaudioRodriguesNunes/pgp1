using Microsoft.AspNetCore.Mvc;
using System.Globalization;

namespace Pgp1.Server.Controllers
{
    [ApiController]
    [Route("api/[controller]")]
    public class TripulantesController : ControllerBase
    {
        private readonly IWebHostEnvironment _env;

        public TripulantesController(IWebHostEnvironment env)
        {
            _env = env;
        }

        [HttpGet("{matricula}")]
        public IActionResult GetTripulante(string matricula)
        {
            try
            {
                string csvPath = Path.Combine(_env.WebRootPath, "data", "tripulantes_pgp1.csv");

                if (!System.IO.File.Exists(csvPath))
                    return NotFound(new { error = "Arquivo de tripulantes não encontrado." });

                var lines = System.IO.File.ReadAllLines(csvPath);
                if (lines.Length <= 1)
                    return NotFound(new { error = "Nenhum tripulante encontrado no arquivo." });

                // Cabeçalhos: nome, nome_guerra, matricula, baleeira, empresa, camarote, leito
                foreach (var line in lines.Skip(1))
                {
                    var values = line.Split(',');

                    if (values.Length < 7)
                        continue;

                    string matriculaCsv = values[2].Trim();
                    if (matriculaCsv.Equals(matricula.Trim(), StringComparison.OrdinalIgnoreCase))
                    {
                        return Ok(new
                        {
                            nome = values[0].Trim(),
                            nome_guerra = values[1].Trim(),
                            matricula = values[2].Trim(),
                            baleeira = values[3].Trim(),
                            empresa = values[4].Trim(),
                            camarote = values[5].Trim(),
                            leito = values[6].Trim()
                        });
                    }
                }

                return NotFound(new { error = $"Tripulante com matrícula {matricula} não encontrado." });
            }
            catch (Exception ex)
            {
                return StatusCode(500, new { error = "Erro interno ao processar requisição.", detalhe = ex.Message });
            }
        }
    }
}