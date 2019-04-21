package cpscala.TSolver.Model.Solver

import cpscala.TSolver.Model.Variable.PVar
import cpscala.XModel.XModel

/**
  * ϸ���Ȳ����������������PSTR3��PSTRbit��
  */

class IPFineSolver(xm: XModel, parallelism: Int, propagator_name: String, var_type: String, heu_name: String) extends IPSolver(xm, parallelism, propagator_name, var_type, heu_name){

  def initialPropagate(): Boolean = {

    // Լ�����ʼ��
    for (c <- tabs){
      Cevt.add(c)
    }
    helper.searchState = 0
    helper.pool.invokeAll(Cevt)

    start_time = System.nanoTime
    prop_start_time = System.nanoTime

    // ��ʼɾֵ
    helper.pool.invokeAll(Cevt)
    if (!helper.isConsistent) {
      return false
    }
    helper.globalStamp += 1

    // ��ʼ����
    helper.isConsistent = true
    Yevt.clear()
    var i = 0
    for (i <- 0 until numVars) {
      if (helper.varStamp(i) == helper.globalStamp) {
        Yevt += vars(i)
      }
    }

    while (Yevt.size != 0) {

      Cevt.clear()
      ClearInCevt()

      for (v <- Yevt) {
        for (c <- subscription(v.id)) {
          if (!inCevt(c.id)) {
            Cevt.add(c)
            inCevt(c.id) = true
          }
        }
      }

      helper.searchState = 2 //"propagate"
      // ����Ķ��ı���stamp = gstamp+1
      helper.pool.invokeAll(Cevt)
      helper.c_sum += Cevt.size()
      helper.p_sum += 1
      if (!helper.isConsistent) {
        return false
      }

      helper.globalStamp += 1
      Yevt.clear()

      var i = helper.level
      while (i < numVars) {
        val vid = levelvdense(i)
        val v = vars(vid)
        //�����ָĹ���
        if (helper.varStamp(vid) == helper.globalStamp) {
          Yevt += v
        }
        i += 1
      }
    }
    return true
  }


  def checkConsistencyAfterAssignment(ix: PVar): Boolean = {

    helper.isConsistent = true
    Yevt.clear()
    Yevt += ix

    while (Yevt.size != 0) {

      Cevt.clear()
      ClearInCevt()

      for (v <- Yevt) {
        for (c <- subscription(v.id)) {
          if (!inCevt(c.id)) {
            Cevt.add(c)
            inCevt(c.id) = true
          }
        }
      }

      helper.searchState = 2 //"propagate"
      // ����Ķ��ı���stamp = gstamp+1
      helper.pool.invokeAll(Cevt)
      helper.c_sum += Cevt.size()
      helper.p_sum += 1
      if (!helper.isConsistent) {
        return false
      }

      helper.globalStamp += 1
      Yevt.clear()

      var i = helper.level
      while (i < numVars) {
        val vid = levelvdense(i)
        val v = vars(vid)
        //�����ָĹ���
        if (helper.varStamp(vid) == helper.globalStamp) {
          Yevt += v
        }
        i += 1
      }
    }
    return true
  }

  def checkConsistencyAfterRefutation(ix: PVar): Boolean = {

    helper.isConsistent = true
    Yevt.clear()
    Yevt += ix

    while (Yevt.size != 0) {

      Cevt.clear()
      ClearInCevt()

      for (v <- Yevt) {
        for (c <- subscription(v.id)) {
          if (!inCevt(c.id)) {
            Cevt.add(c)
            inCevt(c.id) = true
          }
        }
      }

      helper.searchState = 2 //"propagate"
      // ����Ķ��ı���stamp = gstamp+1
      helper.pool.invokeAll(Cevt)
      helper.c_sum += Cevt.size()
      helper.p_sum += 1
      if (!helper.isConsistent) {
        return false
      }

      helper.globalStamp += 1
      Yevt.clear()

      var i = helper.level
      while (i < numVars) {
        val vid = levelvdense(i)
        val v = vars(vid)
        //�����ָĹ���
        if (helper.varStamp(vid) == helper.globalStamp) {
          Yevt += v
        }
        i += 1
      }
    }
    return true
  }

}
