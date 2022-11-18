package dev.vale

import dev.vale.finalast._
import dev.vale.highertyping.{ICompileErrorA, ProgramA}
import dev.vale.lexing.{FailedParse, RangeL}
import dev.vale.options.GlobalOptions
import dev.vale.parsing.ast.FileP
import dev.vale.passmanager.{FullCompilation, FullCompilationOptions}
import dev.vale.postparsing.{ICompileErrorS, _}
import dev.vale.testvm._
import dev.vale.typing.ast._
import dev.vale.typing.citizen.WeakableImplingMismatch
import dev.vale.typing.expression.TookWeakRefOfNonWeakableError
import dev.vale.typing.names.{FunctionNameT, FunctionTemplateNameT}
import dev.vale.typing.templata.MutabilityTemplata
import dev.vale.typing.types.{CoordT, ImmutableT, IntT, ShareT, contentsRuntimeSizedArrayTT}
import dev.vale.typing.{Hinputs, ICompileErrorT}
import dev.vale.von.{IVonData, VonBool, VonFloat, VonInt}
import org.scalatest.{FunSuite, Matchers}


class AfterRegionsIntegrationTests extends FunSuite with Matchers {

  test("TODO") {
    // see MSBAMFI
    vimpl()

    // only look at function bounds from the caller's environment, dont get any actual functions
    // from there. we can get actual functions from the type's environment, however.
    vimpl()

    // every time we do a templatas substitute, we do a substitutions for any of their bounds in the
    // coutputs. that's likely really expensive.
    // and it might be unnecessary? can the monomorphizer resolve those mappings themselves? perhaps
    // there's some in-between where templar can track merely that a bound *was* satisfied, but not
    // what satisfied it.
    // would that be enough for e.g. cases, which bring in bounds from the kind they're matching?
    // and parameters and stuff?
    vimpl()

    // had a bug when as was defined like this:
    //   extern("vale_as_subtype")
    //   func as<SubKind Kind, SuperType Ref>(left SuperType) Result<SubType, SuperType>
    //     where O Ownership,
    //   SuperKind Kind,
    //   SubType Ref = Ref[O, SubKind],
    //   SuperType Ref = Ref[O, SuperKind],
    //   implements(SubType, SuperType);
    // the definition assumed O was own, and the call inferred O to be borrow.
    // this cause some mayhem further down when a name didnt match.
    vimpl()
  }

  test("Test returning empty seq") {
    val compile = RunCompilation.test(
      """
        |export () as Tup0;
        |exported func main() () {
        |  return ();
        |}
        |""".stripMargin)
    val coutputs = compile.expectCompilerOutputs()

    compile.run(Vector())
  }

  test("Map function") {
    val compile = RunCompilation.test(
      Tests.loadExpected("programs/genericvirtuals/mapFunc.vale"))
    compile.expectCompilerOutputs()

    compile.evalForKind(Vector()) match { case VonBool(true) => }
  }

  test("imm tuple access") {
    vfail() // these tuples are actually mutable
    val compile = RunCompilation.test(Tests.loadExpected("programs/tuples/immtupleaccess.vale"))
    compile.evalForKind(Vector()) match { case VonInt(42) => }
  }

  test("Test overload set") {
    val compile =
      RunCompilation.test(
        """
          |import array.each.*;
          |func myfunc(i int) { }
          |exported func main() int {
          |  mylist = [#][1, 3, 3, 7];
          |  mylist.each(myfunc);
          |  42
          |}
          |""".stripMargin)
    compile.evalForKind(Vector()) match { case VonInt(42) => }
  }

  test("Simple tuple with one int") {
    val compile = RunCompilation.test( "exported func main() int { return (9,).0; }")

    val coutputs = compile.expectCompilerOutputs()
    coutputs.lookupFunction("main").header.returnType.kind shouldEqual IntT.i32
    // Funny story, theres no such thing as a one element tuple! It becomes a one element array.
    Collector.only(coutputs.lookupFunction("main"), { case TupleTE(_, _) => })

    compile.evalForKind(Vector()) match { case VonInt(9) => }
  }

  test("Upcasting in a generic function") {
    // This is testing two things:
    //  - Upcasting inside a generic function
    //  - The return type's ownership is actually calculated from the parameter. This will
    //    fail as long as we still have CoordT(Ownership, ITemplata[KindTemplataType])
    //    because that ownership isn't a templata. The call site will correctly have that
    //    ownership as borrow, but the definition will think it's an own, *not* a placeholder
    //    or variable-thing or anything like that. So, when it gets to the monomorphizer, it
    //    will actually make the wrong return type. I think the solution will be to make CoordT
    //    contain a placeholder, and move O to be a generic param.
    val compile = RunCompilation.test(
      """
        |func upcast<SuperKind Kind, SubType Ref>(left SubType) SuperType
        |where O Ownership,
        |  SubKind Kind,
        |  SuperType Ref = Ref[O, SuperKind],
        |  SubType Ref = Ref[O, SubKind],
        |  implements(SubType, SuperType)
        |{
        |  left
        |}
        |
        |sealed interface IShip  {}
        |struct Serenity {}
        |impl IShip for Serenity;
        |
        |exported func main() {
        |  ship &IShip = upcast<IShip>(&Serenity());
        |}
        |
        |""".stripMargin)

    compile.evalForKind(Vector())
  }

