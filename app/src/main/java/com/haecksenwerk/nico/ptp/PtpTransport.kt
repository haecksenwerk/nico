package com.haecksenwerk.nico.ptp

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Raw USB layer: opens the Still Image interface and exposes bulk transfers.
 * All blocking I/O is dispatched to Dispatchers.IO internally so callers on
 * the Main dispatcher are safe.
 */
class PtpTransport(
    private val usbManager: UsbManager,
    private val device: UsbDevice,
) {
    private var connection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null
    private var epOut: UsbEndpoint? = null
    private var epIn: UsbEndpoint? = null
    private var epInterrupt: UsbEndpoint? = null

    val isOpen: Boolean get() = connection != null

    /** Must be called on a worker thread (not Main). Returns false if the device
     *  is unavailable or does not expose a Still Image interface. */
    fun open(): Boolean {
        val iface = findStillImageInterface() ?: return false
        val conn = usbManager.openDevice(device) ?: return false
        if (!conn.claimInterface(iface, true)) {
            conn.close()
            return false
        }

        var out: UsbEndpoint? = null
        var inBulk: UsbEndpoint? = null
        var inInt: UsbEndpoint? = null
        for (i in 0 until iface.endpointCount) {
            val ep = iface.getEndpoint(i)
            when {
                ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK &&
                        ep.direction == UsbConstants.USB_DIR_OUT -> out = ep
                ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK &&
                        ep.direction == UsbConstants.USB_DIR_IN -> inBulk = ep
                ep.type == UsbConstants.USB_ENDPOINT_XFER_INT &&
                        ep.direction == UsbConstants.USB_DIR_IN -> inInt = ep
            }
        }

        if (out == null || inBulk == null) {
            conn.releaseInterface(iface)
            conn.close()
            return false
        }

        connection = conn
        usbInterface = iface
        epOut = out
        epIn = inBulk
        epInterrupt = inInt
        return true
    }

    fun close() {
        connection?.let { conn ->
            usbInterface?.let { conn.releaseInterface(it) }
            conn.close()
        }
        connection = null
        usbInterface = null
        epOut = null
        epIn = null
        epInterrupt = null
    }

    suspend fun bulkOut(data: ByteArray): Int = withContext(Dispatchers.IO) {
        val conn = connection ?: return@withContext -1
        val ep = epOut ?: return@withContext -1
        var n = conn.bulkTransfer(ep, data, data.size, PtpConstants.TRANSFER_TIMEOUT_MS)
        // OUT endpoint stalled: clear halt and retry once (mirrors libgphoto2 usb.c)
        if (n < 0) {
            clearHalt(conn, ep)
            n = conn.bulkTransfer(ep, data, data.size, PtpConstants.TRANSFER_TIMEOUT_MS)
        }
        n
    }

    /** Returns the received bytes, or null on timeout / error. */
    suspend fun bulkIn(maxLength: Int = PtpConstants.MAX_BULK_BUFFER): ByteArray? =
        withContext(Dispatchers.IO) {
            val conn = connection ?: return@withContext null
            val ep = epIn ?: return@withContext null
            val buf = ByteArray(maxLength)
            var n = conn.bulkTransfer(ep, buf, buf.size, PtpConstants.TRANSFER_TIMEOUT_MS)
            // ZLP: camera signals end-of-transfer with a zero-length packet; retry once
            if (n == 0)
                n = conn.bulkTransfer(ep, buf, buf.size, PtpConstants.TRANSFER_TIMEOUT_MS)
            // IN endpoint stalled: clear halt and retry once (mirrors libgphoto2 usb.c)
            if (n < 0) {
                clearHalt(conn, ep)
                n = conn.bulkTransfer(ep, buf, buf.size, PtpConstants.TRANSFER_TIMEOUT_MS)
            }
            if (n < 0) null else buf.copyOf(n)
        }

    /**
     * Reads and discards data from the bulk IN endpoint until it times out.
     * Called after a failed or interrupted GetThumb/GetPartialObject to flush
     * any stale data the camera is still sending, so the next PTP command
     * doesn't read garbage instead of its own response.
     */
    suspend fun drainIn() = withContext(Dispatchers.IO) {
        val conn = connection ?: return@withContext
        val ep = epIn ?: return@withContext
        val buf = ByteArray(PtpConstants.MAX_BULK_BUFFER)
        repeat(32) {
            if (conn.bulkTransfer(ep, buf, buf.size, 300) < 0) return@withContext
        }
    }

    /** Non-blocking drain of the interrupt endpoint; returns null if nothing arrives. */
    suspend fun interruptIn(): ByteArray? = withContext(Dispatchers.IO) {
        val conn = connection ?: return@withContext null
        val ep = epInterrupt ?: return@withContext null
        val buf = ByteArray(64)
        val n = conn.bulkTransfer(ep, buf, buf.size, PtpConstants.INTERRUPT_TIMEOUT_MS)
        if (n < 0) null else buf.copyOf(n)
    }

    /** Sends USB CLEAR_FEATURE(ENDPOINT_HALT) to unstall an endpoint. */
    private fun clearHalt(conn: UsbDeviceConnection, ep: UsbEndpoint) {
        conn.controlTransfer(
            0x02,       // bmRequestType: standard | host-to-device | endpoint
            0x01,       // bRequest: CLEAR_FEATURE
            0x0000,     // wValue: ENDPOINT_HALT
            ep.address, // wIndex: endpoint address (direction bit included)
            null, 0, 1000,
        )
    }

    private fun findStillImageInterface(): UsbInterface? =
        (0 until device.interfaceCount)
            .map { device.getInterface(it) }
            .find { iface ->
                iface.interfaceClass == PtpConstants.USB_CLASS_STILL_IMAGE &&
                        iface.interfaceSubclass == PtpConstants.USB_SUBCLASS_STILL_IMAGE &&
                        iface.interfaceProtocol == PtpConstants.USB_PROTOCOL_STILL_IMAGE
            }
}
