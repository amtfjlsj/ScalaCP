package cpscala.TSolver.Model.Constraint.SConstraint

import cpscala.TSolver.CpUtil.SearchHelper.SearchHelper
import cpscala.TSolver.CpUtil.{RestoreStack, SparseSetInt}
import cpscala.TSolver.Model.Variable.Var

import scala.collection.mutable.ArrayBuffer
import scala.collection.{mutable => m}


/**
  * ����STR3�ĵ�һ���汾��
  * ����Ԥ����ʱ����STR3ά������GAC��
  * ������������Ҳ����STR3ά������GAC��
  * �ο����ģ�2015_AI_STR3 A path-optimal filtering algorithm for table constraints
  * �ο�OscaR�Ĵ���ʵ��
  */
class TableSTR3_SSet(val id: Int, val arity: Int, val num_vars: Int, val scope: Array[Var], val tuples: Array[Array[Int]], val helper: SearchHelper) extends Propagator {

  // �ӱ���ά���飬��һά�������ڶ�άȡֵ������άԪ��
  // ��ʼ������ʱ���������Ѿ������л�������[0, 1, ..., var.size()]�����Կ���ֱ����ȡֵ��Ϊ�±�
  private[this] val subtables = Array.tabulate(arity)(i => new Array[Array[Int]](scope(i).size()))
  // �ֽ������ά���飬��һά�������ڶ�άȡֵ
  private[this] val separators = Array.tabulate(arity)(i => new Array[Int](scope(i).size()))
  // �ֽ��ջ
  // ����������ʼ�㣨0��)��������ֵ��separator�ı��ˣ������±���ջ�����HashMap�����������룬0�㲻��Ҫ���棬��Ϊ1���Ӧ��ջ������ļ���0���ʼ��GAC�����Ϣ��
  // ���������ķǳ�ʼ�㣬������ֵ��separator��һ�η����ı�ʱ�����ı�ǰ��separatorֵ�����ڸñ���ջ����HashMap��
  // HashMap����ķ����е�һ��IntΪvalue���ڶ���IntΪseparator
  private[this] val StackS = Array.fill(arity)(new RestoreStack[Int, Int](num_vars))
  // ����һ��Array�Ƿ��HashMap�죨ȷʵ��һ�㣩
//  private[this] val sepLevel = Array.fill[Array[Array[Int]]](numVars + 1)(Array.tabulate(arity)(i => Array.fill[Int](scope(i).size())(-1)))

  // ��ЧԪ��
  private[this] val invalidTuples = new SparseSetInt(tuples.length, num_vars)
  // �������ù�ϣ��ʵ�֣�keyΪ������scope�ڵ���ţ�valueΪȡֵ
  private[this] val deps = Array.fill(tuples.length)(new m.HashMap[Int, Int]())

  // oldSize�����size֮���ֵ�Ǹ�Լ�����δ���֮�䱻���˵�ֵ��delta��
  // ��������ģ�Sparse-Sets for Domain Implementation
  private[this] val oldSizes = Array.tabulate(arity)(i => scope(i).size())
  private[this] val removeValues = new ArrayBuffer[Int]() //(delta)

  // ��ʼ����־����
  // isInitialΪfalse˵��setup�л�δ��ʼ��������ݽṹ
  // Ϊtrue˵����ʼ�����ݽṹ��ɣ����Խ��г�ʼɾֵ
  private[this] var isInitial = false

  override def setup(): Boolean = {

    if (!isInitial) {
      //println("c_id:" + id + " ===============>")

      // ��ʼ����ЧԪ�鼯
      invalidTuples.clear()

      // ��ʱ�ӱ�
      val tempSupport = Array.tabulate(arity)(i => {
        Array.fill(scope(i).size())(new ArrayBuffer[Int]())
      })

      // ����ʱ�ӱ��ڶ�̬���Ԫ����
      var t = 0
      while (t < tuples.length) {
        if (isValidTuple(tuples(t))) {
          var i = 0
          while (i < arity) {
            val a = tuples(t)(i)
            tempSupport(i)(a) += t
            i += 1
          }
        }
        t += 1
      }

      var i = 0
      while (i < arity) {
        val x = scope(i)
        var j = x.size()
        // ��Ϊ������δɾֵ������j��Ϊindex����Ϊȡֵ
        while (j > 0) {
          j -= 1
          val subtable = tempSupport(i)(j).toArray
          subtables(i)(j) = subtable
          separators(i)(j) = subtable.length - 1
          if (!subtable.isEmpty) {
            // 15������α�����Ƿ��������һ��Ԫ���Ӧ��deps��
            deps(subtable(0)) += (i -> j)
          }
        }
        StackS(i).push()
        i += 1
      }
      // ��ʼ�����ݽṹ���
      isInitial = true

      return true
    }
    else {
      var i = 0
      while (i < arity) {
        val x = scope(i)
        var j = x.size()
        while (j > 0) {
          j -= 1
          // ��Ϊ���������ѱ�ɾֵ��������Ҫͨ���±�j��ȡ��value
          val value = x.get(j)
          if (subtables(i)(value).isEmpty) {
            x.remove(value)
            helper.varStamp(x.id) = helper.globalStamp
          }
        }
        if(x.isEmpty()) {
          return false
        }
        i += 1
      }
    }
    return true
  }

