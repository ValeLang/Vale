package net.verdagon.vale

import net.verdagon.vale.driver.FullCompilationOptions
import net.verdagon.vale.hammer._
import net.verdagon.vale.metal.{BlockH, CallH, InlineH, IntH, NeverH, PrototypeH, ReadonlyH, ReferenceH}
import net.verdagon.vale.{metal => m}
import net.verdagon.vale.templar.types.Share
import org.scalatest.{FunSuite, Matchers}
import net.verdagon.von.VonInt

import scala.collection.immutable.List

class HammerTests extends FunSuite with Matchers {
  // Hammer tests only test the general structure of things, not the generated nodes.
  // The generated nodes will be tested by end-to-end tests.

  test("Simple main") {
    val compile = RunCompilation.test(
      "fn main() int export {3}")
    val hamuts = compile.getHamuts()

    vassert(hamuts.getAllUserFunctions.size == 1)
    hamuts.getAllUserFunctions.head.prototype.fullName.toFullString() shouldEqual """"test":F("main")"""
  }

//  // Make sure a ListNode struct made it out
//  test("Templated struct makes it into hamuts") {
//    val compile = RunCompilation.test(
//      """
//        |struct ListNode<T> imm rules(T: Ref) {
//        |  tail: *ListNode<T>;
//        |}
//        |fn main(a: *ListNode:int) {}
//      """.stripMargin)
//    val hamuts = compile.getHamuts()
//    hamuts.structs.find(_.fullName.parts.last.humanName == "ListNode").get;
//  }

  test("Two templated structs make it into hamuts") {
    val compile = RunCompilation.test(
      """
        |interface MyOption<T> imm rules(T Ref) { }
        |struct MyNone<T> imm rules(T Ref) { }
        |impl<T> MyOption<T> for MyNone<T>;
        |struct MySome<T> imm rules(T Ref) { value T; }
        |impl<T> MyOption<T> for MySome<T>;
        |
        |fn main(a *MySome<int>, b *MyNone<int>) {}
      """.stripMargin)
    val hamuts = compile.getHamuts()
    hamuts.interfaces.find(_.fullName.toFullString() == """"test":C("MyOption",[TR(R(*,<,#,i))])""").get;

    val mySome = hamuts.structs.find(_.fullName.toFullString() == """"test":C("MySome",[TR(R(*,<,#,i))])""").get;
    vassert(mySome.members.size == 1);
    vassert(mySome.members.head.tyype == ReferenceH[IntH](m.ShareH, InlineH, ReadonlyH, IntH()))

    val myNone = hamuts.structs.find(_.fullName.toFullString() == """"test":C("MyNone",[TR(R(*,<,#,i))])""").get;
    vassert(myNone.members.isEmpty);
  }

