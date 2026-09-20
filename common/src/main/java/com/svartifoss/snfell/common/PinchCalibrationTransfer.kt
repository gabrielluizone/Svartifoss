package com.svartifoss.snfell.common

import com.google.protobuf.InvalidProtocolBufferException
import com.svartifoss.snfell.proto.PinchCalibrationMessage

data class PinchCalibrationTransfer(val requestId: String, val profile: PinchCalibration) {
    fun encode(): ByteArray {
        require(REQUEST_ID.matches(requestId))
        return PinchCalibrationMessage.newBuilder().setRequestId(requestId)
                .setProfile(profile.encode()).build().toByteArray()
    }

    companion object {
        private val REQUEST_ID = Regex("[A-Za-z0-9-]{1,64}")
        fun decode(bytes: ByteArray): PinchCalibrationTransfer? {
            if (bytes.isEmpty() || bytes.size > 512) return null
            return try {
                val message = PinchCalibrationMessage.parseFrom(bytes)
                if (!REQUEST_ID.matches(message.requestId)) return null
                PinchCalibrationTransfer(message.requestId,
                        PinchCalibration.decode(message.profile) ?: return null)
            } catch (_: InvalidProtocolBufferException) {
                null
            }
        }
    }
}
