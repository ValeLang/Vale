use crate::interner::StrI;
use crate::utils::range::RangeS;
use crate::postparsing::names::*;
use crate::higher_typing::ast::*;
use crate::typing::names::names::*;
use crate::typing::types::types::*;
use crate::typing::ast::ast::*;
use crate::typing::ast::expressions::*;
use crate::typing::env::environment::*;
use crate::typing::env::function_environment_t::*;
use crate::typing::env::i_env_entry::*;
use crate::typing::compiler_outputs::*;
use crate::typing::compiler::Compiler;
use crate::typing::templata::templata::*;
use crate::typing::templata_compiler::IBoundArgumentsSource;
use crate::typing::ast::citizens::{IStructMemberT, IMemberTypeT};
use crate::postparsing::ast::LocationInDenizen;
use crate::postparsing::names::{IRuneValS, MacroVoidKindRuneS, MacroVoidCoordRuneS, SelfKindTemplateRuneS, SelfKindRuneS, SelfCoordRuneS, IVarNameS, CodeVarNameS, IFunctionDeclarationNameValS, INameValS, FunctionNameS, IFunctionDeclarationNameS};
use crate::postparsing::rules::rules::{LookupSR, CallSR, CoerceToCoordSR, IRulexSR, RuneUsage};
use crate::postparsing::patterns::patterns::{CaptureS, AtomSP};
use crate::postparsing::ast::{ParameterS, IBodyS, GeneratedBodyS};
use crate::postparsing::itemplatatype::{ITemplataType, CoordTemplataType, KindTemplataType, TemplateTemplataType, FunctionTemplataType};
use crate::typing::names::names::{IFunctionTemplateNameT, INameT};
use crate::utils::range::CodeLocationS;
use std::collections::HashMap;
use crate::postparsing::itemplatatype::*;
use crate::postparsing::names::IImpreciseNameValS;
use crate::postparsing::names::CodeNameS;
use crate::higher_typing::ast::FunctionA;
use std::marker::PhantomData;

// (Scala `class StructDropMacro(opts, interner, keywords, nameTranslator, destructorCompiler)`
//  absorbed onto `Compiler`; the three method bodies live at
//  `Compiler::get_struct_sibling_entries_struct_drop`,
//  `Compiler::make_implicit_drop_function_struct_drop`, and
//  `Compiler::generate_function_body_struct_drop` below.)

