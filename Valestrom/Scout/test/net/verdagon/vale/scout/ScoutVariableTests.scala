package net.verdagon.vale.scout

import net.verdagon.vale.parser.{CombinatorParsers, FinalP, ParseFailure, ParseSuccess, Parser, VaryingP}
import net.verdagon.vale.{vassert, vfail, vimpl}
import org.scalatest.{FunSuite, Matchers}

import scala.runtime.Nothing$

class ScoutVariableTests extends FunSuite with Matchers {
  private def compileProgramForError(code: String): IScoutError = {
    Parser.runParser(code) match {
      case ParseFailure(err) => fail(err.toString)
      case ParseSuccess(program0) => {
        Scout.scoutProgram(List(program0)) match {
          case null => vimpl() // TODO impl errors
        }
      }
    }
  }

  private def compile(code: String): ProgramS = {
    Parser.runParser(code) match {
      case ParseFailure(err) => fail(err.toString)
      case ParseSuccess(program0) => Scout.scoutProgram(List(program0))
    }
  }

  test("Regular variable") {
    val program1 = compile("fn main() { x = 4; }")
    val main = program1.lookupFunction("main")
    val CodeBody1(body) = main.body
    vassert(body.block.locals.size == 1)
    body.block.locals.head match {
      case LocalVariable1(
      CodeVarNameS("x"),
      FinalP, NotUsed, NotUsed, NotUsed, NotUsed, NotUsed, NotUsed) =>
    }
  }

  // Intentional failure 2020-08-13
  test("Reports defining same-name variable") {
    compileProgramForError("fn main() { x = 4; x = 5; }") match {
      case null =>
    }
  }

  test("MutableP variable") {
    val program1 = compile("fn main() { x! = 4; }")
    val main = program1.lookupFunction("main")
    val CodeBody1(body) = main.body
    body.block.locals.head match {
      case LocalVariable1(
      CodeVarNameS("x"),
      VaryingP, NotUsed, NotUsed, NotUsed, NotUsed, NotUsed, NotUsed) =>
    }
  }

  test("Self is lending to function") {
    val program1 = compile("fn main() { x = 4; doBlarks(&x); }")
    val main = program1.lookupFunction("main")
    val CodeBody1(body) = main.body
    body.block.locals.head match {
      case LocalVariable1(
      CodeVarNameS("x"),
      FinalP,Used, NotUsed, NotUsed, NotUsed, NotUsed, NotUsed) =>
    }
  }

  test("Self is lending to method") {
    val program1 = compile("fn main() { x = 4; x.doBlarks(); }")
    val main = program1.lookupFunction("main")
    val CodeBody1(body) = main.body
    body.block.locals.head match {
      case LocalVariable1(
      CodeVarNameS("x"),
      FinalP,Used, NotUsed, NotUsed, NotUsed, NotUsed, NotUsed) =>
    }
  }

  test("Self is moving to function") {
    val program1 = compile("fn main() { x = 4; doBlarks(x); }")
    val main = program1.lookupFunction("main")
    val CodeBody1(body) = main.body
    body.block.locals.head match {
      case LocalVariable1(
      CodeVarNameS("x"),
      FinalP,NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed) =>
    }
  }

  test("Self is moving to method") {
    val program1 = compile("fn main() { x = 4; x^.doBlarks(); }")
    val main = program1.lookupFunction("main")
    val CodeBody1(body) = main.body
    body.block.locals.head match {
      case LocalVariable1(
      CodeVarNameS("x"),
      FinalP,NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed) =>
    }
  }

  test("Self is mutating mutable") {
    val program1 = compile("fn main() { x! = 4; mut x = 6; }")
    val main = program1.lookupFunction("main")
    val CodeBody1(body) = main.body
    body.block.locals.head match {
      case LocalVariable1(
      CodeVarNameS("x"),
      VaryingP, NotUsed, NotUsed, Used, NotUsed, NotUsed, NotUsed) =>
    }
  }

