package cpscala.TSolver.Model.Constraint.IPConstraint

import cpscala.TSolver.CpUtil.SearchHelper.IPSearchHelper
import cpscala.TSolver.CpUtil.{RestoreStack, SparseSetInt}
import cpscala.TSolver.Model.Variable.PVar

import scala.collection.mutable.ArrayBuffer
import scala.collection.{mutable => m}

// ��������ʹ��SparseSet

class TableIPSTR3_SSet(val id: Int, val arity: Int, val num_vars: Int, val scope: Array[PVar], val tuples: Array[Array[Int]], val helper: IPSearchHelper) extends IPPropagator {

  // �ӱ���ά���飬��һά�������ڶ�άȡֵ������άԪ��
  // ��ʼ������ʱ���������Ѿ������л�������[0, 1, ..., var.size()]�����Կ���ֱ����ȡֵ��Ϊ�±�
  private[this] val subtables = Array.tabulate(arity)(i => new Array[Array[Int]](scope(i).size()))
  // �ֽ������ά���飬��һά�������ڶ�άȡֵ
  private[this] val separators = Array.tabulate(arity)(i => new Array[Int](scope(i).size()))
  // �ֽ��ջ
  // ����������ʼ�㣨0��)��������ֵ��separator�ı��ˣ������±���ջ�����HashMap�����������룬0�㲻��Ҫ���棬��Ϊ1���Ӧ��ջ������ļ���0�����Ϣ��
  // ���������ķǳ�ʼ�㣬������ֵ��separator��һ�η����ı�ʱ�����ı�ǰ��separatorֵ�����ڸñ���ջ����HashMap��
  // HashMap����ķ����е�һ��IntΪvalue���ڶ���IntΪseparator
  private[this] val StackS = Array.fill(arity)(new RestoreStack[Int, Int](num_vars))

  // ��ЧԪ��
  private[this] val invalidTuples = new SparseSetInt(tuples.length, num_vars)
  // �������ù�ϣ��ʵ�֣�keyΪ������scope�ڵ���ţ�valueΪȡֵ
  private[this] val deps = Array.fill(tuples.length)(new m.HashMap[Int, Int]())

  // oldSize�����size֮���ֵ�Ǹ�Լ�����δ���֮�䱻���˵�ֵ��delta��
  // ��������ģ�Sparse-Sets for Domain Implementation
  private[this] val oldSizes = Array.tabulate(arity)(i => scope(i).size())
  private[this] val removeValues = new ArrayBuffer[Int]() //(delta)

  // ��ʼ����ʶ����
  // isInitialΪfalse˵��setup�б�Լ����δ��ʼ�����ݽṹ
  // Ϊtrue˵����Լ����ʼ����ɣ����Խ��г�ʼɾֵ
  private[this] var isInitial = false

  override def setup(): Unit = {

    if (!isInitial) {

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
      // ��Լ����ʼ�����
      isInitial = true
    }
    // ��ʼɾֵ
    else {
      //println(s"c_id: ${id} initial delete value==========================>")
      var i = 0
      while (i < arity) {
        val x = scope(i)
        var j = x.size()
        while (j > 0) {
          j -= 1
          // ��Ϊ���������ѱ�ɾֵ��������Ҫͨ���±�j��ȡ��value
          val value = x.get(j)
          if (subtables(i)(value).isEmpty) {
            x.safeRemove(value)
            //println(s"     var:${x.id} remove new value:${value}")
            // ���������޸ģ���ȫ��ʱ�����1
            helper.varStamp(x.id) = helper.globalStamp + 1
          }
        }
        if(x.isEmpty()) {
          helper.isConsistent = false
          return
        }
        i += 1
      }
    }
  }


  def propagate(): Boolean = {

    //println(s"c_id: ${id} propagate==========================>")
    val membersBefore = invalidTuples.size()

    // 15�������е�α����ÿ��ֻ����һ��ֵ
    for (i <- 0 until arity) {

      val x = scope(i)

      if (oldSizes(i) != x.size()) {

        // ���delta������oldSize
        removeValues.clear()
        oldSizes(i) = x.getLastRemovedValues(oldSizes(i).toLong, removeValues)
        //println(s"       var: ${x.id} removedValues: " + removeValues.mkString(", "))
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

    //println(s"       the number of invalid tuple: ${membersAfter - membersBefore}")

    // Ѱ��û��֧�ֵ�ֵ
    var i = membersBefore
    while (i < membersAfter) {

      val k = invalidTuples.get(i)
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
            v.safeRemove(value)
            //println(s"     var:${v.id} remove new value:${value}")
            // ��Ϊ�ǲ��в�ͬ�ڴ��У�����ֶ��Լ��ɾ��ͬһ��������ֵͬ��������������ﲻ����oldSize-1
//            oldSizes(varId) -= 1
            // ���������޸ģ���ȫ��ʱ�����1
            helper.varStamp(v.id) = helper.globalStamp + 1
            if (v.isEmpty()) {
              helper.isConsistent = false
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

  def call(): Boolean = {
//        //println(s"start: cur_ID: ${Thread.currentThread().getId()},cur_name: ${Thread.currentThread().getName()},cur_cid: ${id}")

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
    //println(s"c_id: ${id} newlevel==================>")
    level += 1
    // ��inStackSѹ��һ���µ�HashMap����Ӧ�²㣩
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
      // inStackS�ȵ���һ��HashMap����ǰ�㣩���ٻ�ȡ�����HashMap����һ�㣩������һ���sep�ָ�
      //      StackS(i).pop()
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
