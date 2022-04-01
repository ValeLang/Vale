package dev.vale.templar.env

import dev.vale.astronomer.{FunctionA, ImplA, InterfaceA, StructA}
import dev.vale.templar.templata.ITemplata
import dev.vale.vpass
import dev.vale.astronomer.FunctionA
import dev.vale.templar.templata.IContainer
import dev.vale.templar.types.InterfaceTT
import dev.vale.vpass

sealed trait IEnvEntry
// We dont have the unevaluatedContainers in here because see TMRE
case class FunctionEnvEntry(function: FunctionA) extends IEnvEntry {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
}
case class ImplEnvEntry(impl: ImplA) extends IEnvEntry { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
case class StructEnvEntry(struct: StructA) extends IEnvEntry { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
case class InterfaceEnvEntry(interface: InterfaceA) extends IEnvEntry { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
case class TemplataEnvEntry(templata: ITemplata) extends IEnvEntry {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  vpass()
}
