package net.verdagon.vale.templar.function

import net.verdagon.vale.astronomer.{AbstractAP, AtomAP, CallAR, CodeRuneA, CodeTypeNameA, CodeVarNameA, ComponentsAR, CoordTemplataType, EqualsAR, FunctionA, FunctionNameA, FunctionTemplataType, GeneratedBodyA, GlobalFunctionFamilyNameA, ImmConcreteDestructorImpreciseNameA, ImmConcreteDestructorNameA, ImmDropImpreciseNameA, ImmDropNameA, ImmInterfaceDestructorImpreciseNameA, ImmInterfaceDestructorNameA, KindTemplataType, LocalVariableA, MutabilityAT, NameAT, OrAR, OverrideAP, OwnershipAT, OwnershipTemplataType, ParameterA, PermissionAT, PermissionTemplataType, RuneAT, TemplateTemplataType, TemplexAR, UserFunctionA}
import net.verdagon.vale.parser.{FinalP, OwnP, ReadonlyP, ReadwriteP, ShareP}
import net.verdagon.vale.scout.{CodeLocationS, NotUsed, RangeS, Used}
import net.verdagon.vale.templar.types.{CoordT, _}
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.templar.OverloadTemplar.{ScoutExpectedFunctionFailure, ScoutExpectedFunctionSuccess}
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.citizen.StructTemplar
import net.verdagon.vale.templar.env._
import net.verdagon.vale.templar.expression.CallTemplar
import net.verdagon.vale.{PackageCoordinate, vassert, vfail, vimpl}

import scala.collection.immutable.List

