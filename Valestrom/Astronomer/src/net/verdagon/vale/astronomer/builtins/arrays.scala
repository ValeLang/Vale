package net.verdagon.vale.astronomer.builtins

import net.verdagon.vale.astronomer._
import net.verdagon.vale.parser.{ConstraintP, CaptureP, FinalP, LendConstraintP, MutabilityP, MutableP, OwnP, ReadwriteP, UseP}
import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.scout.patterns.AtomSP

object Arrays {
//  def makeArrayFunction(): FunctionA = {
//    FunctionA(
//      RangeS.internal(-57),
//      FunctionNameA("__Array", CodeLocationS.internal(-4)),
//      List.empty,
//      TemplateTemplataType(List(MutabilityTemplataType, CoordTemplataType, CoordTemplataType, PrototypeTemplataType), FunctionTemplataType),
//      Set(CodeRuneA("IntType")),
//      List(CodeRuneA("ArrayMutability"), CodeRuneA("ArrayMutability"), CodeRuneA("ElementType"), CodeRuneA("GeneratorType"), CodeRuneA("GeneratorPrototype")),
//      Set(
//        CodeRuneA("IntType"),
//        CodeRuneA("ArrayMutability"),
//        CodeRuneA("ArrayVariability"),
//        CodeRuneA("ElementType"),
//        CodeRuneA("GeneratorType"),
//        CodeRuneA("GeneratorPrototype"),
//        CodeRuneA("ResultArrayType")),
//      Map(
//        CodeRuneA("ArrayMutability") -> MutabilityTemplataType,
//        CodeRuneA("ArrayVariability") -> MutabilityTemplataType,
//        CodeRuneA("IntType") -> CoordTemplataType,
//        CodeRuneA("ElementType") -> CoordTemplataType,
//        CodeRuneA("GeneratorType") -> CoordTemplataType,
//        CodeRuneA("GeneratorPrototype") -> PrototypeTemplataType,
//        CodeRuneA("ResultArrayType") -> CoordTemplataType),
//      List(
//        ParameterA(AtomAP(RangeS.internal(-121), LocalVariableA(CodeVarNameA("size"), FinalP, NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed), None, CodeRuneA("IntType"), None)),
//        ParameterA(AtomAP(RangeS.internal(-122), LocalVariableA(CodeVarNameA("generator"), FinalP, NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed), None, CodeRuneA("GeneratorType"), None))),
//      Some(CodeRuneA("ResultArrayType")),
//      List(
//        EqualsAR(
//          RangeS.internal(-1404),
//          TemplexAR(RuneAT(RangeS.internal(-5605),CodeRuneA("IntType"), CoordTemplataType)),
//          TemplexAR(NameAT(RangeS.internal(-5606),CodeTypeNameA("int"), CoordTemplataType))),
//        TemplexAR(RuneAT(RangeS.internal(-5607),CodeRuneA("ElementType"), CoordTemplataType)),
//        EqualsAR(
//          RangeS.internal(-1406),
//          TemplexAR(RuneAT(RangeS.internal(-5616),CodeRuneA("ResultArrayType"), CoordTemplataType)),
//          TemplexAR(
//            CallAT(RangeS.internal(-5617),
//              NameAT(RangeS.internal(-5618),
//                CodeTypeNameA("Array"),
//                TemplateTemplataType(List(MutabilityTemplataType, CoordTemplataType), KindTemplataType)),
//              List(
//                RuneAT(RangeS.internal(-5619),CodeRuneA("ArrayMutability"), MutabilityTemplataType),
//                RuneAT(RangeS.internal(-5619),CodeRuneA("ArrayVariability"), VariabilityTemplataType),
//                RuneAT(RangeS.internal(-5620),CodeRuneA("ElementType"), CoordTemplataType)),
//              CoordTemplataType)))),
//      CodeBodyA(
//        BodyAE(
//          RangeS.internal(-5621),
//          List.empty,
//          BlockAE(
//            RangeS.internal(-5622),
//            List(
//              ConstructArrayAE(
//                RangeS.internal(-5623),
//                RuneAT(RangeS.internal(-5624),CodeRuneA("ArrayMutability"), MutabilityTemplataType),
//                RuneAT(RangeS.internal(-5624),CodeRuneA("ArrayVariability"), MutabilityTemplataType),
//                RuneAT(RangeS.internal(-5624),CodeRuneA("ElementType"), CoordTemplataType),
//                RuneAT(RangeS.internal(-5624),CodeRuneA("GeneratorPrototype"), PrototypeTemplataType),
//                LocalLoadAE(RangeS.internal(-5625),CodeVarNameA("size"), UseP),
//                LocalLoadAE(RangeS.internal(-5626),CodeVarNameA("generator"), LendConstraintP(Some(ReadwriteP)))))))))
//  }
}
