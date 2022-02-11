package net.verdagon.vale.templar

import net.verdagon.vale.templar.ast.SignatureT
import net.verdagon.vale.templar.names.{CitizenNameT, CitizenTemplateNameT, FullNameT, FunctionNameT, LambdaCitizenNameT}
import net.verdagon.vale.templar.templata.CoordTemplata
import net.verdagon.vale.templar.types._
import net.verdagon.vale.{Builtins, FileCoordinateMap, PackageCoordinate, Tests, vassert, vassertSome, vimpl}
import org.scalatest.{FunSuite, Matchers}

import scala.collection.immutable.List

class TemplarProjectTests extends FunSuite with Matchers {

  test("Function has correct name") {
    val compile =
      TemplarTestCompilation.test(
        """
          |import v.builtins.tup.*;
          |exported func main() { }
          """.stripMargin)
    val temputs = compile.expectTemputs()

    val fullName = FullNameT(PackageCoordinate.TEST_TLD, Vector(), FunctionNameT("main", Vector(), Vector()))
    vassertSome(temputs.lookupFunction(SignatureT(fullName)))
  }

  test("Lambda has correct name") {
    val compile =
      TemplarTestCompilation.test(
        """
          |import v.builtins.tup.*;
          |exported func main() { {}!() }
          """.stripMargin)
    val temputs = compile.expectTemputs()

//    val fullName = FullName2(PackageCoordinate.TEST_TLD, Vector(), FunctionName2("lamb", Vector(), Vector()))

    val lamFunc = temputs.lookupFunction("__call")
    lamFunc.header.fullName match {
      case FullNameT(
        PackageCoordinate.TEST_TLD,
        Vector(FunctionNameT("main",Vector(),Vector()), LambdaCitizenNameT(_)),
        FunctionNameT("__call",Vector(),Vector(CoordT(ShareT,ReadonlyT,_)))) =>
    }
  }

  test("Struct has correct name") {
    val compile =
      TemplarTestCompilation.test(
        """
          |import v.builtins.tup.*;
          |exported struct MyStruct { a int; }
          |""".stripMargin)
    val temputs = compile.expectTemputs()

    val struct = temputs.lookupStruct("MyStruct")
    struct.fullName match {
      case FullNameT(PackageCoordinate.TEST_TLD,Vector(),CitizenNameT(CitizenTemplateNameT("MyStruct"),Vector())) =>
    }
  }
}
