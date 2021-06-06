package net.verdagon.vale

import net.verdagon.vale.templar._
import net.verdagon.von.{VonInt, VonStr}
import org.scalatest.{FunSuite, Matchers}

class StringTests extends FunSuite with Matchers {
  test("Simple string") {
    val compile = RunCompilation.test(
      """
        |fn main() str export {
        |  "sprogwoggle"
        |}
      """.stripMargin)

    val temputs = compile.expectTemputs()
    temputs.lookupFunction("main").only({ case ConstantStrTE("sprogwoggle") => })

    compile.evalForKind(Vector()) shouldEqual VonStr("sprogwoggle")
  }

  test("String with escapes") {
    val compile = RunCompilation.test(
      """
        |fn main() str export {
        |  "sprog\nwoggle"
        |}
        |""".stripMargin)

    val temputs = compile.expectTemputs()
    temputs.lookupFunction("main").only({ case ConstantStrTE("sprog\nwoggle") => })

    compile.evalForKind(Vector()) shouldEqual VonStr("sprog\nwoggle")
  }

  test("String with hex escape") {
    val compile = RunCompilation.test(
      """
        |fn main() str export {
        |  "sprog\u001bwoggle"
        |}
        |""".stripMargin)

    val temputs = compile.expectTemputs()
    temputs.lookupFunction("main").only({
      case ConstantStrTE(x) => {
        x shouldEqual "sprog\u001bwoggle"
      }
    })

    compile.evalForKind(Vector()) shouldEqual VonStr("sprog\u001bwoggle")
  }

  test("String length") {
    val compile = RunCompilation.test( Tests.loadExpected("programs/strings/strlen.vale"))

    compile.evalForKind(Vector()) shouldEqual VonInt(11)
  }

  test("String interpolate") {
    val compile = RunCompilation.test(
      "fn +(s str, i int) str { s + str(i) }\n" +
      "fn ns(i int) int { i }\n" +
      "fn main() str export { \"\"\"bl\"{ns(4)}rg\"\"\" }")

    compile.evalForKind(Vector()) shouldEqual VonStr("bl\"4rg")
  }

  test("Slice a slice") {
    val compile = RunCompilation.test(
        """
          |import panicutils.*;
          |import printutils.*;
          |
          |struct StrSlice imm {
          |  string str;
          |  begin int;
          |  end int;
          |}
          |fn newStrSlice(string str, begin int, end int) StrSlice {
          |  vassert(begin >= 0, "slice begin was negative!");
          |  vassert(end >= 0, "slice end was negative!");
          |  vassert(begin <= string.len(), "slice begin was more than length!");
          |  vassert(end <= string.len(), "slice end was more than length!");
          |  vassert(end >= begin, "slice end was before begin!");
          |  ret StrSlice(string, begin, end);
          |}
          |
          |fn slice(s str) StrSlice {
          |  newStrSlice(s, 0, s.len())
          |}
          |
          |fn slice(s str, begin int) StrSlice { s.slice().slice(begin) }
          |fn slice(s StrSlice, begin int) StrSlice {
          |  newBegin = s.begin + begin;
          |  vassert(newBegin <= s.string.len(), "slice begin is more than string length!");
          |  = newStrSlice(s.string, newBegin, s.end);
          |}
          |
          |fn len(s StrSlice) int {
          |  ret s.end - s.begin;
          |}
          |
          |fn slice(s str, begin int, end int) StrSlice {
          |  = newStrSlice(s, begin, end);
          |}
          |
          |fn slice(s StrSlice, begin int, end int) StrSlice {
          |  newGlyphBeginOffset = s.begin + begin;
          |  newGlyphEndOffset = s.begin + end;
          |  = newStrSlice(s.string, newGlyphBeginOffset, newGlyphEndOffset);
          |}
          |
          |fn main() int export {
          |  "hello".slice().slice(1, 4).len()
          |}
          |""".stripMargin)

    compile.evalForKind(Vector()) shouldEqual VonInt(3)
  }
}
