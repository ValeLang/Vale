use std::cell::Cell;
use std::collections::HashMap;
use std::marker::PhantomData;
use crate::interner::StrI;
use crate::final_ast::types::{KindHT, CoordH, LocationH, OwnershipH, StructHT, StaticSizedArrayHT, RuntimeSizedArrayHT, StaticSizedArrayDefinitionHT, RuntimeSizedArrayDefinitionHT};
use crate::final_ast::ast::{ProgramH, PrototypeH, StructDefinitionH};
use crate::final_ast::instructions::Local;
use crate::testvm::values::{
    AllocationIdV, AllocationV, ArrayInstanceV, CallIdV, ElementAddressV, ExpressionIdV,
    IObjectReferrerV, KindV, MemberAddressV, PrimitiveKindV, ReferenceRegisterV, ReferenceV, RegisterV,
    StructInstanceV, VariableAddressV, VariableV, VoidV,
};
use crate::testvm::call::CallV;
use crate::final_ast::types::OpaqueHT;
use crate::von::ast::IVonData;
use crate::testvm::vivem::PrintStream;
use crate::scout_arena::ScoutArena;
use crate::simplifying::hammer_interner::HammerInterner;
use crate::testvm::values::ArgumentIdV;
use crate::testvm::values::ArgumentToObjectReferrerV;
use crate::testvm::values::BoolV;
use crate::testvm::values::ElementToObjectReferrerV;
use crate::testvm::values::MemberToObjectReferrerV;
use crate::testvm::values::OpaqueV;
use crate::testvm::values::RRKindV;
use crate::testvm::values::RegisterToObjectReferrerV;
use crate::testvm::values::VariableToObjectReferrerV;
use crate::testvm::vivem::ConstraintViolatedExceptionV;
use crate::testvm::vivem::VmRuntimeErrorV;
use crate::von::ast::VonArray;
use crate::von::ast::VonBool;
use crate::von::ast::VonFloat;
use crate::von::ast::VonInt;
use crate::von::ast::VonMember;
use crate::von::ast::VonObject;
use crate::von::ast::VonStr;
use std::io::Write;

/// Temporary state
pub struct AdapterForExternsV<'a, 'v, 'h, 's>
where 's: 'h, 'h: 'v, 'v: 'a,
{
    pub program_h: &'h ProgramH<'s, 'h>,
    pub interner: &'a HammerInterner<'s, 'h>,
    pub scout_arena: &'a ScoutArena<'s>,
    pub heap: &'a mut HeapV<'v, 'h, 's>,
    pub call_id: CallIdV<'v, 'h, 's>,
    pub stdin: &'v dyn Fn() -> StrI<'s>,
    pub stdout: &'v dyn Fn(StrI<'s>),
}

impl<'a, 'v, 'h, 's> AdapterForExternsV<'a, 'v, 'h, 's> where 's: 'h, 'h: 'v, 'v: 'a {
    pub fn dereference(&self, reference: ReferenceV<'v, 'h, 's>) -> KindV<'v, 'h, 's> {
        self.heap.dereference(reference, false)
    }

    pub fn new_opaque(&mut self, opaque_ht: CoordH<'s, 'h>) -> ReferenceV<'v, 'h, 's> {
        self.heap.new_opaque(self.interner, opaque_ht)
    }

    pub fn add_allocation_for_return(&mut self, ownership: OwnershipH, location: LocationH, kind: KindV<'v, 'h, 's>) -> ReferenceV<'v, 'h, 's> {
        let r#ref = self.heap.add(self.interner, ownership, location, kind);
//    heap.incrementReferenceRefCount(ResultToObjectReferrer(callId), ref) // incrementing because putting it in a return
//    ReturnV(callId, ref)
        r#ref
    }

    pub fn make_void(&self) -> ReferenceV<'v, 'h, 's> {
        self.heap.void()
    }
}

// Per Scala `object AllocationMap.STARTING_ID`. The matching `addImpl` body in
// Rust is the free fn `allocation_map_add_impl` further down — its Scala
// counterpart sits in the audit block below at canonical source order; SCPX
// matches Scala's `object AllocationMap` placement before `class AllocationMap`.
const STARTING_ID: i32 = 501;

// 1:1 with Scala `object AllocationMap.addImpl` above. `&mut HashMap` + `&mut i32`
// mirror Scala's HashMap + IntCounter wrapper. `interner` is Exception-B (Rust's
// sealed StructHT requires explicit interner where Scala used GC + structural eq).
fn allocation_map_add_impl<'v, 'h, 's>(
    objects_by_id: &mut HashMap<AllocationIdV<'v, 'h, 's>, AllocationV<'v, 'h, 's>>,
    next_id: &mut i32,
    interner: &HammerInterner<'s, 'h>,
    ownership: OwnershipH,
    location: LocationH,
    kind: KindV<'v, 'h, 's>,
) -> ReferenceV<'v, 'h, 's> {
    let id = *next_id;
    *next_id = id + 1;
    if let KindV::Void(_) = kind {
        assert_eq!(id, STARTING_ID);
    }
    let reference = ReferenceV {
        actual_kind: kind.tyype(interner),
        seen_as_kind: kind.tyype(interner),
        ownership,
        location,
        num: id,
    };
    let allocation = AllocationV { reference, kind, referrers: HashMap::new() };
    objects_by_id.insert(reference.alloc_id(), allocation);
    reference
}

/// Temporary state
pub struct AllocationMapV<'v, 'h, 's> {
    pub objects_by_id: HashMap<AllocationIdV<'v, 'h, 's>, AllocationV<'v, 'h, 's>>,
    pub next_id: i32,
    pub void_ref: ReferenceV<'v, 'h, 's>,
}

// Mirrors the AllocationMap(vivemDout: PrintStream) constructor in the Scala
// audit block above: initializes objectsById/STARTING_ID/nextId, then
// `val void = add(MutableShareH, InlineH, VoidV)`.
impl<'v, 'h, 's> AllocationMapV<'v, 'h, 's> {
    pub fn new(interner: &HammerInterner<'s, 'h>) -> AllocationMapV<'v, 'h, 's> {
        let mut objects_by_id = HashMap::new();
        let mut next_id = STARTING_ID;
        let void_ref = allocation_map_add_impl(&mut objects_by_id, &mut next_id, interner, OwnershipH::MutableShareH, LocationH::InlineH, KindV::Void(VoidV));
        AllocationMapV { objects_by_id, next_id, void_ref }
    }

