package net.verdagon.von

import net.verdagon.vale.vimpl

sealed trait IVonData

case class VonInt(value: Long) extends IVonData { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
case class VonFloat(value: Double) extends IVonData { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
case class VonBool(value: Boolean) extends IVonData { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
case class VonStr(value: String) extends IVonData { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }

case class VonReference(id: String) extends IVonData { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }

case class VonObject(
  tyype: String,
  id: Option[String],
  members: Vector[VonMember]
) extends IVonData { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
case class VonMember(fieldName: String, value: IVonData) { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }

case class VonArray(
  id: Option[String],
  members: Vector[IVonData]
) extends IVonData { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }

case class VonListMap(
  id: Option[String],
  members: Vector[VonMapEntry]
) extends IVonData { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }

case class VonMap(
  id: Option[String],
  members: Vector[VonMapEntry]
) extends IVonData { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }

case class VonMapEntry(
  key: IVonData,
  value: IVonData) { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
