package net.verdagon.vale.hammer

import net.verdagon.vale.hinputs.Hinputs
import net.verdagon.vale.metal._
import net.verdagon.vale.templar.{FullName2, IVarName2}
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
      vfail("nooo")
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
    val hamuts = HamutsBox(Hamuts(Map(), Map(), List(), Map(), Map(), Map(), Map()))
    val emptyPackStructRefH = StructHammer.translateStructRef(hinputs, hamuts, hinputs.emptyPackStructRef)
    vassert(emptyPackStructRefH == ProgramH.emptyTupleStructRef)
    StructHammer.translateInterfaces(hinputs, hamuts);
    StructHammer.translateStructs(hinputs, hamuts)
    val userFunctions = hinputs.functions.filter(_.header.isUserFunction).toList
    val nonUserFunctions = hinputs.functions.filter(!_.header.isUserFunction).toList
    FunctionHammer.translateFunctions(hinputs, hamuts, userFunctions)
    FunctionHammer.translateFunctions(hinputs, hamuts, nonUserFunctions)

    ProgramH(
      hamuts.interfaceDefs.values.toList,
      hamuts.structDefs,
      List() /* externs */,
      hamuts.functionDefs.values.toList)
  }
}
