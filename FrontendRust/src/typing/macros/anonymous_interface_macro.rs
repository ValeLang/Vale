use crate::higher_typing::ast::*;
use crate::postparsing::ast::NormalStructMemberS;
use crate::postparsing::names::{AnonymousSubstructTemplateNameS, IRuneS};
use crate::postparsing::rules::rules::{IRulexSR, RuneUsage};
use crate::typing::names::names::*;
use crate::typing::env::i_env_entry::*;
use crate::typing::compiler::Compiler;
use crate::postparsing::ast::ICitizenAttributeS;
use crate::postparsing::ast::SealedS;
use crate::postparsing::rules::rules::LookupSR;
use crate::postparsing::rules::rules::CallSR;
use crate::postparsing::itemplatatype::{ITemplataType, KindTemplataType, TemplateTemplataType};
use crate::typing::names::names::IdValT;
use crate::higher_typing::ast::ImplA;
use crate::utils::arena_index_map::ArenaIndexMap;
use crate::postparsing::rules::rules::CoerceToCoordSR;
use crate::postparsing::rules::rules::AugmentSR;
use crate::postparsing::names::{IRuneValS, AnonymousSubstructMethodInheritedRuneValS};
use crate::postparsing::names::AnonymousSubstructVoidKindRuneS;
use crate::postparsing::names::AnonymousSubstructVoidCoordRuneS;
use crate::postparsing::names::CodeNameS;
use crate::postparsing::names::IImpreciseNameValS;
use crate::postparsing::itemplatatype::CoordTemplataType;
use crate::utils::range::RangeS;
use crate::postparsing::rules::rules::PackSR;
use crate::postparsing::rules::rules::DefinitionFuncSR;
use crate::postparsing::rules::rules::CallSiteFuncSR;
use crate::postparsing::rules::rules::ResolveSR;
use crate::postparsing::itemplatatype::{PackTemplataType, PrototypeTemplataType};
use crate::parsing::ast::ast::OwnershipP;
use crate::postparsing::ast::ParameterS;
use crate::postparsing::itemplatatype::OwnershipTemplataType;
use crate::postparsing::itemplatatype::FunctionTemplataType;
use crate::postparsing::rules::rules::CoordComponentsSR;
use crate::postparsing::ast::{GenericParameterS, IBodyS, CodeBodyS, LocationInDenizen, AbstractSP};
use crate::postparsing::expressions::{BodySE, BlockSE, IExpressionSE, FunctionCallSE, DotSE, LocalLoadSE, LocalS, IVariableUseCertainty};
use crate::postparsing::patterns::patterns::{AtomSP, CaptureS};
use crate::parsing::ast::ast::LoadAsP;
use crate::postparsing::names::AnonymousSubstructMemberRuneS;
use crate::parsing::ast::VariabilityP;
use crate::postparsing::names::INameS;
use crate::postparsing::ast::IGenericParameterTypeS;
use crate::postparsing::ast::CoordGenericParameterTypeS;
use crate::postparsing::ast::IStructMemberS;
use crate::postparsing::names::IStructDeclarationNameS;


// (Scala `class AnonymousInterfaceMacro(opts, interner, keywords, nameTranslator,
//  overloadCompiler, structCompiler, structConstructorMacro, structDropMacro)` absorbed
//  onto `Compiler`; the method bodies live at
//  `Compiler::{get_interface_sibling_entries_anonymous_interface, map_runes_anonymous_interface,
//  inherited_method_rune_anonymous_interface, make_struct_anonymous_interface,
//  make_forwarder_function_anonymous_interface}` below.)

