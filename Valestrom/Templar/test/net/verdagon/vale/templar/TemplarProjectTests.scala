package net.verdagon.vale.templar

import net.verdagon.vale.templar.templata.{CoordTemplata, Signature2}
import net.verdagon.vale.templar.types._
import net.verdagon.vale.{Builtins, FileCoordinateMap, PackageCoordinate, Tests, vassert, vassertSome, vimpl}
import org.scalatest.{FunSuite, Matchers}

import scala.collection.immutable.List

class TemplarProjectTests extends FunSuite with Matchers {

  test("Function has correct name") {
    val compile =
      TemplarTestCompilation.test("fn main() export { }")
    val temputs = compile.expectTemputs()

    val fullName = FullName2(PackageCoordinate.TEST_TLD, List(), FunctionName2("main", List(), List()))
    vassertSome(temputs.lookupFunction(Signature2(fullName)))
  }

  test("Lambda has correct name") {
    val compile =
      TemplarTestCompilation.test("fn main() export { {}!() }")
    val temputs = compile.expectTemputs()

//    val fullName = FullName2(PackageCoordinate.TEST_TLD, List(), FunctionName2("lamb", List(), List()))

    val lamFunc = temputs.lookupFunction("__call")
    lamFunc.header.fullName match {
      case FullName2(
        PackageCoordinate.TEST_TLD,
        List(FunctionName2("main",List(),List()), LambdaCitizenName2(_)),
        FunctionName2("__call",List(),List(Coord(Share,Readonly,_)))) =>
    }
  }

  test("Struct has correct name") {
    val compile =
      TemplarTestCompilation.test("struct MyStruct export { a int; }")
    val temputs = compile.expectTemputs()

    val struct = temputs.lookupStruct("MyStruct")
    struct.fullName match {
      case FullName2(PackageCoordinate.TEST_TLD,List(),CitizenName2("MyStruct",List())) =>
    }
  }
}
