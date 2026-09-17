use crate::lexing::ast::RangeL;
use crate::parsing::ast::{
  BlockPE, DotPE, FunctionCallPE, IArraySizeP, IExpressionPE, IImpreciseNameP, ITemplexPT, LoadAsP,
  LookupPE, MutabilityP, NameP, OwnershipP, StaticSizedArraySizeP, VariabilityP,
};
use crate::interner::StrI;
use crate::postparsing::ast::LocationInDenizenBuilder;
use crate::postparsing::ast::IExpressionSE as IExpressionSETrait;
use crate::postparsing::expressions::{
  BlockSE, ConstantBoolSE, ConstantIntSE, ConstantStrSE, DestructSE, DotSE, ExprMutateSE, FunctionCallSE, FunctionSE,
  IExpressionSE, IfSE, IndexSE, LetSE, LoadPartSE, LocalLoadSE, LocalMutateSE, LocalS, NewRuntimeSizedArraySE, OutsideLoadSE, OverloadSetSE,
  OwnershippedSE, PureSE, ReturnSE, RuneLookupSE, StaticArrayFromCallableSE, StaticArrayFromValuesSE,
  TupleSE, VoidSE,
};
use crate::postparsing::names::ImplicitRuneValS;
use crate::postparsing::rules::rules::{
  ILiteralSL, IRulexSR, IntLiteralSL, LiteralSR, MutabilityLiteralSL, RuneUsage, VariabilityLiteralSL,
};
use crate::postparsing::names::{
  CodeNameS, CodeRuneS, IFunctionDeclarationNameS, IImpreciseNameS,
  IImpreciseNameValS, IRuneS, IRuneValS, IVarNameS,
};
use crate::postparsing::post_parser::{
  CouldntFindRuneS, CouldntFindVarToMutateS, FunctionEnvironmentS, ICompileErrorS, IEnvironmentS,
  InitializingRuntimeSizedArrayRequiresSizeAndCallable,
  InitializingStaticSizedArrayRequiresSizeAndCallable, PostParser, RangedInternalErrorS, StackFrame, StatementAfterReturnS,
  VariableNameAlreadyExists,
};
use crate::postparsing::post_parser::translate_imprecise_name;
use crate::postparsing::patterns::pattern_scout::{get_parameter_captures, translate_pattern};
use crate::postparsing::rules::rule_scout::translate_rulexes;
use crate::postparsing::rules::templex_scout::translate_templex;
use crate::postparsing::loop_post_parser::{scout_each, scout_while};
use crate::postparsing::variable_uses::{VariableDeclarations, VariableUses};
use crate::utils::range::RangeS;
use crate::postparsing::expressions::ConstantFloatSE;
use crate::postparsing::expressions::BreakSE;
use crate::postparsing::expressions::UnletSE;
use std::collections::HashMap;

#[derive(Copy, Clone, Debug, PartialEq)]
pub(crate) enum IScoutResult<'s, 'p> {
  LocalLookupResult(LocalLookupResultS<'s>),
  OutsideLookupResult(OutsideLookupResultS<'s, 'p>),
  NormalResult(NormalResultS<'s>),
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub(crate) struct LocalLookupResultS<'s> {
  range: RangeS<'s>,
  name: IVarNameS<'s>,
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub(crate) struct OutsideLookupResultS<'s, 'p> {
  range: RangeS<'s>,
  name: StrI<'s>,
  template_args: Option<&'p [&'p ITemplexPT<'p>]>,
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub(crate) struct NormalResultS<'s> {
  pub(crate) expr: &'s IExpressionSE<'s>,
}

impl<'s, 'p, 'ctx> PostParser<'s, 'p, 'ctx>
{

fn ends_with_return(_expr_se: &IExpressionSE<'s>) -> bool {
  panic!("Unimplemented ends_with_return");
}

pub(crate) fn scout_block(
  &self,
  parent_stack_frame: StackFrame<'s>,
  lidb: &mut LocationInDenizenBuilder,
  // When we scout a function, it might hand in things here because it wants them to be considered part of
  // the body's block, so that we get to reuse the code at the bottom of function, tracking uses etc.
  initial_locals: VariableDeclarations<'s>,
  block_pe: &'p BlockPE<'p>,
) -> Result<(&'s IExpressionSE<'s>, VariableUses<'s>, VariableUses<'s>), ICompileErrorS<'s>>
{
  let file = parent_stack_frame.file;
  let range_s = PostParser::eval_range(file, block_pe.range);
  assert!(block_pe.maybe_default_region.is_none());
  let context_region: IRuneS<'s> = match &block_pe.maybe_default_region {
    None => parent_stack_frame.context_region.clone(),
    Some(region_rune_pt) => {
      let region_rune_name = region_rune_pt
        .name
        .as_ref()
        .unwrap_or_else(|| panic!("POSTPARSER_SCOUT_BLOCK_DEFAULT_REGION_NAME_MISSING"));
      // Re-intern string from 'p into 's for cross-arena translation
      let region_rune_name_s: StrI<'s> = self.scout_arena.intern_str(region_rune_name.str().as_str());
      let region_rune_s: IRuneS<'s> = self.scout_arena.intern_rune(IRuneValS::CodeRune(CodeRuneS::<'s> {
        name: region_rune_name_s,
      }));
      if !parent_stack_frame.parent_env.all_declared_runes().contains(&region_rune_s) {
        return Err(ICompileErrorS::CouldntFindRuneS(CouldntFindRuneS {
          range: range_s.clone(),
          name: region_rune_name.str().as_str().to_string(),
        }));
      }
      region_rune_s
    }
  };
  let function_body_env: FunctionEnvironmentS<'s> = parent_stack_frame.parent_env.clone();
  let mut child_lidb = lidb.child();
  let (block_s, self_uses_of_things_from_above, child_uses_of_things_from_above) = self.new_block(
    function_body_env,
    Some(parent_stack_frame),
    &mut child_lidb,
    range_s,
    context_region,
    initial_locals,
    |stack_frame1, block_lidb| {
      let (stack_frame2, inner_expr_s, self_uses, child_uses) =
        self.scout_expression_and_coerce(stack_frame1, block_lidb, block_pe.inner, LoadAsP::Use)?;
      Ok((stack_frame2, inner_expr_s, self_uses, child_uses))
    },
  )?;
  let resulting_expr_s = if block_pe.maybe_pure.is_some() {
    let block_s = &*self.scout_arena.alloc(block_s);
    &*self.scout_arena.alloc(IExpressionSE::Pure(PureSE {
      range: PostParser::eval_range(file, block_pe.range),
      location: lidb.child().consume_in_arena(self.scout_arena),
      inner: &*self.scout_arena.alloc(IExpressionSE::Block(block_s)),
    }))
  } else {
    let block_s = &*self.scout_arena.alloc(block_s);
    &*self.scout_arena.alloc(IExpressionSE::Block(block_s))
  };
  Ok((
    resulting_expr_s,
    self_uses_of_things_from_above,
    child_uses_of_things_from_above,
  ))
}

fn scout_impure_block(
  &self,
  parent_stack_frame: StackFrame<'s>,
  lidb: &mut LocationInDenizenBuilder,
  initial_locals: VariableDeclarations<'s>,
  block_pe: &'p BlockPE<'p>,
) -> Result<(&'s BlockSE<'s>, VariableUses<'s>, VariableUses<'s>), ICompileErrorS<'s>>
{
  let (expr_s, self_uses_of_things_from_above, child_uses_of_things_from_above) = self.scout_block(
    parent_stack_frame,
    lidb,
    initial_locals,
    block_pe,
  )?;
  match expr_s {
    IExpressionSE::Block(block_s) => Ok((
      block_s,
      self_uses_of_things_from_above,
      child_uses_of_things_from_above,
    )),
    _ => panic!("POSTPARSER_SCOUT_IMPURE_BLOCK_EXPECTED_BLOCK"),
  }
}

  pub(crate) fn new_block<F>(
    &self,
    function_body_env: FunctionEnvironmentS<'s>,
    parent_stack_frame: Option<StackFrame<'s>>,
    lidb: &mut LocationInDenizenBuilder,
    range_s: RangeS<'s>,
    context_region: IRuneS<'s>,
    initial_locals: VariableDeclarations<'s>,
    scout_contents: F,
  ) -> Result<(&'s BlockSE<'s>, VariableUses<'s>, VariableUses<'s>), ICompileErrorS<'s>>
  where
    F: FnOnce(
      StackFrame<'s>,
      &mut LocationInDenizenBuilder,
    ) -> Result<
      (
        StackFrame<'s>,
        &'s IExpressionSE<'s>,
        VariableUses<'s>,
        VariableUses<'s>,
      ),
      ICompileErrorS<'s>,
    >,
  {
    let maybe_parent = parent_stack_frame.clone().map(Box::new);
    let pure_height = parent_stack_frame
      .map(|parent| parent.pure_height + 1)
      .unwrap_or(0);
    let initial_stack_frame = StackFrame::<'s> {
      file: function_body_env.file,
      name: function_body_env.name.clone(),
      parent_env: function_body_env,
      maybe_parent,
      context_region,
      pure_height,
      locals: initial_locals,
    };
    let mut inner_lidb = lidb.child();
    let (
      stack_frame_before_constructing,
      expr_without_constructing_without_void,
      self_uses_before_constructing,
      child_uses_before_constructing,
    ) = scout_contents(initial_stack_frame, &mut inner_lidb)?;

