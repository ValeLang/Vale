package dev.vale.typing

import dev.vale.typing.env.ReferenceLocalVariableT
import dev.vale.typing.expression.CallCompiler
import dev.vale.typing.infer.{KindIsNotConcrete, OwnershipDidntMatch}
import dev.vale.{CodeLocationS, Collector, Err, FileCoordinateMap, Ok, PackageCoordinate, RangeS, Tests, vassert, vassertOne, vpass, vwat, _}
import dev.vale.parsing.ParseErrorHumanizer
import dev.vale.postparsing.PostParser
import dev.vale.typing.templata._
import dev.vale.typing.types._
import dev.vale.highertyping.{FunctionA, HigherTypingCompilation}
import dev.vale.solver.RuleError
import OverloadResolver.{FindFunctionFailure, InferFailure, SpecificParamDoesntSend, WrongNumberOfArguments}
import dev.vale.Collector.ProgramWithExpect
import dev.vale.postparsing._
import dev.vale.postparsing.rules.IRulexSR
import dev.vale.solver.{FailedSolve, RuleError, Step}
import dev.vale.typing.ast.{ConstantIntTE, DestroyTE, DiscardTE, FunctionCallTE, FunctionDefinitionT, FunctionHeaderT, KindExportT, LetAndLendTE, LetNormalTE, LocalLookupTE, ParameterT, PrototypeT, ReferenceMemberLookupTE, ReturnTE, SignatureT, SoftLoadTE, UserFunctionT, referenceExprResultKind, referenceExprResultStructName}
import dev.vale.typing.names._
import dev.vale.typing.templata._
import dev.vale.typing.types._
import dev.vale.typing.ast._
//import dev.vale.typingpass.infer.NotEnoughToSolveError
import org.scalatest.{FunSuite, Matchers, _}

import scala.collection.immutable.List
import scala.io.Source

class CompilerRegionTests extends FunSuite with Matchers {
  def readCodeFromResource(resourceFilename: String): String = {
    val is =
      Source.fromInputStream(getClass().getClassLoader().getResourceAsStream(resourceFilename))
    vassert(is != null)
    is.mkString("")
  }

  test("Test caller and callee regions") {
    val compile = CompilerTestCompilation.test(
      """
        |#!DeriveStructDrop
        |struct MyStruct { }
        |func myFunc(x &MyStruct) { }
        |exported func main() {
        |  a = MyStruct();
        |  myFunc(&a);
        |  [] = a;
        |}
      """.stripMargin)

    val coutputs = compile.expectCompilerOutputs()
    val main = coutputs.lookupFunction("main")
    Collector.only(main, {
      case FunctionCallTE(
        PrototypeT(
          IdT(_,_,FunctionNameT(FunctionTemplateNameT(StrI("myFunc"),_), _, params)), returnType),
        _, _,
        Vector(arg),
        _) => {
        returnType match {
          case CoordT(ShareT,RegionT(PlaceholderTemplataT(IdT(_,Vector(FunctionTemplateNameT(StrI("main"),_)),RegionPlaceholderNameT(0,DenizenDefaultRegionRuneS(FunctionNameS(StrI("main"),_)),Some(0),ReadWriteRegionS)),RegionTemplataType())),VoidT()) =>
        }
        params match {
          case Vector(
            CoordT(
              BorrowT,
              RegionT(PlaceholderTemplataT(IdT(_,Vector(FunctionTemplateNameT(StrI("main"),_)),RegionPlaceholderNameT(0,DenizenDefaultRegionRuneS(FunctionNameS(StrI("main"),_)),Some(0),ReadWriteRegionS)),RegionTemplataType())),
              StructTT(IdT(_,_,StructNameT(StructTemplateNameT(StrI("MyStruct")),Vector(PlaceholderTemplataT(IdT(_,Vector(FunctionTemplateNameT(StrI("main"),_)),RegionPlaceholderNameT(0,DenizenDefaultRegionRuneS(FunctionNameS(StrI("main"),_)),Some(0),ReadWriteRegionS)),RegionTemplataType()))))))) =>
        }
        arg.result.coord.region match {
          case RegionT(PlaceholderTemplataT(IdT(_,Vector(FunctionTemplateNameT(StrI("main"),_)),RegionPlaceholderNameT(0,DenizenDefaultRegionRuneS(FunctionNameS(StrI("main"),_)),Some(0),ReadWriteRegionS)),RegionTemplataType())) =>
        }
      }
    })

    val myFunc = coutputs.lookupFunction("myFunc")
    myFunc.header.params.head.tyype.region match {
      case RegionT(PlaceholderTemplataT(IdT(_,Vector(FunctionTemplateNameT(StrI("myFunc"),_)),RegionPlaceholderNameT(0,DenizenDefaultRegionRuneS(FunctionNameS(StrI("myFunc"),_)),Some(0),ReadWriteRegionS)),RegionTemplataType())) =>
    }
  }

