package net.verdagon.vale.templar.infer

import net.verdagon.vale.astronomer.{FakeState => _, SimpleEnvironment => _, _}
import net.verdagon.vale.astronomer.ruletyper.IRuleTyperEvaluatorDelegate
import net.verdagon.vale.parser._
import net.verdagon.vale.scout.{IEnvironment => _, _}
import net.verdagon.vale.templar.{CitizenNameT, CitizenTemplateNameT, CodeRuneT, FullNameT, FunctionNameT, INameT, ImplicitRuneT, NameTranslator, PackageTopLevelNameT, PrimitiveNameT, Program2, StaticArrayFromValuesTE, TupleNameT}
import net.verdagon.vale.{IProfiler, NullProfiler, PackageCoordinate, vassert, vassertSome, vfail, vimpl, scout => s}
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
  def getMutability(kind: KindT): MutabilityT = {
    kind match {
      case VoidT() => ImmutableT
      case IntT(_) => ImmutableT
      case StructRefT(FullNameT(_, _, CitizenNameT(humanName, _))) if humanName.startsWith("Imm") => ImmutableT
      case StructRefT(FullNameT(_, _, CitizenNameT(humanName, _))) if humanName.startsWith("Mut") => MutableT
      case InterfaceRefT(FullNameT(_, _, CitizenNameT(humanName, _))) if humanName.startsWith("Imm") => ImmutableT
      case InterfaceRefT(FullNameT(_, _, CitizenNameT(humanName, _))) if humanName.startsWith("Mut") => MutableT
      case StaticSizedArrayTT(_, RawArrayTT(_, mutability, _)) => mutability
      case RuntimeSizedArrayTT(RawArrayTT(_, mutability, _)) => mutability
    }
  }
}

