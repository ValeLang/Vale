package net.verdagon.vale.templar

import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.templar.citizen.{StructTemplar, StructTemplarCore}
import net.verdagon.vale.templar.env.{FunctionEnvironment, FunctionEnvironmentBox, IEnvironment, NamespaceEnvironment}
import net.verdagon.vale.templar.function.{DestructorTemplar, FunctionTemplar}
import net.verdagon.vale.vassert

object SequenceTemplar {
  def evaluate(
    env: FunctionEnvironmentBox,
    temputs: TemputsBox,
    exprs2: List[ReferenceExpression2]):
  (Expression2) = {

    val types2 = exprs2.map(_.resultRegister.expectReference().reference)
    if (types2.toSet.size == 1) {
      val memberType = types2.toSet.head
      // Theyre all the same type, so make it an array.
      val mutability = StructTemplarCore.getCompoundTypeMutability(temputs, List(memberType))
      val arraySequenceType = ArrayTemplar.makeArraySequenceType(env.snapshot, temputs, mutability, types2.size, memberType)
      val ownership = if (arraySequenceType.array.mutability == Mutable) Own else Share
      val finalExpr = ArraySequenceE2(exprs2, Coord(ownership, arraySequenceType), arraySequenceType)
      (finalExpr)
    } else {
      val (tupleType2, mutability) = makeTupleType(env.globalEnv, temputs, types2)
      val ownership = if (mutability == Mutable) Own else Share
      val finalExpr = TupleE2(exprs2, Coord(ownership, tupleType2), tupleType2)
      (finalExpr)
    }
  }

  private def makeTupleType(
    env: NamespaceEnvironment[IName2],
    temputs: TemputsBox,
    types2: List[Coord]):
  (TupleT2, Mutability) = {
    val (structRef, mutability) =
      StructTemplar.makeSeqOrPackUnderstruct(env, temputs, types2, TupleName2(types2))

    if (types2.isEmpty)
      vassert(temputs.lookupStruct(structRef).mutability == Immutable)
    // Make sure it's in there
    Templar.getMutability(temputs, structRef)

    val reference =
      Coord(
        if (mutability == Mutable) Own else Share,
        structRef)

    val _ =
      DestructorTemplar.getCitizenDestructor(env, temputs, reference)

    (TupleT2(types2, structRef), mutability)
  }
}
