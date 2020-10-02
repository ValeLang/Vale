package net.verdagon.vale.templar.infer

import net.verdagon.vale.astronomer.{FakeState => _, SimpleEnvironment => _, _}
import net.verdagon.vale.astronomer.ruletyper.IRuleTyperEvaluatorDelegate
import net.verdagon.vale.parser._
import net.verdagon.vale.scout.{IEnvironment => _, _}
import net.verdagon.vale.templar.{ArraySequenceE2, CitizenName2, CitizenTemplateName2, CodeRune2, FullName2, FunctionName2, GlobalNamespaceName2, IName2, ImplicitRune2, NameTranslator, PrimitiveName2, Program2, TupleName2}
import net.verdagon.vale.{IProfiler, NullProfiler, vassert, vassertSome, vfail, vimpl, scout => s}
import net.verdagon.vale.templar.env._
import net.verdagon.vale.templar.infer.{InfererEquator, InfererEvaluator}
import net.verdagon.vale.templar.infer.infer.{IInferSolveResult, InferSolveFailure, InferSolveSuccess}
import net.verdagon.vale.templar.templata._
//import org.scalamock.scalatest.MockFactory
import net.verdagon.vale.templar.types._
import org.scalatest.{FunSuite, Matchers}

import scala.collection.immutable.List

case class FakeEnv()
case class FakeState()

object InfererTestUtils {
  def getMutability(kind: Kind): Mutability = {
    kind match {
      case Void2() => Immutable
      case Int2() => Immutable
      case StructRef2(FullName2(_, CitizenName2(humanName, _))) if humanName.startsWith("Imm") => Immutable
      case StructRef2(FullName2(_, CitizenName2(humanName, _))) if humanName.startsWith("Mut") => Mutable
      case InterfaceRef2(FullName2(_, CitizenName2(humanName, _))) if humanName.startsWith("Imm") => Immutable
      case InterfaceRef2(FullName2(_, CitizenName2(humanName, _))) if humanName.startsWith("Mut") => Mutable
      case KnownSizeArrayT2(_, RawArrayT2(_, mutability)) => mutability
      case UnknownSizeArrayT2(RawArrayT2(_, mutability)) => mutability
    }
  }
}

case class SimpleEnvironment(templatas: TemplatasStore) extends IEnvironment {
  override def getParentEnv(): Option[IEnvironment] = None
  def fullName = FullName2(List(), GlobalNamespaceName2())
  def globalEnv: NamespaceEnvironment[IName2] = {
    vfail()
  }
  override def getAllTemplatasWithAbsoluteName2(name: IName2, lookupFilter: Set[ILookupContext]): List[ITemplata] = {
    templatas.getAllTemplatasWithAbsoluteName2(this, name, lookupFilter)
  }
  override def getNearestTemplataWithAbsoluteName2(name: IName2, lookupFilter: Set[ILookupContext]): Option[ITemplata] = {
    templatas.getNearestTemplataWithAbsoluteName2(this, name, lookupFilter)
  }
  override def getAllTemplatasWithName(profiler: IProfiler, name: IImpreciseNameStepA, lookupFilter: Set[ILookupContext]): List[ITemplata] = {
    templatas.getAllTemplatasWithName(profiler, this, name, lookupFilter)
  }
  override def getNearestTemplataWithName(name: IImpreciseNameStepA, lookupFilter: Set[ILookupContext]): Option[ITemplata] = {
    templatas.getNearestTemplataWithName(this, name, lookupFilter)
  }
}

class FakeInfererEvaluatorDelegate extends IInfererEvaluatorDelegate[SimpleEnvironment, FakeState] {
  override def getAncestorInterfaceDistance(temputs: FakeState, descendantCitizenRef: CitizenRef2, ancestorInterfaceRef: InterfaceRef2): (Option[Int]) = {
    vfail()
  }

  override def getAncestorInterfaces(temputs: FakeState, descendantCitizenRef: CitizenRef2): (Set[InterfaceRef2]) = {
    vfail()
  }

  override def getMutability(state: FakeState, kind: Kind): Mutability = {
    InfererTestUtils.getMutability(kind)
  }

  override def lookupMemberTypes(state: FakeState, kind: Kind, expectedNumMembers: Int): Option[List[Coord]] = {
    vfail()
  }

  override def getMemberCoords(state: FakeState, structRef: StructRef2): List[Coord] = {
    vfail()
  }

  override def structIsClosure(state: FakeState, structRef: StructRef2): Boolean = {
    vfail()
  }

  override def getSimpleInterfaceMethod(state: FakeState, interfaceRef: InterfaceRef2): Prototype2 = {
    vfail()
  }

  override def lookupTemplata(env: SimpleEnvironment, rune: IName2): ITemplata = {
    val results = env.getAllTemplatasWithAbsoluteName2(rune, Set(TemplataLookupContext))
    vassert(results.size == 1)
    results.head
  }

  override def lookupTemplata(profiler: IProfiler, env: SimpleEnvironment, name: IImpreciseNameStepA): ITemplata = {
    val results = env.getAllTemplatasWithName(profiler, name, Set(TemplataLookupContext))
    vassert(results.size == 1)
    results.head
  }

  override def resolveExactSignature(env: SimpleEnvironment, state: FakeState, range: RangeS, name: String, coords: List[Coord]): Prototype2 = {
    val templatas = env.getAllTemplatasWithName(new NullProfiler(), GlobalFunctionFamilyNameA(name), Set(TemplataLookupContext))
    val prototypes = templatas.collect({ case PrototypeTemplata(prot) => prot })
    val matchingPrototypes = prototypes.filter(_.paramTypes == coords)
    vassert(matchingPrototypes.size == 1)
    matchingPrototypes.head
  }
}

class FakeTemplataTemplarInnerDelegate extends ITemplataTemplarInnerDelegate[SimpleEnvironment, FakeState] {
  override def evaluateInterfaceTemplata(state: FakeState, callRange: RangeS, templata: InterfaceTemplata, templateArgs: List[ITemplata]): (Kind) = {
    vfail()
  }
  override def evaluateStructTemplata(state: FakeState, callRange: RangeS, templata: StructTemplata, templateArgs: List[ITemplata]): (Kind) = {
    vfail()
  }
  override def getAncestorInterfaceDistance(state: FakeState, descendantCitizenRef: CitizenRef2, ancestorInterfaceRef: InterfaceRef2): (Option[Int]) = {
    vfail()
  }
  override def getMutability(state: FakeState, kind: Kind): Mutability = {
    InfererTestUtils.getMutability(kind)
  }
//  override def getPackKind(env: SimpleEnvironment, state: FakeState, types2: List[Coord]): (PackT2, Mutability) = {
//    vfail()
//  }
  override def lookupTemplata(env: SimpleEnvironment, name: IName2): ITemplata = {
    vassertSome(env.getNearestTemplataWithAbsoluteName2(name, Set(TemplataLookupContext)))
  }

  override def getArraySequenceKind(env: SimpleEnvironment, state: FakeState, mutability: Mutability, size: Int, element: Coord): (KnownSizeArrayT2) = {
    vfail()
  }
  override def getTupleKind(env: SimpleEnvironment, state: FakeState, elements: List[Coord]): TupleT2 = {
    vfail()
  }
  override def getInterfaceTemplataType(it: InterfaceTemplata): TemplateTemplataType = {
    vfail()
  }

  override def getStructTemplataType(st: StructTemplata): TemplateTemplataType = {
    vfail()
  }

  override def lookupTemplataImprecise(env: SimpleEnvironment, name: IImpreciseNameStepA): ITemplata = {
    vassertSome(env.getNearestTemplataWithName(name, Set(TemplataLookupContext)))
  }
}

class InfererTests extends FunSuite with Matchers {
  val incrementPrototype =
    Prototype2(FullName2(List(), FunctionName2("increment", List(), List(Coord(Share, Int2())))), Coord(Share, Int2()))

