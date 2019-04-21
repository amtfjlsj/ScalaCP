package cpscala.TSolver.Model.Constraint.SConstraint

import cpscala.TSolver.CpUtil.Constants
import cpscala.TSolver.CpUtil.SearchHelper.SearchHelper
import cpscala.TSolver.Model.Variable.Var

import scala.collection.mutable.ArrayBuffer
import scala.util.control.Breaks._

/**
  * ����STRbit�ĵ�һ���汾��
  * ����Ԥ����ʱ����STRbitά������GAC��
  * ������������Ҳ����STRbitά������GAC��
  * �ο����ģ�2016_IJCAI_Optimizing Simple Table Reduction with Bitwise Representation
  */

class TableSTRbit_SSet(val id: Int, val arity: Int, val num_vars: Int, val scope: Array[Var], val tuples: Array[Array[Int]], val helper: SearchHelper) extends Propagator {

  // �����ӱ���ά���飬��һά�������ڶ�άȡֵ������άԪ��
  // ��ʼ������ʱ���������Ѿ������л�������[0, 1, ..., var.size()]�����Կ���ֱ����ȡֵ��Ϊ�±�
  private[this] val bitTables = Array.tabulate(arity)(i => new Array[Array[BitSupport]](scope(i).size()))
  // �ֽ������ά���飬��һά�������ڶ�άȡֵ
  private[this] val last = Array.tabulate(arity)(i => new Array[Int](scope(i).size()))
  // �ֽ��ջ
  // ����������ʼ�㣬������ֵ��last�ı��ˣ������±���ջ�����HashMap�����������룬0�㲻��Ҫ���棬��Ϊ1���Ӧ��ջ������ļ���0���ʼ��GAC�����Ϣ��
  // ���������ķǳ�ʼ�㣬������ֵ��last��һ�η����ı�ʱ�����ı�ǰ��lastֵ�����ڸñ���ջ����HashMap��
  // HashMap����ķ����е�һ��IntΪvalue���ڶ���IntΪlast
  //  private[this] val StackL = Array.fill(arity)(new RestoreStack[Int, Int](numVars))
  // ����һ��Array�Ƿ��HashMap�죨ȷʵ��һ�㣩
  private[this] val lastLevel = Array.fill[Array[Array[Int]]](num_vars + 1)(Array.tabulate(arity)(i => Array.fill[Int](scope(i).size())(-1)))

  private[this] val lengthTuple = tuples.length
  // �������ּ��㷽ʽ��ͬ
  private[this] val numBit = Math.ceil(lengthTuple.toDouble / Constants.BITSIZE.toDouble).toInt
  // ����Ԫ���������tupleLength���ܱ�64������ҪΪ��������һ������Ԫ��
  // private[this] val numBit = if (lengthTuple % 64 == 0) lengthTuple / 64 else lengthTuple / 64 + 1
  // ����Ԫ��ļ��ϣ�����Ԫ���ÿ������λ��¼��Ӧλ�õ�Ԫ���Ƿ���Ч
  private[this] val bitVal = Array.fill[Long](numBit)(-1L)
  // ���һ������Ԫ��ĩβ��0
  bitVal(numBit - 1) <<= Constants.BITSIZE - lengthTuple % Constants.BITSIZE
  // ����Ԫ��ջ
  // ����������ʼ�㣬������Ԫ��ı��ˣ�������ջ�����HashMap�����������룬0�㲻��Ҫ���棬��Ϊ1���Ӧ��ջ������ļ���0���ʼ��GAC�����Ϣ��
  // ���������ķǳ�ʼ�㣬������Ԫ���һ�η����ı�ʱ�����ı�ǰ�ı���Ԫ�鱣����ջ����HashMap��
  // HashMap����ķ�����IntΪts��LongΪmask
  //  private[this] val stackV = new RestoreStack[Int, Long](numVars)
  // ����һ��Array�Ƿ��HashMap�죨ȷʵ��һ�㣩
  private[this] val bitLevel = Array.fill[Long](num_vars + 1, numBit)(0L)

  // oldSize�����size֮���ֵ�Ǹ�Լ�����δ���֮�䱻���˵�ֵ��delta��
  // ��������ģ�Sparse-Sets for Domain Implementation
  private[this] val oldSizes = Array.tabulate(arity)(i => scope(i).size())
  private[this] val removeValues = new ArrayBuffer[Int]() //(delta)
  // ������ʣ����Чֵ
  private[this] val validValues = new ArrayBuffer[Int]()

  // ��ʼ����־����
  // isInitialΪfalse˵��setup�л�δ��ʼ��������ݽṹ
  // Ϊtrue˵����ʼ�����ݽṹ��ɣ����Խ��г�ʼɾֵ
  private[this] var isInitial = false

