// From Frontend/Testvm/src/dev/vale/testvm/
// Scala package `dev.vale.testvm` — module-declaration only. Individual TestVM files
// (heap, values, vivem, etc.) are still 100% panic-stubbed; they get filled in as the
// integration-test pilot cascade demands them.

pub mod call;
pub mod expression_vivem;
pub mod function_vivem;
pub mod heap;
pub mod values;
pub mod vivem;
pub mod vivem_externs;

#[cfg(test)]
mod test;