  test("Diff iter") {
    // When we try to compile this:
    //   HashSetDiffIterator<K>(a.table, b, 0)
    // it makes sure all the struct rules pass, including its members, including this:
    //   table &[]Opt<X>;
    // And here we get a conflict:
    //   Conflict, thought rune X was Kind$_0 but now concluding it's Kind$_0
    // because one is Share ownership, and one is Own. (they look similar dont they)
    // I think it's because HashSet<K Ref imm> has an imm there, and HashSetDiffIterator<X> doesn't.
    // We need a better error message.
    val compile = RunCompilation.test(
      """
        |
        |#!DeriveStructDrop
        |struct HashSet<K Ref imm> {
        |  table! Array<mut, Opt<K>>;
        |  size! int;
        |}
        |
        |struct HashSetDiffIterator<X> {
        |  table &[]Opt<X>;
        |  otherTable &HashSet<X>;
        |  pos! int;
        |}
        |
        |func diff_iter<K>(
        |  a &HashSet<K>,
        |  b &HashSet<K>)
        |HashSetDiffIterator<K> {
        |  HashSetDiffIterator<K>(a.table, b, 0)
        |}
        |
        |exported func main() int {
        |  hash = HashSet([]Opt<int>(0), 0);
        |  diff_iter(&hash, &hash);
        |  destruct hash;
        |  14
        |}
        |
        |""".stripMargin)

    compile.evalForKind(Vector()) match { case VonInt(14) => }
  }

  test("Call Array<> without element type") {
    val compile = RunCompilation.test(
      """
        |exported func main() int {
        |  a = Array<imm>(3, {13 + _});
        |  sum = 0;
        |  drop_into(a, &(e) => { set sum = sum + e; });
        |  return sum;
        |}
      """.stripMargin)

    compile.evalForKind(Vector()) match { case VonInt(42) => }
  }

  test("Make array without type") {
    val compile = RunCompilation.test(
      """
        |exported func main() int {
        |  a = #[](10, {_});
        |  return a.3;
        |}
      """.stripMargin)

    val coutputs = compile.expectCompilerOutputs()
    compile.evalForKind(Vector()) match { case VonInt(3) => }
  }

  test("Abstract func without virtual") {
    val compile = RunCompilation.test(
      """
        |sealed interface ISpaceship<X Ref, Y Ref, Z Ref> { }
        |abstract func launch<X, Y, Z>(self &ISpaceship<X, Y, Z>, bork X) where func drop(X)void;
        |
        |exported func main() int {
        |  a = #[](10, {_});
        |  return a.3;
        |}
      """.stripMargin)

    compile.getCompilerOutputs() match {
      case Err(e) => vimpl(e)
      case Ok(_) => vfail()
    }
  }

  test("Cant make non-weakable extend a weakable") {
    val compile = RunCompilation.test(
      """
        |weakable interface IUnit {}
        |struct Muta { hp int; }
        |impl IUnit for Muta;
        |func main(muta Muta) int  { return 7; }
        |""".stripMargin)

    try {
      compile.expectCompilerOutputs().lookupFunction("main")
      vfail()
    } catch {
      case WeakableImplingMismatch(false, true) =>
      case other => {
        other.printStackTrace()
        vfail()
      }
    }
  }


  test("Cant make weakable extend a non-weakable") {
    val compile = RunCompilation.test(
      """
        |interface IUnit {}
        |weakable struct Muta { hp int; }
        |impl IUnit for Muta;
        |func main(muta Muta) int  { return 7; }
        |""".stripMargin)

    try {
      compile.expectCompilerOutputs().lookupFunction("main")
      vfail()
    } catch {
      case WeakableImplingMismatch(true, false) =>
      case _ => vfail()
    }
  }
  test("Cant make weak ref to non-weakable") {
    val compile = RunCompilation.test(
      """
        |struct Muta { hp int; }
        |func getHp(weakMuta &&Muta) { (lock(weakMuta)).get().hp }
        |exported func main() int { getHp(&&Muta(7)) }
        |""".stripMargin)

    try {
      compile.expectCompilerOutputs().lookupFunction("main")
      vfail()
    } catch {
      case TookWeakRefOfNonWeakableError() =>
      case _ => vfail()
    }

  }

  test("Borrowing toArray") {
    val compile = RunCompilation.test(
      """import list.*;
        |
        |func toArray<E>(list &List<E>) []<mut>&E {
        |  return []&E(list.len(), { list.get(_) });
        |}
        |
        |exported func main() int {
        |  l = List<int>();
        |  add(&l, 5);
        |  add(&l, 9);
        |  add(&l, 7);
        |  return l.toArray().get(1);
        |}
        |
        """.stripMargin)

    compile.evalForKind(Vector()) match { case VonInt(9) => }
  }

  test("Infinite lambda call") {
    val compile = RunCompilation.test(
      """
        |exported func main() int {
        |  lam = (f, z) => {
        |    f(f, z)
        |  };
        |  lam(lam, 7);
        |}
        |
      """.stripMargin)
    val coutputs = compile.expectCompilerOutputs()
    compile.evalForKind(Vector()) match { case VonInt(8) => }
  }


  test("Ignoring receiver") {
    val compile = RunCompilation.test(
      """
        |struct Marine { hp int; }
        |exported func main() int { [_, y] = (Marine(6), Marine(8)); return y.hp; }
        |
      """.stripMargin)
    val coutputs = compile.expectCompilerOutputs()
    val main = coutputs.lookupFunction("main");
    main.header.returnType shouldEqual CoordT(ShareT, IntT.i32)
    compile.evalForKind(Vector()) match { case VonInt(8) => }
  }
}
