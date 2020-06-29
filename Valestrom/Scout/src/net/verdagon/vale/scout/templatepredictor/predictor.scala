package net.verdagon.vale.scout

import net.verdagon.vale.scout.rules.ITypeSR

package object predictor {
  case class ConclusionsBox(var conclusions: Conclusions) {
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