  test("Self is moving and mutating same variable") {
    val program1 = compile("fn main() { x! = 4; mut x = x + 1; }")
    val main = program1.lookupFunction("main")
    val CodeBody1(body) = main.body
    body.block.locals.head match {
      case LocalVariable1(
      CodeVarNameS("x"),
      VaryingP, NotUsed, Used, Used, NotUsed, NotUsed, NotUsed) =>
    }
  }

  test("Child is lending") {
    val program1 = compile(
      """
        |fn main() {
        |  x = 4;
        |  { doBlarks(&x); }();
        |}
      """.stripMargin)
    val main = program1.lookupFunction("main")
    val CodeBody1(body) = main.body
    body.block.locals.head match {
      case LocalVariable1(
          CodeVarNameS("x"),
          FinalP, NotUsed, NotUsed, NotUsed, MaybeUsed, NotUsed, NotUsed) =>
    }
  }

  test("Child is moving") {
    val program1 = compile(
      """
        |fn main() {
        |  x = 4;
        |  { doBlarks(x); }();
        |}
      """.stripMargin)
    val main = program1.lookupFunction("main")
    val CodeBody1(body) = main.body
    body.block.locals.head match {
      case LocalVariable1(
          CodeVarNameS("x"),
          FinalP, NotUsed, NotUsed, NotUsed, NotUsed, MaybeUsed, NotUsed) =>
    }
  }

  test("Child is mutating") {
    val program1 = compile(
      """
        |fn main() {
        |  x = 4;
        |  { mut x = 9; }();
        |}
      """.stripMargin)
    val main = program1.lookupFunction("main")
    val CodeBody1(body) = main.body
    body.block.locals.head match {
      case LocalVariable1(
          CodeVarNameS("x"),
          FinalP, NotUsed, NotUsed, NotUsed, NotUsed, NotUsed, MaybeUsed) =>
    }
  }

  test("Self maybe lending") {
    val program1 = compile(
      """
        |fn main() {
        |  x = 4;
        |  if (true) { doBlarks(&x); } else { }
        |}
      """.stripMargin)
    val main = program1.lookupFunction("main")
    val CodeBody1(body) = main.body
    body.block.locals.head match {
      case LocalVariable1(
          CodeVarNameS("x"),
          FinalP, MaybeUsed, NotUsed, NotUsed, NotUsed, NotUsed, NotUsed) =>
    }
  }

  test("Self maybe moving") {
    val program1 = compile(
      """
        |fn main() {
        |  x = 4;
        |  if (true) { doBlarks(x); } else { }
        |}
      """.stripMargin)
    val main = program1.lookupFunction("main")
    val CodeBody1(body) = main.body
    body.block.locals.head match {
      case LocalVariable1(
          CodeVarNameS("x"),
          FinalP, NotUsed, MaybeUsed, NotUsed, NotUsed, NotUsed, NotUsed) =>
    }
  }

  test("Self maybe mutating") {
    val program1 = compile(
      """
        |fn main() {
        |  x = 4;
        |  if (true) { mut x = 9; } else { }
        |}
      """.stripMargin)
    val main = program1.lookupFunction("main")
    val CodeBody1(body) = main.body
    body.block.locals.head match {
      case LocalVariable1(
          CodeVarNameS("x"),
          FinalP, NotUsed, NotUsed, MaybeUsed, NotUsed, NotUsed, NotUsed) =>
    }
  }

  test("Children maybe lending") {
    val program1 = compile(
      """
        |fn main() {
        |  x = 4;
        |  if (true) { { doBlarks(&x); }(); } else { }
        |}
      """.stripMargin)
    val main = program1.lookupFunction("main")
    val CodeBody1(body) = main.body
    body.block.locals.head match {
      case LocalVariable1(
          CodeVarNameS("x"),
          FinalP,NotUsed, NotUsed, NotUsed, MaybeUsed, NotUsed, NotUsed) =>
    }
  }