  // Known failure 2020-08-20
  // Maybe we can turn off tree shaking?
  // Maybe this just violates requirements?
  test("Virtual and override functions make it into hamuts") {
    val compile = RunCompilation.test(
      """
        |interface Blark imm { }
        |fn wot(virtual b *Blark) int abstract;
        |struct MyStruct export imm {}
        |impl Blark for MyStruct;
        |fn wot(b *MyStruct impl Blark) int { 9 }
      """.stripMargin)
    val hamuts = compile.getHamuts()
    hamuts.nonExternFunctions.find(f => f.prototype.fullName.toFullString().startsWith("""F("wot"""")).get;
    hamuts.nonExternFunctions.find(f => f.prototype.fullName.toFullString() == """F("MyStruct")""").get;
    vassert(hamuts.abstractFunctions.size == 2)
    vassert(hamuts.getAllUserImplementedFunctions.size == 1)
    vassert(hamuts.getAllUserFunctions.size == 1)
  }

  test("Tests stripping things after panic") {
    val compile = RunCompilation.test(
      """
        |fn main() int export {
        |  __panic();
        |  a = 42;
        |  = a;
        |}
      """.stripMargin)
    val hamuts = compile.getHamuts()
    val main = hamuts.lookupFunction("main")
    main.body match {
      case BlockH(CallH(PrototypeH(fullNameH, List(), ReferenceH(_, _, ReadonlyH, NeverH())), List())) => {
        vassert(fullNameH.toFullString().contains("__panic"))
      }
    }
  }

  test("Tests export function") {
    val compile = RunCompilation.test(
      """
        |fn moo() int export { 42 }
        |""".stripMargin)
    val hamuts = compile.getHamuts()
    val moo = hamuts.lookupFunction("moo")
    hamuts.moduleNameToExportedNameToExportee(FileCoordinateMap.TEST_MODULE)("moo") shouldEqual
      (PackageCoordinate(FileCoordinateMap.TEST_MODULE, List()), moo.fullName)
  }

  test("Tests export struct") {
    val compile = RunCompilation.test(
      """
        |struct Moo export { }
        |""".stripMargin)
    val hamuts = compile.getHamuts()
    val moo = hamuts.lookupStruct("Moo")
    hamuts.moduleNameToExportedNameToExportee(FileCoordinateMap.TEST_MODULE)("Moo") shouldEqual
      (PackageCoordinate(FileCoordinateMap.TEST_MODULE, List()), moo.fullName)
  }

  test("Tests export struct twice") {
    val compile = RunCompilation.test(
      """
        |struct Moo export { }
        |export Moo as Bork;
        |""".stripMargin)
    val hamuts = compile.getHamuts()
    val moo = hamuts.lookupStruct("Moo")
    hamuts.moduleNameToExportedNameToExportee(FileCoordinateMap.TEST_MODULE)("Moo") shouldEqual
      (PackageCoordinate(FileCoordinateMap.TEST_MODULE, List()), moo.fullName)
    hamuts.moduleNameToExportedNameToExportee(FileCoordinateMap.TEST_MODULE)("Bork") shouldEqual
      (PackageCoordinate(FileCoordinateMap.TEST_MODULE, List()), moo.fullName)
  }

  test("Tests export interface") {
    val compile = RunCompilation.test(
      """
        |interface Moo export { }
        |""".stripMargin)
    val hamuts = compile.getHamuts()
    val moo = hamuts.lookupInterface("Moo")
    hamuts.moduleNameToExportedNameToExportee(FileCoordinateMap.TEST_MODULE)("Moo") shouldEqual
      (PackageCoordinate(FileCoordinateMap.TEST_MODULE, List()), moo.fullName)
  }

  test("Tests recovering directory from export") {
    val compile =
      new RunCompilation(
        List(PackageCoordinate.BUILTIN, PackageCoordinate("moduleA", List("someDir", "someOtherDir"))),
        Builtins.getCodeMap()
          .or(
            FileCoordinateMap(Map())
              .add("moduleA", List("someDir", "someOtherDir"), "StructA.vale", "struct StructA export { a int; }"))
          .or(Tests.getPackageToResourceResolver),
        FullCompilationOptions())
    val hamuts = compile.getHamuts()

    hamuts.moduleNameToExportedNameToExportee("moduleA")("StructA") match {
      case (PackageCoordinate("moduleA", List("someDir", "someOtherDir")), _) =>
    }
  }

  test("Tests exports from two modules, different names") {
    val compile =
      new RunCompilation(
        List(PackageCoordinate.BUILTIN, PackageCoordinate("moduleA", List()), PackageCoordinate("moduleB", List())),
        Builtins.getCodeMap()
          .or(
            FileCoordinateMap(Map())
              .add("moduleA", List(), "StructA.vale", "struct StructA export { a int; }")
              .add("moduleB", List(), "StructB.vale", "struct StructB export { a int; }"))
          .or(Tests.getPackageToResourceResolver),
        FullCompilationOptions())
    val hamuts = compile.getHamuts()

    val fullNameA =
      hamuts.moduleNameToExportedNameToExportee("moduleA")("StructA") match {
        case (PackageCoordinate("moduleA", List()), fullName) => fullName
      }

    val fullNameB =
      hamuts.moduleNameToExportedNameToExportee("moduleB")("StructB") match {
        case (PackageCoordinate("moduleB", List()), fullName) => fullName
      }

    vassert(fullNameA != fullNameB)
  }

  // Intentional failure, we want these to be invisible to each other and both make it through
  test("Tests exports from two modules, same name") {
    val compile =
      new RunCompilation(
        List(PackageCoordinate.BUILTIN, PackageCoordinate("moduleA", List()), PackageCoordinate("moduleB", List())),
        Builtins.getCodeMap()
          .or(
            FileCoordinateMap(Map())
              .add("moduleA", List(), "MyStruct.vale", "struct MyStruct export { a int; }")
              .add("moduleB", List(), "MyStruct.vale", "struct MyStruct export { a int; }"))
          .or(Tests.getPackageToResourceResolver),
        FullCompilationOptions())
    val hamuts = compile.getHamuts()

    val fullNameA =
      hamuts.moduleNameToExportedNameToExportee("moduleA")("MyStruct") match {
        case (PackageCoordinate("moduleA", List()), fullName) => fullName
      }

    val fullNameB =
      hamuts.moduleNameToExportedNameToExportee("moduleB")("MyStruct") match {
        case (PackageCoordinate("moduleB", List()), fullName) => fullName
      }

    vassert(fullNameA != fullNameB)
  }
}
