package net.verdagon.vale.astronomer.builtins

import net.verdagon.vale.astronomer._
import net.verdagon.vale.parser.{ConstraintP, CaptureP, MutableP, ReadwriteP}
import net.verdagon.vale.scout.{CodeLocationS, MaybeUsed, NotUsed, ParameterS, RangeS}
import net.verdagon.vale.scout.patterns.{AbstractSP, AtomSP}

import scala.collection.immutable.{List, Map}

object IFunction1 {
  val interface =
    InterfaceA(
      RangeS.internal(-69),
      TopLevelCitizenDeclarationNameA("IFunction1", CodeLocationS.internal(-7)),
      List.empty,
      false,
      CodeRuneA("M"),
      None,
      TemplateTemplataType(List(MutabilityTemplataType, CoordTemplataType, CoordTemplataType), KindTemplataType),
      Set(),
      List(CodeRuneA("M"), CodeRuneA("P1"), CodeRuneA("R")),
      Set(CodeRuneA("M"), CodeRuneA("P1"), CodeRuneA("R")),
      Map(
        CodeRuneA("M") -> MutabilityTemplataType,
        CodeRuneA("P1") -> CoordTemplataType,
        CodeRuneA("R") -> CoordTemplataType),
      List(
        TemplexAR(RuneAT(RangeS.internal(-5630),CodeRuneA("M"), MutabilityTemplataType)),
        TemplexAR(RuneAT(RangeS.internal(-5631),CodeRuneA("P1"), CoordTemplataType)),
        TemplexAR(RuneAT(RangeS.internal(-5632),CodeRuneA("R"), CoordTemplataType))),
      List(
        FunctionA(
          RangeS.internal(-5633),
          FunctionNameA("__call", CodeLocationS.internal(-8)),
          List.empty,
          FunctionTemplataType,
          Set(),
          List.empty,
          Set(CodeRuneA("BorrowThis"), CodeRuneA("ThisK")),
          Map(
            CodeRuneA("BorrowThis") -> CoordTemplataType,
            CodeRuneA("ThisK") -> CoordTemplataType),
          List(
            ParameterA(AtomAP(RangeS.internal(-119), LocalA(CodeVarNameA("this"), MaybeUsed, MaybeUsed, MaybeUsed, MaybeUsed, MaybeUsed, MaybeUsed), Some(AbstractAP), CodeRuneA("BorrowThis"), None)),
            ParameterA(AtomAP(RangeS.internal(-120), LocalA(CodeVarNameA("p1"), MaybeUsed, MaybeUsed, MaybeUsed, MaybeUsed, MaybeUsed, MaybeUsed), None, CodeRuneA("P1"), None))),
          Some(CodeRuneA("R")),
          List(
            EqualsAR(
              RangeS.internal(-1400),
              TemplexAR(RuneAT(RangeS.internal(-5634),CodeRuneA("ThisK"), CoordTemplataType)),
              TemplexAR(
                CallAT(RangeS.internal(-5635),
                  NameAT(RangeS.internal(-5636),CodeTypeNameA("IFunction1"), TemplateTemplataType(List(MutabilityTemplataType, CoordTemplataType, CoordTemplataType), KindTemplataType)),
                  List(
                    RuneAT(RangeS.internal(-5637),CodeRuneA("M"), MutabilityTemplataType),
                    RuneAT(RangeS.internal(-5638),CodeRuneA("P1"), CoordTemplataType),
                    RuneAT(RangeS.internal(-5639),CodeRuneA("R"), CoordTemplataType)),
                  CoordTemplataType))),
            EqualsAR(
              RangeS.internal(-1401),
              TemplexAR(RuneAT(RangeS.internal(-5640),CodeRuneA("BorrowThis"), CoordTemplataType)),
              TemplexAR(
                InterpretedAT(
                  RangeS.internal(-5641),ConstraintP,ReadwriteP,
                  RuneAT(RangeS.internal(-5642),CodeRuneA("ThisK"), CoordTemplataType))))),
          AbstractBodyA)))

}
