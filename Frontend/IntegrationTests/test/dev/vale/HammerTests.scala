package dev.vale

import dev.vale.passmanager.FullCompilationOptions
import dev.vale.finalast.{BlockH, CallH, ConsecutorH, ConstantIntH, InlineH, IntHT, NeverHT, PrototypeH, CoordH, ShareH}
import dev.vale.typing.types.ShareT
import dev.vale.testvm.PanicException
import dev.vale.simplifying._
import dev.vale.finalast.VariableIdH
import dev.vale.von.VonInt
import dev.vale.{finalast => m}
import org.scalatest.{FunSuite, Matchers}

import scala.collection.immutable.List

class HammerTests extends FunSuite with Matchers {
  // Hammer tests only test the general structure of things, not the generated nodes.
  // The generated nodes will be tested by end-to-end tests.

  test("Simple main") {
    val compile = RunCompilation.test(
      "exported func main() int { return 3; }")
    val hamuts = compile.getHamuts()

    val testPackage = hamuts.lookupPackage(PackageCoordinate.TEST_TLD(compile.interner, compile.keywords))
    vassert(testPackage.getAllUserFunctions.size == 1)
    testPackage.getAllUserFunctions.head.prototype.fullName.toFullString() shouldEqual """test::F("main")"""
  }

//  // Make sure a ListNode struct made it out
//  test("Templated struct makes it into hamuts") {
//    val compile = RunCompilation.test(
//      """
//        |struct ListNode<T> imm where T: Ref {
//        |  tail: *ListNode<T>;
//        |}
//        |func main(a: *ListNode:int) {}
//      """.stripMargin)
//    val hamuts = compile.getHamuts()
//    hamuts.structs.find(_.fullName.parts.last.humanName == "ListNode").get;
//  }

  test("Two templated structs make it into hamuts") {
    val compile = RunCompilation.test(
      """
        |func __pretend<T>() T { __vbi_panic() }
        |
        |interface MyOption<T Ref imm> imm { }
        |struct MyNone<T Ref imm> imm { }
        |impl<T Ref imm> MyOption<T> for MyNone<T>;
        |struct MySome<T Ref imm> imm { value T; }
        |impl<T Ref imm> MyOption<T> for MySome<T>;
        |
        |exported func main() {
        |  x = __pretend<MySome<int>>();
        |  y = __pretend<MyNone<int>>();
        |  z MyOption<int> = x;
        |}
      """.stripMargin)
    val packageH = compile.getHamuts().lookupPackage(PackageCoordinate.TEST_TLD(compile.interner, compile.keywords))
    vassertSome(
      packageH.interfaces.find(interface => {
        interface.fullName.toFullString() == """test::C(CT("MyOption"),[TR(R(@,<,i(32)))])"""
      }))

    val mySome = packageH.structs.find(_.fullName.toFullString() == """test::C(CT("MySome"),[TR(R(@,<,i(32)))])""").get;
    vassert(mySome.members.size == 1);
    vassert(mySome.members.head.tyype == CoordH[IntHT](ShareH, InlineH, IntHT.i32))

    val myNone = packageH.structs.find(_.fullName.toFullString() == """test::C(CT("MyNone"),[TR(R(@,<,i(32)))])""").get;
    vassert(myNone.members.isEmpty);
  }

  // Known failure 2020-08-20
  // Maybe we can turn off tree shaking?
  // Maybe this just violates requirements?
//  test("Virtual and override functions make it into hamuts") {
//    val compile = RunCompilation.test(
//      """
//        |interface Blark imm { }
//        |func wot(virtual b *Blark) int abstract;
//        |struct MyStruct export imm {}
//        |impl Blark for MyStruct;
//        |func wot(b *MyStruct impl Blark) int { return 9; }
//      """.stripMargin)
//    val packageH = compile.getHamuts().lookupPackage(PackageCoordinate.TEST_TLD(compile.interner, compile.keywords))
//    packageH.nonExternFunctions.find(f => f.prototype.fullName.toFullString().startsWith("""F("wot"""")).get;
//    packageH.nonExternFunctions.find(f => f.prototype.fullName.toFullString() == """F("MyStruct")""").get;
//    vassert(packageH.abstractFunctions.size == 2)
//    vassert(packageH.getAllUserImplementedFunctions.size == 1)
//    vassert(packageH.getAllUserFunctions.size == 1)
//  }

  test("Tests stripping things after panic") {
    val compile = RunCompilation.test(
      """
        |exported func main() int {
        |  __vbi_panic();
        |  a = 42;
        |  return a;
        |}
      """.stripMargin)
    val packageH = compile.getHamuts().lookupPackage(PackageCoordinate.TEST_TLD(compile.interner, compile.keywords))
    val main = packageH.lookupFunction("main")
    main.body match {
      case BlockH(CallH(PrototypeH(fullNameH, Vector(), CoordH(_, _, NeverHT(_))), Vector())) => {
        vassert(fullNameH.toFullString().contains("__vbi_panic"))
      }
    }
  }

