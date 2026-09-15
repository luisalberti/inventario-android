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
 *
 * ## Neto, IVA y total: primero lo declarado, despues la formula
 *
 * 1. Se busca lo que el papel DECLARA: neto (o "monto venta", "compra"),
 *    IVA (tambien "IVA incluido en este pago") y total (o "precio"). El monto
 *    de cada etiqueta es el que la sigue en su linea, prefiriendo el que lleva
 *    $; si la linea no trae monto, el de la linea de arriba o de abajo, porque
 *    en el papel los montos casi nunca quedan a la altura exacta.
 * 2. Si los tres estan y cumplen la cuenta, se usan tal cual.
 * 3. Si dos cumplen la cuenta entre si, el tercero sale de ellos.
 * 4. Si solo sirve uno, el resto sale por formula:
 *      solo total -> neto = total / 1,19   IVA = total - neto
 *      solo neto  -> IVA = neto x 19%       total = neto + IVA
 *      solo IVA   -> neto = IVA / 0,19      total = neto + IVA
 *    El total manda sobre los otros dos: es lo que se cobro.
 * 5. Si no hay ninguna etiqueta, se busca entre todos los montos del papel un
 *    trio que cumpla la cuenta.
 *
 * La cuenta, siempre:  IVA = 19% del neto   y   TOTAL = neto + IVA.
 */
object LectorBoleta {

