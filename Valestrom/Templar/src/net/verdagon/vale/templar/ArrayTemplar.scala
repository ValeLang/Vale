package net.verdagon.vale.templar

import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.parser.MutableP
import net.verdagon.vale.templar.citizen.{StructTemplar, StructTemplarCore}
import net.verdagon.vale.templar.env.{IEnvironment, IEnvironmentBox}
import net.verdagon.vale.templar.function.DestructorTemplar
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.templata._

trait IArrayTemplarDelegate {
  def getArrayDestructor(
    env: IEnvironment,
    temputs: Temputs,
    type2: Coord):
  (Prototype2)
}

class ArrayTemplar(opts: TemplarOptions, delegate: IArrayTemplarDelegate) {

  def makeArraySequenceType(env: IEnvironment, temputs: Temputs, mutability: Mutability, size: Int, type2: Coord):
  (KnownSizeArrayT2) = {
//    val tupleMutability =
//      StructTemplarCore.getCompoundTypeMutability(temputs, List(type2))
    val tupleMutability = Templar.getMutability(temputs, type2.referend)
    val rawArrayT2 = RawArrayT2(type2, tupleMutability)

    temputs.getArraySequenceType(size, rawArrayT2) match {
      case Some(arraySequenceT2) => (arraySequenceT2)
      case None => {
        val arraySeqType = KnownSizeArrayT2(size, rawArrayT2)
        val arraySeqOwnership = if (tupleMutability == Mutable) Own else Share
        val arraySeqPermission = if (tupleMutability == Mutable) Readwrite else Readonly
        val arraySequenceRefType2 = Coord(arraySeqOwnership, arraySeqPermission, arraySeqType)
        val _ =
          delegate.getArrayDestructor(
            env,
            temputs,
            arraySequenceRefType2)
        (arraySeqType)
      }
    }
  }

  def makeUnknownSizeArrayType(env: IEnvironment, temputs: Temputs, type2: Coord, arrayMutability: Mutability):
  (UnknownSizeArrayT2) = {
    val rawArrayT2 = RawArrayT2(type2, arrayMutability)

    temputs.getUnknownSizeArray(rawArrayT2) match {
      case Some(arraySequenceT2) => (arraySequenceT2)
      case None => {
        val runtimeArrayType = UnknownSizeArrayT2(rawArrayT2)
        val runtimeArrayRefType2 =
          Coord(
            if (arrayMutability == Mutable) Own else Share,
            if (arrayMutability == Mutable) Readwrite else Readonly,
            runtimeArrayType)
        val _ =
          delegate.getArrayDestructor(
            env, temputs, runtimeArrayRefType2)
        (runtimeArrayType)
      }
    }
  }
}
