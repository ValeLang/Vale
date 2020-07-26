package net.verdagon.vale.astronomer.builtins

import net.verdagon.vale.astronomer._
import net.verdagon.vale.parser.{CaptureP, FinalP, OwnP, ShareP}
import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
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
        EqualsAR(TemplexAR(RuneAT(CodeRuneA("S"), CoordTemplataType)), TemplexAR(NameAT(CodeTypeNameA("str"), CoordTemplataType))),
        EqualsAR(TemplexAR(RuneAT(CodeRuneA("R"), CoordTemplataType)), TemplexAR(NameAT(CodeTypeNameA("void"), CoordTemplataType)))),
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
                    LocalLoadAE(CodeVarNameA("line"), OwnP),
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
        EqualsAR(TemplexAR(RuneAT(CodeRuneA("I"), CoordTemplataType)), TemplexAR(NameAT(CodeTypeNameA("int"), CoordTemplataType))),
        EqualsAR(TemplexAR(RuneAT(CodeRuneA("R"), CoordTemplataType)), TemplexAR(NameAT(CodeTypeNameA("void"), CoordTemplataType)))),
      CodeBodyA(
        BodyAE(
          List(),
          BlockAE(
            List(LocalVariableA(CodeVarNameA("line"), FinalP, NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed)),
            List(FunctionCallAE(
              FunctionLoadAE(GlobalFunctionFamilyNameA("println")),
              List(
                FunctionCallAE(
                  FunctionLoadAE(GlobalFunctionFamilyNameA("str")),
                  List(
                    LocalLoadAE(CodeVarNameA("line"), OwnP))))))))))

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
        EqualsAR(TemplexAR(RuneAT(CodeRuneA("I"), CoordTemplataType)), TemplexAR(NameAT(CodeTypeNameA("int"), CoordTemplataType))),
        EqualsAR(TemplexAR(RuneAT(CodeRuneA("R"), CoordTemplataType)), TemplexAR(NameAT(CodeTypeNameA("void"), CoordTemplataType)))),
      CodeBodyA(
        BodyAE(
          List(),
          BlockAE(
            List(LocalVariableA(CodeVarNameA("line"), FinalP, NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed)),
            List(FunctionCallAE(
              FunctionLoadAE(GlobalFunctionFamilyNameA("print")),
              List(
                FunctionCallAE(
                  FunctionLoadAE(GlobalFunctionFamilyNameA("str")),
                  List(
                    LocalLoadAE(CodeVarNameA("line"), OwnP))))))))))
}
