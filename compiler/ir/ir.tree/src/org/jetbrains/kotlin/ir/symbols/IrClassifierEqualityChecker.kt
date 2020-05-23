/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.symbols

import org.jetbrains.kotlin.ir.symbols.impl.IrPublicSymbolBase

interface IrClassifierEqualityChecker {
    fun areEqual(left: IrClassifierSymbol, right: IrClassifierSymbol): Boolean

    fun getHashCode(symbol: IrClassifierSymbol): Int
}

object SignatureEqualityChecker : IrClassifierEqualityChecker {
    override fun areEqual(left: IrClassifierSymbol, right: IrClassifierSymbol): Boolean {
        if (left === right) return true
        if (left !is IrPublicSymbolBase<*> || right !is IrPublicSymbolBase<*>) {
            return false
        }
        return left.signature == right.signature
    }

    override fun getHashCode(symbol: IrClassifierSymbol): Int {
        if (symbol !is IrPublicSymbolBase<*>) {
            return symbol.wrappedDescriptor.hashCode()
        }
        return symbol.signature.hashCode()
    }
}