  test("Children maybe moving") {
    val program1 = compile(
      """
        |fn main() {
        |  x = 4;
        |  if (true) { { doBlarks(x); }(); } else { }
        |}
      """.stripMargin)
    val main = program1.lookupFunction("main")
    val CodeBody1(body) = main.body
    body.block.locals.head match {
      case LocalVariable1(
          CodeVarNameS("x"),
          FinalP,NotUsed, NotUsed, NotUsed, NotUsed, MaybeUsed, NotUsed) =>
    }
  }

  test("Children maybe mutating") {
    val program1 = compile(
      """
        |fn main() {
        |  x = 4;
        |  if (true) { { mut x = 9; }(); } else { }
        |}
      """.stripMargin)
    val main = program1.lookupFunction("main")
    val CodeBody1(body) = main.body
    body.block.locals.head match {
      case LocalVariable1(
          CodeVarNameS("x"),
          FinalP,NotUsed, NotUsed, NotUsed, NotUsed, NotUsed, MaybeUsed) =>
    }
  }

  test("Self both lending") {
    val program1 = compile(
      """
        |fn main() {
        |  x = 4;
        |  if (true) { doBoinks(&x); } else { doBloops(&x); }
        |}
      """.stripMargin)
    val main = program1.lookupFunction("main")
    val CodeBody1(body) = main.body
    body.block.locals.head match {
      case LocalVariable1(
          CodeVarNameS("x"),
          FinalP, Used, NotUsed, NotUsed, NotUsed, NotUsed, NotUsed) =>
    }
  }

  test("Children both lending") {
    val program1 = compile(
      """
        |fn main() {
        |  x = 4;
        |  if (true) { { doBoinks(&x); }(); } else { { doBloops(&x); }(); }
        |}
      """.stripMargin)
    val main = program1.lookupFunction("main")
    val CodeBody1(body) = main.body
    body.block.locals.head match {
      case LocalVariable1(
          CodeVarNameS("x"),
          FinalP,NotUsed, NotUsed, NotUsed, MaybeUsed, NotUsed, NotUsed) =>
    }
  }

  test("Self both moving") {
    val program1 = compile(
      """
        |fn main() {
        |  x = 4;
        |  if (true) { doBoinks(x); } else { doBloops(x); }
        |}
      """.stripMargin)
    val main = program1.lookupFunction("main")
    val CodeBody1(body) = main.body
    body.block.locals.head match {
      case LocalVariable1(
          CodeVarNameS("x"),
          FinalP, NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed) =>
    }
  }

  test("Children both moving") {
    val program1 = compile(
      """
        |fn main() {
        |  x = 4;
        |  if (true) { { doBoinks(x); }(); } else { { doBloops(x); }(); }
        |}
      """.stripMargin)
    val main = program1.lookupFunction("main")
    val CodeBody1(body) = main.body
    body.block.locals.head match {
      case LocalVariable1(
          CodeVarNameS("x"),
          FinalP,NotUsed, NotUsed, NotUsed, NotUsed, MaybeUsed, NotUsed) =>
    }
  }

  test("Self both mutating") {
    val program1 = compile(
      """
        |fn main() {
        |  x = 4;
        |  if (true) { mut x = 9; } else { mut x = 8; }
        |}
      """.stripMargin)
    val main = program1.lookupFunction("main")
    val CodeBody1(body) = main.body
    body.block.locals.head match {
      case LocalVariable1(
          CodeVarNameS("x"),
          FinalP, NotUsed, NotUsed, Used, NotUsed, NotUsed, NotUsed) =>
    }
  }

  test("Children both mutating") {
    val program1 = compile(
      """
        |fn main() {
        |  x = 4;
        |  if (true) { { mut x = 9; }(); } else { { mut x = 8; }(); }
        |}
      """.stripMargin)
    val main = program1.lookupFunction("main")
    val CodeBody1(body) = main.body
    body.block.locals.head match {
      case LocalVariable1(
          CodeVarNameS("x"),
          FinalP, NotUsed, NotUsed, NotUsed, NotUsed, NotUsed, MaybeUsed) =>
    }
  }

