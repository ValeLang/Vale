use crate::testvm::vivem::empty_stdin;
use crate::testvm::vivem::execute_with_primitive_args;
use crate::testvm::vivem::null_stdout;
use crate::von::ast::IVonData;
use crate::von::ast::VonInt;
use std::io::stdout;
use std::iter::once;
use std::marker::PhantomData;
use bumpalo::Bump;
use crate::final_ast::ast::FunctionH;
use crate::final_ast::ast::IFunctionAttributeH;
use crate::final_ast::ast::IdHValH;
use crate::final_ast::ast::PackageH;
use crate::final_ast::ast::ProgramH;
use crate::final_ast::ast::PrototypeHValH;
use crate::final_ast::instructions::ArgumentH;
use crate::final_ast::instructions::BlockH;
use crate::final_ast::instructions::CallH;
use crate::final_ast::instructions::ConstantIntH;
use crate::final_ast::instructions::ExpressionH;
use crate::final_ast::instructions::ExternCallH;
use crate::final_ast::types::CoordH;
use crate::final_ast::types::HamutsFunctionExtern;
use crate::final_ast::types::IntHT;
use crate::final_ast::types::KindHT;
use crate::final_ast::types::LocationH;
use crate::final_ast::types::OwnershipH;
use crate::final_ast::types::SimpleId;
use crate::final_ast::types::SimpleIdStep;
use crate::interner::StrI;
use crate::keywords::Keywords;
use crate::parse_arena::ParseArena;
use crate::scout_arena::ScoutArena;
use crate::simplifying::hammer_interner::HammerInterner;
use crate::utils::code_hierarchy::PackageCoordinate;
use crate::utils::code_hierarchy::PackageCoordinateMap;

pub struct VivemTests {}

#[test]
fn return_7() {

    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let hammer_bump = Bump::new();
    let vivem_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let interner = HammerInterner::new(&hammer_bump);

    let test_tld: PackageCoordinate<'_> = *scout_arena.intern_package_coordinate(scout_arena.intern_str("test"), &[]);
    let main_str = scout_arena.intern_str("main");
    let main_id = interner.intern_id_h(IdHValH {
        local_name: main_str,
        package_coordinate: test_tld,
        shortened_name: main_str,
        fully_qualified_name: main_str,
    });
    let i32_return = CoordH { ownership: OwnershipH::MutableShareH, location: LocationH::InlineH, kind: KindHT::IntHT(IntHT { bits: 32 }) };
    let main_proto = interner.intern_prototype(PrototypeHValH {
        id: main_id,
        params: &[],
        return_type: i32_return,
    });
    let const7 = interner.alloc(ConstantIntH { value: 7, bits: 32 });
    let block = interner.alloc(BlockH { inner: ExpressionH::ConstantIntH(const7) });
    let main = FunctionH {
        prototype: main_proto,
        is_abstract: true,
        is_extern: false,
        attributes: interner.alloc_slice_copy(&[IFunctionAttributeH::UserFunctionH]),
        body: ExpressionH::BlockH(block),
    };
    let export_name_to_function = interner.alloc_index_map_from_iter(once((main_str, main_proto)));
    let package = PackageH {
        interfaces: &[],
        structs: &[],
        functions: interner.alloc_slice_copy(&[main]),
        static_sized_arrays: &[],
        runtime_sized_arrays: &[],
        export_name_to_function: interner.alloc(export_name_to_function),
        export_name_to_kind: interner.alloc(interner.alloc_index_map()),
        prototype_to_extern: interner.alloc(interner.alloc_index_map()),
        kind_to_extern: interner.alloc(interner.alloc_index_map()),
    };
    let mut packages = PackageCoordinateMap::new();
    let test_tld_ref: &PackageCoordinate<'_> = scout_arena.intern_package_coordinate(scout_arena.intern_str("test"), &[]);
    packages.put(test_tld_ref, package);
    let program_h: &ProgramH = hammer_bump.alloc(ProgramH { packages });

    let mut stdout = stdout();
    let result = execute_with_primitive_args(
        program_h,
        &interner,
        &scout_arena,
        &[],
        &mut stdout,
        &vivem_bump,
        &empty_stdin,
        &null_stdout,
    );
    match result {
        Ok(IVonData::Int(VonInt { value: 7 })) => {}
        other => panic!("expected VonInt(7), got {:?}", other),
    }
}

