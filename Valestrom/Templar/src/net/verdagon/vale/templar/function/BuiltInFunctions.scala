package net.verdagon.vale.templar.function

import net.verdagon.vale.astronomer._
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.parser._
import net.verdagon.vale.{scout => s}
import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.scout.patterns.{AbstractSP, AtomSP, CaptureS, OverrideSP}
import net.verdagon.vale.scout.rules._
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.env.{FunctionEnvironment, _}
import net.verdagon.vale.{vassert, vfail}

import scala.collection.immutable.List

object BuiltInFunctions {
  def addBuiltInFunctions(
    currentlyConstructingEnv0: NamespaceEnvironment[IName2],
    functionGeneratorByName0: Map[String, IFunctionGenerator]):
  (NamespaceEnvironment[IName2], Map[String, IFunctionGenerator]) = {
    val (currentlyConstructingEnv1, functionGeneratorByName1) = addConcreteDestructor(currentlyConstructingEnv0, functionGeneratorByName0, Mutable)
    val (currentlyConstructingEnv2, functionGeneratorByName2) = addConcreteDestructor(currentlyConstructingEnv1, functionGeneratorByName1, Immutable)
    val (currentlyConstructingEnv3, functionGeneratorByName3) = addInterfaceDestructor(currentlyConstructingEnv2, functionGeneratorByName2, Mutable)
    val (currentlyConstructingEnv4, functionGeneratorByName4) = addInterfaceDestructor(currentlyConstructingEnv3, functionGeneratorByName3, Immutable)
    val (currentlyConstructingEnv5, functionGeneratorByName5) = addImplDestructor(currentlyConstructingEnv4, functionGeneratorByName4, Mutable)
    val (currentlyConstructingEnv6, functionGeneratorByName6) = addImplDestructor(currentlyConstructingEnv5, functionGeneratorByName5, Immutable)
    val (currentlyConstructingEnv7, functionGeneratorByName7) = addDrop(currentlyConstructingEnv6, functionGeneratorByName6, Mutable)
    val (currentlyConstructingEnv8, functionGeneratorByName8) = addDrop(currentlyConstructingEnv7, functionGeneratorByName7, Immutable)
    val currentlyConstructingEnv9 = addArrayLen(currentlyConstructingEnv8)
    val currentlyConstructingEnv10 = addPanic(currentlyConstructingEnv9)
    val currentlyConstructingEnv11 = addWeakLock(currentlyConstructingEnv10)
    (currentlyConstructingEnv11, functionGeneratorByName8)
  }

