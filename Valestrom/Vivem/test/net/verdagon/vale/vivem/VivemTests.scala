package net.verdagon.vale.vivem

import net.verdagon.vale.metal._
import net.verdagon.vale.{PackageCoordinate, PackageCoordinateMap, metal => m}
import net.verdagon.von.{VonArray, VonInt, VonMember, VonObject, VonStr}
import org.scalatest.{FunSuite, Matchers}

class VivemTests extends FunSuite with Matchers {
  test("Return 7") {
    val main =
      FunctionH(
        PrototypeH(
          FullNameH(
            "main",
            0,
            PackageCoordinate.TEST_TLD,
            Vector(VonObject("F",None,Vector(VonMember("humanName",VonStr("main")), VonMember("templateArgs",VonArray(None,Vector())), VonMember("parameters",VonArray(None,Vector())))))),Vector.empty,ReferenceH(m.ShareH,InlineH,ReadonlyH,IntH.i32)),
        true,
        false,
        Vector(UserFunctionH),
        BlockH(ConstantIntH(7, 32)))
    val programH =
      ProgramH(
        PackageCoordinateMap(Map())
          .add(PackageCoordinate.TEST_TLD, PackageH(Vector.empty, Vector.empty, Vector(main), Vector.empty, Vector.empty, Map(), Map("main" -> main.prototype), Map(), Map(), Map())))
    val result =
      Vivem.executeWithPrimitiveArgs(programH, Vector(), System.out, Vivem.emptyStdin, Vivem.nullStdout)
    result shouldEqual VonInt(7)
  }

  test("Adding") {
    val intRef =
      VonObject("Ref",None,Vector(VonMember("ownership",VonObject("Share",None,Vector())), VonMember("location",VonObject("Inline",None,Vector())), VonMember("permission",VonObject("Readonly",None,Vector())), VonMember("kind",VonObject("Int",None,Vector(VonMember("bits", VonInt(32)))))))

    val addPrototype =
      PrototypeH(
        FullNameH(
          "__vbi_addI32",
          0,
          PackageCoordinate.BUILTIN,
          Vector(VonObject("F",None,Vector(VonMember("humanName",VonStr("__vbi_addI32")), VonMember("templateArgs",VonArray(None,Vector())), VonMember("parameters",VonArray(None,Vector(intRef, intRef))))))),
        Vector(ReferenceH(ShareH,InlineH,ReadonlyH,IntH.i32), ReferenceH(ShareH,InlineH,ReadonlyH,IntH.i32)),
        ReferenceH(ShareH,InlineH,ReadonlyH,IntH.i32))
    val main =
      FunctionH(
        PrototypeH(
          FullNameH(
            "main",
            0,
            PackageCoordinate.TEST_TLD,
            Vector(VonObject("F",None,Vector(VonMember("humanName",VonStr("main")), VonMember("templateArgs",VonArray(None,Vector())), VonMember("parameters",VonArray(None,Vector())))))),Vector.empty,ReferenceH(m.ShareH,InlineH,ReadonlyH,IntH.i32)),
        true,
        false,
        Vector(UserFunctionH),
        BlockH(
          CallH(
            addPrototype,
            Vector(
              ConstantIntH(52, 32),
              CallH(
                addPrototype,
                Vector(
                  ConstantIntH(53, 32),
                  ConstantIntH(54, 32)))))))
    val addExtern =
      FunctionH(
        addPrototype,
        false,
        true,
        Vector.empty,
        BlockH(ConstantIntH(133337, 32)))
    val programH =
      ProgramH(
        PackageCoordinateMap(Map())
          .add(PackageCoordinate.BUILTIN, PackageH(Vector.empty, Vector.empty, Vector(addExtern), Vector.empty, Vector.empty, Map(), Map(), Map(), Map("__vbi_addI32" -> addPrototype), Map()))
          .add(PackageCoordinate.TEST_TLD, PackageH(Vector.empty, Vector.empty, Vector(main), Vector.empty, Vector.empty, Map(), Map("main" -> main.prototype), Map(), Map(), Map())))
    val result =
      Vivem.executeWithPrimitiveArgs(programH, Vector(), System.out, Vivem.emptyStdin, Vivem.nullStdout)
    result shouldEqual VonInt(159)
  }
}
