/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.symbols

import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrSymbolOwner
import org.jetbrains.kotlin.ir.symbols.impl.IrPublicSymbolBase
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.isLocalClass

interface IrClassifierEqualityChecker {
    fun areEqual(left: IrClassifierSymbol, right: IrClassifierSymbol): Boolean

    fun getHashCode(symbol: IrClassifierSymbol): Int
}

object SignatureEqualityChecker : IrClassifierEqualityChecker {
    override fun areEqual(left: IrClassifierSymbol, right: IrClassifierSymbol): Boolean {
        if (left === right) return true
        if (left is IrPublicSymbolBase<*> && right is IrPublicSymbolBase<*> && left.signature == right.signature) return true
        // Declarations from FIR can have private symbols even when public.
        if (left.isBound && right.isBound) return checkViaDeclarations(left.owner, right.owner)
        return false
    }

    override fun getHashCode(symbol: IrClassifierSymbol): Int {
        if (symbol !is IrPublicSymbolBase<*>) {
            return symbol.wrappedDescriptor.hashCode()
        }
        return symbol.signature.hashCode()
    }

    private fun checkViaDeclarations(c1: IrSymbolOwner, c2: IrSymbolOwner): Boolean {
        if (c1 is IrClass && c2 is IrClass) {
            if (c1.isLocalClass() || c2.isLocalClass())
                return c1 === c2 // Local declarations should be identical

            return c1.fqNameWhenAvailable == c2.fqNameWhenAvailable
        }

        return c1 == c2
    }

}
