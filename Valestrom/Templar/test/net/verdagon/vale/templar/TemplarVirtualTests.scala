package net.verdagon.vale.templar

import net.verdagon.vale.templar.templata.CoordTemplata
import net.verdagon.vale.templar.types.{Constraint, Coord, InterfaceRef2, Own, Readonly, Readwrite, StructRef2}
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
      case as @ AsSubtype2(sourceExpr, targetSubtype, resultOptType, okConstructor, errConstructor) => {
        sourceExpr.resultRegister.reference match {
          case Coord(Constraint,Readonly,InterfaceRef2(FullName2(_, List(),CitizenName2("IShip",List())))) =>
        }
        targetSubtype match {
          case StructRef2(FullName2(_, List(),CitizenName2("Raza",List()))) =>
        }
        val (firstGenericArg, secondGenericArg) =
          resultOptType match {
            case Coord(
              Own,Readwrite,
              InterfaceRef2(
                FullName2(
                  _, List(),
                  CitizenName2(
                    "Result",
                    List(firstGenericArg, secondGenericArg))))) => (firstGenericArg, secondGenericArg)
          }
        firstGenericArg match {
          case CoordTemplata(
            Coord(
              Constraint,Readonly,
              StructRef2(FullName2(_, List(),CitizenName2("Raza",List()))))) =>
        }
        secondGenericArg match {
          case CoordTemplata(
            Coord(
              Constraint,Readonly,
              InterfaceRef2(FullName2(_, List(),CitizenName2("IShip",List()))))) =>
        }
        vassert(okConstructor.paramTypes.head.referend == targetSubtype)
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
        |fn main() {
        |  rebork(&Bork());
        |}
        |""".stripMargin)
  }

}
