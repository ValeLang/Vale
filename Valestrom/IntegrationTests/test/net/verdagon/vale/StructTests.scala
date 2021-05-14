package net.verdagon.vale

import net.verdagon.vale.vivem.PanicException
import net.verdagon.von.VonInt
import org.scalatest.{FunSuite, Matchers}
import net.verdagon.vale.driver.Compilation

class StructTests extends FunSuite with Matchers {
  test("Make empty mut struct") {
    val compile = Compilation(
      """
        |struct Marine {}
        |fn main() {
        |  Marine();
        |}
      """.stripMargin)

    compile.run(Vector())
  }

  test("Constructor with this") {
    val compile = Compilation(Samples.get("programs/structs/constructor.vale"))

    compile.evalForReferend(Vector()) shouldEqual VonInt(10)
  }

  test("Make struct") {
    val compile = Compilation(
      """
        |struct Marine { hp int; }
        |fn main() {
        |  Marine(9);
        |}
      """.stripMargin)

    compile.run(Vector())
  }

  test("Make struct and get member") {
    val compile = Compilation(Samples.get("programs/structs/getMember.vale"))
    compile.evalForReferend(Vector()) shouldEqual VonInt(9)
  }

  test("Mutate struct") {
    val compile = Compilation(Samples.get("programs/structs/mutate.vale"))
    compile.evalForReferend(Vector()) shouldEqual VonInt(4)
  }

  test("Normal destructure") {
    val compile = Compilation(
      """
        |struct Marine {
        |  hp int;
        |  ammo int;
        |}
        |fn main() int export {
        |  m = Marine(4, 7);
        |  Marine(hp, ammo) = m;
        |  = ammo;
        |}
      """.stripMargin)

    compile.evalForReferend(Vector()) shouldEqual VonInt(7)
  }

  test("Sugar destructure") {
    val compile = Compilation(
      """
        |struct Marine {
        |  hp int;
        |  ammo int;
        |}
        |fn main() int export {
        |  m = Marine(4, 7);
        |  destruct m;
        |  = 9;
        |}
      """.stripMargin)

    compile.evalForReferend(Vector()) shouldEqual VonInt(9)
  }

  test("Destroy members at right times") {
    val compile = Compilation(
      """
        |struct Weapon { }
        |fn destructor(weapon Weapon) {
        |  println("Destroying weapon!");
        |  Weapon() = weapon;
        |}
        |struct Marine {
        |  weapon Weapon;
        |}
        |fn destructor(marine Marine) {
        |  println("Destroying marine!");
        |  Marine(weapon) = marine;
        |}
        |fn main() {
        |  Marine(Weapon());
        |}
      """.stripMargin +
        Samples.get("libraries/castutils.vale") +
        Samples.get("libraries/printutils.vale"))

    compile.evalForStdout(Vector()) shouldEqual "Destroying marine!\nDestroying weapon!\n"
  }

  // Known failure 2020-08-20
  test("Mutate destroys member after moving it out of the object") {
    val compile = Compilation(
      Samples.get("libraries/castutils.vale") +
        Samples.get("libraries/printutils.vale") +
      """
        |interface Opt<T> rules(T Ref) { }
        |struct Some<T> rules(T Ref) { value T; }
        |impl<T> Opt<T> for Some<T>;
        |struct None<T> rules(T Ref) { }
        |impl<T> Opt<T> for None<T>;
        |
        |fn getOr<T>(virtual opt &Opt<T>, default T) T abstract;
        |fn getOr<T>(opt &None<T> impl Opt<T>, default T) T {
        |  default
        |}
        |fn getOr<T>(opt &Some<T> impl Opt<T>, default T) T {
        |  opt.value
        |}
        |
        |fn map<T, R>(virtual opt &Opt<T>, func &!IFunction1<mut, T, R>) Opt<R> abstract;
        |fn map<T, R>(opt &None<T> impl Opt<T>, func &!IFunction1<mut, T, R>) Opt<R> {
        |  None<R>()
        |}
        |fn map<T, R>(opt &Some<T> impl Opt<T>, func &!IFunction1<mut, T, R>) Opt<R> {
        |  Some<R>(func(opt.value))
        |}
        |
        |struct GetMarineWeaponNameFunc { }
        |impl IFunction1<mut, &Marine, str> for GetMarineWeaponNameFunc;
        |fn __call(this &GetMarineWeaponNameFunc impl IFunction1<mut, &Marine, str>, m &Marine) str {
        |  m.weapon.name
        |}
        |
        |struct Weapon {
        |  name str;
        |  owner! Opt<&Marine>;
        |}
        |fn destructor(weapon Weapon) void {
        |  println("Destroying weapon, owner's weapon is: " + weapon.owner.map(&GetMarineWeaponNameFunc()).getOr("none"));
        |  Weapon(name, owner) = weapon;
        |}
        |struct Marine {
        |  weapon! Weapon;
        |}
        |fn destructor(marine Marine) void {
        |  println("Destroying marine!");
        |  set marine.weapon.owner = None<&Marine>();
        |  Marine(weapon) = marine;
        |}
        |fn main() {
        |  m = Marine(Weapon("Sword", None<&Marine>()));
        |  set m.weapon.owner = Some(&m);
        |  set m.weapon = Weapon("Spear", Some(&m));
        |}
      """.stripMargin)

    // The "Destroying weapon, owner's weapon is: Spear" is the important part.
    // That means that before the weapon's destructor was called, the new weapon
    // was already put in place. This behavior prevents us from accessing a
    // currently-destructing instance from the outside.

    compile.evalForStdout(Vector()) shouldEqual
      """Destroying weapon, owner's weapon is: Spear
        |Destroying marine!
        |Destroying weapon, owner's weapon is: none
        |""".stripMargin
  }


  test("Panic function") {
    val compile = Compilation(
      """
        |interface Opt<T> rules(T Ref) { }
        |struct Some<T> rules(T Ref) { value T; }
        |impl<T> Opt<T> for Some<T>;
        |struct None<T> rules(T Ref) { }
        |impl<T> Opt<T> for None<T>;
        |
        |fn get<T>(virtual opt &Opt<T>) &T abstract;
        |fn get<T>(opt &None<T> impl Opt<T>) &T { __panic() }
        |fn get<T>(opt &Some<T> impl Opt<T>) &T { opt.value }
        |
        |fn main() int export {
        |  m Opt<int> = None<int>();
        |  = m.get();
        |}
      """.stripMargin)

    try {
      compile.evalForReferend(Vector())
      vfail() // It should panic instead
    } catch {
      case PanicException() =>
    }
  }


  test("Call borrow parameter with shared reference") {
    val compile = Compilation(
      """fn get<T>(a &T) &T { a }
        |
        |fn main() int export {
        |  = get(6);
        |}
      """.stripMargin)

    compile.evalForReferend(Vector()) shouldEqual VonInt(6)
  }
}
