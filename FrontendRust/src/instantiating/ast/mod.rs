// From Frontend/InstantiatingPass/src/dev/vale/instantiating/ast/
//
// Only `types` is registered here for now — the other files in this directory
// (ast.rs, citizens.rs, expressions.rs, hinputs.rs, names.rs, templata.rs,
// templata_utils.rs) are mid-migration and not yet build-ready. The simplifying
// pass needs the I-suffix output enums (MutabilityI, VariabilityI, OwnershipI,
// LocationI) from types.rs as inputs; everything else stays excluded until
// the instantiating migration finishes its current sweep.

pub mod types;
pub mod hinputs;
pub mod names;
// AST files with placeholder bodies referencing un-migrated types stay excluded
// (per Slab 16i partial). Each carries `<'s, 't>` lifetimes that need updating to
// `<'s, 'i, R>` + use blocks before exposure. Fill these in slab-by-slab as
// downstream consumers (the simplifying pass) need them:
pub mod ast;
pub mod citizens;
pub mod expressions;
pub mod templata;
pub mod templata_utils;