    // If we had for example:
    //   func MyStruct() {
    //     this.a = 5;
    //     println("flamscrankle");
    //     this.b = true;
    //   }
    // then here's where we insert the final
    //     MyStruct(`this.a`, `this.b`);
    let constructing_member_names: Vec<StrI<'s>> = stack_frame_before_constructing
      .locals
      .vars
      .iter()
      .filter_map(|declared| match &declared.name {
        IVarNameS::ConstructingMemberName(member_name) => Some(*member_name),
        _ => None,
      })
      .collect();
    let (
      stack_frame_after_constructing,
      expr_with_constructing_if_necessary,
      self_uses,
      child_uses,
    ): (StackFrame<'s>, &'s IExpressionSE<'s>, VariableUses<'s>, VariableUses<'s>) = if constructing_member_names.is_empty() {
      // Add a void to the end, unless:
      // - we're constructing
      // - we end with a return
      // - a result was requested.
      (
        stack_frame_before_constructing,
        expr_without_constructing_without_void,
        self_uses_before_constructing,
        child_uses_before_constructing,
      )
    } else {
      // Per @PPSPASTNZ, synthesize a constructor call as parser AST, then scout it.
      let function_name = match &stack_frame_before_constructing.parent_env.name {
        IFunctionDeclarationNameS::FunctionName(function_name_s) => {
          // Re-intern into 'p arena for synthetic parser node (see @PPSPASTNZ)
          self.parse_arena.intern_str(function_name_s.name.as_str())
        }
        _ => panic!("POSTPARSER_NEW_BLOCK_EXPECTED_FUNCTION_NAME"),
      };
      let range_at_end = RangeL(range_s.end.offset, range_s.end.offset);
      // Per @PPSPASTNZ, allocate synthetic parser node in parse_arena
      let callable_expr_p = &*self.parse_arena.alloc(IExpressionPE::Lookup(self.parse_arena.alloc(LookupPE {
        name: IImpreciseNameP::LookupName(NameP(range_at_end, function_name)),
        template_args: None,
      })));
      // Per @PPSPASTNZ, all synthetic parser nodes allocated in parse_arena ('p)
      let self_keyword_p = self.parse_arena.intern_str(self.keywords.self_.as_str());
      let arg_exprs_p: Vec<&'p IExpressionPE<'p>> = constructing_member_names
        .iter()
        .map(|member_name: &StrI<'s>| -> &'p IExpressionPE<'p> {
          let self_lookup_p = &*self.parse_arena.alloc(IExpressionPE::Lookup(self.parse_arena.alloc(LookupPE {
            name: IImpreciseNameP::LookupName(NameP(range_at_end, self_keyword_p)),
            template_args: None,
          })));
          let member_name_p = self.parse_arena.intern_str(member_name.as_str());
          &*self.parse_arena.alloc(IExpressionPE::Dot(DotPE {
            range: range_at_end,
            left: self_lookup_p,
            operator_range: RangeL::zero(),
            member: NameP(range_at_end, member_name_p),
          }))
        })
        .collect();
      // Per @PPSPASTNZ, allocate synthetic parser node in parse_arena
      let constructor_call_p: &'p IExpressionPE<'p> = &*self.parse_arena.alloc(IExpressionPE::FunctionCall(FunctionCallPE {
        range: range_at_end,
        operator_range: RangeL::zero(),
        callable_expr: callable_expr_p,
        arg_exprs: self.parse_arena.alloc_slice_from_vec(arg_exprs_p),
      }));
      let mut constructor_lidb = lidb.child();
      let (
        stack_frame_after_constructing,
        constructor_result,
        self_uses_after_constructing,
        child_uses_after_constructing,
      ) = self.scout_expression(
        stack_frame_before_constructing,
        &mut constructor_lidb,
        constructor_call_p,
      )?;
      let construct_expression = match constructor_result {
        IScoutResult::NormalResult(NormalResultS { expr }) => expr,
        _ => panic!("POSTPARSER_NEW_BLOCK_CONSTRUCTOR_SCOUT_RESULT_NOT_NORMAL"),
      };
      let expr_after_constructing =
        self.consecutive(vec![expr_without_constructing_without_void, construct_expression]);
      (
        stack_frame_after_constructing,
        expr_after_constructing,
        self_uses_before_constructing.then_merge(&self_uses_after_constructing),
        child_uses_before_constructing.then_merge(&child_uses_after_constructing),
      )
    };
    let locals: Vec<LocalS<'s>> = stack_frame_after_constructing
      .locals
      .vars
      .iter()
      .map(|declared| LocalS {
        var_name: declared.name.clone(),
        self_borrowed: self_uses.is_borrowed(&declared.name),
        self_moved: self_uses.is_moved(&declared.name),
        self_mutated: self_uses.is_mutated(&declared.name),
        child_borrowed: child_uses.is_borrowed(&declared.name),
        child_moved: child_uses.is_moved(&declared.name),
        child_mutated: child_uses.is_mutated(&declared.name),
      })
      .collect();
    let self_uses_of_things_from_above = VariableUses {
      uses: self_uses
        .uses
        .iter()
        .filter(|use_| !locals.iter().any(|local| local.var_name == use_.name))
        .cloned()
        .collect(),
    };
    let child_uses_of_things_from_above = VariableUses {
      uses: child_uses
        .uses
        .iter()
        .filter(|use_| !locals.iter().any(|local| local.var_name == use_.name))
        .cloned()
        .collect(),
    };
    Ok((
      &*self.scout_arena.alloc(
      BlockSE::<'s> {
        range: range_s,
        locals: self.scout_arena.alloc_slice_from_vec(locals),
        expr: expr_with_constructing_if_necessary,
      }),
      self_uses_of_things_from_above,
      child_uses_of_things_from_above,
    ))
  }

fn find_local(
  &self,
  stack_frame: &StackFrame<'s>,
  range: RangeS<'s>,
  imprecise_name: &IImpreciseNameS<'s>,
) -> Option<LocalLookupResultS<'s>> {
  stack_frame
    .find_variable(imprecise_name)
    .map(|full_name| LocalLookupResultS::<'s> { range, name: full_name })
}

