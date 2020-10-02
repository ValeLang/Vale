package net.verdagon.vale.templar.function

import net.verdagon.vale.astronomer._
import net.verdagon.vale.templar.OverloadTemplar.{ScoutExpectedFunctionFailure, ScoutExpectedFunctionSuccess}
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.citizen.StructTemplar
import net.verdagon.vale.templar.env._
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.templar.types._
import net.verdagon.vale.{vassert, vfail}

import scala.collection.immutable.List

class DropHelper(
    opts: TemplarOptions,
    destructorTemplar: DestructorTemplar) {
  def drop(
    fate: FunctionEnvironmentBox,
    temputs: Temputs,
    undestructedExpr2: ReferenceExpression2):
  (ReferenceExpression2) = {
    val resultExpr2 =
      undestructedExpr2.resultRegister.reference match {
        case r@Coord(Own, referend) => {
          val destructorPrototype =
            referend match {
              case PackT2(_, understructRef) => {
                destructorTemplar.getCitizenDestructor(fate.snapshot, temputs, Coord(Own, understructRef))
              }
              case StructRef2(_) | InterfaceRef2(_) => {
                destructorTemplar.getCitizenDestructor(fate.snapshot, temputs, r)
              }
              case KnownSizeArrayT2(_, _) | UnknownSizeArrayT2(_) => {
                destructorTemplar.getArrayDestructor(fate.snapshot, temputs, r)
              }
            }
          FunctionCall2(destructorPrototype, List(undestructedExpr2))
        }
        case Coord(Borrow, _) => (Discard2(undestructedExpr2))
        case Coord(Weak, _) => (Discard2(undestructedExpr2))
        case Coord(Share, _) => {
          val destroySharedCitizen =
            (temputs: Temputs, Coord: Coord) => {
              val destructorHeader = destructorTemplar.getCitizenDestructor(fate.snapshot, temputs, Coord)
              // We just needed to ensure it's in the temputs, so that the backend can use it
              // for when reference counts drop to zero.
              // If/when we have a GC backend, we can skip generating share destructors.
              val _ = destructorHeader
              Discard2(undestructedExpr2)
            };
          val destroySharedArray =
            (temputs: Temputs, Coord: Coord) => {
              val destructorHeader = destructorTemplar.getArrayDestructor(fate.snapshot, temputs, Coord)
              // We just needed to ensure it's in the temputs, so that the backend can use it
              // for when reference counts drop to zero.
              // If/when we have a GC backend, we can skip generating share destructors.
              val _ = destructorHeader
              Discard2(undestructedExpr2)
            };


          val unshareExpr2 =
            undestructedExpr2.resultRegister.reference.referend match {
              case Never2() => undestructedExpr2
              case Int2() | Str2() | Bool2() | Float2() | Void2() => {
                Discard2(undestructedExpr2)
              }
              case as@KnownSizeArrayT2(_, _) => {
                val underarrayReference2 = Coord(undestructedExpr2.resultRegister.reference.ownership, as)
                destroySharedArray(temputs, underarrayReference2)
              }
              case as@UnknownSizeArrayT2(_) => {
                val underarrayReference2 = Coord(undestructedExpr2.resultRegister.reference.ownership, as)
                destroySharedArray(temputs, underarrayReference2)
              }
              case OverloadSet(overloadSetEnv, name, voidStructRef) => {
                val understructReference2 = undestructedExpr2.resultRegister.reference.copy(referend = voidStructRef)
                destroySharedCitizen(temputs, understructReference2)
              }
              case PackT2(_, understruct2) => {
                val understructReference2 = undestructedExpr2.resultRegister.reference.copy(referend = understruct2)
                destroySharedCitizen(temputs, understructReference2)
              }
              case TupleT2(_, understruct2) => {
                val understructReference2 = undestructedExpr2.resultRegister.reference.copy(referend = understruct2)
                destroySharedCitizen(temputs, understructReference2)
              }
              case StructRef2(_) | InterfaceRef2(_) => {
                destroySharedCitizen(temputs, undestructedExpr2.resultRegister.reference)
              }
            }
          unshareExpr2
        }
      }
    vassert(
      resultExpr2.resultRegister.reference == Coord(Share, Void2()) ||
        resultExpr2.resultRegister.reference == Coord(Share, Never2()))
    resultExpr2
  }

  def generateDropFunction(
    initialBodyEnv: FunctionEnvironment,
    temputs: Temputs,
    originFunction1: FunctionA,
    type2: Coord):
  (FunctionHeader2) = {
    val bodyEnv = FunctionEnvironmentBox(initialBodyEnv)
    val dropExpr2 = drop(bodyEnv, temputs, ArgLookup2(0, type2))
    val header =
      FunctionHeader2(
        bodyEnv.fullName,
        List(),
        List(Parameter2(CodeVarName2("x"), None, type2)),
        Coord(Share, Void2()),
        Some(originFunction1))
    val function2 = Function2(header, List(), Block2(List(dropExpr2, Return2(VoidLiteral2()))))
    temputs.declareFunctionReturnType(header.toSignature, Coord(Share, Void2()))
    temputs.addFunction(function2)
    vassert(temputs.exactDeclaredSignatureExists(bodyEnv.fullName))
    header
  }
}