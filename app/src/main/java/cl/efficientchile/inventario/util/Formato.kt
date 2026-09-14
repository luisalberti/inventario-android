package cl.efficientchile.inventario.util

/**
 * Formato de los campos mientras se escriben.
 *
 * La regla de todo este archivo: el vendedor teclea y el campo se ordena
 * solo. No se le avisa que escribio mal, se le impide escribir mal. Un campo
 * de monto que no acepta letras no necesita el mensaje "solo numeros".
 *
 * Todo lo de aca es funcion pura: entra texto, sale texto. Se puede probar
 * sin levantar la app, y el algoritmo del RUT se verifico caso por caso antes
 * de escribirlo aca.
 */
object Formato {

    // ------------------------------------------------------------------ RUT

    /** Deja solo digitos y la K, en mayuscula. */
    private fun limpiarRut(s: String): String =
        s.filter { it.isDigit() || it == 'k' || it == 'K' }.uppercase()

    /**
     * Formatea el RUT mientras se escribe. El ULTIMO caracter tecleado es el
     * digito verificador: es lo que hace la gente y lo que espera ver.
     *
     *   "7"         -> "7"
     *   "76"        -> "7-6"
     *   "76853"     -> "7.685-3"
     *   "768535132" -> "76.853.513-2"
     */
    fun rut(entrada: String): String {
        /* El corte va ANTES de separar cuerpo y verificador, no despues.
           Cortando el cuerpo ya separado, una tecla de mas empujaba el
           verificador y convertia un RUT correcto en uno invalido:
           76.853.513-2 se volvia 76.853.513-9 al apretar una tecla de sobra.
           Cortando aca, las teclas que sobran simplemente no entran. */
        val s = limpiarRut(entrada).take(9)
        if (s.isEmpty()) return ""

        val dv = s.last()
        // La K solo vale como verificador. Una K en medio se descarta.
        val cuerpo = s.dropLast(1).filter { it.isDigit() }
        if (cuerpo.isEmpty()) return dv.toString()

        val sb = StringBuilder()
        for ((i, c) in cuerpo.withIndex()) {
            if (i > 0 && (cuerpo.length - i) % 3 == 0) sb.append('.')
            sb.append(c)
        }
        return "$sb-$dv"
    }

    /**
     * Modulo 11. Solo true si el RUT esta completo y el verificador calza.
     *
     * Sirve para pintar el campo en rojo, no para bloquear el boton: hay
     * empresas extranjeras y casos raros, y dejar a un vendedor sin poder
     * cerrar una venta por un RUT que el sistema cree malo es peor que
     * guardar un RUT malo.
     */
    fun rutValido(entrada: String): Boolean {
        val s = limpiarRut(entrada)
        if (s.length < 2) return false
        val dv = s.last()
        val cuerpo = s.dropLast(1)
        if (!cuerpo.all { it.isDigit() }) return false
        if (cuerpo.length < 7) return false

        var suma = 0
        var mult = 2
        for (i in cuerpo.indices.reversed()) {
            suma += (cuerpo[i] - '0') * mult
            mult = if (mult == 7) 2 else mult + 1
        }
        val resto = 11 - (suma % 11)
        val esperado = when (resto) {
            11 -> '0'
            10 -> 'K'
            else -> '0' + resto
        }
        return dv == esperado
    }

    // -------------------------------------------------------------- numeros

    /** Solo digitos. Lo que se pega desde otra app tambien pasa por aca. */
    fun soloDigitos(entrada: String, maximo: Int = 12): String =
        entrada.filter { it.isDigit() }.take(maximo)

    /**
     * Monto en pesos, con puntos de miles mientras se escribe.
     * "20000" -> "20.000". Sin decimales: en Chile el peso no los usa.
     */
    fun monto(entrada: String): String {
        val d = soloDigitos(entrada, 10).trimStart('0')
        if (d.isEmpty()) return ""
        val sb = StringBuilder()
        for ((i, c) in d.withIndex()) {
            if (i > 0 && (d.length - i) % 3 == 0) sb.append('.')
            sb.append(c)
        }
        return sb.toString()
    }

    /** El numero limpio que va al servidor. "20.000" -> 20000.0 */
    fun montoValor(entrada: String): Double? =
        entrada.filter { it.isDigit() }.toDoubleOrNull()

    /**
     * Numero de documento u operacion tal como viene impreso en el voucher.
     *
     * No es solo numerico a proposito: los vouchers traen codigos como
     * "A0004521" y guiones. Se limpia el espacio y se sube a mayuscula, que
     * es lo unico que causa duplicados al buscar despues.
     */
    fun documento(entrada: String, maximo: Int = 30): String =
        entrada.filter { it.isLetterOrDigit() || it == '-' }.uppercase().take(maximo)

    // --------------------------------------------------------------- textos

    /**
     * Nombres propios: primera letra de cada palabra en mayuscula.
     *
     * No toca lo que el usuario escribio en mayuscula a proposito ("SPA",
     * "LTDA"): solo sube la primera letra, nunca baja las demas. Corregirle
     * a alguien su propia razon social es peor que dejarla como la escribio.
     */
    fun nombrePropio(entrada: String): String =
        entrada.split(' ').joinToString(" ") { p ->
            if (p.isEmpty()) p else p[0].uppercaseChar() + p.substring(1)
        }
}