// Returns:
// - new seq num
// - declared variables
// - new expression
// - variable uses by self
// - variable uses by child blocks
// AFTERM: rename all "scout" to "post parse" or something.
fn scout_expression(
  &self,
  stack_frame: StackFrame<'s>,
  lidb: &mut LocationInDenizenBuilder,
  expression: &'p IExpressionPE<'p>,
) -> Result<(StackFrame<'s>, IScoutResult<'s, 'p>, VariableUses<'s>, VariableUses<'s>), ICompileErrorS<'s>>
{

  let file_coordinate = stack_frame.file;
  match expression {
  
    IExpressionPE::Void(void) => Ok((
      stack_frame,
      IScoutResult::NormalResult(NormalResultS {
        expr: &*self.scout_arena.alloc(IExpressionSE::Void(VoidSE {
          range: PostParser::eval_range(file_coordinate, void.range),
        })),
      }),
      VariableUses::<'s>::empty(),
      VariableUses::<'s>::empty(),
    )),
    
    IExpressionPE::Lambda(lambda) => {
      let (function_s, child_uses) = self.scout_lambda(stack_frame.clone(), &lambda.function)?;
      Ok((
        stack_frame.clone(),
        IScoutResult::NormalResult(NormalResultS {
          expr: &*self
              .scout_arena
              .alloc(IExpressionSE::Function(FunctionSE { function: function_s })),
        }),
        VariableUses::<'s>::empty(),
        child_uses,
      ))
    }
        
    IExpressionPE::Augment(augment) => {
      let load_as = match augment.target_ownership {
        OwnershipP::Borrow => LoadAsP::LoadAsBorrow,
        OwnershipP::Weak => LoadAsP::LoadAsWeak,
        OwnershipP::Own => panic!("POSTPARSER_AUGMENT_OWN_NOT_YET_IMPLEMENTED"),
        OwnershipP::Live => panic!("POSTPARSER_AUGMENT_LIVE_NOT_YET_IMPLEMENTED"),
        OwnershipP::Share => panic!("POSTPARSER_AUGMENT_SHARE_NOT_YET_IMPLEMENTED"),
      };
      let (stack_frame1, inner_expr_s, inner_self_uses, inner_child_uses): (StackFrame<'s>, &'s IExpressionSE<'s>, VariableUses<'s>, VariableUses<'s>) = {
        let mut inner_lidb = lidb.child();
        let (stack_frame1, inner_expr_s, inner_self_uses, inner_child_uses) = self.scout_expression_and_coerce(
          stack_frame,
          &mut inner_lidb,
          augment.inner,
          load_as,
        )?;
        (stack_frame1, inner_expr_s, inner_self_uses, inner_child_uses)
      };
      match &inner_expr_s {
        IExpressionSE::Ownershipped(ownershipped) => {
          assert_eq!(ownershipped.target_ownership, load_as);
        }
        IExpressionSE::LocalLoad(local_load) => {
          assert_eq!(local_load.target_ownership, load_as);
        }
        _ => panic!("POSTPARSER_SCOUT_AUGMENT_UNEXPECTED_RESULT"),
      }
      Ok((
        stack_frame1,
        IScoutResult::NormalResult(NormalResultS { expr: inner_expr_s }),
        inner_self_uses,
        inner_child_uses,
      ))
    }
      
    IExpressionPE::Return(ret) => {
      let mut ret_expr_lidb = lidb.child();
      let (stack_frame1, inner_expr_s, inner_self_uses, inner_child_uses) = self.scout_expression_and_coerce(
        stack_frame,
        &mut ret_expr_lidb,
        ret.expr,
        LoadAsP::Use,
      )?;
      Ok((
        stack_frame1,
        IScoutResult::NormalResult(NormalResultS {
          expr: &*self.scout_arena.alloc(IExpressionSE::Return(ReturnSE {
            range: PostParser::eval_range(&file_coordinate, ret.range),
            inner: inner_expr_s,
          })),
        }),
        inner_self_uses,
        inner_child_uses,
      ))
    }
      
    IExpressionPE::SubExpression(sub_expression) => {
      let mut sub_expression_lidb = lidb.child();
      let (stack_frame1, sub_expression_s, sub_self_uses, sub_child_uses) = self.scout_expression_and_coerce(
        stack_frame,
        &mut sub_expression_lidb,
        sub_expression.inner,
        LoadAsP::Use,
      )?;
      Ok((
        stack_frame1,
        IScoutResult::NormalResult(NormalResultS {
          expr: sub_expression_s,
        }),
        sub_self_uses,
        sub_child_uses,
      ))
    }
    
    IExpressionPE::ConstantInt(constant_int) => Ok((
      stack_frame.clone(),
      IScoutResult::NormalResult(NormalResultS {
        expr: &*self.scout_arena.alloc(IExpressionSE::ConstantInt(ConstantIntSE {
          range: PostParser::eval_range(&file_coordinate, constant_int.range),
          value: constant_int.value,
          bits: constant_int.bits.unwrap_or(32) as i32,
        })),
      }),
      VariableUses::<'s>::empty(),
      VariableUses::<'s>::empty(),
    )),
    
    IExpressionPE::ConstantBool(constant_bool) => Ok((
      stack_frame.clone(),
      IScoutResult::NormalResult(NormalResultS {
        expr: &*self.scout_arena.alloc(IExpressionSE::ConstantBool(ConstantBoolSE {
          range: PostParser::eval_range(&file_coordinate, constant_bool.range),
          value: constant_bool.value,
        })),
      }),
      VariableUses::<'s>::empty(),
      VariableUses::<'s>::empty(),
    )),
        
    IExpressionPE::ConstantStr(constant_str) => Ok((
      stack_frame.clone(),
      IScoutResult::NormalResult(NormalResultS {
        expr: &*self.scout_arena.alloc(IExpressionSE::ConstantStr(ConstantStrSE {
          range: PostParser::eval_range(&file_coordinate, constant_str.range),
          value: self.scout_arena.intern_str(constant_str.value.as_str()),
        })),
      }),
      VariableUses::<'s>::empty(),
      VariableUses::<'s>::empty(),
    )),
        
    IExpressionPE::ConstantFloat(constant_float) => Ok((
      stack_frame.clone(),
      IScoutResult::NormalResult(NormalResultS {
        expr: &*self.scout_arena.alloc(IExpressionSE::ConstantFloat(ConstantFloatSE {
          range: PostParser::eval_range(&file_coordinate, constant_float.range),
          value: constant_float.value,
        })),
      }),
      VariableUses::<'s>::empty(),
      VariableUses::<'s>::empty(),
    )),
        
    IExpressionPE::MagicParamLookup(magic_param_lookup) => {
      let range_s = PostParser::eval_range(&file_coordinate, magic_param_lookup.range);
      let name = IVarNameS::MagicParamName(PostParser::eval_pos(
        &file_coordinate,
        magic_param_lookup.range.begin(),
      ));
      Ok((
        stack_frame.clone(),
        IScoutResult::LocalLookupResult(LocalLookupResultS {
          range: range_s,
          name: name.clone(),
        }),
        VariableUses::<'s>::empty().mark_moved(name),
        VariableUses::<'s>::empty(),
      ))
    }
    
    IExpressionPE::Lookup(lookup) => {
      match &lookup.template_args {
        None => {
          let range = PostParser::eval_range(&file_coordinate, lookup.name.range());
          let imprecise_name_s = translate_imprecise_name(self.scout_arena, &file_coordinate, &lookup.name);
          let lookup_result = match self.find_local(&stack_frame, range.clone(), &imprecise_name_s) {
            Some(local_result) => IScoutResult::LocalLookupResult(local_result),
            None => match &imprecise_name_s {
              IImpreciseNameS::CodeName(code_name) => {
                let name = code_name.name;
                let code_rune = self.scout_arena.intern_rune(IRuneValS::CodeRune(CodeRuneS { name }));
                if stack_frame.parent_env.all_declared_runes().contains(&code_rune) {
                  IScoutResult::NormalResult(NormalResultS {
                    expr: &*self.scout_arena.alloc(IExpressionSE::RuneLookup(RuneLookupSE {
                      range,
                      rune: code_rune,
                    })),
                  })
                } else {
                  IScoutResult::OutsideLookupResult(OutsideLookupResultS {
                    range,
                    name,
                    template_args: None,
                  })
                }
              }
              _ => panic!("POSTPARSER_SCOUT_LOOKUP_IMPRECISE_NAME_NOT_CODE_NAME"),
            }
          };
          Ok((
            stack_frame.clone(),
            lookup_result,
            VariableUses::<'s>::empty(),
            VariableUses::<'s>::empty(),
          ))
        }
        Some(template_args) => {
          let (range, template_name) = match &lookup.name {
            IImpreciseNameP::LookupName(name_p) => (PostParser::eval_range(&file_coordinate, name_p.range()), self.scout_arena.intern_str(name_p.str().as_str())),
            _ => panic!("POSTPARSER_SCOUT_LOOKUP_TEMPLATE_ARGS_EXPECTED_LOOKUP_NAME"),
          };
          Ok((
            stack_frame.clone(),
            IScoutResult::OutsideLookupResult(OutsideLookupResultS {
              range,
              name: template_name,
              template_args: Some(template_args.args),
            }),
            VariableUses::<'s>::empty(),
            VariableUses::<'s>::empty(),
          ))
        }
      }
    }
    
    IExpressionPE::FunctionCall(function_call) => {
      let parent_env = IEnvironmentS::FunctionEnvironment(stack_frame.parent_env.clone());
      let context_region = stack_frame.context_region.clone();
      let mut callable_lidb = lidb.child();
      let (stack_frame0a, subject_uncoerced_scout_result, uncoerced_callable_self_uses, uncoerced_callable_child_uses) =
        self.scout_expression(stack_frame, &mut callable_lidb, function_call.callable_expr)?;
      match subject_uncoerced_scout_result {
        IScoutResult::OutsideLookupResult(OutsideLookupResultS { range, name: container_name, template_args: container_maybe_template_args }) => {
          let mut rule_builder = Vec::new();
          let container_template_arg_rune_usages = self.translate_maybe_template_args(
            &parent_env,
            lidb,
            &mut rule_builder,
            context_region,
            container_maybe_template_args,
          );
          let load_part_name = self.scout_arena.intern_imprecise_name(IImpreciseNameValS::CodeName(CodeNameS {
            name: container_name,
          }));
          let load_part = self.scout_arena.alloc(LoadPartSE {
            name: load_part_name,
            explicit_template_args: container_template_arg_rune_usages,
          });
          let overload_set_se = &*self.scout_arena.alloc(IExpressionSE::OverloadSet(OverloadSetSE {
            lookup: OutsideLoadSE {
              range,
              rules: self.scout_arena.alloc_slice_from_vec(rule_builder),
              parts: self.scout_arena.alloc_slice_copy(&[&*load_part]),
            },
          }));
          let mut args_lidb = lidb.child();
          let (stack_frame3, args_se, self_uses, child_uses) =
            self.scout_elements_as_expressions(stack_frame0a, &mut args_lidb, &function_call.arg_exprs)?;
          let result = IScoutResult::NormalResult(NormalResultS {
            expr: &*self.scout_arena.alloc(IExpressionSE::FunctionCall(FunctionCallSE {
              range,
              location: lidb.child().consume_in_arena(self.scout_arena),
              callable_expr: overload_set_se,
              arg_exprs: self.scout_arena.alloc_slice_from_vec(args_se),
            })),
          });
          Ok((stack_frame3, result, self_uses, child_uses))
        }
        _ => {
          let callable_child_uses = uncoerced_callable_child_uses;
          let (stack_frame2, callable1, callable_self_uses) = self.coerce(
            stack_frame0a,
            subject_uncoerced_scout_result,
            &mut lidb.child(),
            uncoerced_callable_self_uses,
            LoadAsP::LoadAsBorrow,
          )?;
          let mut args_lidb = lidb.child();
          let (stack_frame3, args1, args_self_uses, args_child_uses) =
            self.scout_elements_as_expressions(stack_frame2, &mut args_lidb, &function_call.arg_exprs)?;
          let result = IScoutResult::NormalResult(NormalResultS {
            expr: &*self.scout_arena.alloc(IExpressionSE::FunctionCall(FunctionCallSE {
              range: PostParser::eval_range(&file_coordinate, function_call.range),
              location: lidb.child().consume_in_arena(self.scout_arena),
              callable_expr: callable1,
              arg_exprs: self.scout_arena.alloc_slice_from_vec(args1),
            })),
          });
          Ok((stack_frame3, result, callable_self_uses.then_merge(&args_self_uses), callable_child_uses.then_merge(&args_child_uses)))
        }
      }
    }
    
    IExpressionPE::BinaryCall(binary_call) => {
      let part_name = self.scout_arena.intern_imprecise_name(IImpreciseNameValS::CodeName(CodeNameS {
        name: self.scout_arena.intern_str(binary_call.function_name.str().as_str()),
      }));
      let load_part = self.scout_arena.alloc(LoadPartSE {
        name: part_name,
        explicit_template_args: &[],
      });
      let overload_set_se = &*self.scout_arena.alloc(IExpressionSE::OverloadSet(OverloadSetSE {
        lookup: OutsideLoadSE {
          range: PostParser::eval_range(&file_coordinate, binary_call.range),
          rules: &[],
          parts: self.scout_arena.alloc_slice_copy(&[&*load_part]),
        },
      }));
      let (stack_frame1, left_expr_s, left_self_uses, left_child_uses): (StackFrame<'s>, &'s IExpressionSE<'s>, VariableUses<'s>, VariableUses<'s>) = {
        let mut left_lidb = lidb.child();
        self.scout_expression_and_coerce(
          stack_frame,
          &mut left_lidb,
          binary_call.left_expr,
          LoadAsP::LoadAsBorrow,
        )?
      };
      let (stack_frame2, right_expr_s, right_self_uses, right_child_uses) = {
        let mut right_lidb = lidb.child();
        self.scout_expression_and_coerce(
          stack_frame1,
          &mut right_lidb,
          binary_call.right_expr,
          LoadAsP::LoadAsBorrow,
        )?
      };
      let result = IScoutResult::NormalResult(NormalResultS {
        expr: &*self.scout_arena.alloc(IExpressionSE::FunctionCall(FunctionCallSE {
          range: PostParser::eval_range(&file_coordinate, binary_call.range),
          location: lidb.child().consume_in_arena(self.scout_arena),
          callable_expr: overload_set_se,
          arg_exprs: self.scout_arena.alloc_slice_from_vec(
            vec![left_expr_s, right_expr_s],
          ),
        })),
      });
      Ok((
        stack_frame2,
        result,
        left_self_uses.then_merge(&right_self_uses),
        left_child_uses.then_merge(&right_child_uses),
      ))
    }
    
    IExpressionPE::MethodCall(method_call) => {
      let method_call_range_s = PostParser::eval_range(&file_coordinate, method_call.range);
      let parent_env = IEnvironmentS::FunctionEnvironment(stack_frame.parent_env.clone());
      let context_region = stack_frame.context_region.clone();
      let method_name_p = method_call.method_lookup.name;
      let maybe_method_template_args_args: Option<&'p [&'p ITemplexPT<'p>]> =
        method_call.method_lookup.template_args.as_ref().map(|t| t.args);
      let mut subject_lidb = lidb.child();
      let (stack_frame0a, subject_uncoerced_scout_result, uncoerced_subject_self_uses, uncoerced_subject_child_uses) =
        self.scout_expression(stack_frame, &mut subject_lidb, method_call.subject_expr)?;
      let method_imprecise_name_s = translate_imprecise_name(self.scout_arena, &file_coordinate, &method_name_p);
      let mut rule_builder: Vec<IRulexSR<'s>> = Vec::new();
      let method_template_arg_rune_usages = self.translate_maybe_template_args(
        &parent_env,
        lidb,
        &mut rule_builder,
        context_region.clone(),
        maybe_method_template_args_args,
      );
      let (stack_frame3, args, self_uses, child_uses, container_parts): (StackFrame<'s>, Vec<&'s IExpressionSE<'s>>, VariableUses<'s>, VariableUses<'s>, Vec<&'s LoadPartSE<'s>>) =
        match subject_uncoerced_scout_result {
          IScoutResult::OutsideLookupResult(OutsideLookupResultS { range: _subject_range_s, name: container_name, template_args: container_maybe_template_args }) => {
            assert!(uncoerced_subject_self_uses.is_empty());
            assert!(uncoerced_subject_child_uses.is_empty());
            let container_template_arg_rune_usages = self.translate_maybe_template_args(
              &parent_env,
              lidb,
              &mut rule_builder,
              context_region.clone(),
              container_maybe_template_args,
            );
            let mut args_lidb = lidb.child();
            let (stack_frame3, args, self_uses, child_uses) =
              self.scout_elements_as_expressions(stack_frame0a, &mut args_lidb, method_call.arg_exprs)?;
            // We don't support more than 2 parts yet
            let container_load_part_name = self.scout_arena.intern_imprecise_name(IImpreciseNameValS::CodeName(CodeNameS {
              name: container_name,
            }));
            let container_part = self.scout_arena.alloc(LoadPartSE {
              name: container_load_part_name,
              explicit_template_args: container_template_arg_rune_usages,
            });
            (stack_frame3, args, self_uses, child_uses, vec![&*container_part])
          }
          _ => {
            let load_subject_as = match method_call.subject_expr {
              // For locals, just borrow.
              IExpressionPE::Lookup(_) => LoadAsP::LoadAsBorrow,
              // For anything else, default to moving.
              _ => LoadAsP::Use,
            };
            let subject_child_uses = uncoerced_subject_child_uses;
            let mut coerce_lidb = lidb.child();
            let (stack_frame1, subject1, subject_self_uses) = self.coerce(
              stack_frame0a,
              subject_uncoerced_scout_result,
              &mut coerce_lidb,
              uncoerced_subject_self_uses,
              load_subject_as,
            )?;
            let mut args_lidb = lidb.child();
            let (stack_frame3, tail_args1, tail_args_self_uses, tail_args_child_uses) =
              self.scout_elements_as_expressions(stack_frame1, &mut args_lidb, method_call.arg_exprs)?;
            let mut args = vec![subject1];
            args.extend(tail_args1);
            (stack_frame3, args, subject_self_uses.then_merge(&tail_args_self_uses), subject_child_uses.then_merge(&tail_args_child_uses), Vec::new())
          }
        };
      let method_load_part = self.scout_arena.alloc(LoadPartSE {
        name: method_imprecise_name_s,
        explicit_template_args: method_template_arg_rune_usages,
      });
      let mut parts: Vec<&'s LoadPartSE<'s>> = container_parts;
      parts.push(&*method_load_part);
      let parts_slice = self.scout_arena.alloc_slice_copy(&parts);
      let overload_set_se = &*self.scout_arena.alloc(IExpressionSE::OverloadSet(OverloadSetSE {
        lookup: OutsideLoadSE {
          range: method_call_range_s,
          rules: self.scout_arena.alloc_slice_from_vec(rule_builder),
          parts: parts_slice,
        },
      }));
      let result = IScoutResult::NormalResult(NormalResultS {
        expr: &*self.scout_arena.alloc(IExpressionSE::FunctionCall(FunctionCallSE {
          range: method_call_range_s,
          location: lidb.child().consume_in_arena(self.scout_arena),
          callable_expr: overload_set_se,
          arg_exprs: self.scout_arena.alloc_slice_from_vec(args),
        })),
      });
      Ok((stack_frame3, result, self_uses, child_uses))
    }
        
    IExpressionPE::ConstructArray(construct_array) => {
      let range_s = PostParser::eval_range(&file_coordinate, construct_array.range);
      let mut rule_builder = Vec::new();
      let parent_env = IEnvironmentS::FunctionEnvironment(stack_frame.parent_env.clone());
      let context_region = stack_frame.context_region.clone();
      let maybe_type_rune_s = construct_array.type_pt.as_ref().map(|type_pt| {
        translate_templex(
          self.scout_arena,
          self.keywords,
          parent_env.clone(),
          &mut lidb.child(),
          &mut rule_builder,
          context_region.clone(),
          type_pt,
        )
      });
      let mutability_rune_s = match &construct_array.mutability_pt {
        None => {
          let rune = self.scout_arena.intern_rune(IRuneValS::ImplicitRune(ImplicitRuneValS::new(lidb.child().borrow_val())));
          let rune_usage = RuneUsage { range: range_s, rune };
          rule_builder.push(IRulexSR::Literal(LiteralSR {
            range: range_s,
            rune: rune_usage,
            literal: ILiteralSL::MutabilityLiteral(MutabilityLiteralSL { mutability: MutabilityP::Mutable }),
          }));
          rune_usage
        }
        Some(mutability_pt) => {
          translate_templex(
            self.scout_arena,
            self.keywords,
            parent_env.clone(),
            &mut lidb.child(),
            &mut rule_builder,
            context_region.clone(),
            mutability_pt,
          )
        }
      };
      let variability_rune_s = match &construct_array.variability_pt {
        None => {
          let rune = self.scout_arena.intern_rune(IRuneValS::ImplicitRune(ImplicitRuneValS::new(lidb.child().borrow_val())));
          let rune_usage = RuneUsage { range: range_s, rune };
          rule_builder.push(IRulexSR::Literal(LiteralSR {
            range: range_s,
            rune: rune_usage,
            literal: ILiteralSL::VariabilityLiteral(VariabilityLiteralSL { variability: VariabilityP::Final }),
          }));
          rune_usage
        }
        Some(variability_pt) => {
          translate_templex(
            self.scout_arena,
            self.keywords,
            parent_env.clone(),
            &mut lidb.child(),
            &mut rule_builder,
            context_region.clone(),
            variability_pt,
          )
        }
      };
      let mut args_lidb = lidb.child();
      let (stack_frame1, args_s, self_uses, child_uses) =
          self.scout_elements_as_expressions(stack_frame, &mut args_lidb, &construct_array.args)?;
      let result = match &construct_array.size {
        IArraySizeP::RuntimeSized => {
          assert!(
            !construct_array.initializing_individual_elements,
            "POSTPARSER_SCOUT_CONSTRUCT_ARRAY_RUNTIME_INIT_INDIVIDUAL_ELEMENTS_NOT_YET_IMPLEMENTED"
          );
          if args_s.is_empty() || args_s.len() > 2 {
            return Err(
              ICompileErrorS::InitializingRuntimeSizedArrayRequiresSizeAndCallable(
                InitializingRuntimeSizedArrayRequiresSizeAndCallable { range: range_s },
              ),
            );
          }
          let size_se = args_s[0];
          let callable_se = args_s.get(1).copied();
          IExpressionSE::NewRuntimeSizedArray(NewRuntimeSizedArraySE {
            range: range_s,
            rules: self.scout_arena.alloc_slice_from_vec(rule_builder),
            maybe_element_type_st: maybe_type_rune_s,
            mutability_st: mutability_rune_s,
            size: self.scout_arena.alloc(size_se),
            callable: callable_se,
          })
        }
        IArraySizeP::StaticSized(StaticSizedArraySizeP { size_pt: maybe_size_pt }) => {
          let maybe_size_rune_s = maybe_size_pt.as_ref().map(|size_pt| {
            translate_templex(
              self.scout_arena,
              self.keywords,
              parent_env.clone(),
              &mut lidb.child(),
              &mut rule_builder,
              context_region.clone(),
              size_pt,
            )
          });
          if construct_array.initializing_individual_elements {
            let size_rune_s = match maybe_size_rune_s {
              Some(s) => s,
              None => {
                let rune = self.scout_arena.intern_rune(IRuneValS::ImplicitRune(ImplicitRuneValS::new(lidb.child().borrow_val())));
                let rune_usage = RuneUsage { range: range_s, rune };
                rule_builder.push(IRulexSR::Literal(LiteralSR {
                  range: range_s,
                  rune: rune_usage,
                  literal: ILiteralSL::IntLiteral(IntLiteralSL { value: args_s.len() as i64 }),
                }));
                rune_usage
              }
            };
            IExpressionSE::StaticArrayFromValues(StaticArrayFromValuesSE {
              range: range_s,
              rules: self.scout_arena.alloc_slice_from_vec(rule_builder),
              maybe_element_type_st: maybe_type_rune_s,
              mutability_st: mutability_rune_s,
              variability_st: variability_rune_s,
              size_st: size_rune_s,
              elements: self.scout_arena.alloc_slice_from_vec(args_s),
            })
          } else {
            if args_s.len() != 1 {
              return Err(
                ICompileErrorS::InitializingStaticSizedArrayRequiresSizeAndCallable(
                  InitializingStaticSizedArrayRequiresSizeAndCallable { range: range_s },
                ),
              );
            }
            let size_rune_s = match maybe_size_rune_s {
              Some(s) => s,
              None => panic!("vassertSome: no size rune for static array from callable"),
            };
            let callable_se = args_s[0];
            IExpressionSE::StaticArrayFromCallable(StaticArrayFromCallableSE {
              range: range_s,
              rules: self.scout_arena.alloc_slice_from_vec(rule_builder),
              maybe_element_type_st: maybe_type_rune_s,
              mutability_st: mutability_rune_s,
              variability_st: variability_rune_s,
              size_st: size_rune_s,
              callable: callable_se,
            })
          }
        }
      };
      Ok((
        stack_frame1,
        IScoutResult::NormalResult(NormalResultS { expr: &*self.scout_arena.alloc(result) }),
        self_uses,
        child_uses,
      ))
    }
    
    IExpressionPE::Block(block) => {
      assert!(
        block.maybe_default_region.is_none(),
        "POSTPARSER_SCOUT_BLOCK_DEFAULT_REGION_NOT_YET_IMPLEMENTED"
      );
      let mut block_lidb = lidb.child();
      let (result_se, self_uses, child_uses) =
          self.scout_block(stack_frame.clone(), &mut block_lidb, PostParser::<'s, 'p, '_>::no_declarations(), block)?;
      Ok((
        stack_frame,
        IScoutResult::NormalResult(NormalResultS { expr: result_se }),
        self_uses,
        child_uses,
      ))
    }
    
    IExpressionPE::Consecutor(consecutor) => {
      let mut consecutor_lidb = lidb.child();
      let (stack_frame1, unfiltered_exprs, self_uses, child_uses) =
          self.scout_elements_as_expressions(stack_frame, &mut consecutor_lidb, &consecutor.inners)?;

      // Match Scala's two-step behavior:
      // 1) recursively scout all inners
      // 2) strip voids that appear after a return; error on non-void after return
      let mut filtered_exprs = Vec::new();
      let mut saw_return = false;
      for expr_s in unfiltered_exprs {
        match (saw_return, &expr_s) {
          (false, IExpressionSE::Return(_)) => {
            saw_return = true;
            filtered_exprs.push(expr_s);
          }
          (false, _) => {
            filtered_exprs.push(expr_s);
          }
          (true, IExpressionSE::Void(_)) => {}
          (true, _) => {
            return Err(ICompileErrorS::StatementAfterReturnS(StatementAfterReturnS {
              range: expr_s.range(),
            }));
          }
        }
      }
      Ok((
        stack_frame1,
        IScoutResult::NormalResult(NormalResultS {
          expr: self.consecutive(filtered_exprs),
        }),
        self_uses,
        child_uses,
      ))
    }
    
    IExpressionPE::If(if_expr) => {
      let range_s = PostParser::eval_range(file_coordinate, if_expr.range);
      let mut block_lidb = lidb.child();
      let (result_se, self_uses, child_uses): (&'s BlockSE<'s>, VariableUses<'s>, VariableUses<'s>) = self.new_block(
        stack_frame.parent_env.clone(),
        Some(stack_frame.clone()),
        &mut block_lidb,
        range_s,
        stack_frame.context_region.clone(),
        PostParser::<'s, 'p, '_>::no_declarations(),
        |stack_frame1: StackFrame<'s>, block_lidb: &mut LocationInDenizenBuilder| {
          let (stack_frame2, cond_se, cond_uses, cond_child_uses): (StackFrame<'s>, &'s IExpressionSE<'s>, VariableUses<'s>, VariableUses<'s>) = {
            let mut cond_lidb = block_lidb.child();
            self.scout_expression_and_coerce(
              stack_frame1,
              &mut cond_lidb,
              if_expr.condition,
              LoadAsP::Use,
            )?
          };
          let (then_se, then_uses, then_child_uses): (&'s BlockSE<'s>, VariableUses<'s>, VariableUses<'s>) = {
            let mut then_lidb = block_lidb.child();
            self.scout_impure_block(
              stack_frame2.clone(),
              &mut then_lidb,
              PostParser::<'s, 'p, '_>::no_declarations(),
              if_expr.then_body,
            )?
          };
          let (else_se, else_uses, else_child_uses): (&'s BlockSE<'s>, VariableUses<'s>, VariableUses<'s>) = {
            let mut else_lidb = block_lidb.child();
            self.scout_impure_block(
              stack_frame2.clone(),
              &mut else_lidb,
              PostParser::<'s, 'p, '_>::no_declarations(),
              if_expr.else_body,
            )?
          };
          let self_case_uses = then_uses.branch_merge(&else_uses);
          let self_uses = cond_uses.then_merge(&self_case_uses);
          let child_case_uses = then_child_uses.branch_merge(&else_child_uses);
          let child_uses = cond_child_uses.then_merge(&child_case_uses);
          let if_se = &*self.scout_arena.alloc(IExpressionSE::If(IfSE {
            range: PostParser::eval_range(file_coordinate, if_expr.range),
            condition: cond_se,
            then_body: then_se,
            else_body: else_se,
          }));
          Ok((
            stack_frame2,
            if_se,
            self_uses,
            child_uses,
          ))
        },
      )?;
      Ok((
        stack_frame,
        IScoutResult::NormalResult(NormalResultS {
          expr: &*self.scout_arena.alloc(IExpressionSE::Block(result_se)),
        }),
        self_uses,
        child_uses,
      ))
    }
    
    IExpressionPE::While(while_expr) => {
      let (loop_s, loop_self_uses, loop_child_uses) = scout_while(
        self,
        stack_frame.clone(),
        lidb,
        while_expr.range,
        while_expr.condition,
        while_expr.body,
      )?;
      Ok((
        stack_frame,
        IScoutResult::NormalResult(NormalResultS {
          expr: &*self.scout_arena.alloc(IExpressionSE::Block(loop_s)),
        }),
        loop_self_uses,
        loop_child_uses,
      ))
    }
        
    IExpressionPE::Each(each) => {
      let (loop_s, self_uses, child_uses) = scout_each(
        self,
        stack_frame.clone(),
        lidb,
        each.range,
        each.maybe_pure.is_some(),
        &each.entry_pattern,
        each.in_keyword_range,
        each.iterable_expr,
        each.body,
      )?;
      Ok((
        stack_frame.clone(),
        IScoutResult::NormalResult(NormalResultS {
          expr: &*self.scout_arena.alloc(IExpressionSE::Block(loop_s)),
        }),
        self_uses,
        child_uses,
      ))
    }
        
    IExpressionPE::Let(lett) => {
      let parent_env = IEnvironmentS::FunctionEnvironment(stack_frame.parent_env.clone());
      let (stack_frame1, source_expr_s, source_self_uses, source_child_uses) = {
        let mut source_expr_lidb = lidb.child();
        self.scout_expression_and_coerce(
          stack_frame,
          &mut source_expr_lidb,
          lett.source,
          LoadAsP::Use,
        )?
      };
      let mut rule_builder = Vec::new();
      let mut rune_to_explicit_type = Vec::new();
      {
        let mut rule_lidb = lidb.child();
        translate_rulexes(
          self.scout_arena,
          self.keywords,
          parent_env,
          &mut rule_lidb,
          &mut rule_builder,
          &mut rune_to_explicit_type,
          stack_frame1.context_region.clone(),
          &[],
        );
      }
      let pattern_s = {
        let mut pattern_lidb = lidb.child();
        let mut rune_to_explicit_type_map = rune_to_explicit_type
          .into_iter()
          .collect::<HashMap<_, _>>();
        translate_pattern(
          self.scout_arena,
          self.keywords,
          stack_frame1.clone(),
          &mut pattern_lidb,
          &mut rule_builder,
          &mut rune_to_explicit_type_map,
          &lett.pattern,
        )
      };
      let declarations_from_pattern = VariableDeclarations {
        vars: get_parameter_captures(&pattern_s),
      };
      let maybe_name_conflict_var_name =
        stack_frame1
          .locals
          .vars
          .iter()
          .map(|decl| decl.name.clone())
          .find(|name| declarations_from_pattern.vars.iter().any(|decl| decl.name == *name));
      if let Some(name_conflict_var_name) = maybe_name_conflict_var_name {
        return Err(ICompileErrorS::VariableNameAlreadyExists(VariableNameAlreadyExists {
          range: PostParser::eval_range(&file_coordinate, lett.range),
          name: name_conflict_var_name,
        }));
      }
      Ok((
        stack_frame1.plus(&declarations_from_pattern),
        IScoutResult::NormalResult(NormalResultS {
          expr: &*self.scout_arena.alloc(IExpressionSE::Let(LetSE {
            range: PostParser::eval_range(&file_coordinate, lett.range),
            rules: self.scout_arena.alloc_slice_from_vec(rule_builder),
            pattern: pattern_s,
            expr: source_expr_s,
          })),
        }),
        source_self_uses,
        source_child_uses,
      ))
    }
    
    IExpressionPE::Mutate(mutate) => {
      let (stack_frame1, source_expr_s, source_inner_self_uses, source_child_uses): (StackFrame<'s>, &'s IExpressionSE<'s>, VariableUses<'s>, VariableUses<'s>) = {
        let mut source_expr_lidb = lidb.child();
        // AFTERM: consider doing &mut StackFrame instead of clone, everywhere.
        self.scout_expression_and_coerce(
          stack_frame,
          &mut source_expr_lidb,
          mutate.source,
          LoadAsP::Use,
        )?
      };
      let (stack_frame2, destination_result_s, destination_self_uses, destination_child_uses): (StackFrame<'s>, IScoutResult<'s, 'p>, VariableUses<'s>, VariableUses<'s>) = {
        let mut destination_expr_lidb = lidb.child();
        self.scout_expression(
          stack_frame1,
          &mut destination_expr_lidb,
          mutate.mutatee,
        )?
      };
      let (mutate_expr_s, source_self_uses): (&'s IExpressionSE<'s>, VariableUses<'s>) = match destination_result_s {
        IScoutResult::LocalLookupResult(LocalLookupResultS { range, name }) => (
          &*self.scout_arena.alloc(IExpressionSE::LocalMutate(LocalMutateSE {
            range,
            name: name.clone(),
            expr: source_expr_s,
          })),
          source_inner_self_uses.mark_mutated(name),
        ),
        IScoutResult::OutsideLookupResult(OutsideLookupResultS { range, name, .. }) => {
          return Err(ICompileErrorS::CouldntFindVarToMutateS(CouldntFindVarToMutateS {
            range,
            name: name.as_str().to_string(),
          }));
        }
        IScoutResult::NormalResult(NormalResultS { expr: destination_expr_s }) => (
          &*self.scout_arena.alloc(IExpressionSE::ExprMutate(ExprMutateSE {
            range: destination_expr_s.range(),
            mutatee: destination_expr_s,
            expr: source_expr_s,
          })),
          source_inner_self_uses,
        ),
      };
      Ok((
        stack_frame2,
        IScoutResult::NormalResult(NormalResultS { expr: mutate_expr_s }),
        source_self_uses.then_merge(&destination_self_uses),
        source_child_uses.then_merge(&destination_child_uses),
      ))
    }
    
    IExpressionPE::Dot(dot) => {
      match dot.left {
        IExpressionPE::Lookup(LookupPE { name: IImpreciseNameP::LookupName(lookup_name), .. })
        if lookup_name.str() == self.keywords.self_
            && stack_frame
            .find_variable(&self.scout_arena.intern_imprecise_name(IImpreciseNameValS::CodeName(CodeNameS {
              name: self.keywords.self_,
            })))
            .is_none() =>
          {
            return Ok((
              stack_frame.clone(),
              IScoutResult::LocalLookupResult(LocalLookupResultS {
                range: PostParser::eval_range(&file_coordinate, lookup_name.range()),
                name: IVarNameS::ConstructingMemberName(self.scout_arena.intern_str(dot.member.str().as_str())),
              }),
              VariableUses::<'s>::empty(),
              VariableUses::<'s>::empty(),
            ));
          }
        _ => {}
      }
      let (stack_frame1, container_expr_s, self_uses, child_uses) = {
        let mut dot_left_lidb = lidb.child();
        let (stack_frame1, container_expr_s, self_uses, child_uses) = self.scout_expression_and_coerce(
          stack_frame.clone(),
          &mut dot_left_lidb,
          dot.left,
          LoadAsP::LoadAsBorrow,
        )?;
        (stack_frame1, container_expr_s, self_uses, child_uses)
      };
      Ok((
        stack_frame1,
        IScoutResult::NormalResult(NormalResultS {
          expr: &*self.scout_arena.alloc(IExpressionSE::Dot(DotSE {
            range: PostParser::eval_range(&file_coordinate, dot.range),
            left: container_expr_s,
            member: self.scout_arena.intern_str(dot.member.str().as_str()),
            borrow_container: true,
          })),
        }),
        self_uses,
        child_uses,
      ))
    }
    
    IExpressionPE::Not(not) => {
      let not_name = self.scout_arena.intern_imprecise_name(IImpreciseNameValS::CodeName(CodeNameS {
        name: self.keywords.not,
      }));
      let load_part = self.scout_arena.alloc(LoadPartSE {
        name: not_name,
        explicit_template_args: &[],
      });
      let callable_expr_s = &*self.scout_arena.alloc(IExpressionSE::OverloadSet(OverloadSetSE {
        lookup: OutsideLoadSE {
          range: PostParser::eval_range(&file_coordinate, not.range),
          rules: &[],
          parts: self.scout_arena.alloc_slice_copy(&[&*load_part]),
        },
      }));
      let (stack_frame1, inner_expr_s, inner_self_uses, inner_child_uses): (StackFrame<'s>, &'s IExpressionSE<'s>, VariableUses<'s>, VariableUses<'s>) = {
        let mut inner_lidb = lidb.child();
        self.scout_expression_and_coerce(stack_frame, &mut inner_lidb, not.inner, LoadAsP::Use)?
      };
      let result = IScoutResult::NormalResult(NormalResultS {
        expr: &*self.scout_arena.alloc(IExpressionSE::FunctionCall(FunctionCallSE {
          range: PostParser::eval_range(&file_coordinate, not.range),
          location: lidb.child().consume_in_arena(self.scout_arena),
          callable_expr: callable_expr_s,
          arg_exprs: self.scout_arena.alloc_slice_from_vec(vec![inner_expr_s]),
        })),
      });
      Ok((stack_frame1, result, inner_self_uses, inner_child_uses))
    }
    IExpressionPE::BraceCall(brace_call) => {
      let load_subject_as = match brace_call.subject_expr {
        IExpressionPE::SubExpression(_) => LoadAsP::Use,
        _ => LoadAsP::LoadAsBorrow,
      };
      assert!(brace_call.arg_exprs.len() == 1);
      let arg_pe = brace_call.arg_exprs[0];
      let (stack_frame1, callable_se, callable_self_uses, callable_child_uses) =
        self.scout_expression_and_coerce(stack_frame, &mut lidb.child(), brace_call.subject_expr, load_subject_as)?;
      let (stack_frame2, arg_se, arg_self_uses, arg_child_uses) =
        self.scout_expression_and_coerce(stack_frame1, &mut lidb.child(), arg_pe, LoadAsP::Use)?;
      let result_se = IExpressionSE::Index(IndexSE {
        range: PostParser::eval_range(&file_coordinate, brace_call.range),
        left: callable_se,
        index_expr: arg_se,
      });
      Ok((stack_frame2, IScoutResult::NormalResult(NormalResultS { expr: self.scout_arena.alloc(result_se) }), callable_self_uses.then_merge(&arg_self_uses), callable_child_uses.then_merge(&arg_child_uses)))
    }
    IExpressionPE::Destruct(destruct_pe) => {
      let (stack_frame1, inner1, inner_self_uses, inner_child_uses) =
        self.scout_expression_and_coerce(stack_frame, &mut lidb.child(), destruct_pe.inner, LoadAsP::Use)?;
      let result_se = IExpressionSE::Destruct(DestructSE {
        range: PostParser::eval_range(&file_coordinate, destruct_pe.range),
        inner: inner1,
      });
      Ok((stack_frame1, IScoutResult::NormalResult(NormalResultS { expr: self.scout_arena.alloc(result_se) }), inner_self_uses, inner_child_uses))
    }
    IExpressionPE::And(and_pe) => {
      let right_range = PostParser::eval_range(&file_coordinate, and_pe.right.range);
      let end_range = RangeS { begin: right_range.end, end: right_range.end };
      let (stack_frame_z, if_se, self_uses, child_uses) = Self::new_if(
        stack_frame, lidb, and_pe.range,
        |sf1, lidb| {
          self.scout_expression_and_coerce(sf1, lidb, and_pe.left, LoadAsP::Use)
        },
        |sf2, lidb| {
          let (then_se, then_uses, then_child_uses) =
            self.scout_impure_block(sf2.clone(), &mut lidb.child(), PostParser::<'s, 'p, '_>::no_declarations(), and_pe.right)?;
          Ok((sf2, then_se, then_uses, then_child_uses))
        },
        |sf3, _lidb| {
          let false_const = self.scout_arena.alloc(IExpressionSE::ConstantBool(ConstantBoolSE {
            range: end_range,
            value: false,
          }));
          let else_se = self.scout_arena.alloc(BlockSE {
            range: end_range,
            locals: &[],
            expr: false_const,
          });
          Ok((sf3, &*else_se, VariableUses::<'s>::empty(), VariableUses::<'s>::empty()))
        },
      )?;
      let if_alloc = self.scout_arena.alloc(IExpressionSE::If(if_se));
      Ok((stack_frame_z, IScoutResult::NormalResult(NormalResultS { expr: if_alloc }), self_uses, child_uses))
    }
    IExpressionPE::Or(or_pe) => {
      let right_range = PostParser::eval_range(&file_coordinate, or_pe.right.range);
      let end_range = RangeS { begin: right_range.end, end: right_range.end };
      let (stack_frame_z, if_se, self_uses, child_uses) = Self::new_if(
        stack_frame, lidb, or_pe.range,
        |sf1, lidb| {
          self.scout_expression_and_coerce(sf1, lidb, or_pe.left, LoadAsP::Use)
        },
        |sf2, _lidb| {
          let true_const = self.scout_arena.alloc(IExpressionSE::ConstantBool(ConstantBoolSE {
            range: end_range,
            value: true,
          }));
          let else_se = self.scout_arena.alloc(BlockSE {
            range: end_range,
            locals: &[],
            expr: true_const,
          });
          Ok((sf2, &*else_se, VariableUses::<'s>::empty(), VariableUses::<'s>::empty()))
        },
        |sf3, lidb| {
          let (then_se, then_uses, then_child_uses) =
            self.scout_impure_block(sf3.clone(), &mut lidb.child(), PostParser::<'s, 'p, '_>::no_declarations(), or_pe.right)?;
          Ok((sf3, then_se, then_uses, then_child_uses))
        },
      )?;
      let if_alloc = self.scout_arena.alloc(IExpressionSE::If(if_se));
      Ok((stack_frame_z, IScoutResult::NormalResult(NormalResultS { expr: if_alloc }), self_uses, child_uses))
    }
    IExpressionPE::Tuple(tuple_pe) => {
      let (stack_frame1, elements1, self_uses, child_uses) =
        self.scout_elements_as_expressions(stack_frame, &mut lidb.child(), tuple_pe.elements)?;
      let result_se = IExpressionSE::Tuple(TupleSE {
        range: PostParser::eval_range(&file_coordinate, tuple_pe.range),
        elements: self.scout_arena.alloc_slice_from_vec(elements1),
      });
      Ok((stack_frame1, IScoutResult::NormalResult(NormalResultS { expr: self.scout_arena.alloc(result_se) }), self_uses, child_uses))
    }
    IExpressionPE::StrInterpolate(str_interp_pe) => {
      let (stack_frame1, parts_se, parts_self_uses, parts_child_uses) =
        self.scout_elements_as_expressions(stack_frame, &mut lidb.child(), str_interp_pe.parts)?;

      let range_s = PostParser::eval_range(&file_coordinate, str_interp_pe.range);
      let starting_expr: &'s IExpressionSE<'s> =
        self.scout_arena.alloc(IExpressionSE::ConstantStr(ConstantStrSE {
          range: RangeS { begin: range_s.begin, end: range_s.begin },
          value: self.scout_arena.intern_str(""),
        }));
      let added_expr = parts_se.iter().fold(starting_expr, |prev_expr, part_se| {
        let add_call_range = RangeS {
          begin: prev_expr.range().end,
          end: part_se.range().begin,
        };
        let plus_name = self.scout_arena.intern_imprecise_name(IImpreciseNameValS::CodeName(CodeNameS {
          name: self.keywords.plus,
        }));
        let load_part = self.scout_arena.alloc(LoadPartSE {
          name: plus_name,
          explicit_template_args: &[],
        });
        let callable_expr = self.scout_arena.alloc(IExpressionSE::OverloadSet(OverloadSetSE {
          lookup: OutsideLoadSE {
            range: add_call_range,
            rules: &[],
            parts: self.scout_arena.alloc_slice_copy(&[&*load_part]),
          },
        }));
        let args = self.scout_arena.alloc_slice_from_vec(vec![prev_expr, *part_se]);
        self.scout_arena.alloc(IExpressionSE::FunctionCall(FunctionCallSE {
          range: add_call_range,
          location: lidb.child().consume_in_arena(self.scout_arena),
          callable_expr,
          arg_exprs: args,
        }))
      });
      Ok((stack_frame1, IScoutResult::NormalResult(NormalResultS { expr: added_expr }), parts_self_uses, parts_child_uses))
    }
    IExpressionPE::Break(break_pe) => {
      let range_s = PostParser::eval_range(&file_coordinate, break_pe.range);
      let expr = self.scout_arena.alloc(IExpressionSE::Break(BreakSE { range: range_s }));
      Ok((stack_frame, IScoutResult::NormalResult(NormalResultS { expr }), VariableUses::empty(), VariableUses::empty()))
    }
    IExpressionPE::Unlet(unlet_pe) => {
      let range_s = PostParser::eval_range(&file_coordinate, unlet_pe.range);
      let imprecise_name_s = translate_imprecise_name(self.scout_arena, &file_coordinate, &unlet_pe.name);
      let var_name_s = match self.find_local(&stack_frame, range_s, &imprecise_name_s) {
        Some(LocalLookupResultS { range: _, name }) => name,
        None => {
          return Err(ICompileErrorS::RangedInternalErrorS(RangedInternalErrorS {
            range: range_s,
            message: format!("Can't unlet local: {:?}", unlet_pe.name),
          }));
        }
      };
      let result = self.scout_arena.alloc(IExpressionSE::Unlet(UnletSE { range: range_s, name: var_name_s }));
      Ok((stack_frame, IScoutResult::NormalResult(NormalResultS { expr: result }), VariableUses::empty().mark_moved(var_name_s), VariableUses::empty()))
    }
    IExpressionPE::Range(range_pe) => {
      let range_name = self.scout_arena.intern_imprecise_name(IImpreciseNameValS::CodeName(CodeNameS {
        name: self.keywords.range,
      }));
      let load_part = self.scout_arena.alloc(LoadPartSE {
        name: range_name,
        explicit_template_args: &[],
      });
      let callable_se = &*self.scout_arena.alloc(IExpressionSE::OverloadSet(OverloadSetSE {
        lookup: OutsideLoadSE {
          range: PostParser::eval_range(&file_coordinate, range_pe.range),
          rules: &[],
          parts: self.scout_arena.alloc_slice_copy(&[&*load_part]),
        },
      }));
      let load_begin_as = match range_pe.from_expr {
        IExpressionPE::SubExpression(_) => LoadAsP::Use,
        _ => LoadAsP::LoadAsBorrow,
      };
      let (stack_frame1, begin_se, begin_self_uses, begin_child_uses) =
        self.scout_expression_and_coerce(stack_frame, &mut lidb.child(), range_pe.from_expr, load_begin_as)?;
      let load_end_as = match range_pe.to_expr {
        IExpressionPE::SubExpression(_) => LoadAsP::Use,
        _ => LoadAsP::LoadAsBorrow,
      };
      let (stack_frame2, end_se, end_self_uses, end_child_uses) =
        self.scout_expression_and_coerce(stack_frame1, &mut lidb.child(), range_pe.to_expr, load_end_as)?;
      let result_se = &*self.scout_arena.alloc(IExpressionSE::FunctionCall(FunctionCallSE {
        range: PostParser::eval_range(&file_coordinate, range_pe.range),
        location: lidb.child().consume_in_arena(self.scout_arena),
        callable_expr: callable_se,
        arg_exprs: self.scout_arena.alloc_slice_from_vec(vec![begin_se, end_se]),
      }));
      Ok((stack_frame2, IScoutResult::NormalResult(NormalResultS { expr: result_se }), begin_self_uses.then_merge(&end_self_uses), begin_child_uses.then_merge(&end_child_uses)))
    }
    _ => panic!(
      "POSTPARSER_SCOUT_EXPRESSION_NOT_YET_IMPLEMENTED: {:?}",
      expression
    ),
  }
}
  
pub(crate) fn new_if<FCond, FThen, FElse>(
  stack_frame0: StackFrame<'s>,
  lidb: &mut LocationInDenizenBuilder,
  range: RangeL,
  make_condition: FCond,
  make_then: FThen,
  make_else: FElse,
) -> Result<(StackFrame<'s>, IfSE<'s>, VariableUses<'s>, VariableUses<'s>), ICompileErrorS<'s>>
where
  FCond: FnOnce(
    StackFrame<'s>,
    &mut LocationInDenizenBuilder,
  ) -> Result<
    (
      StackFrame<'s>,
      &'s IExpressionSE<'s>,
      VariableUses<'s>,
      VariableUses<'s>,
    ),
    ICompileErrorS<'s>,
  >,
  FThen: FnOnce(
    StackFrame<'s>,
    &mut LocationInDenizenBuilder,
  ) -> Result<(StackFrame<'s>, &'s BlockSE<'s>, VariableUses<'s>, VariableUses<'s>), ICompileErrorS<'s>>,
  FElse: FnOnce(
    StackFrame<'s>,
    &mut LocationInDenizenBuilder,
  ) -> Result<(StackFrame<'s>, &'s BlockSE<'s>, VariableUses<'s>, VariableUses<'s>), ICompileErrorS<'s>>,
{
  let file = stack_frame0.file;
  let (stack_frame1, cond_se, cond_uses, cond_child_uses): (StackFrame<'s>, &'s IExpressionSE<'s>, VariableUses<'s>, VariableUses<'s>) = make_condition(stack_frame0, &mut lidb.child())?;
  let (stack_frame2, then_se, then_uses, then_child_uses): (StackFrame<'s>, &'s BlockSE<'s>, VariableUses<'s>, VariableUses<'s>) = make_then(stack_frame1, &mut lidb.child())?;
  let (stack_frame3, else_se, else_uses, else_child_uses): (StackFrame<'s>, &'s BlockSE<'s>, VariableUses<'s>, VariableUses<'s>) = make_else(stack_frame2, &mut lidb.child())?;

  let self_case_uses = then_uses.branch_merge(&else_uses);
  let self_uses = cond_uses.then_merge(&self_case_uses);
  let child_case_uses = then_child_uses.branch_merge(&else_child_uses);
  let child_uses = cond_child_uses.then_merge(&child_case_uses);

  let if_se = IfSE {
    range: PostParser::eval_range(file, range),
    condition: cond_se,
    then_body: then_se,
    else_body: else_se,
  };
  Ok((stack_frame3, if_se, self_uses, child_uses))
}

pub(crate) fn translate_maybe_template_args(
    &self,
    parent_env: &IEnvironmentS<'s>,
    lidb: &mut LocationInDenizenBuilder,
    rule_builder: &mut Vec<IRulexSR<'s>>,
    context_region: IRuneS<'s>,
    maybe_template_args: Option<&'p [&'p ITemplexPT<'p>]>,
  ) -> &'s [RuneUsage<'s>] {
    match maybe_template_args {
      None => &[],
      Some(template_args) => {
        let runes: Vec<RuneUsage<'s>> = template_args.iter().map(|template_arg| {
          let mut child_lidb = lidb.child();
          translate_templex(
            self.scout_arena,
            self.keywords,
            parent_env.clone(),
            &mut child_lidb,
            rule_builder,
            context_region.clone(),
            template_arg,
          )
        }).collect();
        self.scout_arena.alloc_slice_from_vec(runes)
      }
    }
  }
// (audit-trail for translateMaybeTemplateArgs lives at canonical position in scoutExpression's
//  audit-trail block above; no duplicate adjacent block needed.)

// If we load an immutable with targetOwnershipIfLookupResult = Own or Borrow, it will just be Share.
pub(crate) fn scout_expression_and_coerce(
    &self,
    stack_frame: StackFrame<'s>,
    lidb: &mut LocationInDenizenBuilder,
    expression_p: &'p IExpressionPE<'p>,
    load_as_p: LoadAsP,
  ) -> Result<(StackFrame<'s>, &'s IExpressionSE<'s>, VariableUses<'s>, VariableUses<'s>), ICompileErrorS<'s>>
  {
    let mut expression_lidb = lidb.child();
    let (next_stack_frame, first_result_s, first_inner_self_uses, first_child_uses) = self.scout_expression(
      stack_frame,
      &mut expression_lidb,
      expression_p,
    )?;
    let mut coerce_lidb = lidb.child();
    let (next_stack_frame, first_expr_s, first_self_uses) =
      self.coerce(next_stack_frame, first_result_s, &mut coerce_lidb, first_inner_self_uses, load_as_p)?;
    Ok((next_stack_frame, first_expr_s, first_self_uses, first_child_uses))
  }

pub(crate) fn coerce(
    &self,
    stack_frame: StackFrame<'s>,
    first_result: IScoutResult<'s, 'p>,
    _lidb: &mut LocationInDenizenBuilder,
    self_uses_before: VariableUses<'s>,
    load_as_p: LoadAsP,
  ) -> Result<(StackFrame<'s>, &'s IExpressionSE<'s>, VariableUses<'s>), ICompileErrorS<'s>> {
    match first_result {
      IScoutResult::LocalLookupResult(LocalLookupResultS { range, name }) => {
        let self_uses_after = match load_as_p {
          LoadAsP::LoadAsBorrow => self_uses_before.mark_borrowed(name.clone()),
          LoadAsP::LoadAsWeak => self_uses_before.mark_borrowed(name.clone()),
          LoadAsP::Use | LoadAsP::Move => self_uses_before.mark_moved(name.clone()),
        };
        Ok((
          stack_frame,
          &*self.scout_arena.alloc(IExpressionSE::LocalLoad(LocalLoadSE {
            range,
            name,
            target_ownership: load_as_p,
          })),
          self_uses_after,
        ))
      }
      IScoutResult::OutsideLookupResult(OutsideLookupResultS { range, name: _, template_args: _ }) => {
        // When we have globals, here's where we'd check that the user declared they're accessing a global.
        // But since we don't have globals yet, we just throw an error...
        // V: open question... why don't we coerce to overload set here? Is that done somewhere else?
        Err(ICompileErrorS::CouldntFindVarToMutateS(CouldntFindVarToMutateS {
          range,
          name: "a".to_string(),
        }))
      }
      IScoutResult::NormalResult(NormalResultS { expr: inner_expr_s }) => {
        match load_as_p {
          LoadAsP::Use => Ok((stack_frame, inner_expr_s, self_uses_before)),
          _ => Ok((
            stack_frame,
            &*self.scout_arena.alloc(IExpressionSE::Ownershipped(OwnershippedSE {
              range: inner_expr_s.range(),
              inner_expr: inner_expr_s,
              target_ownership: load_as_p,
            })),
            self_uses_before,
          )),
        }
      }
    }
  }

pub(crate) fn scout_elements_as_expressions(
    &self,
    initial_stack_frame: StackFrame<'s>,
    lidb: &mut LocationInDenizenBuilder,
    exprs_p: &'p [&'p IExpressionPE<'p>],
  ) -> Result<(StackFrame<'s>, Vec<&'s IExpressionSE<'s>>, VariableUses<'s>, VariableUses<'s>), ICompileErrorS<'s>>
  {
    let mut self_uses = VariableUses::<'s>::empty();
    let mut child_uses = VariableUses::<'s>::empty();
    let mut exprs_s = Vec::new();
    let mut stack_frame = initial_stack_frame;
    for expr_p in exprs_p {
      let mut expr_lidb = lidb.child();
      let (next_stack_frame, expr_s, first_self_uses, first_child_uses) =
        self.scout_expression_and_coerce(stack_frame, &mut expr_lidb, expr_p, LoadAsP::Use)?;
      stack_frame = next_stack_frame;
      self_uses = self_uses.then_merge(&first_self_uses);
      child_uses = child_uses.then_merge(&first_child_uses);
      exprs_s.push(expr_s);
    }
    Ok((stack_frame, exprs_s, self_uses, child_uses))
  }

}

fn flatten_expressions<'s>(
  _expr: &IExpressionSE<'s>,
) -> Vec<&'s IExpressionSE<'s>> {
  panic!("Unimplemented flatten_expressions");
}

