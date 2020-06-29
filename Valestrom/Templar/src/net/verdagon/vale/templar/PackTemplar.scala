package net.verdagon.vale.templar

import net.verdagon.vale.astronomer.PackAE
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.scout.{IEnvironment => _, FunctionEnvironment => _, Environment => _, _}
import net.verdagon.vale.templar.ExpressionTemplar.evaluateAndCoerceToReferenceExpression
import net.verdagon.vale.templar.citizen.StructTemplar
import net.verdagon.vale.templar.env.{FunctionEnvironment, FunctionEnvironmentBox, IEnvironment, NamespaceEnvironment}
import net.verdagon.vale.templar.function.DestructorTemplar
import net.verdagon.vale.{vassert, vfail}

object PackTemplar {
  val emptyPackExpression: PackE2 = PackE2(List(), Coord(Share, Program2.emptyPackType), Program2.emptyPackType)

  def evaluate(temputs: TemputsBox, fate: FunctionEnvironmentBox, packExpr1: PackAE):
  (ReferenceExpression2, Set[Coord]) = {

    val (argExprs2, returnsFromElements) =
      ExpressionTemplar.evaluateAndCoerceToReferenceExpressions(temputs, fate, packExpr1.elements);

    // Simplify 1-element packs
    val finalExpr2 =
      argExprs2 match {
        case List(onlyExpr2) => {
          (onlyExpr2)
        }
        case _ => {
          val types2 =
            argExprs2.map(
              expr2 => expr2.resultRegister.expectReference().reference)
          val (packType2, mutability) = makePackType(fate.globalEnv, temputs, types2)
          val ownership = if (mutability == Mutable) Own else Share
          val expression = PackE2(argExprs2, Coord(ownership, packType2), packType2)
          (expression)
        }
      };

    (finalExpr2, returnsFromElements)
  }

  def makePackType(env: NamespaceEnvironment[IName2], temputs: TemputsBox, types2: List[Coord]):
  (PackT2, Mutability) = {
    val (structRef, mutability) =
      StructTemplar.makeSeqOrPackUnderstruct(env, temputs, types2, TupleName2(types2))

    if (types2.isEmpty)
      vassert(temputs.lookupStruct(structRef).mutability == Immutable)

    val packType2 = PackT2(types2, structRef);

    val packReferenceType2 = Coord(if (mutability == Mutable) Own else Share, packType2)

    val _ =
      DestructorTemplar.getCitizenDestructor(env, temputs, packReferenceType2)

    (packType2, mutability)
  }
}