#[test]
fn adding() {

    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let hammer_bump = Bump::new();
    let vivem_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let interner = HammerInterner::new(&hammer_bump);

    let int_coord = CoordH { ownership: OwnershipH::MutableShareH, location: LocationH::InlineH, kind: KindHT::IntHT(IntHT { bits: 32 }) };

    let builtin_pkg_ref: &PackageCoordinate<'_> = scout_arena.intern_package_coordinate(keywords.empty_string, &[]);
    let test_tld_ref: &PackageCoordinate<'_> = scout_arena.intern_package_coordinate(scout_arena.intern_str("test"), &[]);

    let add_str = scout_arena.intern_str("__vbi_addI32");
    let add_id = interner.intern_id_h(IdHValH {
        local_name: add_str,
        package_coordinate: *builtin_pkg_ref,
        shortened_name: add_str,
        fully_qualified_name: add_str,
    });
    let add_prototype = interner.intern_prototype(PrototypeHValH {
        id: add_id,
        params: interner.alloc_slice_copy(&[int_coord, int_coord]),
        return_type: int_coord,
    });

    let main_str = scout_arena.intern_str("main");
    let main_id = interner.intern_id_h(IdHValH {
        local_name: main_str,
        package_coordinate: *test_tld_ref,
        shortened_name: main_str,
        fully_qualified_name: main_str,
    });
    let main_prototype = interner.intern_prototype(PrototypeHValH {
        id: main_id,
        params: &[],
        return_type: int_coord,
    });

    let const53 = interner.alloc(ConstantIntH { value: 53, bits: 32 });
    let const54 = interner.alloc(ConstantIntH { value: 54, bits: 32 });
    let inner_call = interner.alloc(CallH {
        function: add_prototype,
        args_expressions: interner.alloc_slice_copy(&[ExpressionH::ConstantIntH(const53), ExpressionH::ConstantIntH(const54)]),
    });
    let const52 = interner.alloc(ConstantIntH { value: 52, bits: 32 });
    let outer_call = interner.alloc(CallH {
        function: add_prototype,
        args_expressions: interner.alloc_slice_copy(&[ExpressionH::ConstantIntH(const52), ExpressionH::CallH(inner_call)]),
    });
    let main_block = interner.alloc(BlockH { inner: ExpressionH::CallH(outer_call) });
    let main = FunctionH {
        prototype: main_prototype,
        is_abstract: true,
        is_extern: false,
        attributes: interner.alloc_slice_copy(&[IFunctionAttributeH::UserFunctionH]),
        body: ExpressionH::BlockH(main_block),
    };

    let arg0 = interner.alloc(ArgumentH { result_type: int_coord, argument_index: 0 });
    let arg1 = interner.alloc(ArgumentH { result_type: int_coord, argument_index: 1 });
    let add_extern_call = interner.alloc(ExternCallH {
        function: add_prototype,
        args_expressions: interner.alloc_slice_copy(&[ExpressionH::ArgumentH(arg0), ExpressionH::ArgumentH(arg1)]),
    });
    let add_extern = FunctionH {
        prototype: add_prototype,
        is_abstract: false,
        is_extern: true,
        attributes: &[],
        body: ExpressionH::ExternCallH(add_extern_call),
    };

    let simple_id_step_empty = SimpleIdStep { name: keywords.empty_string, template_args: &[] };
    let simple_id_step_add = SimpleIdStep { name: add_str, template_args: &[] };
    let add_simple_id = SimpleId { steps: interner.alloc_slice_copy(&[simple_id_step_empty, simple_id_step_add]) };

    let mut builtin_prototype_to_extern = interner.alloc_index_map();
    builtin_prototype_to_extern.insert(add_prototype, HamutsFunctionExtern {
        maybe_extern_name: add_str,
        prototype: add_prototype,
        simple_id: add_simple_id,
    });

    let builtin_package = PackageH {
        interfaces: &[],
        structs: &[],
        functions: interner.alloc_slice_copy(&[add_extern]),
        static_sized_arrays: &[],
        runtime_sized_arrays: &[],
        export_name_to_function: interner.alloc(interner.alloc_index_map()),
        export_name_to_kind: interner.alloc(interner.alloc_index_map()),
        prototype_to_extern: interner.alloc(builtin_prototype_to_extern),
        kind_to_extern: interner.alloc(interner.alloc_index_map()),
    };

    let test_export_name_to_function = interner.alloc_index_map_from_iter(once((main_str, main_prototype)));
    let test_package = PackageH {
        interfaces: &[],
        structs: &[],
        functions: interner.alloc_slice_copy(&[main]),
        static_sized_arrays: &[],
        runtime_sized_arrays: &[],
        export_name_to_function: interner.alloc(test_export_name_to_function),
        export_name_to_kind: interner.alloc(interner.alloc_index_map()),
        prototype_to_extern: interner.alloc(interner.alloc_index_map()),
        kind_to_extern: interner.alloc(interner.alloc_index_map()),
    };

    let mut packages = PackageCoordinateMap::new();
    packages.put(builtin_pkg_ref, builtin_package);
    packages.put(test_tld_ref, test_package);
    let program_h: &ProgramH = hammer_bump.alloc(ProgramH { packages });

    let mut stdout = stdout();
    let result = execute_with_primitive_args(
        program_h,
        &interner,
        &scout_arena,
        &[],
        &mut stdout,
        &vivem_bump,
        &empty_stdin,
        &null_stdout,
    );
    match result {
        Ok(IVonData::Int(VonInt { value: 159 })) => {}
        other => panic!("expected VonInt(159), got {:?}", other),
    }
}