case class SimpleEnvironment(templatas: TemplatasStore) extends IEnvironment {
  override def getParentEnv(): Option[IEnvironment] = None
  def fullName = FullNameT(PackageCoordinate.BUILTIN, Nil, PackageTopLevelNameT())
  def globalEnv: PackageEnvironment[INameT] = {
    vfail()
  }
  override def getAllTemplatasWithAbsoluteName2(name: INameT, lookupFilter: Set[ILookupContext]): List[ITemplata] = {
    templatas.getAllTemplatasWithAbsoluteName2(this, name, lookupFilter)
  }
  override def getNearestTemplataWithAbsoluteName2(name: INameT, lookupFilter: Set[ILookupContext]): Option[ITemplata] = {
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
  override def getAncestorInterfaceDistance(temputs: FakeState, descendantCitizenRef: CitizenRefT, ancestorInterfaceRef: InterfaceRefT): (Option[Int]) = {
    vfail()
  }

  override def getAncestorInterfaces(temputs: FakeState, descendantCitizenRef: CitizenRefT): (Set[InterfaceRefT]) = {
    vfail()
  }

  override def getMutability(state: FakeState, kind: KindT): MutabilityT = {
    InfererTestUtils.getMutability(kind)
  }

  override def lookupMemberTypes(state: FakeState, kind: KindT, expectedNumMembers: Int): Option[List[CoordT]] = {
    vfail()
  }

  override def getMemberCoords(state: FakeState, structRef: StructRefT): List[CoordT] = {
    vfail()
  }

  override def structIsClosure(state: FakeState, structRef: StructRefT): Boolean = {
    vfail()
  }

  override def lookupTemplata(env: SimpleEnvironment, range: RangeS, rune: INameT): ITemplata = {
    val results = env.getAllTemplatasWithAbsoluteName2(rune, Set(TemplataLookupContext))
    vassert(results.size == 1)
    results.head
  }

  override def lookupTemplata(profiler: IProfiler, env: SimpleEnvironment, range: RangeS, name: IImpreciseNameStepA): ITemplata = {
    val results = env.getAllTemplatasWithName(profiler, name, Set(TemplataLookupContext))
    vassert(results.size == 1)
    results.head
  }

  override def resolveExactSignature(env: SimpleEnvironment, state: FakeState, range: RangeS, name: String, coords: List[CoordT]): PrototypeT = {
    val templatas = env.getAllTemplatasWithName(new NullProfiler(), GlobalFunctionFamilyNameA(name), Set(TemplataLookupContext))
    val prototypes = templatas.collect({ case PrototypeTemplata(prot) => prot })
    val matchingPrototypes = prototypes.filter(_.paramTypes == coords)
    vassert(matchingPrototypes.size == 1)
    matchingPrototypes.head
  }
}

class FakeTemplataTemplarInnerDelegate extends ITemplataTemplarInnerDelegate[SimpleEnvironment, FakeState] {
  override def evaluateInterfaceTemplata(state: FakeState, callRange: RangeS, templata: InterfaceTemplata, templateArgs: List[ITemplata]): (KindT) = {
    vfail()
  }
  override def evaluateStructTemplata(state: FakeState, callRange: RangeS, templata: StructTemplata, templateArgs: List[ITemplata]): (KindT) = {
    vfail()
  }
  override def getAncestorInterfaceDistance(state: FakeState, descendantCitizenRef: CitizenRefT, ancestorInterfaceRef: InterfaceRefT): (Option[Int]) = {
    vfail()
  }
  override def getMutability(state: FakeState, kind: KindT): MutabilityT = {
    InfererTestUtils.getMutability(kind)
  }
//  override def getPackKind(env: SimpleEnvironment, state: FakeState, types2: List[Coord]): (PackT2, Mutability) = {
//    vfail()
//  }
  override def lookupTemplata(env: SimpleEnvironment, range: RangeS, name: INameT): ITemplata = {
    vassertSome(env.getNearestTemplataWithAbsoluteName2(name, Set(TemplataLookupContext)))
  }

  override def getStaticSizedArrayKind(env: SimpleEnvironment, state: FakeState, mutability: MutabilityT, variability: VariabilityT, size: Int, element: CoordT): (StaticSizedArrayTT) = {
    vfail()
  }
  override def getRuntimeSizedArrayKind(env: SimpleEnvironment, state: FakeState, type2: CoordT, arrayMutability: MutabilityT, arrayVariability: VariabilityT): RuntimeSizedArrayTT = {
    RuntimeSizedArrayTT(RawArrayTT(type2, arrayMutability, arrayVariability))
  }
  override def getTupleKind(env: SimpleEnvironment, state: FakeState, elements: List[CoordT]): TupleTT = {
    vfail()
  }
  override def getInterfaceTemplataType(it: InterfaceTemplata): TemplateTemplataType = {
    vfail()
  }

  override def getStructTemplataType(st: StructTemplata): TemplateTemplataType = {
    vfail()
  }

  override def lookupTemplataImprecise(env: SimpleEnvironment, range: RangeS, name: IImpreciseNameStepA): ITemplata = {
    vassertSome(env.getNearestTemplataWithName(name, Set(TemplataLookupContext)))
  }
}

class InfererTests extends FunSuite with Matchers {
  val incrementPrototype =
    PrototypeT(FullNameT(PackageCoordinate.TEST_TLD, Nil, FunctionNameT("increment", Nil, List(CoordT(ShareT, ReadonlyT, IntT.i32)))), CoordT(ShareT, ReadonlyT, IntT.i32))

  def makeCannedEnvironment(): SimpleEnvironment = {
    var entries: TemplatasStore = TemplatasStore(Map(), Map())
    val voidName = PrimitiveNameT("void")
    entries = entries.addEntry(true, voidName, TemplataEnvEntry(KindTemplata(VoidT())))
    val intName = PrimitiveNameT("int")
    entries = entries.addEntry(true, intName, TemplataEnvEntry(KindTemplata(IntT.i32)))
    val boolName = PrimitiveNameT("bool")
    entries = entries.addEntry(true, boolName, TemplataEnvEntry(KindTemplata(BoolT())))
    entries = entries.addEntry(true,
      CitizenNameT("ImmInterface", Nil),
        InterfaceEnvEntry(
          InterfaceA(
            RangeS.internal(-70),
            TopLevelCitizenDeclarationNameA("ImmInterface", CodeLocationS.internal(-24)),
            Nil,
            false,
            CodeRuneA("M"),
            Some(ImmutableP),
            KindTemplataType,
            Set(CodeRuneA("M")),
            Nil,
            Set(CodeRuneA("M")),
            Map(CodeRuneA("M") -> MutabilityTemplataType),
            List(EqualsAR(RangeS.testZero,TemplexAR(RuneAT(RangeS.testZero,CodeRuneA("M"), MutabilityTemplataType)), TemplexAR(MutabilityAT(RangeS.testZero,ImmutableP)))),
            Nil)))
    entries = entries.addEntry(true,
      CitizenNameT("ImmStruct", Nil),
        StructEnvEntry(
          StructA(
            RangeS.internal(-71),
            TopLevelCitizenDeclarationNameA("ImmStruct", CodeLocationS.internal(-24)),
            Nil,
            false,
            CodeRuneA("M"),
            Some(ImmutableP),
            KindTemplataType,
            Set(CodeRuneA("M")),
            Nil,
            Set(CodeRuneA("M"), CodeRuneA("I"), CodeRuneA("B")),
            Map(CodeRuneA("M") -> MutabilityTemplataType, CodeRuneA("I") -> CoordTemplataType, CodeRuneA("B") -> CoordTemplataType),
            List(
              EqualsAR(RangeS.testZero,TemplexAR(RuneAT(RangeS.testZero,CodeRuneA("M"), MutabilityTemplataType)), TemplexAR(MutabilityAT(RangeS.testZero,ImmutableP))),
              EqualsAR(RangeS.testZero,TemplexAR(RuneAT(RangeS.testZero,CodeRuneA("I"), CoordTemplataType)), TemplexAR(NameAT(RangeS.testZero,CodeTypeNameA("int"), CoordTemplataType))),
              EqualsAR(RangeS.testZero,TemplexAR(RuneAT(RangeS.testZero,CodeRuneA("B"), CoordTemplataType)), TemplexAR(NameAT(RangeS.testZero,CodeTypeNameA("bool"), CoordTemplataType)))),
            List(
              StructMemberA(RangeS.testZero,"i", FinalP, CodeRuneA("I")),
              StructMemberA(RangeS.testZero,"i", FinalP, CodeRuneA("B"))))))
    entries = entries.addEntry(true, PrimitiveNameT("Array"), TemplataEnvEntry(ArrayTemplateTemplata()))
    entries = entries.addEntry(true,
        CitizenTemplateNameT("MutTStruct", CodeLocationT.internal(-25)),
          StructEnvEntry(
            StructA(
              RangeS.internal(-74),
              TopLevelCitizenDeclarationNameA("MutTStruct", CodeLocationS.internal(-26)),
              Nil,
              false,
              CodeRuneA("M"),
              Some(MutableP),
              TemplateTemplataType(List(CoordTemplataType), KindTemplataType),
              Set(CodeRuneA("M")),
              List(CodeRuneA("T")),
              Set(CodeRuneA("T"), CodeRuneA("M")),
              Map(CodeRuneA("T") -> CoordTemplataType, CodeRuneA("M") -> MutabilityTemplataType),
              List(EqualsAR(RangeS.testZero,TemplexAR(RuneAT(RangeS.testZero,CodeRuneA("M"), MutabilityTemplataType)), TemplexAR(MutabilityAT(RangeS.testZero,MutableP)))),
              Nil)))
    entries = entries.addEntry(true, CitizenTemplateNameT("MutTInterface", CodeLocationT.internal(-27)),
      InterfaceEnvEntry(
        InterfaceA(
          RangeS.internal(-75),
          TopLevelCitizenDeclarationNameA("MutTInterface", CodeLocationS.internal(-28)),
          Nil,
          false,
          CodeRuneA("M"),
          Some(MutableP),
          TemplateTemplataType(List(CoordTemplataType), KindTemplataType),
          Set(CodeRuneA("M")),
          List(CodeRuneA("T")),
          Set(CodeRuneA("T"), CodeRuneA("M")),
          Map(CodeRuneA("T") -> CoordTemplataType, CodeRuneA("M") -> MutabilityTemplataType),
          List(EqualsAR(RangeS.testZero,TemplexAR(RuneAT(RangeS.testZero,CodeRuneA("M"), MutabilityTemplataType)), TemplexAR(MutabilityAT(RangeS.testZero,MutableP)))),
          Nil)))
    entries = entries.addEntry(true, CitizenTemplateNameT("MutStruct", CodeLocationT.internal(-29)),
      StructEnvEntry(
        StructA(
          RangeS.internal(-73),
          TopLevelCitizenDeclarationNameA("MutStruct", CodeLocationS.internal(-30)),
          Nil,
          false,
          CodeRuneA("M"),
          Some(MutableP),
          KindTemplataType,
          Set(CodeRuneA("M")),
          Nil,
          Set(CodeRuneA("M")),
          Map(CodeRuneA("M") -> MutabilityTemplataType),
          List(EqualsAR(RangeS.testZero,TemplexAR(RuneAT(RangeS.testZero,CodeRuneA("M"), MutabilityTemplataType)), TemplexAR(MutabilityAT(RangeS.testZero,MutableP)))),
          Nil)))
    entries = entries.addEntry(true, CitizenTemplateNameT("MutInterface", CodeLocationT.internal(-31)),
      InterfaceEnvEntry(
        InterfaceA(
          RangeS.internal(-72),
          TopLevelCitizenDeclarationNameA("MutInterface", CodeLocationS.internal(-32)),
          Nil,
          false,
          CodeRuneA("M"),
          Some(MutableP),
          KindTemplataType,
          Set(CodeRuneA("M")),
          Nil,
          Set(CodeRuneA("M")),
          Map(CodeRuneA("M") -> MutabilityTemplataType),
          List(EqualsAR(RangeS.testZero,TemplexAR(RuneAT(RangeS.testZero,CodeRuneA("M"), MutabilityTemplataType)), TemplexAR(MutabilityAT(RangeS.testZero,MutableP)))),
          Nil)))
    entries = entries.addEntry(true, CitizenNameT("MutStructConstraint", Nil),
      TemplataEnvEntry(CoordTemplata(CoordT(ConstraintT,ReadonlyT, StructRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("MutStruct", Nil)))))))
    entries = entries.addEntry(true, CitizenNameT("MutStructConstraintRW", Nil),
      TemplataEnvEntry(CoordTemplata(CoordT(ConstraintT,ReadwriteT, StructRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("MutStruct", Nil)))))))
    entries = entries.addEntry(true, CitizenNameT("MutStructWeak", Nil),
      TemplataEnvEntry(CoordTemplata(CoordT(WeakT, ReadonlyT, StructRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("MutStruct", Nil)))))))
    entries = entries.addEntry(true, CitizenNameT("MutStructWeakRW", Nil),
      TemplataEnvEntry(CoordTemplata(CoordT(WeakT, ReadwriteT, StructRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("MutStruct", Nil)))))))
    entries = entries.addEntry(true, CitizenNameT("MutStaticSizedArrayOf4Int", Nil),
      TemplataEnvEntry(KindTemplata(StaticSizedArrayTT(4, RawArrayTT(CoordT(ShareT, ReadonlyT, IntT.i32), MutableT, VaryingT)))))
    // Tuples are normally addressed by TupleNameT, but that's a detail this test doesn't need to care about.
    entries = entries.addEntry(true, CitizenNameT("IntAndBoolTupName", Nil),
      TemplataEnvEntry(
        KindTemplata(
          TupleTT(
            List(Program2.intType, Program2.boolType),
            // Normally this would be backed by a struct simply named "Tup"
            StructRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("ImmStruct", Nil)))))))
    val callPrototype = PrototypeTemplata(incrementPrototype)
    entries = entries.addEntry(true, callPrototype.value.fullName.last, TemplataEnvEntry(callPrototype))
    SimpleEnvironment(entries)
  }

  // Makes an evaluator with some canned data
  def makeCannedEvaluator(): InfererEvaluator[SimpleEnvironment, FakeState] = {
    val templataTemplarDelegate =
      new FakeTemplataTemplarInnerDelegate() {
        override def getMutability(state: FakeState, kind: KindT): MutabilityT = {
          kind match {
            case StructRefT(FullNameT(_, _, CitizenNameT(humanName, _))) if humanName.startsWith("Mut") => MutableT
            case StructRefT(FullNameT(_, _, CitizenNameT(humanName, _))) if humanName.startsWith("Imm") => ImmutableT
            case InterfaceRefT(FullNameT(_, _, CitizenNameT(humanName, _))) if humanName.startsWith("Mut") => MutableT
            case InterfaceRefT(FullNameT(_, _, CitizenNameT(humanName, _))) if humanName.startsWith("Imm") => ImmutableT
            case IntT(_) | VoidT() | BoolT() => ImmutableT
            case StaticSizedArrayTT(_, RawArrayTT(_, mutability, _)) => mutability
            case RuntimeSizedArrayTT(RawArrayTT(_, mutability, _)) => mutability
            case TupleTT(_, StructRefT(FullNameT(_, _, CitizenNameT(humanName, _)))) if humanName.startsWith("Imm") => ImmutableT
            case _ => vfail()
          }
        }
        override def evaluateInterfaceTemplata(state: FakeState, callRange: RangeS, templata: InterfaceTemplata, templateArgs: List[ITemplata]): (KindT) = {
          (templata, templateArgs) match {
            case (InterfaceTemplata(_,interfaceName(TopLevelCitizenDeclarationNameA("MutTInterface", _))), List(CoordTemplata(CoordT(ShareT, ReadonlyT, IntT.i32)) )) => {
              InterfaceRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("MutTInterface", List(CoordTemplata(CoordT(ShareT, ReadonlyT, IntT.i32))))))
            }
            case (InterfaceTemplata(_,interfaceName(TopLevelCitizenDeclarationNameA("MutInterface", _))), Nil) => {
              InterfaceRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("MutInterface", Nil)))
            }
            case (InterfaceTemplata(_,interfaceName(TopLevelCitizenDeclarationNameA("ImmInterface", _))), Nil) => {
              InterfaceRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("ImmInterface", Nil)))
            }
          }
        }

        override def evaluateStructTemplata(state: FakeState, callRange: RangeS, templata: StructTemplata, templateArgs: List[ITemplata]): (KindT) = {
          (templata, templateArgs) match {
            case (StructTemplata(_,structName(TopLevelCitizenDeclarationNameA("MutTStruct", _))), List(CoordTemplata(CoordT(ShareT, ReadonlyT, IntT.i32)) )) => {
              StructRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("MutTStruct", List(CoordTemplata(CoordT(ShareT, ReadonlyT, IntT.i32))))))
            }
            case (StructTemplata(_,structName(TopLevelCitizenDeclarationNameA("MutStruct", _))), Nil) => {
              StructRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("MutStruct", Nil)))
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
        override def getStaticSizedArrayKind(env: SimpleEnvironment, state: FakeState, mutability: MutabilityT, variability: VariabilityT, size: Int, element: CoordT): (StaticSizedArrayTT) = {
          (StaticSizedArrayTT(size, RawArrayTT(element, mutability, variability)))
        }

        override def getTupleKind(env: SimpleEnvironment, state: FakeState, elements: List[CoordT]): TupleTT = {
          // Theres only one tuple in this test, and its backed by the ImmStruct.
          TupleTT(elements, StructRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("ImmStruct", Nil))))
        }
      }
    val delegate =
      new FakeInfererEvaluatorDelegate() {
        override def getAncestorInterfaces(state: FakeState, descendantCitizenRef: CitizenRefT): (Set[InterfaceRefT]) = {
          descendantCitizenRef match {
            case StructRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("MutTStruct",List(CoordTemplata(CoordT(ShareT, ReadonlyT, IntT.i32)))))) => Set(InterfaceRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("MutTInterface", List(CoordTemplata(CoordT(ShareT, ReadonlyT, IntT.i32)))))))
            case StructRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("MutStruct",Nil))) => Set(InterfaceRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("MutInterface", Nil))))
            case InterfaceRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("MutInterface",Nil))) => Set()
            case StructRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("MutSoloStruct",Nil))) => Set()
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

  test("Constraint becomes share if kind is immutable") {
    val (InferSolveSuccess(inferences)) =
      makeCannedEvaluator()
        .solve(
          makeCannedEnvironment(),
          FakeState(),
          List(
            TemplexTR(RuneTT(RangeS.testZero,CodeRuneT("__C"), CoordTemplataType)),
            EqualsTR(RangeS.testZero,
              TemplexTR(RuneTT(RangeS.testZero,CodeRuneT("__C"), CoordTemplataType)),
              TemplexTR(InterpretedTT(RangeS.testZero,ConstraintP,ReadonlyP,NameTT(RangeS.testZero,CodeTypeNameA("ImmInterface"), CoordTemplataType))))),
          RangeS.testZero,
          Map(CodeRuneT("__C") -> CoordTemplataType),
          Set(CodeRuneT("__C")),
          Map(),
          Nil,
          None,
          true)

    vassert(
      inferences.templatasByRune(CodeRuneT("__C")) ==
        CoordTemplata(CoordT(ShareT, ReadonlyT, InterfaceRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("ImmInterface", Nil))))))
  }

  test("Can infer coord rune from an incoming kind") {
    val (isf @ InferSolveFailure(_, _, _,_,_, _, _)) =
      makeCannedEvaluator()
        .solve(
          makeCannedEnvironment(),
          FakeState(),
          List(TemplexTR(RuneTT(RangeS.testZero,CodeRuneT("C"), CoordTemplataType))),
          RangeS.testZero,
          Map(CodeRuneT("C") -> CoordTemplataType),
          Set(CodeRuneT("C")),
          Map(CodeRuneT("C") -> KindTemplata(InterfaceRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("ImmInterface",List(KindTemplata(IntT.i32))))))),
          Nil,
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
          List(EqualsTR(RangeS.testZero,TemplexTR(RuneTT(RangeS.testZero,CodeRuneT("C"), CoordTemplataType)), TemplexTR(RuneTT(RangeS.testZero,CodeRuneT("A"), KindTemplataType)))),
          RangeS.testZero,
          Map(CodeRuneT("A") -> KindTemplataType),
          Set(CodeRuneT("A"), CodeRuneT("C")),
          Map(CodeRuneT("A") -> KindTemplata(IntT.i32)),
          Nil,
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
              TemplexTR(RuneTT(RangeS.testZero,CodeRuneT("C"), CoordTemplataType)),
              CallTR(RangeS.testZero,"toRef", List(TemplexTR(RuneTT(RangeS.testZero,CodeRuneT("A"), KindTemplataType))), CoordTemplataType))),
          RangeS.testZero,
          Map(CodeRuneT("C") -> CoordTemplataType, CodeRuneT("A") -> KindTemplataType),
          Set(CodeRuneT("C"), CodeRuneT("A")),
          Map(CodeRuneT("A") -> KindTemplata(IntT.i32)),
          Nil,
          None,
          true)

    conclusions.templatasByRune(CodeRuneT("C")) shouldEqual CoordTemplata(CoordT(ShareT, ReadonlyT, IntT.i32))
  }

  test("Can explicitly coerce from kind to coord 2") {
    val (InferSolveSuccess(conclusions)) =
      makeCannedEvaluator()
        .solve(
          makeCannedEnvironment(),
          FakeState(),
          List(
            TemplexTR(RuneTT(RangeS.testZero,CodeRuneT("Z"), CoordTemplataType)),
            EqualsTR(RangeS.testZero,TemplexTR(RuneTT(RangeS.testZero,CodeRuneT("Z"), CoordTemplataType)),TemplexTR(NameTT(RangeS.testZero,CodeTypeNameA("int"), CoordTemplataType)))),
          RangeS.testZero,
          Map(CodeRuneT("Z") -> CoordTemplataType),
          Set(CodeRuneT("Z")),
          Map(),
          Nil,
          None,
          true)

    conclusions.templatasByRune(CodeRuneT("Z")) shouldEqual CoordTemplata(CoordT(ShareT, ReadonlyT, IntT.i32))
  }

  test("Can match KindTemplataType against StructEnvEntry / StructTemplata") {
    val (InferSolveSuccess(conclusions)) =
      makeCannedEvaluator()
        .solve(
          makeCannedEnvironment(),
          FakeState(),
          List(
            EqualsTR(RangeS.testZero,
              TemplexTR(RuneTT(RangeS.testZero,CodeRuneT("__RetRune"), CoordTemplataType)),
              CallTR(RangeS.testZero,
                "toRef",
                List(TemplexTR(NameTT(RangeS.testZero,CodeTypeNameA("MutStruct"), KindTemplataType))),
                CoordTemplataType))),
          RangeS.testZero,
          Map(CodeRuneT("__RetRune") -> CoordTemplataType),
          Set(CodeRuneT("__RetRune")),
          Map(),
          Nil,
          None,
          true)

    conclusions.templatasByRune(CodeRuneT("__RetRune")) shouldEqual
      CoordTemplata(CoordT(OwnT,ReadwriteT, StructRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("MutStruct", Nil)))))
  }

  test("Can infer from simple rules") {
    val (InferSolveSuccess(inferences)) =
      makeCannedEvaluator()
        .solve(
          makeCannedEnvironment(),
          FakeState(),
          List(
            TemplexTR(RuneTT(RangeS.testZero,CodeRuneT("Z"), CoordTemplataType)),
            EqualsTR(RangeS.testZero,TemplexTR(RuneTT(RangeS.testZero,CodeRuneT("Z"), CoordTemplataType)),CallTR(RangeS.testZero,"toRef", List(TemplexTR(NameTT(RangeS.testZero,CodeTypeNameA("int"), KindTemplataType))), CoordTemplataType))),
          RangeS.testZero,
          Map(CodeRuneT("Z") -> CoordTemplataType),
          Set(CodeRuneT("Z")),
          Map(),
          Nil,
          None,
          true)

    vassert(inferences.templatasByRune(CodeRuneT("Z")) == CoordTemplata(CoordT(ShareT, ReadonlyT, IntT.i32)))
  }

  test("Can infer templata from CallAT") {
    val (InferSolveSuccess(inferences)) =
      makeCannedEvaluator()
        .solve(
          makeCannedEnvironment(),
          FakeState(),
          List(
            EqualsTR(RangeS.testZero,
              TemplexTR(RuneTT(RangeS.testZero,CodeRuneT("X"), KindTemplataType)),
              TemplexTR(CallTT(RangeS.testZero,NameTT(RangeS.testZero,CodeTypeNameA("MutTInterface"), TemplateTemplataType(List(CoordTemplataType), KindTemplataType)),List(RuneTT(RangeS.testZero,CodeRuneT("T"), CoordTemplataType)), KindTemplataType)))),
          RangeS.testZero,
          Map(CodeRuneT("X") -> KindTemplataType, CodeRuneT("T") -> CoordTemplataType),
          Set(CodeRuneT("X"), CodeRuneT("T")),
          Map(CodeRuneT("X") -> KindTemplata(InterfaceRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("MutTInterface",List(CoordTemplata(CoordT(ShareT, ReadonlyT, IntT.i32)))))))),
          Nil,
          None,
          true)

    vassert(inferences.templatasByRune(CodeRuneT("T")) == CoordTemplata(CoordT(ShareT, ReadonlyT, IntT.i32)))
  }

  test("Can conjure an owning coord from a borrow coord") {
    val (InferSolveSuccess(inferences)) =
      makeCannedEvaluator()
        .solve(
          makeCannedEnvironment(),
          FakeState(),
          List(
            TemplexTR(RuneTT(RangeS.testZero,CodeRuneT("T"), CoordTemplataType)),
            TemplexTR(RuneTT(RangeS.testZero,CodeRuneT("1337"), KindTemplataType)),
            EqualsTR(RangeS.testZero,
              TemplexTR(RuneTT(RangeS.testZero,CodeRuneT("T"), CoordTemplataType)),
              ComponentsTR(
                RangeS.internal(-100),
                CoordTemplataType,
                List(
                  TemplexTR(OwnershipTT(RangeS.testZero,OwnP)),
                  TemplexTR(PermissionTT(RangeS.testZero,ReadwriteP)),
                  TemplexTR(RuneTT(RangeS.testZero,CodeRuneT("1337"), KindTemplataType))))),
            TemplexTR(RuneTT(RangeS.testZero,CodeRuneT("0"), CoordTemplataType)),
            EqualsTR(RangeS.testZero,
              TemplexTR(RuneTT(RangeS.testZero,CodeRuneT("0"), CoordTemplataType)),
              ComponentsTR(
                RangeS.internal(-101),
                CoordTemplataType,
                List(
                  TemplexTR(OwnershipTT(RangeS.testZero,ConstraintP)),
                  TemplexTR(PermissionTT(RangeS.testZero,ReadonlyP)),
                  TemplexTR(RuneTT(RangeS.testZero,CodeRuneT("1337"), KindTemplataType)))))),
          RangeS.testZero,
          Map(
            CodeRuneT("1337") -> KindTemplataType,
            CodeRuneT("0") -> CoordTemplataType,
            CodeRuneT("YT") -> CoordTemplataType),
          Set(CodeRuneT("1337"), CodeRuneT("0"), CodeRuneT("T")),
          Map(),
          List(AtomAP(RangeS.testZero,Some(LocalA(CodeVarNameA("m"), NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed)),None,CodeRuneA("0"),None)),
          Some(List(ParamFilter(CoordT(ConstraintT,ReadonlyT, InterfaceRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("MutInterface", Nil)))),None))),
          true)

    vassert(inferences.templatasByRune(CodeRuneT("T")) == CoordTemplata(CoordT(OwnT,ReadwriteT, InterfaceRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("MutInterface", Nil))))))
  }

  test("Rune 0 upcasts to right type, simple") {
    val (InferSolveSuccess(inferences)) =
      makeCannedEvaluator()
        .solve(
          makeCannedEnvironment(),
          FakeState(),
          List(
            TemplexTR(RuneTT(RangeS.testZero,CodeRuneT("__Let0_"), CoordTemplataType)),
            EqualsTR(RangeS.testZero,
              TemplexTR(RuneTT(RangeS.testZero,CodeRuneT("__Let0_"), CoordTemplataType)),
              CallTR(RangeS.testZero,"toRef", List(TemplexTR(NameTT(RangeS.testZero,CodeTypeNameA("MutInterface"), KindTemplataType))), CoordTemplataType))),
          RangeS.testZero,
          Map(CodeRuneT("__Let0_") -> CoordTemplataType),
          Set(CodeRuneT("__Let0_")),
          Map(),
          List(AtomAP(RangeS.testZero,Some(LocalA(CodeVarNameA("x"), NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed)),None,CodeRuneA("__Let0_"),None)),
          Some(List(ParamFilter(CoordT(OwnT,ReadwriteT, StructRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("MutStruct",Nil)))),None))),
          true)

    vassert(inferences.templatasByRune(CodeRuneT("__Let0_")) == CoordTemplata(CoordT(OwnT,ReadwriteT, InterfaceRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("MutInterface", Nil))))))
  }

  test("Rune 0 upcasts to right type templated") {
    val (InferSolveSuccess(inferences)) =
      makeCannedEvaluator()
        .solve(
          makeCannedEnvironment(),
          FakeState(),
          List(
            TemplexTR(RuneTT(RangeS.testZero,CodeRuneT("__Let0_"), CoordTemplataType)),
            EqualsTR(RangeS.testZero,
              TemplexTR(RuneTT(RangeS.testZero,CodeRuneT("__Let0_"), CoordTemplataType)),
              CallTR(RangeS.testZero,
                "toRef",
                List(
                  TemplexTR(
                    CallTT(RangeS.testZero,
                      NameTT(RangeS.testZero,CodeTypeNameA("MutTInterface"), TemplateTemplataType(List(CoordTemplataType), KindTemplataType)),
                      List(RuneTT(RangeS.testZero,CodeRuneT("T"), CoordTemplataType)),
                      KindTemplataType))),
                CoordTemplataType))),
          RangeS.testZero,
          Map(CodeRuneT("__Let0_") -> CoordTemplataType, CodeRuneT("T") -> CoordTemplataType),
          Set(CodeRuneT("__Let0_"), CodeRuneT("T")),
          Map(),
          List(AtomAP(RangeS.testZero,Some(LocalA(CodeVarNameA("x"), NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed)),None,CodeRuneA("__Let0_"),None)),
          Some(List(ParamFilter(CoordT(OwnT,ReadwriteT, StructRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("MutTStruct",List(CoordTemplata(CoordT(ShareT, ReadonlyT, IntT.i32))))))),None))),
          true)

    vassert(
      inferences.templatasByRune(CodeRuneT("__Let0_")) ==
        CoordTemplata(CoordT(OwnT,ReadwriteT, InterfaceRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("MutTInterface", List(CoordTemplata(CoordT(ShareT, ReadonlyT, IntT.i32)))))))))
    vassert(
      inferences.templatasByRune(CodeRuneT("T")) ==
        CoordTemplata(CoordT(ShareT, ReadonlyT, IntT.i32)))
  }

  test("Tests destructor") {
    // Tests that we can make a rule that will only match structs, arrays, packs, sequences.
    // It doesn't have to be in this form, but we do need the capability in some way, so that
    // we can have a templated destructor that matches any of those.

    val rules =
      List(
        EqualsTR(RangeS.testZero,
          TemplexTR(RuneTT(RangeS.testZero,CodeRuneT("T"), CoordTemplataType)),
          ComponentsTR(
            RangeS.internal(-102),
            CoordTemplataType,
            List(
              OrTR(RangeS.testZero,List(TemplexTR(OwnershipTT(RangeS.testZero,OwnP)), TemplexTR(OwnershipTT(RangeS.testZero,ShareP)))),
              OrTR(RangeS.testZero,List(TemplexTR(PermissionTT(RangeS.testZero,ReadwriteP)), TemplexTR(PermissionTT(RangeS.testZero,ReadonlyP)))),
              CallTR(RangeS.testZero,"passThroughIfConcrete",List(TemplexTR(RuneTT(RangeS.testZero,CodeRuneT("Z"), KindTemplataType))), KindTemplataType)))),
        EqualsTR(RangeS.testZero,TemplexTR(RuneTT(RangeS.testZero,CodeRuneT("V"), CoordTemplataType)),CallTR(RangeS.testZero,"toRef",List(TemplexTR(NameTT(RangeS.testZero,CodeTypeNameA("void"),KindTemplataType))), CoordTemplataType)))
    val atoms =
      List(AtomAP(RangeS.testZero,Some(LocalA(CodeVarNameA("this"), NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed)),None,CodeRuneA("T"),None))

    val solve =
      (paramFilter: ParamFilter) => {
        makeCannedEvaluator().solve(
          makeCannedEnvironment(),
          FakeState(),
          rules,
          RangeS.testZero,
          Map(CodeRuneT("V") -> CoordTemplataType, CodeRuneT("T") -> CoordTemplataType),
          Set(CodeRuneT("V"), CodeRuneT("T"), CodeRuneT("Z")),
          Map(),
          atoms,
          Some(List(paramFilter)),
          true)
      }

    // Test that it does match a pack
    val packCoord = CoordT(ShareT, ReadonlyT,PackTT(Nil,StructRefT(FullNameT(PackageCoordinate.BUILTIN, Nil, CitizenNameT("__Pack",Nil)))))
    val (InferSolveSuccess(inferencesA)) = solve(ParamFilter(packCoord,None))
    vassert(inferencesA.templatasByRune(CodeRuneT("T")) == CoordTemplata(packCoord))

    // Test that it does match a struct
    val structCoord = CoordT(OwnT,ReadwriteT, StructRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("MutStruct",Nil))))
    val (InferSolveSuccess(inferencesD)) = solve(ParamFilter(structCoord,None))
    vassert(inferencesD.templatasByRune(CodeRuneT("T")) == CoordTemplata(structCoord))

    // Test that it doesn't match an int
    val intCoord = CoordT(ShareT, ReadonlyT,IntT.i32)
    val (isfE @ InferSolveFailure(_, _,_,_, _, _, _)) = solve(ParamFilter(intCoord,None))
    vassert(isfE.toString.contains("Bad arguments to passThroughIfConcrete"))

    // Test that it doesn't match an interface
    val interfaceCoord = CoordT(OwnT,ReadwriteT, InterfaceRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("MutInterface",Nil))))
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
          TemplexTR(RuneTT(RangeS.testZero,CodeRuneT("T"), CoordTemplataType)),
          ComponentsTR(
            RangeS.internal(-103),
            CoordTemplataType,
            List(
              OrTR(RangeS.testZero,List(TemplexTR(OwnershipTT(RangeS.testZero,OwnP)), TemplexTR(OwnershipTT(RangeS.testZero,ShareP)))),
              OrTR(RangeS.testZero,List(TemplexTR(PermissionTT(RangeS.testZero,ReadwriteP)), TemplexTR(PermissionTT(RangeS.testZero,ReadonlyP)))),
              CallTR(RangeS.testZero,"passThroughIfInterface",List(TemplexTR(RuneTT(RangeS.testZero,CodeRuneT("Z"), KindTemplataType))), KindTemplataType)))),
        EqualsTR(RangeS.testZero,
          TemplexTR(RuneTT(RangeS.testZero,CodeRuneT("V"), CoordTemplataType)),
          CallTR(RangeS.testZero,"toRef",List(TemplexTR(NameTT(RangeS.testZero,CodeTypeNameA("void"), KindTemplataType))), CoordTemplataType)))
    val atoms =
      List(AtomAP(RangeS.testZero,Some(LocalA(CodeVarNameA("this"),NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed)),None,CodeRuneA("T"),None))

    val solve =
      (paramFilter: ParamFilter) => {
        makeCannedEvaluator().solve(
          makeCannedEnvironment(),
          FakeState(),
          rules,
          RangeS.testZero,
          Map(CodeRuneT("T") -> CoordTemplataType, CodeRuneT("V") -> CoordTemplataType),
          Set(CodeRuneT("T"), CodeRuneT("V"), CodeRuneT("Z")),
          Map(),
          atoms,
          Some(List(paramFilter)),
          true)
      }

    // Test that it does match an interface
    val interfaceCoord = CoordT(OwnT,ReadwriteT, InterfaceRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("MutInterface",Nil))))
    val (InferSolveSuccess(inferencesD)) = solve(ParamFilter(interfaceCoord,None))
    vassert(inferencesD.templatasByRune(CodeRuneT("T")) == CoordTemplata(interfaceCoord))

    // Test that it doesn't match an int
    val intCoord = CoordT(ShareT, ReadonlyT,IntT.i32)
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
          TemplexTR(RuneTT(RangeS.testZero,CodeRuneT("T"), CoordTemplataType)),
          ComponentsTR(
            RangeS.internal(-107),
            CoordTemplataType,
            List(
              OrTR(RangeS.testZero,List(TemplexTR(OwnershipTT(RangeS.testZero,OwnP)), TemplexTR(OwnershipTT(RangeS.testZero,ShareP)))),
              OrTR(RangeS.testZero,List(TemplexTR(PermissionTT(RangeS.testZero,ReadwriteP)), TemplexTR(PermissionTT(RangeS.testZero,ReadonlyP)))),
              CallTR(RangeS.testZero,"passThroughIfStruct",List(TemplexTR(RuneTT(RangeS.testZero,CodeRuneT("Z"), KindTemplataType))), KindTemplataType)))))
    val atoms =
      List(AtomAP(RangeS.testZero,Some(LocalA(CodeVarNameA("this"), NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed)),None,CodeRuneA("T"),None))

    val solve =
      (paramFilter: ParamFilter) => {
        makeCannedEvaluator().solve(
          makeCannedEnvironment(),
          FakeState(),
          rules,
          RangeS.testZero,
          Map(CodeRuneT("T") -> CoordTemplataType),
          Set(CodeRuneT("T"), CodeRuneT("Z")),
          Map(),
          atoms,
          Some(List(paramFilter)),
          true)
      }

    // Test that it does match a struct
    val structCoord = CoordT(OwnT,ReadwriteT, StructRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("MutStruct",Nil))))
    val (InferSolveSuccess(inferencesD)) = solve(ParamFilter(structCoord,None))
    vassert(inferencesD.templatasByRune(CodeRuneT("T")) == CoordTemplata(structCoord))

    // Test that it doesn't match an int
    val intCoord = CoordT(ShareT, ReadonlyT,IntT.i32)
    val (isfE @ InferSolveFailure(_, _, _,_,_, _, _)) = solve(ParamFilter(intCoord,None))
    vassert(isfE.toString.contains("Bad arguments to passThroughIfStruct"))

    // Test that it doesn't match an interface
    val interfaceCoord = CoordT(OwnT,ReadwriteT, InterfaceRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("MutInterface",Nil))))
    val (isfF @ InferSolveFailure(_, _, _,_,_, _, _)) = solve(ParamFilter(interfaceCoord,None))
    vassert(isfF.toString.contains("Bad arguments to passThroughIfStruct"))

    // Test that it doesn't match an pack
    val packCoord = CoordT(ShareT, ReadonlyT,PackTT(Nil,StructRefT(FullNameT(PackageCoordinate.BUILTIN, Nil, CitizenNameT("__Pack",Nil)))))
    val (isfG @ InferSolveFailure(_, _, _,_,_, _, _)) = solve(ParamFilter(packCoord,None))
    vassert(isfG.toString.contains("Bad arguments to passThroughIfStruct"))
  }

  test("Test coercing template call result") {
    // Tests that we can make a rule that will only match structs, arrays, packs, sequences.
    // It doesn't have to be in this form, but we do need the capability in some way, so that
    // we can have a templated destructor that matches any of those.

    val rules =
      List(
        TemplexTR(RuneTT(RangeS.testZero,CodeRuneT("Z"), CoordTemplataType)),
        EqualsTR(RangeS.testZero,
          TemplexTR(RuneTT(RangeS.testZero,CodeRuneT("Z"), CoordTemplataType)),
          TemplexTR(
            CallTT(RangeS.testZero,
              NameTT(RangeS.testZero,CodeTypeNameA("MutTStruct"), TemplateTemplataType(List(CoordTemplataType), KindTemplataType)),
              List(NameTT(RangeS.testZero,CodeTypeNameA("int"), CoordTemplataType)),
              CoordTemplataType))))
    val atoms =
      List(AtomAP(RangeS.testZero,Some(LocalA(CodeVarNameA("this"), NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed)),None,CodeRuneA("T"),None))

    val (InferSolveSuccess(inferencesD)) =
      makeCannedEvaluator().solve(
        makeCannedEnvironment(),
        FakeState(),
        rules,
        RangeS.testZero,
        Map(CodeRuneT("Z") -> CoordTemplataType),
        Set(CodeRuneT("Z")),
        Map(),
        atoms,
        None,
        true)

    inferencesD.templatasByRune(CodeRuneT("Z")) shouldEqual
      CoordTemplata(CoordT(OwnT,ReadwriteT, StructRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("MutTStruct",List(CoordTemplata(CoordT(ShareT, ReadonlyT,IntT.i32))))))))
  }


  test("Test result of a CallAT can coerce to coord") {
    val rules =
      List(
        TemplexTR(RuneTT(RangeS.testZero,CodeRuneT("__Par0"), CoordTemplataType)),
        EqualsTR(RangeS.testZero,TemplexTR(RuneTT(RangeS.testZero,CodeRuneT("__Par0"), CoordTemplataType)),TemplexTR(NameTT(RangeS.testZero,CodeTypeNameA("MutStruct"), CoordTemplataType))))
    val atoms =
      List(AtomAP(RangeS.testZero,Some(LocalA(CodeVarNameA("this"), NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed)),None,CodeRuneA("T"),None))

    val (InferSolveSuccess(inferencesD)) =
      makeCannedEvaluator().solve(
        makeCannedEnvironment(),
        FakeState(),
        rules,
        RangeS.testZero,
        Map(CodeRuneT("__Par0") -> CoordTemplataType),
        Set(CodeRuneT("__Par0")),
        Map(),
        atoms,
        None,
        true)
    inferencesD.templatasByRune(CodeRuneT("__Par0")) shouldEqual
      CoordTemplata(CoordT(OwnT,ReadwriteT, StructRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("MutStruct",Nil)))))
  }

  test("Matching a CoordTemplataType onto a CallAT") {
    val rules =
      List(
        TemplexTR(RuneTT(RangeS.testZero,CodeRuneT("0"), CoordTemplataType)),
        EqualsTR(RangeS.testZero,
          TemplexTR(RuneTT(RangeS.testZero,CodeRuneT("0"), CoordTemplataType)),
          TemplexTR(
            CallTT(RangeS.testZero,
              NameTT(RangeS.testZero,CodeTypeNameA("MutTStruct"), TemplateTemplataType(List(CoordTemplataType), KindTemplataType)),
              List(RuneTT(RangeS.testZero,CodeRuneT("T"), CoordTemplataType)),
              CoordTemplataType))))

    val (InferSolveSuccess(inferencesD)) =
      makeCannedEvaluator().solve(
        makeCannedEnvironment(),
        FakeState(),
        rules,
        RangeS.testZero,
        Map(CodeRuneT("0") -> CoordTemplataType, CodeRuneT("T") -> CoordTemplataType),
        Set(CodeRuneT("0"), CodeRuneT("T")),
        Map(),
        List(AtomAP(RangeS.testZero,Some(LocalA(CodeVarNameA("x"),NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed)),Some(AbstractAP),CodeRuneA("0"),None)),
        Some(List(ParamFilter(CoordT(OwnT,ReadwriteT, StructRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("MutTStruct",List(CoordTemplata(CoordT(ShareT, ReadonlyT,IntT.i32))))))),None))),
        true)
    inferencesD.templatasByRune(CodeRuneT("0")) shouldEqual
      CoordTemplata(CoordT(OwnT,ReadwriteT, StructRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("MutTStruct",List(CoordTemplata(CoordT(ShareT, ReadonlyT,IntT.i32))))))))
  }

  test("Test destructuring") {
    val (InferSolveSuccess(inferencesD)) =
      makeCannedEvaluator().solve(
        makeCannedEnvironment(),
        FakeState(),
        List(
          TemplexTR(RuneTT(RangeS.testZero,CodeRuneT("__Let0_"), CoordTemplataType)),
          TemplexTR(RuneTT(RangeS.testZero,CodeRuneT("__Let0__Mem_0"), CoordTemplataType)),
          TemplexTR(RuneTT(RangeS.testZero,CodeRuneT("__Let0__Mem_1"), CoordTemplataType))),
        RangeS.testZero,
        Map(CodeRuneT("__Let0_") -> CoordTemplataType, CodeRuneT("__Let0__Mem_0") -> CoordTemplataType, CodeRuneT("__Let0__Mem_1") -> CoordTemplataType),
        Set(CodeRuneT("__Let0_"), CodeRuneT("__Let0__Mem_0"), CodeRuneT("__Let0__Mem_1")),
        Map(),
        List(
          AtomAP(RangeS.testZero,
            Some(LocalA(CodeVarNameA("a"), NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed)),
            None,
            CodeRuneA("__Let0_"),
            Some(
              List(
                AtomAP(RangeS.testZero,Some(LocalA(CodeVarNameA("x"), NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed)),None,CodeRuneA("__Let0__Mem_0"),None),
                AtomAP(RangeS.testZero,Some(LocalA(CodeVarNameA("y"), NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed)),None,CodeRuneA("__Let0__Mem_1"),None))))),
        Some(List(ParamFilter(CoordT(ShareT, ReadonlyT,PackTT(List(CoordT(ShareT, ReadonlyT,IntT.i32), CoordT(ShareT, ReadonlyT,IntT.i32)),StructRefT(FullNameT(PackageCoordinate.BUILTIN, Nil, CitizenNameT("__Pack",List(CoordTemplata(CoordT(ShareT, ReadonlyT,IntT.i32)), CoordTemplata(CoordT(ShareT, ReadonlyT,IntT.i32)))))))),None))),
        true)
    inferencesD.templatasByRune(CodeRuneT("__Let0_")) shouldEqual
      CoordTemplata(
        CoordT(
          ShareT,
          ReadonlyT,
          PackTT(
            List(CoordT(ShareT, ReadonlyT,IntT.i32), CoordT(ShareT, ReadonlyT,IntT.i32)),
            StructRefT(FullNameT(PackageCoordinate.BUILTIN, Nil, CitizenNameT("__Pack",List(CoordTemplata(CoordT(ShareT, ReadonlyT,IntT.i32)), CoordTemplata(CoordT(ShareT, ReadonlyT,IntT.i32)))))))))
    inferencesD.templatasByRune(CodeRuneT("__Let0__Mem_0")) shouldEqual
      CoordTemplata(CoordT(ShareT, ReadonlyT,IntT.i32))
    inferencesD.templatasByRune(CodeRuneT("__Let0__Mem_1")) shouldEqual
      CoordTemplata(CoordT(ShareT, ReadonlyT,IntT.i32))
  }

  test("Test evaluating array sequence") {
    val (InferSolveSuccess(inferencesD)) =
      makeCannedEvaluator().solve(
        makeCannedEnvironment(),
        FakeState(),
        List(
          TemplexTR(RuneTT(RangeS.testZero,CodeRuneT("Z"), CoordTemplataType)),
          EqualsTR(RangeS.testZero,
            TemplexTR(RuneTT(RangeS.testZero,CodeRuneT("Z"), CoordTemplataType)),
            TemplexTR(
              RepeaterSequenceTT(RangeS.testZero,
                MutabilityTT(RangeS.testZero,ImmutableP),
                VariabilityTT(RangeS.testZero,FinalP),
                IntTT(RangeS.testZero,5),
                InterpretedTT(RangeS.testZero,ShareP,ReadonlyP,NameTT(RangeS.testZero,CodeTypeNameA("int"), CoordTemplataType)), CoordTemplataType)))),
        RangeS.testZero,
        Map(CodeRuneT("Z") -> CoordTemplataType),
        Set(CodeRuneT("Z")),
        Map(),
        Nil,
        None,
        true)
    inferencesD.templatasByRune(CodeRuneT("Z")) shouldEqual
      CoordTemplata(CoordT(ShareT, ReadonlyT,StaticSizedArrayTT(5,RawArrayTT(CoordT(ShareT, ReadonlyT,IntT.i32),ImmutableT,FinalT))))
  }

  test("Test matching array sequence as coord") {
    val (InferSolveSuccess(inferencesD)) =
      makeCannedEvaluator().solve(
        makeCannedEnvironment(),
        FakeState(),
        List(
          EqualsTR(RangeS.testZero,
            TemplexTR(NameTT(RangeS.testZero,CodeTypeNameA("MutStaticSizedArrayOf4Int"), CoordTemplataType)),
            TemplexTR(
              RepeaterSequenceTT(RangeS.testZero,
                RuneTT(RangeS.testZero,CodeRuneT("M"), MutabilityTemplataType),
                RuneTT(RangeS.testZero,CodeRuneT("V"), VariabilityTemplataType),
                RuneTT(RangeS.testZero,CodeRuneT("N"), IntegerTemplataType),
                RuneTT(RangeS.testZero,CodeRuneT("E"), CoordTemplataType),
                CoordTemplataType)))),
        RangeS.testZero,
        Map(CodeRuneT("E") -> CoordTemplataType),
        Set(CodeRuneT("E"), CodeRuneT("M"), CodeRuneT("V"), CodeRuneT("N")),
        Map(),
        Nil,
        None,
        true)
    inferencesD.templatasByRune(CodeRuneT("M")) shouldEqual MutabilityTemplata(MutableT)
    inferencesD.templatasByRune(CodeRuneT("V")) shouldEqual VariabilityTemplata(VaryingT)
    inferencesD.templatasByRune(CodeRuneT("N")) shouldEqual IntegerTemplata(4)
    inferencesD.templatasByRune(CodeRuneT("E")) shouldEqual CoordTemplata(CoordT(ShareT, ReadonlyT,IntT.i32))
  }

  test("Test matching array sequence as kind") {
    val (InferSolveSuccess(inferencesD)) =
      makeCannedEvaluator().solve(
        makeCannedEnvironment(),
        FakeState(),
        List(
          EqualsTR(RangeS.testZero,
            TemplexTR(NameTT(RangeS.testZero,CodeTypeNameA("MutStaticSizedArrayOf4Int"), KindTemplataType)),
            TemplexTR(
              RepeaterSequenceTT(RangeS.testZero,
                RuneTT(RangeS.testZero,CodeRuneT("M"), MutabilityTemplataType),
                RuneTT(RangeS.testZero,CodeRuneT("V"), VariabilityTemplataType),
                RuneTT(RangeS.testZero,CodeRuneT("N"), IntegerTemplataType),
                RuneTT(RangeS.testZero,CodeRuneT("E"), CoordTemplataType),
                KindTemplataType)))),
        RangeS.testZero,
        Map(CodeRuneT("E") -> CoordTemplataType),
        Set(CodeRuneT("E"), CodeRuneT("M"), CodeRuneT("V"), CodeRuneT("N")),
        Map(),
        Nil,
        None,
        true)
    inferencesD.templatasByRune(CodeRuneT("M")) shouldEqual MutabilityTemplata(MutableT)
    inferencesD.templatasByRune(CodeRuneT("V")) shouldEqual VariabilityTemplata(VaryingT)
    inferencesD.templatasByRune(CodeRuneT("N")) shouldEqual IntegerTemplata(4)
    inferencesD.templatasByRune(CodeRuneT("E")) shouldEqual CoordTemplata(CoordT(ShareT, ReadonlyT,IntT.i32))
  }

  test("Test evaluating manual sequence") {
    val (InferSolveSuccess(inferencesD)) =
      makeCannedEvaluator().solve(
        makeCannedEnvironment(),
        FakeState(),
        List(
          EqualsTR(RangeS.testZero,
            TemplexTR(RuneTT(RangeS.testZero,CodeRuneT("Z"), CoordTemplataType)),
            TemplexTR(
              ManualSequenceTT(RangeS.testZero,
                List(
                  NameTT(RangeS.testZero,CodeTypeNameA("int"), CoordTemplataType),
                  NameTT(RangeS.testZero,CodeTypeNameA("bool"), CoordTemplataType)),
                CoordTemplataType)))),
        RangeS.testZero,
        Map(CodeRuneT("Z") -> CoordTemplataType),
        Set(CodeRuneT("Z")),
        Map(),
        Nil,
        None,
        true)
    inferencesD.templatasByRune(CodeRuneT("Z")) shouldEqual
      CoordTemplata(
        CoordT(
          ShareT,
          ReadonlyT,
          TupleTT(
            List(CoordT(ShareT, ReadonlyT,IntT.i32), CoordT(ShareT, ReadonlyT,BoolT())),
            StructRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil,CitizenNameT("ImmStruct",Nil))))))
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
                  RuneTT(RangeS.testZero,CodeRuneT("A"), CoordTemplataType),
                  RuneTT(RangeS.testZero,CodeRuneT("B"), CoordTemplataType)),
                CoordTemplataType)))),
        RangeS.testZero,
        Map(),
        Set(CodeRuneT("A"), CodeRuneT("B")),
        Map(),
        Nil,
        None,
        true)
    inferencesD.templatasByRune(CodeRuneT("A")) shouldEqual CoordTemplata(CoordT(ShareT, ReadonlyT,IntT.i32))
    inferencesD.templatasByRune(CodeRuneT("B")) shouldEqual CoordTemplata(CoordT(ShareT, ReadonlyT,BoolT()))
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
                  RuneTT(RangeS.testZero,CodeRuneT("A"), CoordTemplataType),
                  RuneTT(RangeS.testZero,CodeRuneT("B"), CoordTemplataType)),
                KindTemplataType)))),
        RangeS.testZero,
        Map(),
        Set(CodeRuneT("A"), CodeRuneT("B")),
        Map(),
        Nil,
        None,
        true)
    inferencesD.templatasByRune(CodeRuneT("A")) shouldEqual CoordTemplata(CoordT(ShareT, ReadonlyT,IntT.i32))
    inferencesD.templatasByRune(CodeRuneT("B")) shouldEqual CoordTemplata(CoordT(ShareT, ReadonlyT,BoolT()))
  }

  test("Test array") {
    val (InferSolveSuccess(inferencesD)) =
      makeCannedEvaluator().solve(
        makeCannedEnvironment(),
        FakeState(),
        List(
          EqualsTR(RangeS.testZero,
            TemplexTR(RuneTT(RangeS.testZero,CodeRuneT("K"), KindTemplataType)),
            TemplexTR(
              CallTT(RangeS.testZero,
                NameTT(RangeS.testZero,CodeTypeNameA("Array"), TemplateTemplataType(List(MutabilityTemplataType, VariabilityTemplataType, CoordTemplataType), KindTemplataType)),
                List(MutabilityTT(RangeS.testZero,MutableP), VariabilityTT(RangeS.testZero,VaryingP), NameTT(RangeS.testZero,CodeTypeNameA("int"), CoordTemplataType)),
                KindTemplataType))),
          EqualsTR(RangeS.testZero,
            TemplexTR(RuneTT(RangeS.testZero,CodeRuneT("K"), KindTemplataType)),
            TemplexTR(
              CallTT(RangeS.testZero,
                NameTT(RangeS.testZero,CodeTypeNameA("Array"), TemplateTemplataType(List(MutabilityTemplataType, VariabilityTemplataType, CoordTemplataType), KindTemplataType)),
                List(
                  RuneTT(RangeS.testZero,CodeRuneT("M"), MutabilityTemplataType),
                  RuneTT(RangeS.testZero,CodeRuneT("V"), VariabilityTemplataType),
                  RuneTT(RangeS.testZero,CodeRuneT("T"), CoordTemplataType)),
                KindTemplataType)))),
        RangeS.testZero,
        Map(
          CodeRuneT("T") -> CoordTemplataType,
          CodeRuneT("M") -> MutabilityTemplataType,
          CodeRuneT("V") -> VariabilityTemplataType,
          CodeRuneT("K") -> KindTemplataType),
        Set(CodeRuneT("T"), CodeRuneT("M"), CodeRuneT("V"), CodeRuneT("K")),
        Map(),
        Nil,
        None,
        true)
    inferencesD.templatasByRune(CodeRuneT("M")) shouldEqual MutabilityTemplata(MutableT)
    inferencesD.templatasByRune(CodeRuneT("V")) shouldEqual VariabilityTemplata(VaryingT)
    inferencesD.templatasByRune(CodeRuneT("T")) shouldEqual CoordTemplata(CoordT(ShareT, ReadonlyT,IntT.i32))
  }

  test("Test evaluating isa") {
    val (InferSolveSuccess(_)) =
      makeCannedEvaluator()
        .solve(
          makeCannedEnvironment(),
          FakeState(),
          List(
            IsaTR(RangeS.testZero,
              TemplexTR(RuneTT(RangeS.testZero,CodeRuneT("K"), KindTemplataType)),
              TemplexTR(NameTT(RangeS.testZero,CodeTypeNameA("MutInterface"), KindTemplataType)))),
          RangeS.testZero,
          Map(CodeRuneT("K") -> KindTemplataType),
          Set(CodeRuneT("K")),
          Map(CodeRuneT("K") -> KindTemplata(StructRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("MutStruct", Nil))))),
          Nil,
          None,
          true)

    val (isf @ InferSolveFailure(_, _, _,_,_, _, _)) =
      makeCannedEvaluator()
        .solve(
          makeCannedEnvironment(),
          FakeState(),
          List(
            IsaTR(RangeS.testZero,
              TemplexTR(RuneTT(RangeS.testZero,CodeRuneT("K"), KindTemplataType)),
              TemplexTR(NameTT(RangeS.testZero,CodeTypeNameA("MutInterface"), KindTemplataType)))),
          RangeS.testZero,
          Map(CodeRuneT("K") -> KindTemplataType),
          Set(CodeRuneT("K")),
          Map(CodeRuneT("K") -> KindTemplata(StructRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("MutSoloStruct", Nil))))),
          Nil,
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
              TemplexTR(RuneTT(RangeS.testZero,CodeRuneT("K"), KindTemplataType)),
              IsaTR(RangeS.testZero,
                TemplexTR(RuneTT(RangeS.testZero,CodeRuneT("Z"), KindTemplataType)),
                TemplexTR(NameTT(RangeS.testZero,CodeTypeNameA("MutInterface"), KindTemplataType))))),
          RangeS.testZero,
          Map(CodeRuneT("K") -> KindTemplataType),
          Set(CodeRuneT("K"), CodeRuneT("Z")),
          Map(CodeRuneT("K") -> KindTemplata(StructRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("MutStruct", Nil))))),
          Nil,
          None,
          true)

    val (isf @ InferSolveFailure(_, _,_,_, _, _, _)) =
      makeCannedEvaluator()
        .solve(
          makeCannedEnvironment(),
          FakeState(),
          List(
            IsaTR(RangeS.testZero,
              TemplexTR(RuneTT(RangeS.testZero,CodeRuneT("K"), KindTemplataType)),
              TemplexTR(NameTT(RangeS.testZero,CodeTypeNameA("MutInterface"), KindTemplataType)))),
          RangeS.testZero,
          Map(CodeRuneT("K") -> KindTemplataType),
          Set(CodeRuneT("K")),
          Map(CodeRuneT("K") -> KindTemplata(StructRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("MutSoloStruct", Nil))))),
          Nil,
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
              TemplexTR(RuneTT(RangeS.testZero,CodeRuneT("F"),PrototypeTemplataType)))),
          RangeS.testZero,
          Map(CodeRuneT("F") -> PrototypeTemplataType),
          Set(CodeRuneT("F")),
          Map(),
          Nil,
          None,
          true)
    conclusions.templatasByRune(CodeRuneT("F")) shouldEqual PrototypeTemplata(incrementPrototype)
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
                        RuneTT(RangeS.testZero,CodeRuneT("T"),CoordTemplataType)))),
                  EqualsTR(RangeS.testZero,
                    TemplexTR(NameTT(RangeS.testZero,CodeTypeNameA("int"),CoordTemplataType)),
                    TemplexTR(RuneTT(RangeS.testZero,CodeRuneT("T"),CoordTemplataType))))),
              TemplexTR(RuneTT(RangeS.testZero,CodeRuneT("F"),PrototypeTemplataType)))),
          RangeS.testZero,
          Map(CodeRuneT("T") -> CoordTemplataType, CodeRuneT("F") -> PrototypeTemplataType),
          Set(CodeRuneT("T"), CodeRuneT("F")),
          Map(),
          Nil,
          None,
          true)
    conclusions.templatasByRune(CodeRuneT("F")) shouldEqual PrototypeTemplata(incrementPrototype)
  }

  test("Test match prototype components") {
    val (InferSolveSuccess(conclusions)) =
      makeCannedEvaluator()
        .solve(
          makeCannedEnvironment(),
          FakeState(),
          List(
            EqualsTR(RangeS.testZero,
              TemplexTR(RuneTT(RangeS.testZero,CodeRuneT("F"),PrototypeTemplataType)),
              ComponentsTR(
                RangeS.internal(-106),
                PrototypeTemplataType,
                List(
                  TemplexTR(RuneTT(RangeS.testZero,CodeRuneT("X"),StringTemplataType)),
                  TemplexTR(RuneTT(RangeS.testZero,CodeRuneT("Y"),PackTemplataType(CoordTemplataType))),
                  TemplexTR(RuneTT(RangeS.testZero,CodeRuneT("T"),CoordTemplataType)))))),
          RangeS.testZero,
          Map(CodeRuneT("X") -> StringTemplataType, CodeRuneT("Y") -> PackTemplataType(CoordTemplataType), CodeRuneT("T") -> CoordTemplataType, CodeRuneT("F") -> PrototypeTemplataType),
          Set(CodeRuneT("X"), CodeRuneT("Y"), CodeRuneT("T"), CodeRuneT("F")),
          Map(CodeRuneT("F") -> PrototypeTemplata(incrementPrototype)),
          Nil,
          None,
          true)
    conclusions.templatasByRune(CodeRuneT("X")) shouldEqual StringTemplata("increment")
    conclusions.templatasByRune(CodeRuneT("Y")) shouldEqual CoordListTemplata(List(CoordT(ShareT, ReadonlyT, IntT.i32)))
    conclusions.templatasByRune(CodeRuneT("T")) shouldEqual CoordTemplata(CoordT(ShareT, ReadonlyT, IntT.i32))
  }

  test("Test InterpretedTT") {
    def run(sourceName: String, targetOwnership: OwnershipP, targetPermission: PermissionP): IInferSolveResult = {
      val result =
        makeCannedEvaluator().solve(
          makeCannedEnvironment(),
          FakeState(),
          List(
            EqualsTR(RangeS.testZero,
              TemplexTR(InterpretedTT(RangeS.testZero,targetOwnership,targetPermission, NameTT(RangeS.testZero,CodeTypeNameA(sourceName), CoordTemplataType))),
              TemplexTR(RuneTT(RangeS.testZero,CodeRuneT("T"), CoordTemplataType)))),
          RangeS.testZero,
          Map(CodeRuneT("T") -> CoordTemplataType),
          Set(CodeRuneT("T")),
          Map(),
          List(AtomAP(RangeS.testZero,Some(LocalA(CodeVarNameA("this"), NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed)),None,CodeRuneA("T"),None)),
          None,
          true)
      result
    }

    def expectSuccess(inferSolveResult: IInferSolveResult): CoordT = {
      val InferSolveSuccess(inferencesD) = inferSolveResult
      val CoordTemplata(coord) = inferencesD.templatasByRune(CodeRuneT("T"))
      coord
    }

    def expectFail(inferSolveResult: IInferSolveResult): String = {
      val isf @ InferSolveFailure(_, _, _, _, _, _, _) = inferSolveResult
      isf.toString
    }

    // Dont need to test Own + Readonly, because its impossible to express that with an InterpretedTT rule.
    expectSuccess(run("int", OwnP, ReadwriteP)) shouldEqual CoordT(ShareT, ReadonlyT, IntT.i32)
    expectSuccess(run("int", ConstraintP, ReadonlyP)) shouldEqual CoordT(ShareT, ReadonlyT, IntT.i32)
    expectSuccess(run("int", ConstraintP, ReadwriteP)) shouldEqual CoordT(ShareT, ReadonlyT, IntT.i32)
    vassert(expectFail(run("int", WeakP, ReadonlyP)).contains("Expected a weak, but was a share"))
    vassert(expectFail(run("int", WeakP, ReadwriteP)).contains("Expected a weak, but was a share"))
    expectSuccess(run("int", ShareP, ReadonlyP)) shouldEqual CoordT(ShareT, ReadonlyT, IntT.i32)
    expectSuccess(run("int", ShareP, ReadwriteP)) shouldEqual CoordT(ShareT, ReadonlyT, IntT.i32)

    vassert(expectFail(run("MutStruct", ShareP, ReadonlyP)).contains("Expected a share, but was an own"))
    expectSuccess(run("MutStruct", OwnP, ReadwriteP)) shouldEqual CoordT(OwnT,ReadwriteT, StructRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("MutStruct", Nil))))
    expectSuccess(run("MutStruct", ConstraintP, ReadonlyP)) shouldEqual CoordT(ConstraintT,ReadonlyT, StructRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("MutStruct", Nil))))
    expectSuccess(run("MutStruct", ConstraintP, ReadwriteP)) shouldEqual CoordT(ConstraintT,ReadwriteT, StructRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("MutStruct", Nil))))
    expectSuccess(run("MutStruct", WeakP, ReadonlyP)) shouldEqual CoordT(WeakT, ReadonlyT, StructRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("MutStruct", Nil))))
    expectSuccess(run("MutStruct", WeakP, ReadwriteP)) shouldEqual CoordT(WeakT, ReadwriteT, StructRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("MutStruct", Nil))))

    vassert(expectFail(run("MutStructConstraint", ShareP, ReadonlyP)).contains("Expected a share, but was a borrow"))
    expectSuccess(run("MutStructConstraint", OwnP, ReadwriteP)) shouldEqual CoordT(OwnT,ReadwriteT, StructRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("MutStruct", Nil))))
    expectSuccess(run("MutStructConstraint", ConstraintP, ReadonlyP)) shouldEqual CoordT(ConstraintT,ReadonlyT, StructRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("MutStruct", Nil))))
    // &!T given a &Spaceship should give a &T, it should make the ro into a Readwrite.
    expectSuccess(run("MutStructConstraint", ConstraintP, ReadwriteP)) shouldEqual CoordT(ConstraintT,ReadwriteT, StructRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("MutStruct", Nil))))
    expectSuccess(run("MutStructConstraint", WeakP, ReadonlyP)) shouldEqual CoordT(WeakT, ReadonlyT, StructRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("MutStruct", Nil))))
    // &&!T given a &Spaceship should give a &T, it should make the ro into a Readwrite, and the borrow into a weak.
    expectSuccess(run("MutStructConstraint", WeakP, ReadwriteP)) shouldEqual CoordT(WeakT, ReadwriteT, StructRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("MutStruct", Nil))))

    vassert(expectFail(run("MutStructConstraintRW", ShareP, ReadonlyP)).contains("Expected a share, but was a borrow"))
    expectSuccess(run("MutStructConstraintRW", OwnP, ReadwriteP)) shouldEqual CoordT(OwnT,ReadwriteT, StructRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("MutStruct", Nil))))
    // &T given a &!Spaceship should give a &T, it should make the Readwrite into a Readonly.
    expectSuccess(run("MutStructConstraintRW", ConstraintP, ReadonlyP)) shouldEqual CoordT(ConstraintT,ReadonlyT, StructRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("MutStruct", Nil))))
    expectSuccess(run("MutStructConstraintRW", ConstraintP, ReadwriteP)) shouldEqual CoordT(ConstraintT,ReadwriteT, StructRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("MutStruct", Nil))))
    expectSuccess(run("MutStructConstraintRW", WeakP, ReadonlyP)) shouldEqual CoordT(WeakT, ReadonlyT, StructRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("MutStruct", Nil))))
    // &&T given a &!Spaceship should give a &T, it should make the Readwrite into a Readonly, and the borrow into a weak.
    expectSuccess(run("MutStructConstraintRW", WeakP, ReadwriteP)) shouldEqual CoordT(WeakT, ReadwriteT, StructRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("MutStruct", Nil))))

    vassert(expectFail(run("MutStructWeak", ShareP, ReadonlyP)).contains("Expected a share, but was a weak"))
    vassert(expectFail(run("MutStructWeak", OwnP, ReadwriteP)).contains("Expected a own, but was a weak"))
    vassert(expectFail(run("MutStructWeak", ConstraintP, ReadonlyP)).contains("Expected a borrow, but was a weak"))
    vassert(expectFail(run("MutStructWeak", ConstraintP, ReadwriteP)).contains("Expected a borrow, but was a weak"))
    expectSuccess(run("MutStructWeak", WeakP, ReadonlyP)) shouldEqual CoordT(WeakT, ReadonlyT, StructRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("MutStruct", Nil))))
    expectSuccess(run("MutStructWeak", WeakP, ReadwriteP)) shouldEqual CoordT(WeakT, ReadwriteT, StructRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("MutStruct", Nil))))

    vassert(expectFail(run("MutStructWeakRW", ShareP, ReadonlyP)).contains("Expected a share, but was a weak"))
    vassert(expectFail(run("MutStructWeakRW", OwnP, ReadwriteP)).contains("Expected a own, but was a weak"))
    vassert(expectFail(run("MutStructWeakRW", ConstraintP, ReadonlyP)).contains("Expected a borrow, but was a weak"))
    vassert(expectFail(run("MutStructWeakRW", ConstraintP, ReadwriteP)).contains("Expected a borrow, but was a weak"))
    expectSuccess(run("MutStructWeakRW", WeakP, ReadonlyP)) shouldEqual CoordT(WeakT, ReadonlyT, StructRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("MutStruct", Nil))))
    expectSuccess(run("MutStructWeakRW", WeakP, ReadwriteP)) shouldEqual CoordT(WeakT, ReadwriteT, StructRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("MutStruct", Nil))))

    expectSuccess(run("void", ShareP, ReadonlyP)) shouldEqual CoordT(ShareT, ReadonlyT, VoidT())
    expectSuccess(run("void", OwnP, ReadwriteP)) shouldEqual CoordT(ShareT, ReadonlyT, VoidT())
    expectSuccess(run("void", ConstraintP, ReadonlyP)) shouldEqual CoordT(ShareT, ReadonlyT, VoidT())
    expectSuccess(run("void", ConstraintP, ReadwriteP)) shouldEqual CoordT(ShareT, ReadonlyT, VoidT())
    vassert(expectFail(run("void", WeakP, ReadonlyP)).contains("Expected a weak, but was a share"))
    vassert(expectFail(run("void", WeakP, ReadwriteP)).contains("Expected a weak, but was a share"))
  }

  test("test matching ownershipped") {
    def run(sourceName: String, targetOwnership: OwnershipP, targetPermission: PermissionP): IInferSolveResult = {
      val result =
        makeCannedEvaluator().solve(
          makeCannedEnvironment(),
          FakeState(),
          List(
            EqualsTR(RangeS.testZero,
              TemplexTR(NameTT(RangeS.testZero,CodeTypeNameA(sourceName), CoordTemplataType)),
              TemplexTR(InterpretedTT(RangeS.testZero,targetOwnership,targetPermission, RuneTT(RangeS.testZero,CodeRuneT("T"), CoordTemplataType))))),
          RangeS.testZero,
          Map(CodeRuneT("T") -> CoordTemplataType),
          Set(CodeRuneT("T")),
          Map(),
          Nil,
          None,
          true)
      result
    }

    def expectSuccess(inferSolveResult: IInferSolveResult): CoordT = {
      val InferSolveSuccess(inferencesD) = inferSolveResult
      val CoordTemplata(coord) = inferencesD.templatasByRune(CodeRuneT("T"))
      coord
    }

    def expectFail(inferSolveResult: IInferSolveResult): String = {
      val isf @ InferSolveFailure(_, _, _, _, _, _, _) = inferSolveResult
      isf.toString
    }

    expectSuccess(run("int", OwnP, ReadwriteP)) shouldEqual CoordT(ShareT, ReadonlyT, IntT.i32)
    expectSuccess(run("int", ConstraintP, ReadonlyP)) shouldEqual CoordT(ShareT, ReadonlyT, IntT.i32)
    expectSuccess(run("int", ConstraintP, ReadwriteP)) shouldEqual CoordT(ShareT, ReadonlyT, IntT.i32)
    vassert(expectFail(run("int", WeakP, ReadonlyP)).contains("Couldn't match incoming share against expected weak"))
    vassert(expectFail(run("int", WeakP, ReadwriteP)).contains("Couldn't match incoming share against expected weak"))
    expectSuccess(run("int", ShareP, ReadonlyP)) shouldEqual CoordT(ShareT, ReadonlyT, IntT.i32)

    expectSuccess(run("void", OwnP, ReadwriteP)) shouldEqual CoordT(ShareT, ReadonlyT, VoidT())
    expectSuccess(run("void", ConstraintP, ReadonlyP)) shouldEqual CoordT(ShareT, ReadonlyT, VoidT())
    expectSuccess(run("void", ConstraintP, ReadwriteP)) shouldEqual CoordT(ShareT, ReadonlyT, VoidT())
    vassert(expectFail(run("void", WeakP, ReadonlyP)).contains("Couldn't match incoming share against expected weak"))
    vassert(expectFail(run("void", WeakP, ReadwriteP)).contains("Couldn't match incoming share against expected weak"))
    expectSuccess(run("void", ShareP, ReadonlyP)) shouldEqual CoordT(ShareT, ReadonlyT, VoidT())

    vassert(expectFail(run("MutStruct", ShareP, ReadonlyP)).contains("Couldn't match incoming own against expected share"))
    // Takes the own off the incoming own coord, ends up as another own.
    expectSuccess(run("MutStruct", OwnP, ReadwriteP)) shouldEqual CoordT(OwnT,ReadwriteT, StructRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("MutStruct", Nil))))
    // Tries to take the borrow off the incoming own coord... fails.
    vassert(expectFail(run("MutStruct", ConstraintP, ReadonlyP)).contains("Couldn't match incoming own against expected constraint"))
    vassert(expectFail(run("MutStruct", ConstraintP, ReadwriteP)).contains("Couldn't match incoming own against expected constraint"))
    vassert(expectFail(run("MutStruct", WeakP, ReadonlyP)).contains("Couldn't match incoming own against expected weak"))
    vassert(expectFail(run("MutStruct", WeakP, ReadwriteP)).contains("Couldn't match incoming own against expected weak"))

    // Tries to take the own off the incoming borrow coord... fails.
    vassert(expectFail(run("MutStructConstraint", OwnP, ReadwriteP)).contains("Couldn't match incoming constraint against expected own"))
    // Takes the borrow off the incoming borrow coord, succeeds and gives us an own.
    expectSuccess(run("MutStructConstraint", ConstraintP, ReadonlyP)) shouldEqual CoordT(OwnT,ReadwriteT, StructRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("MutStruct", Nil))))
    vassert(expectFail(run("MutStructConstraint", ConstraintP, ReadwriteP)).contains("Couldn't match incoming ro against expected rw"))
    // Takes the weak off the incoming borrow coord... fails.
    vassert(expectFail(run("MutStructConstraint", WeakP, ReadonlyP)).contains("Couldn't match incoming constraint against expected weak"))
    vassert(expectFail(run("MutStructConstraint", WeakP, ReadwriteP)).contains("Couldn't match incoming constraint against expected weak"))
    vassert(expectFail(run("MutStructConstraint", ShareP, ReadonlyP)).contains("Couldn't match incoming constraint against expected share"))

    // Tries to take the own off the incoming borrow coord... fails.
    vassert(expectFail(run("MutStructConstraintRW", OwnP, ReadwriteP)).contains("Couldn't match incoming constraint against expected own"))
    vassert(expectFail(run("MutStructConstraintRW", ConstraintP, ReadonlyP)).contains("Couldn't match incoming rw against expected ro"))
    // Takes the borrow off the incoming borrow coord, succeeds and gives us an own.
    expectSuccess(run("MutStructConstraintRW", ConstraintP, ReadwriteP)) shouldEqual CoordT(OwnT,ReadwriteT,StructRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil,CitizenNameT("MutStruct",Nil))))
    // Takes the weak off the incoming borrow coord... fails.
    vassert(expectFail(run("MutStructConstraintRW", WeakP, ReadonlyP)).contains("Couldn't match incoming constraint against expected weak"))
    vassert(expectFail(run("MutStructConstraintRW", WeakP, ReadwriteP)).contains("Couldn't match incoming constraint against expected weak"))
    vassert(expectFail(run("MutStructConstraintRW", ShareP, ReadonlyP)).contains("Couldn't match incoming constraint against expected share"))

    // Tries to take the own off the incoming weak coord... fails.
    vassert(expectFail(run("MutStructWeak", OwnP, ReadwriteP)).contains("Couldn't match incoming weak against expected own"))
    // Takes the borrow off the incoming weak coord... fails.
    vassert(expectFail(run("MutStructWeak", ConstraintP, ReadonlyP)).contains("Couldn't match incoming weak against expected constraint"))
    vassert(expectFail(run("MutStructWeak", ConstraintP, ReadwriteP)).contains("Couldn't match incoming weak against expected constraint"))
    // Takes the weak off the incoming weak coord, succeeds and gives us an own.
    expectSuccess(run("MutStructWeak", WeakP, ReadonlyP)) shouldEqual CoordT(OwnT,ReadwriteT, StructRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("MutStruct", Nil))))
    vassert(expectFail(run("MutStructWeak", WeakP, ReadwriteP)).contains("Couldn't match incoming ro against expected rw"))
    vassert(expectFail(run("MutStructWeak", ShareP, ReadonlyP)).contains("Couldn't match incoming weak against expected share"))

    // Tries to take the own off the incoming weak coord... fails.
    vassert(expectFail(run("MutStructWeakRW", OwnP, ReadwriteP)).contains("Couldn't match incoming weak against expected own"))
    // Takes the borrow off the incoming weak coord... fails.
    vassert(expectFail(run("MutStructWeakRW", ConstraintP, ReadonlyP)).contains("Couldn't match incoming weak against expected constraint"))
    vassert(expectFail(run("MutStructWeakRW", ConstraintP, ReadwriteP)).contains("Couldn't match incoming weak against expected constraint"))
    vassert(expectFail(run("MutStructWeakRW", WeakP, ReadonlyP)).contains("Couldn't match incoming rw against expected ro"))
    // Takes the weak off the incoming weak coord, succeeds and gives us an own.
    expectSuccess(run("MutStructWeakRW", WeakP, ReadwriteP)) shouldEqual CoordT(OwnT,ReadwriteT, StructRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("MutStruct", Nil))))
    vassert(expectFail(run("MutStructWeakRW", ShareP, ReadonlyP)).contains("Couldn't match incoming weak against expected share"))
  }
}
