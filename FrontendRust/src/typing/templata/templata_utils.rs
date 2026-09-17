use crate::typing::ast::ast::*;
use crate::typing::names::names::*;

pub fn unapply_simple_name<'s, 't>(id: &IdT<'s, 't>) -> Option<String>
where 's: 't,
{
  match id.local_name {
    INameT::LambdaCallFunction(_) => Some("__call".to_string()),
    INameT::Let(_) => None,
    INameT::UnnamedLocal(_) => None,
    INameT::FunctionBound(n) => Some(n.template.human_name.as_str().to_string()),
    INameT::ClosureParam(_) => None,
    INameT::MagicParam(_) => None,
    INameT::CodeVar(n) => Some(n.name.as_str().to_string()),
    INameT::Function(n) => Some(n.template.human_name.as_str().to_string()),
    INameT::LambdaCitizen(_) => None,
    INameT::Struct(n) => match n.template {
      IStructTemplateNameT::StructTemplate(st) => Some(st.human_name.as_str().to_string()),
      _ => unreachable!(),
    },
    INameT::StructTemplate(st) => Some(st.human_name.as_str().to_string()),
    INameT::Interface(n) => Some(n.template.human_namee.as_str().to_string()),
    INameT::InterfaceTemplate(it) => Some(it.human_namee.as_str().to_string()),
    INameT::AnonymousSubstructTemplate(n) => match n.interface {
      IInterfaceTemplateNameT::InterfaceTemplate(it) => Some(it.human_namee.as_str().to_string()),
    },
    _ => panic!("Unimplemented: unapply_simple_name for {:?}", id.local_name),
  }
}

pub fn unapply_function_name_def<'s, 't>(function2: &FunctionDefinitionT<'s, 't>) -> Option<String> {
  unapply_function_name_header(&function2.header)
}

pub fn unapply_function_name_header<'s, 't>(header: &FunctionHeaderT<'s, 't>) -> Option<String> {
  unapply_simple_name(&header.id)
}

pub fn unapply_function_name_prototype<'s, 't>(prototype: &PrototypeT<'s, 't>) -> Option<String> {
  unapply_simple_name(&prototype.id)
}

