package net.verdagon.vale.templar.function

import net.verdagon.vale.astronomer.{FunctionA, GlobalFunctionFamilyNameA, ImmConcreteDestructorImpreciseNameA, ImmDropImpreciseNameA, ImmInterfaceDestructorImpreciseNameA}
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.templar.OverloadTemplar.{ScoutExpectedFunctionFailure, ScoutExpectedFunctionSuccess}
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.citizen.StructTemplar
import net.verdagon.vale.templar.env._
import net.verdagon.vale.{vassert, vfail, vimpl}

import scala.collection.immutable.List

object DestructorTemplar {

  def getCitizenDestructor(
      env: IEnvironment,
      temputs: TemputsBox,
      type2: Coord):
  (Prototype2) = {
    type2.referend match {
      case PackT2(_, _) | StructRef2(_) => {// | OrdinaryClosure2(_, _, _) | TemplatedClosure2(_, _, _) => {
        OverloadTemplar.scoutExpectedFunctionForPrototype(
            env,
            temputs,
            if (type2.ownership == Share) {
              ImmConcreteDestructorImpreciseNameA()
            } else {
              GlobalFunctionFamilyNameA(CallTemplar.MUT_DESTRUCTOR_NAME)
            },
            List(),
            List(ParamFilter(type2, None)),
            List(),
            true)  match {
          case (seff @ ScoutExpectedFunctionFailure(_, _, _, _, _)) => {
            vfail("Couldn't find concrete destructor!\n" + seff.toString)
          }
          case (ScoutExpectedFunctionSuccess(p)) => (p)
        }
      }
      case InterfaceRef2(_) => {
        OverloadTemplar.scoutExpectedFunctionForPrototype(
          env,
          temputs,
          if (type2.ownership == Share) {
            ImmInterfaceDestructorImpreciseNameA()
          } else {
            GlobalFunctionFamilyNameA(CallTemplar.MUT_INTERFACE_DESTRUCTOR_NAME)
          },
          List(),
          List(ParamFilter(type2, None)),
          List(),
          true) match {
          case (seff @ ScoutExpectedFunctionFailure(_, _, _, _, _)) => {
            vfail("Couldn't find interface destructor!\n" + seff.toString)
          }
          case (ScoutExpectedFunctionSuccess(p)) => (p)
        }
      }
    }
  }

  def getArrayDestructor(
    env: IEnvironment,
    temputs: TemputsBox,
    type2: Coord):
  (Prototype2) = {
    type2.referend match { case KnownSizeArrayT2(_, _) | UnknownSizeArrayT2(_) => }
    OverloadTemplar.scoutExpectedFunctionForPrototype(
      env,
      temputs,
      if (type2.ownership == Share) {
        ImmConcreteDestructorImpreciseNameA()
      } else {
        GlobalFunctionFamilyNameA(CallTemplar.MUT_DESTRUCTOR_NAME)
      },
      List(),
      List(ParamFilter(type2, None)),
      List(),
      true) match {
      case (seff @ ScoutExpectedFunctionFailure(_, _, _, _, _)) => {
        vfail("Couldn't find array destructor!\n" + seff.toString)
      }
      case (ScoutExpectedFunctionSuccess(p)) => (p)
    }
  }

  // "Drop" is a general term that encompasses:
  // - Destruct. This means we take an owning reference and call its destructor.
  // - Unshare. This means we take a shared reference, and if it's the last one, unshare anything
  //   it's pointing at and deallocate.
  // - Unborrow. This is a no op.
  // This is quite useful for handing into array consumers.
  private def getDropFunction(env: IEnvironment, temputs: TemputsBox, type2: Coord): Prototype2 = {
    OverloadTemplar.scoutExpectedFunctionForPrototype(
      env,
      temputs,
      if (type2.ownership == Share) {
        ImmDropImpreciseNameA()
      } else {
        GlobalFunctionFamilyNameA(CallTemplar.MUT_DROP_FUNCTION_NAME)
      },
      List(),
      List(ParamFilter(type2, None)),
      List(),
      true) match {
      case (seff @ ScoutExpectedFunctionFailure(_, _, _, _, _)) => {
        vfail("Couldn't find drop function!\n" + seff.toString)
      }
      case (ScoutExpectedFunctionSuccess(p)) => (p)
    }
  }

  def generateDropFunction(
    initialBodyEnv: FunctionEnvironment,
    temputs: TemputsBox,
    originFunction1: FunctionA,
    type2: Coord):
  (FunctionHeader2) = {
    val bodyEnv = FunctionEnvironmentBox(initialBodyEnv)
    val dropExpr2 = DestructorTemplar.drop(bodyEnv, temputs, ArgLookup2(0, type2))
    val header =
      FunctionHeader2(
        bodyEnv.fullName,
        isExtern = false,
        isUserFunction = false,
        List(Parameter2(CodeVarName2("x"), None, type2)),
        Coord(Share, Void2()),
        Some(originFunction1))
    val function2 = Function2(header, List(), Block2(List(dropExpr2, Return2(VoidLiteral2()))))
    temputs.declareFunctionReturnType(header.toSignature, Coord(Share, Void2()))
    temputs.addFunction(function2)
    vassert(temputs.exactDeclaredSignatureExists(bodyEnv.fullName))
    header
  }

