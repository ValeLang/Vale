package dev.vale.scout

import dev.vale.{Collector, Err, FileCoordinateMap, Ok, vassert, vfail}
import dev.vale.options.GlobalOptions
import dev.vale.parser.Parser
import dev.vale.Err
import org.scalatest.{FunSuite, Matchers}

import scala.runtime.Nothing$

class ScoutVariableTests extends FunSuite with Matchers {

  private def compileForError(code: String): ICompileErrorS = {
    ScoutTestCompilation.test(code).getScoutput() match {
      case Err(e) => e
      case Ok(t) => vfail("Successfully compiled!\n" + t.toString)
    }
  }

  private def compile(code: String): ProgramS = {
    ScoutTestCompilation.test(code).getScoutput() match {
      case Err(e) => vfail(ScoutErrorHumanizer.humanize(FileCoordinateMap.test(code), e))
      case Ok(t) => t.expectOne()
    }
  }

  test("Regular variable") {
    val program1 = compile("exported func main() int { x = 4; }")
    val main = program1.lookupFunction("main")
    val CodeBodyS(body) = main.body
    vassert(body.block.locals.size == 1)
    body.block.locals.head match {
      case LocalS(
      CodeVarNameS("x"),
      NotUsed, NotUsed, NotUsed, NotUsed, NotUsed, NotUsed) =>
    }
  }

  test("Type-less local has no coord rune") {
    val program1 = compile("exported func main() int { x = 4; }")
    val main = program1.lookupFunction("main")
    val local = Collector.only(main, { case let @ LetSE(_, rules, pattern, _) => let })
    local.pattern.coordRune shouldEqual None
  }

  test("Reports defining same-name variable") {
    compileForError("exported func main() { x = 4; x = 5; }") match {
      case VariableNameAlreadyExists(_, CodeVarNameS("x")) =>
    }
  }

  test("Self is pointing to function") {
    val program1 = compile("exported func main() int { x = 4; doBlarks(&x); }")
    val main = program1.lookupFunction("main")
    val CodeBodyS(body) = main.body
    body.block.locals.head match {
      case LocalS(
      CodeVarNameS("x"),
      Used, NotUsed, NotUsed, NotUsed, NotUsed, NotUsed) =>
    }
  }

  test("Self is pointing to method") {
    val program1 = compile("exported func main() int { x = 4; x.doBlarks(); }")
    val main = program1.lookupFunction("main")
    val CodeBodyS(body) = main.body
    body.block.locals.head match {
      case LocalS(
      CodeVarNameS("x"),
      Used, NotUsed, NotUsed, NotUsed, NotUsed, NotUsed) =>
    }
  }

  test("Self is moving to function") {
    val program1 = compile("exported func main() int { x = 4; doBlarks(x); }")
    val main = program1.lookupFunction("main")
    val CodeBodyS(body) = main.body
    body.block.locals.head match {
      case LocalS(
      CodeVarNameS("x"),
      NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed) =>
    }
  }

  test("Self is moving to method") {
    val program1 = compile("exported func main() int { x = 4; (x).doBlarks(); }")
    val main = program1.lookupFunction("main")
    val CodeBodyS(body) = main.body
    body.block.locals.head match {
      case LocalS(
      CodeVarNameS("x"),
      NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed) =>
    }
  }

  test("Self is mutating mutable") {
    val program1 = compile("exported func main() int { x = 4; set x = 6; }")
    val main = program1.lookupFunction("main")
    val CodeBodyS(body) = main.body
    body.block.locals.head match {
      case LocalS(
      CodeVarNameS("x"),
       NotUsed, NotUsed, Used, NotUsed, NotUsed, NotUsed) =>
    }
  }

  test("Self is moving and mutating same variable") {
    val program1 = compile("exported func main() int { x = 4; set x = +(x, 1); }")
    val main = program1.lookupFunction("main")
    val CodeBodyS(body) = main.body
    body.block.locals.head match {
      case LocalS(
      CodeVarNameS("x"),
       NotUsed, Used, Used, NotUsed, NotUsed, NotUsed) =>
    }
  }

  test("Child is pointing") {
    val program1 = compile(
      """
        |exported func main() int {
        |  x = 4;
        |  ({ doBlarks(&x); })();
        |}
      """.stripMargin)
    val main = program1.lookupFunction("main")
    val CodeBodyS(body) = main.body
    body.block.locals.head match {
      case LocalS(
          CodeVarNameS("x"),
           NotUsed, NotUsed, NotUsed, Used, NotUsed, NotUsed) =>
    }
  }

