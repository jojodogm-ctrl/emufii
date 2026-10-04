package eu.emufii.app.ps2

/** YNCF payload of a `BWNETCNF` save; ifc000/dev000 are ciphered with the 8-byte i.Link ID and have no checksum. */
object Ps2NetcnfConfig {

    enum class NvmLayout(
        internal val ilinkIdOffset: Int,
        internal val config1Offset: Int,
        internal val regionParamsOffset: Int,
    ) {
        BEFORE_1_70(0x1C0, 0x300, 0x180),
        FROM_1_70(0x1E0, 0x2B0, 0x180),
    }

    data class BiosVersion(val major: Int, val minor: Int) {
        init {
            require(major >= 0) { "BIOS major version must be non-negative" }
            require(minor in 0..99) { "BIOS minor version must be in 0..99" }
        }

        val nvmLayout: NvmLayout
            get() = if (major > 1 || major == 1 && minor >= 70) {
                NvmLayout.FROM_1_70
            } else {
                NvmLayout.BEFORE_1_70
            }
    }

    /** What ARMSX2 reports for BIOSes with no real console NVRAM. */
    val ARMSX2_CONSOLE_ID: ByteArray =
        byteArrayOf(0x00.toByte(), 0xAC.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xB9.toByte(), 0x86.toByte())

    private const val HEADER = "# <Sony Computer Entertainment Inc.>\n\n"

    private const val IFC_PLAIN = HEADER + "type nic\ndhcp\n"

    private const val DEV_PLAIN =
        HEADER + "type nic\nvendor \"SCE\"\nproduct \"Ethernet (Network Adaptor)\"\nphy_config auto\n"

    val NET_CNF: ByteArray =
        ("# <Sony Computer Entertainment Inc.>\n\n" +
            "interface \"ifc000.dat + dev000.dat\" \"ifc000.dat\" \"dev000.dat\"\n").toByteArray(Charsets.US_ASCII)

    val INDEX: ByteArray =
        ("0,1,net000.cnf,Combination1\n" +
            "1,1,ifc000.dat,Parametre 1\n" +
            "2,1,dev000.dat,SCE/Ethernet (Network Adaptor)\n").toByteArray(Charsets.US_ASCII)

    fun ifcDat(consoleId: ByteArray = ARMSX2_CONSOLE_ID): ByteArray =
        encode(IFC_PLAIN.toByteArray(Charsets.US_ASCII), consoleId)

    fun devDat(consoleId: ByteArray = ARMSX2_CONSOLE_ID): ByteArray =
        encode(DEV_PLAIN.toByteArray(Charsets.US_ASCII), consoleId)

    fun ilinkIdFromNvm(nvm: ByteArray, version: BiosVersion): ByteArray? =
        ilinkIdFromNvm(nvm, version.nvmLayout)

    /** Applies ARMSX2's own sanity checks, which can discard an imported NVM. */
    fun effectiveIlinkIdFromNvm(nvm: ByteArray, version: BiosVersion): ByteArray {
        val layout = version.nvmLayout
        if (nvm.size < NVM_SIZE || nvm.allZero(layout.config1Offset + 0x10, 16)) {
            return ARMSX2_CONSOLE_ID.copyOf()
        }
        if (version.major == 2 && version.minor != 10 && nvm.allZero(layout.regionParamsOffset, 12)) {
            return ARMSX2_CONSOLE_ID.copyOf()
        }
        return ilinkIdFromNvm(nvm, layout) ?: ARMSX2_CONSOLE_ID.copyOf()
    }

    /** Null when bytes 2 and 3 are both zero (unprogrammed). */
    fun ilinkIdFromNvm(nvm: ByteArray, layout: NvmLayout): ByteArray? {
        val offset = layout.ilinkIdOffset
        if (offset + 8 > nvm.size) return null
        val id = nvm.copyOfRange(offset, offset + 8)
        return id.takeIf { it[2].toInt() != 0 || it[3].toInt() != 0 }
    }

    private fun ByteArray.allZero(offset: Int, length: Int): Boolean =
        offset < 0 || offset + length > size || (offset until offset + length).all { this[it].toInt() == 0 }

    /** `rotl16(word, shifts[k % 24]) xor 0xFFFF` per word; an odd trailing byte uses the 8-bit form. */
    fun encode(plain: ByteArray, consoleId: ByteArray): ByteArray {
        require(consoleId.size == 8) { "i.Link ID is 8 bytes" }
        val shifts = shifts(consoleId)
        val out = ByteArray(plain.size)
        var k = 0
        var i = 0
        while (i + 1 < plain.size) {
            val word = (plain[i].toInt() and 0xFF) or ((plain[i + 1].toInt() and 0xFF) shl 8)
            val cipher = rotl16(word, shifts[k % 24]) xor 0xFFFF
            out[i] = (cipher and 0xFF).toByte()
            out[i + 1] = ((cipher shr 8) and 0xFF).toByte()
            k++
            i += 2
        }
        if (plain.size % 2 == 1) {
            val b = plain[plain.size - 1].toInt() and 0xFF
            out[plain.size - 1] = (rotl8(b, shifts[k % 24]) xor 0xFF).toByte()
        }
        return out
    }

    fun decode(cipher: ByteArray, consoleId: ByteArray): ByteArray {
        require(consoleId.size == 8) { "i.Link ID is 8 bytes" }
        val shifts = shifts(consoleId)
        val out = ByteArray(cipher.size)
        var k = 0
        var i = 0
        while (i + 1 < cipher.size) {
            val word = (cipher[i].toInt() and 0xFF) or ((cipher[i + 1].toInt() and 0xFF) shl 8)
            val plain = rotr16(word xor 0xFFFF, shifts[k % 24])
            out[i] = (plain and 0xFF).toByte()
            out[i + 1] = ((plain shr 8) and 0xFF).toByte()
            k++
            i += 2
        }
        if (cipher.size % 2 == 1) {
            val b = cipher[cipher.size - 1].toInt() and 0xFF
            out[cipher.size - 1] = (rotr8(b xor 0xFF, shifts[k % 24])).toByte()
        }
        return out
    }

    /** Not ps2sdk's version, which initialises only seven ID bytes. */
    internal fun shifts(consoleId: ByteArray): IntArray {
        val table = IntArray(24)
        for (i in 0 until 8) {
            val b = consoleId[i].toInt() and 0xFF
            table[3 * i] = (b shr 5) + 1
            table[3 * i + 1] = ((b shr 2) and 7) + 1
            table[3 * i + 2] = (b and 3) + 1
        }
        return table
    }

    private fun rotl16(x: Int, s: Int): Int {
        val n = s and 15
        return ((x shl n) or (x ushr (16 - n))) and 0xFFFF
    }

    private fun rotr16(x: Int, s: Int): Int {
        val n = s and 15
        return ((x ushr n) or (x shl (16 - n))) and 0xFFFF
    }

    private fun rotl8(x: Int, s: Int): Int {
        val n = s and 7
        return ((x shl n) or (x ushr (8 - n))) and 0xFF
    }

    private fun rotr8(x: Int, s: Int): Int {
        val n = s and 7
        return ((x ushr n) or (x shl (8 - n))) and 0xFF
    }

    private const val NVM_SIZE = 1024
}
