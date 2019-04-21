package cpscala.TSolver.Model.Constraint.IPConstraint

import cpscala.TSolver.CpUtil.SearchHelper.IPSearchHelper
import cpscala.TSolver.CpUtil._
import cpscala.TSolver.Model.Variable.PVar

import scala.collection.mutable.ArrayBuffer

class TableIPCT_SSBit(val id: Int, val arity: Int, val num_vars: Int, val scope: Array[PVar], val tuples: Array[Array[Int]], val helper: IPSearchHelper) extends IPPropagator {
  val currTab = new RSBitSet(id, tuples.length, num_vars)
  val supports = new Array[Array[Array[Long]]](arity)
  val num_bit = currTab.num_bit
  val residues = new Array[Array[Int]](arity)
  level = 0

  for (vv <- 0 until arity) {
    supports(vv) = Array.ofDim[Long](scope(vv).size, num_bit)
    residues(vv) = Array.fill(scope(vv).size)(-1)
  }

  for (i <- 0 until tuples.length) {
    val (x, y) = INDEX.getXY(i)
    val t = tuples(i)

    for (j <- 0 until t.length) {
      supports(j)(t(j))(x) |= Constants.MASK1(y)
    }
  }

  //�����Index
  val Ssup = new ArrayBuffer[Int](arity)
  val Sval = new ArrayBuffer[Int](arity)

  // ��ȡ��������С
  var maxDomainSize = Int.MinValue
  scope.foreach(x => {
    maxDomainSize = math.max(maxDomainSize, x.size())
  })

  // �ֲ��������
  //  val gacValue = new Array[SingleBitSet](arity)
  val newMask = Array.fill[Long](arity)(0L)
  val oldMask = Array.fill[Long](arity)(Constants.ALLONELONG)

  val lastRemovedValues = new ArrayBuffer[Int](maxDomainSize)
  lastRemovedValues.clear()
  val validValues = new ArrayBuffer[Int](maxDomainSize)
  lastRemovedValues.clear()

  // ��ʶ�Ƿ�Ϊ���δ��������δ���updateTableʱ����valid���£��ǳ��δ���updateTableʱ���ݱȽϽ��ȷ����
  var firstProp = true
  //  // ��ʼ��gacValue��lastMask
  //  var ii = 0
  //  while (ii < arity) {
  //    val v = scope(ii)
  //    //    gacValue(ii) = new SingleBitSet(v.size())
  //    lastMask(ii) = v.simpleMask()
  //    ii += 1
  //  }

  //������
  def initial(): Unit = {
    Ssup.clear()
    Sval.clear()

    var i = 0
    while (i < arity) {
      val v = scope(i)
      newMask(i) = v.simpleMask()

      if (newMask(i) != oldMask(i)) {
        Sval += i
      }

      if (v.unBind()) {
        Ssup += i
      }
      i += 1
    }
  }

  //����trueΪdelta���£�false��ͷ����
  @inline def getValues(vidx: Int, v: PVar): Boolean = {
    val lastRemovedMask: Long = (~newMask(vidx)) & oldMask(vidx)
    val valid = java.lang.Long.bitCount(newMask(vidx))
    val remove = java.lang.Long.bitCount(lastRemovedMask)

    // ����Ƿ���Ҫdelta����
    val needDelta: Boolean = remove < valid
    if (needDelta) {
      // delta����
      var i = 0
      lastRemovedValues.clear()
      while (i < v.capacity) {
        if ((lastRemovedMask & Constants.MASK1(i)) != 0L) {
          lastRemovedValues += i
        }
        i += 1
      }
    } else {
      // ��ͷ����
      var i = 0
      validValues.clear()
      while (i < v.capacity) {
        if ((newMask(vidx) & Constants.MASK1(i)) != 0L) {
          validValues += i
        }
        i += 1
      }
    }

    return needDelta
  }

