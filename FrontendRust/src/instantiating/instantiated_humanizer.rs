use crate::interner::StrI;
use crate::utils::range::CodeLocationS;
use crate::instantiating::ast::types::{CoordI, KindIT};
use crate::instantiating::ast::names::{IdI, INameI};
use crate::instantiating::ast::templata::ITemplataI;
use crate::instantiating::ast::ast::SignatureI;
use crate::instantiating::ast::names::RawArrayNameI;
use crate::instantiating::ast::names::RuntimeSizedArrayNameI;
use crate::instantiating::ast::names::StaticSizedArrayNameI;
use crate::instantiating::ast::templata::IntegerTemplataI;
use crate::instantiating::ast::templata::MutabilityTemplataI;
use crate::instantiating::ast::templata::VariabilityTemplataI;
use crate::instantiating::ast::types::MutabilityI;
use crate::instantiating::ast::types::OwnershipI;
use crate::instantiating::ast::types::VariabilityI;
use std::marker::PhantomData;
use std::mem::discriminant;

pub fn humanize_templata<'s, 'i, R: Copy + PartialEq>(
    code_map: &dyn Fn(CodeLocationS<'s>) -> String,
    templata: &ITemplataI<'s, 'i, R>,
) -> String {
    match templata {
        ITemplataI::RuntimeSizedArrayTemplate(_) => "Array".to_string(),
        ITemplataI::StaticSizedArrayTemplate(_) => "StaticArray".to_string(),
        ITemplataI::InterfaceDefinition(t) => humanize_id(code_map, &t.env_id, None),
        ITemplataI::StructDefinition(t) => humanize_id(code_map, &t.env_id, None),
        ITemplataI::Variability(v) => match v.variability {
            VariabilityI::Final => "final".to_string(),
            VariabilityI::Varying => "vary".to_string(),
        },
        ITemplataI::Integer(i) => i.value.to_string(),
        ITemplataI::Mutability(m) => match m.mutability {
            MutabilityI::Mutable => "mut".to_string(),
            MutabilityI::Immutable => "imm".to_string(),
        },
        ITemplataI::Coord(c) => humanize_coord(code_map, &c.coord),
        ITemplataI::Kind(k) => humanize_kind(code_map, &k.kind),
        ITemplataI::Region(r) => r.pure_height.to_string(),
        _ => panic!("humanize_templata: unimplemented variant"),
    }
}

pub fn humanize_coord<'s, 'i, R: Copy + PartialEq>(
    code_map: &dyn Fn(CodeLocationS<'s>) -> String,
    coord: &CoordI<'s, 'i, R>,
) -> String {
    let ownership_str = match coord.ownership {
        OwnershipI::Own => "",
        OwnershipI::MutableShare => "",
        OwnershipI::MutableBorrow => "&",
        OwnershipI::ImmutableShare => "#",
        OwnershipI::ImmutableBorrow => "&#",
        OwnershipI::Weak => "weak&",
    };
    let kind_str = humanize_kind(code_map, &coord.kind);
    ownership_str.to_string() + &kind_str
}

pub fn humanize_kind<'s, 'i, R: Copy + PartialEq>(
    code_map: &dyn Fn(CodeLocationS<'s>) -> String,
    kind: &KindIT<'s, 'i, R>,
) -> String {
    match kind {
        KindIT::IntIT(b) => format!("i{}", b.bits),
        KindIT::BoolIT(_) => "bool".to_string(),
        KindIT::StrIT(_) => "str".to_string(),
        KindIT::NeverIT(_) => "never".to_string(),
        KindIT::VoidIT(_) => "void".to_string(),
        KindIT::FloatIT(_) => "float".to_string(),
        KindIT::InterfaceIT(i) => humanize_id(code_map, &i.id, None),
        KindIT::StructIT(s) => humanize_id(code_map, &s.id, None),
        KindIT::RuntimeSizedArrayIT(rsa) => humanize_id(code_map, &rsa.name, None),
        KindIT::StaticSizedArrayIT(ssa) => humanize_id(code_map, &ssa.name, None),
    }
}

pub fn humanize_id<'s, 'i, R: Copy + PartialEq>(
    code_map: &dyn Fn(CodeLocationS<'s>) -> String,
    name: &IdI<'s, 'i, R>,
    containing_region: Option<&ITemplataI<'s, 'i, R>>,
) -> String {
    let prefix = if !name.init_steps.is_empty() {
        name.init_steps.iter().map(|n| humanize_name(code_map, *n, None)).collect::<Vec<_>>().join(".") + "."
    } else {
        String::new()
    };
    prefix + &humanize_name(code_map, name.local_name, containing_region)
}

pub fn humanize_name<'s, 'i, R: Copy + PartialEq>(
    code_map: &dyn Fn(CodeLocationS<'s>) -> String,
    name: INameI<'s, 'i, R>,
    containing_region: Option<&ITemplataI<'s, 'i, R>>,
) -> String {
    match name {
        INameI::FunctionNameIX(f) => {
            let template_str = humanize_name(code_map, INameI::FunctionTemplate(&f.template), None);
            let args_str = humanize_generic_args(code_map, f.template_args, containing_region);
            let params_str = if !f.parameters.is_empty() {
                "(".to_string() + &f.parameters.iter().map(|c| humanize_coord(code_map, c)).collect::<Vec<_>>().join(",") + ")"
            } else {
                String::new()
            };
            template_str + &args_str + &params_str
        }
        INameI::FunctionTemplate(f) => f.human_name.0.to_string(),
        INameI::ExternFunction(f) => f.human_name.0.to_string() + &humanize_generic_args(code_map, f.template_args, containing_region),
        INameI::StructName(s) => humanize_name(code_map, s.template.into(), None) + &humanize_generic_args(code_map, s.template_args, containing_region),
        INameI::InterfaceName(i) => humanize_name(code_map, i.template.into(), None) + &humanize_generic_args(code_map, i.template_args, containing_region),
        INameI::StructTemplate(t) => t.human_name.0.to_string(),
        INameI::InterfaceTemplate(t) => t.human_namee.0.to_string(),
        INameI::PackageTopLevel(_) => panic!("humanize_name: PackageTopLevel branch"),
        INameI::CodeVar(c) => c.name.0.to_string(),
        INameI::TypingPassBlockResultVar(b) => format!("b:{}", b.life.to_string()),
        INameI::TypingPassFunctionResultVar(_) => "(result)".to_string(),
        INameI::TypingPassTemporaryVar(t) => format!("t:{}", t.life.to_string()),
        INameI::LambdaCitizen(c) => humanize_name(code_map, INameI::LambdaCitizenTemplate(&c.template), None) + "<>",
        INameI::LambdaCitizenTemplate(t) => "λC:".to_string() + &code_map(t.code_location),
        INameI::LambdaCallFunctionTemplate(t) => "λF:".to_string() + &code_map(t.code_location),
        INameI::ClosureParam(c) => "λP:".to_string() + &code_map(c.code_location),
        INameI::ConstructingMember(c) => format!("cm:{}", c.name.0),
        INameI::MagicParam(m) => "mp:".to_string() + &code_map(m.code_location_2),
        INameI::LambdaCallFunction(n) => {
            humanize_name(code_map, INameI::LambdaCallFunctionTemplate(&n.template), None)
                + &humanize_generic_args(code_map, n.template_args, None)
                + "(" + &n.parameters.iter().map(|c| humanize_coord(code_map, c)).collect::<Vec<_>>().join(",") + ")"
        }
        INameI::StaticSizedArray(n) => {
            let StaticSizedArrayNameI { template: _, size, variability, arr } = *n;
            let RawArrayNameI { mutability, element_type, self_region: region } = arr;
            "[]<".to_string()
                + &humanize_templata(code_map, &ITemplataI::<R>::Integer(IntegerTemplataI { value: size, _marker: PhantomData })) + ","
                + &humanize_templata(code_map, &ITemplataI::<R>::Mutability(MutabilityTemplataI { mutability, _marker: PhantomData })) + ","
                + &humanize_templata(code_map, &ITemplataI::<R>::Variability(VariabilityTemplataI { variability, _marker: PhantomData })) + ","
                + &humanize_templata(code_map, &ITemplataI::<R>::Region(region)) + ">"
                + &humanize_templata(code_map, &ITemplataI::<R>::Coord(element_type))
        }
        INameI::RuntimeSizedArray(n) => {
            let RuntimeSizedArrayNameI { template: _, arr } = *n;
            let RawArrayNameI { mutability, element_type, self_region: region } = arr;
            "[]<".to_string()
                + (match mutability { MutabilityI::Immutable => "i", MutabilityI::Mutable => "m" }) + ","
                + &humanize_templata(code_map, &ITemplataI::<R>::Region(region)) + ">"
                + &humanize_templata(code_map, &ITemplataI::<R>::Coord(element_type))
        }
        INameI::Iterator(i) => "it:".to_string() + &code_map(i.range.begin),
        INameI::Iterable(i) => "ib:".to_string() + &code_map(i.range.begin),
        INameI::IterationOption(i) => "io:".to_string() + &code_map(i.range.begin),
        INameI::AnonymousSubstruct(n) => {
            humanize_name(code_map, INameI::AnonymousSubstructTemplate(&n.template), None)
                + "<" + &n.template_args.iter().map(|t| humanize_templata(code_map, t)).collect::<Vec<_>>().join(",") + ">"
        }
        INameI::AnonymousSubstructTemplate(t) => {
            humanize_name(code_map, t.interface.into(), None) + ".anonymous"
        }
        INameI::ForwarderFunction(n) => humanize_name(code_map, INameI::from(n.inner), None),
        INameI::AnonymousSubstructConstructorTemplate(n) => "asc:".to_string() + &humanize_name(code_map, INameI::from(n.substruct), None),
        INameI::AnonymousSubstructConstructor(n) => {
            humanize_name(code_map, INameI::AnonymousSubstructConstructorTemplate(&n.template), None)
                + "<" + &n.template_args.iter().map(|t| humanize_templata(code_map, t)).collect::<Vec<_>>().join(",") + ">"
                + "(" + &n.parameters.iter().map(|c| humanize_coord(code_map, c)).collect::<Vec<_>>().join(",") + ")"
        }
        INameI::Self_(_) => "self".to_string(),
        other => panic!("humanize_name: unimplemented variant {:?}", discriminant(&other)),
    }
}

pub fn humanize_generic_args<'s, 'i, R: Copy + PartialEq>(
    code_map: &dyn Fn(CodeLocationS<'s>) -> String,
    template_args: &[ITemplataI<'s, 'i, R>],
    containing_region: Option<&ITemplataI<'s, 'i, R>>,
) -> String {
    if !template_args.is_empty() {
        let (last, init) = template_args.split_last().unwrap();
        let init_strs: Vec<String> = init.iter().map(|t| humanize_templata(code_map, t)).collect();
        let last_str = match containing_region {
            None => humanize_templata(code_map, last),
            Some(r) => { assert!(r == last); "_".to_string() }
        };
        let mut all = init_strs;
        all.push(last_str);
        "<".to_string() + &all.join(",") + ">"
    } else {
        String::new()
    }
}

pub fn humanize_signature<'s, 'i, R>(
    code_map: &dyn Fn(CodeLocationS<'s>) -> String,
    signature: &'i SignatureI<'s, 'i, R>,
) -> String {
    panic!("Unimplemented: humanize_signature");
}
