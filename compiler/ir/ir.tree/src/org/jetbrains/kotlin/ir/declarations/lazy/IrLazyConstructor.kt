/*
 * Copyright 2010-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.declarations.lazy

import org.jetbrains.kotlin.descriptors.ClassConstructorDescriptor
import org.jetbrains.kotlin.descriptors.Visibility
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrTypeParameter
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.util.DeclarationStubGenerator
import org.jetbrains.kotlin.ir.util.TypeTranslator
import org.jetbrains.kotlin.ir.util.withScope
import org.jetbrains.kotlin.ir.visitors.IrElementVisitor
import org.jetbrains.kotlin.name.Name

class IrLazyConstructor(
    startOffset: Int,
    endOffset: Int,
    origin: IrDeclarationOrigin,
    override val symbol: IrConstructorSymbol,
    _initialDescriptor: ClassConstructorDescriptor,
    name: Name,
    visibility: Visibility,
    isInline: Boolean,
    isExternal: Boolean,
    override val isPrimary: Boolean,
    isExpect: Boolean,
    stubGenerator: DeclarationStubGenerator,
    typeTranslator: TypeTranslator
) :
    IrLazyFunctionBase(
        startOffset, endOffset, _initialDescriptor, origin, name,
        visibility, isInline, isExternal, isExpect,
        stubGenerator, typeTranslator
    ),
    IrConstructor {

    override val wrappedDescriptor get() = symbol.wrappedDescriptor
    override val initialDescriptor get() = symbol.initialDescriptor

    override var typeParameters: List<IrTypeParameter> by lazyVar {
        typeTranslator.buildWithScope(this) {
            stubGenerator.symbolTable.withScope(_initialDescriptor) {
                val classTypeParametersCount = _initialDescriptor.constructedClass.original.declaredTypeParameters.size
                val allConstructorTypeParameters = _initialDescriptor.typeParameters
                allConstructorTypeParameters.subList(classTypeParametersCount, allConstructorTypeParameters.size).mapTo(ArrayList()) {
                    stubGenerator.generateOrGetTypeParameterStub(it)
                }
            }
        }
    }

    init {
        symbol.bind(this)
    }

    override fun <R, D> accept(visitor: IrElementVisitor<R, D>, data: D): R =
        visitor.visitConstructor(this, data)
}