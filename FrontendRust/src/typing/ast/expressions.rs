use crate::interner::StrI;
use crate::utils::range::RangeS;

use crate::typing::names::names::*;
use crate::typing::types::types::*;
use crate::typing::templata::templata::*;
use crate::typing::env::function_environment_t::*;
use crate::typing::ast::ast::*;
use crate::typing::types::types::{CoordT, KindT, NeverT, OwnershipT, VoidT};
use crate::typing::types::types::IntT;
use crate::typing::templata::templata::{ITemplataT, MutabilityTemplataT};
use crate::typing::types::types::MutabilityT;
use crate::typing::types::types::RegionT;
use crate::typing::types::types::BoolT;
use crate::typing::types::types::FloatT;
use std::any::Any;
use std::marker::PhantomData;

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub enum IExpressionResultT<'s, 't> {
    Reference(ReferenceResultT<'s, 't>),
    Address(AddressResultT<'s, 't>),
}

impl<'s, 't> IExpressionResultT<'s, 't> where 's: 't {
    pub fn expect_reference(&self) -> ReferenceResultT<'s, 't> {
        match self {
            IExpressionResultT::Reference(r) => *r,
            IExpressionResultT::Address(_) => panic!("Expected a reference as a result, but got an address!"),
        }
    }
    
    pub fn expect_address(&self) -> AddressResultT<'s, 't> {
        match self {
            IExpressionResultT::Address(a) => *a,
            IExpressionResultT::Reference(_) => panic!("Expected an address as a result, but got a reference!"),
        }
    }
    
    pub fn underlying_coord(&self) -> CoordT<'s, 't> {
        match self {
            IExpressionResultT::Reference(r) => r.coord,
            IExpressionResultT::Address(a) => a.coord,
        }
    }
    
    pub fn kind(&self) -> KindT<'s, 't> {
        match self {
            IExpressionResultT::Reference(r) => panic!("Unimplemented: kind Reference"),
            IExpressionResultT::Address(a) => panic!("Unimplemented: kind Address"),
        }
    }
    
}
/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct AddressResultT<'s, 't> { pub coord: CoordT<'s, 't> }

impl<'s, 't> AddressResultT<'s, 't> {

    fn underlying_coord(&self) -> CoordT<'s, 't> { panic!("Unimplemented: underlying_coord"); }

    fn kind(&self) -> KindT<'s, 't> { panic!("Unimplemented: kind"); }

}
/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct ReferenceResultT<'s, 't> { pub coord: CoordT<'s, 't> }

impl<'s, 't> ReferenceResultT<'s, 't> {

    pub fn underlying_coord(&self) -> CoordT<'s, 't> { self.coord }

    fn kind(&self) -> KindT<'s, 't> { panic!("Unimplemented: kind"); }

}
/// Value-type (see @TFITCX)
//
// Wrapper holding `ReferenceExpressionTE` / `AddressExpressionTE`. The inner
// expression hierarchies opt out of equality entirely (mirroring Scala's `vcurious`
// equals overrides — see comment above `ReferenceExpressionTE`), so this wrapper
// can't `derive(PartialEq)` either: the derive would call the inner type's eq, which
// doesn't exist. Misuse fails at compile time.
#[derive(Copy, Clone, Debug)]
pub enum ExpressionTE<'s, 't> {
    Reference(ReferenceExpressionTE<'s, 't>),
    Address(AddressExpressionTE<'s, 't>),
}

impl<'s, 't> ExpressionTE<'s, 't> where 's: 't {
    pub fn result(&self) -> IExpressionResultT<'s, 't> {
        match self {
            ExpressionTE::Reference(e) => IExpressionResultT::Reference(e.result()),
            ExpressionTE::Address(e) => IExpressionResultT::Address(e.result()),
        }
    }
    
