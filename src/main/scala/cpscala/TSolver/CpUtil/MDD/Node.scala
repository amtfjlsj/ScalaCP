package cpscala.TSolver.CpUtil.MDD

import scala.collection.mutable.ArrayBuffer

class Node(val name: Long, val level: Int) {
  val incomes: ArrayBuffer[Arc] = new ArrayBuffer[Arc]()
  val outcomes: ArrayBuffer[Arc] = new ArrayBuffer[Arc]()
  var active: Boolean = true

  def addIncome(arc: Arc): Unit = {
    incomes += arc
  }

  def addOutcome(label: Int, destination: Node): Arc = {
    val arc = new Arc(label, this, destination)
    outcomes += arc
    return arc
  }

  def addOutcome(arc: Arc) = {
    outcomes += arc
  }

  def deactivate(): Unit = {
    active = false
  }

  def activate() = {
    active = true
  }

  def isActive() = active

  def merge(other: Node): Unit = {
    //# 1. ��other�ϲ���self�У�otherӦ�ñ�ɾȥ
    //# 2. ������incomes��list�ϲ�
    //# 3. �ڵ�deactivate
    //# 4. �ڵ�ɾ������MDD������
    //# 6. ����other.incomes���еĻ�arc��end��ָ��self�ڵ�
    //# 7. ����other.outcomes���еĻ�arc��end�ڵ��incomesɾ�������arc
    //        if (!(equal(other)))
    // ��������һ����,��name��ͬ,
    // ���ϲ�,ֻ�еȼ�ʱ�źϲ�
    if (name != other.name) {
      // �ϲ���other�ڵ��incomes��
      // other.incomes��ÿ������end�ڵ��ض���Ϊ��ǰ�ڵ㲢���뵽this.incomes��
      for (arc <- other.incomes) {
        arc.end = this
        incomes += arc
      }

      // ���other�ڵ������income�ڵ�,��delete other �ڵ�ʱ����ɾ����Щincome-arc
      other.incomes.clear()
      // ������other
      other.deactivate()
    }
  }

  def show(seperator: String): Unit = ???
}
