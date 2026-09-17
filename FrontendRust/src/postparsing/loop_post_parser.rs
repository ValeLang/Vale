use crate::parsing::ast::{
  AugmentPE, BlockPE, ConsecutorPE, DestinationLocalP, FunctionCallPE, IExpressionPE,
  IImpreciseNameP, INameDeclarationP, LetPE, LoadAsP, LookupPE, NameP, OwnershipP, PatternPP,
};
use crate::postparsing::ast::LocationInDenizenBuilder;
use crate::postparsing::expressions::{
  BlockSE, BreakSE, IExpressionSE, MapSE, VoidSE, WhileSE,
};
use crate::postparsing::post_parser::{ICompileErrorS, PostParser, StackFrame};
use crate::postparsing::variable_uses::VariableUses;
use crate::lexing::ast::RangeL;

fn scout_loop<'s, F>(
  _stack_frame0: StackFrame<'s>,
  _lidb: &mut LocationInDenizenBuilder,
  _range_p: RangeL,
  _pure: bool,
  _make_contents: F,
) -> (
  BlockSE<'s>,
  VariableUses<'s>,
  VariableUses<'s>,
)
where
  F: FnOnce(
    StackFrame<'s>,
    &mut LocationInDenizenBuilder,
    bool,
  ) -> (
    StackFrame<'s>,
    BlockSE<'s>,
    VariableUses<'s>,
    VariableUses<'s>,
  ),
{
  panic!("Unimplemented scout_loop");
}

