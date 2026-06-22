package com.haecksenwerk.nico.ptp

data class PtpObjectInfo(
    val handle: Long,
    val storageId: Long,
    val objectFormat: Int,      // 0x3801 = JPEG, 0x3800 = NEF
    val objectSize: Long,       // bytes (0xFFFFFFFF if > 4 GB)
    val thumbSize: Long,        // bytes
    val imagePixWidth: Int,
    val imagePixHeight: Int,
    val parentObject: Long,
    val filename: String,
    val captureDate: String,    // ISO 8601 subset: "YYYYMMDDTHHmmss"
)
