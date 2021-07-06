package net.verdagon.vale.astronomer

import net.verdagon.vale.astronomer._
import net.verdagon.vale.astronomer.ruletyper.{IRuleTyperEvaluatorDelegate, RuleTyperEvaluator, RuleTyperSolveFailure, RuleTyperSolveSuccess}
import net.verdagon.vale.parser._
import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.scout.patterns.{AbstractSP, AtomSP, CaptureS}
import net.verdagon.vale.scout.rules.{EqualsSR, _}
import net.verdagon.vale._
import org.scalatest.{FunSuite, Matchers}

import scala.collection.immutable.List

case class FakeEnv()
case class FakeState()

case class SimpleEnvironment(entries: Map[String, List[ITemplataType]]) {
  def lookupType(name: String): ITemplataType = {
    val List(thing) = entries(name)
    thing
  }
}

class FakeRuleTyperEvaluatorDelegate extends IRuleTyperEvaluatorDelegate[SimpleEnvironment, FakeState] {
  override def lookupType(state: FakeState, env: SimpleEnvironment, rangeS: RangeS, absoluteName: INameS): ITemplataType = {
    absoluteName match {
      case TopLevelCitizenDeclarationNameS(name, _) => env.lookupType(name)
    }
  }
  override def lookupType(state: FakeState, env: SimpleEnvironment, rangeS: RangeS, impreciseName: CodeTypeNameS): ITemplataType = {
    impreciseName match {
      case CodeTypeNameS(name) => env.lookupType(name)
    }
  }
}

class RuleTyperTests extends FunSuite with Matchers {
  def makeCannedEnvironment(): SimpleEnvironment = {
    SimpleEnvironment(
      Map(
        "ImmInterface" -> List(KindTemplataType),
        "Array" -> List(TemplateTemplataType(List(MutabilityTemplataType, VariabilityTemplataType, CoordTemplataType), KindTemplataType)),
        "MutTStruct" -> List(TemplateTemplataType(List(CoordTemplataType), KindTemplataType)),
        "MutTInterface" -> List(TemplateTemplataType(List(CoordTemplataType), KindTemplataType)),
        "MutStruct" -> List(KindTemplataType),
        "MutInterface" -> List(KindTemplataType),
        "void" -> List(KindTemplataType),
        "int" -> List(KindTemplataType)))
  }

  def makeCannedRuleTyper(): RuleTyperEvaluator[SimpleEnvironment, FakeState] = {
    new RuleTyperEvaluator[SimpleEnvironment, FakeState](
      new FakeRuleTyperEvaluatorDelegate() {
        override def lookupType(state: FakeState, env: SimpleEnvironment, rangeS: RangeS, absoluteName: INameS): ITemplataType = {
          absoluteName match {
            case TopLevelCitizenDeclarationNameS(name, _) => env.lookupType(name)
          }
        }
        override def lookupType(state: FakeState, env: SimpleEnvironment, rangeS: RangeS, impreciseName: CodeTypeNameS): ITemplataType = {
          impreciseName match {
            case CodeTypeNameS(name) => env.lookupType(name)
          }
        }
      })
  }

  def makeRuleTyper(
    maybeRuleTyperEvaluator: Option[RuleTyperEvaluator[SimpleEnvironment, FakeState]]):
  RuleTyperEvaluator[SimpleEnvironment, FakeState] = {
    val ruleTyperEvaluator =
      maybeRuleTyperEvaluator match {
        case None =>
          new RuleTyperEvaluator[SimpleEnvironment, FakeState](
            new FakeRuleTyperEvaluatorDelegate())
        case Some(x) => x
      }
    return ruleTyperEvaluator
  }

  test("Borrow becomes share if kind is immutable") {
    val (conclusions, RuleTyperSolveSuccess(_)) =
      makeCannedRuleTyper()
        .solve(
          FakeState(),
          makeCannedEnvironment(),
          List(
            TypedSR(RangeS.testZero,CodeRuneS("__C"),CoordTypeSR),
            EqualsSR(
              RangeS.testZero,
              TemplexSR(RuneST(RangeS.testZero,CodeRuneS("__C"))),
              TemplexSR(
                InterpretedST(RangeS.testZero,ConstraintP,ReadonlyP,NameST(RangeS.testZero, CodeTypeNameS("ImmInterface")))))),
          RangeS.testZero,
          List.empty,
          None)

    vassert(conclusions.typeByRune(CodeRuneA("__C")) == CoordTemplataType)
  }

