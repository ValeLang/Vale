package dev.vale.parsing

import dev.vale.{Collector, Err, Interner, Ok, Tests, vfail}
import dev.vale.options.GlobalOptions
import org.scalatest.{FunSuite, Matchers}



class ParseSamplesTests extends FunSuite with Collector with TestParseUtils {
  def parse(path: String): Unit = {
    val interner = new Interner()
    val code = Tests.loadExpected(path)
    val compilation = ParserTestCompilation.test(interner, code)
    compilation.getParseds() match {
      case Ok(x) => x
      case Err(e) => vfail(ParseErrorHumanizer.humanize(path, code, e.error))
    }
  }

  test("optutils/optutils.vale") { parse("optutils/optutils.vale") }
  test("printutils/printutils.vale") { parse("printutils/printutils.vale") }
  test("ioutils/ioutils.vale") { parse("ioutils/ioutils.vale") }
  test("array/indices/indices.vale") { parse("array/indices/indices.vale") }
  test("array/iter/iter.vale") { parse("array/iter/iter.vale") }
  test("array/each/each.vale") { parse("array/each/each.vale") }
  test("array/drop_into/drop_into.vale") { parse("array/drop_into/drop_into.vale") }
  test("array/make/make.vale") { parse("array/make/make.vale") }
  test("array/has/has.vale") { parse("array/has/has.vale") }
  test("listprintutils/listprintutils.vale") { parse("listprintutils/listprintutils.vale") }
  test("logic/logic.vale") { parse("logic/logic.vale") }
  test("math/math.vale") { parse("math/math.vale") }
  test("hashmap/hashmap.vale") { parse("hashmap/hashmap.vale") }
  test("intrange/intrange.vale") { parse("intrange/intrange.vale") }
  test("programs/lambdas/doubleclosure.vale") { parse("programs/lambdas/doubleclosure.vale") }
  test("programs/lambdas/mutate.vale") { parse("programs/lambdas/mutate.vale") }
  test("programs/lambdas/lambda.vale") { parse("programs/lambdas/lambda.vale") }
  test("programs/lambdas/lambdamut.vale") { parse("programs/lambdas/lambdamut.vale") }
  test("programs/comparei64.vale") { parse("programs/comparei64.vale") }
  test("programs/unreachablemoot.vale") { parse("programs/unreachablemoot.vale") }
  test("programs/constraintRef.vale") { parse("programs/constraintRef.vale") }
  test("programs/add64ret.vale") { parse("programs/add64ret.vale") }
  test("programs/concatstrfloat.vale") { parse("programs/concatstrfloat.vale") }
  test("programs/strings/smallstr.vale") { parse("programs/strings/smallstr.vale") }
  test("programs/strings/complex/main.vale") { parse("programs/strings/complex/main.vale") }
  test("programs/strings/stradd.vale") { parse("programs/strings/stradd.vale") }
  test("programs/strings/strprint.vale") { parse("programs/strings/strprint.vale") }
  test("programs/strings/strneq.vale") { parse("programs/strings/strneq.vale") }
  test("programs/strings/i64tostr.vale") { parse("programs/strings/i64tostr.vale") }
  test("programs/strings/inttostr.vale") { parse("programs/strings/inttostr.vale") }
  test("programs/strings/strlen.vale") { parse("programs/strings/strlen.vale") }
  test("programs/externs/structimmparamextern/test.vale") { parse("programs/externs/structimmparamextern/test.vale") }
  test("programs/externs/structimmparamexport/test.vale") { parse("programs/externs/structimmparamexport/test.vale") }
  test("programs/externs/strreturnexport/test.vale") { parse("programs/externs/strreturnexport/test.vale") }
  test("programs/externs/voidreturnextern/test.vale") { parse("programs/externs/voidreturnextern/test.vale") }
  test("programs/externs/voidreturnexport/test.vale") { parse("programs/externs/voidreturnexport/test.vale") }
  test("programs/externs/structmutreturnexport/test.vale") { parse("programs/externs/structmutreturnexport/test.vale") }
  test("programs/externs/structmutparamexport/test.vale") { parse("programs/externs/structmutparamexport/test.vale") }
  test("programs/externs/structimmparamdeepextern/test.vale") { parse("programs/externs/structimmparamdeepextern/test.vale") }
  test("programs/externs/ssaimmparamdeepextern/test.vale") { parse("programs/externs/ssaimmparamdeepextern/test.vale") }
  test("programs/externs/rsaimmparamdeepexport/test.vale") { parse("programs/externs/rsaimmparamdeepexport/test.vale") }
  test("programs/externs/tupleparamextern/test.vale") { parse("programs/externs/tupleparamextern/test.vale") }
  test("programs/externs/ssamutreturnexport/test.vale") { parse("programs/externs/ssamutreturnexport/test.vale") }
  test("programs/externs/structimmparamdeepexport/test.vale") { parse("programs/externs/structimmparamdeepexport/test.vale") }
  test("programs/externs/ssaimmparamdeepexport/test.vale") { parse("programs/externs/ssaimmparamdeepexport/test.vale") }
  test("programs/externs/rsaimmparamdeepextern/test.vale") { parse("programs/externs/rsaimmparamdeepextern/test.vale") }
  test("programs/externs/rsaimmparamextern/test.vale") { parse("programs/externs/rsaimmparamextern/test.vale") }
  test("programs/externs/rsaimmreturnexport/test.vale") { parse("programs/externs/rsaimmreturnexport/test.vale") }
  test("programs/externs/export.vale") { parse("programs/externs/export.vale") }
  test("programs/externs/rsaimmparamexport/test.vale") { parse("programs/externs/rsaimmparamexport/test.vale") }
  test("programs/externs/rsaimmreturnextern/test.vale") { parse("programs/externs/rsaimmreturnextern/test.vale") }
  test("programs/externs/interfaceimmreturnexport/test.vale") { parse("programs/externs/interfaceimmreturnexport/test.vale") }
  test("programs/externs/interfacemutparamexport/test.vale") { parse("programs/externs/interfacemutparamexport/test.vale") }
  test("programs/externs/interfaceimmreturnextern/test.vale") { parse("programs/externs/interfaceimmreturnextern/test.vale") }
  test("programs/externs/strlenextern/test.vale") { parse("programs/externs/strlenextern/test.vale") }
  test("programs/externs/ssamutparamexport/test.vale") { parse("programs/externs/ssamutparamexport/test.vale") }
  test("programs/externs/rsamutreturnexport/test.vale") { parse("programs/externs/rsamutreturnexport/test.vale") }
  test("programs/externs/ssaimmreturnextern/test.vale") { parse("programs/externs/ssaimmreturnextern/test.vale") }
  test("programs/externs/interfaceimmparamdeepextern/test.vale") { parse("programs/externs/interfaceimmparamdeepextern/test.vale") }
  test("programs/externs/ssaimmreturnexport/test.vale") { parse("programs/externs/ssaimmreturnexport/test.vale") }
  test("programs/externs/rsamutparamexport/test.vale") { parse("programs/externs/rsamutparamexport/test.vale") }
  test("programs/externs/interfaceimmparamdeepexport/test.vale") { parse("programs/externs/interfaceimmparamdeepexport/test.vale") }
  test("programs/externs/extern.vale") { parse("programs/externs/extern.vale") }
  test("programs/externs/interfaceimmparamextern/test.vale") { parse("programs/externs/interfaceimmparamextern/test.vale") }
  test("programs/externs/tupleretextern/test.vale") { parse("programs/externs/tupleretextern/test.vale") }
  test("programs/externs/interfaceimmparamexport/test.vale") { parse("programs/externs/interfaceimmparamexport/test.vale") }
  test("programs/externs/structmutparamdeepexport/test.vale") { parse("programs/externs/structmutparamdeepexport/test.vale") }
  test("programs/externs/ssaimmparamextern/test.vale") { parse("programs/externs/ssaimmparamextern/test.vale") }
  test("programs/externs/interfacemutreturnexport/test.vale") { parse("programs/externs/interfacemutreturnexport/test.vale") }
  test("programs/externs/ssaimmparamexport/test.vale") { parse("programs/externs/ssaimmparamexport/test.vale") }
  test("programs/weaks/callWeakSelfMethodAfterDrop.vale") { parse("programs/weaks/callWeakSelfMethodAfterDrop.vale") }
  test("programs/weaks/callWeakSelfMethodWhileLive.vale") { parse("programs/weaks/callWeakSelfMethodWhileLive.vale") }
  test("programs/weaks/dropThenLockInterface.vale") { parse("programs/weaks/dropThenLockInterface.vale") }
  test("programs/weaks/lockWhileLiveInterface.vale") { parse("programs/weaks/lockWhileLiveInterface.vale") }
  test("programs/weaks/dropWhileLockedStruct.vale") { parse("programs/weaks/dropWhileLockedStruct.vale") }
  test("programs/weaks/weakFromCRefInterface.vale") { parse("programs/weaks/weakFromCRefInterface.vale") }
  test("programs/weaks/weakFromCRefStruct.vale") { parse("programs/weaks/weakFromCRefStruct.vale") }
  test("programs/weaks/lockWhileLiveStruct.vale") { parse("programs/weaks/lockWhileLiveStruct.vale") }
  test("programs/weaks/loadFromWeakable.vale") { parse("programs/weaks/loadFromWeakable.vale") }
  test("programs/weaks/dropWhileLockedInterface.vale") { parse("programs/weaks/dropWhileLockedInterface.vale") }
  test("programs/weaks/weakFromLocalCRefStruct.vale") { parse("programs/weaks/weakFromLocalCRefStruct.vale") }
  test("programs/weaks/weakFromLocalCRefInterface.vale") { parse("programs/weaks/weakFromLocalCRefInterface.vale") }
  test("programs/weaks/dropThenLockStruct.vale") { parse("programs/weaks/dropThenLockStruct.vale") }
  test("programs/readwriteufcs.vale") { parse("programs/readwriteufcs.vale") }
  test("programs/tuples/immtupleaccess.vale") { parse("programs/tuples/immtupleaccess.vale") }
  test("programs/downcast/downcastPointerFailed.vale") { parse("programs/downcast/downcastPointerFailed.vale") }
  test("programs/downcast/downcastPointerSuccess.vale") { parse("programs/downcast/downcastPointerSuccess.vale") }
  test("programs/downcast/downcastBorrowFailed.vale") { parse("programs/downcast/downcastBorrowFailed.vale") }
  test("programs/downcast/downcastOwningSuccessful.vale") { parse("programs/downcast/downcastOwningSuccessful.vale") }
  test("programs/downcast/downcastBorrowSuccessful.vale") { parse("programs/downcast/downcastBorrowSuccessful.vale") }
  test("programs/downcast/downcastOwningFailed.vale") { parse("programs/downcast/downcastOwningFailed.vale") }
  test("programs/if/ifnevers.vale") { parse("programs/if/ifnevers.vale") }
  test("programs/if/nestedif.vale") { parse("programs/if/nestedif.vale") }
  test("programs/if/if.vale") { parse("programs/if/if.vale") }
  test("programs/if/upcastif.vale") { parse("programs/if/upcastif.vale") }
  test("programs/if/neverif.vale") { parse("programs/if/neverif.vale") }
  test("programs/virtuals/callingThroughBorrow.vale") { parse("programs/virtuals/callingThroughBorrow.vale") }
  test("programs/virtuals/calling.vale") { parse("programs/virtuals/calling.vale") }
  test("programs/virtuals/round.vale") { parse("programs/virtuals/round.vale") }
  test("programs/virtuals/upcasting.vale") { parse("programs/virtuals/upcasting.vale") }
  test("programs/virtuals/interfaceimm.vale") { parse("programs/virtuals/interfaceimm.vale") }
  test("programs/virtuals/retUpcast.vale") { parse("programs/virtuals/retUpcast.vale") }
  test("programs/virtuals/ordinarylinkedlist.vale") { parse("programs/virtuals/ordinarylinkedlist.vale") }
  test("programs/virtuals/interfacemut.vale") { parse("programs/virtuals/interfacemut.vale") }
  test("programs/genericvirtuals/getOr.vale") { parse("programs/genericvirtuals/getOr.vale") }
  test("programs/genericvirtuals/templatedinterface.vale") { parse("programs/genericvirtuals/templatedinterface.vale") }
  test("programs/genericvirtuals/templatedlinkedlist.vale") { parse("programs/genericvirtuals/templatedlinkedlist.vale") }
  test("programs/genericvirtuals/stampMultipleAncestors.vale") { parse("programs/genericvirtuals/stampMultipleAncestors.vale") }
  test("programs/genericvirtuals/foreachlinkedlist.vale") { parse("programs/genericvirtuals/foreachlinkedlist.vale") }
  test("programs/genericvirtuals/mapFunc.vale") { parse("programs/genericvirtuals/mapFunc.vale") }
  test("programs/genericvirtuals/callingAbstract.vale") { parse("programs/genericvirtuals/callingAbstract.vale") }
  test("programs/genericvirtuals/templatedoption.vale") { parse("programs/genericvirtuals/templatedoption.vale") }
  test("programs/addret.vale") { parse("programs/addret.vale") }
  test("programs/nestedblocks.vale") { parse("programs/nestedblocks.vale") }
  test("programs/panicnot.vale") { parse("programs/panicnot.vale") }
  test("programs/floateq.vale") { parse("programs/floateq.vale") }
  test("programs/roguelike.vale") { parse("programs/roguelike.vale") }
  test("programs/truncate.vale") { parse("programs/truncate.vale") }
  test("programs/multiUnstackify.vale") { parse("programs/multiUnstackify.vale") }
  test("programs/borrowRef.vale") { parse("programs/borrowRef.vale") }
  test("programs/structs/constructor.vale") { parse("programs/structs/constructor.vale") }
  test("programs/structs/structmutstore.vale") { parse("programs/structs/structmutstore.vale") }
  test("programs/structs/getMember.vale") { parse("programs/structs/getMember.vale") }
  test("programs/structs/structs.vale") { parse("programs/structs/structs.vale") }
  test("programs/structs/mutate.vale") { parse("programs/structs/mutate.vale") }
  test("programs/structs/deadmutstruct.vale") { parse("programs/structs/deadmutstruct.vale") }
  test("programs/structs/structmutstoreinner.vale") { parse("programs/structs/structmutstoreinner.vale") }
  test("programs/structs/bigstructimm.vale") { parse("programs/structs/bigstructimm.vale") }
  test("programs/structs/structmut.vale") { parse("programs/structs/structmut.vale") }
  test("programs/structs/structimm.vale") { parse("programs/structs/structimm.vale") }
  test("programs/structs/memberrefcount.vale") { parse("programs/structs/memberrefcount.vale") }
  test("programs/arrays/ssaimmfromvalues.vale") { parse("programs/arrays/ssaimmfromvalues.vale") }
  test("programs/arrays/rsamutdestroyintocallable.vale") { parse("programs/arrays/rsamutdestroyintocallable.vale") }
  test("programs/arrays/ssamutfromcallable.vale") { parse("programs/arrays/ssamutfromcallable.vale") }
  test("programs/arrays/ssaimmfromcallable.vale") { parse("programs/arrays/ssaimmfromcallable.vale") }
  test("programs/arrays/rsamutlen.vale") { parse("programs/arrays/rsamutlen.vale") }
  test("programs/arrays/ssamutfromvalues.vale") { parse("programs/arrays/ssamutfromvalues.vale") }
  test("programs/arrays/rsaimmlen.vale") { parse("programs/arrays/rsaimmlen.vale") }
  test("programs/arrays/rsamut.vale") { parse("programs/arrays/rsamut.vale") }
  test("programs/arrays/swaprsamutdestroy.vale") { parse("programs/arrays/swaprsamutdestroy.vale") }
  test("programs/arrays/rsamutfromcallable.vale") { parse("programs/arrays/rsamutfromcallable.vale") }
  test("programs/arrays/rsaimm.vale") { parse("programs/arrays/rsaimm.vale") }
  test("programs/arrays/rsaimmfromcallable.vale") { parse("programs/arrays/rsaimmfromcallable.vale") }
  test("programs/arrays/rsamutcapacity.vale") { parse("programs/arrays/rsamutcapacity.vale") }
  test("programs/arrays/ssamutdestroyintocallable.vale") { parse("programs/arrays/ssamutdestroyintocallable.vale") }
  test("programs/arrays/inlssaimm.vale") { parse("programs/arrays/inlssaimm.vale") }
  test("programs/ufcs.vale") { parse("programs/ufcs.vale") }
  test("programs/functions/overloads.vale") { parse("programs/functions/overloads.vale") }
  test("programs/functions/recursion.vale") { parse("programs/functions/recursion.vale") }
  test("programs/printfloat.vale") { parse("programs/printfloat.vale") }
  test("programs/mutswaplocals.vale") { parse("programs/mutswaplocals.vale") }
  test("programs/mutlocal.vale") { parse("programs/mutlocal.vale") }
  test("programs/unstackifyret.vale") { parse("programs/unstackifyret.vale") }
  test("programs/while/while.vale") { parse("programs/while/while.vale") }
  test("programs/while/foreach.vale") { parse("programs/while/foreach.vale") }
  test("programs/invalidaccess.vale") { parse("programs/invalidaccess.vale") }
  test("programs/floatarithmetic.vale") { parse("programs/floatarithmetic.vale") }
  test("programs/panic.vale") { parse("programs/panic.vale") }
  test("list/list.vale") { parse("list/list.vale") }
  test("ifunction/ifunction1/ifunction1.vale") { parse("ifunction/ifunction1/ifunction1.vale") }
  test("string/string.vale") { parse("string/string.vale") }
  test("panicutils/panicutils.vale") { parse("panicutils/panicutils.vale") }
  test("castutils/castutils.vale") { parse("castutils/castutils.vale") }
}
