package net.verdagon.vale.hammer

import net.verdagon.vale.hinputs.Hinputs
import net.verdagon.vale.metal._
import net.verdagon.vale.templar.{CitizenName2, FullName2, FunctionName2, IName2, IVarName2, ImmConcreteDestructorName2, ImmInterfaceDestructorName2}
import net.verdagon.vale.{vassert, vfail}

case class FunctionRefH(prototype: PrototypeH) {
  //  def functionType = prototype.functionType
  def fullName = prototype.fullName
}

case class LocalsBox(var inner: Locals) {
  def snapshot = inner

  def templarLocals: Map[FullName2[IVarName2], VariableIdH] = inner.templarLocals
  def unstackifiedVars: Set[VariableIdH] = inner.unstackifiedVars
  def locals: Map[VariableIdH, Local] = inner.locals

  def get(id: FullName2[IVarName2]) = inner.get(id)
  def get(id: VariableIdH) = inner.get(id)

  def markUnstackified(varId2: FullName2[IVarName2]): Unit = {
    inner = inner.markUnstackified(varId2)
  }

  def markUnstackified(varIdH: VariableIdH): Unit = {
    inner = inner.markUnstackified(varIdH)
  }

  def addHammerLocal(
    tyype: ReferenceH[ReferendH]):
  Local = {
    val (newInner, local) = inner.addHammerLocal(tyype)
    inner = newInner
    local
  }

  def addTemplarLocal(
    hinputs: Hinputs,
    hamuts: HamutsBox,
    varId2: FullName2[IVarName2],
    tyype: ReferenceH[ReferendH]):
  Local = {
    val (newInner, local) = inner.addTemplarLocal(hinputs, hamuts, varId2, tyype)
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
    templarLocals: Map[FullName2[IVarName2], VariableIdH],

    unstackifiedVars: Set[VariableIdH],

    // This has all the locals for the function, a superset of templarLocals.
    locals: Map[VariableIdH, Local]) {

  def addTemplarLocal(
    hinputs: Hinputs,
    hamuts: HamutsBox,
    varId2: FullName2[IVarName2],
    tyype: ReferenceH[ReferendH]):
  (Locals, Local) = {
    if (templarLocals.contains(varId2)) {
      vfail("wot")
    }
    val newLocalIdNumber = locals.size
    val varIdNameH = NameHammer.translateFullName(hinputs, hamuts, varId2)
    val newLocalId = VariableIdH(newLocalIdNumber, Some(varIdNameH))
    val newLocal = Local(newLocalId, tyype)
    val newLocals =
      Locals(
        templarLocals + (varId2 -> newLocalId),
        unstackifiedVars,
        locals + (newLocalId -> newLocal))
    (newLocals, newLocal)
  }

  def addHammerLocal(
    tyype: ReferenceH[ReferendH]):
  (Locals, Local) = {
    val newLocalIdNumber = locals.size
    val newLocalId = VariableIdH(newLocalIdNumber, None)
    val newLocal = Local(newLocalId, tyype)
    val newLocals =
      Locals(
        templarLocals,
        unstackifiedVars,
        locals + (newLocalId -> newLocal))
    (newLocals, newLocal)
  }

  def markUnstackified(varId2: FullName2[IVarName2]): Locals = {
    markUnstackified(templarLocals(varId2))
  }

  def markUnstackified(varIdH: VariableIdH): Locals = {
    // Make sure it existed and wasnt already unstackified
    vassert(locals.contains(varIdH))
    if (unstackifiedVars.contains(varIdH)) {
      vfail("Already unstackified " + varIdH)
    }
    Locals(templarLocals, unstackifiedVars + varIdH, locals)
  }

  def get(varId: FullName2[IVarName2]): Option[Local] = {
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
    val hamuts = HamutsBox(Hamuts(Map(), Map(), Map(), List(), Map(), Map(), Map(), Map()))
    val emptyPackStructRefH = StructHammer.translateStructRef(hinputs, hamuts, hinputs.emptyPackStructRef)
    vassert(emptyPackStructRefH == ProgramH.emptyTupleStructRef)
    StructHammer.translateInterfaces(hinputs, hamuts);
    StructHammer.translateStructs(hinputs, hamuts)
    val userFunctions = hinputs.functions.filter(_.header.isUserFunction).toList
    val nonUserFunctions = hinputs.functions.filter(!_.header.isUserFunction).toList
    FunctionHammer.translateFunctions(hinputs, hamuts, userFunctions)
    FunctionHammer.translateFunctions(hinputs, hamuts, nonUserFunctions)

    val immDestructors2 =
      hinputs.functions.filter(function => {
        function.header.fullName match {
          case FullName2(List(), ImmConcreteDestructorName2(_)) => true
          case FullName2(List(), ImmInterfaceDestructorName2(_, _)) => true
          case _ => false
        }
      })

    val immDestructorPrototypesH =
      immDestructors2.map(immDestructor2 => {
        val kindH = TypeHammer.translateReference(hinputs, hamuts, immDestructor2.header.params.head.tyype).kind
        val immDestructorPrototypeH = FunctionHammer.translateFunction(hinputs, hamuts, immDestructor2).prototype
        (kindH -> immDestructorPrototypeH)
      }).toMap

    immDestructorPrototypesH.foreach({ case (kindH, immDestructorPrototypeH) => {
      vassert(immDestructorPrototypeH.params.head.kind == kindH)
    }})

    val exportedNameByFullName = hamuts.fullNameByExportedName.map(_.swap)
    vassert(exportedNameByFullName.size == hamuts.fullNameByExportedName.size)

    ProgramH(
      hamuts.interfaceDefs.values.toList,
      hamuts.structDefs,
      List() /* externs */,
      hamuts.functionDefs.values.toList,
      immDestructorPrototypesH,
      exportedNameByFullName)
  }

  def exportName(hamuts: HamutsBox, fullName2: FullName2[IName2], fullNameH: FullNameH) = {
    val exportedName =
      fullName2.last match {
        case FunctionName2(humanName, _, _) => humanName
        case CitizenName2(humanName, _) => humanName
        case _ => vfail("Can't export something that doesn't have a human readable name!")
      }
    hamuts.fullNameByExportedName.get(exportedName) match {
      case None =>
      case Some(existingFullName) => {
        vfail("Can't export " + fullNameH + " as " + exportedName + ", that exported name already taken by " + existingFullName)
      }
    }
    hamuts.addExport(fullNameH, exportedName)
  }
}
