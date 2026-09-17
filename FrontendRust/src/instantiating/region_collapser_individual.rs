
use crate::instantiating::ast::names::{IdI, INameI, INameValI, IFunctionNameI, FunctionNameIX, FunctionTemplateNameI, ExportNameI, ExportTemplateNameI, ExternNameI, ExternTemplateNameI, ExternFunctionNameI, IVarNameI, CodeVarNameI, IStructNameI, IInterfaceNameI};
use crate::instantiating::ast::templata::ITemplataI;
use crate::instantiating::ast::types::{sI, cI};
use crate::instantiating::ast::templata::RegionTemplataI;
use crate::instantiating::instantiating_interner::InstantiatingInterner;
use crate::instantiating::region_counter;
use crate::instantiating::ast::ast::{PrototypeI, PrototypeIValI};
use crate::instantiating::ast::types::{CoordI, KindIT, VoidIT, NeverIT, IntIT, BoolIT, FloatIT, StrIT};
use std::collections::HashMap;
use crate::instantiating::ast::names::AnonymousSubstructConstructorNameI;
use crate::instantiating::ast::names::AnonymousSubstructConstructorTemplateNameI;
use crate::instantiating::ast::names::AnonymousSubstructImplNameI;
use crate::instantiating::ast::names::AnonymousSubstructImplTemplateNameI;
use crate::instantiating::ast::names::AnonymousSubstructNameI;
use crate::instantiating::ast::names::AnonymousSubstructTemplateNameI;
use crate::instantiating::ast::names::ClosureParamNameI;
use crate::instantiating::ast::names::ConstructingMemberNameI;
use crate::instantiating::ast::names::ForwarderFunctionNameI;
use crate::instantiating::ast::names::ForwarderFunctionTemplateNameI;
use crate::instantiating::ast::names::IterableNameI;
use crate::instantiating::ast::names::IterationOptionNameI;
use crate::instantiating::ast::names::IteratorNameI;
use crate::instantiating::ast::names::LambdaCallFunctionNameI;
use crate::instantiating::ast::names::LambdaCallFunctionTemplateNameI;
use crate::instantiating::ast::names::LambdaCitizenNameI;
use crate::instantiating::ast::names::LambdaCitizenTemplateNameI;
use crate::instantiating::ast::names::MagicParamNameI;
use crate::instantiating::ast::names::RawArrayNameI;
use crate::instantiating::ast::names::RuntimeSizedArrayNameI;
use crate::instantiating::ast::names::RuntimeSizedArrayTemplateNameI;
use crate::instantiating::ast::names::SelfNameI;
use crate::instantiating::ast::names::StaticSizedArrayNameI;
use crate::instantiating::ast::names::StaticSizedArrayTemplateNameI;
use crate::instantiating::ast::names::StructNameI;
use crate::instantiating::ast::names::TypingPassBlockResultVarNameI;
use crate::instantiating::ast::names::TypingPassFunctionResultVarNameI;
use crate::instantiating::ast::names::TypingPassTemporaryVarNameI;
use crate::instantiating::ast::templata::CoordTemplataI;
use crate::instantiating::ast::templata::KindTemplataI;
use crate::instantiating::ast::templata::expect_coord_templata;
use crate::instantiating::ast::types::RuntimeSizedArrayIT;
use crate::instantiating::ast::types::StaticSizedArrayIT;
use crate::instantiating::region_counter::count_citizen_name_map;
use crate::instantiating::region_counter::count_impl_name_map;
use crate::instantiating::region_counter::count_runtime_sized_array_map;
use crate::instantiating::region_counter::count_static_sized_array_map;
use std::marker::PhantomData;
use std::mem::discriminant;
use crate::instantiating::ast::names::ICitizenTemplateNameI;
use crate::instantiating::ast::names::IFunctionTemplateNameI;
use crate::instantiating::ast::names::IImplNameI;
use crate::instantiating::ast::names::IImplTemplateNameI;
use crate::instantiating::ast::names::IInterfaceTemplateNameI;
use crate::instantiating::ast::names::IStructTemplateNameI;
use crate::instantiating::ast::names::ImplNameI;
use crate::instantiating::ast::names::ImplTemplateNameI;
use crate::instantiating::ast::names::InterfaceNameI;
use crate::instantiating::ast::names::InterfaceTemplateNameI;
use crate::instantiating::ast::names::StructTemplateNameI;
use crate::instantiating::ast::templata::IntegerTemplataI;
use crate::instantiating::ast::templata::MutabilityTemplataI;
use crate::instantiating::ast::templata::VariabilityTemplataI;
use crate::instantiating::ast::types::ICitizenIT;
use crate::instantiating::ast::types::InterfaceITValI;
use crate::instantiating::ast::types::StructITValI;

