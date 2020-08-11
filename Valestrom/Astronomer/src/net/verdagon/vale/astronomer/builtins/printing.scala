package net.verdagon.vale.astronomer.builtins

import net.verdagon.vale.astronomer._
import net.verdagon.vale.parser.{CaptureP, FinalP, OwnP, ShareP}
import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.scout.patterns.AtomSP

object Printing {
  val printlnStrName = FunctionNameA("println", CodeLocationS.internal(-9))
  val printlnStr =
    FunctionA(
      RangeS.internal(-58),
      printlnStrName,
      List(),
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
              RangeS.internal(-38),
              FunctionLoadAE(GlobalFunctionFamilyNameA("print")),
              List(
                FunctionCallAE(
                  RangeS.internal(-43),
                  FunctionLoadAE(GlobalFunctionFamilyNameA("+")),
                  List(
                    LocalLoadAE(CodeVarNameA("line"), OwnP),
                    StrLiteralAE("\n"))))))))))

  val printlnIntName = FunctionNameA("println", CodeLocationS.internal(-10))
  val printlnInt =
    FunctionA(
      RangeS.internal(-54),
      printlnIntName,
      List(),
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
              RangeS.internal(-39),
              FunctionLoadAE(GlobalFunctionFamilyNameA("println")),
              List(
                FunctionCallAE(
                  RangeS.internal(-44),
                  FunctionLoadAE(GlobalFunctionFamilyNameA("str")),
                  List(
                    LocalLoadAE(CodeVarNameA("line"), OwnP))))))))))

  val printlnBoolName = FunctionNameA("println", CodeLocationS.internal(-34))
  val printlnBool =
    FunctionA(
      RangeS.internal(-55),
      printlnBoolName,
      List(),
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
        EqualsAR(TemplexAR(RuneAT(CodeRuneA("I"), CoordTemplataType)), TemplexAR(NameAT(CodeTypeNameA("bool"), CoordTemplataType))),
        EqualsAR(TemplexAR(RuneAT(CodeRuneA("R"), CoordTemplataType)), TemplexAR(NameAT(CodeTypeNameA("void"), CoordTemplataType)))),
      CodeBodyA(
        BodyAE(
          List(),
          BlockAE(
            List(LocalVariableA(CodeVarNameA("line"), FinalP, NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed)),
            List(FunctionCallAE(
              RangeS.internal(-39),
              FunctionLoadAE(GlobalFunctionFamilyNameA("println")),
              List(
                FunctionCallAE(
                  RangeS.internal(-45),
                  FunctionLoadAE(GlobalFunctionFamilyNameA("str")),
                  List(
                    LocalLoadAE(CodeVarNameA("line"), OwnP))))))))))

  val printIntName = FunctionNameA("print", CodeLocationS.internal(-12))
  val printInt =
    FunctionA(
      RangeS.internal(-51),
      printIntName,
      List(),
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
              RangeS.internal(-40),
              FunctionLoadAE(GlobalFunctionFamilyNameA("print")),
              List(
                FunctionCallAE(
                  RangeS.internal(-46),
                  FunctionLoadAE(GlobalFunctionFamilyNameA("str")),
                  List(
                    LocalLoadAE(CodeVarNameA("line"), OwnP))))))))))

  val printBoolName = FunctionNameA("print", CodeLocationS.internal(-12))
  val printBool =
    FunctionA(
      RangeS.internal(-52),
      printBoolName,
      List(),
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
        EqualsAR(TemplexAR(RuneAT(CodeRuneA("I"), CoordTemplataType)), TemplexAR(NameAT(CodeTypeNameA("bool"), CoordTemplataType))),
        EqualsAR(TemplexAR(RuneAT(CodeRuneA("R"), CoordTemplataType)), TemplexAR(NameAT(CodeTypeNameA("void"), CoordTemplataType)))),
      CodeBodyA(
        BodyAE(
          List(),
          BlockAE(
            List(LocalVariableA(CodeVarNameA("line"), FinalP, NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed)),
            List(FunctionCallAE(
              RangeS.internal(-41),
              FunctionLoadAE(GlobalFunctionFamilyNameA("print")),
              List(
                FunctionCallAE(
                  RangeS.internal(-47),
                  FunctionLoadAE(GlobalFunctionFamilyNameA("str")),
                  List(
                    LocalLoadAE(CodeVarNameA("line"), OwnP))))))))))
}