  private def addConcreteDestructor(
    currentlyConstructingEnv: NamespaceEnvironment[IName2],
    functionGeneratorByName: Map[String, IFunctionGenerator],
    mutability: Mutability
  ): (NamespaceEnvironment[IName2], Map[String, IFunctionGenerator]) = {
    // Note the virtuality None in the header, and how we filter so this only applies
    // to structs and not interfaces. We use a different template for interface destructors.
    (
    currentlyConstructingEnv
      .addUnevaluatedFunction(
          FunctionA(
            if (mutability == Mutable) {
              FunctionNameA(CallTemplar.MUT_DESTRUCTOR_NAME, s.CodeLocationS(-16, 0))
            } else {
              ImmConcreteDestructorNameA()
            },
            true,
            TemplateTemplataType(List(CoordTemplataType), FunctionTemplataType),
            Set(CodeRuneA("V")),
            List(CodeRuneA("T")),
            Set(CodeRuneA("T"), CodeRuneA("XX"), CodeRuneA("V")),
            Map(
              CodeRuneA("XX") -> KindTemplataType,
              CodeRuneA("T") -> CoordTemplataType,
              CodeRuneA("V") -> CoordTemplataType),
            List(
              ParameterA(AtomAP(CaptureA(CodeVarNameA("this"), FinalP), None, CodeRuneA("T"), None))),
            Some(CodeRuneA("V")),
            List(
              EqualsAR(
                TemplexAR(RuneAT(CodeRuneA("XX"), KindTemplataType)),
                ComponentsAR(KindTemplataType, List(TemplexAR(MutabilityAT(Conversions.unevaluateMutability(mutability)))))),
              EqualsAR(
                TemplexAR(RuneAT(CodeRuneA("T"), CoordTemplataType)),
                ComponentsAR(
                  CoordTemplataType,
                  List(
                    OrAR(List(TemplexAR(OwnershipAT(OwnP)), TemplexAR(OwnershipAT(ShareP)))),
                    CallAR(
                      "passThroughIfConcrete",
                      List(TemplexAR(RuneAT(CodeRuneA("XX"), KindTemplataType))),
                      KindTemplataType)))),
              EqualsAR(
                TemplexAR(RuneAT(CodeRuneA("V"), CoordTemplataType)),
                TemplexAR(NameAT(CodeTypeNameA("void"), CoordTemplataType)))),
            GeneratedBodyA("concreteDestructorGenerator"))),
      functionGeneratorByName +
        (
        "concreteDestructorGenerator" ->
          new IFunctionGenerator {
            override def generate(
              env: FunctionEnvironment,
              temputs: TemputsBox,
              maybeOriginFunction1: Option[FunctionA],
              paramCoords: List[Parameter2],
              maybeReturnType2: Option[Coord]):
            (FunctionHeader2) = {
              // Even though below we treat packs, closures, and structs the same, they're
              // still disambiguated by the template arguments.
              paramCoords.map(_.tyype) match {
                case List(Coord(_, PackT2(_, structRef))) => {
                  DestructorTemplar.generateStructDestructor(
                    env, temputs, maybeOriginFunction1.get, paramCoords, structRef)
                }
                case List(Coord(_, sr @ StructRef2(_))) => {
                  DestructorTemplar.generateStructDestructor(
                    env, temputs, maybeOriginFunction1.get, paramCoords, sr)
                }
                case List(r @ Coord(_, as @ KnownSizeArrayT2(_, _))) => {
                  DestructorTemplar.generateArraySequenceDestructor(
                    env, temputs, maybeOriginFunction1, r, as)
                }
                case List(r @ Coord(_, ra @ UnknownSizeArrayT2(_))) => {
                  DestructorTemplar.generateUnknownSizeArrayDestructor(
                    env, temputs, maybeOriginFunction1, r, ra)
                }
                case _ => {
                  vfail("wot")
                }
              }
            }
          }))
  }

