package net.verdagon.vale.parser

import net.verdagon.vale.options.GlobalOptions
import net.verdagon.vale.parser.old.OldTestParseUtils
import net.verdagon.vale.{Collector, Tests, vassert}
import org.scalatest.{FunSuite, Matchers}



class ParseSamplesTests extends FunSuite with Collector with TestParseUtils {
  test("programs/weaks/dropThenLockStruct.vale") { new Parser(GlobalOptions(true, true, true, true)).runParserForProgramAndCommentRanges(Tests.loadExpected("programs/weaks/dropThenLockStruct.vale")).getOrDie() }
  test("programs/weaks/lockWhileLiveStruct.vale") { new Parser(GlobalOptions(true, true, true, true)).runParserForProgramAndCommentRanges(Tests.loadExpected("programs/weaks/lockWhileLiveStruct.vale")).getOrDie() }
  test("programs/weaks/weakFromCRefStruct.vale") { new Parser(GlobalOptions(true, true, true, true)).runParserForProgramAndCommentRanges(Tests.loadExpected("programs/weaks/weakFromCRefStruct.vale")).getOrDie() }
  test("programs/weaks/weakFromLocalCRefStruct.vale") { new Parser(GlobalOptions(true, true, true, true)).runParserForProgramAndCommentRanges(Tests.loadExpected("programs/weaks/weakFromLocalCRefStruct.vale")).getOrDie() }
  test("programs/addret.vale") { new Parser(GlobalOptions(true, true, true, true)).runParserForProgramAndCommentRanges(Tests.loadExpected("programs/addret.vale")).getOrDie() }
  test("programs/arrays/rsaimm.vale") { new Parser(GlobalOptions(true, true, true, true)).runParserForProgramAndCommentRanges(Tests.loadExpected("programs/arrays/rsaimm.vale")).getOrDie() }
  test("programs/arrays/rsaimmlen.vale") { new Parser(GlobalOptions(true, true, true, true)).runParserForProgramAndCommentRanges(Tests.loadExpected("programs/arrays/rsaimmlen.vale")).getOrDie() }
  test("programs/arrays/ssaimmfromvalues.vale") { new Parser(GlobalOptions(true, true, true, true)).runParserForProgramAndCommentRanges(Tests.loadExpected("programs/arrays/ssaimmfromvalues.vale")).getOrDie() }
  test("programs/arrays/rsamut.vale") { new Parser(GlobalOptions(true, true, true, true)).runParserForProgramAndCommentRanges(Tests.loadExpected("programs/arrays/rsamut.vale")).getOrDie() }
  test("programs/arrays/rsamutlen.vale") { new Parser(GlobalOptions(true, true, true, true)).runParserForProgramAndCommentRanges(Tests.loadExpected("programs/arrays/rsamutlen.vale")).getOrDie() }
  test("programs/arrays/swaprsamutdestroy.vale") { new Parser(GlobalOptions(true, true, true, true)).runParserForProgramAndCommentRanges(Tests.loadExpected("programs/arrays/swaprsamutdestroy.vale")).getOrDie() }
  test("programs/constraintRef.vale") { new Parser(GlobalOptions(true, true, true, true)).runParserForProgramAndCommentRanges(Tests.loadExpected("programs/constraintRef.vale")).getOrDie() }
  test("optutils/optutils.vale") { new Parser(GlobalOptions(true, true, true, true)).runParserForProgramAndCommentRanges(Tests.loadExpected("optutils/optutils.vale")).getOrDie() }
  test("programs/if/if.vale") { new Parser(GlobalOptions(true, true, true, true)).runParserForProgramAndCommentRanges(Tests.loadExpected("programs/if/if.vale")).getOrDie() }
  test("programs/if/nestedif.vale") { new Parser(GlobalOptions(true, true, true, true)).runParserForProgramAndCommentRanges(Tests.loadExpected("programs/if/nestedif.vale")).getOrDie() }
  test("programs/lambdas/lambda.vale") { new Parser(GlobalOptions(true, true, true, true)).runParserForProgramAndCommentRanges(Tests.loadExpected("programs/lambdas/lambda.vale")).getOrDie() }
  test("programs/lambdas/lambdamut.vale") { new Parser(GlobalOptions(true, true, true, true)).runParserForProgramAndCommentRanges(Tests.loadExpected("programs/lambdas/lambdamut.vale")).getOrDie() }
  test("programs/mutlocal.vale") { new Parser(GlobalOptions(true, true, true, true)).runParserForProgramAndCommentRanges(Tests.loadExpected("programs/mutlocal.vale")).getOrDie() }
  test("programs/nestedblocks.vale") { new Parser(GlobalOptions(true, true, true, true)).runParserForProgramAndCommentRanges(Tests.loadExpected("programs/nestedblocks.vale")).getOrDie() }
  test("programs/panic.vale") { new Parser(GlobalOptions(true, true, true, true)).runParserForProgramAndCommentRanges(Tests.loadExpected("programs/panic.vale")).getOrDie() }
  test("programs/strings/inttostr.vale") { new Parser(GlobalOptions(true, true, true, true)).runParserForProgramAndCommentRanges(Tests.loadExpected("programs/strings/inttostr.vale")).getOrDie() }
  test("programs/strings/stradd.vale") { new Parser(GlobalOptions(true, true, true, true)).runParserForProgramAndCommentRanges(Tests.loadExpected("programs/strings/stradd.vale")).getOrDie() }
  test("programs/strings/strprint.vale") { new Parser(GlobalOptions(true, true, true, true)).runParserForProgramAndCommentRanges(Tests.loadExpected("programs/strings/strprint.vale")).getOrDie() }
  test("programs/structs/bigstructimm.vale") { new Parser(GlobalOptions(true, true, true, true)).runParserForProgramAndCommentRanges(Tests.loadExpected("programs/structs/bigstructimm.vale")).getOrDie() }
  test("programs/structs/structimm.vale") { new Parser(GlobalOptions(true, true, true, true)).runParserForProgramAndCommentRanges(Tests.loadExpected("programs/structs/structimm.vale")).getOrDie() }
  test("programs/structs/memberrefcount.vale") { new Parser(GlobalOptions(true, true, true, true)).runParserForProgramAndCommentRanges(Tests.loadExpected("programs/structs/memberrefcount.vale")).getOrDie() }
  test("programs/structs/structmut.vale") { new Parser(GlobalOptions(true, true, true, true)).runParserForProgramAndCommentRanges(Tests.loadExpected("programs/structs/structmut.vale")).getOrDie() }
  test("programs/structs/structmutstore.vale") { new Parser(GlobalOptions(true, true, true, true)).runParserForProgramAndCommentRanges(Tests.loadExpected("programs/structs/structmutstore.vale")).getOrDie() }
  test("programs/unreachablemoot.vale") { new Parser(GlobalOptions(true, true, true, true)).runParserForProgramAndCommentRanges(Tests.loadExpected("programs/unreachablemoot.vale")).getOrDie() }
  test("programs/unstackifyret.vale") { new Parser(GlobalOptions(true, true, true, true)).runParserForProgramAndCommentRanges(Tests.loadExpected("programs/unstackifyret.vale")).getOrDie() }
  test("programs/virtuals/interfaceimm.vale") { new Parser(GlobalOptions(true, true, true, true)).runParserForProgramAndCommentRanges(Tests.loadExpected("programs/virtuals/interfaceimm.vale")).getOrDie() }
  test("programs/virtuals/interfacemut.vale") { new Parser(GlobalOptions(true, true, true, true)).runParserForProgramAndCommentRanges(Tests.loadExpected("programs/virtuals/interfacemut.vale")).getOrDie() }
  test("programs/while/while.vale") { new Parser(GlobalOptions(true, true, true, true)).runParserForProgramAndCommentRanges(Tests.loadExpected("programs/while/while.vale")).getOrDie() }

}
