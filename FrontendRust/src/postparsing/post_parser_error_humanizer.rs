use crate::postparsing::names::{INameS, IVarNameS};
use crate::postparsing::post_parser::ICompileErrorS;
use crate::utils::range::{CodeLocationS, RangeS};
use crate::postparsing::names::IImpreciseNameS;
use crate::postparsing::names::IRuneS;
use crate::postparsing::rules::rules::IRulexSR;
use crate::postparsing::itemplatatype::ITemplataType;
use crate::postparsing::rules::rules::ILiteralSL;
use crate::parsing::ast::MutabilityP;
use crate::parsing::ast::VariabilityP;
use crate::parsing::ast::OwnershipP;
use crate::postparsing::rules::rules::RuneUsage;
use crate::postparsing::rune_type_solver::IRuneTypeRuleError;

pub fn humanize<'s, HP, LB, LRC, LC>(
  humanize_pos: HP,
  _lines_between: LB,
  _line_range_containing: LRC,
  line_containing: LC,
  err: &'s ICompileErrorS<'s>,
) -> String
where
  HP: Fn(&CodeLocationS<'s>) -> String,
  LB: Fn(&CodeLocationS<'s>, &CodeLocationS<'s>) -> Vec<RangeS<'s>>,
  LRC: Fn(&CodeLocationS<'s>) -> RangeS<'s>,
  LC: Fn(&CodeLocationS<'s>) -> String,
{
  let error_str_body = match err {
    ICompileErrorS::VariableNameAlreadyExists(x) => {
      format!(
        "Local named {} already exists!\n(If you meant to modify the variable, use the `set` keyword beforehand.)",
        humanize_var_name(x.name.clone())
      )
    }
    ICompileErrorS::InterfaceMethodNeedsSelf(_) => {
      "Interface's method needs a virtual param of interface's type!".to_string()
    }
    ICompileErrorS::VirtualAndAbstractGoTogether(_) => {
      "Abstract function needs a `virtual` parameter.".to_string()
    }
    ICompileErrorS::ExternHasBodyS(_) => "Extern function can't have a body too.".to_string(),
    ICompileErrorS::IdentifyingRunesIncompleteS(_) => {
      "Not enough identifying runes.".to_string()
    }
    _ => panic!("Unimplemented humanize branch for {:?}", err),
  };
  let range = err.range();
  let pos_str = humanize_pos(&range.begin);
  let next_stuff = line_containing(&range.begin);
  let error_id = "S";
  format!("{} error {}: {}\n{}\n", pos_str, error_id, error_str_body, next_stuff)
}

pub fn humanize_rune_type_error<'s>(
  _code_map: &dyn Fn(CodeLocationS<'s>) -> String,
  error: &IRuneTypeRuleError<'s>,
) -> String {
  match error {
    IRuneTypeRuleError::FoundTemplataDidntMatchExpectedType(_) => panic!("implement: humanize_rune_type_error FoundTemplataDidntMatchExpectedType"),
    IRuneTypeRuleError::CouldntFindType(e) => {
      format!("Couldn't find anything with the name '{}'", humanize_imprecise_name(e.name))
    }
    IRuneTypeRuleError::NotEnoughArgumentsForGenericCall(_) => panic!("implement: humanize_rune_type_error NotEnoughArgumentsForGenericCall"),
    _ => panic!("implement: humanize_rune_type_error other"),
  }
}

fn humanize_identifiability_rule_errorr<'s>(
  _error: &(),
) -> String {
  panic!("Unimplemented humanize_identifiability_rule_errorr");
}

fn humanize_var_name<'s>(var_name: IVarNameS<'s>) -> String {
  match var_name {
    IVarNameS::CodeVarName(n) => n.as_str().to_string(),
    IVarNameS::ClosureParamName(_) => "(closure)".to_string(),
    _ => panic!("Unimplemented humanize_var_name branch for IVarNameS"),
  }
}

fn humanize_name<'s>(name: INameS<'s>) -> String {
  match name {
    INameS::VarName(var_name) => humanize_var_name((*var_name).clone()),
    _ => panic!("Unimplemented humanize_name branch for INameS"),
  }
}

