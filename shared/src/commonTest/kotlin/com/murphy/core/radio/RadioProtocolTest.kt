package com.murphy.core.radio

import kotlin.test.*

class RadioProtocolTest {
    private val node = "00000000-0000-0000-0000-000000000001"
    private val id = "00000000-0000-0000-0000-000000000002"
    private fun message() = RadioPacket(1, node, "Antônio", "Trail", node, id, "*", "Hello 🏕️", 4, 42)

    @Test fun roundTripsIdentityAndUnicodeMessage() {
        val hello = RadioPacket(0, node, "Antônio", "Trail", node)
        assertEquals(hello, RadioProtocol.decode(RadioProtocol.encode(hello)))
        assertEquals(message(), RadioProtocol.decode(RadioProtocol.encode(message())))
        assertTrue(RadioProtocol.encode(hello).size <= 512)
    }

    @Test fun rejectsUnknownVersionTruncatedTrailingAndInvalidUtf8() {
        val bytes = RadioProtocol.encode(message())
        for (length in bytes.indices) assertNull(RadioProtocol.decode(bytes.copyOf(length)))
        assertNull(RadioProtocol.decode(bytes + byteArrayOf(0)))
        assertNull(RadioProtocol.decode(bytes.copyOf().also { it[3] = 2 }))
        assertNull(RadioProtocol.decode(bytes.copyOf().also { it[8] = 0xFF.toByte() }))
        assertNull(RadioProtocol.decode(ByteArray(8193)))
    }

    @Test fun rejectsInvalidFieldsBeforeEncoding() {
        for (packet in listOf(message().copy(ttl = 9), message().copy(kind = 9),
            message().copy(nodeId = "not-a-node"), message().copy(body = "x".repeat(1001)),
            message().copy(name = "a\u0000b"), message().copy(groupName = ""))) {
            assertFailsWith<IllegalArgumentException> { RadioProtocol.encode(packet) }
        }
    }

    @Test fun reassemblesDefaultMtuFramesAndResetsAfterCompletion() {
        val bytes = RadioProtocol.encode(message().copy(body = "x".repeat(1000)))
        val frames = RadioFragments.split(bytes)
        val assembler = RadioAssembler()
        frames.dropLast(1).forEachIndexed { index, frame ->
            assertTrue(frame.size <= 20)
            val result = assembler.append(frame, index.toLong())
            assertTrue(result.accepted); assertNull(result.packet)
        }
        assertContentEquals(bytes, assembler.append(frames.last(), 500).packet)
        assertFalse(assembler.append(frames.last(), 501).accepted)
    }

    @Test fun rejectsReorderedOversizedAndExpiredFragmentsAndRecovers() {
        val frames = RadioFragments.split(RadioProtocol.encode(message()))
        val assembler = RadioAssembler()
        assertFalse(assembler.append(frames[1], 0).accepted)
        assertTrue(assembler.append(frames[0], 0).accepted)
        assertFalse(assembler.append(frames[2], 1).accepted)
        assertFalse(assembler.append(ByteArray(21), 2).accepted)
        assertTrue(assembler.append(frames[0], 0).accepted)
        assertFalse(assembler.append(frames[1], 60_000).accepted)
        var completed: ByteArray? = null
        frames.forEach { completed = assembler.append(it, 70_000).packet }
        assertEquals(message(), RadioProtocol.decode(completed!!))
    }

    @Test fun slowOrderedTransferDoesNotExtendItsTotalDeadline() {
        val frames = RadioFragments.split(RadioProtocol.encode(message().copy(body = "x".repeat(1000))))
        val assembler = RadioAssembler()
        assertTrue(assembler.append(frames[0], 0).accepted)
        assertTrue(assembler.append(frames[1], 59_999).accepted)
        assertFalse(assembler.append(frames[2], 60_000).accepted)
        assertTrue(assembler.append(frames[0], 70_000).accepted)
        assertFalse(assembler.append(frames[1], 69_999).accepted)
    }

    @Test fun capsAssemblyMemoryEvenWithValidSequenceNumbers() {
        val assembler = RadioAssembler()
        repeat(481) { index ->
            assertTrue(assembler.append(byteArrayOf(if (index == 0) 1 else 0,
                (index ushr 8).toByte(), index.toByte()) + ByteArray(17), index.toLong()).accepted)
        }
        assertFalse(assembler.append(byteArrayOf(0, 1, 225.toByte()) + ByteArray(17), 482).accepted)
    }
}
