package cl.efficientchile.inventario.util

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Lee una boleta o un voucher de tarjeta y saca los datos que el vendedor
 * tendria que teclear.
 *
 * La regla que ordena todo este archivo: **esto rellena campos, no guarda
 * ventas**. Lo que sale de aca se muestra en pantalla y el vendedor lo
 * confirma. Un lector que se equivoca en el monto y guarda solo es peor que
 * no tener lector, porque nadie revisa un campo que ya venia lleno.
 *
 * Por eso `avisos` es parte del resultado y no un detalle: lo que no cuadra
 * se dice.
 *
 * ## Calibrado contra papel de verdad
 *
 * Cada regla rara de aca abajo existe porque una boleta real la rompio. Las
 * once que se usaron para afinarlo: facturas de ESTEL y de Alex Ruz Cid,
 * vouchers GetNet de dos ferreterias, vouchers Transbank de cuatro comercios,
 * y boletas electronicas del SII de cuatro emisores distintos. Entre ellas
 * venian una con el total emborronado, una con un digito comido por la
 * arruga, y una con dos copias impresas en la misma tira.
 */
object LectorBoleta {

    private const val TASA_IVA = 0.19

    data class Lectura(
        val total: Int? = null,
        val neto: Int? = null,
        val iva: Int? = null,
        val numero: String? = null,
        val fecha: String? = null,
        val hora: String? = null,
        val rut: String? = null,
        val ultimos4: String? = null,
        val avisos: List<String> = emptyList(),
    ) {
        /** Si no hay total, no hay nada que precargar. */
        val sirve: Boolean get() = total != null
    }

    private const val MONTO = """\$?\s*(\d{1,3}(?:[.,]\d{3})+|\d+)"""
    // Un numero de documento puede traer puntos de miles: "N 303.747".
    private const val NUMERO = """(\d{1,3}(?:\.\d{3})+|\d{1,12})"""

    /* Palabras que delatan una linea de plata. Una linea de plata no lleva el
       numero del documento, y confundirlos fue el error mas caro que
       encontraron las boletas reales: en la de Astro Supermarket, la linea
       "El IVA de la Boleta $429" hacia que el folio quedara en 429. */
    private val RE_PLATA = Regex(
        """\bTOTAL\b|\bIVA\b|\bNETO\b|\bMONTO\b|\bSUBTOTAL\b|\bCOMPRA\b|\bIMPORTE\b""")

    private val RE_MONTO = Regex(MONTO)
    private val RE_RUT = Regex("""\b(\d{1,2}\.\d{3}\.\d{3}-[\dK])\b""")
    private val RE_RUT_EN_LINEA = Regex("""\d{1,2}\.\d{3}\.\d{3}-[\dK]""")
    private val RE_FECHA = Regex("""\b(\d{1,2})[/-](\d{1,2})[/-](\d{2,4})\b""")
    private val RE_HORA = Regex("""\b(\d{1,2}):(\d{2})(?::\d{2})?\b""")
    // Un solo asterisco basta: GetNet imprime "*4273" y Transbank "****4273".
    private val RE_TARJETA = Regex("""[*X]+\s*(\d{4})\b""")
    private val RE_IVA = Regex("""\bIVA\b""")
    private val RE_SIN_IVA = Regex("""SIN IVA|EXENT""")

    /* El orden es la prioridad. Si el papel trae folio de boleta Y codigo de
       autorizacion, el que el vendedor quiere anotar es el de la boleta. */
    private val ETIQUETAS = listOf(
        """\bBOLETA\b""",
        """\bFOLIO\b""",
        // La N suelta: "N 1234", "N° 303.747", "Nro 36325", "NRO OPERACION".
        // Tiene que ser palabra completa, si no TRANSBANK y VENTA la disparan.
        """\bN(?:RO|UM|º|°)?\b""",
        """\bOPERACION\b""",
        """\bCOMPROBANTE\b""",
        """\bCOD\.?\s*AUT""",
        """\bAUTORIZACION\b""",
        """\bAPROBACION\b""",
        """\bTRANSACCION\b""",
        """\bTICKET\b""",
    )

    private fun sinTildes(s: String): String = s
        .replace('Á', 'A').replace('É', 'E').replace('Í', 'I')
        .replace('Ó', 'O').replace('Ú', 'U').replace('Ñ', 'N')
        .replace('á', 'A').replace('é', 'E').replace('í', 'I')
        .replace('ó', 'O').replace('ú', 'U').replace('ñ', 'N')

    private fun normalizar(texto: String): List<String> =
        texto.lineSequence()
            .map { sinTildes(it).uppercase().replace(Regex("""[ \t]+"""), " ").trim() }
            .filter { it.isNotEmpty() }
            .toList()

    /**
     * El ULTIMO monto de la linea, no el primero.
     *
     * En "19% IVA: 3.051" el 19 es un porcentaje y viene antes que la plata.
     * Tomando el primero, el IVA de la factura de Alex Ruz quedaba en $19.
     */
    private fun monto(linea: String): Int? {
        val todos = RE_MONTO.findAll(linea).map { it.groupValues[1] }.toList()
        if (todos.isEmpty()) return null
        // En pesos chilenos no hay decimales: un punto o una coma dentro de un
        // monto siempre es separador de miles.
        val v = todos.last().replace(".", "").replace(",", "").toIntOrNull()
        return if (v != null && v > 0) v else null
    }

