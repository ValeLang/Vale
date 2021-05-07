package net.verdagon.vale.templar

import net.verdagon.vale.parser.{CombinatorParsers, FileP, ParseErrorHumanizer, ParseFailure, ParseSuccess, Parser}
import net.verdagon.vale.scout.{CodeLocationS, CodeVarNameS, ProgramS, RangeS, Scout, VariableNameAlreadyExists}
import net.verdagon.vale.templar.env.ReferenceLocalVariable2
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.templar.types._
import net.verdagon.vale._
import net.verdagon.vale.astronomer.{Astronomer, FunctionNameA, GlobalFunctionFamilyNameA, IFunctionDeclarationNameA, ProgramA}
import net.verdagon.vale.hinputs.Hinputs
import net.verdagon.vale.templar.OverloadTemplar.{ScoutExpectedFunctionFailure, WrongNumberOfArguments}
import org.scalatest.{FunSuite, Matchers, _}

import scala.collection.immutable.List
import scala.io.Source

class PermissionTests extends FunSuite with Matchers {
  // TODO: pull all of the templar specific stuff out, the unit test-y stuff

  def readCodeFromResource(resourceFilename: String): String = {
    val is = Source.fromInputStream(getClass().getClassLoader().getResourceAsStream(resourceFilename))
    vassert(is != null)
    is.mkString("")
  }


  test("Templex readonly") {
    val compile = TemplarCompilation(
      """struct Bork {}
        |fn main(a &Bork) int {
        |  = 7;
        |}
        |""".stripMargin)
    val temputs = compile.getTemputs()

    val main = temputs.lookupFunction("main")
    main.only({
      case FunctionHeader2(simpleName("main"),List(UserFunction2),List(Parameter2(_, _, Coord(Borrow, Readonly, StructRef2(_)))), _, _) => true
    })
  }

  test("Templex readwrite") {
    val compile = TemplarCompilation(
      """struct Bork {}
        |fn main(a &!Bork) int {
        |  = 7;
        |}
        |""".stripMargin)
    val temputs = compile.getTemputs()

    val main = temputs.lookupFunction("main")
    main.only({
      case FunctionHeader2(simpleName("main"),List(UserFunction2),List(Parameter2(_, _, Coord(Borrow, Readwrite, StructRef2(_)))), _, _) => true
    })
  }

  test("Borrow readwrite member from a readonly container") {
    val compile = TemplarCompilation(
      """
        |struct Engine {}
        |struct Bork {
        |  engine Engine;
        |}
        |fn main(a &Bork) infer-ret {
        |  a.engine
        |}
        |""".stripMargin)
    val temputs = compile.getTemputs()

    val main = temputs.lookupFunction("main")
    main.header.returnType match {
      case Coord(Borrow, Readonly, _) =>
    }
  }

}

