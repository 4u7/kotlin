/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.ir.symbols.impl

import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.descriptors.*
import org.jetbrains.kotlin.ir.expressions.IrReturnableBlock
import org.jetbrains.kotlin.ir.symbols.*
import org.jetbrains.kotlin.ir.util.IdSignature
import org.jetbrains.kotlin.ir.util.render

abstract class IrSymbolBase<out D : DeclarationDescriptor>(override val initialDescriptor: D) : IrSymbol {
    override fun toString(): String {
        if (isBound) return owner.render()
        return "Unbound private symbol ${super.toString()}"
    }
}

abstract class IrBindableSymbolBase<out D : DeclarationDescriptor, B : IrSymbolOwner>(
    initialDescriptor: D,
    doWrapDescriptor: ((D) -> D)? = null
) :
    IrBindableSymbol<D, B>, IrSymbolBase<D>(initialDescriptor) {

    init {
        assert(isOriginalDescriptor(initialDescriptor)) {
            "Substituted descriptor $initialDescriptor for ${initialDescriptor.original}"
        }
        if (initialDescriptor !is WrappedDeclarationDescriptor<*>) {
            val containingDeclaration = initialDescriptor.containingDeclaration
            assert(containingDeclaration == null || isOriginalDescriptor(containingDeclaration)) {
                "Substituted containing declaration: $containingDeclaration\nfor descriptor: $initialDescriptor"
            }
        }
    }

    private fun isOriginalDescriptor(descriptor: DeclarationDescriptor): Boolean =
        descriptor is WrappedDeclarationDescriptor<*> ||
                // TODO fix declaring/referencing value parameters: compute proper original descriptor
                descriptor is ValueParameterDescriptor && isOriginalDescriptor(descriptor.containingDeclaration) ||
                descriptor == descriptor.original

    override val wrappedDescriptor: D = when {
        initialDescriptor is WrappedDeclarationDescriptor<*> -> initialDescriptor
        doWrapDescriptor != null -> doWrapDescriptor(initialDescriptor)
        else -> initialDescriptor
    }

    private var _owner: B? = null
    override val owner: B
        get() = _owner ?: throw IllegalStateException("Symbol with ${javaClass.simpleName} is unbound")

    override fun bind(owner: B) {
        if (_owner == null) {
            _owner = owner
            if (wrappedDescriptor != initialDescriptor) {
                (wrappedDescriptor as? WrappedDeclarationDescriptor<IrDeclaration>)?.bind(owner as IrDeclaration)
            }
        } else {
            throw IllegalStateException("${javaClass.simpleName} is already bound: ${owner.render()}")
        }
    }

    override val isPublicApi: Boolean = false

    override val signature: IdSignature
        get() = error("IdSignature is allowed only for PublicApi symbols")

    override val isBound: Boolean
        get() = _owner != null
}

class IrFileSymbolImpl(descriptor: PackageFragmentDescriptor) :
    IrBindableSymbolBase<PackageFragmentDescriptor, IrFile>(descriptor),
    IrFileSymbol

class IrExternalPackageFragmentSymbolImpl(descriptor: PackageFragmentDescriptor) :
    IrBindableSymbolBase<PackageFragmentDescriptor, IrExternalPackageFragment>(descriptor),
    IrExternalPackageFragmentSymbol

class IrAnonymousInitializerSymbolImpl(descriptor: ClassDescriptor) :
    IrBindableSymbolBase<ClassDescriptor, IrAnonymousInitializer>(descriptor, { d -> WrappedClassDescriptor(d.annotations, d.source) }),
    IrAnonymousInitializerSymbol {
    constructor(irClassSymbol: IrClassSymbol) : this(irClassSymbol.wrappedDescriptor) {}
}

class IrClassSymbolImpl(descriptor: ClassDescriptor) :
    IrBindableSymbolBase<ClassDescriptor, IrClass>(descriptor, { d -> WrappedClassDescriptor(d.annotations, d.source) }),
    IrClassSymbol {
}