pub fn collapse_prototype<'s, 'i>(interner: &InstantiatingInterner<'s, 'i>, prototype: &PrototypeI<'s, 'i, sI>) -> PrototypeI<'s, 'i, cI>
where 's: 'i {
    let PrototypeI { id, return_type, .. } = *prototype;
    *interner.intern_prototype_ci(PrototypeIValI {
        id: collapse_function_id(interner, &id),
        return_type: collapse_coord(interner, &return_type),
    })
}

pub fn collapse_id<'s, 'i>(
    interner: &InstantiatingInterner<'s, 'i>,
    id_i: &IdI<'s, 'i, sI>,
    func: impl Fn(&INameI<'s, 'i, sI>) -> INameI<'s, 'i, cI>,
) -> IdI<'s, 'i, cI>
where 's: 'i {
    let init_steps_c = id_i.init_steps.iter().map(|x| collapse_name(interner, x)).collect::<Vec<_>>();
    IdI {
        package_coord: id_i.package_coord,
        init_steps: interner.alloc_slice_from_vec(init_steps_c),
        local_name: func(&id_i.local_name),
    }
}

pub fn collapse_function_id<'s, 'i>(interner: &InstantiatingInterner<'s, 'i>, id: &IdI<'s, 'i, sI>) -> IdI<'s, 'i, cI>
where 's: 'i {
    collapse_id(interner, id, |x| INameI::from(collapse_function_name(interner, &IFunctionNameI::try_from(*x).unwrap())))
}

pub fn collapse_function_name<'s, 'i>(interner: &InstantiatingInterner<'s, 'i>, name: &IFunctionNameI<'s, 'i, sI>) -> IFunctionNameI<'s, 'i, cI>
where 's: 'i {
    match *name {
        IFunctionNameI::Function(n) => {
            let map = region_counter::count_function_name_map(name);
            let FunctionNameIX { template: FunctionTemplateNameI { human_name, code_location, .. }, template_args, parameters, .. } = *n;
            let template_c = FunctionTemplateNameI { _marker: PhantomData, human_name, code_location };
            let template_args_c = interner.alloc_slice_from_vec(template_args.iter().map(|template_arg| collapse_templata(interner, &map, template_arg)).collect::<Vec<_>>());
            let params_c = interner.alloc_slice_from_vec(parameters.iter().map(|param| collapse_coord(interner, param)).collect::<Vec<_>>());
            IFunctionNameI::Function(interner.intern_function_name_x_ci(FunctionNameIX { template: template_c, template_args: template_args_c, parameters: params_c }))
        }
        IFunctionNameI::ExternFunction(n) => {
            let map = region_counter::count_function_name_map(name);
            let ExternFunctionNameI { human_name, template_args, parameters } = *n;
            let params_c = interner.alloc_slice_from_vec(parameters.iter().map(|param| collapse_coord(interner, param)).collect::<Vec<_>>());
            let template_args_c = interner.alloc_slice_from_vec(template_args.iter().map(|template_arg| collapse_templata(interner, &map, template_arg)).collect::<Vec<_>>());
            IFunctionNameI::ExternFunction(interner.intern_extern_function_name_ci(ExternFunctionNameI { human_name, template_args: template_args_c, parameters: params_c }))
        }
        IFunctionNameI::LambdaCallFunction(n) => {
            let map = region_counter::count_function_name_map(name);
            let LambdaCallFunctionNameI { template: LambdaCallFunctionTemplateNameI { code_location, param_types: params_tt, .. }, template_args, parameters } = *n;
            let params_tt_c = interner.alloc_slice_from_vec(params_tt.iter().map(|p| collapse_coord(interner, p)).collect::<Vec<_>>());
            let template_c = *interner.intern_lambda_call_function_template_name_ci(LambdaCallFunctionTemplateNameI { _marker: PhantomData, code_location, param_types: params_tt_c });
            let template_args_c = interner.alloc_slice_from_vec(template_args.iter().map(|t| collapse_templata(interner, &map, t)).collect::<Vec<_>>());
            let params_c = interner.alloc_slice_from_vec(parameters.iter().map(|p| collapse_coord(interner, p)).collect::<Vec<_>>());
            IFunctionNameI::LambdaCallFunction(interner.intern_lambda_call_function_name_ci(LambdaCallFunctionNameI { template: template_c, template_args: template_args_c, parameters: params_c }))
        }
        IFunctionNameI::AnonymousSubstructConstructor(n) => {
            let map = region_counter::count_function_name_map(name);
            let AnonymousSubstructConstructorNameI { template: AnonymousSubstructConstructorTemplateNameI { substruct }, template_args, parameters } = *n;
            let template_c = AnonymousSubstructConstructorTemplateNameI { substruct: collapse_citizen_template_name(interner, &substruct) };
            let template_args_c = interner.alloc_slice_from_vec(template_args.iter().map(|t| collapse_templata(interner, &map, t)).collect::<Vec<_>>());
            let params_c = interner.alloc_slice_from_vec(parameters.iter().map(|p| collapse_coord(interner, p)).collect::<Vec<_>>());
            IFunctionNameI::AnonymousSubstructConstructor(interner.intern_anonymous_substruct_constructor_name_ci(AnonymousSubstructConstructorNameI { template: template_c, template_args: template_args_c, parameters: params_c }))
        }
        IFunctionNameI::ForwarderFunction(n) => {
            let ForwarderFunctionNameI { template: ForwarderFunctionTemplateNameI { inner: inner_template, index }, inner: func_name } = *n;
            IFunctionNameI::ForwarderFunction(interner.intern_forwarder_function_name_ci(ForwarderFunctionNameI {
                template: *interner.intern_forwarder_function_template_name_ci(ForwarderFunctionTemplateNameI {
                    inner: collapse_function_template_name(interner, &inner_template),
                    index,
                }),
                inner: collapse_function_name(interner, &func_name),
            }))
        }
        _ => panic!("Unimplemented: collapse_function_name other"),
    }
}

