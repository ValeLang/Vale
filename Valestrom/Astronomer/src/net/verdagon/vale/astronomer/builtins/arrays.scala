package net.verdagon.vale.astronomer.builtins

import net.verdagon.vale.astronomer._
import net.verdagon.vale.parser.{BorrowP, CaptureP, FinalP, MutabilityP, MutableP}
import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.scout.patterns.AtomSP

object Arrays {
  def makeArrayFunction(mutability: MutabilityP): FunctionA = {
    FunctionA(
      FunctionNameA("Array", if (mutability == MutableP) { CodeLocationS(1, 1) } else { CodeLocationS(2, 1) }),
      false,
      TemplateTemplataType(List(MutabilityTemplataType, CoordTemplataType), FunctionTemplataType),
      Set(CodeRuneA("I")),
      List(CodeRuneA("ArrayMutability"), CodeRuneA("T"), CodeRuneA("Generator")),
      Set(
        CodeRuneA("I"),
        CodeRuneA("ArrayMutability"),
        CodeRuneA("T"),
        CodeRuneA("Generator"),
        CodeRuneA("M"),
        CodeRuneA("R")),
      Map(
        CodeRuneA("ArrayMutability") -> MutabilityTemplataType,
        CodeRuneA("I") -> CoordTemplataType,
        CodeRuneA("T") -> CoordTemplataType,
        CodeRuneA("Generator") -> CoordTemplataType,
        CodeRuneA("M") -> MutabilityTemplataType,
        CodeRuneA("R") -> MutabilityTemplataType),
      List(
        ParameterA(AtomAP(CaptureA(CodeVarNameA("size"), FinalP), None, CodeRuneA("I"), None)),
        ParameterA(AtomAP(CaptureA(CodeVarNameA("generator"), FinalP), None, CodeRuneA("Generator"), None))),
      Some(CodeRuneA("R")),
      List(
        EqualsAR(TemplexAR(RuneAT(CodeRuneA("ArrayMutability"), MutabilityTemplataType)), TemplexAR(MutabilityAT(mutability))),
        EqualsAR(TemplexAR(RuneAT(CodeRuneA("I"), CoordTemplataType)), TemplexAR(NameAT(CodeTypeNameA("Int"), CoordTemplataType))),
        TemplexAR(RuneAT(CodeRuneA("T"), CoordTemplataType)),
        EqualsAR(
          TemplexAR(RuneAT(CodeRuneA("Generator"), CoordTemplataType)),
          ComponentsAR(
            CoordTemplataType,
            List(
              TemplexAR(OwnershipAT(BorrowP)),
              TemplexAR(
                CallAT(
                  NameAT(CodeTypeNameA("IFunction1"), TemplateTemplataType(List(MutabilityTemplataType, CoordTemplataType, CoordTemplataType), KindTemplataType)),
                  List(
                    RuneAT(CodeRuneA("M"), MutabilityTemplataType),
                    NameAT(CodeTypeNameA("Int"), CoordTemplataType),
                    RuneAT(CodeRuneA("T"), CoordTemplataType)),
                  KindTemplataType))))),
        EqualsAR(
          TemplexAR(RuneAT(CodeRuneA("R"), CoordTemplataType)),
          TemplexAR(
            CallAT(
              NameAT(
                CodeTypeNameA("Array"),
                TemplateTemplataType(List(MutabilityTemplataType, CoordTemplataType), KindTemplataType)),
              List(RuneAT(CodeRuneA("ArrayMutability"), MutabilityTemplataType), RuneAT(CodeRuneA("T"), CoordTemplataType)),
              CoordTemplataType)))),
      CodeBodyA(
        BodyAE(
          List(),
          BlockAE(
            List(
              LocalVariableA(CodeVarNameA("size"), FinalP, NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed),
              LocalVariableA(CodeVarNameA("generator"), FinalP, NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed)),
            List(
              ConstructArrayAE(
                RuneAT(CodeRuneA("T"), CoordTemplataType),
                LocalLoadAE(CodeVarNameA("size"), false),
                LocalLoadAE(CodeVarNameA("generator"), false),
                mutability))))))
  }
}
