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
  override def lookupType(state: FakeState, env: SimpleEnvironment, absoluteName: INameS): ITemplataType = {
    absoluteName match {
      case TopLevelCitizenDeclarationNameS(name, _) => env.lookupType(name)
    }
  }
  override def lookupType(state: FakeState, env: SimpleEnvironment, impreciseName: CodeTypeNameS): ITemplataType = {
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
        "Array" -> List(TemplateTemplataType(List(MutabilityTemplataType, CoordTemplataType), KindTemplataType)),
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
        override def lookupType(state: FakeState, env: SimpleEnvironment, absoluteName: INameS): ITemplataType = {
          absoluteName match {
            case TopLevelCitizenDeclarationNameS(name, _) => env.lookupType(name)
          }
        }
        override def lookupType(state: FakeState, env: SimpleEnvironment, impreciseName: CodeTypeNameS): ITemplataType = {
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
            TypedSR(CodeRuneS("__C"),CoordTypeSR),
            EqualsSR(TemplexSR(RuneST(CodeRuneS("__C"))),TemplexSR(OwnershippedST(BorrowP,NameST(CodeTypeNameS("ImmInterface")))))),
          List(),
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
            TypedSR(CodeRuneS("__C"),CoordTypeSR),
            EqualsSR(TemplexSR(RuneST(CodeRuneS("__C"))),TemplexSR(OwnershippedST(WeakP,NameST(CodeTypeNameS("ImmInterface")))))),
          List(),
          None)

    vassert(conclusions.typeByRune(CodeRuneA("__C")) == CoordTemplataType)
  }

  test("Can infer coord rune from an incoming kind") {
    val (conclusions, RuleTyperSolveSuccess(_)) =
      makeCannedRuleTyper()
        .solve(
          FakeState(),
          makeCannedEnvironment(),
          List(TypedSR(CodeRuneS("C"), CoordTypeSR)),
          List(),
          None)

    vassert(conclusions.typeByRune(CodeRuneA("C")) == CoordTemplataType)
  }

  test("Detects conflict between types") {
    val (_, isf @ RuleTyperSolveFailure(_, _, _)) =
      makeCannedRuleTyper()
        .solve(
          FakeState(),
          makeCannedEnvironment(),
          List(EqualsSR(TypedSR(CodeRuneS("C"), CoordTypeSR), TypedSR(CodeRuneS("C"), KindTypeSR))),
          List(),
          None)

    vassert(isf.toString.contains("but previously concluded"))
  }

  test("Can explicitly coerce from kind to coord") {
    val (conclusions, RuleTyperSolveSuccess(_)) =
      makeCannedRuleTyper()
        .solve(
          FakeState(),
          makeCannedEnvironment(),
          List(EqualsSR(TypedSR(CodeRuneS("C"), CoordTypeSR), CallSR("toRef", List(TypedSR(CodeRuneS("A"), KindTypeSR))))),
          List(),
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
            TypedSR(CodeRuneS("Z"),CoordTypeSR),
            EqualsSR(TemplexSR(RuneST(CodeRuneS("Z"))),TemplexSR(NameST(CodeTypeNameS("int"))))),
          List(),
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
            EqualsSR(TemplexSR(RuneST(CodeRuneS("__RetRune"))),CallSR("toRef",List(TemplexSR(NameST(CodeTypeNameS("MutStruct"))))))),
          List(),
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
            TypedSR(CodeRuneS("Z"),CoordTypeSR),
            EqualsSR(TemplexSR(RuneST(CodeRuneS("Z"))),CallSR("toRef", List(TemplexSR(NameST(CodeTypeNameS("int"))))))),
          List(),
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
            EqualsSR(
              TypedSR(CodeRuneS("K"), KindTypeSR),
              TemplexSR(CallST(NameST(CodeTypeNameS("MutTInterface")),List(RuneST(CodeRuneS("T"))))))),
          List(),
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
            EqualsSR(
              TypedSR(CodeRuneS("X"),KindTypeSR),
              TemplexSR(CallST(NameST(CodeTypeNameS("MutTInterface")),List(RuneST(CodeRuneS("T"))))))),
          List(),
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
            TypedSR(CodeRuneS("T"),CoordTypeSR),
            TypedSR(CodeRuneS("Q"),KindTypeSR),
            ComponentsSR(TypedSR(CodeRuneS("T"),CoordTypeSR),List(TemplexSR(OwnershipST(OwnP)), TemplexSR(RuneST(CodeRuneS("Q"))))),
            TypedSR(CodeRuneS("Z"),CoordTypeSR),
            ComponentsSR(TypedSR(CodeRuneS("Z"),CoordTypeSR),List(TemplexSR(OwnershipST(BorrowP)), TemplexSR(RuneST(CodeRuneS("Q")))))),
          List(AtomSP(CaptureS(CodeVarNameS("m"),FinalP),None,CodeRuneS("Z"),None)),
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
            TypedSR(CodeRuneS("__Let0_"),CoordTypeSR),
            EqualsSR(TemplexSR(RuneST(CodeRuneS("__Let0_"))),CallSR("toRef", List(TemplexSR(NameST(CodeTypeNameS("MutInterface"))))))),
          List(AtomSP(CaptureS(CodeVarNameS("x"),FinalP),None,CodeRuneS("__Let0_"),None)),
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
            TypedSR(CodeRuneS("__Let0_"),CoordTypeSR),
            EqualsSR(TemplexSR(RuneST(CodeRuneS("__Let0_"))),CallSR("toRef", List(TemplexSR(CallST(NameST(CodeTypeNameS("MutTInterface")), List(RuneST(CodeRuneS("T"))))))))),
          List(AtomSP(CaptureS(CodeVarNameS("x"),FinalP),None,CodeRuneS("__Let0_"),None)),
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
        ComponentsSR(TypedSR(CodeRuneS("T"),CoordTypeSR),List(OrSR(List(TemplexSR(OwnershipST(OwnP)), TemplexSR(OwnershipST(ShareP)))), CallSR("passThroughIfConcrete",List(TemplexSR(RuneST(CodeRuneS("Z"))))))),
        EqualsSR(TypedSR(CodeRuneS("V"),CoordTypeSR),CallSR("toRef",List(TemplexSR(NameST(CodeTypeNameS("void")))))))
    val atoms =
      List(AtomSP(CaptureS(CodeVarNameS("this"),FinalP),None,CodeRuneS("T"),None))

    // Test that it does match a pack
    val (conclusions, RuleTyperSolveSuccess(_)) =
      makeCannedRuleTyper().solve(FakeState(), makeCannedEnvironment(), rules, atoms, None)
    vassert(conclusions.typeByRune(CodeRuneA("T")) == CoordTemplataType)
  }

  test("Tests passThroughIfInterface") {
    // Tests that we can make a rule that will only match interfaces.
    // It doesn't have to be in this form, but we do need the capability in some way, so that
    // we can have a templated destructor that matches any of those.

    val rules =
      List(
        ComponentsSR(
          TypedSR(CodeRuneS("T"),CoordTypeSR),
          List(
            OrSR(List(TemplexSR(OwnershipST(OwnP)), TemplexSR(OwnershipST(ShareP)))),
            CallSR("passThroughIfInterface",List(TemplexSR(RuneST(CodeRuneS("Z"))))))),
        EqualsSR(TypedSR(CodeRuneS("V"),CoordTypeSR),CallSR("toRef",List(TemplexSR(NameST(CodeTypeNameS("void")))))))
    val atoms =
      List(AtomSP(CaptureS(CodeVarNameS("this"),FinalP),None,CodeRuneS("T"),None))

    // Test that it does match an interface
    val (conclusions, RuleTyperSolveSuccess(_)) =
      makeCannedRuleTyper().solve(FakeState(), makeCannedEnvironment(), rules, atoms, None)
    vassert(conclusions.typeByRune(CodeRuneA("T")) == CoordTemplataType)
  }


  test("Tests passThroughIfStruct") {
    // Tests that we can make a rule that will only match structs.
    // It doesn't have to be in this form, but we do need the capability in some way, so that
    // we can have a templated destructor that matches any of those.

    val rules =
      List(
        ComponentsSR(
          TypedSR(CodeRuneS("T"),CoordTypeSR),
          List(
            OrSR(List(TemplexSR(OwnershipST(OwnP)), TemplexSR(OwnershipST(ShareP)))),
            CallSR("passThroughIfStruct",List(TemplexSR(RuneST(CodeRuneS("Z"))))))))
    val atoms =
      List(AtomSP(CaptureS(CodeVarNameS("this"),FinalP),None,CodeRuneS("T"),None))

    val (conclusions, RuleTyperSolveSuccess(_)) = makeCannedRuleTyper().solve(FakeState(), makeCannedEnvironment(), rules, atoms,None)
    vassert(conclusions.typeByRune(CodeRuneA("T")) == CoordTemplataType)
  }

  test("Test coercing template call result") {
    // Tests that we can make a rule that will only match structs, arrays, packs, sequences.
    // It doesn't have to be in this form, but we do need the capability in some way, so that
    // we can have a templated destructor that matches any of those.

    val rules =
      List(
        TypedSR(CodeRuneS("Z"),CoordTypeSR),
        EqualsSR(
          TemplexSR(RuneST(CodeRuneS("Z"))),
          TemplexSR(CallST(NameST(CodeTypeNameS("MutTStruct")),List(NameST(CodeTypeNameS("int")))))))
    val atoms =
      List(AtomSP(CaptureS(CodeVarNameS("this"),FinalP),None,CodeRuneS("T"),None))

    val (conclusions, RuleTyperSolveSuccess(_)) =
      makeCannedRuleTyper().solve(FakeState(), makeCannedEnvironment(), rules, atoms, None)

    conclusions.typeByRune(CodeRuneA("Z")) shouldEqual CoordTemplataType
  }

  test("Test ownershipped") {
    // Tests that we can make a rule that will only match structs, arrays, packs, sequences.
    // It doesn't have to be in this form, but we do need the capability in some way, so that
    // we can have a templated destructor that matches any of those.

    val rules =
      List(
        TypedSR(CodeRuneS("Z"),CoordTypeSR),
        EqualsSR(
          TemplexSR(RuneST(CodeRuneS("Z"))),
          TemplexSR(CallST(NameST(CodeTypeNameS("MutTStruct")),List(OwnershippedST(ShareP,NameST(CodeTypeNameS("int"))))))))
    val atoms =
      List(AtomSP(CaptureS(CodeVarNameS("this"),FinalP),None,CodeRuneS("T"),None))

    val (conclusions, RuleTyperSolveSuccess(_)) =
      makeCannedRuleTyper().solve(FakeState(), makeCannedEnvironment(), rules, atoms, None)
    conclusions.typeByRune(CodeRuneA("Z")) shouldEqual CoordTemplataType
  }



  test("Test result of a CallAT can coerce to coord") {
    val rules =
      List(
        TypedSR(CodeRuneS("__Par0"),CoordTypeSR),
        EqualsSR(TemplexSR(RuneST(CodeRuneS("__Par0"))),TemplexSR(NameST(CodeTypeNameS("MutStruct")))))
    val atoms =
      List(AtomSP(CaptureS(CodeVarNameS("this"),FinalP),None,CodeRuneS("T"),None))

    val (conclusions, RuleTyperSolveSuccess(_)) =
      makeCannedRuleTyper().solve(FakeState(), makeCannedEnvironment(), rules, atoms, None)
    conclusions.typeByRune(CodeRuneA("__Par0")) shouldEqual CoordTemplataType
  }

  test("Matching a CoordTemplataType onto a CallAT") {
    val rules =
      List(
        TypedSR(CodeRuneS("Z"),CoordTypeSR),
        EqualsSR(TemplexSR(RuneST(CodeRuneS("Z"))),TemplexSR(CallST(NameST(CodeTypeNameS("MutTStruct")),List(RuneST(CodeRuneS("T")))))))

    val (conclusions, RuleTyperSolveSuccess(_)) =
      makeCannedRuleTyper().solve(
        FakeState(),
        makeCannedEnvironment(),
        rules,
        List(AtomSP(CaptureS(CodeVarNameS("x"),FinalP),Some(AbstractSP),CodeRuneS("Z"),None)),
        None)
    conclusions.typeByRune(CodeRuneA("Z")) shouldEqual CoordTemplataType
  }

  test("Test destructuring") {
    val (conclusions, RuleTyperSolveSuccess(_)) =
      makeCannedRuleTyper().solve(
        FakeState(),
        makeCannedEnvironment(),
        List(
          TypedSR(CodeRuneS("__Let0_"),CoordTypeSR),
          TypedSR(CodeRuneS("__Let0__Mem_0"),CoordTypeSR),
          TypedSR(CodeRuneS("__Let0__Mem_1"),CoordTypeSR)),
        List(
          AtomSP(
            CaptureS(CodeVarNameS("x"), FinalP),
            None,
            CodeRuneS("__Let0_"),
            Some(
              List(
                AtomSP(CaptureS(CodeVarNameS("x"),FinalP),None,CodeRuneS("__Let0__Mem_0"),None),
                AtomSP(CaptureS(CodeVarNameS("y"),FinalP),None,CodeRuneS("__Let0__Mem_1"),None))))),
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
          TypedSR(CodeRuneS("Z"),CoordTypeSR),
          EqualsSR(
            TemplexSR(RuneST(CodeRuneS("Z"))),
            TemplexSR(RepeaterSequenceST(MutabilityST(MutableP), IntST(5),OwnershippedST(ShareP,NameST(CodeTypeNameS("int"))))))),
        List(),
        None)
    conclusions.typeByRune(CodeRuneA("Z")) shouldEqual CoordTemplataType
  }

  test("Test array") {
    val (conclusions, RuleTyperSolveSuccess(_)) =
      makeCannedRuleTyper().solve(
        FakeState(),
        makeCannedEnvironment(),
        List(
          EqualsSR(
            TypedSR(CodeRuneS("K"), KindTypeSR),
            TemplexSR(CallST(NameST(CodeTypeNameS("Array")),List(MutabilityST(MutableP), NameST(CodeTypeNameS("int")))))),
          EqualsSR(
            TypedSR(CodeRuneS("K"), KindTypeSR),
            TemplexSR(CallST(NameST(CodeTypeNameS("Array")),List(RuneST(CodeRuneS("M")), RuneST(CodeRuneS("T"))))))),
        List(),
        None)
    conclusions.typeByRune(CodeRuneA("M")) shouldEqual MutabilityTemplataType
    conclusions.typeByRune(CodeRuneA("T")) shouldEqual CoordTemplataType
  }

  test("Test evaluating isa") {
    val (conclusions, RuleTyperSolveSuccess(_)) =
      makeCannedRuleTyper()
        .solve(
          FakeState(),
          makeCannedEnvironment(),
          List(
            IsaSR(
              TemplexSR(RuneST(CodeRuneS("K"))),
              TemplexSR(NameST(CodeTypeNameS("MutInterface"))))),
          List(),
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
              TypedSR(CodeRuneS("X"), PrototypeTypeSR),
              List(
                TemplexSR(RuneST(CodeRuneS("A"))),
                TemplexSR(PackST(List(RuneST(CodeRuneS("B"))))),
                TemplexSR(RuneST(CodeRuneS("C")))))),
          List(),
          None)
    conclusions.typeByRune(CodeRuneA("X")) shouldEqual PrototypeTemplataType
    conclusions.typeByRune(CodeRuneA("A")) shouldEqual StringTemplataType
    conclusions.typeByRune(CodeRuneA("B")) shouldEqual CoordTemplataType
    conclusions.typeByRune(CodeRuneA("C")) shouldEqual CoordTemplataType
  }
}