pub fn collapse_citizen_template_name<'s, 'i>(interner: &InstantiatingInterner<'s, 'i>, citizen: &ICitizenTemplateNameI<'s, 'i, sI>) -> ICitizenTemplateNameI<'s, 'i, cI> where 's: 'i {
    match citizen {
        ICitizenTemplateNameI::StructTemplate(_) | ICitizenTemplateNameI::LambdaCitizenTemplate(_) | ICitizenTemplateNameI::AnonymousSubstructTemplate(_) => {
            let s: IStructTemplateNameI<'s, 'i, sI> = (*citizen).try_into().unwrap();
            collapse_struct_template_name(interner, &s).into()
        }
        ICitizenTemplateNameI::InterfaceTemplate(_) => {
            let i: IInterfaceTemplateNameI<'s, 'i, sI> = (*citizen).try_into().unwrap();
            ICitizenTemplateNameI::from(collapse_interface_template_name(interner, &i))
        }
        _ => panic!("Unimplemented: collapse_citizen_template_name other"),
    }
}

pub fn collapse_var_name<'s, 'i>(interner: &InstantiatingInterner<'s, 'i>, name: &IVarNameI<'s, 'i, sI>) -> IVarNameI<'s, 'i, cI>
where 's: 'i {
    match name {
        IVarNameI::TypingPassBlockResultVar(TypingPassBlockResultVarNameI { life, .. }) => {
            IVarNameI::TypingPassBlockResultVar(interner.intern_typing_pass_block_result_var_name_ci(TypingPassBlockResultVarNameI {
                _marker: PhantomData,
                life: *life,
            }))
        }
        IVarNameI::CodeVar(x) => IVarNameI::CodeVar(interner.intern_code_var_name_ci(CodeVarNameI { _marker: PhantomData, name: x.name })),
        IVarNameI::TypingPassTemporaryVar(TypingPassTemporaryVarNameI { life, .. }) => {
            IVarNameI::TypingPassTemporaryVar(interner.intern_typing_pass_temporary_var_name_ci(TypingPassTemporaryVarNameI {
                _marker: PhantomData,
                life: *life,
            }))
        }
        IVarNameI::TypingPassFunctionResultVar(_) => IVarNameI::TypingPassFunctionResultVar(interner.intern_typing_pass_function_result_var_name_ci(TypingPassFunctionResultVarNameI(PhantomData))),
        IVarNameI::ClosureParam(ClosureParamNameI { code_location, .. }) => IVarNameI::ClosureParam(interner.intern_closure_param_name_ci(ClosureParamNameI { _marker: PhantomData, code_location: *code_location })),
        IVarNameI::MagicParam(MagicParamNameI { code_location_2, .. }) => IVarNameI::MagicParam(interner.intern_magic_param_name_ci(MagicParamNameI { _marker: PhantomData, code_location_2: *code_location_2 })),
        IVarNameI::Iterable(IterableNameI { range, .. }) => IVarNameI::Iterable(interner.intern_iterable_name_ci(IterableNameI { _marker: PhantomData, range: *range })),
        IVarNameI::ConstructingMember(ConstructingMemberNameI { name, .. }) => {
            IVarNameI::ConstructingMember(interner.intern_constructing_member_name_ci(ConstructingMemberNameI { _marker: PhantomData, name: *name }))
        }
        IVarNameI::Iterator(IteratorNameI { range, .. }) => IVarNameI::Iterator(interner.intern_iterator_name_ci(IteratorNameI { _marker: PhantomData, range: *range })),
        IVarNameI::IterationOption(IterationOptionNameI { range, .. }) => IVarNameI::IterationOption(interner.intern_iteration_option_name_ci(IterationOptionNameI { _marker: PhantomData, range: *range })),
        IVarNameI::Self_(_) => IVarNameI::Self_(interner.intern_self_name_ci(SelfNameI(PhantomData))),
        _ => panic!("Unimplemented: collapse_var_name other"),
    }
}

