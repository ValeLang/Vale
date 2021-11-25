package net.verdagon.vale.templar.infer

import net.verdagon.vale.astronomer._
import net.verdagon.vale.scout.rules.{IRulexSR, RuneUsage}
import net.verdagon.vale.solver.IncompleteSolve
import net.verdagon.vale.templar.OverloadTemplar.{InferFailure, FindFunctionFailure}
import net.verdagon.vale.templar.ast.{ProgramT, PrototypeT}
import net.verdagon.vale.templar.names.{CitizenNameT, CitizenTemplateNameT, FullNameT, FunctionNameT, INameT, PackageTopLevelNameT, PrimitiveNameT}
import net.verdagon.vale.templar.{CompileErrorExceptionT, CouldntFindFunctionToCallT, TemplarTestCompilation}
import net.verdagon.vale.{CodeLocationS, Err, RangeS, vassertOne}
//import net.verdagon.vale.astronomer.ruletyper.IRuleTyperEvaluatorDelegate
import net.verdagon.vale.astronomer.{InterfaceA, StructA}
import net.verdagon.vale.parser._
import net.verdagon.vale.scout.rules.{LiteralSR, LookupSR, MutabilityLiteralSL}
import net.verdagon.vale.scout.{IEnvironment => _, _}
import net.verdagon.vale.{IProfiler, NullProfiler, PackageCoordinate, vassert, vassertSome, vfail, vimpl, scout => s}
import net.verdagon.vale.templar.env._
//import net.verdagon.vale.templar.infer.{InfererEquator, InfererEvaluator}
//import net.verdagon.vale.templar.infer.infer.{IInferSolveResult, InferSolveFailure, InferSolveSuccess}
import net.verdagon.vale.templar.templata._
//import org.scalamock.scalatest.MockFactory
import net.verdagon.vale.templar.types._
import org.scalatest.{FunSuite, Matchers}

import scala.collection.immutable.List