impl<'s, 'ctx, 't> Compiler<'s, 'ctx, 't>
where 's: 't,
{
    pub fn get_interface_sibling_entries_anonymous_interface(
        &self,
        interface_name: IdT<'s, 't>,
        interface_a: &'s InterfaceA<'s>,
    ) -> Vec<(IdT<'s, 't>, IEnvEntryT<'s, 't>)> {
        use crate::postparsing::names::{
            IRuneValS, AnonymousSubstructTemplateNameS, AnonymousSubstructImplDeclarationNameS,
            AnonymousSubstructTemplateRuneS, AnonymousSubstructKindRuneS,
            AnonymousSubstructParentInterfaceTemplateRuneS, AnonymousSubstructParentInterfaceKindRuneS,
            IImplDeclarationNameS,
        };

        if interface_a.attributes.iter().any(|a| matches!(a, ICitizenAttributeS::Sealed(_))) {
            return vec![];
        }

        let member_runes: Vec<RuneUsage<'s>> =
            interface_a.internal_methods.iter().enumerate().map(|(_index, method)| {
                let rune = self.scout_arena.intern_rune(
                    IRuneValS::AnonymousSubstructMemberRune(AnonymousSubstructMemberRuneS {
                        interface: *interface_a.name,
                        method: method.name,
                    }));
                RuneUsage { range: RangeS::new(method.range.begin, method.range.begin), rune }
            }).collect();
        let members: Vec<NormalStructMemberS<'s>> =
            interface_a.internal_methods.iter().zip(member_runes.iter()).enumerate().map(|(index, (method, rune))| {
                NormalStructMemberS {
                    range: method.range,
                    name: self.scout_arena.intern_str(&index.to_string()),
                    variability: VariabilityP::Final,
                    type_rune: *rune,
                }
            }).collect();

        let struct_name_s = AnonymousSubstructTemplateNameS { interface_name: *interface_a.name };
        let struct_name_s_ref = self.scout_arena.alloc(struct_name_s);
        let struct_local_name = self.translate_name_step(INameS::AnonymousSubstructTemplateName(struct_name_s_ref));
        let struct_name_t_steps = interface_name.init_steps.to_vec();
        let struct_name_t = *self.typing_interner.intern_id(IdValT {
            package_coord: interface_name.package_coord,
            init_steps: &struct_name_t_steps,
            local_name: struct_local_name,
        });

        let struct_a = self.make_struct_anonymous_interface(
            interface_a,
            &member_runes,
            &members,
            struct_name_s,
        );

        let more_entries = self.get_struct_sibling_entries_struct_constructor(struct_name_t, struct_a);
        let mut more_entries2 = self.get_struct_sibling_entries_struct_drop(struct_name_t, struct_a);
        let mut more_entries_combined = more_entries;
        more_entries_combined.append(&mut more_entries2);

        let forwarder_methods: Vec<(IdT<'s, 't>, IEnvEntryT<'s, 't>)> =
            interface_a.internal_methods.iter().zip(member_runes.iter()).enumerate().map(|(method_index, (method, _rune))| {
                let local_name: INameT<'s, 't> = self.translate_generic_function_name(method.name).into();
                let name = *self.typing_interner.intern_id(IdValT {
                    package_coord: struct_name_t.package_coord,
                    init_steps: struct_name_t.init_steps,
                    local_name,
                });
                let forwarder = self.make_forwarder_function_anonymous_interface(
                    struct_name_s, interface_a, struct_a, *method, method_index as i32);
                (name, IEnvEntryT::Function(forwarder))
            }).collect();

        let anon_template_rune = self.scout_arena.intern_rune(
            IRuneValS::AnonymousSubstructTemplateRune(AnonymousSubstructTemplateRuneS {})
        );
        let anon_kind_rune = self.scout_arena.intern_rune(
            IRuneValS::AnonymousSubstructKindRune(AnonymousSubstructKindRuneS {})
        );
        let parent_interface_template_rune = self.scout_arena.intern_rune(
            IRuneValS::AnonymousSubstructParentInterfaceTemplateRune(AnonymousSubstructParentInterfaceTemplateRuneS {})
        );
        let parent_interface_kind_rune = self.scout_arena.intern_rune(
            IRuneValS::AnonymousSubstructParentInterfaceKindRune(AnonymousSubstructParentInterfaceKindRuneS {})
        );

        let struct_imprecise_name = struct_a.name.get_imprecise_name(self.scout_arena);
        let interface_imprecise_name = interface_a.name.get_imprecise_name(self.scout_arena);

        let rules: Vec<IRulexSR<'s>> = vec![
            IRulexSR::Lookup(LookupSR {
                range: struct_a.range,
                rune: RuneUsage { range: struct_a.range, rune: anon_template_rune },
                name: struct_imprecise_name,
            }),
            IRulexSR::Call(CallSR {
                range: struct_a.range,
                result_rune: RuneUsage { range: struct_a.range, rune: anon_kind_rune },
                template_rune: RuneUsage { range: struct_a.range, rune: anon_template_rune },
                args: self.scout_arena.alloc_slice_from_vec(
                    struct_a.generic_parameters.iter().map(|gp| gp.rune).collect()
                ),
            }),
            IRulexSR::Lookup(LookupSR {
                range: interface_a.range,
                rune: RuneUsage { range: interface_a.range, rune: parent_interface_template_rune },
                name: interface_imprecise_name,
            }),
            IRulexSR::Call(CallSR {
                range: interface_a.range,
                result_rune: RuneUsage { range: interface_a.range, rune: parent_interface_kind_rune },
                template_rune: RuneUsage { range: interface_a.range, rune: parent_interface_template_rune },
                args: self.scout_arena.alloc_slice_from_vec(
                    interface_a.generic_parameters.iter().map(|gp| gp.rune).collect()
                ),
            }),
        ];

        let mut rune_to_type: ArenaIndexMap<'s, IRuneS<'s>, ITemplataType<'s>> = self.scout_arena.alloc_index_map();
        for gp in struct_a.generic_parameters.iter() {
            let tyype = *struct_a.header_rune_to_type.get(&gp.rune.rune).unwrap();
            rune_to_type.insert(gp.rune.rune, tyype);
        }
        rune_to_type.insert(anon_kind_rune, ITemplataType::KindTemplataType(KindTemplataType {}));
        rune_to_type.insert(anon_template_rune, ITemplataType::TemplateTemplataType(struct_a.tyype));
        rune_to_type.insert(parent_interface_kind_rune, ITemplataType::KindTemplataType(KindTemplataType {}));
        rune_to_type.insert(parent_interface_template_rune, ITemplataType::TemplateTemplataType(interface_a.tyype));

        let struct_kind_rune_s = RuneUsage { range: interface_a.range, rune: anon_kind_rune };
        let interface_kind_rune_s = RuneUsage { range: interface_a.range, rune: parent_interface_kind_rune };

        let impl_name_s = IImplDeclarationNameS::AnonymousSubstructImplDeclarationName(
            AnonymousSubstructImplDeclarationNameS { interface: *interface_a.name }
        );

        let rules_slice = self.scout_arena.alloc_slice_from_vec(rules);
        let impl_a = self.scout_arena.alloc(ImplA {
            range: interface_a.range,
            name: impl_name_s,
            generic_params: struct_a.generic_parameters,
            rules: rules_slice,
            rune_to_type,
            sub_citizen_rune: struct_kind_rune_s,
            sub_citizen_imprecise_name: struct_imprecise_name,
            interface_kind_rune: interface_kind_rune_s,
            super_interface_imprecise_name: interface_imprecise_name,
        });

        let impl_local_name = self.translate_name_step(impl_a.name.to_i_name_s(self.scout_arena));
        let impl_name_t_steps = struct_name_t.init_steps.to_vec();
        let impl_name_t = *self.typing_interner.intern_id(IdValT {
            package_coord: struct_name_t.package_coord,
            init_steps: &impl_name_t_steps,
            local_name: impl_local_name,
        });

        let mut result = vec![
            (struct_name_t, IEnvEntryT::Struct(struct_a)),
            (impl_name_t, IEnvEntryT::Impl(impl_a)),
        ];
        result.extend(more_entries_combined);
        result.extend(forwarder_methods);
        result
    }

    pub fn map_runes_anonymous_interface(
        &self,
        rule: IRulexSR<'s>,
        func: impl Fn(IRuneS<'s>) -> IRuneS<'s>,
    ) -> IRulexSR<'s> {
        match rule {
            IRulexSR::Lookup(x) => IRulexSR::Lookup(LookupSR {
                range: x.range,
                rune: RuneUsage { range: x.rune.range, rune: func(x.rune.rune) },
                name: x.name,
            }),
            IRulexSR::MaybeCoercingLookup(_) => panic!("implement: map_runes_anonymous_interface MaybeCoercingLookup"),
            IRulexSR::RuneParentEnvLookup(_) => panic!("implement: map_runes_anonymous_interface RuneParentEnvLookup"),
            IRulexSR::Equals(_) => panic!("implement: map_runes_anonymous_interface Equals"),
            IRulexSR::DefinitionCoordIsa(_) => panic!("implement: map_runes_anonymous_interface DefinitionCoordIsa"),
            IRulexSR::CallSiteCoordIsa(_) => panic!("implement: map_runes_anonymous_interface CallSiteCoordIsa"),
            IRulexSR::KindComponents(_) => panic!("implement: map_runes_anonymous_interface KindComponents"),
            IRulexSR::CoordComponents(_) => panic!("implement: map_runes_anonymous_interface CoordComponents"),
            IRulexSR::PrototypeComponents(_) => panic!("implement: map_runes_anonymous_interface PrototypeComponents"),
            IRulexSR::Resolve(_) => panic!("implement: map_runes_anonymous_interface Resolve"),
            IRulexSR::CallSiteFunc(_) => panic!("implement: map_runes_anonymous_interface CallSiteFunc"),
            IRulexSR::DefinitionFunc(_) => panic!("implement: map_runes_anonymous_interface DefinitionFunc"),
            IRulexSR::OneOf(_) => panic!("implement: map_runes_anonymous_interface OneOf"),
            IRulexSR::IsConcrete(_) => panic!("implement: map_runes_anonymous_interface IsConcrete"),
            IRulexSR::IsInterface(_) => panic!("implement: map_runes_anonymous_interface IsInterface"),
            IRulexSR::IsStruct(_) => panic!("implement: map_runes_anonymous_interface IsStruct"),
            IRulexSR::CoerceToCoord(x) => IRulexSR::CoerceToCoord(CoerceToCoordSR {
                range: x.range,
                coord_rune: RuneUsage { range: x.coord_rune.range, rune: func(x.coord_rune.rune) },
                kind_rune: RuneUsage { range: x.kind_rune.range, rune: func(x.kind_rune.rune) },
            }),
            IRulexSR::Literal(_) => panic!("implement: map_runes_anonymous_interface Literal"),
            IRulexSR::Augment(x) => {
                IRulexSR::Augment(AugmentSR {
                    range: x.range,
                    result_rune: RuneUsage { range: x.result_rune.range, rune: func(x.result_rune.rune) },
                    ownership: x.ownership,
                    inner_rune: RuneUsage { range: x.inner_rune.range, rune: func(x.inner_rune.rune) },
                })
            }
            IRulexSR::MaybeCoercingCall(_) => panic!("implement: map_runes_anonymous_interface MaybeCoercingCall"),
            IRulexSR::Call(x) => {
                let new_args: Vec<RuneUsage<'s>> = x.args.iter()
                    .map(|ru| RuneUsage { range: ru.range, rune: func(ru.rune) })
                    .collect();
                IRulexSR::Call(CallSR {
                    range: x.range,
                    result_rune: RuneUsage { range: x.result_rune.range, rune: func(x.result_rune.rune) },
                    template_rune: RuneUsage { range: x.template_rune.range, rune: func(x.template_rune.rune) },
                    args: self.scout_arena.alloc_slice_from_vec(new_args),
                })
            }
            IRulexSR::Pack(_) => panic!("implement: map_runes_anonymous_interface Pack"),
            IRulexSR::RefListCompoundMutability(_) => panic!("implement: map_runes_anonymous_interface RefListCompoundMutability"),
            other => panic!("vimpl: map_runes_anonymous_interface {:?}", other),
        }
    }

    pub fn inherited_method_rune_anonymous_interface(
        &self,
        interface_a: &'s InterfaceA<'s>,
        method: &'s FunctionA<'s>,
        rune: IRuneS<'s>,
    ) -> IRuneS<'s> {
        self.scout_arena.intern_rune(IRuneValS::AnonymousSubstructMethodInheritedRune(
            AnonymousSubstructMethodInheritedRuneValS {
                interface: *interface_a.name,
                method: method.name,
                inner: rune,
            }))
    }

    pub fn make_struct_anonymous_interface(
        &self,
        interface_a: &'s InterfaceA<'s>,
        member_runes: &[RuneUsage<'s>],
        members: &[NormalStructMemberS<'s>],
        struct_template_name_s: AnonymousSubstructTemplateNameS<'s>,
    ) -> &'s StructA<'s> {

        let range = |n: i32| RangeS::internal(self.scout_arena, n);
        let use_rune = |n: i32, rune: IRuneS<'s>| RuneUsage { range: range(n), rune };

        let mut rules_builder: Vec<IRulexSR<'s>> = Vec::new();
        let mut rune_to_type: Vec<(IRuneS<'s>, ITemplataType<'s>)> = Vec::new();

        for rule in interface_a.rules.iter() {
            rules_builder.push(*rule);
        }

        for (rune, tyype) in interface_a.rune_to_type.iter() {
            rune_to_type.push((*rune, *tyype));
        }
        for mr in member_runes.iter() {
            rune_to_type.push((mr.rune, ITemplataType::CoordTemplataType(CoordTemplataType {})));
        }

        let void_kind_rune = self.scout_arena.intern_rune(IRuneValS::AnonymousSubstructVoidKindRune(AnonymousSubstructVoidKindRuneS {}));
        rune_to_type.push((void_kind_rune, ITemplataType::KindTemplataType(KindTemplataType {})));
        let void_imprecise_name = self.scout_arena.intern_imprecise_name(IImpreciseNameValS::CodeName(CodeNameS { name: self.keywords.void }));
        rules_builder.push(IRulexSR::Lookup(LookupSR {
            range: range(-1672147),
            rune: use_rune(-64002, void_kind_rune),
            name: void_imprecise_name,
        }));

        let void_coord_rune = self.scout_arena.intern_rune(IRuneValS::AnonymousSubstructVoidCoordRune(AnonymousSubstructVoidCoordRuneS {}));
        rune_to_type.push((void_coord_rune, ITemplataType::CoordTemplataType(CoordTemplataType {})));
        rules_builder.push(IRulexSR::CoerceToCoord(CoerceToCoordSR {
            range: range(-1672147),
            coord_rune: use_rune(-64002, void_coord_rune),
            kind_rune: use_rune(-64002, void_kind_rune),
        }));

        let mut struct_generic_params: Vec<&'s GenericParameterS<'s>> = Vec::new();
        for gp in interface_a.generic_parameters.iter() {
            struct_generic_params.push(*gp);
        }
        for mr in member_runes.iter() {
            let gp = self.scout_arena.alloc(GenericParameterS {
                range: mr.range,
                rune: *mr,
                tyype: IGenericParameterTypeS::CoordGenericParameterType(
                    CoordGenericParameterTypeS {
                        coord_region: None,
                        kind_mutable: true,
                        region_mutable: false,
                    }),
                default: None,
            });
            struct_generic_params.push(gp);
        }

        use crate::postparsing::names::{
            AnonymousSubstructMethodSelfBorrowCoordRuneS,
            AnonymousSubstructMethodSelfOwnCoordRuneS,
            AnonymousSubstructFunctionBoundParamsListRuneS,
            AnonymousSubstructFunctionInterfaceTemplateRuneS,
            AnonymousSubstructFunctionInterfaceKindRuneS,
            AnonymousSubstructFunctionBoundPrototypeRuneS,
            AnonymousSubstructDropBoundParamsListRuneS,
            AnonymousSubstructDropBoundPrototypeRuneS,
        };
        for ((internal_method, member_rune), _method_index) in
            interface_a.internal_methods.iter().zip(member_runes.iter()).zip(0i32..) {
            let internal_method = *internal_method;
            for (method_rune, tyype) in internal_method.rune_to_type.iter() {
                let inherited = self.inherited_method_rune_anonymous_interface(interface_a, internal_method, *method_rune);
                rune_to_type.push((inherited, *tyype));
            }
            for rule in internal_method.rules.iter() {
                let mapped = self.map_runes_anonymous_interface(*rule, |method_rune| {
                    self.inherited_method_rune_anonymous_interface(interface_a, internal_method, method_rune)
                });
                rules_builder.push(mapped);
            }

            let original_ret_rune = internal_method.maybe_ret_coord_rune.unwrap();
            let return_rune = RuneUsage {
                range: original_ret_rune.range,
                rune: self.inherited_method_rune_anonymous_interface(interface_a, internal_method, original_ret_rune.rune),
            };

            // __call bound block
            {
                let self_borrow_coord_rune_s = self.scout_arena.intern_rune(IRuneValS::AnonymousSubstructMethodSelfBorrowCoordRune(
                    AnonymousSubstructMethodSelfBorrowCoordRuneS {
                        interface: *interface_a.name,
                        method: internal_method.name,
                    }));
                rune_to_type.push((self_borrow_coord_rune_s, ITemplataType::CoordTemplataType(CoordTemplataType {})));
                rules_builder.push(IRulexSR::Augment(AugmentSR {
                    range: internal_method.range,
                    result_rune: RuneUsage { range: internal_method.range, rune: self_borrow_coord_rune_s },
                    ownership: Some(OwnershipP::Borrow),
                    inner_rune: *member_rune,
                }));

                let mut param_runes: Vec<RuneUsage<'s>> = Vec::new();
                for param in internal_method.params.iter() {
                    match param.virtuality {
                        None => {
                            param_runes.push(RuneUsage {
                                range: param.pattern.range,
                                rune: self.inherited_method_rune_anonymous_interface(
                                    interface_a, internal_method, param.pattern.coord_rune.unwrap().rune),
                            });
                        }
                        Some(_) => {
                            param_runes.push(RuneUsage {
                                range: param.pattern.range,
                                rune: self_borrow_coord_rune_s,
                            });
                        }
                    }
                }
                let method_params_list_rune = RuneUsage {
                    range: internal_method.range,
                    rune: self.scout_arena.intern_rune(IRuneValS::AnonymousSubstructFunctionBoundParamsListRune(
                        AnonymousSubstructFunctionBoundParamsListRuneS {
                            interface: *interface_a.name,
                            method: internal_method.name,
                        })),
                };
                let param_runes_slice = self.scout_arena.alloc_slice_from_vec(param_runes);
                rules_builder.push(IRulexSR::Pack(PackSR {
                    range: internal_method.range,
                    result_rune: method_params_list_rune,
                    members: param_runes_slice,
                }));
                let coord_type_ref = self.scout_arena.alloc(ITemplataType::CoordTemplataType(CoordTemplataType {}));
                rune_to_type.push((method_params_list_rune.rune, ITemplataType::PackTemplataType(PackTemplataType { element_type: coord_type_ref })));

                let interface_params: Vec<&'s ParameterS<'s>> = internal_method.params.iter()
                    .filter(|p| p.virtuality.is_some())
                    .collect();
                assert_eq!(interface_params.len(), 1, "vassertOne");
                let interface_param = interface_params[0];
                let original_interface_coord_rune = interface_param.pattern.coord_rune.unwrap().rune;
                let interface_coord_rune = RuneUsage {
                    range: interface_param.range,
                    rune: self.inherited_method_rune_anonymous_interface(
                        interface_a, internal_method, interface_param.pattern.coord_rune.unwrap().rune),
                };
                rune_to_type.push((interface_coord_rune.rune, ITemplataType::CoordTemplataType(CoordTemplataType {})));

                let mut collected: Vec<IRuneS<'s>> = Vec::new();
                for rule in internal_method.rules.iter() {
                    match rule {
                        IRulexSR::Augment(a) if a.result_rune.rune.ptr_eq(&original_interface_coord_rune) => {
                            collected.push(a.inner_rune.rune);
                        }
                        _ => {}
                    }
                }
                assert_eq!(collected.len(), 1, "vassertOne");
                let method_interface_coord_rune = RuneUsage {
                    range: interface_param.range,
                    rune: self.inherited_method_rune_anonymous_interface(interface_a, internal_method, collected[0]),
                };

                let method_interface_template_rune = RuneUsage {
                    range: interface_param.range,
                    rune: self.scout_arena.intern_rune(IRuneValS::AnonymousSubstructFunctionInterfaceTemplateRune(
                        AnonymousSubstructFunctionInterfaceTemplateRuneS {
                            interface: *interface_a.name,
                            method: internal_method.name,
                        })),
                };
                rune_to_type.push((method_interface_template_rune.rune, ITemplataType::TemplateTemplataType(interface_a.tyype)));

                let method_interface_kind_rune = RuneUsage {
                    range: interface_param.range,
                    rune: self.scout_arena.intern_rune(IRuneValS::AnonymousSubstructFunctionInterfaceKindRune(
                        AnonymousSubstructFunctionInterfaceKindRuneS {
                            interface: *interface_a.name,
                            method: internal_method.name,
                        })),
                };
                rune_to_type.push((method_interface_kind_rune.rune, ITemplataType::KindTemplataType(KindTemplataType {})));

                rules_builder.push(IRulexSR::Lookup(LookupSR {
                    range: interface_param.range,
                    rune: method_interface_template_rune,
                    name: interface_a.name.get_imprecise_name(self.scout_arena),
                }));
                let generic_param_runes: Vec<RuneUsage<'s>> = interface_a.generic_parameters.iter().map(|gp| gp.rune).collect();
                let generic_param_runes_slice = self.scout_arena.alloc_slice_from_vec(generic_param_runes);
                rules_builder.push(IRulexSR::Call(CallSR {
                    range: interface_param.range,
                    result_rune: method_interface_kind_rune,
                    template_rune: method_interface_template_rune,
                    args: generic_param_runes_slice,
                }));
                rules_builder.push(IRulexSR::CoerceToCoord(CoerceToCoordSR {
                    range: interface_param.range,
                    coord_rune: method_interface_coord_rune,
                    kind_rune: method_interface_kind_rune,
                }));

                let method_prototype_rune = RuneUsage {
                    range: internal_method.range,
                    rune: self.scout_arena.intern_rune(IRuneValS::AnonymousSubstructFunctionBoundPrototypeRune(
                        AnonymousSubstructFunctionBoundPrototypeRuneS {
                            interface: *interface_a.name,
                            method: internal_method.name,
                        })),
                };
                rules_builder.push(IRulexSR::DefinitionFunc(DefinitionFuncSR {
                    range: internal_method.range,
                    result_rune: method_prototype_rune,
                    name: self.keywords.underscores_call,
                    params_list_rune: method_params_list_rune,
                    return_rune,
                }));
                rules_builder.push(IRulexSR::CallSiteFunc(CallSiteFuncSR {
                    range: internal_method.range,
                    prototype_rune: method_prototype_rune,
                    name: self.keywords.underscores_call,
                    params_list_rune: method_params_list_rune,
                    return_rune,
                }));
                rules_builder.push(IRulexSR::Resolve(ResolveSR {
                    range: internal_method.range,
                    result_rune: method_prototype_rune,
                    name: self.keywords.underscores_call,
                    params_list_rune: method_params_list_rune,
                    return_rune,
                }));
                rune_to_type.push((method_prototype_rune.rune, ITemplataType::PrototypeTemplataType(PrototypeTemplataType {})));
            }

            // drop bound block
            {
                let self_own_coord_rune_s = self.scout_arena.intern_rune(IRuneValS::AnonymousSubstructMethodSelfOwnCoordRune(
                    AnonymousSubstructMethodSelfOwnCoordRuneS {
                        interface: *interface_a.name,
                        method: internal_method.name,
                    }));
                rune_to_type.push((self_own_coord_rune_s, ITemplataType::CoordTemplataType(CoordTemplataType {})));
                rules_builder.push(IRulexSR::Augment(AugmentSR {
                    range: internal_method.range,
                    result_rune: RuneUsage { range: internal_method.range, rune: self_own_coord_rune_s },
                    ownership: Some(OwnershipP::Own),
                    inner_rune: *member_rune,
                }));

                let drop_params_list_rune = RuneUsage {
                    range: internal_method.range,
                    rune: self.scout_arena.intern_rune(IRuneValS::AnonymousSubstructDropBoundParamsListRune(
                        AnonymousSubstructDropBoundParamsListRuneS {
                            interface: *interface_a.name,
                            method: internal_method.name,
                        })),
                };
                let drop_params_slice = self.scout_arena.alloc_slice_from_vec(vec![RuneUsage {
                    range: internal_method.range,
                    rune: self_own_coord_rune_s,
                }]);
                rules_builder.push(IRulexSR::Pack(PackSR {
                    range: internal_method.range,
                    result_rune: drop_params_list_rune,
                    members: drop_params_slice,
                }));
                let coord_type_ref2 = self.scout_arena.alloc(ITemplataType::CoordTemplataType(CoordTemplataType {}));
                rune_to_type.push((drop_params_list_rune.rune, ITemplataType::PackTemplataType(PackTemplataType { element_type: coord_type_ref2 })));

                let drop_prototype_rune = RuneUsage {
                    range: internal_method.range,
                    rune: self.scout_arena.intern_rune(IRuneValS::AnonymousSubstructDropBoundPrototypeRune(
                        AnonymousSubstructDropBoundPrototypeRuneS {
                            interface: *interface_a.name,
                            method: internal_method.name,
                        })),
                };
                let void_coord_ru = RuneUsage { range: internal_method.range, rune: void_coord_rune };
                rules_builder.push(IRulexSR::DefinitionFunc(DefinitionFuncSR {
                    range: internal_method.range,
                    result_rune: drop_prototype_rune,
                    name: self.keywords.drop,
                    params_list_rune: drop_params_list_rune,
                    return_rune: void_coord_ru,
                }));
                rules_builder.push(IRulexSR::CallSiteFunc(CallSiteFuncSR {
                    range: internal_method.range,
                    prototype_rune: drop_prototype_rune,
                    name: self.keywords.drop,
                    params_list_rune: drop_params_list_rune,
                    return_rune: void_coord_ru,
                }));
                rules_builder.push(IRulexSR::Resolve(ResolveSR {
                    range: internal_method.range,
                    result_rune: drop_prototype_rune,
                    name: self.keywords.drop,
                    params_list_rune: drop_params_list_rune,
                    return_rune: void_coord_ru,
                }));
                rune_to_type.push((drop_prototype_rune.rune, ITemplataType::PrototypeTemplataType(PrototypeTemplataType {})));
            }
        }

        let member_coord_types: Vec<ITemplataType<'s>> = member_runes.iter()
            .map(|_mr| ITemplataType::CoordTemplataType(CoordTemplataType {}))
            .collect();
        let mut param_types: Vec<ITemplataType<'s>> = interface_a.tyype.param_types.to_vec();
        param_types.extend(member_coord_types);
        let param_types_slice = self.scout_arena.alloc_slice_from_vec(param_types);
        let kind_type = self.scout_arena.alloc(ITemplataType::KindTemplataType(KindTemplataType {}));
        let tyype = TemplateTemplataType {
            param_types: param_types_slice,
            return_type: kind_type,
        };

        let header_rune_to_type = self.scout_arena.alloc_index_map_from_iter(rune_to_type);
        let header_rules_slice = self.scout_arena.alloc_slice_from_vec(rules_builder);
        let members_rune_to_type = self.scout_arena.alloc_index_map::<IRuneS<'s>, ITemplataType<'s>>();
        let member_rules_slice: &'s [IRulexSR<'s>] = self.scout_arena.alloc_slice_from_vec(vec![]);
        let generic_params_slice = self.scout_arena.alloc_slice_from_vec(struct_generic_params);
        let attributes_slice: &'s [ICitizenAttributeS<'s>] = self.scout_arena.alloc_slice_from_vec(vec![]);
        let members_slice: &'s [IStructMemberS<'s>] = self.scout_arena.alloc_slice_from_vec(
            members.iter().map(|m| IStructMemberS::NormalStructMember(*m)).collect::<Vec<_>>());

        let struct_a = StructA::new(
            interface_a.range,
            IStructDeclarationNameS::AnonymousSubstructTemplateName(
                *self.scout_arena.alloc(struct_template_name_s)),
            attributes_slice,
            false,
            interface_a.mutability_rune,
            interface_a.maybe_predicted_mutability,
            tyype,
            generic_params_slice,
            header_rune_to_type,
            header_rules_slice,
            members_rune_to_type,
            member_rules_slice,
            members_slice,
            &[],
        );
        self.scout_arena.alloc(struct_a)
    }

    pub fn make_forwarder_function_anonymous_interface(
        &self,
        struct_name_s: AnonymousSubstructTemplateNameS<'s>,
        interface: &'s InterfaceA<'s>,
        struct_: &'s StructA<'s>,
        method: &'s FunctionA<'s>,
        method_index: i32,
    ) -> &'s FunctionA<'s> {
        use crate::postparsing::names::{
            IRuneValS, INameValS, IVarNameS, SelfOwnershipRuneS, SelfKindRuneS, SelfCoordRuneS,
            SelfKindTemplateRuneS, AnonymousSubstructParentInterfaceTemplateRuneS,
            AnonymousSubstructTemplateImpreciseNameValS, IImpreciseNameValS,
            IFunctionDeclarationNameValS, ForwarderFunctionDeclarationNameValS,
            INameS, IRuneS,
        };

        let struct_type = struct_.tyype;
        let method_range = method.range;
        let attributes = method.attributes;
        let method_original_type = method.tyype;
        let method_original_identifying_runes: &'s [&'s GenericParameterS<'s>] = method.generic_parameters;
        let original_params = method.params;
        let method_original_rules = method.rules;

        // vassert(struct.genericParameters.map(_.rune).startsWith(methodOriginalIdentifyingRunes.map(_.rune)))
        let starts_with = struct_.generic_parameters.len() >= method_original_identifying_runes.len()
            && struct_.generic_parameters.iter().zip(method_original_identifying_runes.iter())
                .all(|(a, b)| a.rune.rune.ptr_eq(&b.rune.rune));
        assert!(starts_with, "vassert: struct.genericParameters.startsWith(methodOriginalIdentifyingRunes)");

        let mut generic_params_vec: Vec<&'s GenericParameterS<'s>> = Vec::new();
        for gp in struct_.generic_parameters.iter() {
            let new_rune = self.inherited_method_rune_anonymous_interface(interface, method, gp.rune.rune);
            generic_params_vec.push(self.scout_arena.alloc(GenericParameterS {
                range: gp.range,
                rune: RuneUsage { range: gp.rune.range, rune: new_rune },
                tyype: gp.tyype,
                default: gp.default,
            }));
        }

        let mut rune_to_type: Vec<(IRuneS<'s>, ITemplataType<'s>)> = Vec::new();
        let mut rules: Vec<IRulexSR<'s>> = Vec::new();

        for (method_rune, tyype) in method.rune_to_type.iter() {
            let inherited = self.inherited_method_rune_anonymous_interface(interface, method, *method_rune);
            rune_to_type.push((inherited, *tyype));
        }
        for rule in method_original_rules.iter() {
            let mapped = self.map_runes_anonymous_interface(*rule, |method_rune| {
                self.inherited_method_rune_anonymous_interface(interface, method, method_rune)
            });
            rules.push(mapped);
        }
        let original_ret_rune = method.maybe_ret_coord_rune.unwrap();
        let inherited_return_rune = RuneUsage {
            range: original_ret_rune.range,
            rune: self.inherited_method_rune_anonymous_interface(interface, method, original_ret_rune.rune),
        };

        for param in struct_.generic_parameters.iter() {
            let inh = self.inherited_method_rune_anonymous_interface(interface, method, param.rune.rune);
            rune_to_type.push((inh, param.tyype.tyype()));
        }

        let self_ownership_rune = self.scout_arena.intern_rune(IRuneValS::SelfOwnershipRune(SelfOwnershipRuneS {}));
        rune_to_type.push((self_ownership_rune, ITemplataType::OwnershipTemplataType(OwnershipTemplataType {})));
        let interface_kind_rune = self.scout_arena.intern_rune(IRuneValS::AnonymousSubstructParentInterfaceTemplateRune(AnonymousSubstructParentInterfaceTemplateRuneS {}));
        rune_to_type.push((interface_kind_rune, ITemplataType::KindTemplataType(KindTemplataType {})));
        let self_kind_rune = self.scout_arena.intern_rune(IRuneValS::SelfKindRune(SelfKindRuneS {}));
        rune_to_type.push((self_kind_rune, ITemplataType::KindTemplataType(KindTemplataType {})));
        let self_coord_rune = self.scout_arena.intern_rune(IRuneValS::SelfCoordRune(SelfCoordRuneS {}));
        rune_to_type.push((self_coord_rune, ITemplataType::CoordTemplataType(CoordTemplataType {})));
        let self_kind_template_rune = self.scout_arena.intern_rune(IRuneValS::SelfKindTemplateRune(SelfKindTemplateRuneS { loc: struct_.range.begin }));
        rune_to_type.push((self_kind_template_rune, ITemplataType::TemplateTemplataType(struct_type)));

        let mut abstract_param_index: i32 = -1;
        for (i, param) in original_params.iter().enumerate() {
            let is_abstract = match param.virtuality {
                Some(AbstractSP { .. }) => true,
                None => false,
            };
            if is_abstract {
                abstract_param_index = i as i32;
                break;
            }
        }
        assert!(abstract_param_index >= 0, "vassert: abstractParamIndex >= 0");
        let abstract_param = &original_params[abstract_param_index as usize];
        let abstract_param_range = abstract_param.pattern.range;
        let abstract_param_coord_rune = RuneUsage {
            range: abstract_param_range,
            rune: self.inherited_method_rune_anonymous_interface(
                interface, method, abstract_param.pattern.coord_rune.unwrap().rune),
        };
        rune_to_type.push((abstract_param_coord_rune.rune, ITemplataType::CoordTemplataType(CoordTemplataType {})));

        let destructuring_interface_rule = IRulexSR::CoordComponents(CoordComponentsSR {
            range: abstract_param_range,
            result_rune: abstract_param_coord_rune,
            ownership_rune: RuneUsage { range: abstract_param_range, rune: self_ownership_rune },
            kind_rune: RuneUsage { range: abstract_param_range, rune: interface_kind_rune },
        });
        rules.push(destructuring_interface_rule);

        let struct_interface_imprecise = struct_name_s.interface_name.get_imprecise_name(self.scout_arena);
        let lookup_struct_template_rule = IRulexSR::Lookup(LookupSR {
            range: abstract_param_range,
            rune: RuneUsage { range: abstract_param_range, rune: self_kind_template_rune },
            name: self.scout_arena.intern_imprecise_name(IImpreciseNameValS::AnonymousSubstructTemplateImpreciseName(
                AnonymousSubstructTemplateImpreciseNameValS { interface_imprecise_name: struct_interface_imprecise })),
        });
        rules.push(lookup_struct_template_rule);

        let gp_runes_vec: Vec<RuneUsage<'s>> = generic_params_vec.iter().map(|gp| gp.rune).collect();
        let gp_runes_slice = self.scout_arena.alloc_slice_from_vec(gp_runes_vec);
        let lookup_struct_rule = IRulexSR::Call(CallSR {
            range: abstract_param_range,
            result_rune: RuneUsage { range: abstract_param_range, rune: self_kind_rune },
            template_rune: RuneUsage { range: abstract_param_range, rune: self_kind_template_rune },
            args: gp_runes_slice,
        });
        rules.push(lookup_struct_rule);

        let assembling_struct_rule = IRulexSR::CoordComponents(CoordComponentsSR {
            range: abstract_param_range,
            result_rune: RuneUsage { range: abstract_param_range, rune: self_coord_rune },
            ownership_rune: RuneUsage { range: abstract_param_range, rune: self_ownership_rune },
            kind_rune: RuneUsage { range: abstract_param_range, rune: self_kind_rune },
        });
        rules.push(assembling_struct_rule);

        let mut new_params_vec: Vec<ParameterS<'s>> = Vec::new();
        for param in original_params.iter() {
            match param.virtuality {
                Some(_) => {
                    new_params_vec.push(ParameterS {
                        range: abstract_param_range,
                        virtuality: None,
                        pre_checked: false,
                        pattern: AtomSP {
                            range: abstract_param_range,
                            name: Some(CaptureS { name: IVarNameS::SelfName, mutate: false }),
                            coord_rune: Some(RuneUsage { range: abstract_param_coord_rune.range, rune: self_coord_rune }),
                            destructure: None,
                        },
                    });
                }
                None => {
                    let old_rune_usage = param.pattern.coord_rune.unwrap();
                    let new_rune = RuneUsage {
                        range: old_rune_usage.range,
                        rune: self.inherited_method_rune_anonymous_interface(interface, method, old_rune_usage.rune),
                    };
                    new_params_vec.push(ParameterS {
                        range: param.range,
                        virtuality: param.virtuality,
                        pre_checked: param.pre_checked,
                        pattern: AtomSP {
                            range: param.pattern.range,
                            name: param.pattern.name,
                            coord_rune: Some(new_rune),
                            destructure: param.pattern.destructure,
                        },
                    });
                }
            }
        }

        // Body: FunctionCallSE(DotSE(LocalLoad(self), index, false), args)
        let self_local_load = self.scout_arena.alloc(IExpressionSE::LocalLoad(LocalLoadSE {
            range: method_range,
            name: IVarNameS::SelfName,
            target_ownership: LoadAsP::Use,
        }));
        let dot_member = self.scout_arena.intern_str(&method_index.to_string());
        let callable_expr = self.scout_arena.alloc(IExpressionSE::Dot(DotSE {
            range: method_range,
            left: self_local_load,
            member: dot_member,
            borrow_container: false,
        }));

        let mut call_args: Vec<&'s IExpressionSE<'s>> = Vec::new();
        for (i, param) in new_params_vec.iter().enumerate() {
            if (i as i32) == abstract_param_index { continue; }
            let nm = param.pattern.name.unwrap().name;
            call_args.push(self.scout_arena.alloc(IExpressionSE::LocalLoad(LocalLoadSE {
                range: method_range,
                name: nm,
                target_ownership: LoadAsP::Use,
            })));
        }
        let call_args_slice = self.scout_arena.alloc_slice_from_vec(call_args);

        let new_body_expr = self.scout_arena.alloc(IExpressionSE::FunctionCall(FunctionCallSE {
            range: method_range,
            location: LocationInDenizen { path: &[] },
            callable_expr,
            arg_exprs: call_args_slice,
        }));

        let locals_vec: Vec<LocalS<'s>> = new_params_vec.iter().map(|p| {
            let nm = p.pattern.name.unwrap().name;
            LocalS {
                var_name: nm,
                self_borrowed: IVariableUseCertainty::NotUsed,
                self_moved: IVariableUseCertainty::Used,
                self_mutated: IVariableUseCertainty::NotUsed,
                child_borrowed: IVariableUseCertainty::NotUsed,
                child_moved: IVariableUseCertainty::NotUsed,
                child_mutated: IVariableUseCertainty::NotUsed,
            }
        }).collect();
        let locals_slice = self.scout_arena.alloc_slice_from_vec(locals_vec);
        let block_se = self.scout_arena.alloc(BlockSE {
            range: method_range,
            locals: locals_slice,
            expr: new_body_expr,
        });
        let body_se = self.scout_arena.alloc(BodySE {
            range: method_range,
            closured_names: self.scout_arena.alloc_slice_from_vec::<IVarNameS<'s>>(vec![]),
            block: block_se,
        });
        let body = IBodyS::CodeBody(CodeBodyS { body: body_se });

        // Forwarder name
        let forwarder_name = match self.scout_arena.intern_name(INameValS::FunctionDeclaration(
            IFunctionDeclarationNameValS::ForwarderFunctionDeclarationName(ForwarderFunctionDeclarationNameValS {
                inner: method.name,
                index: method_index,
            }))) {
            INameS::FunctionDeclaration(r) => *r,
            _ => panic!("vwat: intern_name returned non-FunctionDeclaration"),
        };

        // Tyype: param_types ++ struct.genericParameters.map(_ => CoordTemplataType()), return FunctionTemplataType
        let mut new_param_types: Vec<ITemplataType<'s>> = method_original_type.param_types.to_vec();
        for _ in struct_.generic_parameters.iter() {
            new_param_types.push(ITemplataType::CoordTemplataType(CoordTemplataType {}));
        }
        let new_param_types_slice = self.scout_arena.alloc_slice_from_vec(new_param_types);
        let return_type_ref = self.scout_arena.alloc(ITemplataType::FunctionTemplataType(FunctionTemplataType {}));
        let new_tyype = TemplateTemplataType { param_types: new_param_types_slice, return_type: return_type_ref };

        let new_params_slice = self.scout_arena.alloc_slice_from_vec(new_params_vec);
        let rules_slice = self.scout_arena.alloc_slice_from_vec(rules);
        let generic_params_slice = self.scout_arena.alloc_slice_from_vec(generic_params_vec);
        let rune_to_type_map = self.scout_arena.alloc_index_map_from_iter(rune_to_type);

        self.scout_arena.alloc(FunctionA::new(
            method_range,
            forwarder_name,
            attributes,
            new_tyype,
            generic_params_slice,
            rune_to_type_map,
            new_params_slice,
            Some(inherited_return_rune),
            rules_slice,
            body,
        ))
    }

}
