package dev.vale.typing

import dev.vale.{PackageCoordinate, StrI, Tests, vassert, vassertSome}
import dev.vale.typing.ast.SignatureT
import dev.vale.typing.names.{CitizenNameT, CitizenTemplateNameT, FullNameT, FunctionNameT, LambdaCitizenNameT}
import dev.vale.typing.templata.CoordTemplata
import dev.vale.typing.types.{CoordT, ShareT}
import dev.vale.typing.names.CitizenTemplateNameT
import dev.vale.typing.types._
import org.scalatest.{FunSuite, Matchers}

import scala.collection.immutable.List

class CompilerProjectTests extends FunSuite with Matchers {

  test("Function has correct name") {
    val compile =
      CompilerTestCompilation.test(
        """
          |import v.builtins.tup.*;
          |exported func main() { }
          """.stripMargin)
    val coutputs = compile.expectCompilerOutputs()
    val interner = compile.interner

    val fullName = FullNameT(PackageCoordinate.TEST_TLD(interner, compile.keywords), Vector(), interner.intern(FunctionNameT(StrI("main"), Vector(), Vector())))
    vassertSome(coutputs.lookupFunction(SignatureT(fullName)))
  }

  test("Lambda has correct name") {
    val compile =
      CompilerTestCompilation.test(
        """
          |import v.builtins.tup.*;
          |exported func main() { {}() }
          """.stripMargin)
    val coutputs = compile.expectCompilerOutputs()

//    val fullName = FullName2(PackageCoordinate.TEST_TLD, Vector(), FunctionName2("lamb", Vector(), Vector()))

    val lamFunc = coutputs.lookupFunction("__call")
    lamFunc.header.fullName match {
      case FullNameT(
        x,
        Vector(FunctionNameT(StrI("main"),Vector(),Vector()), LambdaCitizenNameT(_)),
        FunctionNameT(StrI("__call"),Vector(),Vector(CoordT(ShareT,_)))) =>
        vassert(x.isTest)
    }
  }

  test("Struct has correct name") {
    val compile =
      CompilerTestCompilation.test(
        """
          |import v.builtins.tup.*;
          |exported struct MyStruct { a int; }
          |""".stripMargin)
    val coutputs = compile.expectCompilerOutputs()

    val struct = coutputs.lookupStruct("MyStruct")
    struct.fullName match {
      case FullNameT(x,Vector(),CitizenNameT(CitizenTemplateNameT(StrI("MyStruct")),Vector())) => {
        vassert(x.isTest)
      }
    }
  }
}
