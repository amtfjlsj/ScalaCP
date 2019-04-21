package cpscala.TSolver.Model.Constraint.IPConstraint

import cpscala.TSolver.CpUtil.SearchHelper.IPSearchHelper
import cpscala.TSolver.CpUtil.{Constants, INDEX, RSBitSet}
import cpscala.TSolver.Model.Variable.PVar

import scala.collection.mutable.ArrayBuffer

class TableIPCT_SBit(val id: Int, val arity: Int, val num_vars: Int, val scope: Array[PVar], val tuples: Array[Array[Int]], val helper: IPSearchHelper) extends IPPropagator {
  val currTab = new RSBitSet(id, tuples.length, num_vars)
  val supports = new Array[Array[Array[Long]]](arity)
  val num_bit = currTab.num_bit
  val residues = new Array[Array[Int]](arity)

  for (i <- 0 until arity) {
    supports(i) = Array.ofDim[Long](scope(i).size, num_bit)
    residues(i) = Array.fill(scope(i).size)(-1)
  }

  var ii = 0
  while (ii < tuples.length) {
    val (x, y) = INDEX.getXY(ii)
    val t = tuples(ii)

    for (j <- 0 until t.length) {
      supports(j)(t(j))(x) |= Constants.MASK1(y)
    }
    ii += 1
  }

  //�����Index
  val Ssup = new ArrayBuffer[Int](arity)
  val Sval = new ArrayBuffer[Int](arity)

  // ��ȡ��������С
  var maxDomainSize = Int.MinValue
  scope.foreach(x => {
    maxDomainSize = math.max(maxDomainSize, x.size())
  })

  val var_num_bit = Math.ceil(maxDomainSize.toDouble / Constants.BITSIZE.toDouble).toInt

  // �ֲ��������
  // 2. localMask����ǰ����ʱ�����޸ĵ������mask
  // 3. lastMask����һ�ε��ú��mask
  val localMask = Array.fill[Long](arity, var_num_bit)(0L)
  val lastMask = Array.fill[Long](arity, var_num_bit)(0L)
  val globalMask = Array.fill[Long](var_num_bit)(0L)
  val tmpMask = Array.fill[Long](var_num_bit)(0L)

  val values = new ArrayBuffer[Int](maxDomainSize)
  values.clear()

  // ��ʶ�Ƿ�Ϊ���δ��������δ���updateTableʱ����valid���£��ǳ��δ���updateTableʱ���ݱȽϽ��ȷ����
  var firstPropagate = true

  //������
  def initial(): Boolean = {
    Ssup.clear()
    Sval.clear()
    // ���SVal�Ƿ�Ϊ�գ�Ϊ��������propagate
    var snapshotChanged = false

    var i = 0
    while (i < arity) {
      var diff = false
      val v = scope(i)
      v.mask(localMask(i))

      //      // �������������ȫ������ͬ
      //      // ���±����������
      //      // snapshotChanged ��Ϊ��Ҫpropagate��������propagate
      var j = 0
      while (j < var_num_bit) {
        if (lastMask(i)(j) != localMask(i)(j)) {
          diff = true
        }
        j += 1
      }


      //      if (v.isChanged(localMask(i)) != diff) {
      //      if (v.isChanged(lastMask(i))) {
      if (diff) {
        Sval += i
        snapshotChanged = true
      }
      //      v.mask(localMask(i))

      //      if (v.isChanged(localMask(i)) != diff) {
      //        println("diff")
      //      }
      //      else {
      //        println("same")
      //      }

      if (v.unBind()) {
        Ssup += i
      }

      i += 1
    }
    return snapshotChanged
  }


