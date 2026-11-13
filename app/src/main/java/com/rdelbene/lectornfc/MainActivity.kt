package com.rdelbene.lectornfc

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.nfc.NfcAdapter
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.Ndef
import android.nfc.tech.NfcA
import android.nfc.tech.NfcB
import android.nfc.tech.NfcF
import android.nfc.tech.NfcV
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Lector NFC: detecta el tipo de tarjeta acercada y vuelca en pantalla
 * todo el contenido legible (UID, tecnologias, NDEF, bloques Mifare, paginas Ultralight).
 * Solo lectura. Para Mifare Classic intenta las claves publicas por defecto;
 * los sectores con claves propias quedan marcados como no legibles.
 */
class MainActivity : Activity(), NfcAdapter.ReaderCallback {

    private lateinit var salida: TextView
    private var adapter: NfcAdapter? = null

    // Claves publicas por defecto conocidas de Mifare Classic (las mismas que usan
    // las herramientas estandar). Sirven solo para abrir sectores sin proteger.
    private val clavesPorDefecto = listOf(
        "FFFFFFFFFFFF", "000000000000", "A0A1A2A3A4A5", "B0B1B2B3B4B5",
        "D3F7D3F7D3F7", "AABBCCDDEEFF", "4D3A99C351DD", "1A982C7E459A",
        "A0B0C0D0E0F0", "A1B1C1D1E1F1", "714C5C886E97", "587EE5F9350F",
        "A0478CC39091", "533CB6C723F6", "8FD0A4F256E9"
    ).map { hexAbytes(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(24, 24, 24, 24)
        }

        val titulo = TextView(this).apply {
            text = "Lector NFC"
            textSize = 22f
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextColor(Color.BLACK)
        }
        val ayuda = TextView(this).apply {
            text = "Acerca una tarjeta a la parte de atras del telefono."
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setPadding(0, 8, 0, 16)
        }

        salida = TextView(this).apply {
            text = "Esperando tarjeta..."
            textSize = 12f
            setTextColor(Color.BLACK)
            setTextIsSelectable(true)
            typeface = Typeface.MONOSPACE
            movementMethod = ScrollingMovementMethod()
        }
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            addView(salida)
        }

        raiz.addView(titulo)
        raiz.addView(ayuda)
        raiz.addView(scroll)
        setContentView(raiz)

