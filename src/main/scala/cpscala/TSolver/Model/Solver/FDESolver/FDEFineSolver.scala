package cpscala.TSolver.Model.Solver.FDESolver

import cpscala.TSolver.Model.Variable.Var
import cpscala.XModel.FDEModel1

class FDEFineSolver(fdeM: FDEModel1, propagatorName: String, varType: String, heuName: String) extends FDESolver(fdeM, propagatorName, varType, heuName) {

    override def initialPropagate(): Boolean = {

      for (c <- 0 until numTabs) {
        tabs(c).setup()
      }

      helper.globalStamp += 1
      // 初始删值
      for (c <- 0 until numTabs) {
        if (!tabs(c).setup()) {
          return false
        }
      }
      start_time = System.nanoTime
      prop_start_time = System.nanoTime
      return propagate(null)
    }

    override def checkConsistencyAfterAssignment(x: Var): Boolean = {
      return propagate(x)
    }

    override def checkConsistencyAfterRefutation(x: Var): Boolean = {
      return propagate(x)
    }

    def propagate(x: Var): Boolean = {
      Q.clear()
      if (x == null) {
        //初始化
        for (z <- vars) {
          insert(z)
        }
      } else {
        insert(x)
      }
      while (!Q.empty()) {
        val v = Q.pop()
        for (c <- subscription(v.id)) {
          if (helper.varStamp(v.id) > helper.tabStamp(c.id)) {
            Y_evt.clear()
            val consistent = c.propagate(Y_evt)
            helper.c_sum += 1
            if (!consistent) {
              return false
            } else {
              for (y <- Y_evt) {
                insert(y)
              }
            }
            helper.globalStamp += 1
            helper.tabStamp(c.id) = helper.globalStamp
          }
        }
        helper.p_sum += 1
      }

      return true
    }

    def insert(x: Var): Unit = {
      Q.push(x)
      helper.globalStamp += 1
      helper.varStamp(x.id) = helper.globalStamp
    }
}
