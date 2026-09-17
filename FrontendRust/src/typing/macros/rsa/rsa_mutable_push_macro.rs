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

// (Scala `class RSAMutablePushMacro(interner, keywords)` absorbed onto `Compiler`;
//  the method body lives at `Compiler::generate_function_body_rsa_mutable_push` below.)

impl<'s, 'ctx, 't> Compiler<'s, 'ctx, 't>
where 's: 't,
{
    pub fn generate_function_body_rsa_mutable_push(
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
    ) -> (FunctionHeaderT<'s, 't>, ReferenceExpressionTE<'s, 't>) {
        let header = FunctionHeaderT {
            id: env.id,
            attributes: self.typing_interner.alloc_slice_from_vec(vec![]),
            params: self.typing_interner.alloc_slice_from_vec(param_coords.to_vec()),
            return_type: maybe_ret_coord.expect("vassertSome: maybeRetCoord"),
            maybe_origin_function_templata: Some(env.templata()),
        };
        let body = ReferenceExpressionTE::Block(self.typing_interner.alloc(BlockTE {
            inner: ReferenceExpressionTE::Return(self.typing_interner.alloc(ReturnTE {
                source_expr: ReferenceExpressionTE::PushRuntimeSizedArray(self.typing_interner.alloc(PushRuntimeSizedArrayTE {
                    array_expr: ReferenceExpressionTE::ArgLookup(self.typing_interner.alloc(ArgLookupTE {
                        param_index: 0,
                        coord: param_coords[0].tyype,
                    })),
                    new_element_expr: ReferenceExpressionTE::ArgLookup(self.typing_interner.alloc(ArgLookupTE {
                        param_index: 1,
                        coord: param_coords[1].tyype,
                    })),
                })),
            })),
        }));
        (header, body)
    }

}
