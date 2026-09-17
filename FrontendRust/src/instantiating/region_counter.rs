
use crate::instantiating::ast::names::{IdI, INameI, IFunctionNameI, FunctionNameIX, ExternFunctionNameI};
use crate::instantiating::ast::ast::PrototypeI;
use crate::instantiating::ast::types::{CoordI, KindIT};
use crate::instantiating::ast::types::sI;
use crate::instantiating::ast::templata::RegionTemplataI;
use crate::instantiating::ast::names::AnonymousSubstructConstructorNameI;
use crate::instantiating::ast::names::AnonymousSubstructConstructorTemplateNameI;
use crate::instantiating::ast::names::AnonymousSubstructImplNameI;
use crate::instantiating::ast::names::AnonymousSubstructImplTemplateNameI;
use crate::instantiating::ast::names::AnonymousSubstructNameI;
use crate::instantiating::ast::names::AnonymousSubstructTemplateNameI;
use crate::instantiating::ast::names::ForwarderFunctionNameI;
use crate::instantiating::ast::names::ForwarderFunctionTemplateNameI;
use crate::instantiating::ast::names::LambdaCallFunctionNameI;
use crate::instantiating::ast::names::RawArrayNameI;
use crate::instantiating::ast::names::RuntimeSizedArrayNameI;
use crate::instantiating::ast::names::StaticSizedArrayNameI;
use crate::instantiating::ast::types::RuntimeSizedArrayIT;
use crate::instantiating::ast::types::StaticSizedArrayIT;
use std::collections::HashMap;
use std::collections::HashSet;
use std::mem::discriminant;
use crate::instantiating::ast::names::ICitizenNameI;
use crate::instantiating::ast::names::IImplNameI;
use crate::instantiating::ast::names::IImplTemplateNameI;
use crate::instantiating::ast::names::IStructNameI;
use crate::instantiating::ast::names::IStructTemplateNameI;
use crate::instantiating::ast::names::ImplNameI;
use crate::instantiating::ast::names::InterfaceNameI;
use crate::instantiating::ast::names::StructNameI;
use crate::instantiating::ast::templata::ITemplataI;

pub struct CounterI {
    set: HashSet<i32>,
}
impl CounterI {
    pub fn new() -> Self {
        CounterI { set: HashSet::new() }
    }

    pub fn count<'s, 'i>(&mut self, region: RegionTemplataI<sI>) where 's: 'i {
        self.set.insert(region.pure_height);
    }

    pub fn assemble_map(&self) -> HashMap<i32, i32> {
        let num_regions = self.set.len();
        // Let's say we have a set that contains 3, 5, -2, 0, 4, it becomes...
        let mut sorted = self.set.iter().copied().collect::<Vec<i32>>();
        sorted.sort(); // -2, 0, 3, 4, 5
        sorted.into_iter().enumerate() // (-2, 0), (0, 1), (3, 2), (4, 3), (5, 4)
            .map(|(i, subjective_region)| {
                // If we have 4 regions, then they should go from -3 to 0
                (subjective_region, i as i32 - num_regions as i32 + 1)
            }) // (-2, -4), (0, -3), (3, -2), (4, -1), (5, 0)
            .collect()
    }
}

pub fn count_prototype<'s, 'i>(counter: &mut CounterI, prototype: &PrototypeI<'s, 'i, sI>)
where 's: 'i {
    let PrototypeI { id, return_type, .. } = *prototype;
    count_function_id(counter, &id);
    count_coord(counter, &return_type);
}

pub fn count_id<'s, 'i>(counter: &mut CounterI, id_i: &IdI<'s, 'i, sI>, func: impl Fn(&mut CounterI, &INameI<'s, 'i, sI>))
where 's: 'i {
    let IdI { package_coord: _package_coord, init_steps, local_name } = *id_i;
    for x in init_steps {
        count_name(counter, x);
    }
    func(counter, &local_name);
}

