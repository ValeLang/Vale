package dev.vale

import dev.vale.simplifying.VonHammer
import dev.vale.finalast.YonderH
import dev.vale.instantiating.ast._
import dev.vale.typing._
import dev.vale.typing.types._
import dev.vale.testvm.StructInstanceV
import dev.vale.typing.ast.{LetNormalTE, LocalLookupTE, ReferenceMemberLookupTE, StaticSizedArrayLookupTE}
import dev.vale.typing.env.ReferenceLocalVariableT
import dev.vale.typing.names.{CodeVarNameT, IdT, RawArrayNameT, RuntimeSizedArrayNameT, RuntimeSizedArrayTemplateNameT, StaticSizedArrayNameT, StaticSizedArrayTemplateNameT, StructNameT, StructTemplateNameT}
import dev.vale.typing.templata._
import dev.vale.von.{VonBool, VonInt}
import dev.vale.{finalast => m}
import org.scalatest.{FunSuite, Matchers}

class PureTests extends FunSuite with Matchers {
  test("Simple pure block") {
    // Taking in a &Spaceship so we don't call the constructors, that's covered by another test.

    val compile =
      RunCompilation.test(
        """
          |struct Engine { fuel int; }
          |struct Spaceship { engine Engine; }
          |exported func main(s &Spaceship) int {
          |  pure block {
          |    x = s.engine;
          |    y = x.fuel;
          |    y
          |  }
          |}
          |""".stripMargin, false)
    val main = compile.getMonouts().lookupFunction("main")
    val rml =
      Collector.only(main, {
        case rml @ ReferenceMemberLookupIE(_, _, CodeVarNameI(StrI("engine")), _, _) => rml
      })
    rml.memberReference match {
      // See RMLRMO for why this is OwnI
      case CoordI(OwnI,StructIT(IdI(_,_,StructNameI(StructTemplateNameI(StrI("Engine")),Vector(RegionTemplataI(0)))))) =>
    }

    val xType =
      Collector.only(main, {
        case LetNormalIE(ReferenceLocalVariableI(CodeVarNameI(StrI("x")), _, coord), _, _) => coord
      })
    xType match {
      // The ImmutableBorrowI is the important part here
      case CoordI(ImmutableBorrowI,StructIT(IdI(_,_,StructNameI(StructTemplateNameI(StrI("Engine")),Vector(RegionTemplataI(0)))))) =>
    }

    val yType =
      Collector.only(main, {
        case LetNormalIE(ReferenceLocalVariableI(CodeVarNameI(StrI("y")), _, coord), _, _) => coord
      })
    yType match {
      case CoordI(MutableShareI,IntIT(32)) =>
    }

    // We don't evaluate the program, its main takes in a struct which is impossible
    compile.getHamuts()
  }

  test("Pure block accessing arrays") {
    // In other words, calling a constructor. All the default constructors are pure functions.

    val compile =
      RunCompilation.test(
        Tests.loadExpected("programs/pure/pure_block_read_ssa.vale"),
        false)
    val main = compile.getMonouts().lookupFunction("main")
    val ssal =
      vassertSome(
        Collector.all(main, {
          case ssal @ StaticSizedArrayLookupIE(_, _, _, _, _) => ssal
        }).headOption)
    ssal.elementType match {
      // See RMLRMO for why this is OwnI
      case CoordI(OwnI,StaticSizedArrayIT(IdI(_,_,StaticSizedArrayNameI(StaticSizedArrayTemplateNameI(),2,FinalI,RawArrayNameI(MutableI,CoordTemplataI(RegionTemplataI(0),CoordI(MutableShareI,IntIT(32))),RegionTemplataI(0)))))) =>
    }

    val xType =
      Collector.only(main, {
        case LetNormalIE(ReferenceLocalVariableI(CodeVarNameI(StrI("x")), _, coord), _, _) => coord
      })
    xType match {
      case CoordI(ImmutableBorrowI,StaticSizedArrayIT(IdI(_,_,_))) =>
    }

    val yType =
      Collector.only(main, {
        case LetNormalIE(ReferenceLocalVariableI(CodeVarNameI(StrI("y")), _, coord), _, _) => coord
      })
    yType match {
      case CoordI(MutableShareI,IntIT(32)) =>
    }

    compile.evalForKind(Vector()) match { case VonInt(10) => }
  }

  test("Pure block returning an array") {
    // In other words, calling a constructor. All the default constructors are pure functions.

    val compile =
      RunCompilation.test(
        Tests.loadExpected("programs/pure/pure_block_produce_ssa.vale"),
        false)
    val main = compile.getMonouts().lookupFunction("main")

    val xType =
      Collector.only(main, {
        case LetNormalIE(ReferenceLocalVariableI(CodeVarNameI(StrI("x")), _, coord), _, _) => coord
      })
    xType match {
      case CoordI(OwnI,StaticSizedArrayIT(IdI(_,_,StaticSizedArrayNameI(StaticSizedArrayTemplateNameI(),2,FinalI,RawArrayNameI(MutableI,CoordTemplataI(RegionTemplataI(0),CoordI(OwnI,StaticSizedArrayIT(IdI(_,_,StaticSizedArrayNameI(StaticSizedArrayTemplateNameI(),2,FinalI,RawArrayNameI(MutableI,elementType,RegionTemplataI(0))))))),RegionTemplataI(0)))))) => {
        elementType match {
          case CoordTemplataI(RegionTemplataI(0),CoordI(MutableShareI,IntIT(32))) =>
        }
      }
    }

    compile.evalForKind(Vector()) match { case VonInt(10) => }
  }