pub fn collapse_function_template_name<'s, 'i>(interner: &InstantiatingInterner<'s, 'i>, function_name: &IFunctionTemplateNameI<'s, 'i, sI>) -> IFunctionTemplateNameI<'s, 'i, cI> where 's: 'i {
    match function_name {
        IFunctionTemplateNameI::FunctionTemplate(FunctionTemplateNameI { human_name, code_location, .. }) => IFunctionTemplateNameI::FunctionTemplate(interner.intern_function_template_name_ci(FunctionTemplateNameI { _marker: PhantomData, human_name: *human_name, code_location: *code_location })),
        _ => panic!("Unimplemented: collapse_function_template_name other"),
    }
}

pub fn collapse_name<'s, 'i>(interner: &InstantiatingInterner<'s, 'i>, name: &INameI<'s, 'i, sI>) -> INameI<'s, 'i, cI>
where 's: 'i {
    match name {
        INameI::OverrideDispatcher(_) | INameI::ExternFunction(_) | INameI::FunctionNameIX(_)
        | INameI::ForwarderFunction(_) | INameI::FunctionBound(_) | INameI::LambdaCallFunction(_)
        | INameI::AnonymousSubstructConstructor(_) => {
            let n: IFunctionNameI<'s, 'i, sI> = (*name).try_into().unwrap();
            collapse_function_name(interner, &n).into()
        }
        INameI::StructName(_) => {
            let s: IStructNameI<'s, 'i, sI> = (*name).try_into().unwrap();
            collapse_struct_name(interner, &s).into()
        }
        INameI::LambdaCitizen(_) => {
            let s: IStructNameI<'s, 'i, sI> = (*name).try_into().unwrap();
            collapse_struct_name(interner, &s).into()
        }
        INameI::AnonymousSubstructTemplate(astn) => {
            let AnonymousSubstructTemplateNameI { interface } = **astn;
            INameI::AnonymousSubstructTemplate(interner.intern_anonymous_substruct_template_name_ci(AnonymousSubstructTemplateNameI {
                interface: collapse_interface_template_name(interner, &interface),
            }))
        }
        INameI::LambdaCitizenTemplate(LambdaCitizenTemplateNameI { code_location, .. }) => {
            INameI::LambdaCitizenTemplate(interner.intern_lambda_citizen_template_name_ci(LambdaCitizenTemplateNameI { _marker: PhantomData, code_location: *code_location }))
        }
        INameI::StructTemplate(stn) => {
            let StructTemplateNameI { human_name, .. } = **stn;
            INameI::StructTemplate(interner.intern_struct_template_name_ci(StructTemplateNameI { _marker: PhantomData, human_name }))
        }
        INameI::InterfaceTemplate(itn) => {
            let InterfaceTemplateNameI { human_namee, .. } = **itn;
            INameI::InterfaceTemplate(interner.intern_interface_template_name_ci(InterfaceTemplateNameI { _marker: PhantomData, human_namee }))
        }
        other => panic!("Unimplemented: collapse_name {:?}", discriminant(other)),
    }
}

