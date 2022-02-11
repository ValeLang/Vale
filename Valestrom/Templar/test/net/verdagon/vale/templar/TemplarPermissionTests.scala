package net.verdagon.vale.templar

import net.verdagon.vale.templar.templata._
import net.verdagon.vale.templar.types._
import net.verdagon.vale._
import net.verdagon.vale.templar.OverloadTemplar.{FindFunctionFailure, WrongNumberOfArguments}
import net.verdagon.vale.templar.ast.{FunctionHeaderT, ParameterT, UserFunctionT}
import org.scalatest.{FunSuite, Matchers, _}

import scala.collection.immutable.List
import scala.io.Source

class TemplarPermissionTests extends FunSuite with Matchers {
  // TODO: pull all of the templar specific stuff out, the unit test-y stuff

  def readCodeFromResource(resourceFilename: String): String = {
    val is = Source.fromInputStream(getClass().getClassLoader().getResourceAsStream(resourceFilename))
    vassert(is != null)
    is.mkString("")
  }



  test("Templex readonly") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |struct Bork {}
        |func main(a *Bork) int {
        |  ret 7;
        |}
        |""".stripMargin)
    val temputs = compile.expectTemputs()

    val main = temputs.lookupFunction("main")
    Collector.only(main, {
      case FunctionHeaderT(simpleName("main"),Vector(UserFunctionT),Vector(ParameterT(_, _, CoordT(PointerT, ReadonlyT, StructTT(_)))), _, _) => true
    })
  }

  test("Templex readwrite") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |struct Bork {}
        |func main(a *!Bork) int {
        |  ret 7;
        |}
        |""".stripMargin)
    val temputs = compile.expectTemputs()

    val main = temputs.lookupFunction("main")
    Collector.only(main, {
      case FunctionHeaderT(simpleName("main"),Vector(UserFunctionT),Vector(ParameterT(_, _, CoordT(PointerT, ReadwriteT, StructTT(_)))), _, _) => true
    })
  }

  test("Borrow readwrite member from a readonly container") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |struct Engine {}
        |struct Bork {
        |  engine Engine;
        |}
        |func main(a *Bork) infer-ret {
        |  ret *a.engine;
        |}
        |""".stripMargin)
    val temputs = compile.expectTemputs()

    val main = temputs.lookupFunction("main")
    main.header.returnType match {
      case CoordT(PointerT, ReadonlyT, _) =>
    }
  }

  test("Borrow-method-call on readwrite member") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |struct Engine { }
        |struct Bork {
        |  engine Engine;
        |}
        |func getFuel(engine &Engine) int { ret 42; }
        |func main() infer-ret {
        |  bork = Bork(Engine());
        |  ret bork.engine.getFuel();
        |}
        |""".stripMargin)
    val temputs = compile.expectTemputs()
  }

}

