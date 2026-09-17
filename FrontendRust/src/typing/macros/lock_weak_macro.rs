use crate::interner::StrI;
use crate::utils::range::RangeS;

use crate::higher_typing::ast::*;

use crate::typing::types::types::*;
use crate::typing::ast::ast::*;
use crate::typing::ast::expressions::*;
use crate::typing::env::function_environment_t::*;
use crate::typing::compiler_outputs::*;
use crate::typing::compiler::Compiler;
use crate::typing::compiler_error_reporter::ICompileErrorT;
use crate::postparsing::ast::LocationInDenizen;
use crate::typing::types::types::RegionT;

// (Scala `class LockWeakMacro(keywords, expressionCompiler)` absorbed onto `Compiler`;
//  the method body lives at `Compiler::generate_function_body_lock_weak` below.)

impl<'s, 'ctx, 't> Compiler<'s, 'ctx, 't>
where 's: 't,
{
    pub fn generate_function_body_lock_weak(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        env: &'t FunctionEnvironmentT<'s, 't>,
        generator_id: StrI<'s>,
        life: LocationInFunctionEnvironmentT<'t>,
        call_range: &[RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        origin_function: Option<&FunctionA<'s>>,
        param_coords: &[ParameterT<'s, 't>],
        maybe_ret_coord: Option<CoordT<'s, 't>>,
    ) -> Result<(FunctionHeaderT<'s, 't>, ReferenceExpressionTE<'s, 't>), ICompileErrorT<'s, 't>> {
        let header = FunctionHeaderT {
            id: env.id,
            attributes: self.typing_interner.alloc_slice_from_vec(vec![]),
            params: self.typing_interner.alloc_slice_from_vec(param_coords.to_vec()),
            return_type: maybe_ret_coord.expect("vassertSome: maybeRetCoord"),
            maybe_origin_function_templata: Some(env.templata()),
        };
        let borrow_coord = CoordT { ownership: OwnershipT::Borrow, ..param_coords[0].tyype };
        let (opt_coord, some_constructor, none_constructor, some_impl_id, none_impl_id) =
            self.get_option(coutputs, env, call_range, call_location, RegionT { region: IRegionT::Default }, borrow_coord)?;
        let lock_expr = ReferenceExpressionTE::LockWeak(self.typing_interner.alloc(LockWeakTE {
            inner_expr: ReferenceExpressionTE::ArgLookup(self.typing_interner.alloc(ArgLookupTE {
                param_index: 0,
                coord: param_coords[0].tyype,
            })),
            result_opt_borrow_type: opt_coord,
            some_constructor: self.typing_interner.alloc(some_constructor),
            none_constructor: self.typing_interner.alloc(none_constructor),
            some_impl_name: some_impl_id,
            none_impl_name: none_impl_id,
        }));
        let body = ReferenceExpressionTE::Block(self.typing_interner.alloc(BlockTE {
            inner: ReferenceExpressionTE::Return(self.typing_interner.alloc(ReturnTE {
                source_expr: lock_expr,
            })),
        }));
        Ok((header, body))
    }

}