    pub fn kind(&self) -> KindT<'s, 't> {
        match self {
            ExpressionTE::Reference(e) => panic!("Unimplemented: kind Reference"),
            ExpressionTE::Address(e) => panic!("Unimplemented: kind Address"),
        }
    }
    
}
/// Arena-allocated (see @TFITCX)
//
// No `PartialEq`/`Hash` derive or impl — opts out of equality entirely, mirroring
// Scala's `override def equals(obj: Any): Boolean = vcurious()` on every expression
// case class in `ast/expressions.scala` (52 occurrences). Scala's `vcurious` panics
// at runtime; Rust's "no impl" gives a strictly stronger compile-time error.
//
// Per @TFITCX this is `Arena-allocated` (lifetime/storage in the typing arena), but
// per @IEOIBZ such types normally implement identity equality via `std::ptr::eq`.
// The expression hierarchy is the exception: it's stored in the arena for memory
// reasons (large, deeply nested trees with `&'t` child pointers) but has no
// identity semantics — two distinct allocations of `ConstantIntTE { value: 5 }` are
// neither `==` (Scala vfails) nor distinguishable by identity (no callers care).
#[derive(Copy, Clone, Debug)]
pub enum ReferenceExpressionTE<'s, 't> {
    LetAndLend(&'t LetAndLendTE<'s, 't>),
    LockWeak(&'t LockWeakTE<'s, 't>),
    BorrowToWeak(&'t BorrowToWeakTE<'s, 't>),
    LetNormal(&'t LetNormalTE<'s, 't>),
    Unlet(&'t UnletTE<'s, 't>),
    Discard(&'t DiscardTE<'s, 't>),
    Defer(&'t DeferTE<'s, 't>),
    If(&'t IfTE<'s, 't>),
    While(&'t WhileTE<'s, 't>),
    Mutate(&'t MutateTE<'s, 't>),
    Restackify(&'t RestackifyTE<'s, 't>),
    Return(&'t ReturnTE<'s, 't>),
    Break(&'t BreakTE),
    Block(&'t BlockTE<'s, 't>),
    Pure(&'t PureTE<'s, 't>),
    Consecutor(&'t ConsecutorTE<'s, 't>),
    Tuple(&'t TupleTE<'s, 't>),
    StaticArrayFromValues(&'t StaticArrayFromValuesTE<'s, 't>),
    ArraySize(&'t ArraySizeTE<'s, 't>),
    IsSameInstance(&'t IsSameInstanceTE<'s, 't>),
    AsSubtype(&'t AsSubtypeTE<'s, 't>),
    VoidLiteral(&'t VoidLiteralTE),
    ConstantInt(&'t ConstantIntTE<'s, 't>),
    ConstantBool(&'t ConstantBoolTE),
    ConstantStr(&'t ConstantStrTE<'s>),
    ConstantFloat(&'t ConstantFloatTE),
    ArgLookup(&'t ArgLookupTE<'s, 't>),
    ArrayLength(&'t ArrayLengthTE<'s, 't>),
    InterfaceFunctionCall(&'t InterfaceFunctionCallTE<'s, 't>),
    ExternFunctionCall(&'t ExternFunctionCallTE<'s, 't>),
    FunctionCall(&'t FunctionCallTE<'s, 't>),
    Reinterpret(&'t ReinterpretTE<'s, 't>),
    Construct(&'t ConstructTE<'s, 't>),
    NewMutRuntimeSizedArray(&'t NewMutRuntimeSizedArrayTE<'s, 't>),
    StaticArrayFromCallable(&'t StaticArrayFromCallableTE<'s, 't>),
    DestroyStaticSizedArrayIntoFunction(&'t DestroyStaticSizedArrayIntoFunctionTE<'s, 't>),
    DestroyStaticSizedArrayIntoLocals(&'t DestroyStaticSizedArrayIntoLocalsTE<'s, 't>),
    DestroyMutRuntimeSizedArray(&'t DestroyMutRuntimeSizedArrayTE<'s, 't>),
    RuntimeSizedArrayCapacity(&'t RuntimeSizedArrayCapacityTE<'s, 't>),
    PushRuntimeSizedArray(&'t PushRuntimeSizedArrayTE<'s, 't>),
    PopRuntimeSizedArray(&'t PopRuntimeSizedArrayTE<'s, 't>),
    InterfaceToInterfaceUpcast(&'t InterfaceToInterfaceUpcastTE<'s, 't>),
    Upcast(&'t UpcastTE<'s, 't>),
    SoftLoad(&'t SoftLoadTE<'s, 't>),
    Destroy(&'t DestroyTE<'s, 't>),
    DestroyImmRuntimeSizedArray(&'t DestroyImmRuntimeSizedArrayTE<'s, 't>),
    NewImmRuntimeSizedArray(&'t NewImmRuntimeSizedArrayTE<'s, 't>),
}

impl<'s, 't> ReferenceExpressionTE<'s, 't> where 's: 't {
    pub fn result(&self) -> ReferenceResultT<'s, 't> {
        match self {
            ReferenceExpressionTE::LetAndLend(e) => e.result(),
            ReferenceExpressionTE::LockWeak(e) => e.result(),
            ReferenceExpressionTE::BorrowToWeak(e) => e.result(),
            ReferenceExpressionTE::LetNormal(e) => e.result(),
            ReferenceExpressionTE::Unlet(e) => e.result(),
            ReferenceExpressionTE::Discard(e) => e.result(),
            ReferenceExpressionTE::Defer(e) => e.result(),
            ReferenceExpressionTE::If(e) => e.result(),
            ReferenceExpressionTE::While(e) => e.result(),
            ReferenceExpressionTE::Mutate(e) => e.result(),
            ReferenceExpressionTE::Restackify(e) => e.result(),
            ReferenceExpressionTE::Return(e) => e.result(),
            ReferenceExpressionTE::Break(e) => e.result(),
            ReferenceExpressionTE::Block(e) => e.result(),
            ReferenceExpressionTE::Pure(e) => e.result(),
            ReferenceExpressionTE::Consecutor(e) => e.result(),
            ReferenceExpressionTE::Tuple(e) => e.result(),
            ReferenceExpressionTE::StaticArrayFromValues(e) => e.result(),
            ReferenceExpressionTE::ArraySize(e) => e.result(),
            ReferenceExpressionTE::IsSameInstance(e) => e.result(),
            ReferenceExpressionTE::AsSubtype(e) => e.result(),
            ReferenceExpressionTE::VoidLiteral(e) => e.result(),
            ReferenceExpressionTE::ConstantInt(e) => e.result(),
            ReferenceExpressionTE::ConstantBool(e) => e.result(),
            ReferenceExpressionTE::ConstantStr(e) => e.result(),
            ReferenceExpressionTE::ConstantFloat(e) => e.result(),
            ReferenceExpressionTE::ArgLookup(e) => e.result(),
            ReferenceExpressionTE::ArrayLength(e) => e.result(),
            ReferenceExpressionTE::InterfaceFunctionCall(e) => e.result(),
            ReferenceExpressionTE::ExternFunctionCall(e) => e.result(),
            ReferenceExpressionTE::FunctionCall(e) => e.result(),
            ReferenceExpressionTE::Reinterpret(e) => e.result(),
            ReferenceExpressionTE::Construct(e) => e.result(),
            ReferenceExpressionTE::NewMutRuntimeSizedArray(e) => e.result(),
            ReferenceExpressionTE::StaticArrayFromCallable(e) => e.result(),
            ReferenceExpressionTE::DestroyStaticSizedArrayIntoFunction(e) => e.result(),
            ReferenceExpressionTE::DestroyStaticSizedArrayIntoLocals(e) => e.result(),
            ReferenceExpressionTE::DestroyMutRuntimeSizedArray(e) => e.result(),
            ReferenceExpressionTE::RuntimeSizedArrayCapacity(e) => e.result(),
            ReferenceExpressionTE::PushRuntimeSizedArray(e) => e.result(),
            ReferenceExpressionTE::PopRuntimeSizedArray(e) => e.result(),
            ReferenceExpressionTE::InterfaceToInterfaceUpcast(e) => e.result(),
            ReferenceExpressionTE::Upcast(e) => e.result(),
            ReferenceExpressionTE::SoftLoad(e) => e.result(),
            ReferenceExpressionTE::Destroy(e) => e.result(),
            ReferenceExpressionTE::DestroyImmRuntimeSizedArray(e) => e.result(),
            ReferenceExpressionTE::NewImmRuntimeSizedArray(e) => e.result(),
        }
    }
    
    pub fn kind(&self) -> KindT<'s, 't> {
        self.result().coord.kind
    }
    
}
/// Arena-allocated (see @TFITCX)
#[derive(Copy, Clone, Debug)]
pub enum AddressExpressionTE<'s, 't> {
    LocalLookup(&'t LocalLookupTE<'s, 't>),
    StaticSizedArrayLookup(&'t StaticSizedArrayLookupTE<'s, 't>),
    RuntimeSizedArrayLookup(&'t RuntimeSizedArrayLookupTE<'s, 't>),
    ReferenceMemberLookup(&'t ReferenceMemberLookupTE<'s, 't>),
    AddressMemberLookup(&'t AddressMemberLookupTE<'s, 't>),
}

impl<'s, 't> AddressExpressionTE<'s, 't> where 's: 't {
    pub fn result(&self) -> AddressResultT<'s, 't> {
        match self {
            AddressExpressionTE::LocalLookup(e) => e.result(),
            AddressExpressionTE::StaticSizedArrayLookup(e) => e.result(),
            AddressExpressionTE::RuntimeSizedArrayLookup(e) => e.result(),
            AddressExpressionTE::ReferenceMemberLookup(e) => e.result(),
            AddressExpressionTE::AddressMemberLookup(e) => e.result(),
        }
    }
    
    pub fn kind(&self) -> KindT<'s, 't> {
        panic!("Unimplemented: kind");
    }
    
    pub fn range(&self) -> RangeS<'s> {
        match self {
            AddressExpressionTE::LocalLookup(e) => panic!("Unimplemented: range LocalLookup"),
            AddressExpressionTE::StaticSizedArrayLookup(e) => e.range,
            AddressExpressionTE::RuntimeSizedArrayLookup(e) => e.range,
            AddressExpressionTE::ReferenceMemberLookup(e) => e.range,
            AddressExpressionTE::AddressMemberLookup(e) => panic!("Unimplemented: range AddressMemberLookup"),
        }
    }
    
    pub fn variability(&self) -> VariabilityT {
        match self {
            AddressExpressionTE::LocalLookup(e) => e.variability(),
            AddressExpressionTE::StaticSizedArrayLookup(e) => e.variability,
            AddressExpressionTE::RuntimeSizedArrayLookup(e) => e.variability,
            AddressExpressionTE::ReferenceMemberLookup(e) => e.variability,
            AddressExpressionTE::AddressMemberLookup(e) => e.variability,
        }
    }
    
}
/// Arena-allocated (see @TFITCX)
#[derive(Debug)]
pub struct LetAndLendTE<'s, 't>
where 's: 't,
{
    pub variable: ILocalVariableT<'s, 't>,
    pub expr: ReferenceExpressionTE<'s, 't>,
    pub target_ownership: OwnershipT,
}

impl<'s, 't> LetAndLendTE<'s, 't> {

}
impl<'s, 't> LetAndLendTE<'s, 't> where 's: 't, {
    fn new(
        variable: ILocalVariableT<'s, 't>,
        expr: ReferenceExpressionTE<'s, 't>,
        target_ownership: OwnershipT,
    ) -> LetAndLendTE<'s, 't> { panic!("Unimplemented: LetAndLendTE::new"); }

}
impl<'s, 't> LetAndLendTE<'s, 't> {
    pub fn result(&self) -> ReferenceResultT<'s, 't> {
        let CoordT { ownership: _old_ownership, region, kind } = self.expr.result().coord;
        ReferenceResultT { coord: CoordT { ownership: self.target_ownership, region, kind } }
    }

}
/// Arena-allocated (see @TFITCX)
#[derive(Debug)]
pub struct LockWeakTE<'s, 't>
where 's: 't,
{
    pub inner_expr: ReferenceExpressionTE<'s, 't>,
    pub result_opt_borrow_type: CoordT<'s, 't>,
    pub some_constructor: &'t PrototypeT<'s, 't>,
    pub none_constructor: &'t PrototypeT<'s, 't>,
    pub some_impl_name: IdT<'s, 't>,
    pub none_impl_name: IdT<'s, 't>,
}

impl<'s, 't> LockWeakTE<'s, 't> {

    fn result(&self) -> ReferenceResultT<'s, 't> { ReferenceResultT { coord: self.result_opt_borrow_type } }

}
/// Arena-allocated (see @TFITCX)
#[derive(Debug)]
pub struct BorrowToWeakTE<'s, 't>
where 's: 't,
{
    pub inner_expr: ReferenceExpressionTE<'s, 't>,
}

impl<'s, 't> BorrowToWeakTE<'s, 't> {

    fn result(&self) -> ReferenceResultT<'s, 't> {
        ReferenceResultT { coord: CoordT { ownership: OwnershipT::Weak, region: self.inner_expr.result().coord.region, kind: self.inner_expr.kind() } }
    }

}
/// Arena-allocated (see @TFITCX)
#[derive(Debug)]
pub struct LetNormalTE<'s, 't>
where 's: 't,
{
    pub variable: ILocalVariableT<'s, 't>,
    pub expr: ReferenceExpressionTE<'s, 't>,
}

impl<'s, 't> LetNormalTE<'s, 't> {

    fn result(&self) -> ReferenceResultT<'s, 't> {
        ReferenceResultT {
            coord: CoordT {
                ownership: OwnershipT::Share,
                region: self.expr.result().coord.region,
                kind: KindT::Void(VoidT {}),
            }
        }
    }

}
/// Arena-allocated (see @TFITCX)
#[derive(Debug)]
pub struct UnletTE<'s, 't> {
    pub variable: ILocalVariableT<'s, 't>,
}

impl<'s, 't> UnletTE<'s, 't> {

    fn result(&self) -> ReferenceResultT<'s, 't> {
        ReferenceResultT { coord: self.variable.coord() }
    }

}
/// Arena-allocated (see @TFITCX)
#[derive(Debug)]
pub struct DiscardTE<'s, 't>
where 's: 't,
{
    pub expr: ReferenceExpressionTE<'s, 't>,
}

impl<'s, 't> DiscardTE<'s, 't> {

    fn result(&self) -> ReferenceResultT<'s, 't> {
        ReferenceResultT {
            coord: CoordT {
                ownership: OwnershipT::Share,
                region: self.expr.result().coord.region,
                kind: KindT::Void(VoidT),
            }
        }
    }

}
/// Arena-allocated (see @TFITCX)
#[derive(Debug)]
pub struct DeferTE<'s, 't>
where 's: 't,
{
    pub inner_expr: ReferenceExpressionTE<'s, 't>,
    pub deferred_expr: ReferenceExpressionTE<'s, 't>,
}

impl<'s, 't> DeferTE<'s, 't> {

    pub fn result(&self) -> ReferenceResultT<'s, 't> {
        ReferenceResultT { coord: self.inner_expr.result().coord }
    }

}
impl<'s, 't> DeferTE<'s, 't> where 's: 't, {
    pub fn new(
        inner_expr: ReferenceExpressionTE<'s, 't>,
        deferred_expr: ReferenceExpressionTE<'s, 't>,
    ) -> DeferTE<'s, 't> {
        let inner_coord = inner_expr.result().coord;
        assert!(deferred_expr.result().coord == CoordT {
            ownership: OwnershipT::Share,
            region: inner_coord.region,
            kind: KindT::Void(VoidT),
        });
        DeferTE { inner_expr, deferred_expr }
    }

}
/// Arena-allocated (see @TFITCX)
#[derive(Debug)]
pub struct IfTE<'s, 't>
where 's: 't,
{
    pub condition: ReferenceExpressionTE<'s, 't>,
    pub then_call: ReferenceExpressionTE<'s, 't>,
    pub else_call: ReferenceExpressionTE<'s, 't>,
    pub common_supertype: CoordT<'s, 't>,
}

impl<'s, 't> IfTE<'s, 't> {

    pub fn new(
        condition: ReferenceExpressionTE<'s, 't>,
        then_call: ReferenceExpressionTE<'s, 't>,
        else_call: ReferenceExpressionTE<'s, 't>,
    ) -> IfTE<'s, 't> {
        let condition_result_coord = condition.result().coord;
        let then_result_coord = then_call.result().coord;
        let else_result_coord = else_call.result().coord;
        match condition_result_coord {
            CoordT { kind: KindT::Bool(_), ownership: OwnershipT::Share, .. } => {}
            other => panic!("vfail: {:?}", other),
        }
        match (then_result_coord.kind, then_result_coord.kind) {
            (KindT::Never(_), _) => {}
            (_, KindT::Never(_)) => {}
            (a, b) if a == b => {}
            _ => panic!("vwat"),
        }
        let common_supertype = match then_result_coord.kind {
            KindT::Never(_) => else_result_coord,
            _ => then_result_coord,
        };
        IfTE { condition, then_call, else_call, common_supertype }
    }
    
    pub fn result(&self) -> ReferenceResultT<'s, 't> {
        ReferenceResultT { coord: self.common_supertype }
    }

}
/// Arena-allocated (see @TFITCX)
#[derive(Debug)]
pub struct WhileTE<'s, 't>
where 's: 't,
{
    pub block: BlockTE<'s, 't>,
    pub result_coord: CoordT<'s, 't>,
}

impl<'s, 't> WhileTE<'s, 't> {
    pub fn new(block: BlockTE<'s, 't>) -> WhileTE<'s, 't> {
        let result_coord = match block.result().coord.kind {
            KindT::Void(_) => block.result().coord,
            KindT::Never(NeverT { from_break: true }) => CoordT {
                ownership: OwnershipT::Share,
                region: block.result().coord.region,
                kind: KindT::Void(VoidT),
            },
            KindT::Never(NeverT { from_break: false }) => block.result().coord,
            _ => panic!("vwat"),
        };
        WhileTE { block, result_coord }
    }
    
    fn result(&self) -> ReferenceResultT<'s, 't> { ReferenceResultT { coord: self.result_coord } }

}
/// Arena-allocated (see @TFITCX)
#[derive(Debug)]
pub struct MutateTE<'s, 't>
where 's: 't,
{
    pub destination_expr: AddressExpressionTE<'s, 't>,
    pub source_expr: ReferenceExpressionTE<'s, 't>,
}

impl<'s, 't> MutateTE<'s, 't> {

    pub fn result(&self) -> ReferenceResultT<'s, 't> {
        ReferenceResultT { coord: self.destination_expr.result().coord }
    }

}
/// Arena-allocated (see @TFITCX)
#[derive(Debug)]
pub struct RestackifyTE<'s, 't>
where 's: 't,
{
    pub variable: ILocalVariableT<'s, 't>,
    pub source_expr: ReferenceExpressionTE<'s, 't>,
}

impl<'s, 't> RestackifyTE<'s, 't> {

    pub fn result(&self) -> ReferenceResultT<'s, 't> {
        ReferenceResultT { coord: CoordT {
            ownership: OwnershipT::Share,
            region: self.source_expr.result().coord.region,
            kind: KindT::Void(VoidT),
        } }
    }

}
/// Arena-allocated (see @TFITCX)
#[derive(Debug)]
pub struct ReturnTE<'s, 't>
where 's: 't,
{
    pub source_expr: ReferenceExpressionTE<'s, 't>,
}

impl<'s, 't> ReturnTE<'s, 't> {

    fn result(&self) -> ReferenceResultT<'s, 't> {
        ReferenceResultT {
            coord: CoordT {
                ownership: OwnershipT::Share,
                region: self.source_expr.result().coord.region,
                kind: KindT::Never(NeverT { from_break: false }),
            }
        }
    }

}
/// Arena-allocated (see @TFITCX)
#[derive(Debug)]
pub struct BreakTE {
    pub region: RegionT,
}

impl BreakTE {

    fn result<'s, 't>(&self) -> ReferenceResultT<'s, 't> {
        ReferenceResultT { coord: CoordT { ownership: OwnershipT::Share, region: self.region, kind: KindT::Never(NeverT { from_break: true }) } }
    }

}
/// Arena-allocated (see @TFITCX)
#[derive(Debug)]
pub struct BlockTE<'s, 't>
where 's: 't,
{
    pub inner: ReferenceExpressionTE<'s, 't>,
}

impl<'s, 't> BlockTE<'s, 't> {

    pub fn result(&self) -> ReferenceResultT<'s, 't> { self.inner.result() }

}
/// Arena-allocated (see @TFITCX)
#[derive(Debug)]
pub struct PureTE<'s, 't>
where 's: 't,
{
    pub newdefault_region: RegionT,
    pub inner: ReferenceExpressionTE<'s, 't>,
    pub result_type: CoordT<'s, 't>,
}

impl<'s, 't> PureTE<'s, 't> {

    pub fn result(&self) -> ReferenceResultT<'s, 't> { panic!("Unimplemented: result"); }

}
/// Arena-allocated (see @TFITCX)
#[derive(Debug)]
pub struct ConsecutorTE<'s, 't>
where 's: 't,
{
    pub exprs: &'t [ReferenceExpressionTE<'s, 't>],
}

impl<'s, 't> ConsecutorTE<'s, 't> {

}
impl<'s, 't> ConsecutorTE<'s, 't> where 's: 't, {
    fn new(exprs: &'t [ReferenceExpressionTE<'s, 't>]) -> ConsecutorTE<'s, 't> { panic!("Unimplemented: ConsecutorTE::new"); }

}
impl<'s, 't> ConsecutorTE<'s, 't> {
    pub fn result(&self) -> ReferenceResultT<'s, 't> {
        let never_coord = self.exprs.iter()
            .map(|e| e.result().coord)
            .find(|c| matches!(c, CoordT { ownership: OwnershipT::Share, kind: KindT::Never(_), .. }));
        match never_coord {
            Some(n) => ReferenceResultT { coord: n },
            None => self.exprs.last().unwrap().result(),
        }
    }

    fn last_reference_expr(&self) -> &ReferenceExpressionTE<'s, 't> { panic!("Unimplemented: last_reference_expr"); }

}
/// Arena-allocated (see @TFITCX)
#[derive(Debug)]
pub struct TupleTE<'s, 't>
where 's: 't,
{
    pub elements: &'t [ReferenceExpressionTE<'s, 't>],
    pub result_reference: CoordT<'s, 't>,
}

impl<'s, 't> TupleTE<'s, 't> {

    pub fn result(&self) -> ReferenceResultT<'s, 't> { ReferenceResultT { coord: self.result_reference } }

}
/// Arena-allocated (see @TFITCX)
#[derive(Debug)]
pub struct StaticArrayFromValuesTE<'s, 't>
where 's: 't,
{
    pub elements: &'t [ReferenceExpressionTE<'s, 't>],
    pub result_reference: CoordT<'s, 't>,
    pub array_type: &'t StaticSizedArrayTT<'s, 't>,
}

impl<'s, 't> StaticArrayFromValuesTE<'s, 't> {

    fn result(&self) -> ReferenceResultT<'s, 't> { ReferenceResultT { coord: self.result_reference } }

}
/// Arena-allocated (see @TFITCX)
#[derive(Debug)]
pub struct ArraySizeTE<'s, 't>
where 's: 't,
{
    pub array: ReferenceExpressionTE<'s, 't>,
}

impl<'s, 't> ArraySizeTE<'s, 't> {

    fn result(&self) -> ReferenceResultT<'s, 't> { panic!("Unimplemented: result"); }

}
/// Arena-allocated (see @TFITCX)
#[derive(Debug)]
pub struct IsSameInstanceTE<'s, 't>
where 's: 't,
{
    pub left: ReferenceExpressionTE<'s, 't>,
    pub right: ReferenceExpressionTE<'s, 't>,
}

impl<'s, 't> IsSameInstanceTE<'s, 't> {

}
impl<'s, 't> IsSameInstanceTE<'s, 't> where 's: 't, {
    fn new(left: ReferenceExpressionTE<'s, 't>, right: ReferenceExpressionTE<'s, 't>) -> IsSameInstanceTE<'s, 't> { panic!("Unimplemented: IsSameInstanceTE::new"); }

}
impl<'s, 't> IsSameInstanceTE<'s, 't> {
    pub fn result(&self) -> ReferenceResultT<'s, 't> {
        ReferenceResultT {
            coord: CoordT {
                ownership: OwnershipT::Share,
                region: self.left.result().coord.region,
                kind: KindT::Bool(BoolT),
            },
        }
    }

}
/// Arena-allocated (see @TFITCX)
#[derive(Debug)]
pub struct AsSubtypeTE<'s, 't>
where 's: 't,
{
    pub source_expr: ReferenceExpressionTE<'s, 't>,
    pub target_type: CoordT<'s, 't>,
    pub result_result_type: CoordT<'s, 't>,
    pub ok_constructor: &'t PrototypeT<'s, 't>,
    pub err_constructor: &'t PrototypeT<'s, 't>,
    pub impl_name: IdT<'s, 't>,
    pub ok_impl_name: IdT<'s, 't>,
    pub err_impl_name: IdT<'s, 't>,
}

impl<'s, 't> AsSubtypeTE<'s, 't> {

    fn result(&self) -> ReferenceResultT<'s, 't> { ReferenceResultT { coord: self.result_result_type } }

}
/// Arena-allocated (see @TFITCX)
#[derive(Debug)]
pub struct VoidLiteralTE {
    pub region: RegionT,
}

impl VoidLiteralTE {

    fn result<'s, 't>(&self) -> ReferenceResultT<'s, 't> {
        ReferenceResultT {
            coord: CoordT {
                ownership: OwnershipT::Share,
                region: self.region,
                kind: KindT::Void(VoidT),
            }
        }
    }

}
/// Arena-allocated (see @TFITCX)
#[derive(Debug)]
pub struct ConstantIntTE<'s, 't> {
    pub value: ITemplataT<'s, 't>,
    pub bits: i32,
    pub region: RegionT,
}

impl<'s, 't> ConstantIntTE<'s, 't> {

    fn result(&self) -> ReferenceResultT<'s, 't> {
        ReferenceResultT { coord: CoordT { ownership: OwnershipT::Share, region: self.region, kind: KindT::Int(IntT { bits: self.bits }) } }
    }

}
/// Arena-allocated (see @TFITCX)
#[derive(Debug)]
pub struct ConstantBoolTE {
    pub value: bool,
    pub region: RegionT,
}

impl ConstantBoolTE {

    pub fn result<'s, 't>(&self) -> ReferenceResultT<'s, 't> {
        ReferenceResultT { coord: CoordT { ownership: OwnershipT::Share, region: self.region, kind: KindT::Bool(BoolT) } }
    }

}
/// Arena-allocated (see @TFITCX)
#[derive(Debug)]
pub struct ConstantStrTE<'s> {
    pub value: StrI<'s>,
    pub region: RegionT,
}

impl<'s> ConstantStrTE<'s> {

    fn result<'t>(&self) -> ReferenceResultT<'s, 't> {
        ReferenceResultT { coord: CoordT { ownership: OwnershipT::Share, region: self.region, kind: KindT::Str(StrT) } }
    }

}
/// Arena-allocated (see @TFITCX)
#[derive(Debug)]
pub struct ConstantFloatTE {
    pub value: f64,
    pub region: RegionT,
}

impl ConstantFloatTE {

    pub fn result<'s, 't>(&self) -> ReferenceResultT<'s, 't> {
        ReferenceResultT { coord: CoordT {
            ownership: OwnershipT::Share,
            region: self.region,
            kind: KindT::Float(FloatT),
        } }
    }

}
/// Arena-allocated (see @TFITCX)
#[derive(Debug)]
pub struct LocalLookupTE<'s, 't> {
    pub range: RangeS<'s>,
    pub local_variable: ILocalVariableT<'s, 't>,
}

impl<'s, 't> LocalLookupTE<'s, 't> {

    pub fn result(&self) -> AddressResultT<'s, 't> {
        AddressResultT { coord: self.local_variable.coord() }
    }

    pub fn variability(&self) -> VariabilityT { self.local_variable.variability() }

}
/// Arena-allocated (see @TFITCX)
#[derive(Debug)]
pub struct ArgLookupTE<'s, 't> {
    pub param_index: i32,
    pub coord: CoordT<'s, 't>,
}

impl<'s, 't> ArgLookupTE<'s, 't> {

    fn result(&self) -> ReferenceResultT<'s, 't> {
        ReferenceResultT { coord: self.coord }
    }

}
/// Arena-allocated (see @TFITCX)
#[derive(Debug)]
pub struct StaticSizedArrayLookupTE<'s, 't>
where 's: 't,
{
    pub range: RangeS<'s>,
    pub array_expr: ReferenceExpressionTE<'s, 't>,
    pub array_type: &'t StaticSizedArrayTT<'s, 't>,
    pub index_expr: ReferenceExpressionTE<'s, 't>,
    pub element_type: CoordT<'s, 't>,
    pub variability: VariabilityT,
}

impl<'s, 't> StaticSizedArrayLookupTE<'s, 't> {

    fn result(&self) -> AddressResultT<'s, 't> { AddressResultT { coord: self.element_type } }

}
/// Arena-allocated (see @TFITCX)
#[derive(Debug)]
pub struct RuntimeSizedArrayLookupTE<'s, 't>
where 's: 't,
{
    pub range: RangeS<'s>,
    pub array_expr: ReferenceExpressionTE<'s, 't>,
    pub array_type: &'t RuntimeSizedArrayTT<'s, 't>,
    pub index_expr: ReferenceExpressionTE<'s, 't>,
    pub variability: VariabilityT,
}

impl<'s, 't> RuntimeSizedArrayLookupTE<'s, 't> {

}
impl<'s, 't> RuntimeSizedArrayLookupTE<'s, 't> where 's: 't, {
    pub fn new(
        range: RangeS<'s>,
        array_expr: ReferenceExpressionTE<'s, 't>,
        array_type: &'t RuntimeSizedArrayTT<'s, 't>,
        index_expr: ReferenceExpressionTE<'s, 't>,
        variability: VariabilityT,
    ) -> RuntimeSizedArrayLookupTE<'s, 't> {
        assert_eq!(array_expr.result().coord.kind, KindT::RuntimeSizedArray(array_type));
        RuntimeSizedArrayLookupTE { range, array_expr, array_type, index_expr, variability }
    }

}
impl<'s, 't> RuntimeSizedArrayLookupTE<'s, 't> {
    pub fn result(&self) -> AddressResultT<'s, 't> {
        // See RMLRMO why we just return the element type.
        AddressResultT { coord: self.array_type.element_type() }
    }

}
/// Arena-allocated (see @TFITCX)
#[derive(Debug)]
pub struct ArrayLengthTE<'s, 't>
where 's: 't,
{
    pub array_expr: ReferenceExpressionTE<'s, 't>,
}

impl<'s, 't> ArrayLengthTE<'s, 't> {

    fn result(&self) -> ReferenceResultT<'s, 't> {
        ReferenceResultT {
            coord: CoordT {
                ownership: OwnershipT::Share,
                region: self.array_expr.result().coord.region,
                kind: KindT::Int(IntT::I32),
            },
        }
    }

}
/// Arena-allocated (see @TFITCX)
#[derive(Debug)]
pub struct ReferenceMemberLookupTE<'s, 't>
where 's: 't,
{
    pub range: RangeS<'s>,
    pub struct_expr: ReferenceExpressionTE<'s, 't>,
    pub member_name: IVarNameT<'s, 't>,
    pub member_reference: CoordT<'s, 't>,
    pub variability: VariabilityT,
}

impl<'s, 't> ReferenceMemberLookupTE<'s, 't> {

    pub fn result(&self) -> AddressResultT<'s, 't> {
        // See RMLRMO why we just return the member type.
        AddressResultT { coord: self.member_reference }
    }

}
/// Arena-allocated (see @TFITCX)
#[derive(Debug)]
pub struct AddressMemberLookupTE<'s, 't>
where 's: 't,
{
    pub range: RangeS<'s>,
    pub struct_expr: ReferenceExpressionTE<'s, 't>,
    pub member_name: IVarNameT<'s, 't>,
    pub result_type2: CoordT<'s, 't>,
    pub variability: VariabilityT,
}

impl<'s, 't> AddressMemberLookupTE<'s, 't> {

    fn result(&self) -> AddressResultT<'s, 't> { AddressResultT { coord: self.result_type2 } }

}
/// Arena-allocated (see @TFITCX)
#[derive(Debug)]
pub struct InterfaceFunctionCallTE<'s, 't>
where 's: 't,
{
    pub super_function_prototype: &'t PrototypeT<'s, 't>,
    pub virtual_param_index: i32,
    pub result_reference: CoordT<'s, 't>,
    pub args: &'t [ReferenceExpressionTE<'s, 't>],
}

impl<'s, 't> InterfaceFunctionCallTE<'s, 't> {

    fn result(&self) -> ReferenceResultT<'s, 't> { ReferenceResultT { coord: self.result_reference } }

}

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct GenericParametersInheritance {
  pub num_inherited_generic_parameters: i32,
}

/// Arena-allocated (see @TFITCX)
#[derive(Debug)]
pub struct ExternFunctionCallTE<'s, 't>
where 's: 't,
{
    pub prototype2: &'t PrototypeT<'s, 't>,
    pub args: &'t [ReferenceExpressionTE<'s, 't>],
}

impl<'s, 't> ExternFunctionCallTE<'s, 't> {

    fn result(&self) -> ReferenceResultT<'s, 't> { ReferenceResultT { coord: self.prototype2.return_type } }

}
/// Arena-allocated (see @TFITCX)
#[derive(Debug)]
pub struct FunctionCallTE<'s, 't>
where 's: 't,
{
    pub callable: &'t PrototypeT<'s, 't>,
    pub args: &'t [ReferenceExpressionTE<'s, 't>],
    pub return_type: CoordT<'s, 't>,
}

impl<'s, 't> FunctionCallTE<'s, 't> {

}
impl<'s, 't> FunctionCallTE<'s, 't> where 's: 't, {
    fn new(
        callable: &'t PrototypeT<'s, 't>,
        args: &'t [ReferenceExpressionTE<'s, 't>],
        return_type: CoordT<'s, 't>,
    ) -> FunctionCallTE<'s, 't> { panic!("Unimplemented: FunctionCallTE::new"); }

}
impl<'s, 't> FunctionCallTE<'s, 't> {
    fn result(&self) -> ReferenceResultT<'s, 't> { ReferenceResultT { coord: self.return_type } }

}
/// Arena-allocated (see @TFITCX)
#[derive(Debug)]
pub struct ReinterpretTE<'s, 't>
where 's: 't,
{
    pub expr: ReferenceExpressionTE<'s, 't>,
    pub result_reference: CoordT<'s, 't>,
}

impl<'s, 't> ReinterpretTE<'s, 't> {

}
impl<'s, 't> ReinterpretTE<'s, 't> where 's: 't, {
    fn new(expr: ReferenceExpressionTE<'s, 't>, result_reference: CoordT<'s, 't>) -> ReinterpretTE<'s, 't> { panic!("Unimplemented: ReinterpretTE::new"); }

}
impl<'s, 't> ReinterpretTE<'s, 't> {
    fn result(&self) -> ReferenceResultT<'s, 't> {
        ReferenceResultT { coord: self.result_reference }
    }

}
/// Arena-allocated (see @TFITCX)
#[derive(Debug)]
pub struct ConstructTE<'s, 't>
where 's: 't,
{
    pub struct_tt: &'t StructTT<'s, 't>,
    pub result_reference: CoordT<'s, 't>,
    pub args: &'t [ExpressionTE<'s, 't>],
}

impl<'s, 't> ConstructTE<'s, 't> {

    fn result(&self) -> ReferenceResultT<'s, 't> { ReferenceResultT { coord: self.result_reference } }

}
/// Arena-allocated (see @TFITCX)
#[derive(Debug)]
pub struct NewMutRuntimeSizedArrayTE<'s, 't>
where 's: 't,
{
    pub array_type: &'t RuntimeSizedArrayTT<'s, 't>,
    pub region: RegionT,
    pub capacity_expr: ReferenceExpressionTE<'s, 't>,
}

impl<'s, 't> NewMutRuntimeSizedArrayTE<'s, 't> {

    fn result(&self) -> ReferenceResultT<'s, 't> {
        let ownership = match self.array_type.mutability() {
            ITemplataT::Mutability(MutabilityTemplataT { mutability: MutabilityT::Mutable }) => OwnershipT::Own,
            ITemplataT::Mutability(MutabilityTemplataT { mutability: MutabilityT::Immutable }) => OwnershipT::Share,
            ITemplataT::Placeholder(_) => panic!("vimpl"),
            _ => panic!("vwat"),
        };
        ReferenceResultT {
            coord: CoordT {
                ownership,
                region: self.region,
                kind: KindT::RuntimeSizedArray(self.array_type),
            },
        }
    }

}
/// Arena-allocated (see @TFITCX)
#[derive(Debug)]
pub struct StaticArrayFromCallableTE<'s, 't>
where 's: 't,
{
    pub array_type: &'t StaticSizedArrayTT<'s, 't>,
    pub region: RegionT,
    pub generator: ReferenceExpressionTE<'s, 't>,
    pub generator_method: &'t PrototypeT<'s, 't>,
}

impl<'s, 't> StaticArrayFromCallableTE<'s, 't> {

    pub fn result(&self) -> ReferenceResultT<'s, 't> {
        let ownership = match self.array_type.mutability() {
            ITemplataT::Mutability(MutabilityTemplataT { mutability: MutabilityT::Mutable }) => OwnershipT::Own,
            ITemplataT::Mutability(MutabilityTemplataT { mutability: MutabilityT::Immutable }) => OwnershipT::Share,
            ITemplataT::Placeholder(_) => panic!("Unimplemented: StaticArrayFromCallableTE result PlaceholderTemplataT"),
            _ => panic!("vwat"),
        };
        ReferenceResultT { coord: CoordT { ownership, region: self.region, kind: KindT::StaticSizedArray(self.array_type) } }
    }

}
/// Arena-allocated (see @TFITCX)
#[derive(Debug)]
pub struct DestroyStaticSizedArrayIntoFunctionTE<'s, 't>
where 's: 't,
{
    pub array_expr: ReferenceExpressionTE<'s, 't>,
    pub array_type: &'t StaticSizedArrayTT<'s, 't>,
    pub consumer: ReferenceExpressionTE<'s, 't>,
    pub consumer_method: &'t PrototypeT<'s, 't>,
}

impl<'s, 't> DestroyStaticSizedArrayIntoFunctionTE<'s, 't> {

}
impl<'s, 't> DestroyStaticSizedArrayIntoFunctionTE<'s, 't> where 's: 't, {
    fn new(
        array_expr: ReferenceExpressionTE<'s, 't>,
        array_type: &'t StaticSizedArrayTT<'s, 't>,
        consumer: ReferenceExpressionTE<'s, 't>,
        consumer_method: &'t PrototypeT<'s, 't>,
    ) -> DestroyStaticSizedArrayIntoFunctionTE<'s, 't> { panic!("Unimplemented: DestroyStaticSizedArrayIntoFunctionTE::new"); }

}
impl<'s, 't> DestroyStaticSizedArrayIntoFunctionTE<'s, 't> {
    fn result(&self) -> ReferenceResultT<'s, 't> {
        ReferenceResultT {
            coord: CoordT {
                ownership: OwnershipT::Share,
                region: self.array_expr.result().coord.region,
                kind: KindT::Void(VoidT),
            },
        }
    }

}
/// Arena-allocated (see @TFITCX)
#[derive(Debug)]
pub struct DestroyStaticSizedArrayIntoLocalsTE<'s, 't>
where 's: 't,
{
    pub expr: ReferenceExpressionTE<'s, 't>,
    pub static_sized_array: &'t StaticSizedArrayTT<'s, 't>,
    pub destination_reference_variables: &'t [ReferenceLocalVariableT<'s, 't>],
}

impl<'s, 't> DestroyStaticSizedArrayIntoLocalsTE<'s, 't> {

    fn result(&self) -> ReferenceResultT<'s, 't> {
        ReferenceResultT { coord: CoordT { ownership: OwnershipT::Share, region: self.expr.result().coord.region, kind: KindT::Void(VoidT) } }
    }

}
impl<'s, 't> DestroyStaticSizedArrayIntoLocalsTE<'s, 't> where 's: 't, {
    fn new(
        expr: ReferenceExpressionTE<'s, 't>,
        static_sized_array: &'t StaticSizedArrayTT<'s, 't>,
        destination_reference_variables: &'t [ReferenceLocalVariableT<'s, 't>],
    ) -> DestroyStaticSizedArrayIntoLocalsTE<'s, 't> { panic!("Unimplemented: DestroyStaticSizedArrayIntoLocalsTE::new"); }

}
/// Arena-allocated (see @TFITCX)
#[derive(Debug)]
pub struct DestroyMutRuntimeSizedArrayTE<'s, 't>
where 's: 't,
{
    pub array_expr: ReferenceExpressionTE<'s, 't>,
}

impl<'s, 't> DestroyMutRuntimeSizedArrayTE<'s, 't> {
    fn result(&self) -> ReferenceResultT<'s, 't> {
        ReferenceResultT {
            coord: CoordT {
                ownership: OwnershipT::Share,
                region: self.array_expr.result().coord.region,
                kind: KindT::Void(VoidT),
            }
        }
    }

}
/// Arena-allocated (see @TFITCX)
#[derive(Debug)]
pub struct RuntimeSizedArrayCapacityTE<'s, 't>
where 's: 't,
{
    pub array_expr: ReferenceExpressionTE<'s, 't>,
}

impl<'s, 't> RuntimeSizedArrayCapacityTE<'s, 't> {
    fn result(&self) -> ReferenceResultT<'s, 't> {
        ReferenceResultT {
            coord: CoordT {
                ownership: OwnershipT::Share,
                region: self.array_expr.result().coord.region,
                kind: KindT::Int(IntT { bits: 32 }),
            },
        }
    }

}
/// Arena-allocated (see @TFITCX)
#[derive(Debug)]
pub struct PushRuntimeSizedArrayTE<'s, 't>
where 's: 't,
{
    pub array_expr: ReferenceExpressionTE<'s, 't>,
    pub new_element_expr: ReferenceExpressionTE<'s, 't>,
}

impl<'s, 't> PushRuntimeSizedArrayTE<'s, 't> {
    fn result(&self) -> ReferenceResultT<'s, 't> {
        ReferenceResultT {
            coord: CoordT {
                ownership: OwnershipT::Share,
                region: self.array_expr.result().coord.region,
                kind: KindT::Void(VoidT),
            },
        }
    }

}
/// Arena-allocated (see @TFITCX)
#[derive(Debug)]
pub struct PopRuntimeSizedArrayTE<'s, 't>
where 's: 't,
{
    pub array_expr: ReferenceExpressionTE<'s, 't>,
    pub element_type: CoordT<'s, 't>,
}

impl<'s, 't> PopRuntimeSizedArrayTE<'s, 't> {
    fn result(&self) -> ReferenceResultT<'s, 't> {
        ReferenceResultT { coord: self.element_type }
    }

}
/// Arena-allocated (see @TFITCX)
#[derive(Debug)]
pub struct InterfaceToInterfaceUpcastTE<'s, 't>
where 's: 't,
{
    pub inner_expr: ReferenceExpressionTE<'s, 't>,
    pub target_interface: &'t InterfaceTT<'s, 't>,
}

impl<'s, 't> InterfaceToInterfaceUpcastTE<'s, 't> {

    fn result(&self) -> ReferenceResultT<'s, 't> { panic!("Unimplemented: result"); }

}
/// Arena-allocated (see @TFITCX)
#[derive(Debug)]
pub struct UpcastTE<'s, 't>
where 's: 't,
{
    pub inner_expr: ReferenceExpressionTE<'s, 't>,
    pub target_super_kind: ISuperKindTT<'s, 't>,
    pub impl_name: IdT<'s, 't>,
}

impl<'s, 't> UpcastTE<'s, 't> {

    pub fn result(&self) -> ReferenceResultT<'s, 't> {
        let inner_coord = self.inner_expr.result().coord;
        ReferenceResultT {
            coord: CoordT {
                ownership: inner_coord.ownership,
                region: inner_coord.region,
                kind: self.target_super_kind.into(),
            }
        }
    }

}
/// Arena-allocated (see @TFITCX)
#[derive(Debug)]
pub struct SoftLoadTE<'s, 't>
where 's: 't,
{
    pub expr: AddressExpressionTE<'s, 't>,
    pub target_ownership: OwnershipT,
}

impl<'s, 't> SoftLoadTE<'s, 't> {

}
impl<'s, 't> SoftLoadTE<'s, 't> where 's: 't, {
    fn new(expr: AddressExpressionTE<'s, 't>, target_ownership: OwnershipT) -> SoftLoadTE<'s, 't> { panic!("Unimplemented: SoftLoadTE::new"); }

}
impl<'s, 't> SoftLoadTE<'s, 't> {
    fn result(&self) -> ReferenceResultT<'s, 't> {
        let addr_result = self.expr.result();
        ReferenceResultT {
            coord: CoordT {
                ownership: self.target_ownership,
                region: addr_result.coord.region,
                kind: addr_result.coord.kind,
            }
        }
    }

}
/// Arena-allocated (see @TFITCX)
#[derive(Debug)]
pub struct DestroyTE<'s, 't>
where 's: 't,
{
    pub expr: ReferenceExpressionTE<'s, 't>,
    pub struct_tt: &'t StructTT<'s, 't>,
    pub destination_reference_variables: &'t [ReferenceLocalVariableT<'s, 't>],
}

impl<'s, 't> DestroyTE<'s, 't> {

    fn result(&self) -> ReferenceResultT<'s, 't> {
        ReferenceResultT { coord: CoordT { ownership: OwnershipT::Share, region: self.expr.result().coord.region, kind: KindT::Void(VoidT {}) } }
    }

}
/// Arena-allocated (see @TFITCX)
#[derive(Debug)]
pub struct DestroyImmRuntimeSizedArrayTE<'s, 't>
where 's: 't,
{
    pub array_expr: ReferenceExpressionTE<'s, 't>,
    pub array_type: &'t RuntimeSizedArrayTT<'s, 't>,
    pub consumer: ReferenceExpressionTE<'s, 't>,
    pub consumer_method: &'t PrototypeT<'s, 't>,
}

impl<'s, 't> DestroyImmRuntimeSizedArrayTE<'s, 't> {

}
impl<'s, 't> DestroyImmRuntimeSizedArrayTE<'s, 't> where 's: 't, {
    fn new(
        array_expr: ReferenceExpressionTE<'s, 't>,
        array_type: &'t RuntimeSizedArrayTT<'s, 't>,
        consumer: ReferenceExpressionTE<'s, 't>,
        consumer_method: &'t PrototypeT<'s, 't>,
    ) -> DestroyImmRuntimeSizedArrayTE<'s, 't> { panic!("Unimplemented: DestroyImmRuntimeSizedArrayTE::new"); }

}
impl<'s, 't> DestroyImmRuntimeSizedArrayTE<'s, 't> {
    fn result(&self) -> ReferenceResultT<'s, 't> { panic!("Unimplemented: result"); }

}
/// Arena-allocated (see @TFITCX)
#[derive(Debug)]
pub struct NewImmRuntimeSizedArrayTE<'s, 't>
where 's: 't,
{
    pub array_type: &'t RuntimeSizedArrayTT<'s, 't>,
    pub region: RegionT,
    pub size_expr: ReferenceExpressionTE<'s, 't>,
    pub generator: ReferenceExpressionTE<'s, 't>,
    pub generator_method: &'t PrototypeT<'s, 't>,
}

impl<'s, 't> NewImmRuntimeSizedArrayTE<'s, 't> {

    fn result(&self) -> ReferenceResultT<'s, 't> {
        let ownership = match self.array_type.mutability() {
            ITemplataT::Mutability(MutabilityTemplataT { mutability: MutabilityT::Mutable }) => OwnershipT::Own,
            ITemplataT::Mutability(MutabilityTemplataT { mutability: MutabilityT::Immutable }) => OwnershipT::Share,
            ITemplataT::Placeholder(_) => panic!("vimpl"),
            _ => panic!("vwat"),
        };
        ReferenceResultT {
            coord: CoordT {
                ownership,
                region: self.region,
                kind: KindT::RuntimeSizedArray(self.array_type),
            },
        }
    }

}
fn reference_expr_result_struct_name_unapply<'s, 't>(expr: &ReferenceExpressionTE<'s, 't>) -> Option<StrI<'s>> { panic!("Unimplemented: unapply"); }

fn reference_expr_result_kind_unapply<'s, 't>(expr: &ReferenceExpressionTE<'s, 't>) -> Option<KindT<'s, 't>> { panic!("Unimplemented: unapply"); }
