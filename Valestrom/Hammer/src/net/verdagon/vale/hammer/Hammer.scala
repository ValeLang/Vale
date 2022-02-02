package net.verdagon.vale.hammer

import net.verdagon.vale.astronomer.{ICompileErrorA, ProgramA}
import net.verdagon.vale.metal._
import net.verdagon.vale.scout.{ICompileErrorS, ProgramS}
import net.verdagon.vale.templar.ast.{FunctionExportT, FunctionExternT, KindExportT, KindExternT}
import net.verdagon.vale.templar.names.{FullNameT, IVarNameT}
import net.verdagon.vale.templar.{Hinputs, ICompileErrorT, TemplarCompilation, TemplarCompilationOptions, types => t}
import net.verdagon.vale.{Builtins, FileCoordinateMap, IPackageResolver, IProfiler, NullProfiler, PackageCoordinate, PackageCoordinateMap, Result, vassert, vcurious, vfail, vwat}

import scala.collection.immutable.List

case class FunctionRefH(prototype: PrototypeH) {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  //  def functionType = prototype.functionType
  def fullName = prototype.fullName
}

case class LocalsBox(var inner: Locals) {
  override def hashCode(): Int = vfail() // Shouldnt hash, is mutable

  def snapshot = inner

  def templarLocals: Map[FullNameT[IVarNameT], VariableIdH] = inner.templarLocals
  def unstackifiedVars: Set[VariableIdH] = inner.unstackifiedVars
  def locals: Map[VariableIdH, Local] = inner.locals
  def nextLocalIdNumber: Int = inner.nextLocalIdNumber

  def get(id: FullNameT[IVarNameT]) = inner.get(id)
  def get(id: VariableIdH) = inner.get(id)

  def markUnstackified(varId2: FullNameT[IVarNameT]): Unit = {
    inner = inner.markUnstackified(varId2)
  }

  def markUnstackified(varIdH: VariableIdH): Unit = {
    inner = inner.markUnstackified(varIdH)
  }
  def setNextLocalIdNumber(nextLocalIdNumber: Int): Unit = {
    inner = inner.copy(nextLocalIdNumber = nextLocalIdNumber)
  }

  def addHammerLocal(
    tyype: ReferenceH[KindH],
    variability: Variability):
  Local = {
    val (newInner, local) = inner.addHammerLocal(tyype, variability)
    inner = newInner
    local
  }

  def addTemplarLocal(
    hinputs: Hinputs,
    hamuts: HamutsBox,
    varId2: FullNameT[IVarNameT],
    variability: Variability,
    tyype: ReferenceH[KindH]):
  Local = {
    val (newInner, local) = inner.addTemplarLocal(hinputs, hamuts, varId2, variability, tyype)
    inner = newInner
    local
  }

}

// This represents the locals for the entire function.
// Note, some locals will have the same index, that just means they're in
// different blocks.
case class Locals(
     // This doesn't have all the locals that are in the locals list, this just
     // has any locals added by templar.
     templarLocals: Map[FullNameT[IVarNameT], VariableIdH],

     unstackifiedVars: Set[VariableIdH],

     // This has all the locals for the function, a superset of templarLocals.
     locals: Map[VariableIdH, Local],

     nextLocalIdNumber: Int) {
  override def hashCode(): Int = vcurious()

  def addTemplarLocal(
    hinputs: Hinputs,
    hamuts: HamutsBox,
    varId2: FullNameT[IVarNameT],
    variability: Variability,
    tyype: ReferenceH[KindH]):
  (Locals, Local) = {
    if (templarLocals.contains(varId2)) {
      vfail("There's already a templar local named: " + varId2)
    }
    val newLocalHeight = locals.size
    val newLocalIdNumber = nextLocalIdNumber
    val varIdNameH = NameHammer.translateFullName(hinputs, hamuts, varId2)
    val newLocalId = VariableIdH(newLocalIdNumber, newLocalHeight, Some(varIdNameH))
    // Temporary until catalyst fills in stuff here
    val keepAlive = newLocalId.name.map(_.readableName).getOrElse("").endsWith("__tether");
    val newLocal = Local(newLocalId, variability, tyype, keepAlive)
    val newLocals =
      Locals(
        templarLocals + (varId2 -> newLocalId),
        unstackifiedVars,
        locals + (newLocalId -> newLocal),
        newLocalIdNumber + 1)
    (newLocals, newLocal)
  }

  def addHammerLocal(
    tyype: ReferenceH[KindH],
    variability: Variability):
  (Locals, Local) = {
    val newLocalHeight = locals.size
    val newLocalIdNumber = nextLocalIdNumber
    val newLocalId = VariableIdH(newLocalIdNumber, newLocalHeight, None)
    val newLocal = Local(newLocalId, variability, tyype, false)
    val newLocals =
      Locals(
        templarLocals,
        unstackifiedVars,
        locals + (newLocalId -> newLocal),
        newLocalIdNumber + 1)
    (newLocals, newLocal)
  }

  def markUnstackified(varId2: FullNameT[IVarNameT]): Locals = {
    markUnstackified(templarLocals(varId2))
  }

  def markUnstackified(varIdH: VariableIdH): Locals = {
    // Make sure it existed and wasnt already unstackified
    vassert(locals.contains(varIdH))
    if (unstackifiedVars.contains(varIdH)) {
      vfail("Already unstackified " + varIdH)
    }
    Locals(templarLocals, unstackifiedVars + varIdH, locals, nextLocalIdNumber)
  }

  def get(varId: FullNameT[IVarNameT]): Option[Local] = {
    templarLocals.get(varId) match {
      case None => None
      case Some(index) => Some(locals(index))
    }
  }

  def get(varId: VariableIdH): Option[Local] = {
    locals.get(varId)
  }
}

