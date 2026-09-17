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
use crate::typing::types::types::{CoordT, RegionT, OwnershipT, ISubKindTT, ISuperKindTT};
use crate::typing::templata::templata::ITemplataT;
use crate::typing::citizen::impl_compiler::IsParentResult;
use crate::typing::names::names::IFunctionNameT;
use crate::typing::env::environment::IInDenizenEnvironmentT;

// (Scala `class AsSubtypeMacro(keywords, implCompiler, expressionCompiler, destructorCompiler)`
//  absorbed onto `Compiler`; the method body lives at
//  `Compiler::generate_function_body_as_subtype` below.)

impl<'s, 'ctx, 't> Compiler<'s, 'ctx, 't>
where 's: 't,
{
    pub fn generate_function_body_as_subtype(
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

        let local_name: IFunctionNameT<'s, 't> = env.id.local_name.try_into().expect("vassertSome: local_name as IFunctionNameT");
        let target_kind = match local_name.template_args().first().expect("vassertSome: templateArgs.headOption") {
            ITemplataT::Coord(c) => c.coord.kind,
            _ => panic!("vwat"),
        };
        let incoming_ownership = local_name.parameters().first().expect("vassertSome: parameters.headOption").ownership;

        let incoming_coord = param_coords[0].tyype;
        let incoming_kind = incoming_coord.kind;

        // Because we dont yet put borrows in structs
        let result_ownership = incoming_ownership;
        let success_coord = CoordT { ownership: result_ownership, region: RegionT { region: IRegionT::Default }, kind: target_kind };
        let fail_coord = CoordT { ownership: result_ownership, region: RegionT { region: IRegionT::Default }, kind: incoming_kind };
        let (result_coord, ok_constructor, ok_result_impl, err_constructor, err_result_impl) =
            self.get_result(coutputs, env, call_range, call_location, RegionT { region: IRegionT::Default }, success_coord, fail_coord)?;
        if result_coord != maybe_ret_coord.expect("vassertSome: maybeRetCoord") {
            panic!("CompileErrorExceptionT: RangedInternalErrorT: Bad result coord");
        }

        let sub_kind = match ISubKindTT::try_from(target_kind) {
            Ok(x) => x,
            Err(_) => panic!("vwat"),
        };
        let super_kind = match ISuperKindTT::try_from(incoming_kind) {
            Ok(x) => x,
            Err(_) => panic!("vwat"),
        };

        let impl_id = match self.is_parent(
            coutputs,
            IInDenizenEnvironmentT::from(env),
            call_range,
            call_location,
            sub_kind,
            super_kind,
        ) {
            IsParentResult::IsParent(p) => p.impl_id,
            IsParentResult::IsntParent(_) => panic!("vwat"),
        };

        let as_subtype_expr = ReferenceExpressionTE::AsSubtype(self.typing_interner.alloc(AsSubtypeTE {
            source_expr: ReferenceExpressionTE::ArgLookup(self.typing_interner.alloc(ArgLookupTE {
                param_index: 0,
                coord: incoming_coord,
            })),
            target_type: success_coord,
            result_result_type: result_coord,
            ok_constructor: self.typing_interner.alloc(ok_constructor),
            err_constructor: self.typing_interner.alloc(err_constructor),
            impl_name: impl_id,
            ok_impl_name: ok_result_impl,
            err_impl_name: err_result_impl,
        }));

        let body = ReferenceExpressionTE::Block(self.typing_interner.alloc(BlockTE {
            inner: ReferenceExpressionTE::Return(self.typing_interner.alloc(ReturnTE {
                source_expr: as_subtype_expr,
            })),
        }));
        Ok((header, body))
    }

}
