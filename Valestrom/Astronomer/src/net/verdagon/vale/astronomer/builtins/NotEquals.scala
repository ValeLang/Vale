package net.verdagon.vale.astronomer.builtins

import net.verdagon.vale.astronomer._
import net.verdagon.vale.parser.{CaptureP, FinalP}
import net.verdagon.vale.scout.{IEnvironment => _, FunctionEnvironment => _, Environment => _, _}
import net.verdagon.vale.scout.patterns.AtomSP

object NotEquals {
  val name = FunctionNameA("!=", CodeLocationS(1, 1))
  val function =
    FunctionA(
      name,
      false,
      TemplateTemplataType(List(CoordTemplataType), FunctionTemplataType),
      Set(),
      List(CodeRuneA("T")),
      Set(CodeRuneA("T"), CodeRuneA("B")),
      Map(CodeRuneA("T") -> CoordTemplataType, CodeRuneA("B") -> CoordTemplataType),
      List(
        ParameterA(AtomAP(CaptureA(CodeVarNameA("left"), FinalP), None, CodeRuneA("T"), None)),
        ParameterA(AtomAP(CaptureA(CodeVarNameA("right"), FinalP), None, CodeRuneA("T"), None))),
      Some(CodeRuneA("B")),
      List(
        TemplexAR(RuneAT(CodeRuneA("T"), CoordTemplataType)),
        EqualsAR(TemplexAR(RuneAT(CodeRuneA("B"), CoordTemplataType)), TemplexAR(NameAT(CodeTypeNameA("bool"), CoordTemplataType)))),
      CodeBodyA(
        BodyAE(
          List(),
          BlockAE(
            List(
              LocalVariableA(CodeVarNameA("left"), FinalP, NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed),
              LocalVariableA(CodeVarNameA("right"), FinalP, NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed)),
            List(FunctionCallAE(
              FunctionLoadAE(GlobalFunctionFamilyNameA("not")),
              List(
                FunctionCallAE(
                  FunctionLoadAE(GlobalFunctionFamilyNameA("==")),
                  List(
                    LocalLoadAE(CodeVarNameA("left"), false),
                    LocalLoadAE(CodeVarNameA("right"), false))))))))))
}
