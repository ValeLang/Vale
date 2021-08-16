package net.verdagon.vale.astronomer.builtins

import net.verdagon.vale.astronomer._
import net.verdagon.vale.parser.{CaptureP, ConstraintP, FinalP, LendConstraintP, MutabilityP, MutableP, OwnP, ReadonlyP, ReadwriteP, UseP}
import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.scout.patterns.AtomSP

object Arrays {
  def makeArrayFunctions(): Vector[FunctionA] = {
    Vector(
      FunctionA(
        RangeS.internal(-54),
        FunctionNameA("drop_into", CodeLocationS.internal(-4)),
        Vector.empty,
        TemplateTemplataType(Vector(MutabilityTemplataType, VariabilityTemplataType, CoordTemplataType), FunctionTemplataType),
        Set(CodeRuneA("VoidType")),
        Vector(CodeRuneA("ArrayMutability"), CodeRuneA("ArrayVariability"), CodeRuneA("ElementType"), CodeRuneA("GeneratorType")),
        Set(
          CodeRuneA("VoidType"),
          CodeRuneA("ArrayMutability"),
          CodeRuneA("ArrayVariability"),
          CodeRuneA("ArraySize"),
          CodeRuneA("ElementType"),
          CodeRuneA("ArrayType"),
          CodeRuneA("GeneratorType"),
          CodeRuneA("GeneratorKind")),
        Map(
          CodeRuneA("VoidType") -> CoordTemplataType,
          CodeRuneA("ArrayMutability") -> MutabilityTemplataType,
          CodeRuneA("ArrayVariability") -> VariabilityTemplataType,
          CodeRuneA("ArraySize") -> IntegerTemplataType,
          CodeRuneA("ElementType") -> CoordTemplataType,
          CodeRuneA("ArrayType") -> CoordTemplataType,
          CodeRuneA("GeneratorType") -> CoordTemplataType,
          CodeRuneA("GeneratorKind") -> KindTemplataType),
        Vector(
          ParameterA(AtomAP(RangeS.internal(-121), Some(LocalA(CodeVarNameA("arr"), NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed)), None, CodeRuneA("ArrayType"), None)),
          ParameterA(AtomAP(RangeS.internal(-122), Some(LocalA(CodeVarNameA("generator"), NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed)), None, CodeRuneA("GeneratorType"), None))),
        Some(CodeRuneA("VoidType")),
        Vector(
          EqualsAR(
            RangeS.internal(-1404),
            TemplexAR(RuneAT(RangeS.internal(-5605),CodeRuneA("VoidType"), CoordTemplataType)),
            TemplexAR(NameAT(RangeS.internal(-5606),CodeTypeNameA("void"), CoordTemplataType))),
          EqualsAR(
            RangeS.internal(-1406),
            TemplexAR(RuneAT(RangeS.internal(-5616),CodeRuneA("GeneratorType"), CoordTemplataType)),
            ComponentsAR(
              RangeS.internal(-1406),
              CoordTemplataType,
              Vector(
                TemplexAR(OwnershipAT(RangeS.internal(-5617), ConstraintP)),
                TemplexAR(PermissionAT(RangeS.internal(-5617), ReadwriteP)),
                TemplexAR(RuneAT(RangeS.internal(-5618),CodeRuneA("GeneratorKind"), KindTemplataType))))),
          EqualsAR(
            RangeS.internal(-1406),
            TemplexAR(RuneAT(RangeS.internal(-5616),CodeRuneA("ArrayType"), CoordTemplataType)),
            TemplexAR(
              RepeaterSequenceAT(
                RangeS.internal(-5617),
                RuneAT(RangeS.internal(-5619),CodeRuneA("ArrayMutability"), MutabilityTemplataType),
                RuneAT(RangeS.internal(-5619),CodeRuneA("ArrayVariability"), VariabilityTemplataType),
                RuneAT(RangeS.internal(-5619),CodeRuneA("ArraySize"), IntegerTemplataType),
                RuneAT(RangeS.internal(-5620),CodeRuneA("ElementType"), CoordTemplataType),
                CoordTemplataType)))),
        CodeBodyA(
          BodyAE(
            RangeS.internal(-5621),
            Vector.empty,
            BlockAE(
              RangeS.internal(-5622),
              Vector(
                DestroyArrayIntoCallableAE(
                  RangeS.internal(-5623),
                  LocalLoadAE(RangeS.internal(-5625),CodeVarNameA("arr"), UseP),
                  LocalLoadAE(RangeS.internal(-5626),CodeVarNameA("generator"), LendConstraintP(Some(ReadwriteP))))))))),

      FunctionA(
        RangeS.internal(-57),
        FunctionNameA("drop_into", CodeLocationS.internal(-4)),
        Vector.empty,
        TemplateTemplataType(Vector(MutabilityTemplataType, VariabilityTemplataType, CoordTemplataType), FunctionTemplataType),
        Set(CodeRuneA("VoidType")),
        Vector(CodeRuneA("ArrayMutability"), CodeRuneA("ArrayVariability"), CodeRuneA("ElementType"), CodeRuneA("GeneratorType")),
        Set(
          CodeRuneA("VoidType"),
          CodeRuneA("ArrayMutability"),
          CodeRuneA("ArrayVariability"),
          CodeRuneA("ElementType"),
          CodeRuneA("ArrayType"),
          CodeRuneA("GeneratorType"),
          CodeRuneA("GeneratorKind")),
        Map(
          CodeRuneA("VoidType") -> CoordTemplataType,
          CodeRuneA("ArrayMutability") -> MutabilityTemplataType,
          CodeRuneA("ArrayVariability") -> VariabilityTemplataType,
          CodeRuneA("ElementType") -> CoordTemplataType,
          CodeRuneA("ArrayType") -> CoordTemplataType,
          CodeRuneA("GeneratorType") -> CoordTemplataType,
          CodeRuneA("GeneratorKind") -> KindTemplataType),
        Vector(
          ParameterA(AtomAP(RangeS.internal(-121), Some(LocalA(CodeVarNameA("arr"), NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed)), None, CodeRuneA("ArrayType"), None)),
          ParameterA(AtomAP(RangeS.internal(-122), Some(LocalA(CodeVarNameA("generator"), NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed)), None, CodeRuneA("GeneratorType"), None))),
        Some(CodeRuneA("VoidType")),
        Vector(
          EqualsAR(
            RangeS.internal(-1404),
            TemplexAR(RuneAT(RangeS.internal(-5605),CodeRuneA("VoidType"), CoordTemplataType)),
            TemplexAR(NameAT(RangeS.internal(-5606),CodeTypeNameA("void"), CoordTemplataType))),
          EqualsAR(
            RangeS.internal(-1406),
            TemplexAR(RuneAT(RangeS.internal(-5616),CodeRuneA("GeneratorType"), CoordTemplataType)),
            ComponentsAR(
              RangeS.internal(-1406),
              CoordTemplataType,
              Vector(
                TemplexAR(OwnershipAT(RangeS.internal(-5617), ConstraintP)),
                TemplexAR(PermissionAT(RangeS.internal(-5617), ReadwriteP)),
                TemplexAR(RuneAT(RangeS.internal(-5618),CodeRuneA("GeneratorKind"), KindTemplataType))))),
          EqualsAR(RangeS.internal(-9101),
            TemplexAR(RuneAT(RangeS.internal(-9102),CodeRuneA("ArrayType"), CoordTemplataType)),
            ComponentsAR(
              RangeS.internal(-9103),
              CoordTemplataType,
              Vector(
                TemplexAR(OwnershipAT(RangeS.internal(-5617), OwnP)),
                TemplexAR(PermissionAT(RangeS.internal(-5617), ReadwriteP)),
                TemplexAR(
                  CallAT(RangeS.internal(-9108),
                    NameAT(RangeS.internal(-9109),CodeTypeNameA("Array"), TemplateTemplataType(Vector(MutabilityTemplataType, VariabilityTemplataType, CoordTemplataType), KindTemplataType)),
                    Vector(
                      RuneAT(RangeS.internal(-9110),CodeRuneA("ArrayMutability"), MutabilityTemplataType),
                      RuneAT(RangeS.internal(-9110),CodeRuneA("ArrayVariability"), VariabilityTemplataType),
                      RuneAT(RangeS.internal(-9111),CodeRuneA("ElementType"), CoordTemplataType)),
                    KindTemplataType)))))),
        CodeBodyA(
          BodyAE(
            RangeS.internal(-5621),
            Vector.empty,
            BlockAE(
              RangeS.internal(-5622),
              Vector(
                DestroyArrayIntoCallableAE(
                  RangeS.internal(-5623),
                  LocalLoadAE(RangeS.internal(-5625),CodeVarNameA("arr"), UseP),
                  LocalLoadAE(RangeS.internal(-5626),CodeVarNameA("generator"), LendConstraintP(Some(ReadwriteP))))))))))
  }
}
