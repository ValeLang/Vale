package net.verdagon.vale.parser

import net.verdagon.vale.{Samples, vassert}
import org.scalatest.{FunSuite, Matchers}



class ParseSamplesTests extends FunSuite with Matchers with Collector with TestParseUtils {
  test("programs/weaks/dropThenLockStruct.vale") { compileProgramWithComments(Samples.get("programs/weaks/dropThenLockStruct.vale")) }
  test("programs/weaks/lockWhileLiveStruct.vale") { compileProgramWithComments(Samples.get("programs/weaks/lockWhileLiveStruct.vale")) }
  test("programs/weaks/weakFromCRefStruct.vale") { compileProgramWithComments(Samples.get("programs/weaks/weakFromCRefStruct.vale")) }
  test("programs/weaks/weakFromLocalCRefStruct.vale") { compileProgramWithComments(Samples.get("programs/weaks/weakFromLocalCRefStruct.vale")) }
  test("programs/addret.vale") { compileProgramWithComments(Samples.get("programs/addret.vale")) }
  test("programs/arrays/immusa.vale") { compileProgramWithComments(Samples.get("programs/arrays/immusa.vale")) }
  test("programs/arrays/immusalen.vale") { compileProgramWithComments(Samples.get("programs/arrays/immusalen.vale")) }
  test("programs/arrays/knownsizeimmarray.vale") { compileProgramWithComments(Samples.get("programs/arrays/knownsizeimmarray.vale")) }
  test("programs/arrays/mutusa.vale") { compileProgramWithComments(Samples.get("programs/arrays/mutusa.vale")) }
  test("programs/arrays/mutusalen.vale") { compileProgramWithComments(Samples.get("programs/arrays/mutusalen.vale")) }
  test("programs/arrays/swapmutusadestroy.vale") { compileProgramWithComments(Samples.get("programs/arrays/swapmutusadestroy.vale")) }
  test("programs/constraintRef.vale") { compileProgramWithComments(Samples.get("programs/constraintRef.vale")) }
  test("libraries/opt.vale") { compileProgramWithComments(Samples.get("libraries/opt.vale")) }
  test("programs/if/if.vale") { compileProgramWithComments(Samples.get("programs/if/if.vale")) }
  test("programs/if/nestedif.vale") { compileProgramWithComments(Samples.get("programs/if/nestedif.vale")) }
  test("programs/lambdas/lambda.vale") { compileProgramWithComments(Samples.get("programs/lambdas/lambda.vale")) }
  test("programs/lambdas/lambdamut.vale") { compileProgramWithComments(Samples.get("programs/lambdas/lambdamut.vale")) }
  test("programs/mutlocal.vale") { compileProgramWithComments(Samples.get("programs/mutlocal.vale")) }
  test("programs/nestedblocks.vale") { compileProgramWithComments(Samples.get("programs/nestedblocks.vale")) }
  test("programs/panic.vale") { compileProgramWithComments(Samples.get("programs/panic.vale")) }
  test("programs/strings/inttostr.vale") { compileProgramWithComments(Samples.get("programs/strings/inttostr.vale")) }
  test("programs/strings/stradd.vale") { compileProgramWithComments(Samples.get("programs/strings/stradd.vale")) }
  test("programs/strings/strprint.vale") { compileProgramWithComments(Samples.get("programs/strings/strprint.vale")) }
  test("programs/structs/bigimmstruct.vale") { compileProgramWithComments(Samples.get("programs/structs/bigimmstruct.vale")) }
  test("programs/structs/immstruct.vale") { compileProgramWithComments(Samples.get("programs/structs/immstruct.vale")) }
  test("programs/structs/memberrefcount.vale") { compileProgramWithComments(Samples.get("programs/structs/memberrefcount.vale")) }
  test("programs/structs/mutstruct.vale") { compileProgramWithComments(Samples.get("programs/structs/mutstruct.vale")) }
  test("programs/structs/mutstructstore.vale") { compileProgramWithComments(Samples.get("programs/structs/mutstructstore.vale")) }
  test("programs/unreachablemoot.vale") { compileProgramWithComments(Samples.get("programs/unreachablemoot.vale")) }
  test("programs/unstackifyret.vale") { compileProgramWithComments(Samples.get("programs/unstackifyret.vale")) }
  test("programs/virtuals/imminterface.vale") { compileProgramWithComments(Samples.get("programs/virtuals/imminterface.vale")) }
  test("programs/virtuals/mutinterface.vale") { compileProgramWithComments(Samples.get("programs/virtuals/mutinterface.vale")) }
  test("programs/while/while.vale") { compileProgramWithComments(Samples.get("programs/while/while.vale")) }

}
