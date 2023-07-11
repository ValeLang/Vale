package dev.vale

import dev.vale.testvm.PanicException
import dev.vale.von.VonInt
import org.scalatest._

class StructTests extends FunSuite with Matchers {
  test("Make empty imm struct") {
    val compile = RunCompilation.test(
      """
        |struct Marine imm {}
        |exported func main() {
        |  Marine();
        |}
      """.stripMargin)

    compile.run(Vector())
  }

  test("Make imm struct with one member") {
    val compile = RunCompilation.test(
      """
        |struct Marine imm { hp int; }
        |exported func main() {
        |  Marine(7);
        |}
      """.stripMargin)

    compile.run(Vector())
  }

  test("Make nested imm struct") {
    val compile = RunCompilation.test(
      """
        |struct Weapon imm { ammo int; }
        |struct Marine imm { hp int; weapon Weapon; }
        |exported func main() {
        |  Marine(5, Weapon(7));
        |}
      """.stripMargin)

    compile.run(Vector())
  }

  test("Make empty mut struct") {
    val compile = RunCompilation.test(
      """
        |struct Marine {}
        |exported func main() {
        |  Marine();
        |}
      """.stripMargin)

    compile.run(Vector())
  }

  test("Constructor with self") {
    val compile = RunCompilation.test( Tests.loadExpected("programs/structs/constructor.vale"))

    compile.evalForKind(Vector()) match { case VonInt(10) => }
  }

  test("Make struct") {
    val compile = RunCompilation.test(
      """
        |struct Marine { hp int; }
        |exported func main() {
        |  Marine(9);
        |}
      """.stripMargin)

    compile.run(Vector())
  }

  test("Make struct and get member") {
    val compile = RunCompilation.test( Tests.loadExpected("programs/structs/getMember.vale"))
    compile.evalForKind(Vector()) match { case VonInt(9) => }
  }

  test("Mutate struct") {
    val compile = RunCompilation.test( Tests.loadExpected("programs/structs/mutate.vale"))
    compile.evalForKind(Vector()) match { case VonInt(4) => }
  }

  test("Normal destructure") {
    val compile = RunCompilation.test(
      """
        |struct Marine {
        |  hp int;
        |  ammo int;
        |}
        |exported func main() int {
        |  m = Marine(4, 7);
        |  Marine[hp, ammo] = m;
        |  return ammo;
        |}
      """.stripMargin)

    compile.evalForKind(Vector()) match { case VonInt(7) => }
  }

  test("Ignore destructure") {
    val compile = RunCompilation.test(
      """
        |struct Marine {
        |  hp int;
        |}
        |exported func main() int {
        |  m = Marine(4);
        |  Marine[_] = m;
        |  return 42;
        |}
    """.stripMargin)

    compile.evalForKind(Vector()) match {
      case VonInt(42) =>
    }
  }

  test("Sugar destructure") {
    val compile = RunCompilation.test(
      """
        |struct Marine {
        |  hp int;
        |  ammo int;
        |}
        |exported func main() int {
        |  m = Marine(4, 7);
        |  destruct m;
        |  return 9;
        |}
      """.stripMargin)

    compile.evalForKind(Vector()) match { case VonInt(9) => }
  }

  test("Destroy members at right times") {
    val compile = RunCompilation.test(
      """
        |import printutils.*;
        |
        |#!DeriveStructDrop
        |struct Weapon { }
        |func drop(weapon Weapon) {
        |  println("Destroying weapon!");
        |  Weapon[ ] = weapon;
        |}
        |#!DeriveStructDrop
        |struct Marine {
        |  weapon Weapon;
        |}
        |func drop(marine Marine) {
        |  println("Destroying marine!");
        |  Marine[weapon] = marine;
        |}
        |exported func main() {
        |  Marine(Weapon());
        |}
      """.stripMargin)

    compile.evalForStdout(Vector()) shouldEqual "Destroying marine!\nDestroying weapon!\n"
  }

  // Known failure 2020-08-20
//  test("Mutate destroys member after moving it out of the object") {
//    val compile = RunCompilation.test(
//      """import optutils.*;
//        |import printutils.*;
//        |
//        |struct GetMarineWeaponNameFunc { }
//        |impl IFunction1<mut, &Marine, str> for GetMarineWeaponNameFunc;
//        |func __call(this *!GetMarineWeaponNameFunc impl IFunction1<mut, &Marine, str>, m &Marine) str {
//        |  m.weapon.name
//        |}
//        |
//        |struct Weapon {
//        |  name str;
//        |  owner! Opt<&Marine>;
//        |}
//        |func destructor(weapon Weapon) void {
//        |  println("Destroying weapon, owner's weapon is: " + weapon.owner.map(&!GetMarineWeaponNameFunc()).getOr("none"));
//        |  Weapon(name, owner) = weapon;
//        |}
//        |struct Marine {
//        |  weapon! Weapon;
//        |}
//        |func destructor(marine Marine) void {
//        |  println("Destroying marine!");
//        |  set marine.weapon.owner = None<&Marine>();
//        |  Marine(weapon) = marine;
//        |}
//        |exported func main() {
//        |  m = Marine(Weapon("Sword", None<&Marine>()));
//        |  set m.weapon.owner = Some(&m);
//        |  set m.weapon = Weapon("Spear", Some(&m));
//        |}
//      """.stripMargin)
//
//    // The "Destroying weapon, owner's weapon is: Spear" is the important part.
//    // That means that before the weapon's destructor was called, the new weapon
//    // was already put in place. This behavior prevents us from accessing a
//    // currently-destructing instance from the outside.
//
//    compile.evalForStdout(Vector()) shouldEqual
//      """Destroying weapon, owner's weapon is: Spear
//        |Destroying marine!
//        |Destroying weapon, owner's weapon is: none
//        |""".stripMargin
//  }


  test("Panic function") {
    val compile = RunCompilation.test(
      """
        |sealed interface XOpt<T Ref>
        |where func drop(T)void {
        |  func get(virtual opt &XOpt<T>) &T;
        |}
        |
        |struct XNone<T Ref> where func drop(T)void  { }
        |impl<T> XOpt<T> for XNone<T>;
        |
        |func get<T>(opt &XNone<T>) &T {
        |  __vbi_panic();
        |}
        |
        |exported func main() int {
        |  m XOpt<int> = XNone<int>();
        |  return m.get();
        |}
      """.stripMargin)

    try {
      compile.evalForKind(Vector())
      vfail() // It should panic instead
    } catch {
      case PanicException() =>
    }
  }
}