pub fn count_function_id<'s, 'i>(counter: &mut CounterI, id: &IdI<'s, 'i, sI>)
where 's: 'i {
    count_id(counter, id, |counter, x| count_function_name(counter, &IFunctionNameI::try_from(*x).unwrap()))
}

pub fn count_function_name<'s, 'i>(counter: &mut CounterI, name: &IFunctionNameI<'s, 'i, sI>)
where 's: 'i {
    match *name {
        IFunctionNameI::Function(n) => {
            let FunctionNameIX { template_args, parameters, .. } = *n;
            for template_arg in template_args { count_templata(counter, template_arg) }
            for param in parameters { count_coord(counter, param) }
        }
        IFunctionNameI::ExternFunction(n) => {
            let ExternFunctionNameI { template_args, parameters, .. } = *n;
            for template_arg in template_args { count_templata(counter, template_arg) }
            for param in parameters { count_coord(counter, param) }
        }
        IFunctionNameI::LambdaCallFunction(n) => {
            let LambdaCallFunctionNameI { template_args, parameters, .. } = *n;
            for template_arg in template_args { count_templata(counter, template_arg) }
            for param in parameters { count_coord(counter, param) }
        }
        IFunctionNameI::AnonymousSubstructConstructor(n) => {
            let AnonymousSubstructConstructorNameI { template: AnonymousSubstructConstructorTemplateNameI { substruct }, template_args, parameters } = *n;
            count_name(counter, &INameI::from(substruct));
            for template_arg in template_args { count_templata(counter, template_arg) }
            for param in parameters { count_coord(counter, param) }
        }
        IFunctionNameI::ForwarderFunction(n) => {
            let ForwarderFunctionNameI { template: ForwarderFunctionTemplateNameI { inner: func_template_name, index: _ }, inner: func_name } = *n;
            count_name(counter, &INameI::from(func_template_name));
            count_function_name(counter, &func_name);
        }
        _ => panic!("Unimplemented: count_function_name other"),
    }
}

pub fn count_citizen_name<'s, 'i>(counter: &mut CounterI, name: &ICitizenNameI<'s, 'i, sI>) {
    match name {
        ICitizenNameI::Struct(StructNameI { template: _, template_args }) => {
            for t in template_args.iter() { count_templata(counter, t); }
        }
        ICitizenNameI::LambdaCitizen(_) => {}
        ICitizenNameI::Interface(InterfaceNameI { template: _, template_args }) => {
            for t in template_args.iter() { count_templata(counter, t); }
        }
        ICitizenNameI::AnonymousSubstruct(AnonymousSubstructNameI { template: AnonymousSubstructTemplateNameI { interface }, template_args }) => {
            count_name(counter, &INameI::from(*interface));
            for t in template_args.iter() { count_templata(counter, t); }
        }
        ICitizenNameI::StaticSizedArray(_) => panic!("count_citizen_name: StaticSizedArray branch (no Scala counterpart)"),
        ICitizenNameI::RuntimeSizedArray(_) => panic!("count_citizen_name: RuntimeSizedArray branch (no Scala counterpart)"),
    }
}

pub fn count_var_name() {
    panic!("Unimplemented: count_var_name");
}

pub fn count_name<'s, 'i>(counter: &mut CounterI, name: &INameI<'s, 'i, sI>)
where 's: 'i {
    match name {
        INameI::OverrideDispatcher(_) | INameI::ExternFunction(_) | INameI::FunctionNameIX(_)
        | INameI::ForwarderFunction(_) | INameI::FunctionBound(_) | INameI::LambdaCallFunction(_)
        | INameI::AnonymousSubstructConstructor(_) => {
            let f: IFunctionNameI<'s, 'i, sI> = (*name).try_into().unwrap();
            count_function_name(counter, &f);
        }
        INameI::Export(export_name) => {
            counter.count(export_name.region);
        }
        INameI::Extern(extern_name) => {
            counter.count(extern_name.region);
        }
        INameI::StructName(_) | INameI::InterfaceName(_) | INameI::LambdaCitizen(_) | INameI::AnonymousSubstruct(_) => {
            let c: ICitizenNameI<'s, 'i, sI> = (*name).try_into().unwrap();
            count_citizen_name(counter, &c);
        }
        INameI::StructTemplate(_) => {}
        INameI::LambdaCitizenTemplate(_) => {}
        INameI::InterfaceTemplate(_) => {}
        INameI::AnonymousSubstructTemplate(AnonymousSubstructTemplateNameI { interface, .. }) => {
            count_name(counter, &INameI::from(*interface));
        }
        INameI::FunctionTemplate(_) => {}
        other => panic!("Unimplemented: count_name {:?}", discriminant(other)),
    }
}