pub(crate) fn scout_each<'s, 'p, 'ctx>(
  post_parser: &PostParser<'s, 'p, 'ctx>,
  stack_frame0: StackFrame<'s>,
  lidb: &mut LocationInDenizenBuilder,
  range: RangeL,
  _pure: bool,
  entry_pattern_pp: &'p PatternPP<'p>,
  in_keyword_range: RangeL,
  iterable_expr: &'p IExpressionPE<'p>,
  body: &'p BlockPE<'p>,
) -> Result<(&'s BlockSE<'s>, VariableUses<'s>, VariableUses<'s>), ICompileErrorS<'s>>
{
  let each_range_s = PostParser::eval_range(stack_frame0.file, range);
  let parent_env0 = stack_frame0.parent_env.clone();
  let context_region0 = stack_frame0.context_region.clone();
  let mut each_lidb = lidb.child();
  let (each_block_s, self_uses, child_uses) = post_parser.new_block(
    parent_env0,
    Some(stack_frame0),
    &mut each_lidb,
    each_range_s.clone(),
    context_region0,
    PostParser::<'s, 'p, '_>::no_declarations(),
    |stack_frame1, each_contents_lidb| {
      // Per @PPSPASTNZ, synthesize loop desugaring as parser AST, allocated in parse_arena.
      let pa = post_parser.parse_arena;
      let kp = post_parser.keywords_p;
      let (stack_frame2, let_iterable_se, let_iterable_self_uses, let_iterable_child_uses): (StackFrame<'s>, &'s IExpressionSE<'s>, VariableUses<'s>, VariableUses<'s>) = {
        let let_iterable_expr_p: &'p IExpressionPE<'p> = &*pa.alloc(IExpressionPE::Let(LetPE {
          range: in_keyword_range,
          pattern: &*pa.alloc(PatternPP {
            range: in_keyword_range,
            destination: Some(DestinationLocalP {
              decl: INameDeclarationP::IterableNameDeclaration(in_keyword_range),
              mutate: None,
            }),
            templex: None,
            destructure: None,
          }),
          source: iterable_expr,
        }));
        post_parser.scout_expression_and_coerce(
          stack_frame1,
          &mut each_contents_lidb.child(),
          let_iterable_expr_p,
          LoadAsP::Use,
        )?
      };
      let (stack_frame3, let_iterator_se, let_iterator_self_uses, let_iterator_child_uses): (StackFrame<'s>, &'s IExpressionSE<'s>, VariableUses<'s>, VariableUses<'s>) = {
        let begin_lookup_expr_p: &'p IExpressionPE<'p> = &*pa.alloc(IExpressionPE::Lookup(pa.alloc(LookupPE {
          name: IImpreciseNameP::LookupName(NameP(in_keyword_range, kp.begin)),
          template_args: None,
        })));
        let iterable_lookup_expr_p: &'p IExpressionPE<'p> = &*pa.alloc(IExpressionPE::Lookup(pa.alloc(LookupPE {
          name: IImpreciseNameP::IterableName(in_keyword_range),
          template_args: None,
        })));
        let iterable_borrow_expr_p = IExpressionPE::Augment(AugmentPE {
          range: in_keyword_range,
          target_ownership: OwnershipP::Borrow,
          inner: iterable_lookup_expr_p,
        });
        let begin_args: &'p [&'p IExpressionPE<'p>] = pa.alloc_slice_from_vec(vec![&*pa.alloc(iterable_borrow_expr_p)]);
        let begin_call_expr_p: &'p IExpressionPE<'p> = &*pa.alloc(IExpressionPE::FunctionCall(FunctionCallPE {
          range: in_keyword_range,
          operator_range: in_keyword_range,
          callable_expr: begin_lookup_expr_p,
          arg_exprs: begin_args,
        }));
        let let_iterator_expr_p: &'p IExpressionPE<'p> = &*pa.alloc(IExpressionPE::Let(LetPE {
          range: in_keyword_range,
          pattern: &*pa.alloc(PatternPP {
            range: in_keyword_range,
            destination: Some(DestinationLocalP {
              decl: INameDeclarationP::IteratorNameDeclaration(in_keyword_range),
              mutate: None,
            }),
            templex: None,
            destructure: None,
          }),
          source: begin_call_expr_p,
        }));
        post_parser.scout_expression_and_coerce(
          stack_frame2,
          &mut each_contents_lidb.child(),
          let_iterator_expr_p,
          LoadAsP::Use,
        )?
      };
      let parent_env3 = stack_frame3.parent_env.clone();
      let context_region3 = stack_frame3.context_region.clone();
      let (loop_se, loop_body_self_uses, loop_body_child_uses) = post_parser.new_block(
        parent_env3,
        Some(stack_frame3.clone()),
        &mut each_contents_lidb.child(),
        each_range_s.clone(),
        context_region3,
        PostParser::<'s, 'p, '_>::no_declarations(),
        |stack_frame4, loop_lidb| {
          let parent_env4 = stack_frame4.parent_env.clone();
          let context_region4 = stack_frame4.context_region.clone();
          let (loop_body_se, loop_body_self_uses, loop_body_child_uses) = post_parser.new_block(
            parent_env4,
            Some(stack_frame4.clone()),
            &mut loop_lidb.child(),
            each_range_s.clone(),
            context_region4,
            PostParser::<'s, 'p, '_>::no_declarations(),
            |stack_frame5, loop_body_lidb| {
              scout_each_body(
                post_parser,
                stack_frame5,
                loop_body_lidb,
                range,
                in_keyword_range,
                entry_pattern_pp,
                body,
              )
            },
          )?;
          let loop_se = if body.inner.produces_result() {
            &*post_parser.scout_arena.alloc(IExpressionSE::Map(MapSE {
              range: each_range_s.clone(),
              body: loop_body_se,
            }))
          } else {
            &*post_parser.scout_arena.alloc(IExpressionSE::While(WhileSE {
              range: each_range_s.clone(),
              body: loop_body_se,
            }))
          };
          Ok((stack_frame4.clone(), loop_se, loop_body_self_uses, loop_body_child_uses))
        },
      )?;
      let loop_se = &*post_parser.scout_arena.alloc(IExpressionSE::Block(loop_se));
      let contents_se = post_parser.consecutive(vec![let_iterable_se, let_iterator_se, loop_se]);
      let self_uses = let_iterable_self_uses
        .then_merge(&let_iterator_self_uses)
        .then_merge(&loop_body_self_uses);
      let child_uses = let_iterable_child_uses
        .then_merge(&let_iterator_child_uses)
        .then_merge(&loop_body_child_uses);
      Ok((stack_frame3, contents_se, self_uses, child_uses))
    },
  )?;
  Ok((each_block_s, self_uses, child_uses))
}

