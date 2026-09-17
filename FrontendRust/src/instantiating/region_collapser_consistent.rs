
use crate::instantiating::instantiating_interner::InstantiatingInterner;
use crate::instantiating::ast::ast::{PrototypeI, PrototypeIValI};
use crate::instantiating::ast::names::{IdI, INameI, IFunctionNameI, FunctionNameIX, FunctionTemplateNameI};
use crate::instantiating::ast::types::{sI, nI, BoolIT, CoordI, FloatIT, IntIT, KindIT, StrIT, VoidIT};
use crate::instantiating::ast::templata::ITemplataI;
use std::collections::HashMap;
use crate::instantiating::ast::names::AnonymousSubstructConstructorNameI;
use crate::instantiating::ast::names::AnonymousSubstructConstructorTemplateNameI;
use crate::instantiating::ast::names::AnonymousSubstructImplNameI;
use crate::instantiating::ast::names::AnonymousSubstructImplTemplateNameI;
use crate::instantiating::ast::names::AnonymousSubstructNameI;
use crate::instantiating::ast::names::AnonymousSubstructTemplateNameI;
use crate::instantiating::ast::names::ForwarderFunctionNameI;
use crate::instantiating::ast::names::ForwarderFunctionTemplateNameI;
use crate::instantiating::ast::names::LambdaCallFunctionNameI;
use crate::instantiating::ast::names::LambdaCallFunctionTemplateNameI;
use crate::instantiating::ast::names::LambdaCitizenNameI;
use crate::instantiating::ast::names::LambdaCitizenTemplateNameI;
use crate::instantiating::ast::names::RawArrayNameI;
use crate::instantiating::ast::names::RuntimeSizedArrayNameI;
use crate::instantiating::ast::names::RuntimeSizedArrayTemplateNameI;
use crate::instantiating::ast::names::StaticSizedArrayNameI;
use crate::instantiating::ast::names::StaticSizedArrayTemplateNameI;
use crate::instantiating::ast::templata::CoordTemplataI;
use crate::instantiating::ast::templata::KindTemplataI;
use crate::instantiating::ast::templata::RegionTemplataI;
use crate::instantiating::ast::templata::expect_coord_templata;
use crate::instantiating::ast::types::NeverIT;
use crate::instantiating::ast::types::RuntimeSizedArrayIT;
use crate::instantiating::ast::types::StaticSizedArrayIT;
use std::marker::PhantomData;
use std::mem::discriminant;
use crate::instantiating::ast::names::ICitizenTemplateNameI;
use crate::instantiating::ast::names::IFunctionTemplateNameI;
use crate::instantiating::ast::names::IImplNameI;
use crate::instantiating::ast::names::IImplTemplateNameI;
use crate::instantiating::ast::names::IInterfaceNameI;
use crate::instantiating::ast::names::IInterfaceTemplateNameI;
use crate::instantiating::ast::names::IStructNameI;
use crate::instantiating::ast::names::IStructTemplateNameI;
use crate::instantiating::ast::names::ImplNameI;
use crate::instantiating::ast::names::ImplTemplateNameI;
use crate::instantiating::ast::names::InterfaceNameI;
use crate::instantiating::ast::names::InterfaceTemplateNameI;
use crate::instantiating::ast::names::StructNameI;
use crate::instantiating::ast::names::StructTemplateNameI;
use crate::instantiating::ast::templata::IntegerTemplataI;
use crate::instantiating::ast::templata::MutabilityTemplataI;
use crate::instantiating::ast::templata::VariabilityTemplataI;
use crate::instantiating::ast::types::ICitizenIT;
use crate::instantiating::ast::types::InterfaceITValI;
use crate::instantiating::ast::types::StructITValI;

pub fn collapse_prototype<'s, 'i>(interner: &InstantiatingInterner<'s, 'i>, map: HashMap<i32, i32>, prototype: &PrototypeI<'s, 'i, sI>) -> PrototypeI<'s, 'i, nI>
where 's: 'i {
    let PrototypeI { id, return_type, .. } = *prototype;
    *interner.intern_prototype_ni(PrototypeIValI {
        id: collapse_function_id(interner, &map, &id),
        return_type: collapse_coord(interner, &map, &return_type),
    })
}