  test("Weak becomes share if kind is immutable") {
    val (conclusions, RuleTyperSolveSuccess(_)) =
      makeCannedRuleTyper()
        .solve(
          FakeState(),
          makeCannedEnvironment(),
          List(
            TypedSR(RangeS.testZero,CodeRuneS("__C"),CoordTypeSR),
            EqualsSR(
              RangeS.testZero,
              TemplexSR(RuneST(RangeS.testZero,CodeRuneS("__C"))),
              TemplexSR(
                InterpretedST(
                  RangeS.testZero,WeakP,ReadonlyP,NameST(RangeS.testZero, CodeTypeNameS("ImmInterface")))))),
          RangeS.testZero,
          List.empty,
          None)

    vassert(conclusions.typeByRune(CodeRuneA("__C")) == CoordTemplataType)
  }

  test("Can infer coord rune from an incoming kind") {
    val (conclusions, RuleTyperSolveSuccess(_)) =
      makeCannedRuleTyper()
        .solve(
          FakeState(),
          makeCannedEnvironment(),
          List(TypedSR(RangeS.testZero,CodeRuneS("C"), CoordTypeSR)),
          RangeS.testZero,
          List.empty,
          None)

    vassert(conclusions.typeByRune(CodeRuneA("C")) == CoordTemplataType)
  }

  test("Detects conflict between types") {
    val (_, isf @ RuleTyperSolveFailure(_, _, _, _)) =
      makeCannedRuleTyper()
        .solve(
          FakeState(),
          makeCannedEnvironment(),
          List(EqualsSR(RangeS.testZero,TypedSR(RangeS.testZero,CodeRuneS("C"), CoordTypeSR), TypedSR(RangeS.testZero,CodeRuneS("C"), KindTypeSR))),
          RangeS.testZero,
          List.empty,
          None)

    vassert(isf.toString.contains("but previously concluded"))
  }

  test("Can explicitly coerce from kind to coord") {
    val (conclusions, RuleTyperSolveSuccess(_)) =
      makeCannedRuleTyper()
        .solve(
          FakeState(),
          makeCannedEnvironment(),
          List(EqualsSR(RangeS.testZero,TypedSR(RangeS.testZero,CodeRuneS("C"), CoordTypeSR), CallSR(RangeS.testZero,"toRef", List(TypedSR(RangeS.testZero,CodeRuneS("A"), KindTypeSR))))),
          RangeS.testZero,
          List.empty,
          None)

    conclusions.typeByRune(CodeRuneA("C")) shouldEqual CoordTemplataType
  }

  test("Can explicitly coerce from kind to coord 2") {
    val (conclusions, RuleTyperSolveSuccess(_)) =
      makeCannedRuleTyper()
        .solve(
          FakeState(),
          makeCannedEnvironment(),
          List(
            TypedSR(RangeS.testZero,CodeRuneS("Z"),CoordTypeSR),
            EqualsSR(RangeS.testZero,TemplexSR(RuneST(RangeS.testZero,CodeRuneS("Z"))),TemplexSR(NameST(RangeS.testZero, CodeTypeNameS("int"))))),
          RangeS.testZero,
          List.empty,
          None)

    conclusions.typeByRune(CodeRuneA("Z")) shouldEqual CoordTemplataType
  }

  test("Can match KindTemplataType against StructEnvEntry / StructTemplata") {
    val (conclusions, RuleTyperSolveSuccess(_)) =
      makeCannedRuleTyper()
        .solve(
          FakeState(),
          makeCannedEnvironment(),
          List(
            EqualsSR(RangeS.testZero,TemplexSR(RuneST(RangeS.testZero,CodeRuneS("__RetRune"))),CallSR(RangeS.testZero,"toRef",List(TemplexSR(NameST(RangeS.testZero, CodeTypeNameS("MutStruct"))))))),
          RangeS.testZero,
          List.empty,
          None)

    conclusions.typeByRune(CodeRuneA("__RetRune")) shouldEqual CoordTemplataType
  }

