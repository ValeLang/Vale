package net.verdagon.vale.templar

import net.verdagon.vale.templar.templata.CoordTemplata
import net.verdagon.vale.templar.types.{ConstraintT, CoordT, InterfaceTT, OwnT, ReadonlyT, ReadwriteT, StructTT}
import net.verdagon.vale.{vassert, vimpl}
import org.scalatest.{FunSuite, Matchers}

import scala.collection.immutable.Set

class TemplarVirtualTests extends FunSuite with Matchers {

  test("Downcast with as") {
    val compile = TemplarTestCompilation.test(
      """
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

    temputs.lookupFunction("as").only({
      case as @ AsSubtypeTE(sourceExpr, targetSubtype, resultOptType, okConstructor, errConstructor) => {
        sourceExpr.resultRegister.reference match {
          case CoordT(ConstraintT,ReadonlyT,InterfaceTT(FullNameT(_, Vector(),CitizenNameT("IShip",Vector())))) =>
        }
        targetSubtype match {
          case StructTT(FullNameT(_, Vector(),CitizenNameT("Raza",Vector()))) =>
        }
        val (firstGenericArg, secondGenericArg) =
          resultOptType match {
            case CoordT(
              OwnT,ReadwriteT,
              InterfaceTT(
                FullNameT(
                  _, Vector(),
                  CitizenNameT(
                    "Result",
                    Vector(firstGenericArg, secondGenericArg))))) => (firstGenericArg, secondGenericArg)
          }
        firstGenericArg match {
          case CoordTemplata(
            CoordT(
              ConstraintT,ReadonlyT,
              StructTT(FullNameT(_, Vector(),CitizenNameT("Raza",Vector()))))) =>
        }
        secondGenericArg match {
          case CoordTemplata(
            CoordT(
              ConstraintT,ReadonlyT,
              InterfaceTT(FullNameT(_, Vector(),CitizenNameT("IShip",Vector()))))) =>
        }
        vassert(okConstructor.paramTypes.head.kind == targetSubtype)
        vassert(errConstructor.paramTypes.head == sourceExpr.resultRegister.reference)
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
        |fn rebork(virtual result &IBork) bool { true }
        |fn main() export {
        |  rebork(&Bork());
        |}
        |""".stripMargin)
  }

}
