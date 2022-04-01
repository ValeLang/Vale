package dev.vale

import dev.vale.typing.ast.ConstantStrTE
import dev.vale.typing._
import dev.vale.von.{VonInt, VonStr}
import org.scalatest.{FunSuite, Matchers}

class StringTests extends FunSuite with Matchers {
  test("Simple string") {
    val compile = RunCompilation.test(
      """
        |exported func main() str {
        |  return "sprogwoggle";
        |}
      """.stripMargin)

    val coutputs = compile.expectCompilerOutputs()
    Collector.only(coutputs.lookupFunction("main"), { case ConstantStrTE("sprogwoggle") => })

    compile.evalForKind(Vector()) match { case VonStr("sprogwoggle") => }
  }

  test("Empty string") {
    val compile = RunCompilation.test(
      """
        |exported func main() str {
        |  return "";
        |}
      """.stripMargin)

    val coutputs = compile.expectCompilerOutputs()
    Collector.only(coutputs.lookupFunction("main"), { case ConstantStrTE("") => })

    compile.evalForKind(Vector()) match { case VonStr("") => }
  }

  test("String with escapes") {
    val compile = RunCompilation.test(
      """
        |exported func main() str {
        |  return "sprog\nwoggle";
        |}
        |""".stripMargin)

    val coutputs = compile.expectCompilerOutputs()
    Collector.only(coutputs.lookupFunction("main"), { case ConstantStrTE("sprog\nwoggle") => })

    compile.evalForKind(Vector()) match { case VonStr("sprog\nwoggle") => }
  }

  test("String with hex escape") {
    val code = "exported func main() str { return \"sprog\\u001bwoggle\"; }"
    // This assert makes sure the above is making the input we actually intend.
    // Real source files from disk are going to have a backslash character and then a u,
    // they won't have the 0x1b byte.
    vassert(code.contains("\\u001b"))

    val compile = RunCompilation.test(code)

    val coutputs = compile.expectCompilerOutputs()
    Collector.only(coutputs.lookupFunction("main"), {
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
      "func +(s str, i int) str { return s + str(i); }\n" +
      "func ns(i int) int { return i; }\n" +
      "exported func main() str { return \"\"\"bl\"{ns(4)}rg\"\"\"; }")

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
          |  return StrSlice(string, begin, end);
          |}
          |
          |func slice(s str) StrSlice {
          |  return newStrSlice(s, 0, s.len());
          |}
          |
          |func slice(s str, begin int) StrSlice { return s.slice().slice(begin); }
          |func slice(s StrSlice, begin int) StrSlice {
          |  newBegin = s.begin + begin;
          |  vassert(newBegin <= s.string.len(), "slice begin is more than string length!");
          |  return newStrSlice(s.string, newBegin, s.end);
          |}
          |
          |func len(s StrSlice) int {
          |  return s.end - s.begin;
          |}
          |
          |func slice(s str, begin int, end int) StrSlice {
          |  return newStrSlice(s, begin, end);
          |}
          |
          |func slice(s StrSlice, begin int, end int) StrSlice {
          |  newGlyphBeginOffset = s.begin + begin;
          |  newGlyphEndOffset = s.begin + end;
          |  return newStrSlice(s.string, newGlyphBeginOffset, newGlyphEndOffset);
          |}
          |
          |exported func main() int {
          |  return "hello".slice().slice(1, 4).len();
          |}
          |""".stripMargin)

    compile.evalForKind(Vector()) match { case VonInt(3) => }
  }
}