pub fn collapse_coord_templata<'s, 'i>(interner: &InstantiatingInterner<'s, 'i>, map: &HashMap<i32, i32>, templata: CoordTemplataI<'s, 'i, sI>) -> CoordTemplataI<'s, 'i, cI> where 's: 'i {
    let CoordTemplataI { region, coord } = templata;
    CoordTemplataI {
        region: collapse_region_templata(map, region),
        coord: collapse_coord(interner, &coord),
    }
}

pub fn collapse_templata<'s, 'i>(interner: &InstantiatingInterner<'s, 'i>, map: &HashMap<i32, i32>, templata: &ITemplataI<'s, 'i, sI>) -> ITemplataI<'s, 'i, cI>
where 's: 'i {
    match templata {
        ITemplataI::Coord(c) => ITemplataI::Coord(collapse_coord_templata(interner, map, *c)),
        ITemplataI::Kind(k) => ITemplataI::Kind(KindTemplataI { kind: collapse_kind(interner, &k.kind) }),
        ITemplataI::Region(r) => ITemplataI::Region(collapse_region_templata(map, *r)),
        ITemplataI::Mutability(m) => ITemplataI::Mutability(MutabilityTemplataI { mutability: m.mutability, _marker: PhantomData }),
        ITemplataI::Integer(i) => ITemplataI::Integer(IntegerTemplataI { value: i.value, _marker: PhantomData }),
        ITemplataI::Variability(v) => ITemplataI::Variability(VariabilityTemplataI { variability: v.variability, _marker: PhantomData }),
        _ => panic!("collapse_templata: unimplemented variant"),
    }
}

pub fn collapse_region_templata<'s, 'i>(map: &HashMap<i32, i32>, templata: RegionTemplataI<sI>) -> RegionTemplataI<cI>
where 's: 'i {
    let RegionTemplataI { pure_height: old_pure_height, .. } = templata;
    RegionTemplataI { pure_height: *map.get(&old_pure_height).unwrap(), _marker: PhantomData }
}

pub fn collapse_coord<'s, 'i>(interner: &InstantiatingInterner<'s, 'i>, coord: &CoordI<'s, 'i, sI>) -> CoordI<'s, 'i, cI>
where 's: 'i {
    let CoordI { ownership, kind } = *coord;
    CoordI { ownership, kind: collapse_kind(interner, &kind) }
}

pub fn collapse_kind<'s, 'i>(interner: &InstantiatingInterner<'s, 'i>, kind: &KindIT<'s, 'i, sI>) -> KindIT<'s, 'i, cI>
where 's: 'i {
    match kind {
        KindIT::NeverIT(never) => KindIT::NeverIT(NeverIT { from_break: never.from_break, _marker: PhantomData }),
        KindIT::VoidIT(_) => KindIT::VoidIT(VoidIT { _marker: PhantomData }),
        KindIT::IntIT(int) => KindIT::IntIT(IntIT { bits: int.bits, _marker: PhantomData }),
        KindIT::BoolIT(_) => KindIT::BoolIT(BoolIT { _marker: PhantomData }),
        KindIT::FloatIT(_) => KindIT::FloatIT(FloatIT { _marker: PhantomData }),
        KindIT::StrIT(_) => KindIT::StrIT(StrIT { _marker: PhantomData }),
        KindIT::StructIT(s) => KindIT::StructIT(interner.intern_struct_it_ci(StructITValI { id: collapse_struct_id(interner, &s.id) })),
        KindIT::InterfaceIT(i) => KindIT::InterfaceIT(interner.intern_interface_it_ci(InterfaceITValI { id: collapse_interface_id(interner, &i.id) })),
        KindIT::StaticSizedArrayIT(ssa) => KindIT::StaticSizedArrayIT(interner.alloc(collapse_static_sized_array(interner, ssa))),
        KindIT::RuntimeSizedArrayIT(rsa) => KindIT::RuntimeSizedArrayIT(interner.alloc(collapse_runtime_sized_array(interner, rsa))),
    }
}