  def makeCannedEnvironment(): SimpleEnvironment = {
    var entries: TemplatasStore = TemplatasStore(Map(), Map())
    val voidName = PrimitiveName2("void")
    entries = entries.addEntry(true, voidName, TemplataEnvEntry(KindTemplata(Void2())))
    val intName = PrimitiveName2("int")
    entries = entries.addEntry(true, intName, TemplataEnvEntry(KindTemplata(Int2())))
    val boolName = PrimitiveName2("bool")
    entries = entries.addEntry(true, boolName, TemplataEnvEntry(KindTemplata(Bool2())))
    entries = entries.addEntry(true,
      CitizenName2("ImmInterface", List()),
        InterfaceEnvEntry(
          InterfaceA(
            RangeS.internal(-70),
            TopLevelCitizenDeclarationNameA("ImmInterface", CodeLocationS.internal(-24)),
            List(),
            false,
            CodeRuneA("M"),
            Some(ImmutableP),
            KindTemplataType,
            Set(CodeRuneA("M")),
            List(),
            Set(CodeRuneA("M")),
            Map(CodeRuneA("M") -> MutabilityTemplataType),
            List(EqualsAR(RangeS.testZero,TemplexAR(RuneAT(RangeS.testZero,CodeRuneA("M"), MutabilityTemplataType)), TemplexAR(MutabilityAT(RangeS.testZero,ImmutableP)))),
            List())))
    entries = entries.addEntry(true,
      CitizenName2("ImmStruct", List()),
        StructEnvEntry(
          StructA(
            RangeS.internal(-71),
            TopLevelCitizenDeclarationNameA("ImmStruct", CodeLocationS.internal(-24)),
            List(),
            false,
            CodeRuneA("M"),
            Some(ImmutableP),
            KindTemplataType,
            Set(CodeRuneA("M")),
            List(),
            Set(CodeRuneA("M"), CodeRuneA("I"), CodeRuneA("B")),
            Map(CodeRuneA("M") -> MutabilityTemplataType, CodeRuneA("I") -> CoordTemplataType, CodeRuneA("B") -> CoordTemplataType),
            List(
              EqualsAR(RangeS.testZero,TemplexAR(RuneAT(RangeS.testZero,CodeRuneA("M"), MutabilityTemplataType)), TemplexAR(MutabilityAT(RangeS.testZero,ImmutableP))),
              EqualsAR(RangeS.testZero,TemplexAR(RuneAT(RangeS.testZero,CodeRuneA("I"), CoordTemplataType)), TemplexAR(NameAT(RangeS.testZero,CodeTypeNameA("int"), CoordTemplataType))),
              EqualsAR(RangeS.testZero,TemplexAR(RuneAT(RangeS.testZero,CodeRuneA("B"), CoordTemplataType)), TemplexAR(NameAT(RangeS.testZero,CodeTypeNameA("bool"), CoordTemplataType)))),
            List(
              StructMemberA(RangeS.testZero,"i", FinalP, CodeRuneA("I")),
              StructMemberA(RangeS.testZero,"i", FinalP, CodeRuneA("B"))))))
    entries = entries.addEntry(true, PrimitiveName2("Array"), TemplataEnvEntry(ArrayTemplateTemplata()))
    entries = entries.addEntry(true,
        CitizenTemplateName2("MutTStruct", CodeLocation2(-25, 0)),
          StructEnvEntry(
            StructA(
              RangeS.internal(-74),
              TopLevelCitizenDeclarationNameA("MutTStruct", CodeLocationS.internal(-26)),
              List(),
              false,
              CodeRuneA("M"),
              Some(MutableP),
              TemplateTemplataType(List(CoordTemplataType), KindTemplataType),
              Set(CodeRuneA("M")),
              List(CodeRuneA("T")),
              Set(CodeRuneA("T"), CodeRuneA("M")),
              Map(CodeRuneA("T") -> CoordTemplataType, CodeRuneA("M") -> MutabilityTemplataType),
              List(EqualsAR(RangeS.testZero,TemplexAR(RuneAT(RangeS.testZero,CodeRuneA("M"), MutabilityTemplataType)), TemplexAR(MutabilityAT(RangeS.testZero,MutableP)))),
              List())))
    entries = entries.addEntry(true, CitizenTemplateName2("MutTInterface", CodeLocation2(-27, 0)),
      InterfaceEnvEntry(
        InterfaceA(
          RangeS.internal(-75),
          TopLevelCitizenDeclarationNameA("MutTInterface", CodeLocationS.internal(-28)),
          List(),
          false,
          CodeRuneA("M"),
          Some(MutableP),
          TemplateTemplataType(List(CoordTemplataType), KindTemplataType),
          Set(CodeRuneA("M")),
          List(CodeRuneA("T")),
          Set(CodeRuneA("T"), CodeRuneA("M")),
          Map(CodeRuneA("T") -> CoordTemplataType, CodeRuneA("M") -> MutabilityTemplataType),
          List(EqualsAR(RangeS.testZero,TemplexAR(RuneAT(RangeS.testZero,CodeRuneA("M"), MutabilityTemplataType)), TemplexAR(MutabilityAT(RangeS.testZero,MutableP)))),
          List())))
    entries = entries.addEntry(true, CitizenTemplateName2("MutStruct", CodeLocation2(-29, 0)),
      StructEnvEntry(
        StructA(
          RangeS.internal(-73),
          TopLevelCitizenDeclarationNameA("MutStruct", CodeLocationS.internal(-30)),
          List(),
          false,
          CodeRuneA("M"),
          Some(MutableP),
          KindTemplataType,
          Set(CodeRuneA("M")),
          List(),
          Set(CodeRuneA("M")),
          Map(CodeRuneA("M") -> MutabilityTemplataType),
          List(EqualsAR(RangeS.testZero,TemplexAR(RuneAT(RangeS.testZero,CodeRuneA("M"), MutabilityTemplataType)), TemplexAR(MutabilityAT(RangeS.testZero,MutableP)))),
          List())))
    entries = entries.addEntry(true, CitizenTemplateName2("MutInterface", CodeLocation2(-31, 0)),
      InterfaceEnvEntry(
        InterfaceA(
          RangeS.internal(-72),
          TopLevelCitizenDeclarationNameA("MutInterface", CodeLocationS.internal(-32)),
          List(),
          false,
          CodeRuneA("M"),
          Some(MutableP),
          KindTemplataType,
          Set(CodeRuneA("M")),
          List(),
          Set(CodeRuneA("M")),
          Map(CodeRuneA("M") -> MutabilityTemplataType),
          List(EqualsAR(RangeS.testZero,TemplexAR(RuneAT(RangeS.testZero,CodeRuneA("M"), MutabilityTemplataType)), TemplexAR(MutabilityAT(RangeS.testZero,MutableP)))),
          List())))
    val mutStructBorrowName = CitizenName2("MutStructBorrow", List())
    entries = entries.addEntry(true, mutStructBorrowName,
      TemplataEnvEntry(CoordTemplata(Coord(Borrow, StructRef2(FullName2(List(), CitizenName2("MutStruct", List())))))))
    val mutStructWeakName = CitizenName2("MutStructWeak", List())
    entries = entries.addEntry(true, mutStructWeakName,
      TemplataEnvEntry(CoordTemplata(Coord(Weak, StructRef2(FullName2(List(), CitizenName2("MutStruct", List())))))))
    val mutArraySequenceOf4IntName = CitizenName2("MutArraySequenceOf4Int", List())
    entries = entries.addEntry(true, mutArraySequenceOf4IntName,
      TemplataEnvEntry(KindTemplata(KnownSizeArrayT2(4, RawArrayT2(Coord(Share, Int2()), Mutable)))))
    val intAndBoolTupName = CitizenName2("IntAndBoolTupName", List()) // Tuples are normally addressed by TupleNameT, but that's a detail this test doesn't need to care about.
    entries = entries.addEntry(true, intAndBoolTupName,
      TemplataEnvEntry(
        KindTemplata(
          TupleT2(
            List(Program2.intType, Program2.boolType),
            // Normally this would be backed by a struct simply named "Tup"
            StructRef2(FullName2(List(), CitizenName2("ImmStruct", List())))))))
    val callPrototype = PrototypeTemplata(incrementPrototype)
    entries = entries.addEntry(true, callPrototype.value.fullName.last, TemplataEnvEntry(callPrototype))
    SimpleEnvironment(entries)
  }

  // Makes an evaluator with some canned data
  def makeCannedEvaluator(): InfererEvaluator[SimpleEnvironment, FakeState] = {
    val templataTemplarDelegate =
      new FakeTemplataTemplarInnerDelegate() {
        override def getMutability(state: FakeState, kind: Kind): Mutability = {
          kind match {
            case StructRef2(FullName2(_, CitizenName2(humanName, _))) if humanName.startsWith("Mut") => Mutable
            case StructRef2(FullName2(_, CitizenName2(humanName, _))) if humanName.startsWith("Imm") => Immutable
            case InterfaceRef2(FullName2(_, CitizenName2(humanName, _))) if humanName.startsWith("Mut") => Mutable
            case InterfaceRef2(FullName2(_, CitizenName2(humanName, _))) if humanName.startsWith("Imm") => Immutable
            case Int2() | Void2() | Bool2() => Immutable
            case KnownSizeArrayT2(_, RawArrayT2(_, mutability)) => mutability
            case UnknownSizeArrayT2(RawArrayT2(_, mutability)) => mutability
            case TupleT2(_, StructRef2(FullName2(_, CitizenName2(humanName, _)))) if humanName.startsWith("Imm") => Immutable
            case _ => vfail()
          }
        }
        override def evaluateInterfaceTemplata(state: FakeState, callRange: RangeS, templata: InterfaceTemplata, templateArgs: List[ITemplata]): (Kind) = {
          (templata, templateArgs) match {
            case (InterfaceTemplata(_,interfaceName(TopLevelCitizenDeclarationNameA("MutTInterface", _))), List(CoordTemplata(Coord(Share, Int2())) )) => {
              InterfaceRef2(FullName2(List(), CitizenName2("MutTInterface", List(CoordTemplata(Coord(Share, Int2()))))))
            }
            case (InterfaceTemplata(_,interfaceName(TopLevelCitizenDeclarationNameA("MutInterface", _))), List()) => {
              InterfaceRef2(FullName2(List(), CitizenName2("MutInterface", List())))
            }
            case (InterfaceTemplata(_,interfaceName(TopLevelCitizenDeclarationNameA("ImmInterface", _))), List()) => {
              InterfaceRef2(FullName2(List(), CitizenName2("ImmInterface", List())))
            }
          }
        }

        override def evaluateStructTemplata(state: FakeState, callRange: RangeS, templata: StructTemplata, templateArgs: List[ITemplata]): (Kind) = {
          (templata, templateArgs) match {
            case (StructTemplata(_,structName(TopLevelCitizenDeclarationNameA("MutTStruct", _))), List(CoordTemplata(Coord(Share, Int2())) )) => {
              StructRef2(FullName2(List(), CitizenName2("MutTStruct", List(CoordTemplata(Coord(Share, Int2()))))))
            }
            case (StructTemplata(_,structName(TopLevelCitizenDeclarationNameA("MutStruct", _))), List()) => {
              StructRef2(FullName2(List(), CitizenName2("MutStruct", List())))
            }
          }
        }
        override def getInterfaceTemplataType(it: InterfaceTemplata): TemplateTemplataType = {
          it match {
            case InterfaceTemplata(_,interfaceName(TopLevelCitizenDeclarationNameA("MutTInterface", _))) => {
              TemplateTemplataType(List(CoordTemplataType), KindTemplataType)
            }
            case InterfaceTemplata(_, interfaceName(TopLevelCitizenDeclarationNameA("MutInterface", _))) => vfail()
          }
        }
        override def getStructTemplataType(it: StructTemplata): TemplateTemplataType = {
          it match {
            case StructTemplata(_,structName(TopLevelCitizenDeclarationNameA("MutTStruct", _))) => TemplateTemplataType(List(CoordTemplataType), KindTemplataType)
          }
        }
        override def getArraySequenceKind(env: SimpleEnvironment, state: FakeState, mutability: Mutability, size: Int, element: Coord): (KnownSizeArrayT2) = {
          (KnownSizeArrayT2(size, RawArrayT2(element, mutability)))
        }

        override def getTupleKind(env: SimpleEnvironment, state: FakeState, elements: List[Coord]): TupleT2 = {
          // Theres only one tuple in this test, and its backed by the ImmStruct.
          TupleT2(elements, StructRef2(FullName2(List(), CitizenName2("ImmStruct", List()))))
        }
      }
    val delegate =
      new FakeInfererEvaluatorDelegate() {
        override def getAncestorInterfaces(state: FakeState, descendantCitizenRef: CitizenRef2): (Set[InterfaceRef2]) = {
          descendantCitizenRef match {
            case StructRef2(FullName2(List(), CitizenName2("MutTStruct",List(CoordTemplata(Coord(Share, Int2())))))) => Set(InterfaceRef2(FullName2(List(), CitizenName2("MutTInterface", List(CoordTemplata(Coord(Share, Int2())))))))
            case StructRef2(FullName2(List(), CitizenName2("MutStruct",List()))) => Set(InterfaceRef2(FullName2(List(), CitizenName2("MutInterface", List()))))
            case InterfaceRef2(FullName2(List(), CitizenName2("MutInterface",List()))) => Set()
            case StructRef2(FullName2(List(), CitizenName2("MutSoloStruct",List()))) => Set()
            case _ => vfail(descendantCitizenRef.toString)
          }
        }
      }

    makeEvaluator(Some(templataTemplarDelegate), Some(delegate))
  }