pub fn count_templata<'s, 'i>(_counter: &mut CounterI, _templata: &ITemplataI<'s, 'i, sI>) {
    match _templata {
        ITemplataI::Coord(c) => {
            count_templata(_counter, &ITemplataI::Region(c.region));
            count_coord(_counter, &c.coord);
        }
        ITemplataI::Kind(k) => count_kind(_counter, &k.kind),
        ITemplataI::Region(r) => _counter.count(*r),
        ITemplataI::Mutability(_) => {}
        ITemplataI::Integer(_) => {}
        ITemplataI::Variability(_) => {}
        _ => panic!("count_templata: unimplemented variant"),
    }
}

pub fn count_coord<'s, 'i>(counter: &mut CounterI, coord: &CoordI<'s, 'i, sI>)
where 's: 'i {
    let CoordI { ownership: _ownership, kind } = *coord;
    count_kind(counter, &kind);
}

pub fn count_kind_map() -> HashMap<i32, i32> {
    panic!("Unimplemented: count_kind");
}

pub fn count_kind<'s, 'i>(counter: &mut CounterI, kind: &KindIT<'s, 'i, sI>)
where 's: 'i {
    match kind {
        KindIT::NeverIT(_) => {}
        KindIT::VoidIT(_) => {}
        KindIT::IntIT(_) => {}
        KindIT::BoolIT(_) => {}
        KindIT::FloatIT(_) => {}
        KindIT::StrIT(_) => {}
        KindIT::StructIT(s) => count_struct_id(counter, &s.id),
        KindIT::InterfaceIT(i) => count_interface_id(counter, &i.id),
        KindIT::StaticSizedArrayIT(ssa) => {
            let ssa_id = ssa.name;
            count_id(counter, &ssa_id, |counter, local_name| {
                match local_name {
                    INameI::StaticSizedArray(n) => {
                        let StaticSizedArrayNameI { template: _, size: _, variability: _, arr } = **n;
                        let RawArrayNameI { mutability: _, element_type, self_region } = arr;
                        count_templata(counter, &ITemplataI::Coord(element_type));
                        counter.count(self_region);
                    }
                    _ => panic!("count_kind StaticSizedArray: non-StaticSizedArrayName local name"),
                }
            });
        }
        KindIT::RuntimeSizedArrayIT(rsa) => {
            let rsa_id = rsa.name;
            count_id(counter, &rsa_id, |counter, local_name| {
                match local_name {
                    INameI::RuntimeSizedArray(n) => {
                        let RuntimeSizedArrayNameI { template: _, arr } = **n;
                        let RawArrayNameI { mutability: _, element_type, self_region } = arr;
                        count_templata(counter, &ITemplataI::Coord(element_type));
                        counter.count(self_region);
                    }
                    _ => panic!("count_kind RuntimeSizedArray: non-RuntimeSizedArrayName local name"),
                }
            });
        }
    }
}

pub fn count_runtime_sized_array<'s, 'i>(counter: &mut CounterI, rsa: &RuntimeSizedArrayIT<'s, 'i, sI>) {
    let rsa_id = rsa.name;
    count_id(counter, &rsa_id, |counter, local_name| {
        match local_name {
            INameI::RuntimeSizedArray(n) => {
                let RuntimeSizedArrayNameI { template: _, arr } = **n;
                let RawArrayNameI { mutability: _, element_type, self_region } = arr;
                count_templata(counter, &ITemplataI::Coord(element_type));
                counter.count(self_region);
            }
            _ => panic!("count_runtime_sized_array: non-RuntimeSizedArrayName local name"),
        }
    });
}

