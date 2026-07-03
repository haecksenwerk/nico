package com.haecksenwerk.nico.ptp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

// ── Data structures ───────────────────────────────────────────────────────────

data class PtpResponse(
    val code: Int,
    val transactionId: Int,
    val params: List<Long>,
)

data class PtpDataResponse(
    val payload: ByteArray,     // data container body (header stripped)
    val response: PtpResponse,
) {
    override fun equals(other: Any?) = other is PtpDataResponse &&
            payload.contentEquals(other.payload) && response == other.response
    override fun hashCode() = 31 * payload.contentHashCode() + response.hashCode()
}

class PtpException(message: String) : Exception(message)

// ── Session ───────────────────────────────────────────────────────────────────

/**
 * PTP protocol layer.  Builds / parses containers and sequences the USB
 * exchanges.  Transaction IDs start at 1 and increment monotonically.
 *
 * Pattern A — command only (OpenSession, InitiateCapture …):
 *   bulkOut(cmd) → bulkIn(response)
 *
 * Pattern B — command + data in (GetDeviceInfo, GetDevicePropValue …):
 *   bulkOut(cmd) → bulkIn(data) → bulkIn(response)
 *   GetDeviceInfo is the canonical example: always two bulkIn reads, not one.
 */
class PtpSession(private val transport: PtpTransport) {

    private var txCounter = 1
    private fun nextTxId() = txCounter++

    // ── Container building ────────────────────────────────────────────────────

