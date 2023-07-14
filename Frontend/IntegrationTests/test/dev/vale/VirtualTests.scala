package dev.vale

import dev.vale.instantiating.ast._
import dev.vale.typing.{ast, types}
import dev.vale.typing.ast.{AbstractT, SignatureT}
import dev.vale.typing.names._
import dev.vale.typing.types._
import dev.vale.testvm.IntV
import dev.vale.typing.ast._
import dev.vale.typing.templata.ITemplataT.{expectCoord, expectCoordTemplata}
import dev.vale.typing.types._
import dev.vale.von.{VonInt, VonStr}
import org.scalatest._

class VirtualTests extends FunSuite with Matchers {

    test("Simple program containing a virtual function") {
      val compile = RunCompilation.test(
        """
          |sealed interface I  {}
          |func doThing(virtual i I) int { return 4; }
          |func main(i I) int {
          |  return doThing(i);
          |}
        """.stripMargin, false)
      val coutputs = compile.expectCompilerOutputs()
      val interner = compile.interner
      val keywords = compile.keywords

      vassert(coutputs.getAllUserFunctions.size == 2)
      vassert(coutputs.lookupFunction("main").header.returnType == CoordT(ShareT, RegionT(), IntT.i32))

      val doThing =
        vassertSome(
          coutputs.lookupFunction(
            SignatureT(
              IdT(
                PackageCoordinate.TEST_TLD(interner, keywords),
                Vector.empty,
                interner.intern(
                  FunctionNameT(
                    interner.intern(FunctionTemplateNameT(
                      interner.intern(StrI("doThing")),
                      CodeLocationS(
                        interner.intern(FileCoordinate(
                          interner.intern(PackageCoordinate(interner.intern(StrI("test")),Vector())),"0.vale")), 24))),
                    Vector.empty,
                    Vector(
                      CoordT(
                        OwnT,
                        RegionT(),
                        interner.intern(
                          InterfaceTT(
                            IdT(PackageCoordinate.TEST_TLD(interner, keywords), Vector.empty, interner.intern(InterfaceNameT(interner.intern(InterfaceTemplateNameT(interner.intern(StrI("I")))), Vector.empty)))))))))))))
      vassert(doThing.header.params(0).virtuality.get == AbstractT())
    }

  test("Can call virtual function") {
    val compile = RunCompilation.test(
      """
        |sealed interface I  {}
        |func doThing(virtual i I) int { return 4; }
        |func main(i I) int {
        |  return doThing(i);
        |}
      """.stripMargin, false)
    val coutputs = compile.expectCompilerOutputs()
    val interner = compile.interner
    val keywords = compile.keywords

    vassert(coutputs.getAllUserFunctions.size == 2)
    vassert(coutputs.lookupFunction("main").header.returnType == CoordT(ShareT, RegionT(), IntT.i32))


    val doThing =
      vassertSome(
        coutputs.lookupFunction(
          ast.SignatureT(
            IdT(
              PackageCoordinate.TEST_TLD(interner, keywords),
              Vector.empty,
              interner.intern(
                FunctionNameT(
                  interner.intern(
                    FunctionTemplateNameT(
                      interner.intern(StrI("doThing")),
                      CodeLocationS(
                        interner.intern(FileCoordinate(
                          interner.intern(PackageCoordinate(interner.intern(StrI("test")),Vector())),"0.vale")), 24))),
                  Vector.empty,
                  Vector(
                    CoordT(
                      OwnT,
                      RegionT(),
                      interner.intern(
                        InterfaceTT(
                          IdT(PackageCoordinate.TEST_TLD(interner, keywords), Vector.empty, interner.intern(InterfaceNameT(interner.intern(InterfaceTemplateNameT(interner.intern(StrI("I")))), Vector.empty)))))))))))))
    vassert(doThing.header.params(0).virtuality.get == AbstractT())
  }

  test("Owning interface") {
    val compile = RunCompilation.test(
      """
        |exported func main() int {
        |  x Opt<int> = Some(7);
        |  return 7;
        |}
        |""".stripMargin)
    compile.evalForKind(Vector()) match { case VonInt(7) => }
  }

  test("Simple override with param and bound") {
    // This is the Serenity case in ROWC.
    val compile = RunCompilation.test(
      """
        |import v.builtins.drop.*;
        |
        |sealed interface ISpaceship<E Ref, F Ref, G Ref> { }
        |abstract func launch<X, Y, Z>(virtual self &ISpaceship<X, Y, Z>, bork X)
        |    where func drop(X)void;
        |
        |struct Serenity<A Ref, B Ref, C Ref> { }
        |impl<H, I, J> ISpaceship<H, I, J> for Serenity<H, I, J>;
        |func launch<M, N, P>(self &Serenity<M, N, P>, bork M)
        |    where func drop(M)void { }
        |
        |exported func main() {
        |  ship ISpaceship<int, bool, str> = Serenity<int, bool, str>();
        |  ship.launch(7);
        |}
        |""".stripMargin, false)
    compile.evalForKind(Vector())
  }