pub fn humanize_imprecise_name<'s>(
  name: IImpreciseNameS<'s>,
) -> String {
  match name {
    IImpreciseNameS::ArbitraryName(_) => "_arby".to_string(),
    IImpreciseNameS::SelfName(_) => "_Self".to_string(),
    IImpreciseNameS::CodeName(n) => n.name.0.to_string(),
    IImpreciseNameS::RuneName(rune) => humanize_rune(rune.rune),
    IImpreciseNameS::AnonymousSubstructTemplateImpreciseName(_) => panic!("implement: humanize_imprecise_name AnonymousSubstructTemplateImpreciseName"),
    IImpreciseNameS::LambdaStructImpreciseName(_) => panic!("implement: humanize_imprecise_name LambdaStructImpreciseName"),
    IImpreciseNameS::LambdaImpreciseName(_) => "_Lam".to_string(),
    _ => panic!("implement: humanize_imprecise_name other"),
  }
}

pub fn humanize_rune<'s>(
  rune: IRuneS<'s>,
) -> String {
  match rune {
    IRuneS::ImplicitRune(r) => "_".to_string() + &r.lid.path.iter().map(|p| p.to_string()).collect::<Vec<_>>().join(""),
    IRuneS::MagicParamRune(_) => panic!("implement: humanize_rune MagicParamRune"),
    IRuneS::CodeRune(r) => r.name.0.to_string(),
    IRuneS::ArgumentRune(r) => {
      format!("(arg {})", r.arg_index)
    }
    IRuneS::SelfKindRune(_) => {
      panic!("implement: humanize_rune SelfKindRune");
      // "(self kind)"
    }
    IRuneS::SelfOwnershipRune(_) => {
      panic!("implement: humanize_rune SelfOwnershipRune");
      // "(self ownership)"
    }
    IRuneS::SelfKindTemplateRune(_) => {
      panic!("implement: humanize_rune SelfKindTemplateRune");
      // "(self kind template)"
    }
    IRuneS::PatternInputRune(_) => {
      panic!("implement: humanize_rune PatternInputRune");
      // "(pattern input " + codeLoc + ")"
    }
    IRuneS::SelfRune(_) => {
      panic!("implement: humanize_rune SelfRune");
      // "(self)"
    }
    IRuneS::SelfCoordRune(_) => {
      panic!("implement: humanize_rune SelfCoordRune");
      // "(self ref)"
    }
    IRuneS::ReturnRune(_) => {
      panic!("implement: humanize_rune ReturnRune");
      // "(ret)"
    }
    IRuneS::AnonymousSubstructParentInterfaceTemplateRune(_) => {
      panic!("implement: humanize_rune AnonymousSubstructParentInterfaceTemplateRune");
      // "(anon sub parent interface template)"
    }
    IRuneS::ImplDropVoidRune(_) => {
      panic!("implement: humanize_rune ImplDropVoidRune");
      // "(impl drop void)"
    }
    IRuneS::ImplDropCoordRune(_) => {
      panic!("implement: humanize_rune ImplDropCoordRune");
      // "(impl drop coord)"
    }
    IRuneS::FreeOverrideInterfaceRune(_) => {
      panic!("implement: humanize_rune FreeOverrideInterfaceRune");
      // "(freeing interface)"
    }
    IRuneS::FreeOverrideStructRune(_) => {
      panic!("implement: humanize_rune FreeOverrideStructRune");
      // "(freeing struct)"
    }
    IRuneS::AnonymousSubstructKindRune(_) => {
      panic!("implement: humanize_rune AnonymousSubstructKindRune");
      // "(anon substruct kind)"
    }
    IRuneS::AnonymousSubstructCoordRune(_) => {
      panic!("implement: humanize_rune AnonymousSubstructCoordRune");
      // "(anon substruct ref)"
    }
    IRuneS::AnonymousSubstructTemplateRune(_) => {
      panic!("implement: humanize_rune AnonymousSubstructTemplateRune");
      // "(anon substruct template)"
    }
    IRuneS::AnonymousSubstructParentInterfaceKindRune(_) => {
      panic!("implement: humanize_rune AnonymousSubstructParentInterfaceKindRune");
      // "(anon sub parent kind)"
    }
    IRuneS::AnonymousSubstructParentInterfaceCoordRune(_) => {
      panic!("implement: humanize_rune AnonymousSubstructParentInterfaceCoordRune");
      // "(anon sub parent ref)"
    }
    IRuneS::StructNameRune(_) => {
      panic!("implement: humanize_rune StructNameRune");
      // humanizeName(inner)
    }
    IRuneS::FreeOverrideStructTemplateRune(_) => {
      panic!("implement: humanize_rune FreeOverrideStructTemplateRune");
      // "(free override template)"
    }
    IRuneS::FunctorPrototypeRuneName(_) => {
      panic!("implement: humanize_rune FunctorPrototypeRuneName");
      // "(functor prototype)"
    }
    IRuneS::MacroSelfKindRune(_) => {
      panic!("implement: humanize_rune MacroSelfKindRune");
      // "_MSelfK"
    }
    IRuneS::MacroSelfCoordRune(_) => {
      panic!("implement: humanize_rune MacroSelfCoordRune");
      // "_MSelf"
    }
    IRuneS::MacroVoidKindRune(_) => {
      panic!("implement: humanize_rune MacroVoidKindRune");
      // "_MVoidK"
    }
    IRuneS::MacroVoidCoordRune(_) => {
      panic!("implement: humanize_rune MacroVoidCoordRune");
      // "_MVoid"
    }
    IRuneS::MacroSelfKindTemplateRune(_) => {
      panic!("implement: humanize_rune MacroSelfKindTemplateRune");
      // "_MSelfKT"
    }
    IRuneS::AnonymousSubstructMemberRune(_) => {
      panic!("implement: humanize_rune AnonymousSubstructMemberRune");
      // "$" + humanizeName(interface) + ".anon." + humanizeName(method) + ".functor"
    }
    IRuneS::AnonymousSubstructFunctionBoundParamsListRune(_) => {
      panic!("implement: humanize_rune AnonymousSubstructFunctionBoundParamsListRune");
      // "$" + humanizeName(interface) + ".anon." + humanizeName(method) + ".params"
    }
    IRuneS::AnonymousSubstructFunctionBoundPrototypeRune(_) => {
      panic!("implement: humanize_rune AnonymousSubstructFunctionBoundPrototypeRune");
      // "$" + humanizeName(interface) + ".anon." + humanizeName(method) + ".proto"
    }
    IRuneS::AnonymousSubstructFunctionInterfaceTemplateRune(_) => {
      panic!("implement: humanize_rune AnonymousSubstructFunctionInterfaceTemplateRune");
      // "$" + humanizeName(interface) + ".anon." + humanizeName(method) + ".itemplate"
    }
    IRuneS::AnonymousSubstructFunctionInterfaceKindRune(_) => {
      panic!("implement: humanize_rune AnonymousSubstructFunctionInterfaceKindRune");
      // "$" + humanizeName(interface) + ".anon." + humanizeName(method) + ".ikind"
    }
    IRuneS::AnonymousSubstructDropBoundParamsListRune(_) => {
      panic!("implement: humanize_rune AnonymousSubstructDropBoundParamsListRune");
      // "$" + humanizeName(interface) + ".anon.drop.params"
    }
    IRuneS::AnonymousSubstructDropBoundPrototypeRune(_) => {
      panic!("implement: humanize_rune AnonymousSubstructDropBoundPrototypeRune");
      // "$" + humanizeName(interface) + ".anon.drop.proto"
    }
    IRuneS::AnonymousSubstructMethodInheritedRune(_) => {
      panic!("implement: humanize_rune AnonymousSubstructMethodInheritedRune");
      // "$" + humanizeName(interface) + ".anon." + humanizeName(method) + ":" + humanizeRune(inner)
    }
    IRuneS::AnonymousSubstructMethodSelfOwnCoordRune(_) => {
      panic!("implement: humanize_rune AnonymousSubstructMethodSelfOwnCoordRune");
      // "$" + humanizeName(interface) + ".anon." + humanizeName(method) + ".ownself"
    }
    IRuneS::AnonymousSubstructMethodSelfBorrowCoordRune(_) => {
      panic!("implement: humanize_rune AnonymousSubstructMethodSelfBorrowCoordRune");
      // "$" + humanizeName(interface) + ".anon." + humanizeName(method) + ".borrowself"
    }
    IRuneS::DenizenDefaultRegionRune(_) => {
      panic!("implement: humanize_rune DenizenDefaultRegionRune");
      // humanizeName(denizenName) + "'"
    }
    IRuneS::ExternDefaultRegionRune(_) => {
      panic!("implement: humanize_rune ExternDefaultRegionRune");
      // humanizeName(denizenName) + "'"
    }
    IRuneS::AnonymousSubstructVoidKindRune(_) => {
      panic!("implement: humanize_rune AnonymousSubstructVoidKindRune");
      // "anon.void.kind"
    }
    IRuneS::AnonymousSubstructVoidCoordRune(_) => {
      panic!("implement: humanize_rune AnonymousSubstructVoidCoordRune");
      // "anon.void"
    }
    IRuneS::ImplicitCoercionOwnershipRune(_) => {
      panic!("implement: humanize_rune ImplicitCoercionOwnershipRune");
      // humanizeRune(inner) + ".own"
    }
    IRuneS::ImplicitCoercionKindRune(r) => {
      format!("{}.kind", humanize_rune(r.original_coord_rune))
    }
    IRuneS::ImplicitCoercionTemplateRune(r) => {
      format!("{}.gen", humanize_rune(r.original_kind_rune))
    }
    IRuneS::ImplicitRegionRune(_) => {
      panic!("implement: humanize_rune ImplicitRegionRune");
      // humanizeRune(originalRune) + ".region"
    }
    IRuneS::CallRegionRune(_) => {
      panic!("implement: humanize_rune CallRegionRune");
      // "_" + lid.path.mkString("") + ".pcall"
    }
    IRuneS::CaseRuneFromImpl(_) => {
      panic!("implement: humanize_rune CaseRuneFromImpl");
      // "case:" + humanizeRune(innerRune)
    }
    IRuneS::DispatcherRuneFromImpl(_) => {
      panic!("implement: humanize_rune DispatcherRuneFromImpl");
      // "disimpl:" + humanizeRune(innerRune)
    }
    IRuneS::PureBlockRegionRune(_) => panic!("implement: humanize_rune PureBlockRegionRune"),
    IRuneS::CallPureMergeRegionRune(_) => panic!("implement: humanize_rune CallPureMergeRegionRune"),
    IRuneS::ReachablePrototypeRune(_) => panic!("implement: humanize_rune ReachablePrototypeRune"),
    IRuneS::MemberRune(_) => panic!("implement: humanize_rune MemberRune"),
    IRuneS::LocalDefaultRegionRune(_) => panic!("implement: humanize_rune LocalDefaultRegionRune"),
    IRuneS::ExportDefaultRegionRune(_) => panic!("implement: humanize_rune ExportDefaultRegionRune"),
    IRuneS::ArraySizeImplicitRune(_) => panic!("implement: humanize_rune ArraySizeImplicitRune"),
    IRuneS::ArrayMutabilityImplicitRune(_) => panic!("implement: humanize_rune ArrayMutabilityImplicitRune"),
    IRuneS::ArrayVariabilityImplicitRune(_) => panic!("implement: humanize_rune ArrayVariabilityImplicitRune"),
    IRuneS::InterfaceNameRune(_) => panic!("implement: humanize_rune InterfaceNameRune"),
    IRuneS::LetImplicitRune(_) => panic!("implement: humanize_rune LetImplicitRune"),
    IRuneS::ExplicitTemplateArgRune(_) => panic!("implement: humanize_rune ExplicitTemplateArgRune"),
    IRuneS::FunctorParamRuneName(_) => panic!("implement: humanize_rune FunctorParamRuneName"),
    IRuneS::FunctorReturnRuneName(_) => panic!("implement: humanize_rune FunctorReturnRuneName"),
  }
}