fn scout_each_body<'s, 'p, 'ctx>(
  post_parser: &PostParser<'s, 'p, 'ctx>,
  stack_frame0: StackFrame<'s>,
  lidb: &mut LocationInDenizenBuilder,
  range: RangeL,
  in_keyword_range: RangeL,
  entry_pattern_pp: &'p PatternPP<'p>,
  body_pe: &'p BlockPE<'p>,
) -> Result<
  (
    StackFrame<'s>,
    &'s IExpressionSE<'s>,
    VariableUses<'s>,
    VariableUses<'s>,
  ),
  ICompileErrorS<'s>,
>
{
  let pa = post_parser.parse_arena;
  let kp = post_parser.keywords_p;
  let each_range_s = PostParser::eval_range(stack_frame0.file, range);
  let (stack_frame4, if_se, if_self_uses, if_child_uses) = PostParser::new_if(
    stack_frame0,
    lidb,
    range,
    |stack_frame1, condition_lidb| {
      // Per @PPSPASTNZ, synthesize loop iteration as parser AST, allocated in parse_arena.
      let next_lookup_expr_p: &'p IExpressionPE<'p> = &*pa.alloc(IExpressionPE::Lookup(pa.alloc(LookupPE {
        name: IImpreciseNameP::LookupName(NameP(in_keyword_range, kp.next)),
        template_args: None,
      })));
      let iterator_lookup_expr_p: &'p IExpressionPE<'p> = &*pa.alloc(IExpressionPE::Lookup(pa.alloc(LookupPE {
        name: IImpreciseNameP::IteratorName(in_keyword_range),
        template_args: None,
      })));
      let iterator_borrow_expr_p = IExpressionPE::Augment(AugmentPE {
        range: in_keyword_range,
        target_ownership: OwnershipP::Borrow,
        inner: iterator_lookup_expr_p,
      });
      let next_args: &'p [&'p IExpressionPE<'p>] = pa.alloc_slice_from_vec(vec![&*pa.alloc(iterator_borrow_expr_p)]);
      let next_call_expr_p: &'p IExpressionPE<'p> = &*pa.alloc(IExpressionPE::FunctionCall(FunctionCallPE {
        range: in_keyword_range,
        operator_range: in_keyword_range,
        callable_expr: next_lookup_expr_p,
        arg_exprs: next_args,
      }));
      let let_iteration_option_expr_p = IExpressionPE::Let(LetPE {
        range: entry_pattern_pp.range,
        pattern: &*pa.alloc(PatternPP {
          range: in_keyword_range,
          destination: Some(DestinationLocalP {
            decl: INameDeclarationP::IterationOptionNameDeclaration(in_keyword_range),
            mutate: None,
          }),
          templex: None,
          destructure: None,
        }),
        source: next_call_expr_p,
      });
      let is_empty_lookup_expr_p: &'p IExpressionPE<'p> = &*pa.alloc(IExpressionPE::Lookup(pa.alloc(LookupPE {
        name: IImpreciseNameP::LookupName(NameP(in_keyword_range, kp.is_empty)),
        template_args: None,
      })));
      let iteration_option_lookup_expr_p: &'p IExpressionPE<'p> = &*pa.alloc(IExpressionPE::Lookup(pa.alloc(LookupPE {
        name: IImpreciseNameP::IterationOptionName(in_keyword_range),
        template_args: None,
      })));
      let iteration_option_borrow_expr_p = IExpressionPE::Augment(AugmentPE {
        range: in_keyword_range,
        target_ownership: OwnershipP::Borrow,
        inner: iteration_option_lookup_expr_p,
      });
      let is_empty_args: &'p [&'p IExpressionPE<'p>] = pa.alloc_slice_from_vec(vec![&*pa.alloc(iteration_option_borrow_expr_p)]);
      let is_empty_call_expr_p = IExpressionPE::FunctionCall(FunctionCallPE {
        range: in_keyword_range,
        operator_range: in_keyword_range,
        callable_expr: is_empty_lookup_expr_p,
        arg_exprs: is_empty_args,
      });
      let condition_inners: &'p [&'p IExpressionPE<'p>] = pa.alloc_slice_from_vec(vec![&*pa.alloc(let_iteration_option_expr_p), &*pa.alloc(is_empty_call_expr_p)]);
      let condition_expr_p: &'p IExpressionPE<'p> = &*pa.alloc(IExpressionPE::Consecutor(ConsecutorPE {
        inners: condition_inners,
      }));
      let (stack_frame3, cond_se, cond_self_uses, cond_child_uses) = post_parser.scout_expression_and_coerce(
        stack_frame1,
        condition_lidb,
        condition_expr_p,
        LoadAsP::Use,
      )?;
      Ok((stack_frame3, cond_se, cond_self_uses, cond_child_uses))
    },
    |stack_frame1, then_lidb| {
      let parent_env1 = stack_frame1.parent_env.clone();
      let context_region1 = stack_frame1.context_region.clone();
      let (then_s, then_uses, then_child_uses) = post_parser.new_block(
        parent_env1,
        Some(stack_frame1.clone()),
        then_lidb,
        each_range_s.clone(),
        context_region1,
        PostParser::<'s, 'p, '_>::no_declarations(),
        |stack_frame2, then_inner_lidb| {
          // Per @PPSPASTNZ, allocate synthetic parser node in parse_arena
          let iteration_option_lookup_expr_p: &'p IExpressionPE<'p> = &*pa.alloc(IExpressionPE::Lookup(pa.alloc(LookupPE {
            name: IImpreciseNameP::IterationOptionName(in_keyword_range),
            template_args: None,
          })));
          let (stack_frame3, lookup_se, lookup_self_uses, lookup_child_uses) = post_parser
            .scout_expression_and_coerce(
              stack_frame2,
              then_inner_lidb,
              iteration_option_lookup_expr_p,
              LoadAsP::Use,
            )?;
          let break_s = &*post_parser.scout_arena.alloc(IExpressionSE::Break(BreakSE {
            range: each_range_s.clone(),
          }));
          let lookup_and_break_se = post_parser.consecutive(vec![lookup_se, break_s]);
          Ok((stack_frame3, lookup_and_break_se, lookup_self_uses, lookup_child_uses))
        },
      )?;
      Ok((stack_frame1, then_s, then_uses, then_child_uses))
    },
    |stack_frame1, _else_lidb| {
      // Else does nothing
      let else_s = &*post_parser.scout_arena.alloc(BlockSE {
        range: each_range_s.clone(),
        locals: &[],
        expr: &*post_parser.scout_arena.alloc(IExpressionSE::Void(VoidSE {
          range: each_range_s.clone(),
        })),
      });
      Ok((
        stack_frame1,
        else_s,
        PostParser::<'s, 'p, '_>::no_variable_uses(),
        PostParser::<'s, 'p, '_>::no_variable_uses(),
      ))
    },
  )?;
  let if_se = &*post_parser.scout_arena.alloc(IExpressionSE::If(if_se));

  // Per @PPSPASTNZ, allocate synthetic parser nodes in parse_arena
  let (stack_frame5, consume_some_se, consume_some_self_uses, consume_some_child_uses): (StackFrame<'s>, &'s IExpressionSE<'s>, VariableUses<'s>, VariableUses<'s>) = {
    let get_lookup_expr_p: &'p IExpressionPE<'p> = &*pa.alloc(IExpressionPE::Lookup(pa.alloc(LookupPE {
      name: IImpreciseNameP::LookupName(NameP(in_keyword_range, kp.get)),
      template_args: None,
    })));
    let iteration_option_lookup_expr_p = IExpressionPE::Lookup(pa.alloc(LookupPE {
      name: IImpreciseNameP::IterationOptionName(in_keyword_range),
      template_args: None,
    }));
    let get_args: &'p [&'p IExpressionPE<'p>] = pa.alloc_slice_from_vec(vec![&*pa.alloc(iteration_option_lookup_expr_p)]);
    let get_call_expr_p: &'p IExpressionPE<'p> = &*pa.alloc(IExpressionPE::FunctionCall(FunctionCallPE {
      range: in_keyword_range,
      operator_range: in_keyword_range,
      callable_expr: get_lookup_expr_p,
      arg_exprs: get_args,
    }));
    let consume_some_expr_p: &'p IExpressionPE<'p> = &*pa.alloc(IExpressionPE::Let(LetPE {
      range: in_keyword_range,
      pattern: entry_pattern_pp,
      source: get_call_expr_p,
    }));
    let mut consume_some_lidb = lidb.child();
    post_parser.scout_expression_and_coerce(
      stack_frame4,
      &mut consume_some_lidb,
      consume_some_expr_p,
      LoadAsP::Use,
    )?
  };

  let (user_body_se, user_body_self_uses, user_body_child_uses) = post_parser.scout_block(
    stack_frame5.clone(),
    &mut lidb.child(),
    PostParser::<'s, 'p, '_>::no_declarations(),
    body_pe,
  )?;

  let self_uses = if_self_uses
    .then_merge(&consume_some_self_uses)
    .then_merge(&user_body_self_uses);
  let child_uses = if_child_uses
    .then_merge(&consume_some_child_uses)
    .then_merge(&user_body_child_uses);
  let loop_body_se = post_parser.consecutive(vec![if_se, consume_some_se, user_body_se]);
  Ok((stack_frame5, loop_body_se, self_uses, child_uses))
}

