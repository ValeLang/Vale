package net.verdagon.vale.templar

import net.verdagon.vale.templar.ast.AsSubtypeTE
import net.verdagon.vale.templar.names.{CitizenNameT, CitizenTemplateNameT, FullNameT}
import net.verdagon.vale.templar.templata.CoordTemplata
import net.verdagon.vale.templar.types.{BorrowT, CoordT, InterfaceTT, OwnT, PointerT, ReadonlyT, ReadwriteT, StructTT}
import net.verdagon.vale.{Collector, vassert, vimpl}
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
        |fn bork(a IA) {}
        |fn zork(b IB) {}
        |fn main() export {
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
        |  fn bork(virtual self &Bork) int;
        |}
        |
        |fn main() int export {
        |  f = Bork({ 7 });
        |  = f.bork();
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
        |fn main() int export {
        |  f = IFunction1<mut, int, int>({_});
        |  = (f)!(7);
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
        |fn main() export {
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
        |
        |interface IShip {}
        |
        |struct Raza { fuel int; }
        |impl IShip for Raza;
        |
        |fn main() export {
        |  ship IShip = Raza(42);
        |  ship.as<Raza>();
        |}
        |""".stripMargin)
    val temputs = compile.expectTemputs()

    Collector.only(temputs.lookupFunction("as"), {
      case as @ AsSubtypeTE(sourceExpr, targetSubtype, resultOptType, okConstructor, errConstructor) => {
        sourceExpr.result.reference match {
          case CoordT(BorrowT,ReadonlyT,InterfaceTT(FullNameT(_, Vector(),CitizenNameT(CitizenTemplateNameT("IShip"),Vector())))) =>
        }
        targetSubtype match {
          case StructTT(FullNameT(_, Vector(),CitizenNameT(CitizenTemplateNameT("Raza"),Vector()))) =>
        }
        val (firstGenericArg, secondGenericArg) =
          resultOptType match {
            case CoordT(
              OwnT,ReadwriteT,
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
              PointerT,ReadonlyT,
              StructTT(FullNameT(_, Vector(),CitizenNameT(CitizenTemplateNameT("Raza"),Vector()))))) =>
        }
        secondGenericArg match {
          case CoordTemplata(
            CoordT(
              PointerT,ReadonlyT,
              InterfaceTT(FullNameT(_, Vector(),CitizenNameT(CitizenTemplateNameT("IShip"),Vector()))))) =>
        }
        vassert(okConstructor.paramTypes.head.kind == targetSubtype)
        vassert(errConstructor.paramTypes.head.permission == sourceExpr.result.reference.permission)
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
        |fn rebork(virtual result *IBork) bool { true }
        |fn main() export {
        |  rebork(*Bork());
        |}
        |""".stripMargin)
  }

}