pub fn collapse_runtime_sized_array<'s, 'i>(interner: &InstantiatingInterner<'s, 'i>, rsa: &RuntimeSizedArrayIT<'s, 'i, sI>) -> RuntimeSizedArrayIT<'s, 'i, cI>
where 's: 'i {
    let rsa_id = rsa.name;
    let map = count_runtime_sized_array_map(rsa);
    let collapsed_id = collapse_id(interner, &rsa_id, |local_name| {
        match local_name {
            INameI::RuntimeSizedArray(n) => {
                let RuntimeSizedArrayNameI { template: _, arr } = **n;
                let RawArrayNameI { mutability, element_type, self_region } = arr;
                INameI::RuntimeSizedArray(interner.alloc(RuntimeSizedArrayNameI {
                    template: RuntimeSizedArrayTemplateNameI(PhantomData),
                    arr: RawArrayNameI {
                        mutability,
                        element_type: expect_coord_templata(collapse_templata(interner, &map, &ITemplataI::Coord(element_type))),
                        self_region: collapse_region_templata(&map, self_region),
                    },
                }))
            }
            _ => panic!("collapse_runtime_sized_array: non-RuntimeSizedArrayName local name"),
        }
    });
    *interner.intern_runtime_sized_array_it_ci(crate::instantiating::ast::types::RuntimeSizedArrayITValI { name: collapsed_id })
}

pub fn collapse_static_sized_array<'s, 'i>(interner: &InstantiatingInterner<'s, 'i>, ssa: &StaticSizedArrayIT<'s, 'i, sI>) -> StaticSizedArrayIT<'s, 'i, cI>
where 's: 'i {
    let ssa_id = ssa.name;
    let map = count_static_sized_array_map(ssa);
    let collapsed_id = collapse_id(interner, &ssa_id, |local_name| {
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
                        element_type: expect_coord_templata(collapse_templata(interner, &map, &ITemplataI::Coord(element_type))),
                        self_region: collapse_region_templata(&map, self_region),
                    },
                }))
            }
            _ => panic!("collapse_static_sized_array: non-StaticSizedArrayName local name"),
        }
    });
    *interner.intern_static_sized_array_it_ci(crate::instantiating::ast::types::StaticSizedArrayITValI { name: collapsed_id })
}

pub fn collapse_interface_id<'s, 'i>(interner: &InstantiatingInterner<'s, 'i>, interface_id: &IdI<'s, 'i, sI>) -> IdI<'s, 'i, cI> where 's: 'i {
    collapse_id(interner, interface_id, |x| {
        match x {
            INameI::InterfaceName(i) => INameI::InterfaceName(match collapse_interface_name(interner, &IInterfaceNameI::Interface(i)) {
                IInterfaceNameI::Interface(r) => r,
            }),
            _ => panic!("collapse_interface_id: non-InterfaceName local name"),
        }
    })
}

pub fn collapse_struct_id<'s, 'i>(interner: &InstantiatingInterner<'s, 'i>, struct_id: &IdI<'s, 'i, sI>) -> IdI<'s, 'i, cI> where 's: 'i {
    collapse_id(interner, struct_id, |x| {
        let narrowed: IStructNameI<'s, 'i, sI> = (*x).try_into().unwrap();
        collapse_struct_name(interner, &narrowed).into()
    })
}

