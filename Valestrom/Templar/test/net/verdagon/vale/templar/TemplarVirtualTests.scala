package net.verdagon.vale.templar

import net.verdagon.vale.templar.ast.AsSubtypeTE
import net.verdagon.vale.templar.names.{CitizenNameT, CitizenTemplateNameT, FullNameT}
import net.verdagon.vale.templar.templata.CoordTemplata
import net.verdagon.vale.templar.types.{ConstraintT, CoordT, InterfaceTT, OwnT, ReadonlyT, ReadwriteT, StructTT}
import net.verdagon.vale.{Collector, vassert, vimpl}
import org.scalatest.{FunSuite, Matchers}

import scala.collection.immutable.Set

class TemplarVirtualTests extends FunSuite with Matchers {

  test("Basic interface anonymous subclass") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |
        |interface Bork {
        |  fn bork(virtual self *Bork) int;
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
          case CoordT(ConstraintT,ReadonlyT,InterfaceTT(FullNameT(_, Vector(),CitizenNameT(CitizenTemplateNameT("IShip"),Vector())))) =>
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
        firstGenericArg match {
          case CoordTemplata(
            CoordT(
              ConstraintT,ReadonlyT,
              StructTT(FullNameT(_, Vector(),CitizenNameT(CitizenTemplateNameT("Raza"),Vector()))))) =>
        }
        secondGenericArg match {
          case CoordTemplata(
            CoordT(
              ConstraintT,ReadonlyT,
              InterfaceTT(FullNameT(_, Vector(),CitizenNameT(CitizenTemplateNameT("IShip"),Vector()))))) =>
        }
        vassert(okConstructor.paramTypes.head.kind == targetSubtype)
        vassert(errConstructor.paramTypes.head == sourceExpr.result.reference)
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
