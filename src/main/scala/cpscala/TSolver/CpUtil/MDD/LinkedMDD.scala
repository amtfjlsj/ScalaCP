package cpscala.TSolver.CpUtil.MDD

import java.util
import java.util.Arrays

import cpscala.XModel.{XTab, XVar}

import scala.collection.mutable
import scala.collection.mutable.HashSet

abstract class LinkedMDD(val tab: XTab) {
  // Լ��������Ϣ
  val cid = tab.id
  val arity = tab.arity
  val semantics = tab.semantics
  val numTuples = tab.tuples.size

  // ��ʱ���±���˳�򣩵�Ԫ�鼯��
  val tuples = tab.tuples.clone()

  //  val levelNodes = new Array[HashSet[Node]](arity + 1)
  // ��ÿ�е�ļ���
  val levelNodes = Array.fill(arity + 1)(new mutable.HashSet[Node]())

  // �µ�˳���µ�scope
  val scope = new Array[XVar](arity)
  val scopeInt = new Array[Int](arity)
  //�µı���λ��
  val newPosition = Array.fill(arity)(-1)

  // MDD��ͱߵĸ���
  var numNodes = 0L
  var numArcs = 0L

  // Լ����������С
  var maxDomainSize = Int.MinValue
  tab.scope.foreach(x => {
    maxDomainSize = math.max(maxDomainSize, x.size)
  })

  // MDD���˵Ľڵ�
  var root: Node
  var sink: Node

  def reorderScope(order: Array[Int]): Unit = {
    var i = 0
    while (i < arity) {
      scope(order(i)) = tab.scope(i)
      scopeInt(order(i)) = tab.scope(i).id
      i += 1
    }
  }

  def reorderTuples(order: Array[Int]): Unit = {
    var i = 0
    var j = 0

    while (i < numTuples) {
      j = 0
      while (j < arity) {
        tuples(i)(order(j)) = tab.tuples(i)(j)
        j += 1
      }
      i += 1
    }

    // Ԫ�鼯��������
    util.Arrays.sort(tuples, cmp)
  }

  def clear(): Unit = {
    var i = arity
    while (i >= 0) {
      levelNodes(i).clear()
      i -= 1
    }
  }

  @inline def cmp(x: Array[Int], y: Array[Int]): Int = {
    var i = 0
    while (i < arity) {
      if (x(i) < y(i)) {
        return -1
      }
      else if (x(i) > y(i)) {
        return 1
      }
      i += 1
    }
    return 0
  }

  def newNode(ith: Int): Node = {
    numNodes += 1
    val node = new Node(numNodes, ith + 1)
    levelNodes(ith + 1) += node
    return node
  }

  def build(): Unit = {
    // ����root, sink�����ڵ�
    // ��0��: ���ڵ㣬id =1
    // ��ײ㣺 sink�ڵ㣬id=0
    root = new Node(1, 0)
    levelNodes(0) += root
    sink = new Node(0, arity)
    levelNodes(arity) += sink

    numNodes = 2
    numArcs = 0

    for (t <- tuples) {
      var u = root
      var i = 0
      while (i < arity) {
        var label = Int.MaxValue

        if (u.outcomes.nonEmpty) {
          label = u.outcomes.last.label
        }

        if (label != t(i)) {
          // Ԫ������һ��Ԫ��ָ��sink�ڵ�
          if (i == (arity - 1)) {
            val arc = u.addOutcome(t(i), sink)
            sink.addIncome(arc)
            numArcs += 1
          } else {
            val node = newNode(i)
            val arc = u.addOutcome(t(i), node)
            node.addIncome(arc)
            numArcs += 1
          }

        }
        u = u.outcomes.last.end
        i += 1
      }
    }
  }

  def pReduce(): Unit = {
    // �����ڵ�����Ľ�������뵱�ڴ���
    var maxLevelSizeInLevelNodes = Int.MinValue
    levelNodes.foreach(s => {
      maxLevelSizeInLevelNodes = math.max(maxLevelSizeInLevelNodes, s.size)
    })
  }

  def reduceLayer(layer: HashSet[Node], VA: Array[HashSet[Node]], NA: mutable.HashMap[Node, HashSet[Node]], Vlist: Array[Int], Nlist: Array[Tuple2[Int, Node]]) {}

  def reducePack(p: Pack, VA: Array[HashSet[Node]], NA: mutable.HashMap[Node, HashSet[Node]], Vlist: Array[Int], Nlist: Array[Tuple2[Int, Node]], Q: mutable.Queue[Pack]) {}

}