// (`def newId` removed in the refactor that introduced `IntCounter` —
// nextId.increment() is now called inline inside addImpl.)
    pub fn is_empty(&self) -> bool {
        panic!("Unimplemented: is_empty");
    }

    pub fn size(&self) -> usize {
        self.objects_by_id.len()
    }

    pub fn get(&self, interner: &HammerInterner<'s, 'h>, alloc_id: AllocationIdV<'v, 'h, 's>) -> &AllocationV<'v, 'h, 's> {
        let allocation = self.objects_by_id.get(&alloc_id).expect("get: not found");
        assert!(allocation.kind.tyype(interner) == alloc_id.tyype);
        allocation
    }

    pub fn remove(&self, alloc_id: AllocationIdV<'v, 'h, 's>) {
        panic!("Unimplemented: remove");
    }

    pub fn contains(&self, alloc_id: AllocationIdV<'v, 'h, 's>) -> bool {
        panic!("Unimplemented: contains");
    }

    pub fn add(&mut self, interner: &HammerInterner<'s, 'h>, ownership: OwnershipH, location: LocationH, kind: KindV<'v, 'h, 's>) -> ReferenceV<'v, 'h, 's> {
        allocation_map_add_impl(&mut self.objects_by_id, &mut self.next_id, interner, ownership, location, kind)
    }

    pub fn print_all(&self) {
        panic!("Unimplemented: print_all");
    }

    pub fn check_for_leaks(&self, vivem_dout: &mut PrintStream) {
        let m = &self.objects_by_id;
        let non_interned_objects: Vec<&AllocationV<'v, 'h, 's>> = m.values().filter(|a| !matches!(a.kind, KindV::Void(_))).collect();
        if !non_interned_objects.is_empty() {
            let mut ids: Vec<i32> = non_interned_objects.iter().map(|a| a.reference.alloc_id().num).collect();
            ids.sort();
            for obj_id in &ids {
                write!(vivem_dout, "o{} ", obj_id).unwrap();
            }
            writeln!(vivem_dout).unwrap();
            let mut sorted: Vec<&AllocationV<'v, 'h, 's>> = non_interned_objects.clone();
            sorted.sort_by_key(|a| a.reference.alloc_id().num);
            for a in &sorted {
                a.print_refs(vivem_dout);
            }
            panic!("Memory leaks! See above for ");
        }
        drop(non_interned_objects);
    }
}

/// Temporary state
pub struct HeapV<'v, 'h, 's> {
    pub vivem_dout: &'v mut PrintStream,
    pub vivem_bump: &'v bumpalo::Bump,
    pub objects_by_id: AllocationMapV<'v, 'h, 's>,
    pub call_id_stack: Vec<CallIdV<'v, 'h, 's>>,
    pub calls_by_id: HashMap<CallIdV<'v, 'h, 's>, CallV<'v, 'h, 's>>,
}