pub(crate) fn scout_while<'s, 'p, 'ctx>(
  post_parser: &PostParser<'s, 'p, 'ctx>,
  stack_frame0: StackFrame<'s>,
  lidb: &mut LocationInDenizenBuilder,
  range: RangeL,
  condition_pe: &'p IExpressionPE<'p>,
  body: &'p BlockPE<'p>,
) -> Result<(&'s BlockSE<'s>, VariableUses<'s>, VariableUses<'s>), ICompileErrorS<'s>>
{
  let while_range_s = PostParser::eval_range(stack_frame0.file, range);
  let parent_env0 = stack_frame0.parent_env.clone();
  let context_region0 = stack_frame0.context_region.clone();
  let (loop_s, loop_body_self_uses, loop_body_child_uses) = post_parser.new_block(
    parent_env0,
    Some(stack_frame0),
    &mut lidb.child(),
    while_range_s.clone(),
    context_region0,
    PostParser::<'s, 'p, '_>::no_declarations(),
    |stack_frame1, inner_lidb| {
      let parent_env1 = stack_frame1.parent_env.clone();
      let context_region1 = stack_frame1.context_region.clone();
      let (inner_loop_s, loop_body_self_uses, loop_body_child_uses) = post_parser.new_block(
        parent_env1,
        Some(stack_frame1.clone()),
        &mut inner_lidb.child(),
        while_range_s.clone(),
        context_region1,
        PostParser::<'s, 'p, '_>::no_declarations(),
        |stack_frame4, innermost_lidb| {
          let parent_env4 = stack_frame4.parent_env.clone();
          let context_region4 = stack_frame4.context_region.clone();
          let (loop_body_se, loop_body_self_uses, loop_body_child_uses) = post_parser.new_block(
            parent_env4,
            Some(stack_frame4.clone()),
            &mut innermost_lidb.child(),
            while_range_s.clone(),
            context_region4,
            PostParser::<'s, 'p, '_>::no_declarations(),
            |stack_frame5, body_lidb| {
              scout_while_body(
                post_parser,
                stack_frame5,
                body_lidb,
                range,
                condition_pe,
                body,
              )
            },
          )?;
          let while_se = &*post_parser.scout_arena.alloc(IExpressionSE::While(WhileSE {
            range: while_range_s.clone(),
            body: loop_body_se,
          }));
          Ok((stack_frame4, while_se, loop_body_self_uses, loop_body_child_uses))
        },
      )?;
      let inner_loop_expr =
        &*post_parser.scout_arena.alloc(IExpressionSE::Block(inner_loop_s));
      Ok((
        stack_frame1,
        inner_loop_expr,
        loop_body_self_uses,
        loop_body_child_uses,
      ))
    },
  )?;
  Ok((loop_s, loop_body_self_uses, loop_body_child_uses))
}

