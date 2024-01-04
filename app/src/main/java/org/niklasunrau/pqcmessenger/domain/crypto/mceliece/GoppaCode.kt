package org.niklasunrau.pqcmessenger.domain.crypto.mceliece

import android.util.Log
import cc.redberry.rings.poly.FiniteField
import cc.redberry.rings.poly.univar.UnivariateFactorization
import cc.redberry.rings.poly.univar.UnivariatePolynomialZ64
import org.jetbrains.kotlinx.multik.api.mk
import org.jetbrains.kotlinx.multik.api.ndarray
import org.jetbrains.kotlinx.multik.api.zeros
import org.jetbrains.kotlinx.multik.ndarray.operations.append
import kotlin.streams.toList
import cc.redberry.rings.poly.univar.UnivariatePolynomial as Poly
import cc.redberry.rings.poly.univar.UnivariatePolynomialZp64 as Element

data class GoppaCode(
    val gMatrix: Array<LongArray>,
    val ff2m: FiniteField<Element>,
    val support: List<Element>,
    val gPoly: Poly<Element>,
)

fun generateCode(n: Int, m: Int, t: Int): GoppaCode {
//    val ff2m = GF(2, m)
        val ff2m = FiniteField(UnivariatePolynomialZ64.create(1, 1, 0, 0, 1).modulus(2))
    val powerToPoly = generatePowerLUT(ff2m)
    val support = ff2m.iterator().asSequence().toList()
//    val gPoly = IrreduciblePolynomials.randomIrreduciblePolynomial(ff2m, t, Well19937c())
    val gPoly = Poly.create(
        ff2m,
        powerToPoly[2],
        ff2m.zero,
        ff2m.zero,
        ff2m.one
        )

    val xMatrix = Array(t) { Array(t) { ff2m.zero } }
    for (row in 0 until t) {
        for (col in 0 until t) {
            if (row - col in 0 until t) {
                xMatrix[row][col] = gPoly[t - (row - col)]
            }
        }
    }


    val yMatrix = Array(t) { Array(n) { ff2m.zero } }
    for (row in 0 until t) {
        for (col in 0 until n) {
            yMatrix[row][col] = ff2m.pow(support[col], row)
        }
    }

    val zMatrix = Array(n) { Array(n) { ff2m.zero } }
    for (row in 0 until n) {
        for (col in 0 until n) {
            if (row == col) {
                zMatrix[row][col] = ff2m.pow(gPoly.evaluate(support[row]), -1)
            }
        }
    }

    val xyMatrix = multiplyFieldMatrices(ff2m, xMatrix, yMatrix)
    val hMatrix = multiplyFieldMatrices(ff2m, xyMatrix, zMatrix)


    var hBinMatrix = mk.zeros<Long>(1, 1)
    for (row in 0 until t) {
        var rowMatrix = mk.zeros<Long>(1, 1)
        for (col in 0 until n) {
            val coeffs =
                mk.ndarray(listOf(lJustZerosList(hMatrix[row][col].stream().toList(), m).reversed())).transpose()
            rowMatrix = if (col == 0) {
                coeffs
            } else {
                rowMatrix.append(coeffs, 1)
            }
        }
        hBinMatrix = if (row == 0) {
            rowMatrix
        } else {
            hBinMatrix.append(rowMatrix, 0)
        }
    }

//    val lhsArray = hBinMatrix.toArray()
//    val rhsArray = LongArray(hBinMatrix.shape[0]) { 0L }
//
//    LinearSolver.rowEchelonForm(
//        IntegersZp64(2), lhsArray, rhsArray, true, false
//    )
//    val gMatrix = nullspace(lhsArray)
    logging(hBinMatrix)
    val gMatrix = hBinMatrix.nullspace()
    return GoppaCode(gMatrix, ff2m, support, gPoly)
}

private fun pattersonAlgorithm(cipher: LongArray, goppaCode: GoppaCode): LongArray {
    val inversePolys = mutableListOf<Poly<Element>>()
    val ff2m = goppaCode.ff2m
    val gPoly = goppaCode.gPoly


    for (i in cipher.indices) {
        if (cipher[i] == 1L) {
            inversePolys.add(
                inverseModPoly(
                    Poly.create(
                        ff2m, goppaCode.support[i].negate(), ff2m.one
                    ), gPoly

                )
            )
        }
    }
    var syndrome = Poly.zero(ff2m)
    for (poly in inversePolys) {
        syndrome += poly
    }

    val syndromeInverse = inverseModPoly(syndrome, gPoly)
    val s = sqrtModPoly(syndromeInverse - ff2m.identity(), gPoly)

    val (alpha, beta) = latticeBasisReduction(s, gPoly)


    val sigma = (alpha * alpha) + (ff2m.identity() * (beta * beta))
    val factors = UnivariateFactorization.FactorInGF(sigma).factors

    for (factor in factors) {
        val loc = factor.cc().toBinary().toInt(2)
        cipher[loc] = (cipher[loc] + 1) % 2
    }

    return cipher


}

fun logging(str: String) {
    Log.d("MCEL", str)
}
fun logging(ar: Array<LongArray>){
    Log.d("MCEL", ar.toPrettyString())

}
fun logging(str: Any) {
    Log.d("MCEL", str.toString())
}

fun decode(cipher: LongArray, goppaCode: GoppaCode): LongArray {
    val k = goppaCode.gMatrix.size
    val fixedMessage = pattersonAlgorithm(cipher, goppaCode)
//    val systemToSolve =
//        mk.ndarray(goppaCode.gMatrix).transpose().append(mk.ndarray(fixedMessage).reshape(fixedMessage.size, 1), 1).toArray()
//    logging(systemToSolve.toPrettyString())
//    systemToSolve.toReducedRowEchelonForm(k)
//    logging(systemToSolve.toPrettyString())
//    val solution = mk.ndarray(systemToSolve)
    return fixedMessage.sliceArray(fixedMessage.size-k..<fixedMessage.size)

}