pub fn count_static_sized_array<'s, 'i>(counter: &mut CounterI, ssa: &StaticSizedArrayIT<'s, 'i, sI>) {
    let ssa_id = ssa.name;
    count_id(counter, &ssa_id, |counter, local_name| {
        match local_name {
            INameI::StaticSizedArray(n) => {
                let StaticSizedArrayNameI { template: _, size: _, variability: _, arr } = **n;
                let RawArrayNameI { mutability: _, element_type, self_region } = arr;
                count_templata(counter, &ITemplataI::Coord(element_type));
                counter.count(self_region);
            }
            _ => panic!("count_static_sized_array: non-StaticSizedArrayName local name"),
        }
    });
}

pub fn count_citizen_id<'s, 'i>(counter: &mut CounterI, citizen_id: &IdI<'s, 'i, sI>)
where 's: 'i {
    match citizen_id.local_name {
        INameI::StructName(_) | INameI::LambdaCitizen(_) | INameI::AnonymousSubstruct(_) => count_struct_id(counter, citizen_id),
        INameI::InterfaceName(_) => count_interface_id(counter, citizen_id),
        _ => panic!("count_citizen_id: non-citizen local name"),
    }
}

pub fn count_struct_id<'s, 'i>(counter: &mut CounterI, struct_id: &IdI<'s, 'i, sI>)
where 's: 'i {
    count_id(counter, struct_id, |counter, x| count_struct_name(counter, &IStructNameI::try_from(*x).unwrap()))
}

pub fn count_struct_template_name<'s, 'i>(_counter: &mut CounterI, struct_name: &IStructTemplateNameI<'s, 'i, sI>)
where 's: 'i {
    match struct_name {
        IStructTemplateNameI::StructTemplate(_) => {}
        _ => panic!("count_struct_template_name: other"),
    }
}

pub fn count_struct_name<'s, 'i>(counter: &mut CounterI, struct_name: &IStructNameI<'s, 'i, sI>)
where 's: 'i {
    match struct_name {
        IStructNameI::Struct(StructNameI { template, template_args }) => {
            count_struct_template_name(counter, template);
            for t in template_args.iter() { count_templata(counter, t); }
        }
        IStructNameI::LambdaCitizen(_) => {}
        IStructNameI::AnonymousSubstruct(AnonymousSubstructNameI { template: _, template_args }) => {
            for t in template_args.iter() { count_templata(counter, t); }
        }
    }
}

pub fn count_impl_id<'s, 'i>(counter: &mut CounterI, struct_id: &IdI<'s, 'i, sI>)
where 's: 'i {
    count_id(counter, struct_id, |counter, x| count_impl_name(counter, &IImplNameI::try_from(*x).unwrap()))
}

pub fn count_impl_name<'s, 'i>(counter: &mut CounterI, impl_id: &IImplNameI<'s, 'i, sI>)
where 's: 'i {
    match impl_id {
        IImplNameI::Impl(ImplNameI { template, template_args, sub_citizen }) => {
            count_impl_template_name(counter, template);
            for t in template_args.iter() { count_templata(counter, t); }
            count_citizen_id(counter, &sub_citizen.id());
        }
        IImplNameI::AnonymousSubstructImpl(AnonymousSubstructImplNameI { template: AnonymousSubstructImplTemplateNameI { interface }, template_args, sub_citizen }) => {
            count_name(counter, &INameI::from(*interface));
            for t in template_args.iter() { count_templata(counter, t); }
            count_citizen_id(counter, &sub_citizen.id());
        }
        IImplNameI::ImplBound(_) => panic!("count_impl_name: ImplBound branch"),
    }
}