fn scout_while_body<'s, 'p, 'ctx>(
  post_parser: &PostParser<'s, 'p, 'ctx>,
  stack_frame0: StackFrame<'s>,
  lidb: &mut LocationInDenizenBuilder,
  range: RangeL,
  condition_pe: &'p IExpressionPE<'p>,
  body_pe: &'p BlockPE<'p>,
) -> Result<
  (
    StackFrame<'s>,
    &'s IExpressionSE<'s>,
    VariableUses<'s>,
    VariableUses<'s>,
  ),
  ICompileErrorS<'s>,
>
{
  let while_range_s = PostParser::eval_range(stack_frame0.file, range);
  let (stack_frame4, if_se, if_self_uses, if_child_uses) = PostParser::new_if(
    stack_frame0,
    lidb,
    range,
    |stack_frame2, condition_lidb| {
      let (stack_frame3, cond_se, cond_self_uses, cond_child_uses) =
        post_parser.scout_expression_and_coerce(
          stack_frame2,
          condition_lidb,
          condition_pe,
          LoadAsP::Use,
        )?;
      Ok((stack_frame3, cond_se, cond_self_uses, cond_child_uses))
    },
    |stack_frame2, _then_lidb| {
      // Then does nothing, just continue on
      let void_s = &*post_parser.scout_arena.alloc(BlockSE {
        range: while_range_s.clone(),
        locals: &[],
        expr: &*post_parser.scout_arena.alloc(IExpressionSE::Void(VoidSE {
          range: while_range_s.clone(),
        })),
      });
      Ok((
        stack_frame2,
        void_s,
        PostParser::<'s, 'p, '_>::no_variable_uses(),
        PostParser::<'s, 'p, '_>::no_variable_uses(),
      ))
    },
    |stack_frame3, else_lidb| {
      let parent_env3 = stack_frame3.parent_env.clone();
      let context_region3 = stack_frame3.context_region.clone();
      let (then_s, then_uses, then_child_uses) = post_parser.new_block(
        parent_env3,
        Some(stack_frame3.clone()),
        else_lidb,
        while_range_s.clone(),
        context_region3,
        PostParser::<'s, 'p, '_>::no_declarations(),
        |stack_frame4, _break_lidb| {
          let break_s = &*post_parser.scout_arena.alloc(IExpressionSE::Break(BreakSE {
            range: while_range_s.clone(),
          }));
          Ok((stack_frame4, break_s, PostParser::<'s, 'p, '_>::no_variable_uses(), PostParser::<'s, 'p, '_>::no_variable_uses()))
        },
      )?;
      Ok((stack_frame3, then_s, then_uses, then_child_uses))
    },
  )?;
  let if_se = &*post_parser.scout_arena.alloc(IExpressionSE::If(if_se));

  let (user_body_se, user_body_self_uses, user_body_child_uses) = post_parser.scout_block(
    stack_frame4.clone(),
    &mut lidb.child(),
    PostParser::<'s, 'p, '_>::no_declarations(),
    body_pe,
  )?;

  let self_uses = if_self_uses.then_merge(&user_body_self_uses);
  let child_uses = if_child_uses.then_merge(&user_body_child_uses);
  let loop_body_se = post_parser.consecutive(vec![if_se, user_body_se]);
  Ok((stack_frame4, loop_body_se, self_uses, child_uses))
}