  test("Can infer from simple rules") {
    val (conclusions, RuleTyperSolveSuccess(_)) =
      makeCannedRuleTyper()
        .solve(
          FakeState(),
          makeCannedEnvironment(),
          List(
            TypedSR(RangeS.testZero,CodeRuneS("Z"),CoordTypeSR),
            EqualsSR(RangeS.testZero,TemplexSR(RuneST(RangeS.testZero,CodeRuneS("Z"))),CallSR(RangeS.testZero,"toRef", List(TemplexSR(NameST(RangeS.testZero, CodeTypeNameS("int"))))))),
          RangeS.testZero,
          List.empty,
          None)

    vassert(conclusions.typeByRune(CodeRuneA("Z")) == CoordTemplataType)
  }

  test("Can infer type from interface template param") {
    val (conclusions, RuleTyperSolveSuccess(_)) =
      makeCannedRuleTyper()
        .solve(
          FakeState(),
          makeCannedEnvironment(),
          List(
            EqualsSR(RangeS.testZero,
              TypedSR(RangeS.testZero,CodeRuneS("K"), KindTypeSR),
              TemplexSR(CallST(RangeS.testZero,NameST(RangeS.testZero, CodeTypeNameS("MutTInterface")),List(RuneST(RangeS.testZero,CodeRuneS("T"))))))),
          RangeS.testZero,
          List.empty,
          None)

    vassert(conclusions.typeByRune(CodeRuneA("T")) == CoordTemplataType)
    vassert(conclusions.typeByRune(CodeRuneA("K")) == KindTemplataType)
  }

  test("Can infer templata from CallST") {
    val (conclusions, RuleTyperSolveSuccess(_)) =
      makeCannedRuleTyper()
        .solve(
          FakeState(),
          makeCannedEnvironment(),
          List(
            EqualsSR(RangeS.testZero,
              TypedSR(RangeS.testZero,CodeRuneS("X"),KindTypeSR),
              TemplexSR(CallST(RangeS.testZero,NameST(RangeS.testZero, CodeTypeNameS("MutTInterface")),List(RuneST(RangeS.testZero,CodeRuneS("T"))))))),
          RangeS.testZero,
          List.empty,
          None)

    vassert(conclusions.typeByRune(CodeRuneA("T")) == CoordTemplataType)
  }

  test("Can conjure an owning coord from a borrow coord") {
    val (conclusions, RuleTyperSolveSuccess(_)) =
      makeCannedRuleTyper()
        .solve(
          FakeState(),
          makeCannedEnvironment(),
          List(
            TypedSR(RangeS.testZero,CodeRuneS("T"),CoordTypeSR),
            TypedSR(RangeS.testZero,CodeRuneS("Q"),KindTypeSR),
            ComponentsSR(
              RangeS.testZero,
              TypedSR(
                RangeS.testZero,
                CodeRuneS("T"),CoordTypeSR),
              List(
                TemplexSR(OwnershipST(RangeS.testZero,OwnP)),
                TemplexSR(PermissionST(RangeS.testZero,ReadonlyP)),
                TemplexSR(RuneST(RangeS.testZero,CodeRuneS("Q"))))),
            TypedSR(RangeS.testZero,CodeRuneS("Z"),CoordTypeSR),
            ComponentsSR(
              RangeS.testZero,
              TypedSR(RangeS.testZero,CodeRuneS("Z"),CoordTypeSR),
              List(
                TemplexSR(OwnershipST(RangeS.testZero,ConstraintP)),
                TemplexSR(PermissionST(RangeS.testZero,ReadonlyP)),
                TemplexSR(RuneST(RangeS.testZero,CodeRuneS("Q")))))),
          RangeS.testZero,
          List(AtomSP(RangeS.testZero, CaptureS(CodeVarNameS("m")),None,CodeRuneS("Z"),None)),
          None)

    conclusions.typeByRune(CodeRuneA("T")) shouldEqual CoordTemplataType
  }

