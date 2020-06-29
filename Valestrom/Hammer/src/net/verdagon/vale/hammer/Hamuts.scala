package net.verdagon.vale.hammer

import net.verdagon.vale.metal._
import net.verdagon.vale.templar.templata.Prototype2
import net.verdagon.vale.templar.types.{InterfaceRef2, PackT2, StructRef2}
import net.verdagon.vale.vassert


case class HamutsBox(var inner: Hamuts) {

  def structRefsByRef2: Map[StructRef2, StructRefH] = inner.structRefsByRef2
  def structDefsByRef2: Map[StructRef2, StructDefinitionH] = inner.structDefsByRef2
  def structDefs: List[StructDefinitionH] = inner.structDefs
  def interfaceRefs: Map[InterfaceRef2, InterfaceRefH] = inner.interfaceRefs
  def interfaceDefs: Map[InterfaceRef2, InterfaceDefinitionH] = inner.interfaceDefs
  def functionRefs: Map[Prototype2, FunctionRefH] = inner.functionRefs
  def functionDefs: Map[Prototype2, FunctionH] = inner.functionDefs

  def forwardDeclareStruct(structRef2: StructRef2, structRefH: StructRefH): Unit = {
    inner = inner.forwardDeclareStruct(structRef2, structRefH)
  }

  def addStructOriginatingFromTemplar(structRef2: StructRef2, structDefH: StructDefinitionH): Unit = {
    inner = inner.addStructOriginatingFromTemplar(structRef2, structDefH)
  }

  def addStructOriginatingFromHammer(structDefH: StructDefinitionH): Unit = {
    inner = inner.addStructOriginatingFromHammer(structDefH)
  }

  def forwardDeclareInterface(interfaceRef2: InterfaceRef2, interfaceRefH: InterfaceRefH): Unit = {
    inner = inner.forwardDeclareInterface(interfaceRef2, interfaceRefH)
  }

  def addInterface(interfaceRef2: InterfaceRef2, interfaceDefH: InterfaceDefinitionH): Unit = {
    inner = inner.addInterface(interfaceRef2, interfaceDefH)
  }

  def forwardDeclareFunction(functionRef2: Prototype2, functionRefH: FunctionRefH): Unit = {
    inner = inner.forwardDeclareFunction(functionRef2, functionRefH)
  }

  def addFunction(functionRef2: Prototype2, functionDefH: FunctionH): Unit = {
    inner = inner.addFunction(functionRef2, functionDefH)
  }
}

case class Hamuts(
    structRefsByRef2: Map[StructRef2, StructRefH],
    structDefsByRef2: Map[StructRef2, StructDefinitionH],
    structDefs: List[StructDefinitionH],
    interfaceRefs: Map[InterfaceRef2, InterfaceRefH],
    interfaceDefs: Map[InterfaceRef2, InterfaceDefinitionH],
    functionRefs: Map[Prototype2, FunctionRefH],
    functionDefs: Map[Prototype2, FunctionH]) {
  def forwardDeclareStruct(structRef2: StructRef2, structRefH: StructRefH): Hamuts = {
    Hamuts(
      structRefsByRef2 + (structRef2 -> structRefH),
      structDefsByRef2,
      structDefs,
      interfaceRefs,
      interfaceDefs,
      functionRefs,
      functionDefs)
  }

  def addStructOriginatingFromTemplar(structRef2: StructRef2, structDefH: StructDefinitionH): Hamuts = {
    vassert(structRefsByRef2.contains(structRef2))
    Hamuts(
      structRefsByRef2,
      structDefsByRef2 + (structRef2 -> structDefH),
      structDefs :+ structDefH,
      interfaceRefs,
      interfaceDefs,
      functionRefs,
      functionDefs)
  }

  def addStructOriginatingFromHammer(structDefH: StructDefinitionH): Hamuts = {
    Hamuts(
      structRefsByRef2,
      structDefsByRef2,
      structDefs :+ structDefH,
      interfaceRefs,
      interfaceDefs,
      functionRefs,
      functionDefs)
  }

  def forwardDeclareInterface(interfaceRef2: InterfaceRef2, interfaceRefH: InterfaceRefH): Hamuts = {
    Hamuts(
      structRefsByRef2,
      structDefsByRef2,
      structDefs,
      interfaceRefs + (interfaceRef2 -> interfaceRefH),
      interfaceDefs,
      functionRefs,
      functionDefs)
  }

  def addInterface(interfaceRef2: InterfaceRef2, interfaceDefH: InterfaceDefinitionH): Hamuts = {
    vassert(interfaceRefs.contains(interfaceRef2))
    Hamuts(
      structRefsByRef2,
      structDefsByRef2,
      structDefs,
      interfaceRefs,
      interfaceDefs + (interfaceRef2 -> interfaceDefH),
      functionRefs,
      functionDefs)
  }

  def forwardDeclareFunction(functionRef2: Prototype2, functionRefH: FunctionRefH): Hamuts = {
    Hamuts(
      structRefsByRef2,
      structDefsByRef2,
      structDefs,
      interfaceRefs,
      interfaceDefs,
      functionRefs + (functionRef2 -> functionRefH),
      functionDefs)
  }

  def addFunction(functionRef2: Prototype2, functionDefH: FunctionH): Hamuts = {
    vassert(functionRefs.contains(functionRef2))
    Hamuts(
      structRefsByRef2,
      structDefsByRef2,
      structDefs,
      interfaceRefs,
      interfaceDefs,
      functionRefs,
      functionDefs + (functionRef2 -> functionDefH))
  }
}
