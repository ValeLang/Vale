use crate::typing::compiler::Compiler;
use crate::postparsing::ast::{LocationInDenizen, FunctionS, IFunctionAttributeS, UserFunctionS, IExpressionSE as IExpressionSETrait};
use crate::postparsing::itemplatatype::{ITemplataType, CoordTemplataType};
use crate::utils::range::RangeS;
use crate::postparsing::names::*;
use crate::postparsing::expressions::*;
use crate::postparsing::patterns::patterns::AtomSP;
use crate::postparsing::rules::rules::IRulexSR;
use crate::higher_typing::ast::FunctionA;
use crate::typing::ast::ast::*;
use crate::typing::ast::expressions::*;
use crate::typing::env::environment::*;
use crate::typing::env::function_environment_t::*;
use crate::typing::names::names::*;
use crate::typing::types::types::*;
use crate::typing::templata::templata::*;
use crate::typing::compiler_outputs::*;
use crate::parsing::ast::*;
use indexmap::IndexMap;
use std::collections::{HashMap, HashSet};
use crate::typing::templata_compiler::IBoundArgumentsSource;
use crate::typing::compiler_error_reporter::ICompileErrorT;
use crate::typing::ast::citizens::{IStructMemberT, NormalStructMemberT, IMemberTypeT, ReferenceMemberTypeT, AddressMemberTypeT};
use crate::typing::env::environment::ILookupContext;
use crate::postparsing::names::{IImpreciseNameValS, CodeNameS};
use crate::typing::env::environment::IEnvironmentT;
use crate::typing::env::environment::IInDenizenEnvironmentT;
use crate::typing::templata::templata::{ITemplataT, CoordTemplataT};
use crate::typing::citizen::struct_compiler::IResolveOutcome;
use crate::typing::function::function_compiler::IResolveFunctionResult;
use crate::typing::types::types::{CoordT, OwnershipT, KindT, ISubKindTT, ISuperKindTT, InterfaceTTValT};
use crate::typing::citizen::impl_compiler::IsParentResult;
use crate::typing::names::names::ArbitraryNameT;
use crate::typing::env::i_env_entry::IEnvEntryT;
use crate::postparsing::names::ArbitraryNameS;
use crate::postparsing::rune_type_solver::RuneTypeSolver;
use crate::typing::env::function_environment_t::NodeEnvironmentBox;
use crate::typing::typing_interner::TypingInterner;
use crate::scout_arena::ScoutArena;
use crate::postparsing::rune_type_solver::IRuneTypeSolverEnv;
use crate::postparsing::names::IImpreciseNameS;
use crate::postparsing::rune_type_solver::IRuneTypeSolverLookupResult;
use crate::postparsing::rune_type_solver::IRuneTypingLookupFailedError;
use crate::postparsing::rune_type_solver::CitizenRuneTypeSolverLookupResult;
use crate::postparsing::rune_type_solver::TemplataLookupResult;
use crate::postparsing::rune_type_solver::RuneTypingCouldntFindType;
use crate::higher_typing::higher_typing_pass::explicify_lookups;
use crate::higher_typing::patterns::get_rune_types_from_pattern;
use crate::postparsing::names::IRuneValS;
use crate::postparsing::names::SelfRuneS;
use crate::postparsing::rules::rules::RuneParentEnvLookupSR;
use crate::postparsing::rules::rules::RuneUsage;
use crate::postparsing::rune_type_solver::solve_rune_type;
use crate::typing::names::names::RuneNameT;
use std::iter::once;
use std::marker::PhantomData;

