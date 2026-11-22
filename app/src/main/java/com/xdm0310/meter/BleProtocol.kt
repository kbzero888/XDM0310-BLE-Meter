package com.xdm0310.meter

import java.util.Locale

data class MeterReading(
    val moduleAddress: Int,
    val resistanceText: String,
    val isOverload: Boolean,
    val voltageText: String,
    val supplyVoltageText: String,
    val batteryPercentText: String,
    val rawHex: String,
    val timestamp: Long = System.currentTimeMillis()
)

object BleProtocol {

    /**
     * 构造轮询指令: [Addr, 0xA6, 0x02, 0x8A, 0x8A, Checksum]
     * 当 Addr = 0x01 时，结果为 01 A6 02 8A 8A BD
     */
    fun buildPollCommand(address: Int): ByteArray {
        val addrByte = (address and 0xFF).toByte()
        val payload = byteArrayOf(
            addrByte,
            0xA6.toByte(),
            0x02.toByte(),
            0x8A.toByte(),
            0x8A.toByte()
        )
        var sum = 0
        for (b in payload) {
            sum += (b.toInt() and 0xFF)
        }
        val checksum = (sum and 0xFF).toByte()
        return payload + checksum
    }

    /**
     * 解析 16 字节内阻仪数据包
     */
    fun parseFrame(bytes: ByteArray): MeterReading? {
        if (bytes.size != 16) return null

        // 校验前 15 字节累加和
        var sum = 0
        for (i in 0..14) {
            sum += (bytes[i].toInt() and 0xFF)
        }
        val expectedChecksum = sum and 0xFF
        val actualChecksum = bytes[15].toInt() and 0xFF
        if (expectedChecksum != actualChecksum) return null

        val moduleAddress = bytes[0].toInt() and 0xFF
        val isValidResistance = (bytes[3].toInt() and 0xFF) == 0
        val resRange = bytes[4].toInt() and 0xFF
        val resRaw = ((bytes[5].toInt() and 0xFF) shl 8) or (bytes[6].toInt() and 0xFF)

        val resistanceText = if (!isValidResistance) {
            "oL"
        } else {
            when (resRange) {
                0 -> String.format(Locale.US, "%.2fΩ", resRaw / 100.0)
                1 -> String.format(Locale.US, "%.2fmΩ", resRaw / 10.0)
                2 -> String.format(Locale.US, "%.2fmΩ", resRaw / 100.0)
                else -> "0.00mΩ"
            }
        }

        val isPositiveVolt = (bytes[7].toInt() and 0xFF) == 0
        val voltRange = bytes[8].toInt() and 0xFF
        val voltRaw = ((bytes[9].toInt() and 0xFF) shl 8) or (bytes[10].toInt() and 0xFF)
        var voltValue = when (voltRange) {
            2 -> voltRaw / 100.0
            3 -> voltRaw / 1000.0
            else -> 0.0
        }
        if (!isPositiveVolt) {
            voltValue = -voltValue
        }
        val voltageText = String.format(Locale.US, "%.3fV", voltValue)

        val supplyRaw = ((bytes[11].toInt() and 0xFF) shl 8) or (bytes[12].toInt() and 0xFF)
        val supplyVoltageText = String.format(Locale.US, "%.3fV", supplyRaw / 1000.0)

        val batteryPercent = bytes[13].toInt() and 0xFF
        val batteryPercentText = "$batteryPercent%"

        return MeterReading(
            moduleAddress = moduleAddress,
            resistanceText = resistanceText,
            isOverload = !isValidResistance,
            voltageText = voltageText,
            supplyVoltageText = supplyVoltageText,
            batteryPercentText = batteryPercentText,
            rawHex = bytesToHex(bytes)
        )
    }

    fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString(" ") { String.format(Locale.US, "%02X", it.toInt() and 0xFF) }
    }

    fun hexToBytes(hexInput: String, appendChecksum: Boolean = false): ByteArray? {
        val cleaned = hexInput.replace("\\s+".toRegex(), "").uppercase(Locale.US)
        if (cleaned.isEmpty() || cleaned.length % 2 != 0) return null
        if (!cleaned.matches(Regex("^[0-9A-F]+$"))) return null

        val result = ByteArray(cleaned.length / 2)
        var sum = 0
        for (i in result.indices) {
            val byteVal = cleaned.substring(i * 2, i * 2 + 2).toInt(16)
            result[i] = byteVal.toByte()
            sum += byteVal
        }
        return if (appendChecksum) {
            result + (sum and 0xFF).toByte()
        } else {
            result
        }
    }
}
