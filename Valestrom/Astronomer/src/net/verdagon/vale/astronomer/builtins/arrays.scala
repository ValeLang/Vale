package net.verdagon.vale.astronomer.builtins

import net.verdagon.vale.astronomer._
import net.verdagon.vale.parser.{BorrowP, CaptureP, FinalP, LendBorrowP, MutabilityP, MutableP, OwnP, ReadwriteP, UseP}
import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.scout.patterns.AtomSP

object Arrays {
  def makeArrayFunction(mutability: MutabilityP): FunctionA = {
    FunctionA(
      RangeS.internal(-57),
      FunctionNameA("Array", if (mutability == MutableP) { CodeLocationS.internal(-3) } else { CodeLocationS.internal(-4) }),
      List(),
      TemplateTemplataType(List(MutabilityTemplataType, CoordTemplataType), FunctionTemplataType),
      Set(CodeRuneA("I")),
      List(CodeRuneA("ArrayMutability"), CodeRuneA("T"), CodeRuneA("Generator")),
      Set(
        CodeRuneA("I"),
        CodeRuneA("ArrayMutability"),
        CodeRuneA("T"),
        CodeRuneA("P"),
        CodeRuneA("Generator"),
        CodeRuneA("M"),
        CodeRuneA("R")),
      Map(
        CodeRuneA("ArrayMutability") -> MutabilityTemplataType,
        CodeRuneA("I") -> CoordTemplataType,
        CodeRuneA("T") -> CoordTemplataType,
        CodeRuneA("P") -> PermissionTemplataType,
        CodeRuneA("Generator") -> CoordTemplataType,
        CodeRuneA("M") -> MutabilityTemplataType,
        CodeRuneA("R") -> CoordTemplataType),
      List(
        ParameterA(AtomAP(RangeS.internal(-121), LocalVariableA(CodeVarNameA("size"), FinalP, NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed), None, CodeRuneA("I"), None)),
        ParameterA(AtomAP(RangeS.internal(-122), LocalVariableA(CodeVarNameA("generator"), FinalP, NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed), None, CodeRuneA("Generator"), None))),
      Some(CodeRuneA("R")),
      List(
        EqualsAR(RangeS.internal(-1403),TemplexAR(RuneAT(RangeS.internal(-5603),CodeRuneA("ArrayMutability"), MutabilityTemplataType)), TemplexAR(MutabilityAT(RangeS.internal(-5604),mutability))),
        EqualsAR(RangeS.internal(-1404),TemplexAR(RuneAT(RangeS.internal(-5605),CodeRuneA("I"), CoordTemplataType)), TemplexAR(NameAT(RangeS.internal(-5606),CodeTypeNameA("int"), CoordTemplataType))),
        TemplexAR(RuneAT(RangeS.internal(-5607),CodeRuneA("T"), CoordTemplataType)),
        EqualsAR(
          RangeS.internal(-1405),
          TemplexAR(RuneAT(RangeS.internal(-5608),CodeRuneA("Generator"), CoordTemplataType)),
          ComponentsAR(
            RangeS.internal(-78),
            CoordTemplataType,
            List(
              TemplexAR(OwnershipAT(RangeS.internal(-5609),BorrowP)),
              TemplexAR(RuneAT(RangeS.internal(-5610),CodeRuneA("P"),PermissionTemplataType)),
              TemplexAR(
                CallAT(RangeS.internal(-5611),
                  NameAT(RangeS.internal(-5612),CodeTypeNameA("IFunction1"), TemplateTemplataType(List(MutabilityTemplataType, CoordTemplataType, CoordTemplataType), KindTemplataType)),
                  List(
                    RuneAT(RangeS.internal(-5613),CodeRuneA("M"), MutabilityTemplataType),
                    NameAT(RangeS.internal(-5614),CodeTypeNameA("int"), CoordTemplataType),
                    RuneAT(RangeS.internal(-5615),CodeRuneA("T"), CoordTemplataType)),
                  KindTemplataType))))),
        EqualsAR(
          RangeS.internal(-1406),
          TemplexAR(RuneAT(RangeS.internal(-5616),CodeRuneA("R"), CoordTemplataType)),
          TemplexAR(
            CallAT(RangeS.internal(-5617),
              NameAT(RangeS.internal(-5618),
                CodeTypeNameA("Array"),
                TemplateTemplataType(List(MutabilityTemplataType, CoordTemplataType), KindTemplataType)),
              List(RuneAT(RangeS.internal(-5619),CodeRuneA("ArrayMutability"), MutabilityTemplataType), RuneAT(RangeS.internal(-5620),CodeRuneA("T"), CoordTemplataType)),
              CoordTemplataType)))),
      CodeBodyA(
        BodyAE(
          RangeS.internal(-5621),
          List(),
          BlockAE(
            RangeS.internal(-5622),
            List(
              ConstructArrayAE(
                RangeS.internal(-5623),
                RuneAT(RangeS.internal(-5624),CodeRuneA("T"), CoordTemplataType),
                LocalLoadAE(RangeS.internal(-5625),CodeVarNameA("size"), UseP),
                LocalLoadAE(RangeS.internal(-5626),CodeVarNameA("generator"), LendBorrowP(Some(ReadwriteP))),
                mutability))))))
  }
}
