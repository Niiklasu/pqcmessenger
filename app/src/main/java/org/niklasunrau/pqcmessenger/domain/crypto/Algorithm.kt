package org.niklasunrau.pqcmessenger.domain.crypto

import org.niklasunrau.pqcmessenger.domain.crypto.mceliece.McEliece

object Algorithm {
    enum class Type{
        MCELIECE,
    }
    val map: Map<Type, AsymmetricAlgorithm<AsymmetricSecretKey, AsymmetricPublicKey>> = mapOf(
        Type.MCELIECE to McEliece,
    )
}