class IrEnumEntrySymbolImpl(descriptor: ClassDescriptor) :
    IrBindableSymbolBase<ClassDescriptor, IrEnumEntry>(descriptor, { d-> WrappedEnumEntryDescriptor(d.annotations, d.source) }),
    IrEnumEntrySymbol {
}

class IrFieldSymbolImpl(descriptor: PropertyDescriptor) :
    IrBindableSymbolBase<PropertyDescriptor, IrField>(descriptor, { d -> WrappedFieldDescriptor(d.annotations, d.source) }),
    IrFieldSymbol {
}

class IrTypeParameterSymbolImpl(descriptor: TypeParameterDescriptor) :
    IrBindableSymbolBase<TypeParameterDescriptor, IrTypeParameter>(
        descriptor,
        { d -> WrappedTypeParameterDescriptor(d.annotations, d.source) }
    ),
    IrTypeParameterSymbol {
}

class IrValueParameterSymbolImpl(descriptor: ParameterDescriptor) :
    IrBindableSymbolBase<ParameterDescriptor, IrValueParameter>(
        descriptor,
        { d ->
            if (d is ReceiverParameterDescriptor)
                WrappedReceiverParameterDescriptor(d.annotations, d.source)
            else
                WrappedValueParameterDescriptor(d.annotations, d.source)
        }
    ),
    IrValueParameterSymbol {
}

class IrVariableSymbolImpl(descriptor: VariableDescriptor) :
    IrBindableSymbolBase<VariableDescriptor, IrVariable>(descriptor, { d -> WrappedVariableDescriptor(d.annotations, d.source) }),
    IrVariableSymbol {
}

class IrSimpleFunctionSymbolImpl(descriptor: FunctionDescriptor) :
    IrBindableSymbolBase<FunctionDescriptor, IrSimpleFunction>(
        descriptor,
        { d -> WrappedSimpleFunctionDescriptor(d.annotations, d.source) }
    ),
    IrSimpleFunctionSymbol {
}

class IrConstructorSymbolImpl(descriptor: ClassConstructorDescriptor) :
    IrBindableSymbolBase<ClassConstructorDescriptor, IrConstructor>(
        descriptor,
        { d -> WrappedClassConstructorDescriptor(d.annotations, d.source) }
    ),
    IrConstructorSymbol {
}

class IrReturnableBlockSymbolImpl(descriptor: FunctionDescriptor) :
    IrBindableSymbolBase<FunctionDescriptor, IrReturnableBlock>(
        descriptor,
        { d -> WrappedSimpleFunctionDescriptor(d.annotations, d.source) }
    ),
    IrReturnableBlockSymbol

class IrPropertySymbolImpl(descriptor: PropertyDescriptor) :
    IrBindableSymbolBase<PropertyDescriptor, IrProperty>(descriptor, { d -> WrappedPropertyDescriptor(d.annotations, d.source) }),
    IrPropertySymbol {
}

class IrLocalDelegatedPropertySymbolImpl(descriptor: VariableDescriptorWithAccessors) :
    IrBindableSymbolBase<VariableDescriptorWithAccessors, IrLocalDelegatedProperty>(
        descriptor,
        { d -> WrappedVariableDescriptorWithAccessor() }
    ),
    IrLocalDelegatedPropertySymbol

class IrTypeAliasSymbolImpl(descriptor: TypeAliasDescriptor) :
    IrBindableSymbolBase<TypeAliasDescriptor, IrTypeAlias>(descriptor, { d -> WrappedTypeAliasDescriptor(d.annotations, d.source) }),
    IrTypeAliasSymbol {
}

class IrScriptSymbolImpl(descriptor: ScriptDescriptor) :
    IrScriptSymbol, IrBindableSymbolBase<ScriptDescriptor, IrScript>(descriptor)