pub fn collapse_id<'s, 'i>(interner: &InstantiatingInterner<'s, 'i>, map: &HashMap<i32, i32>, id_i: &IdI<'s, 'i, sI>, func: impl Fn(&INameI<'s, 'i, sI>) -> INameI<'s, 'i, nI>) -> IdI<'s, 'i, nI>
where 's: 'i {
    let init_steps_c = id_i.init_steps.iter().map(|x| collapse_name(interner, map, x)).collect::<Vec<_>>();
    IdI {
        package_coord: id_i.package_coord,
        init_steps: interner.alloc_slice_from_vec(init_steps_c),
        local_name: func(&id_i.local_name),
    }
}

pub fn collapse_function_id<'s, 'i>(interner: &InstantiatingInterner<'s, 'i>, map: &HashMap<i32, i32>, id: &IdI<'s, 'i, sI>) -> IdI<'s, 'i, nI>
where 's: 'i {
    collapse_id(interner, map, id, |x| INameI::from(collapse_function_name(interner, map, &IFunctionNameI::try_from(*x).unwrap())))
}

pub fn collapse_function_name<'s, 'i>(interner: &InstantiatingInterner<'s, 'i>, map: &HashMap<i32, i32>, name: &IFunctionNameI<'s, 'i, sI>) -> IFunctionNameI<'s, 'i, nI>
where 's: 'i {
    match *name {
        IFunctionNameI::Function(n) => {
            let FunctionNameIX { template: FunctionTemplateNameI { human_name, code_location, .. }, template_args, parameters, .. } = *n;
            let template_c = FunctionTemplateNameI { _marker: PhantomData, human_name, code_location };
            let template_args_c = interner.alloc_slice_from_vec(template_args.iter().map(|template_arg| collapse_templata(interner, map, template_arg)).collect::<Vec<_>>());
            let params_c = interner.alloc_slice_from_vec(parameters.iter().map(|param| collapse_coord(interner, map, param)).collect::<Vec<_>>());
            IFunctionNameI::Function(interner.intern_function_name_x_ni(FunctionNameIX { template: template_c, template_args: template_args_c, parameters: params_c }))
        }
        IFunctionNameI::ExternFunction(_) => panic!("Unimplemented: collapse_function_name ExternFunction"),
        IFunctionNameI::LambdaCallFunction(n) => {
            let LambdaCallFunctionNameI { template: LambdaCallFunctionTemplateNameI { code_location, param_types: params_tt, .. }, template_args, parameters } = *n;
            let params_tt_c = interner.alloc_slice_from_vec(params_tt.iter().map(|p| collapse_coord(interner, map, p)).collect::<Vec<_>>());
            let template_c = *interner.intern_lambda_call_function_template_name_ni(LambdaCallFunctionTemplateNameI { _marker: PhantomData, code_location, param_types: params_tt_c });
            let template_args_c = interner.alloc_slice_from_vec(template_args.iter().map(|t| collapse_templata(interner, map, t)).collect::<Vec<_>>());
            let params_c = interner.alloc_slice_from_vec(parameters.iter().map(|p| collapse_coord(interner, map, p)).collect::<Vec<_>>());
            IFunctionNameI::LambdaCallFunction(interner.intern_lambda_call_function_name_ni(LambdaCallFunctionNameI { template: template_c, template_args: template_args_c, parameters: params_c }))
        }
        IFunctionNameI::AnonymousSubstructConstructor(n) => {
            let AnonymousSubstructConstructorNameI { template: AnonymousSubstructConstructorTemplateNameI { substruct }, template_args, parameters } = *n;
            let template_c = AnonymousSubstructConstructorTemplateNameI { substruct: collapse_citizen_template_name(interner, &substruct) };
            let template_args_c = interner.alloc_slice_from_vec(template_args.iter().map(|t| collapse_templata(interner, map, t)).collect::<Vec<_>>());
            let params_c = interner.alloc_slice_from_vec(parameters.iter().map(|p| collapse_coord(interner, map, p)).collect::<Vec<_>>());
            IFunctionNameI::AnonymousSubstructConstructor(interner.intern_anonymous_substruct_constructor_name_ni(AnonymousSubstructConstructorNameI { template: template_c, template_args: template_args_c, parameters: params_c }))
        }
        IFunctionNameI::ForwarderFunction(n) => {
            let ForwarderFunctionNameI { template: ForwarderFunctionTemplateNameI { inner: inner_template, index }, inner: func_name } = *n;
            IFunctionNameI::ForwarderFunction(interner.intern_forwarder_function_name_ni(ForwarderFunctionNameI {
                template: *interner.intern_forwarder_function_template_name_ni(ForwarderFunctionTemplateNameI {
                    inner: collapse_function_template_name(interner, &inner_template),
                    index,
                }),
                inner: collapse_function_name(interner, map, &func_name),
            }))
        }
        _ => panic!("Unimplemented: collapse_function_name other"),
    }
}

