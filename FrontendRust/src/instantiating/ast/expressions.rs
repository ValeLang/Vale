
use crate::interner::StrI;
use crate::utils::range::RangeS;
use crate::instantiating::ast::types::{
    CoordI, OwnershipI, MutabilityI, VariabilityI,
    InterfaceIT, RuntimeSizedArrayIT, StaticSizedArrayIT, StructIT,
    KindIT, BoolIT,
};
use crate::instantiating::ast::names::{IdI, IVarNameI};
use crate::instantiating::ast::ast::{
    IVariableI, ILocalVariableI, PrototypeI, ReferenceLocalVariableI,
};
use crate::instantiating::ast::types::FloatIT;
use crate::instantiating::ast::types::IntIT;
use crate::instantiating::ast::types::StrIT;
use crate::instantiating::ast::types::VoidIT;
use std::marker::PhantomData;

/// Arena-allocated (see @TFITCX)
//
// No PartialEq/Hash derive: Scala uses `vcurious` (panic on equals) on every
// expression case class; Rust's "no impl" gives a strictly stronger compile-time
// error. Mirrors typing/ast/expressions.rs ExpressionTE.
#[derive(Copy, Clone, Debug)]
pub enum ExpressionIE<'s, 'i, R> {
    Reference(ReferenceExpressionIE<'s, 'i, R>),
    Address(AddressExpressionIE<'s, 'i, R>),
}

impl<'s, 'i, R: Copy> ExpressionIE<'s, 'i, R> {
    pub fn result(&self) -> CoordI<'s, 'i, R> {
        match self {
            ExpressionIE::Reference(r) => r.result(),
            ExpressionIE::Address(a) => panic!("ExpressionIE::result: Address branch"),
        }
    }
}
impl<'s, 'i, R: Copy> ReferenceExpressionIE<'s, 'i, R> {
    pub fn result(&self) -> CoordI<'s, 'i, R> {
        match self {
            ReferenceExpressionIE::LetAndLend(x) => x.result,
            ReferenceExpressionIE::LockWeak(x) => x.result,
            ReferenceExpressionIE::BorrowToWeak(x) => x.result,
            ReferenceExpressionIE::LetNormal(x) => x.result,
            ReferenceExpressionIE::Restackify(_) => panic!("RE::result: Restackify"),
            ReferenceExpressionIE::Unlet(x) => x.result,
            ReferenceExpressionIE::Discard(_) => panic!("RE::result: Discard"),
            ReferenceExpressionIE::Defer(x) => x.result,
            ReferenceExpressionIE::If(x) => x.result,
            ReferenceExpressionIE::While(_) => panic!("RE::result: While"),
            ReferenceExpressionIE::Mutate(m) => m.result,
            ReferenceExpressionIE::Return(_) => panic!("RE::result: Return"),
            ReferenceExpressionIE::Break(_) => panic!("RE::result: Break"),
            ReferenceExpressionIE::Block(x) => x.result,
            ReferenceExpressionIE::Mutabilify(_) => panic!("RE::result: Mutabilify"),
            ReferenceExpressionIE::Immutabilify(_) => panic!("RE::result: Immutabilify"),
            ReferenceExpressionIE::PreCheckBorrow(_) => panic!("RE::result: PreCheckBorrow"),
            ReferenceExpressionIE::Consecutor(x) => x.result,
            ReferenceExpressionIE::Tuple(x) => x.result,
            ReferenceExpressionIE::StaticArrayFromValues(s) => s.result_reference,
            ReferenceExpressionIE::ArraySize(_) => panic!("RE::result: ArraySize"),
            ReferenceExpressionIE::IsSameInstance(x) => x.result(),
            ReferenceExpressionIE::AsSubtype(x) => x.result,
            ReferenceExpressionIE::VoidLiteral(v) => v.result(),
            ReferenceExpressionIE::ConstantInt(x) => x.result(),
            ReferenceExpressionIE::ConstantBool(x) => x.result(),
            ReferenceExpressionIE::ConstantStr(x) => x.result(),
            ReferenceExpressionIE::ConstantFloat(x) => x.result(),
            ReferenceExpressionIE::ArgLookup(x) => x.coord,
            ReferenceExpressionIE::ArrayLength(x) => x.result(),
            ReferenceExpressionIE::InterfaceFunctionCall(x) => x.result,
            ReferenceExpressionIE::ExternFunctionCall(e) => e.result,
            ReferenceExpressionIE::FunctionCall(c) => c.result,
            ReferenceExpressionIE::Reinterpret(_) => panic!("RE::result: Reinterpret"),
            ReferenceExpressionIE::Construct(c) => c.result,
            ReferenceExpressionIE::NewMutRuntimeSizedArray(n) => n.result,
            ReferenceExpressionIE::StaticArrayFromCallable(s) => s.result,
            ReferenceExpressionIE::DestroyStaticSizedArrayIntoFunction(d) => d.result(),
            ReferenceExpressionIE::DestroyStaticSizedArrayIntoLocals(_) => panic!("RE::result: DestroyStaticSizedArrayIntoLocals"),
            ReferenceExpressionIE::DestroyMutRuntimeSizedArray(_) => CoordI { ownership: OwnershipI::MutableShare, kind: KindIT::VoidIT(VoidIT { _marker: PhantomData }) },
            ReferenceExpressionIE::RuntimeSizedArrayCapacity(r) => r.result(),
            ReferenceExpressionIE::PushRuntimeSizedArray(_) => CoordI { ownership: OwnershipI::MutableShare, kind: KindIT::VoidIT(VoidIT { _marker: PhantomData }) },
            ReferenceExpressionIE::PopRuntimeSizedArray(p) => p.result,
            ReferenceExpressionIE::InterfaceToInterfaceUpcast(_) => panic!("RE::result: InterfaceToInterfaceUpcast"),
            ReferenceExpressionIE::Upcast(u) => u.result,
            ReferenceExpressionIE::SoftLoad(s) => s.result,
            ReferenceExpressionIE::Destroy(_) => panic!("RE::result: Destroy"),
            ReferenceExpressionIE::DestroyImmRuntimeSizedArray(_) => panic!("RE::result: DestroyImmRuntimeSizedArray"),
            ReferenceExpressionIE::NewImmRuntimeSizedArray(n) => n.result,
        }
    }
}