  def makeEvaluator(
    maybeTemplataTemplarDelegate: Option[FakeTemplataTemplarInnerDelegate],
    maybeEvaluatorDelegate: Option[FakeInfererEvaluatorDelegate]):
  InfererEvaluator[SimpleEnvironment, FakeState] = {
    val templataTemplar =
      new TemplataTemplarInner[SimpleEnvironment, FakeState](
        maybeTemplataTemplarDelegate match {
          case None => new FakeTemplataTemplarInnerDelegate()
          case Some(t) => t
        })

    val equalsLayer =
      new InfererEquator[SimpleEnvironment, FakeState](
        templataTemplar)
    val inferEvaluatorDelegate =
      maybeEvaluatorDelegate match {
        case Some(e) => e
        case None => new FakeInfererEvaluatorDelegate()
      }
    val evaluator =
      new InfererEvaluator[SimpleEnvironment, FakeState](
        new NullProfiler(),
        templataTemplar,
        equalsLayer,
        inferEvaluatorDelegate)
    evaluator
  }

  test("Borrow becomes share if kind is immutable") {
    val (InferSolveSuccess(inferences)) =
      makeCannedEvaluator()
        .solve(
          makeCannedEnvironment(),
          FakeState(),
          List(
            TemplexTR(RuneTT(RangeS.testZero,CodeRune2("__C"), CoordTemplataType)),
            EqualsTR(RangeS.testZero,
              TemplexTR(RuneTT(RangeS.testZero,CodeRune2("__C"), CoordTemplataType)),
              TemplexTR(OwnershippedTT(RangeS.testZero,BorrowP,NameTT(RangeS.testZero,CodeTypeNameA("ImmInterface"), CoordTemplataType))))),
          RangeS.testZero,
          Map(CodeRune2("__C") -> CoordTemplataType),
          Set(CodeRune2("__C")),
          Map(),
          List(),
          None,
          true)

    vassert(
      inferences.templatasByRune(CodeRune2("__C")) ==
        CoordTemplata(Coord(Share, InterfaceRef2(FullName2(List(), CitizenName2("ImmInterface", List()))))))
  }

  test("Can infer coord rune from an incoming kind") {
    val (isf @ InferSolveFailure(_, _, _,_,_, _, _)) =
      makeCannedEvaluator()
        .solve(
          makeCannedEnvironment(),
          FakeState(),
          List(TemplexTR(RuneTT(RangeS.testZero,CodeRune2("C"), CoordTemplataType))),
          RangeS.testZero,
          Map(CodeRune2("C") -> CoordTemplataType),
          Set(CodeRune2("C")),
          Map(CodeRune2("C") -> KindTemplata(InterfaceRef2(FullName2(List(), CitizenName2("ImmInterface",List(KindTemplata(Int2()))))))),
          List(),
          None,
          true)

    vassert(isf.toString.contains("doesn't match expected type"))
  }

  test("Detects conflict between types") {
    val (isf @ InferSolveFailure(_, _, _,_,_, _, _)) =
      makeCannedEvaluator()
        .solve(
          makeCannedEnvironment(),
          FakeState(),
          List(EqualsTR(RangeS.testZero,TemplexTR(RuneTT(RangeS.testZero,CodeRune2("C"), CoordTemplataType)), TemplexTR(RuneTT(RangeS.testZero,CodeRune2("A"), KindTemplataType)))),
          RangeS.testZero,
          Map(CodeRune2("A") -> KindTemplataType),
          Set(CodeRune2("A"), CodeRune2("C")),
          Map(CodeRune2("A") -> KindTemplata(Int2())),
          List(),
          None,
          true)

    vassert(isf.toString.contains("Doesn't match type!"))
  }

  test("Can explicitly coerce from kind to coord") {
    val (InferSolveSuccess(conclusions)) =
      makeCannedEvaluator()
        .solve(
          makeCannedEnvironment(),
          FakeState(),
          List(
            EqualsTR(RangeS.testZero,
              TemplexTR(RuneTT(RangeS.testZero,CodeRune2("C"), CoordTemplataType)),
              CallTR(RangeS.testZero,"toRef", List(TemplexTR(RuneTT(RangeS.testZero,CodeRune2("A"), KindTemplataType))), CoordTemplataType))),
          RangeS.testZero,
          Map(CodeRune2("C") -> CoordTemplataType, CodeRune2("A") -> KindTemplataType),
          Set(CodeRune2("C"), CodeRune2("A")),
          Map(CodeRune2("A") -> KindTemplata(Int2())),
          List(),
          None,
          true)

    conclusions.templatasByRune(CodeRune2("C")) shouldEqual CoordTemplata(Coord(Share, Int2()))
  }

  test("Can explicitly coerce from kind to coord 2") {
    val (InferSolveSuccess(conclusions)) =
      makeCannedEvaluator()
        .solve(
          makeCannedEnvironment(),
          FakeState(),
          List(
            TemplexTR(RuneTT(RangeS.testZero,CodeRune2("Z"), CoordTemplataType)),
            EqualsTR(RangeS.testZero,TemplexTR(RuneTT(RangeS.testZero,CodeRune2("Z"), CoordTemplataType)),TemplexTR(NameTT(RangeS.testZero,CodeTypeNameA("int"), CoordTemplataType)))),
          RangeS.testZero,
          Map(CodeRune2("Z") -> CoordTemplataType),
          Set(CodeRune2("Z")),
          Map(),
          List(),
          None,
          true)

    conclusions.templatasByRune(CodeRune2("Z")) shouldEqual CoordTemplata(Coord(Share, Int2()))
  }

  test("Can match KindTemplataType against StructEnvEntry / StructTemplata") {
    val (InferSolveSuccess(conclusions)) =
      makeCannedEvaluator()
        .solve(
          makeCannedEnvironment(),
          FakeState(),
          List(
            EqualsTR(RangeS.testZero,
              TemplexTR(RuneTT(RangeS.testZero,CodeRune2("__RetRune"), CoordTemplataType)),
              CallTR(RangeS.testZero,
                "toRef",
                List(TemplexTR(NameTT(RangeS.testZero,CodeTypeNameA("MutStruct"), KindTemplataType))),
                CoordTemplataType))),
          RangeS.testZero,
          Map(CodeRune2("__RetRune") -> CoordTemplataType),
          Set(CodeRune2("__RetRune")),
          Map(),
          List(),
          None,
          true)

    conclusions.templatasByRune(CodeRune2("__RetRune")) shouldEqual
      CoordTemplata(Coord(Own, StructRef2(FullName2(List(), CitizenName2("MutStruct", List())))))
  }

  test("Can infer from simple rules") {
    val (InferSolveSuccess(inferences)) =
      makeCannedEvaluator()
        .solve(
          makeCannedEnvironment(),
          FakeState(),
          List(
            TemplexTR(RuneTT(RangeS.testZero,CodeRune2("Z"), CoordTemplataType)),
            EqualsTR(RangeS.testZero,TemplexTR(RuneTT(RangeS.testZero,CodeRune2("Z"), CoordTemplataType)),CallTR(RangeS.testZero,"toRef", List(TemplexTR(NameTT(RangeS.testZero,CodeTypeNameA("int"), KindTemplataType))), CoordTemplataType))),
          RangeS.testZero,
          Map(CodeRune2("Z") -> CoordTemplataType),
          Set(CodeRune2("Z")),
          Map(),
          List(),
          None,
          true)

    vassert(inferences.templatasByRune(CodeRune2("Z")) == CoordTemplata(Coord(Share, Int2())))
  }