pub fn collapse_var_name() { panic!("Unimplemented: collapse_var_name"); }

pub fn collapse_name<'s, 'i>(interner: &InstantiatingInterner<'s, 'i>, map: &HashMap<i32, i32>, name: &INameI<'s, 'i, sI>) -> INameI<'s, 'i, nI>
where 's: 'i {
    match name {
        INameI::StructTemplate(x) => collapse_struct_template_name(interner, &IStructTemplateNameI::StructTemplate(x)).into(),
        INameI::LambdaCitizenTemplate(x) => collapse_struct_template_name(interner, &IStructTemplateNameI::LambdaCitizenTemplate(x)).into(),
        INameI::AnonymousSubstructTemplate(x) => collapse_struct_template_name(interner, &IStructTemplateNameI::AnonymousSubstructTemplate(x)).into(),
        INameI::InterfaceTemplate(x) => collapse_interface_template_name(interner, &IInterfaceTemplateNameI::InterfaceTemplate(x)).into(),
        INameI::OverrideDispatcherTemplate(_) | INameI::FunctionBoundTemplate(_) | INameI::FunctionTemplate(_)
        | INameI::LambdaCallFunctionTemplate(_) | INameI::ForwarderFunctionTemplate(_) | INameI::ConstructorTemplate(_)
        | INameI::AnonymousSubstructConstructorTemplate(_) => {
            panic!("Unimplemented: collapse_name IFunctionTemplateNameI (collapse_function_template_name is a stub)")
        }
        INameI::OverrideDispatcher(_) | INameI::ExternFunction(_) | INameI::FunctionNameIX(_)
        | INameI::ForwarderFunction(_) | INameI::FunctionBound(_) | INameI::LambdaCallFunction(_)
        | INameI::AnonymousSubstructConstructor(_) => {
            let f: IFunctionNameI<'s, 'i, sI> = (*name).try_into().unwrap();
            collapse_function_name(interner, map, &f).into()
        }
        INameI::StructName(_) => {
            let s: IStructNameI<'s, 'i, sI> = (*name).try_into().unwrap();
            collapse_struct_name(interner, map, &s).into()
        }
        other => panic!("Unimplemented: collapse_name {:?}", discriminant(other)),
    }
}

pub fn collapse_coord_templata<'s, 'i>(interner: &InstantiatingInterner<'s, 'i>, map: &HashMap<i32, i32>, templata: CoordTemplataI<'s, 'i, sI>) -> CoordTemplataI<'s, 'i, nI>
where 's: 'i {
    let CoordTemplataI { region, coord } = templata;
    CoordTemplataI {
        region: collapse_region_templata(map, region),
        coord: collapse_coord(interner, map, &coord),
    }
}

pub fn collapse_templata<'s, 'i>(interner: &InstantiatingInterner<'s, 'i>, map: &HashMap<i32, i32>, templata: &ITemplataI<'s, 'i, sI>) -> ITemplataI<'s, 'i, nI>
where 's: 'i {
    match templata {
        ITemplataI::Coord(c) => ITemplataI::Coord(collapse_coord_templata(interner, map, *c)),
        ITemplataI::Kind(k) => ITemplataI::Kind(KindTemplataI { kind: collapse_kind(interner, map, &k.kind) }),
        ITemplataI::Region(r) => ITemplataI::Region(collapse_region_templata(map, *r)),
        ITemplataI::Mutability(m) => ITemplataI::Mutability(MutabilityTemplataI { mutability: m.mutability, _marker: PhantomData }),
        ITemplataI::Integer(i) => ITemplataI::Integer(IntegerTemplataI { value: i.value, _marker: PhantomData }),
        ITemplataI::Variability(v) => ITemplataI::Variability(VariabilityTemplataI { variability: v.variability, _marker: PhantomData }),
        _ => panic!("collapse_templata: unimplemented variant"),
    }
}

