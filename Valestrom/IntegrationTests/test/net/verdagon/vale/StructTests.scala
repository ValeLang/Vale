package net.verdagon.vale

import net.verdagon.vale.vivem.PanicException
import net.verdagon.von.VonInt
import org.scalatest.{FunSuite, Matchers}

class StructTests extends FunSuite with Matchers {
  test("Make empty mut struct") {
    val compile = RunCompilation.test(
      """
        |struct Marine {}
        |fn main() export {
        |  Marine();
        |}
      """.stripMargin)

    compile.run(Vector())
  }

  test("Constructor with this") {
    val compile = RunCompilation.test( Tests.loadExpected("programs/structs/constructor.vale"))

    compile.evalForKind(Vector()) shouldEqual VonInt(10)
  }

  test("Make struct") {
    val compile = RunCompilation.test(
      """
        |struct Marine { hp int; }
        |fn main() export {
        |  Marine(9);
        |}
      """.stripMargin)

    compile.run(Vector())
  }

  test("Make struct and get member") {
    val compile = RunCompilation.test( Tests.loadExpected("programs/structs/getMember.vale"))
    compile.evalForKind(Vector()) shouldEqual VonInt(9)
  }

  test("Mutate struct") {
    val compile = RunCompilation.test( Tests.loadExpected("programs/structs/mutate.vale"))
    compile.evalForKind(Vector()) shouldEqual VonInt(4)
  }

  test("Normal destructure") {
    val compile = RunCompilation.test(
      """
        |struct Marine {
        |  hp int;
        |  ammo int;
        |}
        |fn main() int export {
        |  m = Marine(4, 7);
        |  Marine(hp, ammo) = m;
        |  ret ammo;
        |}
      """.stripMargin)

    compile.evalForKind(Vector()) shouldEqual VonInt(7)
  }

  test("Sugar destructure") {
    val compile = RunCompilation.test(
      """
        |struct Marine {
        |  hp int;
        |  ammo int;
        |}
        |fn main() int export {
        |  m = Marine(4, 7);
        |  destruct m;
        |  ret 9;
        |}
      """.stripMargin)

    compile.evalForKind(Vector()) shouldEqual VonInt(9)
  }

  test("Destroy members at right times") {
    val compile = RunCompilation.test(
      """
        |import printutils.*;
        |
        |struct Weapon #!DeriveStructDrop { }
        |fn drop(weapon Weapon) {
        |  println("Destroying weapon!");
        |  Weapon() = weapon;
        |}
        |struct Marine #!DeriveStructDrop {
        |  weapon Weapon;
        |}
        |fn drop(marine Marine) {
        |  println("Destroying marine!");
        |  Marine(weapon) = marine;
        |}
        |fn main() export {
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
//        |impl IFunction1<mut, *Marine, str> for GetMarineWeaponNameFunc;
//        |fn __call(this *!GetMarineWeaponNameFunc impl IFunction1<mut, *Marine, str>, m *Marine) str {
//        |  m.weapon.name
//        |}
//        |
//        |struct Weapon {
//        |  name str;
//        |  owner! Opt<*Marine>;
//        |}
//        |fn destructor(weapon Weapon) void {
//        |  println("Destroying weapon, owner's weapon is: " + weapon.owner.map(*!GetMarineWeaponNameFunc()).getOr("none"));
//        |  Weapon(name, owner) = weapon;
//        |}
//        |struct Marine {
//        |  weapon! Weapon;
//        |}
//        |fn destructor(marine Marine) void {
//        |  println("Destroying marine!");
//        |  set marine.weapon.owner = None<*Marine>();
//        |  Marine(weapon) = marine;
//        |}
//        |fn main() export {
//        |  m = Marine(Weapon("Sword", None<*Marine>()));
//        |  set m.weapon.owner = Some(*m);
//        |  set m.weapon = Weapon("Spear", Some(*m));
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
        |interface XOpt<T> rules(T Ref) {
        |  fn get(virtual opt &XOpt<T>) *T;
        |}
        |struct XSome<T> rules(T Ref) { value T; }
        |impl<T> XOpt<T> for XSome<T>;
        |struct XNone<T> rules(T Ref) { }
        |impl<T> XOpt<T> for XNone<T>;
        |
        |
        |fn get<T>(opt &XNone<T> impl XOpt<T>) *T { __vbi_panic(); }
        |fn get<T>(opt &XSome<T> impl XOpt<T>) *T { ret opt.value; }
        |
        |fn main() int export {
        |  m XOpt<int> = XNone<int>();
        |  ret m.get();
        |}
      """.stripMargin)

    try {
      compile.evalForKind(Vector())
      vfail() // It should panic instead
    } catch {
      case PanicException() =>
    }
  }


  test("Call borrow parameter with shared reference") {
    val compile = RunCompilation.test(
      """fn get<T>(a *T) *T { ret a; }
        |
        |fn main() int export {
        |  ret get(6);
        |}
      """.stripMargin)

    compile.evalForKind(Vector()) shouldEqual VonInt(6)
  }
}
