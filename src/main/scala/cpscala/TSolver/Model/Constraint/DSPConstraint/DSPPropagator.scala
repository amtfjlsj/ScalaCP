package cpscala.TSolver.Model.Constraint.DSPConstraint

import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}
import java.util.concurrent.locks.ReentrantLock

import cpscala.TSolver.Model.Variable.PVar

abstract class DSPPropagator extends Runnable {
  val id: Int
  val arity: Int
  val scope: Array[PVar]
  var level = 0
  var assignedCount = 0
  //  val loopContinue = new AtomicBoolean(false)
  var loopContinue: Boolean = false
  // ����״̬
  // runningStatus = 0 δ����
  // runningStatus = 1 �ύ����
  // runningStatus = 2 ����
  // runningStatus = 3 ѭ���У�ѭ������˳�
  // runningStatus = 4 ѭ���У�������һ��ѭ��
  // runningStatus = 5 ��ѭ��������������
  val runningStatus = new AtomicInteger(0)
  val lock = new ReentrantLock()
  val isLock = new AtomicBoolean(false)

  def setup(): Unit = ???

  def domainChanged(v: PVar, mask: Array[Long]): Boolean

  def domainChanged(v: PVar): Boolean

  def propagate(): Boolean

  def newLevel(): Unit

  def backLevel(): Unit
}
