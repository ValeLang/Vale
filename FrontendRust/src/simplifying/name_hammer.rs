//
// Per typing-pass `Compiler` precedent, `NameHammer` is not a Rust struct.
// Its methods live as `impl Hammer { ... }` blocks colocated here; its free
// functions (in Scala's `object NameHammer`) become module-level Rust fns.

use crate::interner::StrI;
use crate::utils::range::CodeLocationS;
use crate::utils::code_hierarchy::{FileCoordinate, PackageCoordinate};
use crate::von::ast::VonObject;
use crate::final_ast::ast::IdH;
use crate::final_ast::types::{SimpleId, SimpleIdStep};
use crate::simplifying::hamuts::Hamuts;
use crate::simplifying::hammer::Hammer;
use crate::instantiating::ast::hinputs::HinputsI;
use crate::instantiating::ast::names::{IdI, INameI};
use crate::instantiating::ast::templata::ITemplataI;
use crate::instantiating::ast::types::{cI, CoordI, KindIT};
use crate::final_ast::ast::IdHValH;
use crate::instantiating::ast::names::IStructTemplateNameI;
use crate::instantiating::ast::names::StructNameI;
use crate::instantiating::ast::types::IntIT;
use crate::instantiating::ast::types::OwnershipI;
use crate::instantiating::instantiated_humanizer::humanize_id;
use crate::instantiating::instantiated_humanizer::humanize_name;
use crate::scout_arena::ScoutArena;
use crate::simplifying::hammer_interner::HammerInterner;
use crate::von::ast::IVonData;
use crate::von::ast::VonArray;
use crate::von::ast::VonMember;
use crate::von::ast::VonStr;
use std::marker::PhantomData;
use std::mem::discriminant;

impl<'s, 'i, 'h, 'ctx> Hammer<'s, 'i, 'h, 'ctx>
where 's: 'h, 's: 'i, 'i: 'h,
{
    pub fn translate_full_name(
        &self,
        hinputs: &HinputsI<'s, 'i>,
        hamuts: &Hamuts<'s, 'i, 'h>,
        full_name2: &IdI<'s, 'i, cI>,
    ) -> &'h IdH<'s>
    {
        let IdI { package_coord, init_steps: _, local_name: local_name_t } = full_name2;
        let code_map = |loc: CodeLocationS<'s>| format!("{:?}", loc);
        let long_name = humanize_id(&code_map, full_name2, None);
        let local_name = humanize_name(&code_map, *local_name_t, None);
        self.interner.intern_id_h(IdHValH {
            local_name: self.scout_arena.intern_str(&local_name),
            package_coordinate: **package_coord,
            shortened_name: self.scout_arena.intern_str(&long_name),
            fully_qualified_name: self.scout_arena.intern_str(&long_name),
        })
    }

    pub fn add_step(
        &self,
        _hamuts: &Hamuts<'s, 'i, 'h>,
        full_name: &IdH<'s>,
        s: StrI<'s>,
    ) -> &'h IdH<'s>
    {
        let IdH { package_coordinate, shortened_name, fully_qualified_name, .. } = *full_name;
        let new_shortened = format!("{}.{}", shortened_name.0, s.0);
        let new_fqn = format!("{}.{}", fully_qualified_name.0, s.0);
        self.interner.intern_id_h(IdHValH {
            local_name: s,
            package_coordinate,
            shortened_name: self.scout_arena.intern_str(&new_shortened),
            fully_qualified_name: self.scout_arena.intern_str(&new_fqn),
        })
    }
}

pub fn translate_code_location<'p>(location: &CodeLocationS<'p>) -> VonObject {
    panic!("Unimplemented: translate_code_location");
}

pub fn translate_file_coordinate<'p>(coord: &FileCoordinate<'p>) -> VonObject {
    panic!("Unimplemented: translate_file_coordinate");
}

pub fn translate_package_coordinate<'p>(coord: &PackageCoordinate<'p>) -> VonObject {
    let PackageCoordinate { module, packages: paackage } = coord;
    let non_empty_module_name = if module.0 == "" { "__vale".to_string() } else { module.0.to_string() };
    VonObject {
        tyype: "PackageCoordinate".to_string(),
        id: None,
        members: vec![
            VonMember {
                field_name: "project".to_string(),
                value: IVonData::Str(VonStr { value: non_empty_module_name }),
            },
            VonMember {
                field_name: "packageSteps".to_string(),
                value: IVonData::Array(VonArray {
                    id: None,
                    members: paackage.iter().map(|s| IVonData::Str(VonStr { value: s.0.to_string() })).collect(),
                }),
            },
        ],
    }
}