  test("Can infer templata from CallAT") {
    val (InferSolveSuccess(inferences)) =
      makeCannedEvaluator()
        .solve(
          makeCannedEnvironment(),
          FakeState(),
          List(
            EqualsTR(RangeS.testZero,
              TemplexTR(RuneTT(RangeS.testZero,CodeRune2("X"), KindTemplataType)),
              TemplexTR(CallTT(RangeS.testZero,NameTT(RangeS.testZero,CodeTypeNameA("MutTInterface"), TemplateTemplataType(List(CoordTemplataType), KindTemplataType)),List(RuneTT(RangeS.testZero,CodeRune2("T"), CoordTemplataType)), KindTemplataType)))),
          RangeS.testZero,
          Map(CodeRune2("X") -> KindTemplataType, CodeRune2("T") -> CoordTemplataType),
          Set(CodeRune2("X"), CodeRune2("T")),
          Map(CodeRune2("X") -> KindTemplata(InterfaceRef2(FullName2(List(), CitizenName2("MutTInterface",List(CoordTemplata(Coord(Share, Int2())))))))),
          List(),
          None,
          true)

    vassert(inferences.templatasByRune(CodeRune2("T")) == CoordTemplata(Coord(Share, Int2())))
  }

  test("Can conjure an owning coord from a borrow coord") {
    val (InferSolveSuccess(inferences)) =
      makeCannedEvaluator()
        .solve(
          makeCannedEnvironment(),
          FakeState(),
          List(
            TemplexTR(RuneTT(RangeS.testZero,CodeRune2("T"), CoordTemplataType)),
            TemplexTR(RuneTT(RangeS.testZero,CodeRune2("1337"), KindTemplataType)),
            EqualsTR(RangeS.testZero,
              TemplexTR(RuneTT(RangeS.testZero,CodeRune2("T"), CoordTemplataType)),
              ComponentsTR(
                RangeS.internal(-100),
                CoordTemplataType,
                List(TemplexTR(OwnershipTT(RangeS.testZero,OwnP)), TemplexTR(RuneTT(RangeS.testZero,CodeRune2("1337"), KindTemplataType))))),
            TemplexTR(RuneTT(RangeS.testZero,CodeRune2("0"), CoordTemplataType)),
            EqualsTR(RangeS.testZero,
              TemplexTR(RuneTT(RangeS.testZero,CodeRune2("0"), CoordTemplataType)),
              ComponentsTR(
                RangeS.internal(-101),
                CoordTemplataType,
                List(TemplexTR(OwnershipTT(RangeS.testZero,BorrowP)), TemplexTR(RuneTT(RangeS.testZero,CodeRune2("1337"), KindTemplataType)))))),
          RangeS.testZero,
          Map(
            CodeRune2("1337") -> KindTemplataType,
            CodeRune2("0") -> CoordTemplataType,
            CodeRune2("YT") -> CoordTemplataType),
          Set(CodeRune2("1337"), CodeRune2("0"), CodeRune2("T")),
          Map(),
          List(AtomAP(RangeS.testZero,CaptureA(CodeVarNameA("m"),FinalP),None,CodeRuneA("0"),None)),
          Some(List(ParamFilter(Coord(Borrow,InterfaceRef2(FullName2(List(), CitizenName2("MutInterface", List())))),None))),
          true)

    vassert(inferences.templatasByRune(CodeRune2("T")) == CoordTemplata(Coord(Own,InterfaceRef2(FullName2(List(), CitizenName2("MutInterface", List()))))))
  }

  test("Rune 0 upcasts to right type, simple") {
    val (InferSolveSuccess(inferences)) =
      makeCannedEvaluator()
        .solve(
          makeCannedEnvironment(),
          FakeState(),
          List(
            TemplexTR(RuneTT(RangeS.testZero,CodeRune2("__Let0_"), CoordTemplataType)),
            EqualsTR(RangeS.testZero,
              TemplexTR(RuneTT(RangeS.testZero,CodeRune2("__Let0_"), CoordTemplataType)),
              CallTR(RangeS.testZero,"toRef", List(TemplexTR(NameTT(RangeS.testZero,CodeTypeNameA("MutInterface"), KindTemplataType))), CoordTemplataType))),
          RangeS.testZero,
          Map(CodeRune2("__Let0_") -> CoordTemplataType),
          Set(CodeRune2("__Let0_")),
          Map(),
          List(AtomAP(RangeS.testZero,CaptureA(CodeVarNameA("x"),FinalP),None,CodeRuneA("__Let0_"),None)),
          Some(List(ParamFilter(Coord(Own,StructRef2(FullName2(List(), CitizenName2("MutStruct",List())))),None))),
          true)

    vassert(inferences.templatasByRune(CodeRune2("__Let0_")) == CoordTemplata(Coord(Own, InterfaceRef2(FullName2(List(), CitizenName2("MutInterface", List()))))))
  }

  test("Rune 0 upcasts to right type templated") {
    val (InferSolveSuccess(inferences)) =
      makeCannedEvaluator()
        .solve(
          makeCannedEnvironment(),
          FakeState(),
          List(
            TemplexTR(RuneTT(RangeS.testZero,CodeRune2("__Let0_"), CoordTemplataType)),
            EqualsTR(RangeS.testZero,
              TemplexTR(RuneTT(RangeS.testZero,CodeRune2("__Let0_"), CoordTemplataType)),
              CallTR(RangeS.testZero,
                "toRef",
                List(
                  TemplexTR(
                    CallTT(RangeS.testZero,
                      NameTT(RangeS.testZero,CodeTypeNameA("MutTInterface"), TemplateTemplataType(List(CoordTemplataType), KindTemplataType)),
                      List(RuneTT(RangeS.testZero,CodeRune2("T"), CoordTemplataType)),
                      KindTemplataType))),
                CoordTemplataType))),
          RangeS.testZero,
          Map(CodeRune2("__Let0_") -> CoordTemplataType, CodeRune2("T") -> CoordTemplataType),
          Set(CodeRune2("__Let0_"), CodeRune2("T")),
          Map(),
          List(AtomAP(RangeS.testZero,CaptureA(CodeVarNameA("x"),FinalP),None,CodeRuneA("__Let0_"),None)),
          Some(List(ParamFilter(Coord(Own,StructRef2(FullName2(List(), CitizenName2("MutTStruct",List(CoordTemplata(Coord(Share, Int2()))))))),None))),
          true)

    vassert(
      inferences.templatasByRune(CodeRune2("__Let0_")) ==
        CoordTemplata(Coord(Own, InterfaceRef2(FullName2(List(), CitizenName2("MutTInterface", List(CoordTemplata(Coord(Share, Int2())))))))))
    vassert(
      inferences.templatasByRune(CodeRune2("T")) ==
        CoordTemplata(Coord(Share, Int2())))
  }

  test("Tests destructor") {
    // Tests that we can make a rule that will only match structs, arrays, packs, sequences.
    // It doesn't have to be in this form, but we do need the capability in some way, so that
    // we can have a templated destructor that matches any of those.

    val rules =
      List(
        EqualsTR(RangeS.testZero,
          TemplexTR(RuneTT(RangeS.testZero,CodeRune2("T"), CoordTemplataType)),
          ComponentsTR(
            RangeS.internal(-102),
            CoordTemplataType,
            List(
              OrTR(RangeS.testZero,List(TemplexTR(OwnershipTT(RangeS.testZero,OwnP)), TemplexTR(OwnershipTT(RangeS.testZero,ShareP)))),
              CallTR(RangeS.testZero,"passThroughIfConcrete",List(TemplexTR(RuneTT(RangeS.testZero,CodeRune2("Z"), KindTemplataType))), KindTemplataType)))),
        EqualsTR(RangeS.testZero,TemplexTR(RuneTT(RangeS.testZero,CodeRune2("V"), CoordTemplataType)),CallTR(RangeS.testZero,"toRef",List(TemplexTR(NameTT(RangeS.testZero,CodeTypeNameA("void"),KindTemplataType))), CoordTemplataType)))
    val atoms =
      List(AtomAP(RangeS.testZero,CaptureA(CodeVarNameA("this"),FinalP),None,CodeRuneA("T"),None))

    val solve =
      (paramFilter: ParamFilter) => {
        makeCannedEvaluator().solve(
          makeCannedEnvironment(),
          FakeState(),
          rules,
          RangeS.testZero,
          Map(CodeRune2("V") -> CoordTemplataType, CodeRune2("T") -> CoordTemplataType),
          Set(CodeRune2("V"), CodeRune2("T"), CodeRune2("Z")),
          Map(),
          atoms,
          Some(List(paramFilter)),
          true)
      }

    // Test that it does match a pack
    val packCoord = Coord(Share,PackT2(List(),StructRef2(FullName2(List(), CitizenName2("__Pack",List())))))
    val (InferSolveSuccess(inferencesA)) = solve(ParamFilter(packCoord,None))
    vassert(inferencesA.templatasByRune(CodeRune2("T")) == CoordTemplata(packCoord))

    // Test that it does match a struct
    val structCoord = Coord(Own,StructRef2(FullName2(List(), CitizenName2("MutStruct",List()))))
    val (InferSolveSuccess(inferencesD)) = solve(ParamFilter(structCoord,None))
    vassert(inferencesD.templatasByRune(CodeRune2("T")) == CoordTemplata(structCoord))

    // Test that it doesn't match an int
    val intCoord = Coord(Share,Int2())
    val (isfE @ InferSolveFailure(_, _,_,_, _, _, _)) = solve(ParamFilter(intCoord,None))
    vassert(isfE.toString.contains("Bad arguments to passThroughIfConcrete"))

    // Test that it doesn't match an interface
    val interfaceCoord = Coord(Own,InterfaceRef2(FullName2(List(), CitizenName2("MutInterface",List()))))
    val (isfF @ InferSolveFailure(_, _, _,_,_, _, _)) = solve(ParamFilter(interfaceCoord,None))
    vassert(isfF.toString.contains("Bad arguments to passThroughIfConcrete"))
  }

