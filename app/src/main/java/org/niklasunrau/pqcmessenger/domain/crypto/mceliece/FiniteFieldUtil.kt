package org.niklasunrau.pqcmessenger.domain.crypto.mceliece

import cc.redberry.rings.Ring
import cc.redberry.rings.Rings.UnivariateRing
import cc.redberry.rings.poly.FiniteField
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set
import cc.redberry.rings.poly.univar.UnivariatePolynomial as Poly
import cc.redberry.rings.poly.univar.UnivariatePolynomialZp64 as Element

fun generatePowerLUT(ring: Ring<Element>): Map<Int, Element>{
    val finiteField = ring as FiniteField
    val lut = mutableMapOf<Int, Element>(
        0 to finiteField.one,
    )
    val a = finiteField.generator()
    for(i in 1..<finiteField.cardinality().toInt() - 1){
        lut[i] = finiteField.pow(a, i)
    }
    return lut
}

fun inverseModPoly(poly: Poly<Element>, mod: Poly<Element>): Poly<Element> {
    val polyRing = UnivariateRing(poly.ring)
    return polyRing.extendedGCD(poly, mod)[1]
}

fun sqrtModPoly(poly: Poly<Element>, mod: Poly<Element>): Poly<Element> {
    val (g0, g1) = mod.split()
    val (t0, t1) = poly.split()
    val polyRing = UnivariateRing(poly.ring)
    return polyRing.remainder(t0 + (g0 * inverseModPoly(g1, mod) * t1), mod)
}

private fun normExpo(aPoly: Poly<Element>, bPoly: Poly<Element>): Int {
    return ((aPoly * aPoly) + (bPoly.ring.identity() * (bPoly * bPoly))).degree()
}

fun latticeBasisReduction(
    poly: Poly<Element>, mod: Poly<Element>
): Pair<Poly<Element>, Poly<Element>> {
    val t = mod.degree()
    val a = mutableListOf<Poly<Element>>()
    val b = mutableListOf<Poly<Element>>()
    val ring = poly.ring
    val polyRing = UnivariateRing(ring)


    val (q0) = polyRing.divideAndRemainder(mod, poly)
    a.add(mod - (q0 * poly))
    b.add(-q0)

    if (normExpo(a[0], b[0]) > t) {
        val (q, r) = polyRing.divideAndRemainder(poly, a[0])
        a.add(r)
        b.add(polyRing.one - (q * b[0]))
    } else {
        return a[0] to b[0]
    }


    var i = 1
    while (normExpo(a[i], b[i]) > t) {
        val (q, r) = polyRing.divideAndRemainder(a[i - 1], a[i])
        a.add(r)
        b.add(b[i - 1] - (q * b[i]))
        i++
    }

    return a[i] to b[i]
}