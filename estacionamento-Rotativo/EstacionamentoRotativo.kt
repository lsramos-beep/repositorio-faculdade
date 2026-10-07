import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.min

const val CAPACIDADE = 10          
const val TOLERANCIA_MIN = 15      
const val VALOR_PRIMEIRA_HORA = 5.0
const val VALOR_HORA_ADICIONAL = 3.0
const val VALOR_MAXIMO_DIA = 30.0  

val FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

//carro
data class Ticket(
    val placa: String,
    val entrada: LocalDateTime,
    var saida: LocalDateTime? = null,
    var valorPago: Double = 0.0
)

class Estacionamento(private val capacidade: Int) {

    private val ativos = mutableMapOf<String, Ticket>()   // carros estacionados
    private val historico = mutableListOf<Ticket>()       // carros que já saíram

    val vagasLivres get() = capacidade - ativos.size

    fun registrarEntrada(placa: String): Result<Ticket> {
        if (!placaValida(placa)) return Result.failure(Exception("Placa inválida (ex: ABC1D23 ou ABC1234)."))
        if (ativos.containsKey(placa)) return Result.failure(Exception("Veículo já está no estacionamento."))
        if (vagasLivres == 0) return Result.failure(Exception("Estacionamento lotado!"))

        val ticket = Ticket(placa, LocalDateTime.now())
        ativos[placa] = ticket
        return Result.success(ticket)
    }

    fun registrarSaida(placa: String, minutosSimulados: Long? = null): Result<Ticket> {
        val ticket = ativos[placa] ?: return Result.failure(Exception("Veículo não encontrado."))

        ticket.saida = if (minutosSimulados != null) ticket.entrada.plusMinutes(minutosSimulados)
                       else LocalDateTime.now()
        ticket.valorPago = calcularValor(minutosPermanencia(ticket))

        ativos.remove(placa)
        historico.add(ticket)
        return Result.success(ticket)
    }

    fun minutosPermanencia(t: Ticket): Long =
        Duration.between(t.entrada, t.saida ?: LocalDateTime.now()).toMinutes()

    fun calcularValor(minutos: Long): Double {
        if (minutos <= TOLERANCIA_MIN) return 0.0
        if (minutos <= 60) return VALOR_PRIMEIRA_HORA

        val horasAdicionais = ceil((minutos - 60) / 60.0)   //cada fração conta como hora cheia
        val total = VALOR_PRIMEIRA_HORA + horasAdicionais * VALOR_HORA_ADICIONAL
        return min(total, VALOR_MAXIMO_DIA)
    }

    fun listarAtivos(): List<Ticket> = ativos.values.toList()

    fun faturamentoTotal(): Double = historico.sumOf { it.valorPago }

    fun totalAtendidos(): Int = historico.size

    private fun placaValida(placa: String) =
        Regex("^[A-Z]{3}[0-9][A-Z0-9][0-9]{2}$").matches(placa)
}
// console
fun moeda(v: Double) = "R$ %.2f".format(v)

fun main() {
    val estacionamento = Estacionamento(CAPACIDADE)

    while (true) {
        println(
            """
            ==============================
              ESTACIONAMENTO ROTATIVO
              Vagas livres: ${estacionamento.vagasLivres}/$CAPACIDADE
            ==============================
            1 - Registrar entrada
            2 - Registrar saída
            3 - Veículos estacionados
            4 - Relatório de faturamento
            5 - Tabela de preços
            0 - Sair
            """.trimIndent()
        )
        print("Opção: ")

        when (readLine()?.trim()) {
            "1" -> {
                print("Placa: ")
                val placa = readLine().orEmpty().trim().uppercase()
                estacionamento.registrarEntrada(placa)
                    .onSuccess { println("✔ Entrada registrada às ${it.entrada.format(FORMATO)}") }
                    .onFailure { println("✘ ${it.message}") }
            }

            "2" -> {
                print("Placa: ")
                val placa = readLine().orEmpty().trim().uppercase()
                print("Minutos simulados (Enter para usar o horário real): ")
                val minutos = readLine()?.trim()?.toLongOrNull()

                estacionamento.registrarSaida(placa, minutos)
                    .onSuccess {
                        println("✔ Saída registrada.")
                        println("  Permanência: ${estacionamento.minutosPermanencia(it)} min")
                        println("  Valor a pagar: ${moeda(it.valorPago)}")
                    }
                    .onFailure { println("✘ ${it.message}") }
            }

            "3" -> {
                val lista = estacionamento.listarAtivos()
                if (lista.isEmpty()) println("Nenhum veículo estacionado.")
                else lista.forEach {
                    println("${it.placa} | entrada: ${it.entrada.format(FORMATO)} | ${estacionamento.minutosPermanencia(it)} min")
                }
            }

            "4" -> {
                println("Veículos atendidos: ${estacionamento.totalAtendidos()}")
                println("Faturamento total:  ${moeda(estacionamento.faturamentoTotal())}")
            }

            "5" -> {
                println("Até $TOLERANCIA_MIN min: grátis")
                println("Até 1 hora: ${moeda(VALOR_PRIMEIRA_HORA)}")
                println("Hora adicional (ou fração): ${moeda(VALOR_HORA_ADICIONAL)}")
                println("Máximo por dia: ${moeda(VALOR_MAXIMO_DIA)}")
            }

            "0" -> {
                println("Encerrando...")
                return
            }

            else -> println("Opção inválida.")
        }
        println()
    }
}
