use crate::postparsing::names::IVarNameS;
use crate::postparsing::rules::RuneUsage;
use crate::utils::range::RangeS;

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct CaptureS<'s> {
  pub name: IVarNameS<'s>,
  pub mutate: bool,
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct AtomSP<'s> {
  pub range: RangeS<'s>,
  pub name: Option<CaptureS<'s>>,
  pub coord_rune: Option<RuneUsage<'s>>,
  pub destructure: Option<&'s [AtomSP<'s>]>,
}

// V: does this need to be clone?
// VA: No. Neither CaptureS nor AtomSP is ever cloned anywhere in the codebase. Both are
// VA: Clone-without-Copy (ATDCX violation). The root blocker for Copy is AtomSP.destructure:
// VA: Option<Vec<AtomSP>> — Vec prevents Copy. If destructure became Option<&'s [AtomSP<'s>]>
// VA: (arena-allocated), then AtomSP could be Copy (all other fields are Copy: IVarNameS, bool,
// VA: RangeS, RuneUsage). CaptureS could then also be Copy. The Vec is also an AASSNCMCX violation.