pub fn humanize_templata_type(
  _tyype: &ITemplataType,
) -> String {
  panic!("Unimplemented humanize_templata_type");
}

pub fn humanize_rule<'s>(
  rule: &IRulexSR<'s>,
) -> String {
  match rule {
    IRulexSR::KindComponents(r) => {
      humanize_rune(r.kind_rune.rune) + " = Kind[" + &humanize_rune(r.mutability_rune.rune) + "]"
    }
    IRulexSR::CoordComponents(r) => {
      humanize_rune(r.result_rune.rune) + " = Ref[" + &humanize_rune(r.ownership_rune.rune) + ", " + &humanize_rune(r.kind_rune.rune) + "]"
    }
    IRulexSR::PrototypeComponents(_) => {
      panic!("implement: humanize_rule PrototypeComponents");
      // humanizeRune(resultRune.rune) + " = Prot[" + humanizeRune(paramsRune.rune) + ", " + humanizeRune(returnRune.rune) + "]"
    }
    IRulexSR::OneOf(_) => {
      panic!("implement: humanize_rule OneOf");
      // humanizeRune(resultRune.rune) + " = " + literals.map(_.toString).mkString(" | ")
    }
    IRulexSR::IsInterface(_) => {
      panic!("implement: humanize_rule IsInterface");
      // "isInterface(" + humanizeRune(resultRune.rune) + ")"
    }
    IRulexSR::IsStruct(_) => {
      panic!("implement: humanize_rule IsStruct");
      // "isStruct(" + humanizeRune(resultRune.rune) + ")"
    }
    IRulexSR::RefListCompoundMutability(_) => {
      panic!("implement: humanize_rule RefListCompoundMutability");
      // humanizeRune(resultRune.rune) + " = refListCompoundMutability(" + humanizeRune(coordListRune.rune) + ")"
    }
    IRulexSR::DefinitionCoordIsa(_) => {
      panic!("implement: humanize_rule DefinitionCoordIsa");
      // humanizeRune(resultRune.rune) + " = " + humanizeRune(subRune.rune) + " def-isa " + humanizeRune(superRune.rune)
    }
    IRulexSR::CallSiteCoordIsa(r) => {
      let result = r.result_rune.map(|x| humanize_rune(x.rune)).unwrap_or_else(|| "_".to_string());
      format!("{} = {} call-isa {}", result, humanize_rune(r.sub_rune.rune), humanize_rune(r.super_rune.rune))
    }
    IRulexSR::CoordSend(r) => {
      format!("{} -> {}", humanize_rune(r.sender_rune.rune), humanize_rune(r.receiver_rune.rune))
    }
    IRulexSR::CoerceToCoord(r) => {
      format!("coerceToCoord({}, {})", humanize_rune(r.coord_rune.rune), humanize_rune(r.kind_rune.rune))
    }
    IRulexSR::MaybeCoercingCall(r) => humanize_rune(r.result_rune.rune) + " = " + &humanize_rune(r.template_rune.rune) + "<" + &r.args.iter().map(|x| humanize_rune(x.rune)).collect::<Vec<_>>().join(", ") + ">",
    IRulexSR::MaybeCoercingLookup(r) => humanize_rune(r.rune.rune) + " = \"" + &humanize_imprecise_name(r.name) + "\"",
    IRulexSR::Call(r) => {
      let args_str = r.args.iter().map(|x| humanize_rune(x.rune)).collect::<Vec<_>>().join(", ");
      format!("{} = {}<{}>", humanize_rune(r.result_rune.rune), humanize_rune(r.template_rune.rune), args_str)
    }
    IRulexSR::Lookup(r) => {
      format!("{} = \"{}\"", humanize_rune(r.rune.rune), humanize_imprecise_name(r.name))
    }
    IRulexSR::Literal(r) => humanize_rune(r.rune.rune) + " = " + &humanize_literal(&r.literal),
    IRulexSR::Augment(r) => humanize_rune(r.result_rune.rune) + " = " + &r.ownership.map(humanize_ownership).unwrap_or_else(String::new) + &humanize_rune(r.inner_rune.rune),
    IRulexSR::Equals(r) => {
      format!("{} = {}", humanize_rune(r.left.rune), humanize_rune(r.right.rune))
    }
    IRulexSR::RuneParentEnvLookup(_) => {
      panic!("implement: humanize_rule RuneParentEnvLookup");
      // "inherit " + humanizeRune(rune.rune)
    }
    IRulexSR::Pack(r) => {
      let members_str = r.members.iter().map(|x| humanize_rune(x.rune)).collect::<Vec<_>>().join(", ");
      format!("{} = ({})", humanize_rune(r.result_rune.rune), members_str)
    }
    IRulexSR::Resolve(r) => {
      format!("{} = resolve-func {}({}){}",
        humanize_rune(r.result_rune.rune),
        r.name.0,
        humanize_rune(r.params_list_rune.rune),
        humanize_rune(r.return_rune.rune))
    }
    IRulexSR::CallSiteFunc(r) => {
      format!("{} = callsite-func {}({}){}",
        humanize_rune(r.prototype_rune.rune),
        r.name.0,
        humanize_rune(r.params_list_rune.rune),
        humanize_rune(r.return_rune.rune))
    }
    IRulexSR::DefinitionFunc(_) => {
      panic!("implement: humanize_rule DefinitionFunc");
      // humanizeRune(resultRune.rune) + " = definition-func " + name + "(" + humanizeRune(paramsListRune.rune) + ")" + humanizeRune(returnRune.rune)
    }
    other => panic!("vimpl humanize_rule: {:?}", other),
  }
}

