package net.verdagon.vale

import net.verdagon.vale.hammer._
import net.verdagon.vale.metal.{BlockH, CallH, InlineH, IntH, NeverH, PrototypeH, ReferenceH}
import net.verdagon.vale.{metal => m}
import net.verdagon.vale.templar.types.Share
import org.scalatest.{FunSuite, Matchers}
import net.verdagon.vale.driver.Compilation

class HammerTests extends FunSuite with Matchers {
  // Hammer tests only test the general structure of things, not the generated nodes.
  // The generated nodes will be tested by end-to-end tests.

  test("Simple main") {
    val compile = new Compilation(
      "fn main(){3}")
    val hamuts = compile.getHamuts()

    vassert(hamuts.getAllUserFunctions.size == 1)
    hamuts.getAllUserFunctions.head.prototype.fullName.toString shouldEqual """F("main")"""
  }

//  // Make sure a ListNode struct made it out
//  test("Templated struct makes it into hamuts") {
//    val compile = new Compilation(
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
    val compile = new Compilation(
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
    hamuts.interfaces.find(_.fullName.toString == """C("MyOption",[TR(R(*,<,i))])""").get;

    val mySome = hamuts.structs.find(_.fullName.toString == """C("MySome",[TR(R(*,<,i))])""").get;
    vassert(mySome.members.size == 1);
    vassert(mySome.members.head.tyype == ReferenceH[IntH](m.ShareH, InlineH, IntH()))

    val myNone = hamuts.structs.find(_.fullName.toString == """C("MyNone",[TR(R(*,<,i))])""").get;
    vassert(myNone.members.isEmpty);
  }

  // Known failure 2020-08-05
  test("Virtual and override functions make it into hamuts") {
    val compile = new Compilation(
      """
        |interface Blark imm { }
        |fn wot(virtual b *Blark) int abstract;
        |struct MyStruct export imm {}
        |impl Blark for MyStruct;
        |fn wot(b *MyStruct impl Blark) int { 9 }
      """.stripMargin)
    val hamuts = compile.getHamuts()
    hamuts.nonExternFunctions.find(f => f.prototype.fullName.toString.startsWith("""F("wot"""")).get;
    hamuts.nonExternFunctions.find(f => f.prototype.fullName.toString == """F("MyStruct")""").get;
    vassert(hamuts.abstractFunctions.size == 2)
    vassert(hamuts.getAllUserImplementedFunctions.size == 1)
    vassert(hamuts.getAllUserFunctions.size == 1)
  }

  test("Tests stripping things after panic") {
    val compile = new Compilation(
      """
        |fn main() int {
        |  panic();
        |  a = 42;
        |  = a;
        |}
      """.stripMargin)
    val hamuts = compile.getHamuts()
    val main = hamuts.lookupFunction("main")
    main.body match {
      case BlockH(CallH(PrototypeH(fullNameH, List(), ReferenceH(_, _, NeverH())), List())) => {
        vassert(fullNameH.toString.contains("panic"))
      }
    }
  }


  test("Tests export function") {
    val compile = new Compilation(
      """
        |fn moo() export { 42 }
        |""".stripMargin)
    val hamuts = compile.getHamuts()
    val moo = hamuts.lookupFunction("moo")
  }

  test("Tests export struct") {
    val compile = new Compilation(
      """
        |struct Moo export { }
        |""".stripMargin)
    val hamuts = compile.getHamuts()
    val moo = hamuts.lookupStruct("Moo")
  }
}