// Mirrors the Heap(in_vivemDout: PrintStream) constructor in the Scala audit
// block above: stores vivemDout, builds objectsById = new AllocationMap(vivemDout),
// initializes callIdStack/callsById, sets void = objectsById.void.
impl<'v, 'h, 's> HeapV<'v, 'h, 's> {
    pub fn new(interner: &HammerInterner<'s, 'h>, vivem_dout: &'v mut PrintStream, vivem_bump: &'v bumpalo::Bump) -> HeapV<'v, 'h, 's> {
        HeapV {
            objects_by_id: AllocationMapV::new(interner),
            call_id_stack: Vec::new(),
            calls_by_id: HashMap::new(),
            vivem_dout,
            vivem_bump,
        }
    }

    pub fn void(&self) -> ReferenceV<'v, 'h, 's> {
        self.objects_by_id.void_ref
    }
    pub fn add_local(&mut self, interner: &HammerInterner<'s, 'h>, var_addr: VariableAddressV<'v, 'h, 's>, reference: ReferenceV<'v, 'h, 's>, expected_type: CoordH<'s, 'h>) {
        self.check_reference(interner, expected_type, reference);
        self.get_current_call(var_addr.call_id, |call| {
            call.add_local(var_addr, reference, expected_type);
        });
        self.increment_reference_ref_count(
            IObjectReferrerV::VariableToObjectReferrer(VariableToObjectReferrerV { var_addr, ownership: expected_type.ownership }),
            reference,
        );
    }

    pub fn get_reference(&self, var_addr: VariableAddressV<'v, 'h, 's>, expected_type: CoordH<'s, 'h>) -> ReferenceV<'v, 'h, 's> {
        panic!("Unimplemented: get_reference");
    }

    pub fn remove_local(&mut self, interner: &HammerInterner<'s, 'h>, var_addr: VariableAddressV<'v, 'h, 's>, expected_type: CoordH<'s, 'h>) {
        let variable = self.get_local(var_addr);
        let actual_reference = variable.reference;
        self.check_reference(interner, expected_type, actual_reference);
        self.decrement_reference_ref_count(
            IObjectReferrerV::VariableToObjectReferrer(VariableToObjectReferrerV { var_addr, ownership: expected_type.ownership }),
            actual_reference,
        );
        self.get_current_call(var_addr.call_id, |call| {
            call.remove_local(var_addr);
        });
    }

    pub fn get_reference_from_local(&self, interner: &HammerInterner<'s, 'h>, var_addr: VariableAddressV<'v, 'h, 's>, expected_type: CoordH<'s, 'h>, target_type: CoordH<'s, 'h>) -> ReferenceV<'v, 'h, 's> {
        let variable = self.get_local(var_addr);
        if variable.expected_type != expected_type {
            panic!("blort");
        }
        let variable_reference = variable.reference;
        self.check_reference(interner, expected_type, variable_reference);
        self.transmute(variable_reference, expected_type, target_type)
    }

    pub fn get_local(&self, var_addr: VariableAddressV<'v, 'h, 's>) -> VariableV<'v, 'h, 's> {
        let calls_by_id = &self.calls_by_id;
        let call = calls_by_id.get(&var_addr.call_id).expect("get_local: call not found");
        call.get_local(var_addr)
    }

    pub fn mutate_variable(&mut self, interner: &HammerInterner<'s, 'h>, var_address: VariableAddressV<'v, 'h, 's>, reference: ReferenceV<'v, 'h, 's>, expected_type: CoordH<'s, 'h>) -> ReferenceV<'v, 'h, 's> {
        let variable = self.calls_by_id.get(&var_address.call_id).expect("mutate_variable: call not found").get_local(var_address);
        self.check_reference(interner, expected_type, reference);
        self.check_reference(interner, variable.expected_type, reference);
        let old_reference = variable.reference;
        self.decrement_reference_ref_count(
            IObjectReferrerV::VariableToObjectReferrer(VariableToObjectReferrerV { var_addr: var_address, ownership: expected_type.ownership }),
            old_reference);
        self.increment_reference_ref_count(
            IObjectReferrerV::VariableToObjectReferrer(VariableToObjectReferrerV { var_addr: var_address, ownership: expected_type.ownership }),
            reference);
        self.get_current_call(var_address.call_id, |c| c.mutate_local(var_address, reference, expected_type));
        old_reference
    }

    pub fn mutate_array(&mut self, element_address: ElementAddressV<'v, 'h, 's>, reference: ReferenceV<'v, 'h, 's>, expected_type: CoordH<'s, 'h>) -> ReferenceV<'v, 'h, 's> {
        let ElementAddressV { array_id: array_ref, element_index } = element_address;
        let allocation = self.objects_by_id.objects_by_id.get(&array_ref).expect("get: not found");
        match allocation.kind {
            KindV::ArrayInstance(ai) => {
                let old_reference = ai.get_element(element_index);
                self.decrement_reference_ref_count(IObjectReferrerV::ElementToObjectReferrer(ElementToObjectReferrerV { element_addr: element_address, ownership: expected_type.ownership }), old_reference);
                ai.set_element(self.vivem_bump, element_index, reference);
                self.increment_reference_ref_count(IObjectReferrerV::ElementToObjectReferrer(ElementToObjectReferrerV { element_addr: element_address, ownership: expected_type.ownership }), reference);
                old_reference
            }
            _ => panic!("mutate_array: not an ArrayInstance"),
        }
    }

    pub fn mutate_struct(&mut self, member_address: MemberAddressV<'v, 'h, 's>, reference: ReferenceV<'v, 'h, 's>, expected_type: CoordH<'s, 'h>) -> ReferenceV<'v, 'h, 's> {
        let MemberAddressV { struct_id: object_id, field_index } = member_address;
        let allocation = self.objects_by_id.objects_by_id.get(&object_id).expect("get: not found");
        match allocation.kind {
            KindV::StructInstance(si) => {
                let members = si.members.get().expect("StructInstance has no members");
                let old_member_reference = members[field_index as usize];
                self.decrement_reference_ref_count(IObjectReferrerV::MemberToObjectReferrer(MemberToObjectReferrerV { member_addr: member_address, ownership: expected_type.ownership }), old_member_reference);
                assert!(si.struct_h.members[field_index as usize].tyype == expected_type);
                si.get_reference_member(field_index);
                si.set_reference_member(self.vivem_bump, field_index, reference);
                self.increment_reference_ref_count(IObjectReferrerV::MemberToObjectReferrer(MemberToObjectReferrerV { member_addr: member_address, ownership: expected_type.ownership }), reference);
                old_member_reference
            }
            _ => panic!("mutate_struct: not a StructInstance"),
        }
    }

    pub fn get_reference_from_struct(&self, interner: &HammerInterner<'s, 'h>, address: MemberAddressV<'v, 'h, 's>, expected_type: CoordH<'s, 'h>, target_type: CoordH<'s, 'h>) -> ReferenceV<'v, 'h, 's> {
        let MemberAddressV { struct_id: object_id, field_index } = address;
        let allocation = self.objects_by_id.objects_by_id.get(&object_id).expect("get: not found");
        match allocation.kind {
            KindV::StructInstance(si) => {
                let members = si.members.get().expect("StructInstance has no members");
                let actual_reference = members[field_index as usize];
                self.check_reference(interner, expected_type, actual_reference);
                self.transmute(actual_reference, expected_type, target_type)
            }
            _ => panic!("get_reference_from_struct: not a StructInstance"),
        }
    }

    pub fn get_reference_from_array(&self, interner: &HammerInterner<'s, 'h>, address: ElementAddressV<'v, 'h, 's>, expected_type: CoordH<'s, 'h>, target_type: CoordH<'s, 'h>) -> ReferenceV<'v, 'h, 's> {
        let ElementAddressV { array_id: object_id, element_index } = address;
        match self.objects_by_id.objects_by_id.get(&object_id).expect("get_reference_from_array: not found").kind {
            KindV::ArrayInstance(ai) => {
                let r#ref = ai.get_element(element_index);
                self.check_reference(interner, expected_type, r#ref);
                self.transmute(r#ref, expected_type, target_type)
            }
            _ => panic!("get_reference_from_array: not an array instance"),
        }
    }

    pub fn contains_live_object(&self, reference: ReferenceV<'v, 'h, 's>) -> bool {
        self.contains_live_object_alloc_id(reference.alloc_id())
    }

    pub fn contains_live_object_alloc_id(&self, alloc_id: AllocationIdV<'v, 'h, 's>) -> bool {
        let m = &self.objects_by_id.objects_by_id;
        let result = match m.get(&alloc_id) {
            None => false,
            Some(alloc) => match alloc.kind {
                KindV::StructInstance(si) => match si.members.get() {
                    None => false,
                    Some(_) => true,
                },
                _ => true,
            },
        };
        result
    }

    pub fn contains_live_or_undead_object(&self, alloc_id: AllocationIdV<'v, 'h, 's>) -> bool {
        let m = &self.objects_by_id.objects_by_id;
        let result = m.contains_key(&alloc_id);
        result
    }

    pub fn dereference(&self, reference: ReferenceV<'v, 'h, 's>, allow_undead: bool) -> KindV<'v, 'h, 's> {
        if !allow_undead {
            assert!(self.contains_live_object_alloc_id(reference.alloc_id()));
        }
        let m = &self.objects_by_id.objects_by_id;
        let result = m.get(&reference.alloc_id()).expect("dereference: alloc_id not in objects_by_id").kind;
        result
    }

    pub fn is_same_instance(&mut self, interner: &HammerInterner<'s, 'h>, call_id: CallIdV<'v, 'h, 's>, left: ReferenceV<'v, 'h, 's>, right: ReferenceV<'v, 'h, 's>) -> ReferenceV<'v, 'h, 's> {
        let r#ref = self.allocate_transient(interner, OwnershipH::MutableShareH, LocationH::InlineH, KindV::Bool(BoolV { value: left.alloc_id() == right.alloc_id(), _phantom: PhantomData }));
        self.increment_reference_ref_count(IObjectReferrerV::RegisterToObjectReferrer(RegisterToObjectReferrerV { call_id, ownership: OwnershipH::MutableShareH }), r#ref);
        r#ref
    }

    pub fn increment_reference_hold_count(&self, expression_id: ExpressionIdV<'v, 'h, 's>, reference: ReferenceV<'v, 'h, 's>) {
        panic!("Unimplemented: increment_reference_hold_count");
    }

    pub fn decrement_reference_hold_count(&self, expression_id: ExpressionIdV<'v, 'h, 's>, reference: ReferenceV<'v, 'h, 's>) {
        panic!("Unimplemented: decrement_reference_hold_count");
    }

    pub fn increment_reference_ref_count(&mut self, referrer: IObjectReferrerV<'v, 'h, 's>, reference: ReferenceV<'v, 'h, 's>) {
        self.increment_object_ref_count(referrer, reference.alloc_id(), reference.ownership == OwnershipH::WeakH);
    }

    pub fn decrement_reference_ref_count(&mut self, referrer: IObjectReferrerV<'v, 'h, 's>, reference: ReferenceV<'v, 'h, 's>) {
        self.decrement_object_ref_count(referrer, reference.alloc_id(), reference.ownership == OwnershipH::WeakH);
    }

    pub fn destructure_array(&mut self, reference: ReferenceV<'v, 'h, 's>) -> &'v [ReferenceV<'v, 'h, 's>] {
        let allocation = self.dereference(reference, false);
        match allocation {
            KindV::ArrayInstance(ai) => {
                let elements_len = ai.elements.get().len();
                let element_refs_vec: Vec<ReferenceV<'v, 'h, 's>> = (0..elements_len).rev().map(|_index| self.deinitialize_array_element(reference)).collect::<Vec<_>>().into_iter().rev().collect();
                self.vivem_bump.alloc_slice_copy(&element_refs_vec)
            }
            _ => panic!("destructure_array: not an array instance"),
        }
    }

    pub fn destructure(&mut self, reference: ReferenceV<'v, 'h, 's>) -> Result<&'v [ReferenceV<'v, 'h, 's>], VmRuntimeErrorV<'s>> {
        let allocation = self.dereference(reference, false);
        match allocation {
            KindV::StructInstance(s) => {
                let member_refs = s.members.get().expect("destructure: members None");
                for (index, member_ref) in member_refs.iter().enumerate() {
                    self.decrement_reference_ref_count(
                        IObjectReferrerV::MemberToObjectReferrer(MemberToObjectReferrerV {
                            member_addr: MemberAddressV { struct_id: reference.alloc_id(), field_index: index as i32 },
                            ownership: member_ref.ownership,
                        }),
                        *member_ref);
                }
                self.zero(reference);
                self.deallocate_if_no_weak_refs(reference)?;
                Ok(member_refs)
            }
            _ => panic!("destructure: not a StructInstance"),
        }
    }

    pub fn zero(&mut self, reference: ReferenceV<'v, 'h, 's>) {
        let m = &mut self.objects_by_id.objects_by_id;
        let allocation = m.get(&reference.alloc_id()).expect("zero: not in objects_by_id");
        assert_eq!(allocation.get_total_ref_count(Some(OwnershipH::OwnH)), 0);
        assert_eq!(allocation.get_total_ref_count(Some(OwnershipH::ImmutableBorrowH)), 0);
        assert_eq!(allocation.get_total_ref_count(Some(OwnershipH::MutableBorrowH)), 0);
        match allocation.kind {
            KindV::StructInstance(si) => si.zero(),
            _ => {}
        }
        {
            let handle = &mut *self.vivem_dout;
            write!(handle, " o{}zero", reference.alloc_id().num).unwrap();
        }
    }

    pub fn deallocate_if_no_weak_refs(&mut self, reference: ReferenceV<'v, 'h, 's>) -> Result<(), VmRuntimeErrorV<'s>> {
        let m = &mut self.objects_by_id.objects_by_id;
        let allocation = m.get(&reference.alloc_id()).expect("deallocate_if_no_weak_refs: not in objects_by_id");
        if reference.ownership == OwnershipH::OwnH &&
            (allocation.get_total_ref_count(Some(OwnershipH::MutableBorrowH)) + allocation.get_total_ref_count(Some(OwnershipH::ImmutableBorrowH))) > 0 {
            return Err(VmRuntimeErrorV::ConstraintViolatedException(ConstraintViolatedExceptionV {
                msg: StrI("Constraint violated!"),
            }));
        }
        if allocation.get_total_ref_count(None) == 0 {
            m.remove(&reference.alloc_id());
            {
                let handle = &mut *self.vivem_dout;
                write!(handle, " o{}dealloc", reference.alloc_id().num).unwrap();
            }
        }
        Ok(())
    }

    pub fn increment_object_ref_count(&mut self, pointing_from: IObjectReferrerV<'v, 'h, 's>, alloc_id: AllocationIdV<'v, 'h, 's>, allow_undead: bool) {
        if !allow_undead {
            if !self.contains_live_object_alloc_id(alloc_id) {
                panic!("Trying to increment dead object: {}", alloc_id.num);
            }
        }
        let m = &mut self.objects_by_id.objects_by_id;
        let obj = m.get_mut(&alloc_id).expect("increment_object_ref_count: alloc_id not in objects_by_id");
        obj.increment_ref_count(pointing_from);
        let new_ref_count = obj.get_total_ref_count(None);
        {
            let handle = &mut *self.vivem_dout;
            write!(handle, " o{}rc{}->{}", alloc_id.num, new_ref_count - 1, new_ref_count).unwrap();
        }
    }

    pub fn decrement_object_ref_count(&mut self, pointed_from: IObjectReferrerV<'v, 'h, 's>, alloc_id: AllocationIdV<'v, 'h, 's>, allow_undead: bool) -> i32 {
        if !allow_undead {
            if !self.contains_live_object_alloc_id(alloc_id) {
                panic!("Can't decrement object {}, not in heap!", alloc_id.num);
            }
        }
        let m = &mut self.objects_by_id.objects_by_id;
        let obj = m.get_mut(&alloc_id).expect("decrement_object_ref_count: not in objects_by_id");
        obj.decrement_ref_count(pointed_from);
        let new_ref_count = obj.get_total_ref_count(None);
        {
            let handle = &mut *self.vivem_dout;
            write!(handle, " o{}rc{}->{}", alloc_id.num, new_ref_count + 1, new_ref_count).unwrap();
        }
        new_ref_count
    }

    pub fn get_ref_count(&self, reference: ReferenceV<'v, 'h, 's>) -> i32 {
        panic!("Unimplemented: get_ref_count");
    }

    pub fn get_total_ref_count(&self, reference: ReferenceV<'v, 'h, 's>) -> i32 {
        if reference.ownership == OwnershipH::WeakH {
            assert!(self.contains_live_or_undead_object(reference.alloc_id()));
        } else {
            assert!(self.contains_live_object_alloc_id(reference.alloc_id()));
        }
        let m = &self.objects_by_id.objects_by_id;
        let allocation = m.get(&reference.alloc_id()).expect("get_total_ref_count: not in objects_by_id");
        let result = allocation.get_total_ref_count(None);
        result
    }

    pub fn ensure_ref_count(&self, interner: &HammerInterner<'s, 'h>, scout_arena: &ScoutArena<'s>, reference: ReferenceV<'v, 'h, 's>, ownership_filter: Option<&'v [OwnershipH]>, expected_num: i32) -> Result<(), VmRuntimeErrorV<'s>> {
        assert!(self.contains_live_object_alloc_id(reference.alloc_id()));
        let allocation = self.objects_by_id.get(interner, reference.alloc_id());
        allocation.ensure_ref_count(scout_arena, ownership_filter, expected_num)
    }

    pub fn add(&mut self, interner: &HammerInterner<'s, 'h>, ownership: OwnershipH, location: LocationH, kind: KindV<'v, 'h, 's>) -> ReferenceV<'v, 'h, 's> {
        self.objects_by_id.add(interner, ownership, location, kind)
    }

    pub fn transmute(&self, reference: ReferenceV<'v, 'h, 's>, expected_type: CoordH<'s, 'h>, target_type: CoordH<'s, 'h>) -> ReferenceV<'v, 'h, 's> {
        if expected_type == target_type {
            return reference;
        }
        let ReferenceV { actual_kind, seen_as_kind: old_seen_as_type, ownership: old_ownership, location: _old_location, num: object_id } = reference;
        assert_eq!(
            old_ownership == OwnershipH::MutableShareH || old_ownership == OwnershipH::ImmutableShareH,
            target_type.ownership == OwnershipH::MutableShareH || target_type.ownership == OwnershipH::ImmutableShareH,
        );
        if old_seen_as_type.hamut != expected_type.kind {
            panic!("wot");
        }
        ReferenceV {
            actual_kind,
            seen_as_kind: RRKindV { hamut: target_type.kind, _phantom: PhantomData },
            ownership: target_type.ownership,
            location: target_type.location,
            num: object_id,
        }
    }

    pub fn cast(&self, call_id: CallIdV<'v, 'h, 's>, reference: ReferenceV<'v, 'h, 's>, expected_type: CoordH<'s, 'h>, target_type: CoordH<'s, 'h>) -> ReferenceV<'v, 'h, 's> {
        panic!("Unimplemented: cast");
    }

    pub fn is_empty(&self) -> bool {
        panic!("Unimplemented: is_empty");
    }

    pub fn print_all(&self) {
        panic!("Unimplemented: print_all");
    }

    pub fn count_unreachable_allocations(&self, interner: &HammerInterner<'s, 'h>, roots: &'v [ReferenceV<'v, 'h, 's>]) -> usize {
        let num_reachables = self.find_reachable_allocations(interner, roots).len();
        assert!(num_reachables <= self.objects_by_id.size());
        self.objects_by_id.size() - num_reachables
    }

    pub fn find_reachable_allocations<'a>(&'a self, interner: &HammerInterner<'s, 'h>, input_reachables: &'v [ReferenceV<'v, 'h, 's>]) -> HashMap<ReferenceV<'v, 'h, 's>, &'a AllocationV<'v, 'h, 's>> {
        let mut destination_map: HashMap<ReferenceV<'v, 'h, 's>, &'a AllocationV<'v, 'h, 's>> = HashMap::new();
        for input_reachable in input_reachables {
            self.inner_find_reachable_allocations(interner, &mut destination_map, *input_reachable);
        }
        self.inner_find_reachable_allocations(interner, &mut destination_map, self.void());
        destination_map
    }

    pub fn inner_find_reachable_allocations<'a>(&'a self, interner: &HammerInterner<'s, 'h>, destination_map: &mut HashMap<ReferenceV<'v, 'h, 's>, &'a AllocationV<'v, 'h, 's>>, input_reachable: ReferenceV<'v, 'h, 's>) {
        // Doublecheck that all the inputReachables are actually in this ..
        assert!(self.contains_live_object(input_reachable));
        assert!(self.objects_by_id.get(interner, input_reachable.alloc_id()).kind.tyype(interner).hamut == input_reachable.actual_kind.hamut);

        let allocation = self.objects_by_id.get(interner, input_reachable.alloc_id());
        if destination_map.contains_key(&input_reachable) {
            return;
        }

        destination_map.insert(input_reachable, allocation);
        match allocation.kind {
            KindV::Int(_) => {}
            KindV::Void(_) => {}
            KindV::Bool(_) => {}
            KindV::Float(_) => {}
            KindV::StructInstance(struct_instance) => {
                let members = struct_instance.members.get().expect("StructInstance has no members");
                for (reference, _struct_member_h) in members.iter().zip(struct_instance.struct_h.members.iter()) {
                    self.inner_find_reachable_allocations(interner, destination_map, *reference);
                }
            }
            _ => panic!(),
        }
    }

    pub fn check_for_leaks(&mut self) {
        self.objects_by_id.check_for_leaks(self.vivem_dout);
    }

    // Scala signature: getCurrentCall(callId: CallId): Call returns the Call by reference (Scala mutable shared object).
    pub fn get_current_call<R>(&mut self, expected_call_id: CallIdV<'v, 'h, 's>, f: impl FnOnce(&mut CallV<'v, 'h, 's>) -> R) -> R {
        assert_eq!(*self.call_id_stack.last().expect("call_id_stack empty"), expected_call_id);
        let call = self.calls_by_id.get_mut(&expected_call_id).expect("get_current_call: not in calls_by_id");
        f(call)
    }

    pub fn take_argument(&mut self, interner: &HammerInterner<'s, 'h>, call_id: CallIdV<'v, 'h, 's>, argument_index: i32, expected_type: CoordH<'s, 'h>) -> ReferenceV<'v, 'h, 's> {
        let reference = self.get_current_call(call_id, |c| c.take_argument(argument_index));
        self.check_reference(interner, expected_type, reference);
        self.decrement_reference_ref_count(
            IObjectReferrerV::ArgumentToObjectReferrer(ArgumentToObjectReferrerV {
                argument_id: ArgumentIdV { call_id, index: argument_index },
                ownership: expected_type.ownership,
            }),
            reference); // decrementing because taking it out of arg
        // Now, the register is the only one that has this reference.
        reference
    }

    pub fn allocate_transient(&mut self, interner: &HammerInterner<'s, 'h>, ownership: OwnershipH, location: LocationH, kind: KindV<'v, 'h, 's>) -> ReferenceV<'v, 'h, 's> {
        let r#ref = self.add(interner, ownership, location, kind);
        {
            let handle = &mut *self.vivem_dout;
            write!(handle, " o{}=", r#ref.alloc_id().num).unwrap();
        }
        self.print_kind(kind);
        r#ref
    }

    pub fn print_kind(&mut self, kind: KindV<'v, 'h, 's>) {
        let handle = &mut *self.vivem_dout;
        match kind {
            KindV::Void(_) => write!(handle, "void").unwrap(),
            KindV::Int(int_v) => write!(handle, "{}", int_v.value).unwrap(),
            KindV::Opaque(_) => write!(handle, "opaque").unwrap(),
            KindV::Bool(b) => write!(handle, "{}", b.value).unwrap(),
            KindV::Str(s) => write!(handle, "{}", s.value.0).unwrap(),
            KindV::Float(f) => write!(handle, "{}", f.value).unwrap(),
            KindV::StructInstance(si) => {
                let members = si.members.get().expect("print_kind: StructInstance members None");
                let members_str = members.iter().map(|r| format!("o{}", r.alloc_id().num)).collect::<Vec<_>>().join(", ");
                write!(handle, "{}{{{}}}", si.struct_h.id.fully_qualified_name.0, members_str).unwrap()
            }
            KindV::ArrayInstance(ai) => {
                let elements = ai.elements.get();
                let elements_str = elements.iter().map(|r| format!("o{}", r.alloc_id().num)).collect::<Vec<_>>().join(", ");
                write!(handle, "array:{}:{:?}{{{}}}", ai.capacity, ai.element_type_h, elements_str).unwrap()
            }
        }
    }

    pub fn initialize_array_element(&mut self, array_reference: ReferenceV<'v, 'h, 's>, ret: ReferenceV<'v, 'h, 's>) {
        match self.dereference(array_reference, false) {
            KindV::ArrayInstance(a) => {
                self.increment_reference_ref_count(
                    IObjectReferrerV::ElementToObjectReferrer(ElementToObjectReferrerV {
                        element_addr: ElementAddressV { array_id: array_reference.alloc_id(), element_index: a.get_size() },
                        ownership: a.element_type_h.ownership,
                    }),
                    ret);
                a.initialize_element(self.vivem_bump, ret);
            }
            _ => panic!("initialize_array_element: not an ArrayInstance"),
        }
    }

    pub fn new_struct(&mut self, interner: &HammerInterner<'s, 'h>, struct_def_h: StructDefinitionH<'s, 'h>, struct_ref_h: CoordH<'s, 'h>, member_references: &'v [ReferenceV<'v, 'h, 's>]) -> ReferenceV<'v, 'h, 's> {
        let instance: &'v StructInstanceV<'v, 'h, 's> = self.vivem_bump.alloc(StructInstanceV {
            struct_h: struct_def_h,
            members: Cell::new(Some(member_references)),
        });
        let reference = self.add(interner, struct_ref_h.ownership, struct_ref_h.location, KindV::StructInstance(instance));
        for (index, member_reference) in member_references.iter().enumerate() {
            self.increment_reference_ref_count(
                IObjectReferrerV::MemberToObjectReferrer(MemberToObjectReferrerV {
                    member_addr: MemberAddressV { struct_id: reference.alloc_id(), field_index: index as i32 },
                    ownership: member_reference.ownership,
                }),
                *member_reference);
        }
        {
            write!(self.vivem_dout, " o{}=", reference.num).unwrap();
        }
        self.print_kind(KindV::StructInstance(instance));
        reference
    }

    pub fn new_opaque(&mut self, interner: &HammerInterner<'s, 'h>, opaque_coord_ht: CoordH<'s, 'h>) -> ReferenceV<'v, 'h, 's> {
        let opaque_ht = match opaque_coord_ht.kind {
            KindHT::OpaqueHT(o) => o,
            _ => panic!(),
        };
        let instance = KindV::Opaque(OpaqueV { opaque_ht: *opaque_ht, _phantom: PhantomData });
        let reference = self.add(interner, opaque_coord_ht.ownership, opaque_coord_ht.location, instance);
        {
            let handle = &mut *self.vivem_dout;
            write!(handle, " o{}=", reference.num).unwrap();
        }
        self.print_kind(instance);
        reference
    }

    pub fn deinitialize_array_element(&mut self, array_reference: ReferenceV<'v, 'h, 's>) -> ReferenceV<'v, 'h, 's> {
        let array_instance = match self.dereference(array_reference, false) {
            KindV::ArrayInstance(ai) => ai,
            _ => panic!("deinitialize_array_element: not an array instance"),
        };
        let element_reference = array_instance.deinitialize_element();
        self.decrement_reference_ref_count(
            IObjectReferrerV::ElementToObjectReferrer(ElementToObjectReferrerV {
                element_addr: ElementAddressV { array_id: array_reference.alloc_id(), element_index: array_instance.get_size() },
                ownership: element_reference.ownership,
            }),
            element_reference);
        element_reference
    }

    pub fn add_uninitialized_array(&mut self, interner: &HammerInterner<'s, 'h>, array_definition_th: RuntimeSizedArrayDefinitionHT<'s, 'h>, array_ref_type: CoordH<'s, 'h>, capacity: i32) -> (ReferenceV<'v, 'h, 's>, &'v ArrayInstanceV<'v, 'h, 's>) {
        let instance: &'v ArrayInstanceV<'v, 'h, 's> = self.vivem_bump.alloc(ArrayInstanceV { type_h: array_ref_type, element_type_h: array_definition_th.element_type, capacity, elements: Cell::new(&[]) });
        let reference = self.add(interner, array_ref_type.ownership, array_ref_type.location, KindV::ArrayInstance(instance));
        (reference, instance)
    }

    pub fn add_array(&mut self, interner: &HammerInterner<'s, 'h>, array_definition_th: StaticSizedArrayDefinitionHT<'s, 'h>, array_ref_type: CoordH<'s, 'h>, member_refs: &'v [ReferenceV<'v, 'h, 's>]) -> (ReferenceV<'v, 'h, 's>, &'v ArrayInstanceV<'v, 'h, 's>) {
        let instance: &'v ArrayInstanceV<'v, 'h, 's> = self.vivem_bump.alloc(ArrayInstanceV { type_h: array_ref_type, element_type_h: array_definition_th.element_type, capacity: member_refs.len() as i32, elements: Cell::new(member_refs) });
        let reference = self.add(interner, array_ref_type.ownership, array_ref_type.location, KindV::ArrayInstance(instance));
        for (index, member_ref) in member_refs.iter().enumerate() {
            self.increment_reference_ref_count(
                IObjectReferrerV::ElementToObjectReferrer(ElementToObjectReferrerV {
                    element_addr: ElementAddressV { array_id: reference.alloc_id(), element_index: index as i64 },
                    ownership: member_ref.ownership,
                }),
                *member_ref);
        }
        (reference, instance)
    }

    pub fn check_reference(&self, interner: &HammerInterner<'s, 'h>, expected_type: CoordH<'s, 'h>, actual_reference: ReferenceV<'v, 'h, 's>) {
        if actual_reference.ownership == OwnershipH::WeakH {
            assert!(self.contains_live_or_undead_object(actual_reference.alloc_id()));
        } else {
            assert!(self.contains_live_object_alloc_id(actual_reference.alloc_id()));
        }
        if (match actual_reference.seen_as_coord().hamut.ownership {
            OwnershipH::ImmutableShareH => OwnershipH::MutableShareH,
            OwnershipH::ImmutableBorrowH => OwnershipH::MutableBorrowH,
            other => other,
        }) != (match expected_type.ownership {
            OwnershipH::ImmutableShareH => OwnershipH::MutableShareH,
            OwnershipH::ImmutableBorrowH => OwnershipH::MutableBorrowH,
            other => other,
        }) {
            panic!("Expected {:?} but was {:?}", expected_type, actual_reference.seen_as_coord().hamut);
        }
        if actual_reference.seen_as_coord().hamut.location != expected_type.location {
            panic!("Expected {:?} but was {:?}", expected_type, actual_reference.seen_as_coord().hamut);
        }
        if actual_reference.seen_as_coord().hamut.kind != expected_type.kind {
            panic!("Expected {:?} but was {:?}", expected_type, actual_reference.seen_as_coord().hamut);
        }
        let actual_kind = self.dereference(actual_reference, actual_reference.ownership == OwnershipH::WeakH);
        self.check_kind(interner, expected_type.kind, actual_kind);
    }

    pub fn check_reference_register(&self, tyype: CoordH<'s, 'h>, register: RegisterV<'v, 'h, 's>) -> ReferenceRegisterV<'v, 'h, 's> {
        panic!("Unimplemented: check_reference_register");
    }

    pub fn check_kind(&self, interner: &HammerInterner<'s, 'h>, expected_type: KindHT<'s, 'h>, actual_kind: KindV<'v, 'h, 's>) {
        match (actual_kind, expected_type) {
            (KindV::Int(actual), KindHT::IntHT(expected)) => {
                if actual.bits != expected.bits {
                    panic!("Expected {:?} but was {:?}", expected, actual);
                }
            }
            (KindV::Opaque(a), KindHT::OpaqueHT(b)) => {
                if a.opaque_ht != *b {
                    panic!("Expected {:?} but was {:?}", expected_type, actual_kind);
                }
            }
            (KindV::Void(_), KindHT::VoidHT(_)) => {}
            (KindV::Bool(_), KindHT::BoolHT(_)) => {}
            (KindV::Str(_), KindHT::StrHT(_)) => {}
            (KindV::Float(_), KindHT::FloatHT(_)) => {}
            (KindV::StructInstance(struct_def_h), KindHT::StructHT(struct_ref_h)) => {
                if struct_def_h.struct_h.get_ref(interner) != struct_ref_h {
                    panic!("Expected {:?} but was {:?}", struct_ref_h, struct_def_h.struct_h);
                }
            }
            (KindV::ArrayInstance(type_h_array), array_h @ KindHT::RuntimeSizedArrayHT(_)) => {
                if type_h_array.type_h.kind != array_h {
                    panic!("Expected {:?} but was {:?}", array_h, type_h_array.type_h);
                }
            }
            (KindV::ArrayInstance(type_h_array), array_h @ KindHT::StaticSizedArrayHT(_)) => {
                if type_h_array.type_h.kind != array_h {
                    panic!("Expected {:?} but was {:?}", array_h, type_h_array.type_h);
                }
            }
            (KindV::StructInstance(struct_def_h), ir_h @ KindHT::InterfaceHT(_)) => {
                let interface_h = match ir_h {
                    KindHT::InterfaceHT(i) => i,
                    _ => unreachable!(),
                };
                let struct_implements_interface = struct_def_h.struct_h.edges.iter().any(|e| e.interface == interface_h);
                if !struct_implements_interface {
                    panic!("Struct {:?} doesnt implement interface {:?}", struct_def_h.struct_h.get_ref(interner), interface_h);
                }
            }
            (a, b) => {
                panic!("Mismatch! {:?} is not a {:?}", a, b);
            }
        }
    }

    pub fn check_struct_id(&self, expected_struct_type: StructHT<'s, 'h>, expected_struct_pointer_type: CoordH<'s, 'h>, register: RegisterV<'v, 'h, 's>) -> AllocationIdV<'v, 'h, 's> {
        panic!("Unimplemented: check_struct_id");
    }

    pub fn check_struct_coord(&self, expected_struct_type: StructHT<'s, 'h>, expected_struct_pointer_type: CoordH<'s, 'h>, register: RegisterV<'v, 'h, 's>) -> StructInstanceV<'v, 'h, 's> {
        panic!("Unimplemented: check_struct_coord");
    }

    pub fn check_struct_coord_ref(&self, expected_struct_type: StructHT<'s, 'h>, reference: ReferenceV<'v, 'h, 's>) -> StructInstanceV<'v, 'h, 's> {
        panic!("Unimplemented: check_struct_coord");
    }

    pub fn push_new_stack_frame(&mut self, function_h: &'h PrototypeH<'s, 'h>, args: &'v [ReferenceV<'v, 'h, 's>]) -> CallIdV<'v, 'h, 's> {
        let calls_by_id = &mut self.calls_by_id;
        let call_id_stack = &mut self.call_id_stack;
        assert_eq!(calls_by_id.len(), call_id_stack.len());
        let call_id = CallIdV {
            call_depth: if !call_id_stack.is_empty() { call_id_stack.last().unwrap().call_depth + 1 } else { 0 },
            function: function_h,
            _phantom: PhantomData,
        };
        let call = CallV {
            call_id,
            in_args: args,
            args: args.iter().enumerate().map(|(i, r)| (i as i32, Some(*r))).collect(),
            locals: HashMap::new(),
        };
        calls_by_id.insert(call_id, call);
        let mut call_id_stack = call_id_stack;
        call_id_stack.push(call_id);
        assert_eq!(calls_by_id.len(), call_id_stack.len());
        call_id
    }

    pub fn pop_stack_frame(&mut self, expected_call_id: CallIdV<'v, 'h, 's>) {
        let calls_by_id = &mut self.calls_by_id;
        let call_id_stack = &mut self.call_id_stack;
        assert_eq!(calls_by_id.len(), call_id_stack.len());
        assert_eq!(*call_id_stack.last().expect("call_id_stack empty"), expected_call_id);
        {
            let call = calls_by_id.get_mut(&expected_call_id).expect("call not found");
            call.prepare_to_die();
        }
        call_id_stack.pop();
        calls_by_id.remove(&expected_call_id);
        assert_eq!(calls_by_id.len(), call_id_stack.len());
    }

    pub fn to_von(&self, reference: ReferenceV<'v, 'h, 's>) -> IVonData {
        match self.dereference(reference, false) {
            KindV::Void(_) => IVonData::Object(VonObject { tyype: "void".to_string(), id: None, members: vec![] }),
            KindV::Int(v) => IVonData::Int(VonInt { value: v.value }),
            KindV::Float(v) => IVonData::Float(VonFloat { value: v.value }),
            KindV::Bool(v) => IVonData::Bool(VonBool { value: v.value }),
            KindV::Str(v) => IVonData::Str(VonStr { value: v.value.0.to_string() }),
            KindV::ArrayInstance(ai) => {
                let elements_von: Vec<IVonData> =
                    ai.elements.get().iter().map(|e| self.to_von(*e)).collect();
                IVonData::Array(VonArray { id: None, members: elements_von })
            }
            KindV::StructInstance(si) => {
                let members = si.members.get().expect("vassertSome StructInstance members");
                assert_eq!(members.len(), si.struct_h.members.len());
                let von_members: Vec<VonMember> =
                    si.struct_h.members.iter().zip(members.iter()).enumerate().map(|(_index, (member_h, member_v))| {
                        let _ = member_h;
                        VonMember {
                            field_name: panic!("vimpl: memberH.name.toString"),
                            value: self.to_von(*member_v),
                        }
                    }).collect();
                IVonData::Object(VonObject {
                    tyype: si.struct_h.id.to_string(),
                    id: None,
                    members: von_members,
                })
            }
            KindV::Opaque(_) => panic!("to_von: Opaque — pilot doesn't exercise"),
        }
    }
}

pub fn get_var_address<'v, 'h, 's>(call_id: CallIdV<'v, 'h, 's>, local: Local<'s, 'h>) -> VariableAddressV<'v, 'h, 's> {
    VariableAddressV { call_id, local }
}

