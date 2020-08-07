package net.verdagon.vale.templar

import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.scout.{IEnvironment => _, FunctionEnvironment => _, Environment => _, _}
import net.verdagon.vale.templar.citizen.StructTemplar
import net.verdagon.vale.templar.env.{FunctionEnvironment, FunctionEnvironmentBox, IEnvironment, NamespaceEnvironment}
import net.verdagon.vale.templar.function.DestructorTemplar
import net.verdagon.vale.{vassert, vfail}

object PackTemplar {
  val emptyPackExpression: PackE2 = PackE2(List(), Coord(Share, Program2.emptyPackType), Program2.emptyPackType)
}

class PackTemplar(
  opts: TemplarOptions,
    structTemplar: StructTemplar,
    destructorTemplar: DestructorTemplar) {
  def makePackType(env: NamespaceEnvironment[IName2], temputs: TemputsBox, types2: List[Coord]):
  (PackT2, Mutability) = {
    val (structRef, mutability) =
      structTemplar.makeSeqOrPackUnderstruct(env, temputs, types2, TupleName2(types2))

    if (types2.isEmpty)
      vassert(temputs.lookupStruct(structRef).mutability == Immutable)

    val packType2 = PackT2(types2, structRef);

    val packReferenceType2 = Coord(if (mutability == Mutable) Own else Share, packType2)

    val _ =
      destructorTemplar.getCitizenDestructor(env, temputs, packReferenceType2)

    (packType2, mutability)
  }
}