  test("Self lending or moving") {
    val program1 = compile(
      """
        |fn main() {
        |  x = 4;
        |  if (true) { doThings(&x); } else { moveThis(x); }
        |}
      """.stripMargin)
    val main = program1.lookupFunction("main")
    val CodeBody1(body) = main.body
    body.block.locals.head match {
      case LocalVariable1(
          CodeVarNameS("x"),
          FinalP, MaybeUsed, MaybeUsed, NotUsed, NotUsed, NotUsed, NotUsed) =>
    }
  }

  test("Children lending or moving") {
    val program1 = compile(
      """
        |fn main() {
        |  x = 4;
        |  if (true) { { doThings(&x); }(); } else { { moveThis(x); }(); }
        |}
      """.stripMargin)
    val main = program1.lookupFunction("main")
    val CodeBody1(body) = main.body
    body.block.locals.head match {
      case LocalVariable1(
          CodeVarNameS("x"),
          FinalP,NotUsed, NotUsed, NotUsed, MaybeUsed, MaybeUsed, NotUsed) =>
    }
  }

  test("Self mutating or moving") {
    val program1 = compile(
      """
        |fn main() {
        |  x = 4;
        |  if (true) { mut x = 9; } else { moveThis(x); }
        |}
      """.stripMargin)
    val main = program1.lookupFunction("main")
    val CodeBody1(body) = main.body
    body.block.locals.head match {
      case LocalVariable1(
          CodeVarNameS("x"),
          FinalP, NotUsed, MaybeUsed, MaybeUsed, NotUsed, NotUsed, NotUsed) =>
    }
  }

  test("Children mutating or moving") {
    val program1 = compile(
      """
        |fn main() {
        |  x = 4;
        |  if (true) { { mut x = 9; }(); } else { { moveThis(x); }(); }
        |}
      """.stripMargin)
    val main = program1.lookupFunction("main")
    val CodeBody1(body) = main.body
    body.block.locals.head match {
      case LocalVariable1(
          CodeVarNameS("x"),
          FinalP, NotUsed, NotUsed, NotUsed, NotUsed, MaybeUsed, MaybeUsed) =>
    }
  }

  test("Self moving and mutating same variable") {
    val program1 = compile("fn main() { x! = 4; mut x = x + 1; }")
    val main = program1.lookupFunction("main")
    val CodeBody1(body) = main.body
    body.block.locals.head match {
      case LocalVariable1(
          CodeVarNameS("x"),
          VaryingP, NotUsed, Used, Used, NotUsed, NotUsed, NotUsed) =>
    }
  }

  test("Children moving and mutating same variable") {
    val program1 = compile("fn main() { x! = 4; { mut x = x + 1; }(); }")
    val main = program1.lookupFunction("main")
    val CodeBody1(body) = main.body
    body.block.locals.head match {
      case LocalVariable1(
          CodeVarNameS("x"),
          VaryingP, NotUsed, NotUsed, NotUsed, NotUsed, MaybeUsed, MaybeUsed) =>
    }
  }

  test("Self borrowing param") {
    val program1 = compile(
      """
        |fn main(x int) {
        |  print(&x);
        |}
      """.stripMargin)
    val main = program1.lookupFunction("main")
    val CodeBody1(body) = main.body
    body.block.locals.head match {
      case LocalVariable1(
          CodeVarNameS("x"),
          FinalP, Used, NotUsed, NotUsed, NotUsed, NotUsed, NotUsed) =>
    }
  }

  test("Children borrowing param") {
    val program1 = compile(
      """
        |fn main(x int) {
        |  { print(&x); }();
        |}
      """.stripMargin)
    val main = program1.lookupFunction("main")
    val CodeBody1(body) = main.body
    body.block.locals.head match {
      case LocalVariable1(
          CodeVarNameS("x"),
          FinalP,NotUsed, NotUsed, NotUsed, MaybeUsed, NotUsed, NotUsed) =>
    }
  }