  test("Pure function returning a static sized array") {
    // In other words, calling a constructor. All the default constructors are pure functions.

    val compile =
      RunCompilation.test(
        Tests.loadExpected("programs/pure/pure_func_return_ssa.vale"),
        false)
    val main = compile.getMonouts().lookupFunction("main")

    val xType =
      Collector.only(main, {
        case LetNormalIE(ReferenceLocalVariableI(CodeVarNameI(StrI("x")), _, coord), _, _) => coord
      })
    xType match {
     case CoordI(OwnI,StaticSizedArrayIT(IdI(_,_,StaticSizedArrayNameI(StaticSizedArrayTemplateNameI(),2,FinalI,RawArrayNameI(MutableI,CoordTemplataI(RegionTemplataI(0),CoordI(OwnI,StaticSizedArrayIT(IdI(_,_,StaticSizedArrayNameI(StaticSizedArrayTemplateNameI(),2,FinalI,RawArrayNameI(MutableI,elementType,RegionTemplataI(0))))))),RegionTemplataI(0)))))) => {
       elementType match {
         case CoordTemplataI(RegionTemplataI(0), CoordI(MutableShareI, IntIT(32))) =>
       }
     }
    }

    compile.evalForKind(Vector()) match { case VonInt(10) => }
  }

  test("Pure function taking in a static sized array") {
    // In other words, calling a constructor. All the default constructors are pure functions.

    val compile =
      RunCompilation.test(
        Tests.loadExpected("programs/pure/pure_func_take_ssa.vale"),
        false)
    val main = compile.getMonouts().lookupFunction("main")

    val callArg =
      Collector.only(main, {
        case FunctionCallIE(calleePrototype, Vector(arg), _) => arg.result
      })

    val display = compile.getMonouts().lookupFunction("Display")
    display.header.id.localName match {
      case FunctionNameIX(
        FunctionTemplateNameI(StrI("Display"),_),
        Vector(RegionTemplataI(-1), RegionTemplataI(0)),
        Vector(_)) =>
    }
    val funcParam = display.header.id.localName.parameters.head
    funcParam match {
      case CoordI(
        ImmutableBorrowI,
        StaticSizedArrayIT(
          IdI(_,_,
            StaticSizedArrayNameI(
              StaticSizedArrayTemplateNameI(),2,FinalI,
              RawArrayNameI(
                MutableI,
                CoordTemplataI(
                  RegionTemplataI(0),
                  CoordI(
                    OwnI,
                    StaticSizedArrayIT(
                      IdI(_,_,
                        StaticSizedArrayNameI(
                          StaticSizedArrayTemplateNameI(),2,FinalI,
                          RawArrayNameI(
                            MutableI,
                            elementType,
                            RegionTemplataI(0))))))),
                RegionTemplataI(0)))))) => {
        elementType match {
          case CoordTemplataI(RegionTemplataI(0), CoordI(MutableShareI, IntIT(32))) =>
        }
      }
    }

    vassert(callArg == funcParam)

    compile.evalForKind(Vector()) match { case VonInt(10) => }
  }

  test("Pure function returning a runtime sized array") {
    // In other words, calling a constructor. All the defaul qt constructors are pure functions.

    val compile =
      RunCompilation.test(
        Tests.loadExpected("programs/pure/pure_func_return_rsa.vale"),
        false)
    val main = compile.getMonouts().lookupFunction("main")

    val xType =
      Collector.only(main, {
        case LetNormalIE(ReferenceLocalVariableI(CodeVarNameI(StrI("x")), _, coord), _, _) => coord
      })
    xType match {
      case CoordI(OwnI,RuntimeSizedArrayIT(IdI(_,_,RuntimeSizedArrayNameI(RuntimeSizedArrayTemplateNameI(),RawArrayNameI(MutableI,CoordTemplataI(RegionTemplataI(0),CoordI(OwnI,RuntimeSizedArrayIT(IdI(_,_,RuntimeSizedArrayNameI(RuntimeSizedArrayTemplateNameI(),RawArrayNameI(MutableI,elementType,RegionTemplataI(0))))))),RegionTemplataI(0)))))) => {
        elementType match {
          case CoordTemplataI(RegionTemplataI(0), CoordI(MutableShareI, IntIT(32))) =>
        }
      }
//      case CoordI(OwnI,RuntimeSizedArrayIT(IdI(_,_,RuntimeSizedArrayNameI(_,RawArrayNameI(MutableI,CoordI(OwnI,RuntimeSizedArrayIT(IdI(_,_,RuntimeSizedArrayNameI(RuntimeSizedArrayTemplateNameI(),RawArrayNameI(MutableI,CoordI(MutableShareI,IntIT(32)),RegionTemplataI(0)))))),RegionTemplataI(0)))))) =>
    }

    compile.evalForKind(Vector()) match { case VonInt(42) => }
  }