    private fun buscarMonto(lineas: List<String>, patron: String): Int? {
        val re = Regex(patron)
        for (l in lineas) if (re.containsMatchIn(l)) monto(l)?.let { return it }
        return null
    }

    fun leer(texto: String): Lectura {
        val L = normalizar(texto)
        val avisos = mutableListOf<String>()

        // ------------------------------------------------------------ totales
        /* "TOTAL NETO" y "TOTAL IVA" NO son el total. La boleta de Los Cisnes
           imprime las tres lineas en ese orden, y quedarse con la primera
           daba $834 como total de una compra de $2.182. */
        var total = buscarMonto(L, """\bTOTAL\b(?!\s*(?:NETO|IVA))""")
            ?: buscarMonto(L, """\bIMPORTE\b|\bVALOR TOTAL\b""")
            ?: buscarMonto(L, """\bMONTO\b(?!\s*VENTA)""")

        /* "MONTO VENTA" en un voucher Transbank es el NETO, no el total; en
           un voucher GetNet, "Monto" a secas es el total. Por eso se
           distinguen, y por eso el total de arriba excluye "MONTO VENTA". */
        var neto = buscarMonto(L,
            """\bNETO\b|\bAFECTO\b|\bSUBTOTAL\b|\bMONTO VENTA\b|\bCOMPRA\b""")

        var iva: Int? = null
        for (l in L) {
            if (RE_IVA.containsMatchIn(l) && !RE_SIN_IVA.containsMatchIn(l)) {
                val v = monto(l)
                // Un 19 suelto es la tasa, no el impuesto.
                if (v != null && v != 19) { iva = v; break }
            }
        }

        // ------------------------------------------- numero de documento
        val candidatos = mutableListOf<String>()
        for (et in ETIQUETAS) {
            val re = Regex(et + """[^0-9]{0,20}?""" + NUMERO)
            for (l in L) {
                if (RE_RUT_EN_LINEA.containsMatchIn(l)) continue  // el RUT no es folio
                if (RE_PLATA.containsMatchIn(l)) continue         // ni una linea de plata
                re.find(l)?.let { candidatos.add(it.groupValues[1].replace(".", "")) }
            }
        }
        /* Se prefiere el primer candidato de tres digitos o mas. El motivo es
           "BOLETA ELECTRONICA 39": ese 39 es el codigo de documento del SII,
           no el folio. Si ninguno llega a tres digitos se usa el primero
           igual, porque hay boletas con folio de dos cifras. */
        val numero = candidatos.firstOrNull { it.length >= 3 } ?: candidatos.firstOrNull()

        // ------------------------------------------------------- fecha y hora
        var fecha: String? = null
        var hora: String? = null
        for (l in L) {
            /* La direccion "21 DE MAYO 71-73-75" se leia como fecha y daba
               2075-73-71. Tres numeros con guion no son una fecha si el dia
               pasa de 31 o el mes de 12. */
            if (fecha == null) {
                for (m in RE_FECHA.findAll(l)) {
                    val d = m.groupValues[1].toInt()
                    val mes = m.groupValues[2].toInt()
                    var a = m.groupValues[3].toInt()
                    if (d !in 1..31 || mes !in 1..12) continue
                    if (a < 100) a += 2000
                    if (a !in 2000..2100) continue
                    fecha = "%04d-%02d-%02d".format(a, mes, d)
                    break
                }
            }
            if (hora == null) RE_HORA.find(l)?.let { m ->
                hora = "%02d:%s".format(m.groupValues[1].toInt(), m.groupValues[2])
            }
        }

        val rut = L.firstNotNullOfOrNull { RE_RUT.find(it)?.groupValues?.get(1) }
        val u4 = L.firstNotNullOfOrNull { RE_TARJETA.find(it)?.groupValues?.get(1) }

        // ---------------------------------------------------------- cuadratura
        /* El total emborronado: el voucher de Electronica Segovia se lee
           "TOTAL: $21." porque el resto quedo tapado por una mancha. Un total
           menor que el neto es imposible, asi que manda neto + IVA. */
        if (total != null && neto != null && iva != null && total < neto) {
            avisos += "El total se leyó mal ($total). Se usó neto + IVA."
            total = neto + iva
        }

        val t = total
        if (t != null && neto != null && iva != null) {
            if (abs((neto + iva) - t) > 2) {
                avisos += "El neto y el IVA no suman el total. Revisa los tres."
            }
        } else if (t != null && neto == null && iva != null) {
            // No se pisa un IVA que SI se leyo.
            neto = t - iva
            avisos += "Neto calculado como total menos IVA."
        } else if (t != null && neto == null) {
            // En Chile el precio va con IVA incluido, asi que se derivan.
            neto = (t / (1 + TASA_IVA)).roundToInt()
            iva = t - neto!!
            avisos += "Neto e IVA calculados desde el total."
        } else if (t == null && neto != null && iva != null) {
            total = neto + iva
            avisos += "Total calculado desde neto + IVA."
        }

        if (total == null) avisos += "No se encontró el total. Escríbelo a mano."
        if (numero == null) avisos += "No se encontró el número. Escríbelo a mano."

        return Lectura(total, neto, iva, numero, fecha, hora, rut, u4, avisos)
    }
}