  test("Self loading or mutating or moving") {
    val program1 = compile(
      """
        |fn main() {
        |  x = 4;
        |  if (true) { mut x = 9; } else if (true) { moveThis(x); } else { blark(&x); }
        |}
      """.stripMargin)
    val main = program1.lookupFunction("main")
    val CodeBody1(body) = main.body
    body.block.locals.head match {
      case LocalVariable1(
          CodeVarNameS("x"),
          FinalP, MaybeUsed, MaybeUsed, MaybeUsed, NotUsed, NotUsed, NotUsed) =>
    }
  }

  test("Children loading or mutating or moving") {
    val program1 = compile(
      """
        |fn main() {
        |  x = 4;
        |  if (true) { { mut x = 9; }(); } else if (true) { { moveThis(x); }(); } else { { blark(&x); }(); }
        |}
      """.stripMargin)
    val main = program1.lookupFunction("main")
    val CodeBody1(body) = main.body
    body.block.locals.head match {
      case LocalVariable1(
          CodeVarNameS("x"),
          FinalP, NotUsed, NotUsed, NotUsed, MaybeUsed, MaybeUsed, MaybeUsed) =>
    }
  }

  test("While condition borrowing") {
    val program1 = compile(
      """
        |fn main() {
        |  x = Marine();
        |  while (&x) { }
        |}
      """.stripMargin)
    val main = program1.lookupFunction("main")
    // x is always borrowed because the condition of a while is always run
    val CodeBody1(body) = main.body
    body.block.locals.head match {
      case LocalVariable1(
          CodeVarNameS("x"),
          FinalP, Used, NotUsed, NotUsed, NotUsed, NotUsed, NotUsed) =>
    }
  }

  test("While body maybe loading") {
    val program1 = compile(
      """
        |fn main() {
        |  x = Marine();
        |  while (true) { doThing(&x); }
        |}
      """.stripMargin)
    val main = program1.lookupFunction("main")
    val CodeBody1(body) = main.body
    body.block.locals.head match {
      case LocalVariable1(
          CodeVarNameS("x"),
          FinalP, MaybeUsed, NotUsed, NotUsed, NotUsed, NotUsed, NotUsed) =>
    }
  }

  test("Include closure var in locals") {
    val program1 = compile(
      """
        |fn main() {
        |  m = Marine();
        |  { m.shout() }();
        |}
      """.stripMargin)
    val scoutput = program1
    val main = scoutput.lookupFunction("main")
    val CodeBody1(BodySE(_, mainBlock)) = main.body
    // __Closure is shown as not used... we could change scout to automatically
    // borrow it whenever we try to access a closure variable?
    val lamBlock =
      mainBlock.exprs.collect({
        case FunctionCallSE(_, FunctionSE(FunctionS(_, _, _, _, _, _, _, _, _, _, _, CodeBody1(innerBody))), _) => innerBody.block
      }).head
    lamBlock.locals.head match {
      case LocalVariable1(name, FinalP,NotUsed, NotUsed, NotUsed, NotUsed, NotUsed, NotUsed) => {
        name match {
          case ClosureParamNameS() =>
        }
      }
    }
  }

  test("Include _ in locals") {
    val program1 = compile(
      """
        |fn main() {
        |  { print(_) }(3);
        |}
      """.stripMargin)
    val scoutput = program1
    val main = scoutput.lookupFunction("main")
    val CodeBody1(BodySE(_, mainBlock)) = main.body
    // __Closure is shown as not used... we could change scout to automatically
    // borrow it whenever we try to access a closure variable?
    val lamBlock =
    mainBlock.exprs.collect({
      case FunctionCallSE(_, FunctionSE(FunctionS(_, _, _, _, _, _, _, _, _, _, _, CodeBody1(innerBody))), _) => innerBody.block
    }).head
    val locals = lamBlock.locals
    locals.find(_.varName == ClosureParamNameS()).get match {
      case LocalVariable1(ClosureParamNameS(),
        FinalP,NotUsed, NotUsed, NotUsed, NotUsed, NotUsed, NotUsed) =>
    }
  }
}
