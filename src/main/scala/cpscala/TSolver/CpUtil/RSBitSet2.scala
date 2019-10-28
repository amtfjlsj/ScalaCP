package cpscala.TSolver.CpUtil

class RSBitSet2(id: Int, numTuples: Int, numVars: Int) {
  val numLevel = numVars + 1
  val numBit = Math.ceil(numTuples.toDouble / Constants.BITSIZE.asInstanceOf[Double]).toInt
  val lastLimits = numTuples % Constants.BITSIZE
  // array of rlong, words.length = p
  //lastLimit 取值为[0, 63]
  //若lastLimit = 0, lastWord不改变
  //otherwise, lastWord <<= 64 - lastLimit
  // array of int,  index.length = p
  // array of rlong, words.length = p
  val words = Array.ofDim[Long](numLevel, numBit)
  words(0) = Array.fill(numBit)(Constants.ALLONELONG);
  if (lastLimits != 0) words(0)(numBit - 1) <<= 64 - lastLimits
  //初始化limit, index, mask
  // rint
  val limit = Array.fill(numLevel)(-1)
  limit(0) = numBit - 1
  // array of int,  index.length = p
  val index = Array.range(0, numBit)
  val map = Array.range(0, numBit)
  // array of long, mask.length = p
  val mask = new Array[Long](numBit)
  var currentLevel = 0
  var preLevel = 0

  def newLevel(level: Int): Unit = {
    if (currentLevel != level) {
      currentLevel = level
      limit(currentLevel) = limit(preLevel)

      var i = limit(preLevel)
      while (i >= 0) {
        val offset = index(i)
        words(currentLevel)(offset) = words(preLevel)(offset)
        i -= 1
      }
      preLevel = level

      //      for (i <- (0 to limit(preLevel)).reverse) {
      //        val offset = index(i)
      //        words(currentLevel)(offset) = words(preLevel)(offset)
      //      }
      //
      //      preLevel = level
    }
  }

  def deleteLevel(level: Int): Unit = {
    limit(level) = -1
    preLevel = level - 1

    while (limit(preLevel) == -1) preLevel -= 1
    currentLevel = preLevel
  }

  def BackToLevel(level: Int): Unit = {
    limit(level) = -1
    preLevel = level - 1;
    while (limit(preLevel) == -1) preLevel -= 1
    currentLevel = preLevel
  }

  def isEmpty(): Boolean = limit(currentLevel) == -1

  def swap(i: Int, j: Int): Unit = {
    val tmp = index(i)
    index(i) = index(j)
    index(j) = tmp

    map(index(i)) = i
    map(index(j)) = j
  }

  def remove(i: Int): Unit = {
    val j = limit(currentLevel)
    index(i) = index(j)
    index(j) = i

    map(index(i)) = i
    map(index(j)) = j
    limit(currentLevel) -= 1
  }

  def clearMask(): Unit = {
    val currentLimit = limit(currentLevel)
    var i = 0
    while (i <= currentLimit) {
      val offset = index(i)
      mask(offset) = 0L
      i += 1
    }
    //    for (i <- 0 to currentLimit) {
    //      val offset = index(i)
    //      mask(offset) = 0L
    //    }
  }

  def reverseMask(): Unit = {
    val currentLimit = limit(currentLevel)
    var i = 0
    while (i <= currentLimit) {
      val offset = index(i)
      mask(offset) = ~mask(offset)
      i += 1
    }
    //    for (i <- 0 to currentLimit) {
    //      val offset = index(i)
    //      mask(offset) = ~mask(offset)
    //    }
  }

  def addToMask(m: Array[Long]): Unit = {
    val currentLimit = limit(currentLevel)
    var i = 0
    while (i <= currentLimit) {
      val offset = index(i)
      mask(offset) = mask(offset) | m(offset)
      i += 1
    }

    //    for (i <- 0 to currentLimit) {
    //      val offset = index(i)
    //      mask(offset) = mask(offset) | m(offset)
    //    }
  }

  def intersectWithMask(): Boolean = {
    //本表默认未修改
    var changed = false
    var w = 0L
    var currentWords = 0L

    var i = limit(currentLevel)
    while (i >= 0) {
      val offset = index(i)
      currentWords = words(currentLevel)(offset)
      w = currentWords & mask(offset)
      if (w != currentWords) {
        words(currentLevel)(offset) = w
        //本表已修改
        changed = true
        //        if (w == 0L) {
        //          index(i) = index(limit(currentLevel))
        //          index(limit(currentLevel)) = offset
        //          limit(currentLevel) -= 1
        //        }
        if (w == 0L) {
          remove(offset)
          limit(currentLevel) -= 1
        }
      }
      i -= 1
    }

    //记录是否改变
    return changed
  }

  def intersectIndex(m: Array[Long]): Int = {
    val currentLimit = limit(currentLevel)
    var i = 0
    while (i <= currentLimit) {
      val offset = index(i)
      if ((words(currentLevel)(offset) & m(offset)) != 0L)
        return offset
      i += 1
    }
    return -1
  }

  def intersectIndex(m: RSIndexedBitSet): Int = {
    var i = m.Size()
    var j = limit(currentLevel)

    // 对比长度，以短的为主，以短的遍历，只要为结果为0就交换
    if (i < j) {
      // 若m比较短，遍历m改words
      while (i >= 0) {
        // 拿到公共index
        val absoluteIdx = m.index_arr(i)
        // 相交为不为0存入res中
        if ((words(currentLevel)(absoluteIdx) & m.words(i)) != 0L) {
          return absoluteIdx
        }
        else {
          m.remove(absoluteIdx)
        }
        //交换在上一层
        i -= 1
      }

    } else {
      // 若m比较长,遍历words改m，若按位与后为0，则
      while (j >= 0) {
        //        val mIdx = m.index_arr(i)
        val absoluteIdx = index(j)
        val denseIdx = m.index_map(absoluteIdx)

        if ((words(currentLevel)(absoluteIdx) & m.words(denseIdx)) != 0L) {
          return absoluteIdx
        }
        else {
          m.remove(absoluteIdx)
        }

        //        val b = index(j)
        //        if ((words(currentLevel)(commonIdx) & m.words(commonIdx)) != 0L) return commonIdx
        //        //交换在上一层
        //        i -= 1
        j -= 1
      }

    }

    return Constants.kINDEXOVERFLOW
  }

  def show(): Unit = {
    print("name = " + id + ", level = " + currentLevel + " ")
    for (i <- 0 until numBit) {
      printf("%x ", words(currentLevel)(i))
    }
    println()
  }

}
