
// # Don't Use Ellipses In Matches (DUEIM)
// By default, don't like using ellipses in matches. We prefer to use explicit matches.
// Only the human should use ellipses. This is because explicit matches are a signal of when we
// might need to consider extra code at the pattern site whenever we add a new field to the struct.

use bumpalo::Bump;
use crate::lexing::RangeL;
use crate::interner::StrI;
use crate::parse_arena::ParseArena;
use crate::keywords::Keywords;
use crate::parsing::ast::*;
use crate::parsing::tests::utils::compile;

fn collect_if<'p, T, F>(pred: &F, out: &mut Vec<T>, node: NodeRefP<'p>)
where
  F: Fn(NodeRefP<'p>) -> Option<T>,
{
  if let Some(value) = pred(node) {
    out.push(value);
  }
}

fn visit_denizen<'p, T, F>(pred: &F, out: &mut Vec<T>, denizen: &'p IDenizenP<'p>)
where
  F: Fn(NodeRefP<'p>) -> Option<T>,
{
  match denizen {
    IDenizenP::TopLevelFunction(function) => {
      visit_function(pred, out, function);
    }
    IDenizenP::TopLevelStruct(struct_) => {
      visit_struct(pred, out, struct_);
    }
    IDenizenP::TopLevelInterface(interface) => {
      visit_interface(pred, out, interface);
    }
    IDenizenP::TopLevelImpl(impl_) => {
      visit_impl(pred, out, impl_);
    }
    IDenizenP::TopLevelExportAs(export_as) => {
      visit_export_as(pred, out, export_as);
    }
    IDenizenP::TopLevelImport(import) => {
      visit_import(pred, out, import);
    }
  }
}

fn visit_struct<'p, T, F>(pred: &F, out: &mut Vec<T>, struct_: &'p StructP<'p>)
where
  F: Fn(NodeRefP<'p>) -> Option<T>,
{
  collect_if(pred, out, NodeRefP::Struct(struct_));
  let StructP {
    range: _range,
    name,
    attributes,
    mutability,
    identifying_runes,
    template_rules,
    maybe_default_region_rune,
    body_range: _body_range,
    members,
  } = struct_;
  visit_name(pred, out, name);
  for attribute in *attributes {
    visit_attribute(pred, out, attribute);
  }
  if let Some(mutability) = mutability {
    visit_templex(pred, out, mutability);
  }
  if let Some(identifying_runes) = identifying_runes {
    visit_generic_parameters(pred, out, identifying_runes);
  }
  if let Some(template_rules) = template_rules {
    visit_template_rules(pred, out, template_rules);
  }
  if let Some(maybe_default_region_rune) = maybe_default_region_rune {
    visit_region_rune(pred, out, maybe_default_region_rune);
  }
  visit_struct_members(pred, out, members);
}

fn visit_impl<'p, T, F>(pred: &F, out: &mut Vec<T>, impl_: &'p ImplP<'p>)
where
  F: Fn(NodeRefP<'p>) -> Option<T>,
{
  collect_if(pred, out, NodeRefP::Impl(impl_));
  let ImplP {
    range: _range,
    generic_params,
    template_rules,
    struct_: struuct,
    interface,
    attributes,
  } = impl_;
  if let Some(generic_params) = generic_params {
    visit_generic_parameters(pred, out, generic_params);
  }
  if let Some(template_rules) = template_rules {
    visit_template_rules(pred, out, template_rules);
  }
  if let Some(struuct) = struuct {
    visit_templex(pred, out, struuct);
  }
  visit_templex(pred, out, interface);
  for attribute in *attributes {
    visit_attribute(pred, out, attribute);
  }
}

fn visit_export_as<'p, T, F>(pred: &F, out: &mut Vec<T>, export_as: &'p ExportAsP<'p>)
where
  F: Fn(NodeRefP<'p>) -> Option<T>,
{
  collect_if(pred, out, NodeRefP::ExportAs(export_as));
  let ExportAsP {
    range: _range,
    struct_,
    exported_name,
  } = export_as;
  visit_templex(pred, out, struct_);
  visit_name(pred, out, exported_name);
}

fn visit_import<'p, T, F>(pred: &F, out: &mut Vec<T>, import: &'p ImportP<'p>)
where
  F: Fn(NodeRefP<'p>) -> Option<T>,
{
  collect_if(pred, out, NodeRefP::Import(import));
  let ImportP {
    range: _range,
    module_name,
    package_steps,
    importee_name,
  } = import;
  visit_name(pred, out, module_name);
  for step in *package_steps {
    visit_name(pred, out, step);
  }
  visit_name(pred, out, importee_name);
}

fn visit_struct_member<'p, T, F>(pred: &F, out: &mut Vec<T>, member: &'p IStructContent<'p>)
where
  F: Fn(NodeRefP<'p>) -> Option<T>,
{
  collect_if(pred, out, NodeRefP::StructMember(member));
  match member {
    IStructContent::StructMethod(function) => visit_function(pred, out, function),
    IStructContent::NormalStructMember(normal_member) => {
      visit_normal_struct_member(pred, out, normal_member)
    }
    IStructContent::VariadicStructMember(variadic_member) => {
      visit_variadic_struct_member(pred, out, variadic_member)
    }
  }
}

fn visit_struct_members<'p, T, F>(pred: &F, out: &mut Vec<T>, members: &'p StructMembersP<'p>)
where
  F: Fn(NodeRefP<'p>) -> Option<T>,
{
  collect_if(pred, out, NodeRefP::StructMembers(members));
  let StructMembersP {
    range: _range,
    contents,
  } = members;
  for member in *contents {
    visit_struct_member(pred, out, member);
  }
}

fn visit_normal_struct_member<'p, T, F>(pred: &F, out: &mut Vec<T>, member: &'p NormalStructMemberP<'p>)
where
  F: Fn(NodeRefP<'p>) -> Option<T>,
{
  collect_if(pred, out, NodeRefP::NormalStructMember(member));
  let NormalStructMemberP {
    range: _range,
    name,
    variability: _variability,
    tyype,
  } = member;
  visit_name(pred, out, name);
  visit_templex(pred, out, tyype);
}

fn visit_variadic_struct_member<'p, T, F>(
  pred: &F,
  out: &mut Vec<T>,
  member: &'p VariadicStructMemberP<'p>,
) where
  F: Fn(NodeRefP<'p>) -> Option<T>,
{
  collect_if(pred, out, NodeRefP::VariadicStructMember(member));
  let VariadicStructMemberP {
    range: _range,
    variability: _variability,
    tyype,
  } = member;
  visit_templex(pred, out, tyype);
}

fn visit_interface<'p, T, F>(pred: &F, out: &mut Vec<T>, interface: &'p InterfaceP<'p>)
where
  F: Fn(NodeRefP<'p>) -> Option<T>,
{
  collect_if(pred, out, NodeRefP::Interface(interface));
  let InterfaceP {
    range: _range,
    name,
    attributes,
    mutability,
    maybe_identifying_runes,
    template_rules,
    maybe_default_region_rune,
    body_range: _body_range,
    members,
  } = interface;
  visit_name(pred, out, name);
  for attribute in *attributes {
    visit_attribute(pred, out, attribute);
  }
  if let Some(mutability) = mutability {
    visit_templex(pred, out, mutability);
  }
  if let Some(maybe_identifying_runes) = maybe_identifying_runes {
    visit_generic_parameters(pred, out, maybe_identifying_runes);
  }
  if let Some(template_rules) = template_rules {
    visit_template_rules(pred, out, template_rules);
  }
  if let Some(maybe_default_region_rune) = maybe_default_region_rune {
    visit_region_rune(pred, out, maybe_default_region_rune);
  }
  for member in *members {
    visit_function(pred, out, member);
  }
}

fn visit_function<'p, T, F>(pred: &F, out: &mut Vec<T>, function: &'p FunctionP<'p>)
where
  F: Fn(NodeRefP<'p>) -> Option<T>,
{
  collect_if(pred, out, NodeRefP::Function(function));
  // Recurse down into function's fields
  let FunctionP {
    range: _range,
    header,
    body,
  } = function;
  visit_function_header(pred, out, header);
  if let Some(body) = body {
    visit_block(pred, out, body);
  }
}

fn visit_function_header<'p, T, F>(pred: &F, out: &mut Vec<T>, header: &'p FunctionHeaderP<'p>)
where
  F: Fn(NodeRefP<'p>) -> Option<T>,
{
  collect_if(pred, out, NodeRefP::FunctionHeader(header));
  let FunctionHeaderP {
    range: _range,
    name,
    attributes,
    generic_parameters,
    template_rules,
    params,
    ret,
  } = header;
  if let Some(name) = name {
    visit_name(pred, out, name);
  }
  visit_function_return(pred, out, ret);
  if let Some(generic_parameters) = generic_parameters {
    visit_generic_parameters(pred, out, generic_parameters);
  }
  if let Some(template_rules) = template_rules {
    visit_template_rules(pred, out, template_rules);
  }
  if let Some(params) = params {
    visit_params(pred, out, params);
  }
  for attribute in *attributes {
    visit_attribute(pred, out, attribute);
  }
}

fn visit_block<'p, T, F>(pred: &F, out: &mut Vec<T>, block: &'p BlockPE<'p>)
where
  F: Fn(NodeRefP<'p>) -> Option<T>,
{
  collect_if(pred, out, NodeRefP::Block(block));
  let BlockPE {
    range: _range,
    maybe_pure: _maybe_pure,
    maybe_default_region,
    inner,
  } = block;
  if let Some(maybe_default_region) = maybe_default_region {
    visit_region_rune(pred, out, maybe_default_region);
  }
  visit_expression(pred, out, inner);
}

fn visit_function_return<'p, T, F>(pred: &F, out: &mut Vec<T>, return_: &'p FunctionReturnP<'p>)
where
  F: Fn(NodeRefP<'p>) -> Option<T>,
{
  collect_if(pred, out, NodeRefP::FunctionReturn(return_));
  let FunctionReturnP {
    range: _range,
    ret_type,
  } = return_;
  if let Some(ret_type) = ret_type {
    visit_templex(pred, out, ret_type);
  }
}

fn visit_generic_parameters<'p, T, F>(
  pred: &F,
  out: &mut Vec<T>,
  generic_parameters: &'p GenericParametersP<'p>,
) where
  F: Fn(NodeRefP<'p>) -> Option<T>,
{
  collect_if(pred, out, NodeRefP::GenericParameters(generic_parameters));
  let GenericParametersP {
    range: _range,
    params,
  } = generic_parameters;
  for param in *params {
    visit_generic_parameter(pred, out, param);
  }
}

fn visit_generic_parameter<'p, T, F>(pred: &F, out: &mut Vec<T>, param: &'p GenericParameterP<'p>)
where
  F: Fn(NodeRefP<'p>) -> Option<T>,
{
  collect_if(pred, out, NodeRefP::GenericParameter(param));
  let GenericParameterP {
    range: _range,
    name,
    maybe_type,
    coord_region,
    attributes,
    maybe_default,
  } = param;
  visit_name(pred, out, name);
  if let Some(maybe_type) = maybe_type {
    visit_generic_parameter_type(pred, out, maybe_type);
  }
  if let Some(coord_region) = coord_region {
    visit_region_rune(pred, out, coord_region);
  }
  for attribute in *attributes {
    visit_rune_attribute(pred, out, attribute);
  }
  if let Some(maybe_default) = maybe_default {
    visit_templex(pred, out, maybe_default);
  }
}

fn visit_template_rules<'p, T, F>(pred: &F, out: &mut Vec<T>, template_rules: &'p TemplateRulesP<'p>)
where
  F: Fn(NodeRefP<'p>) -> Option<T>,
{
  collect_if(pred, out, NodeRefP::TemplateRules(template_rules));
  let TemplateRulesP {
    range: _range,
    rules,
  } = template_rules;
  for rule in *rules {
    visit_rulex(pred, out, rule);
  }
}

fn visit_params<'p, T, F>(pred: &F, out: &mut Vec<T>, params: &'p ParamsP<'p>)
where
  F: Fn(NodeRefP<'p>) -> Option<T>,
{
  collect_if(pred, out, NodeRefP::Params(params));
  let ParamsP {
    range: _range,
    params,
  } = params;
  for param in *params {
    visit_parameter(pred, out, param);
  }
}

fn visit_parameter<'p, T, F>(pred: &F, out: &mut Vec<T>, parameter: &'p ParameterP<'p>)
where
  F: Fn(NodeRefP<'p>) -> Option<T>,
{
  collect_if(pred, out, NodeRefP::Parameter(parameter));
  let ParameterP {
    range: _range,
    virtuality,
    maybe_pre_checked: _maybe_pre_checked,
    self_borrow: _self_borrow,
    pattern,
  } = parameter;
  if let Some(virtuality) = virtuality {
    visit_abstract(pred, out, virtuality);
  }
  if let Some(pattern) = pattern {
    visit_pattern(pred, out, pattern);
  }
}

fn visit_pattern<'p, T, F>(pred: &F, out: &mut Vec<T>, pattern: &'p PatternPP<'p>)
where
  F: Fn(NodeRefP<'p>) -> Option<T>,
{
  collect_if(pred, out, NodeRefP::Pattern(pattern));
  let PatternPP {
    range: _range,
    destination,
    templex,
    destructure,
  } = pattern;
  if let Some(destination) = destination {
    visit_destination(pred, out, destination);
  }
  if let Some(templex) = templex {
    visit_templex(pred, out, templex);
  }
  if let Some(destructure) = destructure {
    visit_destructure(pred, out, destructure);
  }
}

fn visit_destination<'p, T, F>(pred: &F, out: &mut Vec<T>, destination: &'p DestinationLocalP<'p>)
where
  F: Fn(NodeRefP<'p>) -> Option<T>,
{
  collect_if(pred, out, NodeRefP::DestinationLocal(destination));
  let DestinationLocalP {
    decl,
    mutate: _mutate,
  } = destination;
  visit_name_declaration(pred, out, decl);
}

fn visit_destructure<'p, T, F>(pred: &F, out: &mut Vec<T>, destructure: &'p DestructureP<'p>)
where
  F: Fn(NodeRefP<'p>) -> Option<T>,
{
  collect_if(pred, out, NodeRefP::Destructure(destructure));
  let DestructureP {
    range: _range,
    patterns,
  } = destructure;
  for pattern in *patterns {
    visit_pattern(pred, out, pattern);
  }
}

fn visit_name_declaration<'p, T, F>(pred: &F, out: &mut Vec<T>, declaration: &'p INameDeclarationP<'p>)
where
  F: Fn(NodeRefP<'p>) -> Option<T>,
{
  collect_if(pred, out, NodeRefP::NameDeclaration(declaration));
  match declaration {
    INameDeclarationP::LocalNameDeclaration(name) => visit_name(pred, out, name),
    INameDeclarationP::IgnoredLocalNameDeclaration(_range) => {}
    INameDeclarationP::IterableNameDeclaration(_range) => {}
    INameDeclarationP::IteratorNameDeclaration(_range) => {}
    INameDeclarationP::IterationOptionNameDeclaration(_range) => {}
    INameDeclarationP::ConstructingMemberNameDeclaration(name) => visit_name(pred, out, name),
  }
}

fn visit_generic_parameter_type<'p, T, F>(
  pred: &F,
  out: &mut Vec<T>,
  param_type: &'p GenericParameterTypeP,
) where
  F: Fn(NodeRefP<'p>) -> Option<T>,
{
  collect_if(pred, out, NodeRefP::GenericParameterType(param_type));
  let GenericParameterTypeP {
    range: _range,
    tyype: _tyype,
  } = param_type;
}

fn visit_abstract<'p, T, F>(pred: &F, out: &mut Vec<T>, abstract_: &'p AbstractP)
where
  F: Fn(NodeRefP<'p>) -> Option<T>,
{
  collect_if(pred, out, NodeRefP::Abstract(abstract_));
  let AbstractP { range: _range } = abstract_;
}

fn visit_rune_attribute<'p, T, F>(pred: &F, out: &mut Vec<T>, attribute: &'p IRuneAttributeP)
where
  F: Fn(NodeRefP<'p>) -> Option<T>,
{
  collect_if(pred, out, NodeRefP::RuneAttribute(attribute));
  match attribute {
    IRuneAttributeP::ImmutableRuneAttribute(_range) => {}
    IRuneAttributeP::MutableRuneAttribute(_range) => {}
    IRuneAttributeP::ReadOnlyRegionRuneAttribute(_range) => {}
    IRuneAttributeP::ReadWriteRegionRuneAttribute(_range) => {}
    IRuneAttributeP::ImmutableRegionRuneAttribute(_range) => {}
    IRuneAttributeP::AdditiveRegionRuneAttribute(_range) => {}
    IRuneAttributeP::PoolRuneAttribute(_range) => {}
    IRuneAttributeP::ArenaRuneAttribute(_range) => {}
    IRuneAttributeP::BumpRuneAttribute(_range) => {}
  }
}

fn visit_region_rune<'p, T, F>(pred: &F, out: &mut Vec<T>, region_rune: &'p RegionRunePT<'p>)
where
  F: Fn(NodeRefP<'p>) -> Option<T>,
{
  collect_if(pred, out, NodeRefP::RegionRune(region_rune));
  let RegionRunePT {
    range: _range,
    name,
  } = region_rune;
  if let Some(name) = name {
    visit_name(pred, out, name);
  }
}

fn visit_attribute<'p, T, F>(pred: &F, out: &mut Vec<T>, attribute: &'p IAttributeP<'p>)
where
  F: Fn(NodeRefP<'p>) -> Option<T>,
{
  collect_if(pred, out, NodeRefP::Attribute(attribute));
  match attribute {
    IAttributeP::WeakableAttribute(WeakableAttributeP { range }) => {
      visit_weakable_attribute(pred, out, range)
    }
    IAttributeP::SealedAttribute(SealedAttributeP { range }) => {
      visit_sealed_attribute(pred, out, range)
    }
    IAttributeP::MacroCall(MacroCallP {
      range,
      inclusion,
      name,
    }) => visit_macro_call(pred, out, range, inclusion, name),
    _ => {}
  }
}

fn visit_weakable_attribute<'p, T, F>(_pred: &F, _out: &mut Vec<T>, _range: &RangeL)
where
  F: Fn(NodeRefP<'p>) -> Option<T>,
{
}

fn visit_sealed_attribute<'p, T, F>(_pred: &F, _out: &mut Vec<T>, _range: &RangeL)
where
  F: Fn(NodeRefP<'p>) -> Option<T>,
{
}

fn visit_macro_call<'p, T, F>(
  pred: &F,
  out: &mut Vec<T>,
  _range: &RangeL,
  _inclusion: &IMacroInclusionP,
  name: &'p NameP<'p>,
) where
  F: Fn(NodeRefP<'p>) -> Option<T>,
{
  visit_name(pred, out, name);
}

fn visit_name<'p, T, F>(pred: &F, out: &mut Vec<T>, name: &'p NameP<'p>)
where
  F: Fn(NodeRefP<'p>) -> Option<T>,
{
  collect_if(pred, out, NodeRefP::Name(name));
}

fn visit_templex<'p, T, F>(pred: &F, out: &mut Vec<T>, templex: &'p ITemplexPT<'p>)
where
  F: Fn(NodeRefP<'p>) -> Option<T>,
{
  collect_if(pred, out, NodeRefP::Templex(templex));
  match templex {
    ITemplexPT::AnonymousRune(AnonymousRunePT { range: _range }) => {}
    ITemplexPT::Bool(BoolPT {
      range: _range,
      value: _value,
    }) => {}
    ITemplexPT::Point(PointPT {
      range: _range,
      inner,
    }) => visit_templex(pred, out, inner),
    ITemplexPT::Call(CallPT {
      range: _range,
      template,
      args,
    }) => {
      visit_templex(pred, out, template);
      for arg in *args {
        visit_templex(pred, out, arg);
      }
    }
    ITemplexPT::Function(FunctionPT {
      range: _range,
      mutability,
      parameters,
      return_type,
    }) => {
      if let Some(mutability) = mutability {
        visit_templex(pred, out, mutability);
      }
      visit_pack(pred, out, parameters);
      visit_templex(pred, out, return_type);
    }
    ITemplexPT::Inline(InlinePT {
      range: _range,
      inner,
    }) => visit_templex(pred, out, inner),
    ITemplexPT::Int(IntPT {
      range: _range,
      value: _value,
    }) => {}
    ITemplexPT::RegionRune(region_rune) => visit_region_rune(pred, out, region_rune),
    ITemplexPT::Location(LocationPT {
      range: _range,
      location: _location,
    }) => {}
    ITemplexPT::Tuple(TuplePT {
      range: _range,
      elements,
    }) => {
      for element in *elements {
        visit_templex(pred, out, element);
      }
    }
    ITemplexPT::Mutability(MutabilityPT(_range, _mutability)) => {}
    ITemplexPT::NameOrRune(NameOrRunePT(name)) => visit_name(pred, out, name),
    ITemplexPT::Interpreted(InterpretedPT {
      range: _range,
      maybe_ownership,
      maybe_region,
      inner,
    }) => {
      if let Some(maybe_ownership) = maybe_ownership {
        visit_ownership(pred, out, maybe_ownership);
      }
      if let Some(maybe_region) = maybe_region {
        visit_region_rune(pred, out, maybe_region);
      }
      visit_templex(pred, out, inner);
    }
    ITemplexPT::Ownership(ownership) => visit_ownership(pred, out, ownership),
    ITemplexPT::Pack(pack) => {
      visit_pack(pred, out, pack);
    }
    ITemplexPT::Func(FuncPT {
      range: _range,
      name,
      params_range: _params_range,
      parameters,
      return_type,
    }) => {
      visit_name(pred, out, name);
      for parameter in *parameters {
        visit_templex(pred, out, parameter);
      }
      visit_templex(pred, out, return_type);
    }
    ITemplexPT::StaticSizedArray(StaticSizedArrayPT {
      range: _range,
      mutability,
      variability,
      size,
      element,
    }) => {
      visit_templex(pred, out, mutability);
      visit_templex(pred, out, variability);
      visit_templex(pred, out, size);
      visit_templex(pred, out, element);
    }
    ITemplexPT::RuntimeSizedArray(RuntimeSizedArrayPT {
      range: _range,
      mutability,
      element,
    }) => {
      visit_templex(pred, out, mutability);
      visit_templex(pred, out, element);
    }
    ITemplexPT::Share(SharePT {
      range: _range,
      inner,
    }) => visit_templex(pred, out, inner),
    ITemplexPT::String(StringPT {
      range: _range,
      str: _str,
    }) => {}
    ITemplexPT::TypedRune(TypedRunePT {
      range: _range,
      rune,
      tyype: _tyype,
    }) => visit_name(pred, out, rune),
    ITemplexPT::Variability(VariabilityPT(_range, _variability)) => {}
  }
}

fn visit_rulex<'p, T, F>(pred: &F, out: &mut Vec<T>, rulex: &'p IRulexPR<'p>)
where
  F: Fn(NodeRefP<'p>) -> Option<T>,
{
  collect_if(pred, out, NodeRefP::Rulex(rulex));
  match rulex {
    IRulexPR::Equals(EqualsPR {
      range: _range,
      left,
      right,
    }) => {
      visit_rulex(pred, out, left);
      visit_rulex(pred, out, right);
    }
    IRulexPR::Or(OrPR {
      range: _range,
      possibilities,
    }) => {
      for possibility in *possibilities {
        visit_rulex(pred, out, possibility);
      }
    }
    IRulexPR::Dot(DotPR {
      range: _range,
      container,
      member_name,
    }) => {
      visit_rulex(pred, out, container);
      visit_name(pred, out, member_name);
    }
    IRulexPR::Components(ComponentsPR {
      range: _range,
      container: _container,
      components,
    }) => {
      for component in *components {
        visit_rulex(pred, out, component);
      }
    }
    IRulexPR::Typed(TypedPR {
      range: _range,
      rune,
      tyype: _tyype,
    }) => {
      if let Some(rune) = rune {
        visit_name(pred, out, rune);
      }
    }
    IRulexPR::Templex(templex) => visit_templex(pred, out, templex),
    IRulexPR::BuiltinCall(BuiltinCallPR {
      range: _range,
      name,
      args,
    }) => {
      visit_name(pred, out, name);
      for arg in *args {
        visit_rulex(pred, out, arg);
      }
    }
    IRulexPR::Pack(PackPR {
      range: _range,
      elements,
    }) => {
      for element in *elements {
        visit_rulex(pred, out, element);
      }
    }
  }
}

fn visit_expression<'p, T, F>(pred: &F, out: &mut Vec<T>, expr: &'p IExpressionPE<'p>)
where
  F: Fn(NodeRefP<'p>) -> Option<T>,
{
  collect_if(pred, out, NodeRefP::Expression(expr));
  match expr {
    IExpressionPE::Void(_void) => {}
    IExpressionPE::Pack(pack) => {
      let PackPE {
        range: _range,
        inners,
      } = pack;
      for inner in *inners {
        visit_expression(pred, out, inner);
      }
    }
    IExpressionPE::SubExpression(sub_expression) => {
      let SubExpressionPE {
        range: _range,
        inner,
      } = sub_expression;
      visit_expression(pred, out, inner);
    }
    IExpressionPE::And(and_expr) => {
      let AndPE {
        range: _range,
        left,
        right,
      } = and_expr;
      visit_expression(pred, out, left);
      visit_block(pred, out, right);
    }
    IExpressionPE::Or(or_expr) => {
      let OrPE {
        range: _range,
        left,
        right,
      } = or_expr;
      visit_expression(pred, out, left);
      visit_block(pred, out, right);
    }
    IExpressionPE::If(if_expr) => {
      let IfPE {
        range: _range,
        condition,
        then_body,
        else_body,
      } = if_expr;
      visit_expression(pred, out, condition);
      visit_block(pred, out, then_body);
      visit_block(pred, out, else_body);
    }
    IExpressionPE::While(while_expr) => {
      let WhilePE {
        range: _range,
        condition,
        body,
      } = while_expr;
      visit_expression(pred, out, condition);
      visit_block(pred, out, body);
    }
    IExpressionPE::Each(each_expr) => {
      let EachPE {
        range: _range,
        maybe_pure: _maybe_pure,
        entry_pattern,
        in_keyword_range: _in_keyword_range,
        iterable_expr,
        body,
      } = each_expr;
      visit_pattern(pred, out, entry_pattern);
      visit_expression(pred, out, iterable_expr);
      visit_block(pred, out, body);
    }
    IExpressionPE::Range(range_expr) => {
      let RangePE {
        range: _range,
        from_expr,
        to_expr,
      } = range_expr;
      visit_expression(pred, out, from_expr);
      visit_expression(pred, out, to_expr);
    }
    IExpressionPE::Destruct(destruct_expr) => {
      let DestructPE {
        range: _range,
        inner,
      } = destruct_expr;
      visit_expression(pred, out, inner);
    }
    IExpressionPE::Unlet(unlet_expr) => {
      let UnletPE {
        range: _range,
        name,
      } = unlet_expr;
      visit_imprecise_name(pred, out, name);
    }
    IExpressionPE::Mutate(mutate_expr) => {
      let MutatePE {
        range: _range,
        mutatee,
        source,
      } = mutate_expr;
      visit_expression(pred, out, mutatee);
      visit_expression(pred, out, source);
    }
    IExpressionPE::Return(return_expr) => {
      let ReturnPE {
        range: _range,
        expr,
      } = return_expr;
      visit_expression(pred, out, expr);
    }
    IExpressionPE::Break(_break_expr) => {}
    IExpressionPE::Let(let_expr) => {
      let LetPE {
        range: _range,
        pattern,
        source,
      } = let_expr;
      visit_pattern(pred, out, pattern);
      visit_expression(pred, out, source);
    }
    IExpressionPE::Tuple(tuple_expr) => {
      let TuplePE {
        range: _range,
        elements,
      } = tuple_expr;
      for element in *elements {
        visit_expression(pred, out, element);
      }
    }
    IExpressionPE::ConstructArray(construct_array_expr) => {
      let ConstructArrayPE {
        range: _range,
        type_pt,
        mutability_pt,
        variability_pt,
        size,
        initializing_individual_elements: _initializing_individual_elements,
        args,
      } = construct_array_expr;
      if let Some(type_pt) = type_pt {
        visit_templex(pred, out, type_pt);
      }
      if let Some(mutability_pt) = mutability_pt {
        visit_templex(pred, out, mutability_pt);
      }
      if let Some(variability_pt) = variability_pt {
        visit_templex(pred, out, variability_pt);
      }
      visit_array_size(pred, out, size);
      for arg in *args {
        visit_expression(pred, out, arg);
      }
    }
    IExpressionPE::ConstantInt(_constant_int_expr) => {}
    IExpressionPE::ConstantBool(_constant_bool_expr) => {}
    IExpressionPE::ConstantStr(_constant_str_expr) => {}
    IExpressionPE::ConstantFloat(_constant_float_expr) => {}
    IExpressionPE::StrInterpolate(str_interpolate_expr) => {
      let StrInterpolatePE {
        range: _range,
        parts,
      } = str_interpolate_expr;
      for part in *parts {
        visit_expression(pred, out, part);
      }
    }
    IExpressionPE::Dot(dot_expr) => {
      let DotPE {
        range: _range,
        left,
        operator_range: _operator_range,
        member,
      } = dot_expr;
      visit_expression(pred, out, left);
      visit_name(pred, out, member);
    }
    IExpressionPE::Index(index_expr) => {
      let IndexPE {
        range: _range,
        left,
        args,
      } = index_expr;
      visit_expression(pred, out, left);
      for arg in *args {
        visit_expression(pred, out, arg);
      }
    }
    IExpressionPE::FunctionCall(function_call_expr) => {
      let FunctionCallPE {
        range: _range,
        operator_range: _operator_range,
        callable_expr,
        arg_exprs,
      } = function_call_expr;
      visit_expression(pred, out, callable_expr);
      for arg in *arg_exprs {
        visit_expression(pred, out, arg);
      }
    }
    IExpressionPE::BraceCall(brace_call_expr) => {
      let BraceCallPE {
        range: _range,
        operator_range: _operator_range,
        subject_expr,
        arg_exprs,
        callable_readwrite: _callable_readwrite,
      } = brace_call_expr;
      visit_expression(pred, out, subject_expr);
      for arg in *arg_exprs {
        visit_expression(pred, out, arg);
      }
    }
    IExpressionPE::Not(not_expr) => {
      let NotPE {
        range: _range,
        inner,
      } = not_expr;
      visit_expression(pred, out, inner);
    }
    IExpressionPE::Augment(augment_expr) => {
      let AugmentPE {
        range: _range,
        target_ownership: _target_ownership,
        inner,
      } = augment_expr;
      visit_expression(pred, out, inner);
    }
    IExpressionPE::Transmigrate(transmigrate_expr) => {
      let TransmigratePE {
        range: _range,
        target_region,
        inner,
      } = transmigrate_expr;
      visit_name(pred, out, target_region);
      visit_expression(pred, out, inner);
    }
    IExpressionPE::BinaryCall(binary_call_expr) => {
      let BinaryCallPE {
        range: _range,
        function_name,
        left_expr,
        right_expr,
      } = binary_call_expr;
      visit_name(pred, out, function_name);
      visit_expression(pred, out, left_expr);
      visit_expression(pred, out, right_expr);
    }
    IExpressionPE::MethodCall(method_call_expr) => {
      let MethodCallPE {
        range: _range,
        subject_expr,
        operator_range: _operator_range,
        method_lookup,
        arg_exprs,
      } = method_call_expr;
      visit_expression(pred, out, subject_expr);
      visit_lookup(pred, out, method_lookup);
      for arg in *arg_exprs {
        visit_expression(pred, out, arg);
      }
    }
    IExpressionPE::Lookup(lookup_expr) => {
      visit_lookup(pred, out, lookup_expr);
    }
    IExpressionPE::MagicParamLookup(_magic_param_lookup_expr) => {}
    IExpressionPE::Lambda(lambda_expr) => {
      let LambdaPE {
        captures: _captures,
        function,
      } = lambda_expr;
      visit_function(pred, out, function);
    }
    IExpressionPE::Block(block_expr) => {
      visit_block(pred, out, block_expr);
    }
    IExpressionPE::Consecutor(consecutor_expr) => {
      let ConsecutorPE { inners } = consecutor_expr;
      for inner in *inners {
        visit_expression(pred, out, inner);
      }
    }
    IExpressionPE::Shortcall(shortcall_expr) => {
      let ShortcallPE {
        range: _range,
        arg_exprs,
      } = shortcall_expr;
      for arg in *arg_exprs {
        visit_expression(pred, out, arg);
      }
    }
  }
}

fn visit_lookup<'p, T, F>(pred: &F, out: &mut Vec<T>, lookup: &'p LookupPE<'p>)
where
  F: Fn(NodeRefP<'p>) -> Option<T>,
{
  collect_if(pred, out, NodeRefP::Lookup(lookup));
  let LookupPE {
    name,
    template_args,
  } = lookup;
  visit_imprecise_name(pred, out, name);
  if let Some(template_args) = template_args {
    visit_template_args(pred, out, template_args);
  }
}

fn visit_template_args<'p, T, F>(pred: &F, out: &mut Vec<T>, template_args: &'p TemplateArgsP<'p>)
where
  F: Fn(NodeRefP<'p>) -> Option<T>,
{
  collect_if(pred, out, NodeRefP::TemplateArgs(template_args));
  let TemplateArgsP {
    range: _range,
    args,
  } = template_args;
  for arg in *args {
    visit_templex(pred, out, arg);
  }
}

fn visit_imprecise_name<'p, T, F>(pred: &F, out: &mut Vec<T>, name: &'p IImpreciseNameP<'p>)
where
  F: Fn(NodeRefP<'p>) -> Option<T>,
{
  collect_if(pred, out, NodeRefP::ImpreciseName(name));
  match name {
    IImpreciseNameP::LookupName(name) => visit_name(pred, out, name),
    IImpreciseNameP::IterableName(_range) => {}
    IImpreciseNameP::IteratorName(_range) => {}
    IImpreciseNameP::IterationOptionName(_range) => {}
  }
}

fn visit_array_size<'p, T, F>(pred: &F, out: &mut Vec<T>, size: &'p IArraySizeP<'p>)
where
  F: Fn(NodeRefP<'p>) -> Option<T>,
{
  match size {
    IArraySizeP::RuntimeSized => {}
    IArraySizeP::StaticSized(static_sized) => {
      if let Some(size_pt) = &static_sized.size_pt {
        visit_templex(pred, out, size_pt);
      }
    }
  }
}

fn visit_pack<'p, T, F>(pred: &F, out: &mut Vec<T>, pack: &'p PackPT<'p>)
where
  F: Fn(NodeRefP<'p>) -> Option<T>,
{
  collect_if(pred, out, NodeRefP::Pack(pack));
  let PackPT {
    range: _range,
    members,
  } = pack;
  for member in *members {
    visit_templex(pred, out, member);
  }
}

fn visit_ownership<'p, T, F>(pred: &F, out: &mut Vec<T>, ownership: &'p OwnershipPT)
where
  F: Fn(NodeRefP<'p>) -> Option<T>,
{
  collect_if(pred, out, NodeRefP::Ownership(ownership));
  let OwnershipPT(_range, _ownership) = ownership;
}

pub enum NodeRefP<'p> {
  Struct(&'p StructP<'p>),
  StructMembers(&'p StructMembersP<'p>),
  StructMember(&'p IStructContent<'p>),
  NormalStructMember(&'p NormalStructMemberP<'p>),
  VariadicStructMember(&'p VariadicStructMemberP<'p>),
  Interface(&'p InterfaceP<'p>),
  Function(&'p FunctionP<'p>),
  FunctionHeader(&'p FunctionHeaderP<'p>),
  FunctionReturn(&'p FunctionReturnP<'p>),
  GenericParameters(&'p GenericParametersP<'p>),
  GenericParameter(&'p GenericParameterP<'p>),
  GenericParameterType(&'p GenericParameterTypeP),
  Abstract(&'p AbstractP),
  Params(&'p ParamsP<'p>),
  Parameter(&'p ParameterP<'p>),
  TemplateRules(&'p TemplateRulesP<'p>),
  RegionRune(&'p RegionRunePT<'p>),
  Attribute(&'p IAttributeP<'p>),
  RuneAttribute(&'p IRuneAttributeP),
  Name(&'p NameP<'p>),
  Block(&'p BlockPE<'p>),
  Expression(&'p IExpressionPE<'p>),
  Pattern(&'p PatternPP<'p>),
  DestinationLocal(&'p DestinationLocalP<'p>),
  Destructure(&'p DestructureP<'p>),
  NameDeclaration(&'p INameDeclarationP<'p>),
  Templex(&'p ITemplexPT<'p>),
  Pack(&'p PackPT<'p>),
  Ownership(&'p OwnershipPT),
  Rulex(&'p IRulexPR<'p>),
  Lookup(&'p LookupPE<'p>),
  TemplateArgs(&'p TemplateArgsP<'p>),
  ImpreciseName(&'p IImpreciseNameP<'p>),
  Impl(&'p ImplP<'p>),
  ExportAs(&'p ExportAsP<'p>),
  Import(&'p ImportP<'p>),
}

pub fn collect_in_file<'p, T, F>(file: &'p FileP<'p>, predicate: &F) -> Vec<T>
where
  F: Fn(NodeRefP<'p>) -> Option<T>,
{
  let mut out = Vec::new();
  for denizen in file.denizens {
    visit_denizen(predicate, &mut out, denizen);
  }
  out
}

pub fn collect_in_rulex<'p, T, F>(rulex: &'p IRulexPR<'p>, predicate: &F) -> Vec<T>
where
  F: Fn(NodeRefP<'p>) -> Option<T>,
{
  let mut out = Vec::new();
  visit_rulex(predicate, &mut out, rulex);
  out
}

/// Deep search helper; if the given pattern matches anything in the given expr, then return
/// the given body's result.
/// See test_collect_where_finds_function_by_name for an example.
#[macro_export]
macro_rules! collect_where {
  ($expr:expr, $pattern:pat => $body:expr) => {{
    $crate::parsing::tests::traverse::collect_in_file($expr, &|node| match node {
      $pattern => $body,
      _ => None,
    })
  }};
  ($expr:expr, $pattern:pat if $guard:expr => $body:expr) => {{
    $crate::parsing::tests::traverse::collect_in_file($expr, &|node| match node {
      $pattern if $guard => $body,
      _ => None,
    })
  }};
}
#[test]
fn test_collect_where_finds_function_by_name() {
  let parse_bump = Bump::new();
  let parse_arena = ParseArena::new(&parse_bump);
  let keywords = Keywords::new_for_parse(&parse_arena);
  let program = compile(&parse_arena, &keywords, "exported func main() int {}");
  assert!(!collect_where!(
      &program,
      NodeRefP::Function(FunctionP {
          header: FunctionHeaderP {
              name: Some(NameP(_, StrI("main"))),
              ..
          },
          ..
      }) => Some(())
  )
  .is_empty());
}

#[macro_export]
macro_rules! collect_only {
    ($expr:expr, $pattern:pat => $body:expr) => {{
        let mut matches = $crate::collect_where!($expr, $pattern => $body);
        assert_eq!(1, matches.len());
        matches.remove(0)
    }};
    ($expr:expr, $pattern:pat if $guard:expr => $body:expr) => {{
        let mut matches = $crate::collect_where!($expr, $pattern if $guard => $body);
        assert_eq!(1, matches.len());
        matches.remove(0)
    }};
}

#[macro_export]
macro_rules! collect_where_rulex {
  ($expr:expr, $pattern:pat => $body:expr) => {{
    $crate::parsing::tests::traverse::collect_in_rulex($expr, &|node| match node {
      $pattern => $body,
      _ => None,
    })
  }};
  ($expr:expr, $pattern:pat if $guard:expr => $body:expr) => {{
    $crate::parsing::tests::traverse::collect_in_rulex($expr, &|node| match node {
      $pattern if $guard => $body,
      _ => None,
    })
  }};
}

#[macro_export]
macro_rules! collect_only_rulex {
  ($expr:expr, $pattern:pat => $body:expr) => {{
    let mut matches = $crate::collect_where_rulex!($expr, $pattern => $body);
    assert_eq!(1, matches.len());
    matches.remove(0)
  }};
  ($expr:expr, $pattern:pat if $guard:expr => $body:expr) => {{
    let mut matches = $crate::collect_where_rulex!($expr, $pattern if $guard => $body);
    assert_eq!(1, matches.len());
    matches.remove(0)
  }};
}
