package dev.vale.typing

import dev.vale.typing.ast.AsSubtypeTE
import dev.vale.typing.names.{CitizenNameT, CitizenTemplateNameT, FullNameT}
import dev.vale.typing.templata.CoordTemplata
import dev.vale.typing.types.{BorrowT, CoordT, InterfaceTT, OwnT, StructTT}
import dev.vale.{Collector, StrI, vassert}
import dev.vale.typing.names.CitizenTemplateNameT
import dev.vale.typing.types.InterfaceTT
import org.scalatest.{FunSuite, Matchers}

import scala.collection.immutable.Set

class CompilerVirtualTests extends FunSuite with Matchers {

  test("Implementing two interfaces causes no vdrop conflict") {
    // See NIIRII
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |
        |struct MyStruct {}
        |
        |interface IA {}
        |impl IA for MyStruct;
        |
        |interface IB {}
        |impl IB for MyStruct;
        |
        |func bork(a IA) {}
        |func zork(b IB) {}
        |exported func main() {
        |  bork(MyStruct());
        |  zork(MyStruct());
        |}
      """.stripMargin)
    val coutputs = compile.expectCompilerOutputs()
  }

  test("Basic interface anonymous subclass") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |
        |interface Bork {
        |  func bork(virtual self &Bork) int;
        |}
        |
        |exported func main() int {
        |  f = Bork({ 7 });
        |  return f.bork();
        |}
      """.stripMargin)
    val coutputs = compile.expectCompilerOutputs()
  }

  test("Basic IFunction1 anonymous subclass") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |import ifunction.ifunction1.*;
        |
        |exported func main() int {
        |  f = IFunction1<mut, int, int>({_});
        |  return (f)(7);
        |}
      """.stripMargin)
    val coutputs = compile.expectCompilerOutputs()
  }

  test("Upcast") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |interface IShip {}
        |struct Raza { fuel int; }
        |impl IShip for Raza;
        |
        |exported func main() {
        |  ship IShip = Raza(42);
        |}
        |""".stripMargin)
    val coutputs = compile.expectCompilerOutputs()
  }

  test("Downcast with as") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |import v.builtins.as.*;
        |import v.builtins.drop.*;
        |
        |interface IShip {}
        |
        |struct Raza { fuel int; }
        |impl IShip for Raza;
        |
        |exported func main() {
        |  ship IShip = Raza(42);
        |  ship.as<Raza>();
        |}
        |""".stripMargin)
    val coutputs = compile.expectCompilerOutputs()

    Collector.only(coutputs.lookupFunction("as"), {
      case as @ AsSubtypeTE(sourceExpr, targetSubtype, resultOptType, okConstructor, errConstructor) => {
        sourceExpr.result.reference match {
          case CoordT(BorrowT,InterfaceTT(FullNameT(_, Vector(),CitizenNameT(CitizenTemplateNameT(StrI("IShip")),Vector())))) =>
        }
        targetSubtype match {
          case StructTT(FullNameT(_, Vector(),CitizenNameT(CitizenTemplateNameT(StrI("Raza")),Vector()))) =>
        }
        val (firstGenericArg, secondGenericArg) =
          resultOptType match {
            case CoordT(
              OwnT,
              InterfaceTT(
                FullNameT(
                  _, Vector(),
                  CitizenNameT(
                    CitizenTemplateNameT(StrI("Result")),
                    Vector(firstGenericArg, secondGenericArg))))) => (firstGenericArg, secondGenericArg)
          }
        // They should both be pointers, since we dont really do borrows in structs yet
        firstGenericArg match {
          case CoordTemplata(
            CoordT(
              BorrowT,
              StructTT(FullNameT(_, Vector(),CitizenNameT(CitizenTemplateNameT(StrI("Raza")),Vector()))))) =>
        }
        secondGenericArg match {
          case CoordTemplata(
            CoordT(
              BorrowT,
              InterfaceTT(FullNameT(_, Vector(),CitizenNameT(CitizenTemplateNameT(StrI("IShip")),Vector()))))) =>
        }
        vassert(okConstructor.paramTypes.head.kind == targetSubtype)
        vassert(errConstructor.paramTypes.head.kind == sourceExpr.result.reference.kind)
        as
      }
    })
  }

  test("Virtual with body") {
    CompilerTestCompilation.test(
      """
        |interface IBork { }
        |struct Bork { }
        |impl IBork for Bork;
        |
        |func rebork(virtual result *IBork) bool { true }
        |exported func main() {
        |  rebork(&Bork());
        |}
        |""".stripMargin)
  }

}