  test("Test region'd type") {
    val compile = CompilerTestCompilation.test(
      """
        |func CellularAutomata<r' imm>(rand_seed r'int) { }
      """.stripMargin)

    val coutputs = compile.expectCompilerOutputs()
    val main = coutputs.lookupFunction("CellularAutomata")
  }

  test("Test deeply region'd type") {
    val compile = CompilerTestCompilation.test(
      """
        |func CellularAutomata<r' imm>(rand_seed &r'[]int) { }
      """.stripMargin)

    object regionR {
      def unapply(templata: ITemplataT[ITemplataType]): Option[Unit] = {
        templata match {
          case PlaceholderTemplataT(
            IdT(_,
              Vector(FunctionTemplateNameT(StrI("CellularAutomata"),_)),
              RegionPlaceholderNameT(_,CodeRuneS(StrI("r")), _, _)),
            RegionTemplataType()) => Some(Unit)
          case _ => None
        }
      }
    }

    val coutputs = compile.expectCompilerOutputs()
    val main = coutputs.lookupFunction("CellularAutomata")
    main.header.params.head.tyype match {
      case CoordT(
        BorrowT,
        regionR(_),
        RuntimeSizedArrayTT(
          IdT(_,
            Vector(),
            RuntimeSizedArrayNameT(
              RuntimeSizedArrayTemplateNameT(),
              RawArrayNameT(
                MutabilityTemplataT(MutableT),
                CoordT(ShareT, regionR(_), IntT(32)),
              regionR(_)))))) =>
    }
  }

  test("Function with two regions") {
    val compile = CompilerTestCompilation.test(
      """
        |func CellularAutomata<r' imm, x'>(incoming_int r'int) { }
        |""".stripMargin)

    val coutputs = compile.expectCompilerOutputs()
    val main = coutputs.lookupFunction("CellularAutomata")
  }

  test("Function with region param still has a default region") {
    val compile = CompilerTestCompilation.test(
      """
        |func CellularAutomata<r' imm>(incoming_int r'int) {
        |  a = 7;
        |}
        |""".stripMargin)

    val coutputs = compile.expectCompilerOutputs()
    val func = coutputs.lookupFunction("CellularAutomata")
    val intType = Collector.only(func, { case LetNormalTE(ReferenceLocalVariableT(CodeVarNameT(StrI("a")), _, c), _) => c })
    intType.region match {
      case RegionT(PlaceholderTemplataT(
        IdT(
          _,
          Vector(FunctionTemplateNameT(StrI("CellularAutomata"),_)),
          RegionPlaceholderNameT(1,DenizenDefaultRegionRuneS(_), _, _)),RegionTemplataType())) =>
    }
  }

  test("Explicit default region") {
    val compile = CompilerTestCompilation.test(
      """
        |func CellularAutomata<r' imm, x' rw>(incoming_int r'int) x'{
        |  a = 7;
        |}
        |""".stripMargin)

    val coutputs = compile.expectCompilerOutputs()
    val func = coutputs.lookupFunction("CellularAutomata")
    val intType = Collector.only(func, { case LetNormalTE(ReferenceLocalVariableT(CodeVarNameT(StrI("a")), _, c), _) => c })
    intType.region match {
      case RegionT(PlaceholderTemplataT(
        IdT(
          _,
          Vector(FunctionTemplateNameT(StrI("CellularAutomata"),_)),
          RegionPlaceholderNameT(1,CodeRuneS(StrI("x")), _, _)),
        RegionTemplataType())) =>
    }
  }

