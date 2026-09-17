use crate::utils::range::CodeLocationS;

use crate::postparsing::names::*;

use crate::typing::names::names::*;
use crate::typing::types::types::*;
use crate::typing::compiler::Compiler;
use std::marker::PhantomData;
use std::mem::discriminant;

impl<'s, 'ctx, 't> Compiler<'s, 'ctx, 't>
where 's: 't,
{
    pub fn translate_generic_template_function_name(&self, function_name: IFunctionDeclarationNameS<'s>, params: &[CoordT<'s, 't>]) -> INameT<'s, 't> {
        match function_name {
            IFunctionDeclarationNameS::LambdaDeclarationName(lambda_name) => {
                let interned = self.typing_interner.intern_lambda_call_function_template_name(LambdaCallFunctionTemplateNameValT {
                    code_location: lambda_name.code_location,
                    param_types: params,
                });
                INameT::LambdaCallFunctionTemplate(interned)
            }
            _ => { panic!("vwat: Only templates should call this"); }
        }
    }

    pub fn translate_generic_function_name(&self, function_name: IFunctionDeclarationNameS<'s>) -> IFunctionTemplateNameT<'s, 't> {
        match function_name {
            IFunctionDeclarationNameS::LambdaDeclarationName(_) => {
                panic!("Lambdas are generic templates, not generics");
            }
            IFunctionDeclarationNameS::FunctionName(n) => {
                IFunctionTemplateNameT::FunctionTemplate(
                    self.typing_interner.intern_function_template_name(FunctionTemplateNameT {
                        human_name: n.name,
                        code_location: self.translate_code_location(n.code_location),
                    })
                )
            }
            IFunctionDeclarationNameS::ForwarderFunctionDeclarationName(r) => {
                IFunctionTemplateNameT::ForwarderFunctionTemplate(
                    self.typing_interner.intern_forwarder_function_template_name(ForwarderFunctionTemplateNameT {
                        inner: self.translate_generic_function_name(r.inner),
                        index: r.index,
                    })
                )
            }
            IFunctionDeclarationNameS::ConstructorName(r) => {
                match r.tlcd {
                    ICitizenDeclarationNameS::TopLevelStructDeclarationName(s) => {
                        IFunctionTemplateNameT::FunctionTemplate(
                            self.typing_interner.intern_function_template_name(FunctionTemplateNameT {
                                human_name: s.name,
                                code_location: self.translate_code_location(s.range.begin),
                            })
                        )
                    }
                    ICitizenDeclarationNameS::TopLevelInterfaceDeclarationName(i) => {
                        IFunctionTemplateNameT::FunctionTemplate(
                            self.typing_interner.intern_function_template_name(FunctionTemplateNameT {
                                human_name: i.name,
                                code_location: self.translate_code_location(i.range.begin),
                            })
                        )
                    }
                    ICitizenDeclarationNameS::AnonymousSubstructTemplateName(astn) => {
                        // See LNASC.
                        let citizen_name = self.translate_citizen_name(ICitizenDeclarationNameS::AnonymousSubstructTemplateName(astn));
                        IFunctionTemplateNameT::AnonymousSubstructConstructorTemplate(
                            self.typing_interner.intern_anonymous_substruct_constructor_template_name(
                                AnonymousSubstructConstructorTemplateNameT { substruct: citizen_name }
                            )
                        )
                    }
                }
            }
            IFunctionDeclarationNameS::ImmConcreteDestructorName(_) => panic!("Unimplemented: ImmConcreteDestructorName in translate_generic_function_name"),
            IFunctionDeclarationNameS::ImmInterfaceDestructorName(_) => panic!("Unimplemented: ImmInterfaceDestructorName in translate_generic_function_name"),
        }
    }

    pub fn translate_struct_name(&self, name: IStructDeclarationNameS<'s>) -> IStructTemplateNameT<'s, 't> {
        match name {
            IStructDeclarationNameS::TopLevelStructDeclarationName(top_level) => {
                let struct_template_name = StructTemplateNameT {
                    human_name: top_level.name,
                };
                IStructTemplateNameT::StructTemplate(
                    self.typing_interner.intern_struct_template_name(struct_template_name)
                )
            }
            IStructDeclarationNameS::AnonymousSubstructTemplateName(anon) => {
                let interface_template_name = self.translate_interface_name(anon.interface_name);
                IStructTemplateNameT::AnonymousSubstructTemplate(
                    self.typing_interner.intern_anonymous_substruct_template_name(
                        AnonymousSubstructTemplateNameT { interface: interface_template_name }
                    )
                )
            }
        }
    }

    pub fn translate_interface_name(&self, name: TopLevelInterfaceDeclarationNameS<'s>) -> IInterfaceTemplateNameT<'s, 't> {
        let interface_template_name = InterfaceTemplateNameT {
            human_namee: name.name,
        };
        IInterfaceTemplateNameT::InterfaceTemplate(
            self.typing_interner.intern_interface_template_name(interface_template_name)
        )
    }

    pub fn translate_citizen_name(&self, name: ICitizenDeclarationNameS<'s>) -> ICitizenTemplateNameT<'s, 't> {
        match name {
            ICitizenDeclarationNameS::TopLevelStructDeclarationName(n) => {
                ICitizenTemplateNameT::StructTemplate(
                    self.typing_interner.intern_struct_template_name(StructTemplateNameT {
                        human_name: n.name,
                    })
                )
            }
            ICitizenDeclarationNameS::AnonymousSubstructTemplateName(astn) => {
                // See LNASC.
                let interface_template_name = self.translate_interface_name(astn.interface_name);
                ICitizenTemplateNameT::AnonymousSubstructTemplate(
                    self.typing_interner.intern_anonymous_substruct_template_name(
                        AnonymousSubstructTemplateNameT { interface: interface_template_name }
                    )
                )
            }
            ICitizenDeclarationNameS::TopLevelInterfaceDeclarationName(n) => {
                ICitizenTemplateNameT::InterfaceTemplate(
                    self.typing_interner.intern_interface_template_name(InterfaceTemplateNameT {
                        human_namee: n.name,
                    })
                )
            }
        }
    }

    pub fn translate_name_step(&self, name: INameS<'s>) -> INameT<'s, 't> {
        match name {
            INameS::LambdaStructDeclaration(_) => panic!("Unimplemented: translate_name_step LambdaStructDeclaration"),
            INameS::LetName(_) => panic!("Unimplemented: translate_name_step LetNameS"),
            INameS::ExportAsName(_) => panic!("Unimplemented: translate_name_step ExportAsNameS"),
            INameS::VarName(v) => panic!("Unimplemented: translate_name_step VarName {:?}", v),
            INameS::TopLevelStructDeclaration(s) => {
                match self.translate_struct_name(IStructDeclarationNameS::TopLevelStructDeclarationName(*s)) {
                    IStructTemplateNameT::StructTemplate(r) => INameT::StructTemplate(r),
                    IStructTemplateNameT::AnonymousSubstructTemplate(r) => INameT::AnonymousSubstructTemplate(r),
                    IStructTemplateNameT::LambdaCitizenTemplate(_) => panic!("Unimplemented: translate_name_step LambdaCitizenTemplate"),
                }
            }
            INameS::TopLevelInterfaceDeclaration(i) => {
                match self.translate_interface_name(*i) {
                    IInterfaceTemplateNameT::InterfaceTemplate(r) => INameT::InterfaceTemplate(r),
                }
            }
            INameS::AnonymousSubstructTemplateName(n) => {
                // See LNASC.
                let interface_template_name = self.translate_interface_name(n.interface_name);
                INameT::AnonymousSubstructTemplate(
                    self.typing_interner.intern_anonymous_substruct_template_name(
                        AnonymousSubstructTemplateNameT { interface: interface_template_name }
                    )
                )
            }
            INameS::AnonymousSubstructImplDeclaration(n) => {
                // See LNASC.
                let interface_template_name = self.translate_interface_name(n.interface);
                INameT::AnonymousSubstructImplTemplate(
                    self.typing_interner.intern_anonymous_substruct_impl_template_name(
                        AnonymousSubstructImplTemplateNameT { interface: interface_template_name }
                    )
                )
            }
            INameS::ImplDeclaration(_) => panic!("Unimplemented: translate_name_step ImplDeclarationNameS"),
            INameS::RuneName(_) => panic!("Unimplemented: translate_name_step RuneNameS"),
            INameS::RuntimeSizedArrayDeclarationName(_) => panic!("Unimplemented: translate_name_step RuntimeSizedArrayDeclarationName"),
            INameS::StaticSizedArrayDeclarationName(_) => panic!("Unimplemented: translate_name_step StaticSizedArrayDeclarationName"),
            INameS::GlobalFunctionFamilyName(_) => panic!("Unimplemented: translate_name_step GlobalFunctionFamilyName"),
            INameS::ArbitraryName(_) => panic!("Unimplemented: translate_name_step ArbitraryName"),
            INameS::FunctionDeclaration(fn_decl) => {
                match fn_decl {
                    IFunctionDeclarationNameS::LambdaDeclarationName(_) => panic!("Unimplemented: translate_name_step LambdaDeclarationNameS"),
                    IFunctionDeclarationNameS::FunctionName(n) => {
                        INameT::FunctionTemplate(self.typing_interner.intern_function_template_name(FunctionTemplateNameT {
                            human_name: n.name,
                            code_location: n.code_location,
                        }))
                    }
                    IFunctionDeclarationNameS::ConstructorName(ctor) => {
                        match ctor.tlcd {
                            ICitizenDeclarationNameS::TopLevelStructDeclarationName(n) => {
                                INameT::FunctionTemplate(self.typing_interner.intern_function_template_name(FunctionTemplateNameT {
                                    human_name: n.name,
                                    code_location: n.range.begin,
                                }))
                            }
                            ICitizenDeclarationNameS::TopLevelInterfaceDeclarationName(_) => {
                                panic!("Unimplemented: translate_name_step ConstructorNameS for interface")
                            }
                            ICitizenDeclarationNameS::AnonymousSubstructTemplateName(astn) => {
                                // See LNASC.
                                let citizen_name = self.translate_citizen_name(ICitizenDeclarationNameS::AnonymousSubstructTemplateName(astn));
                                INameT::AnonymousSubstructConstructorTemplate(
                                    self.typing_interner.intern_anonymous_substruct_constructor_template_name(
                                        AnonymousSubstructConstructorTemplateNameT { substruct: citizen_name }
                                    )
                                )
                            }
                        }
                    }
                    IFunctionDeclarationNameS::ForwarderFunctionDeclarationName(_) => panic!("Unimplemented: translate_name_step ForwarderFunctionDeclarationName"),
                    IFunctionDeclarationNameS::ImmConcreteDestructorName(_) => panic!("Unimplemented: translate_name_step ImmConcreteDestructorName"),
                    IFunctionDeclarationNameS::ImmInterfaceDestructorName(_) => panic!("Unimplemented: translate_name_step ImmInterfaceDestructorName"),
                }
            }
        }
    }

    pub fn translate_code_location(&self, s: CodeLocationS<'s>) -> CodeLocationS<'s> {
        s
    }

    pub fn translate_var_name_step(&self, name: IVarNameS<'s>) -> IVarNameT<'s, 't> {
        match name {
            IVarNameS::CodeVarName(name_str) => {
                IVarNameT::CodeVar(self.typing_interner.intern_code_var_name(
                    CodeVarNameT { name: name_str}))
            }
            IVarNameS::ClosureParamName(closure_param_name_s) => {
                IVarNameT::ClosureParam(self.typing_interner.intern_closure_param_name(
                    ClosureParamNameT { code_location: closure_param_name_s.code_location}))
            }
            IVarNameS::MagicParamName(code_location) => {
                IVarNameT::MagicParam(self.typing_interner.intern_magic_param_name(
                    MagicParamNameT { code_location2: self.translate_code_location(code_location)}))
            }
            IVarNameS::SelfName => {
                IVarNameT::Self_(self.typing_interner.intern_self_name(
                    SelfNameT { }))
            }
            IVarNameS::ConstructingMemberName(n) => {
                IVarNameT::ConstructingMember(self.typing_interner.intern_constructing_member_name(
                    ConstructingMemberNameT { name: n}))
            }
            IVarNameS::IterableName(range) => {
                IVarNameT::Iterable(self.typing_interner.intern_iterable_name(
                    IterableNameT { range}))
            }
            IVarNameS::IteratorName(range) => {
                IVarNameT::Iterator(self.typing_interner.intern_iterator_name(
                    IteratorNameT { range}))
            }
            IVarNameS::IterationOptionName(range) => {
                IVarNameT::IterationOption(self.typing_interner.intern_iteration_option_name(
                    IterationOptionNameT { range}))
            }
            _ => {
                panic!("implement: translate_var_name_step — {:?}", discriminant(&name));
            }
        }
    }

    pub fn translate_impl_name(&self, n: IImplDeclarationNameS<'s>) -> IImplTemplateNameT<'s, 't> {
        match n {
            IImplDeclarationNameS::ImplDeclarationName(impl_decl) => {
                let impl_template_name = ImplTemplateNameT {
                    code_location_s: self.translate_code_location(impl_decl.code_location),
                };
                IImplTemplateNameT::ImplTemplate(
                    self.typing_interner.intern_impl_template_name(impl_template_name)
                )
            }
            IImplDeclarationNameS::AnonymousSubstructImplDeclarationName(anon) => {
                let interface_template_name = self.translate_interface_name(anon.interface);
                let anon_impl_template_name = AnonymousSubstructImplTemplateNameT {
                    interface: interface_template_name,
                };
                IImplTemplateNameT::AnonymousSubstructImplTemplate(
                    self.typing_interner.intern_anonymous_substruct_impl_template_name(anon_impl_template_name)
                )
            }
        }
    }

}