  test("Child is moving") {
    val program1 = compile(
      """
        |exported func main() int {
        |  x = 4;
        |  ({ doBlarks(x); })();
        |}
      """.stripMargin)
    val main = program1.lookupFunction("main")
    val CodeBodyS(body) = main.body
    body.block.locals.head match {
      case LocalS(
          CodeVarNameS("x"),
           NotUsed, NotUsed, NotUsed, NotUsed, Used, NotUsed) =>
    }
  }

  test("Child is mutating") {
    val program1 = compile(
      """
        |exported func main() int {
        |  x = 4;
        |  ({ set x = 9; })();
        |}
      """.stripMargin)
    val main = program1.lookupFunction("main")
    val CodeBodyS(body) = main.body
    body.block.locals.head match {
      case LocalS(
          CodeVarNameS("x"),
           NotUsed, NotUsed, NotUsed, NotUsed, NotUsed, Used) =>
    }
  }

  test("Self maybe pointing") {
    val program1 = compile(
      """
        |exported func main() int {
        |  x = 4;
        |  if (true) { doBlarks(&x); } else { }
        |}
      """.stripMargin)
    val main = program1.lookupFunction("main")
    val CodeBodyS(body) = main.body
    body.block.locals.head match {
      case LocalS(CodeVarNameS("x"), Used, NotUsed, NotUsed, NotUsed, NotUsed, NotUsed) =>
    }
  }

  test("Self maybe moving") {
    val program1 = compile(
      """
        |exported func main() int {
        |  x = 4;
        |  if (true) { doBlarks(x); } else { }
        |}
      """.stripMargin)
    val main = program1.lookupFunction("main")
    val CodeBodyS(body) = main.body
    body.block.locals.head match {
      case LocalS(
          CodeVarNameS("x"),
           NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed) =>
    }
  }

  test("Self maybe mutating") {
    val program1 = compile(
      """
        |exported func main() int {
        |  x = 4;
        |  if (true) { set x = 9; } else { }
        |}
      """.stripMargin)
    val main = program1.lookupFunction("main")
    val CodeBodyS(body) = main.body
    body.block.locals.head match {
      case LocalS(
          CodeVarNameS("x"),
           NotUsed, NotUsed, Used, NotUsed, NotUsed, NotUsed) =>
    }
  }

  test("Children maybe pointing") {
    val program1 = compile(
      """
        |exported func main() int {
        |  x = 4;
        |  if (true) { { doBlarks(&x); }(); } else { }
        |}
      """.stripMargin)
    val main = program1.lookupFunction("main")
    val CodeBodyS(body) = main.body
    body.block.locals.head match {
      case LocalS(
          CodeVarNameS("x"),
          NotUsed, NotUsed, NotUsed, Used, NotUsed, NotUsed) =>
    }
  }

  test("Children maybe moving") {
    val program1 = compile(
      """
        |exported func main() int {
        |  x = 4;
        |  if (true) { { doBlarks(x); }(); } else { }
        |}
      """.stripMargin)
    val main = program1.lookupFunction("main")
    val CodeBodyS(body) = main.body
    body.block.locals.head match {
      case LocalS(
          CodeVarNameS("x"),
          NotUsed, NotUsed, NotUsed, NotUsed, Used, NotUsed) =>
    }
  }

  test("Children maybe mutating") {
    val program1 = compile(
      """
        |exported func main() int {
        |  x = 4;
        |  if (true) { { set x = 9; }(); } else { }
        |}
      """.stripMargin)
    val main = program1.lookupFunction("main")
    val CodeBodyS(body) = main.body
    body.block.locals.head match {
      case LocalS(
          CodeVarNameS("x"),
          NotUsed, NotUsed, NotUsed, NotUsed, NotUsed, Used) =>
    }
  }

  test("Self both pointing") {
    val program1 = compile(
      """
        |exported func main() int {
        |  x = 4;
        |  if (true) { doBoinks(&x); } else { doBloops(&x); }
        |}
      """.stripMargin)
    val main = program1.lookupFunction("main")
    val CodeBodyS(body) = main.body
    body.block.locals.head match {
      case LocalS(
          CodeVarNameS("x"),
           Used, NotUsed, NotUsed, NotUsed, NotUsed, NotUsed) =>
    }
  }

  test("Children both pointing") {
    val program1 = compile(
      """
        |exported func main() int {
        |  x = 4;
        |  if (true) { { doBoinks(&x); }(); } else { { doBloops(&x); }(); }
        |}
      """.stripMargin)
    val main = program1.lookupFunction("main")
    val CodeBodyS(body) = main.body
    body.block.locals.head match {
      case LocalS(
          CodeVarNameS("x"),
          NotUsed, NotUsed, NotUsed, Used, NotUsed, NotUsed) =>
    }
  }