  test("Call function with callee param explicit region") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.runtime_sized_array_mut_new.*;
        |
        |func Display<r', x' rw>(map &r'[][]bool) x'{ }
        |
        |exported func main() {
        |  board_0 = [][]bool(20);
        |  Display(&board_0);
        |  [] = board_0;
        |}
        |
        |""".stripMargin)

    val coutputs = compile.expectCompilerOutputs()
    val func = coutputs.lookupFunction("main")
  }

  test("Access field of immutable object") {
    val compile = CompilerTestCompilation.test(
      """struct Ship { hp int; }
        |func GetHp<r', x' rw>(map &r'Ship) x'int x'{ map.hp }
        |exported func main() int {
        |  ship = Ship(42);
        |  return GetHp(&ship);
        |}
        |""".stripMargin)

    val coutputs = compile.expectCompilerOutputs()
    val func = coutputs.lookupFunction("main")
  }

  test("Test automatic int transmigration for call") {
    // See PATDR, the int from main should automatically match GetHp's default region.
    val compile = CompilerTestCompilation.test(
      """
        |struct Ship { }
        |pure func GetHp<r'>(map &r'Ship, x bool) int { 42 }
        |exported func main() int {
        |  ship = Ship();
        |  return GetHp(&ship, true);
        |}
        |""".stripMargin)

    val coutputs = compile.expectCompilerOutputs()
    val func = coutputs.lookupFunction("main")
//    Collector.only(func, {
//      case TransmigrateTE(sourceExpr, targetRegion) if sourceExpr.kind == IntT(32) =>
//    })
    Collector.only(func, {
      case TransmigrateTE(sourceExpr, targetRegion) if sourceExpr.kind == BoolT() =>
    })
  }

  test("Test automatic int transmigration from member load") {
    // See PATDR, the int from main should automatically match GetHp's default region.
    val compile = CompilerTestCompilation.test(
      """
        |struct Ship { fuel int; }
        |pure func GetFuel<r'>(ship &r'Ship) int { ship.fuel }
        |exported func main() int {
        |  ship = Ship(42);
        |  return GetFuel(&ship);
        |}
        |""".stripMargin)

    val coutputs = compile.expectCompilerOutputs()
    val func = coutputs.lookupFunction("GetFuel")
    Collector.only(func, {
      case TransmigrateTE(sourceExpr, targetRegion) if sourceExpr.kind == IntT(32) =>
    })
  }

  test("Tests pure") {
    val compile = CompilerTestCompilation.test(
      """
        |#!DeriveStructDrop
        |struct Ship { hp int; }
        |pure func bork<i' imm, f' rw>(x &i'Ship) f'int f'{
        |  x.hp
        |}
        |exported func main() int {
        |  ship = Ship(42);
        |  x = bork(&ship);
        |  [_] = ship;
        |  return x;
        |}
        """.stripMargin)
    val bork = compile.expectCompilerOutputs().lookupFunction("bork")
    val genArg =
      bork.header.id.localName match {
        case FunctionNameT(_, genArgs, _) => {
          genArgs match {
            case Vector(x, y) => x
          }
        }
      }
    genArg match {
      case PlaceholderTemplataT(
      IdT(_,Vector(FunctionTemplateNameT(StrI("bork"),_)),RegionPlaceholderNameT(0,CodeRuneS(StrI("i")),None,ImmutableRegionS)),
      RegionTemplataType()) =>
    }
  }

  test("Tests detect pure violation") {
    val compile = CompilerTestCompilation.test(
      """
        |#!DeriveStructDrop
        |struct Engine { fuel int; }
        |#!DeriveStructDrop
        |struct Ship { engine! Engine; }
        |pure func bork<r' imm>(x &r'Ship) Engine {
        |  return set x.engine = Engine(73);
        |}
        |exported func main() int {
        |  ship = Ship(Engine(42));
        |  [z] = bork(&ship);
        |  [[_]] = ship;
        |  return z;
        |}
        """.stripMargin)
    compile.expectCompilerOutputs()
//    compile.getCompilerOutputs().expectErr() match {
//      case null =>
//    }
    vimpl()
  }

  test("Tests additive") {
    val compile = CompilerTestCompilation.test(
      """
        |#!DeriveStructDrop
        |struct Ship { hp int; }
        |additive func bork<i' additive, f' rw>(x &i'Ship) f'int f'{
        |  x.hp
        |}
        |exported func main() int {
        |  ship = Ship(42);
        |  x = bork(&ship);
        |  [_] = ship;
        |  return x;
        |}
        """.stripMargin)
    val bork = compile.expectCompilerOutputs().lookupFunction("bork")
    val genArg =
      bork.header.id.localName match {
        case FunctionNameT(_, genArgs, _) => {
          genArgs match {
            case Vector(x, y) => x
          }
        }
      }
    genArg match {
      case PlaceholderTemplataT(
        IdT(_,Vector(FunctionTemplateNameT(StrI("bork"),_)),RegionPlaceholderNameT(0,CodeRuneS(StrI("i")),None,AdditiveRegionS)),
        RegionTemplataType()) =>
    }
  }

  test("Tests detect additive violation") {
    val compile = CompilerTestCompilation.test(
      """
        |#!DeriveStructDrop
        |struct Engine { fuel int; }
        |#!DeriveStructDrop
        |struct Ship { engine! Engine; }
        |additive func bork(x &Ship) Engine {
        |  return set x.engine = Engine(73);
        |}
        |exported func main() int {
        |  ship = Ship(Engine(42));
        |  [z] = bork(&ship);
        |  [[_]] = ship;
        |  return z;
        |}
        """.stripMargin)
    compile.getCompilerOutputs().expectErr() match {
      case null =>
    }
  }

}
