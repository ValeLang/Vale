package net.verdagon.vale.parser

import net.verdagon.vale.{Samples, vassert}
import org.scalatest.{FunSuite, Matchers}



class ParseSamplesTests extends FunSuite with Matchers with Collector with TestParseUtils {
  test("weaks/dropThenLock.vale") { compileProgramWithComments(Samples.get("weaks/dropThenLock.vale")) }
  test("weaks/lockWhileLive.vale") { compileProgramWithComments(Samples.get("weaks/lockWhileLive.vale")) }
  test("weaks/weakFromCRef.vale") { compileProgramWithComments(Samples.get("weaks/weakFromCRef.vale")) }
  test("weaks/weakFromLocalCRef.vale") { compileProgramWithComments(Samples.get("weaks/weakFromLocalCRef.vale")) }
  test("addret.vale") { compileProgramWithComments(Samples.get("addret.vale")) }
  test("arrays/immusa.vale") { compileProgramWithComments(Samples.get("arrays/immusa.vale")) }
  test("arrays/immusalen.vale") { compileProgramWithComments(Samples.get("arrays/immusalen.vale")) }
  test("arrays/knownsizeimmarray.vale") { compileProgramWithComments(Samples.get("arrays/knownsizeimmarray.vale")) }
  test("arrays/mutusa.vale") { compileProgramWithComments(Samples.get("arrays/mutusa.vale")) }
  test("arrays/mutusalen.vale") { compileProgramWithComments(Samples.get("arrays/mutusalen.vale")) }
  test("arrays/swapmutusadestroy.vale") { compileProgramWithComments(Samples.get("arrays/swapmutusadestroy.vale")) }
  test("constraintRef.vale") { compileProgramWithComments(Samples.get("constraintRef.vale")) }
  test("genericvirtuals/opt.vale") { compileProgramWithComments(Samples.get("genericvirtuals/opt.vale")) }
  test("if/if.vale") { compileProgramWithComments(Samples.get("if/if.vale")) }
  test("if/nestedif.vale") { compileProgramWithComments(Samples.get("if/nestedif.vale")) }
  test("lambdas/lambda.vale") { compileProgramWithComments(Samples.get("lambdas/lambda.vale")) }
  test("lambdas/lambdamut.vale") { compileProgramWithComments(Samples.get("lambdas/lambdamut.vale")) }
  test("mutlocal.vale") { compileProgramWithComments(Samples.get("mutlocal.vale")) }
  test("nestedblocks.vale") { compileProgramWithComments(Samples.get("nestedblocks.vale")) }
  test("panic.vale") { compileProgramWithComments(Samples.get("panic.vale")) }
  test("strings/inttostr.vale") { compileProgramWithComments(Samples.get("strings/inttostr.vale")) }
  test("strings/stradd.vale") { compileProgramWithComments(Samples.get("strings/stradd.vale")) }
  test("strings/strprint.vale") { compileProgramWithComments(Samples.get("strings/strprint.vale")) }
  test("structs/bigimmstruct.vale") { compileProgramWithComments(Samples.get("structs/bigimmstruct.vale")) }
  test("structs/immstruct.vale") { compileProgramWithComments(Samples.get("structs/immstruct.vale")) }
  test("structs/memberrefcount.vale") { compileProgramWithComments(Samples.get("structs/memberrefcount.vale")) }
  test("structs/mutstruct.vale") { compileProgramWithComments(Samples.get("structs/mutstruct.vale")) }
  test("structs/mutstructstore.vale") { compileProgramWithComments(Samples.get("structs/mutstructstore.vale")) }
  test("unreachablemoot.vale") { compileProgramWithComments(Samples.get("unreachablemoot.vale")) }
  test("unstackifyret.vale") { compileProgramWithComments(Samples.get("unstackifyret.vale")) }
  test("virtuals/imminterface.vale") { compileProgramWithComments(Samples.get("virtuals/imminterface.vale")) }
  test("virtuals/mutinterface.vale") { compileProgramWithComments(Samples.get("virtuals/mutinterface.vale")) }
  test("while/while.vale") { compileProgramWithComments(Samples.get("while/while.vale")) }

}
