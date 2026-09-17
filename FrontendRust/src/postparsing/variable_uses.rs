use crate::postparsing::expressions::IVariableUseCertainty;
use crate::postparsing::names::IImpreciseNameS;
use crate::postparsing::names::IVarNameS;

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct VariableUseS<'s> {
  pub name: IVarNameS<'s>,
  pub borrowed: Option<IVariableUseCertainty>,
  pub moved: Option<IVariableUseCertainty>,
  pub mutated: Option<IVariableUseCertainty>,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct VariableDeclarationS<'s> {
  pub name: IVarNameS<'s>,
}

#[derive(Clone, Debug, PartialEq, Eq, Hash)]
pub struct VariableDeclarations<'s> {
  pub vars: Vec<VariableDeclarationS<'s>>,
}

impl<'s> VariableDeclarations<'s> {
  // MIGALLOW: empty -> empty
  pub fn empty() -> VariableDeclarations<'s> {
    VariableDeclarations { vars: Vec::new() }
  }

// MIGALLOW: ++ -> plus_plus
pub fn plus_plus(&self, that: &VariableDeclarations<'s>) -> VariableDeclarations<'s> {
  let mut vars = self.vars.clone();
  vars.extend(that.vars.clone());
  VariableDeclarations { vars }
}

pub fn find(&self, needle: &IImpreciseNameS<'s>) -> Option<IVarNameS<'s>> {
  match needle {
    IImpreciseNameS::CodeName(needle_name) => self.vars.iter().find_map(|decl| match &decl.name {
      IVarNameS::CodeVarName(haystack_name) if *haystack_name == needle_name.name => {
        Some(decl.name.clone())
      }
      _ => None,
    }),
    IImpreciseNameS::IterableName(needle_name) => self.vars.iter().find_map(|decl| match &decl.name {
      IVarNameS::IterableName(haystack_name) if *haystack_name == needle_name.range => {
        Some(decl.name.clone())
      }
      _ => None,
    }),
    IImpreciseNameS::IteratorName(needle_name) => self.vars.iter().find_map(|decl| match &decl.name {
      IVarNameS::IteratorName(haystack_name) if *haystack_name == needle_name.range => {
        Some(decl.name.clone())
      }
      _ => None,
    }),
    IImpreciseNameS::IterationOptionName(needle_name) => self.vars.iter().find_map(|decl| match &decl.name {
      IVarNameS::IterationOptionName(haystack_name) if *haystack_name == needle_name.range => {
        Some(decl.name.clone())
      }
      _ => None,
    }),
    IImpreciseNameS::SelfName(_) => self.vars.iter().find_map(|decl| match &decl.name {
      IVarNameS::SelfName => Some(decl.name.clone()),
      _ => None,
    }),
    _ => None,
  }
}

}

#[derive(Clone, Debug, PartialEq)]
pub struct VariableUses<'s> {
  pub uses: Vec<VariableUseS<'s>>,
}

impl<'s> VariableUses<'s> {

  // MIGALLOW: empty -> empty
  pub fn empty() -> VariableUses<'s> {
    VariableUses { uses: Vec::new() }
  }

  pub fn is_empty(&self) -> bool {
    self.uses.is_empty()
  }

  pub fn all_used_names(&self) -> Vec<IVarNameS<'_>> {
    self.uses.iter().map(|use_| use_.name.clone()).collect()
  }