pub fn collapse_region_templata<'s, 'i>(map: &HashMap<i32, i32>, templata: RegionTemplataI<sI>) -> RegionTemplataI<nI>
where 's: 'i {
    let RegionTemplataI { pure_height: old_pure_height, .. } = templata;
    RegionTemplataI { pure_height: *map.get(&old_pure_height).expect("collapse_region_templata: missing"), _marker: PhantomData }
}

pub fn collapse_coord<'s, 'i>(interner: &InstantiatingInterner<'s, 'i>, map: &HashMap<i32, i32>, coord: &CoordI<'s, 'i, sI>) -> CoordI<'s, 'i, nI>
where 's: 'i {
    let CoordI { ownership, kind } = *coord;
    CoordI { ownership, kind: collapse_kind(interner, map, &kind) }
}

pub fn collapse_kind<'s, 'i>(interner: &InstantiatingInterner<'s, 'i>, map: &HashMap<i32, i32>, kind: &KindIT<'s, 'i, sI>) -> KindIT<'s, 'i, nI>
where 's: 'i {
    match kind {
        KindIT::NeverIT(n) => KindIT::NeverIT(NeverIT { from_break: n.from_break, _marker: PhantomData }),
        KindIT::VoidIT(_) => KindIT::VoidIT(VoidIT { _marker: PhantomData }),
        KindIT::IntIT(x) => KindIT::IntIT(IntIT { bits: x.bits, _marker: PhantomData }),
        KindIT::BoolIT(_) => KindIT::BoolIT(BoolIT { _marker: PhantomData }),
        KindIT::FloatIT(_) => KindIT::FloatIT(FloatIT { _marker: PhantomData }),
        KindIT::StrIT(_) => KindIT::StrIT(StrIT { _marker: PhantomData }),
        KindIT::StructIT(s) => KindIT::StructIT(interner.intern_struct_it_ni(StructITValI { id: collapse_struct_id(interner, map, &s.id) })),
        KindIT::InterfaceIT(i) => KindIT::InterfaceIT(interner.intern_interface_it_ni(InterfaceITValI { id: collapse_interface_id(interner, map, &i.id) })),
        KindIT::StaticSizedArrayIT(ssa) => KindIT::StaticSizedArrayIT(interner.alloc(collapse_static_sized_array(interner, map, ssa))),
        KindIT::RuntimeSizedArrayIT(rsa) => KindIT::RuntimeSizedArrayIT(interner.alloc(collapse_runtime_sized_array(interner, map, rsa))),
    }
}

pub fn collapse_runtime_sized_array<'s, 'i>(interner: &InstantiatingInterner<'s, 'i>, map: &HashMap<i32, i32>, rsa: &RuntimeSizedArrayIT<'s, 'i, sI>) -> RuntimeSizedArrayIT<'s, 'i, nI>
where 's: 'i {
    let rsa_id = rsa.name;
    let collapsed_id = collapse_id(interner, map, &rsa_id, |local_name| {
        match local_name {
            INameI::RuntimeSizedArray(n) => {
                let RuntimeSizedArrayNameI { template: _, arr } = **n;
                let RawArrayNameI { mutability, element_type, self_region } = arr;
                INameI::RuntimeSizedArray(interner.alloc(RuntimeSizedArrayNameI {
                    template: RuntimeSizedArrayTemplateNameI(PhantomData),
                    arr: RawArrayNameI {
                        mutability,
                        element_type: expect_coord_templata(collapse_templata(interner, map, &ITemplataI::Coord(element_type))),
                        self_region: collapse_region_templata(map, self_region),
                    },
                }))
            }
            _ => panic!("collapse_runtime_sized_array: non-RuntimeSizedArrayName local name"),
        }
    });
    *interner.intern_runtime_sized_array_it_ni(crate::instantiating::ast::types::RuntimeSizedArrayITValI { name: collapsed_id })
}

