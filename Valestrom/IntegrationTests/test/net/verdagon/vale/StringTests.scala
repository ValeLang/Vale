package net.verdagon.vale

import net.verdagon.vale.templar._
import net.verdagon.vale.templar.ast.ConstantStrTE
import net.verdagon.von.{VonInt, VonStr}
import org.scalatest.{FunSuite, Matchers}

class StringTests extends FunSuite with Matchers {
  test("Simple string") {
    val compile = RunCompilation.test(
      """
        |exported func main() str {
        |  ret "sprogwoggle";
        |}
      """.stripMargin)

    val temputs = compile.expectTemputs()
    Collector.only(temputs.lookupFunction("main"), { case ConstantStrTE("sprogwoggle") => })

    compile.evalForKind(Vector()) match { case VonStr("sprogwoggle") => }
  }

  test("Empty string") {
    val compile = RunCompilation.test(
      """
        |exported func main() str {
        |  ret "";
        |}
      """.stripMargin)

    val temputs = compile.expectTemputs()
    Collector.only(temputs.lookupFunction("main"), { case ConstantStrTE("") => })

    compile.evalForKind(Vector()) match { case VonStr("") => }
  }

  test("String with escapes") {
    val compile = RunCompilation.test(
      """
        |exported func main() str {
        |  ret "sprog\nwoggle";
        |}
        |""".stripMargin)

    val temputs = compile.expectTemputs()
    Collector.only(temputs.lookupFunction("main"), { case ConstantStrTE("sprog\nwoggle") => })

    compile.evalForKind(Vector()) match { case VonStr("sprog\nwoggle") => }
  }

  test("String with hex escape") {
    val code = "exported func main() str { ret \"sprog\\u001bwoggle\"; }"
    // This assert makes sure the above is making the input we actually intend.
    // Real source files from disk are going to have a backslash character and then a u,
    // they won't have the 0x1b byte.
    vassert(code.contains("\\u001b"))

    val compile = RunCompilation.test(code)

    val temputs = compile.expectTemputs()
    Collector.only(temputs.lookupFunction("main"), {
      case ConstantStrTE(x) => {
        x shouldEqual "sprog\u001bwoggle"
      }
    })

    val VonStr(result) = compile.evalForKind(Vector())
    result.size shouldEqual 12
    result shouldEqual "sprog\u001bwoggle"
  }

  test("int to string") {
    val compile = RunCompilation.test( Tests.loadExpected("programs/strings/inttostr.vale"))
    compile.evalForKind(Vector()) match { case VonInt(4) => }
  }

  test("i64 to string") {
    val compile = RunCompilation.test( Tests.loadExpected("programs/strings/i64tostr.vale"))
    compile.evalForKind(Vector()) match { case VonInt(4) => }
  }

  test("String length") {
    val compile = RunCompilation.test( Tests.loadExpected("programs/strings/strlen.vale"))

    compile.evalForKind(Vector()) match { case VonInt(12) => }
  }

  test("String interpolate") {
    val compile = RunCompilation.test(
      "func +(s str, i int) str { ret s + str(i); }\n" +
      "func ns(i int) int { ret i; }\n" +
      "exported func main() str { ret \"\"\"bl\"{ns(4)}rg\"\"\"; }")

    compile.evalForKind(Vector()) match { case VonStr("bl\"4rg") => }
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
          |func newStrSlice(string str, begin int, end int) StrSlice {
          |  vassert(begin >= 0, "slice begin was negative!");
          |  vassert(end >= 0, "slice end was negative!");
          |  vassert(begin <= string.len(), "slice begin was more than length!");
          |  vassert(end <= string.len(), "slice end was more than length!");
          |  vassert(end >= begin, "slice end was before begin!");
          |  ret StrSlice(string, begin, end);
          |}
          |
          |func slice(s str) StrSlice {
          |  ret newStrSlice(s, 0, s.len());
          |}
          |
          |func slice(s str, begin int) StrSlice { ret s.slice().slice(begin); }
          |func slice(s StrSlice, begin int) StrSlice {
          |  newBegin = s.begin + begin;
          |  vassert(newBegin <= s.string.len(), "slice begin is more than string length!");
          |  ret newStrSlice(s.string, newBegin, s.end);
          |}
          |
          |func len(s StrSlice) int {
          |  ret s.end - s.begin;
          |}
          |
          |func slice(s str, begin int, end int) StrSlice {
          |  ret newStrSlice(s, begin, end);
          |}
          |
          |func slice(s StrSlice, begin int, end int) StrSlice {
          |  newGlyphBeginOffset = s.begin + begin;
          |  newGlyphEndOffset = s.begin + end;
          |  ret newStrSlice(s.string, newGlyphBeginOffset, newGlyphEndOffset);
          |}
          |
          |exported func main() int {
          |  ret "hello".slice().slice(1, 4).len();
          |}
          |""".stripMargin)

    compile.evalForKind(Vector()) match { case VonInt(3) => }
  }
}
