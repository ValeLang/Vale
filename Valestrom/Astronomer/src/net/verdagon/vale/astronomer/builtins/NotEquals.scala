package net.verdagon.vale.astronomer.builtins

import net.verdagon.vale.astronomer._
import net.verdagon.vale.parser.{CaptureP, FinalP, OwnP}
import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.scout.patterns.AtomSP

object NotEquals {
  val name = FunctionNameA("!=", CodeLocationS.internal(-2))
  val function =
    FunctionA(
      RangeS.internal(-50),
      name,
      List(),
      TemplateTemplataType(List(CoordTemplataType), FunctionTemplataType),
      Set(),
      List(CodeRuneA("T")),
      Set(CodeRuneA("T"), CodeRuneA("B")),
      Map(CodeRuneA("T") -> CoordTemplataType, CodeRuneA("B") -> CoordTemplataType),
      List(
        ParameterA(AtomAP(RangeS.internal(-108), CaptureA(CodeVarNameA("left"), FinalP), None, CodeRuneA("T"), None)),
        ParameterA(AtomAP(RangeS.internal(-109), CaptureA(CodeVarNameA("right"), FinalP), None, CodeRuneA("T"), None))),
      Some(CodeRuneA("B")),
      List(
        TemplexAR(RuneAT(RangeS.internal(-56),CodeRuneA("T"), CoordTemplataType)),
        EqualsAR(RangeS.internal(-1402),TemplexAR(RuneAT(RangeS.internal(-56),CodeRuneA("B"), CoordTemplataType)), TemplexAR(NameAT(RangeS.internal(-56),CodeTypeNameA("bool"), CoordTemplataType)))),
      CodeBodyA(
        BodyAE(
          List(),
          BlockAE(
            List(
              LocalVariableA(CodeVarNameA("left"), FinalP, NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed),
              LocalVariableA(CodeVarNameA("right"), FinalP, NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed)),
            List(FunctionCallAE(
              RangeS.internal(-36),
              FunctionLoadAE(RangeS.internal(-36), GlobalFunctionFamilyNameA("not")),
              List(
                FunctionCallAE(
                  RangeS.internal(-37),
                  FunctionLoadAE(RangeS.internal(-37), GlobalFunctionFamilyNameA("==")),
                  List(
                    LocalLoadAE(CodeVarNameA("left"), OwnP),
                    LocalLoadAE(CodeVarNameA("right"), OwnP))))))))))
}