pub fn collapse_static_sized_array<'s, 'i>(interner: &InstantiatingInterner<'s, 'i>, map: &HashMap<i32, i32>, ssa: &StaticSizedArrayIT<'s, 'i, sI>) -> StaticSizedArrayIT<'s, 'i, nI>
where 's: 'i {
    let ssa_id = ssa.name;
    let collapsed_id = collapse_id(interner, map, &ssa_id, |local_name| {
        match local_name {
            INameI::StaticSizedArray(n) => {
                let StaticSizedArrayNameI { template: _, size, variability, arr } = **n;
                let RawArrayNameI { mutability, element_type, self_region } = arr;
                INameI::StaticSizedArray(interner.alloc(StaticSizedArrayNameI {
                    template: StaticSizedArrayTemplateNameI(PhantomData),
                    size,
                    variability,
                    arr: RawArrayNameI {
                        mutability,
                        element_type: expect_coord_templata(collapse_templata(interner, map, &ITemplataI::Coord(element_type))),
                        self_region: collapse_region_templata(map, self_region),
                    },
                }))
            }
            _ => panic!("collapse_static_sized_array: non-StaticSizedArrayName local name"),
        }
    });
    *interner.intern_static_sized_array_it_ni(crate::instantiating::ast::types::StaticSizedArrayITValI { name: collapsed_id })
}

pub fn collapse_citizen<'s, 'i>(interner: &InstantiatingInterner<'s, 'i>, map: &HashMap<i32, i32>, citizen: &ICitizenIT<'s, 'i, sI>) -> ICitizenIT<'s, 'i, nI>
where 's: 'i {
    match citizen {
        ICitizenIT::StructIT(s) => ICitizenIT::StructIT(interner.intern_struct_it_ni(StructITValI { id: collapse_struct_id(interner, map, &s.id) })),
        ICitizenIT::InterfaceIT(i) => ICitizenIT::InterfaceIT(interner.intern_interface_it_ni(InterfaceITValI { id: collapse_interface_id(interner, map, &i.id) })),
    }
}

pub fn collapse_citizen_id() { panic!("Unimplemented: collapse_citizen_id"); }

pub fn collapse_citizen_name() { panic!("Unimplemented: collapse_citizen_name"); }

pub fn collapse_struct_name<'s, 'i>(interner: &InstantiatingInterner<'s, 'i>, map: &HashMap<i32, i32>, struct_name: &IStructNameI<'s, 'i, sI>) -> IStructNameI<'s, 'i, nI>
where 's: 'i {
    match struct_name {
        IStructNameI::Struct(StructNameI { template, template_args }) => {
            let template_c = collapse_struct_template_name(interner, template);
            let template_args_c: Vec<ITemplataI<'s, 'i, nI>> = template_args.iter().map(|t| collapse_templata(interner, map, t)).collect();
            IStructNameI::Struct(interner.intern_struct_name_ni(StructNameI {
                template: template_c,
                template_args: interner.bump().alloc_slice_fill_iter(template_args_c.into_iter()),
            }))
        }
        IStructNameI::LambdaCitizen(LambdaCitizenNameI { template: LambdaCitizenTemplateNameI { code_location, _marker: _ } }) => {
            IStructNameI::LambdaCitizen(interner.intern_lambda_citizen_name_ni(LambdaCitizenNameI {
                template: *interner.intern_lambda_citizen_template_name_ni(LambdaCitizenTemplateNameI { _marker: PhantomData, code_location: *code_location }),
            }))
        }
        IStructNameI::AnonymousSubstruct(AnonymousSubstructNameI { template: AnonymousSubstructTemplateNameI { interface }, template_args }) => {
            let template_args_c: Vec<ITemplataI<'s, 'i, nI>> = template_args.iter().map(|t| collapse_templata(interner, map, t)).collect();
            IStructNameI::AnonymousSubstruct(interner.intern_anonymous_substruct_name_ni(AnonymousSubstructNameI {
                template: *interner.intern_anonymous_substruct_template_name_ni(AnonymousSubstructTemplateNameI {
                    interface: collapse_interface_template_name(interner, interface),
                }),
                template_args: interner.bump().alloc_slice_fill_iter(template_args_c.into_iter()),
            }))
        }
    }
}

pub fn collapse_struct_id<'s, 'i>(interner: &InstantiatingInterner<'s, 'i>, map: &HashMap<i32, i32>, struct_id: &IdI<'s, 'i, sI>) -> IdI<'s, 'i, nI>
where 's: 'i {
    collapse_id(interner, map, struct_id, |x| {
        let narrowed: IStructNameI<'s, 'i, sI> = (*x).try_into().unwrap();
        collapse_struct_name(interner, map, &narrowed).into()
    })
}