  test("Rune 0 upcasts to right type, simple") {
    val (conclusions, RuleTyperSolveSuccess(_)) =
      makeCannedRuleTyper()
        .solve(
          FakeState(),
          makeCannedEnvironment(),
          List(
            TypedSR(RangeS.testZero,CodeRuneS("__Let0_"),CoordTypeSR),
            EqualsSR(RangeS.testZero,TemplexSR(RuneST(RangeS.testZero,CodeRuneS("__Let0_"))),CallSR(RangeS.testZero,"toRef", List(TemplexSR(NameST(RangeS.testZero, CodeTypeNameS("MutInterface"))))))),
          RangeS.testZero,
          List(AtomSP(RangeS.testZero, CaptureS(CodeVarNameS("x")),None,CodeRuneS("__Let0_"),None)),
          None)

    vassert(conclusions.typeByRune(CodeRuneA("__Let0_")) == CoordTemplataType)
  }

  test("Rune 0 upcasts to right type templated") {
    val (conclusions, RuleTyperSolveSuccess(_)) =
      makeCannedRuleTyper()
        .solve(
          FakeState(),
          makeCannedEnvironment(),
          List(
            TypedSR(RangeS.testZero,CodeRuneS("__Let0_"),CoordTypeSR),
            EqualsSR(RangeS.testZero,TemplexSR(RuneST(RangeS.testZero,CodeRuneS("__Let0_"))),CallSR(RangeS.testZero,"toRef", List(TemplexSR(CallST(RangeS.testZero,NameST(RangeS.testZero, CodeTypeNameS("MutTInterface")), List(RuneST(RangeS.testZero,CodeRuneS("T"))))))))),
          RangeS.testZero,
          List(AtomSP(RangeS.testZero, CaptureS(CodeVarNameS("x")),None,CodeRuneS("__Let0_"),None)),
          None)

    vassert(conclusions.typeByRune(CodeRuneA("__Let0_")) == CoordTemplataType)
    vassert(conclusions.typeByRune(CodeRuneA("T")) == CoordTemplataType)
  }

  test("Tests destructor") {
    // Tests that we can make a rule that will only match structs, arrays, packs, sequences.
    // It doesn't have to be in this form, but we do need the capability in some way, so that
    // we can have a templated destructor that matches any of those.

    val rules =
      List(
        ComponentsSR(
          RangeS.testZero,
          TypedSR(RangeS.testZero,CodeRuneS("T"),CoordTypeSR),
          List(
            OrSR(RangeS.testZero,List(TemplexSR(OwnershipST(RangeS.testZero,OwnP)), TemplexSR(OwnershipST(RangeS.testZero,ShareP)))),
            TemplexSR(PermissionST(RangeS.testZero,ReadonlyP)),
            CallSR(RangeS.testZero,"passThroughIfConcrete",List(TemplexSR(RuneST(RangeS.testZero,CodeRuneS("Z"))))))),
        EqualsSR(RangeS.testZero,TypedSR(RangeS.testZero,CodeRuneS("V"),CoordTypeSR),CallSR(RangeS.testZero,"toRef",List(TemplexSR(NameST(RangeS.testZero, CodeTypeNameS("void")))))))
    val atoms =
      List(AtomSP(RangeS.testZero, CaptureS(CodeVarNameS("this")),None,CodeRuneS("T"),None))

    // Test that it does match a pack
    val (conclusions, RuleTyperSolveSuccess(_)) =
      makeCannedRuleTyper().solve(FakeState(), makeCannedEnvironment(), rules, RangeS.testZero,atoms, None)
    vassert(conclusions.typeByRune(CodeRuneA("T")) == CoordTemplataType)
  }

  test("Tests passThroughIfInterface") {
    // Tests that we can make a rule that will only match interfaces.
    // It doesn't have to be in this form, but we do need the capability in some way, so that
    // we can have a templated destructor that matches any of those.

    val rules =
      List(
        ComponentsSR(
          RangeS.testZero,
          TypedSR(RangeS.testZero,CodeRuneS("T"),CoordTypeSR),
          List(
            OrSR(RangeS.testZero,List(TemplexSR(OwnershipST(RangeS.testZero,OwnP)), TemplexSR(OwnershipST(RangeS.testZero,ShareP)))),
            TemplexSR(PermissionST(RangeS.testZero,ReadonlyP)),
            CallSR(RangeS.testZero,"passThroughIfInterface",List(TemplexSR(RuneST(RangeS.testZero,CodeRuneS("Z"))))))),
        EqualsSR(RangeS.testZero,TypedSR(RangeS.testZero,CodeRuneS("V"),CoordTypeSR),CallSR(RangeS.testZero,"toRef",List(TemplexSR(NameST(RangeS.testZero, CodeTypeNameS("void")))))))
    val atoms =
      List(AtomSP(RangeS.testZero, CaptureS(CodeVarNameS("this")),None,CodeRuneS("T"),None))

    // Test that it does match an interface
    val (conclusions, RuleTyperSolveSuccess(_)) =
      makeCannedRuleTyper().solve(FakeState(), makeCannedEnvironment(), rules, RangeS.testZero,atoms, None)
    vassert(conclusions.typeByRune(CodeRuneA("T")) == CoordTemplataType)
  }