  test("Struct with different ordered runes") {
    // This is the Firefly case in ROWC.
    val compile = RunCompilation.test(
      """
        |import v.builtins.drop.*;
        |
        |sealed interface ISpaceship<E Ref, F Ref, G Ref> { }
        |abstract func launch<X, Y, Z>(virtual self &ISpaceship<X, Y, Z>, bork X)
        |    where func drop(X)void;
        |
        |struct Firefly<A Ref, B Ref, C Ref> { }
        |impl<H, I, J> ISpaceship<H, I, J> for Firefly<J, I, H>;
        |func launch<M, N, P>(self &Firefly<M, N, P>, bork P)
        |    where func drop(P)void { }
        |
        |exported func main() {
        |  ship ISpaceship<int, bool, str> = Firefly<str, bool, int>();
        |  ship.launch(7);
        |}
        |""".stripMargin, false)
    compile.evalForKind(Vector())
  }

  test("Struct with less generic params than interface") {
    // This is the Raza case in ROWC.
    val compile = RunCompilation.test(
      """
        |import v.builtins.drop.*;
        |
        |sealed interface ISpaceship<E Ref, F Ref, G Ref> { }
        |abstract func launch<X, Y, Z>(virtual self &ISpaceship<X, Y, Z>, bork X)
        |    where func drop(X)void;
        |
        |struct Raza<B Ref, C Ref> { }
        |impl<I, J> ISpaceship<int, I, J> for Raza<I, J>;
        |func launch<N, P>(self &Raza<N, P>, bork int) { }
        |
        |exported func main() {
        |  ship ISpaceship<int, bool, str> = Raza<bool, str>();
        |  ship.launch(7);
        |}
        |""".stripMargin, false)
    compile.evalForKind(Vector())
  }

  test("Struct with more generic params than interface") {
    // This is the Milano case in ROWC.
    val compile = RunCompilation.test(
      """
        |import v.builtins.drop.*;
        |
        |sealed interface ISpaceship<E Ref, F Ref, G Ref> { }
        |abstract func launch<X, Y, Z>(virtual self &ISpaceship<X, Y, Z>, bork X)
        |    where func drop(X)void;
        |
        |struct Milano<A Ref, B Ref, C Ref, D Ref> { }
        |impl<H, I, J, K> ISpaceship<H, I, J> for Milano<H, I, J, K>;
        |func launch<H, I, J, K>(self &Milano<H, I, J, K>, bork H) where func drop(H)void { }
        |
        |exported func main() {
        |  ship ISpaceship<int, bool, str> = Milano<int, bool, str, float>();
        |  ship.launch(7);
        |}
        |""".stripMargin, false)
    compile.evalForKind(Vector())
  }

  test("Struct repeating generic params for interface") {
    // This is the Enterprise case in ROWC.
    val compile = RunCompilation.test(
      """
        |import v.builtins.drop.*;
        |
        |sealed interface ISpaceship<E Ref, F Ref, G Ref> { }
        |abstract func launch<X, Y, Z>(virtual self &ISpaceship<X, Y, Z>, bork X)
        |    where func drop(X)void;
        |
        |struct Enterprise<A Ref> { }
        |impl<H> ISpaceship<H, H, H> for Enterprise<H>;
        |func launch<H>(self &Enterprise<H>, bork H) where func drop(H)void { }
        |
        |exported func main() {
        |  ship ISpaceship<int, int, int> = Enterprise<int>();
        |  ship.launch(7);
        |}
        |""".stripMargin, false)
    compile.evalForKind(Vector())
  }

  test("Imm interface") {
    val compile = RunCompilation.test(
      Tests.loadExpected("programs/virtuals/interfaceimm.vale"))
    compile.evalForKind(Vector()) match { case VonInt(42) => }
  }

  test("Can call interface env's function from outside") {
    val compile = RunCompilation.test(
      """
        |sealed interface I {
        |  func doThing(virtual i I) int;
        |}
        |func main(i I) int {
        |  return doThing(i);
        |}
      """.stripMargin, false)
    val coutputs = compile.expectCompilerOutputs()
    val interner = compile.interner

    vassert(coutputs.getAllUserFunctions.size == 1)
    vassert(coutputs.lookupFunction("main").header.returnType == CoordT(ShareT, RegionT(), IntT.i32))


    val doThing = coutputs.lookupFunction("doThing")
    vassert(doThing.header.params(0).virtuality.get == AbstractT())
  }


  test("Interface with method with param of substruct") {
    val compile = RunCompilation.test(
        """
          |struct List<T Ref> { }
          |
          |sealed interface SectionMember {}
          |struct Header {}
          |impl SectionMember for Header;
          |abstract func collectHeaders2(header &List<&Header>, virtual this &SectionMember);
          |func collectHeaders2(header &List<&Header>, this &Header) { }
        """.stripMargin)
    val coutputs = compile.getHamuts()
  }

