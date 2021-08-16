package net.verdagon.vale.scout

import net.verdagon.vale.scout.rules.ITypeSR
import net.verdagon.vale.{vcurious, vfail}

package object predictor {
  case class ConclusionsBox(var conclusions: Conclusions) {
    override def hashCode(): Int = vfail() // Shouldnt hash, is mutable

    def knowableValueRunes: Set[IRuneS] = conclusions.knowableValueRunes
    def predictedTypeByRune: Map[IRuneS, ITypeSR] = conclusions.predictedTypeByRune
    def markRuneValueKnowable(rune: IRuneS): Unit = {
      conclusions = conclusions.markRuneValueKnowable(rune)
    }
    def markRuneTypeKnown(rune: IRuneS, tyype: ITypeSR): Unit = {
      conclusions = conclusions.markRuneTypeKnown(rune, tyype)
    }
  }

  case class Conclusions(
      knowableValueRunes: Set[IRuneS],
      predictedTypeByRune: Map[IRuneS, ITypeSR]) {
    override def hashCode(): Int = vcurious()

    def markRuneValueKnowable(rune: IRuneS): Conclusions = {
      Conclusions(
        knowableValueRunes + rune,
        predictedTypeByRune)
    }
    def markRuneTypeKnown(rune: IRuneS, tyype: ITypeSR): Conclusions = {
      Conclusions(
        knowableValueRunes,
        predictedTypeByRune + (rune -> tyype))
    }
  }
}
