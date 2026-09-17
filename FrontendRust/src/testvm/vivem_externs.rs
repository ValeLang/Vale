use crate::testvm::values::ReferenceV;
use crate::testvm::heap::AdapterForExternsV;
use crate::final_ast::ast::PrototypeH;
use crate::final_ast::types::CoordH;
use crate::final_ast::types::KindHT;
use crate::final_ast::types::LocationH;
use crate::final_ast::types::OwnershipH;
use crate::testvm::values::BoolV;
use crate::testvm::values::FloatV;
use crate::testvm::values::IntV;
use crate::testvm::values::KindV;
use crate::testvm::values::StrV;
use crate::testvm::vivem::PanicExceptionV;
use crate::testvm::vivem::VmRuntimeErrorV;
use std::marker::PhantomData;

pub fn panic<'v, 'h, 's>(memory: &mut AdapterForExternsV<'_, 'v, 'h, 's>, args: &'v [ReferenceV<'v, 'h, 's>]) -> Result<ReferenceV<'v, 'h, 's>, VmRuntimeErrorV<'s>> where 's: 'h, 'h: 'v, {
    assert_eq!(args.len(), 0);
    Err(VmRuntimeErrorV::PanicException(PanicExceptionV))
}

pub fn add_float_float<'v, 'h, 's>(memory: &mut AdapterForExternsV<'_, 'v, 'h, 's>, args: &'v [ReferenceV<'v, 'h, 's>]) -> Result<ReferenceV<'v, 'h, 's>, VmRuntimeErrorV<'s>> where 's: 'h, 'h: 'v, {
    assert_eq!(args.len(), 2);
    let a_kind = memory.dereference(args[0]);
    let b_kind = memory.dereference(args[1]);
    Ok(match (a_kind, b_kind) {
        (KindV::Float(FloatV { value: a_value, .. }), KindV::Float(FloatV { value: b_value, .. })) => {
            memory.add_allocation_for_return(OwnershipH::MutableShareH, LocationH::InlineH, KindV::Float(FloatV { value: a_value + b_value, _phantom: PhantomData }))
        }
        _ => panic!("add_float_float: non-FloatV args"),
    })
}

pub fn multiply_float_float<'v, 'h, 's>(memory: &mut AdapterForExternsV<'_, 'v, 'h, 's>, args: &'v [ReferenceV<'v, 'h, 's>]) -> Result<ReferenceV<'v, 'h, 's>, VmRuntimeErrorV<'s>> where 's: 'h, 'h: 'v, {
    assert_eq!(args.len(), 2);
    let a_kind = memory.dereference(args[0]);
    let b_kind = memory.dereference(args[1]);
    Ok(match (a_kind, b_kind) {
        (KindV::Float(FloatV { value: a_value, .. }), KindV::Float(FloatV { value: b_value, .. })) => {
            memory.add_allocation_for_return(OwnershipH::MutableShareH, LocationH::InlineH, KindV::Float(FloatV { value: a_value * b_value, _phantom: PhantomData }))
        }
        _ => panic!("multiply_float_float: non-FloatV args"),
    })
}

pub fn divide_float_float<'v, 'h, 's>(memory: &mut AdapterForExternsV<'_, 'v, 'h, 's>, args: &'v [ReferenceV<'v, 'h, 's>]) -> Result<ReferenceV<'v, 'h, 's>, VmRuntimeErrorV<'s>> where 's: 'h, 'h: 'v, {
    assert_eq!(args.len(), 2);
    let a_kind = memory.dereference(args[0]);
    let b_kind = memory.dereference(args[1]);
    Ok(match (a_kind, b_kind) {
        (KindV::Float(FloatV { value: a_value, .. }), KindV::Float(FloatV { value: b_value, .. })) => {
            memory.add_allocation_for_return(OwnershipH::MutableShareH, LocationH::InlineH, KindV::Float(FloatV { value: a_value / b_value, _phantom: PhantomData }))
        }
        _ => panic!("divide_float_float: non-FloatV args"),
    })
}

pub fn subtract_float_float<'v, 'h, 's>(memory: &mut AdapterForExternsV<'_, 'v, 'h, 's>, args: &'v [ReferenceV<'v, 'h, 's>]) -> Result<ReferenceV<'v, 'h, 's>, VmRuntimeErrorV<'s>> where 's: 'h, 'h: 'v, {
    assert_eq!(args.len(), 2);
    let a_kind = memory.dereference(args[0]);
    let b_kind = memory.dereference(args[1]);
    Ok(match (a_kind, b_kind) {
        (KindV::Float(FloatV { value: a_value, .. }), KindV::Float(FloatV { value: b_value, .. })) => {
            memory.add_allocation_for_return(OwnershipH::MutableShareH, LocationH::InlineH, KindV::Float(FloatV { value: a_value - b_value, _phantom: PhantomData }))
        }
        _ => panic!("subtract_float_float: non-FloatV args"),
    })
}

