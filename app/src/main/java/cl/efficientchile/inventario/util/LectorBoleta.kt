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
 * ## Neto, IVA y total se confirman con la cuenta
 *
 * En el papel, NETO / IVA / TOTAL suelen ir en columna abajo a la derecha, y
 * los montos casi nunca quedan exactamente a la altura de su etiqueta. Por eso
 * una etiqueta no se casa solo con el monto de su misma linea: tambien con los
 * de la linea de arriba y la de abajo. Y ningun trio se acepta porque "estaba
 * al lado": se acepta si cumple la cuenta
 *
 *     IVA   = 19% del neto
 *     TOTAL = neto + IVA
 *
 * Si ninguna combinacion de etiquetas cuadra, se busca el trio entre todos los
 * montos del papel. Solo si eso tambien falla se usan los montos sin
 * confirmar, y se avisa.
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
        /** Lo que entrego el OCR, ordenado por filas. Para diagnosticar. */
        val textoLeido: String = "",
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
        """\bTOTAL\b|\bIVA\b|\bNETO\b|\bMONTO\b|\bSUBTOTAL\b|\bCOMPRA\b|\bIMPORTE\b|\bPRECIO\b""")

    private val RE_MONTO = Regex(MONTO)
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
    private val RE_SIN_IVA = Regex("""SIN IVA|EXENT""")
    // "IVA 19%" y "19% IVA": ese 19 es la tasa, no plata.
    private val RE_PORCENTAJE = Regex("""\d{1,2}(?:[.,]\d+)?\s*%""")
    // El OCR lee el cero como letra O: "$1O.4O4".
    private val RE_O_POR_CERO = Regex("""(?<=\d)O(?=[\d.,]|$)|(?<=[$.,])O|O(?=\d)""")

    /* Etiquetas de cada monto, en orden de prioridad.

       "TOTAL NETO" y "TOTAL IVA" NO son el total. La boleta de Los Cisnes
       imprime las tres lineas en ese orden, y quedarse con la primera daba
       $834 como total de una compra de $2.182.

       "MONTO VENTA" en un voucher Transbank es el NETO, no el total; en un
       voucher GetNet, "Monto" a secas es el total. Por eso se distinguen.

       "PRECIO" tambien marca el total en algunas boletas, pero no en el
       encabezado "PRECIO UNITARIO" de la lista de articulos. */
    private val PATRONES_TOTAL = listOf(
        Regex("""\bTOTAL\b(?!\s*:?\s*(?:NETO|IVA|AFECTO|EXENTO))"""),
        Regex("""\bVALOR TOTAL\b|\bIMPORTE\b|\bA PAGAR\b"""),
        Regex("""\bMONTO\b(?!\s*:?\s*(?:VENTA|NETO|AFECTO|EXENTO))"""),
        Regex("""\bPRECIO\b(?!\s*(?:UNIT|U\b|X\b))"""),
    )
    private val PATRONES_NETO = listOf(
        Regex("""\bNETO\b|\bAFECTO\b"""),
        Regex("""\bMONTO VENTA\b|\bSUB\s?TOTAL\b"""),
        Regex("""\bCOMPRA\b(?!\s*AFECTA)"""),
    )
    private val PATRONES_IVA = listOf(Regex("""\bIVA\b"""))

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
     * Todos los montos de la linea, del ULTIMO al primero.
     *
     * El ultimo va primero porque en "19% IVA: 3.051" la plata viene al final.
     * Antes de buscar se borran las cosas que parecen montos y no lo son:
     * porcentajes, RUT, fechas, horas y los 4 digitos de la tarjeta.
     */
    private fun montos(linea: String): List<Int> {
        var limpia = RE_O_POR_CERO.replace(linea, "0")
        for (re in listOf(RE_PORCENTAJE, RE_RUT_EN_LINEA, RE_FECHA, RE_FECHA_ISO,
                RE_HORA, RE_TARJETA_LIMPIAR)) {
            limpia = re.replace(limpia, " ")
        }
        // En pesos chilenos no hay decimales: un punto o una coma dentro de un
        // monto siempre es separador de miles.
        return RE_MONTO.findAll(limpia)
            .mapNotNull { it.groupValues[1].replace(".", "").replace(",", "").toIntOrNull() }
            .filter { it > 0 }
            .toList()
            .asReversed()
    }

    /**
     * Montos que podrian ir con una etiqueta, en orden de preferencia.
     *
     * Primero los de la misma linea. Si la linea de la etiqueta no trae monto
     * (la columna de montos quedo un poco mas arriba o mas abajo), se toman
     * los de la linea siguiente y la anterior. La cuenta decide despues cual
     * era.
     */
    private fun candidatos(lineas: List<String>, patrones: List<Regex>, esIva: Boolean): List<Int> {
        val out = LinkedHashSet<Int>()
        for (re in patrones) {
            val vecinos = mutableListOf<Int>()
            for ((k, l) in lineas.withIndex()) {
                if (!re.containsMatchIn(l)) continue
                if (esIva && RE_SIN_IVA.containsMatchIn(l)) continue
                val propios = montos(l)
                if (propios.isNotEmpty()) {
                    out += propios
                } else {
                    lineas.getOrNull(k + 1)?.let { vecinos += montos(it) }
                    lineas.getOrNull(k - 1)?.let { vecinos += montos(it) }
                }
            }
            out += vecinos
        }
        // Un 19 suelto junto a "IVA" es la tasa, no el impuesto.
        return out.filter { !(esIva && it == 19) }
    }

    private fun ivaDe(neto: Int): Int = (neto * TASA_IVA).roundToInt()

    /**
     * La cuenta que tiene que cumplir cualquier boleta chilena afecta.
     *
     * Se aceptan 2 pesos de diferencia por redondeo, o un 0,2% del neto en
     * facturas grandes, donde cada linea redondea su propio IVA.
     */
    private fun cuadra(neto: Int, iva: Int, total: Int): Boolean =
        neto > 0 && iva > 0 &&
            abs(neto + iva - total) <= 2 &&
            abs(iva - ivaDe(neto)) <= maxOf(2, neto / 500)

    private data class Montos(
        val total: Int?,
        val neto: Int?,
        val iva: Int?,
        val avisos: List<String>,
    )

    /**
     * Busca entre TODOS los montos del papel un trio que cumpla la cuenta.
     * Se prefiere el trio cuyo total tambien aparecio junto a una etiqueta de
     * total; entre iguales, el de total mas grande.
     */
    private fun trioPorCuenta(todos: Set<Int>, totalesRotulados: List<Int>): Triple<Int, Int, Int>? {
        val lista = todos.filter { it >= 10 }
        var mejor: Triple<Int, Int, Int>? = null
        for (n in lista) {
            val esperado = ivaDe(n)
            val tolerancia = maxOf(2, n / 500)
            for (i in lista) {
                if (abs(i - esperado) > tolerancia) continue
                for (t in lista) {
                    if (!cuadra(n, i, t)) continue
                    val m = mejor
                    val rotulado = t in totalesRotulados
                    val mRotulado = m != null && m.third in totalesRotulados
                    if (m == null || (rotulado && !mRotulado) ||
                        (rotulado == mRotulado && t > m.third)) {
                        mejor = Triple(n, i, t)
                    }
                }
            }
        }
        return mejor
    }

    private fun resolverMontos(
        cT: List<Int>, cN: List<Int>, cI: List<Int>, todos: Set<Int>,
    ): Montos {
        // 1. Las tres etiquetas encontradas y la cuenta cuadra.
        for (t in cT) for (n in cN) for (i in cI) {
            if (cuadra(n, i, t)) return Montos(t, n, i, emptyList())
        }

        // 2. Dos etiquetas que cuadran; la tercera se calcula.
        for (t in cT) for (i in cI) {
            val n = t - i
            if (cuadra(n, i, t)) {
                val aviso = if (cN.isEmpty()) "Neto calculado como total menos IVA."
                else "El neto se leyó mal (${cN.first()}). Se usó total menos IVA."
                return Montos(t, n, i, listOf(aviso))
            }
        }
        for (t in cT) for (n in cN) {
            val i = t - n
            if (cuadra(n, i, t)) {
                val aviso = if (cI.isEmpty()) "IVA calculado como total menos neto."
                else "El IVA se leyó mal (${cI.first()}). Se usó total menos neto."
                return Montos(t, n, i, listOf(aviso))
            }
        }
        /* El total emborronado: el voucher de Electronica Segovia se lee
           "TOTAL: $21." porque el resto quedo tapado por una mancha. Si neto
           e IVA cuadran entre si, manda neto + IVA. */
        for (n in cN) for (i in cI) {
            if (cuadra(n, i, n + i)) {
                val aviso = if (cT.isEmpty()) "Total calculado desde neto + IVA."
                else "El total se leyó mal (${cT.first()}). Se usó neto + IVA."
                return Montos(n + i, n, i, listOf(aviso))
            }
        }

        // 3. Ninguna etiqueta sirvio: el trio se busca solo por la cuenta.
        trioPorCuenta(todos, cT)?.let { (n, i, t) ->
            return Montos(t, n, i, listOf(
                "Neto, IVA y total se reconocieron por la cuenta (neto + 19% = total), " +
                    "no por su etiqueta. Revísalos."))
        }

        // 4. Nada cuadra. Se usa lo que haya y se avisa.
        val t = cT.firstOrNull()
        val n = cN.firstOrNull()
        val i = cI.firstOrNull()
        return when {
            t != null && n == null && i == null -> {
                // En Chile el precio va con IVA incluido, asi que se derivan.
                val nn = (t / (1 + TASA_IVA)).roundToInt()
                Montos(t, nn, t - nn, listOf("Neto e IVA calculados desde el total."))
            }
            t != null -> Montos(t, n, i, listOf(
                "El neto y el IVA no cuadran con el total (neto + 19% = total). Revisa los tres."))
            else -> Montos(null, n, i, emptyList())
        }
    }

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
        val cT = fuentes.flatMap { candidatos(it, PATRONES_TOTAL, esIva = false) }.distinct()
        val cN = fuentes.flatMap { candidatos(it, PATRONES_NETO, esIva = false) }.distinct()
        val cI = fuentes.flatMap { candidatos(it, PATRONES_IVA, esIva = true) }.distinct()
        val todos = fuentes.flatMap { lineas -> lineas.flatMap { montos(it) } }.toSet()

        val m = resolverMontos(cT, cN, cI, todos)
        avisos += m.avisos

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
        )
    }
}