  test("Pure function returning struct") {
    // In other words, calling a constructor. All the default constructors are pure functions.

    val compile =
      RunCompilation.test(
        Tests.loadExpected("programs/pure/pure_func_return_struct.vale"),
        false)
    val main = compile.getMonouts().lookupFunction("main")

    compile.evalForKind(Vector()) match { case VonInt(10) => }
  }


  test("Pure function with immediate value") {
    val compile =
      RunCompilation.test(
        """
          |pure func pureFunc<r'>(s r'str) bool {
          |  true
          |}
          |exported func main() bool {
          |  pureFunc("abc")
          |}
          |""".stripMargin, false)
    val main = compile.getMonouts().lookupFunction("main")

    compile.evalForKind(Vector()) match { case VonBool(true) => }
  }

  test("Pure function reading from local") {
    val compile =
      RunCompilation.test(
        """
          |pure func pureFunc<r'>(s r'str) bool {
          |  true
          |}
          |exported func main() bool {
          |  s = "abc";
          |  return pureFunc(s);
          |}
          |""".stripMargin, false)
    val main = compile.getMonouts().lookupFunction("main")

    compile.evalForKind(Vector()) match {
      case VonBool(true) =>
    }
  }

  test("Readonly function call inside pure block") {
    val compile =
      RunCompilation.test(
        """
          |func rofunc<r'>(s r'str) bool {
          |  true
          |}
          |exported func main() bool {
          |  s = "abc";
          |  return pure block { rofunc(s) };
          |}
          |""".stripMargin, false)
    val main = compile.getMonouts().lookupFunction("main")

    compile.evalForKind(Vector()) match {
      case VonBool(true) =>
    }
  }

  test("Extern function with different regions") {
    val compile =
      RunCompilation.test(
        Tests.loadExpected("programs/regions/multi_region_extern.vale"),
        false)
    val main = compile.getMonouts().lookupFunction("main")
    compile.evalForKind(Vector()) match { case VonInt(42) => }
  }

  test("Test imm array length") {
    val compile = RunCompilation.test(
      """
        |import v.builtins.runtime_sized_array_mut_new.*;
        |import v.builtins.runtime_sized_array_len.*;
        |pure func pureLen<r', T>(x &r'[]T) int {
        |  return len(x);
        |}
        |exported func main() int {
        |  a = []int(0);
        |  l = pureLen(&a);
        |  [] = a;
        |  return l;
        |}
    """.stripMargin, false)
    val pureLenI = compile.getMonouts().lookupFunction("pureLen")

    {
      // Check the actual parameter coming in
      val CoordI(arrOwnership, arrKind) =
        pureLenI.header.id.localName.parameters(0)
      val RuntimeSizedArrayIT(IdI(_, _, RuntimeSizedArrayNameI(_, RawArrayNameI(arrMutability, elementType, arrSelfRegion)))) =
        arrKind
      arrOwnership shouldEqual ImmutableBorrowI
      arrMutability shouldEqual MutableI
      // Everything is the 0th region to itself
      arrSelfRegion shouldEqual RegionTemplataI(0)
      val CoordTemplataI(elementOuterRegion, CoordI(elementOwnership, elementKind)) = elementType
      // The element is in the same region as the array
      elementOuterRegion shouldEqual RegionTemplataI(0)
      elementOwnership shouldEqual MutableShareI
      elementKind shouldEqual IntIT(32)
    }

    // Check the generic arg for the region r'
    val RegionTemplataI(-1) = pureLenI.header.id.localName.templateArgs(0)

    {
      // Check the generic arg for the element T
      val CoordTemplataI(elementOuterRegion, elementType) = pureLenI.header.id.localName.templateArgs(1)
      val CoordI(elementOwnership, elementKind) = elementType
      // Not sure if this is right. It kind of makes sense.
      // It's -1 from the perspective of this function; it's 1 pure block away (collapsed).
      elementOuterRegion shouldEqual RegionTemplataI(-1)
      elementOwnership shouldEqual MutableShareI
      elementKind shouldEqual IntIT(32)
    }

    compile.evalForKind(Vector()) match {
      case VonInt(0) =>
    }
  }

  test("Calling func with element from pure incoming array") {
    val compile =
      RunCompilation.test(
        """
          |import v.builtins.runtime_sized_array_mut_new.*;
          |
          |func bork<r', E>(arr &r'[]<mut>E) int { 0 }
          |
          |pure func Display<r'>(board &r'[]<mut>[]<mut>str) {
          |  row = board[0];
          |  row.bork();
          |}
          |
          |exported func main() {
          |  board = [][]str(0);
          |  Display(&board);
          |  [] = board;
          |}
          |""".stripMargin, false)
    compile.getHamuts()
  }

}
