package dev.vale

import dev.vale.typing.{ast, types}
import dev.vale.typing.ast.{AbstractT, SignatureT}
import dev.vale.typing.names.{CitizenNameT, CitizenTemplateNameT, FullNameT, FunctionNameT}
import dev.vale.typing.types.{CoordT, IntT, InterfaceTT, OwnT, ShareT}
import dev.vale.testvm.IntV
import dev.vale.typing.ast._
import dev.vale.typing.names.CitizenTemplateNameT
import dev.vale.typing.types._
import dev.vale.von.{VonInt, VonStr}
import org.scalatest.{FunSuite, Matchers}

class VirtualTests extends FunSuite with Matchers {

    test("Simple program containing a virtual function") {
      val compile = RunCompilation.test(
        """
          |sealed interface I  {}
          |func doThing(virtual i I) int { return 4; }
          |func main(i I) int {
          |  return doThing(i);
          |}
        """.stripMargin)
      val coutputs = compile.expectCompilerOutputs()
      val interner = compile.interner
      val keywords = compile.keywords

      vassert(coutputs.getAllUserFunctions.size == 2)
      vassert(coutputs.lookupFunction("main").header.returnType == CoordT(ShareT, IntT.i32))

      val doThing =
        vassertSome(
          coutputs.lookupFunction(
            SignatureT(
              FullNameT(
                PackageCoordinate.TEST_TLD(interner, keywords),
                Vector.empty,
                interner.intern(
                  FunctionNameT(
                    interner.intern(StrI("doThing")),
                    Vector.empty,
                    Vector(
                      CoordT(
                        OwnT,
                        interner.intern(
                          InterfaceTT(
                            FullNameT(PackageCoordinate.TEST_TLD(interner, keywords), Vector.empty, interner.intern(CitizenNameT(interner.intern(CitizenTemplateNameT(interner.intern(StrI("I")))), Vector.empty)))))))))))))
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
      """.stripMargin)
    val coutputs = compile.expectCompilerOutputs()
    val interner = compile.interner
    val keywords = compile.keywords

    vassert(coutputs.getAllUserFunctions.size == 2)
    vassert(coutputs.lookupFunction("main").header.returnType == CoordT(ShareT, IntT.i32))


    val doThing =
      vassertSome(
        coutputs.lookupFunction(
          ast.SignatureT(
            FullNameT(
              PackageCoordinate.TEST_TLD(interner, keywords),
              Vector.empty,
              interner.intern(
                FunctionNameT(
                  interner.intern(StrI("doThing")),
                  Vector.empty,
                  Vector(
                    CoordT(
                      OwnT,
                      interner.intern(
                        types.InterfaceTT(
                          FullNameT(PackageCoordinate.TEST_TLD(interner, keywords), Vector.empty, interner.intern(CitizenNameT(interner.intern(CitizenTemplateNameT(interner.intern(StrI("I")))), Vector.empty)))))))))))))
    vassert(doThing.header.params(0).virtuality.get == AbstractT())
  }

  test("Imm interface") {
    val compile = RunCompilation.test(
      Tests.loadExpected("programs/virtuals/interfaceimm.vale"))
    compile.evalForKind(Vector()) match { case VonInt(42) => }
  }

  test("Can call interface env's function from outside") {
    val compile = RunCompilation.test(
      """
        |interface I {
        |  func doThing(virtual i I) int;
        |}
        |func main(i I) int {
        |  return doThing(i);
        |}
      """.stripMargin)
    val coutputs = compile.expectCompilerOutputs()
    val interner = compile.interner

    vassert(coutputs.getAllUserFunctions.size == 1)
    vassert(coutputs.lookupFunction("main").header.returnType == CoordT(ShareT, IntT.i32))


    val doThing = coutputs.lookupFunction("doThing")
    vassert(doThing.header.params(0).virtuality.get == AbstractT())
  }


  test("Interface with method with param of substruct") {
    val compile = RunCompilation.test(
        """
          |import list.*;
          |interface SectionMember {}
          |struct Header {}
          |impl SectionMember for Header;
          |abstract func collectHeaders2(header &List<&Header>, virtual this &SectionMember);
          |func collectHeaders2(header &List<&Header>, this &Header) { }
        """.stripMargin)
    val coutputs = compile.getHamuts()
  }

  test("Open interface constructors") {
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
        |interface AFunction2<R, P1, P2> where R Ref, P1 Ref, P2 Ref {
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