  def updateTable(): Boolean = {
    //    //println(s"      ut cid: ${id}===========>")
    var i = 0
    while (i < Sval.length && helper.isConsistent) {
      val vv: Int = Sval(i)
      val v: PVar = scope(vv)
      //      v.mask(localMask(vv))
      //println(s"cid: ${id}, vid: ${v.id}: localMask ${Constants.toFormatBinaryString(localMask(vv)(0))}")

      // ���delta��������
      var numValid = 0
      var numRemoved = 0

      var j = 0
      while (j < var_num_bit) {
        tmpMask(j) = (~localMask(vv)(j)) & lastMask(vv)(j)
        numRemoved += java.lang.Long.bitCount(tmpMask(j))
        numValid += java.lang.Long.bitCount(localMask(vv)(j))
        lastMask(vv)(j) = localMask(vv)(j)
        j += 1
      }

      currTab.clearMask()
      if (numRemoved >= numValid || firstPropagate) {
        Constants.getValues(localMask(vv), values)
        for (a <- values) {
          currTab.addToMask(supports(vv)(a))
        }
      } else {
        Constants.getValues(tmpMask, values)
        // ��ͷ����
        for (a <- values) {
          currTab.addToMask(supports(vv)(a))
        }
        currTab.reverseMask()
      }

      val changed = currTab.intersectWithMask()

      //����ʧ��
      if (currTab.isEmpty()) {
        helper.isConsistent = false
        //println(s"update faild!!: ${Thread.currentThread().getName}, cid: ${id}")
        return false
      }

      i += 1
    }

    firstPropagate = false
    //    printf(s"      cid: %2d           after ut   table: ${currTab.words(helper.level)(0)}\n", id)
    //    printf(s"      cid: %2d           after ut   table: ${Constants.toFormatBinaryString(currTab.words(helper.level)(0))}\n", id)

    return true
  }

  def filterDomains(): Boolean = {
    //println(s"      fd cid: ${id}===========>")
    var i = 0
    while (i < Ssup.length && helper.isConsistent) {
      var deleted: Boolean = false
      val vv: Int = Ssup(i)
      val v = scope(vv)

      Constants.getValues(localMask(vv), values)

      for (a <- values) {
        if (id == 7) {
          //println(s"      cid: ${id} var: ${v.id} value: ${a} support: ${Constants.toFormatBinaryString(supports(vv)(a)(0))}")
        }
        var index = residues(vv)(a)
        if (index == -1 || (currTab.words(helper.level)(index) & supports(vv)(a)(index)) == 0L) { //resʧЧ
          index = currTab.intersectIndex(supports(vv)(a))
          if (index != -1) { //�����ҵ�֧��
            residues(vv)(a) = index
          }
          else {
            deleted = true
            //�޷��ҵ�֧��, ɾ��(v, a)
            //println(s"      cons:${id} var:${v.id} remove new value:${a}")
            val (x, y) = INDEX.getXY(a)
            localMask(vv)(x) &= Constants.MASK0(y)
          }
        }
      }

      if (deleted) {
        helper.varStamp(v.id) = helper.globalStamp + 1
        val changed = v.submitMask(localMask(vv))
        // �����߳�ɾֵ
        // �ύ���ģ�����ȡ��ֵ
        if (v.isEmpty()) {
          helper.isConsistent = false
          //println(s"filter faild!!: ${Thread.currentThread().getName}, cid: ${id}, vid: ${v.id}")
          return false
        }

        // ���ﲻ�ܵ���newMask��Ϊ���ǻ���lastMask���µ�
        var j = 0
        while (j < var_num_bit) {
          lastMask(vv)(j) = localMask(vv)(j)
          j += 1
        }
      }
      i += 1
    }

    return true
  }

  override def propagate(): Boolean = {
    //    initial()
    //    if (!updateTable()) {
    //      return false
    //    }
    //    if (!filterDomains()) {
    //      return false
    //    }
    //    //    }
    //    return true
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
  }

  override def backLevel(): Unit = {
    currTab.deleteLevel(level)
    level -= 1
    var i = 0
    while (i < arity) {
      scope(i).mask(lastMask(i))
      i += 1
    }
  }

  override def call(): Boolean = {
    if (helper.isConsistent) {
      //    //println(s"start: cur_ID: ${Thread.currentThread().getId()},cur_name: ${Thread.currentThread().getName()},cur_cid: ${id}
      return propagate()
      //    //println(s"end:   cur_ID: ${Thread.currentThread().getId()},cur_name: ${Thread.currentThread().getName()},cur_cid: ${id},propagate_res: ${res}")
    } else {
      return false
    }
  }

}
