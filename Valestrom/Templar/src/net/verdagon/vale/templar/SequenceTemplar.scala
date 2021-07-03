package net.verdagon.vale.templar

import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.templar.citizen.{StructTemplar, StructTemplarCore}
import net.verdagon.vale.templar.env.{FunctionEnvironment, FunctionEnvironmentBox, IEnvironment, PackageEnvironment}
import net.verdagon.vale.templar.function.{DestructorTemplar, FunctionTemplar}
import net.verdagon.vale.vassert

class SequenceTemplar(
  opts: TemplarOptions,
    structTemplar: StructTemplar,
    destructorTemplar: DestructorTemplar) {
  def evaluate(
    env: FunctionEnvironmentBox,
    temputs: Temputs,
    exprs2: List[ReferenceExpressionTE]):
  (ExpressionT) = {
    val types2 = exprs2.map(_.resultRegister.expectReference().reference)
    val (tupleType2, mutability) = makeTupleType(env.globalEnv, temputs, types2)
    val ownership = if (mutability == MutableT) OwnT else ShareT
    val permission = if (mutability == MutableT) ReadwriteT else ReadonlyT
    val finalExpr = TupleTE(exprs2, CoordT(ownership, permission, tupleType2), tupleType2)
    (finalExpr)
  }

  def makeTupleType(
    env: IEnvironment,
    temputs: Temputs,
    types2: List[CoordT]):
  (TupleTT, MutabilityT) = {
    val (structRef, mutability) =
      structTemplar.makeSeqOrPackUnderstruct(env.globalEnv, temputs, types2, TupleNameT(types2))

    if (types2.isEmpty)
      vassert(temputs.lookupStruct(structRef).mutability == ImmutableT)
    // Make sure it's in there
    Templar.getMutability(temputs, structRef)

    val reference =
      CoordT(
        if (mutability == MutableT) OwnT else ShareT,
        if (mutability == MutableT) ReadwriteT else ReadonlyT,
        structRef)

    val _ =
      destructorTemplar.getCitizenDestructor(env, temputs, reference)

    (TupleTT(types2, structRef), mutability)
  }
}
