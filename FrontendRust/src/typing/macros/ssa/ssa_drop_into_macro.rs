use crate::interner::StrI;
use crate::utils::range::RangeS;

use crate::higher_typing::ast::*;

use crate::typing::types::types::*;
use crate::typing::ast::ast::*;
use crate::typing::ast::expressions::*;
use crate::typing::env::function_environment_t::*;
use crate::typing::compiler_outputs::*;
use crate::typing::compiler::Compiler;
use crate::postparsing::ast::LocationInDenizen;
use crate::typing::types::types::RegionT;
use crate::typing::compiler_error_reporter::ICompileErrorT;

// (Scala `class SSADropIntoMacro(keywords, arrayCompiler)` absorbed onto `Compiler`;
//  the method body lives at `Compiler::generate_function_body_ssa_drop_into` below.)

impl<'s, 'ctx, 't> Compiler<'s, 'ctx, 't>
where 's: 't,
{
    pub fn generate_function_body_ssa_drop_into(
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
        coutputs.declare_function_return_type(
            self.typing_interner.alloc(header.to_signature()),
            header.return_type,
        );
        let arr_arg = ReferenceExpressionTE::ArgLookup(self.typing_interner.alloc(ArgLookupTE { param_index: 0, coord: param_coords[0].tyype }));
        let callable_arg = ReferenceExpressionTE::ArgLookup(self.typing_interner.alloc(ArgLookupTE { param_index: 1, coord: param_coords[1].tyype }));
        let destroy_te = self.evaluate_destroy_static_sized_array_into_callable(
            coutputs,
            env,
            call_range,
            call_location,
            arr_arg,
            callable_arg,
            RegionT { region: IRegionT::Default },
        )?;
        let body = ReferenceExpressionTE::Block(self.typing_interner.alloc(BlockTE {
            inner: ReferenceExpressionTE::Return(self.typing_interner.alloc(ReturnTE {
                source_expr: ReferenceExpressionTE::DestroyStaticSizedArrayIntoFunction(self.typing_interner.alloc(destroy_te)),
            })),
        }));
        Ok((header, body))
    }

}
