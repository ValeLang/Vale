package net.verdagon.vale.parser

import net.verdagon.vale.{Tests, vassert}
import org.scalatest.{FunSuite, Matchers}



class ParseSamplesTests extends FunSuite with Matchers with Collector with TestParseUtils {
  test("programs/weaks/dropThenLockStruct.vale") { compileProgramWithComments(Tests.loadExpected("programs/weaks/dropThenLockStruct.vale")) }
  test("programs/weaks/lockWhileLiveStruct.vale") { compileProgramWithComments(Tests.loadExpected("programs/weaks/lockWhileLiveStruct.vale")) }
  test("programs/weaks/weakFromCRefStruct.vale") { compileProgramWithComments(Tests.loadExpected("programs/weaks/weakFromCRefStruct.vale")) }
  test("programs/weaks/weakFromLocalCRefStruct.vale") { compileProgramWithComments(Tests.loadExpected("programs/weaks/weakFromLocalCRefStruct.vale")) }
  test("programs/addret.vale") { compileProgramWithComments(Tests.loadExpected("programs/addret.vale")) }
  test("programs/arrays/rsaimm.vale") { compileProgramWithComments(Tests.loadExpected("programs/arrays/rsaimm.vale")) }
  test("programs/arrays/rsaimmlen.vale") { compileProgramWithComments(Tests.loadExpected("programs/arrays/rsaimmlen.vale")) }
  test("programs/arrays/ssaimmfromvalues.vale") { compileProgramWithComments(Tests.loadExpected("programs/arrays/ssaimmfromvalues.vale")) }
  test("programs/arrays/rsamut.vale") { compileProgramWithComments(Tests.loadExpected("programs/arrays/rsamut.vale")) }
  test("programs/arrays/rsamutlen.vale") { compileProgramWithComments(Tests.loadExpected("programs/arrays/rsamutlen.vale")) }
  test("programs/arrays/swaprsamutdestroy.vale") { compileProgramWithComments(Tests.loadExpected("programs/arrays/swaprsamutdestroy.vale")) }
  test("programs/constraintRef.vale") { compileProgramWithComments(Tests.loadExpected("programs/constraintRef.vale")) }
  test("optutils/optutils.vale") { compileProgramWithComments(Tests.loadExpected("optutils/optutils.vale")) }
  test("programs/if/if.vale") { compileProgramWithComments(Tests.loadExpected("programs/if/if.vale")) }
  test("programs/if/nestedif.vale") { compileProgramWithComments(Tests.loadExpected("programs/if/nestedif.vale")) }
  test("programs/lambdas/lambda.vale") { compileProgramWithComments(Tests.loadExpected("programs/lambdas/lambda.vale")) }
  test("programs/lambdas/lambdamut.vale") { compileProgramWithComments(Tests.loadExpected("programs/lambdas/lambdamut.vale")) }
  test("programs/mutlocal.vale") { compileProgramWithComments(Tests.loadExpected("programs/mutlocal.vale")) }
  test("programs/nestedblocks.vale") { compileProgramWithComments(Tests.loadExpected("programs/nestedblocks.vale")) }
  test("programs/panic.vale") { compileProgramWithComments(Tests.loadExpected("programs/panic.vale")) }
  test("programs/strings/inttostr.vale") { compileProgramWithComments(Tests.loadExpected("programs/strings/inttostr.vale")) }
  test("programs/strings/stradd.vale") { compileProgramWithComments(Tests.loadExpected("programs/strings/stradd.vale")) }
  test("programs/strings/strprint.vale") { compileProgramWithComments(Tests.loadExpected("programs/strings/strprint.vale")) }
  test("programs/structs/bigstructimm.vale") { compileProgramWithComments(Tests.loadExpected("programs/structs/bigstructimm.vale")) }
  test("programs/structs/structimm.vale") { compileProgramWithComments(Tests.loadExpected("programs/structs/structimm.vale")) }
  test("programs/structs/memberrefcount.vale") { compileProgramWithComments(Tests.loadExpected("programs/structs/memberrefcount.vale")) }
  test("programs/structs/structmut.vale") { compileProgramWithComments(Tests.loadExpected("programs/structs/structmut.vale")) }
  test("programs/structs/structmutstore.vale") { compileProgramWithComments(Tests.loadExpected("programs/structs/structmutstore.vale")) }
  test("programs/unreachablemoot.vale") { compileProgramWithComments(Tests.loadExpected("programs/unreachablemoot.vale")) }
  test("programs/unstackifyret.vale") { compileProgramWithComments(Tests.loadExpected("programs/unstackifyret.vale")) }
  test("programs/virtuals/interfaceimm.vale") { compileProgramWithComments(Tests.loadExpected("programs/virtuals/interfaceimm.vale")) }
  test("programs/virtuals/interfacemut.vale") { compileProgramWithComments(Tests.loadExpected("programs/virtuals/interfacemut.vale")) }
  test("programs/while/while.vale") { compileProgramWithComments(Tests.loadExpected("programs/while/while.vale")) }

}
