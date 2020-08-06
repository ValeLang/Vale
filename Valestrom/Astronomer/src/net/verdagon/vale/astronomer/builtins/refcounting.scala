package net.verdagon.vale.astronomer.builtins

import net.verdagon.vale.astronomer._
import net.verdagon.vale.parser.{BorrowP, CaptureP, FinalP, OwnP, ShareP}
import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.scout.patterns.AtomSP

object RefCounting {
  val checkvarrc =
    FunctionA(
      FunctionNameA("__checkvarrc", CodeLocationS(-11, 0)),
      false,
      TemplateTemplataType(List(CoordTemplataType), FunctionTemplataType),
      Set(CodeRuneA("V"), CodeRuneA("I")),
      List(CodeRuneA("T")),
      Set(CodeRuneA("T"), CodeRuneA("TK"), CodeRuneA("V"), CodeRuneA("I")),
      Map(
        CodeRuneA("I") -> CoordTemplataType,
        CodeRuneA("T") -> CoordTemplataType,
        CodeRuneA("V") -> CoordTemplataType,
        CodeRuneA("TK") -> KindTemplataType
      ),
      List(
        ParameterA(AtomAP(CaptureA(CodeVarNameA("obj"), FinalP), None, CodeRuneA("T"), None)),
        ParameterA(AtomAP(CaptureA(CodeVarNameA("num"), FinalP), None, CodeRuneA("I"), None))),
      Some(CodeRuneA("V")),
      List(
        EqualsAR(TemplexAR(RuneAT(CodeRuneA("I"), CoordTemplataType)), TemplexAR(NameAT(CodeTypeNameA("int"), CoordTemplataType))),
        EqualsAR(TemplexAR(RuneAT(CodeRuneA("T"), CoordTemplataType)), ComponentsAR(CoordTemplataType, List(TemplexAR(OwnershipAT(BorrowP)), TemplexAR(RuneAT(CodeRuneA("TK"), KindTemplataType))))),
        EqualsAR(TemplexAR(RuneAT(CodeRuneA("V"), CoordTemplataType)), TemplexAR(NameAT(CodeTypeNameA("void"), CoordTemplataType)))),
      CodeBodyA(
        BodyAE(
          List(),
          BlockAE(
            List(
              LocalVariableA(CodeVarNameA("obj"), FinalP, NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed),
              LocalVariableA(CodeVarNameA("num"), FinalP, NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed)),
            List(
              CheckRefCountAE(
                LocalLoadAE(CodeVarNameA("obj"), OwnP),
                VariableRefCount,
                FunctionCallAE(
                  // We add 1 because that "obj" is also a borrow ref
                  FunctionLoadAE(GlobalFunctionFamilyNameA("+")),
                  List(
                    LocalLoadAE(CodeVarNameA("num"), OwnP),
                    IntLiteralAE(1)))),
              VoidAE())))))

  val checkMemberRcName = FunctionNameA("__checkmemberrc", CodeLocationS(-5, 0))
  val checkmemberrc =
    FunctionA(
      checkMemberRcName,
      false,
      TemplateTemplataType(List(CoordTemplataType), FunctionTemplataType),
      Set(CodeRuneA("V"), CodeRuneA("I")),
      List(CodeRuneA("T")),
      Set(CodeRuneA("T"), CodeRuneA("V"), CodeRuneA("I"), CodeRuneA("TK")),
      Map(
        CodeRuneA("I") -> CoordTemplataType,
        CodeRuneA("T") -> CoordTemplataType,
        CodeRuneA("V") -> CoordTemplataType,
        CodeRuneA("TK") -> KindTemplataType),
      List(
        ParameterA(AtomAP(CaptureA(CodeVarNameA("obj"), FinalP), None, CodeRuneA("T"), None)),
        ParameterA(AtomAP(CaptureA(CodeVarNameA("num"), FinalP), None, CodeRuneA("I"), None))),
      Some(CodeRuneA("V")),
      List(
        EqualsAR(TemplexAR(RuneAT(CodeRuneA("I"), CoordTemplataType)), TemplexAR(NameAT(CodeTypeNameA("int"), CoordTemplataType))),
        EqualsAR(TemplexAR(RuneAT(CodeRuneA("T"), CoordTemplataType)), ComponentsAR(CoordTemplataType, List(TemplexAR(OwnershipAT(BorrowP)), TemplexAR(RuneAT(CodeRuneA("TK"), KindTemplataType))))),
        EqualsAR(TemplexAR(RuneAT(CodeRuneA("V"), CoordTemplataType)), TemplexAR(NameAT(CodeTypeNameA("void"), CoordTemplataType)))),
      CodeBodyA(
        BodyAE(
          List(),
          BlockAE(
            List(
              LocalVariableA(CodeVarNameA("obj"), FinalP, NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed),
              LocalVariableA(CodeVarNameA("num"), FinalP, NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed)),
            List(
              CheckRefCountAE(LocalLoadAE(CodeVarNameA("obj"), OwnP), MemberRefCount, LocalLoadAE(CodeVarNameA("num"), OwnP)),
              VoidAE())))))
}
