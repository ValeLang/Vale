package net.verdagon.vale.templar

import net.verdagon.vale._
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.templar.types._
import org.scalatest.{FunSuite, Matchers}

import scala.collection.immutable.List
import scala.io.Source

class TemplarOwnershipTests extends FunSuite with Matchers {
  // TODO: pull all of the templar specific stuff out, the unit test-y stuff

  def readCodeFromResource(resourceFilename: String): String = {
    val is = Source.fromInputStream(getClass().getClassLoader().getResourceAsStream(resourceFilename))
    vassert(is != null)
    is.mkString("")
  }


  test("Parenthesized method syntax will move instead of borrow") {
    val compile = TemplarTestCompilation.test(
      """
        |struct Bork { a int; }
        |fn consumeBork(bork Bork) int {
        |  ret bork.a;
        |}
        |fn main() int {
        |  bork = Bork(42);
        |  ret (bork).consumeBork();
        |}
        |""".stripMargin)
    val temputs = compile.expectTemputs()
  }

}