    private fun buildCommand(opCode: Int, txId: Int, params: List<Long>): ByteArray {
        val size = PtpConstants.CONTAINER_HEADER_SIZE + params.size * 4
        return ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(size)
            putShort(PtpConstants.CONTAINER_TYPE_COMMAND.toShort())
            putShort(opCode.toShort())   // toShort() preserves bits for 0x9xxx codes
            putInt(txId)
            params.forEach { putInt(it.toInt()) }
        }.array()
    }

    // ── Container parsing ─────────────────────────────────────────────────────

    private fun parseResponse(raw: ByteArray): PtpResponse {
        if (raw.size < PtpConstants.CONTAINER_HEADER_SIZE)
            throw PtpException("Response too short (${raw.size} bytes)")
        val buf = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
        val length = buf.int
        buf.short                                       // type (ignored here)
        val code = buf.short.toInt() and 0xFFFF
        val txId = buf.int
        val paramBytes = (length - PtpConstants.CONTAINER_HEADER_SIZE).coerceAtLeast(0)
        val paramCount = paramBytes / 4
        val params = (0 until paramCount).map { buf.int.toLong() and 0xFFFFFFFFL }
        return PtpResponse(code, txId, params)
    }

    private fun containerType(raw: ByteArray): Int {
        if (raw.size < 6) return -1
        return ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
            .run { getInt(0); getShort(4).toInt() and 0xFFFF }
    }

    // ── Exchange patterns ─────────────────────────────────────────────────────

    /** Pattern A: command → response. */
    suspend fun sendCommand(opCode: Int, params: List<Long> = emptyList()): PtpResponse {
        val txId = nextTxId()
        val n = transport.bulkOut(buildCommand(opCode, txId, params))
        if (n < 0) throw PtpException("bulkOut failed (op=0x${opCode.toString(16)})")
        val raw = transport.bulkIn() ?: throw PtpException("No response (op=0x${opCode.toString(16)})")
        return parseResponse(raw)
    }

    /**
     * Pattern C: command → data out → response.
     * Used by SetDevicePropValue: send the command container, then a data container
     * carrying the new value, then read the response.
     */
    suspend fun sendCommandSendData(
        opCode: Int,
        params: List<Long> = emptyList(),
        data: ByteArray,
    ): PtpResponse {
        val txId = nextTxId()
        var n = transport.bulkOut(buildCommand(opCode, txId, params))
        if (n < 0) throw PtpException("bulkOut cmd failed (op=0x${opCode.toString(16)})")
        n = transport.bulkOut(buildDataContainer(opCode, txId, data))
        if (n < 0) throw PtpException("bulkOut data failed (op=0x${opCode.toString(16)})")
        val raw = transport.bulkIn() ?: throw PtpException("No response (op=0x${opCode.toString(16)})")
        return parseResponse(raw)
    }

    private fun buildDataContainer(opCode: Int, txId: Int, data: ByteArray): ByteArray {
        val size = PtpConstants.CONTAINER_HEADER_SIZE + data.size
        return ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(size)
            putShort(PtpConstants.CONTAINER_TYPE_DATA.toShort())
            putShort(opCode.toShort())
            putInt(txId)
            put(data)
        }.array()
    }

    /**
     * Like sendCommandReadData but continues reading bulk packets until the full data
     * container declared size is consumed.  Needed for payloads > MAX_BULK_BUFFER (e.g.
     * GetPartialObject requesting 512 KB).
     */
    suspend fun sendCommandReadAllData(
        opCode: Int,
        params: List<Long> = emptyList(),
    ): PtpDataResponse {
        val txId = nextTxId()
        val n = transport.bulkOut(buildCommand(opCode, txId, params))
        if (n < 0) throw PtpException("bulkOut failed (op=0x${opCode.toString(16)})")

        val first = transport.bulkIn() ?: throw PtpException("No data (op=0x${opCode.toString(16)})")
        if (containerType(first) != PtpConstants.CONTAINER_TYPE_DATA) {
            return PtpDataResponse(ByteArray(0), parseResponse(first))
        }

        val totalContainerLen = ByteBuffer.wrap(first).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
        val totalDataLen = (totalContainerLen - PtpConstants.CONTAINER_HEADER_SIZE).coerceAtLeast(0)
        val headerEnd = PtpConstants.CONTAINER_HEADER_SIZE.coerceAtMost(first.size)
        val firstDataLen = (first.size - headerEnd).coerceAtLeast(0)

        val payload = if (firstDataLen.toLong() >= totalDataLen) {
            first.copyOfRange(headerEnd, headerEnd + totalDataLen.toInt())
        } else {
            val accum = ByteArrayOutputStream(totalDataLen.toInt())
            accum.write(first, headerEnd, firstDataLen)
            var readComplete = true
            while (accum.size().toLong() < totalDataLen) {
                val chunk = transport.bulkIn() ?: run { readComplete = false; break }
                val toWrite = minOf(chunk.size.toLong(), totalDataLen - accum.size()).toInt()
                accum.write(chunk, 0, toWrite)
            }
            if (!readComplete) {
                // Flush any data the camera is still sending so the next PTP command
                // does not read this transaction's leftovers instead of its own response.
                transport.drainIn()
                throw PtpException("Incomplete data read (op=0x${opCode.toString(16)})")
            }
            accum.toByteArray()
        }

        val respRaw = transport.bulkIn()
            ?: throw PtpException("No response after data (op=0x${opCode.toString(16)})")
        return PtpDataResponse(payload, parseResponse(respRaw))
    }

    /**
     * Pattern D: command → data in (streamed to [out]).
     * Used for GetObject: streams the full object data directly to an OutputStream
     * without accumulating it in a ByteArray.  Returns bytes written.
     */
    suspend fun sendCommandStreamData(
        opCode: Int,
        params: List<Long> = emptyList(),
        out: OutputStream,
    ): Long {
        val txId = nextTxId()
        val n = transport.bulkOut(buildCommand(opCode, txId, params))
        if (n < 0) throw PtpException("bulkOut failed (op=0x${opCode.toString(16)})")

        val first = transport.bulkIn() ?: throw PtpException("No data (op=0x${opCode.toString(16)})")
        if (containerType(first) != PtpConstants.CONTAINER_TYPE_DATA) {
            val resp = parseResponse(first)
            if (resp.code != PtpConstants.RC_OK)
                throw PtpException("GetObject: 0x${resp.code.toString(16)}")
            return 0L
        }

        val totalContainerLen = ByteBuffer.wrap(first).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
        val totalDataLen = (totalContainerLen - PtpConstants.CONTAINER_HEADER_SIZE).coerceAtLeast(0)
        val headerEnd = PtpConstants.CONTAINER_HEADER_SIZE.coerceAtMost(first.size)

        var written = 0L
        val toWriteFirst = minOf((first.size - headerEnd).toLong(), totalDataLen).toInt()
        if (toWriteFirst > 0) {
            withContext(Dispatchers.IO) {
                out.write(first, headerEnd, toWriteFirst)
            }
            written += toWriteFirst
        }
        while (written < totalDataLen) {
            val chunk = transport.bulkIn() ?: break
            val toWrite = minOf(chunk.size.toLong(), totalDataLen - written).toInt()
            withContext(Dispatchers.IO) {
                out.write(chunk, 0, toWrite)
            }
            written += toWrite
        }

        val respRaw = transport.bulkIn()
            ?: throw PtpException("No response after stream (op=0x${opCode.toString(16)})")
        val resp = parseResponse(respRaw)
        if (resp.code != PtpConstants.RC_OK)
            throw PtpException("GetObject: 0x${resp.code.toString(16)}")
        return written
    }

    /** Pattern B: command → data container → response. */
    suspend fun sendCommandReadData(
        opCode: Int,
        params: List<Long> = emptyList(),
    ): PtpDataResponse {
        val txId = nextTxId()
        val n = transport.bulkOut(buildCommand(opCode, txId, params))
        if (n < 0) throw PtpException("bulkOut failed (op=0x${opCode.toString(16)})")

        val first = transport.bulkIn() ?: throw PtpException("No data (op=0x${opCode.toString(16)})")

        return if (containerType(first) == PtpConstants.CONTAINER_TYPE_DATA) {
            val payload = first.copyOfRange(
                PtpConstants.CONTAINER_HEADER_SIZE.coerceAtMost(first.size),
                first.size,
            )
            val respRaw = transport.bulkIn()
                ?: throw PtpException("No response after data (op=0x${opCode.toString(16)})")
            PtpDataResponse(payload, parseResponse(respRaw))
        } else {
            // Camera skipped data phase (error path)
            PtpDataResponse(ByteArray(0), parseResponse(first))
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    fun requireOk(resp: PtpResponse, opName: String) {
        if (resp.code != PtpConstants.RC_OK)
            throw PtpException("$opName → 0x${resp.code.toString(16)}")
    }

    // ── Standard operations ───────────────────────────────────────────────────

    suspend fun openSession(sessionId: Int = 1): PtpResponse {
        val resp = sendCommand(PtpConstants.OP_OPEN_SESSION, listOf(sessionId.toLong()))
        // Camera keeps a session open after cable pull; close the stale one and retry.
        if (resp.code == PtpConstants.RC_SESSION_ALREADY_OPEN) {
            try { sendCommand(PtpConstants.OP_CLOSE_SESSION) } catch (_: Exception) {}
            return sendCommand(PtpConstants.OP_OPEN_SESSION, listOf(sessionId.toLong()))
        }
        return resp
    }

    suspend fun closeSession(): PtpResponse =
        sendCommand(PtpConstants.OP_CLOSE_SESSION)

    suspend fun getDeviceInfo(): ByteArray =
        sendCommandReadData(PtpConstants.OP_GET_DEVICE_INFO).payload

    /**
     * Returns (manufacturer, model) from the DeviceInfo dataset, e.g. ("Nikon", "Z fc").
     * DeviceInfo layout (all little-endian):
     *   UINT16 StandardVersion
     *   UINT32 VendorExtensionID
     *   UINT16 VendorExtensionVersion
     *   PTPString VendorExtensionDesc
     *   UINT16 FunctionalMode
     *   UINT16[] OperationsSupported / EventsSupported / DevicePropertiesSupported
     *   UINT16[] CaptureFormats / ImageFormats
     *   PTPString Manufacturer   ← first string extracted
     *   PTPString Model          ← second string extracted
     * PTPString = UINT8 numChars (incl. null) + numChars × UINT16 (UTF-16LE)
     */
    suspend fun getDeviceMakeModel(): Pair<String, String> = try {
        val buf = ByteBuffer.wrap(getDeviceInfo()).order(ByteOrder.LITTLE_ENDIAN)
        buf.short                       // StandardVersion
        buf.int                         // VendorExtensionID
        buf.short                       // VendorExtensionVersion
        buf.skipPtpString()             // VendorExtensionDesc
        buf.short                       // FunctionalMode
        buf.skipUint16Array()           // OperationsSupported
        buf.skipUint16Array()           // EventsSupported
        buf.skipUint16Array()           // DevicePropertiesSupported
        buf.skipUint16Array()           // CaptureFormats
        buf.skipUint16Array()           // ImageFormats
        val manufacturer = buf.readPtpString()
        val model        = buf.readPtpString()
        manufacturer to model
    } catch (_: Exception) { "" to "" }

    private fun ByteBuffer.skipPtpString() {
        val n = (get().toInt() and 0xFF)
        if (n > 0) repeat(n) { short }
    }

    private fun ByteBuffer.skipUint16Array() {
        val n = int
        if (n > 0) repeat(n) { short }
    }

    private fun ByteBuffer.readPtpString(): String {
        val n = (get().toInt() and 0xFF)
        if (n == 0) return ""
        val sb = StringBuilder(n)
        repeat(n) { i ->
            val c = (short.toInt() and 0xFFFF).toChar()
            if (i < n - 1) sb.append(c)   // skip null terminator
        }
        return sb.toString().trim()
    }

    suspend fun getStorageIds(): List<Long> {
        val dr = sendCommandReadData(PtpConstants.OP_GET_STORAGE_IDS)
        requireOk(dr.response, "GetStorageIDs")
        if (dr.payload.size < 4) return emptyList()
        val buf = ByteBuffer.wrap(dr.payload).order(ByteOrder.LITTLE_ENDIAN)
        val count = buf.int
        return (0 until count).map { buf.int.toLong() and 0xFFFFFFFFL }
    }

    suspend fun getDevicePropValue(propCode: Int): ByteArray {
        val dr = sendCommandReadData(
            PtpConstants.OP_GET_DEVICE_PROP_VALUE,
            listOf(propCode.toLong()),
        )
        requireOk(dr.response, "GetDevicePropValue(0x${propCode.toString(16)})")
        return dr.payload
    }

    /**
     * GetDevicePropDesc (0x1014): returns the property's PTP data type and its enumerated
     * allowed values, sign-extended to Long (negative for signed INT types).
     * Returns (dataType=0, emptyList) on any error or for non-enumeration properties.
     *
     * Wire format (DevicePropDesc, all little-endian):
     *   UINT16  DevicePropCode
     *   UINT16  DataType
     *   UINT8   GetSet
     *   <N>     DefaultValue     (N bytes per DataType)
     *   <N>     CurrentValue
     *   UINT8   FormFlag         (0x02 = Enumeration)
     *   UINT16  NumberOfValues
     *   <N>  × NumberOfValues
     */
    suspend fun getDevicePropDescEnum(propCode: Int): Pair<Int, List<Long>> {
        return try {
            val dr = sendCommandReadData(
                PtpConstants.OP_GET_DEVICE_PROP_DESC,
                listOf(propCode.toLong()),
            )
            if (dr.response.code != PtpConstants.RC_OK) return 0 to emptyList()
            val buf = ByteBuffer.wrap(dr.payload).order(ByteOrder.LITTLE_ENDIAN)
            if (buf.remaining() < 11) return 0 to emptyList()

            buf.short                                       // DevicePropCode
            val dataType = buf.short.toInt() and 0xFFFF
            buf.get()                                       // GetSet
            val valueSize = when (dataType) {
                0x0001, 0x0002 -> 1   // INT8, UINT8
                0x0003, 0x0004 -> 2   // INT16, UINT16
                0x0005, 0x0006 -> 4   // INT32, UINT32
                0x0007, 0x0008 -> 8   // INT64, UINT64
                else -> return 0 to emptyList()
            }
            if (buf.remaining() < valueSize * 2 + 1) return 0 to emptyList()
            repeat(valueSize * 2) { buf.get() }             // skip DefaultValue + CurrentValue
            val formFlag = buf.get().toInt() and 0xFF
            if (formFlag != 0x02) return dataType to emptyList()
            if (buf.remaining() < 2) return dataType to emptyList()
            val count = buf.short.toInt() and 0xFFFF

            val values = mutableListOf<Long>()
            repeat(count) {
                val v: Long? = when (dataType) {
                    0x0001 -> if (buf.remaining() >= 1) buf.get().toLong() else null
                    0x0002 -> if (buf.remaining() >= 1) (buf.get().toInt() and 0xFF).toLong() else null
                    0x0003 -> if (buf.remaining() >= 2) buf.short.toLong() else null
                    0x0004 -> if (buf.remaining() >= 2) (buf.short.toInt() and 0xFFFF).toLong() else null
                    0x0005 -> if (buf.remaining() >= 4) buf.int.toLong() else null
                    0x0006 -> if (buf.remaining() >= 4) (buf.int.toLong() and 0xFFFFFFFFL) else null
                    else -> null
                }
                v?.let { values += it }
            }
            dataType to values
        } catch (_: Exception) {
            0 to emptyList()
        }
    }

    /**
     * SetDevicePropValue (0x1016): serialises [value] according to [dataType] and sends it.
     * Handles 1-byte (INT8/UINT8), 2-byte (INT16/UINT16), and 4-byte (INT32/UINT32) types.
     */
    suspend fun setDevicePropValueRaw(propCode: Int, value: Long, dataType: Int) {
        val data = when (dataType) {
            0x0001, 0x0002 -> byteArrayOf(value.toByte())
            0x0003, 0x0004 -> ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)
                .putShort(value.toShort()).array()
            0x0005, 0x0006 -> ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(value.toInt()).array()
            else -> throw PtpException("Unsupported data type 0x${dataType.toString(16)}")
        }
        val resp = sendCommandSendData(
            PtpConstants.OP_SET_DEVICE_PROP_VALUE,
            listOf(propCode.toLong()),
            data,
        )
        requireOk(resp, "SetDevicePropValue(0x${propCode.toString(16)})")
    }

    /**
     * Nikon GetEvent (0x90C7) — returns all queued camera-side events since the
     * last call.  Wire format: [uint16 count][{uint16 code, uint32 param1} × count].
     * Returns a list of (eventCode, param1) pairs; empty list if queue is empty or
     * the command is not supported by this body.
     */
    suspend fun getNikonEvents(): List<Pair<Int, Int>> {
        val dr = try {
            sendCommandReadData(PtpConstants.OP_NIKON_GET_EVENT)
        } catch (_: Exception) {
            return emptyList()
        }
        if (dr.response.code != PtpConstants.RC_OK || dr.payload.size < 2) return emptyList()
        val buf = ByteBuffer.wrap(dr.payload).order(ByteOrder.LITTLE_ENDIAN)
        val count = buf.short.toInt() and 0xFFFF
        val events = mutableListOf<Pair<Int, Int>>()
        repeat(count) {
            if (buf.remaining() >= 6) {
                val code  = buf.short.toInt() and 0xFFFF
                val param = buf.int
                events += code to param
            }
        }
        return events
    }

    // ── Live view ─────────────────────────────────────────────────────────────

    suspend fun startLiveView() {
        requireOk(sendCommand(PtpConstants.OP_NIKON_START_LIVEVIEW), "StartLiveView")
    }

    suspend fun endLiveView() {
        // Ignore errors — camera may already have stopped live view
        try { sendCommand(PtpConstants.OP_NIKON_END_LIVEVIEW) } catch (_: Exception) {}
    }

    /**
     * Returns true when the camera is ready for the next operation.
     * RC_NIKON_SILENT_RELEASE_BUSY (0xA201) means the camera is in silent-shutter mode
     * but is fully responsive — libgphoto2 nikon_wait_busy() treats it as RC_OK.
     */
    suspend fun deviceReady(): Boolean {
        val code = sendCommand(PtpConstants.OP_NIKON_DEVICE_READY).code
        return code == PtpConstants.RC_OK || code == PtpConstants.RC_NIKON_SILENT_RELEASE_BUSY
    }

    /**
     * Fetch one live view JPEG frame.  The raw payload has an opaque header before
     * the JPEG; we scan for the SOI marker (0xFFD8) and return from there to EOI.
     * Throws PtpException("NotLiveView") when the camera has exited live view mode.
     * Throws PtpException("DeviceBusy") when the camera needs a brief retry.
     * Returns null for any other non-OK response.
     */
    suspend fun getLiveViewImage(): ByteArray? {
        val dr = sendCommandReadData(PtpConstants.OP_NIKON_GET_LIVEVIEW_IMG)
        return when (dr.response.code) {
            PtpConstants.RC_OK -> extractJpeg(dr.payload)
            PtpConstants.RC_NIKON_NOT_LIVEVIEW -> throw PtpException("NotLiveView")
            PtpConstants.RC_DEVICE_BUSY -> throw PtpException("DeviceBusy")
            else -> null
        }
    }

    private fun extractJpeg(data: ByteArray): ByteArray? {
        val soi = (0 until data.size - 1).firstOrNull { i ->
            data[i] == 0xFF.toByte() && data[i + 1] == 0xD8.toByte()
        } ?: return null
        val eoi = (soi until data.size - 1).lastOrNull { i ->
            data[i] == 0xFF.toByte() && data[i + 1] == 0xD9.toByte()
        }
        return data.copyOfRange(soi, if (eoi != null) eoi + 2 else data.size)
    }

    /** Trigger Nikon autofocus (PTP_OC_NIKON_AfDrive). */
    suspend fun afDrive(): PtpResponse =
        sendCommand(PtpConstants.OP_NIKON_AF_DRIVE)

    /**
     * Manual focus drive (0x9204): direction 0x1=near, 0x2=far; amount 1–32767.
     * LiveView must be active. Call DeviceReady after to wait for completion.
     */
    suspend fun mfDrive(direction: Int, steps: Int): PtpResponse =
        sendCommand(PtpConstants.OP_NIKON_MF_DRIVE, listOf(direction.toLong(), steps.toLong()))

    /** Cancel an in-progress AF drive (PTP_OC_NIKON_AfDriveCancel, 0x9206). Errors are ignored. */
    suspend fun afDriveCancel() {
        try { sendCommand(PtpConstants.OP_NIKON_AF_DRIVE_CANCEL) } catch (_: Exception) {}
    }

    /** Move the AF point to pixel (x, y) in the LiveView image (0x9205). LiveView must be active. */
    suspend fun changeAfArea(x: Int, y: Int): PtpResponse =
        sendCommand(PtpConstants.OP_NIKON_CHANGE_AF_AREA, listOf(x.toLong(), y.toLong()))

    /** Raw response code from NikonDeviceReady — callers use this to detect AF results. */
    suspend fun deviceReadyCode(): Int =
        sendCommand(PtpConstants.OP_NIKON_DEVICE_READY).code

    /** InitiateCapture: storage=0 (current), format=0 (default). */
    suspend fun initiateCapture(): PtpResponse =
        sendCommand(PtpConstants.OP_INITIATE_CAPTURE, listOf(0L, 0L))

    /** Drain one interrupt event (ObjectAdded etc.) to keep the endpoint clean. */
    suspend fun drainInterruptEvent() {
        transport.interruptIn()
    }

    // ── Property value decoders ───────────────────────────────────────────────

    fun decodeUint8(data: ByteArray): Int = data[0].toInt() and 0xFF

    fun decodeUint16(data: ByteArray): Int =
        ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF

    fun decodeUint32(data: ByteArray): Long =
        ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL

    // ── Object browser operations ─────────────────────────────────────────────

    /**
     * Returns all non-folder objects on the camera by recursively walking the
     * directory tree.  Nikon Z bodies return only the top-level folder when
     * the parent wildcard 0xFFFFFFFF is used, so we must traverse manually.
     */
    suspend fun listImages(): List<PtpObjectInfo> {
        val storageIds = getStorageIds()
        val result = mutableListOf<PtpObjectInfo>()
        for (sid in storageIds) {
            collectObjects(sid, 0x00000000L, result)
        }
        return result
    }

    // Nikon Z fc returns the full object list even when a specific parent is given,
    // so the same handle can appear in multiple GetObjectHandles responses as we
    // recurse into subdirectories.  The seen set prevents duplicate list entries
    // that would crash LazyVerticalGrid (which requires unique keys).
    private suspend fun collectObjects(
        storageId: Long,
        parent: Long,
        out: MutableList<PtpObjectInfo>,
        seen: MutableSet<Long> = mutableSetOf(),
    ) {
        val dr = sendCommandReadData(
            PtpConstants.OP_GET_OBJECT_HANDLES,
            listOf(storageId, 0L, parent),
        )
        if (dr.response.code != PtpConstants.RC_OK || dr.payload.size < 4) return
        val buf = ByteBuffer.wrap(dr.payload).order(ByteOrder.LITTLE_ENDIAN)
        val count = buf.int
        val handles = (0 until count).mapNotNull {
            if (buf.remaining() >= 4) buf.int.toLong() and 0xFFFFFFFFL else null
        }
        for (handle in handles) {
            if (!seen.add(handle)) continue     // skip handles already processed
            val infoDr = sendCommandReadData(PtpConstants.OP_GET_OBJECT_INFO, listOf(handle))
            if (infoDr.response.code != PtpConstants.RC_OK) continue
            val info = parseObjectInfo(handle, infoDr.payload)
            if (info.objectFormat == PtpConstants.OBJ_FORMAT_ASSOCIATION) {
                collectObjects(storageId, handle, out, seen)
            } else {
                out += info
            }
        }
    }

    private fun parseObjectInfo(handle: Long, payload: ByteArray): PtpObjectInfo {
        val buf = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        val storageId      = buf.int.toLong() and 0xFFFFFFFFL
        val objectFormat   = buf.short.toInt() and 0xFFFF
        buf.short                               // ProtectionStatus
        val objectSize     = buf.int.toLong() and 0xFFFFFFFFL
        buf.short                               // ThumbFormat
        val thumbSize      = buf.int.toLong() and 0xFFFFFFFFL
        buf.int; buf.int                        // ThumbPixWidth, ThumbPixHeight
        val imagePixWidth  = buf.int
        val imagePixHeight = buf.int
        buf.int                                 // ImageBitDepth
        val parentObject   = buf.int.toLong() and 0xFFFFFFFFL
        buf.short                               // AssociationType
        buf.int                                 // AssociationDesc
        buf.int                                 // SequenceNumber
        val filename       = buf.readPtpString()
        val captureDate    = buf.readPtpString()
        return PtpObjectInfo(
            handle = handle,
            storageId = storageId,
            objectFormat = objectFormat,
            objectSize = objectSize,
            thumbSize = thumbSize,
            imagePixWidth = imagePixWidth,
            imagePixHeight = imagePixHeight,
            parentObject = parentObject,
            filename = filename,
            captureDate = captureDate,
        )
    }

    /** GetThumb: returns the embedded JPEG thumbnail for [handle]. */
    suspend fun getThumb(handle: Long): ByteArray {
        val dr = sendCommandReadAllData(
            PtpConstants.OP_GET_THUMB,
            listOf(handle),
        )
        requireOk(dr.response, "GetThumb($handle)")
        return dr.payload
    }

    /**
     * GetPartialObject: reads up to [maxBytes] bytes starting at [offset].
     * Useful for extracting NEF embedded preview JPEGs without downloading the full file.
     */
    suspend fun getPartialObject(handle: Long, offset: Long, maxBytes: Long): ByteArray {
        val dr = sendCommandReadAllData(
            PtpConstants.OP_GET_PARTIAL_OBJECT,
            listOf(handle, offset, maxBytes),
        )
        requireOk(dr.response, "GetPartialObject($handle)")
        return dr.payload
    }

    /** GetObject: streams the full object to [out]. Returns bytes written. */
    suspend fun downloadObject(handle: Long, out: OutputStream): Long =
        sendCommandStreamData(PtpConstants.OP_GET_OBJECT, listOf(handle), out)

    /** Decode a signed integer from 2 or 4 bytes (handles both INT16 and INT32 payloads). */
    fun decodeIntFlex(data: ByteArray): Int = when (data.size) {
        2 -> ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
        4 -> ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).int
        else -> 0
    }

    /** Decode an unsigned integer from 1, 2, or 4 bytes.
     *  Nikon's ExposureIndex (0x500F) is UINT16 per PTP spec even though
     *  the property description says UINT32; this handles both. */
    fun decodeUintFlex(data: ByteArray): Long = when (data.size) {
        1 -> data[0].toLong() and 0xFFL
        2 -> ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).short.toLong() and 0xFFFFL
        4 -> ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
        else -> 0L
    }
}