  test("Self both moving") {
    val program1 = compile(
      """
        |exported func main() int {
        |  x = 4;
        |  if (true) { doBoinks(x); } else { doBloops(x); }
        |}
      """.stripMargin)
    val main = program1.lookupFunction("main")
    val CodeBodyS(body) = main.body
    body.block.locals.head match {
      case LocalS(
          CodeVarNameS("x"),
           NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed) =>
    }
  }

  test("Children both moving") {
    val program1 = compile(
      """
        |exported func main() int {
        |  x = 4;
        |  if (true) { { doBoinks(x); }(); } else { { doBloops(x); }(); }
        |}
      """.stripMargin)
    val main = program1.lookupFunction("main")
    val CodeBodyS(body) = main.body
    body.block.locals.head match {
      case LocalS(
          CodeVarNameS("x"),
          NotUsed, NotUsed, NotUsed, NotUsed, Used, NotUsed) =>
    }
  }

  test("Self both mutating") {
    val program1 = compile(
      """
        |exported func main() int {
        |  x = 4;
        |  if (true) { set x = 9; } else { set x = 8; }
        |}
      """.stripMargin)
    val main = program1.lookupFunction("main")
    val CodeBodyS(body) = main.body
    body.block.locals.head match {
      case LocalS(
          CodeVarNameS("x"),
           NotUsed, NotUsed, Used, NotUsed, NotUsed, NotUsed) =>
    }
  }

  test("Children both mutating") {
    val program1 = compile(
      """
        |exported func main() int {
        |  x = 4;
        |  if (true) { { set x = 9; }(); } else { { set x = 8; }(); }
        |}
      """.stripMargin)
    val main = program1.lookupFunction("main")
    val CodeBodyS(body) = main.body
    body.block.locals.head match {
      case LocalS(
          CodeVarNameS("x"),
           NotUsed, NotUsed, NotUsed, NotUsed, NotUsed, Used) =>
    }
  }

  test("Self pointing or moving") {
    val program1 = compile(
      """
        |exported func main() int {
        |  x = 4;
        |  if (true) { doThings(&x); } else { moveThis(x); }
        |}
      """.stripMargin)
    val main = program1.lookupFunction("main")
    val CodeBodyS(body) = main.body
    body.block.locals.head match {
      case LocalS(
          CodeVarNameS("x"),
           Used, Used, NotUsed, NotUsed, NotUsed, NotUsed) =>
    }
  }

  test("Children pointing or moving") {
    val program1 = compile(
      """
        |exported func main() int {
        |  x = 4;
        |  if (true) { { doThings(&x); }(); } else { { moveThis(x); }(); }
        |}
      """.stripMargin)
    val main = program1.lookupFunction("main")
    val CodeBodyS(body) = main.body
    body.block.locals.head match {
      case LocalS(
          CodeVarNameS("x"),
          NotUsed, NotUsed, NotUsed, Used, Used, NotUsed) =>
    }
  }

  test("Self mutating or moving") {
    val program1 = compile(
      """
        |exported func main() int {
        |  x = 4;
        |  if (true) { set x = 9; } else { moveThis(x); }
        |}
      """.stripMargin)
    val main = program1.lookupFunction("main")
    val CodeBodyS(body) = main.body
    body.block.locals.head match {
      case LocalS(
          CodeVarNameS("x"),
           NotUsed, Used, Used, NotUsed, NotUsed, NotUsed) =>
    }
  }

  test("Children mutating or moving") {
    val program1 = compile(
      """
        |exported func main() int {
        |  x = 4;
        |  if (true) { { set x = 9; }(); } else { { moveThis(x); }(); }
        |}
      """.stripMargin)
    val main = program1.lookupFunction("main")
    val CodeBodyS(body) = main.body
    body.block.locals.head match {
      case LocalS(
          CodeVarNameS("x"),
           NotUsed, NotUsed, NotUsed, NotUsed, Used, Used) =>
    }
  }

  test("Self moving and mutating same variable") {
    val program1 = compile("exported func main() int { x = 4; set x = +(x, 1); }")
    val main = program1.lookupFunction("main")
    val CodeBodyS(body) = main.body
    body.block.locals.head match {
      case LocalS(
          CodeVarNameS("x"),
           NotUsed, Used, Used, NotUsed, NotUsed, NotUsed) =>
    }
  }

  test("Children moving and mutating same variable") {
    val program1 = compile("exported func main() int { x = 4; { set x = +(x, 1); }(); }")
    val main = program1.lookupFunction("main")
    val CodeBodyS(body) = main.body
    body.block.locals.head match {
      case LocalS(
          CodeVarNameS("x"),
           NotUsed, NotUsed, NotUsed, NotUsed, Used, Used) =>
    }
  }

