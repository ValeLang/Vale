use crate::typing::compiler::Compiler;
use crate::typing::compiler_error_reporter::ICompileErrorT;
use crate::postparsing::ast::LocationInDenizen;
use crate::utils::range::RangeS;
use crate::postparsing::names::*;
use crate::postparsing::expressions::*;
use crate::typing::ast::ast::*;
use crate::typing::ast::expressions::*;
use crate::typing::env::function_environment_t::*;
use crate::typing::names::names::*;
use crate::typing::types::types::*;
use crate::typing::compiler_outputs::*;
use std::collections::HashSet;
use std::iter::once;

// deleted: delegate trait removed per god-struct refactor (Compiler now holds all methods directly)

impl<'s, 'ctx, 't> Compiler<'s, 'ctx, 't>
where 's: 't,
{
    pub fn evaluate_block(
        &self,
        parent_fate: &mut FunctionEnvironmentBuilder<'s, 't>,
        coutputs: &mut CompilerOutputs<'s, 't>,
        life: LocationInFunctionEnvironmentT<'t>,
        parent_ranges: &'t [RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        region: RegionT,
        block_1: &'s BlockSE<'s>,
    ) -> (&'t BlockTE<'s, 't>, HashSet<IVarNameT<'s, 't>>, HashSet<IVarNameT<'s, 't>>, HashSet<CoordT<'s, 't>>) {
        panic!("Unimplemented: Slab 15 — body migration");
    }

    pub fn evaluate_block_statements_block(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        starting_nenv: &'t NodeEnvironmentT<'s, 't>,
        nenv: &mut NodeEnvironmentBox<'s, 't>,
        parent_ranges: &'t [RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        life: LocationInFunctionEnvironmentT<'t>,
        region: RegionT,
        block_se: &'s BlockSE<'s>,
    ) -> Result<(ReferenceExpressionTE<'s, 't>, HashSet<CoordT<'s, 't>>), ICompileErrorT<'s, 't>> {
        let (unnevered_unresultified_undestructed_root_expression, returns_from_exprs) =
            self.evaluate_and_coerce_to_reference_expression(
                coutputs, nenv, life.add(self.typing_interner, 0), parent_ranges,
                call_location, region, block_se.expr)?;

        let unresultified_undestructed_expressions =
            unnevered_unresultified_undestructed_root_expression;

        let drop_range = RangeS { begin: block_se.range.end, end: block_se.range.end };
        let drop_ranges: Vec<RangeS<'s>> =
            once(drop_range).chain(parent_ranges.iter().copied()).collect();
        let new_expr =
            self.drop_since(
                coutputs, starting_nenv, nenv,
                &drop_ranges, call_location, life, region,
                unresultified_undestructed_expressions)?;

        Ok((new_expr, returns_from_exprs))
    }

}
