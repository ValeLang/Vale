package net.verdagon.vale

import net.verdagon.vale.templar.{CitizenName2, FullName2, FunctionName2, simpleName}
import net.verdagon.vale.templar.templata.{Abstract2, Signature2}
import net.verdagon.vale.templar.types._
import org.scalatest.{FunSuite, Matchers}
import net.verdagon.vale.driver.Compilation

class VirtualTests extends FunSuite with Matchers {

    test("Simple program containing a virtual function") {
      val compile = Compilation(
        """
          |interface I {}
          |fn doThing(virtual i I) int {4}
          |fn main(i I) int {
          |  doThing(^i)
          |}
        """.stripMargin)
      val temputs = compile.getTemputs()

      vassert(temputs.getAllUserFunctions.size == 2)
      vassert(temputs.lookupFunction("main").header.returnType == Coord(Share, Int2()))

      val doThing =
        vassertSome(
          temputs.lookupFunction(
            Signature2(
              FullName2(
                List(),
                FunctionName2(
                  "doThing",
                  List(),
                  List(
                    Coord(
                      Own,
                      InterfaceRef2(
                        FullName2(List(), CitizenName2("I", List()))))))))))
      vassert(doThing.header.params(0).virtuality.get == Abstract2)
    }

  test("Can call virtual function") {
    val compile = Compilation(
      """
        |interface I {}
        |fn doThing(virtual i I) int {4}
        |fn main(i I) int {
        |  doThing(^i)
        |}
      """.stripMargin)
    val temputs = compile.getTemputs()

    vassert(temputs.getAllUserFunctions.size == 2)
    vassert(temputs.lookupFunction("main").header.returnType == Coord(Share, Int2()))


    val doThing =
      vassertSome(
        temputs.lookupFunction(
          Signature2(
            FullName2(
              List(),
              FunctionName2(
                "doThing",
                List(),
                List(
                  Coord(
                    Own,
                    InterfaceRef2(
                      FullName2(List(), CitizenName2("I", List()))))))))))
    vassert(doThing.header.params(0).virtuality.get == Abstract2)
  }

  test("Can call interface env's function from outside") {
    val compile = Compilation(
      """
        |interface I {
        |  fn doThing(virtual i I) int;
        |}
        |fn main(i I) int {
        |  doThing(^i)
        |}
      """.stripMargin)
    val temputs = compile.getTemputs()

    vassert(temputs.getAllUserFunctions.size == 1)
    vassert(temputs.lookupFunction("main").header.returnType == Coord(Share, Int2()))


    val doThing =
      vassertSome(
        temputs.lookupFunction(
          Signature2(
            FullName2(List(CitizenName2("I",List())),FunctionName2("doThing",List(),List(Coord(Own,InterfaceRef2(FullName2(List(),CitizenName2("I",List()))))))))))
    vassert(doThing.header.params(0).virtuality.get == Abstract2)
  }


  test("Interface with method with param of substruct") {
    val compile = Compilation.multiple(
      List(
        Samples.get("libraries/opt.vale"),
        Samples.get("libraries/list.vale"),
        """
          |interface SectionMember {}
          |struct Header {}
          |impl SectionMember for Header;
          |fn collectHeaders2(header &List<&Header>, virtual this &SectionMember) abstract;
          |fn collectHeaders2(header &List<&Header>, this &Header impl SectionMember) { }
        """.stripMargin))
    val temputs = compile.getHamuts()
  }





}
