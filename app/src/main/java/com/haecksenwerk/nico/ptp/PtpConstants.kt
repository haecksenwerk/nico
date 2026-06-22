package com.haecksenwerk.nico.ptp

object PtpConstants {

    // Container types (little-endian uint16 at offset 4)
    const val CONTAINER_TYPE_COMMAND = 0x0001
    const val CONTAINER_TYPE_DATA = 0x0002
    const val CONTAINER_TYPE_RESPONSE = 0x0003
    const val CONTAINER_TYPE_EVENT = 0x0004

    // Standard PTP operation codes
    const val OP_GET_DEVICE_INFO = 0x1001
    const val OP_OPEN_SESSION = 0x1002
    const val OP_CLOSE_SESSION = 0x1003
    const val OP_GET_STORAGE_IDS = 0x1004
    const val OP_GET_STORAGE_INFO = 0x1005
    const val OP_GET_DEVICE_PROP_DESC = 0x1014
    const val OP_GET_DEVICE_PROP_VALUE = 0x1015
    const val OP_SET_DEVICE_PROP_VALUE = 0x1016
    const val OP_GET_OBJECT_HANDLES = 0x1007
    const val OP_GET_OBJECT_INFO = 0x1008
    const val OP_GET_OBJECT = 0x1009
    const val OP_GET_THUMB = 0x100A
    const val OP_INITIATE_CAPTURE = 0x100E
    const val OP_TERMINATE_CAPTURE = 0x1018
    const val OP_DELETE_OBJECT = 0x100B
    const val OP_GET_PARTIAL_OBJECT = 0x101B

    // Object format codes
    const val OBJ_FORMAT_ASSOCIATION = 0x3001  // directory / folder
    const val OBJ_FORMAT_NEF = 0x3800          // Nikon raw (vendor-defined PTP_OFC)
    const val OBJ_FORMAT_JPEG = 0x3801         // EXIF/JFIF JPEG

    // Nikon vendor extension codes
    const val OP_NIKON_GET_EVENT = 0x90C7   // data-in: packed event list
    const val OP_NIKON_DEVICE_READY = 0x90C8
    const val OP_NIKON_AF_DRIVE = 0x90CC
    const val OP_NIKON_START_LIVEVIEW = 0x9201
    const val OP_NIKON_END_LIVEVIEW = 0x9202
    const val OP_NIKON_GET_LIVEVIEW_IMG = 0x9203

    const val RC_NIKON_NOT_LIVEVIEW = 0xA00B

    // Response codes
    const val RC_OK = 0x2001
    const val RC_GENERAL_ERROR = 0x2002
    const val RC_SESSION_NOT_OPEN = 0x2003
    const val RC_INVALID_TRANSACTION_ID = 0x2004
    const val RC_OPERATION_NOT_SUPPORTED = 0x2005
    const val RC_DEVICE_BUSY = 0x2019
    const val RC_SESSION_ALREADY_OPEN = 0x201E

    // Device property codes
    const val PROP_BATTERY_LEVEL = 0x5001   // UINT8,  read-only, 0–100
    const val PROP_WHITE_BALANCE = 0x5005   // UINT16
    const val PROP_F_NUMBER = 0x5007        // UINT16, f×100 (e.g. 280 = f/2.8)
    const val PROP_FOCUS_MODE = 0x500A              // UINT16: read-only on Z series (use PROP_NIKON_FOCUS_MODE to set)
    const val PROP_EXPOSURE_METERING_MODE = 0x500B  // UINT16: 2=CW 3=Matrix 4=Spot 0x8010=HL-W (Nikon Z fc)
    const val PROP_EXPOSURE_PROGRAM_MODE = 0x500E  // UINT16: 1=M 2=P 3=A 4=S 0x8010=AUTO — read-only on Z series
    const val PROP_EXPOSURE_TIME = 0x500D   // UINT32, units of 0.0001 s — read-only on Z series
    const val PROP_EXPOSURE_INDEX = 0x500F  // UINT16 or UINT32 depending on body, ISO value (read display from here)
    const val PROP_EXPOSURE_BIAS = 0x5010   // INT16, EV × 1000 (writable on Z series)

    // Nikon Z-series vendor property codes (write-enabled where standard codes are read-only)
    const val PROP_NIKON_FOCUS_MODE = 0xD061  // UINT8 enum: 0=AF-S, 1=AF-C, 4=MF
    const val PROP_NIKON_ISO_EX = 0xD0B4      // UINT32 enum: raw ISO values (50…204800)
    const val PROP_NIKON_AUTO_ISO = 0xD054    // UINT8 range [0-1]: 0=manual ISO, 1=Auto ISO active

    // Response codes (browser-relevant)
    const val RC_OBJECT_NOT_FOUND = 0x2009
    const val RC_STORE_NOT_AVAILABLE = 0x200D

    // Event codes
    const val EVENT_OBJECT_ADDED = 0x4002
    const val EVENT_OBJECT_REMOVED = 0x4003
    const val EVENT_DEVICE_PROP_CHANGED = 0x4006  // param1 = property code that changed
    const val EVENT_STORE_ADDED = 0x4004
    const val EVENT_STORE_REMOVED = 0x4005

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