  def drop(
      fate: FunctionEnvironmentBox,
      temputs: TemputsBox,
      undestructedExpr2: ReferenceExpression2):
  (ReferenceExpression2) = {
    val resultExpr2 =
      undestructedExpr2.resultRegister.reference match {
        case r @ Coord(Own, referend) => {
          val destructorPrototype =
            referend match {
              case PackT2(_, understructRef) => {
                getCitizenDestructor(fate.snapshot, temputs, Coord(Own, understructRef))
              }
              case StructRef2(_) | InterfaceRef2(_) => {
                getCitizenDestructor(fate.snapshot, temputs, r)
              }
              case KnownSizeArrayT2(_, _) | UnknownSizeArrayT2(_) => {
                getArrayDestructor(fate.snapshot, temputs, r)
              }
            }
          FunctionCall2(destructorPrototype, List(undestructedExpr2))
        }
        case Coord(Borrow, _) => (Discard2(undestructedExpr2))
        case Coord(Share, _) => {
          val destroySharedCitizen =
            (temputs: TemputsBox, Coord: Coord) => {
              val destructorHeader = getCitizenDestructor(fate.snapshot, temputs, Coord)
              // We just needed to ensure it's in the temputs, so that the backend can use it
              // for when reference counts drop to zero.
              // If/when we have a GC backend, we can skip generating share destructors.
              val _ = destructorHeader
              Discard2(undestructedExpr2)
            };
          val destroySharedArray =
            (temputs: TemputsBox, Coord: Coord) => {
              val destructorHeader = getArrayDestructor(fate.snapshot, temputs, Coord)
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
              case as @ KnownSizeArrayT2(_, _) => {
                val underarrayReference2 = Coord(undestructedExpr2.resultRegister.reference.ownership, as)
                destroySharedArray(temputs, underarrayReference2)
              }
              case as @ UnknownSizeArrayT2(_) => {
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

  def generateStructDestructor(
    namedEnv: FunctionEnvironment,
      temputs: TemputsBox,
      originFunction1: FunctionA,
      params2: List[Parameter2],
      structRef: StructRef2):
  (FunctionHeader2) = {
    val destructorFullName = namedEnv.fullName

    val bodyEnv = FunctionEnvironmentBox(namedEnv)

    val structDef = temputs.lookupStruct(structRef)
    val structOwnership = if (structDef.mutability == Mutable) Own else Share
    val structBorrowOwnership = if (structDef.mutability == Mutable) Borrow else Share
    val structType = Coord(structOwnership, structDef.getRef)

    val header =
      FunctionHeader2(
        destructorFullName,
        false, false,
        params2,
        Coord(Share, Void2()),
        Some(originFunction1));

      temputs
          .declareFunctionReturnType(header.toSignature, header.returnType)

    val structArgument = ArgLookup2(0, structType)
    val memberLocalVariables =
      structDef.members.flatMap({
        case StructMember2(name, variability, ReferenceMemberType2(reference)) => {
          List(ReferenceLocalVariable2(destructorFullName.addStep(name), Final, reference))
        }
        case StructMember2(name, variability, AddressMemberType2(reference)) => {
          // See Destructure2 and its handling of addressible members for why
          // we don't include these in the destination variables.
          List()
        }
      })

    val destroyedUnletStruct = Destroy2(structArgument, structRef, memberLocalVariables)
    val destructMemberExprs =
      memberLocalVariables.map({
        case (variable) => {
          val destructMemberExpr = drop(bodyEnv, temputs, Unlet2(variable))
          destructMemberExpr
        }
      })

    val returnVoid = Return2(VoidLiteral2())

    val function2 =
      Function2(
        header,
        memberLocalVariables,
        Block2(List(destroyedUnletStruct) ++ destructMemberExprs :+ returnVoid))
    temputs.addFunction(function2)
    (function2.header)
  }

  def generateArraySequenceDestructor(
    env: FunctionEnvironment,
    temputs: TemputsBox,
    maybeOriginFunction1: Option[FunctionA],
    sequenceRefType2: Coord,
    sequence: KnownSizeArrayT2):
  (FunctionHeader2) = {
    println("turn this into just a regular destructor template function? dont see why its special.")

    val arrayOwnership = if (sequence.array.mutability == Mutable) Own else Share
    val arrayBorrowOwnership = if (sequence.array.mutability == Mutable) Borrow else Share
    val arrayRefType = Coord(arrayOwnership, sequence)

    val elementDropFunctionPrototype = getDropFunction(env, temputs, sequence.array.memberType)

    val (ifunction1InterfaceRef, elementDropFunctionAsIFunctionSubstructStructRef, constructorPrototype) =
      StructTemplar.prototypeToAnonymousIFunctionSubstruct(env, temputs, elementDropFunctionPrototype)

    val ifunctionExpression =
      StructToInterfaceUpcast2(
        FunctionCall2(constructorPrototype, List()),
        ifunction1InterfaceRef)

    val function2 =
      Function2(
        FunctionHeader2(
          env.fullName,
          false, false,
          List(Parameter2(CodeVarName2("this"), None, arrayRefType)),
          Coord(Share, Void2()),
          maybeOriginFunction1),
        List(),
        Block2(
          List(
            DestroyArraySequenceIntoFunction2(
              ArgLookup2(0, arrayRefType),
              sequence,
              ifunctionExpression),
            Return2(VoidLiteral2()))))

    temputs.declareFunctionReturnType(function2.header.toSignature, function2.header.returnType)
    temputs.addFunction(function2)
    function2.header
  }

  def generateUnknownSizeArrayDestructor(
      env: FunctionEnvironment,
      temputs: TemputsBox,
      maybeOriginFunction1: Option[FunctionA],
      arrayRefType2: Coord,
      array: UnknownSizeArrayT2):
  (FunctionHeader2) = {
    val arrayOwnership = if (array.array.mutability == Mutable) Own else Share
    val arrayBorrowOwnership = if (array.array.mutability == Mutable) Borrow else Share

    val elementDropFunctionPrototype = getDropFunction(env, temputs, array.array.memberType)

    val (ifunction1InterfaceRef, elementDropFunctionAsIFunctionSubstructStructRef, constructorPrototype) =
      StructTemplar.prototypeToAnonymousIFunctionSubstruct(env, temputs, elementDropFunctionPrototype)

    val ifunctionExpression =
      StructToInterfaceUpcast2(
        FunctionCall2(constructorPrototype, List()),
        ifunction1InterfaceRef)

    val function2 =
      Function2(
        FunctionHeader2(
          env.fullName,
          false, false,
          List(Parameter2(CodeVarName2("this"), None, arrayRefType2)),
          Coord(Share, Void2()),
          maybeOriginFunction1),
        List(),
        Block2(
          List(
            DestroyUnknownSizeArray2(
              ArgLookup2(0, arrayRefType2),
              array,
              ifunctionExpression),
            Return2(VoidLiteral2()))))

      temputs.declareFunctionReturnType(function2.header.toSignature, function2.header.returnType)
      temputs.addFunction(function2)
    (function2.header)
  }

  def getImmConcreteDestructor(
    temputs: TemputsBox,
    env: IEnvironment,
    structRef2: StructRef2):
  Prototype2 = {
    vassert(Templar.getMutability(temputs, structRef2) == Immutable)

    OverloadTemplar.scoutExpectedFunctionForPrototype(
      env,
      temputs,
      ImmConcreteDestructorImpreciseNameA(),
      List(),
      List(ParamFilter(Coord(Share, structRef2), None)),
      List(),
      true) match {
      case (seff @ ScoutExpectedFunctionFailure(_, _, _, _, _)) => {
        vfail("Couldn't find function to call!\n" + seff.toString)
      }
      case (ScoutExpectedFunctionSuccess(p)) => (p)
    }
  }

  def getImmInterfaceDestructor(
    temputs: TemputsBox,
    env: IEnvironment,
    interfaceRef2: InterfaceRef2):
  Prototype2 = {
    vassert(Templar.getMutability(temputs, interfaceRef2) == Immutable)

    val prototype =
      OverloadTemplar.scoutExpectedFunctionForPrototype(
        env,
        temputs,
        ImmInterfaceDestructorImpreciseNameA(),
        List(),
        List(ParamFilter(Coord(Share, interfaceRef2), None)),
        List(),
        true) match {
        case (seff @ ScoutExpectedFunctionFailure(_, _, _, _, _)) => {
          vfail("Couldn't find function to call!\n" + seff.toString)
        }
        case (ScoutExpectedFunctionSuccess(p)) => (p)
      }
    prototype
  }

  def getImmInterfaceDestructorOverride(
    temputs: TemputsBox,
    env: IEnvironment,
    structRef2: StructRef2,
    implementedInterfaceRefT: InterfaceRef2):
  Prototype2 = {
    vassert(Templar.getMutability(temputs, structRef2) == Immutable)
    vassert(Templar.getMutability(temputs, implementedInterfaceRefT) == Immutable)

    val sefResult =
      OverloadTemplar.scoutExpectedFunctionForPrototype(
        env,
        temputs,
        ImmInterfaceDestructorImpreciseNameA(),
        List(),
        List(ParamFilter(Coord(Share, structRef2), Some(Override2(implementedInterfaceRefT)))),
        List(),
        true)
    sefResult match {
      case ScoutExpectedFunctionSuccess(prototype) => prototype
      case ScoutExpectedFunctionFailure(_, _, _, _, _) => {
        vfail(sefResult.toString)
      }
    }
  }
}