class DestructorTemplar(
    opts: TemplarOptions,
    structTemplar: StructTemplar,
    overloadTemplar: OverloadTemplar) {
  def getCitizenDestructor(
    env: IEnvironment,
    temputs: Temputs,
    type2: CoordT):
  (PrototypeT) = {
    type2.kind match {
      case PackTT(_, _) | StructRefT(_) => { // | OrdinaryClosure2(_, _, _) | TemplatedClosure2(_, _, _) => {
        overloadTemplar.scoutExpectedFunctionForPrototype(
          env,
          temputs,
          RangeS.internal(-1663),
          if (type2.ownership == ShareT) {
            ImmConcreteDestructorImpreciseNameA()
          } else {
            GlobalFunctionFamilyNameA(CallTemplar.MUT_DESTRUCTOR_NAME)
          },
          List(),
          List(ParamFilter(type2, None)),
          List(),
          true) match {
          case (seff@ScoutExpectedFunctionFailure(_, _, _, _, _)) => {
            throw CompileErrorExceptionT(RangedInternalErrorT(RangeS.internal(-1674), "Couldn't find concrete destructor!\n" + seff.toString))
          }
          case (ScoutExpectedFunctionSuccess(p)) => (p)
        }
      }
      case InterfaceRefT(_) => {
        overloadTemplar.scoutExpectedFunctionForPrototype(
          env,
          temputs,
          RangeS.internal(-1668),
          if (type2.ownership == ShareT) {
            ImmInterfaceDestructorImpreciseNameA()
          } else {
            GlobalFunctionFamilyNameA(CallTemplar.MUT_INTERFACE_DESTRUCTOR_NAME)
          },
          List(),
          List(ParamFilter(type2, None)),
          List(),
          true) match {
          case (seff@ScoutExpectedFunctionFailure(_, _, _, _, _)) => {
            throw CompileErrorExceptionT(RangedInternalErrorT(RangeS.internal(-1674), "Couldn't find interface destructor!\n" + seff.toString))
          }
          case (ScoutExpectedFunctionSuccess(p)) => (p)
        }
      }
    }
  }

  def getArrayDestructor(
    env: IEnvironment,
    temputs: Temputs,
    type2: CoordT):
  (PrototypeT) = {
    type2.kind match {
      case StaticSizedArrayTT(_, _) | RuntimeSizedArrayTT(_) =>
    }
    overloadTemplar.scoutExpectedFunctionForPrototype(
      env,
      temputs,
      RangeS.internal(-16721),
      if (type2.ownership == ShareT) {
        ImmConcreteDestructorImpreciseNameA()
      } else {
        GlobalFunctionFamilyNameA(CallTemplar.MUT_DESTRUCTOR_NAME)
      },
      List(),
      List(ParamFilter(type2, None)),
      List(),
      true) match {
      case (seff@ScoutExpectedFunctionFailure(_, _, _, _, _)) => {
        throw CompileErrorExceptionT(RangedInternalErrorT(RangeS.internal(-1674), "Couldn't find array destructor!\n" + seff.toString))
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
  private def getDropFunction(env: IEnvironment, temputs: Temputs, type2: CoordT): PrototypeT = {
    overloadTemplar.scoutExpectedFunctionForPrototype(
      env,
      temputs,
      RangeS.internal(-1676),
      if (type2.ownership == ShareT) {
        ImmDropImpreciseNameA()
      } else {
        GlobalFunctionFamilyNameA(CallTemplar.MUT_DROP_FUNCTION_NAME)
      },
      List(),
      List(ParamFilter(type2, None)),
      List(),
      true) match {
      case (seff@ScoutExpectedFunctionFailure(_, _, _, _, _)) => {
        throw CompileErrorExceptionT(RangedInternalErrorT(RangeS.internal(-1674), "Couldn't find drop function!\n" + seff.toString))
      }
      case (ScoutExpectedFunctionSuccess(p)) => (p)
    }
  }

  def drop(
    fate: FunctionEnvironmentBox,
    temputs: Temputs,
    undestructedExpr2: ReferenceExpressionTE):
  (ReferenceExpressionTE) = {
    val resultExpr2 =
      undestructedExpr2.resultRegister.reference match {
        case r@CoordT(OwnT, ReadwriteT, kind) => {
          val destructorPrototype =
            kind match {
              case PackTT(_, understructRef) => {
                getCitizenDestructor(fate.snapshot, temputs, CoordT(OwnT, ReadwriteT, understructRef))
              }
              case StructRefT(_) | InterfaceRefT(_) => {
                getCitizenDestructor(fate.snapshot, temputs, r)
              }
              case StaticSizedArrayTT(_, _) | RuntimeSizedArrayTT(_) => {
                getArrayDestructor(fate.snapshot, temputs, r)
              }
            }
          FunctionCallTE(destructorPrototype, List(undestructedExpr2))
        }
        case CoordT(ConstraintT, _, _) => (DiscardTE(undestructedExpr2))
        case CoordT(WeakT, _, _) => (DiscardTE(undestructedExpr2))
        case CoordT(ShareT, ReadonlyT, _) => {
          val destroySharedCitizen =
            (temputs: Temputs, Coord: CoordT) => {
              val destructorHeader = getCitizenDestructor(fate.snapshot, temputs, Coord)
              // We just needed to ensure it's in the temputs, so that the backend can use it
              // for when reference counts drop to zero.
              // If/when we have a GC backend, we can skip generating share destructors.
              val _ = destructorHeader
              DiscardTE(undestructedExpr2)
            };
          val destroySharedArray =
            (temputs: Temputs, Coord: CoordT) => {
              val destructorHeader = getArrayDestructor(fate.snapshot, temputs, Coord)
              // We just needed to ensure it's in the temputs, so that the backend can use it
              // for when reference counts drop to zero.
              // If/when we have a GC backend, we can skip generating share destructors.
              val _ = destructorHeader
              DiscardTE(undestructedExpr2)
            };


          val unshareExpr2 =
            undestructedExpr2.resultRegister.reference.kind match {
              case NeverT() => undestructedExpr2
              case IntT(_) | StrT() | BoolT() | FloatT() | VoidT() => {
                DiscardTE(undestructedExpr2)
              }
              case as@StaticSizedArrayTT(_, _) => {
                val underarrayReference2 =
                  CoordT(
                    undestructedExpr2.resultRegister.reference.ownership,
                    undestructedExpr2.resultRegister.reference.permission,
                    as)
                destroySharedArray(temputs, underarrayReference2)
              }
              case as@RuntimeSizedArrayTT(_) => {
                val underarrayReference2 =
                  CoordT(
                    undestructedExpr2.resultRegister.reference.ownership,
                    undestructedExpr2.resultRegister.reference.permission,
                    as)
                destroySharedArray(temputs, underarrayReference2)
              }
              case OverloadSet(overloadSetEnv, name, voidStructRef) => {
                val understructReference2 = undestructedExpr2.resultRegister.reference.copy(kind = voidStructRef)
                destroySharedCitizen(temputs, understructReference2)
              }
              case PackTT(_, understruct2) => {
                val understructReference2 = undestructedExpr2.resultRegister.reference.copy(kind = understruct2)
                destroySharedCitizen(temputs, understructReference2)
              }
              case TupleTT(_, understruct2) => {
                val understructReference2 = undestructedExpr2.resultRegister.reference.copy(kind = understruct2)
                destroySharedCitizen(temputs, understructReference2)
              }
              case StructRefT(_) | InterfaceRefT(_) => {
                destroySharedCitizen(temputs, undestructedExpr2.resultRegister.reference)
              }
            }
          unshareExpr2
        }
      }
    vassert(
      resultExpr2.resultRegister.reference == CoordT(ShareT, ReadonlyT, VoidT()) ||
        resultExpr2.resultRegister.reference == CoordT(ShareT, ReadonlyT, NeverT()))
    resultExpr2
  }

  def generateDropFunction(
    initialBodyEnv: FunctionEnvironment,
    temputs: Temputs,
    originFunction1: FunctionA,
    type2: CoordT):
  (FunctionHeaderT) = {
    val bodyEnv = FunctionEnvironmentBox(initialBodyEnv)
    val dropExpr2 = drop(bodyEnv, temputs, ArgLookupTE(0, type2))
    val header =
      FunctionHeaderT(
        bodyEnv.fullName,
        List(),
        List(ParameterT(CodeVarNameT("x"), None, type2)),
        CoordT(ShareT, ReadonlyT, VoidT()),
        Some(originFunction1))
    val function2 = FunctionT(header, List(), BlockTE(List(dropExpr2, ReturnTE(VoidLiteralTE()))))
    temputs.declareFunctionReturnType(header.toSignature, CoordT(ShareT, ReadonlyT, VoidT()))
    temputs.addFunction(function2)
    vassert(temputs.getDeclaredSignatureOrigin(bodyEnv.fullName) == Some(originFunction1.range))
    header
  }

  def generateStructDestructor(
    namedEnv: FunctionEnvironment,
    temputs: Temputs,
    originFunction1: FunctionA,
    params2: List[ParameterT],
    structRef: StructRefT):
  (FunctionHeaderT) = {
    val destructorFullName = namedEnv.fullName

    val bodyEnv = FunctionEnvironmentBox(namedEnv)

    val structDef = temputs.lookupStruct(structRef)
    val structOwnership = if (structDef.mutability == MutableT) OwnT else ShareT
    val structPermission = if (structDef.mutability == MutableT) ReadwriteT else ReadonlyT
    val structBorrowOwnership = if (structDef.mutability == MutableT) ConstraintT else ShareT
    val structType = CoordT(structOwnership, structPermission, structDef.getRef)

    val header =
      FunctionHeaderT(
        destructorFullName,
        List(),
        params2,
        CoordT(ShareT, ReadonlyT, VoidT()),
        Some(originFunction1));

    temputs
      .declareFunctionReturnType(header.toSignature, header.returnType)

    val structArgument = ArgLookupTE(0, structType)
    val memberLocalVariables =
      structDef.members.flatMap({
        case StructMemberT(name, variability, ReferenceMemberTypeT(reference)) => {
          List(ReferenceLocalVariableT(destructorFullName.addStep(name), FinalT, reference))
        }
        case StructMemberT(name, variability, AddressMemberTypeT(reference)) => {
          // See Destructure2 and its handling of addressible members for why
          // we don't include these in the destination variables.
          List()
        }
      })

    val destroyedUnletStruct = DestroyTE(structArgument, structRef, memberLocalVariables)
    val destructMemberExprs =
      memberLocalVariables.map({
        case (variable) => {
          val destructMemberExpr = drop(bodyEnv, temputs, UnletTE(variable))
          destructMemberExpr
        }
      })

    val returnVoid = ReturnTE(VoidLiteralTE())

    val function2 =
      FunctionT(
        header,
        memberLocalVariables,
        BlockTE(List(destroyedUnletStruct) ++ destructMemberExprs :+ returnVoid))
    temputs.addFunction(function2)
    (function2.header)
  }

  def generateStaticSizedArrayDestructor(
    env: FunctionEnvironment,
    temputs: Temputs,
    maybeOriginFunction1: Option[FunctionA],
    sequenceRefType2: CoordT,
    sequence: StaticSizedArrayTT):
  (FunctionHeaderT) = {
    opts.debugOut("turn this into just a regular destructor template function? dont see why its special.")

    val arrayOwnership = if (sequence.array.mutability == MutableT) OwnT else ShareT
    val arrayPermission = if (sequence.array.mutability == MutableT) ReadwriteT else ReadonlyT
    val arrayBorrowOwnership = if (sequence.array.mutability == MutableT) ConstraintT else ShareT
    val arrayRefType = CoordT(arrayOwnership, arrayPermission, sequence)

    val elementDropFunctionPrototype = getDropFunction(env, temputs, sequence.array.memberType)

    val (ifunction1InterfaceRef, elementDropFunctionAsIFunctionSubstructStructRef, constructorPrototype) =
      structTemplar.prototypeToAnonymousIFunctionSubstruct(env, temputs, RangeS.internal(-1203), elementDropFunctionPrototype)

    val ifunctionExpression =
      StructToInterfaceUpcastTE(
        FunctionCallTE(constructorPrototype, List()),
        ifunction1InterfaceRef)


    val consumerMethod2 =
      overloadTemplar.scoutExpectedFunctionForPrototype(
        env, temputs, RangeS.internal(-108),
        GlobalFunctionFamilyNameA(CallTemplar.CALL_FUNCTION_NAME),
        List(),
        List(ParamFilter(ifunctionExpression.resultRegister.reference, None), ParamFilter(sequence.array.memberType, None)),
        List(), true) match {
        case seff@ScoutExpectedFunctionFailure(_, _, _, _, _) => {
          vimpl()
        }
        case ScoutExpectedFunctionSuccess(prototype) => prototype
      }

    val function2 =
      FunctionT(
        FunctionHeaderT(
          env.fullName,
          List(),
          List(ParameterT(CodeVarNameT("this"), None, arrayRefType)),
          CoordT(ShareT, ReadonlyT, VoidT()),
          maybeOriginFunction1),
        List(),
        BlockTE(
          List(
            DestroyStaticSizedArrayIntoFunctionTE(
              ArgLookupTE(0, arrayRefType),
              sequence,
              ifunctionExpression,
              consumerMethod2),
            ReturnTE(VoidLiteralTE()))))

    temputs.declareFunctionReturnType(function2.header.toSignature, function2.header.returnType)
    temputs.addFunction(function2)
    function2.header
  }

  def generateRuntimeSizedArrayDestructor(
    env: FunctionEnvironment,
    temputs: Temputs,
    maybeOriginFunction1: Option[FunctionA],
    arrayRefType2: CoordT,
    array: RuntimeSizedArrayTT):
  (FunctionHeaderT) = {
    val arrayOwnership = if (array.array.mutability == MutableT) OwnT else ShareT
    val arrayBorrowOwnership = if (array.array.mutability == MutableT) ConstraintT else ShareT

    val elementDropFunctionPrototype = getDropFunction(env, temputs, array.array.memberType)

    val (ifunction1InterfaceRef, elementDropFunctionAsIFunctionSubstructStructRef, constructorPrototype) =
      structTemplar.prototypeToAnonymousIFunctionSubstruct(env, temputs, RangeS.internal(-1879), elementDropFunctionPrototype)

    val ifunctionExpression =
      StructToInterfaceUpcastTE(
        FunctionCallTE(constructorPrototype, List()),
        ifunction1InterfaceRef)

    val consumerMethod2 =
      overloadTemplar.scoutExpectedFunctionForPrototype(
        env, temputs, RangeS.internal(-108),
        GlobalFunctionFamilyNameA(CallTemplar.CALL_FUNCTION_NAME),
        List(),
        List(ParamFilter(ifunctionExpression.resultRegister.reference, None), ParamFilter(array.array.memberType, None)),
        List(), true) match {
        case seff@ScoutExpectedFunctionFailure(_, _, _, _, _) => {
          vimpl(seff.toString)
        }
        case ScoutExpectedFunctionSuccess(prototype) => prototype
      }

    val function2 =
      FunctionT(
        FunctionHeaderT(
          env.fullName,
          List(),
          List(ParameterT(CodeVarNameT("this"), None, arrayRefType2)),
          CoordT(ShareT, ReadonlyT, VoidT()),
          maybeOriginFunction1),
        List(),
        BlockTE(
          List(
            DestroyRuntimeSizedArrayTE(
              ArgLookupTE(0, arrayRefType2),
              array,
              ifunctionExpression,
              consumerMethod2),
            ReturnTE(VoidLiteralTE()))))

    temputs.declareFunctionReturnType(function2.header.toSignature, function2.header.returnType)
    temputs.addFunction(function2)
    (function2.header)
  }

  def getImmConcreteDestructor(
    temputs: Temputs,
    env: IEnvironment,
    structRef2: StructRefT):
  PrototypeT = {
    vassert(Templar.getMutability(temputs, structRef2) == ImmutableT)

    overloadTemplar.scoutExpectedFunctionForPrototype(
      env,
      temputs,
      RangeS.internal(-1673),
      ImmConcreteDestructorImpreciseNameA(),
      List(),
      List(ParamFilter(CoordT(ShareT, ReadonlyT, structRef2), None)),
      List(),
      true) match {
      case (seff@ScoutExpectedFunctionFailure(_, _, _, _, _)) => {
        throw CompileErrorExceptionT(CouldntFindFunctionToCallT(RangeS.internal(-49), seff))
      }
      case (ScoutExpectedFunctionSuccess(p)) => (p)
    }
  }

  def getImmInterfaceDestructor(
    temputs: Temputs,
    env: IEnvironment,
    interfaceRef2: InterfaceRefT):
  PrototypeT = {
    vassert(Templar.getMutability(temputs, interfaceRef2) == ImmutableT)

    val prototype =
      overloadTemplar.scoutExpectedFunctionForPrototype(
        env,
        temputs,
        RangeS.internal(-1677),
        ImmInterfaceDestructorImpreciseNameA(),
        List(),
        List(ParamFilter(CoordT(ShareT, ReadonlyT, interfaceRef2), None)),
        List(),
        true) match {
        case (seff@ScoutExpectedFunctionFailure(_, _, _, _, _)) => {
          throw CompileErrorExceptionT(CouldntFindFunctionToCallT(RangeS.internal(-48), seff))
        }
        case (ScoutExpectedFunctionSuccess(p)) => (p)
      }
    prototype
  }

  def getImmInterfaceDestructorOverride(
    temputs: Temputs,
    env: IEnvironment,
    structRef2: StructRefT,
    implementedInterfaceRefT: InterfaceRefT):
  PrototypeT = {
    vassert(Templar.getMutability(temputs, structRef2) == ImmutableT)
    vassert(Templar.getMutability(temputs, implementedInterfaceRefT) == ImmutableT)

    val sefResult =
      overloadTemplar.scoutExpectedFunctionForPrototype(
        env,
        temputs,
        RangeS.internal(-1674),
        ImmInterfaceDestructorImpreciseNameA(),
        List(),
        List(ParamFilter(CoordT(ShareT, ReadonlyT, structRef2), Some(OverrideT(implementedInterfaceRefT)))),
        List(),
        true)
    sefResult match {
      case ScoutExpectedFunctionSuccess(prototype) => prototype
      case ScoutExpectedFunctionFailure(_, _, _, _, _) => {
        throw CompileErrorExceptionT(RangedInternalErrorT(RangeS.internal(-1674), sefResult.toString))
      }
    }
  }
}

object DestructorTemplar {
  def addConcreteDestructor(mutability: MutabilityT): (FunctionA, IFunctionGenerator) = {
    // Note the virtuality None in the header, and how we filter so this only applies
    // to structs and not interfaces. We use a different template for interface destructors.
    val unevaluatedFunction =
    FunctionA(
      RangeS.internal(-68),
      if (mutability == MutableT) {
        FunctionNameA(CallTemplar.MUT_DESTRUCTOR_NAME, CodeLocationS.internal(-16))
      } else {
        ImmConcreteDestructorNameA(PackageCoordinate.internal)
      },
      List(UserFunctionA),
      TemplateTemplataType(List(CoordTemplataType), FunctionTemplataType),
      Set(CodeRuneA("V")),
      List(CodeRuneA("T")),
      Set(CodeRuneA("T"), CodeRuneA("XX"), CodeRuneA("V")),
      Map(
        CodeRuneA("XX") -> KindTemplataType,
        CodeRuneA("T") -> CoordTemplataType,
        CodeRuneA("V") -> CoordTemplataType),
      List(
        ParameterA(AtomAP(RangeS.internal(-1339), LocalVariableA(CodeVarNameA("this"), FinalP, NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed), None, CodeRuneA("T"), None))),
      Some(CodeRuneA("V")),
      List(
        EqualsAR(RangeS.internal(-16722),
          TemplexAR(RuneAT(RangeS.internal(-16723),CodeRuneA("XX"), KindTemplataType)),
          ComponentsAR(
            RangeS.internal(-93),
            KindTemplataType, List(TemplexAR(MutabilityAT(RangeS.internal(-16724),Conversions.unevaluateMutability(mutability)))))),
        EqualsAR(RangeS.internal(-16725),
          TemplexAR(RuneAT(RangeS.internal(-16726),CodeRuneA("T"), CoordTemplataType)),
          ComponentsAR(
            RangeS.internal(-94),
            CoordTemplataType,
            List(
              OrAR(RangeS.internal(-16727),List(TemplexAR(OwnershipAT(RangeS.internal(-1672),OwnP)), TemplexAR(OwnershipAT(RangeS.internal(-1672),ShareP)))),
              OrAR(RangeS.internal(-16728),List(TemplexAR(PermissionAT(RangeS.internal(-1672),ReadwriteP)), TemplexAR(PermissionAT(RangeS.internal(-1672),ReadonlyP)))),
              CallAR(RangeS.internal(-16729),
                "passThroughIfConcrete",
                List(TemplexAR(RuneAT(RangeS.internal(-167210),CodeRuneA("XX"), KindTemplataType))),
                KindTemplataType)))),
        EqualsAR(RangeS.internal(-167211),
          TemplexAR(RuneAT(RangeS.internal(-167212),CodeRuneA("V"), CoordTemplataType)),
          TemplexAR(NameAT(RangeS.internal(-167213),CodeTypeNameA("void"), CoordTemplataType)))),
      GeneratedBodyA("concreteDestructorGenerator"))
    val generator =
      new IFunctionGenerator {
        override def generate(
          functionTemplarCore: FunctionTemplarCore,
          structTemplar: StructTemplar,
          destructorTemplar: DestructorTemplar,
          env: FunctionEnvironment,
          temputs: Temputs,
          callRange: RangeS,
          maybeOriginFunction1: Option[FunctionA],
          paramCoords: List[ParameterT],
          maybeReturnType2: Option[CoordT]):
        (FunctionHeaderT) = {
          // Even though below we treat packs, closures, and structs the same, they're
          // still disambiguated by the template arguments.
          paramCoords.map(_.tyype) match {
            case List(CoordT(_, _, PackTT(_, structRef))) => {
              destructorTemplar.generateStructDestructor(
                env, temputs, maybeOriginFunction1.get, paramCoords, structRef)
            }
            case List(CoordT(_, _, sr @ StructRefT(_))) => {
              destructorTemplar.generateStructDestructor(
                env, temputs, maybeOriginFunction1.get, paramCoords, sr)
            }
            case List(r @ CoordT(_, _, as @ StaticSizedArrayTT(_, _))) => {
              destructorTemplar.generateStaticSizedArrayDestructor(
                env, temputs, maybeOriginFunction1, r, as)
            }
            case List(r @ CoordT(_, _, ra @ RuntimeSizedArrayTT(_))) => {
              destructorTemplar.generateRuntimeSizedArrayDestructor(
                env, temputs, maybeOriginFunction1, r, ra)
            }
            case _ => {
              vfail("wot")
            }
          }
        }
      }
    (unevaluatedFunction, generator)
  }

  def addInterfaceDestructor(mutability: MutabilityT):
  (FunctionA, IFunctionGenerator) = {
    val unevaluatedFunctionA =
      FunctionA(
        RangeS.internal(-64),
        if (mutability == MutableT) {
          FunctionNameA(CallTemplar.MUT_INTERFACE_DESTRUCTOR_NAME, CodeLocationS.internal(-17))
        } else {
          ImmInterfaceDestructorNameA(PackageCoordinate.internal)
        },
        List(UserFunctionA),
        TemplateTemplataType(List(CoordTemplataType), FunctionTemplataType),
        Set(CodeRuneA("V")),
        List(CodeRuneA("T")),
        Set(CodeRuneA("T"), CodeRuneA("XX"), CodeRuneA("V")),
        Map(
          CodeRuneA("T") -> CoordTemplataType,
          CodeRuneA("V") -> CoordTemplataType,
          CodeRuneA("XX") -> KindTemplataType),
        List(
          ParameterA(AtomAP(RangeS.internal(-1340), LocalVariableA(CodeVarNameA("this"), FinalP, NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed), Some(AbstractAP), CodeRuneA("T"), None))),
        Some(CodeRuneA("V")),
        List(
          EqualsAR(RangeS.internal(-167214),
            TemplexAR(RuneAT(RangeS.internal(-167215),CodeRuneA("XX"), KindTemplataType)),
            ComponentsAR(
              RangeS.internal(-95),
              KindTemplataType, List(TemplexAR(MutabilityAT(RangeS.internal(-167216),Conversions.unevaluateMutability(mutability)))))),
          EqualsAR(RangeS.internal(-167217),
            TemplexAR(RuneAT(RangeS.internal(-167218),CodeRuneA("T"), CoordTemplataType)),
            ComponentsAR(
              RangeS.internal(-96),
              CoordTemplataType,
              List(
                OrAR(RangeS.internal(-167219),List(TemplexAR(OwnershipAT(RangeS.internal(-167220),OwnP)), TemplexAR(OwnershipAT(RangeS.internal(-167221),ShareP)))),
                OrAR(RangeS.internal(-167222),List(TemplexAR(PermissionAT(RangeS.internal(-167223),ReadwriteP)), TemplexAR(PermissionAT(RangeS.internal(-167224),ReadonlyP)))),
                CallAR(RangeS.internal(-167225),
                  "passThroughIfInterface",
                  List(TemplexAR(RuneAT(RangeS.internal(-167226),CodeRuneA("XX"), KindTemplataType))),
                  KindTemplataType)))),
          EqualsAR(RangeS.internal(-167227),
            TemplexAR(RuneAT(RangeS.internal(-167228),CodeRuneA("V"), CoordTemplataType)),
            TemplexAR(NameAT(RangeS.internal(-167229),CodeTypeNameA("void"), CoordTemplataType)))),
        GeneratedBodyA("interfaceDestructorGenerator"))
    val generator =
      new IFunctionGenerator {
        override def generate(
          functionTemplarCore: FunctionTemplarCore,
          structTemplar: StructTemplar,
          destructorTemplar: DestructorTemplar,
          namedEnv: FunctionEnvironment,
          temputs: Temputs,
          callRange: RangeS,
          maybeOriginFunction1: Option[FunctionA],
          params: List[ParameterT],
          maybeReturnType2: Option[CoordT]):
        (FunctionHeaderT) = {
          // Even though below we treat packs, closures, and structs the same, they're
          // still disambiguated by the template arguments.
          val Some(returnType2) = maybeReturnType2
          params.map(_.tyype) match {
            case List(CoordT(_, _, InterfaceRefT(_))) => {
              functionTemplarCore.makeInterfaceFunction(
                namedEnv,
                temputs,
                maybeOriginFunction1,
                params,
                returnType2)
            }
            case _ => {
              vfail("wot")
            }
          }
        }
      }
    (unevaluatedFunctionA, generator)
  }

  def addImplDestructor(
    mutability: MutabilityT):
  (FunctionA, IFunctionGenerator) = {
    val unevaluatedFunctionA =
      FunctionA(
        RangeS.internal(-65),
        if (mutability == MutableT) {
          FunctionNameA(CallTemplar.MUT_INTERFACE_DESTRUCTOR_NAME, CodeLocationS.internal(-18))
        } else {
          ImmInterfaceDestructorNameA(PackageCoordinate.internal)
        },
        List(UserFunctionA),
        TemplateTemplataType(List(CoordTemplataType, KindTemplataType), FunctionTemplataType),
        Set(CodeRuneA("V")),
        List(CodeRuneA("T"), CodeRuneA("I")),
        Set(CodeRuneA("I"), CodeRuneA("T"), CodeRuneA("XX"), CodeRuneA("V")),
        Map(
          CodeRuneA("T") -> CoordTemplataType,
          CodeRuneA("I") -> KindTemplataType,
          CodeRuneA("V") -> CoordTemplataType,
          CodeRuneA("XX") -> KindTemplataType),
        List(
          ParameterA(AtomAP(RangeS.internal(-1341), LocalVariableA(CodeVarNameA("this"), FinalP, NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed), Some(OverrideAP(RangeS.internal(-1133), CodeRuneA("I"))), CodeRuneA("T"), None))),
        Some(CodeRuneA("V")),
        List(
          EqualsAR(RangeS.internal(-167230),
            TemplexAR(RuneAT(RangeS.internal(-167231),CodeRuneA("XX"), KindTemplataType)),
            ComponentsAR(
              RangeS.internal(-97),
              KindTemplataType, List(TemplexAR(MutabilityAT(RangeS.internal(-167232),Conversions.unevaluateMutability(mutability)))))),
          EqualsAR(RangeS.internal(-167233),
            TemplexAR(RuneAT(RangeS.internal(-167234),CodeRuneA("T"), CoordTemplataType)),
            ComponentsAR(
              RangeS.internal(-98),
              CoordTemplataType,
              List(
                OrAR(RangeS.internal(-167235),List(TemplexAR(OwnershipAT(RangeS.internal(-167236),OwnP)), TemplexAR(OwnershipAT(RangeS.internal(-167237),ShareP)))),
                OrAR(RangeS.internal(-167238),List(TemplexAR(PermissionAT(RangeS.internal(-167239),ReadwriteP)), TemplexAR(PermissionAT(RangeS.internal(-167240),ReadonlyP)))),
                CallAR(RangeS.internal(-167241),
                  "passThroughIfStruct",
                  List(TemplexAR(RuneAT(RangeS.internal(-167242),CodeRuneA("XX"), KindTemplataType))),
                  KindTemplataType)))),
          CallAR(RangeS.internal(-167243),"passThroughIfInterface", List(TemplexAR(RuneAT(RangeS.internal(-167244),CodeRuneA("I"), KindTemplataType))), KindTemplataType),
          EqualsAR(RangeS.internal(-167245),
            TemplexAR(RuneAT(RangeS.internal(-167246),CodeRuneA("V"), CoordTemplataType)),
            TemplexAR(NameAT(RangeS.internal(-167247),CodeTypeNameA("void"), CoordTemplataType)))),
        GeneratedBodyA("implDestructorGenerator"))
    val generator =
      new IFunctionGenerator {
        override def generate(
          functionTemplarCore: FunctionTemplarCore,
          structTemplar: StructTemplar,
          destructorTemplar: DestructorTemplar,
          namedEnv: FunctionEnvironment,
          temputs: Temputs,
          callRange: RangeS,
          maybeOriginFunction1: Option[FunctionA],
          params: List[ParameterT],
          maybeReturnType2: Option[CoordT]):
        (FunctionHeaderT) = {
          // There are multiple idestructor overrides for a given struct, which can
          // confuse us.
          // They all override different interfaces, but that's not factored into the
          // overload templar.
          // However, the template arguments are, and idestructor's template argument
          // is the interface we're overriding.
          val List(
          CoordTemplata(CoordT(_, _, overridingStructRef2FromTemplateArg @ StructRefT(_))),
          KindTemplata(implementedInterfaceRef2 @ InterfaceRefT(_))) =
          namedEnv.fullName.last.templateArgs

          params.map(_.tyype) match {
            case List(CoordT(_, _, structRef2 @ StructRefT(_))) => {
              vassert(overridingStructRef2FromTemplateArg == structRef2)
              val structDef2 = temputs.lookupStruct(structRef2)
              val ownership = if (structDef2.mutability == MutableT) OwnT else ShareT
              val permission = if (structDef2.mutability == MutableT) ReadwriteT else ReadonlyT
              val structType2 = CoordT(ownership, permission, structRef2)
              val structDestructor =
                destructorTemplar.getCitizenDestructor(namedEnv, temputs, structType2)
              functionTemplarCore.makeImplDestructor(
                namedEnv, temputs, maybeOriginFunction1, structDef2, implementedInterfaceRef2, structDestructor)
            }
            case _ => {
              vfail("wot")
            }
          }
        }
      }
    (unevaluatedFunctionA, generator)
  }

  def addDrop(
    mutability: MutabilityT):
  (FunctionA, IFunctionGenerator) = {
    // Drop is a function that:
    // - If received an owning pointer, will call the destructor
    // - If received a share pointer, will decrement it and if was last, call its destructor
    // - If received a borrow, do nothing.
    // Conceptually it's "drop the reference", as opposed to destructor which is "drop the object"
    val unevaluatedFunctionA =
    FunctionA(
      RangeS.internal(-66),
      if (mutability == MutableT) {
        FunctionNameA(CallTemplar.MUT_DROP_FUNCTION_NAME, CodeLocationS.internal(-19))
      } else {
        ImmDropNameA(PackageCoordinate.internal)
      },
      List(UserFunctionA),
      TemplateTemplataType(List(CoordTemplataType), FunctionTemplataType),
      Set(CodeRuneA("V"), CodeRuneA("O")),
      List(CodeRuneA("T")),
      Set(CodeRuneA("T"), CodeRuneA("V"), CodeRuneA("O"), CodeRuneA("P")),
      Map(
        CodeRuneA("T") -> CoordTemplataType,
        CodeRuneA("V") -> CoordTemplataType,
        CodeRuneA("O") -> OwnershipTemplataType,
        CodeRuneA("P") -> PermissionTemplataType),
      List(
        ParameterA(AtomAP(RangeS.internal(-1342), LocalVariableA(CodeVarNameA("x"), FinalP, NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed), None, CodeRuneA("T"), None))),
      Some(CodeRuneA("V")),
      List(
        EqualsAR(RangeS.internal(-167248),
          TemplexAR(RuneAT(RangeS.internal(-167249),CodeRuneA("T"), CoordTemplataType)),
          ComponentsAR(
            RangeS.internal(-98),
            CoordTemplataType,
            List(
              TemplexAR(RuneAT(RangeS.internal(-167250),CodeRuneA("O"), OwnershipTemplataType)),
              TemplexAR(RuneAT(RangeS.internal(-167260),CodeRuneA("P"), PermissionTemplataType)),
              ComponentsAR(
                RangeS.internal(-99),
                KindTemplataType,
                List(TemplexAR(MutabilityAT(RangeS.internal(-167251),Conversions.unevaluateMutability(mutability)))))))),
        TemplexAR(RuneAT(RangeS.internal(-167252),CodeRuneA("T"), CoordTemplataType)),
        EqualsAR(RangeS.internal(-167253),
          TemplexAR(RuneAT(RangeS.internal(-167254),CodeRuneA("V"), CoordTemplataType)),
          TemplexAR(NameAT(RangeS.internal(-167255),CodeTypeNameA("void"), CoordTemplataType)))),
      GeneratedBodyA("dropGenerator"))
    val generator =
      new IFunctionGenerator {
        override def generate(
          functionTemplarCore: FunctionTemplarCore,
          structTemplar: StructTemplar,
          destructorTemplar: DestructorTemplar,
          namedEnv: FunctionEnvironment,
          temputs: Temputs,
          callRange: RangeS,
          maybeOriginFunction1: Option[FunctionA],
          params: List[ParameterT],
          maybeReturnType2: Option[CoordT]):
        (FunctionHeaderT) = {
          vassert(maybeReturnType2 == Some(CoordT(ShareT, ReadonlyT, VoidT())))
          val List(CoordTemplata(ref2)) = namedEnv.fullName.last.templateArgs
          val List(ParameterT(CodeVarNameT("x"), None, paramType2)) = params
          vassert(paramType2 == ref2)
          destructorTemplar.generateDropFunction(
            namedEnv, temputs, maybeOriginFunction1.get, ref2)
        }
      }
    (unevaluatedFunctionA, generator)
  }

}