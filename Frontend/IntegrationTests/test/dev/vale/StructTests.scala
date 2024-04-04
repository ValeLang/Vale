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
        |import v.builtins.panic.*;
        |import v.builtins.drop.*;
        |
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
      """.stripMargin, false)

    try {
      compile.evalForKind(Vector())
      vfail() // It should panic instead
    } catch {
      case PanicException() =>
    }
  }

  test("ODMFRC") {
    // Order doesnt matter for resolving calls (ODMFRC)
    //
    // When it was in this order:
    //
    //   import v.builtins.opt.*;
    //
    //   struct _B { }
    //   func __call(self &_B) int { 0 }
    //
    //   struct _C<H>
    //   where func(&H)int, func drop(H)void {
    //     hasher H;
    //   }
    //
    //   struct _A {
    //     idByName _C<_B>;
    //   }
    //
    // we got an error because while compiling _A, after making its inner env, we
    //  1. tried to resolve _C<_B>. During that, we
    //  2. tried resolving __call(&_B), because _C's definition says that func(&H) should exist.
    //     During that, we
    //  3. attempted the candidate func __call(self &_B) int { 0 }. It successfully solved,
    //     including making a call to _B<>.
    //  4. Once we had that, we wanted to pull in all the bounds from _B, since it's a parameter
    //     and every function wants to pull in bounds from its parameters. But to do that, it
    //  5. looked for the inner env of _B... which didn't exist.
    //
    // We solved it by not doing all these steps right after making the inner env.
    // We now do all the resolves in a phase after making the inner env.
    //
    // Search ODMFRC for the class that helps with this.

    val code =
      """
        |import v.builtins.opt.*;
        |
        |struct _X { }
        |func __call(self &_X) int { 0 }
        |
        |struct _Y<H>
        |where func(&H)int, func drop(H)void {
        |  hasher H;
        |}
        |
        |struct _Z {
        |  idByName _Y<_X>;
        |}
    """.stripMargin

    for (replacements <- U.scrambles(Map("_X" -> "_A", "_Y" -> "_B", "_Z" -> "_C"))) {
      val replacedCode = U.replaceAll(code, replacements)
      RunCompilation.test(replacedCode).getMonouts()
    }
  }
}
