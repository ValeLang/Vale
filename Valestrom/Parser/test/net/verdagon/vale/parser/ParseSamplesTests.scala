package net.verdagon.vale.parser

import net.verdagon.vale.parser.old.OldTestParseUtils
import net.verdagon.vale.{Collector, Tests, vassert}
import org.scalatest.{FunSuite, Matchers}



class ParseSamplesTests extends FunSuite with Collector with TestParseUtils {
  test("programs/weaks/dropThenLockStruct.vale") { Parser.runParserForProgramAndCommentRanges(Tests.loadExpected("programs/weaks/dropThenLockStruct.vale")).getOrDie() }
  test("programs/weaks/lockWhileLiveStruct.vale") { Parser.runParserForProgramAndCommentRanges(Tests.loadExpected("programs/weaks/lockWhileLiveStruct.vale")).getOrDie() }
  test("programs/weaks/weakFromCRefStruct.vale") { Parser.runParserForProgramAndCommentRanges(Tests.loadExpected("programs/weaks/weakFromCRefStruct.vale")).getOrDie() }
  test("programs/weaks/weakFromLocalCRefStruct.vale") { Parser.runParserForProgramAndCommentRanges(Tests.loadExpected("programs/weaks/weakFromLocalCRefStruct.vale")).getOrDie() }
  test("programs/addret.vale") { Parser.runParserForProgramAndCommentRanges(Tests.loadExpected("programs/addret.vale")).getOrDie() }
  test("programs/arrays/rsaimm.vale") { Parser.runParserForProgramAndCommentRanges(Tests.loadExpected("programs/arrays/rsaimm.vale")).getOrDie() }
  test("programs/arrays/rsaimmlen.vale") { Parser.runParserForProgramAndCommentRanges(Tests.loadExpected("programs/arrays/rsaimmlen.vale")).getOrDie() }
  test("programs/arrays/ssaimmfromvalues.vale") { Parser.runParserForProgramAndCommentRanges(Tests.loadExpected("programs/arrays/ssaimmfromvalues.vale")).getOrDie() }
  test("programs/arrays/rsamut.vale") { Parser.runParserForProgramAndCommentRanges(Tests.loadExpected("programs/arrays/rsamut.vale")).getOrDie() }
  test("programs/arrays/rsamutlen.vale") { Parser.runParserForProgramAndCommentRanges(Tests.loadExpected("programs/arrays/rsamutlen.vale")).getOrDie() }
  test("programs/arrays/swaprsamutdestroy.vale") { Parser.runParserForProgramAndCommentRanges(Tests.loadExpected("programs/arrays/swaprsamutdestroy.vale")).getOrDie() }
  test("programs/constraintRef.vale") { Parser.runParserForProgramAndCommentRanges(Tests.loadExpected("programs/constraintRef.vale")).getOrDie() }
  test("optutils/optutils.vale") { Parser.runParserForProgramAndCommentRanges(Tests.loadExpected("optutils/optutils.vale")).getOrDie() }
  test("programs/if/if.vale") { Parser.runParserForProgramAndCommentRanges(Tests.loadExpected("programs/if/if.vale")).getOrDie() }
  test("programs/if/nestedif.vale") { Parser.runParserForProgramAndCommentRanges(Tests.loadExpected("programs/if/nestedif.vale")).getOrDie() }
  test("programs/lambdas/lambda.vale") { Parser.runParserForProgramAndCommentRanges(Tests.loadExpected("programs/lambdas/lambda.vale")).getOrDie() }
  test("programs/lambdas/lambdamut.vale") { Parser.runParserForProgramAndCommentRanges(Tests.loadExpected("programs/lambdas/lambdamut.vale")).getOrDie() }
  test("programs/mutlocal.vale") { Parser.runParserForProgramAndCommentRanges(Tests.loadExpected("programs/mutlocal.vale")).getOrDie() }
  test("programs/nestedblocks.vale") { Parser.runParserForProgramAndCommentRanges(Tests.loadExpected("programs/nestedblocks.vale")).getOrDie() }
  test("programs/panic.vale") { Parser.runParserForProgramAndCommentRanges(Tests.loadExpected("programs/panic.vale")).getOrDie() }
  test("programs/strings/inttostr.vale") { Parser.runParserForProgramAndCommentRanges(Tests.loadExpected("programs/strings/inttostr.vale")).getOrDie() }
  test("programs/strings/stradd.vale") { Parser.runParserForProgramAndCommentRanges(Tests.loadExpected("programs/strings/stradd.vale")).getOrDie() }
  test("programs/strings/strprint.vale") { Parser.runParserForProgramAndCommentRanges(Tests.loadExpected("programs/strings/strprint.vale")).getOrDie() }
  test("programs/structs/bigstructimm.vale") { Parser.runParserForProgramAndCommentRanges(Tests.loadExpected("programs/structs/bigstructimm.vale")).getOrDie() }
  test("programs/structs/structimm.vale") { Parser.runParserForProgramAndCommentRanges(Tests.loadExpected("programs/structs/structimm.vale")).getOrDie() }
  test("programs/structs/memberrefcount.vale") { Parser.runParserForProgramAndCommentRanges(Tests.loadExpected("programs/structs/memberrefcount.vale")).getOrDie() }
  test("programs/structs/structmut.vale") { Parser.runParserForProgramAndCommentRanges(Tests.loadExpected("programs/structs/structmut.vale")).getOrDie() }
  test("programs/structs/structmutstore.vale") { Parser.runParserForProgramAndCommentRanges(Tests.loadExpected("programs/structs/structmutstore.vale")).getOrDie() }
  test("programs/unreachablemoot.vale") { Parser.runParserForProgramAndCommentRanges(Tests.loadExpected("programs/unreachablemoot.vale")).getOrDie() }
  test("programs/unstackifyret.vale") { Parser.runParserForProgramAndCommentRanges(Tests.loadExpected("programs/unstackifyret.vale")).getOrDie() }
  test("programs/virtuals/interfaceimm.vale") { Parser.runParserForProgramAndCommentRanges(Tests.loadExpected("programs/virtuals/interfaceimm.vale")).getOrDie() }
  test("programs/virtuals/interfacemut.vale") { Parser.runParserForProgramAndCommentRanges(Tests.loadExpected("programs/virtuals/interfacemut.vale")).getOrDie() }
  test("programs/while/while.vale") { Parser.runParserForProgramAndCommentRanges(Tests.loadExpected("programs/while/while.vale")).getOrDie() }

}