pub fn collapse_struct_name<'s, 'i>(interner: &InstantiatingInterner<'s, 'i>, struct_name: &IStructNameI<'s, 'i, sI>) -> IStructNameI<'s, 'i, cI> where 's: 'i {
    match struct_name {
        IStructNameI::Struct(StructNameI { template, template_args }) => {
            let map = count_citizen_name_map(&(*struct_name).into());
            let template_c = collapse_struct_template_name(interner, template);
            let template_args_c: Vec<ITemplataI<'s, 'i, cI>> = template_args.iter().map(|t| collapse_templata(interner, &map, t)).collect();
            IStructNameI::Struct(interner.intern_struct_name_ci(StructNameI {
                template: template_c,
                template_args: interner.bump().alloc_slice_fill_iter(template_args_c.into_iter()),
            }))
        }
        IStructNameI::LambdaCitizen(LambdaCitizenNameI { template: LambdaCitizenTemplateNameI { code_location, _marker: _ } }) => {
            IStructNameI::LambdaCitizen(interner.intern_lambda_citizen_name_ci(LambdaCitizenNameI {
                template: *interner.intern_lambda_citizen_template_name_ci(LambdaCitizenTemplateNameI { _marker: PhantomData, code_location: *code_location }),
            }))
        }
        IStructNameI::AnonymousSubstruct(AnonymousSubstructNameI { template: AnonymousSubstructTemplateNameI { interface }, template_args }) => {
            let map = count_citizen_name_map(&(*struct_name).into());
            let template_args_c: Vec<ITemplataI<'s, 'i, cI>> = template_args.iter().map(|t| collapse_templata(interner, &map, t)).collect();
            IStructNameI::AnonymousSubstruct(interner.intern_anonymous_substruct_name_ci(AnonymousSubstructNameI {
                template: *interner.intern_anonymous_substruct_template_name_ci(AnonymousSubstructTemplateNameI {
                    interface: collapse_interface_template_name(interner, interface),
                }),
                template_args: interner.bump().alloc_slice_fill_iter(template_args_c.into_iter()),
            }))
        }
    }
}