  test("Tests passThroughIfStruct") {
    // Tests that we can make a rule that will only match structs.
    // It doesn't have to be in this form, but we do need the capability in some way, so that
    // we can have a templated destructor that matches any of those.

    val rules =
      List(
        ComponentsSR(
          RangeS.testZero,
          TypedSR(RangeS.testZero,CodeRuneS("T"),CoordTypeSR),
          List(
            OrSR(RangeS.testZero,List(TemplexSR(OwnershipST(RangeS.testZero,OwnP)), TemplexSR(OwnershipST(RangeS.testZero,ShareP)))),
            TemplexSR(PermissionST(RangeS.testZero,ReadonlyP)),
            CallSR(RangeS.testZero,"passThroughIfStruct",List(TemplexSR(RuneST(RangeS.testZero,CodeRuneS("Z"))))))))
    val atoms =
      List(AtomSP(RangeS.testZero, CaptureS(CodeVarNameS("this")),None,CodeRuneS("T"),None))

    val (conclusions, RuleTyperSolveSuccess(_)) = makeCannedRuleTyper().solve(FakeState(), makeCannedEnvironment(), rules, RangeS.testZero,atoms,None)
    vassert(conclusions.typeByRune(CodeRuneA("T")) == CoordTemplataType)
  }

  test("Test coercing template call result") {
    // Tests that we can make a rule that will only match structs, arrays, packs, sequences.
    // It doesn't have to be in this form, but we do need the capability in some way, so that
    // we can have a templated destructor that matches any of those.

    val rules =
      List(
        TypedSR(RangeS.testZero,CodeRuneS("Z"),CoordTypeSR),
        EqualsSR(RangeS.testZero,
          TemplexSR(RuneST(RangeS.testZero,CodeRuneS("Z"))),
          TemplexSR(CallST(RangeS.testZero,NameST(RangeS.testZero, CodeTypeNameS("MutTStruct")),List(NameST(RangeS.testZero, CodeTypeNameS("int")))))))
    val atoms =
      List(AtomSP(RangeS.testZero, CaptureS(CodeVarNameS("this")),None,CodeRuneS("T"),None))

    val (conclusions, RuleTyperSolveSuccess(_)) =
      makeCannedRuleTyper().solve(FakeState(), makeCannedEnvironment(), rules, RangeS.testZero,atoms, None)

    conclusions.typeByRune(CodeRuneA("Z")) shouldEqual CoordTemplataType
  }

  test("Test ownershipped") {
    // Tests that we can make a rule that will only match structs, arrays, packs, sequences.
    // It doesn't have to be in this form, but we do need the capability in some way, so that
    // we can have a templated destructor that matches any of those.

    val rules =
      List(
        TypedSR(RangeS.testZero,CodeRuneS("Z"),CoordTypeSR),
        EqualsSR(RangeS.testZero,
          TemplexSR(RuneST(RangeS.testZero,CodeRuneS("Z"))),
          TemplexSR(CallST(RangeS.testZero,NameST(RangeS.testZero, CodeTypeNameS("MutTStruct")),List(InterpretedST(RangeS.testZero,ShareP,ReadonlyP,NameST(RangeS.testZero, CodeTypeNameS("int"))))))))
    val atoms =
      List(AtomSP(RangeS.testZero, CaptureS(CodeVarNameS("this")),None,CodeRuneS("T"),None))

    val (conclusions, RuleTyperSolveSuccess(_)) =
      makeCannedRuleTyper().solve(FakeState(), makeCannedEnvironment(), rules, RangeS.testZero,atoms, None)
    conclusions.typeByRune(CodeRuneA("Z")) shouldEqual CoordTemplataType
  }