pub fn collapse_interface_name<'s, 'i>(interner: &InstantiatingInterner<'s, 'i>, map: &HashMap<i32, i32>, interface_name: &IInterfaceNameI<'s, 'i, sI>) -> IInterfaceNameI<'s, 'i, nI>
where 's: 'i {
    match interface_name {
        IInterfaceNameI::Interface(InterfaceNameI { template, template_args }) => {
            let template_c = collapse_interface_template_name(interner, template);
            let template_args_c: Vec<ITemplataI<'s, 'i, nI>> = template_args.iter().map(|t| collapse_templata(interner, map, t)).collect();
            IInterfaceNameI::Interface(interner.intern_interface_name_ni(InterfaceNameI {
                template: template_c,
                template_args: interner.bump().alloc_slice_fill_iter(template_args_c.into_iter()),
            }))
        }
    }
}

pub fn collapse_interface_id<'s, 'i>(interner: &InstantiatingInterner<'s, 'i>, map: &HashMap<i32, i32>, interface_id: &IdI<'s, 'i, sI>) -> IdI<'s, 'i, nI>
where 's: 'i {
    collapse_id(interner, map, interface_id, |x| {
        match x {
            INameI::InterfaceName(i) => INameI::InterfaceName(match collapse_interface_name(interner, map, &IInterfaceNameI::Interface(i)) {
                IInterfaceNameI::Interface(r) => r,
            }),
            _ => panic!("collapse_interface_id: non-InterfaceName local name"),
        }
    })
}

pub fn collapse_export_id() { panic!("Unimplemented: collapse_export_id"); }

pub fn collapse_extern_id() { panic!("Unimplemented: collapse_extern_id"); }

pub fn collapse_citizen_template_name<'s, 'i>(interner: &InstantiatingInterner<'s, 'i>, citizen_name: &ICitizenTemplateNameI<'s, 'i, sI>) -> ICitizenTemplateNameI<'s, 'i, nI> where 's: 'i {
    match citizen_name {
        ICitizenTemplateNameI::StructTemplate(_) | ICitizenTemplateNameI::LambdaCitizenTemplate(_) | ICitizenTemplateNameI::AnonymousSubstructTemplate(_) => {
            let s: IStructTemplateNameI<'s, 'i, sI> = (*citizen_name).try_into().unwrap();
            collapse_struct_template_name(interner, &s).into()
        }
        ICitizenTemplateNameI::InterfaceTemplate(_) => {
            let i: IInterfaceTemplateNameI<'s, 'i, sI> = (*citizen_name).try_into().unwrap();
            ICitizenTemplateNameI::from(collapse_interface_template_name(interner, &i))
        }
        _ => panic!("Unimplemented: collapse_citizen_template_name other"),
    }
}

pub fn collapse_struct_template_name<'s, 'i>(interner: &InstantiatingInterner<'s, 'i>, struct_name: &IStructTemplateNameI<'s, 'i, sI>) -> IStructTemplateNameI<'s, 'i, nI>
where 's: 'i {
    match struct_name {
        IStructTemplateNameI::StructTemplate(StructTemplateNameI { human_name, .. }) => IStructTemplateNameI::StructTemplate(interner.intern_struct_template_name_ni(StructTemplateNameI { _marker: PhantomData, human_name: *human_name })),
        IStructTemplateNameI::AnonymousSubstructTemplate(AnonymousSubstructTemplateNameI { interface }) => IStructTemplateNameI::AnonymousSubstructTemplate(interner.intern_anonymous_substruct_template_name_ni(AnonymousSubstructTemplateNameI {
            interface: collapse_interface_template_name(interner, interface),
        })),
        IStructTemplateNameI::LambdaCitizenTemplate(LambdaCitizenTemplateNameI { code_location, .. }) => {
            IStructTemplateNameI::LambdaCitizenTemplate(interner.intern_lambda_citizen_template_name_ni(LambdaCitizenTemplateNameI { _marker: PhantomData, code_location: *code_location }))
        }
    }
}