#[derive(Copy, Clone, Debug)]
pub enum ReferenceExpressionIE<'s, 'i, R> {
    LetAndLend(&'i LetAndLendIE<'s, 'i, R>),
    LockWeak(&'i LockWeakIE<'s, 'i, R>),
    BorrowToWeak(&'i BorrowToWeakIE<'s, 'i, R>),
    LetNormal(&'i LetNormalIE<'s, 'i, R>),
    Restackify(&'i RestackifyIE<'s, 'i, R>),
    Unlet(&'i UnletIE<'s, 'i, R>),
    Discard(&'i DiscardIE<'s, 'i, R>),
    Defer(&'i DeferIE<'s, 'i, R>),
    If(&'i IfIE<'s, 'i, R>),
    While(&'i WhileIE<'s, 'i, R>),
    Mutate(&'i MutateIE<'s, 'i, R>),
    Return(&'i ReturnIE<'s, 'i, R>),
    Break(&'i BreakIE<R>),
    Block(&'i BlockIE<'s, 'i, R>),
    Mutabilify(&'i MutabilifyIE<'s, 'i, R>),
    Immutabilify(&'i ImmutabilifyIE<'s, 'i, R>),
    PreCheckBorrow(&'i PreCheckBorrowIE<'s, 'i, R>),
    Consecutor(&'i ConsecutorIE<'s, 'i, R>),
    Tuple(&'i TupleIE<'s, 'i, R>),
    StaticArrayFromValues(&'i StaticArrayFromValuesIE<'s, 'i, R>),
    ArraySize(&'i ArraySizeIE<'s, 'i, R>),
    IsSameInstance(&'i IsSameInstanceIE<'s, 'i, R>),
    AsSubtype(&'i AsSubtypeIE<'s, 'i, R>),
    VoidLiteral(&'i VoidLiteralIE<R>),
    ConstantInt(&'i ConstantIntIE<R>),
    ConstantBool(&'i ConstantBoolIE<R>),
    ConstantStr(&'i ConstantStrIE<'s, R>),
    ConstantFloat(&'i ConstantFloatIE<R>),
    ArgLookup(&'i ArgLookupIE<'s, 'i, R>),
    ArrayLength(&'i ArrayLengthIE<'s, 'i, R>),
    InterfaceFunctionCall(&'i InterfaceFunctionCallIE<'s, 'i, R>),
    ExternFunctionCall(&'i ExternFunctionCallIE<'s, 'i, R>),
    FunctionCall(&'i FunctionCallIE<'s, 'i, R>),
    Reinterpret(&'i ReinterpretIE<'s, 'i, R>),
    Construct(&'i ConstructIE<'s, 'i, R>),
    NewMutRuntimeSizedArray(&'i NewMutRuntimeSizedArrayIE<'s, 'i, R>),
    StaticArrayFromCallable(&'i StaticArrayFromCallableIE<'s, 'i, R>),
    DestroyStaticSizedArrayIntoFunction(&'i DestroyStaticSizedArrayIntoFunctionIE<'s, 'i, R>),
    DestroyStaticSizedArrayIntoLocals(&'i DestroyStaticSizedArrayIntoLocalsIE<'s, 'i, R>),
    DestroyMutRuntimeSizedArray(&'i DestroyMutRuntimeSizedArrayIE<'s, 'i, R>),
    RuntimeSizedArrayCapacity(&'i RuntimeSizedArrayCapacityIE<'s, 'i, R>),
    PushRuntimeSizedArray(&'i PushRuntimeSizedArrayIE<'s, 'i, R>),
    PopRuntimeSizedArray(&'i PopRuntimeSizedArrayIE<'s, 'i, R>),
    InterfaceToInterfaceUpcast(&'i InterfaceToInterfaceUpcastIE<'s, 'i, R>),
    Upcast(&'i UpcastIE<'s, 'i, R>),
    SoftLoad(&'i SoftLoadIE<'s, 'i, R>),
    Destroy(&'i DestroyIE<'s, 'i, R>),
    DestroyImmRuntimeSizedArray(&'i DestroyImmRuntimeSizedArrayIE<'s, 'i, R>),
    NewImmRuntimeSizedArray(&'i NewImmRuntimeSizedArrayIE<'s, 'i, R>),
}

#[derive(Copy, Clone, Debug)]
pub enum AddressExpressionIE<'s, 'i, R> {
    LocalLookup(&'i LocalLookupIE<'s, 'i, R>),
    StaticSizedArrayLookup(&'i StaticSizedArrayLookupIE<'s, 'i, R>),
    RuntimeSizedArrayLookup(&'i RuntimeSizedArrayLookupIE<'s, 'i, R>),
    ReferenceMemberLookup(&'i ReferenceMemberLookupIE<'s, 'i, R>),
    AddressMemberLookup(&'i AddressMemberLookupIE<'s, 'i, R>),
}

/// Arena-allocated (see @TFITCX) — no equality.
#[derive(Copy, Clone, Debug)]
pub struct LetAndLendIE<'s, 'i, R> {
	pub variable: ILocalVariableI<'s, 'i>,
	pub expr: ReferenceExpressionIE<'s, 'i, R>,
	pub target_ownership: OwnershipI,
	pub result: CoordI<'s, 'i, R>,
}

// (Realized by `impl PartialEq for LetAndLendIE` below.)

// (Realized by `impl Hash for LetAndLendIE` below.)

/// Arena-allocated (see @TFITCX) — no equality.
#[derive(Copy, Clone, Debug)]
pub struct LockWeakIE<'s, 'i, R> {
	pub inner_expr: ReferenceExpressionIE<'s, 'i, R>,
	pub result_opt_borrow_type: CoordI<'s, 'i, R>,
	pub some_constructor: PrototypeI<'s, 'i, R>,
	pub none_constructor: PrototypeI<'s, 'i, R>,
	pub some_impl_name: IdI<'s, 'i, R>,
	pub none_impl_name: IdI<'s, 'i, R>,
	pub result: CoordI<'s, 'i, R>,
}

// (Realized by `impl PartialEq for LockWeakIE` below.)

// (Realized by `impl Hash for LockWeakIE` below.)

/// Arena-allocated (see @TFITCX) — no equality.
#[derive(Copy, Clone, Debug)]
pub struct BorrowToWeakIE<'s, 'i, R> {
	pub inner_expr: ReferenceExpressionIE<'s, 'i, R>,
	pub result: CoordI<'s, 'i, R>,
}

// (Realized by `impl PartialEq for BorrowToWeakIE` below.)

// (Realized by `impl Hash for BorrowToWeakIE` below.)

/// Arena-allocated (see @TFITCX) — no equality.
#[derive(Copy, Clone, Debug)]
pub struct LetNormalIE<'s, 'i, R> {
	pub variable: ILocalVariableI<'s, 'i>,
	pub expr: ReferenceExpressionIE<'s, 'i, R>,
	pub result: CoordI<'s, 'i, R>,
}

// (Realized by `impl PartialEq for LetNormalIE` below.)

// (Realized by `impl Hash for LetNormalIE` below.)

/// Arena-allocated (see @TFITCX) — no equality.
#[derive(Copy, Clone, Debug)]
pub struct RestackifyIE<'s, 'i, R> {
	pub variable: ILocalVariableI<'s, 'i>,
	pub expr: ReferenceExpressionIE<'s, 'i, R>,
	pub result: CoordI<'s, 'i, R>,
}

// (Realized by `impl PartialEq for RestackifyIE` below.)

// (Realized by `impl Hash for RestackifyIE` below.)

/// Arena-allocated (see @TFITCX) — no equality.
#[derive(Copy, Clone, Debug)]
pub struct UnletIE<'s, 'i, R> {
	pub variable: ILocalVariableI<'s, 'i>,
	pub result: CoordI<'s, 'i, R>,
}

// (Realized by `impl PartialEq for UnletIE` below.)

// (Realized by `impl Hash for UnletIE` below.)

/// Arena-allocated (see @TFITCX) — no equality.
#[derive(Copy, Clone, Debug)]
pub struct DiscardIE<'s, 'i, R> {
	pub expr: ReferenceExpressionIE<'s, 'i, R>,
}

// (Realized by `impl PartialEq for DiscardIE` below.)

// (Realized by `impl Hash for DiscardIE` below.)

impl<'s, 'i, R> DiscardIE<'s, 'i, R> {
	pub fn result(&self) -> CoordI<'s, 'i, R> { panic!("Unimplemented: result"); }
}

/// Arena-allocated (see @TFITCX) — no equality.
#[derive(Copy, Clone, Debug)]
pub struct DeferIE<'s, 'i, R> {
	pub inner_expr: ReferenceExpressionIE<'s, 'i, R>,
	pub deferred_expr: ReferenceExpressionIE<'s, 'i, R>,
	pub result: CoordI<'s, 'i, R>,
}

// (Realized by `impl PartialEq for DeferIE` below.)

// (Realized by `impl Hash for DeferIE` below.)

/// Arena-allocated (see @TFITCX) — no equality.
#[derive(Copy, Clone, Debug)]
pub struct IfIE<'s, 'i, R> {
	pub condition: ReferenceExpressionIE<'s, 'i, R>,
	pub then_call: ReferenceExpressionIE<'s, 'i, R>,
	pub else_call: ReferenceExpressionIE<'s, 'i, R>,
	pub result: CoordI<'s, 'i, R>,
}

// (Realized by `impl PartialEq for IfIE` below.)

// (Realized by `impl Hash for IfIE` below.)

/// Arena-allocated (see @TFITCX) — no equality.
#[derive(Copy, Clone, Debug)]
pub struct WhileIE<'s, 'i, R> {
	pub block: BlockIE<'s, 'i, R>,
	pub result: CoordI<'s, 'i, R>,
}

// (Realized by `impl PartialEq for WhileIE` below.)

// (Realized by `impl Hash for WhileIE` below.)

/// Arena-allocated (see @TFITCX) — no equality.
#[derive(Copy, Clone, Debug)]
pub struct MutateIE<'s, 'i, R> {
	pub destination_expr: AddressExpressionIE<'s, 'i, R>,
	pub source_expr: ReferenceExpressionIE<'s, 'i, R>,
	pub result: CoordI<'s, 'i, R>,
}

// (Realized by `impl PartialEq for MutateIE` below.)

// (Realized by `impl Hash for MutateIE` below.)

/// Arena-allocated (see @TFITCX) — no equality.
#[derive(Copy, Clone, Debug)]
pub struct ReturnIE<'s, 'i, R> {
	pub source_expr: ReferenceExpressionIE<'s, 'i, R>,
}

// (Realized by `impl PartialEq for ReturnIE` below.)

// (Realized by `impl Hash for ReturnIE` below.)

impl<'s, 'i, R> ReturnIE<'s, 'i, R> {
	pub fn result(&self) -> CoordI<'s, 'i, R> { panic!("Unimplemented: result"); }
}

/// Arena-allocated (see @TFITCX) — no equality.
#[derive(Copy, Clone, Debug)]
pub struct BreakIE<R>(pub PhantomData<R>);

// (Realized by `impl PartialEq for BreakIE` below.)

// (Realized by `impl Hash for BreakIE` below.)

impl<R> BreakIE<R> {
	pub fn result<'s, 'i>(&self) -> CoordI<'s, 'i, R> { panic!("Unimplemented: result"); }
}

/// Arena-allocated (see @TFITCX) — no equality.
#[derive(Copy, Clone, Debug)]
pub struct BlockIE<'s, 'i, R> {
	pub inner: ReferenceExpressionIE<'s, 'i, R>,
	pub result: CoordI<'s, 'i, R>,
}

// (Realized by `impl PartialEq for BlockIE` below.)

// (Realized by `impl Hash for BlockIE` below.)

/// Arena-allocated (see @TFITCX) — no equality.
#[derive(Copy, Clone, Debug)]
pub struct MutabilifyIE<'s, 'i, R> {
	pub inner: ReferenceExpressionIE<'s, 'i, R>,
	pub result: CoordI<'s, 'i, R>,
}

// (Realized by `impl PartialEq for MutabilifyIE` below.)

// (Realized by `impl Hash for MutabilifyIE` below.)

/// Arena-allocated (see @TFITCX) — no equality.
#[derive(Copy, Clone, Debug)]
pub struct ImmutabilifyIE<'s, 'i, R> {
	pub inner: ReferenceExpressionIE<'s, 'i, R>,
	pub result: CoordI<'s, 'i, R>,
}

// (Realized by `impl PartialEq for ImmutabilifyIE` below.)

// (Realized by `impl Hash for ImmutabilifyIE` below.)

/// Arena-allocated (see @TFITCX) — no equality.
#[derive(Copy, Clone, Debug)]
pub struct PreCheckBorrowIE<'s, 'i, R> {
	pub inner: ReferenceExpressionIE<'s, 'i, R>,
}

impl<'s, 'i, R> PreCheckBorrowIE<'s, 'i, R> {
	pub fn result(&self) -> CoordI<'s, 'i, R> { panic!("Unimplemented: result"); }
}

// (Realized by `impl PartialEq for PreCheckBorrowIE` below.)

// (Realized by `impl Hash for PreCheckBorrowIE` below.)

/// Arena-allocated (see @TFITCX) — no equality.
#[derive(Copy, Clone, Debug)]
pub struct ConsecutorIE<'s, 'i, R> {
	pub exprs: &'i[ReferenceExpressionIE<'s, 'i, R>],
	pub result: CoordI<'s, 'i, R>,
}

// (Realized by `impl PartialEq for ConsecutorIE` below.)

// (Realized by `impl Hash for ConsecutorIE` below.)

/// Arena-allocated (see @TFITCX) — no equality.
#[derive(Copy, Clone, Debug)]
pub struct TupleIE<'s, 'i, R> {
	pub elements: &'i[ReferenceExpressionIE<'s, 'i, R>],
	pub result: CoordI<'s, 'i, R>,
}

// (Realized by `impl PartialEq for TupleIE` below.)

// (Realized by `impl Hash for TupleIE` below.)

/// Arena-allocated (see @TFITCX) — no equality.
#[derive(Copy, Clone, Debug)]
pub struct StaticArrayFromValuesIE<'s, 'i, R> {
	pub elements: &'i[ReferenceExpressionIE<'s, 'i, R>],
	pub result_reference: CoordI<'s, 'i, R>,
	pub array_type: StaticSizedArrayIT<'s, 'i, R>,
}

// (Realized by `impl PartialEq for StaticArrayFromValuesIE` below.)

// (Realized by `impl Hash for StaticArrayFromValuesIE` below.)

impl<'s, 'i, R> StaticArrayFromValuesIE<'s, 'i, R> {
	pub fn result(&self) -> CoordI<'s, 'i, R> { panic!("Unimplemented: result"); }
}

/// Arena-allocated (see @TFITCX) — no equality.
#[derive(Copy, Clone, Debug)]
pub struct ArraySizeIE<'s, 'i, R> {
	pub array: ReferenceExpressionIE<'s, 'i, R>,
	pub result: CoordI<'s, 'i, R>,
}

// (Realized by `impl PartialEq for ArraySizeIE` below.)

// (Realized by `impl Hash for ArraySizeIE` below.)

/// Arena-allocated (see @TFITCX) — no equality.
#[derive(Copy, Clone, Debug)]
pub struct IsSameInstanceIE<'s, 'i, R> {
	pub left: ReferenceExpressionIE<'s, 'i, R>,
	pub right: ReferenceExpressionIE<'s, 'i, R>,
}

// (Realized by `impl PartialEq for IsSameInstanceIE` below.)

// (Realized by `impl Hash for IsSameInstanceIE` below.)

impl<'s, 'i, R> IsSameInstanceIE<'s, 'i, R> {
	pub fn result(&self) -> CoordI<'s, 'i, R> {
		CoordI { ownership: OwnershipI::MutableShare, kind: KindIT::BoolIT(BoolIT { _marker: PhantomData }) }
	}
}

/// Arena-allocated (see @TFITCX) — no equality.
#[derive(Copy, Clone, Debug)]
pub struct AsSubtypeIE<'s, 'i, R> {
	pub source_expr: ReferenceExpressionIE<'s, 'i, R>,
	pub target_type: CoordI<'s, 'i, R>,
	pub result_result_type: CoordI<'s, 'i, R>,
	pub ok_constructor: &'i PrototypeI<'s, 'i, R>,
	pub err_constructor: &'i PrototypeI<'s, 'i, R>,
	pub impl_name: IdI<'s, 'i, R>,
	pub ok_impl_name: IdI<'s, 'i, R>,
	pub err_impl_name: IdI<'s, 'i, R>,
	pub result: CoordI<'s, 'i, R>,
}

// (Realized by `impl PartialEq for AsSubtypeIE` below.)

// (Realized by `impl Hash for AsSubtypeIE` below.)

/// Arena-allocated (see @TFITCX) — no equality.
#[derive(Copy, Clone, Debug)]
pub struct VoidLiteralIE<R>(pub PhantomData<R>);

// (Realized by `impl PartialEq for VoidLiteralIE` below.)

// (Realized by `impl Hash for VoidLiteralIE` below.)

impl<R> VoidLiteralIE<R> {
	pub fn result<'s, 'i>(&self) -> CoordI<'s, 'i, R> {
		CoordI { ownership: OwnershipI::MutableShare, kind: KindIT::VoidIT(VoidIT { _marker: PhantomData }) }
	}
}

/// Arena-allocated (see @TFITCX) — no equality.
#[derive(Copy, Clone, Debug)]
pub struct ConstantIntIE<R> {
	pub value: i64,
	pub bits: i32,
	pub _marker: PhantomData<R>,
}

// (Realized by `impl PartialEq for ConstantIntIE` below.)

// (Realized by `impl Hash for ConstantIntIE` below.)

impl<R> ConstantIntIE<R> {
	pub fn result<'s, 'i>(&self) -> CoordI<'s, 'i, R> {
		CoordI {
			ownership: OwnershipI::MutableShare,
			kind: KindIT::IntIT(IntIT { bits: self.bits, _marker: PhantomData }),
		}
	}
}

/// Arena-allocated (see @TFITCX) — no equality.
#[derive(Copy, Clone, Debug)]
pub struct ConstantBoolIE<R> {
	pub _marker: PhantomData<R>,
	pub value: bool,
}

// (Realized by `impl PartialEq for ConstantBoolIE` below.)

// (Realized by `impl Hash for ConstantBoolIE` below.)

impl<R> ConstantBoolIE<R> {
	pub fn result<'s, 'i>(&self) -> CoordI<'s, 'i, R> {
		CoordI { ownership: OwnershipI::MutableShare, kind: KindIT::BoolIT(BoolIT { _marker: PhantomData }) }
	}
}

/// Arena-allocated (see @TFITCX) — no equality.
#[derive(Copy, Clone, Debug)]
pub struct ConstantStrIE<'s, R> {
	pub _marker: PhantomData<(&'s (), R)>,
	pub value: &'s str,
}

// (Realized by `impl PartialEq for ConstantStrIE` below.)

// (Realized by `impl Hash for ConstantStrIE` below.)

impl<'s, R> ConstantStrIE<'s, R> {
	pub fn result<'i>(&self) -> CoordI<'s, 'i, R> {
		CoordI { ownership: OwnershipI::MutableShare, kind: KindIT::StrIT(StrIT { _marker: PhantomData }) }
	}
}

/// Arena-allocated (see @TFITCX) — no equality.
#[derive(Copy, Clone, Debug)]
pub struct ConstantFloatIE<R> {
	pub _marker: PhantomData<R>,
	pub value: f64,
}

// (Realized by `impl PartialEq for ConstantFloatIE` below.)

// (Realized by `impl Hash for ConstantFloatIE` below.)

impl<R> ConstantFloatIE<R> {
	pub fn result<'s, 'i>(&self) -> CoordI<'s, 'i, R> {
		CoordI { ownership: OwnershipI::MutableShare, kind: KindIT::FloatIT(FloatIT { _marker: PhantomData }) }
	}
}

/// Arena-allocated (see @TFITCX) — no equality.
#[derive(Copy, Clone, Debug)]
pub struct LocalLookupIE<'s, 'i, R> {
	pub local_variable: ILocalVariableI<'s, 'i>,
	pub result: CoordI<'s, 'i, R>,
}

// (Realized by `impl PartialEq for LocalLookupIE` below.)

// (Realized by `impl Hash for LocalLookupIE` below.)

/// Arena-allocated (see @TFITCX) — no equality.
#[derive(Copy, Clone, Debug)]
pub struct ArgLookupIE<'s, 'i, R> {
	pub param_index: i32,
	pub coord: CoordI<'s, 'i, R>,
}

// (Realized by `impl PartialEq for ArgLookupIE` below.)

// (Realized by `impl Hash for ArgLookupIE` below.)

impl<'s, 'i, R> ArgLookupIE<'s, 'i, R> {
	pub fn result(&self) -> CoordI<'s, 'i, R> { panic!("Unimplemented: result"); }
}

/// Arena-allocated (see @TFITCX) — no equality.
#[derive(Copy, Clone, Debug)]
pub struct StaticSizedArrayLookupIE<'s, 'i, R> {
	pub range: RangeS<'s>,
	pub array_expr: ReferenceExpressionIE<'s, 'i, R>,
	pub index_expr: ReferenceExpressionIE<'s, 'i, R>,
	pub element_type: CoordI<'s, 'i, R>,
	pub variability: VariabilityI,
}

// (Realized by `impl PartialEq for StaticSizedArrayLookupIE` below.)

// (Realized by `impl Hash for StaticSizedArrayLookupIE` below.)

impl<'s, 'i, R> StaticSizedArrayLookupIE<'s, 'i, R> {
	pub fn result(&self) -> CoordI<'s, 'i, R> { panic!("Unimplemented: result"); }
}

/// Arena-allocated (see @TFITCX) — no equality.
#[derive(Copy, Clone, Debug)]
pub struct RuntimeSizedArrayLookupIE<'s, 'i, R> {
	pub array_expr: ReferenceExpressionIE<'s, 'i, R>,
	pub index_expr: ReferenceExpressionIE<'s, 'i, R>,
	pub element_type: CoordI<'s, 'i, R>,
	pub variability: VariabilityI,
}

// (Realized by `impl PartialEq for RuntimeSizedArrayLookupIE` below.)

// (Realized by `impl Hash for RuntimeSizedArrayLookupIE` below.)

impl<'s, 'i, R> RuntimeSizedArrayLookupIE<'s, 'i, R> {
	pub fn result(&self) -> CoordI<'s, 'i, R> { panic!("Unimplemented: result"); }
}

/// Arena-allocated (see @TFITCX) — no equality.
#[derive(Copy, Clone, Debug)]
pub struct ArrayLengthIE<'s, 'i, R> {
	pub array_expr: ReferenceExpressionIE<'s, 'i, R>,
}

// (Realized by `impl PartialEq for ArrayLengthIE` below.)

// (Realized by `impl Hash for ArrayLengthIE` below.)

impl<'s, 'i, R> ArrayLengthIE<'s, 'i, R> {
	pub fn result(&self) -> CoordI<'s, 'i, R> {
		CoordI {
			ownership: OwnershipI::MutableShare,
			kind: KindIT::IntIT(IntIT { bits: 32, _marker: PhantomData }),
		}
	}
}

/// Arena-allocated (see @TFITCX) — no equality.
#[derive(Copy, Clone, Debug)]
pub struct ReferenceMemberLookupIE<'s, 'i, R> {
	pub range: RangeS<'s>,
	pub struct_expr: ReferenceExpressionIE<'s, 'i, R>,
	pub member_name: IVarNameI<'s, 'i, R>,
	pub member_reference: CoordI<'s, 'i, R>,
	pub variability: VariabilityI,
}

// (Realized by `impl PartialEq for ReferenceMemberLookupIE` below.)

// (Realized by `impl Hash for ReferenceMemberLookupIE` below.)

impl<'s, 'i, R> ReferenceMemberLookupIE<'s, 'i, R> {
	pub fn result(&self) -> CoordI<'s, 'i, R> { panic!("Unimplemented: result"); }
}

/// Arena-allocated (see @TFITCX) — no equality.
#[derive(Copy, Clone, Debug)]
pub struct AddressMemberLookupIE<'s, 'i, R> {
	pub struct_expr: ReferenceExpressionIE<'s, 'i, R>,
	pub member_name: IVarNameI<'s, 'i, R>,
	pub member_reference: CoordI<'s, 'i, R>,
	pub variability: VariabilityI,
}

// (Realized by `impl PartialEq for AddressMemberLookupIE` below.)

// (Realized by `impl Hash for AddressMemberLookupIE` below.)

impl<'s, 'i, R> AddressMemberLookupIE<'s, 'i, R> {
	pub fn result(&self) -> CoordI<'s, 'i, R> { panic!("Unimplemented: result"); }
}

/// Arena-allocated (see @TFITCX) — no equality.
#[derive(Copy, Clone, Debug)]
pub struct InterfaceFunctionCallIE<'s, 'i, R> {
	pub super_function_prototype: &'i PrototypeI<'s, 'i, R>,
	pub virtual_param_index: i32,
	pub args: &'i[ReferenceExpressionIE<'s, 'i, R>],
	pub result: CoordI<'s, 'i, R>,
}

// (Realized by `impl PartialEq for InterfaceFunctionCallIE` below.)

// (Realized by `impl Hash for InterfaceFunctionCallIE` below.)

/// Arena-allocated (see @TFITCX) — no equality.
#[derive(Copy, Clone, Debug)]
pub struct ExternFunctionCallIE<'s, 'i, R> {
	pub prototype2: PrototypeI<'s, 'i, R>,
	pub args: &'i[ReferenceExpressionIE<'s, 'i, R>],
	pub result: CoordI<'s, 'i, R>,
}

// (Realized by `impl PartialEq for ExternFunctionCallIE` below.)

// (Realized by `impl Hash for ExternFunctionCallIE` below.)

/// Arena-allocated (see @TFITCX) — no equality.
#[derive(Copy, Clone, Debug)]
pub struct FunctionCallIE<'s, 'i, R> {
	pub callable: PrototypeI<'s, 'i, R>,
	pub args: &'i[ReferenceExpressionIE<'s, 'i, R>],
	pub result: CoordI<'s, 'i, R>,
}

// (Realized by `impl PartialEq for FunctionCallIE` below.)

// (Realized by `impl Hash for FunctionCallIE` below.)

/// Arena-allocated (see @TFITCX) — no equality.
#[derive(Copy, Clone, Debug)]
pub struct ReinterpretIE<'s, 'i, R> {
	pub expr: ReferenceExpressionIE<'s, 'i, R>,
	pub result_reference: CoordI<'s, 'i, R>,
	pub result: CoordI<'s, 'i, R>,
}

// (Realized by `impl PartialEq for ReinterpretIE` below.)

// (Realized by `impl Hash for ReinterpretIE` below.)

/// Arena-allocated (see @TFITCX) — no equality.
#[derive(Copy, Clone, Debug)]
pub struct ConstructIE<'s, 'i, R> {
	pub struct_tt: StructIT<'s, 'i, R>,
	pub result: CoordI<'s, 'i, R>,
	pub args: &'i[ExpressionIE<'s, 'i, R>],
}

// (Realized by `impl PartialEq for ConstructIE` below.)

// (Realized by `impl Hash for ConstructIE` below.)

/// Arena-allocated (see @TFITCX) — no equality.
#[derive(Copy, Clone, Debug)]
pub struct NewMutRuntimeSizedArrayIE<'s, 'i, R> {
	pub array_type: RuntimeSizedArrayIT<'s, 'i, R>,
	pub capacity_expr: ReferenceExpressionIE<'s, 'i, R>,
	pub result: CoordI<'s, 'i, R>,
}

// (Realized by `impl PartialEq for NewMutRuntimeSizedArrayIE` below.)

// (Realized by `impl Hash for NewMutRuntimeSizedArrayIE` below.)

/// Arena-allocated (see @TFITCX) — no equality.
#[derive(Copy, Clone, Debug)]
pub struct StaticArrayFromCallableIE<'s, 'i, R> {
	pub array_type: StaticSizedArrayIT<'s, 'i, R>,
	pub generator: ReferenceExpressionIE<'s, 'i, R>,
	pub generator_method: PrototypeI<'s, 'i, R>,
	pub result: CoordI<'s, 'i, R>,
}

// (Realized by `impl PartialEq for StaticArrayFromCallableIE` below.)

// (Realized by `impl Hash for StaticArrayFromCallableIE` below.)

/// Arena-allocated (see @TFITCX) — no equality.
#[derive(Copy, Clone, Debug)]
pub struct DestroyStaticSizedArrayIntoFunctionIE<'s, 'i, R> {
	pub array_expr: ReferenceExpressionIE<'s, 'i, R>,
	pub array_type: StaticSizedArrayIT<'s, 'i, R>,
	pub consumer: ReferenceExpressionIE<'s, 'i, R>,
	pub consumer_method: PrototypeI<'s, 'i, R>,
}

// (Realized by `impl PartialEq for DestroyStaticSizedArrayIntoFunctionIE` below.)

// (Realized by `impl Hash for DestroyStaticSizedArrayIntoFunctionIE` below.)

impl<'s, 'i, R> DestroyStaticSizedArrayIntoFunctionIE<'s, 'i, R> {
	pub fn result(&self) -> CoordI<'s, 'i, R> {
		CoordI { ownership: OwnershipI::MutableShare, kind: KindIT::VoidIT(VoidIT { _marker: PhantomData }) }
	}
}

/// Arena-allocated (see @TFITCX) — no equality.
#[derive(Copy, Clone, Debug)]
pub struct DestroyStaticSizedArrayIntoLocalsIE<'s, 'i, R> {
	pub expr: ReferenceExpressionIE<'s, 'i, R>,
	pub static_sized_array: StaticSizedArrayIT<'s, 'i, R>,
	pub destination_reference_variables: &'i[ReferenceLocalVariableI<'s, 'i>],
}

// (Realized by `impl PartialEq for DestroyStaticSizedArrayIntoLocalsIE` below.)

// (Realized by `impl Hash for DestroyStaticSizedArrayIntoLocalsIE` below.)

impl<'s, 'i, R> DestroyStaticSizedArrayIntoLocalsIE<'s, 'i, R> {
	pub fn result(&self) -> CoordI<'s, 'i, R> { panic!("Unimplemented: result"); }
}

/// Arena-allocated (see @TFITCX) — no equality.
#[derive(Copy, Clone, Debug)]
pub struct DestroyMutRuntimeSizedArrayIE<'s, 'i, R> {
	pub array_expr: ReferenceExpressionIE<'s, 'i, R>,
}

impl<'s, 'i, R> DestroyMutRuntimeSizedArrayIE<'s, 'i, R> {
	pub fn result(&self) -> CoordI<'s, 'i, R> { panic!("Unimplemented: result"); }
}

/// Arena-allocated (see @TFITCX) — no equality.
#[derive(Copy, Clone, Debug)]
pub struct RuntimeSizedArrayCapacityIE<'s, 'i, R> {
	pub array_expr: ReferenceExpressionIE<'s, 'i, R>,
}

impl<'s, 'i, R> RuntimeSizedArrayCapacityIE<'s, 'i, R> {
	pub fn result(&self) -> CoordI<'s, 'i, R> {
		CoordI { ownership: OwnershipI::MutableShare, kind: KindIT::IntIT(IntIT { bits: 32, _marker: PhantomData }) }
	}
}

/// Arena-allocated (see @TFITCX) — no equality.
#[derive(Copy, Clone, Debug)]
pub struct PushRuntimeSizedArrayIE<'s, 'i, R> {
	pub array_expr: ReferenceExpressionIE<'s, 'i, R>,
	pub new_element_expr: ReferenceExpressionIE<'s, 'i, R>,
}

impl<'s, 'i, R> PushRuntimeSizedArrayIE<'s, 'i, R> {
	pub fn result(&self) -> CoordI<'s, 'i, R> { panic!("Unimplemented: result"); }
}

/// Arena-allocated (see @TFITCX) — no equality.
#[derive(Copy, Clone, Debug)]
pub struct PopRuntimeSizedArrayIE<'s, 'i, R> {
	pub array_expr: ReferenceExpressionIE<'s, 'i, R>,
	pub result: CoordI<'s, 'i, R>,
}

/// Arena-allocated (see @TFITCX) — no equality.
#[derive(Copy, Clone, Debug)]
pub struct InterfaceToInterfaceUpcastIE<'s, 'i, R> {
	pub inner_expr: ReferenceExpressionIE<'s, 'i, R>,
	pub target_interface: InterfaceIT<'s, 'i, R>,
	pub result: CoordI<'s, 'i, R>,
}

// (Realized by `impl PartialEq for InterfaceToInterfaceUpcastIE` below.)

// (Realized by `impl Hash for InterfaceToInterfaceUpcastIE` below.)

/// Arena-allocated (see @TFITCX) — no equality.
#[derive(Copy, Clone, Debug)]
pub struct UpcastIE<'s, 'i, R> {
	pub inner_expr: ReferenceExpressionIE<'s, 'i, R>,
	pub target_interface: InterfaceIT<'s, 'i, R>,
	pub impl_name: IdI<'s, 'i, R>,
	pub result: CoordI<'s, 'i, R>,
}

// (Realized by `impl PartialEq for UpcastIE` below.)

// (Realized by `impl Hash for UpcastIE` below.)

/// Arena-allocated (see @TFITCX) — no equality.
#[derive(Copy, Clone, Debug)]
pub struct SoftLoadIE<'s, 'i, R> {
	pub expr: AddressExpressionIE<'s, 'i, R>,
	pub target_ownership: OwnershipI,
	pub result: CoordI<'s, 'i, R>,
}

// (Realized by `impl PartialEq for SoftLoadIE` below.)

// (Realized by `impl Hash for SoftLoadIE` below.)

/// Arena-allocated (see @TFITCX) — no equality.
#[derive(Copy, Clone, Debug)]
pub struct DestroyIE<'s, 'i, R> {
	pub expr: ReferenceExpressionIE<'s, 'i, R>,
	pub struct_tt: StructIT<'s, 'i, R>,
	pub destination_reference_variables: &'i[ReferenceLocalVariableI<'s, 'i>],
}

// (Realized by `impl PartialEq for DestroyIE` below.)

// (Realized by `impl Hash for DestroyIE` below.)

impl<'s, 'i, R> DestroyIE<'s, 'i, R> {
	pub fn result(&self) -> CoordI<'s, 'i, R> { panic!("Unimplemented: result"); }
}

/// Arena-allocated (see @TFITCX) — no equality.
#[derive(Copy, Clone, Debug)]
pub struct DestroyImmRuntimeSizedArrayIE<'s, 'i, R> {
	pub array_expr: ReferenceExpressionIE<'s, 'i, R>,
	pub array_type: RuntimeSizedArrayIT<'s, 'i, R>,
	pub consumer: ReferenceExpressionIE<'s, 'i, R>,
	pub consumer_method: PrototypeI<'s, 'i, R>,
}

// (Realized by `impl PartialEq for DestroyImmRuntimeSizedArrayIE` below.)

// (Realized by `impl Hash for DestroyImmRuntimeSizedArrayIE` below.)

impl<'s, 'i, R> DestroyImmRuntimeSizedArrayIE<'s, 'i, R> {
	pub fn result(&self) -> CoordI<'s, 'i, R> { panic!("Unimplemented: result"); }
}

/// Arena-allocated (see @TFITCX) — no equality.
#[derive(Copy, Clone, Debug)]
pub struct NewImmRuntimeSizedArrayIE<'s, 'i, R> {
	pub array_type: RuntimeSizedArrayIT<'s, 'i, R>,
	pub size_expr: ReferenceExpressionIE<'s, 'i, R>,
	pub generator: ReferenceExpressionIE<'s, 'i, R>,
	pub generator_method: PrototypeI<'s, 'i, R>,
	pub result: CoordI<'s, 'i, R>,
}

// (Realized by `impl PartialEq for NewImmRuntimeSizedArrayIE` below.)

// (Realized by `impl Hash for NewImmRuntimeSizedArrayIE` below.)

// (Realized via `impl TryFrom<ReferenceExpressionIE>` or inline match.)

// (Realized via `impl TryFrom<ReferenceExpressionIE>` or inline match.)
