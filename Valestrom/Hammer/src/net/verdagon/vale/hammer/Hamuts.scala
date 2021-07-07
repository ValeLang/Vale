package net.verdagon.vale.hammer

import net.verdagon.vale.metal._
import net.verdagon.vale.templar.{FullNameT, INameT}
import net.verdagon.vale.templar.templata.PrototypeT
import net.verdagon.vale.templar.types.{InterfaceRefT, PackTT, StructRefT}
import net.verdagon.vale.{PackageCoordinate, vassert, vfail, vimpl}
import net.verdagon.von.IVonData


case class HamutsBox(var inner: Hamuts) {

  def packageCoordToExportNameToFunction: Map[PackageCoordinate, Map[String, PrototypeH]] = inner.packageCoordToExportNameToFunction
  def packageCoordToExportNameToKind: Map[PackageCoordinate, Map[String, KindH]] = inner.packageCoordToExportNameToKind
  def packageCoordToExternNameToFunction: Map[PackageCoordinate, Map[String, PrototypeH]] = inner.packageCoordToExternNameToFunction
  def packageCoordToExternNameToKind: Map[PackageCoordinate, Map[String, KindH]] = inner.packageCoordToExternNameToKind
  def structRefsByRef2: Map[StructRefT, StructRefH] = inner.structRefsByRef2
  def structDefsByRef2: Map[StructRefT, StructDefinitionH] = inner.structDefsByRef2
  def structDefs: List[StructDefinitionH] = inner.structDefs
  def interfaceRefs: Map[InterfaceRefT, InterfaceRefH] = inner.interfaceRefs
  def interfaceDefs: Map[InterfaceRefT, InterfaceDefinitionH] = inner.interfaceDefs
  def functionRefs: Map[PrototypeT, FunctionRefH] = inner.functionRefs
  def functionDefs: Map[PrototypeT, FunctionH] = inner.functionDefs
  def staticSizedArrays: List[StaticSizedArrayDefinitionTH] = inner.staticSizedArrays
  def runtimeSizedArrays: List[RuntimeSizedArrayDefinitionTH] = inner.runtimeSizedArrays

  def forwardDeclareStruct(structRefT: StructRefT, structRefH: StructRefH): Unit = {
    inner = inner.forwardDeclareStruct(structRefT, structRefH)
  }

  def addStructOriginatingFromTemplar(structRefT: StructRefT, structDefH: StructDefinitionH): Unit = {
    inner = inner.addStructOriginatingFromTemplar(structRefT, structDefH)
  }

  def addStructOriginatingFromHammer(structDefH: StructDefinitionH): Unit = {
    inner = inner.addStructOriginatingFromHammer(structDefH)
  }

  def forwardDeclareInterface(interfaceRef2: InterfaceRefT, interfaceRefH: InterfaceRefH): Unit = {
    inner = inner.forwardDeclareInterface(interfaceRef2, interfaceRefH)
  }

  def addInterface(interfaceRef2: InterfaceRefT, interfaceDefH: InterfaceDefinitionH): Unit = {
    inner = inner.addInterface(interfaceRef2, interfaceDefH)
  }

  def addStaticSizedArray(staticSizedArrayDefinitionTH: StaticSizedArrayDefinitionTH): Unit = {
    inner = inner.addStaticSizedArray(staticSizedArrayDefinitionTH)
  }

  def addRuntimeSizedArray(runtimeSizedArrayDefinitionTH: RuntimeSizedArrayDefinitionTH): Unit = {
    inner = inner.addRuntimeSizedArray(runtimeSizedArrayDefinitionTH)
  }

  def forwardDeclareFunction(functionRef2: PrototypeT, functionRefH: FunctionRefH): Unit = {
    inner = inner.forwardDeclareFunction(functionRef2, functionRefH)
  }

  def addFunction(functionRef2: PrototypeT, functionDefH: FunctionH): Unit = {
    inner = inner.addFunction(functionRef2, functionDefH)
  }

  def addKindExport(kind: KindH, packageCoordinate: PackageCoordinate, exportedName: String): Unit = {
    inner = inner.addKindExport(kind, packageCoordinate, exportedName)
  }

  def addKindExtern(kind: KindH, packageCoordinate: PackageCoordinate, exportedName: String): Unit = {
    inner = inner.addKindExtern(kind, packageCoordinate, exportedName)
  }

  def addFunctionExport(prototype: PrototypeH, packageCoordinate: PackageCoordinate, exportedName: String): Unit = {
    inner = inner.addFunctionExport(prototype, packageCoordinate, exportedName)
  }

  def addFunctionExtern(prototype: PrototypeH, packageCoordinate: PackageCoordinate, exportedName: String): Unit = {
    inner = inner.addFunctionExtern(prototype, packageCoordinate, exportedName)
  }

  def getNameId(readableName: String, packageCoordinate: PackageCoordinate, parts: List[IVonData]): Int = {
    val (newInner, id) = inner.getNameId(readableName, packageCoordinate, parts)
    inner = newInner
    id
  }

  def getStaticSizedArray(staticSizedArrayTH: StaticSizedArrayTH): StaticSizedArrayDefinitionTH = {
    inner.getStaticSizedArray(staticSizedArrayTH)
  }
  def getRuntimeSizedArray(runtimeSizedArrayTH: RuntimeSizedArrayTH): RuntimeSizedArrayDefinitionTH = {
    inner.getRuntimeSizedArray(runtimeSizedArrayTH)
  }
}