  test("Tests passThroughIfInterface") {
    // Tests that we can make a rule that will only match interfaces.
    // It doesn't have to be in this form, but we do need the capability in some way, so that
    // we can have a templated destructor that matches any of those.

    val rules =
      List(
        EqualsTR(RangeS.testZero,
          TemplexTR(RuneTT(RangeS.testZero,CodeRune2("T"), CoordTemplataType)),
          ComponentsTR(
            RangeS.internal(-103),
            CoordTemplataType,
            List(
              OrTR(RangeS.testZero,List(TemplexTR(OwnershipTT(RangeS.testZero,OwnP)), TemplexTR(OwnershipTT(RangeS.testZero,ShareP)))),
              CallTR(RangeS.testZero,"passThroughIfInterface",List(TemplexTR(RuneTT(RangeS.testZero,CodeRune2("Z"), KindTemplataType))), KindTemplataType)))),
        EqualsTR(RangeS.testZero,
          TemplexTR(RuneTT(RangeS.testZero,CodeRune2("V"), CoordTemplataType)),
          CallTR(RangeS.testZero,"toRef",List(TemplexTR(NameTT(RangeS.testZero,CodeTypeNameA("void"), KindTemplataType))), CoordTemplataType)))
    val atoms =
      List(AtomAP(RangeS.testZero,CaptureA(CodeVarNameA("this"),FinalP),None,CodeRuneA("T"),None))

    val solve =
      (paramFilter: ParamFilter) => {
        makeCannedEvaluator().solve(
          makeCannedEnvironment(),
          FakeState(),
          rules,
          RangeS.testZero,
          Map(CodeRune2("T") -> CoordTemplataType, CodeRune2("V") -> CoordTemplataType),
          Set(CodeRune2("T"), CodeRune2("V"), CodeRune2("Z")),
          Map(),
          atoms,
          Some(List(paramFilter)),
          true)
      }

    // Test that it does match an interface
    val interfaceCoord = Coord(Own,InterfaceRef2(FullName2(List(), CitizenName2("MutInterface",List()))))
    val (InferSolveSuccess(inferencesD)) = solve(ParamFilter(interfaceCoord,None))
    vassert(inferencesD.templatasByRune(CodeRune2("T")) == CoordTemplata(interfaceCoord))

    // Test that it doesn't match an int
    val intCoord = Coord(Share,Int2())
    val (isfE @ InferSolveFailure(_, _, _, _,_,_, _)) = solve(ParamFilter(intCoord,None))
    vassert(isfE.toString.contains("Bad arguments to passThroughIfInterface"))

    // TODO: make a more accurate test that tests a struct doesn't match. Tried doing
    // it like the int, but since its handed in as a parameter, it just upcasted! LOL
  }


  test("Tests passThroughIfStruct") {
    // Tests that we can make a rule that will only match structs.
    // It doesn't have to be in this form, but we do need the capability in some way, so that
    // we can have a templated destructor that matches any of those.

    val rules =
      List(
        EqualsTR(RangeS.testZero,
          TemplexTR(RuneTT(RangeS.testZero,CodeRune2("T"), CoordTemplataType)),
          ComponentsTR(
            RangeS.internal(-107),
            CoordTemplataType,
            List(
              OrTR(RangeS.testZero,List(TemplexTR(OwnershipTT(RangeS.testZero,OwnP)), TemplexTR(OwnershipTT(RangeS.testZero,ShareP)))),
              CallTR(RangeS.testZero,"passThroughIfStruct",List(TemplexTR(RuneTT(RangeS.testZero,CodeRune2("Z"), KindTemplataType))), KindTemplataType)))))
    val atoms =
      List(AtomAP(RangeS.testZero,CaptureA(CodeVarNameA("this"), FinalP),None,CodeRuneA("T"),None))

    val solve =
      (paramFilter: ParamFilter) => {
        makeCannedEvaluator().solve(
          makeCannedEnvironment(),
          FakeState(),
          rules,
          RangeS.testZero,
          Map(CodeRune2("T") -> CoordTemplataType),
          Set(CodeRune2("T"), CodeRune2("Z")),
          Map(),
          atoms,
          Some(List(paramFilter)),
          true)
      }

    // Test that it does match a struct
    val structCoord = Coord(Own,StructRef2(FullName2(List(), CitizenName2("MutStruct",List()))))
    val (InferSolveSuccess(inferencesD)) = solve(ParamFilter(structCoord,None))
    vassert(inferencesD.templatasByRune(CodeRune2("T")) == CoordTemplata(structCoord))

    // Test that it doesn't match an int
    val intCoord = Coord(Share,Int2())
    val (isfE @ InferSolveFailure(_, _, _,_,_, _, _)) = solve(ParamFilter(intCoord,None))
    vassert(isfE.toString.contains("Bad arguments to passThroughIfStruct"))

    // Test that it doesn't match an interface
    val interfaceCoord = Coord(Own,InterfaceRef2(FullName2(List(), CitizenName2("MutInterface",List()))))
    val (isfF @ InferSolveFailure(_, _, _,_,_, _, _)) = solve(ParamFilter(interfaceCoord,None))
    vassert(isfF.toString.contains("Bad arguments to passThroughIfStruct"))

    // Test that it doesn't match an pack
    val packCoord = Coord(Share,PackT2(List(),StructRef2(FullName2(List(), CitizenName2("__Pack",List())))))
    val (isfG @ InferSolveFailure(_, _, _,_,_, _, _)) = solve(ParamFilter(packCoord,None))
    vassert(isfG.toString.contains("Bad arguments to passThroughIfStruct"))
  }

  test("Test coercing template call result") {
    // Tests that we can make a rule that will only match structs, arrays, packs, sequences.
    // It doesn't have to be in this form, but we do need the capability in some way, so that
    // we can have a templated destructor that matches any of those.

    val rules =
      List(
        TemplexTR(RuneTT(RangeS.testZero,CodeRune2("Z"), CoordTemplataType)),
        EqualsTR(RangeS.testZero,
          TemplexTR(RuneTT(RangeS.testZero,CodeRune2("Z"), CoordTemplataType)),
          TemplexTR(
            CallTT(RangeS.testZero,
              NameTT(RangeS.testZero,CodeTypeNameA("MutTStruct"), TemplateTemplataType(List(CoordTemplataType), KindTemplataType)),
              List(NameTT(RangeS.testZero,CodeTypeNameA("int"), CoordTemplataType)),
              CoordTemplataType))))
    val atoms =
      List(AtomAP(RangeS.testZero,CaptureA(CodeVarNameA("this"),FinalP),None,CodeRuneA("T"),None))

    val (InferSolveSuccess(inferencesD)) =
      makeCannedEvaluator().solve(
        makeCannedEnvironment(),
        FakeState(),
        rules,
        RangeS.testZero,
        Map(CodeRune2("Z") -> CoordTemplataType),
        Set(CodeRune2("Z")),
        Map(),
        atoms,
        None,
        true)

    inferencesD.templatasByRune(CodeRune2("Z")) shouldEqual
      CoordTemplata(Coord(Own,StructRef2(FullName2(List(), CitizenName2("MutTStruct",List(CoordTemplata(Coord(Share,Int2()))))))))
  }


  test("Test result of a CallAT can coerce to coord") {
    val rules =
      List(
        TemplexTR(RuneTT(RangeS.testZero,CodeRune2("__Par0"), CoordTemplataType)),
        EqualsTR(RangeS.testZero,TemplexTR(RuneTT(RangeS.testZero,CodeRune2("__Par0"), CoordTemplataType)),TemplexTR(NameTT(RangeS.testZero,CodeTypeNameA("MutStruct"), CoordTemplataType))))
    val atoms =
      List(AtomAP(RangeS.testZero,CaptureA(CodeVarNameA("this"),FinalP),None,CodeRuneA("T"),None))

    val (InferSolveSuccess(inferencesD)) =
      makeCannedEvaluator().solve(
        makeCannedEnvironment(),
        FakeState(),
        rules,
        RangeS.testZero,
        Map(CodeRune2("__Par0") -> CoordTemplataType),
        Set(CodeRune2("__Par0")),
        Map(),
        atoms,
        None,
        true)
    inferencesD.templatasByRune(CodeRune2("__Par0")) shouldEqual
      CoordTemplata(Coord(Own,StructRef2(FullName2(List(), CitizenName2("MutStruct",List())))))
  }

