package dev.vale

import dev.vale.parsing.ast.FinalP
import dev.vale.postparsing.CodeRuneS
import dev.vale.typing.env.ReferenceLocalVariableT
import dev.vale.typing.types._
import dev.vale.typing._
import dev.vale.typing.ast.{NormalStructMemberT, ReferenceMemberTypeT}
import dev.vale.typing.names.{IdT, KindPlaceholderNameT, KindPlaceholderTemplateNameT, StructNameT, StructTemplateNameT}
import dev.vale.typing.types.IntT
import dev.vale.von.VonInt
import org.scalatest.{FunSuite, Matchers}

class PatternTests extends FunSuite with Matchers {
  // To get something like this to work would be rather involved.
  //test("Test matching a single-member pack") {
  //  val compile = RunCompilation.test( "exported func main() int { [x] = (4); = x; }")
  //  compile.getCompilerOutputs()
  //  val main = coutputs.lookupFunction("main")
  //  main.header.returnType shouldEqual Coord(Share, Readonly, Int2())
  //  compile.evalForKind(Vector()) match { case VonInt(4) => }
  //}

  test("Test matching a multiple-member seq of immutables") {
    // Checks that the 5 made it into y, and it was an int
    val compile = RunCompilation.test( "exported func main() int { [x, y] = (4, 5); return y; }")
    val coutputs = compile.expectCompilerOutputs()
    val main = coutputs.lookupFunction("main")
    main.header.returnType shouldEqual CoordT(ShareT, IntT.i32)
    compile.evalForKind(Vector()) match { case VonInt(5) => }
  }

  test("Test matching a multiple-member seq of mutables") {
    // Checks that the 5 made it into y, and it was an int
    val compile = RunCompilation.test(
      """
        |struct Marine { hp int; }
        |exported func main() int { [x, y] = (Marine(6), Marine(8)); return y.hp; }
      """.stripMargin)
    val coutputs = compile.expectCompilerOutputs()
    val main = coutputs.lookupFunction("main");
    main.header.returnType shouldEqual CoordT(ShareT, IntT.i32)
    compile.evalForKind(Vector()) match { case VonInt(8) => }
  }

  test("Test matching a multiple-member pack of immutable and own") {
    // Checks that the 5 made it into y, and it was an int
    val compile = RunCompilation.test(
      """
        |struct Marine { hp int; }
        |exported func main() int { [x, y] = (7, Marine(8)); return y.hp; }
      """.stripMargin)
    val coutputs = compile.expectCompilerOutputs()
    coutputs.functions.head.header.returnType == CoordT(ShareT, IntT.i32)
    compile.evalForKind(Vector()) match { case VonInt(8) => }
  }

  test("Test matching a multiple-member pack of immutable and borrow") {
    // Checks that the 5 made it into y, and it was an int
    val compile = RunCompilation.test(
      """
        |struct Marine { hp int; }
        |exported func main() int {
        |  m = Marine(8);
        |  [x, y] = (7, &m);
        |  return y.hp;
        |}
      """.stripMargin)
    val coutputs = compile.expectCompilerOutputs()
    coutputs.functions.head.header.returnType == CoordT(ShareT, IntT.i32)

    val monouts = compile.getMonouts()
    val tupDef = monouts.lookupStruct("Tup")
    val tupDefMemberTypes =
      tupDef.members.collect({ case NormalStructMemberT(_, _, tyype) => tyype.reference })
    tupDefMemberTypes match {
      case Vector(
        CoordT(ShareT,IntT(32)),
        CoordT(BorrowT,StructTT(IdT(_,Vector(),StructNameT(StructTemplateNameT(StrI("Marine")),Vector()))))) =>
      case null =>
//      case Vector(
//        ReferenceMemberTypeT(CoordT(own,PlaceholderT(IdT(_,Vector(StructTemplateNameT(StrI("Tup"))),PlaceholderNameT(PlaceholderTemplateNameT(0,CodeRuneS(StrI("T1")))))))),
//        ReferenceMemberTypeT(CoordT(OwnT,PlaceholderT(IdT(_,Vector(StructTemplateNameT(StrI("Tup"))),PlaceholderNameT(PlaceholderTemplateNameT(1,CodeRuneS(StrI("T2"))))))))) =>
    }
    compile.evalForKind(Vector()) match { case VonInt(8) => }
  }


  test("Test destructuring a shared") {
    val compile = RunCompilation.test(
      """
        |import array.iter.*;
        |exported func main() int {
        |  sm = #[#](#[#](42, 73, 73));
        |  foreach [i, m1] in sm {
        |    return i;
        |  }
        |}
      """.stripMargin)
    val coutputs = compile.expectCompilerOutputs()
    coutputs.functions.head.header.returnType == CoordT(ShareT, IntT.i32)
    compile.evalForKind(Vector()) match { case VonInt(42) => }
  }





//  test("Test if-let") {
//    // Checks that the 5 made it into y, and it was an int
//    val compile = RunCompilation.test(
//      """
//        |interface ISpaceship { }
//        |
//        |struct Firefly { fuel int; }
//        |impl ISpaceship for Firefly;
//        |
//        |exported func main() int {
//        |  s ISpaceship = Firefly(42);
//        |  return if (Firefly(fuel) = *s) {
//        |      fuel
//        |    } else {
//        |      73
//        |    }
//        |}
//      """.stripMargin)
//    val coutputs = compile.expectCompilerOutputs()
//    coutputs.functions.head.header.returnType == CoordT(ShareT, IntT.i32)
//    compile.evalForKind(Vector()) match { case VonInt(8) => }
//  }

  // Intentional known failure 2021.02.28, we never implemented pattern destructuring
//  test("Test imm struct param destructure") {
//    // Checks that the 5 made it into y, and it was an int
//    val compile = RunCompilation.test(
//      """
//        |
//        |struct Vec3 { x int; y int; z int; } exported func main() { refuelB(Vec3(1, 2, 3), 2); }
//        |// Using above Vec3
//        |
//        |// Without destructuring:
//        |func refuelA(
//        |    vec Vec3,
//        |    len int) {
//        |  Vec3(
//        |      vec.x * len,
//        |      vec.y * len,
//        |      vec.z * len)
//        |}
//        |
//        |// With destructuring:
//        |func refuelB(
//        |    Vec3(x, y, z),
//        |    len int) {
//        |  Vec3(x * len, y * len, z * len)
//        |}
//        |""".stripMargin)
//    val coutputs = compile.expectCompilerOutputs()
//    coutputs.functions.head.header.returnType == CoordT(ShareT, IntT.i32)
//    compile.evalForKind(Vector()) match { case VonInt(8) => }
//  }
}
