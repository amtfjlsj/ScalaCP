package cpscala.TSolver.CpUtil.SearchHelper

class SearchHelper(val numVars: Int, val numTabs: Int) {
  // ��ʼ����ʱ���
  var globalStamp: Long = 0L
  val tabStamp: Array[Long] = Array.fill(numTabs)(0L)
  val varStamp: Array[Long] = Array.fill(numVars)(0L)
  //  val sizeLevel: Array[Int] = Array.fill(numLevel)(0)

  // ����ʱ��
  var time: Long = 0L
  var branchTime = 0L
  var propTime = 0L
  var updateTableTime = 0L
  var filterDomainTime = 0L
  var backTime = 0L
  var lockTime = 0L
  var nodes: Long = 0L
//  @volatile var isConsistent: Boolean = true
  var isConsistent: Boolean = true
  // ��������
  var timeLimit = 0L
  var nodeLimit = 0L
  var failureLimit = 0L

  var level: Int = 0

  //�߳���������
  var p_sum = 0L
  //Լ����������
  var c_sum = 0L


}