  override def propagate(evt: ArrayBuffer[Var]): Boolean = {
    
      //println(s"c_id: ${id} propagate==========================>")
    val membersBefore = invalidTuples.size()

    // 15�������е�α����ÿ��ֻ����һ��ֵ
    for (i <- 0 until arity) {

      val v = scope(i)

      if (oldSizes(i) != v.size()) {

        // ���delta������oldSize
        removeValues.clear()
        oldSizes(i) = v.getLastRemovedValues(oldSizes(i).toLong, removeValues)
          //println(s"       cons: ${id} var: ${v.id} removedValues: " + removeValues.mkString(", "))
        // Ѱ���µ���ЧԪ��
        for (a <- removeValues) {
          val sep = separators(i)(a)
          for (p <- 0 to sep) {
            val k = subtables(i)(a)(p)
            invalidTuples.add(k)
          }
        }
      }
    }

    // ��ЧԪ��û�и���
    val membersAfter = invalidTuples.size()
    if (membersBefore == membersAfter) {
      return true
    }
      //println(s"    cons: ${id}  the number of invalid tuple: ${membersAfter - membersBefore}")

    // Ѱ��û��֧�ֵ�ֵ
    var i = membersBefore
    while (i < membersAfter) {

      val k = invalidTuples.get(i)
        //println(s"       invalid tuple is ${k}: " + tuples(k).mkString(","))
      val dep = deps(k)

      for ((varId, value) <- dep) {

        val v = scope(varId)
        if (v.unBind() && v.contains(value)) {

          val subtable = subtables(varId)(value)
          val sep = separators(varId)(value)

          // Ѱ��֧��
          var p = sep
          while (p >= 0 && invalidTuples.has(subtable(p))) p -= 1

          // û��֧�֣�ɾȥ��ֵ
          if (p == -1) {
            v.remove(value)
            //println(s"       cons: ${id}  var: ${v.id}  remove new value: ${value}")
            oldSizes(varId) -= 1
            evt += v

            if (v.isEmpty()) {
              //println(s"       var:${v.id} is empty")
              return false
            }
          } else {
            if (p != sep) {
              // ���±���ջ���Ĺ�ϣ��
              val topHash = StackS(varId).top

              if (!topHash.contains(value)) {
                topHash(value) = sep
              }
              separators(varId)(value) = p
            }
            // ������ֵ�Դ���Ч��������(k)Ų��֧�ֵ�������(subtable(p))
            deps(k) -= (varId)
            deps(subtable(p)) += ((varId, value))
          }
        }
      }
      i += 1
    }
    return true
  }

  // �²�
  def newLevel(): Unit = {
    level += 1
    // ��StackSѹ��һ���µ�HashMap����Ӧ�²㣩
    for (i <- 0 until arity) {
      StackS(i).push()
    }
    // �����ϲ�invalidTuples�ı߽�cursize��15�������е�member��
    invalidTuples.newLevel()
    // �����²���ø���oldSize��oldSize���ϲ㱣��һ��
  }

  // ����
  def backLevel(): Unit = {
    level -= 1
    //println(s"c_id: ${id} backlevel==================>")
    for (i <- 0 until arity) {
      val topHash = StackS(i).pop
      // iΪ������ţ�aΪȡֵ��sΪ��Ӧ�ӱ��sep
      for ((a, s) <- topHash) {
        separators(i)(a) = s
      }
      // ���ݺ�����oldSize���¾ɴ�С��ͬ����Ϊ��û�д���
      oldSizes(i) = scope(i).size()
    }
    // �ָ��ϲ�invalidTuples�ı߽�cursize��15�������е�member��
    invalidTuples.backLevel()
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