pub fn count_impl_template_name<'s, 'i>(_counter: &mut CounterI, name: &IImplTemplateNameI<'s, 'i, sI>)
where 's: 'i {
    match name {
        IImplTemplateNameI::ImplTemplate(_) => {}
        _ => panic!("count_impl_template_name: other"),
    }
}

pub fn count_export_id<'s, 'i>(id_i: &IdI<'s, 'i, sI>) -> HashMap<i32, i32>
where 's: 'i {
    let mut counter = CounterI::new();
    count_id(&mut counter, id_i, |counter, x| count_name(counter, x));
    counter.assemble_map()
}

pub fn count_extern_id<'s, 'i>(id_i: &IdI<'s, 'i, sI>) -> HashMap<i32, i32>
where 's: 'i {
    let mut counter = CounterI::new();
    count_id(&mut counter, id_i, |counter, x| count_name(counter, x));
    counter.assemble_map()
}

pub fn count_struct_id_map() -> HashMap<i32, i32> {
    panic!("Unimplemented: count_struct_id");
}

pub fn count_interface_id_map() -> HashMap<i32, i32> {
    panic!("Unimplemented: count_interface_id");
}

pub fn count_interface_id<'s, 'i>(counter: &mut CounterI, interface_id: &IdI<'s, 'i, sI>)
where 's: 'i {
    count_id(counter, interface_id, |counter, x| count_name(counter, x))
}

pub fn count_function_id_map() -> HashMap<i32, i32> {
    panic!("Unimplemented: count_function_id");
}

pub fn count_impl_id_map<'s, 'i>(id_i: &IdI<'s, 'i, sI>) -> HashMap<i32, i32>
where 's: 'i {
    let mut counter = CounterI::new();
    count_id(&mut counter, id_i, |counter, x| count_impl_name(counter, &IImplNameI::try_from(*x).unwrap()));
    counter.assemble_map()
}

pub fn count_coord_map() -> HashMap<i32, i32> {
    panic!("Unimplemented: count_coord");
}

pub fn count_var_name_map() -> HashMap<i32, i32> {
    panic!("Unimplemented: count_var_name");
}

pub fn count_static_sized_array_map<'s, 'i>(ssa: &StaticSizedArrayIT<'s, 'i, sI>) -> HashMap<i32, i32> {
    let mut counter = CounterI::new();
    count_static_sized_array(&mut counter, ssa);
    counter.assemble_map()
}

pub fn count_runtime_sized_array_map<'s, 'i>(rsa: &RuntimeSizedArrayIT<'s, 'i, sI>) -> HashMap<i32, i32>
where 's: 'i {
    let mut counter = CounterI::new();
    count_runtime_sized_array(&mut counter, rsa);
    counter.assemble_map()
}

pub fn count_prototype_map<'s, 'i>(prototype: &PrototypeI<'s, 'i, sI>) -> HashMap<i32, i32>
where 's: 'i {
    let mut counter = CounterI::new();
    count_prototype(&mut counter, prototype);
    counter.assemble_map()
}

pub fn count_function_name_map<'s, 'i>(name: &IFunctionNameI<'s, 'i, sI>) -> HashMap<i32, i32>
where 's: 'i {
    let mut counter = CounterI::new();
    count_function_name(&mut counter, name);
    counter.assemble_map()
}

pub fn count_impl_name_map<'s, 'i>(name: &IImplNameI<'s, 'i, sI>) -> HashMap<i32, i32>
where 's: 'i {
    let mut counter = CounterI::new();
    count_impl_name(&mut counter, name);
    counter.assemble_map()
}

pub fn count_citizen_name_map<'s, 'i>(name: &ICitizenNameI<'s, 'i, sI>) -> HashMap<i32, i32> {
    let mut counter = CounterI::new();
    count_citizen_name(&mut counter, name);
    counter.assemble_map()
}

pub fn count_citizen_id_map() -> HashMap<i32, i32> {
    panic!("Unimplemented: count_citizen_id");
}

pub fn count_templata_map() -> HashMap<i32, i32> {
    panic!("Unimplemented: count_templata");
}
