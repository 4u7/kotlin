/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.symbols.impl

import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.descriptors.*
import org.jetbrains.kotlin.ir.symbols.*
import org.jetbrains.kotlin.ir.util.IdSignature
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.serialization.deserialization.descriptors.DescriptorWithContainerSource

abstract class IrPublicSymbolBase<out D : DeclarationDescriptor>(
    override val initialDescriptor: D,
    override val signature: IdSignature
) : IrSymbol {
    override fun toString(): String {
        if (isBound) return owner.render()
        return "Unbound public symbol for $signature"
    }
}

abstract class IrBindablePublicSymbolBase<out D : DeclarationDescriptor, B : IrSymbolOwner>(
    initialDescriptor: D,
    sig: IdSignature,
    doWrapDescriptor: ((D) -> D)? = null
) : IrBindableSymbol<D, B>, IrPublicSymbolBase<D>(initialDescriptor, sig) {

    init {
        assert(isOriginalDescriptor(initialDescriptor)) {
            "Substituted descriptor $initialDescriptor for ${initialDescriptor.original}"
        }
        assert(sig.isPublic)
    }

    override val descriptor: D = when {
        initialDescriptor is WrappedDeclarationDescriptor<*> -> initialDescriptor
        doWrapDescriptor != null -> doWrapDescriptor(initialDescriptor)
        else -> initialDescriptor
    }

    private fun isOriginalDescriptor(descriptor: DeclarationDescriptor): Boolean =
        descriptor is WrappedDeclarationDescriptor<*> ||
                // TODO fix declaring/referencing value parameters: compute proper original descriptor
                descriptor is ValueParameterDescriptor && isOriginalDescriptor(descriptor.containingDeclaration) ||
                descriptor == descriptor.original

    private var _owner: B? = null
    override val owner: B
        get() = _owner ?: throw IllegalStateException("Symbol for $signature is unbound")

    override fun bind(owner: B) {
        if (_owner == null) {
            _owner = owner
            if (descriptor != initialDescriptor) {
                (descriptor as? WrappedDeclarationDescriptor<IrDeclaration>)?.bind(owner as IrDeclaration)
            }
        } else {
            throw IllegalStateException("${javaClass.simpleName} for $signature is already bound: ${owner.render()}")
        }
    }

    override val isPublicApi: Boolean = true

    override val isBound: Boolean
        get() = _owner != null
}

class IrClassPublicSymbolImpl(descriptor: ClassDescriptor, sig: IdSignature) :
    IrBindablePublicSymbolBase<ClassDescriptor, IrClass>(descriptor, sig, { d -> WrappedClassDescriptor(d.annotations, d.source) }),
    IrClassSymbol {
}

class IrEnumEntryPublicSymbolImpl(descriptor: ClassDescriptor, sig: IdSignature) :
    IrBindablePublicSymbolBase<ClassDescriptor, IrEnumEntry>(descriptor, sig, { d -> WrappedEnumEntryDescriptor(d.annotations, d.source) }),
    IrEnumEntrySymbol {
}

class IrSimpleFunctionPublicSymbolImpl(descriptor: FunctionDescriptor, sig: IdSignature) :
    IrBindablePublicSymbolBase<FunctionDescriptor, IrSimpleFunction>(
        descriptor, sig, { d ->
            when (d) {
                is DescriptorWithContainerSource -> WrappedFunctionDescriptorWithContainerSource(d.containerSource)
                is PropertyGetterDescriptor -> WrappedPropertyGetterDescriptor(d.annotations, d.source)
                is PropertySetterDescriptor -> WrappedPropertySetterDescriptor(d.annotations, d.source)
                else -> WrappedSimpleFunctionDescriptor(d.annotations, d.source)
            }
        }
    ),
    IrSimpleFunctionSymbol {
}

class IrConstructorPublicSymbolImpl(descriptor: ClassConstructorDescriptor, sig: IdSignature) :
    IrBindablePublicSymbolBase<ClassConstructorDescriptor, IrConstructor>(
        descriptor, sig, { d -> WrappedClassConstructorDescriptor(d.annotations, d.source) }
    ),
    IrConstructorSymbol {
}

class IrPropertyPublicSymbolImpl(descriptor: PropertyDescriptor, sig: IdSignature) :
    IrBindablePublicSymbolBase<PropertyDescriptor, IrProperty>(
        descriptor, sig, { d ->
            if (d is DescriptorWithContainerSource)
                WrappedPropertyDescriptorWithContainerSource(d.containerSource)
            else
                WrappedPropertyDescriptor(d.annotations, d.source)
        }
    ),
    IrPropertySymbol {
}

class IrTypeAliasPublicSymbolImpl(descriptor: TypeAliasDescriptor, sig: IdSignature) :
    IrBindablePublicSymbolBase<TypeAliasDescriptor, IrTypeAlias>(
        descriptor, sig, { d -> WrappedTypeAliasDescriptor(d.annotations, d.source) }
    ),
    IrTypeAliasSymbol {
}
