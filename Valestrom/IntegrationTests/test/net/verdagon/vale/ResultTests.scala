package net.verdagon.vale

import net.verdagon.vale.vivem.PanicException
import net.verdagon.von.{VonInt, VonStr}
import org.scalatest.{FunSuite, Matchers}

class ResultTests extends FunSuite with Matchers {
  test("Test borrow is_ok and expect for Ok") {
    val compile = RunCompilation.test(
        """
          |import v.builtins.panic.*;
          |import v.builtins.result.*;
          |
          |fn main() int export {
          |  result Result<int, str> = Ok<int, str>(42);
          |  = if (result.is_ok()) { result.expect() }
          |    else { panic("wat") }
          |}
        """.stripMargin)

    compile.evalForReferend(Vector()) shouldEqual VonInt(42)
  }

  test("Test is_err and borrow expect_err for Err") {
    val compile = RunCompilation.test(
        """
          |import v.builtins.panic.*;
          |import v.builtins.result.*;
          |
          |fn main() str export {
          |  result Result<int, str> = Err<int, str>("file not found!");
          |  = if (result.is_err()) { result.expect_err() }
          |    else { panic("fail!") }
          |}
        """.stripMargin)

    compile.evalForReferend(Vector()) shouldEqual VonStr("file not found!")
  }

  test("Test owning expect") {
    val compile = RunCompilation.test(
      """
        |import v.builtins.panic.*;
        |import v.builtins.result.*;
        |
        |fn main() int export {
        |  result Result<int, str> = Ok<int, str>(42);
        |  ret (result).expect();
        |}
        """.stripMargin)

    compile.evalForReferend(Vector()) shouldEqual VonInt(42)
  }

  test("Test owning expect_err") {
    val compile = RunCompilation.test(
      """
        |import v.builtins.panic.*;
        |import v.builtins.result.*;
        |
        |fn main() str export {
        |  result Result<int, str> = Err<int, str>("file not found!");
        |  ret (result).expect_err();
        |}
        """.stripMargin)

    compile.evalForReferend(Vector()) shouldEqual VonStr("file not found!")
  }

  test("Test expect() panics for Err") {
    val compile = RunCompilation.test(
        """
          |import v.builtins.panic.*;
          |import v.builtins.result.*;
          |
          |fn main() int export {
          |  result Result<int, str> = Err<int, str>("file not found!");
          |  ret result.expect();
          |}
        """.stripMargin)

    try {
      compile.evalForReferend(Vector())
      vfail()
    } catch {
      case PanicException() =>
    }
  }

  test("Test expect_err() panics for Ok") {
    val compile = RunCompilation.test(
      """
        |import v.builtins.panic.*;
        |import v.builtins.result.*;
        |
        |fn main() str export {
        |  result Result<int, str> = Ok<int, str>(73);
        |  ret result.expect_err();
        |}
        """.stripMargin)

    try {
      compile.evalForReferend(Vector())
      vfail()
    } catch {
      case PanicException() =>
    }
  }
}