  test("Test result of a CallAT can coerce to coord") {
    val rules =
      List(
        TypedSR(RangeS.testZero,CodeRuneS("__Par0"),CoordTypeSR),
        EqualsSR(RangeS.testZero,TemplexSR(RuneST(RangeS.testZero,CodeRuneS("__Par0"))),TemplexSR(NameST(RangeS.testZero, CodeTypeNameS("MutStruct")))))
    val atoms =
      List(AtomSP(RangeS.testZero, CaptureS(CodeVarNameS("this")),None,CodeRuneS("T"),None))

    val (conclusions, RuleTyperSolveSuccess(_)) =
      makeCannedRuleTyper().solve(FakeState(), makeCannedEnvironment(), rules, RangeS.testZero,atoms, None)
    conclusions.typeByRune(CodeRuneA("__Par0")) shouldEqual CoordTemplataType
  }

  test("Matching a CoordTemplataType onto a CallAT") {
    val rules =
      List(
        TypedSR(RangeS.testZero,CodeRuneS("Z"),CoordTypeSR),
        EqualsSR(RangeS.testZero,TemplexSR(RuneST(RangeS.testZero,CodeRuneS("Z"))),TemplexSR(CallST(RangeS.testZero,NameST(RangeS.testZero, CodeTypeNameS("MutTStruct")),List(RuneST(RangeS.testZero,CodeRuneS("T")))))))

    val (conclusions, RuleTyperSolveSuccess(_)) =
      makeCannedRuleTyper().solve(
        FakeState(),
        makeCannedEnvironment(),
        rules,
        RangeS.testZero,
        List(AtomSP(RangeS.testZero, CaptureS(CodeVarNameS("x")),Some(AbstractSP),CodeRuneS("Z"),None)),
        None)
    conclusions.typeByRune(CodeRuneA("Z")) shouldEqual CoordTemplataType
  }

  test("Test destructuring") {
    val (conclusions, RuleTyperSolveSuccess(_)) =
      makeCannedRuleTyper().solve(
        FakeState(),
        makeCannedEnvironment(),
        List(
          TypedSR(RangeS.testZero,CodeRuneS("__Let0_"),CoordTypeSR),
          TypedSR(RangeS.testZero,CodeRuneS("__Let0__Mem_0"),CoordTypeSR),
          TypedSR(RangeS.testZero,CodeRuneS("__Let0__Mem_1"),CoordTypeSR)),
        RangeS.testZero,
        List(
          AtomSP(
            RangeS.testZero,
            CaptureS(CodeVarNameS("x")),
            None,
            CodeRuneS("__Let0_"),
            Some(
              List(
                AtomSP(RangeS.testZero, CaptureS(CodeVarNameS("x")),None,CodeRuneS("__Let0__Mem_0"),None),
                AtomSP(RangeS.testZero, CaptureS(CodeVarNameS("y")),None,CodeRuneS("__Let0__Mem_1"),None))))),
        Some(Set(CodeRuneA("__Let0__Mem_0"), CodeRuneA("__Let0__Mem_1"), CodeRuneA("__Let0_"))))
    conclusions.typeByRune(CodeRuneA("__Let0_")) shouldEqual CoordTemplataType
    conclusions.typeByRune(CodeRuneA("__Let0__Mem_0")) shouldEqual CoordTemplataType
    conclusions.typeByRune(CodeRuneA("__Let0__Mem_1")) shouldEqual CoordTemplataType
  }

  test("Test array sequence") {
    val (conclusions, RuleTyperSolveSuccess(_)) =
      makeCannedRuleTyper().solve(
        FakeState(),
        makeCannedEnvironment(),
        List(
          TypedSR(RangeS.testZero,CodeRuneS("Z"),CoordTypeSR),
          EqualsSR(RangeS.testZero,
            TemplexSR(RuneST(RangeS.testZero,CodeRuneS("Z"))),
            TemplexSR(
              RepeaterSequenceST(
                RangeS.testZero,
                MutabilityST(RangeS.testZero,MutableP),
                VariabilityST(RangeS.testZero,VaryingP),
                IntST(RangeS.testZero,5),InterpretedST(RangeS.testZero,ShareP,ReadonlyP,NameST(RangeS.testZero, CodeTypeNameS("int"))))))),
        RangeS.testZero,
        List.empty,
        None)
    conclusions.typeByRune(CodeRuneA("Z")) shouldEqual CoordTemplataType
  }

