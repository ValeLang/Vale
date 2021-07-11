package net.verdagon.vale.scout

import net.verdagon.vale.parser._
import net.verdagon.vale.scout.{IEnvironment => _, FunctionEnvironment => _, Environment => _, _}
import net.verdagon.vale.scout.patterns.{AbstractSP, AtomSP}
import net.verdagon.vale.scout.predictor.Conclusions
import net.verdagon.vale.scout.rules.{EqualsSR, _}
import net.verdagon.vale.scout.templatepredictor.PredictorEvaluator
import net.verdagon.vale.{vassert, vassertSome, vfail, vimpl}
import org.scalatest.{FunSuite, Matchers}

import scala.collection.immutable.List

class RunePredictorTests extends FunSuite with Matchers {
  test("Predict doesnt crash for simple templex") {
    val conclusions =
      PredictorEvaluator.solve(Set(), List(TemplexSR(NameST(RangeS.testZero, CodeTypeNameS("int")))), List.empty)
    conclusions shouldEqual Conclusions(Set(), Map())
  }

  test("Can know rune from simple equals") {
    val conclusions =
      PredictorEvaluator.solve(
        Set(),
        List(
          EqualsSR(RangeS.testZero,TemplexSR(RuneST(RangeS.testZero,CodeRuneS("T"))), TemplexSR(NameST(RangeS.testZero, CodeTypeNameS("int"))))),
        List.empty)
    conclusions shouldEqual Conclusions(Set(CodeRuneS("T")), Map())
  }

  test("Predict for simple equals 2") {
    val conclusions =
      PredictorEvaluator.solve(
        Set(),
        List(
          TypedSR(RangeS.testZero,CodeRuneS("Z"),CoordTypeSR),
          EqualsSR(RangeS.testZero,
            TemplexSR(RuneST(RangeS.testZero,CodeRuneS("Z"))),
            TemplexSR(CallST(RangeS.testZero,NameST(RangeS.testZero, CodeTypeNameS("MyOption")),List(InterpretedST(RangeS.testZero,ShareP,ReadonlyP, NameST(RangeS.testZero, CodeTypeNameS("int")))))))),
        List.empty)
    conclusions shouldEqual Conclusions(Set(CodeRuneS("Z")), Map(CodeRuneS("Z") -> CoordTypeSR))
  }

  test("Predict doesn't know value from Or rule") {
    val tRune = CodeRuneS("T")
    val aRune = CodeRuneS("A")
    val bRune = CodeRuneS("B")
    val conclusions =
      PredictorEvaluator.solve(
        Set(),
        List(
          ComponentsSR(
            RangeS.internal(-81),
            TypedSR(RangeS.testZero,tRune,CoordTypeSR),
            List(
              OrSR(RangeS.testZero,List(TemplexSR(OwnershipST(RangeS.testZero,OwnP)), TemplexSR(OwnershipST(RangeS.testZero,ShareP)))),
              // Not exactly valid but itll do for this test
              OrSR(RangeS.testZero,List(TypedSR(RangeS.testZero,aRune,KindTypeSR), TypedSR(RangeS.testZero,bRune,CoordTypeSR)))))),
        List.empty)
    conclusions shouldEqual
      Conclusions(Set(), Map(tRune -> CoordTypeSR, aRune -> KindTypeSR, bRune -> CoordTypeSR))
  }

  test("Predict doesnt know T from components with anonymous kind") {
    // Tests that we can make a rule that will only match structs, arrays, packs, sequences.
    // It doesn't have to be in this form, but we do need the capability in some way, so that
    // we can have a templated destructor that matches any of those.
    val conclusions =
    PredictorEvaluator.solve(
      Set(),
      List(
        ComponentsSR(
          RangeS.internal(-82),
          TypedSR(RangeS.testZero,CodeRuneS("T"),CoordTypeSR),
          List(
            OrSR(RangeS.testZero,List(TemplexSR(OwnershipST(RangeS.testZero,OwnP)), TemplexSR(OwnershipST(RangeS.testZero,ShareP)))),
            CallSR(RangeS.testZero,"passThroughIfConcrete",List(TemplexSR(RuneST(RangeS.testZero,CodeRuneS("Z"))))))),
        EqualsSR(RangeS.testZero,TypedSR(RangeS.testZero,CodeRuneS("V"),CoordTypeSR),CallSR(RangeS.testZero,"toRef",List(TemplexSR(NameST(RangeS.testZero, CodeTypeNameS("void"))))))),
      List.empty)
    conclusions shouldEqual
      Conclusions(
        Set(CodeRuneS("V")),
        Map(
          CodeRuneS("T") -> CoordTypeSR,
          CodeRuneS("V") -> CoordTypeSR))
  }