  test("Open interface constructor") {
    val compile = RunCompilation.test(
      """
        |interface Bipedal {
        |  func hop(virtual s &Bipedal) int;
        |}
        |
        |func hopscotch(s &Bipedal) int {
        |  s.hop();
        |  return s.hop();
        |}
        |
        |exported func main() int {
        |   x = Bipedal({ 3 });
        |  // x is an unnamed substruct which implements Bipedal.
        |
        |  return hopscotch(&x);
        |}
        """.stripMargin, false)
    val coutputs = compile.getHamuts()
    compile.evalForKind(Vector()) match { case VonInt(3) => }
  }

  test("Open interface constructor, multiple methods") {
    val compile = RunCompilation.test(
        """
          |interface Bipedal {
          |  func hop(virtual s &Bipedal) int;
          |  func skip(virtual s &Bipedal) int;
          |}
          |
          |struct Human {  }
          |func hop(s &Human) int { return 7; }
          |func skip(s &Human) int { return 9; }
          |impl Bipedal for Human;
          |
          |func hopscotch(s &Bipedal) int {
          |  s.hop();
          |  s.skip();
          |  return s.hop();
          |}
          |
          |exported func main() int {
          |   x = Bipedal({ 3 }, { 5 });
          |  // x is an unnamed substruct which implements Bipedal.
          |
          |  return hopscotch(&x);
          |}
        """.stripMargin)
    val coutputs = compile.getHamuts()
    compile.evalForKind(Vector()) match { case VonInt(3) => }
  }

//  test("Successful borrow downcast with as") {
//    val compile = RunCompilation.test(
//      Tests.loadExpected("programs/downcast/downcastBorrowSuccessful.vale"))
//    compile.evalForKind(Vector()) match { case VonInt(42) => }
//  }
//
//  test("Failed borrow downcast with as") {
//    val compile = RunCompilation.test(
//      Tests.loadExpected("programs/downcast/downcastBorrowFailed.vale"))
//    compile.evalForKind(Vector()) match { case VonInt(42) => }
//  }

  test("Successful pointer downcast with as") {
    val compile = RunCompilation.test(
      Tests.loadExpected("programs/downcast/downcastPointerSuccess.vale"))
    compile.evalForKind(Vector()) match { case VonInt(42) => }
  }

  test("Failed pointer downcast with as") {
    val compile = RunCompilation.test(
      Tests.loadExpected("programs/downcast/downcastPointerFailed.vale"))

    {
      val moo = compile.expectCompilerOutputs().lookupFunction("moo")
      val (destVar, returnType) =
        Collector.only(moo, {
          case LetNormalTE(destVar, FunctionCallTE(PrototypeT(IdT(_, _, FunctionNameT(FunctionTemplateNameT(StrI("as"), _), _, _)), returnType), _, _)) => {
            (destVar, returnType)
          }
        })
      vassert(destVar.coord == returnType)
      val Vector(successType, failType) = returnType.kind.expectInterface().id.localName.templateArgs
      vassert(expectCoordTemplata(successType).coord.ownership == BorrowT)
      vassert(expectCoordTemplata(failType).coord.ownership == BorrowT)
    }

    {
      val moo = compile.getMonouts().lookupFunction("moo")
      val (destVar, returnType) =
        Collector.only(moo, {
          case LetNormalIE(destVar, FunctionCallIE(PrototypeI(IdI(_, _, FunctionNameIX(FunctionTemplateNameI(StrI("as"), _), _, _)), returnType), _, _), _) => {
            (destVar, returnType)
          }
        })
      vassert(destVar.collapsedCoord == returnType)
      val Vector(successType, failType) = returnType.kind.expectInterface().id.localName.templateArgs
      vassert(successType.expectCoordTemplata().coord.ownership == MutableBorrowI)
      vassert(failType.expectCoordTemplata().coord.ownership == MutableBorrowI)
    }

    compile.evalForKind(Vector()) match { case VonInt(42) => }
  }

  test("Successful owning downcast with as") {
    val compile = RunCompilation.test(
      Tests.loadExpected("programs/downcast/downcastOwningSuccessful.vale"))
    compile.evalForKind(Vector()) match { case VonInt(42) => }
  }

  test("Failed owning downcast with as") {
    val compile = RunCompilation.test(
      Tests.loadExpected("programs/downcast/downcastOwningFailed.vale"))
    compile.evalForKind(Vector()) match { case VonInt(42) => }
  }

  test("Lambda is compatible anonymous interface") {
    val compile = RunCompilation.test(
      """
        |import castutils.*;
        |
        |interface AFunction2<R Ref, P1 Ref, P2 Ref> {
        |  func __call(virtual this &AFunction2<R, P1, P2>, a P1, b P2) R;
        |}
        |exported func main() str {
        |  func = AFunction2<str, int, bool>((i, b) => { str(i) + str(b) });
        |  return func(42, true);
        |}
        |""".stripMargin)
    compile.evalForKind(Vector()) match { case VonStr("42true") => }
  }
}