object Hammer {
  def translate(hinputs: Hinputs): ProgramH = {
    val Hinputs(
      interfaces,
      structs,
      functions,
      kindToDestructor,
      edgeBlueprintsByInterface,
      edges,
      kindExports,
      functionExports,
      kindExterns,
      functionExterns) = hinputs


    val hamuts = HamutsBox(Hamuts(Map(), Map(), Map(), Vector.empty, Map(), Map(), Map(), Map(), Map(), Map(), Map(), Map(), Map(), Map()))
//    val emptyPackStructRefH = StructHammer.translateStructRef(hinputs, hamuts, emptyPackStructRef)
//    vassert(emptyPackStructRefH == ProgramH.emptyTupleStructRef)

    kindExports.foreach({ case KindExportT(_, tyype, packageCoordinate, exportName) =>
      val kindH = TypeHammer.translateKind(hinputs, hamuts, tyype)
      hamuts.addKindExport(kindH, packageCoordinate, exportName)
    })

    functionExports.foreach({ case FunctionExportT(_, prototype, packageCoordinate, exportName) =>
      val prototypeH = FunctionHammer.translatePrototype(hinputs, hamuts, prototype)
      hamuts.addFunctionExport(prototypeH, packageCoordinate, exportName)
    })

    kindExterns.foreach({ case KindExternT(tyype, packageCoordinate, exportName) =>
      val kindH = TypeHammer.translateKind(hinputs, hamuts, tyype)
      hamuts.addKindExtern(kindH, packageCoordinate, exportName)
    })

    functionExterns.foreach({ case FunctionExternT(_, prototype, packageCoordinate, exportName) =>
      val prototypeH = FunctionHammer.translatePrototype(hinputs, hamuts, prototype)
      hamuts.addFunctionExtern(prototypeH, packageCoordinate, exportName)
    })

    // We generate the names here first, so that externs get the first chance at having
    // ID 0 for each name, which means we dont need to add _1 _2 etc to the end of them,
    // and they'll match up with the actual outside names.
//          externNameToExtern.map({ case (externName, prototype2) =>
//            val fullNameH = NameHammer.translateFullName(hinputs, hamuts, prototype2.fullName)
//            val humanName =
//              prototype2.fullName.last match {
//                case ExternFunctionName2(humanName, _) => humanName
//                case _ => vfail("Only human-named functions can be extern")
//              }
//            if (fullNameH.readableName != humanName) {
//              vfail("Name conflict, two externs with the same name!")
//            }
//            val prototypeH = FunctionHammer.translatePrototype(hinputs, hamuts, prototype2)
//            (packageCoordinate -> (externName, prototypeH))
//          })
//      })
//    val externPrototypesH =
//      packageToExternNameToPrototypeH.values.flatMap(_.values.toVector)

    StructHammer.translateInterfaces(hinputs, hamuts);
    StructHammer.translateStructs(hinputs, hamuts)
    val userFunctions = functions.filter(f => f.header.isUserFunction).toVector
    val nonUserFunctions = functions.filter(f => !f.header.isUserFunction).toVector
    FunctionHammer.translateFunctions(hinputs, hamuts, userFunctions)
    FunctionHammer.translateFunctions(hinputs, hamuts, nonUserFunctions)

    val immDestructorPrototypesH =
      kindToDestructor.map({ case (kind, destructor) =>
        val kindH = TypeHammer.translateKind(hinputs, hamuts, kind)
        val immDestructorPrototypeH = FunctionHammer.translatePrototype(hinputs, hamuts, destructor)
        (kindH -> immDestructorPrototypeH)
      }).toMap

    immDestructorPrototypesH.foreach({ case (kindH, immDestructorPrototypeH) => {
      vassert(immDestructorPrototypeH.params.head.kind == kindH)
    }})

    val packageToInterfaceDefs = hamuts.interfaceDefs.groupBy(_._1.fullName.packageCoord)
    val packageToStructDefs = hamuts.structDefs.groupBy(_.fullName.packageCoordinate)
    val packageToFunctionDefs = hamuts.functionDefs.groupBy(_._1.fullName.packageCoord).mapValues(_.values.toVector)
    val packageToStaticSizedArrays = hamuts.staticSizedArrays.values.toVector.groupBy(_.name.packageCoordinate)
    val packageToRuntimeSizedArrays = hamuts.runtimeSizedArrays.values.toVector.groupBy(_.name.packageCoordinate)
    val packageToImmDestructorPrototypes = immDestructorPrototypesH.groupBy(_._1.packageCoord)
    val packageToExportNameToKind = hamuts.packageCoordToExportNameToKind
    val packageToExportNameToFunction = hamuts.packageCoordToExportNameToFunction
    val packageToExternNameToKind = hamuts.packageCoordToExternNameToKind
    val packageToExternNameToFunction = hamuts.packageCoordToExternNameToFunction

    val allPackageCoords =
      packageToInterfaceDefs.keySet ++
      packageToStructDefs.keySet ++
      packageToFunctionDefs.keySet ++
      packageToStaticSizedArrays.keySet ++
      packageToRuntimeSizedArrays.keySet ++
      packageToImmDestructorPrototypes.keySet ++
      packageToExportNameToFunction.keySet ++
      packageToExportNameToKind.keySet ++
      packageToExternNameToFunction.keySet ++
      packageToExternNameToKind.keySet

    val packages =
      allPackageCoords.toVector.map(packageCoord => {
        packageCoord ->
          PackageH(
            packageToInterfaceDefs.getOrElse(packageCoord, Map()).values.toVector,
            packageToStructDefs.getOrElse(packageCoord, Vector.empty),
            packageToFunctionDefs.getOrElse(packageCoord, Vector.empty),
            packageToStaticSizedArrays.getOrElse(packageCoord, Vector.empty),
            packageToRuntimeSizedArrays.getOrElse(packageCoord, Vector.empty),
            packageToImmDestructorPrototypes.getOrElse(packageCoord, Map()),
            packageToExportNameToFunction.getOrElse(packageCoord, Map()),
            packageToExportNameToKind.getOrElse(packageCoord, Map()),
            packageToExternNameToFunction.getOrElse(packageCoord, Map()),
            packageToExternNameToKind.getOrElse(packageCoord, Map()))
      }).toMap
        .groupBy(_._1.module)
        .mapValues(packageCoordToPackage => {
          packageCoordToPackage.map({ case (packageCoord, paackage) => (packageCoord.packages, paackage) }).toMap
        })

    ProgramH(PackageCoordinateMap(packages))
  }

  def consecutive(unfilteredExprsH: Vector[ExpressionH[KindH]]): ExpressionH[KindH] = {
    val indexOfFirstNever = unfilteredExprsH.indexWhere(_.resultType.kind == NeverH())
    // If there's an expression returning a Never, then remove all the expressions after that.
    val exprsH =
      unfilteredExprsH.zipWithIndex
        // It comes after a Never statement, take it out.
        .filter({ case (expr, index) => indexOfFirstNever < 0 || index <= indexOfFirstNever })
        // If this isnt the last expr, and its just making a void, take it out.
        .filter({ case (expr, index) =>
          if (index < unfilteredExprsH.size - 1) {
            expr match {
              case NewStructH(Vector(), Vector(), ReferenceH(_, InlineH, _, _)) => false
              case _ => true
            }
          } else {
            true
          }
        }).map(_._1)

    vassert(exprsH.nonEmpty)

    exprsH match {
      case Vector() => vwat("Cant have empty consecutive")
      case Vector(only) => only
      case multiple => ConsecutorH(multiple)
    }
  }
}

