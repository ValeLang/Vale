package net.verdagon.vale.astronomer.builtins

import net.verdagon.vale.astronomer._
import net.verdagon.vale.parser.{BorrowP, CaptureP, FinalP, MutableP}
import net.verdagon.vale.scout.{CodeLocationS, ParameterS}
import net.verdagon.vale.scout.patterns.{AbstractSP, AtomSP}

import scala.collection.immutable.{List, Map}

object IFunction1 {
  val interface =
    InterfaceA(
      TopLevelCitizenDeclarationNameA("IFunction1", CodeLocationS(1, 1)),
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
        TemplexAR(RuneAT(CodeRuneA("M"), MutabilityTemplataType)),
        TemplexAR(RuneAT(CodeRuneA("P1"), CoordTemplataType)),
        TemplexAR(RuneAT(CodeRuneA("R"), CoordTemplataType))),
      List(
        FunctionA(
          FunctionNameA("__call", CodeLocationS(1, 0)),
          true,
          FunctionTemplataType,
          Set(),
          List(),
          Set(CodeRuneA("BorrowThis"), CodeRuneA("ThisK")),
          Map(
            CodeRuneA("BorrowThis") -> CoordTemplataType,
            CodeRuneA("ThisK") -> CoordTemplataType),
          List(
            ParameterA(AtomAP(CaptureA(CodeVarNameA("this"), FinalP), Some(AbstractAP), CodeRuneA("BorrowThis"), None)),
            ParameterA(AtomAP(CaptureA(CodeVarNameA("p1"), FinalP), None, CodeRuneA("P1"), None))),
          Some(CodeRuneA("R")),
          List(
            EqualsAR(
              TemplexAR(RuneAT(CodeRuneA("ThisK"), CoordTemplataType)),
              TemplexAR(
                CallAT(
                  NameAT(CodeTypeNameA("IFunction1"), TemplateTemplataType(List(MutabilityTemplataType, CoordTemplataType, CoordTemplataType), KindTemplataType)),
                  List(
                    RuneAT(CodeRuneA("M"), MutabilityTemplataType),
                    RuneAT(CodeRuneA("P1"), CoordTemplataType),
                    RuneAT(CodeRuneA("R"), CoordTemplataType)),
                  CoordTemplataType))),
            EqualsAR(
              TemplexAR(RuneAT(CodeRuneA("BorrowThis"), CoordTemplataType)),
              TemplexAR(OwnershippedAT(BorrowP, RuneAT(CodeRuneA("ThisK"), CoordTemplataType))))),
          AbstractBodyA)))

}