  test("Matching a CoordTemplataType onto a CallAT") {
    val rules =
      List(
        TemplexTR(RuneTT(RangeS.testZero,CodeRune2("0"), CoordTemplataType)),
        EqualsTR(RangeS.testZero,
          TemplexTR(RuneTT(RangeS.testZero,CodeRune2("0"), CoordTemplataType)),
          TemplexTR(
            CallTT(RangeS.testZero,
              NameTT(RangeS.testZero,CodeTypeNameA("MutTStruct"), TemplateTemplataType(List(CoordTemplataType), KindTemplataType)),
              List(RuneTT(RangeS.testZero,CodeRune2("T"), CoordTemplataType)),
              CoordTemplataType))))

    val (InferSolveSuccess(inferencesD)) =
      makeCannedEvaluator().solve(
        makeCannedEnvironment(),
        FakeState(),
        rules,
        RangeS.testZero,
        Map(CodeRune2("0") -> CoordTemplataType, CodeRune2("T") -> CoordTemplataType),
        Set(CodeRune2("0"), CodeRune2("T")),
        Map(),
        List(AtomAP(RangeS.testZero,CaptureA(CodeVarNameA("x"),FinalP),Some(AbstractAP),CodeRuneA("0"),None)),
        Some(List(ParamFilter(Coord(Own,StructRef2(FullName2(List(), CitizenName2("MutTStruct",List(CoordTemplata(Coord(Share,Int2()))))))),None))),
        true)
    inferencesD.templatasByRune(CodeRune2("0")) shouldEqual
      CoordTemplata(Coord(Own,StructRef2(FullName2(List(), CitizenName2("MutTStruct",List(CoordTemplata(Coord(Share,Int2()))))))))
  }

  test("Test destructuring") {
    val (InferSolveSuccess(inferencesD)) =
      makeCannedEvaluator().solve(
        makeCannedEnvironment(),
        FakeState(),
        List(
          TemplexTR(RuneTT(RangeS.testZero,CodeRune2("__Let0_"), CoordTemplataType)),
          TemplexTR(RuneTT(RangeS.testZero,CodeRune2("__Let0__Mem_0"), CoordTemplataType)),
          TemplexTR(RuneTT(RangeS.testZero,CodeRune2("__Let0__Mem_1"), CoordTemplataType))),
        RangeS.testZero,
        Map(CodeRune2("__Let0_") -> CoordTemplataType, CodeRune2("__Let0__Mem_0") -> CoordTemplataType, CodeRune2("__Let0__Mem_1") -> CoordTemplataType),
        Set(CodeRune2("__Let0_"), CodeRune2("__Let0__Mem_0"), CodeRune2("__Let0__Mem_1")),
        Map(),
        List(
          AtomAP(RangeS.testZero,
            CaptureA(CodeVarNameA("a"), FinalP),
            None,
            CodeRuneA("__Let0_"),
            Some(
              List(
                AtomAP(RangeS.testZero,CaptureA(CodeVarNameA("x"), FinalP),None,CodeRuneA("__Let0__Mem_0"),None),
                AtomAP(RangeS.testZero,CaptureA(CodeVarNameA("y"), FinalP),None,CodeRuneA("__Let0__Mem_1"),None))))),
        Some(List(ParamFilter(Coord(Share,PackT2(List(Coord(Share,Int2()), Coord(Share,Int2())),StructRef2(FullName2(List(), CitizenName2("__Pack",List(CoordTemplata(Coord(Share,Int2())), CoordTemplata(Coord(Share,Int2())))))))),None))),
        true)
    inferencesD.templatasByRune(CodeRune2("__Let0_")) shouldEqual
      CoordTemplata(
        Coord(
          Share,
          PackT2(
            List(Coord(Share,Int2()), Coord(Share,Int2())),
            StructRef2(FullName2(List(), CitizenName2("__Pack",List(CoordTemplata(Coord(Share,Int2())), CoordTemplata(Coord(Share,Int2())))))))))
    inferencesD.templatasByRune(CodeRune2("__Let0__Mem_0")) shouldEqual
      CoordTemplata(Coord(Share,Int2()))
    inferencesD.templatasByRune(CodeRune2("__Let0__Mem_1")) shouldEqual
      CoordTemplata(Coord(Share,Int2()))
  }

  test("Test evaluating array sequence") {
    val (InferSolveSuccess(inferencesD)) =
      makeCannedEvaluator().solve(
        makeCannedEnvironment(),
        FakeState(),
        List(
          TemplexTR(RuneTT(RangeS.testZero,CodeRune2("Z"), CoordTemplataType)),
          EqualsTR(RangeS.testZero,
            TemplexTR(RuneTT(RangeS.testZero,CodeRune2("Z"), CoordTemplataType)),
            TemplexTR(RepeaterSequenceTT(RangeS.testZero,MutabilityTT(RangeS.testZero,ImmutableP), IntTT(RangeS.testZero,5),OwnershippedTT(RangeS.testZero,ShareP,NameTT(RangeS.testZero,CodeTypeNameA("int"), CoordTemplataType)), CoordTemplataType)))),
        RangeS.testZero,
        Map(CodeRune2("Z") -> CoordTemplataType),
        Set(CodeRune2("Z")),
        Map(),
        List(),
        None,
        true)
    inferencesD.templatasByRune(CodeRune2("Z")) shouldEqual
      CoordTemplata(Coord(Share,KnownSizeArrayT2(5,RawArrayT2(Coord(Share,Int2()),Immutable))))
  }

  test("Test matching array sequence as coord") {
    val (InferSolveSuccess(inferencesD)) =
      makeCannedEvaluator().solve(
        makeCannedEnvironment(),
        FakeState(),
        List(
          EqualsTR(RangeS.testZero,
            TemplexTR(NameTT(RangeS.testZero,CodeTypeNameA("MutArraySequenceOf4Int"), CoordTemplataType)),
            TemplexTR(
              RepeaterSequenceTT(RangeS.testZero,
                RuneTT(RangeS.testZero,CodeRune2("M"), MutabilityTemplataType),
                RuneTT(RangeS.testZero,CodeRune2("N"), IntegerTemplataType),
                RuneTT(RangeS.testZero,CodeRune2("E"), CoordTemplataType),
                CoordTemplataType)))),
        RangeS.testZero,
        Map(CodeRune2("E") -> CoordTemplataType),
        Set(CodeRune2("E"), CodeRune2("M"), CodeRune2("N")),
        Map(),
        List(),
        None,
        true)
    inferencesD.templatasByRune(CodeRune2("M")) shouldEqual MutabilityTemplata(Mutable)
    inferencesD.templatasByRune(CodeRune2("N")) shouldEqual IntegerTemplata(4)
    inferencesD.templatasByRune(CodeRune2("E")) shouldEqual CoordTemplata(Coord(Share,Int2()))
  }

  test("Test matching array sequence as kind") {
    val (InferSolveSuccess(inferencesD)) =
      makeCannedEvaluator().solve(
        makeCannedEnvironment(),
        FakeState(),
        List(
          EqualsTR(RangeS.testZero,
            TemplexTR(NameTT(RangeS.testZero,CodeTypeNameA("MutArraySequenceOf4Int"), KindTemplataType)),
            TemplexTR(
              RepeaterSequenceTT(RangeS.testZero,
                RuneTT(RangeS.testZero,CodeRune2("M"), MutabilityTemplataType),
                RuneTT(RangeS.testZero,CodeRune2("N"), IntegerTemplataType),
                RuneTT(RangeS.testZero,CodeRune2("E"), CoordTemplataType),
                KindTemplataType)))),
        RangeS.testZero,
        Map(CodeRune2("E") -> CoordTemplataType),
        Set(CodeRune2("E"), CodeRune2("M"), CodeRune2("N")),
        Map(),
        List(),
        None,
        true)
    inferencesD.templatasByRune(CodeRune2("M")) shouldEqual MutabilityTemplata(Mutable)
    inferencesD.templatasByRune(CodeRune2("N")) shouldEqual IntegerTemplata(4)
    inferencesD.templatasByRune(CodeRune2("E")) shouldEqual CoordTemplata(Coord(Share,Int2()))
  }

  test("Test evaluating manual sequence") {
    val (InferSolveSuccess(inferencesD)) =
      makeCannedEvaluator().solve(
        makeCannedEnvironment(),
        FakeState(),
        List(
          EqualsTR(RangeS.testZero,
            TemplexTR(RuneTT(RangeS.testZero,CodeRune2("Z"), CoordTemplataType)),
            TemplexTR(
              ManualSequenceTT(RangeS.testZero,
                List(
                  NameTT(RangeS.testZero,CodeTypeNameA("int"), CoordTemplataType),
                  NameTT(RangeS.testZero,CodeTypeNameA("bool"), CoordTemplataType)),
                CoordTemplataType)))),
        RangeS.testZero,
        Map(CodeRune2("Z") -> CoordTemplataType),
        Set(CodeRune2("Z")),
        Map(),
        List(),
        None,
        true)
    inferencesD.templatasByRune(CodeRune2("Z")) shouldEqual
      CoordTemplata(
        Coord(
          Share,
          TupleT2(
            List(Coord(Share,Int2()), Coord(Share,Bool2())),
            StructRef2(FullName2(List(),CitizenName2("ImmStruct",List()))))))
  }

  test("Test matching manual sequence as coord") {
    val (InferSolveSuccess(inferencesD)) =
      makeCannedEvaluator().solve(
        makeCannedEnvironment(),
        FakeState(),
        List(
          EqualsTR(RangeS.testZero,
            TemplexTR(NameTT(RangeS.testZero,CodeTypeNameA("IntAndBoolTupName"), CoordTemplataType)),
            TemplexTR(
              ManualSequenceTT(RangeS.testZero,
                List(
                  RuneTT(RangeS.testZero,CodeRune2("A"), CoordTemplataType),
                  RuneTT(RangeS.testZero,CodeRune2("B"), CoordTemplataType)),
                CoordTemplataType)))),
        RangeS.testZero,
        Map(),
        Set(CodeRune2("A"), CodeRune2("B")),
        Map(),
        List(),
        None,
        true)
    inferencesD.templatasByRune(CodeRune2("A")) shouldEqual CoordTemplata(Coord(Share,Int2()))
    inferencesD.templatasByRune(CodeRune2("B")) shouldEqual CoordTemplata(Coord(Share,Bool2()))
  }