  override def setup(): Boolean = {

    if (!isInitial) {
      //println("c_id:" + id + " ===============>")

      // ��ʱ�����ӱ�, ʵ��֤��ArrayBuffer���HashMap���
      // ArrayBuffer��ʱ�临�Ӷ�O(trB), BΪ���ֲ���ʱ�临�Ӷ�O(log(t/w)), wΪ���������ĳ���
      // HashMap��ʱ�临�Ӷ�O(trH + drB), HΪScala���ù�ϣ�����ʱ�临�Ӷ�

      //    val tempBitTable = Array.tabulate(arity)(i => {
      //      Array.fill(scope(i).size())(new m.HashMap[Int, Long]())
      //    })

      val tempBitTable = Array.tabulate(arity)(i => {
        Array.fill(scope(i).size())(new ArrayBuffer[BitSupport]())
      })


      // ����ʱ�ӱ��ڶ�̬���Ԫ����
      var t = 0
      while (t < lengthTuple) {
        if (isValidTuple(tuples(t))) {
          // tsΪ����Ԫ���±�
          val ts = t / 64
          // indexΪ��ts������Ԫ����Ԫ���λ��
          val index = t % 64
          var i = 0
          while (i < arity) {
            val a = tuples(t)(i)
            //          val bitSupportsMap = tempBitTable(i)(a)
            //          if (!bitSupportsMap.contains(ts)) {
            //            bitSupportsMap(ts) = Constants.MASK1(index)
            //          } else {
            //            bitSupportsMap(ts) = bitSupportsMap(ts) | Constants.MASK1(index)
            //          }
            // �����۰����ʹ�ñ���ֵ�ı���֧�ְ���ŵ�������
            val bitSupportsArray = tempBitTable(i)(a)

            var low = 0
            var high = bitSupportsArray.length - 1
            var middle = 0
            var find = false
            breakable {
              while (low <= high) {
                middle = (low + high) / 2
                if (ts == bitSupportsArray(middle).ts) {
                  bitSupportsArray(middle).mask |= Constants.MASK1(index)
                  find = true
                  break
                } else if (ts < bitSupportsArray(middle).ts) {
                  high = middle - 1
                } else {
                  low = middle + 1
                }
              }
            }
            if (!find) {
              val loc = high + 1
              val bitSupport = new BitSupport(ts, Constants.MASK1(index))
              bitSupportsArray.insert(loc, bitSupport)
            }
            i += 1
          }
        }
        t += 1
      }

      //    val tempBitSupports = new ArrayBuffer[BitSupport]()
      var i = 0
      while (i < arity) {
        val x = scope(i)
        // ��Ϊ������δɾֵ������j��Ϊindex����Ϊȡֵ
        var j = x.size()
        while (j > 0) {
          j -= 1
          //        val bitSupportsMap = tempBitTable(i)(j)
          val tempBitSupports = tempBitTable(i)(j)
          //          tempBitSupports.clear()
          //          // �����۰����ʹ�ñ���ֵ�ı���֧�ְ���ŵ�������
          //          for ((ts, mask) <- bitSupportsMap) {
          //            //            //////////println(s"ts: ${ts} mask: ${mask.toBinaryString}")
          //            var low = 0
          //            var high = tempBitSupports.length - 1
          //            var middle = 0
          //            while (low <= high) {
          //              middle = (low + high) / 2
          //              if (ts < tempBitSupports(middle).ts) {
          //                high = middle - 1
          //              } else {
          //                low = middle + 1
          //              }
          //            }
          //            val index = high + 1
          //            val bitSupport = new BitSupport(ts, mask)
          //            tempBitSupports.insert(index, bitSupport)
          //          }
          bitTables(i)(j) = tempBitSupports.toArray
          last(i)(j) = tempBitSupports.length - 1
        }
        //      stackL(i).push()
        i += 1
      }
      // ��ʼ�����ݽṹ���
      isInitial = true
      return true
    }
    else {
      //println("c_id:" + id + " ===============>")
      var i = 0
      while (i < arity) {
        val x = scope(i)
        // j��Ϊȡֵ����Ϊ�±�
        var j = x.size()
        while (j > 0) {
          j -= 1
          // ��Ϊ���������ѱ�ɾֵ��������Ҫͨ���±�j��ȡ��value
          val value = x.get(j)
          if (bitTables(i)(value).isEmpty) {
            x.remove(value)
            helper.varStamp(x.id) = helper.globalStamp
            //println(s"       var:${x.id} remove new value:${value}")
          }
        }
        //      stackL(i).push()
        i += 1
        if (x.isEmpty()) {
          return false
        }
      }
    }
    //    stackV.push()
    return true
  }

