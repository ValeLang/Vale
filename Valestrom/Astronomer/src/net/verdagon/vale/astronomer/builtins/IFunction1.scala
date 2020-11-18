package net.verdagon.vale.astronomer.builtins

import net.verdagon.vale.astronomer._
import net.verdagon.vale.parser.{BorrowP, CaptureP, FinalP, MutableP}
import net.verdagon.vale.scout.{CodeLocationS, MaybeUsed, NotUsed, ParameterS, RangeS}
import net.verdagon.vale.scout.patterns.{AbstractSP, AtomSP}

import scala.collection.immutable.{List, Map}

object IFunction1 {
  val interface =
    InterfaceA(
      RangeS.internal(-69),
      TopLevelCitizenDeclarationNameA("IFunction1", CodeLocationS.internal(-7)),
      List(),
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
        TemplexAR(RuneAT(RangeS.internal(-56),CodeRuneA("M"), MutabilityTemplataType)),
        TemplexAR(RuneAT(RangeS.internal(-56),CodeRuneA("P1"), CoordTemplataType)),
        TemplexAR(RuneAT(RangeS.internal(-56),CodeRuneA("R"), CoordTemplataType))),
      List(
        FunctionA(
          RangeS.internal(-56),
          FunctionNameA("__call", CodeLocationS.internal(-8)),
          List(),
          FunctionTemplataType,
          Set(),
          List(),
          Set(CodeRuneA("BorrowThis"), CodeRuneA("ThisK")),
          Map(
            CodeRuneA("BorrowThis") -> CoordTemplataType,
            CodeRuneA("ThisK") -> CoordTemplataType),
          List(
            ParameterA(AtomAP(RangeS.internal(-119), LocalVariableA(CodeVarNameA("this"), FinalP, MaybeUsed, MaybeUsed, MaybeUsed, MaybeUsed, MaybeUsed, MaybeUsed), Some(AbstractAP), CodeRuneA("BorrowThis"), None)),
            ParameterA(AtomAP(RangeS.internal(-120), LocalVariableA(CodeVarNameA("p1"), FinalP, MaybeUsed, MaybeUsed, MaybeUsed, MaybeUsed, MaybeUsed, MaybeUsed), None, CodeRuneA("P1"), None))),
          Some(CodeRuneA("R")),
          List(
            EqualsAR(
              RangeS.internal(-1400),
              TemplexAR(RuneAT(RangeS.internal(-56),CodeRuneA("ThisK"), CoordTemplataType)),
              TemplexAR(
                CallAT(RangeS.internal(-56),
                  NameAT(RangeS.internal(-56),CodeTypeNameA("IFunction1"), TemplateTemplataType(List(MutabilityTemplataType, CoordTemplataType, CoordTemplataType), KindTemplataType)),
                  List(
                    RuneAT(RangeS.internal(-56),CodeRuneA("M"), MutabilityTemplataType),
                    RuneAT(RangeS.internal(-56),CodeRuneA("P1"), CoordTemplataType),
                    RuneAT(RangeS.internal(-56),CodeRuneA("R"), CoordTemplataType)),
                  CoordTemplataType))),
            EqualsAR(
              RangeS.internal(-1401),
              TemplexAR(RuneAT(RangeS.internal(-56),CodeRuneA("BorrowThis"), CoordTemplataType)),
              TemplexAR(OwnershippedAT(RangeS.internal(-56),BorrowP, RuneAT(RangeS.internal(-56),CodeRuneA("ThisK"), CoordTemplataType))))),
          AbstractBodyA)))

}