  test("Test matching manual sequence as kind") {
    val (InferSolveSuccess(inferencesD)) =
      makeCannedEvaluator().solve(
        makeCannedEnvironment(),
        FakeState(),
        List(
          EqualsTR(RangeS.testZero,
            TemplexTR(NameTT(RangeS.testZero,CodeTypeNameA("IntAndBoolTupName"), KindTemplataType)),
            TemplexTR(
              ManualSequenceTT(RangeS.testZero,
                List(
                  RuneTT(RangeS.testZero,CodeRune2("A"), CoordTemplataType),
                  RuneTT(RangeS.testZero,CodeRune2("B"), CoordTemplataType)),
                KindTemplataType)))),
        RangeS.testZero,
        Map(),
        Set(CodeRune2("A"), CodeRune2("B")),
        Map(),
        List(),
        None,
        true)
    inferencesD.templatasByRune(CodeRune2("A")) shouldEqual CoordTemplata(Coord(Share,Int2()))
    inferencesD.templatasByRune(CodeRune2("B")) shouldEqual CoordTemplata(Coord(Share,Bool2()))
  }

  test("Test array") {
    val (InferSolveSuccess(inferencesD)) =
      makeCannedEvaluator().solve(
        makeCannedEnvironment(),
        FakeState(),
        List(
          EqualsTR(RangeS.testZero,
            TemplexTR(RuneTT(RangeS.testZero,CodeRune2("K"), KindTemplataType)),
            TemplexTR(
              CallTT(RangeS.testZero,
                NameTT(RangeS.testZero,CodeTypeNameA("Array"), TemplateTemplataType(List(MutabilityTemplataType, CoordTemplataType), KindTemplataType)),
                List(MutabilityTT(RangeS.testZero,MutableP), NameTT(RangeS.testZero,CodeTypeNameA("int"), CoordTemplataType)),
                KindTemplataType))),
          EqualsTR(RangeS.testZero,
            TemplexTR(RuneTT(RangeS.testZero,CodeRune2("K"), KindTemplataType)),
            TemplexTR(
              CallTT(RangeS.testZero,
                NameTT(RangeS.testZero,CodeTypeNameA("Array"), TemplateTemplataType(List(MutabilityTemplataType, CoordTemplataType), KindTemplataType)),
                List(RuneTT(RangeS.testZero,CodeRune2("M"), MutabilityTemplataType), RuneTT(RangeS.testZero,CodeRune2("T"), CoordTemplataType)),
                KindTemplataType)))),
        RangeS.testZero,
        Map(CodeRune2("T") -> CoordTemplataType, CodeRune2("M") -> MutabilityTemplataType, CodeRune2("K") -> KindTemplataType),
        Set(CodeRune2("T"), CodeRune2("M"), CodeRune2("K")),
        Map(),
        List(),
        None,
        true)
    inferencesD.templatasByRune(CodeRune2("M")) shouldEqual MutabilityTemplata(Mutable)
    inferencesD.templatasByRune(CodeRune2("T")) shouldEqual CoordTemplata(Coord(Share,Int2()))
  }

  test("Test evaluating isa") {
    val (InferSolveSuccess(_)) =
      makeCannedEvaluator()
        .solve(
          makeCannedEnvironment(),
          FakeState(),
          List(
            IsaTR(RangeS.testZero,
              TemplexTR(RuneTT(RangeS.testZero,CodeRune2("K"), KindTemplataType)),
              TemplexTR(NameTT(RangeS.testZero,CodeTypeNameA("MutInterface"), KindTemplataType)))),
          RangeS.testZero,
          Map(CodeRune2("K") -> KindTemplataType),
          Set(CodeRune2("K")),
          Map(CodeRune2("K") -> KindTemplata(StructRef2(FullName2(List(), CitizenName2("MutStruct", List()))))),
          List(),
          None,
          true)

    val (isf @ InferSolveFailure(_, _, _,_,_, _, _)) =
      makeCannedEvaluator()
        .solve(
          makeCannedEnvironment(),
          FakeState(),
          List(
            IsaTR(RangeS.testZero,
              TemplexTR(RuneTT(RangeS.testZero,CodeRune2("K"), KindTemplataType)),
              TemplexTR(NameTT(RangeS.testZero,CodeTypeNameA("MutInterface"), KindTemplataType)))),
          RangeS.testZero,
          Map(CodeRune2("K") -> KindTemplataType),
          Set(CodeRune2("K")),
          Map(CodeRune2("K") -> KindTemplata(StructRef2(FullName2(List(), CitizenName2("MutSoloStruct", List()))))),
          List(),
          None,
          true)
    vassert(isf.toString.contains("Isa failed"))
  }

  test("Test matching isa") {
    val (InferSolveSuccess(_)) =
      makeCannedEvaluator()
        .solve(
          makeCannedEnvironment(),
          FakeState(),
          List(
            EqualsTR(RangeS.testZero,
              TemplexTR(RuneTT(RangeS.testZero,CodeRune2("K"), KindTemplataType)),
              IsaTR(RangeS.testZero,
                TemplexTR(RuneTT(RangeS.testZero,CodeRune2("Z"), KindTemplataType)),
                TemplexTR(NameTT(RangeS.testZero,CodeTypeNameA("MutInterface"), KindTemplataType))))),
          RangeS.testZero,
          Map(CodeRune2("K") -> KindTemplataType),
          Set(CodeRune2("K"), CodeRune2("Z")),
          Map(CodeRune2("K") -> KindTemplata(StructRef2(FullName2(List(), CitizenName2("MutStruct", List()))))),
          List(),
          None,
          true)

    val (isf @ InferSolveFailure(_, _,_,_, _, _, _)) =
      makeCannedEvaluator()
        .solve(
          makeCannedEnvironment(),
          FakeState(),
          List(
            IsaTR(RangeS.testZero,
              TemplexTR(RuneTT(RangeS.testZero,CodeRune2("K"), KindTemplataType)),
              TemplexTR(NameTT(RangeS.testZero,CodeTypeNameA("MutInterface"), KindTemplataType)))),
          RangeS.testZero,
          Map(CodeRune2("K") -> KindTemplataType),
          Set(CodeRune2("K")),
          Map(CodeRune2("K") -> KindTemplata(StructRef2(FullName2(List(), CitizenName2("MutSoloStruct", List()))))),
          List(),
          None,
          true)
    vassert(isf.toString.contains("Isa failed"))
  }

  test("Test evaluate prototype components") {
    val (InferSolveSuccess(conclusions)) =
      makeCannedEvaluator()
        .solve(
          makeCannedEnvironment(),
          FakeState(),
          List(
            EqualsTR(RangeS.testZero,
              ComponentsTR(
                RangeS.internal(-104),
                PrototypeTemplataType,
                List(
                  TemplexTR(StringTT(RangeS.testZero,"increment")),
                  TemplexTR(CoordListTT(RangeS.testZero,List(NameTT(RangeS.testZero,CodeTypeNameA("int"), CoordTemplataType)))),
                  TemplexTR(NameTT(RangeS.testZero,CodeTypeNameA("int"), CoordTemplataType)))),
              TemplexTR(RuneTT(RangeS.testZero,CodeRune2("F"),PrototypeTemplataType)))),
          RangeS.testZero,
          Map(CodeRune2("F") -> PrototypeTemplataType),
          Set(CodeRune2("F")),
          Map(),
          List(),
          None,
          true)
    conclusions.templatasByRune(CodeRune2("F")) shouldEqual PrototypeTemplata(incrementPrototype)
  }

  test("Test evaluate prototype return") {
    // We evaluate the prototype return when we fail to evaluate the name and params.
    // Lets make it so we can only evaluate the params from evaluating the ret, to exercise
    // evaluating the ret.

    val (InferSolveSuccess(conclusions)) =
      makeCannedEvaluator()
        .solve(
          makeCannedEnvironment(),
          FakeState(),
          List(
            EqualsTR(RangeS.testZero,
              ComponentsTR(
                RangeS.internal(-105),
                PrototypeTemplataType,
                List(
                  TemplexTR(StringTT(RangeS.testZero,"increment")),
                  TemplexTR(
                    CoordListTT(RangeS.testZero,
                      List(
                        RuneTT(RangeS.testZero,CodeRune2("T"),CoordTemplataType)))),
                  EqualsTR(RangeS.testZero,
                    TemplexTR(NameTT(RangeS.testZero,CodeTypeNameA("int"),CoordTemplataType)),
                    TemplexTR(RuneTT(RangeS.testZero,CodeRune2("T"),CoordTemplataType))))),
              TemplexTR(RuneTT(RangeS.testZero,CodeRune2("F"),PrototypeTemplataType)))),
          RangeS.testZero,
          Map(CodeRune2("T") -> CoordTemplataType, CodeRune2("F") -> PrototypeTemplataType),
          Set(CodeRune2("T"), CodeRune2("F")),
          Map(),
          List(),
          None,
          true)
    conclusions.templatasByRune(CodeRune2("F")) shouldEqual PrototypeTemplata(incrementPrototype)
  }

