package net.verdagon.vale.solver

import net.verdagon.vale.{solver, vassert, vassertSome, vfail, vimpl}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

case class TentativeRune(runeIndex: Int)

object Builder {
  def apply[RuleID, Literal, Lookup]():
  Builder[RuleID, Literal, Lookup] = {
    Builder[RuleID, Literal, Lookup](
      mutable.ArrayBuffer[IRulexAR[TentativeRune, RuleID, Literal, Lookup]](),
      0,
      mutable.HashMap[TentativeRune, TentativeRune]())
  }
}

case class Builder[RuleID, Literal, Lookup](
  rules: mutable.ArrayBuffer[IRulexAR[TentativeRune, RuleID, Literal, Lookup]],
  var nextTentativeRuneIndex: Int,
  redundantRuneToOriginalRune: mutable.HashMap[TentativeRune, TentativeRune]
) {
  def addRule(rule: IRulexAR[TentativeRune, RuleID, Literal, Lookup]): Int = {
    val ruleIndex = rules.size
    rules += rule
    ruleIndex
  }
  def addRune(): TentativeRune = {
    val tentativeRuneIndex = nextTentativeRuneIndex
    nextTentativeRuneIndex = nextTentativeRuneIndex + 1
    TentativeRune(tentativeRuneIndex)
  }
  def noteRunesEqual(left: TentativeRune, right: TentativeRune): TentativeRune = {
    if (left == right) {
      left
    } else {
      val earlierRune = TentativeRune(Math.min(left.runeIndex, right.runeIndex))
      val laterRune = TentativeRune(Math.max(left.runeIndex, right.runeIndex))
      redundantRuneToOriginalRune.put(laterRune, earlierRune)
      left
    }
  }
}
