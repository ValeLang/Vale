package net.verdagon.vale

import net.verdagon.vale.templar.ast.{AbstractT, SignatureT}
import net.verdagon.vale.templar.names.{CitizenNameT, CitizenTemplateNameT, FullNameT, FunctionNameT}
import net.verdagon.vale.templar.types._
import org.scalatest.{FunSuite, Matchers}
import net.verdagon.vale.vivem.IntV
import net.verdagon.von.{VonInt, VonStr}

class VirtualTests extends FunSuite with Matchers {

    test("Simple program containing a virtual function") {
      val compile = RunCompilation.test(
        """
          |interface I sealed {}
          |fn doThing(virtual i I) int { ret 4; }
          |fn main(i I) int {
          |  ret doThing(i);
          |}
        """.stripMargin)
      val temputs = compile.expectTemputs()

      vassert(temputs.getAllUserFunctions.size == 2)
      vassert(temputs.lookupFunction("main").header.returnType == CoordT(ShareT, ReadonlyT, IntT.i32))

      val doThing =
        vassertSome(
          temputs.lookupFunction(
            SignatureT(
              FullNameT(
                PackageCoordinate.TEST_TLD,
                Vector.empty,
                FunctionNameT(
                  "doThing",
                  Vector.empty,
                  Vector(
                    CoordT(
                      OwnT,
                      ReadwriteT,
                      InterfaceTT(
                        FullNameT(PackageCoordinate.TEST_TLD, Vector.empty, CitizenNameT(CitizenTemplateNameT("I"), Vector.empty))))))))))
      vassert(doThing.header.params(0).virtuality.get == AbstractT)
    }

  test("Can call virtual function") {
    val compile = RunCompilation.test(
      """
        |interface I sealed {}
        |fn doThing(virtual i I) int { ret 4; }
        |fn main(i I) int {
        |  ret doThing(i);
        |}
      """.stripMargin)
    val temputs = compile.expectTemputs()

    vassert(temputs.getAllUserFunctions.size == 2)
    vassert(temputs.lookupFunction("main").header.returnType == CoordT(ShareT, ReadonlyT, IntT.i32))


    val doThing =
      vassertSome(
        temputs.lookupFunction(
          SignatureT(
            FullNameT(
              PackageCoordinate.TEST_TLD,
              Vector.empty,
              FunctionNameT(
                "doThing",
                Vector.empty,
                Vector(
                  CoordT(
                    OwnT,
                    ReadwriteT,
                    InterfaceTT(
                      FullNameT(PackageCoordinate.TEST_TLD, Vector.empty, CitizenNameT(CitizenTemplateNameT("I"), Vector.empty))))))))))
    vassert(doThing.header.params(0).virtuality.get == AbstractT)
  }

  test("Imm interface") {
    val compile = RunCompilation.test(
      Tests.loadExpected("programs/virtuals/interfaceimm.vale"))
    compile.evalForKind(Vector()) shouldEqual VonInt(42)
  }

  test("Can call interface env's function from outside") {
    val compile = RunCompilation.test(
      """
        |interface I {
        |  fn doThing(virtual i I) int;
        |}
        |fn main(i I) int {
        |  ret doThing(i);
        |}
      """.stripMargin)
    val temputs = compile.expectTemputs()

    vassert(temputs.getAllUserFunctions.size == 1)
    vassert(temputs.lookupFunction("main").header.returnType == CoordT(ShareT, ReadonlyT, IntT.i32))


    val doThing =
      vassertSome(
        temputs.lookupFunction(
          SignatureT(
            FullNameT(PackageCoordinate.TEST_TLD, Vector(CitizenNameT(CitizenTemplateNameT("I"),Vector.empty)),FunctionNameT("doThing",Vector.empty,Vector(CoordT(OwnT,ReadwriteT,InterfaceTT(FullNameT(PackageCoordinate.TEST_TLD, Vector.empty,CitizenNameT(CitizenTemplateNameT("I"),Vector.empty))))))))))
    vassert(doThing.header.params(0).virtuality.get == AbstractT)
  }


  test("Interface with method with param of substruct") {
    val compile = RunCompilation.test(
        """
          |import list.*;
          |interface SectionMember {}
          |struct Header {}
          |impl SectionMember for Header;
          |fn collectHeaders2(header *List<*Header>, virtual this *SectionMember) abstract;
          |fn collectHeaders2(header *List<*Header>, this *Header impl SectionMember) { }
        """.stripMargin)
    val temputs = compile.getHamuts()
  }

  test("Open interface constructors") {
    val compile = RunCompilation.test(
        """
          |interface Bipedal {
          |  fn hop(virtual s *Bipedal) int;
          |  fn skip(virtual s *Bipedal) int;
          |}
          |
          |struct Human {  }
          |fn hop(s *Human impl Bipedal) int { ret 7; }
          |fn skip(s *Human impl Bipedal) int { ret 9; }
          |impl Bipedal for Human;
          |
          |fn hopscotch(s *Bipedal) int {
          |  s.hop();
          |  s.skip();
          |  ret s.hop();
          |}
          |
          |fn main() export int {
          |   x = Bipedal({ 3 }, { 5 });
          |  // x is an unnamed substruct which implements Bipedal.
          |
          |  ret hopscotch(*x);
          |}
        """.stripMargin)
    val temputs = compile.getHamuts()
    compile.evalForKind(Vector()) shouldEqual VonInt(3)
  }

//  test("Successful borrow downcast with as") {
//    val compile = RunCompilation.test(
//      Tests.loadExpected("programs/downcast/downcastBorrowSuccessful.vale"))
//    compile.evalForKind(Vector()) shouldEqual VonInt(42)
//  }
//
//  test("Failed borrow downcast with as") {
//    val compile = RunCompilation.test(
//      Tests.loadExpected("programs/downcast/downcastBorrowFailed.vale"))
//    compile.evalForKind(Vector()) shouldEqual VonInt(42)
//  }

  test("Successful pointer downcast with as") {
    val compile = RunCompilation.test(
      Tests.loadExpected("programs/downcast/downcastPointerSuccess.vale"))
    compile.evalForKind(Vector()) shouldEqual VonInt(42)
  }

  test("Failed pointer downcast with as") {
    val compile = RunCompilation.test(
      Tests.loadExpected("programs/downcast/downcastPointerFailed.vale"))
    compile.evalForKind(Vector()) shouldEqual VonInt(42)
  }

  test("Successful owning downcast with as") {
    val compile = RunCompilation.test(
      Tests.loadExpected("programs/downcast/downcastOwningSuccessful.vale"))
    compile.evalForKind(Vector()) shouldEqual VonInt(42)
  }

  test("Failed owning downcast with as") {
    val compile = RunCompilation.test(
      Tests.loadExpected("programs/downcast/downcastOwningFailed.vale"))
    compile.evalForKind(Vector()) shouldEqual VonInt(42)
  }

  test("Lambda is compatible anonymous interface") {
    val compile = RunCompilation.test(
      """
        |import castutils.*;
        |interface AFunction2<R, P1, P2> rules(R Ref, P1 Ref, P2 Ref) {
        |  fn __call(virtual this &AFunction2<R, P1, P2>, a P1, b P2) R;
        |}
        |fn main() str export {
        |  func = AFunction2<str, int, bool>((i, b){ str(i) + str(b) });
        |  ret func(42, true);
        |}
        |""".stripMargin)
    compile.evalForKind(Vector()) shouldEqual VonStr("42true")
  }
}