impl<'s, 'ctx, 't> Compiler<'s, 'ctx, 't>
where 's: 't,
{
    pub fn get_struct_sibling_entries_struct_drop(
        &self,
        struct_name: IdT<'s, 't>,
        struct_a: &'s StructA<'s>,
    ) -> Vec<(IdT<'s, 't>, IEnvEntryT<'s, 't>)> {

        let range = |n: i32| -> RangeS<'s> {
            let loc = CodeLocationS::internal(self.scout_arena, n);
            RangeS { begin: loc, end: loc }
        };
        let use_ = |n: i32, rune| RuneUsage { range: range(n), rune };

        let mut rules: Vec<IRulexSR<'s>> = Vec::new();
        // Use the same rules as the original struct, see MDSFONARFO.
        for r in struct_a.header_rules.iter() { rules.push(*r); }
        let mut rune_to_type: HashMap<_, _> = HashMap::new();
        // Use the same runes as the original struct, see MDSFONARFO.
        for (k, v) in struct_a.header_rune_to_type.iter() { rune_to_type.insert(*k, *v); }

        let void_kind_rune_s = self.scout_arena.intern_rune(IRuneValS::MacroVoidKindRune(MacroVoidKindRuneS {}));
        rune_to_type.insert(void_kind_rune_s, ITemplataType::KindTemplataType(KindTemplataType {}));
        rules.push(IRulexSR::Lookup(LookupSR {
            range: range(-1672147),
            rune: use_(-64002, void_kind_rune_s),
            name: self.scout_arena.intern_imprecise_name(IImpreciseNameValS::CodeName(CodeNameS { name: self.keywords.void })),
        }));
        let void_coord_rune_s = self.scout_arena.intern_rune(IRuneValS::MacroVoidCoordRune(MacroVoidCoordRuneS {}));
        rune_to_type.insert(void_coord_rune_s, ITemplataType::CoordTemplataType(CoordTemplataType {}));
        rules.push(IRulexSR::CoerceToCoord(CoerceToCoordSR {
            range: range(-1672147),
            coord_rune: use_(-64002, void_coord_rune_s),
            kind_rune: use_(-64002, void_kind_rune_s),
        }));

        let self_kind_template_rune_s = self.scout_arena.intern_rune(IRuneValS::SelfKindTemplateRune(SelfKindTemplateRuneS { loc: struct_a.range.begin }));
        rune_to_type.insert(self_kind_template_rune_s, ITemplataType::TemplateTemplataType(struct_a.tyype));
        rules.push(IRulexSR::Lookup(LookupSR {
            range: struct_a.name.range(),
            rune: RuneUsage { range: struct_a.name.range(), rune: self_kind_template_rune_s },
            name: struct_a.name.get_imprecise_name(self.scout_arena),
        }));

        let self_kind_rune_s = self.scout_arena.intern_rune(IRuneValS::SelfKindRune(SelfKindRuneS {}));
        rune_to_type.insert(self_kind_rune_s, ITemplataType::KindTemplataType(KindTemplataType {}));
        let generic_param_runes: Vec<_> = struct_a.generic_parameters.iter().map(|p| p.rune).collect();
        let generic_param_runes_slice = self.scout_arena.alloc_slice_copy(&generic_param_runes);
        rules.push(IRulexSR::Call(CallSR {
            range: struct_a.name.range(),
            result_rune: use_(-64002, self_kind_rune_s),
            template_rune: RuneUsage { range: struct_a.name.range(), rune: self_kind_template_rune_s },
            args: generic_param_runes_slice,
        }));

        let self_coord_rune_s = self.scout_arena.intern_rune(IRuneValS::SelfCoordRune(SelfCoordRuneS {}));
        rune_to_type.insert(self_coord_rune_s, ITemplataType::CoordTemplataType(CoordTemplataType {}));
        rules.push(IRulexSR::CoerceToCoord(CoerceToCoordSR {
            range: struct_a.name.range(),
            coord_rune: RuneUsage { range: struct_a.name.range(), rune: self_coord_rune_s },
            kind_rune: RuneUsage { range: struct_a.name.range(), rune: self_kind_rune_s },
        }));

        // Use the same generic parameters as the struct
        let function_generic_parameters = struct_a.generic_parameters;

        let function_templata_type = TemplateTemplataType {
            param_types: self.scout_arena.alloc_slice_from_vec(
                function_generic_parameters.iter().map(|p| *rune_to_type.get(&p.rune.rune).unwrap()).collect()
            ),
            return_type: self.scout_arena.alloc(ITemplataType::FunctionTemplataType(FunctionTemplataType {})),
        };

        let name_s = IFunctionDeclarationNameS::FunctionName(FunctionNameS {
            name: self.keywords.drop,
            code_location: struct_a.range.begin,
        });
        let mut rune_to_type_map = self.scout_arena.alloc_index_map();
        for (k, v) in rune_to_type { rune_to_type_map.insert(k, v); }
        let rules_slice = self.scout_arena.alloc_slice_copy(&rules);
        let drop_function_a = self.scout_arena.alloc(FunctionA::new(
            struct_a.range,
            name_s,
            &[],
            function_templata_type,
            function_generic_parameters,
            rune_to_type_map,
            self.scout_arena.alloc_slice_from_vec(vec![ParameterS::new(
                range(-1340),
                None,
                false,
                AtomSP {
                    range: range(-1340),
                    name: Some(CaptureS { name: IVarNameS::CodeVarName(self.keywords.thiss), mutate: false }),
                    coord_rune: Some(use_(-64002, self_coord_rune_s)),
                    destructure: None,
                },
            )]),
            Some(use_(-64002, void_coord_rune_s)),
            rules_slice,
            IBodyS::GeneratedBody(GeneratedBodyS { generator_id: self.keywords.drop_generator }),
        ));
        let drop_name_local = match self.translate_generic_function_name(drop_function_a.name) {
            IFunctionTemplateNameT::FunctionTemplate(r) => INameT::FunctionTemplate(r),
            IFunctionTemplateNameT::ForwarderFunctionTemplate(r) => INameT::ForwarderFunctionTemplate(r),
            IFunctionTemplateNameT::ConstructorTemplate(r) => INameT::ConstructorTemplate(r),
            IFunctionTemplateNameT::AnonymousSubstructConstructorTemplate(r) => INameT::AnonymousSubstructConstructorTemplate(r),
            IFunctionTemplateNameT::LambdaCallFunctionTemplate(r) => INameT::LambdaCallFunctionTemplate(r),
            IFunctionTemplateNameT::OverrideDispatcherTemplate(r) => INameT::OverrideDispatcherTemplate(r),
            IFunctionTemplateNameT::ExternFunction(r) => INameT::ExternFunction(r),
            IFunctionTemplateNameT::FunctionBoundTemplate(r) => INameT::FunctionBoundTemplate(r),
            IFunctionTemplateNameT::PredictedFunctionTemplate(r) => INameT::PredictedFunctionTemplate(r),
        };
        let drop_name_t = struct_name.add_step(self.typing_interner, drop_name_local);
        vec![(*drop_name_t, IEnvEntryT::Function(drop_function_a))]
    }

    pub fn make_implicit_drop_function_struct_drop(
        &self,
        drop_or_free_function_name_s: IFunctionDeclarationNameS<'s>,
        struct_range: RangeS<'s>,
    ) -> FunctionA<'s> {

        let internal_range = |n: i32| {
            let loc = CodeLocationS::internal(self.scout_arena, n);
            RangeS::new(loc, loc)
        };

        let drop_p1_rune = self.scout_arena.intern_rune(IRuneValS::CodeRune(CodeRuneS { name: self.keywords.drop_p1 }));
        let drop_p1k_rune = self.scout_arena.intern_rune(IRuneValS::CodeRune(CodeRuneS { name: self.keywords.drop_p1k }));
        let drop_vk_rune = self.scout_arena.intern_rune(IRuneValS::CodeRune(CodeRuneS { name: self.keywords.drop_vk }));
        let drop_v_rune = self.scout_arena.intern_rune(IRuneValS::CodeRune(CodeRuneS { name: self.keywords.drop_v }));

        let rune_to_type = self.scout_arena.alloc_index_map_from_iter(vec![
            (drop_p1_rune, ITemplataType::CoordTemplataType(CoordTemplataType {})),
            (drop_p1k_rune, ITemplataType::KindTemplataType(KindTemplataType {})),
            (drop_vk_rune, ITemplataType::KindTemplataType(KindTemplataType {})),
            (drop_v_rune, ITemplataType::CoordTemplataType(CoordTemplataType {})),
        ]);

        let params = self.scout_arena.alloc_slice_from_vec(vec![
            ParameterS::new(
                internal_range(-1342),
                None,
                false,
                AtomSP {
                    range: internal_range(-1342),
                    name: Some(CaptureS { name: IVarNameS::CodeVarName(self.keywords.x), mutate: false }),
                    coord_rune: Some(RuneUsage { range: internal_range(-64002), rune: drop_p1_rune }),
                    destructure: None,
                }),
        ]);

        let maybe_ret_coord_rune = Some(RuneUsage { range: internal_range(-64002), rune: drop_v_rune });

        let self_name_s = self.scout_arena.intern_imprecise_name(IImpreciseNameValS::SelfName(SelfNameS {}));
        let void_name_s = self.scout_arena.intern_imprecise_name(IImpreciseNameValS::CodeName(CodeNameS { name: self.keywords.void }));

        let rules = self.scout_arena.alloc_slice_from_vec(vec![
            IRulexSR::Lookup(LookupSR {
                range: internal_range(-1672161),
                rune: RuneUsage { range: internal_range(-64002), rune: drop_p1k_rune },
                name: self_name_s,
            }),
            IRulexSR::Lookup(LookupSR {
                range: internal_range(-1672162),
                rune: RuneUsage { range: internal_range(-64002), rune: drop_vk_rune },
                name: void_name_s,
            }),
            IRulexSR::CoerceToCoord(CoerceToCoordSR {
                range: internal_range(-1672162),
                coord_rune: RuneUsage { range: internal_range(-64002), rune: drop_v_rune },
                kind_rune: RuneUsage { range: internal_range(-64002), rune: drop_vk_rune },
            }),
            IRulexSR::CoerceToCoord(CoerceToCoordSR {
                range: internal_range(-1672162),
                coord_rune: RuneUsage { range: internal_range(-64002), rune: drop_p1_rune },
                kind_rune: RuneUsage { range: internal_range(-64002), rune: drop_p1k_rune },
            }),
        ]);

        FunctionA::new(
            struct_range,
            drop_or_free_function_name_s,
            self.scout_arena.alloc_slice_from_vec(vec![]),
            TemplateTemplataType {
                param_types: self.scout_arena.alloc_slice_from_vec(vec![]),
                return_type: self.scout_arena.alloc(ITemplataType::FunctionTemplataType(FunctionTemplataType {})),
            },
            self.scout_arena.alloc_slice_from_vec(vec![]),
            rune_to_type,
            params,
            maybe_ret_coord_rune,
            rules,
            IBodyS::GeneratedBody(GeneratedBodyS { generator_id: self.keywords.drop_generator }),
        )
    }

    pub fn generate_function_body_struct_drop(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        env: &'t FunctionEnvironmentT<'s, 't>,
        generator_id: StrI<'s>,
        life: LocationInFunctionEnvironmentT<'t>,
        call_range: &[RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        origin_function1: Option<&'s FunctionA<'s>>,
        params2: &[ParameterT<'s, 't>],
        maybe_ret_coord: Option<CoordT<'s, 't>>,
    ) -> (FunctionHeaderT<'s, 't>, ReferenceExpressionTE<'s, 't>) {
        let body_env = IInDenizenEnvironmentT::Function(env);

        let struct_tt = match params2[0].tyype.kind {
            KindT::Struct(s) => s,
            _ => panic!("struct drop: first param is not a struct"),
        };
        let struct_def = coutputs.lookup_struct(struct_tt.id, self);
        let struct_ownership = match struct_def.mutability {
            ITemplataT::Mutability(MutabilityTemplataT { mutability: MutabilityT::Mutable }) => OwnershipT::Own,
            ITemplataT::Mutability(MutabilityTemplataT { mutability: MutabilityT::Immutable }) => OwnershipT::Share,
            ITemplataT::Placeholder(_) => OwnershipT::Own,
            _ => panic!("struct drop: unexpected mutability"),
        };
        let struct_type = CoordT { ownership: struct_ownership, region: RegionT { region: IRegionT::Default }, kind: KindT::Struct(struct_tt) };

        let ret = CoordT { ownership: OwnershipT::Share, region: RegionT { region: IRegionT::Default }, kind: KindT::Void(VoidT {}) };
        let params_arena: &'t [ParameterT<'s, 't>] = self.typing_interner.alloc_slice_from_vec(params2.to_vec());
        let header = FunctionHeaderT {
            id: env.id,
            attributes: &[],
            params: params_arena,
            return_type: ret,
            maybe_origin_function_templata: Some(env.templata()),
        };

        coutputs.declare_function_return_type(
            self.typing_interner.alloc(header.to_signature()), header.return_type);

        let body_expr: ReferenceExpressionTE<'s, 't> = match struct_def.mutability {
            ITemplataT::Mutability(MutabilityTemplataT { mutability: MutabilityT::Immutable }) => {
                ReferenceExpressionTE::Discard(self.typing_interner.alloc(DiscardTE {
                    expr: ReferenceExpressionTE::ArgLookup(self.typing_interner.alloc(ArgLookupTE { param_index: 0, coord: struct_type })),
                }))
            }
            ITemplataT::Mutability(MutabilityTemplataT { mutability: MutabilityT::Mutable }) |
            ITemplataT::Placeholder(_) => {
                let member_local_variables: Vec<ReferenceLocalVariableT<'s, 't>> =
                    struct_def.members.iter().flat_map(|member| {
                        match member {
                            IStructMemberT::Normal(n) => {
                                match &n.tyype {
                                    IMemberTypeT::Reference(r) => {
                                        let substituter = self.get_placeholder_substituter(
                                            self.opts.global_options.sanity_check,
                                            env.template_id,
                                            struct_tt.id,
                                            IBoundArgumentsSource::InheritBoundsFromTypeItself,
                                        );
                                        let reference = substituter.substitute_for_coord(coutputs, r.reference);
                                        vec![ReferenceLocalVariableT { name: n.name, variability: VariabilityT::Final, coord: reference }]
                                    }
                                    IMemberTypeT::Address(_) => vec![],
                                }
                            }
                            IStructMemberT::Variadic(_) => panic!("vimpl: VariadicStructMemberT in struct drop"),
                        }
                    }).collect();
                let member_local_variables_slice = self.typing_interner.alloc_slice_from_vec(member_local_variables.clone());
                let arg_lookup = ReferenceExpressionTE::ArgLookup(self.typing_interner.alloc(ArgLookupTE { param_index: 0, coord: struct_type }));
                let destroy = ReferenceExpressionTE::Destroy(self.typing_interner.alloc(DestroyTE {
                    expr: arg_lookup,
                    struct_tt,
                    destination_reference_variables: member_local_variables_slice,
                }));
                let origin_range: Vec<RangeS<'s>> = origin_function1.map(|f| f.range).into_iter().collect();
                let drop_call_range: Vec<RangeS<'s>> = origin_range.into_iter().chain(call_range.iter().copied()).collect();
                let drop_call_range_slice = self.typing_interner.alloc_slice_from_vec(drop_call_range);
                let drop_exprs: Vec<ReferenceExpressionTE<'s, 't>> = member_local_variables.iter().map(|v| {
                    let unlet = ReferenceExpressionTE::Unlet(self.typing_interner.alloc(UnletTE {
                        variable: ILocalVariableT::Reference(*v),
                    }));
                    // Until a test path forces Result conversion through struct_drop_macro.
                    self.drop(body_env, coutputs, drop_call_range_slice, call_location, RegionT { region: IRegionT::Default }, unlet)
                        .unwrap_or_else(|_| panic!("Unimplemented: Result propagation through struct_drop_macro"))
                }).collect();
                let mut all_exprs: Vec<ReferenceExpressionTE<'s, 't>> = vec![destroy];
                all_exprs.extend(drop_exprs.into_iter());
                self.consecutive(&all_exprs)
            }
            _ => panic!("struct drop: unexpected mutability"),
        };

        let return_expr =
            ReferenceExpressionTE::Return(self.typing_interner.alloc(ReturnTE {
                source_expr: ReferenceExpressionTE::VoidLiteral(self.typing_interner.alloc(VoidLiteralTE { region: RegionT { region: IRegionT::Default }})),
            }));
        let body = ReferenceExpressionTE::Block(self.typing_interner.alloc(BlockTE {
            inner: self.consecutive(&[body_expr, return_expr]),
        }));

        (header, body)
    }

}
