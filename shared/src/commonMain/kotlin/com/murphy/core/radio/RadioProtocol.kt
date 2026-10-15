package com.murphy.core.radio

/** Version-one, unauthenticated development wire format. Security is a separate release gate. */
public object RadioProtocol {
    public const val SERVICE_UUID: String = "725be150-3d62-4e2a-a26f-3edca38d4001"
    public const val IDENTITY_UUID: String = "725be150-3d62-4e2a-a26f-3edca38d4002"
    public const val WRITE_UUID: String = "725be150-3d62-4e2a-a26f-3edca38d4003"
    public const val NOTIFY_UUID: String = "725be150-3d62-4e2a-a26f-3edca38d4004"
    public const val MAX_PACKET_BYTES: Int = 8192
    public const val IDENTITY: Int = 0
    public const val MESSAGE: Int = 1

    private val uuid: Regex = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

    public fun encode(packet: RadioPacket): ByteArray {
        require(valid(packet)) { "Invalid Murphy radio packet" }
        val bytes = mutableListOf<Byte>(77, 85, 82, 1, packet.kind.toByte(), packet.ttl.toByte())
        for (value in listOf(packet.groupName, packet.nodeId, packet.name, packet.groupId,
            packet.messageId, packet.destination, packet.body)) {
            val data = value.encodeToByteArray()
            bytes.add((data.size ushr 8).toByte()); bytes.add(data.size.toByte())
            bytes.addAll(data.toList())
        }
        for (shift in 56 downTo 0 step 8) bytes.add((packet.createdAtMillis ushr shift).toByte())
        require(bytes.size <= MAX_PACKET_BYTES)
        return bytes.toByteArray()
    }

    /** Malformed/untrusted packets are rejected, never thrown across the native callback boundary. */
    public fun decode(bytes: ByteArray): RadioPacket? {
        if (bytes.size !in 26..MAX_PACKET_BYTES) return null
        return try {
            val reader = Reader(bytes)
            if (reader.byte() != 77 || reader.byte() != 85 || reader.byte() != 82 || reader.byte() != 1) return null
            val kind = reader.byte(); val ttl = reader.byte()
            val group = reader.string(160); val node = reader.string(36); val name = reader.string(160)
            val groupId = reader.string(36)
            val id = reader.string(36); val destination = reader.string(36); val body = reader.string(4000)
            var created = 0L
            repeat(8) { created = (created shl 8) or reader.byte().toLong() }
            if (!reader.finished()) return null
            RadioPacket(kind, node, name, group, groupId, id, destination, body, ttl, created).takeIf(::valid)
        } catch (_: Exception) { null }
    }

    private fun valid(packet: RadioPacket): Boolean =
        uuid.matches(packet.nodeId) && uuid.matches(packet.groupId) && validName(packet.name) && validName(packet.groupName) &&
            when (packet.kind) {
                IDENTITY -> packet.messageId.isEmpty() && packet.body.isEmpty() &&
                    packet.destination.isEmpty() && packet.ttl == 0
                MESSAGE -> uuid.matches(packet.messageId) &&
                    (packet.destination == "*" || uuid.matches(packet.destination)) &&
                    packet.body.isNotBlank() && packet.body.length <= 1000 &&
                    packet.body.none { it.code < 32 && it !in "\n\r\t" } && packet.ttl in 0..8
                else -> false
            }

    public fun validName(name: String): Boolean = name.isNotBlank() && name.length <= 40 &&
        name.none { it.code < 32 || it.code == 127 }

    private class Reader(val bytes: ByteArray) {
        var position = 0
        fun byte(): Int {
            require(position < bytes.size)
            return bytes[position++].toInt() and 255
        }
        fun string(limit: Int): String {
            val length = (byte() shl 8) or byte()
            require(length <= limit && position + length <= bytes.size)
            val value = bytes.decodeToString(position, position + length, throwOnInvalidSequence = true)
            position += length
            return value
        }
        fun finished(): Boolean = position == bytes.size
    }
}

public data class RadioPacket(
    public val kind: Int,
    public val nodeId: String,
    public val name: String,
    public val groupName: String,
    public val groupId: String,
    public val messageId: String = "",
    public val destination: String = "",
    public val body: String = "",
    public val ttl: Int = 0,
    public val createdAtMillis: Long = 0,
)

/** 20-byte ATT values work with the default 23-byte MTU, including cross-platform links. */
public object RadioFragments {
    public fun split(packet: ByteArray): List<ByteArray> {
        require(packet.isNotEmpty() && packet.size <= RadioProtocol.MAX_PACKET_BYTES)
        return packet.toList().chunked(17).mapIndexed { index, data ->
            val flags = (if (index == 0) 1 else 0) or (if ((index + 1) * 17 >= packet.size) 2 else 0)
            byteArrayOf(flags.toByte(), (index ushr 8).toByte(), index.toByte()) + data.toByteArray()
        }
    }
}

/** One assembler per ordered connection. Native adapters must serialize their frame queues. */
public class RadioAssembler {
    private val bytes: ByteArray = ByteArray(RadioProtocol.MAX_PACKET_BYTES)
    private var length: Int = 0
    private var sequence: Int = 0
    private var startedAt: Long? = null

    public fun reset() { length = 0; sequence = 0; startedAt = null }

    public fun append(frame: ByteArray, atMillis: Long): RadioFragmentResult {
        val start = startedAt
        if (start != null && (atMillis < start || atMillis - start >= 60_000)) reset()
        if (frame.size !in 4..20) return reject()
        val flags = frame[0].toInt() and 255
        val index = ((frame[1].toInt() and 255) shl 8) or (frame[2].toInt() and 255)
        if (flags !in 0..3) return reject()
        if (flags and 1 != 0) {
            reset()
            if (index != 0) return reject()
            startedAt = atMillis
        }
        if (startedAt == null || index != sequence || length + frame.size - 3 > bytes.size) return reject()
        frame.copyInto(bytes, length, 3)
        length += frame.size - 3
        sequence++
        if (flags and 2 == 0) return RadioFragmentResult(true, null)
        val result = bytes.copyOf(length)
        reset()
        return RadioFragmentResult(true, result)
    }

    private fun reject(): RadioFragmentResult { reset(); return RadioFragmentResult(false, null) }
}

public data class RadioFragmentResult(public val accepted: Boolean, public val packet: ByteArray?)