  test("Test manual sequence") {
    val (conclusions, RuleTyperSolveSuccess(_)) =
      makeCannedRuleTyper().solve(
        FakeState(),
        makeCannedEnvironment(),
        List(
          TypedSR(RangeS.testZero,CodeRuneS("Z"),CoordTypeSR),
          EqualsSR(RangeS.testZero,
            TemplexSR(RuneST(RangeS.testZero,CodeRuneS("Z"))),
            TemplexSR(
              ManualSequenceST(RangeS.testZero,
                List(
                  InterpretedST(RangeS.testZero,ShareP,ReadonlyP, NameST(RangeS.testZero, CodeTypeNameS("int"))),
                  InterpretedST(RangeS.testZero,ShareP,ReadonlyP, NameST(RangeS.testZero, CodeTypeNameS("int")))))))),
        RangeS.testZero,
        List.empty,
        None)
    conclusions.typeByRune(CodeRuneA("Z")) shouldEqual CoordTemplataType
  }

  test("Test array") {
    val (conclusions, RuleTyperSolveSuccess(_)) =
      makeCannedRuleTyper().solve(
        FakeState(),
        makeCannedEnvironment(),
        List(
          EqualsSR(RangeS.testZero,
            TypedSR(RangeS.testZero,CodeRuneS("K"), KindTypeSR),
            TemplexSR(
              CallST(RangeS.testZero,NameST(RangeS.testZero, CodeTypeNameS("Array")),List(MutabilityST(RangeS.testZero,MutableP), VariabilityST(RangeS.testZero,VaryingP), NameST(RangeS.testZero, CodeTypeNameS("int")))))),
          EqualsSR(RangeS.testZero,
            TypedSR(RangeS.testZero,CodeRuneS("K"), KindTypeSR),
            TemplexSR(CallST(RangeS.testZero,NameST(RangeS.testZero, CodeTypeNameS("Array")),List(RuneST(RangeS.testZero,CodeRuneS("M")), RuneST(RangeS.testZero,CodeRuneS("V")), RuneST(RangeS.testZero,CodeRuneS("T"))))))),
        RangeS.testZero,
        List.empty,
        None)
    conclusions.typeByRune(CodeRuneA("M")) shouldEqual MutabilityTemplataType
    conclusions.typeByRune(CodeRuneA("V")) shouldEqual VariabilityTemplataType
    conclusions.typeByRune(CodeRuneA("T")) shouldEqual CoordTemplataType
  }

  test("Test evaluating isa") {
    val (conclusions, RuleTyperSolveSuccess(_)) =
      makeCannedRuleTyper()
        .solve(
          FakeState(),
          makeCannedEnvironment(),
          List(
            IsaSR(RangeS.testZero,
              TemplexSR(RuneST(RangeS.testZero,CodeRuneS("K"))),
              TemplexSR(NameST(RangeS.testZero, CodeTypeNameS("MutInterface"))))),
          RangeS.testZero,
          List.empty,
          None)
    conclusions.typeByRune(CodeRuneA("K")) shouldEqual KindTemplataType
  }

  test("Test evaluating prototype components") {
    val (conclusions, RuleTyperSolveSuccess(_)) =
      makeCannedRuleTyper()
        .solve(
          FakeState(),
          makeCannedEnvironment(),
          List(
            ComponentsSR(
              RangeS.testZero,
              TypedSR(RangeS.testZero,CodeRuneS("X"), PrototypeTypeSR),
              List(
                TemplexSR(RuneST(RangeS.testZero,CodeRuneS("A"))),
                TemplexSR(PackST(RangeS.testZero,List(RuneST(RangeS.testZero,CodeRuneS("B"))))),
                TemplexSR(RuneST(RangeS.testZero,CodeRuneS("C")))))),
          RangeS.testZero,
          List.empty,
          None)
    conclusions.typeByRune(CodeRuneA("X")) shouldEqual PrototypeTemplataType
    conclusions.typeByRune(CodeRuneA("A")) shouldEqual StringTemplataType
    conclusions.typeByRune(CodeRuneA("B")) shouldEqual CoordTemplataType
    conclusions.typeByRune(CodeRuneA("C")) shouldEqual CoordTemplataType
  }
}
