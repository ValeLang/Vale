package net.verdagon.vale.templar

import net.verdagon.vale.templar.templata.{CoordTemplata, SignatureT}
import net.verdagon.vale.templar.types._
import net.verdagon.vale.{Builtins, FileCoordinateMap, PackageCoordinate, Tests, vassert, vassertSome, vimpl}
import org.scalatest.{FunSuite, Matchers}

import scala.collection.immutable.List

class TemplarProjectTests extends FunSuite with Matchers {

  test("Function has correct name") {
    val compile =
      TemplarTestCompilation.test("fn main() export { }")
    val temputs = compile.expectTemputs()

    val fullName = FullNameT(PackageCoordinate.TEST_TLD, Nil, FunctionNameT("main", Nil, Nil))
    vassertSome(temputs.lookupFunction(SignatureT(fullName)))
  }

  test("Lambda has correct name") {
    val compile =
      TemplarTestCompilation.test("fn main() export { {}!() }")
    val temputs = compile.expectTemputs()

//    val fullName = FullName2(PackageCoordinate.TEST_TLD, Nil, FunctionName2("lamb", Nil, Nil))

    val lamFunc = temputs.lookupFunction("__call")
    lamFunc.header.fullName match {
      case FullNameT(
        PackageCoordinate.TEST_TLD,
        List(FunctionNameT("main",Nil,Nil), LambdaCitizenNameT(_)),
        FunctionNameT("__call",Nil,List(CoordT(ShareT,ReadonlyT,_)))) =>
    }
  }

  test("Struct has correct name") {
    val compile =
      TemplarTestCompilation.test("struct MyStruct export { a int; }")
    val temputs = compile.expectTemputs()

    val struct = temputs.lookupStruct("MyStruct")
    struct.fullName match {
      case FullNameT(PackageCoordinate.TEST_TLD,Nil,CitizenNameT("MyStruct",Nil)) =>
    }
  }
}