pub fn collapse_function_template_name<'s, 'i>(interner: &InstantiatingInterner<'s, 'i>, function_name: &IFunctionTemplateNameI<'s, 'i, sI>) -> IFunctionTemplateNameI<'s, 'i, nI> where 's: 'i {
    match function_name {
        IFunctionTemplateNameI::FunctionTemplate(FunctionTemplateNameI { human_name, code_location, .. }) => IFunctionTemplateNameI::FunctionTemplate(interner.intern_function_template_name_ni(FunctionTemplateNameI { _marker: PhantomData, human_name: *human_name, code_location: *code_location })),
        _ => panic!("Unimplemented: collapse_function_template_name other"),
    }
}

pub fn collapse_interface_template_name<'s, 'i>(interner: &InstantiatingInterner<'s, 'i>, name: &IInterfaceTemplateNameI<'s, 'i, sI>) -> IInterfaceTemplateNameI<'s, 'i, nI>
where 's: 'i {
    match name {
        IInterfaceTemplateNameI::InterfaceTemplate(InterfaceTemplateNameI { human_namee, .. }) => {
            IInterfaceTemplateNameI::InterfaceTemplate(interner.intern_interface_template_name_ni(InterfaceTemplateNameI { _marker: PhantomData, human_namee: *human_namee }))
        }
    }
}

pub fn collapse_impl_name<'s, 'i>(interner: &InstantiatingInterner<'s, 'i>, map: &HashMap<i32, i32>, name: &IImplNameI<'s, 'i, sI>) -> IImplNameI<'s, 'i, nI>
where 's: 'i {
    match name {
        IImplNameI::Impl(ImplNameI { template, template_args, sub_citizen }) => {
            let template_c = collapse_impl_template_name(interner, map, template);
            let template_args_c: Vec<ITemplataI<'s, 'i, nI>> = template_args.iter().map(|t| collapse_templata(interner, map, t)).collect();
            let sub_citizen_c = collapse_citizen(interner, map, sub_citizen);
            IImplNameI::Impl(interner.intern_impl_name_ni(ImplNameI {
                template: template_c,
                template_args: interner.bump().alloc_slice_fill_iter(template_args_c.into_iter()),
                sub_citizen: sub_citizen_c,
            }))
        }
        IImplNameI::AnonymousSubstructImpl(AnonymousSubstructImplNameI { template: AnonymousSubstructImplTemplateNameI { interface }, template_args, sub_citizen }) => {
            let template_args_c: Vec<ITemplataI<'s, 'i, nI>> = template_args.iter().map(|t| collapse_templata(interner, map, t)).collect();
            let sub_citizen_c = collapse_citizen(interner, map, sub_citizen);
            IImplNameI::AnonymousSubstructImpl(interner.intern_anonymous_substruct_impl_name_ni(AnonymousSubstructImplNameI {
                template: AnonymousSubstructImplTemplateNameI {
                    interface: collapse_interface_template_name(interner, interface),
                },
                template_args: interner.bump().alloc_slice_fill_iter(template_args_c.into_iter()),
                sub_citizen: sub_citizen_c,
            }))
        }
        IImplNameI::ImplBound(_) => panic!("collapse_impl_name: ImplBound branch"),
    }
}

pub fn collapse_impl_id<'s, 'i>(interner: &InstantiatingInterner<'s, 'i>, map: &HashMap<i32, i32>, impl_id: &IdI<'s, 'i, sI>) -> IdI<'s, 'i, nI>
where 's: 'i {
    collapse_id(interner, map, impl_id, |x| {
        let narrowed: IImplNameI<'s, 'i, sI> = (*x).try_into().unwrap();
        collapse_impl_name(interner, map, &narrowed).into()
    })
}

pub fn collapse_impl_template_id() { panic!("Unimplemented: collapse_impl_template_id"); }

pub fn collapse_impl_template_name<'s, 'i>(interner: &InstantiatingInterner<'s, 'i>, _map: &HashMap<i32, i32>, name: &IImplTemplateNameI<'s, 'i, sI>) -> IImplTemplateNameI<'s, 'i, nI>
where 's: 'i {
    match name {
        IImplTemplateNameI::ImplTemplate(ImplTemplateNameI { code_location_s, .. }) => {
            IImplTemplateNameI::ImplTemplate(interner.intern_impl_template_name_ni(ImplTemplateNameI { _marker: PhantomData, code_location_s: *code_location_s }))
        }
        _ => panic!("collapse_impl_template_name: other"),
    }
}