  test("Self borrowing param") {
    val program1 = compile(
      """
        |func main(x int) {
        |  print(&x);
        |}
      """.stripMargin)
    val main = program1.lookupFunction("main")
    val CodeBodyS(body) = main.body
    body.block.locals.head match {
      case LocalS(
          CodeVarNameS("x"),
           Used, NotUsed, NotUsed, NotUsed, NotUsed, NotUsed) =>
    }
  }

  test("Children borrowing param") {
    val program1 = compile(
      """
        |func main(x int) {
        |  { print(&x); }();
        |}
      """.stripMargin)
    val main = program1.lookupFunction("main")
    val CodeBodyS(body) = main.body
    body.block.locals.head match {
      case LocalS(
          CodeVarNameS("x"),
          NotUsed, NotUsed, NotUsed, Used, NotUsed, NotUsed) =>
    }
  }

  test("Self loading or mutating or moving") {
    val program1 = compile(
      """
        |exported func main() int {
        |  x = 4;
        |  if (true) { set x = 9; } else if (true) { moveThis(x); } else { blark(&x); }
        |}
      """.stripMargin)
    val main = program1.lookupFunction("main")
    val CodeBodyS(body) = main.body
    body.block.locals.head match {
      case LocalS(
          CodeVarNameS("x"),
           Used, Used, Used, NotUsed, NotUsed, NotUsed) =>
    }
  }

  test("Children loading or mutating or moving") {
    val program1 = compile(
      """
        |exported func main() int {
        |  x = 4;
        |  if (true) { { set x = 9; }(); } else if (true) { { moveThis(x); }(); } else { { blark(&x); }(); }
        |}
      """.stripMargin)
    val main = program1.lookupFunction("main")
    val CodeBodyS(body) = main.body
    body.block.locals.head match {
      case LocalS(
          CodeVarNameS("x"),
           NotUsed, NotUsed, NotUsed, Used, Used, Used) =>
    }
  }

  test("While condition borrowing") {
    val program1 = compile(
      """
        |exported func main() int {
        |  x = Marine();
        |  while (&x) { }
        |}
      """.stripMargin)
    val main = program1.lookupFunction("main")
    // x is always borrowed because the condition of a while is always run
    val CodeBodyS(body) = main.body
    body.block.locals.head match {
      case LocalS(
          CodeVarNameS("x"),
           Used, NotUsed, NotUsed, NotUsed, NotUsed, NotUsed) =>
    }
  }

  test("While body maybe loading") {
    val program1 = compile(
      """
        |exported func main() int {
        |  x = Marine();
        |  while (true) { doThing(&x); }
        |}
      """.stripMargin)
    val main = program1.lookupFunction("main")
    val CodeBodyS(body) = main.body
    body.block.locals.head match {
      case LocalS(
          CodeVarNameS("x"),
           Used, NotUsed, NotUsed, NotUsed, NotUsed, NotUsed) =>
    }
  }

  test("Include closure var in locals") {
    val program1 = compile(
      """
        |exported func main() int {
        |  m = Marine();
        |  { m.shout() }();
        |}
      """.stripMargin)
    val scoutput = program1
    val main = scoutput.lookupFunction("main")
    val CodeBodyS(BodySE(_, _, BlockSE(_, _, ConsecutorSE(exprs)))) = main.body
    // __Closure is shown as not used... we could change scout to automatically
    // borrow it whenever we try to access a closure variable?
    val lamBlock =
      exprs.collect({
        case FunctionCallSE(_, OwnershippedSE(_, FunctionSE(FunctionS(_, _, _, _, _, _, _, _, CodeBodyS(innerBody))), _), _) => innerBody.block
      }).head
    lamBlock.locals.head match {
      case LocalS(name, NotUsed, NotUsed, NotUsed, NotUsed, NotUsed, NotUsed) => {
        name match {
          case ClosureParamNameS() =>
        }
      }
    }
  }

  test("Include _ in locals") {
    val program1 = compile(
      """
        |exported func main() int {
        |  { print(_) }(3);
        |}
      """.stripMargin)
    val scoutput = program1
    val main = scoutput.lookupFunction("main")
    val CodeBodyS(BodySE(_, _, BlockSE(_, _, ConsecutorSE(exprs)))) = main.body
    // __Closure is shown as not used... we could change scout to automatically
    // borrow it whenever we try to access a closure variable?
    val lamBlock =
      exprs.collect({
        case FunctionCallSE(_, OwnershippedSE(_, FunctionSE(FunctionS(_, _, _, _, _, _, _, _, CodeBodyS(innerBody))), _), _) => innerBody.block
      }).head
    val locals = lamBlock.locals
    locals.find(_.varName match { case ClosureParamNameS() => true case _ => false }).get match {
      case LocalS(ClosureParamNameS(),
        NotUsed, NotUsed, NotUsed, NotUsed, NotUsed, NotUsed) =>
    }
  }
}
