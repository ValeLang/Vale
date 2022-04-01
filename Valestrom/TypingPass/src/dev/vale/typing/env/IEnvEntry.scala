package dev.vale.typing.env

import dev.vale.highertyping.{FunctionA, ImplA, InterfaceA, StructA}
import dev.vale.typing.templata.ITemplata
import dev.vale.vpass
import dev.vale.highertyping.FunctionA
import dev.vale.typing.templata.IContainer
import dev.vale.typing.types.InterfaceTT
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