        adapter = NfcAdapter.getDefaultAdapter(this)
        if (adapter == null) {
            salida.text = "Este telefono no tiene NFC."
        } else if (adapter?.isEnabled == false) {
            salida.text = "El NFC esta apagado. Activalo en los ajustes del telefono."
        }
    }

    override fun onResume() {
        super.onResume()
        val flags = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V or
            NfcAdapter.FLAG_READER_NFC_BARCODE
        adapter?.enableReaderMode(this, this, flags, null)
    }

    override fun onPause() {
        super.onPause()
        adapter?.disableReaderMode(this)
    }

    // Se llama en un hilo de fondo cuando aparece una tarjeta.
    override fun onTagDiscovered(tag: Tag) {
        val r = StringBuilder()
        try {
            r.appendLine("UID: ${bytesAhex(tag.id)}  (${tag.id.size} bytes)")
            r.appendLine("Tecnologias: ${tag.techList.joinToString(", ") { it.substringAfterLast('.') }}")
            r.appendLine("Tipo probable: ${tipoProbable(tag)}")
            r.appendLine("".padEnd(40, '-'))

            tag.techList.forEach { tech ->
                try {
                    when (tech) {
                        NfcA::class.java.name -> detalleNfcA(tag, r)
                        NfcB::class.java.name -> detalleNfcB(tag, r)
                        NfcF::class.java.name -> detalleNfcF(tag, r)
                        NfcV::class.java.name -> detalleNfcV(tag, r)
                        IsoDep::class.java.name -> detalleIsoDep(tag, r)
                        Ndef::class.java.name -> detalleNdef(tag, r)
                        MifareUltralight::class.java.name -> detalleUltralight(tag, r)
                        MifareClassic::class.java.name -> detalleClassic(tag, r)
                    }
                } catch (e: Exception) {
                    r.appendLine("  (error leyendo $tech: ${e.message})")
                }
            }
        } catch (e: Exception) {
            r.appendLine("Error: ${e.message}")
        }
        runOnUiThread { salida.text = r.toString() }
    }

    private fun tipoProbable(tag: Tag): String {
        val techs = tag.techList.map { it.substringAfterLast('.') }
        return when {
            "MifareClassic" in techs -> {
                val mc = MifareClassic.get(tag)
                val tam = when (mc?.type) {
                    MifareClassic.TYPE_CLASSIC -> "Classic"
                    MifareClassic.TYPE_PLUS -> "Plus"
                    MifareClassic.TYPE_PRO -> "Pro"
                    else -> "Classic"
                }
                "Mifare $tam ${mc?.size ?: 0} bytes"
            }
            "MifareUltralight" in techs -> "Mifare Ultralight / NTAG"
            "IsoDep" in techs && "NfcA" in techs -> "ISO 14443-4 tipo A (tarjeta con chip: bancaria, transporte, documento...)"
            "IsoDep" in techs && "NfcB" in techs -> "ISO 14443-4 tipo B"
            "NfcF" in techs -> "FeliCa (JIS X 6319-4)"
            "NfcV" in techs -> "ISO 15693 (vicinity)"
            "NfcA" in techs -> "ISO 14443-3 tipo A"
            "NfcB" in techs -> "ISO 14443-3 tipo B"
            else -> "Desconocido"
        }
    }

    private fun detalleNfcA(tag: Tag, r: StringBuilder) {
        val t = NfcA.get(tag) ?: return
        r.appendLine("NfcA:")
        r.appendLine("  ATQA: ${bytesAhex(t.atqa)}")
        r.appendLine("  SAK: 0x${"%02X".format(t.sak)}")
        r.appendLine("  Max transceive: ${t.maxTransceiveLength} bytes")
    }

    private fun detalleNfcB(tag: Tag, r: StringBuilder) {
        val t = NfcB.get(tag) ?: return
        r.appendLine("NfcB:")
        r.appendLine("  App data: ${bytesAhex(t.applicationData)}")
        r.appendLine("  Protocol info: ${bytesAhex(t.protocolInfo)}")
    }

    private fun detalleNfcF(tag: Tag, r: StringBuilder) {
        val t = NfcF.get(tag) ?: return
        r.appendLine("NfcF (FeliCa):")
        r.appendLine("  Manufacturer: ${bytesAhex(t.manufacturer)}")
        r.appendLine("  System code: ${bytesAhex(t.systemCode)}")
    }

    private fun detalleNfcV(tag: Tag, r: StringBuilder) {
        val t = NfcV.get(tag) ?: return
        r.appendLine("NfcV (ISO 15693):")
        r.appendLine("  DSFID: 0x${"%02X".format(t.dsfId)}")
        r.appendLine("  Response flags: 0x${"%02X".format(t.responseFlags)}")
    }

    private fun detalleIsoDep(tag: Tag, r: StringBuilder) {
        val t = IsoDep.get(tag) ?: return
        r.appendLine("IsoDep (ISO 14443-4):")
        if (t.historicalBytes != null)
            r.appendLine("  Historical bytes (ATS): ${bytesAhex(t.historicalBytes)}")
        if (t.hiLayerResponse != null)
            r.appendLine("  Hi layer response: ${bytesAhex(t.hiLayerResponse)}")
        r.appendLine("  Max transceive: ${t.maxTransceiveLength} bytes")
    }

    private fun detalleNdef(tag: Tag, r: StringBuilder) {
        val ndef = Ndef.get(tag) ?: return
        r.appendLine("NDEF:")
        r.appendLine("  Tipo: ${ndef.type}")
        r.appendLine("  Tamano max: ${ndef.maxSize} bytes, escribible: ${ndef.isWritable}")
        try {
            ndef.connect()
            val msg = ndef.ndefMessage ?: ndef.cachedNdefMessage
            if (msg == null) {
                r.appendLine("  (sin mensaje NDEF grabado)")
            } else {
                msg.records.forEachIndexed { i, rec ->
                    r.appendLine("  Registro $i: ${describirRegistro(rec)}")
                }
            }
        } finally {
            try { ndef.close() } catch (_: Exception) {}
        }
    }

    private fun describirRegistro(rec: NdefRecord): String {
        val tipo = String(rec.type)
        return when {
            rec.tnf == NdefRecord.TNF_WELL_KNOWN && tipo == "T" -> {
                val p = rec.payload
                val largoIdioma = p[0].toInt() and 0x3F
                "Texto = \"" + String(p, 1 + largoIdioma, p.size - 1 - largoIdioma) + "\""
            }
            rec.tnf == NdefRecord.TNF_WELL_KNOWN && tipo == "U" -> {
                val p = rec.payload
                "URI = " + prefijoUri(p[0].toInt()) + String(p, 1, p.size - 1)
            }
            else -> "TNF=${rec.tnf} tipo=$tipo payload=${bytesAhex(rec.payload)}"
        }
    }

    private fun detalleUltralight(tag: Tag, r: StringBuilder) {
        val t = MifareUltralight.get(tag) ?: return
        r.appendLine("Mifare Ultralight / NTAG (lectura de paginas):")
        try {
            t.connect()
            var pagina = 0
            while (pagina < 256) {
                try {
                    val datos = t.readPages(pagina) // devuelve 4 paginas (16 bytes)
                    for (j in 0 until 4) {
                        val p = pagina + j
                        val bloque = datos.copyOfRange(j * 4, j * 4 + 4)
                        r.appendLine("  Pag %03d: %s".format(p, bytesAhex(bloque)))
                    }
                    pagina += 4
                } catch (e: Exception) {
                    break
                }
            }
        } finally {
            try { t.close() } catch (_: Exception) {}
        }
    }

    private fun detalleClassic(tag: Tag, r: StringBuilder) {
        val mc = MifareClassic.get(tag) ?: return
        r.appendLine("Mifare Classic (lectura con claves por defecto):")
        r.appendLine("  Sectores: ${mc.sectorCount}, bloques: ${mc.blockCount}, tamano: ${mc.size} bytes")
        try {
            mc.connect()
            for (sector in 0 until mc.sectorCount) {
                var clave: ByteArray? = null
                var tipoClave = ""
                for (k in clavesPorDefecto) {
                    if (mc.authenticateSectorWithKeyA(sector, k)) { clave = k; tipoClave = "A"; break }
                    if (mc.authenticateSectorWithKeyB(sector, k)) { clave = k; tipoClave = "B"; break }
                }
                if (clave == null) {
                    r.appendLine("  Sector %02d: [bloqueado, clave propia desconocida]".format(sector))
                    continue
                }
                r.appendLine("  Sector %02d: [abierto con clave %s = %s]".format(sector, tipoClave, bytesAhex(clave)))
                val primero = mc.sectorToBlock(sector)
                for (b in 0 until mc.getBlockCountInSector(sector)) {
                    val nb = primero + b
                    try {
                        r.appendLine("    Bloque %03d: %s".format(nb, bytesAhex(mc.readBlock(nb))))
                    } catch (e: Exception) {
                        r.appendLine("    Bloque %03d: (no se pudo leer)".format(nb))
                    }
                }
            }
        } finally {
            try { mc.close() } catch (_: Exception) {}
        }
    }

    // --- utilidades ---

    private fun bytesAhex(b: ByteArray?): String =
        b?.joinToString(" ") { "%02X".format(it) } ?: "-"

    private fun hexAbytes(s: String): ByteArray =
        ByteArray(s.length / 2) { ((Character.digit(s[it * 2], 16) shl 4) + Character.digit(s[it * 2 + 1], 16)).toByte() }

    private fun prefijoUri(code: Int): String = when (code) {
        0x01 -> "http://www."; 0x02 -> "https://www."; 0x03 -> "http://"; 0x04 -> "https://"
        0x05 -> "tel:"; 0x06 -> "mailto:"; else -> ""
    }
}
