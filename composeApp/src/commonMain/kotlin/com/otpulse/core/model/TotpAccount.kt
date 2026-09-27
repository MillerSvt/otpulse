package com.otpulse.core.model

import com.otpulse.totp.TotpAlgorithm

data class TotpAccount(
    val id: String,
    val issuer: String,
    val accountName: String,
    val secretReference: String,
    val algorithm: TotpAlgorithm = TotpAlgorithm.SHA1,
    val digits: Int = 6,
    val periodSeconds: Int = 30,
    val sortOrder: Int = 0,
    val optionalTimeShiftOverrideMilliseconds: Long? = null,
)
