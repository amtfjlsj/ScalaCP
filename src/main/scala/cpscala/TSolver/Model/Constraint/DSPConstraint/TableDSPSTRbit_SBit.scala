package cpscala.TSolver.Model.Constraint.DSPConstraint

import cpscala.TSolver.Model.Constraint.SConstraint.BitSupport
import cpscala.TSolver.CpUtil.SearchHelper.DSPSearchHelper
import cpscala.TSolver.CpUtil.{Constants, INDEX}
import cpscala.TSolver.Model.Variable.PVar

import scala.collection.mutable.ArrayBuffer
import scala.util.control.Breaks._

/**
  * ����DSPSTRbitʹ��SafeBitVar��Ϊ�������͵İ汾�����Դ������������С�ı�����
  * ����Ԥ����ʱ����STRbitά������GAC��
  * ������������Ҳ����STRbitά������GAC��
  */

class TableDSPSTRbit_SBit(val id: Int, val arity: Int, val numVars: Int, val scope: Array[PVar], val tuples: Array[Array[Int]], val helper: DSPSearchHelper) extends DSPPropagator {

  // �����ӱ���ά���飬��һά�������ڶ�άȡֵ������άԪ��
  // ��ʼ������ʱ���������Ѿ������л�������[0, 1, ..., var.size()]�����Կ���ֱ����ȡֵ��Ϊ�±�
  private[this] val bitTables = Array.tabulate(arity)(i => new Array[Array[BitSupport]](scope(i).size()))
  // �ֽ������ά���飬��һά�������ڶ�άȡֵ
  private[this] val last = Array.tabulate(arity)(i => new Array[Int](scope(i).size()))
  // �ֽ��ջ
  // ����������ʼ�㣬������ֵ��last�ı��ˣ������±���ջ�����Array�����������룬0�㲻��Ҫ���棬��Ϊ1���Ӧ��ջ������ļ���0���ʼ��GAC�����Ϣ��
  // ���������ķǳ�ʼ�㣬������ֵ��last��һ�η����ı�ʱ�����ı�ǰ��lastֵ�����ڸñ���ջ����Array��
  private[this] val lastLevel = Array.fill[Array[Array[Int]]](numVars + 1)(Array.tabulate(arity)(i => Array.fill[Int](scope(i).size())(-1)))

  private[this] val lengthTuple = tuples.length
  // ����Ԫ���������tupleLength���ܱ�64������ҪΪ��������һ������Ԫ��
  private[this] val numBit = Math.ceil(lengthTuple.toDouble / Constants.BITSIZE.toDouble).toInt
  // ����Ԫ��ļ��ϣ�����Ԫ���ÿ������λ��¼��Ӧλ�õ�Ԫ���Ƿ���Ч
  private[this] val bitVal = Array.fill[Long](numBit)(-1L)
  // ���һ������Ԫ��ĩβ��0
  bitVal(numBit - 1) <<= Constants.BITSIZE - lengthTuple % Constants.BITSIZE
  // ����Ԫ��ջ
  // ����������ʼ�㣬������Ԫ��ı��ˣ�������ջ�����Array�����������룬0�㲻��Ҫ���棬��Ϊ1���Ӧ��ջ������ļ���0���ʼ��GAC�����Ϣ��
  // ���������ķǳ�ʼ�㣬������Ԫ���һ�η����ı�ʱ�����ı�ǰ�ı���Ԫ�鱣����ջ����Array��
  private[this] val bitLevel = Array.fill[Long](numVars + 1, numBit)(0L)

  // �����ı��������
  private[this] val varNumBit: Array[Int] = Array.tabulate[Int](arity)(i => scope(i).getNumBit())
  // lastMask�����Mask��ͬ��ֵ�Ǹ�Լ�����δ���֮�䱻���˵�ֵ��delta��
  private[this] val lastMask = Array.tabulate(arity)(i => new Array[Long](varNumBit(i)))
  // ��Լ��������ʼʱlocalMask��ȡ�������µ�mask
  private[this] val localMask = Array.tabulate(arity)(i => new Array[Long](varNumBit(i)))
  // ��¼��Լ�����δ���֮��ɾֵ��mask
  private[this] val removeMask = Array.tabulate(arity)(i => new Array[Long](varNumBit(i)))
  // delta
  private[this] val removeValues = new ArrayBuffer[Int]()
  // ������ʣ����Чֵ
  private[this] val validValues = new ArrayBuffer[Int]()

