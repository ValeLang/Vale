//package net.verdagon.vale.templar.macros.drop
//
//import net.verdagon.vale.astronomer.{FunctionA}
//import net.verdagon.vale.parser.{OwnP, ReadonlyP, ReadwriteP, ShareP}
//import net.verdagon.vale.scout._
//import net.verdagon.vale.scout.patterns.{AtomSP, CaptureS}
//import net.verdagon.vale.scout.rules._
//import net.verdagon.vale.templar.ast.{FunctionHeaderT, LocationInFunctionEnvironment, ParameterT, PrototypeT}
//import net.verdagon.vale.templar.citizen.StructTemplar
//import net.verdagon.vale.templar.env.{FunctionEnvironment, IEnvironment}
//import net.verdagon.vale.templar.function.{DestructorTemplar, FunctionTemplarCore}
//import net.verdagon.vale.templar.templata.Conversions
//import net.verdagon.vale.templar.types._
//import net.verdagon.vale.templar._
//import net.verdagon.vale._
//import net.verdagon.vale.templar.expression.CallTemplar
//
//class ConcreteDestructorMacro(overloadTemplar: OverloadTemplar) {
//
//  def addConcreteDestructor(mutability: MutabilityT): (FunctionA, IFunctionGenerator) = {
//    // Note the virtuality None in the header, and how we filter so this only applies
//    // to structs and not interfaces. We use a different template for interface destructors.
//    val unevaluatedFunction =
//    FunctionA(
//      RangeS.internal(-68),
//      FunctionNameS(CallTemplar.DROP_FUNCTION_NAME, CodeLocationS.internal(-16)),
////      if (mutability == MutableT) {
////        FunctionNameS(CallTemplar.MUT_DESTRUCTOR_NAME, CodeLocationS.internal(-16))
////      } else {
////        ImmConcreteDestructorNameS(PackageCoordinate.internal)
////      },
//      Vector(),
//      TemplateTemplataType(Vector(CoordTemplataType), FunctionTemplataType),
//      Vector(RuneUsage(RangeS.internal(-68001), CodeRuneS("T"))),
//      Map(
//        CodeRuneS("XX") -> KindTemplataType,
//        CodeRuneS("T") -> CoordTemplataType,
//        CodeRuneS("V") -> CoordTemplataType,
//        CodeRuneS("M") -> MutabilityTemplataType,
//        CodeRuneS("O") -> OwnershipTemplataType,
//        CodeRuneS("P") -> PermissionTemplataType),
//      Vector(
//        ParameterS(AtomSP(RangeS.internal(-1339), Some(CaptureS(CodeVarNameS("this"))), None, Some(RuneUsage(RangeS.internal(-68002), CodeRuneS("T"))), None))),
//      Some(RuneUsage(RangeS.internal(-68002), CodeRuneS("V"))),
//      // RangeS.internal(-16725),
//      // RangeS.internal(-16726)
//      // RangeS.internal(-167210)
//      // RangeS.internal(-1672),
//      // RangeS.internal(-167212)
//      // EqualsSR(RangeS.internal(-167211),
//      Vector(
//        KindComponentsSR(RangeS.internal(-93), RuneUsage(RangeS.internal(-68002), CodeRuneS("XX")), RuneUsage(RangeS.internal(-68002), CodeRuneS("M"))),
//        LiteralSR(RangeS.internal(-16724),RuneUsage(RangeS.internal(-68002), CodeRuneS("M")),MutabilityLiteralSL(Conversions.unevaluateMutability(mutability))),
//        CoordComponentsSR(
//          RangeS.internal(-94), RuneUsage(RangeS.internal(-68002), CodeRuneS("T")), RuneUsage(RangeS.internal(-68002), CodeRuneS("O")), RuneUsage(RangeS.internal(-68002), CodeRuneS("P")), RuneUsage(RangeS.internal(-68002), CodeRuneS("XX"))),
//        IsConcreteSR(RangeS.internal(-16729), RuneUsage(RangeS.internal(-68002), CodeRuneS("XX"))),
//        OneOfSR(RangeS.internal(-16727),RuneUsage(RangeS.internal(-68002), CodeRuneS("O")),Array(OwnershipLiteralSL(OwnP), OwnershipLiteralSL(ShareP))),
//        OneOfSR(RangeS.internal(-16728),RuneUsage(RangeS.internal(-68002), CodeRuneS("P")),Array(PermissionLiteralSL(ReadwriteP), PermissionLiteralSL(ReadonlyP))),
//        LookupSR(RangeS.internal(-167213),RuneUsage(RangeS.internal(-68002), CodeRuneS("V")),CodeNameS("void"))),
//      GeneratedBodyS("concreteDestructorGenerator"))
//    val generator =
//      new IFunctionGenerator {
//        override def generate(
//          profiler: IProfiler,
//          functionTemplarCore: FunctionTemplarCore,
//          structTemplar: StructTemplar,
//          destructorTemplar: DestructorTemplar,
//          arrayTemplar: ArrayTemplar,
//          env: FunctionEnvironment,
//          temputs: Temputs,
//          life: LocationInFunctionEnvironment,
//          callRange: RangeS,
//          maybeOriginFunction1: Option[FunctionA],
//          paramCoords: Vector[ParameterT],
//          maybeReturnType2: Option[CoordT]):
//        (FunctionHeaderT) = {
//          // Even though below we treat packs, closures, and structs the same, they're
//          // still disambiguated by the template arguments.
//          vimpl()
////          paramCoords.map(_.tyype) match {
////            case Vector(CoordT(_, _, PackTT(_, structTT))) => {
////              destructorTemplar.generateStructDestructor(
////                env, temputs, maybeOriginFunction1.get, paramCoords, structTT)
////            }
////            case Vector(CoordT(_, _, sr @ StructTT(_))) => {
////              destructorTemplar.generateStructDestructor(
////                env, temputs, maybeOriginFunction1.get, paramCoords, sr)
////            }
////            case Vector(r @ CoordT(_, _, as @ StaticSizedArrayTT(_, _))) => {
////              destructorTemplar.generateStaticSizedArrayDestructor(
////                env, temputs, life + 0, maybeOriginFunction1, r, as)
////            }
////            case Vector(r @ CoordT(_, _, ra @ RuntimeSizedArrayTT(_))) => {
////              destructorTemplar.generateRuntimeSizedArrayDestructor(
////                env, temputs, life + 1, maybeOriginFunction1, r, ra)
////            }
////            case _ => {
////              vfail("wot")
////            }
////          }
//        }
//      }
//    (unevaluatedFunction, generator)
//  }
//
//  def getImmConcreteDestructor(
//    temputs: Temputs,
//    env: IEnvironment,
//    structTT: StructTT):
//  PrototypeT = {
//    vassert(Templar.getMutability(temputs, structTT) == ImmutableT)
//
//    overloadTemplar.findFunction(
//      env,
//      temputs,
//      RangeS.internal(-1673),
//      FreeImpreciseNameS(),
//      Vector.empty,
//      Array.empty,
//      Vector(ParamFilter(CoordT(ShareT, ReadonlyT, structTT), None)),
//      Vector.empty,
//      true)
//  }
//
//}
