package net.verdagon.vale.hinputs

import net.verdagon.vale.vassert

//case class TetrisTable[K, V](
//  hasher: K => Int,
//  directory: Array[Option[DirectoryEntry]],
//  combinedBuckets: Array[Option[(K, V)]]) {
//  vassert((directory.length & (directory.length - 1)) == 0)
//  def get(key: K): Option[V] = {
//    val hash = hasher(key);
//    val indexInDirectory = hash % directory.length;
//    directory(indexInDirectory) match {
//      case None => None
//      case Some(DirectoryEntry(bucketBeginIndex, bucketSize)) => {
//        val indexInBucket = hash % bucketSize;
//        val indexInCombinedBuckets = bucketBeginIndex + indexInBucket
//        combinedBuckets(indexInCombinedBuckets) match {
//          case None => None
//          case Some((foundKey, foundValue)) => {
//            if (foundKey == key) Some(foundValue) else None
//          }
//        }
//      }
//    }
//  }
//}
//
//case class Table[K](members: Array[Option[K]])
//case class BucketTable[K](bucketIndex: Int, table: Table[K])
//
//case class DirectoryEntry(indexInTable: Int, size: Int) { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
//
//case class IntermediateTetrisTable[K](
//  bucketStartIndexByBucketIndex: Map[Int, Int],
//  members: Array[Option[K]])
