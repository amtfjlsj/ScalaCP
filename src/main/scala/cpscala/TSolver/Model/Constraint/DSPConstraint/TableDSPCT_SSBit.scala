package cpscala.TSolver.Model.Constraint.DSPConstraint

import cpscala.TSolver.CpUtil.SearchHelper.DSPSearchHelper
import cpscala.TSolver.CpUtil.{Constants, INDEX, RSBitSet}
import cpscala.TSolver.Model.Variable.PVar

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

class TableDSPCT_SSBit(val id: Int, val arity: Int, val num_vars: Int, val scope: Array[PVar], val tuples: Array[Array[Int]], val helper: DSPSearchHelper) extends DSPPropagator {
  val currTab = new RSBitSet(id, tuples.length, num_vars)
  val supports = new Array[Array[Array[Long]]](arity)
  val num_bit = currTab.num_bit
  val residues = new Array[Array[Int]](arity)
  // �����
  val Xevt = new ArrayBuffer[PVar](arity)
  Xevt.clear()

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

  val scopeMap = new mutable.HashMap[PVar, Int]()
  for (i <- 0 until arity) {
    scopeMap.put(scope(i), i)
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
  // 1. newMask: ԭ���ύ���õ�����
  // 1. oldMask: ԭ���ύǰ��õ�����
  // 2. localMask����ǰ����ʱ�����޸ĵ������mask
  // 3. lastMask����һ�ε��ú��mask
  val localMask = Array.fill[Long](arity)(0L)
  val lastMask = Array.fill[Long](arity)(Constants.ALLONELONG)

  val lastRemovedValues = new ArrayBuffer[Int](maxDomainSize)
  lastRemovedValues.clear()
  val validValues = new ArrayBuffer[Int](maxDomainSize)
  validValues.clear()

  //�ж�domain�Ƿ�ı�
  override def domainChanged(v: PVar, mask: Array[Long]): Boolean = {
    //    mask == lastMask(scopeMap(v))
    return true
  }

  //������
  def initial(): Boolean = {

    Ssup.clear()
    Sval.clear()
    // ���SVal�Ƿ�Ϊ�գ�Ϊ��������propagate
    var snapshotChanged = false

    var i = 0
    while (i < arity) {
      val v = scope(i)
      val globalMask = v.simpleMask()

      // �������������ȫ������ͬ
      // ���±����������
      // snapshotChanged ��Ϊ��Ҫpropagate��������propagate
      if (lastMask(i) != globalMask) {
        Sval += i
        //        localMask(i) = globalMask
        snapshotChanged = true
      }

      if (v.unBind()) {
        Ssup += i
      }
      i += 1
    }
    return snapshotChanged
  }

  def updateTable(): Boolean = {
    var i = 0
    val SvalN = Sval.length
    while (i < SvalN && helper.isConsistent) {
      val vv: Int = Sval(i)
      val v: PVar = scope(vv)
      localMask(vv) = v.simpleMask()
      currTab.clearMask()

      val lastRemovedMask: Long = (~localMask(vv)) & lastMask(vv)
      //      val valid = java.lang.Long.bitCount(lastMask(vv))
      val valid = java.lang.Long.bitCount(localMask(vv))
      val remove = java.lang.Long.bitCount(lastRemovedMask)
      lastMask(vv) = localMask(vv)

      // !!����Ƿ���Ҫdelta����
      //      if ((old - last) < last) {
      if (remove < valid) {
        // delta����
        lastRemovedValues.clear()
        var j = 0
        while (j < v.capacity) {
          if ((lastRemovedMask & Constants.MASK1(j)) != 0L) {
            lastRemovedValues += j
          }
          j += 1
        }

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
          if ((localMask(vv) & Constants.MASK1(j)) != 0L) {
            validValues += j
          }
          j += 1
        }
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

    return true
  }

  // �������»��Xevt
  def filterDomains(): Boolean = {
    Xevt.clear()
    val SsupN = Ssup.length
    var i = 0
    while (i < SsupN && helper.isConsistent) {
      var deleted: Boolean = false
      val vv: Int = Ssup(i)
      val v = scope(vv)

      validValues.clear()
      var j = 0
      while (j < v.capacity) {
        if ((localMask(vv) & Constants.MASK1(j)) != 0L) {
          validValues += j
        }
        j += 1
      }

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
            //            println("name: " + id + ", delete: " + v.id + "," + a + ", level: " + helper.level)
            localMask(vv) &= Constants.MASK0(a)
          }
        }
      }

      if (deleted) {
        val newMask = v.submitMaskAndGet(localMask(vv))
        // �����߳�ɾֵ
        // �ύ���ģ�����ȡ��ֵ
        if (newMask == 0L) {
          helper.isConsistent = false
          return false
        }

        lastMask(vv) = localMask(vv)
        Xevt += v
      }
      i += 1
    }
    return true
  }

  def submitPropagtors(): Boolean = {
    // �ύ����Լ��
    for (x <- Xevt) {
      if (helper.isConsistent) {
        for (c <- helper.subscription(x.id)) {
          // !!������Լ���������c.v.simpleMask!=x.simpleMask
          //          if (c.id != id && c.domainChanged(x, localMask(scopeMap(x)))) {
          if (c.id != id) {
            helper.submitToPool(c)
          }
        }
      }
    }
    return false
  }

  override def run(): Unit = {
    //    println(s"start: cur_ID: ${Thread.currentThread().getId()},cur_name: ${Thread.currentThread().getName()},cur_cid: ${id}")
    do {
      helper.c_prop.incrementAndGet()
      // ��ִ�й���֮ǰ������Ϊ1

      //      if (runningStatus.get() == 0) {
      //        return
      //      }

      runningStatus.set(1)

      if (propagate()) {
        submitPropagtors()
      }

      // !!ԭ�Ӽ�����ٽ���
      // �������ƻ�ȡ����״̬
      //      lock.lock()
      //      try {
      //        if (runningStatus.get() > 1 && helper.isConsistent) {
      //          // ���Ҫ���е��������1�� ѭ�������
      //          loopContinue = true
      //          runningStatus.set(1)
      //        } else {
      //          // ���Ҫ���е��������1����˵��ֻ�е�ǰ�������ڵ�ǰ�����Ѿ����ѭ��Ӧ�˳�
      //          loopContinue = false
      //          runningStatus.set(0)
      //        }
      //      }
      //      finally {
      //        lock.unlock()
      //      }

    } while (!runningStatus.compareAndSet(1, 0))
    helper.c_sub.decrementAndGet()
  }

  override def propagate(): Boolean = {
    if (initial()) {
      if (updateTable()) {
        if (filterDomains()) {
          return true
        }
      }
    }
    return false
  }

  override def newLevel(): Unit = {
    level += 1
    currTab.newLevel(level)
    // ����runningStatus
    runningStatus.set(0)
  }

  override def backLevel(): Unit = {
    currTab.deleteLevel(level)
    level -= 1
    var i = 0
    while (i < arity) {
      localMask(i) = scope(i).simpleMask()
      lastMask(i) = localMask(i)
      i += 1
    }
    // ����runningStatus
    runningStatus.set(0)
  }

  override def domainChanged(v: PVar): Boolean = ???
}