  // ɾ����ЧԪ��
  def deleteInvalidTuple(): Unit = {

    for (i <- 0 until arity) {
      val x = scope(i)

      if (oldSizes(i) != x.size()) {
        // ���delta������oldSize
        removeValues.clear()
        oldSizes(i) = x.getLastRemovedValues(oldSizes(i).toLong, removeValues)
        //println(s"       var: ${x.id} dit removedValues: " + removeValues.mkString(", "))

        // Ѱ���µ���ЧԪ��
        for (a <- removeValues) {
          val old = last(i)(a)

          val bitSupports = bitTables(i)(a)
          for (l <- 0 to old) {
            val ts = bitSupports(l).ts
            val u = bitSupports(l).mask & bitVal(ts)

            // ������0��˵��bitΪ1��λ�ö�Ӧ��Ԫ���Ϊ��Ч
            if (u != 0L) {

              //              val topHashV = stackV.top
              //              if (!topHashV.contains(ts)) {
              //                topHashV(ts) = bitVal(ts)
              //              }
              // ����һ�θı�֮ǰ�ı���Ԫ���¼����
              if (bitLevel(level)(ts) == 0L) {
                bitLevel(level)(ts) = bitVal(ts)
              }
              // ���±���Ԫ��
              bitVal(ts) &= ~u
            }
          }
        }
      }
    }
  }

  // Ѱ��û��֧�ֵ�ֵ
  def searchSupport(evt: ArrayBuffer[Var]): Boolean = {

    for (i <- 0 until arity) {
      val v = scope(i)
      if (v.unBind()) {
        var deleted: Boolean = false
        validValues.clear()
        v.getValidValues(validValues)

        //println(s"       var: ${v.id} ss validValues: " + validValues.mkString(", "))

        for (a <- validValues) {
          val bitSupports = bitTables(i)(a)
          val old = last(i)(a)

          // Ѱ��֧�ֵı���Ԫ��
          var now = old
          while (now >= 0 && (bitSupports(now).mask & bitVal(bitSupports(now).ts)) == 0L) {
            now -= 1
          }

          if (now == -1) {
            deleted = true
            v.remove(a)
//            println(s"    cur_cid: ${id}, var: ${v.id}, remove val: ${a}")

            // ���²����������ﲻ�����ѭ������죬��Ϊɾ�˶��ֵ���ظ�ִ��
            //            oldSizes(i) -= 1
            //            evt += v
            //            if (v.isEmpty()) return false
          } else {
            if (now != old) {
              // ���±���ջ���Ĺ�ϣ��
              //            val topHashL = stackL(i).top
              //
              //            if (!topHashL.contains(a)) {
              //              topHashL(a) = old
              //            }
              // ����һ�θı�֮ǰ��last��¼����
              if (lastLevel(level)(i)(a) == -1) {
                lastLevel(level)(i)(a) = old
              }
              last(i)(a) = now
            }
          }
        }
        if (deleted) {
          if (v.isEmpty()) return false
          oldSizes(i) = v.size()
          evt += v
        }
      }
    }
    return true
  }

  override def propagate(evt: ArrayBuffer[Var]): Boolean = {

//    println(s"c_id: ${id} propagate==========================>")
//    val ditStart = System.nanoTime
    deleteInvalidTuple()
//    val ditEnd = System.nanoTime
//    helper.updateTableTime += ditEnd - ditStart

//    val ssStart = System.nanoTime
    val ss = searchSupport(evt)
//    val ssEnd = System.nanoTime
//    helper.filterDomainTime += ssEnd - ssStart

    return ss

  }

  // �²�
  def newLevel(): Unit = {
    level += 1
    // ��stackLѹ��һ���µ�HashMap����Ӧ�²㣩
    //    for (i <- 0 until arity) {
    //      stackL(i).push()
    //    }
    // ��stackVѹ��һ���µ�HashMap����Ӧ�²㣩
    //    stackV.push()

    // �����²���ø���oldSize��oldSize���ϲ㱣��һ��
  }

  // ����
  def backLevel(): Unit = {
    for (i <- 0 until arity) {
      //      val topHashL = stackL(i).pop
      // iΪ������ţ�aΪȡֵ��lΪ��Ӧ�ӱ��last
      //      for ((a, l) <- topHashL) {
      //      last(i)(a) = l
      for (a <- 0 until scope(i).capacity) {
        if (lastLevel(level)(i)(a) != -1) {
          last(i)(a) = lastLevel(level)(i)(a)
          lastLevel(level)(i)(a) = -1
        }
      }
      // ���ݺ�����oldSize���¾ɴ�С��ͬ����Ϊ��û�д���
      oldSizes(i) = scope(i).size()
    }

    // �ָ�bitVal
    //    val topHashV = stackV.pop
    //    for ((ts, mask) <- topHashV) {
    //      bitVal(ts) = mask
    //    }
    for (ts <- 0 until numBit) {
      if (bitLevel(level)(ts) != 0L) {
        bitVal(ts) = bitLevel(level)(ts)
        bitLevel(level)(ts) = 0L
      }
    }

    level -= 1
  }

  override def stepUp(num_vars: Int): Unit = ???

  override def isEntailed(): Boolean = ???

  override def isSatisfied(): Unit = ???

  // ��Ԫ����Ч���򷵻���
  @inline private def isValidTuple(tuple: Array[Int]): Boolean = {
    var i = arity
    while (i > 0) {
      i -= 1
      if (!scope(i).contains(tuple(i))) return false
    }
    return true
  }
}

class BitSupport(val ts: Int, var mask: Long) {

}