  def updateTable(): Boolean = {
    //    //println(s"id:${id}-----------ut----------")
    var i = 0
    val SvalN = Sval.length
    while (i < SvalN && helper.isConsistent) {
      val vv: Int = Sval(i)
      val v: PVar = scope(vv)
      // ��Ϊinitial֮�󻹿�����ֵ��ɾ��
      newMask(vv) = v.simpleMask()
      currTab.clearMask()

      val lastRemovedMask: Long = (~newMask(vv)) & oldMask(vv)
      val valid = java.lang.Long.bitCount(newMask(vv))
      val remove = java.lang.Long.bitCount(lastRemovedMask)

      // ����Ƿ���Ҫdelta����
      if (remove < valid && !firstProp) {
        // delta����
        lastRemovedValues.clear()
        var j = 0
        while (j < v.capacity) {
          if ((lastRemovedMask & Constants.MASK1(j)) != 0L) {
            lastRemovedValues += j
            //            currTab.addToMask(supports(vv)(j))
          }
          j += 1
        }

        //        //println(s"cid: ${id}, vid: ${v.id}, lastRemovedValues: ", lastRemovedValues.mkString(","))
        for (a <- lastRemovedValues) {
          currTab.addToMask(supports(vv)(a))
        }
        currTab.reverseMask()
      }
      else {
        // ��ͷ����
        validValues.clear()
        var j = 0
        while (j < v.capacity) {
          if ((newMask(vv) & Constants.MASK1(j)) != 0L) {
            validValues += j
            //            currTab.addToMask(supports(vv)(j))
          }
          j += 1
        }
        //        //println(s"cid: ${id}, vid: ${v.id}, validValues: ", validValues.mkString(","))
        // ��ͷ����
        for (a <- validValues) {
          currTab.addToMask(supports(vv)(a))
        }
      }

      val changed = currTab.intersectWithMask()

      //����ʧ��
      if (currTab.isEmpty()) {
        helper.isConsistent = false
        return false
      }
      i += 1
    }
    // �״δ���updateTable���
    firstProp = false
    return true
  }

  def filterDomains(): Boolean = {
    //    //println(s"id:${id}-----------fd----------")
    val SsupN = Ssup.length
    var i = 0
    while (i < SsupN && helper.isConsistent) {
      var deleted: Boolean = false
      val vv: Int = Ssup(i)
      val v = scope(vv)

      validValues.clear()
      var j = 0
      while (j < v.capacity) {
        if ((newMask(vv) & Constants.MASK1(j)) != 0L) {
          validValues += j
        }
        j += 1
      }

      //      //println(s"cid: ${id}, vid: ${v.id}, validValues: ", validValues.mkString(","))
      for (a <- validValues) {
        var index = residues(vv)(a)
        if (index == -1 || (currTab.words(helper.level)(index) & supports(vv)(a)(index)) == 0L) { //resʧЧ
          index = currTab.intersectIndex(supports(vv)(a))
          if (index != -1) { //�����ҵ�֧��
            residues(vv)(a) = index
          }
          else {
            deleted = true
            //�޷��ҵ�֧��, ɾ��(v, a)
            newMask(vv) &= Constants.MASK0(a)
            //            //println("name: " + id + ", delete: " + v.id + "," + a + ", level: " + helper.level)
          }
        }
      }


      if (deleted) {
        helper.varStamp(v.id) = helper.globalStamp + 1
        v.submitMaskAndGet(newMask(vv))

        if (v.isEmpty()) {
          helper.isConsistent = false
          return false
        }

        // ���ﲻ�ܵ���newMask��Ϊ���ǻ���lastMask���µ�
        oldMask(vv) = newMask(vv)

        //        if (lastMask(vv) != v.simpleMask()) {
        //          conti = true
        //        }
      }
      i += 1
    }

    return true
  }

  override def propagate(): Boolean = {
    //    conti = true
    //    while (conti) {
    //      conti = false
    initial()
    if (!updateTable()) {
      return false
    }
    if (!filterDomains()) {
      return false
    }
    //    }
    return true
  }

  override def newLevel(): Unit = {
    level += 1
    currTab.newLevel(level)
  }

  override def backLevel(): Unit = {
    currTab.deleteLevel(level)
    level -= 1
    var i = 0
    while (i < arity) {
      oldMask(i) = scope(i).simpleMask()
      i += 1
    }
  }

  override def call(): Boolean = {
    if (helper.isConsistent) {
      //    //println(s"start: cur_ID: ${Thread.currentThread().getId()},cur_name: ${Thread.currentThread().getName()},cur_cid: ${id}
      return propagate()
      //    //println(s"end:   cur_ID: ${Thread.currentThread().getId()},cur_name: ${Thread.currentThread().getName()},cur_cid: ${id},propagate_res: ${res}")
    } else {
      return false
    }
//    if (!helper.isConsistent) {
//      return false
//    }
//
//    helper.searchState match {
//      case 0 => {
//        //println("setup")
//        setup()
//      };
//      case 1 => {
//        //println("newLevel")
//        newLevel()
//      };
//      case 2 => {
//        //println("propagate")
//        propagate()
//      };
//      case 3 => {
//        //println("backLevel")
//        backLevel()
//      };
//    }
//
//    return true
  }


}
