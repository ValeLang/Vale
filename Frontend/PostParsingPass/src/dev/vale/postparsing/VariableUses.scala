package dev.vale.postparsing

import dev.vale.{vassert, vcurious, vfail}
import dev.vale.vimpl


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

case class VariableDeclarations(vars: Vector[VariableDeclaration]) {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()

  vassert(vars.distinct == vars)

  def ++(that: VariableDeclarations): VariableDeclarations = {
    VariableDeclarations(vars ++ that.vars)
  }
  def find(needle: IImpreciseNameS): Option[IVarNameS] = {
    (needle match {
      case CodeNameS(needle) => {
        vars.map(_.name).collect({ case v @ CodeVarNameS(hay) if hay == needle => v })
      }
      case IterableNameS(needle) => {
        vars.map(_.name).collect({ case v @ IterableNameS(hay) if hay == needle => v })
      }
      case IteratorNameS(needle) => {
        vars.map(_.name).collect({ case v @ IteratorNameS(hay) if hay == needle => v })
      }
      case IterationOptionNameS(needle) => {
        vars.map(_.name).collect({ case v @ IterationOptionNameS(hay) if hay == needle => v })
      }
    }).headOption
  }
}

case class VariableUses(uses: Vector[VariableUse]) {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()

  vassert(uses.map(_.name).distinct == uses.map(_.name))

  def allUsedNames: Vector[IVarNameS] = uses.map(_.name)
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
        VariableUses(uses.filter(_.name != existingUse.name) :+ merge(existingUse, newUse, certaintyMerger))
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
      case (Some(NotUsed), Some(Used)) => Some(Used)
      case (Some(Used), Some(NotUsed)) => Some(Used)
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
      case (None, Some(Used)) => Some(Used)
      case (Some(NotUsed), None) => Some(NotUsed)
      case (Some(NotUsed), Some(NotUsed)) => Some(NotUsed)
      case (Some(NotUsed), Some(Used)) => Some(Used)
      case (Some(Used), None) => Some(Used)
      case (Some(Used), Some(NotUsed)) => Some(Used)
      case (Some(Used), Some(Used)) => Some(Used)
    }
  }
}