pub fn collapse_impl_name<'s, 'i>(interner: &InstantiatingInterner<'s, 'i>, name: &IImplNameI<'s, 'i, sI>) -> IImplNameI<'s, 'i, cI>
where 's: 'i {
    match name {
        IImplNameI::Impl(ImplNameI { template, template_args, sub_citizen }) => {
            let map = count_impl_name_map(name);
            let template_c = collapse_impl_template_name(interner, template);
            let template_args_c: Vec<ITemplataI<'s, 'i, cI>> = template_args.iter().map(|t| collapse_templata(interner, &map, t)).collect();
            let sub_citizen_c = collapse_citizen(interner, sub_citizen);
            IImplNameI::Impl(interner.intern_impl_name_ci(ImplNameI {
                template: template_c,
                template_args: interner.bump().alloc_slice_fill_iter(template_args_c.into_iter()),
                sub_citizen: sub_citizen_c,
            }))
        }
        IImplNameI::AnonymousSubstructImpl(AnonymousSubstructImplNameI { template: AnonymousSubstructImplTemplateNameI { interface }, template_args, sub_citizen }) => {
            let map = count_impl_name_map(name);
            let template_args_c: Vec<ITemplataI<'s, 'i, cI>> = template_args.iter().map(|t| collapse_templata(interner, &map, t)).collect();
            let sub_citizen_c = collapse_citizen(interner, sub_citizen);
            IImplNameI::AnonymousSubstructImpl(interner.intern_anonymous_substruct_impl_name_ci(AnonymousSubstructImplNameI {
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

pub fn collapse_interface_name<'s, 'i>(interner: &InstantiatingInterner<'s, 'i>, interface_name: &IInterfaceNameI<'s, 'i, sI>) -> IInterfaceNameI<'s, 'i, cI> where 's: 'i {
    match interface_name {
        IInterfaceNameI::Interface(InterfaceNameI { template: IInterfaceTemplateNameI::InterfaceTemplate(InterfaceTemplateNameI { human_namee, .. }), template_args }) => {
            let map = count_citizen_name_map(&(*interface_name).into());
            let template_args_c: Vec<ITemplataI<'s, 'i, cI>> = template_args.iter().map(|t| collapse_templata(interner, &map, t)).collect();
            IInterfaceNameI::Interface(interner.intern_interface_name_ci(InterfaceNameI {
                template: IInterfaceTemplateNameI::InterfaceTemplate(interner.intern_interface_template_name_ci(InterfaceTemplateNameI { _marker: PhantomData, human_namee: *human_namee })),
                template_args: interner.bump().alloc_slice_fill_iter(template_args_c.into_iter()),
            }))
        }
    }
}

pub fn collapse_export_id<'s, 'i>(interner: &InstantiatingInterner<'s, 'i>, map: HashMap<i32, i32>, export_id_s: &IdI<'s, 'i, sI>) -> IdI<'s, 'i, cI>
where 's: 'i {
    collapse_id(interner, export_id_s, |name| {
        match name {
            INameI::Export(e) => {
                interner.intern_name_ci(INameValI::Export(ExportNameI {
                    template: ExportTemplateNameI { _marker: PhantomData, code_loc: e.template.code_loc },
                    region: collapse_region_templata(&map, e.region),
                }))
            }
            _ => panic!("Unimplemented: collapse_export_id closure"),
        }
    })
}

pub fn collapse_extern_id<'s, 'i>(interner: &InstantiatingInterner<'s, 'i>, map: HashMap<i32, i32>, extern_id_s: &IdI<'s, 'i, sI>) -> IdI<'s, 'i, cI>
where 's: 'i {
    collapse_id(interner, extern_id_s, |name| {
        match name {
            INameI::Extern(e) => {
                interner.intern_name_ci(INameValI::Extern(ExternNameI {
                    template: ExternTemplateNameI { _marker: PhantomData, code_loc: e.template.code_loc },
                    region: collapse_region_templata(&map, e.region),
                }))
            }
            _ => panic!("Unimplemented: collapse_extern_id closure"),
        }
    })
}

pub fn collapse_struct_template_name<'s, 'i>(interner: &InstantiatingInterner<'s, 'i>, struct_name: &IStructTemplateNameI<'s, 'i, sI>) -> IStructTemplateNameI<'s, 'i, cI> where 's: 'i {
    match struct_name {
        IStructTemplateNameI::StructTemplate(StructTemplateNameI { human_name, .. }) => IStructTemplateNameI::StructTemplate(interner.intern_struct_template_name_ci(StructTemplateNameI { _marker: PhantomData, human_name: *human_name })),
        IStructTemplateNameI::AnonymousSubstructTemplate(AnonymousSubstructTemplateNameI { interface }) => IStructTemplateNameI::AnonymousSubstructTemplate(interner.intern_anonymous_substruct_template_name_ci(AnonymousSubstructTemplateNameI {
            interface: collapse_interface_template_name(interner, interface),
        })),
        IStructTemplateNameI::LambdaCitizenTemplate(_) => panic!("collapse_struct_template_name: LambdaCitizenTemplate branch (no Scala counterpart in collapseStructTemplateName)"),
    }
}

pub fn collapse_interface_template_name<'s, 'i>(interner: &InstantiatingInterner<'s, 'i>, name: &IInterfaceTemplateNameI<'s, 'i, sI>) -> IInterfaceTemplateNameI<'s, 'i, cI> where 's: 'i {
    match name {
        IInterfaceTemplateNameI::InterfaceTemplate(InterfaceTemplateNameI { human_namee, .. }) => IInterfaceTemplateNameI::InterfaceTemplate(interner.intern_interface_template_name_ci(InterfaceTemplateNameI { _marker: PhantomData, human_namee: *human_namee })),
    }
}

pub fn collapse_impl_id<'s, 'i>(interner: &InstantiatingInterner<'s, 'i>, impl_id: &IdI<'s, 'i, sI>) -> IdI<'s, 'i, cI>
where 's: 'i {
    collapse_id(interner, impl_id, |x| {
        let narrowed: IImplNameI<'s, 'i, sI> = (*x).try_into().unwrap();
        collapse_impl_name(interner, &narrowed).into()
    })
}

pub fn collapse_impl_template_name<'s, 'i>(interner: &InstantiatingInterner<'s, 'i>, name: &IImplTemplateNameI<'s, 'i, sI>) -> IImplTemplateNameI<'s, 'i, cI>
where 's: 'i {
    match name {
        IImplTemplateNameI::ImplTemplate(ImplTemplateNameI { code_location_s, .. }) => {
            IImplTemplateNameI::ImplTemplate(interner.intern_impl_template_name_ci(ImplTemplateNameI { _marker: PhantomData, code_location_s: *code_location_s }))
        }
        _ => panic!("collapse_impl_template_name: other"),
    }
}

pub fn collapse_citizen<'s, 'i>(interner: &InstantiatingInterner<'s, 'i>, citizen: &ICitizenIT<'s, 'i, sI>) -> ICitizenIT<'s, 'i, cI>
where 's: 'i {
    match citizen {
        ICitizenIT::StructIT(s) => ICitizenIT::StructIT(interner.intern_struct_it_ci(StructITValI { id: collapse_struct_id(interner, &s.id) })),
        ICitizenIT::InterfaceIT(i) => ICitizenIT::InterfaceIT(interner.intern_interface_it_ci(InterfaceITValI { id: collapse_interface_id(interner, &i.id) })),
    }
}
