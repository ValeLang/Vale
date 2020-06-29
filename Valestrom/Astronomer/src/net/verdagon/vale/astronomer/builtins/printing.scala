package net.verdagon.vale.astronomer.builtins

import net.verdagon.vale.astronomer._
import net.verdagon.vale.parser.{CaptureP, FinalP, ShareP}
import net.verdagon.vale.scout.{IEnvironment => _, FunctionEnvironment => _, Environment => _, _}
import net.verdagon.vale.scout.patterns.AtomSP

object Printing {
  val printlnStrName = FunctionNameA("println", CodeLocationS(1, 1))
  val printlnStr =
    FunctionA(
      printlnStrName,
      false,
      FunctionTemplataType,
      Set(CodeRuneA("S"), CodeRuneA("R")),
      List(),
      Set(CodeRuneA("S"), CodeRuneA("R")),
      Map(
        CodeRuneA("R") -> CoordTemplataType,
        CodeRuneA("S") -> CoordTemplataType),
      List(
        ParameterA(AtomAP(CaptureA(CodeVarNameA("line"), FinalP), None, CodeRuneA("S"), None))),
      Some(CodeRuneA("R")),
      List(
        EqualsAR(TemplexAR(RuneAT(CodeRuneA("S"), CoordTemplataType)), TemplexAR(NameAT(CodeTypeNameA("Str"), CoordTemplataType))),
        EqualsAR(TemplexAR(RuneAT(CodeRuneA("R"), CoordTemplataType)), TemplexAR(NameAT(CodeTypeNameA("Void"), CoordTemplataType)))),
      CodeBodyA(
        BodyAE(
          List(),
          BlockAE(
            List(LocalVariableA(CodeVarNameA("line"), FinalP, NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed)),
            List(FunctionCallAE(
              FunctionLoadAE(GlobalFunctionFamilyNameA("print")),
              List(
                FunctionCallAE(
                  FunctionLoadAE(GlobalFunctionFamilyNameA("+")),
                  List(
                    LocalLoadAE(CodeVarNameA("line"), false),
                    StrLiteralAE("\n"))))))))))

  val printlnIntName = FunctionNameA("println", CodeLocationS(1, 1))
  val printlnInt =
    FunctionA(
      printlnIntName,
      false,
      FunctionTemplataType,
      Set(CodeRuneA("R"), CodeRuneA("I")),
      List(),
      Set(CodeRuneA("R"), CodeRuneA("I")),
      Map(
        CodeRuneA("R") -> CoordTemplataType,
        CodeRuneA("I") -> CoordTemplataType),
      List(
        ParameterA(AtomAP(CaptureA(CodeVarNameA("line"), FinalP), None, CodeRuneA("I"), None))),
      Some(CodeRuneA("R")),
      List(
        EqualsAR(TemplexAR(RuneAT(CodeRuneA("I"), CoordTemplataType)), TemplexAR(NameAT(CodeTypeNameA("Int"), CoordTemplataType))),
        EqualsAR(TemplexAR(RuneAT(CodeRuneA("R"), CoordTemplataType)), TemplexAR(NameAT(CodeTypeNameA("Void"), CoordTemplataType)))),
      CodeBodyA(
        BodyAE(
          List(),
          BlockAE(
            List(LocalVariableA(CodeVarNameA("line"), FinalP, NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed)),
            List(FunctionCallAE(
              FunctionLoadAE(GlobalFunctionFamilyNameA("println")),
              List(
                FunctionCallAE(
                  FunctionLoadAE(GlobalFunctionFamilyNameA("Str")),
                  List(
                    LocalLoadAE(CodeVarNameA("line"), false))))))))))

  val printIntName = FunctionNameA("print", CodeLocationS(1, 1))
  val printInt =
    FunctionA(
      printIntName,
      false,
      FunctionTemplataType,
      Set(CodeRuneA("I"), CodeRuneA("R")),
      List(),
      Set(CodeRuneA("I"), CodeRuneA("R")),
      Map(
        CodeRuneA("I") -> CoordTemplataType,
        CodeRuneA("R") -> CoordTemplataType,
      ),
      List(
        ParameterA(AtomAP(CaptureA(CodeVarNameA("line"), FinalP), None, CodeRuneA("I"), None))),
      Some(CodeRuneA("R")),
      List(
        EqualsAR(TemplexAR(RuneAT(CodeRuneA("I"), CoordTemplataType)), TemplexAR(NameAT(CodeTypeNameA("Int"), CoordTemplataType))),
        EqualsAR(TemplexAR(RuneAT(CodeRuneA("R"), CoordTemplataType)), TemplexAR(NameAT(CodeTypeNameA("Void"), CoordTemplataType)))),
      CodeBodyA(
        BodyAE(
          List(),
          BlockAE(
            List(LocalVariableA(CodeVarNameA("line"), FinalP, NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed)),
            List(FunctionCallAE(
              FunctionLoadAE(GlobalFunctionFamilyNameA("print")),
              List(
                FunctionCallAE(
                  FunctionLoadAE(GlobalFunctionFamilyNameA("Str")),
                  List(
                    LocalLoadAE(CodeVarNameA("line"), false))))))))))
}
