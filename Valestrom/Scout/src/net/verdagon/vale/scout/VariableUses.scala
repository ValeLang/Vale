package net.verdagon.vale.scout

import net.verdagon.vale.parser.VariabilityP
import net.verdagon.vale.{vassert, vcurious, vfail, vimpl}


case class VariableUse(
    name: IVarNameS,
    borrowed: Option[IVariableUseCertainty],
    moved: Option[IVariableUseCertainty],
    mutated: Option[IVariableUseCertainty]) {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
}

case class VariableDeclaration(
    name: IVarNameS) {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
}

case class VariableDeclarations(vars: List[VariableDeclaration]) {
  override def hashCode(): Int = vcurious()

  vassert(vars.distinct == vars)

  def ++(that: VariableDeclarations): VariableDeclarations = {
    VariableDeclarations(vars ++ that.vars)
  }
  def find(needle: String): Option[IVarNameS] = {
    vars.map(_.name).find({
      case CodeVarNameS(humanName) => humanName == needle
      case ConstructingMemberNameS(_) => false
      case ClosureParamNameS() => false
//      case UnnamedLocalNameS(_) => false
      case MagicParamNameS(_) => false
    })
  }
}

case class VariableUses(uses: List[VariableUse]) {
  override def hashCode(): Int = vcurious()

  vassert(uses.distinct == uses)

  def allUsedNames: List[IVarNameS] = uses.map(_.name)
  def markBorrowed(name: IVarNameS): VariableUses = {
    merge(VariableUse(name, Some(Used), None, None), thenMerge)
  }
  def markMoved(name: IVarNameS): VariableUses = {
    merge(VariableUse(name, None, Some(Used), None), thenMerge)
  }
  def markMutated(name: IVarNameS): VariableUses = {
    merge(VariableUse(name, None, None, Some(Used)), thenMerge)
  }
  // Incorporate this new use into
  def thenMerge(newUses: VariableUses): VariableUses = {
    combine(newUses, thenMerge)
  }
  def branchMerge(newUses: VariableUses): VariableUses = {
    combine(newUses, branchMerge)
  }
  def isBorrowed(name: IVarNameS): IVariableUseCertainty = {
    uses.find(_.name == name) match {
      case None => NotUsed
      case Some(use) => use.borrowed.getOrElse(NotUsed)
    }
  }
  def isMoved(name: IVarNameS): IVariableUseCertainty = {
    uses.find(_.name == name) match {
      case None => NotUsed
      case Some(use) => use.moved.getOrElse(NotUsed)
    }
  }
  def isMutated(name: IVarNameS): IVariableUseCertainty = {
    uses.find(_.name == name) match {
      case None => NotUsed
      case Some(use) => use.mutated.getOrElse(NotUsed)
    }
  }
  // Turns all Used into MaybeUsed. For example:
  //   fn main() int export {
  //     m = Marine();
  //     someFunction({ doThings(m); });
  //     ...
  //   }
  // Inside the lambda, it knows that m has been moved.
  // However, main() doesn't know if someFunction() *actually* called the lambda.
  // So, to main(), m is *maybe* moved.
  //
  // Note: we even consider a lambda to *maybe* have happened even if it's
  // immediately called, like:
  //   fn main() int export {
  //     m = Marine();
  //     { doThings(m); }();
  //     ...
  //   }
  // There's no particular reason we do this, and no particular reason to do
  // otherwise. Nobody should be using immediately-called anonymous blocks in
  // this language because we have unlet for that.
  def maybify(): VariableUses = {
    VariableUses(
      uses.map({ case VariableUse(name, borrowed, moved, mutated) =>
        VariableUse(name, maybify(borrowed), maybify(moved), maybify(mutated))
      }))
  }
  def maybify(use: Option[IVariableUseCertainty]) = {
    use.map({
      case Used => MaybeUsed
      case MaybeUsed => MaybeUsed
      case NotUsed => NotUsed
    })
  }
  def combine(
      that: VariableUses,
      certaintyMerger: (Option[IVariableUseCertainty], Option[IVariableUseCertainty]) => Option[IVariableUseCertainty]):
  VariableUses = {
    val mergedUses =
      (uses.map(_.name) ++ that.uses.map(_.name)).distinct.map({ name =>
        (uses.find(_.name == name), that.uses.find(_.name == name)) match {
          case (None, Some(use)) => merge(VariableUse(name, None, None, None), use, certaintyMerger)
          case (Some(use), None) => merge(use, VariableUse(name, None, None, None), certaintyMerger)
          case (Some(thisUse), Some(thatUse)) => merge(thisUse, thatUse, certaintyMerger)
        }
      })
    VariableUses(mergedUses)
  }
  private def merge(
      newUse: VariableUse,
      certaintyMerger: (Option[IVariableUseCertainty], Option[IVariableUseCertainty]) => Option[IVariableUseCertainty]):
  VariableUses = {
    uses.find(_.name == newUse.name) match {
      case None => VariableUses((uses :+ newUse).distinct)
      case Some(existingUse) => {
        VariableUses(uses.filter(_ != existingUse) :+ merge(existingUse, newUse, certaintyMerger))
      }
    }
  }
  private def merge(
      existingUse: VariableUse,
      newUse: VariableUse,
      certaintyMerger: (Option[IVariableUseCertainty], Option[IVariableUseCertainty]) => Option[IVariableUseCertainty]):
  VariableUse = {
    val VariableUse(name, newlyBorrowed, newlyMoved, newlyMutated) = newUse
    val VariableUse(_, alreadyBorrowed, alreadyMoved, alreadyMutated) = existingUse
    VariableUse(
      name,
      certaintyMerger(alreadyBorrowed, newlyBorrowed),
      certaintyMerger(alreadyMoved, newlyMoved),
      certaintyMerger(alreadyMutated, newlyMutated))
  }

  // If A happens, then B happens, we want the resulting use to reflect that.
  private def thenMerge(
      a: Option[IVariableUseCertainty],
      b: Option[IVariableUseCertainty]):
  Option[IVariableUseCertainty] = {
    (a, b) match {
      case (None, other) => other
      case (other, None) => other
      case (Some(MaybeUsed), _) => Some(MaybeUsed)
      case (_, Some(MaybeUsed)) => Some(MaybeUsed)
      case (Some(NotUsed), Some(Used)) => Some(MaybeUsed)
      case (Some(Used), Some(NotUsed)) => Some(MaybeUsed)
      case (Some(Used), Some(Used)) => Some(Used)
      case (Some(NotUsed), Some(NotUsed)) => Some(NotUsed)
      case _ => vfail("wat")
    }
  }

  // If A happens, OR B happens, we want the resulting use to reflect that.
  private def branchMerge(
      a: Option[IVariableUseCertainty],
      b: Option[IVariableUseCertainty]):
  Option[IVariableUseCertainty] = {
    (a, b) match {
      case (None, None) => None
      case (None, Some(NotUsed)) => Some(NotUsed)
      case (None, Some(MaybeUsed)) => Some(MaybeUsed)
      case (None, Some(Used)) => Some(MaybeUsed)
      case (Some(NotUsed), None) => Some(NotUsed)
      case (Some(NotUsed), Some(NotUsed)) => Some(NotUsed)
      case (Some(NotUsed), Some(MaybeUsed)) => Some(MaybeUsed)
      case (Some(NotUsed), Some(Used)) => Some(MaybeUsed)
      case (Some(MaybeUsed), None) => Some(MaybeUsed)
      case (Some(MaybeUsed), Some(NotUsed)) => Some(MaybeUsed)
      case (Some(MaybeUsed), Some(MaybeUsed)) => Some(MaybeUsed)
      case (Some(MaybeUsed), Some(Used)) => Some(MaybeUsed)
      case (Some(Used), None) => Some(MaybeUsed)
      case (Some(Used), Some(NotUsed)) => Some(MaybeUsed)
      case (Some(Used), Some(MaybeUsed)) => Some(MaybeUsed)
      case (Some(Used), Some(Used)) => Some(Used)
    }
  }
}
