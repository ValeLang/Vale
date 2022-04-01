package dev.vale.templar

import dev.vale.templar.ast.AsSubtypeTE
import dev.vale.templar.names.{CitizenNameT, CitizenTemplateNameT, FullNameT}
import dev.vale.templar.templata.CoordTemplata
import dev.vale.templar.types.{BorrowT, CoordT, InterfaceTT, OwnT, StructTT}
import dev.vale.{Collector, vassert}
import dev.vale.templar.names.CitizenTemplateNameT
import dev.vale.templar.types.InterfaceTT
import dev.vale.Collector
import org.scalatest.{FunSuite, Matchers}

import scala.collection.immutable.Set

class TemplarVirtualTests extends FunSuite with Matchers {

  test("Implementing two interfaces causes no vdrop conflict") {
    // See NIIRII
    val compile = TemplarTestCompilation.test(
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
    val temputs = compile.expectTemputs()
  }

  test("Basic interface anonymous subclass") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |
        |interface Bork {
        |  func bork(virtual self &Bork) int;
        |}
        |
        |exported func main() int {
        |  f = Bork({ 7 });
        |  ret f.bork();
        |}
      """.stripMargin)
    val temputs = compile.expectTemputs()
  }

  test("Basic IFunction1 anonymous subclass") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |import ifunction.ifunction1.*;
        |
        |exported func main() int {
        |  f = IFunction1<mut, int, int>({_});
        |  ret (f)(7);
        |}
      """.stripMargin)
    val temputs = compile.expectTemputs()
  }

  test("Upcast") {
    val compile = TemplarTestCompilation.test(
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
    val temputs = compile.expectTemputs()
  }

  test("Downcast with as") {
    val compile = TemplarTestCompilation.test(
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
    val temputs = compile.expectTemputs()

    Collector.only(temputs.lookupFunction("as"), {
      case as @ AsSubtypeTE(sourceExpr, targetSubtype, resultOptType, okConstructor, errConstructor) => {
        sourceExpr.result.reference match {
          case CoordT(BorrowT,InterfaceTT(FullNameT(_, Vector(),CitizenNameT(CitizenTemplateNameT("IShip"),Vector())))) =>
        }
        targetSubtype match {
          case StructTT(FullNameT(_, Vector(),CitizenNameT(CitizenTemplateNameT("Raza"),Vector()))) =>
        }
        val (firstGenericArg, secondGenericArg) =
          resultOptType match {
            case CoordT(
              OwnT,
              InterfaceTT(
                FullNameT(
                  _, Vector(),
                  CitizenNameT(
                    CitizenTemplateNameT("Result"),
                    Vector(firstGenericArg, secondGenericArg))))) => (firstGenericArg, secondGenericArg)
          }
        // They should both be pointers, since we dont really do borrows in structs yet
        firstGenericArg match {
          case CoordTemplata(
            CoordT(
              BorrowT,
              StructTT(FullNameT(_, Vector(),CitizenNameT(CitizenTemplateNameT("Raza"),Vector()))))) =>
        }
        secondGenericArg match {
          case CoordTemplata(
            CoordT(
              BorrowT,
              InterfaceTT(FullNameT(_, Vector(),CitizenNameT(CitizenTemplateNameT("IShip"),Vector()))))) =>
        }
        vassert(okConstructor.paramTypes.head.kind == targetSubtype)
        vassert(errConstructor.paramTypes.head.kind == sourceExpr.result.reference.kind)
        as
      }
    })
  }

  test("Virtual with body") {
    TemplarTestCompilation.test(
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
