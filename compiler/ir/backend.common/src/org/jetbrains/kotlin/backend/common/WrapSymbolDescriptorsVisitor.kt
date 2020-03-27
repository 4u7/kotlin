package org.jetbrains.kotlin.backend.common

import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrSymbolDeclaration
import org.jetbrains.kotlin.ir.expressions.IrDeclarationReference
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid

class WrapSymbolDescriptorsVisitor : IrElementVisitorVoid {
    override fun visitElement(element: IrElement) {
        element.acceptChildrenVoid(this)
    }

    override fun visitFile(declaration: IrFile) {
        declaration.symbol.wrapDescriptor()
        declaration.annotations.forEach { it.acceptVoid(this) }
        super.visitFile(declaration)
    }

    override fun visitDeclaration(declaration: IrDeclaration) {
        if (declaration is IrSymbolDeclaration<*>) {
            declaration.symbol.wrapDescriptor()
        }
        declaration.annotations.forEach { it.acceptVoid(this) }
        super.visitDeclaration(declaration)
    }

    override fun visitDeclarationReference(expression: IrDeclarationReference) {
        expression.symbol.wrapDescriptor()
        super.visitDeclarationReference(expression)
    }
}