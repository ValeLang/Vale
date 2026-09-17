use crate::utils::range::RangeS;
use crate::postparsing::names::IImpreciseNameS;
use crate::postparsing::rune_type_solver::RuneTypeSolveError;
use std::any::Any;
use std::collections::HashSet;
// VISTODO: rename

pub struct CompileErrorExceptionA<'s> {
    pub err: ICompileErrorA<'s>,
}

impl<'s> CompileErrorExceptionA<'s> {

}

pub enum ICompileErrorA<'s> {
    
    CouldntFindType(CouldntFindTypeA<'s>),
    TooManyMatchingTypes(TooManyMatchingTypesA<'s>),
    CouldntSolveRules(CouldntSolveRulesA<'s>),
    CircularModuleDependency(CircularModuleDependency<'s>),
    WrongNumArgsForTemplate(WrongNumArgsForTemplateA<'s>),
    RangedInternalError(RangedInternalErrorA<'s>),
    
}
impl<'s> ICompileErrorA<'s> {
    pub fn range(&self) -> RangeS<'s> {
        match self {
            ICompileErrorA::CouldntFindType(x) => x.range.clone(),
            ICompileErrorA::TooManyMatchingTypes(x) => x.range.clone(),
            ICompileErrorA::CouldntSolveRules(x) => x.range.clone(),
            ICompileErrorA::CircularModuleDependency(x) => x.range.clone(),
            ICompileErrorA::WrongNumArgsForTemplate(x) => x.range.clone(),
            ICompileErrorA::RangedInternalError(x) => x.range.clone(),
        }
    }
}

pub enum ILookupFailedErrorA<'s> {
    CouldntFindType(CouldntFindTypeA<'s>),
    TooManyMatchingTypes(TooManyMatchingTypesA<'s>),
}
impl<'s> From<ILookupFailedErrorA<'s>> for ICompileErrorA<'s> {
    fn from(e: ILookupFailedErrorA<'s>) -> Self {
        match e {
            ILookupFailedErrorA::CouldntFindType(x) => ICompileErrorA::CouldntFindType(x),
            ILookupFailedErrorA::TooManyMatchingTypes(x) => ICompileErrorA::TooManyMatchingTypes(x),
        }
    }
}

pub struct TooManyMatchingTypesA<'s> {
    pub range: RangeS<'s>,
    pub name: IImpreciseNameS<'s>,
}

pub struct CouldntFindTypeA<'s> {
    pub range: RangeS<'s>,
    pub name: IImpreciseNameS<'s>,
}

pub struct CouldntSolveRulesA<'s> {
    pub range: RangeS<'s>,
    pub error: RuneTypeSolveError<'s>,
}

pub struct CircularModuleDependency<'s> {
    pub range: RangeS<'s>,
    pub modules: HashSet<String>,
}

impl<'s> CircularModuleDependency<'s> {}
pub struct WrongNumArgsForTemplateA<'s> {
    pub range: RangeS<'s>,
    pub expected_num_args: i32,
    pub actual_num_args: i32,
}

impl<'s> WrongNumArgsForTemplateA<'s> {}
pub struct RangedInternalErrorA<'s> {
    pub range: RangeS<'s>,
    pub message: String,
}

impl<'s> RangedInternalErrorA<'s> {}

pub fn report<'s>(_err: ICompileErrorA<'s>) -> ! {
    panic!("Unimplemented: report");
}