pub fn add_str_str<'v, 'h, 's>(memory: &mut AdapterForExternsV<'_, 'v, 'h, 's>, args: &'v [ReferenceV<'v, 'h, 's>]) -> Result<ReferenceV<'v, 'h, 's>, VmRuntimeErrorV<'s>> where 's: 'h, 'h: 'v, {
    assert_eq!(args.len(), 6);
    let a_str = match memory.dereference(args[0]) { KindV::Str(StrV { value, .. }) => value, _ => panic!("add_str_str: arg 0 not StrV") };
    let a_begin = match memory.dereference(args[1]) { KindV::Int(IntV { value, bits: 32, .. }) => value, _ => panic!("add_str_str: arg 1 not IntV(_, 32)") };
    let a_length = match memory.dereference(args[2]) { KindV::Int(IntV { value, bits: 32, .. }) => value, _ => panic!("add_str_str: arg 2 not IntV(_, 32)") };
    let b_str = match memory.dereference(args[3]) { KindV::Str(StrV { value, .. }) => value, _ => panic!("add_str_str: arg 3 not StrV") };
    let b_begin = match memory.dereference(args[4]) { KindV::Int(IntV { value, bits: 32, .. }) => value, _ => panic!("add_str_str: arg 4 not IntV(_, 32)") };
    let b_length = match memory.dereference(args[5]) { KindV::Int(IntV { value, bits: 32, .. }) => value, _ => panic!("add_str_str: arg 5 not IntV(_, 32)") };
    let a_slice = &a_str.0[a_begin as usize .. (a_begin as i32 + a_length as i32) as usize];
    let b_slice = &b_str.0[b_begin as usize .. (b_begin as i32 + b_length as i32) as usize];
    let concat = format!("{}{}", a_slice, b_slice);
    let interned = memory.scout_arena.intern_str(&concat);
    Ok(memory.add_allocation_for_return(OwnershipH::MutableShareH, LocationH::YonderH, KindV::Str(StrV { value: interned, _phantom: PhantomData })))
}

pub fn getch<'v, 'h, 's>(memory: &mut AdapterForExternsV<'_, 'v, 'h, 's>, args: &'v [ReferenceV<'v, 'h, 's>]) -> Result<ReferenceV<'v, 'h, 's>, VmRuntimeErrorV<'s>> where 's: 'h, 'h: 'v, {
    assert!(args.is_empty());
    let next = (memory.stdin)();
    let code = if next.0.is_empty() { 0i64 } else { next.0.chars().next().unwrap() as i64 };
    Ok(memory.add_allocation_for_return(OwnershipH::MutableShareH, LocationH::InlineH, KindV::Int(IntV { value: code, bits: 32, _phantom: PhantomData })))
}

pub fn less_than_float<'v, 'h, 's>(memory: &mut AdapterForExternsV<'_, 'v, 'h, 's>, args: &'v [ReferenceV<'v, 'h, 's>]) -> Result<ReferenceV<'v, 'h, 's>, VmRuntimeErrorV<'s>> where 's: 'h, 'h: 'v, {
    assert_eq!(args.len(), 2);
    let a_kind = memory.dereference(args[0]);
    let b_kind = memory.dereference(args[1]);
    Ok(match (a_kind, b_kind) {
        (KindV::Float(FloatV { value: a_value, .. }), KindV::Float(FloatV { value: b_value, .. })) => {
            memory.add_allocation_for_return(OwnershipH::MutableShareH, LocationH::InlineH, KindV::Bool(BoolV { value: a_value < b_value, _phantom: PhantomData }))
        }
        _ => panic!("less_than_float: non-FloatV args"),
    })
}