  test("Predict returns true for array sequence") {
    // Tests that we can make a rule that will only match structs, arrays, packs, sequences.
    // It doesn't have to be in this form, but we do need the capability in some way, so that
    // we can have a templated destructor that matches any of those.
    val conclusions =
    PredictorEvaluator.solve(
      Set(),
      List(
        TypedSR(RangeS.testZero,CodeRuneS("Z"),CoordTypeSR),
        EqualsSR(RangeS.testZero,
          TemplexSR(RuneST(RangeS.testZero,CodeRuneS("Z"))),
          TemplexSR(RepeaterSequenceST(RangeS.testZero,MutabilityST(RangeS.testZero,MutableP), VariabilityST(RangeS.testZero,VaryingP), IntST(RangeS.testZero,5),InterpretedST(RangeS.testZero,ShareP,ReadonlyP,NameST(RangeS.testZero, CodeTypeNameS("int"))))))),
      List.empty)
    conclusions shouldEqual Conclusions(Set(CodeRuneS("Z")), Map(CodeRuneS("Z") -> CoordTypeSR))
  }

  test("Predict for idestructor for interface") {
    // Tests that we can make a rule that will only match structs, arrays, packs, sequences.
    // It doesn't have to be in this form, but we do need the capability in some way, so that
    // we can have a templated destructor that matches any of those.
    val conclusions =
    PredictorEvaluator.solve(
      Set(),
      List(
        ComponentsSR(
          RangeS.internal(-83),
          TypedSR(RangeS.testZero,CodeRuneS("T"),CoordTypeSR),
          List(
            OrSR(RangeS.testZero,List(TemplexSR(OwnershipST(RangeS.testZero,OwnP)), TemplexSR(OwnershipST(RangeS.testZero,ShareP)))),
            CallSR(RangeS.testZero,"passThroughIfInterface",List(TemplexSR(RuneST(RangeS.testZero,CodeRuneS("Z"))))))),
        EqualsSR(RangeS.testZero,TypedSR(RangeS.testZero,CodeRuneS("V"),CoordTypeSR),CallSR(RangeS.testZero,"toRef",List(TemplexSR(NameST(RangeS.testZero, CodeTypeNameS("void"))))))),
      List.empty)
    conclusions shouldEqual
      Conclusions(
        Set(CodeRuneS("V")),
        Map(
          CodeRuneS("T") -> CoordTypeSR,
          CodeRuneS("V") -> CoordTypeSR))

  }

  test("Predict for idestructor for struct") {
    // Tests that we can make a rule that will only match structs, arrays, packs, sequences.
    // It doesn't have to be in this form, but we do need the capability in some way, so that
    // we can have a templated destructor that matches any of those.
    val conclusions =
    PredictorEvaluator.solve(
      Set(),
      List(
        ComponentsSR(
          RangeS.internal(-84),
          TypedSR(RangeS.testZero,CodeRuneS("T"),CoordTypeSR),
          List(
            OrSR(RangeS.testZero,List(TemplexSR(OwnershipST(RangeS.testZero,OwnP)), TemplexSR(OwnershipST(RangeS.testZero,ShareP)))),
            CallSR(RangeS.testZero,"passThroughIfStruct",List(TemplexSR(RuneST(RangeS.testZero,CodeRuneS("Z"))))))),
        CallSR(RangeS.testZero,"passThroughIfInterface",List(TemplexSR(RuneST(RangeS.testZero,CodeRuneS("I")))))),
      List.empty)
    conclusions shouldEqual Conclusions(Set(), Map(CodeRuneS("T") -> CoordTypeSR))
  }

  // See MKKRFA.
  test("Predict runes from above") {
    val conclusions =
      PredictorEvaluator.solve(
        Set(CodeRuneS("P1"), CodeRuneS("R")),
        List(
          EqualsSR(RangeS.testZero,
            TypedSR(RangeS.testZero,CodeRuneS("Z"),CoordTypeSR),
            TemplexSR(
              InterpretedST(RangeS.testZero,
                ConstraintP,
                ReadonlyP,
                CallST(RangeS.testZero,
                  NameST(RangeS.testZero, CodeTypeNameS("MyIFunction1")),
                  List(
                    RuneST(RangeS.testZero,CodeRuneS("P1")),
                    RuneST(RangeS.testZero,CodeRuneS("R"))))))),
          TypedSR(RangeS.testZero,CodeRuneS("P1"),CoordTypeSR),
          TypedSR(RangeS.testZero,CodeRuneS("R"),CoordTypeSR)),
        List.empty)
    vassert(conclusions.knowableValueRunes.contains(CodeRuneS("P1")))
    vassert(conclusions.knowableValueRunes.contains(CodeRuneS("R")))
    vassert(conclusions.knowableValueRunes.contains(CodeRuneS("Z")))
  }
}