  // ��ʼ����־����
  // isInitialΪfalse˵��setup�л�δ��ʼ��������ݽṹ
  // Ϊtrue˵����ʼ�����ݽṹ��ɣ����Խ��г�ʼɾֵ
  private[this] var isInitial = false

  // �������ı�ı�����
  private val Xevt = new ArrayBuffer[PVar](arity)
  Xevt.clear()

  override def setup(): Unit = {

    if (!isInitial) {
      //      println(s"cons: ${id} setup ===============>")

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

      var i = 0
      while (i < arity) {
        val v = scope(i)
        v.mask(localMask(i))
        // ��Ϊ������δɾֵ������j��Ϊindex����Ϊȡֵ
        var j = v.size()
        while (j > 0) {
          j -= 1
          val tempBitSupports = tempBitTable(i)(j)
          bitTables(i)(j) = tempBitSupports.toArray
          last(i)(j) = tempBitSupports.length - 1
          if (tempBitSupports.isEmpty) {
            // ���bitɾֵ������mask��ֵj��Ӧ��bitλ����Ϊ0
            val (x, y) = INDEX.getXY(j)
            localMask(i)(x) &= Constants.MASK0(y)
            helper.varIsChange.set(true)
          }
        }
        i += 1
      }
      // ��ʼ�����ݽṹ���
      isInitial = true
    }
    else {
      var i = 0
      while (i < arity) {
        val v = scope(i)
        // ���±�������
        v.submitMask(localMask(i))
        if (v.isEmpty()) {
          helper.isConsistent = false
          return
        }

        // ����lastMask
        var j = 0
        while (j < varNumBit(i)) {
          lastMask(i)(j) = localMask(i)(j)
          j += 1
        }

        i += 1
      }
    }
  }

