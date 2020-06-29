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
    val (currentlyConstructingEnv1, functionGeneratorByName1) = addConcreteDestructor(currentlyConstructingEnv0, functionGeneratorByName0)
    val (currentlyConstructingEnv2, functionGeneratorByName2) = addInterfaceDestructor(currentlyConstructingEnv1, functionGeneratorByName1)
    val (currentlyConstructingEnvH, functionGeneratorByNameH) = addImplDestructor(currentlyConstructingEnv2, functionGeneratorByName2)
    val (currentlyConstructingEnv4, functionGeneratorByName4) = addDrop(currentlyConstructingEnvH, functionGeneratorByNameH)
    val currentlyConstructingEnv5 = addArrayLen(currentlyConstructingEnv4)
    val currentlyConstructingEnv6 = addPanic(currentlyConstructingEnv5)
    (currentlyConstructingEnv6, functionGeneratorByName4)
  }

  private def addConcreteDestructor(
    currentlyConstructingEnv: NamespaceEnvironment[IName2],
    functionGeneratorByName: Map[String, IFunctionGenerator]
  ): (NamespaceEnvironment[IName2], Map[String, IFunctionGenerator]) = {
    // Note the virtuality None in the header, and how we filter so this only applies
    // to structs and not interfaces. We use a different template for interface destructors.
    (
    currentlyConstructingEnv
      .addEntry(
        FunctionTemplateName2(CallTemplar.DESTRUCTOR_NAME, CodeLocation2(0, 0)),
        FunctionEnvEntry(
          FunctionA(
            FunctionNameA(CallTemplar.DESTRUCTOR_NAME, s.CodeLocationS(1, 1)),
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
                TemplexAR(NameAT(CodeTypeNameA("Void"), CoordTemplataType)))),
            GeneratedBodyA("concreteDestructorGenerator")))),
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
  //              case List(Coord(_, OrdinaryClosure2(_, structRef2, _))) => {
  //                DestructorTemplar.generateStructDestructor(
  //                  env, temputs, maybeOriginFunction1, templateArgTemplatas, paramCoords, structRef2)
  //              }
  //              case List(Coord(_, TemplatedClosure2(_, structRef2, _))) => {
  //                DestructorTemplar.generateStructDestructor(
  //                  env, temputs, maybeOriginFunction1, templateArgTemplatas, paramCoords, structRef2)
  //              }
                case List(Coord(_, sr @ StructRef2(_))) => {
                  DestructorTemplar.generateStructDestructor(
                    env, temputs, maybeOriginFunction1.get, paramCoords, sr)
                }
                case List(r @ Coord(_, as @ ArraySequenceT2(_, _))) => {
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
    functionGeneratorByName: Map[String, IFunctionGenerator]
  ): (NamespaceEnvironment[IName2], Map[String, IFunctionGenerator]) = {

    (
    currentlyConstructingEnv
      .addUnevaluatedFunction(
        FunctionA(
          FunctionNameA(CallTemplar.INTERFACE_DESTRUCTOR_NAME, CodeLocationS(1, 1)),
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
              TemplexAR(NameAT(CodeTypeNameA("Void"), CoordTemplataType)))),
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
    functionGeneratorByName: Map[String, IFunctionGenerator]
  ): (NamespaceEnvironment[IName2], Map[String, IFunctionGenerator]) = {
    (
    currentlyConstructingEnv
      .addUnevaluatedFunction(
        FunctionA(
          FunctionNameA(CallTemplar.INTERFACE_DESTRUCTOR_NAME, CodeLocationS(1, 1)),
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
              TemplexAR(NameAT(CodeTypeNameA("Void"), CoordTemplataType)))),
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
                    namedEnv.globalEnv, temputs, maybeOriginFunction1, structDef2, implementedInterfaceRef2)
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
    functionGeneratorByName: Map[String, IFunctionGenerator]
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
            FunctionNameA(CallTemplar.DROP_FUNCTION_NAME, CodeLocationS(1, 1)),
            true,
            TemplateTemplataType(List(CoordTemplataType), FunctionTemplataType),
            Set(CodeRuneA("V")),
            List(CodeRuneA("T")),
            Set(CodeRuneA("T"), CodeRuneA("V")),
            Map(
              CodeRuneA("T") -> CoordTemplataType,
              CodeRuneA("V") -> CoordTemplataType),
            List(
              ParameterA(AtomAP(CaptureA(CodeVarNameA("x"), FinalP), None, CodeRuneA("T"), None))),
            Some(CodeRuneA("V")),
            List(
              TemplexAR(RuneAT(CodeRuneA("T"), CoordTemplataType)),
              EqualsAR(
                TemplexAR(RuneAT(CodeRuneA("V"), CoordTemplataType)),
                TemplexAR(NameAT(CodeTypeNameA("Void"), CoordTemplataType)))),
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
          FunctionNameA("len", s.CodeLocationS(1, 1)),
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
              TemplexAR(NameAT(CodeTypeNameA("Int"), CoordTemplataType)))),
          CodeBodyA(
            BodyAE(
              List(),
              BlockAE(
                List(LocalVariableA(CodeVarNameA("arr"), FinalP, NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed)),
                List(
                  ArrayLengthAE(
                    LocalLoadAE(CodeVarNameA("arr"), false))))))))
      .addUnevaluatedFunction(
        FunctionA(
          FunctionNameA("len", s.CodeLocationS(0, 1)),
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
              TemplexAR(NameAT(CodeTypeNameA("Int"), CoordTemplataType)))),
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
          FunctionNameA("panic", s.CodeLocationS(1, 1)),
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
}
