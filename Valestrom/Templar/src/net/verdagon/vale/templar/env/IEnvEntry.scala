package net.verdagon.vale.templar.env

import net.verdagon.vale.astronomer.{FunctionA, ImplA, InterfaceA, StructA}
import net.verdagon.vale.templar.templata.{IContainer, ITemplata}
import net.verdagon.vale.templar.types.{InterfaceTT, StructTT}
import net.verdagon.vale.vimpl

sealed trait IEnvEntry
// We dont have the unevaluatedContainers in here because see TMRE
case class FunctionEnvEntry(function: FunctionA) extends IEnvEntry {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
}
case class ImplEnvEntry(impl: ImplA) extends IEnvEntry { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
case class StructEnvEntry(struct: StructA) extends IEnvEntry { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
case class InterfaceEnvEntry(interface: InterfaceA) extends IEnvEntry { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
case class TemplataEnvEntry(templata: ITemplata) extends IEnvEntry { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