  test("panic in expr") {
    val compile = RunCompilation.test(
      """
        |import intrange.*;
        |
        |exported func main() int {
        |  return 3 + __vbi_panic();
        |}
        |""".stripMargin)
    val packageH = compile.getHamuts().lookupPackage(PackageCoordinate.TEST_TLD(compile.interner, compile.keywords))
    val main = packageH.lookupFunction("main")
    val intExpr =
      main.body match {
        case BlockH(
          ConsecutorH(Vector(
            intExpr,
            CallH(
              PrototypeH(_,Vector(),CoordH(_,_,NeverHT(_))),
              Vector())))) => {
          intExpr
        }
      }
    Collector.only(intExpr, {
      case ConstantIntH(3, 32) =>
    })
  }

  test("Tests export function") {
    val compile = RunCompilation.test(
      """
        |exported func moo() int { return 42; }
        |""".stripMargin)
    val packageH = compile.getHamuts().lookupPackage(PackageCoordinate.TEST_TLD(compile.interner, compile.keywords))
    val moo = packageH.lookupFunction("moo")
    packageH.exportNameToFunction(compile.interner.intern(StrI("moo"))) shouldEqual moo.prototype
  }

  test("Tests export struct") {
    val compile = RunCompilation.test(
      """
        |exported struct Moo { }
        |""".stripMargin)
    val packageH = compile.getHamuts().lookupPackage(PackageCoordinate.TEST_TLD(compile.interner, compile.keywords))
    val moo = packageH.lookupStruct("Moo")
    packageH.exportNameToKind(compile.interner.intern(StrI("Moo"))) shouldEqual moo.getRef
  }

  test("Tests export interface") {
    val compile = RunCompilation.test(
      """
        |exported interface Moo { }
        |""".stripMargin)
    val packageH = compile.getHamuts().lookupPackage(PackageCoordinate.TEST_TLD(compile.interner, compile.keywords))
    val moo = packageH.lookupInterface("Moo")
    packageH.exportNameToKind(compile.interner.intern(StrI("Moo"))) shouldEqual moo.getRef
  }

  test("Tests exports from two modules, different names") {
    val interner = new Interner()
    val keywords = new Keywords(interner)
    val map = new FileCoordinateMap[String]()
    map.put(
      interner.intern(FileCoordinate(
        interner.intern(PackageCoordinate(
          interner.intern(StrI("moduleA")),
          Vector.empty)),
        "StructA.vale")),
      "exported struct StructA { a int; }")
    map.put(
      interner.intern(FileCoordinate(
        interner.intern(PackageCoordinate(
          interner.intern(StrI("moduleB")),
          Vector.empty)),
        "StructB.vale")),
      "exported struct StructB { a int; }")

    val compile =
      new RunCompilation(
        interner,
        keywords,
        Vector(
          PackageCoordinate.BUILTIN(interner, keywords),
          interner.intern(PackageCoordinate(
            interner.intern(StrI("moduleA")),
            Vector.empty)),
          interner.intern(PackageCoordinate(
            interner.intern(StrI("moduleB")),
            Vector.empty))),
        Builtins.getCodeMap(interner, keywords)
          .or(map)
          .or(Tests.getPackageToResourceResolver),
        FullCompilationOptions())
    val hamuts = compile.getHamuts()

    val packageA = hamuts.lookupPackage(interner.intern(PackageCoordinate(compile.interner.intern(StrI("moduleA")), Vector.empty)))
    val fullNameA = vassertSome(packageA.exportNameToKind.get(compile.interner.intern(StrI("StructA"))))

    val packageB = hamuts.lookupPackage(interner.intern(PackageCoordinate(compile.interner.intern(StrI("moduleB")), Vector.empty)))
    val fullNameB = vassertSome(packageB.exportNameToKind.get(compile.interner.intern(StrI("StructB"))))

    vassert(fullNameA != fullNameB)
  }

  // Intentional known failure, need to separate things internally inside Hammer
//  test("Tests exports from two modules, same name") {
//    val compile =
//      new RunCompilation(
//        Vector(PackageCoordinate.BUILTIN, PackageCoordinate(compile.interner.intern(StrI("moduleA")), Vector.empty), PackageCoordinate(compile.interner.intern(StrI("moduleB")), Vector.empty)),
//        Builtins.getCodeMap()
//          .or(
//            FileCoordinateMap(Map())
//              .add("moduleA", Vector.empty, "MyStruct.vale", "struct MyStruct export { a int; }")
//              .add("moduleB", Vector.empty, "MyStruct.vale", "struct MyStruct export { a int; }"))
//          .or(Tests.getPackageToResourceResolver),
//        FullCompilationOptions())
//    val hamuts = compile.getHamuts()
//
//    val packageA = hamuts.lookupPackage(PackageCoordinate(compile.interner.intern(StrI("moduleA")), Vector.empty))
//    val fullNameA = vassertSome(packageA.exportNameToKind.get("StructA"))
//
//    val packageB = hamuts.lookupPackage(PackageCoordinate(compile.interner.intern(StrI("moduleB")), Vector.empty))
//    val fullNameB = vassertSome(packageB.exportNameToKind.get("StructA"))
//
//    vassert(fullNameA != fullNameB)
//  }
}
