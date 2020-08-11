package net.verdagon.vale.templar.function

import net.verdagon.vale.astronomer._
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.parser._
import net.verdagon.vale.{scout => s}
import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.scout.patterns.{AbstractSP, AtomSP, CaptureS, OverrideSP}
import net.verdagon.vale.scout.rules._
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.env.{FunctionEnvironment, _}
import net.verdagon.vale.{vassert, vfail}

import scala.collection.immutable.List

object BuiltInFunctions {
  val builtIns =
    List(
      FunctionA(
        RangeS.internal(-61),
        FunctionNameA("len", s.CodeLocationS.internal(-20)),
        List(UserFunctionA),
        TemplateTemplataType(List(CoordTemplataType), FunctionTemplataType),
        Set(CodeRuneA("I")),
        List(CodeRuneA("T")),
        Set(CodeRuneA("T"), CodeRuneA("XX"), CodeRuneA("__1"), CodeRuneA("I")),
        Map(
          CodeRuneA("T") -> CoordTemplataType,
          CodeRuneA("XX") -> MutabilityTemplataType,
          CodeRuneA("__1") -> CoordTemplataType,
          CodeRuneA("I") -> CoordTemplataType),
        List(
          ParameterA(AtomAP(CaptureA(CodeVarNameA("arr"), FinalP), None, CodeRuneA("T"), None))),
        Some(CodeRuneA("I")),
        List(
          EqualsAR(
            TemplexAR(RuneAT(CodeRuneA("T"), CoordTemplataType)),
            ComponentsAR(
              CoordTemplataType,
              List(
                OrAR(List(TemplexAR(OwnershipAT(BorrowP)), TemplexAR(OwnershipAT(ShareP)))),
                TemplexAR(
                  CallAT(
                    NameAT(CodeTypeNameA("Array"), TemplateTemplataType(List(MutabilityTemplataType, CoordTemplataType), KindTemplataType)),
                    List(
                      RuneAT(CodeRuneA("XX"), MutabilityTemplataType),
                      RuneAT(CodeRuneA("__1"), CoordTemplataType)),
                    KindTemplataType))))),
          EqualsAR(
            TemplexAR(RuneAT(CodeRuneA("I"), CoordTemplataType)),
            TemplexAR(NameAT(CodeTypeNameA("int"), CoordTemplataType)))),
        CodeBodyA(
          BodyAE(
            List(),
            BlockAE(
              List(LocalVariableA(CodeVarNameA("arr"), FinalP, NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed)),
              List(
                ArrayLengthAE(
                  LocalLoadAE(CodeVarNameA("arr"), OwnP))))))),
      FunctionA(
        RangeS.internal(-62),
        FunctionNameA("len", s.CodeLocationS.internal(-21)),
        List(UserFunctionA),
        TemplateTemplataType(List(CoordTemplataType), FunctionTemplataType),
        Set(CodeRuneA("I")),
        List(CodeRuneA("N"), CodeRuneA("T")),
        Set(CodeRuneA("A"), CodeRuneA("N"), CodeRuneA("M"), CodeRuneA("T"), CodeRuneA("I")),
        Map(
          CodeRuneA("A") -> CoordTemplataType,
          CodeRuneA("N") -> IntegerTemplataType,
          CodeRuneA("T") -> CoordTemplataType,
          CodeRuneA("I") -> CoordTemplataType,
          CodeRuneA("M") -> MutabilityTemplataType),
        List(
          ParameterA(AtomAP(CaptureA(CodeVarNameA("arr"), FinalP), None, CodeRuneA("A"), None))),
        Some(CodeRuneA("I")),
        List(
          EqualsAR(
            TemplexAR(RuneAT(CodeRuneA("A"), CoordTemplataType)),
            ComponentsAR(
              CoordTemplataType,
              List(
                TemplexAR(OwnershipAT(BorrowP)),
                TemplexAR(
                  RepeaterSequenceAT(
                    RuneAT(CodeRuneA("M"), MutabilityTemplataType),
                    RuneAT(CodeRuneA("N"), IntegerTemplataType),
                    RuneAT(CodeRuneA("T"), CoordTemplataType),
                    KindTemplataType))))),
          EqualsAR(
            TemplexAR(RuneAT(CodeRuneA("I"), CoordTemplataType)),
            TemplexAR(NameAT(CodeTypeNameA("int"), CoordTemplataType)))),
        CodeBodyA(
          BodyAE(
            List(),
            BlockAE(
              List(LocalVariableA(CodeVarNameA("arr"), FinalP, NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed)),
              List(
                RuneLookupAE(CodeRuneA("N"), IntegerTemplataType)))))),
      FunctionA(
        RangeS.internal(-67),
        FunctionNameA("panic", s.CodeLocationS.internal(-22)),
        List(UserFunctionA),
        FunctionTemplataType,
        Set(),
        List(),
        Set(CodeRuneA("N")),
        Map(CodeRuneA("N") -> CoordTemplataType),
        List(),
        Some(CodeRuneA("N")),
        List(
          EqualsAR(
            TemplexAR(RuneAT(CodeRuneA("N"), CoordTemplataType)),
            TemplexAR(NameAT(CodeTypeNameA("__Never"), CoordTemplataType)))),
        ExternBodyA),
      FunctionA(
        RangeS.internal(-63),
        FunctionNameA("lock", s.CodeLocationS.internal(-23)),
        List(UserFunctionA),
        TemplateTemplataType(List(CoordTemplataType), FunctionTemplataType),
        Set(),
        List(CodeRuneA("OwningRune")),
        Set(CodeRuneA("OwningRune"), CodeRuneA("OptBorrowRune"), CodeRuneA("WeakRune")),
        Map(CodeRuneA("OwningRune") -> CoordTemplataType, CodeRuneA("OptBorrowRune") -> CoordTemplataType, CodeRuneA("WeakRune") -> CoordTemplataType),
        List(ParameterA(AtomAP(CaptureA(CodeVarNameA("weakRef"), FinalP), None, CodeRuneA("WeakRune"), None))),
        Some(CodeRuneA("OptBorrowRune")),
        List(
          EqualsAR(
            TemplexAR(RuneAT(CodeRuneA("OptBorrowRune"), CoordTemplataType)),
            TemplexAR(
              CallAT(
                NameAT(CodeTypeNameA("Opt"), TemplateTemplataType(List(CoordTemplataType), KindTemplataType)),
                List(OwnershippedAT(BorrowP, RuneAT(CodeRuneA("OwningRune"), CoordTemplataType))),
                CoordTemplataType))),
          EqualsAR(
            TemplexAR(RuneAT(CodeRuneA("WeakRune"), CoordTemplataType)),
            TemplexAR(
              OwnershippedAT(WeakP, RuneAT(CodeRuneA("OwningRune"), CoordTemplataType))))),
        CodeBodyA(
          BodyAE(
            List(),
            BlockAE(
              List(LocalVariableA(CodeVarNameA("weakRef"), FinalP, NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed)),
              List(
                LockWeakAE(LocalLoadAE(CodeVarNameA("weakRef"), WeakP))))))))
}