pub fn greater_than_float<'v, 'h, 's>(memory: &mut AdapterForExternsV<'_, 'v, 'h, 's>, args: &'v [ReferenceV<'v, 'h, 's>]) -> Result<ReferenceV<'v, 'h, 's>, VmRuntimeErrorV<'s>> where 's: 'h, 'h: 'v, { panic!("Unimplemented: greater_than_float"); }

pub fn eq_float_float<'v, 'h, 's>(memory: &mut AdapterForExternsV<'_, 'v, 'h, 's>, args: &'v [ReferenceV<'v, 'h, 's>]) -> Result<ReferenceV<'v, 'h, 's>, VmRuntimeErrorV<'s>> where 's: 'h, 'h: 'v, {
    assert_eq!(args.len(), 2);
    let a_kind = memory.dereference(args[0]);
    let b_kind = memory.dereference(args[1]);
    Ok(match (a_kind, b_kind) {
        (KindV::Float(FloatV { value: a_value, .. }), KindV::Float(FloatV { value: b_value, .. })) => {
            memory.add_allocation_for_return(OwnershipH::MutableShareH, LocationH::InlineH, KindV::Bool(BoolV { value: a_value == b_value, _phantom: PhantomData }))
        }
        _ => panic!("eq_float_float: non-FloatV args"),
    })
}

pub fn eq_str_str<'v, 'h, 's>(memory: &mut AdapterForExternsV<'_, 'v, 'h, 's>, args: &'v [ReferenceV<'v, 'h, 's>]) -> Result<ReferenceV<'v, 'h, 's>, VmRuntimeErrorV<'s>> where 's: 'h, 'h: 'v, {
    assert_eq!(args.len(), 6);
    let left_str = match memory.dereference(args[0]) { KindV::Str(StrV { value, .. }) => value, _ => panic!("eq_str_str: arg 0 not StrV") };
    let left_str_start = match memory.dereference(args[1]) { KindV::Int(IntV { value, bits: 32, .. }) => value, _ => panic!("eq_str_str: arg 1 not IntV(_, 32)") };
    let left_str_len = match memory.dereference(args[2]) { KindV::Int(IntV { value, bits: 32, .. }) => value, _ => panic!("eq_str_str: arg 2 not IntV(_, 32)") };
    let right_str = match memory.dereference(args[3]) { KindV::Str(StrV { value, .. }) => value, _ => panic!("eq_str_str: arg 3 not StrV") };
    let right_str_start = match memory.dereference(args[4]) { KindV::Int(IntV { value, bits: 32, .. }) => value, _ => panic!("eq_str_str: arg 4 not IntV(_, 32)") };
    let right_str_len = match memory.dereference(args[5]) { KindV::Int(IntV { value, bits: 32, .. }) => value, _ => panic!("eq_str_str: arg 5 not IntV(_, 32)") };
    // BUG: Scala uses .slice(start, len) but Scala's slice takes (from, until), so the
    // "len" arg is being misinterpreted as an end index. Mirroring Scala parity-faithfully.
    let result_eq = &left_str.0[left_str_start as usize .. left_str_len as usize] == &right_str.0[right_str_start as usize .. right_str_len as usize];
    Ok(memory.add_allocation_for_return(OwnershipH::MutableShareH, LocationH::InlineH, KindV::Bool(BoolV { value: result_eq, _phantom: PhantomData })))
}

pub fn eq_bool_bool<'v, 'h, 's>(memory: &mut AdapterForExternsV<'_, 'v, 'h, 's>, args: &'v [ReferenceV<'v, 'h, 's>]) -> Result<ReferenceV<'v, 'h, 's>, VmRuntimeErrorV<'s>> where 's: 'h, 'h: 'v, {
    assert_eq!(args.len(), 2);
    let a_kind = memory.dereference(args[0]);
    let b_kind = memory.dereference(args[1]);
    Ok(match (a_kind, b_kind) {
        (KindV::Bool(BoolV { value: a_value, .. }), KindV::Bool(BoolV { value: b_value, .. })) => {
            memory.add_allocation_for_return(OwnershipH::MutableShareH, LocationH::InlineH, KindV::Bool(BoolV { value: a_value == b_value, _phantom: PhantomData }))
        }
        _ => panic!("eq_bool_bool: non-BoolV args"),
    })
}

pub fn and<'v, 'h, 's>(memory: &mut AdapterForExternsV<'_, 'v, 'h, 's>, args: &'v [ReferenceV<'v, 'h, 's>]) -> Result<ReferenceV<'v, 'h, 's>, VmRuntimeErrorV<'s>> where 's: 'h, 'h: 'v, { panic!("Unimplemented: and"); }

pub fn or<'v, 'h, 's>(memory: &mut AdapterForExternsV<'_, 'v, 'h, 's>, args: &'v [ReferenceV<'v, 'h, 's>]) -> Result<ReferenceV<'v, 'h, 's>, VmRuntimeErrorV<'s>> where 's: 'h, 'h: 'v, { panic!("Unimplemented: or"); }

pub fn not<'v, 'h, 's>(memory: &mut AdapterForExternsV<'_, 'v, 'h, 's>, args: &'v [ReferenceV<'v, 'h, 's>]) -> Result<ReferenceV<'v, 'h, 's>, VmRuntimeErrorV<'s>> where 's: 'h, 'h: 'v, {
    assert_eq!(args.len(), 1);
    let value = match memory.dereference(args[0]) {
        KindV::Bool(BoolV { value, .. }) => value,
        _ => panic!("not: non-BoolV arg"),
    };
    Ok(memory.add_allocation_for_return(OwnershipH::MutableShareH, LocationH::InlineH, KindV::Bool(BoolV { value: !value, _phantom: PhantomData })))
}

pub fn sqrt<'v, 'h, 's>(memory: &mut AdapterForExternsV<'_, 'v, 'h, 's>, args: &'v [ReferenceV<'v, 'h, 's>]) -> Result<ReferenceV<'v, 'h, 's>, VmRuntimeErrorV<'s>> where 's: 'h, 'h: 'v, {
    assert_eq!(args.len(), 1);
    let value = match memory.dereference(args[0]) {
        KindV::Float(FloatV { value, .. }) => value,
        _ => panic!("sqrt: non-FloatV arg"),
    };
    Ok(memory.add_allocation_for_return(OwnershipH::MutableShareH, LocationH::InlineH, KindV::Float(FloatV { value: value.sqrt(), _phantom: PhantomData })))
}

pub fn str_length<'v, 'h, 's>(memory: &mut AdapterForExternsV<'_, 'v, 'h, 's>, args: &'v [ReferenceV<'v, 'h, 's>]) -> Result<ReferenceV<'v, 'h, 's>, VmRuntimeErrorV<'s>> where 's: 'h, 'h: 'v, {
    assert_eq!(args.len(), 1);
    let value = match memory.dereference(args[0]) {
        KindV::Str(StrV { value, .. }) => value,
        _ => panic!("str_length: non-StrV arg"),
    };
    Ok(memory.add_allocation_for_return(OwnershipH::MutableShareH, LocationH::InlineH, KindV::Int(IntV { value: value.0.len() as i64, bits: 32, _phantom: PhantomData })))
}

pub fn cast_float_str<'v, 'h, 's>(memory: &mut AdapterForExternsV<'_, 'v, 'h, 's>, args: &'v [ReferenceV<'v, 'h, 's>]) -> Result<ReferenceV<'v, 'h, 's>, VmRuntimeErrorV<'s>> where 's: 'h, 'h: 'v, {
    assert_eq!(args.len(), 1);
    let value = match memory.dereference(args[0]) {
        KindV::Float(FloatV { value, .. }) => value,
        _ => panic!("cast_float_str: non-FloatV arg"),
    };
    let interned = memory.scout_arena.intern_str(&value.to_string());
    Ok(memory.add_allocation_for_return(OwnershipH::MutableShareH, LocationH::YonderH, KindV::Str(StrV { value: interned, _phantom: PhantomData })))
}

pub fn negate_float<'v, 'h, 's>(memory: &mut AdapterForExternsV<'_, 'v, 'h, 's>, args: &'v [ReferenceV<'v, 'h, 's>]) -> Result<ReferenceV<'v, 'h, 's>, VmRuntimeErrorV<'s>> where 's: 'h, 'h: 'v, {
    assert_eq!(args.len(), 1);
    let value = match memory.dereference(args[0]) {
        KindV::Float(FloatV { value, .. }) => value,
        _ => panic!("negate_float: non-FloatV arg"),
    };
    Ok(memory.add_allocation_for_return(OwnershipH::MutableShareH, LocationH::InlineH, KindV::Float(FloatV { value: -value, _phantom: PhantomData })))
}

pub fn print<'v, 'h, 's>(memory: &mut AdapterForExternsV<'_, 'v, 'h, 's>, args: &'v [ReferenceV<'v, 'h, 's>]) -> Result<ReferenceV<'v, 'h, 's>, VmRuntimeErrorV<'s>> where 's: 'h, 'h: 'v, {
    assert_eq!(args.len(), 3);
    let a_str = match memory.dereference(args[0]) {
        KindV::Str(StrV { value, .. }) => value,
        _ => panic!("print: arg 0 not StrV"),
    };
    let a_begin = match memory.dereference(args[1]) {
        KindV::Int(IntV { value, bits: 32, .. }) => value,
        _ => panic!("print: arg 1 not IntV(_, 32)"),
    };
    let a_length = match memory.dereference(args[2]) {
        KindV::Int(IntV { value, bits: 32, .. }) => value,
        _ => panic!("print: arg 2 not IntV(_, 32)"),
    };
    let substring = &a_str.0[a_begin as usize .. (a_begin as i32 + a_length as i32) as usize];
    let substring_interned = memory.scout_arena.intern_str(substring);
    (memory.stdout)(substring_interned);
    Ok(memory.make_void())
}

pub fn add_i32<'v, 'h, 's>(memory: &mut AdapterForExternsV<'_, 'v, 'h, 's>, args: &'v [ReferenceV<'v, 'h, 's>]) -> Result<ReferenceV<'v, 'h, 's>, VmRuntimeErrorV<'s>> where 's: 'h, 'h: 'v, {
    assert_eq!(args.len(), 2);
    let a_kind = memory.dereference(args[0]);
    let b_kind = memory.dereference(args[1]);
    Ok(match (a_kind, b_kind) {
        (KindV::Int(IntV { value: a_value, bits: 32, .. }), KindV::Int(IntV { value: b_value, bits: 32, .. })) => {
            memory.add_allocation_for_return(OwnershipH::MutableShareH, LocationH::InlineH, KindV::Int(IntV { value: (a_value as i32 + b_value as i32) as i64, bits: 32, _phantom: PhantomData }))
        }
        _ => panic!("add_i32: non-IntV(_, 32) args"),
    })
}

pub fn multiply_i32<'v, 'h, 's>(memory: &mut AdapterForExternsV<'_, 'v, 'h, 's>, args: &'v [ReferenceV<'v, 'h, 's>]) -> Result<ReferenceV<'v, 'h, 's>, VmRuntimeErrorV<'s>> where 's: 'h, 'h: 'v, {
    assert_eq!(args.len(), 2);
    let a_kind = memory.dereference(args[0]);
    let b_kind = memory.dereference(args[1]);
    Ok(match (a_kind, b_kind) {
        (KindV::Int(IntV { value: a_value, bits: 32, .. }), KindV::Int(IntV { value: b_value, bits: 32, .. })) => {
            memory.add_allocation_for_return(OwnershipH::MutableShareH, LocationH::InlineH, KindV::Int(IntV { value: (a_value as i32).wrapping_mul(b_value as i32) as i64, bits: 32, _phantom: PhantomData }))
        }
        _ => panic!("multiply_i32: non-IntV(_, 32) args"),
    })
}

pub fn divide_i32<'v, 'h, 's>(memory: &mut AdapterForExternsV<'_, 'v, 'h, 's>, args: &'v [ReferenceV<'v, 'h, 's>]) -> Result<ReferenceV<'v, 'h, 's>, VmRuntimeErrorV<'s>> where 's: 'h, 'h: 'v, {
    assert_eq!(args.len(), 2);
    let a_kind = memory.dereference(args[0]);
    let b_kind = memory.dereference(args[1]);
    Ok(match (a_kind, b_kind) {
        (KindV::Int(IntV { value: a_value, bits: 32, .. }), KindV::Int(IntV { value: b_value, bits: 32, .. })) => {
            memory.add_allocation_for_return(OwnershipH::MutableShareH, LocationH::InlineH, KindV::Int(IntV { value: (a_value as i32).wrapping_div(b_value as i32) as i64, bits: 32, _phantom: PhantomData }))
        }
        _ => panic!("divide_i32: non-IntV(_, 32) args"),
    })
}

pub fn mod_i32<'v, 'h, 's>(memory: &mut AdapterForExternsV<'_, 'v, 'h, 's>, args: &'v [ReferenceV<'v, 'h, 's>]) -> Result<ReferenceV<'v, 'h, 's>, VmRuntimeErrorV<'s>> where 's: 'h, 'h: 'v, {
    assert_eq!(args.len(), 2);
    let a_kind = memory.dereference(args[0]);
    let b_kind = memory.dereference(args[1]);
    Ok(match (a_kind, b_kind) {
        (KindV::Int(IntV { value: a_value, bits: 32, .. }), KindV::Int(IntV { value: b_value, bits: 32, .. })) => {
            memory.add_allocation_for_return(OwnershipH::MutableShareH, LocationH::InlineH, KindV::Int(IntV { value: ((a_value as i32) % (b_value as i32)) as i64, bits: 32, _phantom: PhantomData }))
        }
        _ => panic!("mod_i32: non-IntV(_, 32) args"),
    })
}

pub fn subtract_i32<'v, 'h, 's>(memory: &mut AdapterForExternsV<'_, 'v, 'h, 's>, args: &'v [ReferenceV<'v, 'h, 's>]) -> Result<ReferenceV<'v, 'h, 's>, VmRuntimeErrorV<'s>> where 's: 'h, 'h: 'v, {
    assert_eq!(args.len(), 2);
    let a_kind = memory.dereference(args[0]);
    let b_kind = memory.dereference(args[1]);
    Ok(match (a_kind, b_kind) {
        (KindV::Int(IntV { value: a_value, bits: 32, .. }), KindV::Int(IntV { value: b_value, bits: 32, .. })) => {
            memory.add_allocation_for_return(OwnershipH::MutableShareH, LocationH::InlineH, KindV::Int(IntV { value: (a_value as i32).wrapping_sub(b_value as i32) as i64, bits: 32, _phantom: PhantomData }))
        }
        _ => panic!("subtract_i32: non-IntV(_, 32) args"),
    })
}

pub fn less_than_i32<'v, 'h, 's>(memory: &mut AdapterForExternsV<'_, 'v, 'h, 's>, args: &'v [ReferenceV<'v, 'h, 's>]) -> Result<ReferenceV<'v, 'h, 's>, VmRuntimeErrorV<'s>> where 's: 'h, 'h: 'v, {
    assert_eq!(args.len(), 2);
    let a_kind = memory.dereference(args[0]);
    let b_kind = memory.dereference(args[1]);
    Ok(match (a_kind, b_kind) {
        (KindV::Int(IntV { value: a_value, bits: 32, .. }), KindV::Int(IntV { value: b_value, bits: 32, .. })) => {
            memory.add_allocation_for_return(OwnershipH::MutableShareH, LocationH::InlineH, KindV::Bool(BoolV { value: a_value < b_value, _phantom: PhantomData }))
        }
        _ => panic!("less_than_i32: non-IntV(_, 32) args"),
    })
}

pub fn less_than_or_eq_i32<'v, 'h, 's>(memory: &mut AdapterForExternsV<'_, 'v, 'h, 's>, args: &'v [ReferenceV<'v, 'h, 's>]) -> Result<ReferenceV<'v, 'h, 's>, VmRuntimeErrorV<'s>> where 's: 'h, 'h: 'v, {
    assert_eq!(args.len(), 2);
    let a_kind = memory.dereference(args[0]);
    let b_kind = memory.dereference(args[1]);
    Ok(match (a_kind, b_kind) {
        (KindV::Int(IntV { value: a_value, bits: 32, .. }), KindV::Int(IntV { value: b_value, bits: 32, .. })) => {
            memory.add_allocation_for_return(OwnershipH::MutableShareH, LocationH::InlineH, KindV::Bool(BoolV { value: a_value <= b_value, _phantom: PhantomData }))
        }
        _ => panic!("less_than_or_eq_i32: non-IntV(_, 32) args"),
    })
}

pub fn greater_than_i32<'v, 'h, 's>(memory: &mut AdapterForExternsV<'_, 'v, 'h, 's>, args: &'v [ReferenceV<'v, 'h, 's>]) -> Result<ReferenceV<'v, 'h, 's>, VmRuntimeErrorV<'s>> where 's: 'h, 'h: 'v, {
    assert_eq!(args.len(), 2);
    let a_kind = memory.dereference(args[0]);
    let b_kind = memory.dereference(args[1]);
    Ok(match (a_kind, b_kind) {
        (KindV::Int(IntV { value: a_value, bits: 32, .. }), KindV::Int(IntV { value: b_value, bits: 32, .. })) => {
            memory.add_allocation_for_return(OwnershipH::MutableShareH, LocationH::InlineH, KindV::Bool(BoolV { value: a_value > b_value, _phantom: PhantomData }))
        }
        _ => panic!("greater_than_i32: non-IntV(_, 32) args"),
    })
}

pub fn greater_than_or_eq_i32<'v, 'h, 's>(memory: &mut AdapterForExternsV<'_, 'v, 'h, 's>, args: &'v [ReferenceV<'v, 'h, 's>]) -> Result<ReferenceV<'v, 'h, 's>, VmRuntimeErrorV<'s>> where 's: 'h, 'h: 'v, {
    assert_eq!(args.len(), 2);
    let a_kind = memory.dereference(args[0]);
    let b_kind = memory.dereference(args[1]);
    Ok(match (a_kind, b_kind) {
        (KindV::Int(IntV { value: a_value, bits: 32, .. }), KindV::Int(IntV { value: b_value, bits: 32, .. })) => {
            memory.add_allocation_for_return(OwnershipH::MutableShareH, LocationH::InlineH, KindV::Bool(BoolV { value: a_value >= b_value, _phantom: PhantomData }))
        }
        _ => panic!("greater_than_or_eq_i32: non-IntV(_, 32) args"),
    })
}

pub fn eq_i32<'v, 'h, 's>(memory: &mut AdapterForExternsV<'_, 'v, 'h, 's>, args: &'v [ReferenceV<'v, 'h, 's>]) -> Result<ReferenceV<'v, 'h, 's>, VmRuntimeErrorV<'s>> where 's: 'h, 'h: 'v, {
    assert_eq!(args.len(), 2);
    let a_kind = memory.dereference(args[0]);
    let b_kind = memory.dereference(args[1]);
    Ok(match (a_kind, b_kind) {
        (KindV::Int(IntV { value: a_value, bits: 32, .. }), KindV::Int(IntV { value: b_value, bits: 32, .. })) => {
            memory.add_allocation_for_return(OwnershipH::MutableShareH, LocationH::InlineH, KindV::Bool(BoolV { value: a_value == b_value, _phantom: PhantomData }))
        }
        _ => panic!("eq_i32: non-IntV(_, 32) args"),
    })
}

pub fn cast_i32_str<'v, 'h, 's>(memory: &mut AdapterForExternsV<'_, 'v, 'h, 's>, args: &'v [ReferenceV<'v, 'h, 's>]) -> Result<ReferenceV<'v, 'h, 's>, VmRuntimeErrorV<'s>> where 's: 'h, 'h: 'v, {
    assert_eq!(args.len(), 1);
    let value = match memory.dereference(args[0]) {
        KindV::Int(IntV { value, bits: 32, .. }) => value,
        _ => panic!("cast_i32_str: non-IntV(_, 32) arg"),
    };
    let interned = memory.scout_arena.intern_str(&value.to_string());
    Ok(memory.add_allocation_for_return(OwnershipH::MutableShareH, LocationH::YonderH, KindV::Str(StrV { value: interned, _phantom: PhantomData })))
}

pub fn cast_float_i32<'v, 'h, 's>(memory: &mut AdapterForExternsV<'_, 'v, 'h, 's>, args: &'v [ReferenceV<'v, 'h, 's>]) -> Result<ReferenceV<'v, 'h, 's>, VmRuntimeErrorV<'s>> where 's: 'h, 'h: 'v, {
    assert_eq!(args.len(), 1);
    let value = match memory.dereference(args[0]) {
        KindV::Float(FloatV { value, .. }) => value,
        _ => panic!("cast_float_i32: non-FloatV arg"),
    };
    Ok(memory.add_allocation_for_return(OwnershipH::MutableShareH, LocationH::InlineH, KindV::Int(IntV { value: value as i32 as i64, bits: 32, _phantom: PhantomData })))
}

pub fn cast_i32_float<'v, 'h, 's>(memory: &mut AdapterForExternsV<'_, 'v, 'h, 's>, args: &'v [ReferenceV<'v, 'h, 's>]) -> Result<ReferenceV<'v, 'h, 's>, VmRuntimeErrorV<'s>> where 's: 'h, 'h: 'v, {
    assert_eq!(args.len(), 1);
    let value = match memory.dereference(args[0]) {
        KindV::Int(IntV { value, bits: 32, .. }) => value,
        _ => panic!("cast_i32_float: non-IntV(_, 32) arg"),
    };
    Ok(memory.add_allocation_for_return(OwnershipH::MutableShareH, LocationH::InlineH, KindV::Float(FloatV { value: value as f64, _phantom: PhantomData })))
}

pub fn add_i64<'v, 'h, 's>(memory: &mut AdapterForExternsV<'_, 'v, 'h, 's>, args: &'v [ReferenceV<'v, 'h, 's>]) -> Result<ReferenceV<'v, 'h, 's>, VmRuntimeErrorV<'s>> where 's: 'h, 'h: 'v, { panic!("Unimplemented: add_i64"); }

pub fn multiply_i64<'v, 'h, 's>(memory: &mut AdapterForExternsV<'_, 'v, 'h, 's>, args: &'v [ReferenceV<'v, 'h, 's>]) -> Result<ReferenceV<'v, 'h, 's>, VmRuntimeErrorV<'s>> where 's: 'h, 'h: 'v, {
    assert_eq!(args.len(), 2);
    let a_kind = memory.dereference(args[0]);
    let b_kind = memory.dereference(args[1]);
    Ok(match (a_kind, b_kind) {
        (KindV::Int(IntV { value: a_value, bits: 64, .. }), KindV::Int(IntV { value: b_value, bits: 64, .. })) => {
            memory.add_allocation_for_return(OwnershipH::MutableShareH, LocationH::InlineH, KindV::Int(IntV { value: a_value.wrapping_mul(b_value), bits: 64, _phantom: PhantomData }))
        }
        _ => panic!("multiply_i64: non-IntV(_, 64) args"),
    })
}

pub fn divide_i64<'v, 'h, 's>(memory: &mut AdapterForExternsV<'_, 'v, 'h, 's>, args: &'v [ReferenceV<'v, 'h, 's>]) -> Result<ReferenceV<'v, 'h, 's>, VmRuntimeErrorV<'s>> where 's: 'h, 'h: 'v, {
    assert_eq!(args.len(), 2);
    let a_kind = memory.dereference(args[0]);
    let b_kind = memory.dereference(args[1]);
    Ok(match (a_kind, b_kind) {
        (KindV::Int(IntV { value: a_value, bits: 64, .. }), KindV::Int(IntV { value: b_value, bits: 64, .. })) => {
            memory.add_allocation_for_return(OwnershipH::MutableShareH, LocationH::InlineH, KindV::Int(IntV { value: a_value.wrapping_div(b_value), bits: 64, _phantom: PhantomData }))
        }
        _ => panic!("divide_i64: non-IntV(_, 64) args"),
    })
}

pub fn truncate_i64_to_i32<'v, 'h, 's>(memory: &mut AdapterForExternsV<'_, 'v, 'h, 's>, args: &'v [ReferenceV<'v, 'h, 's>]) -> Result<ReferenceV<'v, 'h, 's>, VmRuntimeErrorV<'s>> where 's: 'h, 'h: 'v, {
    assert_eq!(args.len(), 1);
    let value = match memory.dereference(args[0]) {
        KindV::Int(IntV { value, bits: 64, .. }) => value,
        _ => panic!("truncate_i64_to_i32: non-IntV(_, 64) arg"),
    };
    let result = value & 0xFFFFFFFFi64;
    Ok(memory.add_allocation_for_return(OwnershipH::MutableShareH, LocationH::InlineH, KindV::Int(IntV { value: result, bits: 32, _phantom: PhantomData })))
}

pub fn mod_i64<'v, 'h, 's>(memory: &mut AdapterForExternsV<'_, 'v, 'h, 's>, args: &'v [ReferenceV<'v, 'h, 's>]) -> Result<ReferenceV<'v, 'h, 's>, VmRuntimeErrorV<'s>> where 's: 'h, 'h: 'v, { panic!("Unimplemented: mod_i64"); }

pub fn subtract_i64<'v, 'h, 's>(memory: &mut AdapterForExternsV<'_, 'v, 'h, 's>, args: &'v [ReferenceV<'v, 'h, 's>]) -> Result<ReferenceV<'v, 'h, 's>, VmRuntimeErrorV<'s>> where 's: 'h, 'h: 'v, {
    assert_eq!(args.len(), 2);
    let a_kind = memory.dereference(args[0]);
    let b_kind = memory.dereference(args[1]);
    Ok(match (a_kind, b_kind) {
        (KindV::Int(IntV { value: a_value, bits: 64, .. }), KindV::Int(IntV { value: b_value, bits: 64, .. })) => {
            memory.add_allocation_for_return(OwnershipH::MutableShareH, LocationH::InlineH, KindV::Int(IntV { value: a_value.wrapping_sub(b_value), bits: 64, _phantom: PhantomData }))
        }
        _ => panic!("subtract_i64: non-IntV(_, 64) args"),
    })
}

pub fn less_than_i64<'v, 'h, 's>(memory: &mut AdapterForExternsV<'_, 'v, 'h, 's>, args: &'v [ReferenceV<'v, 'h, 's>]) -> Result<ReferenceV<'v, 'h, 's>, VmRuntimeErrorV<'s>> where 's: 'h, 'h: 'v, { panic!("Unimplemented: less_than_i64"); }

pub fn less_than_or_eq_i64<'v, 'h, 's>(memory: &mut AdapterForExternsV<'_, 'v, 'h, 's>, args: &'v [ReferenceV<'v, 'h, 's>]) -> Result<ReferenceV<'v, 'h, 's>, VmRuntimeErrorV<'s>> where 's: 'h, 'h: 'v, { panic!("Unimplemented: less_than_or_eq_i64"); }

pub fn greater_than_i64<'v, 'h, 's>(memory: &mut AdapterForExternsV<'_, 'v, 'h, 's>, args: &'v [ReferenceV<'v, 'h, 's>]) -> Result<ReferenceV<'v, 'h, 's>, VmRuntimeErrorV<'s>> where 's: 'h, 'h: 'v, { panic!("Unimplemented: greater_than_i64"); }

pub fn greater_than_or_eq_i64<'v, 'h, 's>(memory: &mut AdapterForExternsV<'_, 'v, 'h, 's>, args: &'v [ReferenceV<'v, 'h, 's>]) -> Result<ReferenceV<'v, 'h, 's>, VmRuntimeErrorV<'s>> where 's: 'h, 'h: 'v, { panic!("Unimplemented: greater_than_or_eq_i64"); }

pub fn eq_i64<'v, 'h, 's>(memory: &mut AdapterForExternsV<'_, 'v, 'h, 's>, args: &'v [ReferenceV<'v, 'h, 's>]) -> Result<ReferenceV<'v, 'h, 's>, VmRuntimeErrorV<'s>> where 's: 'h, 'h: 'v, { panic!("Unimplemented: eq_i64"); }

pub fn cast_i64_str<'v, 'h, 's>(memory: &mut AdapterForExternsV<'_, 'v, 'h, 's>, args: &'v [ReferenceV<'v, 'h, 's>]) -> Result<ReferenceV<'v, 'h, 's>, VmRuntimeErrorV<'s>> where 's: 'h, 'h: 'v, {
    assert_eq!(args.len(), 1);
    let value = match memory.dereference(args[0]) {
        KindV::Int(IntV { value, bits: 64, .. }) => value,
        _ => panic!("cast_i64_str: non-IntV(_, 64) arg"),
    };
    let interned = memory.scout_arena.intern_str(&value.to_string());
    Ok(memory.add_allocation_for_return(OwnershipH::MutableShareH, LocationH::YonderH, KindV::Str(StrV { value: interned, _phantom: PhantomData })))
}

pub fn cast_float_i64<'v, 'h, 's>(memory: &mut AdapterForExternsV<'_, 'v, 'h, 's>, args: &'v [ReferenceV<'v, 'h, 's>]) -> Result<ReferenceV<'v, 'h, 's>, VmRuntimeErrorV<'s>> where 's: 'h, 'h: 'v, { panic!("Unimplemented: cast_float_i64"); }

pub fn cast_i64_float<'v, 'h, 's>(memory: &mut AdapterForExternsV<'_, 'v, 'h, 's>, args: &'v [ReferenceV<'v, 'h, 's>]) -> Result<ReferenceV<'v, 'h, 's>, VmRuntimeErrorV<'s>> where 's: 'h, 'h: 'v, { panic!("Unimplemented: cast_i64_float"); }

pub fn new_vec<'v, 'h, 's>(memory: &mut AdapterForExternsV<'_, 'v, 'h, 's>, prototype: &PrototypeH<'s, 'h>, args: &'v [ReferenceV<'v, 'h, 's>]) -> Result<ReferenceV<'v, 'h, 's>, VmRuntimeErrorV<'s>> where 's: 'h, 'h: 'v, {
    assert!(args.len() == 0);
    let opaque_coord = match prototype.return_type {
        CoordH { ownership: own, location: loc, kind: KindHT::OpaqueHT(s) } => CoordH { ownership: own, location: loc, kind: KindHT::OpaqueHT(s) },
        _ => panic!(),
    };
    Ok(memory.new_opaque(opaque_coord))
}

pub fn new_vec_with_capacity<'v, 'h, 's>(memory: &mut AdapterForExternsV<'_, 'v, 'h, 's>, prototype: &PrototypeH<'s, 'h>, args: &'v [ReferenceV<'v, 'h, 's>]) -> Result<ReferenceV<'v, 'h, 's>, VmRuntimeErrorV<'s>> where 's: 'h, 'h: 'v, {
    assert!(args.len() == 1);
    // This whole function only exists for testing purposes
    match memory.dereference(args[0]) {
        KindV::Int(IntV { value: 42, bits: 64, .. }) => {}
        _ => panic!(),
    }
    let opaque_coord = match prototype.return_type {
        CoordH { ownership: own, location: loc, kind: KindHT::OpaqueHT(s) } => CoordH { ownership: own, location: loc, kind: KindHT::OpaqueHT(s) },
        _ => panic!(),
    };
    Ok(memory.new_opaque(opaque_coord))
}

pub fn vec_capacity<'v, 'h, 's>(memory: &mut AdapterForExternsV<'_, 'v, 'h, 's>, _prototype: &PrototypeH<'s, 'h>, args: &'v [ReferenceV<'v, 'h, 's>]) -> Result<ReferenceV<'v, 'h, 's>, VmRuntimeErrorV<'s>> where 's: 'h, 'h: 'v, {
    assert!(args.len() == 1);
    match memory.dereference(args[0]) {
        KindV::Opaque(_) => {}
        _ => panic!(),
    }
    // This whole function just exists for testing, there are some tests that feed 42 in to newVecWithCapacity
    Ok(memory.add_allocation_for_return(OwnershipH::MutableShareH, LocationH::InlineH, KindV::Int(IntV { value: 42, bits: 64, _phantom: PhantomData })))
}