pub fn mark_borrowed(&self, name: IVarNameS<'s>) -> VariableUses<'s>
{
  self.merge_new_use(VariableUseS {
    name,
    borrowed: Some(IVariableUseCertainty::Used),
    moved: None,
    mutated: None,
  }, Self::then_merge_certainty)
}

pub fn mark_moved(&self, name: IVarNameS<'s>) -> VariableUses<'s>
{
  self.merge_new_use(VariableUseS {
    name,
    borrowed: None,
    moved: Some(IVariableUseCertainty::Used),
    mutated: None,
  }, Self::then_merge_certainty)
}

pub fn mark_mutated(&self, name: IVarNameS<'s>) -> VariableUses<'s>
{
  self.merge_new_use(VariableUseS {
    name,
    borrowed: None,
    moved: None,
    mutated: Some(IVariableUseCertainty::Used),
  }, Self::then_merge_certainty)
}

// MIGALLOW: thenMerge -> then_merge
pub fn then_merge(&self, new_uses: &VariableUses<'s>) -> VariableUses<'s>
{
  self.combine(new_uses, Self::then_merge_certainty)
}

// MIGALLOW: branchMerge -> branch_merge
pub fn branch_merge(&self, new_uses: &VariableUses<'s>) -> VariableUses<'s>
{
  self.combine(new_uses, Self::branch_merge_certainty)
}

pub fn is_borrowed(&self, name: &IVarNameS<'_>) -> IVariableUseCertainty {
  self
    .uses
    .iter()
    .find(|use_| &use_.name == name)
    .and_then(|use_| use_.borrowed)
    .unwrap_or(IVariableUseCertainty::NotUsed)
}

pub fn is_moved(&self, name: &IVarNameS<'_>) -> IVariableUseCertainty {
  self
    .uses
    .iter()
    .find(|use_| &use_.name == name)
    .and_then(|use_| use_.moved)
    .unwrap_or(IVariableUseCertainty::NotUsed)
}

pub fn is_mutated(&self, name: &IVarNameS<'_>) -> IVariableUseCertainty {
  self
    .uses
    .iter()
    .find(|use_| &use_.name == name)
    .and_then(|use_| use_.mutated)
    .unwrap_or(IVariableUseCertainty::NotUsed)
}

fn combine(
  &self,
  that: &VariableUses<'s>,
  certainty_merger: fn(
    Option<IVariableUseCertainty>,
    Option<IVariableUseCertainty>,
  ) -> Option<IVariableUseCertainty>,
) -> VariableUses<'s>
{
  let mut names: Vec<IVarNameS<'_>> = self.uses.iter().map(|use_| use_.name.clone()).collect();
  for name in that.uses.iter().map(|use_| use_.name.clone()) {
    if !names.contains(&name) {
      names.push(name);
    }
  }
  let merged_uses = names
    .into_iter()
    .map(|name| {
      let this_use = self.uses.iter().find(|use_| use_.name == name);
      let that_use = that.uses.iter().find(|use_| use_.name == name);
      match (this_use, that_use) {
        (None, Some(only_that)) => Self::merge_uses(
          &VariableUseS { name: name.clone(), borrowed: None, moved: None, mutated: None },
          only_that,
          certainty_merger,
        ),
        (Some(only_this), None) => Self::merge_uses(
          only_this,
          &VariableUseS { name: name.clone(), borrowed: None, moved: None, mutated: None },
          certainty_merger,
        ),
        (Some(this_use), Some(that_use)) => {
          Self::merge_uses(this_use, that_use, certainty_merger)
        }
        (None, None) => {
          panic!("POSTPARSER_VARIABLE_USES_COMBINE_BOTH_NONE")
        }
      }
    })
    .collect();
  VariableUses { uses: merged_uses }
}

fn merge_new_use(
  &self,
  new_use: VariableUseS<'s>,
  certainty_merger: fn(
    Option<IVariableUseCertainty>,
    Option<IVariableUseCertainty>,
  ) -> Option<IVariableUseCertainty>,
) -> VariableUses<'s>
{
  match self.uses.iter().find(|use_| use_.name == new_use.name) {
    None => {
      let mut uses = self.uses.clone();
      uses.push(new_use);
      VariableUses { uses }
    }
    Some(existing_use) => {
      let mut uses: Vec<VariableUseS<'_>> = self
        .uses
        .iter()
        .filter(|use_| use_.name != existing_use.name)
        .cloned()
        .collect();
      uses.push(Self::merge_uses(existing_use, &new_use, certainty_merger));
      VariableUses { uses }
    }
  }
}

fn merge_uses<'b>(
  existing_use: &VariableUseS<'b>,
  new_use: &VariableUseS<'b>,
  certainty_merger: fn(
    Option<IVariableUseCertainty>,
    Option<IVariableUseCertainty>,
  ) -> Option<IVariableUseCertainty>,
) -> VariableUseS<'b> {
  VariableUseS {
    name: new_use.name.clone(),
    borrowed: certainty_merger(existing_use.borrowed, new_use.borrowed),
    moved: certainty_merger(existing_use.moved, new_use.moved),
    mutated: certainty_merger(existing_use.mutated, new_use.mutated),
  }
}

fn then_merge_certainty(
  a: Option<IVariableUseCertainty>,
  b: Option<IVariableUseCertainty>,
) -> Option<IVariableUseCertainty> {
  match (a, b) {
    (None, other) => other,
    (other, None) => other,
    (Some(IVariableUseCertainty::NotUsed), Some(IVariableUseCertainty::Used)) => {
      Some(IVariableUseCertainty::Used)
    }
    (Some(IVariableUseCertainty::Used), Some(IVariableUseCertainty::NotUsed)) => {
      Some(IVariableUseCertainty::Used)
    }
    (Some(IVariableUseCertainty::Used), Some(IVariableUseCertainty::Used)) => {
      Some(IVariableUseCertainty::Used)
    }
    (Some(IVariableUseCertainty::NotUsed), Some(IVariableUseCertainty::NotUsed)) => {
      Some(IVariableUseCertainty::NotUsed)
    }
  }
}
// MIGALLOW: thenMerge -> then_merge_certainty

fn branch_merge_certainty(
  a: Option<IVariableUseCertainty>,
  b: Option<IVariableUseCertainty>,
) -> Option<IVariableUseCertainty> {
  match (a, b) {
    (None, None) => None,
    (None, Some(IVariableUseCertainty::NotUsed)) => Some(IVariableUseCertainty::NotUsed),
    (None, Some(IVariableUseCertainty::Used)) => Some(IVariableUseCertainty::Used),
    (Some(IVariableUseCertainty::NotUsed), None) => Some(IVariableUseCertainty::NotUsed),
    (Some(IVariableUseCertainty::NotUsed), Some(IVariableUseCertainty::NotUsed)) => {
      Some(IVariableUseCertainty::NotUsed)
    }
    (Some(IVariableUseCertainty::NotUsed), Some(IVariableUseCertainty::Used)) => {
      Some(IVariableUseCertainty::Used)
    }
    (Some(IVariableUseCertainty::Used), None) => Some(IVariableUseCertainty::Used),
    (Some(IVariableUseCertainty::Used), Some(IVariableUseCertainty::NotUsed)) => {
      Some(IVariableUseCertainty::Used)
    }
    (Some(IVariableUseCertainty::Used), Some(IVariableUseCertainty::Used)) => {
      Some(IVariableUseCertainty::Used)
    }
  }
}

}

