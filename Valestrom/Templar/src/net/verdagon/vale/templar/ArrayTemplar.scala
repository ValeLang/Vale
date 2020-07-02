package net.verdagon.vale.templar

import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.parser.MutableP
import net.verdagon.vale.templar.citizen.{StructTemplar, StructTemplarCore}
import net.verdagon.vale.templar.env.{IEnvironment, IEnvironmentBox}
import net.verdagon.vale.templar.function.DestructorTemplar
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.templata._

object ArrayTemplar {

  def makeArraySequenceType(env: IEnvironment, temputs: TemputsBox, mutability: Mutability, size: Int, type2: Coord):
  (KnownSizeArrayT2) = {
//    val tupleMutability =
//      StructTemplarCore.getCompoundTypeMutability(temputs, List(type2))
    val tupleMutability = Templar.getMutability(temputs, type2.referend)
    val rawArrayT2 = RawArrayT2(type2, tupleMutability)

    temputs.arraySequenceTypes.get(size, rawArrayT2) match {
      case Some(arraySequenceT2) => (arraySequenceT2)
      case None => {
        val arraySeqType = KnownSizeArrayT2(size, rawArrayT2)
        val arraySequenceRefType2 =
          Coord(
            if (tupleMutability == Mutable) Own else Share,
            arraySeqType)
        val _ =
          DestructorTemplar.getArrayDestructor(
            env,
            temputs,
            arraySequenceRefType2)
        (arraySeqType)
      }
    }
  }

  def makeUnknownSizeArrayType(env: IEnvironment, temputs: TemputsBox, type2: Coord, arrayMutability: Mutability):
  (UnknownSizeArrayT2) = {
    val rawArrayT2 = RawArrayT2(type2, arrayMutability)

    temputs.unknownSizeArrayTypes.get(rawArrayT2) match {
      case Some(arraySequenceT2) => (arraySequenceT2)
      case None => {
        val runtimeArrayType = UnknownSizeArrayT2(rawArrayT2)
        val runtimeArrayRefType2 =
          Coord(
            if (arrayMutability == Mutable) Own else Share,
            runtimeArrayType)
        val _ =
          DestructorTemplar.getArrayDestructor(
            env, temputs, runtimeArrayRefType2)
        (runtimeArrayType)
      }
    }
  }
}
