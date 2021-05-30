package net.verdagon.vale.templar

import net.verdagon.vale.templar.templata.{CoordTemplata, Signature2}
import net.verdagon.vale.templar.types._
import net.verdagon.vale.{Builtins, FileCoordinateMap, PackageCoordinate, Tests, vassert, vimpl}
import org.scalatest.{FunSuite, Matchers}

import scala.collection.immutable.List

class TemplarProjectTests extends FunSuite with Matchers {

  test("Function has correct name") {
    val compile =
      TemplarTestCompilation.test("fn main() export { }")
    val temputs = compile.expectTemputs()

    val fullName = FullName2(PackageCoordinate.TEST_TLD, List(), FunctionName2("main", List(), List()))
    temputs.lookupFunction(Signature2(fullName))
  }

  test("Lambda has correct name") {
    val compile =
      TemplarTestCompilation.test("fn main() export { {}!() }")
    val temputs = compile.expectTemputs()

    val fullName = FullName2(PackageCoordinate.TEST_TLD, List(), FunctionName2("lamb", List(), List()))
    temputs.lookupFunction(Signature2(fullName))
  }

  test("Struct has correct name") {
    val compile =
      TemplarTestCompilation.test("struct MyStruct export { a int; }")
    val temputs = compile.expectTemputs()

    temputs.lookupStruct(StructRef2(FullName2(PackageCoordinate.TEST_TLD, List(), CitizenName2("MyStruct", List()))))
  }

  test("Virtual with body") {
    val compile =
      new TemplarCompilation(
        List(PackageCoordinate.BUILTIN, PackageCoordinate("moduleA", List()), PackageCoordinate("moduleB", List())),
        Builtins.getCodeMap()
          .or(
            FileCoordinateMap(Map())
              .add("moduleA", List(), "MyStruct.vale", "struct MyStruct export { a int; }")
              .add("moduleB", List(), "MyStruct.vale", "struct MyStruct export { b int; }"))
          .or(Tests.getPackageToResourceResolver),
        TemplarCompilationOptions())
    val temputs = compile.getTemputs()

    vimpl()
//    val fullNameA =
//      temputs.moduleNameToExportedNameToExportee("moduleA")("MyStruct") match {
//        case (PackageCoordinate("moduleA", List()), fullName) => fullName
//      }
//
//    val fullNameB =
//      hamuts.moduleNameToExportedNameToExportee("moduleB")("MyStruct") match {
//        case (PackageCoordinate("moduleB", List()), fullName) => fullName
//      }
//
//    vassert(fullNameA != fullNameB)
  }

}