fn humanize_literal(
  literal: &ILiteralSL,
) -> String {
  match literal {
    ILiteralSL::OwnershipLiteral(_) => panic!("Unimplemented: humanize_literal OwnershipLiteral"),
    ILiteralSL::MutabilityLiteral(x) => humanize_mutability(x.mutability),
    ILiteralSL::VariabilityLiteral(_) => panic!("Unimplemented: humanize_literal VariabilityLiteral"),
    ILiteralSL::IntLiteral(x) => x.value.to_string(),
    ILiteralSL::StringLiteral(_) => panic!("Unimplemented: humanize_literal StringLiteral"),
    other => panic!("vimpl humanize_literal: {:?}", other),
  }
}

fn humanize_mutability(
  p: MutabilityP,
) -> String {
  match p {
    MutabilityP::Mutable => "mut".to_string(),
    MutabilityP::Immutable => "imm".to_string(),
  }
}

fn humanize_variability(
  _p: VariabilityP,
) -> String {
  panic!("Unimplemented humanize_variability");
}

fn humanize_ownership(
  p: OwnershipP,
) -> String {
  match p {
    OwnershipP::Own => "^".to_string(),
    OwnershipP::Share => "@".to_string(),
    OwnershipP::Borrow => "&".to_string(),
    OwnershipP::Weak => "&&".to_string(),
    OwnershipP::Live => panic!("Unimplemented: humanize_ownership Live"),
  }
}

fn humanize_region<'s>(
  _r: &RuneUsage<'s>,
) -> String {
  panic!("Unimplemented humanize_region");
}
