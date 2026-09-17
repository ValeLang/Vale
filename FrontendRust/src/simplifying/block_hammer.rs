//
// Scala's `BlockHammer` is collapsed: per typing-pass `Compiler` precedent
// (no sub-`StructCompiler`/`ExpressionCompiler`/etc.), all sub-hammer methods
// live as `impl Hammer { ... }` blocks colocated in per-area files.
// `BlockHammer` itself is NOT a Rust struct.

use crate::final_ast::instructions::{BlockH, ImmutabilifyH, MutabilifyH};
use crate::instantiating::ast::ast::FunctionHeaderI;
use crate::instantiating::ast::expressions::{BlockIE, ImmutabilifyIE, MutabilifyIE};
use crate::instantiating::ast::hinputs::HinputsI;
use crate::instantiating::ast::types::cI;
use crate::simplifying::hamuts::Hamuts;
use crate::simplifying::hammer::{Hammer, Locals};
use crate::final_ast::types::KindHT;
use crate::instantiating::ast::expressions::ExpressionIE;
use std::collections::HashSet;

impl<'s, 'i, 'h, 'ctx> Hammer<'s, 'i, 'h, 'ctx>
where 's: 'h, 's: 'i, 'i: 'h,
{
    pub fn translate_block(
        &self,
        hinputs: &HinputsI<'s, 'i>,
        hamuts: &mut Hamuts<'s, 'i, 'h>,
        current_function_header: &FunctionHeaderI<'s, 'i>,
        parent_locals: &mut Locals<'s, 'i, 'h>,
        block2: &BlockIE<'s, 'i, cI>,
    ) -> &'h BlockH<'s, 'h>
    {
        let mut block_locals = parent_locals.snapshot();
        let expr_h = self.translate_expressions_and_deferreds(
            hinputs, hamuts, current_function_header, &mut block_locals,
            &[ExpressionIE::Reference(block2.inner)]);
        let parent_local_ids: HashSet<_> = parent_locals.locals.keys().copied().collect();
        let local_ids_in_this_block: HashSet<_> = block_locals.locals.keys().copied().filter(|k| !parent_local_ids.contains(k)).collect();
        let unstackified_local_ids_in_this_block: HashSet<_> = block_locals.unstackified_vars.iter().copied().filter(|k| local_ids_in_this_block.contains(k)).collect();
        if local_ids_in_this_block != unstackified_local_ids_in_this_block {
            match expr_h.result_type().kind {
                KindHT::NeverHT(_) => {}
                _ => panic!("Ununstackified local: {:?}", local_ids_in_this_block.difference(&unstackified_local_ids_in_this_block).collect::<Vec<_>>()),
            }
        }
        let parent_unstackified: HashSet<_> = parent_locals.unstackified_vars.iter().copied().collect();
        let unstackified_locals_from_parent: Vec<_> = parent_locals.locals.keys().copied()
            .filter(|k| !parent_unstackified.contains(k))
            .filter(|k| block_locals.unstackified_vars.contains(k))
            .collect();
        for var in unstackified_locals_from_parent {
            parent_locals.mark_unstackified(var);
        }
        parent_locals.set_next_local_id_number(block_locals.next_local_id_number);
        self.interner.alloc(BlockH { inner: expr_h })
    }

    pub fn translate_mutabilify(
        &self,
        hinputs: &HinputsI<'s, 'i>,
        hamuts: &mut Hamuts<'s, 'i, 'h>,
        current_function_header: &FunctionHeaderI<'s, 'i>,
        locals: &mut Locals<'s, 'i, 'h>,
        node: &MutabilifyIE<'s, 'i, cI>,
    ) -> &'h MutabilifyH<'s, 'h>
    {
        panic!("Unimplemented: translate_mutabilify");
    }

    pub fn translate_immutabilify(
        &self,
        hinputs: &HinputsI<'s, 'i>,
        hamuts: &mut Hamuts<'s, 'i, 'h>,
        current_function_header: &FunctionHeaderI<'s, 'i>,
        locals: &mut Locals<'s, 'i, 'h>,
        node: &ImmutabilifyIE<'s, 'i, cI>,
    ) -> &'h ImmutabilifyH<'s, 'h>
    {
        panic!("Unimplemented: translate_immutabilify");
    }
}