  test("Test match prototype components") {
    val (InferSolveSuccess(conclusions)) =
      makeCannedEvaluator()
        .solve(
          makeCannedEnvironment(),
          FakeState(),
          List(
            EqualsTR(RangeS.testZero,
              TemplexTR(RuneTT(RangeS.testZero,CodeRune2("F"),PrototypeTemplataType)),
              ComponentsTR(
                RangeS.internal(-106),
                PrototypeTemplataType,
                List(
                  TemplexTR(RuneTT(RangeS.testZero,CodeRune2("X"),StringTemplataType)),
                  TemplexTR(RuneTT(RangeS.testZero,CodeRune2("Y"),PackTemplataType(CoordTemplataType))),
                  TemplexTR(RuneTT(RangeS.testZero,CodeRune2("T"),CoordTemplataType)))))),
          RangeS.testZero,
          Map(CodeRune2("X") -> StringTemplataType, CodeRune2("Y") -> PackTemplataType(CoordTemplataType), CodeRune2("T") -> CoordTemplataType, CodeRune2("F") -> PrototypeTemplataType),
          Set(CodeRune2("X"), CodeRune2("Y"), CodeRune2("T"), CodeRune2("F")),
          Map(CodeRune2("F") -> PrototypeTemplata(incrementPrototype)),
          List(),
          None,
          true)
    conclusions.templatasByRune(CodeRune2("X")) shouldEqual StringTemplata("increment")
    conclusions.templatasByRune(CodeRune2("Y")) shouldEqual CoordListTemplata(List(Coord(Share, Int2())))
    conclusions.templatasByRune(CodeRune2("T")) shouldEqual CoordTemplata(Coord(Share, Int2()))
  }

  test("Test ownershipped") {
    def run(sourceName: String, targetOwnership: OwnershipP): IInferSolveResult = {
      val result =
        makeCannedEvaluator().solve(
          makeCannedEnvironment(),
          FakeState(),
          List(
            EqualsTR(RangeS.testZero,
              TemplexTR(OwnershippedTT(RangeS.testZero,targetOwnership, NameTT(RangeS.testZero,CodeTypeNameA(sourceName), CoordTemplataType))),
              TemplexTR(RuneTT(RangeS.testZero,CodeRune2("T"), CoordTemplataType)))),
          RangeS.testZero,
          Map(CodeRune2("T") -> CoordTemplataType),
          Set(CodeRune2("T")),
          Map(),
          List(AtomAP(RangeS.testZero,CaptureA(CodeVarNameA("this"),FinalP),None,CodeRuneA("T"),None)),
          None,
          true)
      result
    }

    def expectSuccess(inferSolveResult: IInferSolveResult): Coord = {
      val InferSolveSuccess(inferencesD) = inferSolveResult
      val CoordTemplata(coord) = inferencesD.templatasByRune(CodeRune2("T"))
      coord
    }

    def expectFail(inferSolveResult: IInferSolveResult): String = {
      val isf @ InferSolveFailure(_, _, _, _, _, _, _) = inferSolveResult
      isf.toString
    }

    expectSuccess(run("int", OwnP)) shouldEqual Coord(Share, Int2())
    expectSuccess(run("int", BorrowP)) shouldEqual Coord(Share, Int2())
    vassert(expectFail(run("int", WeakP)).contains("Expected a weak, but was a share"))
    expectSuccess(run("int", ShareP)) shouldEqual Coord(Share, Int2())

    vassert(expectFail(run("MutStruct", ShareP)).contains("Expected a share, but was an own"))
    expectSuccess(run("MutStruct", OwnP)) shouldEqual Coord(Own, StructRef2(FullName2(List(), CitizenName2("MutStruct", List()))))
    expectSuccess(run("MutStruct", BorrowP)) shouldEqual Coord(Borrow, StructRef2(FullName2(List(), CitizenName2("MutStruct", List()))))
    expectSuccess(run("MutStruct", WeakP)) shouldEqual Coord(Weak, StructRef2(FullName2(List(), CitizenName2("MutStruct", List()))))

    vassert(expectFail(run("MutStructBorrow", ShareP)).contains("Expected a share, but was a borrow"))
    expectSuccess(run("MutStructBorrow", OwnP)) shouldEqual Coord(Own, StructRef2(FullName2(List(), CitizenName2("MutStruct", List()))))
    expectSuccess(run("MutStructBorrow", BorrowP)) shouldEqual Coord(Borrow, StructRef2(FullName2(List(), CitizenName2("MutStruct", List()))))
    expectSuccess(run("MutStructBorrow", WeakP)) shouldEqual Coord(Weak, StructRef2(FullName2(List(), CitizenName2("MutStruct", List()))))

    vassert(expectFail(run("MutStructWeak", ShareP)).contains("Expected a share, but was a weak"))
    vassert(expectFail(run("MutStructWeak", OwnP)).contains("Expected a own, but was a weak"))
    vassert(expectFail(run("MutStructWeak", BorrowP)).contains("Expected a borrow, but was a weak"))
    expectSuccess(run("MutStructWeak", WeakP)) shouldEqual Coord(Weak, StructRef2(FullName2(List(), CitizenName2("MutStruct", List()))))

    expectSuccess(run("void", ShareP)) shouldEqual Coord(Share, Void2())
    expectSuccess(run("void", OwnP)) shouldEqual Coord(Share, Void2())
    expectSuccess(run("void", BorrowP)) shouldEqual Coord(Share, Void2())
    vassert(expectFail(run("void", WeakP)).contains("Expected a weak, but was a share"))
  }

  test("test matching ownershipped") {
    def run(sourceName: String, targetOwnership: OwnershipP): IInferSolveResult = {
      val result =
        makeCannedEvaluator().solve(
          makeCannedEnvironment(),
          FakeState(),
          List(
            EqualsTR(RangeS.testZero,
              TemplexTR(NameTT(RangeS.testZero,CodeTypeNameA(sourceName), CoordTemplataType)),
              TemplexTR(OwnershippedTT(RangeS.testZero,targetOwnership, RuneTT(RangeS.testZero,CodeRune2("T"), CoordTemplataType))))),
          RangeS.testZero,
          Map(CodeRune2("T") -> CoordTemplataType),
          Set(CodeRune2("T")),
          Map(),
          List(),
          None,
          true)
      result
    }

    def expectSuccess(inferSolveResult: IInferSolveResult): Coord = {
      val InferSolveSuccess(inferencesD) = inferSolveResult
      val CoordTemplata(coord) = inferencesD.templatasByRune(CodeRune2("T"))
      coord
    }

    def expectFail(inferSolveResult: IInferSolveResult): String = {
      val isf @ InferSolveFailure(_, _, _, _, _, _, _) = inferSolveResult
      isf.toString
    }

    expectSuccess(run("int", OwnP)) shouldEqual Coord(Share, Int2())
    expectSuccess(run("int", BorrowP)) shouldEqual Coord(Share, Int2())
    vassert(expectFail(run("int", WeakP)).contains("Couldn't match incoming Share against expected Weak"))
    expectSuccess(run("int", ShareP)) shouldEqual Coord(Share, Int2())

    expectSuccess(run("void", OwnP)) shouldEqual Coord(Share, Void2())
    expectSuccess(run("void", BorrowP)) shouldEqual Coord(Share, Void2())
    vassert(expectFail(run("void", WeakP)).contains("Couldn't match incoming Share against expected Weak"))
    expectSuccess(run("void", ShareP)) shouldEqual Coord(Share, Void2())

    vassert(expectFail(run("MutStruct", ShareP)).contains("Couldn't match incoming Own against expected Share"))
    // Takes the own off the incoming own coord, ends up as another own.
    expectSuccess(run("MutStruct", OwnP)) shouldEqual Coord(Own, StructRef2(FullName2(List(), CitizenName2("MutStruct", List()))))
    // Tries to take the borrow off the incoming own coord... fails.
    vassert(expectFail(run("MutStruct", BorrowP)).contains("Couldn't match incoming Own against expected Borrow"))
    vassert(expectFail(run("MutStruct", WeakP)).contains("Couldn't match incoming Own against expected Weak"))

    // Tries to take the own off the incoming borrow coord... fails.
    vassert(expectFail(run("MutStructBorrow", OwnP)).contains("Couldn't match incoming Borrow against expected Own"))
    // Takes the borrow off the incoming borrow coord, succeeds and gives us an own.
    expectSuccess(run("MutStructBorrow", BorrowP)) shouldEqual Coord(Own, StructRef2(FullName2(List(), CitizenName2("MutStruct", List()))))
    // Takes the weak off the incoming borrow coord... fails.
    vassert(expectFail(run("MutStructBorrow", WeakP)).contains("Couldn't match incoming Borrow against expected Weak"))
    vassert(expectFail(run("MutStructBorrow", ShareP)).contains("Couldn't match incoming Borrow against expected Share"))

    // Tries to take the own off the incoming weak coord... fails.
    vassert(expectFail(run("MutStructWeak", OwnP)).contains("Couldn't match incoming Weak against expected Own"))
    // Takes the borrow off the incoming weak coord... fails.
    vassert(expectFail(run("MutStructWeak", BorrowP)).contains("Couldn't match incoming Weak against expected Borrow"))
    // Takes the weak off the incoming weak coord, succeeds and gives us an own.
    expectSuccess(run("MutStructWeak", WeakP)) shouldEqual Coord(Own, StructRef2(FullName2(List(), CitizenName2("MutStruct", List()))))
    vassert(expectFail(run("MutStructWeak", ShareP)).contains("Couldn't match incoming Weak against expected Share"))

  }
}