pub fn simplify_id<'s, 'i, 'h>(interner: &HammerInterner<'s, 'h>, scout_arena: &ScoutArena<'s>, id: &IdI<'s, 'i, cI>) -> SimpleId<'s, 'h>
where 's: 'i, 'i: 'h,
{
    let IdI { package_coord, init_steps, local_name } = id;
    let PackageCoordinate { module, packages } = **package_coord;
    let mut steps: Vec<SimpleIdStep<'s, 'h>> = Vec::new();
    steps.push(SimpleIdStep { name: module, template_args: &[] });
    for paackage in packages.iter() {
        steps.push(SimpleIdStep { name: *paackage, template_args: &[] });
    }
    for step in init_steps.iter() {
        steps.push(simplify_name(interner, scout_arena, step));
    }
    steps.push(simplify_name(interner, scout_arena, local_name));
    SimpleId { steps: interner.alloc_slice_from_vec(steps) }
}

pub fn simplify_name<'s, 'i, 'h>(interner: &HammerInterner<'s, 'h>, scout_arena: &ScoutArena<'s>, name: &INameI<'s, 'i, cI>) -> SimpleIdStep<'s, 'h>
where 's: 'i, 'i: 'h,
{
    match name {
        INameI::StructName(StructNameI { template: IStructTemplateNameI::StructTemplate(t), template_args }) => SimpleIdStep {
            name: t.human_name,
            template_args: interner.alloc_slice_from_vec(template_args.iter().map(|t| simplify_templata(interner, scout_arena, t)).collect()),
        },
        INameI::StructName(_) => panic!("simplify_name: StructName non-StructTemplate inner"),
        INameI::StructTemplate(s) => SimpleIdStep {
            name: s.human_name,
            template_args: &[],
        },
        INameI::InterfaceName(i) => panic!("simplify_name: InterfaceName branch"),
        INameI::InterfaceTemplate(i) => panic!("simplify_name: InterfaceTemplate branch"),
        INameI::ExternFunction(f) => SimpleIdStep {
            name: f.human_name,
            template_args: interner.alloc_slice_from_vec(f.template_args.iter().map(|t| simplify_templata(interner, scout_arena, t)).collect()),
        },
        other => panic!("simplify_name: unimplemented variant {:?}", discriminant(other)),
    }
}

pub fn simplify_templata<'s, 'i, 'h>(interner: &HammerInterner<'s, 'h>, scout_arena: &ScoutArena<'s>, templata: &ITemplataI<'s, 'i, cI>) -> SimpleId<'s, 'h>
where 's: 'i, 'i: 'h,
{
    match templata {
        ITemplataI::Coord(c) => simplify_coord(interner, scout_arena, &c.coord),
        other => panic!("simplify_templata: unimplemented variant {:?}", discriminant(other)),
    }
}

pub fn simplify_kind<'s, 'i, 'h>(interner: &HammerInterner<'s, 'h>, scout_arena: &ScoutArena<'s>, value: &KindIT<'s, 'i, cI>) -> SimpleId<'s, 'h>
where 's: 'i, 'i: 'h,
{
    match value {
        KindIT::IntIT(IntIT { bits, .. }) => {
            let name = scout_arena.intern_str(&format!("i{}", bits));
            SimpleId { steps: interner.alloc_slice_from_vec(vec![SimpleIdStep { name, template_args: &[] }]) }
        }
        KindIT::StrIT(_) => {
            let name = scout_arena.intern_str("str");
            SimpleId { steps: interner.alloc_slice_from_vec(vec![SimpleIdStep { name, template_args: &[] }]) }
        }
        other => panic!("simplify_kind: unimplemented variant {:?}", discriminant(other)),
    }
}

pub fn simplify_coord<'s, 'i, 'h>(interner: &HammerInterner<'s, 'h>, scout_arena: &ScoutArena<'s>, value: &CoordI<'s, 'i, cI>) -> SimpleId<'s, 'h>
where 's: 'i, 'i: 'h,
{
    let CoordI { ownership, kind } = *value;
    let kind_id = simplify_kind(interner, scout_arena, &kind);
    match ownership {
        OwnershipI::ImmutableShare => kind_id,
        OwnershipI::MutableShare => kind_id,
        OwnershipI::Own => kind_id,
        OwnershipI::Weak => panic!("simplify_coord: Weak"),
        OwnershipI::ImmutableBorrow => panic!("simplify_coord: ImmutableBorrow"),
        OwnershipI::MutableBorrow => panic!("simplify_coord: MutableBorrow"),
    }
}

