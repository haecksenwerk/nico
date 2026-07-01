package com.haecksenwerk.nico.ptp

object PtpConstants {

    // Container types (little-endian uint16 at offset 4)
    const val CONTAINER_TYPE_COMMAND = 0x0001
    const val CONTAINER_TYPE_DATA = 0x0002

    // Standard PTP operation codes
    const val OP_GET_DEVICE_INFO = 0x1001
    const val OP_OPEN_SESSION = 0x1002
    const val OP_CLOSE_SESSION = 0x1003
    const val OP_GET_STORAGE_IDS = 0x1004
    const val OP_GET_DEVICE_PROP_DESC = 0x1014
    const val OP_GET_DEVICE_PROP_VALUE = 0x1015
    const val OP_SET_DEVICE_PROP_VALUE = 0x1016
    const val OP_GET_OBJECT_HANDLES = 0x1007
    const val OP_GET_OBJECT_INFO = 0x1008
    const val OP_GET_OBJECT = 0x1009
    const val OP_GET_THUMB = 0x100A
    const val OP_INITIATE_CAPTURE = 0x100E
    const val OP_DELETE_OBJECT = 0x100B
    const val OP_GET_PARTIAL_OBJECT = 0x101B

    // Object format codes
    const val OBJ_FORMAT_ASSOCIATION = 0x3001  // directory / folder
    const val OBJ_FORMAT_NEF = 0x3800          // Nikon raw (vendor-defined PTP_OFC)
    const val OBJ_FORMAT_JPEG = 0x3801         // EXIF/JFIF JPEG

    // Nikon vendor extension codes
    const val OP_NIKON_GET_EVENT = 0x90C7   // data-in: packed event list
    const val OP_NIKON_DEVICE_READY = 0x90C8
    const val OP_NIKON_AF_DRIVE = 0x90C1        // PTP_OC_NIKON_AfDrive — no params, no data
    const val OP_NIKON_CHANGE_AF_AREA = 0x9205  // 2 params: x, y in LiveView pixel coords; LiveView must be active
    const val OP_NIKON_AF_DRIVE_CANCEL = 0x9206 // PTP_OC_NIKON_AfDriveCancel — abort in-progress AF search
    const val OP_NIKON_START_LIVEVIEW = 0x9201
    const val OP_NIKON_END_LIVEVIEW = 0x9202
    const val OP_NIKON_GET_LIVEVIEW_IMG = 0x9203

    const val RC_NIKON_NOT_LIVEVIEW = 0xA00B
    const val RC_NIKON_OUT_OF_FOCUS = 0xA002  // returned by DeviceReady after AfDrive if AF failed

    // Response codes
    const val RC_OK = 0x2001
    const val RC_DEVICE_BUSY = 0x2019
    const val RC_SESSION_ALREADY_OPEN = 0x201E

    // Nikon-specific DeviceReady (0x90C8) return codes, per libgphoto2 nikon_wait_busy():
    // 0xA200 = camera is doing a bulb exposure → keep polling (same as DeviceBusy)
    // 0xA201 = silent-shutter mode, camera is ready → treat as RC_OK
    const val RC_NIKON_BULB_RELEASE_BUSY = 0xA200
    const val RC_NIKON_SILENT_RELEASE_BUSY = 0xA201

    // Device property codes
    const val PROP_BATTERY_LEVEL = 0x5001   // UINT8,  read-only, 0–100
    const val PROP_WHITE_BALANCE = 0x5005   // UINT16
    const val PROP_F_NUMBER = 0x5007        // UINT16, f×100 (e.g. 280 = f/2.8)
    const val PROP_EXPOSURE_METERING_MODE = 0x500B  // UINT16: 2=CW 3=Matrix 4=Spot 0x8010=HL-W
    const val PROP_EXPOSURE_PROGRAM_MODE = 0x500E  // UINT16: 1=M 2=P 3=A 4=S 0x8010=AUTO — read-only on Z series
    const val PROP_EXPOSURE_INDEX = 0x500F  // UINT16 or UINT32 depending on body, ISO value
    const val PROP_EXPOSURE_BIAS = 0x5010   // INT16, EV × 1000 (writable on Z series)

    // Nikon Z-series vendor property codes (write-enabled where standard codes are read-only)
    const val PROP_NIKON_FOCUS_MODE = 0xD061      // UINT8 enum: 0=AF-S, 1=AF-C, 4=MF
    // UINT32 enum; max value × 4 = ChangeAfArea x-coordinate range
    // APS-C bodies (Z fc, Z30, Z50): [0,360,720,1440] → x_max=5760
    // Full-frame bodies (Z6, Z6II):  [0,512,1024,2048] → x_max=8192
    const val PROP_NIKON_LIVE_VIEW_ZOOM_AREA = 0xD1BD
    const val PROP_NIKON_ISO_EX = 0xD0B4          // UINT32: ISO value (read from camera events)
    const val PROP_NIKON_EXPOSURE_TIME = 0xD100   // UINT32: shutter speed, encoding (num<<16)|den; 0xFFFFFFFF=Bulb

    // Event codes
    const val EVENT_DEVICE_PROP_CHANGED = 0x4006  // param1 = property code that changed

    // USB Still Image interface identifiers (class 0x06 / sub 0x01 / proto 0x01)
    const val USB_CLASS_STILL_IMAGE = 0x06
    const val USB_SUBCLASS_STILL_IMAGE = 0x01
    const val USB_PROTOCOL_STILL_IMAGE = 0x01

    // Identify Nikon by VID only — PID is body-specific
    const val NIKON_VENDOR_ID = 0x04B0

    const val TRANSFER_TIMEOUT_MS = 5000
    const val CONTAINER_HEADER_SIZE = 12
    const val MAX_BULK_BUFFER = 65536
    const val INTERRUPT_TIMEOUT_MS = 150
}