    private const val TASA_IVA = 0.19
    private const val FACTOR_TOTAL = 1.19

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
        /** Lo que entrego el OCR, ordenado por filas. Para diagnosticar. */
        val textoLeido: String = "",
        /** Cuales de "total", "neto" e "iva" no venian en el papel y se calcularon. */
        val calculados: Set<String> = emptySet(),
    ) {
        /** Si no hay total, no hay nada que precargar. */
        val sirve: Boolean get() = total != null
    }

    /* Letras que el OCR confunde dentro de las etiquetas: la O con el cero en
       TOTAL y NETO, la I con la L o el 1 en IVA. Tambien "I.V.A." con puntos. */
    private const val ET_TOTAL = """T[O0]TA[LI1]"""
    private const val ET_NETO = """NET[O0]"""
    private const val ET_IVA = """(?<![A-Z])[I1L]\.?\s?V\.?\s?A\.?(?![A-Z])"""

    // Un numero de documento puede traer puntos de miles: "N 303.747".
    private const val NUMERO = """(\d{1,3}(?:\.\d{3})+|\d{1,12})"""

    /* Palabras que delatan una linea de plata. Una linea de plata no lleva el
       numero del documento, y confundirlos fue el error mas caro que
       encontraron las boletas reales: en la de Astro Supermarket, la linea
       "El IVA de la Boleta $429" hacia que el folio quedara en 429. */
    private val RE_PLATA = Regex(
        """\b$ET_TOTAL\b|$ET_IVA|\b$ET_NETO\b|\bMONTO\b|\bSUBTOTAL\b|\bCOMPRA\b|\bIMPORTE\b|\bPRECIO\b""")

    /* Un monto: "$3.990", "$ 3.990", "3.990" o "S3.990" (el OCR lee el $ como
       S). El grupo 1 dice si venia con signo; el 2 es el numero. */
    private val RE_MONTO = Regex("""(\$|S(?=\s?\d))?\s*(\d{1,3}(?:[.,]\d{3})+|\d+)""")
    private val RE_RUT = Regex("""\b(\d{1,2}\.\d{3}\.\d{3}-[\dK])\b""")
    private val RE_RUT_EN_LINEA = Regex("""\d{1,2}\.\d{3}\.\d{3}-[\dK]""")
    private val RE_FECHA = Regex("""\b(\d{1,2})[/-](\d{1,2})[/-](\d{2,4})\b""")
    private val RE_FECHA_ISO = Regex("""\b\d{4}-\d{2}-\d{2}\b""")

    /* Fecha y hora. Formatos del papel: dd/mm/aaaa, dd-mm-aaaa, dd/mm/aa,
       dd-mm-aa; hora hh:mm:ss o hh:mm.

       Se usa (?<!\d) en vez de \b porque el OCR suele pegar la fecha con la
       hora: "02/09/2614:44:01". Con \b el año quedaba en 2614, se descartaba,
       y la hora salia "44:01". */
    private val RE_FECHA_DMA = Regex("""(?<!\d)(\d{1,2})[/-](\d{1,2})[/-](\d{2})(\d{2})?""")
    private val RE_FECHA_AMD = Regex("""(?<!\d)(20\d{2})[/-](\d{1,2})[/-](\d{1,2})(?!\d)""")
    private val RE_HORA_VALIDA =
        Regex("""(?<!\d)([01]?\d|2[0-3])[:;]([0-5]\d)(?:[:;]([0-5]\d))?(?!\d)""")
    // "02 / 09 / 2026" y "14 : 44": el OCR mete espacios en los separadores.
    private val RE_ESPACIO_SEPARADOR = Regex("""(?<=\d)\s*([/:;-])\s*(?=\d)""")
    // "O2/O9/2O26": la O junto a un digito o separador es un cero.
    private val RE_O_EN_FECHA = Regex("""(?<=[\d/:;-])O|O(?=[\d/:;-])""")
    // "02/09/2614:44:01" -> "02/09/26 14:44:01".
    private val RE_FECHA_PEGADA =
        Regex("""(\d{1,2}[/-]\d{1,2}[/-](?:\d{4}|\d{2}))(?=\d{1,2}[:;]\d{2})""")
    private val RE_ETIQUETA_FECHA = Regex("""\bFECHA\b|\bEMISION\b""")
    private val RE_VENCIMIENTO = Regex("""VENC""")
    private val RE_ETIQUETA_HORA = Regex("""\bHORA\b""")
    private val RE_HORA = Regex("""\b(\d{1,2}):(\d{2})(?::\d{2})?\b""")
    // Un solo asterisco basta: GetNet imprime "*4273" y Transbank "****4273".
    private val RE_TARJETA = Regex("""[*X]+\s*(\d{4})\b""")
    private val RE_TARJETA_LIMPIAR = Regex("""\*+\s*\d{4}\b""")
    private val RE_SIN_IVA = Regex("""SIN $ET_IVA|EXENT""")
    // "IVA 19%" y "19% IVA": ese 19 es la tasa, no plata.
    private val RE_PORCENTAJE = Regex("""\d{1,2}(?:[.,]\d+)?\s*%""")
    // El OCR lee el cero como letra O: "$1O.4O4".
    private val RE_O_POR_CERO = Regex("""(?<=\d)O(?=[\d.,]|$)|(?<=[$.,])O|O(?=\d)""")

    /* Etiquetas de cada monto, en orden de prioridad.

       "TOTAL NETO" y "TOTAL IVA" NO son el total. La boleta de Los Cisnes
       imprime las tres lineas en ese orden, y quedarse con la primera daba
       $834 como total de una compra de $2.182. "SUB TOTAL" tampoco.

       "MONTO VENTA" en un voucher Transbank es el NETO, no el total; en un
       voucher GetNet, "Monto" a secas es el total. Por eso se distinguen.

       "PRECIO" tambien marca el total en algunas boletas, pero no en el
       encabezado "PRECIO UNITARIO" de la lista de articulos. */
    private val PATRONES_TOTAL = listOf(
        Regex("""(?<!SUB\s?)\b$ET_TOTAL\b(?!\s*:?\s*(?:$ET_NETO|[I1L]\.?\s?V\.?\s?A|AFECTO|EXENTO))"""),
        Regex("""\bVALOR $ET_TOTAL\b|\bIMPORTE\b|\bA PAGAR\b"""),
        Regex("""\bMONTO\b(?!\s*:?\s*(?:VENTA|$ET_NETO|AFECTO|EXENTO))"""),
        Regex("""\bPRECIO\b(?!\s*(?:UNIT|U\b|X\b))"""),
    )
    private val PATRONES_NETO = listOf(
        Regex("""\b$ET_NETO\b|\bAFECTO\b"""),
        Regex("""\bMONTO\s?VENTA\b|\bSUB\s?$ET_TOTAL\b"""),
        Regex("""\bCOMPRA\b(?!\s*AFECTA)"""),
    )
    // "IVA: $637", "IVA 19% $479", "IVA incluido en este pago: $1.533".
    private val PATRONES_IVA = listOf(Regex(ET_IVA))

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

    // ================================================================ montos

    private class Monto(val valor: Int, val conSigno: Boolean)

    /**
     * Borra lo que parece monto y no lo es: porcentajes, RUT, fechas, horas y
     * los 4 digitos de la tarjeta. Y arregla el cero leido como O.
     */
    private fun limpiar(texto: String): String {
        var s = RE_O_POR_CERO.replace(texto, "0")
        for (re in listOf(RE_PORCENTAJE, RE_RUT_EN_LINEA, RE_FECHA, RE_FECHA_ISO,
                RE_HORA, RE_TARJETA_LIMPIAR)) {
            s = re.replace(s, " ")
        }
        return s
    }

    /** Los montos de un texto, en el orden en que aparecen. */
    private fun montosEn(texto: String): List<Monto> =
        RE_MONTO.findAll(limpiar(texto))
            .mapNotNull { m ->
                // En pesos chilenos no hay decimales: un punto o una coma dentro
                // de un monto siempre es separador de miles.
                val v = m.groupValues[2].replace(".", "").replace(",", "").toIntOrNull()
                if (v == null || v <= 0) null else Monto(v, m.groupValues[1].isNotEmpty())
            }
            .toList()

    /** Todos los montos de la linea, del ultimo al primero. */
    private fun montos(linea: String): List<Int> = montosEn(linea).map { it.valor }.asReversed()

    /**
     * Los montos que acompañan a una etiqueta dentro de su linea, en orden de
     * preferencia: primero los que la SIGUEN y llevan $, despues los que la
     * siguen sin $, y al final los que estan antes de la etiqueta.
     *
     * "IVA incluido en este pago: $1.533" -> 1.533.
     * "19% IVA: 3.051" -> 3.051 (el 19% se borra antes).
     * "TOTAL IVA 19 $ 348" -> 348 primero, el 19 despues (y se descarta).
     */
    private fun montosJuntoA(linea: String, etiqueta: MatchResult): List<Int> {
        val despues = montosEn(linea.substring(etiqueta.range.last + 1))
        val antes = montosEn(linea.substring(0, etiqueta.range.first)).asReversed()
        return (despues.filter { it.conSigno } + despues.filterNot { it.conSigno } + antes)
            .map { it.valor }
    }

    /**
     * Montos que el papel declara para una etiqueta, en orden de preferencia.
     *
     * Primero los de la misma linea. Si la linea de la etiqueta no trae monto
     * (la columna de montos quedo un poco mas arriba o mas abajo), se toman
     * los de la linea siguiente y la anterior. La cuenta decide despues cual
     * era.
     */
    private fun declarados(lineas: List<String>, patrones: List<Regex>, esIva: Boolean): List<Int> {
        // Un 19 suelto junto a "IVA" es la tasa, no el impuesto.
        fun sirve(v: Int) = !(esIva && v == 19)

        val out = LinkedHashSet<Int>()
        for (re in patrones) {
            val vecinos = mutableListOf<Int>()
            for ((k, l) in lineas.withIndex()) {
                val m = re.find(l) ?: continue
                if (esIva && RE_SIN_IVA.containsMatchIn(l)) continue
                val propios = montosJuntoA(l, m).filter { sirve(it) }
                if (propios.isNotEmpty()) {
                    out += propios
                } else {
                    for (vecina in listOfNotNull(lineas.getOrNull(k + 1), lineas.getOrNull(k - 1))) {
                        vecinos += montosEn(vecina)
                            .sortedByDescending { it.conSigno }
                            .map { it.valor }
                            .filter { sirve(it) }
                    }
                }
            }
            out += vecinos
        }
        return out.toList()
    }

    private fun ivaDesdeNeto(neto: Int): Int = (neto * TASA_IVA).roundToInt()
    private fun netoDesdeTotal(total: Int): Int = (total / FACTOR_TOTAL).roundToInt()
    private fun netoDesdeIva(iva: Int): Int = (iva / TASA_IVA).roundToInt()

    /**
     * La cuenta que tiene que cumplir cualquier boleta chilena afecta:
     * IVA = 19% del neto, y total = neto + IVA.
     *
     * Se aceptan 2 pesos de diferencia por redondeo, o un 0,2% del neto en
     * facturas grandes, donde cada linea redondea su propio IVA.
     */
    private fun cuadra(neto: Int, iva: Int, total: Int): Boolean =
        neto > 0 && iva > 0 &&
            abs(neto + iva - total) <= 2 &&
            abs(iva - ivaDesdeNeto(neto)) <= maxOf(2, neto / 500)

    private class Montos(
        val total: Int?,
        val neto: Int?,
        val iva: Int?,
        val calculados: Set<String>,
        val avisos: List<String>,
    )

    /**
     * Busca entre TODOS los montos del papel un trio que cumpla la cuenta.
     * Solo se usa cuando ninguna etiqueta dio un monto.
     */
    private fun trioPorCuenta(todos: Set<Int>): Triple<Int, Int, Int>? {
        val lista = todos.filter { it >= 10 }
        var mejor: Triple<Int, Int, Int>? = null
        for (n in lista) {
            val esperado = ivaDesdeNeto(n)
            val tolerancia = maxOf(2, n / 500)
            for (i in lista) {
                if (abs(i - esperado) > tolerancia) continue
                for (t in lista) {
                    if (!cuadra(n, i, t)) continue
                    val m = mejor
                    if (m == null || t > m.third) mejor = Triple(n, i, t)
                }
            }
        }
        return mejor
    }

    private fun resolverMontos(
        dT: List<Int>, dN: List<Int>, dI: List<Int>, todos: Set<Int>,
    ): Montos {
        // ---- 1. Los tres declarados y la cuenta cuadra: se usan tal cual.
        for (t in dT) for (n in dN) for (i in dI) {
            if (cuadra(n, i, t)) return Montos(t, n, i, emptySet(), emptyList())
        }

        // ---- 2. Dos declarados que cuadran entre si: el tercero sale de ellos.
        for (t in dT) for (i in dI) {
            val n = t - i
            if (cuadra(n, i, t)) {
                val aviso = if (dN.isEmpty()) "Neto calculado: total − IVA."
                else "El neto leído (${dN.first()}) no cuadraba. Neto calculado: total − IVA."
                return Montos(t, n, i, setOf("neto"), listOf(aviso))
            }
        }
        for (t in dT) for (n in dN) {
            val i = t - n
            if (cuadra(n, i, t)) {
                val aviso = if (dI.isEmpty()) "IVA calculado: total − neto."
                else "El IVA leído (${dI.first()}) no cuadraba. IVA calculado: total − neto."
                return Montos(t, n, i, setOf("iva"), listOf(aviso))
            }
        }
        /* El total emborronado: el voucher de Electronica Segovia se lee
           "TOTAL: $21." porque el resto quedo tapado por una mancha. Si neto
           e IVA cuadran entre si, manda neto + IVA. */
        for (n in dN) for (i in dI) {
            if (cuadra(n, i, n + i)) {
                val aviso = if (dT.isEmpty()) "Total calculado: neto + IVA."
                else "El total leído (${dT.first()}) no cuadraba. Total calculado: neto + IVA."
                return Montos(n + i, n, i, setOf("total"), listOf(aviso))
            }
        }

        // ---- 3. Un solo dato declarado sirve: el resto sale por formula.
        //         El total manda: es lo que efectivamente se cobro.
        dT.firstOrNull()?.let { t ->
            val n = netoDesdeTotal(t)
            val i = t - n
            val avisos = mutableListOf("Neto e IVA calculados desde el total (neto = total ÷ 1,19).")
            if (dI.isNotEmpty()) avisos += "El IVA leído (${dI.first()}) no cuadraba con el total."
            if (dN.isNotEmpty()) avisos += "El neto leído (${dN.first()}) no cuadraba con el total."
            return Montos(t, n, i, setOf("neto", "iva"), avisos)
        }
        dN.firstOrNull()?.let { n ->
            val i = ivaDesdeNeto(n)
            val avisos = mutableListOf("IVA y total calculados desde el neto (IVA = neto × 19%).")
            if (dI.isNotEmpty()) avisos += "El IVA leído (${dI.first()}) no cuadraba con el neto."
            return Montos(n + i, n, i, setOf("iva", "total"), avisos)
        }
        dI.firstOrNull()?.let { i ->
            val n = netoDesdeIva(i)
            return Montos(n + i, n, i, setOf("neto", "total"), listOf(
                "Solo se leyó el IVA. Neto y total calculados desde él (neto = IVA ÷ 0,19): " +
                    "revisa el total."))
        }

        // ---- 4. Ninguna etiqueta dio un monto: el trio se busca por la cuenta.
        trioPorCuenta(todos)?.let { (n, i, t) ->
            return Montos(t, n, i, emptySet(), listOf(
                "Neto, IVA y total se reconocieron por la cuenta (neto + 19% = total), " +
                    "no por su etiqueta. Revísalos."))
        }

        return Montos(null, null, null, emptySet(), emptyList())
    }

    // ================================================================ numero

    private fun buscarNumero(L: List<String>): String? {
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
        return candidatos.firstOrNull { it.length >= 3 } ?: candidatos.firstOrNull()
    }

    // ========================================================= fecha y hora

    /** Arregla lo que el OCR le hace a fechas y horas antes de buscarlas. */
    private fun prepararFechaHora(linea: String): String {
        var s = RE_O_EN_FECHA.replace(linea, "0")
        s = RE_ESPACIO_SEPARADOR.replace(s) { it.groupValues[1] }
        s = RE_FECHA_PEGADA.replace(s) { it.groupValues[1] + " " }
        return s
    }

    /** "aaaa-mm-dd" si la fecha existe de verdad (no 31 de febrero), si no null. */
    private fun fechaValida(a: Int, mes: Int, d: Int): String? {
        if (a !in 2000..2100) return null
        return try {
            java.time.LocalDate.of(a, mes, d).toString()
        } catch (_: Exception) {
            null
        }
    }

    private fun fechaDe(linea: String): String? {
        /* dd/mm/aa o dd/mm/aaaa. El año se lee como dos digitos mas dos
           opcionales: si los cuatro no dan un año razonable, los dos ultimos
           eran de otra cosa pegada y el año es 20aa.

           La direccion "21 DE MAYO 71-73-75" no pasa: mes 73 no existe. */
        for (f in RE_FECHA_DMA.findAll(linea)) {
            val d = f.groupValues[1].toInt()
            val mes = f.groupValues[2].toInt()
            val corto = f.groupValues[3]
            val resto = f.groupValues[4]
            val largo = (corto + resto).toInt()
            val a = if (resto.isNotEmpty() && largo in 2000..2100) largo else 2000 + corto.toInt()
            fechaValida(a, mes, d)?.let { return it }
        }
        // aaaa-mm-dd, como la imprime Astro Supermarket.
        for (f in RE_FECHA_AMD.findAll(linea)) {
            fechaValida(
                f.groupValues[1].toInt(), f.groupValues[2].toInt(), f.groupValues[3].toInt(),
            )?.let { return it }
        }
        return null
    }

    private fun horaDe(linea: String): String? =
        RE_HORA_VALIDA.find(linea)?.let {
            "%02d:%s".format(it.groupValues[1].toInt(), it.groupValues[2])
        }

    /**
     * Fecha y hora del documento.
     *
     * La fecha se busca primero en las lineas que dicen FECHA o EMISION, y
     * nunca en la de vencimiento: una factura trae las dos y la que importa
     * es la de emision. La hora, primero en la linea que dice HORA, despues
     * en la misma linea de la fecha, y por ultimo en cualquiera.
     */
    private fun buscarFechaHora(fuentes: List<List<String>>): Pair<String?, String?> {
        val lineas = fuentes.flatten().map { prepararFechaHora(it) }
        val sinVencimiento = lineas.filterNot { RE_VENCIMIENTO.containsMatchIn(it) }

        var fecha: String? = null
        var lineaFecha: String? = null
        val ordenFecha = sinVencimiento.filter { RE_ETIQUETA_FECHA.containsMatchIn(it) } +
            sinVencimiento + lineas
        for (l in ordenFecha) {
            val f = fechaDe(l)
            if (f != null) {
                fecha = f
                lineaFecha = l
                break
            }
        }

        val ordenHora = lineas.filter { RE_ETIQUETA_HORA.containsMatchIn(it) } +
            listOfNotNull(lineaFecha) + lineas
        val hora = ordenHora.firstNotNullOfOrNull { horaDe(it) }

        return fecha to hora
    }

    // ================================================================ lectura

    /**
     * @param texto el texto del OCR tal como lo entrega, agrupado por bloques.
     *   Con este se busca el numero, que ya se leia bien asi.
     * @param textoPorFilas el mismo texto reordenado por filas segun su
     *   posicion en la foto, para que cada etiqueta quede junto a su monto
     *   aunque en el papel esten en columnas separadas.
     */
    fun leer(texto: String, textoPorFilas: String = texto): Lectura {
        val L = normalizar(texto)
        val F = normalizar(textoPorFilas)
        val avisos = mutableListOf<String>()
        val fuentes = if (F == L) listOf(F) else listOf(F, L)

        // ------------------------------------------------------------ montos
        val dT = fuentes.flatMap { declarados(it, PATRONES_TOTAL, esIva = false) }.distinct()
        val dN = fuentes.flatMap { declarados(it, PATRONES_NETO, esIva = false) }.distinct()
        val dI = fuentes.flatMap { declarados(it, PATRONES_IVA, esIva = true) }.distinct()
        val todos = fuentes.flatMap { lineas -> lineas.flatMap { montos(it) } }.toSet()

        val m = resolverMontos(dT, dN, dI, todos)
        avisos += m.avisos

        // Control final: lo que sale de aca SIEMPRE cumple neto + IVA = total.
        val t = m.total
        val n = m.neto
        val i = m.iva
        if (t != null && n != null && i != null && abs(n + i - t) > 2) {
            avisos += "Neto + IVA no da el total. Revisa los tres."
        }

        // ------------------------------------------- numero de documento
        val numero = buscarNumero(L) ?: buscarNumero(F)

        // ------------------------------------------------------- fecha y hora
        val (fecha, hora) = buscarFechaHora(listOf(L, F))

        val rut = L.firstNotNullOfOrNull { RE_RUT.find(it)?.groupValues?.get(1) }
        val u4 = L.firstNotNullOfOrNull { RE_TARJETA.find(it)?.groupValues?.get(1) }

        if (m.total == null) avisos += "No se encontró el total. Escríbelo a mano."
        if (numero == null) avisos += "No se encontró el número. Escríbelo a mano."

        return Lectura(
            total = m.total,
            neto = m.neto,
            iva = m.iva,
            numero = numero,
            fecha = fecha,
            hora = hora,
            rut = rut,
            ultimos4 = u4,
            avisos = avisos,
            textoLeido = textoPorFilas,
            calculados = m.calculados,
        )
    }
}