  // ɾ����ЧԪ��
  def deleteInvalidTuple(): Unit = {

    var i = 0
    while (i < arity && helper.isConsistent) {
      val v = scope(i)
      v.mask(localMask(i))

      // �����¾�mask�ıȽ�ȷ���Ƿ���ɾֵ
      var diff = false
      var j = 0
      while (j < varNumBit(i)) {
        // ���Ƚ�removeMask��գ��������գ���ô����lastMask��localMask��ȵ������removeMask��Ȼά��ԭ������ԭ����ȫ0��������
        removeMask(i)(j) = 0L
        // �����¾�mask�ıȽ�ȷ���Ƿ���ɾֵ
        if (lastMask(i)(j) != localMask(i)(j)) {
          removeMask(i)(j) = (~localMask(i)(j)) & lastMask(i)(j)
          // ����lastMasks
          lastMask(i)(j) = localMask(i)(j)
          diff = true
        }
        j += 1
      }

      if (diff) {
        // ����ɾֵ������delta
        Constants.getValues(removeMask(i), removeValues)
        // Ѱ���µ���ЧԪ��
        for (a <- removeValues) {
          val old = last(i)(a)
          val bitSupports = bitTables(i)(a)

          for (l <- 0 to old) {
            val ts = bitSupports(l).ts
            val u = bitSupports(l).mask & bitVal(ts)

            // ������0��˵��bitΪ1��λ�ö�Ӧ��Ԫ���Ϊ��Ч
            if (u != 0L) {
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
      i += 1
    }
  }

  // Ѱ��û��֧�ֵ�ֵ
  def searchSupport(): Boolean = {

    Xevt.clear()
    var i = 0
    while (i < arity && helper.isConsistent) {
      val v = scope(i)

      if (v.unBind()) {
        var deleted = false
        Constants.getValues(localMask(i), validValues)

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
            // ���bitɾֵ������mask��ֵvalue��Ӧ��bitλ����Ϊ0
            val (x, y) = INDEX.getXY(a)
            localMask(i)(x) &= Constants.MASK0(y)
            //            println(s"    cur_cid: ${id}, var: ${v.id}, remove val: ${a}")
          } else {
            if (now != old) {
              // ����һ�θı�֮ǰ��last��¼����
              if (lastLevel(level)(i)(a) == -1) {
                lastLevel(level)(i)(a) = old
              }
              last(i)(a) = now
            }
          }
        }
        if (deleted) {
          val changed = v.submitMask(localMask(i))
          if (v.isEmpty()) {
            helper.isConsistent = false
            return false
          }

          if (changed) {
            Xevt += v
          }

          // ����lastMask
          var j = 0
          while (j < varNumBit(i)) {
            lastMask(i)(j) = localMask(i)(j)
            j += 1
          }
        }
      }
      i += 1
    }
    return true
  }

  def propagate(): Boolean = {

    deleteInvalidTuple()

    return searchSupport()
  }

  def submitPropagtors(): Boolean = {
    //    println(s"    cur_ID: ${Thread.currentThread().getId()} cons: ${id}  submit, its Xevt size: ${Xevt.length}")
    // �ύ����Լ��
    for (x <- Xevt) {
      if (helper.isConsistent) {
        for (c <- helper.subscription(x.id)) {
          // !!������Լ���������c.v.simpleMask!=x.simpleMask
          if (c.id != id) {
            helper.submitToPool(c)
          }
        }
      }
    }
    return false
  }

  override def run(): Unit = {
    //    println(s"start: cur_ID: ${Thread.currentThread().getId()}, cons: ${id} =========>")

    do {
      helper.c_prop.incrementAndGet()
      runningStatus.set(1)
      if (propagate()) {
        submitPropagtors()
      }
    } while (!runningStatus.compareAndSet(1, 0))
    helper.c_sub.decrementAndGet()

    // ���°汾���ȶ����ݲ����ǣ�ֻ�ǶԴ������̼���
    //    helper.searchState match {
    //      case 0 => {
    //        //        println("setup")
    //        setup()
    //      };
    //      case 1 => {
    //        //        println("newLevel")
    //        newLevel()
    //      };
    //      case 2 => {
    //        //        println(s"c_id: ${id} propagate==========================>")
    //        do {
    //          helper.c_prop.incrementAndGet()
    //          runningStatus.set(1)
    //          if (propagate() && Xevt.nonEmpty) {
    //            submitPropagtors()
    //          }
    //        } while (!runningStatus.compareAndSet(1, 0))
    //      };
    //      case 3 => {
    //        //        println("backLevel")
    //        backLevel()
    //      };
    //    }
    //
    //    helper.c_sub.decrementAndGet()
    //    runningStatus.set(0)
  }

  // �²�
  def newLevel(): Unit = {
    level += 1

    // �����²���ø���oldSize��oldSize���ϲ㱣��һ��
  }

  // ����
  def backLevel(): Unit = {
    for (i <- 0 until arity) {
      for (a <- 0 until scope(i).capacity) {
        if (lastLevel(level)(i)(a) != -1) {
          last(i)(a) = lastLevel(level)(i)(a)
          lastLevel(level)(i)(a) = -1
        }
      }
      // ���ݺ�����oldMasks���¾�mask��ͬ����Ϊ��û�д���
      scope(i).mask(lastMask(i))
    }

    for (ts <- 0 until numBit) {
      if (bitLevel(level)(ts) != 0L) {
        bitVal(ts) = bitLevel(level)(ts)
        bitLevel(level)(ts) = 0L
      }
    }

    level -= 1
  }

  // ��Ԫ����Ч���򷵻���
  @inline private def isValidTuple(tuple: Array[Int]): Boolean = {
    var i = arity
    while (i > 0) {
      i -= 1
      if (!scope(i).contains(tuple(i))) return false
    }
    return true
  }

  override def domainChanged(v: PVar): Boolean = ???

  override def domainChanged(v: PVar, mask: Array[Long]): Boolean = ???

}
