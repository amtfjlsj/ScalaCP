package cpscala.TSolver.Model.Constraint.IPConstraint

import cpscala.TSolver.CpUtil.SearchHelper.IPSearchHelper
import cpscala.TSolver.CpUtil._
import cpscala.TSolver.Model.Variable.PVar

import scala.collection.mutable.ArrayBuffer

class TableIPSTR2_SSBit(val id: Int, val arity: Int, val num_vars: Int, val scope: Array[PVar], val tuples: Array[Array[Int]], val helper: IPSearchHelper) extends IPPropagator {
  val position = Array.range(0, tuples.length)
  // �ֲ�
  val gacValue = new Array[SingleBitSet](arity)

  val levelLimits = Array.fill(num_vars + 1)(-1)
  levelLimits(0) = tuples.length - 1
  //�����Index
  val Ssup = new ArrayBuffer[Int](arity)
  val Sval = new ArrayBuffer[Int](arity)
  //  val lastsize = Array.fill(arity)(-1)
  //���lastsize������v.simpleMask��һ������Ҫ����Sval
  val lastMask = Array.fill(arity)(0L)
  level = 0

  // ��ʼ��gacvalue
  var ii = 0
  while (ii < arity) {
    val v = scope(ii)
    gacValue(ii) = new SingleBitSet(v.size())
    lastMask(ii) = v.simpleMask()
    ii += 1
  }

  //������
  def initial(): Unit = {
    Ssup.clear()
    Sval.clear()

    var i = 0
    while (i < arity) {
      val v = scope(i)
      gacValue(i).clear()

      val mask = v.simpleMask()
      if (lastMask(i) != mask) {
        Sval += i
        lastMask(i) = mask
      }

      if (v.unBind()) {
        Ssup += i
      }
      i += 1
    }
  }

  // �����ֵ�ӵ�gacValue��
  def updateTable(): Boolean = {
    var i = levelLimits(level)
    while (i >= 0) {
      if (!helper.isConsistent) {
        return false
      }
      val index = position(i)
      val t = tuples(index)

      if (isValidTuple(t)) {
        var j = 0
        while (j < Ssup.length) {
          val vv = Ssup(j)
          val v = scope(vv)
          val a = t(vv)

          gacValue(vv).add(a)
          // Ӧ�ñȽ�lastmask�����ǵ�ǰ��v.simplemask�����߳��ǿ��Աȵģ����Ƕ��̲߳�����
          //          if (gacValue(vv).mask() == v.simpleMask()){
          if (gacValue(vv).mask() == lastMask(vv)) {
            //            printf("remove from cid: %d, var: %d\n", id, v.id)
            val lastPos = Ssup.length - 1
            //�Ƚ�Ssup�����һ��Ԫ�ظ��Ƶ���ǰjλ��
            Ssup(j) = Ssup(lastPos)
            //�ٽ����һ��Ԫ��ɾ���������ܽ�Լʱ��
            Ssup.remove(lastPos)
            j -= 1
          }
          j += 1
        }
      } else {
        //        println("id: " + id + ", remove: " + t.mkString(","))
        removeTuples(i, level)
        // ֻ�иĶ����ʱ��Ż�Ķ�ʱ���
        helper.tabStamp(id) = helper.globalStamp
      }
      i -= 1
    }
    true
  }

  def filterDomains(): Boolean = {
    var i = 0
    val ssupN = Ssup.length

    while (i < ssupN) {
      val vv: Int = Ssup(i)
      val v = scope(vv)
      val mask = gacValue(vv).mask()
      val newMask = v.submitMaskAndGet(mask)
      //      printf("cid: %d, var: %d, fd:%64s~%64s~%64s\n", id, v.id, mask.toBinaryString, newMask.toBinaryString, lastMask(vv).toBinaryString)

      // ��������ı䣬ʱ�������Ϊȫ��ʱ���+1
      //      if (lastMask(vv) != newMask) {
      //      val newMask = v.simpleMask()
      if (lastMask(vv) != newMask) {
        // ���������޸ģ���ȫ��ʱ�����1
        helper.varStamp(v.id) = helper.globalStamp + 1
        // ����Ϊ�շ���false
        // ������newmask����
        // һ��Ҫ�ӱ���ȡ��
        if (v.simpleMask() == 0L) {
          //          println(s"fail: cid: ${id}, vid:${v.id}")
          helper.isConsistent = false
          return false
        }

        //���±����ڸ�Լ���ڵ�lastmask
        //        lastMask(vv) = newMask
        lastMask(vv) = mask
      }
      i += 1
    }
    return true
  }

  // !!����鿴ȫ�ֵı���ֵ�Ƿ���ڣ���û���ÿ��յģ���Ӱ���㷨��ȷ��
  // !!�����ڱ�֤���г����޴������� ���������㷨Ч��
  def isValidTuple(t: Array[Int]): Boolean = {
    for (vidx <- Sval) {
      if (!scope(vidx).contains(t(vidx))) {
        return false
      }
    }
    return true
  }

  def removeTuples(i: Int, p: Int): Unit = {
    val tmp = position(i)
    position(i) = position(levelLimits(p))
    position(levelLimits(p)) = tmp
    levelLimits(p) -= 1
  }

  override def propagate(): Boolean = {
    initial()
    if (!updateTable())
      return false
    return filterDomains()
  }

  override def newLevel(): Unit = {
    //      currentLimit = levelLimits(tabLevel)
    levelLimits(level + 1) = levelLimits(level)
    level += 1
  }

  override def backLevel(): Unit = {
    levelLimits(level) = -1
    level -= 1
    for (i <- 0 until arity) {
      lastMask(i) = scope(i).simpleMask()
    }
  }

  def show(): Unit = {
    //      println()
  }

  def call(): Boolean = {
    //    println(s"start: cur_ID: ${Thread.currentThread().getId()},cur_name: ${Thread.currentThread().getName()},cur_cid: ${id}")
    //    if (!helper.isConsistent) {
    //      return false
    //    }
    val res = propagate()
    //    println(s"end:   cur_ID: ${Thread.currentThread().getId()},cur_name: ${Thread.currentThread().getName()},cur_cid: ${id},propagate_res: ${res}")
    return res
  }
}