  private def addInterfaceDestructor(
    currentlyConstructingEnv: NamespaceEnvironment[IName2],
    functionGeneratorByName: Map[String, IFunctionGenerator],
    mutability: Mutability
  ): (NamespaceEnvironment[IName2], Map[String, IFunctionGenerator]) = {

    (
    currentlyConstructingEnv
      .addUnevaluatedFunction(
        FunctionA(
          if (mutability == Mutable) {
            FunctionNameA(CallTemplar.MUT_INTERFACE_DESTRUCTOR_NAME, CodeLocationS(-17, 0))
          } else {
            ImmInterfaceDestructorNameA()
          },
          true,
          TemplateTemplataType(List(CoordTemplataType), FunctionTemplataType),
          Set(CodeRuneA("V")),
          List(CodeRuneA("T")),
          Set(CodeRuneA("T"), CodeRuneA("XX"), CodeRuneA("V")),
          Map(
            CodeRuneA("T") -> CoordTemplataType,
            CodeRuneA("V") -> CoordTemplataType,
            CodeRuneA("XX") -> KindTemplataType),
          List(
            ParameterA(AtomAP(CaptureA(CodeVarNameA("this"), FinalP), Some(AbstractAP), CodeRuneA("T"), None))),
          Some(CodeRuneA("V")),
          List(
            EqualsAR(
              TemplexAR(RuneAT(CodeRuneA("XX"), KindTemplataType)),
              ComponentsAR(KindTemplataType, List(TemplexAR(MutabilityAT(Conversions.unevaluateMutability(mutability)))))),
            EqualsAR(
              TemplexAR(RuneAT(CodeRuneA("T"), CoordTemplataType)),
              ComponentsAR(
                CoordTemplataType,
                List(
                  OrAR(List(TemplexAR(OwnershipAT(OwnP)), TemplexAR(OwnershipAT(ShareP)))),
                  CallAR(
                    "passThroughIfInterface",
                    List(TemplexAR(RuneAT(CodeRuneA("XX"), KindTemplataType))),
                    KindTemplataType)))),
            EqualsAR(
              TemplexAR(RuneAT(CodeRuneA("V"), CoordTemplataType)),
              TemplexAR(NameAT(CodeTypeNameA("void"), CoordTemplataType)))),
          GeneratedBodyA("interfaceDestructorGenerator"))),
      functionGeneratorByName +
        ("interfaceDestructorGenerator" ->
        new IFunctionGenerator {
          override def generate(
            namedEnv: FunctionEnvironment,
            temputs: TemputsBox,
            maybeOriginFunction1: Option[FunctionA],
            params: List[Parameter2],
            maybeReturnType2: Option[Coord]):
          (FunctionHeader2) = {
            // Even though below we treat packs, closures, and structs the same, they're
            // still disambiguated by the template arguments.
            val Some(returnType2) = maybeReturnType2
            params.map(_.tyype) match {
              case List(Coord(_, InterfaceRef2(_))) => {
                FunctionTemplarCore.makeInterfaceFunction(
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
        }))
  }

  private def addImplDestructor(
    currentlyConstructingEnv: NamespaceEnvironment[IName2],
    functionGeneratorByName: Map[String, IFunctionGenerator],
    mutability: Mutability
  ): (NamespaceEnvironment[IName2], Map[String, IFunctionGenerator]) = {
    (
    currentlyConstructingEnv
      .addUnevaluatedFunction(
        FunctionA(
          if (mutability == Mutable) {
            FunctionNameA(CallTemplar.MUT_INTERFACE_DESTRUCTOR_NAME, CodeLocationS(-18, 0))
          } else {
            ImmInterfaceDestructorNameA()
          },
          true,
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
            ParameterA(AtomAP(CaptureA(CodeVarNameA("this"), FinalP), Some(OverrideAP(CodeRuneA("I"))), CodeRuneA("T"), None))),
          Some(CodeRuneA("V")),
          List(
            EqualsAR(
              TemplexAR(RuneAT(CodeRuneA("XX"), KindTemplataType)),
              ComponentsAR(KindTemplataType, List(TemplexAR(MutabilityAT(Conversions.unevaluateMutability(mutability)))))),
            EqualsAR(
              TemplexAR(RuneAT(CodeRuneA("T"), CoordTemplataType)),
              ComponentsAR(
                CoordTemplataType,
                List(
                  OrAR(List(TemplexAR(OwnershipAT(OwnP)), TemplexAR(OwnershipAT(ShareP)))),
                  CallAR(
                    "passThroughIfStruct",
                    List(TemplexAR(RuneAT(CodeRuneA("XX"), KindTemplataType))),
                    KindTemplataType)))),
            CallAR("passThroughIfInterface", List(TemplexAR(RuneAT(CodeRuneA("I"), KindTemplataType))), KindTemplataType),
            EqualsAR(
              TemplexAR(RuneAT(CodeRuneA("V"), CoordTemplataType)),
              TemplexAR(NameAT(CodeTypeNameA("void"), CoordTemplataType)))),
          GeneratedBodyA("implDestructorGenerator"))),
      functionGeneratorByName + (
        "implDestructorGenerator" ->
          new IFunctionGenerator {
            override def generate(
              namedEnv: FunctionEnvironment,
              temputs: TemputsBox,
              maybeOriginFunction1: Option[FunctionA],
              params: List[Parameter2],
              maybeReturnType2: Option[Coord]):
            (FunctionHeader2) = {
              // There are multiple idestructor overrides for a given struct, which can
              // confuse us.
              // They all override different interfaces, but that's not factored into the
              // overload templar.
              // However, the template arguments are, and idestructor's template argument
              // is the interface we're overriding.
              val List(
                CoordTemplata(Coord(_, overridingStructRef2FromTemplateArg @ StructRef2(_))),
                KindTemplata(implementedInterfaceRef2 @ InterfaceRef2(_))) =
                  namedEnv.fullName.last.templateArgs

              params.map(_.tyype) match {
                case List(Coord(_, structRef2 @ StructRef2(_))) => {
                  vassert(overridingStructRef2FromTemplateArg == structRef2)
                  val structDef2 = temputs.lookupStruct(structRef2)
                  FunctionTemplarCore.makeImplDestructor(
                    namedEnv, temputs, maybeOriginFunction1, structDef2, implementedInterfaceRef2)
                }
                case _ => {
                  vfail("wot")
                }
              }
            }
          }))
  }

  private def addDrop(
    currentlyConstructingEnv: NamespaceEnvironment[IName2],
    functionGeneratorByName: Map[String, IFunctionGenerator],
    mutability: Mutability
  ): (NamespaceEnvironment[IName2], Map[String, IFunctionGenerator]) = {
    // Drop is a function that:
    // - If received an owning pointer, will call the destructor
    // - If received a share pointer, will decrement it and if was last, call its destructor
    // - If received a borrow, do nothing.
    // Conceptually it's "drop the reference", as opposed to destructor which is "drop the object"
    (
      currentlyConstructingEnv
        .addUnevaluatedFunction(
          FunctionA(
            if (mutability == Mutable) {
              FunctionNameA(CallTemplar.MUT_DROP_FUNCTION_NAME, CodeLocationS(-19, 0))
            } else {
              ImmDropNameA()
            },
            true,
            TemplateTemplataType(List(CoordTemplataType), FunctionTemplataType),
            Set(CodeRuneA("V"), CodeRuneA("O")),
            List(CodeRuneA("T")),
            Set(CodeRuneA("T"), CodeRuneA("V"), CodeRuneA("O")),
            Map(
              CodeRuneA("T") -> CoordTemplataType,
              CodeRuneA("V") -> CoordTemplataType,
              CodeRuneA("O") -> OwnershipTemplataType),
            List(
              ParameterA(AtomAP(CaptureA(CodeVarNameA("x"), FinalP), None, CodeRuneA("T"), None))),
            Some(CodeRuneA("V")),
            List(
              EqualsAR(
                TemplexAR(RuneAT(CodeRuneA("T"), CoordTemplataType)),
                ComponentsAR(
                  CoordTemplataType,
                  List(
                    TemplexAR(RuneAT(CodeRuneA("O"), OwnershipTemplataType)),
                    ComponentsAR(
                      KindTemplataType,
                      List(TemplexAR(MutabilityAT(Conversions.unevaluateMutability(mutability)))))))),
              TemplexAR(RuneAT(CodeRuneA("T"), CoordTemplataType)),
              EqualsAR(
                TemplexAR(RuneAT(CodeRuneA("V"), CoordTemplataType)),
                TemplexAR(NameAT(CodeTypeNameA("void"), CoordTemplataType)))),
            GeneratedBodyA("dropGenerator"))),
      functionGeneratorByName + (
        "dropGenerator" ->
          new IFunctionGenerator {
            override def generate(
              namedEnv: FunctionEnvironment,
              temputs: TemputsBox,
              maybeOriginFunction1: Option[FunctionA],
              params: List[Parameter2],
              maybeReturnType2: Option[Coord]):
            (FunctionHeader2) = {
              vassert(maybeReturnType2 == Some(Coord(Share, Void2())))
              val List(CoordTemplata(ref2)) = namedEnv.fullName.last.templateArgs
              val List(Parameter2(CodeVarName2("x"), None, paramType2)) = params
              vassert(paramType2 == ref2)
              DestructorTemplar.generateDropFunction(
                namedEnv, temputs, maybeOriginFunction1.get, ref2)
            }
          }))
  }

  private def addArrayLen(currentlyConstructingEnv: NamespaceEnvironment[IName2]): NamespaceEnvironment[IName2] = {
    currentlyConstructingEnv
      .addUnevaluatedFunction(
        FunctionA(
          FunctionNameA("len", s.CodeLocationS(-20, 0)),
          true,
          TemplateTemplataType(List(CoordTemplataType), FunctionTemplataType),
          Set(CodeRuneA("I")),
          List(CodeRuneA("T")),
          Set(CodeRuneA("T"), CodeRuneA("XX"), CodeRuneA("__1"), CodeRuneA("I")),
          Map(
            CodeRuneA("T") -> CoordTemplataType,
            CodeRuneA("XX") -> MutabilityTemplataType,
            CodeRuneA("__1") -> CoordTemplataType,
            CodeRuneA("I") -> CoordTemplataType),
          List(
            ParameterA(AtomAP(CaptureA(CodeVarNameA("arr"), FinalP), None, CodeRuneA("T"), None))),
          Some(CodeRuneA("I")),
          List(
            EqualsAR(
              TemplexAR(RuneAT(CodeRuneA("T"), CoordTemplataType)),
              ComponentsAR(
                CoordTemplataType,
                List(
                  OrAR(List(TemplexAR(OwnershipAT(BorrowP)), TemplexAR(OwnershipAT(ShareP)))),
                  TemplexAR(
                    CallAT(
                      NameAT(CodeTypeNameA("Array"), TemplateTemplataType(List(MutabilityTemplataType, CoordTemplataType), KindTemplataType)),
                      List(
                        RuneAT(CodeRuneA("XX"), MutabilityTemplataType),
                        RuneAT(CodeRuneA("__1"), CoordTemplataType)),
                      KindTemplataType))))),
            EqualsAR(
              TemplexAR(RuneAT(CodeRuneA("I"), CoordTemplataType)),
              TemplexAR(NameAT(CodeTypeNameA("int"), CoordTemplataType)))),
          CodeBodyA(
            BodyAE(
              List(),
              BlockAE(
                List(LocalVariableA(CodeVarNameA("arr"), FinalP, NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed)),
                List(
                  ArrayLengthAE(
                    LocalLoadAE(CodeVarNameA("arr"), OwnP))))))))
      .addUnevaluatedFunction(
        FunctionA(
          FunctionNameA("len", s.CodeLocationS(-21, 0)),
          true,
          TemplateTemplataType(List(CoordTemplataType), FunctionTemplataType),
          Set(CodeRuneA("I")),
          List(CodeRuneA("N"), CodeRuneA("T")),
          Set(CodeRuneA("A"), CodeRuneA("N"), CodeRuneA("M"), CodeRuneA("T"), CodeRuneA("I")),
          Map(
            CodeRuneA("A") -> CoordTemplataType,
            CodeRuneA("N") -> IntegerTemplataType,
            CodeRuneA("T") -> CoordTemplataType,
            CodeRuneA("I") -> CoordTemplataType,
            CodeRuneA("M") -> MutabilityTemplataType),
          List(
            ParameterA(AtomAP(CaptureA(CodeVarNameA("arr"), FinalP), None, CodeRuneA("A"), None))),
          Some(CodeRuneA("I")),
          List(
            EqualsAR(
              TemplexAR(RuneAT(CodeRuneA("A"), CoordTemplataType)),
              ComponentsAR(
                CoordTemplataType,
                List(
                  TemplexAR(OwnershipAT(BorrowP)),
                  TemplexAR(
                    RepeaterSequenceAT(
                      RuneAT(CodeRuneA("M"), MutabilityTemplataType),
                      RuneAT(CodeRuneA("N"), IntegerTemplataType),
                      RuneAT(CodeRuneA("T"), CoordTemplataType),
                      KindTemplataType))))),
            EqualsAR(
              TemplexAR(RuneAT(CodeRuneA("I"), CoordTemplataType)),
              TemplexAR(NameAT(CodeTypeNameA("int"), CoordTemplataType)))),
          CodeBodyA(
            BodyAE(
              List(),
              BlockAE(
                List(LocalVariableA(CodeVarNameA("arr"), FinalP, NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed)),
                List(
                  RuneLookupAE(CodeRuneA("N"), IntegerTemplataType)))))))
  }

  private def addPanic(currentlyConstructingEnv: NamespaceEnvironment[IName2]): NamespaceEnvironment[IName2] = {
    currentlyConstructingEnv
      .addUnevaluatedFunction(
        FunctionA(
          FunctionNameA("panic", s.CodeLocationS(-22, 0)),
          true,
          FunctionTemplataType,
          Set(),
          List(),
          Set(CodeRuneA("N")),
          Map(CodeRuneA("N") -> CoordTemplataType),
          List(),
          Some(CodeRuneA("N")),
          List(
            EqualsAR(
              TemplexAR(RuneAT(CodeRuneA("N"), CoordTemplataType)),
              TemplexAR(NameAT(CodeTypeNameA("__Never"), CoordTemplataType)))),
          ExternBodyA))
  }

  // Uses coord instead of kind as an identifying rune because the implementation
  // might be different for different regions.
  private def addWeakLock(currentlyConstructingEnv: NamespaceEnvironment[IName2]): NamespaceEnvironment[IName2] = {
    currentlyConstructingEnv
      .addUnevaluatedFunction(
        FunctionA(
          FunctionNameA("lock", s.CodeLocationS(-23, 0)),
          true,
          TemplateTemplataType(List(CoordTemplataType), FunctionTemplataType),
          Set(),
          List(CodeRuneA("OwningRune")),
          Set(CodeRuneA("OwningRune"), CodeRuneA("OptBorrowRune"), CodeRuneA("WeakRune")),
          Map(CodeRuneA("OwningRune") -> CoordTemplataType, CodeRuneA("OptBorrowRune") -> CoordTemplataType, CodeRuneA("WeakRune") -> CoordTemplataType),
          List(ParameterA(AtomAP(CaptureA(CodeVarNameA("weakRef"), FinalP), None, CodeRuneA("WeakRune"), None))),
          Some(CodeRuneA("OptBorrowRune")),
          List(
            EqualsAR(
              TemplexAR(RuneAT(CodeRuneA("OptBorrowRune"), CoordTemplataType)),
              TemplexAR(
                CallAT(
                  NameAT(CodeTypeNameA("Opt"), TemplateTemplataType(List(CoordTemplataType), KindTemplataType)),
                  List(OwnershippedAT(BorrowP, RuneAT(CodeRuneA("OwningRune"), CoordTemplataType))),
                  CoordTemplataType))),
            EqualsAR(
              TemplexAR(RuneAT(CodeRuneA("WeakRune"), CoordTemplataType)),
              TemplexAR(
                OwnershippedAT(WeakP, RuneAT(CodeRuneA("OwningRune"), CoordTemplataType))))),
          CodeBodyA(
            BodyAE(
              List(),
              BlockAE(
                List(LocalVariableA(CodeVarNameA("weakRef"), FinalP, NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed)),
                List(
                  LockWeakAE(LocalLoadAE(CodeVarNameA("weakRef"), WeakP))))))))
  }
}
