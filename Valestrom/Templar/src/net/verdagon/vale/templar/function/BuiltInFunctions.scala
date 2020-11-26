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
          ParameterA(AtomAP(RangeS.internal(-1337), LocalVariableA(CodeVarNameA("arr"), FinalP, NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed), None, CodeRuneA("T"), None))),
        Some(CodeRuneA("I")),
        List(
          EqualsAR(RangeS.internal(-91),
            TemplexAR(RuneAT(RangeS.internal(-91),CodeRuneA("T"), CoordTemplataType)),
            ComponentsAR(
              RangeS.internal(-91),
              CoordTemplataType,
              List(
                OrAR(RangeS.internal(-91),List(TemplexAR(OwnershipAT(RangeS.internal(-91),BorrowP)), TemplexAR(OwnershipAT(RangeS.internal(-91),ShareP)))),
                TemplexAR(
                  CallAT(RangeS.internal(-91),
                    NameAT(RangeS.internal(-91),CodeTypeNameA("Array"), TemplateTemplataType(List(MutabilityTemplataType, CoordTemplataType), KindTemplataType)),
                    List(
                      RuneAT(RangeS.internal(-91),CodeRuneA("XX"), MutabilityTemplataType),
                      RuneAT(RangeS.internal(-91),CodeRuneA("__1"), CoordTemplataType)),
                    KindTemplataType))))),
          EqualsAR(RangeS.internal(-91),
            TemplexAR(RuneAT(RangeS.internal(-91),CodeRuneA("I"), CoordTemplataType)),
            TemplexAR(NameAT(RangeS.internal(-91),CodeTypeNameA("int"), CoordTemplataType)))),
        CodeBodyA(
          BodyAE(
            RangeS.internal(-62),
            List(),
            BlockAE(
              RangeS.internal(-62),
              List(
                ArrayLengthAE(
                  RangeS.internal(-62),
                  LocalLoadAE(RangeS.internal(-62),CodeVarNameA("arr"), OwnP))))))),
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
          ParameterA(AtomAP(RangeS.internal(-1338), LocalVariableA(CodeVarNameA("arr"), FinalP, NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed), None, CodeRuneA("A"), None))),
        Some(CodeRuneA("I")),
        List(
          EqualsAR(RangeS.internal(-91),
            TemplexAR(RuneAT(RangeS.internal(-91),CodeRuneA("A"), CoordTemplataType)),
            ComponentsAR(
              RangeS.internal(-92),
              CoordTemplataType,
              List(
                TemplexAR(OwnershipAT(RangeS.internal(-91),BorrowP)),
                TemplexAR(
                  RepeaterSequenceAT(RangeS.internal(-91),
                    RuneAT(RangeS.internal(-91),CodeRuneA("M"), MutabilityTemplataType),
                    RuneAT(RangeS.internal(-91),CodeRuneA("N"), IntegerTemplataType),
                    RuneAT(RangeS.internal(-91),CodeRuneA("T"), CoordTemplataType),
                    KindTemplataType))))),
          EqualsAR(RangeS.internal(-91),
            TemplexAR(RuneAT(RangeS.internal(-91),CodeRuneA("I"), CoordTemplataType)),
            TemplexAR(NameAT(RangeS.internal(-91),CodeTypeNameA("int"), CoordTemplataType)))),
        CodeBodyA(
          BodyAE(
            RangeS.internal(-62),
            List(),
            BlockAE(
              RangeS.internal(-62),
              List(
                RuneLookupAE(RangeS.internal(-62),CodeRuneA("N"), IntegerTemplataType)))))),
      FunctionA(
        RangeS.internal(-67),
        FunctionNameA("__panic", s.CodeLocationS.internal(-22)),
        List(UserFunctionA),
        FunctionTemplataType,
        Set(),
        List(),
        Set(CodeRuneA("N")),
        Map(CodeRuneA("N") -> CoordTemplataType),
        List(),
        Some(CodeRuneA("N")),
        List(
          EqualsAR(RangeS.internal(-91),
            TemplexAR(RuneAT(RangeS.internal(-91),CodeRuneA("N"), CoordTemplataType)),
            TemplexAR(NameAT(RangeS.internal(-91),CodeTypeNameA("__Never"), CoordTemplataType)))),
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
        List(ParameterA(AtomAP(RangeS.internal(-1350), LocalVariableA(CodeVarNameA("weakRef"), FinalP, NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed), None, CodeRuneA("WeakRune"), None))),
        Some(CodeRuneA("OptBorrowRune")),
        List(
          EqualsAR(RangeS.internal(-91),
            TemplexAR(RuneAT(RangeS.internal(-91),CodeRuneA("OptBorrowRune"), CoordTemplataType)),
            TemplexAR(
              CallAT(RangeS.internal(-91),
                NameAT(RangeS.internal(-91),CodeTypeNameA("Opt"), TemplateTemplataType(List(CoordTemplataType), KindTemplataType)),
                List(OwnershippedAT(RangeS.internal(-91),BorrowP, RuneAT(RangeS.internal(-91),CodeRuneA("OwningRune"), CoordTemplataType))),
                CoordTemplataType))),
          EqualsAR(RangeS.internal(-91),
            TemplexAR(RuneAT(RangeS.internal(-91),CodeRuneA("WeakRune"), CoordTemplataType)),
            TemplexAR(
              OwnershippedAT(RangeS.internal(-91),WeakP, RuneAT(RangeS.internal(-91),CodeRuneA("OwningRune"), CoordTemplataType))))),
        CodeBodyA(
          BodyAE(
            RangeS.internal(-62),
            List(),
            BlockAE(
              RangeS.internal(-62),
              List(
                LockWeakAE(RangeS.internal(-91), LocalLoadAE(RangeS.internal(-62),CodeVarNameA("weakRef"), WeakP))))))))
}
