use crate::postparsing::ast::LocationInDenizen;
use crate::typing::ast::expressions::{DiscardTE, FunctionCallTE, ReferenceExpressionTE};
use crate::typing::compiler::Compiler;
use crate::typing::compiler_outputs::CompilerOutputs;
use crate::typing::env::environment::IInDenizenEnvironmentT;
use crate::typing::function::function_compiler::StampFunctionSuccess;
use crate::typing::types::types::{CoordT, IRegionT, KindT, OwnershipT, RegionT};
use crate::utils::range::RangeS;
use crate::typing::compiler_error_reporter::ICompileErrorT;
use crate::postparsing::names::IImpreciseNameValS;
use crate::postparsing::names::CodeNameS;

impl<'s, 'ctx, 't> Compiler<'s, 'ctx, 't>
where 's: 't,
{
    pub fn get_drop_function(
        &self,
        env: IInDenizenEnvironmentT<'s, 't>,
        coutputs: &mut CompilerOutputs<'s, 't>,
        call_range: &[RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        context_region: RegionT,
        type_2: CoordT<'s, 't>,
    ) -> Result<StampFunctionSuccess<'s, 't>, ICompileErrorT<'s, 't>> {
        let name = self.scout_arena.intern_imprecise_name(
            IImpreciseNameValS::CodeName(
                CodeNameS { name: self.keywords.drop }));
        let args = &[type_2];
        match self.find_function(env, coutputs, call_range, call_location, name, &[], &[], &[], context_region, args, &[], true)?
        {
            Err(e) => Err(ICompileErrorT::CouldntFindFunctionToCallT {
                range: self.typing_interner.alloc_slice_copy(call_range),
                fff: e,
            }),
            Ok(x) => Ok(x),
        }
    }

    pub fn drop(
        &self,
        env: IInDenizenEnvironmentT<'s, 't>,
        coutputs: &mut CompilerOutputs<'s, 't>,
        call_range: &[RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        context_region: RegionT,
        undestructed_expr_2: ReferenceExpressionTE<'s, 't>,
    ) -> Result<ReferenceExpressionTE<'s, 't>, ICompileErrorT<'s, 't>> {
        let result_coord = undestructed_expr_2.result().coord;
        let result_expr_2 = match (result_coord.ownership, result_coord.kind) {
            (OwnershipT::Share, KindT::Never(_)) => undestructed_expr_2,
            (OwnershipT::Share, _) => {
                ReferenceExpressionTE::Discard(self.typing_interner.alloc(DiscardTE { expr: undestructed_expr_2 }))
            }
            (OwnershipT::Own, _) => {
                let StampFunctionSuccess { prototype: destructor_prototype, .. } =
                    self.get_drop_function(env, coutputs, call_range, call_location, RegionT { region: IRegionT::Default }, result_coord)?;
                assert!(coutputs.get_instantiation_bounds(self.typing_interner, destructor_prototype.id).is_some());
                let result_tt = destructor_prototype.return_type;
                ReferenceExpressionTE::FunctionCall(self.typing_interner.alloc(FunctionCallTE {
                    callable: destructor_prototype,
                    args: self.typing_interner.alloc_slice_from_vec(vec![undestructed_expr_2]),
                    return_type: result_tt,
                }))
            }
            (OwnershipT::Borrow, _) => {
                ReferenceExpressionTE::Discard(self.typing_interner.alloc(DiscardTE { expr: undestructed_expr_2 }))
            }
            (OwnershipT::Weak, _) => {
                ReferenceExpressionTE::Discard(self.typing_interner.alloc(DiscardTE { expr: undestructed_expr_2 }))
            }
        };
        match result_expr_2.result().coord.kind {
            KindT::Void(_) | KindT::Never(_) => {}
            _ => {
                panic!("Unexpected return type for drop autocall.\nReturn: {:?}\nParam: {:?}", result_expr_2.result().coord.kind, undestructed_expr_2.result().coord);
            }
        }
        Ok(result_expr_2)
    }

}