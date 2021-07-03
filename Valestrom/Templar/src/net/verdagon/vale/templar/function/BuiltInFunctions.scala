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
        Set(CodeRuneA("T"), CodeRuneA("XX"), CodeRuneA("XY"), CodeRuneA("__1"), CodeRuneA("I")),
        Map(
          CodeRuneA("T") -> CoordTemplataType,
          CodeRuneA("XX") -> MutabilityTemplataType,
          CodeRuneA("XY") -> VariabilityTemplataType,
          CodeRuneA("__1") -> CoordTemplataType,
          CodeRuneA("I") -> CoordTemplataType),
        List(
          ParameterA(AtomAP(RangeS.internal(-1337), LocalA(CodeVarNameA("arr"), NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed), None, CodeRuneA("T"), None))),
        Some(CodeRuneA("I")),
        List(
          EqualsAR(RangeS.internal(-9101),
            TemplexAR(RuneAT(RangeS.internal(-9102),CodeRuneA("T"), CoordTemplataType)),
            ComponentsAR(
              RangeS.internal(-9103),
              CoordTemplataType,
              List(
                OrAR(RangeS.internal(-9104),List(TemplexAR(OwnershipAT(RangeS.internal(-9105),ConstraintP)), TemplexAR(OwnershipAT(RangeS.internal(-9106),ShareP)))),
                TemplexAR(PermissionAT(RangeS.internal(-9107), ReadonlyP)),
                TemplexAR(
                  CallAT(RangeS.internal(-9108),
                    NameAT(RangeS.internal(-9109),CodeTypeNameA("Array"), TemplateTemplataType(List(MutabilityTemplataType, VariabilityTemplataType, CoordTemplataType), KindTemplataType)),
                    List(
                      RuneAT(RangeS.internal(-9110),CodeRuneA("XX"), MutabilityTemplataType),
                      RuneAT(RangeS.internal(-9110),CodeRuneA("XY"), VariabilityTemplataType),
                      RuneAT(RangeS.internal(-9111),CodeRuneA("__1"), CoordTemplataType)),
                    KindTemplataType))))),
          EqualsAR(RangeS.internal(-9112),
            TemplexAR(RuneAT(RangeS.internal(-9113),CodeRuneA("I"), CoordTemplataType)),
            TemplexAR(NameAT(RangeS.internal(-9114),CodeTypeNameA("int"), CoordTemplataType)))),
        CodeBodyA(
          BodyAE(
            RangeS.internal(-62),
            List(),
            BlockAE(
              RangeS.internal(-62),
              List(
                ArrayLengthAE(
                  RangeS.internal(-62),
                  LocalLoadAE(RangeS.internal(-62),CodeVarNameA("arr"), UseP))))))),
      FunctionA(
        RangeS.internal(-62),
        FunctionNameA("len", s.CodeLocationS.internal(-21)),
        List(UserFunctionA),
        TemplateTemplataType(List(CoordTemplataType), FunctionTemplataType),
        Set(CodeRuneA("I")),
        List(CodeRuneA("N"), CodeRuneA("T")),
        Set(CodeRuneA("A"), CodeRuneA("N"), CodeRuneA("M"), CodeRuneA("V"), CodeRuneA("T"), CodeRuneA("I")),
        Map(
          CodeRuneA("A") -> CoordTemplataType,
          CodeRuneA("N") -> IntegerTemplataType,
          CodeRuneA("T") -> CoordTemplataType,
          CodeRuneA("I") -> CoordTemplataType,
          CodeRuneA("M") -> MutabilityTemplataType,
          CodeRuneA("V") -> VariabilityTemplataType),
        List(
          ParameterA(AtomAP(RangeS.internal(-1338), LocalA(CodeVarNameA("arr"), NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed), None, CodeRuneA("A"), None))),
        Some(CodeRuneA("I")),
        List(
          EqualsAR(RangeS.internal(-9115),
            TemplexAR(RuneAT(RangeS.internal(-9116),CodeRuneA("A"), CoordTemplataType)),
            ComponentsAR(
              RangeS.internal(-92),
              CoordTemplataType,
              List(
                TemplexAR(OwnershipAT(RangeS.internal(-9117),ConstraintP)),
                TemplexAR(PermissionAT(RangeS.internal(-9117),ReadonlyP)),
                TemplexAR(
                  RepeaterSequenceAT(RangeS.internal(-9118),
                    RuneAT(RangeS.internal(-9119),CodeRuneA("M"), MutabilityTemplataType),
                    RuneAT(RangeS.internal(-9119),CodeRuneA("V"), VariabilityTemplataType),
                    RuneAT(RangeS.internal(-9120),CodeRuneA("N"), IntegerTemplataType),
                    RuneAT(RangeS.internal(-9121),CodeRuneA("T"), CoordTemplataType),
                    KindTemplataType))))),
          EqualsAR(RangeS.internal(-9122),
            TemplexAR(RuneAT(RangeS.internal(-9123),CodeRuneA("I"), CoordTemplataType)),
            TemplexAR(NameAT(RangeS.internal(-9124),CodeTypeNameA("int"), CoordTemplataType)))),
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
          EqualsAR(RangeS.internal(-9125),
            TemplexAR(RuneAT(RangeS.internal(-9126),CodeRuneA("N"), CoordTemplataType)),
            TemplexAR(NameAT(RangeS.internal(-9127),CodeTypeNameA("__Never"), CoordTemplataType)))),
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
        List(ParameterA(AtomAP(RangeS.internal(-1350), LocalA(CodeVarNameA("weakRef"), NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed), None, CodeRuneA("WeakRune"), None))),
        Some(CodeRuneA("OptBorrowRune")),
        List(
          EqualsAR(RangeS.internal(-9128),
            TemplexAR(RuneAT(RangeS.internal(-9129),CodeRuneA("OptBorrowRune"), CoordTemplataType)),
            TemplexAR(
              CallAT(RangeS.internal(-9130),
                NameAT(RangeS.internal(-9131),CodeTypeNameA("Opt"), TemplateTemplataType(List(CoordTemplataType), KindTemplataType)),
                List(InterpretedAT(RangeS.internal(-9132),ConstraintP,ReadonlyP, RuneAT(RangeS.internal(-9133),CodeRuneA("OwningRune"), CoordTemplataType))),
                CoordTemplataType))),
          EqualsAR(RangeS.internal(-9134),
            TemplexAR(RuneAT(RangeS.internal(-9135),CodeRuneA("WeakRune"), CoordTemplataType)),
            TemplexAR(
              InterpretedAT(RangeS.internal(-9136),WeakP,ReadonlyP, RuneAT(RangeS.internal(-9137),CodeRuneA("OwningRune"), CoordTemplataType))))),
        CodeBodyA(
          BodyAE(
            RangeS.internal(-62),
            List(),
            BlockAE(
              RangeS.internal(-62),
              List(
                LockWeakAE(RangeS.internal(-9138), LocalLoadAE(RangeS.internal(-62),CodeVarNameA("weakRef"), LendWeakP(ReadonlyP)))))))))
}