case class Hamuts(
    idByFullNameByHumanName: Map[String, Map[String, Int]],
    structRefsByRef2: Map[StructRefT, StructRefH],
    structDefsByRef2: Map[StructRefT, StructDefinitionH],
    structDefs: List[StructDefinitionH],
    staticSizedArrays: List[StaticSizedArrayDefinitionTH],
    runtimeSizedArrays: List[RuntimeSizedArrayDefinitionTH],
    interfaceRefs: Map[InterfaceRefT, InterfaceRefH],
    interfaceDefs: Map[InterfaceRefT, InterfaceDefinitionH],
    functionRefs: Map[PrototypeT, FunctionRefH],
    functionDefs: Map[PrototypeT, FunctionH],
    packageCoordToExportNameToFunction: Map[PackageCoordinate, Map[String, PrototypeH]],
    packageCoordToExportNameToKind: Map[PackageCoordinate, Map[String, KindH]],
    packageCoordToExternNameToFunction: Map[PackageCoordinate, Map[String, PrototypeH]],
    packageCoordToExternNameToKind: Map[PackageCoordinate, Map[String, KindH]]) {
  def forwardDeclareStruct(structRefT: StructRefT, structRefH: StructRefH): Hamuts = {
    Hamuts(
      idByFullNameByHumanName,
      structRefsByRef2 + (structRefT -> structRefH),
      structDefsByRef2,
      structDefs,
      staticSizedArrays,
      runtimeSizedArrays,
      interfaceRefs,
      interfaceDefs,
      functionRefs,
      functionDefs,
      packageCoordToExportNameToFunction,
      packageCoordToExportNameToKind,
      packageCoordToExternNameToFunction,
      packageCoordToExternNameToKind)
  }

  def addStructOriginatingFromTemplar(structRefT: StructRefT, structDefH: StructDefinitionH): Hamuts = {
    vassert(structRefsByRef2.contains(structRefT))
    Hamuts(
      idByFullNameByHumanName,
      structRefsByRef2,
      structDefsByRef2 + (structRefT -> structDefH),
      structDefs :+ structDefH,
      staticSizedArrays,
      runtimeSizedArrays,
      interfaceRefs,
      interfaceDefs,
      functionRefs,
      functionDefs,
      packageCoordToExportNameToFunction,
      packageCoordToExportNameToKind,
      packageCoordToExternNameToFunction,
      packageCoordToExternNameToKind)
  }

  def addStructOriginatingFromHammer(structDefH: StructDefinitionH): Hamuts = {
    Hamuts(
      idByFullNameByHumanName,
      structRefsByRef2,
      structDefsByRef2,
      structDefs :+ structDefH,
      staticSizedArrays,
      runtimeSizedArrays,
      interfaceRefs,
      interfaceDefs,
      functionRefs,
      functionDefs,
      packageCoordToExportNameToFunction,
      packageCoordToExportNameToKind,
      packageCoordToExternNameToFunction,
      packageCoordToExternNameToKind)
  }

  def forwardDeclareInterface(interfaceRef2: InterfaceRefT, interfaceRefH: InterfaceRefH): Hamuts = {
    Hamuts(
      idByFullNameByHumanName,
      structRefsByRef2,
      structDefsByRef2,
      structDefs,
      staticSizedArrays,
      runtimeSizedArrays,
      interfaceRefs + (interfaceRef2 -> interfaceRefH),
      interfaceDefs,
      functionRefs,
      functionDefs,
      packageCoordToExportNameToFunction,
      packageCoordToExportNameToKind,
      packageCoordToExternNameToFunction,
      packageCoordToExternNameToKind)
  }

  def addInterface(interfaceRef2: InterfaceRefT, interfaceDefH: InterfaceDefinitionH): Hamuts = {
    vassert(interfaceRefs.contains(interfaceRef2))
    Hamuts(
      idByFullNameByHumanName,
      structRefsByRef2,
      structDefsByRef2,
      structDefs,
      staticSizedArrays,
      runtimeSizedArrays,
      interfaceRefs,
      interfaceDefs + (interfaceRef2 -> interfaceDefH),
      functionRefs,
      functionDefs,
      packageCoordToExportNameToFunction,
      packageCoordToExportNameToKind,
      packageCoordToExternNameToFunction,
      packageCoordToExternNameToKind)
  }

  def forwardDeclareFunction(functionRef2: PrototypeT, functionRefH: FunctionRefH): Hamuts = {
    Hamuts(
      idByFullNameByHumanName,
      structRefsByRef2,
      structDefsByRef2,
      structDefs,
      staticSizedArrays,
      runtimeSizedArrays,
      interfaceRefs,
      interfaceDefs,
      functionRefs + (functionRef2 -> functionRefH),
      functionDefs,
      packageCoordToExportNameToFunction,
      packageCoordToExportNameToKind,
      packageCoordToExternNameToFunction,
      packageCoordToExternNameToKind)
  }

  def addFunction(functionRef2: PrototypeT, functionDefH: FunctionH): Hamuts = {
    vassert(functionRefs.contains(functionRef2))

    Hamuts(
      idByFullNameByHumanName,
      structRefsByRef2,
      structDefsByRef2,
      structDefs,
      staticSizedArrays,
      runtimeSizedArrays,
      interfaceRefs,
      interfaceDefs,
      functionRefs,
      functionDefs + (functionRef2 -> functionDefH),
      packageCoordToExportNameToFunction,
      packageCoordToExportNameToKind,
      packageCoordToExternNameToFunction,
      packageCoordToExternNameToKind)
  }

  def addKindExport(kind: KindH, packageCoordinate: PackageCoordinate, exportedName: String): Hamuts = {
    val newPackageCoordToExportNameToKind =
      packageCoordToExportNameToKind.get(packageCoordinate) match {
        case None => {
          packageCoordToExportNameToKind + (packageCoordinate -> Map(exportedName -> kind))
        }
        case Some(exportNameToFullName) => {
          exportNameToFullName.get(exportedName) match {
            case None => {
              packageCoordToExportNameToKind + (packageCoordinate -> (exportNameToFullName + (exportedName -> kind)))
            }
            case Some(existingFullName) => {
              vfail("Already exported a `" + exportedName + "` from package `" + packageCoordinate + " : " + existingFullName)
            }
          }
        }
      }

    Hamuts(
      idByFullNameByHumanName,
      structRefsByRef2,
      structDefsByRef2,
      structDefs,
      staticSizedArrays,
      runtimeSizedArrays,
      interfaceRefs,
      interfaceDefs,
      functionRefs,
      functionDefs,
      packageCoordToExportNameToFunction,
      newPackageCoordToExportNameToKind,
      packageCoordToExternNameToFunction,
      packageCoordToExternNameToKind)
  }

  def addFunctionExport(function: PrototypeH, packageCoordinate: PackageCoordinate, exportedName: String): Hamuts = {
    val newPackageCoordToExportNameToFunction =
      packageCoordToExportNameToFunction.get(packageCoordinate) match {
        case None => {
          packageCoordToExportNameToFunction + (packageCoordinate -> Map(exportedName -> function))
        }
        case Some(exportNameToFullName) => {
          exportNameToFullName.get(exportedName) match {
            case None => {
              packageCoordToExportNameToFunction + (packageCoordinate -> (exportNameToFullName + (exportedName -> function)))
            }
            case Some(existingFullName) => {
              vfail("Already exported a `" + exportedName + "` from package `" + packageCoordinate + " : " + existingFullName)
            }
          }
        }
      }

    Hamuts(
      idByFullNameByHumanName,
      structRefsByRef2,
      structDefsByRef2,
      structDefs,
      staticSizedArrays,
      runtimeSizedArrays,
      interfaceRefs,
      interfaceDefs,
      functionRefs,
      functionDefs,
      newPackageCoordToExportNameToFunction,
      packageCoordToExportNameToKind,
      packageCoordToExternNameToFunction,
      packageCoordToExternNameToKind)
  }

  def addKindExtern(kind: KindH, packageCoordinate: PackageCoordinate, exportedName: String): Hamuts = {
    val newPackageCoordToExternNameToKind =
      packageCoordToExternNameToKind.get(packageCoordinate) match {
        case None => {
          packageCoordToExternNameToKind + (packageCoordinate -> Map(exportedName -> kind))
        }
        case Some(exportNameToFullName) => {
          exportNameToFullName.get(exportedName) match {
            case None => {
              packageCoordToExternNameToKind + (packageCoordinate -> (exportNameToFullName + (exportedName -> kind)))
            }
            case Some(existingFullName) => {
              vfail("Already exported a `" + exportedName + "` from package `" + packageCoordinate + " : " + existingFullName)
            }
          }
        }
      }

    Hamuts(
      idByFullNameByHumanName,
      structRefsByRef2,
      structDefsByRef2,
      structDefs,
      staticSizedArrays,
      runtimeSizedArrays,
      interfaceRefs,
      interfaceDefs,
      functionRefs,
      functionDefs,
      packageCoordToExportNameToFunction,
      packageCoordToExportNameToKind,
      packageCoordToExternNameToFunction,
      newPackageCoordToExternNameToKind)
  }

  def addFunctionExtern(function: PrototypeH, packageCoordinate: PackageCoordinate, exportedName: String): Hamuts = {
    val newPackageCoordToExternNameToFunction =
      packageCoordToExternNameToFunction.get(packageCoordinate) match {
        case None => {
          packageCoordToExternNameToFunction + (packageCoordinate -> Map(exportedName -> function))
        }
        case Some(exportNameToFullName) => {
          exportNameToFullName.get(exportedName) match {
            case None => {
              packageCoordToExternNameToFunction + (packageCoordinate -> (exportNameToFullName + (exportedName -> function)))
            }
            case Some(existingFullName) => {
              vfail("Already exported a `" + exportedName + "` from package `" + packageCoordinate + " : " + existingFullName)
            }
          }
        }
      }

    Hamuts(
      idByFullNameByHumanName,
      structRefsByRef2,
      structDefsByRef2,
      structDefs,
      staticSizedArrays,
      runtimeSizedArrays,
      interfaceRefs,
      interfaceDefs,
      functionRefs,
      functionDefs,
      packageCoordToExportNameToFunction,
      packageCoordToExportNameToKind,
      newPackageCoordToExternNameToFunction,
      packageCoordToExternNameToKind)
  }

  def addStaticSizedArray(staticSizedArrayDefinitionTH: StaticSizedArrayDefinitionTH): Hamuts = {
    Hamuts(
      idByFullNameByHumanName,
      structRefsByRef2,
      structDefsByRef2,
      structDefs,
      staticSizedArrays :+ staticSizedArrayDefinitionTH,
      runtimeSizedArrays,
      interfaceRefs,
      interfaceDefs,
      functionRefs,
      functionDefs,
      packageCoordToExportNameToFunction,
      packageCoordToExportNameToKind,
      packageCoordToExternNameToFunction,
      packageCoordToExternNameToKind)
  }

  def addRuntimeSizedArray(runtimeSizedArrayDefinitionTH: RuntimeSizedArrayDefinitionTH): Hamuts = {
    Hamuts(
      idByFullNameByHumanName,
      structRefsByRef2,
      structDefsByRef2,
      structDefs,
      staticSizedArrays,
      runtimeSizedArrays :+ runtimeSizedArrayDefinitionTH,
      interfaceRefs,
      interfaceDefs,
      functionRefs,
      functionDefs,
      packageCoordToExportNameToFunction,
      packageCoordToExportNameToKind,
      packageCoordToExternNameToFunction,
      packageCoordToExternNameToKind)
  }

  // This returns a unique ID for that specific human name.
  // Two things with two different human names could result in the same ID here.
  // This ID is meant to be concatenated onto the human name.
  def getNameId(readableName: String, packageCoordinate: PackageCoordinate, parts: List[IVonData]): (Hamuts, Int) = {
    val namePartsString = FullNameH.namePartsToString(packageCoordinate, parts)
    val idByFullNameForHumanName =
      idByFullNameByHumanName.get(readableName) match {
        case None => Map[String, Int]()
        case Some(x) => x
      }
    val id =
      idByFullNameForHumanName.get(namePartsString) match {
        case None => idByFullNameForHumanName.size
        case Some(i) => i
      }
    val idByFullNameForHumanNameNew = idByFullNameForHumanName + (namePartsString -> id)
    val idByFullNameByHumanNameNew = idByFullNameByHumanName + (readableName -> idByFullNameForHumanNameNew)
    val newHamuts =
      Hamuts(
        idByFullNameByHumanNameNew,
        structRefsByRef2,
        structDefsByRef2,
        structDefs,
        staticSizedArrays,
        runtimeSizedArrays,
        interfaceRefs,
        interfaceDefs,
        functionRefs,
        functionDefs,
        packageCoordToExportNameToFunction,
        packageCoordToExportNameToKind,
        packageCoordToExternNameToFunction,
        packageCoordToExternNameToKind)
    (newHamuts, id)
  }

  def getStaticSizedArray(staticSizedArrayTH: StaticSizedArrayTH): StaticSizedArrayDefinitionTH = {
    staticSizedArrays.find(_.name == staticSizedArrayTH.name).get
  }
  def getRuntimeSizedArray(runtimeSizedArrayTH: RuntimeSizedArrayTH): RuntimeSizedArrayDefinitionTH = {
    runtimeSizedArrays.find(_.name == runtimeSizedArrayTH.name).get
  }

}
