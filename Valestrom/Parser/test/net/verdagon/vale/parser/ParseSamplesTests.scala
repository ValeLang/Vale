package net.verdagon.vale.parser

import net.verdagon.vale.{Tests, vassert}
import org.scalatest.{FunSuite, Matchers}



class ParseSamplesTests extends FunSuite with Matchers with Collector with TestParseUtils {
  test("programs/weaks/dropThenLockStruct.vale") { compileProgramWithComments(Tests.loadExpected("programs/weaks/dropThenLockStruct.vale")) }
  test("programs/weaks/lockWhileLiveStruct.vale") { compileProgramWithComments(Tests.loadExpected("programs/weaks/lockWhileLiveStruct.vale")) }
  test("programs/weaks/weakFromCRefStruct.vale") { compileProgramWithComments(Tests.loadExpected("programs/weaks/weakFromCRefStruct.vale")) }
  test("programs/weaks/weakFromLocalCRefStruct.vale") { compileProgramWithComments(Tests.loadExpected("programs/weaks/weakFromLocalCRefStruct.vale")) }
  test("programs/addret.vale") { compileProgramWithComments(Tests.loadExpected("programs/addret.vale")) }
  test("programs/arrays/immrsa.vale") { compileProgramWithComments(Tests.loadExpected("programs/arrays/immrsa.vale")) }
  test("programs/arrays/immrsalen.vale") { compileProgramWithComments(Tests.loadExpected("programs/arrays/immrsalen.vale")) }
  test("programs/arrays/immssafromvalues.vale") { compileProgramWithComments(Tests.loadExpected("programs/arrays/immssafromvalues.vale")) }
  test("programs/arrays/mutrsa.vale") { compileProgramWithComments(Tests.loadExpected("programs/arrays/mutrsa.vale")) }
  test("programs/arrays/mutrsalen.vale") { compileProgramWithComments(Tests.loadExpected("programs/arrays/mutrsalen.vale")) }
  test("programs/arrays/swapmutrsadestroy.vale") { compileProgramWithComments(Tests.loadExpected("programs/arrays/swapmutrsadestroy.vale")) }
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
  test("programs/structs/bigimmstruct.vale") { compileProgramWithComments(Tests.loadExpected("programs/structs/bigimmstruct.vale")) }
  test("programs/structs/immstruct.vale") { compileProgramWithComments(Tests.loadExpected("programs/structs/immstruct.vale")) }
  test("programs/structs/memberrefcount.vale") { compileProgramWithComments(Tests.loadExpected("programs/structs/memberrefcount.vale")) }
  test("programs/structs/mutstruct.vale") { compileProgramWithComments(Tests.loadExpected("programs/structs/mutstruct.vale")) }
  test("programs/structs/mutstructstore.vale") { compileProgramWithComments(Tests.loadExpected("programs/structs/mutstructstore.vale")) }
  test("programs/unreachablemoot.vale") { compileProgramWithComments(Tests.loadExpected("programs/unreachablemoot.vale")) }
  test("programs/unstackifyret.vale") { compileProgramWithComments(Tests.loadExpected("programs/unstackifyret.vale")) }
  test("programs/virtuals/imminterface.vale") { compileProgramWithComments(Tests.loadExpected("programs/virtuals/imminterface.vale")) }
  test("programs/virtuals/mutinterface.vale") { compileProgramWithComments(Tests.loadExpected("programs/virtuals/mutinterface.vale")) }
  test("programs/while/while.vale") { compileProgramWithComments(Tests.loadExpected("programs/while/while.vale")) }

}