case class FakeEnv() { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
case class FakeState() { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }

case class SimpleEnvironment(templatas: TemplatasStore) extends IEnvironment {
//  override def localNamespaces: List[TemplatasStore] = vimpl()
  override def globalEnv: GlobalEnvironment = vimpl()
//  override def globalNamespaces: Vector[TemplatasStore] = vimpl()

  def fullName = FullNameT(PackageCoordinate.BUILTIN, Vector(), PackageTopLevelNameT())
  def lookupWithImpreciseName(
    profiler: IProfiler,
    nameS: IImpreciseNameS,
    lookupFilter: Set[ILookupContext],
    getOnlyNearest: Boolean):
  Iterable[ITemplata] = {
    vimpl()
//    templatas.lookupWithImpreciseName(profiler, this, nameS, lookupFilter, getOnlyNearest)
  }

  def lookupWithName(
    profiler: IProfiler,
    nameS: INameT,
    lookupFilter: Set[ILookupContext],
    getOnlyNearest: Boolean):
  Iterable[ITemplata] = {
    vimpl()
//    templatas.lookupWithName(profiler, this, nameS, lookupFilter, getOnlyNearest)
  }

  def lookupWithImpreciseNameInner(profiler: net.verdagon.vale.IProfiler,nameS: net.verdagon.vale.scout.IImpreciseNameS,lookupFilter: scala.collection.immutable.Set[net.verdagon.vale.templar.env.ILookupContext],getOnlyNearest: Boolean): Iterable[net.verdagon.vale.templar.templata.ITemplata] = vimpl()
  def lookupWithNameInner(profiler: net.verdagon.vale.IProfiler,nameS: net.verdagon.vale.templar.names.INameT,lookupFilter: scala.collection.immutable.Set[net.verdagon.vale.templar.env.ILookupContext],getOnlyNearest: Boolean): Iterable[net.verdagon.vale.templar.templata.ITemplata] = vimpl()

}

class FakeTemplataTemplarDelegate extends IInfererDelegate[SimpleEnvironment, FakeState] {
//  override def lookupTemplata(profiler: IProfiler, env: SimpleEnvironment, range: RangeS, name: INameS): ITemplata = {
//    val results = env.getAllTemplatasWithName(profiler, name, Set(TemplataLookupContext))
//    vassert(results.size == 1)
//    results.headgetAncestorInterfaces
//  }

  override def coerce(env: SimpleEnvironment, state: FakeState, range: RangeS, toType: ITemplataType, templata: ITemplata): ITemplata = {
    if (templata.tyype == toType) {
      templata
    } else {
      vimpl()
    }
  }

  override def isDescendant(env: SimpleEnvironment, state: FakeState, kind: KindT): Boolean = {
    vimpl()
  }

  override def isAncestor(env: SimpleEnvironment, state: FakeState, kind: KindT): Boolean = {
    vimpl()
  }

  override def getMutability(state: FakeState, kind: KindT): MutabilityT = {
    kind match {
      case StructTT(FullNameT(_, _, CitizenNameT(CitizenTemplateNameT(humanName), _))) if humanName.startsWith("Mut") => MutableT
      case StructTT(FullNameT(_, _, CitizenNameT(CitizenTemplateNameT(humanName), _))) if humanName.startsWith("Imm") => ImmutableT
      case InterfaceTT(FullNameT(_, _, CitizenNameT(CitizenTemplateNameT(humanName), _))) if humanName.startsWith("Mut") => MutableT
      case InterfaceTT(FullNameT(_, _, CitizenNameT(CitizenTemplateNameT(humanName), _))) if humanName.startsWith("Imm") => ImmutableT
      case IntT(_) | VoidT() | BoolT() => ImmutableT
      case StaticSizedArrayTT(_, RawArrayTT(_, mutability, _)) => mutability
      case RuntimeSizedArrayTT(RawArrayTT(_, mutability, _)) => mutability
//      case TupleTT(_, StructTT(FullNameT(_, _, CitizenNameT(humanName, _)))) if humanName.startsWith("Imm") => ImmutableT
      case _ => vfail()
    }
  }
  override def evaluateInterfaceTemplata(state: FakeState, callRange: RangeS, templata: InterfaceTemplata, templateArgs: Vector[ITemplata]): (KindT) = {
    (templata, templateArgs) match {
      case (InterfaceTemplata(_,interfaceName(TopLevelCitizenDeclarationNameS("MutTInterface", _))), Vector(CoordTemplata(CoordT(ShareT, ReadonlyT, IntT.i32)) )) => {
        InterfaceTT(FullNameT(PackageCoordinate.TEST_TLD, Vector(), CitizenNameT(CitizenTemplateNameT("MutTInterface"), Vector(CoordTemplata(CoordT(ShareT, ReadonlyT, IntT.i32))))))
      }
      case (InterfaceTemplata(_,interfaceName(TopLevelCitizenDeclarationNameS("MutInterface", _))), Vector()) => {
        InterfaceTT(FullNameT(PackageCoordinate.TEST_TLD, Vector(), CitizenNameT(CitizenTemplateNameT("MutInterface"), Vector())))
      }
      case (InterfaceTemplata(_,interfaceName(TopLevelCitizenDeclarationNameS("ImmInterface", _))), Vector()) => {
        InterfaceTT(FullNameT(PackageCoordinate.TEST_TLD, Vector(), CitizenNameT(CitizenTemplateNameT("ImmInterface"), Vector())))
      }
    }
  }

  override def evaluateStructTemplata(state: FakeState, callRange: RangeS, templata: StructTemplata, templateArgs: Vector[ITemplata]): (KindT) = {
    (templata, templateArgs) match {
      case (StructTemplata(_,structName(TopLevelCitizenDeclarationNameS("MutTStruct", _))), Vector(CoordTemplata(CoordT(ShareT, ReadonlyT, IntT.i32)) )) => {
        StructTT(FullNameT(PackageCoordinate.TEST_TLD, Vector(), CitizenNameT(CitizenTemplateNameT("MutTStruct"), Vector(CoordTemplata(CoordT(ShareT, ReadonlyT, IntT.i32))))))
      }
      case (StructTemplata(_,structName(TopLevelCitizenDeclarationNameS("MutStruct", _))), Vector()) => {
        StructTT(FullNameT(PackageCoordinate.TEST_TLD, Vector(), CitizenNameT(CitizenTemplateNameT("MutStruct"), Vector())))
      }
    }
  }
  override def getInterfaceTemplataType(it: InterfaceTemplata): TemplateTemplataType = {
    it match {
      case InterfaceTemplata(_,interfaceName(TopLevelCitizenDeclarationNameS("MutTInterface", _))) => {
        TemplateTemplataType(Vector(CoordTemplataType), KindTemplataType)
      }
      case InterfaceTemplata(_, interfaceName(TopLevelCitizenDeclarationNameS("MutInterface", _))) => vfail()
    }
  }
  override def getStructTemplataType(it: StructTemplata): TemplateTemplataType = {
    it match {
      case StructTemplata(_,structName(TopLevelCitizenDeclarationNameS("MutTStruct", _))) => TemplateTemplataType(Vector(CoordTemplataType), KindTemplataType)
    }
  }
  override def getStaticSizedArrayKind(env: SimpleEnvironment, state: FakeState, mutability: MutabilityT, variability: VariabilityT, size: Int, element: CoordT): (StaticSizedArrayTT) = {
    (StaticSizedArrayTT(size, RawArrayTT(element, mutability, variability)))
  }

//  override def getTupleKind(env: SimpleEnvironment, state: FakeState, elements: Vector[CoordT]): TupleTT = {
//    // Theres only one tuple in this test, and its backed by the ImmStruct.
//    TupleTT(elements, StructTT(FullNameT(PackageCoordinate.TEST_TLD, Vector(), CitizenNameT(CitizenTemplateNameT("ImmStruct"), Vector()))))
//  }

//  override def getAncestorInterfaces(state: FakeState, descendantCitizenRef: CitizenRefT): (Set[InterfaceTT]) = {
//    descendantCitizenRef match {
//      case StructTT(FullNameT(PackageCoordinate.TEST_TLD, Vector(), CitizenNameT(CitizenTemplateNameT("MutTStruct"),Vector(CoordTemplata(CoordT(ShareT, ReadonlyT, IntT.i32)))))) => Set(InterfaceTT(FullNameT(PackageCoordinate.TEST_TLD, Vector(), CitizenNameT(CitizenTemplateNameT("MutTInterface"), Vector(CoordTemplata(CoordT(ShareT, ReadonlyT, IntT.i32)))))))
//      case StructTT(FullNameT(PackageCoordinate.TEST_TLD, Vector(), CitizenNameT(CitizenTemplateNameT("MutStruct"),Vector()))) => Set(InterfaceTT(FullNameT(PackageCoordinate.TEST_TLD, Vector(), CitizenNameT(CitizenTemplateNameT("MutInterface"), Vector()))))
//      case InterfaceTT(FullNameT(PackageCoordinate.TEST_TLD, Vector(), CitizenNameT(CitizenTemplateNameT("MutInterface"),Vector()))) => Set()
//      case StructTT(FullNameT(PackageCoordinate.TEST_TLD, Vector(), CitizenNameT(CitizenTemplateNameT("MutSoloStruct"),Vector()))) => Set()
//      case _ => vfail(descendantCitizenRef.toString)
//    }
//  }
//
//  override def getAncestorInterfaceDistance(temputs: FakeState, descendantCitizenRef: CitizenRefT, ancestorInterfaceRef: InterfaceTT): (Option[Int]) = {
//    vfail()
//  }

  override def kindIsFromTemplate(state: FakeState, actualCitizenRef: KindT, expectedCitizenTemplata: ITemplata): Boolean = {
    vimpl()
  }
  override def getAncestors(temputs: FakeState, descendant: KindT, includeSelf: Boolean): Set[KindT] = {
    vimpl()
  }

  override def lookupMemberTypes(state: FakeState, kind: KindT, expectedNumMembers: Int): Option[Vector[CoordT]] = {
    vfail()
  }

  override def getMemberCoords(state: FakeState, structTT: StructTT): Vector[CoordT] = {
    vfail()
  }

  override def structIsClosure(state: FakeState, structTT: StructTT): Boolean = {
    vfail()
  }

  override def lookupTemplata(env: SimpleEnvironment, state: FakeState, range: RangeS, name: INameT): ITemplata = {
    vassertOne(env.lookupWithName(new NullProfiler(), name, Set(TemplataLookupContext), true))
  }

  override def lookupTemplataImprecise(env: SimpleEnvironment, state: FakeState, range: RangeS, name: IImpreciseNameS): Option[ITemplata] = {
    val results = env.lookupWithImpreciseName(new NullProfiler(), name, Set(TemplataLookupContext), true)
    if (results.size > 1) {
      vfail()
    }
    results.headOption
  }

  override def resolveExactSignature(env: SimpleEnvironment, state: FakeState, range: RangeS, name: String, coords: Vector[CoordT]): PrototypeT = {
    val templatas = env.lookupWithImpreciseName(new NullProfiler(), CodeNameS(name), Set(TemplataLookupContext), false)
    val prototypes = templatas.collect({ case PrototypeTemplata(prot) => prot })
    val matchingPrototypes = prototypes.filter(_.paramTypes == coords)
    vassert(matchingPrototypes.size == 1)
    matchingPrototypes.head
  }

  override def getRuntimeSizedArrayKind(env: SimpleEnvironment, state: FakeState, type2: CoordT, arrayMutability: MutabilityT, arrayVariability: VariabilityT): RuntimeSizedArrayTT = {
    RuntimeSizedArrayTT(RawArrayTT(type2, arrayMutability, arrayVariability))
  }
}

class InfererTests extends FunSuite with Matchers {
  val incrementPrototype =
    PrototypeT(FullNameT(PackageCoordinate.TEST_TLD, Vector(), FunctionNameT("increment", Vector(), Vector(CoordT(ShareT, ReadonlyT, IntT.i32)))), CoordT(ShareT, ReadonlyT, IntT.i32))

  def makeCannedEnvironment(): SimpleEnvironment = {
    // FullNameT(PackageCoordinate.BUILTIN, Vector(), PackageTopLevelNameT()),
    var entries: TemplatasStore = TemplatasStore(ProgramT.topLevelName, Map(), Map())
    val voidName = PrimitiveNameT("void")
    entries = entries.addEntry(voidName, TemplataEnvEntry(KindTemplata(VoidT())))
    val intName = PrimitiveNameT("int")
    entries = entries.addEntry(intName, TemplataEnvEntry(KindTemplata(IntT.i32)))
    val boolName = PrimitiveNameT("bool")
    entries = entries.addEntry(boolName, TemplataEnvEntry(KindTemplata(BoolT())))
    entries = entries.addEntry(
      CitizenNameT(CitizenTemplateNameT("ImmInterface"), Vector()),
        InterfaceEnvEntry(
          InterfaceA(
            RangeS.internal(-70),
            TopLevelCitizenDeclarationNameS("ImmInterface", RangeS.internal(-24)),
            Vector(),
            false,
            RuneUsage(RangeS.internal(-70001), CodeRuneS("M")),
            Some(ImmutableP),
            KindTemplataType,
            Vector(),
            Map(CodeRuneS("M") -> MutabilityTemplataType),
            Vector(LiteralSR(RangeS.testZero,RuneUsage(RangeS.internal(-70001), CodeRuneS("M")),MutabilityLiteralSL(ImmutableP))),
            Vector())))
    entries = entries.addEntry(
      CitizenNameT(CitizenTemplateNameT("ImmStruct"), Vector()),
        StructEnvEntry(
          StructA(
            RangeS.internal(-71),
            TopLevelCitizenDeclarationNameS("ImmStruct", RangeS.internal(-24)),
            Vector(),
            false,
            RuneUsage(RangeS.internal(-70001), CodeRuneS("M")),
            Some(ImmutableP),
            KindTemplataType,
            Vector(),
            Map(CodeRuneS("M") -> MutabilityTemplataType, CodeRuneS("I") -> CoordTemplataType, CodeRuneS("B") -> CoordTemplataType),
            Vector(
              LiteralSR(RangeS.testZero,RuneUsage(RangeS.internal(-70001), CodeRuneS("M")), MutabilityLiteralSL(ImmutableP)),
              LookupSR(RangeS.testZero,RuneUsage(RangeS.internal(-70001), CodeRuneS("I")), CodeNameS("int")),
              LookupSR(RangeS.testZero,RuneUsage(RangeS.internal(-70001), CodeRuneS("B")), CodeNameS("bool"))),
            Vector(
              NormalStructMemberS(RangeS.testZero,"i", FinalP, RuneUsage(RangeS.internal(-70001), CodeRuneS("I"))),
              NormalStructMemberS(RangeS.testZero,"i", FinalP, RuneUsage(RangeS.internal(-70001), CodeRuneS("B")))))))
    entries = entries.addEntry(PrimitiveNameT("Array"), TemplataEnvEntry(RuntimeSizedArrayTemplateTemplata()))
    entries = entries.addEntry(
        CitizenTemplateNameT("MutTStruct"),//, CodeLocationS.internal(-25)),
          StructEnvEntry(
            StructA(
              RangeS.internal(-74),
              TopLevelCitizenDeclarationNameS("MutTStruct", RangeS.internal(-26)),
              Vector(),
              false,
              RuneUsage(RangeS.internal(-70001), CodeRuneS("M")),
              Some(MutableP),
              TemplateTemplataType(Vector(CoordTemplataType), KindTemplataType),
//              Set(CodeRuneS("M")),
              Vector(RuneUsage(RangeS.internal(-70001), CodeRuneS("T"))),
//              Set(CodeRuneS("T"), CodeRuneS("M")),
              Map(CodeRuneS("T") -> CoordTemplataType, CodeRuneS("M") -> MutabilityTemplataType),
              Vector(LiteralSR(RangeS.testZero,RuneUsage(RangeS.internal(-70001), CodeRuneS("M")), MutabilityLiteralSL(MutableP))),
              Vector())))
    entries = entries.addEntry(CitizenTemplateNameT("MutTInterface"),//, CodeLocationS.internal(-27)),
      InterfaceEnvEntry(
        InterfaceA(
          RangeS.internal(-75),
          TopLevelCitizenDeclarationNameS("MutTInterface", RangeS.internal(-28)),
          Vector(),
          false,
          RuneUsage(RangeS.internal(-70001), CodeRuneS("M")),
          Some(MutableP),
          TemplateTemplataType(Vector(CoordTemplataType), KindTemplataType),
//          Set(CodeRuneS("M")),
          Vector(RuneUsage(RangeS.internal(-70001), CodeRuneS("T"))),
//          Set(CodeRuneS("T"), CodeRuneS("M")),
          Map(CodeRuneS("T") -> CoordTemplataType, CodeRuneS("M") -> MutabilityTemplataType),
          Vector(LiteralSR(RangeS.testZero,RuneUsage(RangeS.internal(-70001), CodeRuneS("M")), MutabilityLiteralSL(MutableP))),
          Vector())))
    entries = entries.addEntry(CitizenTemplateNameT("MutStruct"),//, CodeLocationS.internal(-29)),
      StructEnvEntry(
        StructA(
          RangeS.internal(-73),
          TopLevelCitizenDeclarationNameS("MutStruct", RangeS.internal(-30)),
          Vector(),
          false,
          RuneUsage(RangeS.internal(-70001), CodeRuneS("M")),
          Some(MutableP),
          KindTemplataType,
//          Set(CodeRuneS("M")),
          Vector(),
//          Set(CodeRuneS("M")),
          Map(CodeRuneS("M") -> MutabilityTemplataType),
          Vector(LiteralSR(RangeS.testZero,RuneUsage(RangeS.internal(-70001), CodeRuneS("M")), MutabilityLiteralSL(MutableP))),
          Vector())))
    entries = entries.addEntry(CitizenTemplateNameT("MutInterface"),//, CodeLocationS.internal(-31)),
      InterfaceEnvEntry(
        InterfaceA(
          RangeS.internal(-72),
          TopLevelCitizenDeclarationNameS("MutInterface", RangeS.internal(-32)),
          Vector(),
          false,
          RuneUsage(RangeS.internal(-70001), CodeRuneS("M")),
          Some(MutableP),
          KindTemplataType,
//          Set(CodeRuneS("M")),
          Vector(),
//          Set(CodeRuneS("M")),
          Map(CodeRuneS("M") -> MutabilityTemplataType),
          Vector(LiteralSR(RangeS.testZero,RuneUsage(RangeS.internal(-70001), CodeRuneS("M")), MutabilityLiteralSL(MutableP))),
          Vector())))
    entries = entries.addEntry(CitizenNameT(CitizenTemplateNameT("MutStructConstraint"), Vector()),
      TemplataEnvEntry(CoordTemplata(CoordT(ConstraintT,ReadonlyT, StructTT(FullNameT(PackageCoordinate.TEST_TLD, Vector(), CitizenNameT(CitizenTemplateNameT("MutStruct"), Vector())))))))
    entries = entries.addEntry(CitizenNameT(CitizenTemplateNameT("MutStructConstraintRW"), Vector()),
      TemplataEnvEntry(CoordTemplata(CoordT(ConstraintT,ReadwriteT, StructTT(FullNameT(PackageCoordinate.TEST_TLD, Vector(), CitizenNameT(CitizenTemplateNameT("MutStruct"), Vector())))))))
    entries = entries.addEntry(CitizenNameT(CitizenTemplateNameT("MutStructWeak"), Vector()),
      TemplataEnvEntry(CoordTemplata(CoordT(WeakT, ReadonlyT, StructTT(FullNameT(PackageCoordinate.TEST_TLD, Vector(), CitizenNameT(CitizenTemplateNameT("MutStruct"), Vector())))))))
    entries = entries.addEntry(CitizenNameT(CitizenTemplateNameT("MutStructWeakRW"), Vector()),
      TemplataEnvEntry(CoordTemplata(CoordT(WeakT, ReadwriteT, StructTT(FullNameT(PackageCoordinate.TEST_TLD, Vector(), CitizenNameT(CitizenTemplateNameT("MutStruct"), Vector())))))))
    entries = entries.addEntry(CitizenNameT(CitizenTemplateNameT("MutStaticSizedArrayOf4Int"), Vector()),
      TemplataEnvEntry(KindTemplata(StaticSizedArrayTT(4, RawArrayTT(CoordT(ShareT, ReadonlyT, IntT.i32), MutableT, VaryingT)))))
    // Tuples are normally addressed by TupleNameT, but that's a detail this test doesn't need to care about.
    entries = entries.addEntry(CitizenNameT(CitizenTemplateNameT("IntAndBoolTupName"), Vector()),
      TemplataEnvEntry(
        KindTemplata(
          StructTT(FullNameT(PackageCoordinate.TEST_TLD, Vector(), CitizenNameT(CitizenTemplateNameT("Tup"), Vector(CoordTemplata(ProgramT.intType), CoordTemplata(ProgramT.boolType))))))))
    val callPrototype = PrototypeTemplata(incrementPrototype)
    entries = entries.addEntry(callPrototype.value.fullName.last, TemplataEnvEntry(callPrototype))
    SimpleEnvironment(entries)
  }

  // Makes an evaluator with some canned data
  def makeCannedEvaluator(): Unit = {
//    makeEvaluator(Some(templataTemplarDelegate), Some(delegate))
  }

  def makeEvaluator():
  Unit = {
    vimpl()
//  InfererEvaluator[SimpleEnvironment, FakeState] = {
//    val templataTemplar =
//      new TemplataTemplarInner[SimpleEnvironment, FakeState](
//        maybeTemplataTemplarDelegate match {
//          case None => new FakeTemplataTemplarInnerDelegate()
//          case Some(t) => t
//        })
//
//    val equalsLayer =
//      new InfererEquator[SimpleEnvironment, FakeState](
//        templataTemplar)
//    val inferEvaluatorDelegate =
//      maybeEvaluatorDelegate match {
//        case Some(e) => e
//        case None => new FakeInfererEvaluatorDelegate()
//      }
//    val evaluator =
//      new InfererEvaluator[SimpleEnvironment, FakeState](
//        new NullProfiler(),
//        templataTemplar,
//        equalsLayer,
//        inferEvaluatorDelegate)
//    evaluator
  }

//  test("Test InterpretedTT") {
//    vimpl()
//    def run(sourceName: String, targetOwnership: OwnershipP, targetPermission: PermissionP): IInferSolveResult = {
//      val result =
//        makeCannedEvaluator().solve(
//          makeCannedEnvironment(),
//          FakeState(),
//          Vector(
//            EqualsTR(RangeS.testZero,
//              InterpretedTT(RangeS.testZero,targetOwnership,targetPermission, NameTT(RangeS.testZero,CodeTypeNameA(sourceName), CoordTemplataType)),
//              RuneTT(RangeS.testZero,CodeRuneT("T"), CoordTemplataType))),
//          RangeS.testZero,
//          Map(CodeRuneT("T") -> CoordTemplataType),
//          Set(CodeRuneT("T")),
//          Map(),
//          Vector(AtomSP(RangeS.testZero,Some(LocalA(CodeVarNameA("this"), NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed)),None,CodeRuneS("T"),None)),
//          None,
//          true)
//      result
//    }
//
//    def expectSuccess(inferSolveResult: IInferSolveResult): CoordT = {
//      val InferSolveSuccess(inferencesD) = inferSolveResult
//      val CoordTemplata(coord) = inferencesD.templatasByRune(CodeRuneT("T"))
//      coord
//    }
//
//    def expectFail(inferSolveResult: IInferSolveResult): String = {
//      val isf @ InferSolveFailure(_, _, _, _, _, _, _) = inferSolveResult
//      isf.toString
//    }
//
//    // Dont need to test Own + Readonly, because its impossible to express that with an InterpretedTT rule.
//    expectSuccess(run("int", OwnP, ReadwriteP)) shouldEqual CoordT(ShareT, ReadonlyT, IntT.i32)
//    expectSuccess(run("int", ConstraintP, ReadonlyP)) shouldEqual CoordT(ShareT, ReadonlyT, IntT.i32)
//    expectSuccess(run("int", ConstraintP, ReadwriteP)) shouldEqual CoordT(ShareT, ReadonlyT, IntT.i32)
//    vassert(expectFail(run("int", WeakP, ReadonlyP)).contains("Expected a weak, but was a share"))
//    vassert(expectFail(run("int", WeakP, ReadwriteP)).contains("Expected a weak, but was a share"))
//    expectSuccess(run("int", ShareP, ReadonlyP)) shouldEqual CoordT(ShareT, ReadonlyT, IntT.i32)
//    expectSuccess(run("int", ShareP, ReadwriteP)) shouldEqual CoordT(ShareT, ReadonlyT, IntT.i32)
//
//    vassert(expectFail(run("MutStruct", ShareP, ReadonlyP)).contains("Expected a share, but was an own"))
//    expectSuccess(run("MutStruct", OwnP, ReadwriteP)) shouldEqual CoordT(OwnT,ReadwriteT, StructTT(FullNameT(PackageCoordinate.TEST_TLD, Vector(), CitizenNameT(CitizenTemplateNameT("MutStruct"), Vector()))))
//    expectSuccess(run("MutStruct", ConstraintP, ReadonlyP)) shouldEqual CoordT(ConstraintT,ReadonlyT, StructTT(FullNameT(PackageCoordinate.TEST_TLD, Vector(), CitizenNameT(CitizenTemplateNameT("MutStruct"), Vector()))))
//    expectSuccess(run("MutStruct", ConstraintP, ReadwriteP)) shouldEqual CoordT(ConstraintT,ReadwriteT, StructTT(FullNameT(PackageCoordinate.TEST_TLD, Vector(), CitizenNameT(CitizenTemplateNameT("MutStruct"), Vector()))))
//    expectSuccess(run("MutStruct", WeakP, ReadonlyP)) shouldEqual CoordT(WeakT, ReadonlyT, StructTT(FullNameT(PackageCoordinate.TEST_TLD, Vector(), CitizenNameT(CitizenTemplateNameT("MutStruct"), Vector()))))
//    expectSuccess(run("MutStruct", WeakP, ReadwriteP)) shouldEqual CoordT(WeakT, ReadwriteT, StructTT(FullNameT(PackageCoordinate.TEST_TLD, Vector(), CitizenNameT(CitizenTemplateNameT("MutStruct"), Vector()))))
//
//    vassert(expectFail(run("MutStructConstraint", ShareP, ReadonlyP)).contains("Expected a share, but was a borrow"))
//    expectSuccess(run("MutStructConstraint", OwnP, ReadwriteP)) shouldEqual CoordT(OwnT,ReadwriteT, StructTT(FullNameT(PackageCoordinate.TEST_TLD, Vector(), CitizenNameT(CitizenTemplateNameT("MutStruct"), Vector()))))
//    expectSuccess(run("MutStructConstraint", ConstraintP, ReadonlyP)) shouldEqual CoordT(ConstraintT,ReadonlyT, StructTT(FullNameT(PackageCoordinate.TEST_TLD, Vector(), CitizenNameT(CitizenTemplateNameT("MutStruct"), Vector()))))
//    // &!T given a &Spaceship should give a &T, it should make the ro into a Readwrite.
//    expectSuccess(run("MutStructConstraint", ConstraintP, ReadwriteP)) shouldEqual CoordT(ConstraintT,ReadwriteT, StructTT(FullNameT(PackageCoordinate.TEST_TLD, Vector(), CitizenNameT(CitizenTemplateNameT("MutStruct"), Vector()))))
//    expectSuccess(run("MutStructConstraint", WeakP, ReadonlyP)) shouldEqual CoordT(WeakT, ReadonlyT, StructTT(FullNameT(PackageCoordinate.TEST_TLD, Vector(), CitizenNameT(CitizenTemplateNameT("MutStruct"), Vector()))))
//    // &&!T given a &Spaceship should give a &T, it should make the ro into a Readwrite, and the borrow into a weak.
//    expectSuccess(run("MutStructConstraint", WeakP, ReadwriteP)) shouldEqual CoordT(WeakT, ReadwriteT, StructTT(FullNameT(PackageCoordinate.TEST_TLD, Vector(), CitizenNameT(CitizenTemplateNameT("MutStruct"), Vector()))))
//
//    vassert(expectFail(run("MutStructConstraintRW", ShareP, ReadonlyP)).contains("Expected a share, but was a borrow"))
//    expectSuccess(run("MutStructConstraintRW", OwnP, ReadwriteP)) shouldEqual CoordT(OwnT,ReadwriteT, StructTT(FullNameT(PackageCoordinate.TEST_TLD, Vector(), CitizenNameT(CitizenTemplateNameT("MutStruct"), Vector()))))
//    // &T given a &!Spaceship should give a &T, it should make the Readwrite into a Readonly.
//    expectSuccess(run("MutStructConstraintRW", ConstraintP, ReadonlyP)) shouldEqual CoordT(ConstraintT,ReadonlyT, StructTT(FullNameT(PackageCoordinate.TEST_TLD, Vector(), CitizenNameT(CitizenTemplateNameT("MutStruct"), Vector()))))
//    expectSuccess(run("MutStructConstraintRW", ConstraintP, ReadwriteP)) shouldEqual CoordT(ConstraintT,ReadwriteT, StructTT(FullNameT(PackageCoordinate.TEST_TLD, Vector(), CitizenNameT(CitizenTemplateNameT("MutStruct"), Vector()))))
//    expectSuccess(run("MutStructConstraintRW", WeakP, ReadonlyP)) shouldEqual CoordT(WeakT, ReadonlyT, StructTT(FullNameT(PackageCoordinate.TEST_TLD, Vector(), CitizenNameT(CitizenTemplateNameT("MutStruct"), Vector()))))
//    // &&T given a &!Spaceship should give a &T, it should make the Readwrite into a Readonly, and the borrow into a weak.
//    expectSuccess(run("MutStructConstraintRW", WeakP, ReadwriteP)) shouldEqual CoordT(WeakT, ReadwriteT, StructTT(FullNameT(PackageCoordinate.TEST_TLD, Vector(), CitizenNameT(CitizenTemplateNameT("MutStruct"), Vector()))))
//
//    vassert(expectFail(run("MutStructWeak", ShareP, ReadonlyP)).contains("Expected a share, but was a weak"))
//    vassert(expectFail(run("MutStructWeak", OwnP, ReadwriteP)).contains("Expected a own, but was a weak"))
//    vassert(expectFail(run("MutStructWeak", ConstraintP, ReadonlyP)).contains("Expected a borrow, but was a weak"))
//    vassert(expectFail(run("MutStructWeak", ConstraintP, ReadwriteP)).contains("Expected a borrow, but was a weak"))
//    expectSuccess(run("MutStructWeak", WeakP, ReadonlyP)) shouldEqual CoordT(WeakT, ReadonlyT, StructTT(FullNameT(PackageCoordinate.TEST_TLD, Vector(), CitizenNameT(CitizenTemplateNameT("MutStruct"), Vector()))))
//    expectSuccess(run("MutStructWeak", WeakP, ReadwriteP)) shouldEqual CoordT(WeakT, ReadwriteT, StructTT(FullNameT(PackageCoordinate.TEST_TLD, Vector(), CitizenNameT(CitizenTemplateNameT("MutStruct"), Vector()))))
//
//    vassert(expectFail(run("MutStructWeakRW", ShareP, ReadonlyP)).contains("Expected a share, but was a weak"))
//    vassert(expectFail(run("MutStructWeakRW", OwnP, ReadwriteP)).contains("Expected a own, but was a weak"))
//    vassert(expectFail(run("MutStructWeakRW", ConstraintP, ReadonlyP)).contains("Expected a borrow, but was a weak"))
//    vassert(expectFail(run("MutStructWeakRW", ConstraintP, ReadwriteP)).contains("Expected a borrow, but was a weak"))
//    expectSuccess(run("MutStructWeakRW", WeakP, ReadonlyP)) shouldEqual CoordT(WeakT, ReadonlyT, StructTT(FullNameT(PackageCoordinate.TEST_TLD, Vector(), CitizenNameT(CitizenTemplateNameT("MutStruct"), Vector()))))
//    expectSuccess(run("MutStructWeakRW", WeakP, ReadwriteP)) shouldEqual CoordT(WeakT, ReadwriteT, StructTT(FullNameT(PackageCoordinate.TEST_TLD, Vector(), CitizenNameT(CitizenTemplateNameT("MutStruct"), Vector()))))
//
//    expectSuccess(run("void", ShareP, ReadonlyP)) shouldEqual CoordT(ShareT, ReadonlyT, VoidT())
//    expectSuccess(run("void", OwnP, ReadwriteP)) shouldEqual CoordT(ShareT, ReadonlyT, VoidT())
//    expectSuccess(run("void", ConstraintP, ReadonlyP)) shouldEqual CoordT(ShareT, ReadonlyT, VoidT())
//    expectSuccess(run("void", ConstraintP, ReadwriteP)) shouldEqual CoordT(ShareT, ReadonlyT, VoidT())
//    vassert(expectFail(run("void", WeakP, ReadonlyP)).contains("Expected a weak, but was a share"))
//    vassert(expectFail(run("void", WeakP, ReadwriteP)).contains("Expected a weak, but was a share"))
//  }

//  test("test matching ownershipped") {
//    vimpl()
//    def run(sourceName: String, targetOwnership: OwnershipP, targetPermission: PermissionP): IInferSolveResult = {
//      val result =
//        makeCannedEvaluator().solve(
//          makeCannedEnvironment(),
//          FakeState(),
//          Vector(
//            EqualsTR(RangeS.testZero,
//              NameTT(RangeS.testZero,CodeTypeNameA(sourceName), CoordTemplataType),
//              InterpretedTT(RangeS.testZero,targetOwnership,targetPermission, RuneTT(RangeS.testZero,CodeRuneT("T"), CoordTemplataType)))),
//          RangeS.testZero,
//          Map(CodeRuneT("T") -> CoordTemplataType),
//          Set(CodeRuneT("T")),
//          Map(),
//          Vector(),
//          None,
//          true)
//      result
//    }
//
//    def expectSuccess(inferSolveResult: IInferSolveResult): CoordT = {
//      val InferSolveSuccess(inferencesD) = inferSolveResult
//      val CoordTemplata(coord) = inferencesD.templatasByRune(CodeRuneT("T"))
//      coord
//    }
//
//    def expectFail(inferSolveResult: IInferSolveResult): String = {
//      val isf @ InferSolveFailure(_, _, _, _, _, _, _) = inferSolveResult
//      isf.toString
//    }
//
//    expectSuccess(run("int", OwnP, ReadwriteP)) shouldEqual CoordT(ShareT, ReadonlyT, IntT.i32)
//    expectSuccess(run("int", ConstraintP, ReadonlyP)) shouldEqual CoordT(ShareT, ReadonlyT, IntT.i32)
//    expectSuccess(run("int", ConstraintP, ReadwriteP)) shouldEqual CoordT(ShareT, ReadonlyT, IntT.i32)
//    vassert(expectFail(run("int", WeakP, ReadonlyP)).contains("Couldn't match incoming share against expected weak"))
//    vassert(expectFail(run("int", WeakP, ReadwriteP)).contains("Couldn't match incoming share against expected weak"))
//    expectSuccess(run("int", ShareP, ReadonlyP)) shouldEqual CoordT(ShareT, ReadonlyT, IntT.i32)
//
//    expectSuccess(run("void", OwnP, ReadwriteP)) shouldEqual CoordT(ShareT, ReadonlyT, VoidT())
//    expectSuccess(run("void", ConstraintP, ReadonlyP)) shouldEqual CoordT(ShareT, ReadonlyT, VoidT())
//    expectSuccess(run("void", ConstraintP, ReadwriteP)) shouldEqual CoordT(ShareT, ReadonlyT, VoidT())
//    vassert(expectFail(run("void", WeakP, ReadonlyP)).contains("Couldn't match incoming share against expected weak"))
//    vassert(expectFail(run("void", WeakP, ReadwriteP)).contains("Couldn't match incoming share against expected weak"))
//    expectSuccess(run("void", ShareP, ReadonlyP)) shouldEqual CoordT(ShareT, ReadonlyT, VoidT())
//
//    vassert(expectFail(run("MutStruct", ShareP, ReadonlyP)).contains("Couldn't match incoming own against expected share"))
//    // Takes the own off the incoming own coord, ends up as another own.
//    expectSuccess(run("MutStruct", OwnP, ReadwriteP)) shouldEqual CoordT(OwnT,ReadwriteT, StructTT(FullNameT(PackageCoordinate.TEST_TLD, Vector(), CitizenNameT(CitizenTemplateNameT("MutStruct"), Vector()))))
//    // Tries to take the borrow off the incoming own coord... fails.
//    vassert(expectFail(run("MutStruct", ConstraintP, ReadonlyP)).contains("Couldn't match incoming own against expected constraint"))
//    vassert(expectFail(run("MutStruct", ConstraintP, ReadwriteP)).contains("Couldn't match incoming own against expected constraint"))
//    vassert(expectFail(run("MutStruct", WeakP, ReadonlyP)).contains("Couldn't match incoming own against expected weak"))
//    vassert(expectFail(run("MutStruct", WeakP, ReadwriteP)).contains("Couldn't match incoming own against expected weak"))
//
//    // Tries to take the own off the incoming borrow coord... fails.
//    vassert(expectFail(run("MutStructConstraint", OwnP, ReadwriteP)).contains("Couldn't match incoming constraint against expected own"))
//    // Takes the borrow off the incoming borrow coord, succeeds and gives us an own.
//    expectSuccess(run("MutStructConstraint", ConstraintP, ReadonlyP)) shouldEqual CoordT(OwnT,ReadwriteT, StructTT(FullNameT(PackageCoordinate.TEST_TLD, Vector(), CitizenNameT(CitizenTemplateNameT("MutStruct"), Vector()))))
//    vassert(expectFail(run("MutStructConstraint", ConstraintP, ReadwriteP)).contains("Couldn't match incoming ro against expected rw"))
//    // Takes the weak off the incoming borrow coord... fails.
//    vassert(expectFail(run("MutStructConstraint", WeakP, ReadonlyP)).contains("Couldn't match incoming constraint against expected weak"))
//    vassert(expectFail(run("MutStructConstraint", WeakP, ReadwriteP)).contains("Couldn't match incoming constraint against expected weak"))
//    vassert(expectFail(run("MutStructConstraint", ShareP, ReadonlyP)).contains("Couldn't match incoming constraint against expected share"))
//
//    // Tries to take the own off the incoming borrow coord... fails.
//    vassert(expectFail(run("MutStructConstraintRW", OwnP, ReadwriteP)).contains("Couldn't match incoming constraint against expected own"))
//    vassert(expectFail(run("MutStructConstraintRW", ConstraintP, ReadonlyP)).contains("Couldn't match incoming rw against expected ro"))
//    // Takes the borrow off the incoming borrow coord, succeeds and gives us an own.
//    expectSuccess(run("MutStructConstraintRW", ConstraintP, ReadwriteP)) shouldEqual CoordT(OwnT,ReadwriteT,StructTT(FullNameT(PackageCoordinate.TEST_TLD, Vector(),CitizenNameT(CitizenTemplateNameT("MutStruct"),Vector()))))
//    // Takes the weak off the incoming borrow coord... fails.
//    vassert(expectFail(run("MutStructConstraintRW", WeakP, ReadonlyP)).contains("Couldn't match incoming constraint against expected weak"))
//    vassert(expectFail(run("MutStructConstraintRW", WeakP, ReadwriteP)).contains("Couldn't match incoming constraint against expected weak"))
//    vassert(expectFail(run("MutStructConstraintRW", ShareP, ReadonlyP)).contains("Couldn't match incoming constraint against expected share"))
//
//    // Tries to take the own off the incoming weak coord... fails.
//    vassert(expectFail(run("MutStructWeak", OwnP, ReadwriteP)).contains("Couldn't match incoming weak against expected own"))
//    // Takes the borrow off the incoming weak coord... fails.
//    vassert(expectFail(run("MutStructWeak", ConstraintP, ReadonlyP)).contains("Couldn't match incoming weak against expected constraint"))
//    vassert(expectFail(run("MutStructWeak", ConstraintP, ReadwriteP)).contains("Couldn't match incoming weak against expected constraint"))
//    // Takes the weak off the incoming weak coord, succeeds and gives us an own.
//    expectSuccess(run("MutStructWeak", WeakP, ReadonlyP)) shouldEqual CoordT(OwnT,ReadwriteT, StructTT(FullNameT(PackageCoordinate.TEST_TLD, Vector(), CitizenNameT(CitizenTemplateNameT("MutStruct"), Vector()))))
//    vassert(expectFail(run("MutStructWeak", WeakP, ReadwriteP)).contains("Couldn't match incoming ro against expected rw"))
//    vassert(expectFail(run("MutStructWeak", ShareP, ReadonlyP)).contains("Couldn't match incoming weak against expected share"))
//
//    // Tries to take the own off the incoming weak coord... fails.
//    vassert(expectFail(run("MutStructWeakRW", OwnP, ReadwriteP)).contains("Couldn't match incoming weak against expected own"))
//    // Takes the borrow off the incoming weak coord... fails.
//    vassert(expectFail(run("MutStructWeakRW", ConstraintP, ReadonlyP)).contains("Couldn't match incoming weak against expected constraint"))
//    vassert(expectFail(run("MutStructWeakRW", ConstraintP, ReadwriteP)).contains("Couldn't match incoming weak against expected constraint"))
//    vassert(expectFail(run("MutStructWeakRW", WeakP, ReadonlyP)).contains("Couldn't match incoming rw against expected ro"))
//    // Takes the weak off the incoming weak coord, succeeds and gives us an own.
//    expectSuccess(run("MutStructWeakRW", WeakP, ReadwriteP)) shouldEqual CoordT(OwnT,ReadwriteT, StructTT(FullNameT(PackageCoordinate.TEST_TLD, Vector(), CitizenNameT(CitizenTemplateNameT("MutStruct"), Vector()))))
//    vassert(expectFail(run("MutStructWeakRW", ShareP, ReadonlyP)).contains("Couldn't match incoming weak against expected share"))
//  }
}
