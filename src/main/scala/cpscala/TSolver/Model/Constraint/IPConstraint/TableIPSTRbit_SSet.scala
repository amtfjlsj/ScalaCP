package cpscala.TSolver.Model.Constraint.IPConstraint

import cpscala.TSolver.Model.Constraint.SConstraint.BitSupport
import cpscala.TSolver.CpUtil.Constants
import cpscala.TSolver.CpUtil.SearchHelper.IPSearchHelper
import cpscala.TSolver.Model.Variable.PVar

import scala.collection.mutable.ArrayBuffer
import scala.util.control.Breaks._

/**
  * ����PSTRbit�ĵ�һ���汾����������ʹ��SparseSet
  * ����Ԥ����ʱ����STRbitά������GAC��
  * ������������Ҳ����STRbitά������GAC��
  */

class TableIPSTRbit_SSet(val id: Int, val arity: Int, val num_vars: Int, val scope: Array[PVar], val tuples: Array[Array[Int]], val helper: IPSearchHelper) extends IPPropagator {

  // �����ӱ���ά���飬��һά�������ڶ�άȡֵ������άԪ��
  // ��ʼ������ʱ���������Ѿ������л�������[0, 1, ..., var.size()]�����Կ���ֱ����ȡֵ��Ϊ�±�
  private[this] val bitTables = Array.tabulate(arity)(i => new Array[Array[BitSupport]](scope(i).size()))
  // �ֽ������ά���飬��һά�������ڶ�άȡֵ
  private[this] val last = Array.tabulate(arity)(i => new Array[Int](scope(i).size()))
  // �ֽ��ջ
  // ����������ʼ�㣬������ֵ��last�ı��ˣ������±���ջ�����Array�����������룬0�㲻��Ҫ���棬��Ϊ1���Ӧ��ջ������ļ���0���ʼ��GAC�����Ϣ��
  // ���������ķǳ�ʼ�㣬������ֵ��last��һ�η����ı�ʱ�����ı�ǰ��lastֵ�����ڸñ���ջ����Array��
  private[this] val lastLevel = Array.fill[Array[Array[Int]]](num_vars + 1)(Array.tabulate(arity)(i => Array.fill[Int](scope(i).size())(-1)))

  private[this] val tupleLength = tuples.length
  // ����Ԫ���������tupleLength���ܱ�64������ҪΪ��������һ������Ԫ��
  private[this] val bitNum = if (tupleLength % 64 == 0) tupleLength / 64 else tupleLength / 64 + 1
  // ����Ԫ��ļ��ϣ�����Ԫ���ÿ������λ��¼��Ӧλ�õ�Ԫ���Ƿ���Ч
  private[this] val bitVal = Array.fill[Long](bitNum)(-1L)
  // ���һ������Ԫ��ĩβ��0
  bitVal(bitNum - 1) <<= 64 - tupleLength % 64
  // ����Ԫ��ջ
  // ����������ʼ�㣬������Ԫ��ı��ˣ�������ջ�����Array�����������룬0�㲻��Ҫ���棬��Ϊ1���Ӧ��ջ������ļ���0���ʼ��GAC�����Ϣ��
  // ���������ķǳ�ʼ�㣬������Ԫ���һ�η����ı�ʱ�����ı�ǰ�ı���Ԫ�鱣����ջ����Array��
  private[this] val bitLevel = Array.fill[Long](num_vars + 1, bitNum)(0L)

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

  override def setup(): Unit = {

    if (!isInitial) {
      //println("c_id:" + id + " ===============>")

      val tempBitTable = Array.tabulate(arity)(i => {
        Array.fill(scope(i).size())(new ArrayBuffer[BitSupport]())
      })


      // ����ʱ�ӱ��ڶ�̬���Ԫ����
      var t = 0
      while (t < tupleLength) {
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
        val x = scope(i)
        // ��Ϊ������δɾֵ������j��Ϊindex����Ϊȡֵ
        var j = x.size()
        while (j > 0) {
          j -= 1
          val tempBitSupports = tempBitTable(i)(j)
          bitTables(i)(j) = tempBitSupports.toArray
          last(i)(j) = tempBitSupports.length - 1
        }
        i += 1
      }
      // ��ʼ�����ݽṹ���
      isInitial = true
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
            x.safeRemove(value)
            helper.varStamp(x.id) = helper.globalStamp + 1
            //println(s"       var:${x.id} remove new value:${j}")
          }
        }
        i += 1
        if(x.isEmpty()) {
          helper.isConsistent = false
          return
        }
      }
    }
  }

  // ɾ����ЧԪ��
  def deleteInvalidTuple(): Unit = {

    for (i <- 0 until arity) {
      val x = scope(i)

      if (oldSizes(i) != x.size()) {
        // ���delta������oldSize
        removeValues.clear()
        oldSizes(i) = x.getLastRemovedValues(oldSizes(i).toLong, removeValues)

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
    }
  }

  // Ѱ��û��֧�ֵ�ֵ
  def searchSupport(): Boolean = {

    for (i <- 0 until arity) {
      val v = scope(i)

      if (v.unBind()) {
        validValues.clear()
        v.getValidValues(validValues)

        for (a <- validValues) {
          val bitSupports = bitTables(i)(a)
          val old = last(i)(a)
          // Ѱ��֧�ֵı���Ԫ��
          var now = old

          while (now >= 0 && (bitSupports(now).mask & bitVal(bitSupports(now).ts)) == 0L) {
            now -= 1
          }

          if (now == -1) {
            v.safeRemove(a)
            // ��Ϊ�ǲ��в�ͬ�ڴ��У�����ֶ��Լ��ɾ��ͬһ��������ֵͬ��������������ﲻ����oldSize-1
            //            oldSizes(varId) -= 1
            // ���������޸ģ���ȫ��ʱ�����1
            helper.varStamp(v.id) = helper.globalStamp + 1

            if (v.isEmpty()) {
              helper.isConsistent = false
              return false
            }
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
      }
    }
    return true
  }

  def propagate(): Boolean = {

    deleteInvalidTuple()

    return searchSupport()
  }

  def call(): Boolean = {
    //    //println(s"start: cur_ID: ${Thread.currentThread().getId()},cur_name: ${Thread.currentThread().getName()},cur_cid: ${id}")
    //    if (!helper.isConsistent) {
    //      return false
    //    }
    if (!helper.isConsistent) {
      return false
    }

    helper.searchState match {
      case 0 => {
        //println("setup")
        setup()
      };
      case 1 => {
        //println("newLevel")
        newLevel()
      };
      case 2 => {
        //println("propagate")
        propagate()
      };
      case 3 => {
        //println("backLevel")
        backLevel()
      };
    }

    return true
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
      // ���ݺ�����oldSize���¾ɴ�С��ͬ����Ϊ��û�д���
      oldSizes(i) = scope(i).size()
    }

    for (ts <- 0 until bitNum) {
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
}