impl<'s, 'ctx, 't> Compiler<'s, 'ctx, 't>
where 's: 't,
{
    pub fn evaluate_and_coerce_to_reference_expressions(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        nenv: &mut NodeEnvironmentBox<'s, 't>,
        life: LocationInFunctionEnvironmentT<'t>,
        parent_ranges: &'t [RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        region: RegionT,
        exprs_1: &[&'s IExpressionSE<'s>],
    ) -> Result<(Vec<ReferenceExpressionTE<'s, 't>>, HashSet<CoordT<'s, 't>>), ICompileErrorT<'s, 't>> {
        let mut result_exprs = Vec::new();
        let mut all_returns = HashSet::new();
        for (index, expr) in exprs_1.iter().enumerate() {
            let (ref_expr, returns) = self.evaluate_and_coerce_to_reference_expression(
                coutputs, nenv, life.add(self.typing_interner, index as i32), parent_ranges, call_location, region, expr)?;
            result_exprs.push(ref_expr);
            all_returns.extend(returns);
        }
        Ok((result_exprs, all_returns))
    }

    pub fn evaluate_lookup_for_load(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        nenv: &mut NodeEnvironmentBox<'s, 't>,
        range: &[RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        region: RegionT,
        name: IVarNameT<'s, 't>,
        target_ownership: LoadAsP,
    ) -> Result<Option<ExpressionTE<'s, 't>>, ICompileErrorT<'s, 't>> {
        match self.evaluate_addressible_lookup(coutputs, nenv, range, region, name)? {
            Some(x) => {
                let thing = self.soft_load(nenv, range, x, target_ownership, region);
                Ok(Some(ExpressionTE::Reference(thing)))
            }
            None => {
                let name_as_name_t: INameT<'s, 't> = name.into();
                let lookup_filter: HashSet<ILookupContext> =
                    [ILookupContext::TemplataLookupContext].into_iter().collect();
                match nenv.lookup_nearest_with_name(name_as_name_t, &lookup_filter) {
                    Some(ITemplataT::Integer(num)) => {
                        Ok(Some(ExpressionTE::Reference(ReferenceExpressionTE::ConstantInt(self.typing_interner.alloc(ConstantIntTE {
                            value: ITemplataT::Integer(num),
                            bits: 32,
                            region,
                        })))))
                    }
                    Some(ITemplataT::Boolean(b)) => {
                        Ok(Some(ExpressionTE::Reference(ReferenceExpressionTE::ConstantBool(self.typing_interner.alloc(ConstantBoolTE {
                            value: b,
                            region,
                        })))))
                    }
                    None => Ok(None),
                    _ => unreachable!("Scala's evaluateLookupForLoad None-branch match is exhaustive over IntegerTemplataT/BooleanTemplataT/None with no catch-all"),
                }
            }
        }
    }

    pub fn evaluate_addressible_lookup_for_mutate(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        nenv: &mut NodeEnvironmentBox<'s, 't>,
        parent_ranges: &'t [RangeS<'s>],
        region: RegionT,
        load_range: RangeS<'s>,
        name_a: IVarNameS<'s>,
    ) -> Option<AddressExpressionTE<'s, 't>> {
        let name_2 = self.translate_var_name_step(name_a);
        match nenv.get_variable(name_2, self.typing_interner) {
            Some(IVariableT::AddressibleLocal(alv)) => {
                Some(AddressExpressionTE::LocalLookup(self.typing_interner.alloc(LocalLookupTE {
                    range: load_range,
                    local_variable: ILocalVariableT::Addressible(alv),
                })))
            }
            Some(IVariableT::ReferenceLocal(rlv)) => {
                Some(AddressExpressionTE::LocalLookup(self.typing_interner.alloc(LocalLookupTE {
                    range: load_range,
                    local_variable: ILocalVariableT::Reference(rlv),
                })))
            }
            Some(IVariableT::AddressibleClosure(acv)) => {
                let closured_vars_struct_ref = *acv.closured_vars_struct_type;
                let closured_vars_struct_template_id = self.get_struct_template(closured_vars_struct_ref.id);
                let closured_vars_struct_template_name = match closured_vars_struct_template_id.local_name {
                    INameT::LambdaCitizenTemplate(n) => n,
                    _ => panic!("evaluate_addressible_lookup_for_mutate AddressibleClosure: expected LambdaCitizenTemplateNameT"),
                };
                let mutability = self.get_mutability(coutputs, KindT::Struct(self.typing_interner.alloc(closured_vars_struct_ref)));
                let ownership = match mutability {
                    ITemplataT::Mutability(MutabilityTemplataT { mutability: MutabilityT::Mutable }) => OwnershipT::Borrow,
                    ITemplataT::Mutability(MutabilityTemplataT { mutability: MutabilityT::Immutable }) => OwnershipT::Share,
                    ITemplataT::Placeholder(_) => panic!("implement: evaluate_addressible_lookup_for_mutate AddressibleClosure — PlaceholderTemplataT mutability"),
                    _ => unreachable!("Scala's AddressibleClosure mutability match is exhaustive over Mutable/Immutable/Placeholder with no catch-all"),
                };
                let closured_vars_struct_ref_coord = CoordT { ownership, region: RegionT { region: IRegionT::Default }, kind: KindT::Struct(self.typing_interner.alloc(closured_vars_struct_ref)) };
                let closure_param_var_name_2 = IVarNameT::ClosureParam(self.typing_interner.intern_closure_param_name(ClosureParamNameT { code_location: closured_vars_struct_template_name.code_location}));
                let borrow_expr = self.borrow_soft_load(coutputs, AddressExpressionTE::LocalLookup(self.typing_interner.alloc(LocalLookupTE {
                    range: load_range,
                    local_variable: ILocalVariableT::Reference(ReferenceLocalVariableT { name: closure_param_var_name_2, variability: VariabilityT::Final, coord: closured_vars_struct_ref_coord }),
                })));
                let closured_vars_struct_def = coutputs.lookup_struct(closured_vars_struct_ref.id, self);
                assert!(closured_vars_struct_def.members.iter().any(|m| m.name() == &acv.name));
                Some(AddressExpressionTE::AddressMemberLookup(self.typing_interner.alloc(AddressMemberLookupTE {
                    range: load_range,
                    struct_expr: borrow_expr,
                    member_name: acv.name,
                    result_type2: acv.coord,
                    variability: acv.variability,
                })))
            }
            Some(IVariableT::ReferenceClosure(_)) => {
                panic!("implement: evaluate_addressible_lookup_for_mutate — ReferenceClosureVariableT");
            }
            None => None,
        }
    }

    pub fn evaluate_addressible_lookup(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        nenv: &mut NodeEnvironmentBox<'s, 't>,
        ranges: &[RangeS<'s>],
        region: RegionT,
        name_2: IVarNameT<'s, 't>,
    ) -> Result<Option<AddressExpressionTE<'s, 't>>, ICompileErrorT<'s, 't>> {
        match nenv.get_variable(name_2, self.typing_interner) {
            Some(IVariableT::AddressibleLocal(alv)) => {
                assert!(!nenv.unstackifieds().contains(&alv.name));
                Ok(Some(AddressExpressionTE::LocalLookup(self.typing_interner.alloc(LocalLookupTE {
                    range: ranges[0],
                    local_variable: ILocalVariableT::Addressible(alv),
                }))))
            }
            Some(IVariableT::ReferenceLocal(rlv)) => {
                if nenv.unstackifieds().contains(&rlv.name) {
                    return Err(ICompileErrorT::CantUseUnstackifiedLocal {
                        range: self.typing_interner.alloc_slice_copy(ranges),
                        local_id: rlv.name,
                    });
                }
                Ok(Some(AddressExpressionTE::LocalLookup(self.typing_interner.alloc(LocalLookupTE {
                    range: ranges[0],
                    local_variable: ILocalVariableT::Reference(rlv),
                }))))
            }
            Some(IVariableT::AddressibleClosure(acv)) => {
                let closured_vars_struct_ref = *acv.closured_vars_struct_type;
                let closured_vars_struct_template_id = self.get_struct_template(closured_vars_struct_ref.id);
                let closured_vars_struct_template_name = match closured_vars_struct_template_id.local_name {
                    INameT::LambdaCitizenTemplate(n) => n,
                    _ => panic!("evaluate_addressible_lookup AddressibleClosure: expected LambdaCitizenTemplateNameT"),
                };
                let mutability = self.get_mutability(coutputs, KindT::Struct(self.typing_interner.alloc(closured_vars_struct_ref)));
                let ownership = match mutability {
                    ITemplataT::Mutability(MutabilityTemplataT { mutability: MutabilityT::Mutable }) => OwnershipT::Borrow,
                    ITemplataT::Mutability(MutabilityTemplataT { mutability: MutabilityT::Immutable }) => OwnershipT::Share,
                    ITemplataT::Placeholder(_) => panic!("implement: evaluate_addressible_lookup AddressibleClosure — PlaceholderTemplataT mutability"),
                    _ => unreachable!("Scala's AddressibleClosure mutability match is exhaustive over Mutable/Immutable/Placeholder with no catch-all"),
                };
                let closured_vars_struct_ref_coord = CoordT { ownership, region: RegionT { region: IRegionT::Default }, kind: KindT::Struct(self.typing_interner.alloc(closured_vars_struct_ref)) };
                let closure_param_var_name_2 = IVarNameT::ClosureParam(self.typing_interner.intern_closure_param_name(ClosureParamNameT { code_location: closured_vars_struct_template_name.code_location}));
                let borrow_expr = self.borrow_soft_load(coutputs, AddressExpressionTE::LocalLookup(self.typing_interner.alloc(LocalLookupTE {
                    range: ranges[0],
                    local_variable: ILocalVariableT::Reference(ReferenceLocalVariableT { name: closure_param_var_name_2, variability: VariabilityT::Final, coord: closured_vars_struct_ref_coord }),
                })));
                let closured_vars_struct_def = coutputs.lookup_struct(closured_vars_struct_ref.id, self);
                assert!(closured_vars_struct_def.members.iter().any(|m| m.name() == &acv.name));
                Ok(Some(AddressExpressionTE::AddressMemberLookup(self.typing_interner.alloc(AddressMemberLookupTE {
                    range: ranges[0],
                    struct_expr: borrow_expr,
                    member_name: acv.name,
                    result_type2: acv.coord,
                    variability: acv.variability,
                }))))
            }
            Some(IVariableT::ReferenceClosure(rcv)) => {
                let closured_vars_struct_ref = *rcv.closured_vars_struct_type;
                let closured_vars_struct_template_id = self.get_struct_template(closured_vars_struct_ref.id);
                let closured_vars_struct_template_name = match closured_vars_struct_template_id.local_name {
                    INameT::LambdaCitizenTemplate(n) => n,
                    _ => panic!("evaluate_addressible_lookup ReferenceClosure: expected LambdaCitizenTemplateNameT"),
                };
                let mutability = self.get_mutability(coutputs, KindT::Struct(self.typing_interner.alloc(closured_vars_struct_ref)));
                let ownership = match mutability {
                    ITemplataT::Mutability(MutabilityTemplataT { mutability: MutabilityT::Mutable }) => OwnershipT::Borrow,
                    ITemplataT::Mutability(MutabilityTemplataT { mutability: MutabilityT::Immutable }) => OwnershipT::Share,
                    ITemplataT::Placeholder(_) => panic!("implement: evaluate_addressible_lookup ReferenceClosure — PlaceholderTemplataT mutability"),
                    _ => unreachable!("Scala's ReferenceClosure mutability match is exhaustive over Mutable/Immutable/Placeholder with no catch-all"),
                };
                let closured_vars_struct_ref_coord = CoordT { ownership, region: RegionT { region: IRegionT::Default }, kind: KindT::Struct(self.typing_interner.alloc(closured_vars_struct_ref)) };
                let closured_vars_struct_def = coutputs.lookup_struct(closured_vars_struct_ref.id, self);
                assert!(closured_vars_struct_def.members.iter().any(|m| m.name() == &rcv.name));
                let borrow_expr = self.borrow_soft_load(coutputs, AddressExpressionTE::LocalLookup(self.typing_interner.alloc(LocalLookupTE {
                    range: ranges[0],
                    local_variable: ILocalVariableT::Reference(ReferenceLocalVariableT {
                        name: IVarNameT::ClosureParam(self.typing_interner.intern_closure_param_name(ClosureParamNameT { code_location: closured_vars_struct_template_name.code_location})),
                        variability: VariabilityT::Final,
                        coord: closured_vars_struct_ref_coord,
                    }),
                })));
                Ok(Some(AddressExpressionTE::ReferenceMemberLookup(self.typing_interner.alloc(ReferenceMemberLookupTE {
                    range: ranges[0],
                    struct_expr: borrow_expr,
                    member_name: rcv.name,
                    member_reference: rcv.coord,
                    variability: rcv.variability,
                }))))
            }
            None => Ok(None),
            #[allow(unreachable_patterns)] // mirrors Scala's `case _ => vwat()` catch-all
            _ => panic!("evaluate_addressible_lookup: unexpected variable type"),
        }
    }

    pub fn make_closure_struct_construct_expression(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        nenv: &mut NodeEnvironmentBox<'s, 't>,
        range: &[RangeS<'s>],
        region: RegionT,
        closure_struct_ref: StructTT<'s, 't>,
    ) -> ReferenceExpressionTE<'s, 't> {
        let closure_struct_def = coutputs.lookup_struct(closure_struct_ref.id, self);
        let substituter =
            self.get_placeholder_substituter(
                self.opts.global_options.sanity_check,
                nenv.function_environment().template_id,
                closure_struct_ref.id,
                IBoundArgumentsSource::InheritBoundsFromTypeItself,
            );
        // Note, this is where the unordered closuredNames set becomes ordered.
        let lookup_expressions2: Vec<ExpressionTE<'s, 't>> =
            closure_struct_def.members.iter().map(|member| {
                match member {
                    IStructMemberT::Variadic(_) => panic!("implement: make_closure_struct_construct_expression — VariadicStructMemberT (closures cant contain variadic members)"),
                    IStructMemberT::Normal(NormalStructMemberT { name: member_name, tyype, .. }) => {
                        let lookup = self.evaluate_addressible_lookup(coutputs, nenv, range, region, *member_name)
                            .unwrap_or_else(|_| panic!("evaluate_addressible_lookup error"))
                            .unwrap_or_else(|| panic!("Couldn't find {:?}", member_name));
                        match tyype {
                            IMemberTypeT::Reference(ReferenceMemberTypeT { reference: unsubstituted_coord }) => {
                                let coord = substituter.substitute_for_coord(coutputs, *unsubstituted_coord);
                                assert_eq!(coord.kind, lookup.result().coord.kind);
                                // Closures never contain owning references.
                                // If we're capturing an own, then on the inside of the closure
                                // it's a borrow or a weak. See "Captured own is borrow" test for more.
                                assert!(coord.ownership != OwnershipT::Own);
                                let borrow_loaded = self.borrow_soft_load(coutputs, lookup);
                                ExpressionTE::Reference(borrow_loaded)
                            }
                            IMemberTypeT::Address(AddressMemberTypeT { reference: unsubstituted_coord }) => {
                                let coord = substituter.substitute_for_coord(coutputs, *unsubstituted_coord);
                                assert_eq!(coord, lookup.result().coord);
                                ExpressionTE::Address(lookup)
                            }
                        }
                    }
                }
            }).collect();
        let ownership =
            match closure_struct_def.mutability {
                ITemplataT::Mutability(MutabilityTemplataT { mutability: MutabilityT::Mutable }) => OwnershipT::Own,
                ITemplataT::Mutability(MutabilityTemplataT { mutability: MutabilityT::Immutable }) => OwnershipT::Share,
                ITemplataT::Placeholder(_) => { panic!("Unimplemented: make_closure_struct_construct_expression PlaceholderTemplataT"); }
                _ => unreachable!("Scala's closure-struct mutability match has no catch-all"),
            };
        let struct_ref = self.typing_interner.alloc(closure_struct_ref);
        let result_pointer_type = CoordT { ownership, region, kind: KindT::Struct(struct_ref) };

        let construct_expr2 = ConstructTE {
            struct_tt: struct_ref,
            result_reference: result_pointer_type,
            args: self.typing_interner.alloc_slice_from_vec(lookup_expressions2),
        };
        ReferenceExpressionTE::Construct(self.typing_interner.alloc(construct_expr2))
    }

    pub fn evaluate_and_coerce_to_reference_expression(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        nenv: &mut NodeEnvironmentBox<'s, 't>,
        life: LocationInFunctionEnvironmentT<'t>,
        parent_ranges: &'t [RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        region: RegionT,
        expr_1: &'s IExpressionSE<'s>,
    ) -> Result<(ReferenceExpressionTE<'s, 't>, HashSet<CoordT<'s, 't>>), ICompileErrorT<'s, 't>> {
        let (expr2, returns_from_expr) =
            self.evaluate_expression(coutputs, nenv, life, parent_ranges, call_location, region, expr_1)?;
        match expr2 {
            ExpressionTE::Reference(r) => Ok((r, returns_from_expr)),
            ExpressionTE::Address(a) => {
                let expr = self.coerce_to_reference_expression(nenv, parent_ranges, ExpressionTE::Address(a), region);
                Ok((expr, returns_from_expr))
            }
        }
    }

    pub fn coerce_to_reference_expression(
        &self,
        nenv: &mut NodeEnvironmentBox<'s, 't>,
        parent_ranges: &'t [RangeS<'s>],
        expr_2: ExpressionTE<'s, 't>,
        region: RegionT,
    ) -> ReferenceExpressionTE<'s, 't> {
        match expr_2 {
            ExpressionTE::Reference(r) => r,
            ExpressionTE::Address(a) => {
                let range_with_parent: Vec<RangeS<'s>> =
                    once(a.range()).chain(parent_ranges.iter().copied()).collect();
                let soft_loaded = self.soft_load(nenv, &range_with_parent, a, LoadAsP::Use, region);
                soft_loaded
            }
        }
    }

    pub fn evaluate_expected_address_expression(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        nenv: &mut NodeEnvironmentBox<'s, 't>,
        life: LocationInFunctionEnvironmentT<'t>,
        parent_ranges: &'t [RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        region: RegionT,
        expr_1: &'s IExpressionSE<'s>,
    ) -> Result<(AddressExpressionTE<'s, 't>, HashSet<CoordT<'s, 't>>), ICompileErrorT<'s, 't>> {
        let (expr_2, returns) =
            self.evaluate_expression(coutputs, nenv, life, parent_ranges, call_location, region, expr_1)?;
        let range_with_parent: &'t [RangeS<'s>] = self.typing_interner.alloc_slice_copy(
            &once(expr_1.range()).chain(parent_ranges.iter().copied()).collect::<Vec<_>>());
        match expr_2 {
            ExpressionTE::Address(a) => Ok((a, returns)),
            ExpressionTE::Reference(_) => {
                Err(ICompileErrorT::RangedInternalErrorT {
                    range: range_with_parent,
                    message: "Expected reference expression!",
                })
            }
        }
    }

    pub fn evaluate_expression(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        nenv: &mut NodeEnvironmentBox<'s, 't>,
        life: LocationInFunctionEnvironmentT<'t>,
        parent_ranges: &'t [RangeS<'s>],
        outer_call_location: LocationInDenizen<'s>,
        region: RegionT,
        expr_1: &'s IExpressionSE<'s>,
    ) -> Result<(ExpressionTE<'s, 't>, HashSet<CoordT<'s, 't>>), ICompileErrorT<'s, 't>> {
        match expr_1 {
            IExpressionSE::Void(_) => {
                Ok((ExpressionTE::Reference(
                    ReferenceExpressionTE::VoidLiteral(self.typing_interner.alloc(VoidLiteralTE {
                        region,
                    }))), HashSet::new()))
            }
            IExpressionSE::ConstantInt(c) => {
                Ok((ExpressionTE::Reference(
                    ReferenceExpressionTE::ConstantInt(self.typing_interner.alloc(ConstantIntTE {
                        value: ITemplataT::Integer(c.value),
                        bits: c.bits,
                        region,
                    }))), HashSet::new()))
            }
            IExpressionSE::Return(ret) => {
                let (uncasted_inner_expr_2, returns_from_inner_expr) =
                    self.evaluate_and_coerce_to_reference_expression(
                        coutputs, nenv, life.add(self.typing_interner, 0), parent_ranges,
                        outer_call_location, region, ret.inner)?;

                let inner_expr_2 = match nenv.maybe_return_type() {
                    None => uncasted_inner_expr_2,
                    Some(return_type) => {
                        let snapshot = nenv.snapshot(self.typing_interner);
                        let snapshot_env = IInDenizenEnvironmentT::Node(snapshot);
                        let range_list: Vec<RangeS<'s>> =
                            once(ret.range).chain(parent_ranges.iter().copied()).collect();
                        match self.is_type_convertible(
                            coutputs, snapshot_env, &range_list, outer_call_location,
                            uncasted_inner_expr_2.result().coord, return_type) {
                            false => {
                                panic!("implement: evaluate_expression ReturnSE — CouldntConvertForReturnT");
                            }
                            true => {
                                self.convert(
                                    snapshot_env, coutputs, &range_list, outer_call_location,
                                    uncasted_inner_expr_2, return_type)
                            }
                        }
                    }
                };

                let all_locals = nenv.get_all_locals();
                let unstackified_locals = nenv.get_all_unstackified_locals();
                let variables_to_destruct: Vec<&ILocalVariableT<'s, 't>> = all_locals.iter()
                    .filter(|x| !unstackified_locals.contains(&x.name()))
                    .collect();
                let reversed_variables_to_destruct: Vec<&ILocalVariableT<'s, 't>> =
                    variables_to_destruct.into_iter().rev().collect();

                let mut returns = returns_from_inner_expr;
                returns.insert(inner_expr_2.result().coord);

                let result_var_name = self.typing_interner.intern_typing_pass_function_result_var_name(
                    TypingPassFunctionResultVarNameT { });
                let result_var_id = IVarNameT::TypingPassFunctionResultVar(result_var_name);
                let result_variable = ReferenceLocalVariableT {
                    name: result_var_id,
                    variability: VariabilityT::Final,
                    coord: inner_expr_2.result().coord,
                };
                let result_let =
                    ReferenceExpressionTE::LetNormal(self.typing_interner.alloc(LetNormalTE {
                        variable: ILocalVariableT::Reference(result_variable),
                        expr: inner_expr_2,
                    }));
                nenv.add_variable(IVariableT::ReferenceLocal(result_variable));

                let range_list: Vec<RangeS<'s>> =
                    once(ret.range).chain(parent_ranges.iter().copied()).collect();
                let destruct_exprs_refs =
                    self.unlet_and_drop_all(
                        coutputs, nenv, &range_list, outer_call_location, region,
                        &reversed_variables_to_destruct)?;

                let get_result_expr = self.unlet_local_without_dropping(
                    nenv, &ILocalVariableT::Reference(result_variable));
                let get_result_expr_ref =
                    ReferenceExpressionTE::Unlet(self.typing_interner.alloc(get_result_expr));

                let mut all_exprs: Vec<ReferenceExpressionTE<'s, 't>> = Vec::new();
                all_exprs.push(result_let);
                all_exprs.extend(destruct_exprs_refs);
                all_exprs.push(get_result_expr_ref);

                let consecutor = self.consecutive(&all_exprs);

                let return_te =
                    ReferenceExpressionTE::Return(self.typing_interner.alloc(ReturnTE {
                        source_expr: consecutor,
                    }));

                Ok((ExpressionTE::Reference(return_te), returns))
            }
            IExpressionSE::Let(let_se) => {
                let (source_expr_2, returns_from_source) =
                    self.evaluate_and_coerce_to_reference_expression(
                        coutputs, nenv, life.add(self.typing_interner, 0), parent_ranges, outer_call_location, nenv.default_region(), let_se.expr)?;

                let rune_type_solve_env = LetExprRuneTypeSolverEnv { nenv, typing_interner: self.typing_interner, scout_arena: self.scout_arena };
                let rune_to_initially_known_type: IndexMap<_, _> =
                    get_rune_types_from_pattern(&let_se.pattern)
                        .into_iter().collect();
                let range_list: Vec<RangeS<'s>> =
                    once(let_se.range).chain(parent_ranges.iter().copied()).collect();
                let rune_to_type =
                    solve_rune_type(
                        self.scout_arena,
                        self.opts.global_options.sanity_check,
                        &rune_type_solve_env,
                        range_list,
                        false,
                        let_se.rules,
                        &[],
                        true,
                        rune_to_initially_known_type,
                    ).unwrap_or_else(|_e| {
                        panic!("implement: LetSE — HigherTypingInferError");
                    });

                let result_te = self.infer_and_translate_pattern(
                    coutputs,
                    nenv,
                    life.add(self.typing_interner, 1),
                    parent_ranges,
                    outer_call_location,
                    let_se.rules,
                    &rune_to_type,
                    &let_se.pattern,
                    source_expr_2,
                    region,
                    |compiler, _coutputs, nenv, _life, _live_capture_locals| {
                        ReferenceExpressionTE::VoidLiteral(self.typing_interner.alloc(VoidLiteralTE {
                            region: nenv.default_region(),
                        }))
                    },
                );

                Ok((ExpressionTE::Reference(result_te), returns_from_source))
            }
            IExpressionSE::Consecutor(consecutor_se) => {
                assert!(region == nenv.default_region());
                let region_for_inners = region;

                let mut init_exprs_te: Vec<ReferenceExpressionTE<'s, 't>> = Vec::new();
                let mut init_returns: HashSet<CoordT<'s, 't>> = HashSet::new();
                for (index, expr_se) in consecutor_se.exprs.iter().enumerate().take(consecutor_se.exprs.len() - 1) {
                    let (undropped_expr_te, returns) =
                        self.evaluate_and_coerce_to_reference_expression(
                            coutputs, nenv, life.add(self.typing_interner, index as i32), parent_ranges, outer_call_location, region_for_inners, expr_se)?;
                    let expr_te = match undropped_expr_te.result().coord.kind {
                        KindT::Void(_) => undropped_expr_te,
                        _ => {
                            let snap = IInDenizenEnvironmentT::Node(nenv.snapshot(self.typing_interner));
                            let range_with_parent: Vec<RangeS<'s>> =
                                once((*expr_se).range()).chain(parent_ranges.iter().copied()).collect();
                            self.drop(snap, coutputs, &range_with_parent, outer_call_location, region, undropped_expr_te)?
                        }
                    };
                    init_exprs_te.push(expr_te);
                    init_returns.extend(returns);
                }

                let (last_expr_te, last_returns) =
                    self.evaluate_and_coerce_to_reference_expression(
                        coutputs, nenv,
                        life.add(self.typing_interner, (consecutor_se.exprs.len() - 1) as i32),
                        parent_ranges,
                        outer_call_location,
                        region_for_inners,
                        consecutor_se.exprs.last().unwrap())?;

                init_exprs_te.push(last_expr_te);
                init_returns.extend(last_returns);

                let result = self.consecutive(&init_exprs_te);
                Ok((ExpressionTE::Reference(result), init_returns))
            }
            IExpressionSE::LocalLoad(local_load) => {
                let name = self.translate_var_name_step(local_load.name);
                let range_list = vec![local_load.range];
                let lookup_expr_1 =
                    self.evaluate_lookup_for_load(coutputs, nenv, &range_list, outer_call_location, region, name, local_load.target_ownership)?;
                match lookup_expr_1 {
                    None => unreachable!("Scala throws CouldntFindIdentifierToLoadT here, but the scout pass intercepts unknown names with CouldntFindVarToMutateS before typing runs; Scala's own test was deleted for the same reason (see compiler_tests.rs:3763)"),
                    Some(x) => Ok((x, HashSet::new())),
                }
            }
            IExpressionSE::FunctionCall(fc) => {
                match fc.callable_expr {
                    IExpressionSE::OverloadSet(overload_set) => {
                        let (args_exprs_2, returns_from_args) =
                            self.evaluate_and_coerce_to_reference_expressions(
                                coutputs, nenv, life.add(self.typing_interner, 0), parent_ranges, fc.location,
                                // See SRIE
                                nenv.default_region(),
                                fc.arg_exprs)?;
                        let mut range_list = vec![fc.range];
                        range_list.extend_from_slice(parent_ranges);
                        let initial_container_receiving: Vec<(RuneUsage<'s>, RuneUsage<'s>)> = Vec::new();
                        let initial_look_in_env: IInDenizenEnvironmentT<'s, 't> = IInDenizenEnvironmentT::Node(nenv.snapshot(self.typing_interner));
                        let parts = overload_set.lookup.parts;
                        let (final_look_in_env, container_receiving_rune_to_explicit_template_arg_rune) =
                            parts[..parts.len() - 1].iter().try_fold(
                                (initial_look_in_env, initial_container_receiving),
                                |(previous_look_in_env, previous_container_receiving), part| -> Result<(IInDenizenEnvironmentT<'s, 't>, Vec<(RuneUsage<'s>, RuneUsage<'s>)>), ICompileErrorT<'s, 't>> {
                                    let struct_templata = match previous_look_in_env.lookup_nearest_with_imprecise_name(
                                        part.name,
                                        once(ILookupContext::TemplataLookupContext).collect(),
                                        self.typing_interner,
                                    ) {
                                        Some(ITemplataT::StructDefinition(s)) => s,
                                        _ => return Err(ICompileErrorT::CouldntFindTypeT {
                                            range: self.typing_interner.alloc_slice_copy(&range_list),
                                            name: part.name,
                                        }),
                                    };
                                    let struct_template_id = self.resolve_struct_template(struct_templata);
                                    let look_in_env = coutputs.get_outer_env_for_type(&range_list, *struct_template_id);
                                    let part_rune_to_template_arg: Vec<(RuneUsage<'s>, RuneUsage<'s>)> =
                                        struct_templata.origin_struct.generic_parameters.iter()
                                            .zip(part.explicit_template_args.iter())
                                            .map(|(gp, arg_rune)| (gp.rune, *arg_rune))
                                            .collect();
                                    let mut next_container_receiving = previous_container_receiving;
                                    next_container_receiving.extend(part_rune_to_template_arg);
                                    Ok((look_in_env, next_container_receiving))
                                },
                            )?;
                        let env_ref = final_look_in_env;
                        let last_part = overload_set.lookup.parts.last().expect("OverloadSet parts must be non-empty");
                        let callable_expr = self.new_global_function_group_expression(
                            env_ref,
                            coutputs,
                            nenv.default_region(),
                            last_part.name);
                        let template_arg_runes: Vec<IRuneS<'s>> = last_part.explicit_template_args.iter().map(|a| a.rune).collect();
                        let call_expr_2 =
                            self.evaluate_prefix_call(
                                coutputs,
                                nenv,
                                life.add(self.typing_interner, 1),
                                &range_list,
                                fc.location,
                                region,
                                callable_expr,
                                overload_set.lookup.rules,
                                &template_arg_runes,
                                &container_receiving_rune_to_explicit_template_arg_rune,
                                &args_exprs_2)?;
                        Ok((ExpressionTE::Reference(call_expr_2), returns_from_args))
                    }
                    _ => {
                        let (undecayed_callable_expr_2, returns_from_callable) =
                            self.evaluate_and_coerce_to_reference_expression(
                                coutputs, nenv, life.add(self.typing_interner, 0), parent_ranges, fc.location, region, fc.callable_expr)?;
                        let decayed_callable_expr_2_ref =
                            self.maybe_borrow_soft_load(coutputs, &ExpressionTE::Reference(undecayed_callable_expr_2));
                        let decayed_callable_reference_expr_2 =
                            self.coerce_to_reference_expression(nenv, parent_ranges, ExpressionTE::Reference(decayed_callable_expr_2_ref), region);
                        let (args_exprs_2, returns_from_args) =
                            self.evaluate_and_coerce_to_reference_expressions(
                                coutputs, nenv, life.add(self.typing_interner, 1), parent_ranges, fc.location,
                                nenv.default_region(),
                                fc.arg_exprs)?;
                        let function_pointer_call_2 =
                            self.evaluate_prefix_call(
                                coutputs,
                                nenv,
                                life.add(self.typing_interner, 2),
                                &{
                                    let mut range_list = vec![fc.range];
                                    range_list.extend_from_slice(parent_ranges);
                                    range_list
                                },
                                fc.location,
                                region,
                                decayed_callable_reference_expr_2,
                                &[],
                                &[],
                                &[],
                                &args_exprs_2)?;
                        let mut all_returns = returns_from_callable;
                        all_returns.extend(returns_from_args);
                        Ok((ExpressionTE::Reference(function_pointer_call_2), all_returns))
                    }
                }
            }
            IExpressionSE::Function(function_se) => {
                let function_s = function_se.function;
                let range_list: &'t [RangeS<'s>] = self.typing_interner.alloc_slice_copy(
                    &once(function_s.range).chain(parent_ranges.iter().copied()).collect::<Vec<_>>());
                let call_expr_2 = self.evaluate_closure(
                    coutputs, nenv, range_list, outer_call_location, region, *function_s.name, function_s)?;
                Ok((ExpressionTE::Reference(call_expr_2), HashSet::new()))
            }
            IExpressionSE::Ownershipped(ownershipped) => {
                let (source_te, returns_from_inner) =
                    self.evaluate_and_coerce_to_reference_expression(
                        coutputs, nenv, life.add(self.typing_interner, 0), parent_ranges, outer_call_location, region, ownershipped.inner_expr)?;
                let result_expr_2 =
                    match source_te.result().coord.ownership {
                        OwnershipT::Own => {
                            match ownershipped.target_ownership {
                                LoadAsP::Move => {
                                    // this can happen if we put a ^ on an owning reference. No harm, let it go.
                                    source_te
                                }
                                LoadAsP::LoadAsBorrow => {
                                    let range_with_parent: Vec<RangeS<'s>> =
                                        once(ownershipped.range).chain(parent_ranges.iter().copied()).collect();
                                    let defer_te = self.make_temporary_local_defer(
                                        coutputs, nenv, &range_with_parent, outer_call_location,
                                        life.add(self.typing_interner, 1), region,
                                        source_te, OwnershipT::Borrow);
                                    ReferenceExpressionTE::Defer(self.typing_interner.alloc(defer_te))
                                }
                                LoadAsP::LoadAsWeak => {
                                    let range_with_parent: Vec<RangeS<'s>> =
                                        once(ownershipped.range).chain(parent_ranges.iter().copied()).collect();
                                    let defer_te = self.make_temporary_local_defer(
                                        coutputs, nenv, &range_with_parent, outer_call_location,
                                        life.add(self.typing_interner, 3), region,
                                        source_te, OwnershipT::Borrow);
                                    let expr = ReferenceExpressionTE::Defer(self.typing_interner.alloc(defer_te));
                                    self.weak_alias(coutputs, self.typing_interner.alloc_slice_copy(&range_with_parent), expr)?
                                }
                                LoadAsP::Use => {
                                    panic!("implement: Ownershipped OwnT UseP (vcurious)");
                                }
                            }
                        }
                        OwnershipT::Borrow => {
                            match ownershipped.target_ownership {
                                LoadAsP::Move => panic!("implement: Ownershipped BorrowT MoveP (vcurious)"),
                                LoadAsP::LoadAsBorrow => source_te,
                                LoadAsP::LoadAsWeak => {
                                    let range_with_parent: Vec<RangeS<'s>> =
                                        once(ownershipped.range).chain(parent_ranges.iter().copied()).collect();
                                    self.weak_alias(coutputs, self.typing_interner.alloc_slice_copy(&range_with_parent), source_te)?
                                }
                                LoadAsP::Use => source_te,
                            }
                        }
                        OwnershipT::Weak => {
                            panic!("implement: Ownershipped WeakT");
                        }
                        OwnershipT::Share => {
                            match ownershipped.target_ownership {
                                LoadAsP::Move => {
                                    // Allow this, we can do ^ on a share ref, itll just give us a share ref.
                                    source_te
                                }
                                LoadAsP::LoadAsBorrow => {
                                    // Allow this, we can do & on a share ref, itll just give us a share ref.
                                    source_te
                                }
                                LoadAsP::LoadAsWeak => {
                                    panic!("implement: Ownershipped ShareT LoadAsWeakP");
                                }
                                LoadAsP::Use => source_te,
                            }
                        }
                    };
                Ok((ExpressionTE::Reference(result_expr_2), returns_from_inner))
            }
            IExpressionSE::Dot(dot) => {
                let member_name: IVarNameT<'s, 't> =
                    IVarNameT::CodeVar(self.typing_interner.intern_code_var_name(
                        CodeVarNameT { name: dot.member}));
                let (unborrowed_container_expr_2, returns_from_container_expr) =
                    self.evaluate_expression(coutputs, nenv, life.add(self.typing_interner, 0), parent_ranges, outer_call_location, region, dot.left)?;
                let container_expr_2 = {
                    let range_with_parent: Vec<RangeS<'s>> =
                        once(dot.range).chain(parent_ranges.iter().copied()).collect();
                    self.dot_borrow(coutputs, nenv, &range_with_parent, outer_call_location, life.add(self.typing_interner, 1), region, unborrowed_container_expr_2)
                };
                let expr_2 = match container_expr_2.result().coord.kind {
                    KindT::Struct(struct_tt) => {
                        let struct_def = coutputs.lookup_struct(struct_tt.id, self);
                        let (struct_member, _member_index) =
                            struct_def.get_member_and_index(&member_name)
                                .unwrap_or_else(|| panic!("CouldntFindMemberT"));
                        let unsubstituted_member_type = struct_member.tyype.expect_reference_member().reference;
                        let instantiation_bounds =
                            coutputs.get_instantiation_bounds(self.typing_interner, struct_tt.id)
                                .unwrap_or_else(|| panic!("vassertSome: getInstantiationBounds"));
                        let member_type =
                            self.get_placeholder_substituter(
                                self.opts.global_options.sanity_check,
                                nenv.function_environment().template_id,
                                struct_tt.id,
                                IBoundArgumentsSource::UseBoundsFromContainer {
                                    instantiation_bound_params: struct_def.instantiation_bound_params,
                                    instantiation_bound_arguments: instantiation_bounds,
                                })
                            .substitute_for_coord(coutputs, unsubstituted_member_type);
                        assert!(struct_def.members.iter().any(|m| m.name() == &member_name));
                        AddressExpressionTE::ReferenceMemberLookup(self.typing_interner.alloc(ReferenceMemberLookupTE {
                            range: dot.range,
                            struct_expr: container_expr_2,
                            member_name,
                            member_reference: member_type,
                            variability: struct_member.variability,
                        }))
                    }
                    KindT::StaticSizedArray(ssa) => {
                        if dot.member.0.chars().all(|c| c.is_ascii_digit()) {
                            let index = dot.member.0.parse::<i64>().expect("vassert: member is digit string");
                            let index_expr_2 = ReferenceExpressionTE::ConstantInt(self.typing_interner.alloc(ConstantIntTE {
                                value: ITemplataT::Integer(index),
                                bits: 32,
                                region,
                            }));
                            AddressExpressionTE::StaticSizedArrayLookup(
                                self.typing_interner.alloc(self.lookup_in_static_sized_array(dot.range, container_expr_2, index_expr_2, *ssa))
                            )
                        } else {
                            let range_with_parent: Vec<RangeS<'s>> =
                                once(dot.range).chain(parent_ranges.iter().copied()).collect();
                            return Err(ICompileErrorT::RangedInternalErrorT {
                                range: self.typing_interner.alloc_slice_from_vec(range_with_parent),
                                message: self.scout_arena.intern_str(&format!(
                                    "Sequence has no member named {}", dot.member.0)).0,
                            });
                        }
                    }
                    KindT::RuntimeSizedArray(rsa) => {
                        if dot.member.0.chars().all(|c| c.is_ascii_digit()) {
                            let index = dot.member.0.parse::<i64>().expect("vassert: member is digit string");
                            let index_expr_2 = ReferenceExpressionTE::ConstantInt(self.typing_interner.alloc(ConstantIntTE {
                                value: ITemplataT::Integer(index),
                                bits: 32,
                                region,
                            }));
                            let range_with_parent: Vec<RangeS<'s>> =
                                once(dot.range).chain(parent_ranges.iter().copied()).collect();
                            AddressExpressionTE::RuntimeSizedArrayLookup(
                                self.typing_interner.alloc(self.lookup_in_unknown_sized_array(
                                    &range_with_parent, dot.range, container_expr_2, index_expr_2, rsa)?)
                            )
                        } else {
                            let range_with_parent: Vec<RangeS<'s>> =
                                once(dot.range).chain(parent_ranges.iter().copied()).collect();
                            return Err(ICompileErrorT::RangedInternalErrorT {
                                range: self.typing_interner.alloc_slice_from_vec(range_with_parent),
                                message: self.scout_arena.intern_str(&format!(
                                    "Array has no member named {}", dot.member.0)).0,
                            });
                        }
                    }
                    other => {
                        let range_with_parent: Vec<RangeS<'s>> =
                            once(dot.range).chain(parent_ranges.iter().copied()).collect();
                        return Err(ICompileErrorT::RangedInternalErrorT {
                            range: self.typing_interner.alloc_slice_from_vec(range_with_parent),
                            message: self.scout_arena.intern_str(&format!(
                                "Can't apply .{} to {:?}", dot.member.0, other)).0,
                        });
                    }
                };
                match expr_2.result().coord.kind {
                    KindT::Struct(s) => {
                        assert!(coutputs.get_instantiation_bounds(self.typing_interner, s.id).is_some());
                    }
                    KindT::Interface(i) => {
                        assert!(coutputs.get_instantiation_bounds(self.typing_interner, i.id).is_some());
                    }
                    _ => {}
                }
                Ok((ExpressionTE::Address(expr_2), returns_from_container_expr))
            }
            IExpressionSE::If(if_se) => {
                // We make a block for the if-statement which contains its condition (the "if block"),
                // and then two child blocks under that for the then and else blocks.
                // The then and else blocks are children of the block which contains the condition
                // so they can access any locals declared by the condition.

                let (condition_expr, returns_from_condition) =
                    self.evaluate_and_coerce_to_reference_expression(
                        coutputs, nenv, life.add(self.typing_interner, 1), parent_ranges, outer_call_location, nenv.default_region(), if_se.condition)?;
                match condition_expr.result().coord {
                    CoordT { ownership: OwnershipT::Share, kind: KindT::Bool(_), .. } => {}
                    actual_type => {
                        let range_with_parent: Vec<RangeS<'s>> =
                            once(if_se.condition.range()).chain(parent_ranges.iter().copied()).collect();
                        return Err(ICompileErrorT::IfConditionIsntBoolean {
                            range: self.typing_interner.alloc_slice_from_vec(range_with_parent),
                            actual_type,
                        });
                    }
                }

                let then_body_se_as_expr: &'s IExpressionSE<'s> =
                    self.scout_arena.alloc(IExpressionSE::Block(if_se.then_body));
                let mut then_fate = NodeEnvironmentBox::new(nenv.make_child(self.typing_interner, then_body_se_as_expr, None));
                let then_fate_starting = then_fate.snapshot(self.typing_interner);
                let (then_expressions_with_result, then_returns_from_exprs) =
                    self.evaluate_block_statements(
                        coutputs,
                        then_fate_starting,
                        &mut then_fate,
                        life.add(self.typing_interner, 2),
                        parent_ranges,
                        outer_call_location,
                        nenv.default_region(),
                        if_se.then_body)?;
                let uncoerced_then_block_2 = BlockTE { inner: then_expressions_with_result };
                let (then_unstackified_ancestor_locals, then_restackified_ancestor_locals) =
                    then_fate.snapshot(self.typing_interner).get_effects_since(nenv.snapshot(self.typing_interner));
                let then_continues = match uncoerced_then_block_2.result().coord.kind {
                    KindT::Never(_) => false,
                    _ => true,
                };

                let else_body_se_as_expr: &'s IExpressionSE<'s> =
                    self.scout_arena.alloc(IExpressionSE::Block(if_se.else_body));
                let mut else_fate = NodeEnvironmentBox::new(nenv.make_child(self.typing_interner, else_body_se_as_expr, None));
                let else_fate_starting = else_fate.snapshot(self.typing_interner);
                let (else_expressions_with_result, else_returns_from_exprs) =
                    self.evaluate_block_statements(
                        coutputs,
                        else_fate_starting,
                        &mut else_fate,
                        life.add(self.typing_interner, 3),
                        parent_ranges,
                        outer_call_location,
                        nenv.default_region(),
                        if_se.else_body)?;
                let uncoerced_else_block_2 = BlockTE { inner: else_expressions_with_result };
                let (else_unstackified_ancestor_locals, else_restackified_ancestor_locals) =
                    else_fate.snapshot(self.typing_interner).get_effects_since(nenv.snapshot(self.typing_interner));
                let else_continues = match uncoerced_else_block_2.result().coord.kind {
                    KindT::Never(_) => false,
                    _ => true,
                };

                if then_continues && else_continues && uncoerced_then_block_2.result().coord.ownership != uncoerced_else_block_2.result().coord.ownership {
                    panic!("implement: evaluate_expression If — CantReconcileBranchesResults ownership mismatch");
                }

                let common_type = match (uncoerced_then_block_2.result().coord.kind, uncoerced_else_block_2.result().coord.kind) {
                    // If one side has a return-never, use the other side.
                    (KindT::Never(NeverT { from_break: false }), _) => uncoerced_else_block_2.result().coord,
                    (_, KindT::Never(NeverT { from_break: false })) => uncoerced_then_block_2.result().coord,
                    // If we get here, theres no return-nevers in play.
                    // If one side has a break-never, use the other side.
                    (KindT::Never(NeverT { from_break: true }), _) => uncoerced_else_block_2.result().coord,
                    (_, KindT::Never(NeverT { from_break: true })) => uncoerced_then_block_2.result().coord,
                    (a, b) if a == b => uncoerced_then_block_2.result().coord,
                    (a, b) => {
                        let a_citizen = ICitizenTT::try_from(a);
                        let b_citizen = ICitizenTT::try_from(b);
                        match (a_citizen, b_citizen) {
                            (Ok(a_c), Ok(b_c)) => {
                                let nenv_snap = IInDenizenEnvironmentT::Node(nenv.snapshot(self.typing_interner));
                                let a_ancestors: HashSet<ISuperKindTT<'s, 't>> =
                                    self.get_parents(coutputs, parent_ranges, outer_call_location, nenv_snap, ISubKindTT::try_from(a).unwrap()).into_iter().collect();
                                let b_ancestors: HashSet<ISuperKindTT<'s, 't>> =
                                    self.get_parents(coutputs, parent_ranges, outer_call_location, nenv_snap, ISubKindTT::try_from(b).unwrap()).into_iter().collect();
                                let common_ancestors: Vec<ISuperKindTT<'s, 't>> = a_ancestors.intersection(&b_ancestors).copied().collect();

                                if uncoerced_else_block_2.result().coord.ownership != uncoerced_else_block_2.result().coord.ownership {
                                    let range_with_parent: Vec<RangeS<'s>> =
                                        once(if_se.range).chain(parent_ranges.iter().copied()).collect();
                                    let _ = range_with_parent;
                                    panic!("CompileErrorExceptionT RangedInternalErrorT: Two branches of if have different ownerships!\n{:?}\n{:?}", a_c, b_c);
                                }
                                let ownership = uncoerced_else_block_2.result().coord.ownership;

                                if common_ancestors.is_empty() {
                                    let range_with_parent: Vec<RangeS<'s>> =
                                        once(if_se.range).chain(parent_ranges.iter().copied()).collect();
                                    let _ = range_with_parent;
                                    panic!("CompileErrorExceptionT RangedInternalErrorT: No common ancestors of two branches of if:\n{:?}\n{:?}", a_c, b_c);
                                } else if common_ancestors.len() > 1 {
                                    let range_with_parent: Vec<RangeS<'s>> =
                                        once(if_se.range).chain(parent_ranges.iter().copied()).collect();
                                    let _ = range_with_parent;
                                    panic!("CompileErrorExceptionT RangedInternalErrorT: More than one common ancestor of two branches of if:\n{:?}\n{:?}", a_c, b_c);
                                } else {
                                    CoordT { ownership, region: RegionT { region: IRegionT::Default }, kind: KindT::from(common_ancestors[0]) }
                                }
                            }
                            _ => {
                                let range_with_parent: Vec<RangeS<'s>> =
                                    once(if_se.range).chain(parent_ranges.iter().copied()).collect();
                                return Err(ICompileErrorT::CantReconcileBranchesResults {
                                    range: self.typing_interner.alloc_slice_from_vec(range_with_parent),
                                    then_result: uncoerced_then_block_2.result().coord,
                                    else_result: uncoerced_else_block_2.result().coord,
                                });
                            }
                        }
                    }
                };

                let then_fate_snap = IInDenizenEnvironmentT::Node(then_fate.snapshot(self.typing_interner));
                let range_with_parent: Vec<RangeS<'s>> =
                    once(if_se.range).chain(parent_ranges.iter().copied()).collect();
                let then_expr_2 = self.convert(then_fate_snap, coutputs, &range_with_parent, outer_call_location,
                    ReferenceExpressionTE::Block(self.typing_interner.alloc(uncoerced_then_block_2)), common_type);
                let else_fate_snap = IInDenizenEnvironmentT::Node(else_fate.snapshot(self.typing_interner));
                let else_expr_2 = self.convert(else_fate_snap, coutputs, &range_with_parent, outer_call_location,
                    ReferenceExpressionTE::Block(self.typing_interner.alloc(uncoerced_else_block_2)), common_type);

                let if_expr_2 = ReferenceExpressionTE::If(self.typing_interner.alloc(IfTE::new(
                    condition_expr,
                    then_expr_2,
                    else_expr_2,
                )));

                if then_continues == else_continues { // Both continue, or both don't
                    // Each branch might have moved some things. Make sure they moved the same things.
                    if then_unstackified_ancestor_locals != else_unstackified_ancestor_locals {
                        return Err(ICompileErrorT::RangedInternalErrorT {
                            range: self.typing_interner.alloc_slice_copy(&range_with_parent),
                            message: self.scout_arena.intern_str(&format!(
                                "Must move same variables from inside branches!\nFrom then branch: {:?}\nFrom else branch: {:?}",
                                then_unstackified_ancestor_locals, else_unstackified_ancestor_locals)).0,
                        });
                    }
                    if then_restackified_ancestor_locals != else_restackified_ancestor_locals {
                        unreachable!("Scala throws RangedInternalErrorT here, but Vale's flow analysis appears to swallow restackify-mismatches at this point — no Vale program can trigger it (Scala's own test corpus has none either)");
                    }
                    for local in &then_unstackified_ancestor_locals {
                        nenv.mark_local_unstackified(*local);
                    }
                    for local in &then_restackified_ancestor_locals {
                        nenv.mark_local_restackified(*local);
                    }
                } else {
                    // One of them continues and the other does not.
                    if then_continues {
                        for local in &then_unstackified_ancestor_locals {
                            nenv.mark_local_unstackified(*local);
                        }
                        for local in &then_restackified_ancestor_locals {
                            nenv.mark_local_restackified(*local);
                        }
                    } else if else_continues {
                        for local in &else_unstackified_ancestor_locals {
                            nenv.mark_local_unstackified(*local);
                        }
                        for local in &else_restackified_ancestor_locals {
                            nenv.mark_local_restackified(*local);
                        }
                    } else {
                        panic!("implement: evaluate_expression If — vfail branch");
                    }
                }

                let (if_block_unstackified_ancestor_locals, if_block_restackified_ancestor_locals) =
                    nenv.snapshot(self.typing_interner).get_effects_since(nenv.snapshot(self.typing_interner));
                for local in if_block_unstackified_ancestor_locals {
                    nenv.mark_local_unstackified(local);
                }
                for local in if_block_restackified_ancestor_locals {
                    nenv.mark_local_restackified(local);
                }

                let mut all_returns = returns_from_condition;
                all_returns.extend(then_returns_from_exprs);
                all_returns.extend(else_returns_from_exprs);
                Ok((ExpressionTE::Reference(if_expr_2), all_returns))
            }
            IExpressionSE::Break(b) => {
                // See BEAFB, we need to find the nearest while to see local since then.
                let range_with_parent: Vec<RangeS<'s>> =
                    once(b.range).chain(parent_ranges.iter().copied()).collect();
                match nenv.nearest_loop_env(self.typing_interner) {
                    None => {
                        panic!("RangedInternalErrorT: Using break while not inside loop!");
                    }
                    Some((while_nenv, _)) => {
                        assert!(region == nenv.default_region()); // vcurious
                        let void_literal = ReferenceExpressionTE::VoidLiteral(self.typing_interner.alloc(VoidLiteralTE { region}));
                        let drops_te = self.drop_since(coutputs, while_nenv, nenv, &range_with_parent, outer_call_location, life, region, void_literal)?;
                        let break_te = ReferenceExpressionTE::Break(self.typing_interner.alloc(BreakTE { region}));
                        let drops_and_break_te = self.consecutive(&[drops_te, break_te]);
                        Ok((ExpressionTE::Reference(drops_and_break_te), HashSet::new()))
                    }
                }
            }
            IExpressionSE::While(w) => {
                // We make a block for the while-statement which contains its condition (the "if block"),
                // and the body block, so they can access any locals declared by the condition.

                // See BEAFB for why we make a new environment for the While
                let loop_nenv = nenv.make_child(self.typing_interner, expr_1, None);

                let body_se_as_expr: &'s IExpressionSE<'s> =
                    self.scout_arena.alloc(IExpressionSE::Block(w.body));
                let mut loop_block_fate = NodeEnvironmentBox::new(loop_nenv.make_child(self.typing_interner, body_se_as_expr, None));
                let loop_block_fate_starting = loop_block_fate.snapshot(self.typing_interner);
                let (body_expressions_with_result, body_returns_from_exprs) =
                    self.evaluate_block_statements(
                        coutputs,
                        loop_block_fate_starting,
                        &mut loop_block_fate,
                        life.add(self.typing_interner, 1),
                        parent_ranges,
                        outer_call_location,
                        nenv.default_region(),
                        w.body)?;
                let uncoerced_body_block_2 = BlockTE { inner: body_expressions_with_result };

                match uncoerced_body_block_2.result().coord.kind {
                    KindT::Never(_) => {}
                    _ => {
                        let (body_unstackified_ancestor_locals, body_restackified_ancestor_locals) =
                            loop_block_fate.snapshot(self.typing_interner).get_effects_since(nenv.snapshot(self.typing_interner));

                        if !body_unstackified_ancestor_locals.is_empty() {
                            let range_with_parent: &'t [RangeS<'s>] = self.typing_interner.alloc_slice_copy(
                                &once(w.range).chain(parent_ranges.iter().copied()).collect::<Vec<_>>());
                            return Err(ICompileErrorT::CantUnstackifyOutsideLocalFromInsideWhile {
                                range: range_with_parent,
                                local_id: *body_unstackified_ancestor_locals.iter().next().unwrap(),
                            });
                        }
                        if !body_restackified_ancestor_locals.is_empty() {
                            let range_with_parent: &'t [RangeS<'s>] = self.typing_interner.alloc_slice_copy(
                                &once(w.range).chain(parent_ranges.iter().copied()).collect::<Vec<_>>());
                            return Err(ICompileErrorT::CantRestackifyOutsideLocalFromInsideWhile {
                                range: range_with_parent,
                                local_id: *body_unstackified_ancestor_locals.iter().next().unwrap(),
                            });
                        }
                        // BUG: Scala checks bodyRestackifiedAncestorLocals twice (same condition, same error) — mirroring as-is
                        if !body_restackified_ancestor_locals.is_empty() {
                            let range_with_parent: &'t [RangeS<'s>] = self.typing_interner.alloc_slice_copy(
                                &once(w.range).chain(parent_ranges.iter().copied()).collect::<Vec<_>>());
                            return Err(ICompileErrorT::CantRestackifyOutsideLocalFromInsideWhile {
                                range: range_with_parent,
                                local_id: *body_unstackified_ancestor_locals.iter().next().unwrap(),
                            });
                        }
                    }
                }

                let loop_expr_2 = ReferenceExpressionTE::While(self.typing_interner.alloc(WhileTE::new(uncoerced_body_block_2)));
                Ok((ExpressionTE::Reference(loop_expr_2), body_returns_from_exprs))
            }
            IExpressionSE::Map(m) => {
                // Preprocess the entire loop once, to predict what its result type
                // will be.
                // We can't just use this, because any returns inside won't drop
                // the temporary list.
                let element_ref_t = {
                    // See BEAFB for why we make a new environment for the While
                    let loop_nenv = nenv.make_child(self.typing_interner, expr_1, None);
                    let body_se_as_expr: &'s IExpressionSE<'s> =
                        self.scout_arena.alloc(IExpressionSE::Block(m.body));
                    let mut loop_block_fate = NodeEnvironmentBox::new(loop_nenv.make_child(self.typing_interner, body_se_as_expr, None));
                    let loop_block_fate_starting = loop_block_fate.snapshot(self.typing_interner);
                    let (body_expressions_with_result, _) =
                        self.evaluate_block_statements(
                            coutputs,
                            loop_block_fate_starting,
                            &mut loop_block_fate,
                            life.add(self.typing_interner, 1),
                            parent_ranges,
                            outer_call_location,
                            nenv.default_region(),
                            m.body)?;
                    body_expressions_with_result.result().coord
                };

                // Now that we know the result type, let's make a temporary list.

                let self_rune_irune = self.scout_arena.intern_rune(IRuneValS::SelfRune(SelfRuneS {}));
                let self_rune_name_t = INameT::Rune(self.typing_interner.intern_rune_name(RuneNameT { rune: self_rune_irune}));
                let element_coord_templata: &'t CoordTemplataT<'s, 't> = self.typing_interner.alloc(CoordTemplataT { coord: element_ref_t });
                let snap = nenv.snapshot(self.typing_interner);
                let call_env_node = snap.add_entries(
                    self.typing_interner,
                    self.scout_arena,
                    &[(self_rune_name_t, IEnvEntryT::Templata(ITemplataT::Coord(element_coord_templata)))]);
                let call_env = IInDenizenEnvironmentT::Node(call_env_node);
                let make_list_callable = self.new_global_function_group_expression(
                    call_env, coutputs, RegionT { region: IRegionT::Default },
                    self.scout_arena.intern_imprecise_name(IImpreciseNameValS::CodeName(CodeNameS { name: self.keywords.list })));
                let range_with_parent_t: &'t [RangeS<'s>] = self.typing_interner.alloc_slice_copy(
                    &once(m.range).chain(parent_ranges.iter().copied()).collect::<Vec<_>>());
                let rune_parent_env_lookup_rule = IRulexSR::RuneParentEnvLookup(RuneParentEnvLookupSR {
                    range: m.range,
                    rune: RuneUsage { range: m.range, rune: self_rune_irune },
                });
                let make_list_te = self.evaluate_prefix_call(
                    coutputs,
                    nenv,
                    life.add(self.typing_interner, 1),
                    range_with_parent_t,
                    outer_call_location,
                    region,
                    make_list_callable,
                    &[rune_parent_env_lookup_rule],
                    &[self_rune_irune],
                    &[],
                    &[])?;

                let list_local = self.make_temporary_local(
                    nenv, life.add(self.typing_interner, 2), make_list_te.result().coord);
                let let_list_te = ReferenceExpressionTE::LetNormal(self.typing_interner.alloc(LetNormalTE {
                    variable: ILocalVariableT::Reference(list_local),
                    expr: make_list_te,
                }));

                let (loop_te, returns_from_loop) = {
                    // See BEAFB for why we make a new environment for the While
                    let loop_nenv = nenv.make_child(self.typing_interner, expr_1, None);
                    let body_se_as_expr: &'s IExpressionSE<'s> =
                        self.scout_arena.alloc(IExpressionSE::Block(m.body));
                    let mut loop_block_fate = NodeEnvironmentBox::new(loop_nenv.make_child(self.typing_interner, body_se_as_expr, None));
                    let loop_block_fate_starting = loop_block_fate.snapshot(self.typing_interner);
                    let (user_body_te, body_returns_from_exprs) =
                        self.evaluate_block_statements(
                            coutputs,
                            loop_block_fate_starting,
                            &mut loop_block_fate,
                            life.add(self.typing_interner, 1),
                            parent_ranges,
                            outer_call_location,
                            nenv.default_region(),
                            m.body)?;

                    // We store the iteration result in a local because the loop body will have
                    // breaks, and we can't have a BreakTE inside a FunctionCallTE, see BRCOBS.
                    let iteration_result_local = self.make_temporary_local(
                        nenv, life.add(self.typing_interner, 3), user_body_te.result().coord);
                    let let_iteration_result_te = ReferenceExpressionTE::LetNormal(self.typing_interner.alloc(LetNormalTE {
                        variable: ILocalVariableT::Reference(iteration_result_local),
                        expr: user_body_te,
                    }));

                    let add_callable = self.new_global_function_group_expression(
                        call_env, coutputs, RegionT { region: IRegionT::Default },
                        self.scout_arena.intern_imprecise_name(IImpreciseNameValS::CodeName(CodeNameS { name: self.keywords.add })));
                    let local_lookup_te = AddressExpressionTE::LocalLookup(self.typing_interner.alloc(LocalLookupTE {
                        range: m.range,
                        local_variable: ILocalVariableT::Reference(list_local),
                    }));
                    let borrow_load = self.borrow_soft_load(coutputs, local_lookup_te);
                    let unlet_iter = ReferenceExpressionTE::Unlet(self.typing_interner.alloc(self.unlet_local_without_dropping(nenv, &ILocalVariableT::Reference(iteration_result_local))));
                    let add_call = self.evaluate_prefix_call(
                        coutputs,
                        nenv,
                        life.add(self.typing_interner, 4),
                        range_with_parent_t,
                        outer_call_location,
                        region,
                        add_callable,
                        &[],
                        &[],
                        &[],
                        &[borrow_load, unlet_iter])?;
                    let body_te = BlockTE { inner: self.consecutive(&[let_iteration_result_te, add_call]) };

                    let (body_unstackified_ancestor_locals, body_restackified_ancestor_locals) =
                        loop_block_fate.snapshot(self.typing_interner).get_effects_since(nenv.snapshot(self.typing_interner));
                    if !body_unstackified_ancestor_locals.is_empty() {
                        return Err(ICompileErrorT::CantUnstackifyOutsideLocalFromInsideWhile {
                            range: range_with_parent_t,
                            local_id: *body_unstackified_ancestor_locals.iter().next().unwrap(),
                        });
                    }
                    if !body_restackified_ancestor_locals.is_empty() {
                        return Err(ICompileErrorT::CantRestackifyOutsideLocalFromInsideWhile {
                            range: range_with_parent_t,
                            local_id: *body_unstackified_ancestor_locals.iter().next().unwrap(),
                        });
                    }

                    let while_te = ReferenceExpressionTE::While(self.typing_interner.alloc(WhileTE::new(body_te)));
                    (while_te, body_returns_from_exprs)
                };

                let unlet_list_te = ReferenceExpressionTE::Unlet(self.typing_interner.alloc(self.unlet_local_without_dropping(nenv, &ILocalVariableT::Reference(list_local))));

                let combined_te = self.consecutive(&[let_list_te, loop_te, unlet_list_te]);

                Ok((ExpressionTE::Reference(combined_te), returns_from_loop))
            }
            IExpressionSE::ExprMutate(em) => {
                let (unconverted_source_expr_2, returns_from_source) =
                    self.evaluate_and_coerce_to_reference_expression(
                        coutputs, nenv, life.add(self.typing_interner, 0), parent_ranges, outer_call_location, nenv.default_region(), em.expr)?;
                let (destination_expr_2, returns_from_destination) =
                    self.evaluate_expected_address_expression(
                        coutputs, nenv, life.add(self.typing_interner, 1), parent_ranges, outer_call_location, region, em.mutatee)?;
                if destination_expr_2.variability() != VariabilityT::Varying {
                    match destination_expr_2 {
                        AddressExpressionTE::ReferenceMemberLookup(rml) => {
                            match rml.struct_expr.result().coord.kind {
                                KindT::Struct(s) => {
                                    return Err(ICompileErrorT::CantMutateFinalMember {
                                        range: self.typing_interner.alloc_slice_copy(
                                            &once(rml.range).chain(parent_ranges.iter().copied()).collect::<Vec<_>>()),
                                        struct_: *s,
                                        member_name: rml.member_name,
                                    });
                                }
                                _ => panic!("implement: ExprMutate ReferenceMemberLookup non-struct kind"),
                            }
                        }
                        AddressExpressionTE::RuntimeSizedArrayLookup(rsal) => {
                            return Err(ICompileErrorT::CantMutateFinalElement {
                                range: self.typing_interner.alloc_slice_copy(
                                    &once(rsal.range).chain(parent_ranges.iter().copied()).collect::<Vec<_>>()),
                                coord: rsal.array_expr.result().coord,
                            });
                        }
                        AddressExpressionTE::StaticSizedArrayLookup(ssal) => {
                            return Err(ICompileErrorT::CantMutateFinalElement {
                                range: self.typing_interner.alloc_slice_copy(
                                    &once(ssal.range).chain(parent_ranges.iter().copied()).collect::<Vec<_>>()),
                                coord: ssal.array_expr.result().coord,
                            });
                        }
                        _ => panic!("implement: ExprMutate non-varying variability unexpected arm"),
                    }
                }
                let range_with_parent: Vec<RangeS<'s>> =
                    once(em.range).chain(parent_ranges.iter().copied()).collect();
                let is_convertible =
                    self.is_type_convertible(coutputs, IInDenizenEnvironmentT::Node(nenv.snapshot(self.typing_interner)), &range_with_parent, outer_call_location,
                        unconverted_source_expr_2.result().coord, destination_expr_2.result().coord);
                if !is_convertible {
                    return Err(ICompileErrorT::CouldntConvertForMutateT {
                        range: self.typing_interner.alloc_slice_copy(&range_with_parent),
                        expected_type: destination_expr_2.result().coord,
                        actual_type: unconverted_source_expr_2.result().coord,
                    });
                }
                let converted_source_expr_2 =
                    self.convert(IInDenizenEnvironmentT::Node(nenv.snapshot(self.typing_interner)), coutputs, &range_with_parent, outer_call_location,
                        unconverted_source_expr_2, destination_expr_2.result().coord);
                let mutate_2 = ReferenceExpressionTE::Mutate(self.typing_interner.alloc(MutateTE {
                    destination_expr: destination_expr_2,
                    source_expr: converted_source_expr_2,
                }));
                let mut returns = returns_from_source;
                returns.extend(returns_from_destination);
                Ok((ExpressionTE::Reference(mutate_2), returns))
            }
            IExpressionSE::LocalMutate(lm) => {
                let (unconverted_source_expr_2, returns_from_source) =
                    self.evaluate_and_coerce_to_reference_expression(
                        coutputs, nenv, life.add(self.typing_interner, 0), parent_ranges, outer_call_location, region, lm.expr)?;
                // We do this after the source because of statements like these:
                //   set ship = foo(ship);
                // which move the thing on the right and then restackify it on the left.
                let range_with_parent: Vec<RangeS<'s>> =
                    once(lm.range).chain(parent_ranges.iter().copied()).collect();
                let destination_expr_2 =
                    self.evaluate_addressible_lookup_for_mutate(coutputs, nenv, parent_ranges, region, lm.range, lm.name)
                        .unwrap_or_else(|| panic!("Couldnt find {:?}", lm.name));
                // We should have inferred variability from the presents of sets
                assert_eq!(destination_expr_2.variability(), VariabilityT::Varying);
                let is_convertible =
                    self.is_type_convertible(coutputs, IInDenizenEnvironmentT::Node(nenv.snapshot(self.typing_interner)), &range_with_parent, outer_call_location,
                        unconverted_source_expr_2.result().coord, destination_expr_2.result().coord);
                if !is_convertible {
                    return Err(ICompileErrorT::CouldntConvertForMutateT {
                        range: self.typing_interner.alloc_slice_copy(&range_with_parent),
                        expected_type: destination_expr_2.result().coord,
                        actual_type: unconverted_source_expr_2.result().coord,
                    });
                }
                assert!(is_convertible);
                let converted_source_expr_2 =
                    self.convert(IInDenizenEnvironmentT::Node(nenv.snapshot(self.typing_interner)), coutputs, &range_with_parent, outer_call_location,
                        unconverted_source_expr_2, destination_expr_2.result().coord);
                let expr_te = match destination_expr_2 {
                    AddressExpressionTE::LocalLookup(local_lookup) if nenv.unstackifieds().contains(&local_lookup.local_variable.name()) => {
                        nenv.mark_local_restackified(local_lookup.local_variable.name());
                        ReferenceExpressionTE::Restackify(self.typing_interner.alloc(RestackifyTE {
                            variable: local_lookup.local_variable,
                            source_expr: converted_source_expr_2,
                        }))
                    }
                    _ => {
                        ReferenceExpressionTE::Mutate(self.typing_interner.alloc(MutateTE {
                            destination_expr: destination_expr_2,
                            source_expr: converted_source_expr_2,
                        }))
                    }
                };
                Ok((ExpressionTE::Reference(expr_te), returns_from_source))
            }
            IExpressionSE::Tuple(t) => {
                let (exprs_2, returns_from_elements) =
                    self.evaluate_and_coerce_to_reference_expressions(
                        coutputs, nenv, life.add(self.typing_interner, 0), parent_ranges, outer_call_location, nenv.default_region(), t.elements)?;
                let expr_2 = self.resolve_tuple(
                    IInDenizenEnvironmentT::Node(nenv.snapshot(self.typing_interner)),
                    coutputs,
                    parent_ranges,
                    outer_call_location,
                    exprs_2,
                );
                Ok((ExpressionTE::Reference(expr_2), returns_from_elements))
            }
            IExpressionSE::StaticArrayFromValues(sav) => {
                let (exprs_2, returns_from_elements) =
                    self.evaluate_and_coerce_to_reference_expressions(
                        coutputs, nenv, life, parent_ranges, outer_call_location, nenv.default_region(), sav.elements)?;
                let new_parent_ranges: Vec<RangeS<'s>> =
                    once(sav.range).chain(parent_ranges.iter().copied()).collect();
                let expr_2 = self.evaluate_static_sized_array_from_values(
                    coutputs,
                    IInDenizenEnvironmentT::Node(nenv.snapshot(self.typing_interner)),
                    &new_parent_ranges,
                    outer_call_location,
                    sav.rules,
                    sav.maybe_element_type_st.map(|r| r.rune),
                    sav.size_st.rune,
                    sav.mutability_st.rune,
                    sav.variability_st.rune,
                    exprs_2,
                    region,
                )?;
                Ok((ExpressionTE::Reference(ReferenceExpressionTE::StaticArrayFromValues(self.typing_interner.alloc(expr_2))), returns_from_elements))
            }
            IExpressionSE::StaticArrayFromCallable(sa) => {
                let (callable_te, returns_from_callable) =
                    self.evaluate_and_coerce_to_reference_expression(
                        coutputs, nenv, life.add(self.typing_interner, 0), parent_ranges, outer_call_location, nenv.default_region(), sa.callable)?;
                let range_with_parent: Vec<RangeS<'s>> =
                    once(sa.range).chain(parent_ranges.iter().copied()).collect();
                let expr_2 = self.evaluate_static_sized_array_from_callable(
                    coutputs,
                    IInDenizenEnvironmentT::Node(nenv.snapshot(self.typing_interner)),
                    region,
                    &range_with_parent,
                    outer_call_location,
                    sa.rules,
                    sa.maybe_element_type_st.map(|r| r.rune),
                    sa.size_st.rune,
                    sa.mutability_st.rune,
                    sa.variability_st.rune,
                    callable_te,
                )?;
                Ok((ExpressionTE::Reference(ReferenceExpressionTE::StaticArrayFromCallable(self.typing_interner.alloc(expr_2))), returns_from_callable))
            }
            IExpressionSE::NewRuntimeSizedArray(nrsa) => {
                let (size_te, returns_from_size) = self.evaluate_and_coerce_to_reference_expression(
                    coutputs, nenv, life.add(self.typing_interner, 0), parent_ranges, outer_call_location, region, nrsa.size)?;
                let (maybe_callable_te, returns_from_callable) = match nrsa.callable {
                    None => (None, HashSet::new()),
                    Some(callable_ae) => {
                        let (callable_te, rets) = self.evaluate_and_coerce_to_reference_expression(
                            coutputs, nenv, life.add(self.typing_interner, 1), parent_ranges, outer_call_location, nenv.default_region(), callable_ae)?;
                        (Some(callable_te), rets)
                    }
                };
                let range_with_parent: Vec<RangeS<'s>> =
                    once(nrsa.range).chain(parent_ranges.iter().copied()).collect();
                let expr_2 = self.evaluate_runtime_sized_array_from_callable(
                    coutputs,
                    nenv.snapshot(self.typing_interner),
                    &range_with_parent,
                    outer_call_location,
                    region,
                    nrsa.rules,
                    nrsa.maybe_element_type_st.map(|r| r.rune),
                    nrsa.mutability_st.rune,
                    size_te,
                    maybe_callable_te,
                )?;
                let mut returns = returns_from_size;
                returns.extend(returns_from_callable);
                Ok((ExpressionTE::Reference(expr_2), returns))
            }
            IExpressionSE::Block(b) => {
                let mut child_environment = NodeEnvironmentBox::new(nenv.make_child(self.typing_interner, expr_1, None));
                let child_starting = child_environment.snapshot(self.typing_interner);
                let (expressions_with_result, returns_from_exprs) =
                    self.evaluate_block_statements(
                        coutputs,
                        child_starting,
                        &mut child_environment,
                        life,
                        parent_ranges,
                        outer_call_location,
                        nenv.default_region(),
                        b)?;
                let block_2 = ReferenceExpressionTE::Block(self.typing_interner.alloc(BlockTE { inner: expressions_with_result }));
                let (unstackified_ancestor_locals, restackified_ancestor_locals) =
                    child_environment.snapshot(self.typing_interner).get_effects_since(nenv.snapshot(self.typing_interner));
                for local in unstackified_ancestor_locals {
                    nenv.mark_local_unstackified(local);
                }
                for local in restackified_ancestor_locals {
                    nenv.mark_local_restackified(local);
                }
                Ok((ExpressionTE::Reference(block_2), returns_from_exprs))
            }
            IExpressionSE::Pure(_) => panic!("implement: evaluate_expression — Pure"),
            IExpressionSE::ConstantStr(c) => {
                let result = ReferenceExpressionTE::ConstantStr(self.typing_interner.alloc(ConstantStrTE {
                    value: c.value,
                    region,
                }));
                Ok((ExpressionTE::Reference(result), HashSet::new()))
            }
            IExpressionSE::ConstantFloat(c) => {
                let result = ReferenceExpressionTE::ConstantFloat(self.typing_interner.alloc(ConstantFloatTE {
                    value: c.value,
                    region,
                }));
                Ok((ExpressionTE::Reference(result), HashSet::new()))
            }
            IExpressionSE::Destruct(destruct_se) => {
                let (inner_expr_2, returns_from_array_expr) =
                    self.evaluate_and_coerce_to_reference_expression(
                        coutputs, nenv, life.add(self.typing_interner, 0), parent_ranges, outer_call_location, region, destruct_se.inner)?;
                assert!(inner_expr_2.result().coord.ownership == OwnershipT::Own, "can only destruct own");
                let destroy_2 = match inner_expr_2.result().coord.kind {
                    KindT::Struct(struct_tt) => {
                        let struct_def = coutputs.lookup_struct(struct_tt.id, self);
                        let substituter = self.get_placeholder_substituter(
                            self.opts.global_options.sanity_check,
                            nenv.function_environment().template_id,
                            struct_tt.id,
                            IBoundArgumentsSource::InheritBoundsFromTypeItself,
                        );
                        let destination_locals: Vec<ReferenceLocalVariableT<'s, 't>> = struct_def.members.iter().enumerate().map(|(index, m)| {
                            let unsubstituted_coord = match m {
                                IStructMemberT::Normal(NormalStructMemberT { tyype: IMemberTypeT::Reference(ReferenceMemberTypeT { reference }), .. }) => *reference,
                                IStructMemberT::Normal(NormalStructMemberT { tyype: IMemberTypeT::Address(_), .. }) => panic!("implement: Destruct — AddressMemberTypeT"),
                                IStructMemberT::Variadic(_) => panic!("implement: Destruct — VariadicStructMemberT"),
                            };
                            let reference = substituter.substitute_for_coord(coutputs, unsubstituted_coord);
                            self.make_temporary_local(nenv, life.add(self.typing_interner, 1 + index as i32), reference)
                        }).collect();
                        ReferenceExpressionTE::Destroy(self.typing_interner.alloc(DestroyTE {
                            expr: inner_expr_2,
                            struct_tt: struct_tt,
                            destination_reference_variables: self.typing_interner.alloc_slice_from_vec(destination_locals),
                        }))
                    }
                    KindT::Interface(_) => panic!("implement: evaluate_expression Destruct — Interface"),
                    _ => panic!("vfail: Can't destruct type: {:?}", inner_expr_2.result().coord.kind),
                };
                Ok((ExpressionTE::Reference(destroy_2), returns_from_array_expr))
            }
            IExpressionSE::Unlet(unlet_se) => {
                let name = self.translate_var_name_step(unlet_se.name);
                let local = match nenv.get_variable(name, self.typing_interner) {
                    Some(IVariableT::ReferenceLocal(rlv)) => ILocalVariableT::Reference(rlv),
                    Some(IVariableT::AddressibleLocal(_)) => panic!("implement: Unlet — AddressibleLocal"),
                    Some(IVariableT::AddressibleClosure(_)) => panic!("implement: Unlet — AddressibleClosure (not a local)"),
                    Some(IVariableT::ReferenceClosure(_)) => panic!("implement: Unlet — ReferenceClosure (not a local)"),
                    None => panic!("implement: Unlet — No local with name"),
                };
                let result_expr = self.unlet_local_without_dropping(nenv, &local);
                // This will likely be dropped, as theyre probably not doing anything with it.
                // But who knows, maybe they'll do something with it, like pass it as a parameter
                // to something.
                Ok((ExpressionTE::Reference(ReferenceExpressionTE::Unlet(self.typing_interner.alloc(result_expr))), HashSet::new()))
            }
            IExpressionSE::Index(index_se) => {
                let (unborrowed_container_expr_2, returns_from_container_expr) =
                    self.evaluate_expression(coutputs, nenv, life.add(self.typing_interner, 0), parent_ranges, outer_call_location, nenv.default_region(), index_se.left)?;
                let range_with_parent: Vec<RangeS<'s>> =
                    once(index_se.range).chain(parent_ranges.iter().copied()).collect();
                let container_expr_2 =
                    self.dot_borrow(coutputs, nenv, &range_with_parent, outer_call_location, life.add(self.typing_interner, 1), region, unborrowed_container_expr_2);
                let (index_expr_2, returns_from_index_expr) =
                    self.evaluate_and_coerce_to_reference_expression(
                        coutputs, nenv, life.add(self.typing_interner, 2), parent_ranges, outer_call_location, nenv.default_region(), index_se.index_expr)?;
                let expr_templata = match container_expr_2.result().coord.kind {
                    KindT::RuntimeSizedArray(rsa) => {
                        let lookup = self.lookup_in_unknown_sized_array(&range_with_parent, index_se.range, container_expr_2, index_expr_2, rsa)?;
                        ExpressionTE::Address(AddressExpressionTE::RuntimeSizedArrayLookup(self.typing_interner.alloc(lookup)))
                    }
                    KindT::StaticSizedArray(at) => {
                        let lookup = self.lookup_in_static_sized_array(index_se.range, container_expr_2, index_expr_2, *at);
                        ExpressionTE::Address(AddressExpressionTE::StaticSizedArrayLookup(self.typing_interner.alloc(lookup)))
                    }
                    _ => {
                        return Err(ICompileErrorT::CannotSubscriptT {
                            range: self.typing_interner.alloc_slice_copy(&range_with_parent),
                            tyype: container_expr_2.result().coord.kind,
                        });
                    }
                };
                let mut returns = returns_from_container_expr;
                returns.extend(returns_from_index_expr);
                Ok((expr_templata, returns))
            }
            IExpressionSE::RuneLookup(r) => {
                let rune_name_s = self.scout_arena.intern_imprecise_name(
                    IImpreciseNameValS::RuneName(RuneNameValS { rune: r.rune }));
                let templata = nenv.lookup_nearest_with_imprecise_name(rune_name_s, &{
                    let mut s = HashSet::new();
                    s.insert(ILookupContext::TemplataLookupContext);
                    s
                }, self.typing_interner).unwrap();
                match templata {
                    ITemplataT::Integer(value) => {
                        let result = ReferenceExpressionTE::ConstantInt(self.typing_interner.alloc(ConstantIntTE {
                            value: ITemplataT::Integer(value),
                            bits: 32,
                            region,
                        }));
                        Ok((ExpressionTE::Reference(result), HashSet::new()))
                    }
                    ITemplataT::Placeholder(p) if matches!(p.tyype, ITemplataType::IntegerTemplataType(_)) => {
                        let result = ReferenceExpressionTE::ConstantInt(self.typing_interner.alloc(ConstantIntTE {
                            value: ITemplataT::Placeholder(p),
                            bits: 32,
                            region,
                        }));
                        Ok((ExpressionTE::Reference(result), HashSet::new()))
                    }
                    ITemplataT::Prototype(_pt) => {
                        let mut tiny_env = nenv.function_environment().make_child_node_environment(
                            expr_1, life);
                        let arbitrary_name_t = INameT::Arbitrary(self.typing_interner.intern_arbitrary_name(
                            ArbitraryNameT { }));
                        tiny_env.add_entries(self.scout_arena, self.typing_interner,
                            &[(arbitrary_name_t, IEnvEntryT::Templata(templata))]);
                        let arbitrary_imprecise = self.scout_arena.intern_imprecise_name(
                            IImpreciseNameValS::ArbitraryName(ArbitraryNameS {}));
                        let tiny_env_snapshot = tiny_env.snapshot(self.typing_interner);
                        let expr = self.new_global_function_group_expression(
                            IInDenizenEnvironmentT::Node(tiny_env_snapshot),
                            coutputs, RegionT { region: IRegionT::Default }, arbitrary_imprecise);
                        Ok((ExpressionTE::Reference(expr), HashSet::new()))
                    }
                    _ => {
                        let mut ranges: Vec<RangeS<'s>> = vec![r.range.clone()];
                        ranges.extend_from_slice(parent_ranges);
                        return Err(ICompileErrorT::CantUseRuneValueAsExpression {
                            range: self.typing_interner.alloc_slice_copy(&ranges),
                            rune: r.rune,
                        });
                    }
                }
            }
            IExpressionSE::ConstantBool(c) => {
                let result = ReferenceExpressionTE::ConstantBool(self.typing_interner.alloc(ConstantBoolTE {
                    value: c.value,
                    region,
                }));
                Ok((ExpressionTE::Reference(result), HashSet::new()))
            }
            IExpressionSE::OverloadSet(overload_set) => {
                // Per canonical: vassert(rules.isEmpty); val name = parts.head.name
                assert!(overload_set.lookup.rules.is_empty()); // implement
                let name = overload_set.lookup.parts.first().expect("OverloadSet parts must be non-empty").name;
                let mut lookup_filter = HashSet::new();
                lookup_filter.insert(ILookupContext::ExpressionLookupContext);
                let templatas_from_env = nenv.lookup_all_with_imprecise_name(name, &lookup_filter, self.typing_interner);
                let range_list: Vec<RangeS<'s>> = once(overload_set.lookup.range).chain(parent_ranges.iter().copied()).collect();
                let range_list_t: &'t [RangeS<'s>] = self.typing_interner.alloc_slice_from_vec(range_list);
                let templata_from_env = match templatas_from_env.as_slice() {
                    [ITemplataT::Boolean(_value)] => {
                        panic!("implement: evaluate_expression OverloadSet — BooleanTemplataT")
                    }
                    [ITemplataT::Integer(_value)] => {
                        panic!("implement: evaluate_expression OverloadSet — IntegerTemplataT")
                    }
                    [ITemplataT::Placeholder(_t)] => {
                        panic!("implement: evaluate_expression OverloadSet — PlaceholderTemplataT IntegerTemplataType")
                    }
                    _ if !templatas_from_env.is_empty() && templatas_from_env.iter().all(|t| matches!(t, ITemplataT::Function(_))) => {
                        panic!("implement: evaluate_expression OverloadSet — all functions")
                    }
                    _ if templatas_from_env.len() > 1 => {
                        unreachable!("Scala throws RangedInternalErrorT \"Found too many different things named\" here; defensive check with no Scala test coverage and no Vale program known to trigger it")
                    }
                    [] => {
                        return Err(ICompileErrorT::CouldntFindIdentifierToLoadT {
                            range: range_list_t,
                            name,
                        });
                    }
                    _ => unreachable!("Scala's OverloadSet match has no catch-all; Rust over-matches for slice-pattern exhaustiveness"),
                };
                #[allow(unreachable_code)] // unreachable until the panic!-placeholder match arms above get real bodies
                Ok((ExpressionTE::Reference(templata_from_env), HashSet::new()))
            }
        }
    }

    pub fn check_array(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        range: &[RangeS<'s>],
        array_mutability: MutabilityT,
        element_coord: CoordT<'s, 't>,
        generator_prototype: PrototypeT<'s, 't>,
        generator_type: CoordT<'s, 't>,
    ) {
        panic!("Unimplemented: Slab 15 — body migration");
    }

    pub fn get_option(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        nenv: &'t FunctionEnvironmentT<'s, 't>,
        range: &[RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        context_region: RegionT,
        contained_coord: CoordT<'s, 't>,
    ) -> Result<(CoordT<'s, 't>, PrototypeT<'s, 't>, PrototypeT<'s, 't>, IdT<'s, 't>, IdT<'s, 't>), ICompileErrorT<'s, 't>> {

        let opt_name = self.scout_arena.intern_imprecise_name(
            IImpreciseNameValS::CodeName(CodeNameS { name: self.keywords.opt }));
        let interface_templata = match IEnvironmentT::from(nenv).lookup_nearest_with_imprecise_name(
            opt_name,
            [ILookupContext::TemplataLookupContext].into_iter().collect(),
            self.typing_interner,
        ) {
            Some(ITemplataT::InterfaceDefinition(it)) => *it,
            _ => panic!("vfail"),
        };

        let call_range_t = self.typing_interner.alloc_slice_copy(range);
        let opt_interface_val = match self.resolve_interface(
            coutputs,
            IInDenizenEnvironmentT::from(nenv),
            call_range_t,
            call_location,
            interface_templata,
            &[ITemplataT::Coord(self.typing_interner.alloc(CoordTemplataT { coord: contained_coord }))],
        ) {
            IResolveOutcome::ResolveSuccess(s) => s.kind,
            _ => panic!("vfail"),
        };
        let opt_interface_ref = self.typing_interner.intern_interface_tt(InterfaceTTValT { id: opt_interface_val.id });
        let own_opt_coord = CoordT { ownership: OwnershipT::Own, region: context_region, kind: KindT::Interface(opt_interface_ref) };

        let some_name = self.scout_arena.intern_imprecise_name(
            IImpreciseNameValS::CodeName(CodeNameS { name: self.keywords.some }));
        let some_constructor_templata = match IEnvironmentT::from(nenv).lookup_nearest_with_imprecise_name(
            some_name,
            [ILookupContext::ExpressionLookupContext].into_iter().collect(),
            self.typing_interner,
        ) {
            Some(ITemplataT::Function(ft)) => *ft,
            _ => panic!("vwat"),
        };
        let some_constructor = match self.evaluate_generic_light_function_from_call_for_prototype(
            coutputs,
            range,
            call_location,
            IInDenizenEnvironmentT::from(nenv),
            some_constructor_templata,
            &[ITemplataT::Coord(self.typing_interner.alloc(CoordTemplataT { coord: contained_coord }))],
            context_region,
            &[contained_coord],
            &[],
        )? {
            IResolveFunctionResult::ResolveFunctionFailure(_fff) => {
                panic!("CompileErrorExceptionT: RangedInternalErrorT")
            }
            IResolveFunctionResult::ResolveFunctionSuccess(p) => p.prototype.prototype,
        };

        let none_name = self.scout_arena.intern_imprecise_name(
            IImpreciseNameValS::CodeName(CodeNameS { name: self.keywords.none }));
        let none_constructor_templata = match IEnvironmentT::from(nenv).lookup_nearest_with_imprecise_name(
            none_name,
            [ILookupContext::ExpressionLookupContext].into_iter().collect(),
            self.typing_interner,
        ) {
            Some(ITemplataT::Function(ft)) => *ft,
            _ => panic!("vwat"),
        };
        let none_constructor = match self.evaluate_generic_light_function_from_call_for_prototype(
            coutputs,
            range,
            call_location,
            IInDenizenEnvironmentT::from(nenv),
            none_constructor_templata,
            &[ITemplataT::Coord(self.typing_interner.alloc(CoordTemplataT { coord: contained_coord }))],
            context_region,
            &[],
            &[],
        )? {
            IResolveFunctionResult::ResolveFunctionFailure(_fff) => {
                panic!("CompileErrorExceptionT: RangedInternalErrorT")
            }
            IResolveFunctionResult::ResolveFunctionSuccess(p) => p.prototype.prototype,
        };

        let some_impl_id = match self.is_parent(
            coutputs,
            IInDenizenEnvironmentT::from(nenv),
            range,
            call_location,
            ISubKindTT::from(some_constructor.return_type.kind.expect_citizen()),
            ISuperKindTT::Interface(opt_interface_ref),
        ) {
            IsParentResult::IsParent(p) => p.impl_id,
            IsParentResult::IsntParent(_) => panic!("vwat"),
        };

        let none_impl_id = match self.is_parent(
            coutputs,
            IInDenizenEnvironmentT::from(nenv),
            range,
            call_location,
            ISubKindTT::from(none_constructor.return_type.kind.expect_citizen()),
            ISuperKindTT::Interface(opt_interface_ref),
        ) {
            IsParentResult::IsParent(p) => p.impl_id,
            IsParentResult::IsntParent(_) => panic!("vwat"),
        };

        Ok((own_opt_coord, *some_constructor, *none_constructor, some_impl_id, none_impl_id))
    }

    pub fn get_result(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        nenv: &'t FunctionEnvironmentT<'s, 't>,
        range: &[RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        region: RegionT,
        contained_success_coord: CoordT<'s, 't>,
        contained_fail_coord: CoordT<'s, 't>,
    ) -> Result<(CoordT<'s, 't>, PrototypeT<'s, 't>, IdT<'s, 't>, PrototypeT<'s, 't>, IdT<'s, 't>), ICompileErrorT<'s, 't>> {

        let result_name = self.scout_arena.intern_imprecise_name(
            IImpreciseNameValS::CodeName(CodeNameS { name: self.keywords.result }));
        let interface_templata = match IEnvironmentT::from(nenv).lookup_nearest_with_imprecise_name(
            result_name,
            [ILookupContext::TemplataLookupContext].into_iter().collect(),
            self.typing_interner,
        ) {
            Some(ITemplataT::InterfaceDefinition(it)) => *it,
            _ => panic!("vfail"),
        };

        let call_range_t = self.typing_interner.alloc_slice_copy(range);
        let result_interface_val = match self.resolve_interface(
            coutputs,
            IInDenizenEnvironmentT::from(nenv),
            call_range_t,
            call_location,
            interface_templata,
            &[
                ITemplataT::Coord(self.typing_interner.alloc(CoordTemplataT { coord: contained_success_coord })),
                ITemplataT::Coord(self.typing_interner.alloc(CoordTemplataT { coord: contained_fail_coord })),
            ],
        ) {
            IResolveOutcome::ResolveSuccess(s) => s.kind,
            _ => panic!("vfail"),
        };
        let result_interface_ref = self.typing_interner.intern_interface_tt(InterfaceTTValT { id: result_interface_val.id });
        let own_result_coord = CoordT { ownership: OwnershipT::Own, region, kind: KindT::Interface(result_interface_ref) };

        let ok_name = self.scout_arena.intern_imprecise_name(
            IImpreciseNameValS::CodeName(CodeNameS { name: self.keywords.ok }));
        let ok_constructor_templata = match IEnvironmentT::from(nenv).lookup_nearest_with_imprecise_name(
            ok_name,
            [ILookupContext::ExpressionLookupContext].into_iter().collect(),
            self.typing_interner,
        ) {
            Some(ITemplataT::Function(ft)) => *ft,
            _ => panic!("vwat"),
        };
        let ok_constructor = match self.evaluate_generic_light_function_from_call_for_prototype(
            coutputs,
            range,
            call_location,
            IInDenizenEnvironmentT::from(nenv),
            ok_constructor_templata,
            &[
                ITemplataT::Coord(self.typing_interner.alloc(CoordTemplataT { coord: contained_success_coord })),
                ITemplataT::Coord(self.typing_interner.alloc(CoordTemplataT { coord: contained_fail_coord })),
            ],
            region,
            &[contained_success_coord],
            &[],
        )? {
            IResolveFunctionResult::ResolveFunctionFailure(_fff) => {
                panic!("CompileErrorExceptionT: RangedInternalErrorT")
            }
            IResolveFunctionResult::ResolveFunctionSuccess(p) => p.prototype.prototype,
        };
        let ok_kind = ok_constructor.return_type.kind;
        let ok_result_impl = match self.is_parent(
            coutputs,
            IInDenizenEnvironmentT::from(nenv),
            range,
            call_location,
            ISubKindTT::from(ok_kind.expect_struct()),
            ISuperKindTT::Interface(result_interface_ref),
        ) {
            IsParentResult::IsParent(p) => p.impl_id,
            IsParentResult::IsntParent(_) => panic!("vfail"),
        };

        let err_name = self.scout_arena.intern_imprecise_name(
            IImpreciseNameValS::CodeName(CodeNameS { name: self.keywords.err }));
        let err_constructor_templata = match IEnvironmentT::from(nenv).lookup_nearest_with_imprecise_name(
            err_name,
            [ILookupContext::ExpressionLookupContext].into_iter().collect(),
            self.typing_interner,
        ) {
            Some(ITemplataT::Function(ft)) => *ft,
            _ => panic!("vwat"),
        };
        let err_constructor = match self.evaluate_generic_light_function_from_call_for_prototype(
            coutputs,
            range,
            call_location,
            IInDenizenEnvironmentT::from(nenv),
            err_constructor_templata,
            &[
                ITemplataT::Coord(self.typing_interner.alloc(CoordTemplataT { coord: contained_success_coord })),
                ITemplataT::Coord(self.typing_interner.alloc(CoordTemplataT { coord: contained_fail_coord })),
            ],
            region,
            &[contained_fail_coord],
            &[],
        )? {
            IResolveFunctionResult::ResolveFunctionFailure(_fff) => {
                panic!("CompileErrorExceptionT: RangedInternalErrorT")
            }
            IResolveFunctionResult::ResolveFunctionSuccess(p) => p.prototype.prototype,
        };
        let err_kind = err_constructor.return_type.kind;
        let err_result_impl = match self.is_parent(
            coutputs,
            IInDenizenEnvironmentT::from(nenv),
            range,
            call_location,
            ISubKindTT::from(err_kind.expect_struct()),
            ISuperKindTT::Interface(result_interface_ref),
        ) {
            IsParentResult::IsParent(p) => p.impl_id,
            IsParentResult::IsntParent(_) => panic!("vfail"),
        };

        Ok((own_result_coord, *ok_constructor, ok_result_impl, *err_constructor, err_result_impl))
    }

    pub fn weak_alias(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        parent_ranges: &'t [RangeS<'s>],
        expr: ReferenceExpressionTE<'s, 't>,
    ) -> Result<ReferenceExpressionTE<'s, 't>, ICompileErrorT<'s, 't>> {
        match expr.result().coord.kind {
            KindT::Struct(sr) => {
                let struct_def = coutputs.lookup_struct(sr.id, self);
                if !struct_def.weakable {
                    return Err(ICompileErrorT::TookWeakRefOfNonWeakableError { range: parent_ranges });
                }
            }
            KindT::Interface(ir) => {
                let interface_def = coutputs.lookup_interface(*ir, self);
                if !interface_def.weakable {
                    return Err(ICompileErrorT::TookWeakRefOfNonWeakableError { range: parent_ranges });
                }
            }
            _ => panic!("vfail"),
        }

        match expr.result().coord.ownership {
            OwnershipT::Borrow => Ok(ReferenceExpressionTE::BorrowToWeak(self.typing_interner.alloc(BorrowToWeakTE { inner_expr: expr }))),
            other => panic!("vwat: {:?}", other),
        }
    }

    pub fn dot_borrow(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        nenv: &mut NodeEnvironmentBox<'s, 't>,
        range: &[RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        life: LocationInFunctionEnvironmentT<'t>,
        context_region: RegionT,
        undecayed_unborrowed_container_expr_2: ExpressionTE<'s, 't>,
    ) -> ReferenceExpressionTE<'s, 't> {
        match undecayed_unborrowed_container_expr_2 {
            ExpressionTE::Address(a) => {
                panic!("implement: dot_borrow — AddressExpressionTE arm (borrow_soft_load)");
            }
            ExpressionTE::Reference(r) => {
                let unborrowed_container_expr_2 = r; // decaySoloPack(nenv, life + 0, r)
                match unborrowed_container_expr_2.result().coord.ownership {
                    OwnershipT::Own => {
                        panic!("implement: dot_borrow — OwnT arm (makeTemporaryLocal)");
                    }
                    OwnershipT::Borrow | OwnershipT::Share => unborrowed_container_expr_2,
                    OwnershipT::Weak => panic!("implement: dot_borrow — WeakT arm"),
                }
            }
        }
    }

    pub fn evaluate_closure(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        nenv: &mut NodeEnvironmentBox<'s, 't>,
        parent_ranges: &'t [RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        region: RegionT,
        name: IFunctionDeclarationNameS<'s>,
        function_s: &'s FunctionS<'s>,
    ) -> Result<ReferenceExpressionTE<'s, 't>, ICompileErrorT<'s, 't>> {
        let function_a = self.astronomize_lambda(coutputs, nenv, parent_ranges, function_s);

        let snapshot_env = nenv.snapshot(self.typing_interner);
        let closure_struct_tt =
            self.evaluate_closure_struct(coutputs, snapshot_env, parent_ranges, call_location, name, function_a, true)?;
        let closure_coord =
            self.pointify_kind(coutputs, KindT::Struct(self.typing_interner.alloc(closure_struct_tt)), region, OwnershipT::Own);

        let mut range_list = vec![function_a.range];
        range_list.extend_from_slice(parent_ranges);
        let construct_expr_2 =
            self.make_closure_struct_construct_expression(coutputs, nenv, &range_list, region, closure_struct_tt);
        assert!(construct_expr_2.result().coord == closure_coord);

        Ok(construct_expr_2)
    }

    pub fn new_global_function_group_expression(
        &self,
        env: IInDenizenEnvironmentT<'s, 't>,
        coutputs: &mut CompilerOutputs<'s, 't>,
        region: RegionT,
        name: IImpreciseNameS<'s>,
    ) -> ReferenceExpressionTE<'s, 't> {
        let name_ref: &'s IImpreciseNameS<'s> = self.scout_arena.alloc(name);
        let overload_set = self.typing_interner.intern_overload_set(
            OverloadSetTValT { env, name: name_ref });
        let void_expr: ReferenceExpressionTE<'s, 't> =
            ReferenceExpressionTE::VoidLiteral(self.typing_interner.alloc(VoidLiteralTE { region}));
        ReferenceExpressionTE::Reinterpret(self.typing_interner.alloc(ReinterpretTE {
            expr: void_expr,
            result_reference: CoordT {
                ownership: OwnershipT::Share,
                region,
                kind: KindT::OverloadSet(overload_set),
            },
        }))
    }

    pub fn evaluate_block_statements(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        starting_nenv: &'t NodeEnvironmentT<'s, 't>,
        nenv: &mut NodeEnvironmentBox<'s, 't>,
        life: LocationInFunctionEnvironmentT<'t>,
        parent_ranges: &'t [RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        region: RegionT,
        block: &'s BlockSE<'s>,
    ) -> Result<(ReferenceExpressionTE<'s, 't>, HashSet<CoordT<'s, 't>>), ICompileErrorT<'s, 't>> {
        self.evaluate_block_statements_block(
            coutputs, starting_nenv, nenv, parent_ranges, call_location,
            life, region, block)
    }

    pub fn translate_pattern_list(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        nenv: &mut NodeEnvironmentBox<'s, 't>,
        life: LocationInFunctionEnvironmentT<'t>,
        parent_ranges: &'t [RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        patterns_1: &'t [&'s AtomSP<'s>],
        pattern_input_exprs_2: &'t [ReferenceExpressionTE<'s, 't>],
        region: RegionT,
    ) -> ReferenceExpressionTE<'s, 't> {
        self.translate_pattern_list_pattern(
            coutputs, nenv, life, parent_ranges, call_location,
            patterns_1, pattern_input_exprs_2, region,
            |compiler, _coutputs, nenv, _live_capture_locals| {
                ReferenceExpressionTE::VoidLiteral(compiler.typing_interner.alloc(VoidLiteralTE {
                    region: nenv.default_region(),
                }))
            })
    }

    pub fn astronomize_lambda(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        nenv: &mut NodeEnvironmentBox<'s, 't>,
        parent_ranges: &'t [RangeS<'s>],
        function_s: &'s FunctionS<'s>,
    ) -> &'s FunctionA<'s> {
        let range_s = function_s.range;
        let name_s = *function_s.name;
        let attributes_s = function_s.attributes;
        let identifying_runes_s = function_s.generic_params;
        let rune_to_explicit_type = &function_s.rune_to_predicted_type;
        let tyype = &function_s.tyype;
        let params_s = function_s.params;
        let maybe_ret_coord_rune = &function_s.maybe_ret_coord_rune;
        let rules_with_implicitly_coercing_lookups_s = function_s.rules;
        let body_s = function_s.body;

        let mut rune_s_to_pre_known_type_a: IndexMap<IRuneS<'s>, ITemplataType<'s>> =
            rune_to_explicit_type.iter().map(|(k, v)| (*k, v.clone())).collect();
        for param in params_s {
            if let Some(ref coord_rune) = param.pattern.coord_rune {
                rune_s_to_pre_known_type_a.insert(coord_rune.rune, ITemplataType::CoordTemplataType(CoordTemplataType {}));
            }
        }

        let snapshot = nenv.snapshot(self.typing_interner);
        let env_ref: IInDenizenEnvironmentT<'s, 't> =
            IInDenizenEnvironmentT::Node(snapshot);
        let rune_type_solve_env = self.create_rune_type_solver_env(env_ref);

        let rune_type_solver = RuneTypeSolver {
            scout_arena: self.scout_arena,
        };
        let mut range_list = vec![range_s];
        range_list.extend_from_slice(parent_ranges);
        let rune_a_to_type_with_implicitly_coercing_lookups_s =
            match rune_type_solver.solve_rune_type(
                self.opts.global_options.sanity_check,
                &rune_type_solve_env,
                range_list.clone(),
                false,
                rules_with_implicitly_coercing_lookups_s,
                &identifying_runes_s.iter().map(|gp| gp.rune.rune).collect::<Vec<_>>(),
                true,
                rune_s_to_pre_known_type_a,
            ) {
                Ok(t) => t,
                Err(_e) => panic!("CouldntSolveRuneTypesT"),
            };

        let mut rune_a_to_type: IndexMap<IRuneS<'s>, ITemplataType<'s>> =
            rune_a_to_type_with_implicitly_coercing_lookups_s;
        // We've now calculated all the types of all the runes, but the LookupSR rules are still a bit
        // loose. We intentionally ignored the types of the things they're looking up, so we could know
        // what types we *expect* them to be, so we could coerce.
        // That coercion is good, but lets make it more explicit.
        let mut rule_builder: Vec<IRulexSR<'s>> = Vec::new();
        match explicify_lookups(
            &rune_type_solve_env,
            self.scout_arena,
            &mut rune_a_to_type,
            &mut rule_builder,
            rules_with_implicitly_coercing_lookups_s.to_vec(),
        ) {
            Err(_e) => panic!("explicify_lookups failed in astronomize_lambda"),
            Ok(()) => {}
        }

        let mut attributes: Vec<IFunctionAttributeS<'s>> = attributes_s.to_vec();
        attributes.push(IFunctionAttributeS::UserFunction(UserFunctionS));

        self.scout_arena.alloc(FunctionA::new(
            range_s,
            name_s,
            self.scout_arena.alloc_slice_from_vec(attributes),
            tyype.clone(),
            identifying_runes_s,
            self.scout_arena.alloc_index_map_from_iter(rune_a_to_type.into_iter()),
            params_s,
            maybe_ret_coord_rune.clone(),
            self.scout_arena.alloc_slice_from_vec(rule_builder),
            *body_s,
        ))
    }

    pub fn drop_since(
        &self,
        coutputs: &mut CompilerOutputs<'s, 't>,
        starting_nenv: &'t NodeEnvironmentT<'s, 't>,
        nenv: &mut NodeEnvironmentBox<'s, 't>,
        range: &[RangeS<'s>],
        call_location: LocationInDenizen<'s>,
        life: LocationInFunctionEnvironmentT<'t>,
        region: RegionT,
        expr_te: ReferenceExpressionTE<'s, 't>,
    ) -> Result<ReferenceExpressionTE<'s, 't>, ICompileErrorT<'s, 't>> {
        let snapshot = nenv.snapshot(self.typing_interner);
        let unreversed_variables_to_destruct =
            snapshot.get_live_variables_introduced_since(starting_nenv);

        if unreversed_variables_to_destruct.is_empty() {
            Ok(expr_te)
        } else {
            match expr_te.result().coord.kind {
                KindT::Void(_) => {
                    let reversed_variables_to_destruct: Vec<_> = unreversed_variables_to_destruct.iter().rev().collect();
                    let destroy_expressions = self.unlet_and_drop_all(coutputs, nenv, range, call_location, region, &reversed_variables_to_destruct)?;
                    let mut exprs: Vec<ReferenceExpressionTE<'s, 't>> = Vec::new();
                    exprs.push(expr_te);
                    exprs.extend(destroy_expressions);
                    exprs.push(ReferenceExpressionTE::VoidLiteral(self.typing_interner.alloc(VoidLiteralTE { region})));
                    Ok(self.consecutive(&exprs))
                }
                KindT::Never(_) => {
                    // In this case, we want to not drop them, so we can support things like:
                    //   func drop(self Server) { panic("unreachable"); }
                    // and not drop Server.
                    let reversed_variables_to_destruct: Vec<_> = unreversed_variables_to_destruct.iter().rev().collect();
                    let _destroy_expressions = self.unlet_all_without_dropping(coutputs, nenv, range, &reversed_variables_to_destruct);
                    // Just dont add in the destroyExpressions, let em go.
                    // We did the above simply to mark them as unstackified.
                    Ok(expr_te)
                }
                _ => {
                    let (resultified_expr, result_local_variable) = self.resultify_expressions(nenv, life.add(self.typing_interner, 1), expr_te);
                    let reversed_variables_to_destruct: Vec<_> = unreversed_variables_to_destruct.iter().rev().collect();
                    let destroy_expressions = self.unlet_and_drop_all(coutputs, nenv, range, call_location, region, &reversed_variables_to_destruct)?;
                    let mut exprs: Vec<ReferenceExpressionTE<'s, 't>> = Vec::new();
                    exprs.push(resultified_expr);
                    exprs.extend(destroy_expressions);
                    let result_ilocal_variable = ILocalVariableT::Reference(result_local_variable);
                    let unlet_te = self.unlet_local_without_dropping(nenv, &result_ilocal_variable);
                    exprs.push(ReferenceExpressionTE::Unlet(self.typing_interner.alloc(unlet_te)));
                    Ok(self.consecutive(&exprs))
                }
            }
        }
    }

    pub fn resultify_expressions(
        &self,
        nenv: &mut NodeEnvironmentBox<'s, 't>,
        life: LocationInFunctionEnvironmentT<'t>,
        expr: ReferenceExpressionTE<'s, 't>,
    ) -> (ReferenceExpressionTE<'s, 't>, ReferenceLocalVariableT<'s, 't>) {
        let result_var_ref = self.typing_interner.intern_typing_pass_block_result_var_name(TypingPassBlockResultVarNameT { life });
        let result_var_name: IVarNameT<'s, 't> = result_var_ref.into();
        let result_variable = ReferenceLocalVariableT { name: result_var_name, variability: VariabilityT::Final, coord: expr.result().coord };
        let result_let = LetNormalTE { variable: ILocalVariableT::Reference(result_variable), expr };
        nenv.add_variable(IVariableT::ReferenceLocal(result_variable));
        (ReferenceExpressionTE::LetNormal(self.typing_interner.alloc(result_let)), result_variable)
    }

}

// Concrete IRuneTypeSolverEnv for the LetSE arm of evaluate. The Scala anonymous
// `new IRuneTypeSolverEnv` at ExpressionCompiler.scala:959 closes over `nenv` and
// delegates to lookupNearestWithImpreciseName. This struct captures that field.
// Same shape as `HigherTypingRuneTypeSolverEnv` in higher_typing_pass.rs (which
// collapses 6 anonymous Scala impls into one named struct).
struct LetExprRuneTypeSolverEnv<'a, 's, 't>
where
    's: 't,
{
    nenv: &'a NodeEnvironmentBox<'s, 't>,
    typing_interner: &'a TypingInterner<'s, 't>,
    scout_arena: &'a ScoutArena<'s>,
}

impl<'a, 's, 't> IRuneTypeSolverEnv<'s>
    for LetExprRuneTypeSolverEnv<'a, 's, 't>
where
    's: 't,
{
    fn lookup(
        &self,
        range: RangeS<'s>,
        name_s: IImpreciseNameS<'s>,
    ) -> Result<
        IRuneTypeSolverLookupResult<'s>,
        IRuneTypingLookupFailedError<'s>,
    > {
        let mut filter = HashSet::new();
        filter.insert(ILookupContext::TemplataLookupContext);
        match self.nenv.lookup_nearest_with_imprecise_name(name_s, &filter, self.typing_interner) {
            Some(ITemplataT::StructDefinition(t)) => {
                Ok(IRuneTypeSolverLookupResult::Citizen(
                    CitizenRuneTypeSolverLookupResult {
                        tyype: ITemplataType::TemplateTemplataType(
                            t.origin_struct.tyype,
                        ),
                        generic_params: t.origin_struct.generic_parameters,
                    },
                ))
            }
            Some(ITemplataT::InterfaceDefinition(t)) => {
                Ok(IRuneTypeSolverLookupResult::Citizen(
                    CitizenRuneTypeSolverLookupResult {
                        tyype: ITemplataType::TemplateTemplataType(
                            t.origin_interface.tyype,
                        ),
                        generic_params: t.origin_interface.generic_parameters,
                    },
                ))
            }
            Some(x) => {
                Ok(IRuneTypeSolverLookupResult::Templata(
                    TemplataLookupResult {
                        templata: x.tyype(self.scout_arena),
                    },
                ))
            }
            None => Err(
                IRuneTypingLookupFailedError::CouldntFindType(
                    RuneTypingCouldntFindType {
                        range,
                        name: name_s,
                    },
                ),
            ),
        }
    }
}
