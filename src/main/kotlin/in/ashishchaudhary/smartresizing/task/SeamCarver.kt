package `in`.ashishchaudhary.smartresizing.task

import java.io.File
import java.math.BigInteger
import java.nio.file.Path
import java.security.MessageDigest
import java.util.LinkedList
import kotlin.math.sqrt

class SeamCarver(filePath: Path, private val desiredWidth: Int, private val desiredHeight: Int) {
    private val parentPath = filePath.parent.parent
    private val originalPicture = Picture(filePath.toString())
    private var defensiveCopy = Picture(originalPicture)
    private var saveFile = parentPath.toFile()
    private var actionSequence = LinkedList<Action>()
    private var cache: Path? = null

    private sealed class Action {
        data class Expansion(val axis: String, val desiredDimension: Int) : Action()
        data class Reduction(val axis: String, val desiredDimension: Int) : Action()
    }

    init {
        if (desiredWidth < 1 || desiredWidth > 2500) throw IllegalArgumentException()
        if (desiredHeight < 1 || desiredHeight > 2500) throw IllegalArgumentException()
        findActionSequence()
        try {
            val file = filePath.toFile().readBytes()
            val digest = MessageDigest.getInstance("MD5").digest(file).toHex()
            saveFile = File(saveFile, "${desiredWidth}X${desiredHeight}${digest}.png")
            if (saveFile.exists() && saveFile.isFile) cache = saveFile.toPath()
        } catch (e: Exception) {
            println("Error in finding digest for ${filePath}, err: ${e.message}")
            throw e
        }
    }

    private fun width() = defensiveCopy.width()
    private fun height() = defensiveCopy.height()

    private fun energy(x: Int, y: Int): Double {
        if ((x < 0 || x > width() - 1) || (y < 0) || y > height() - 1) throw IllegalArgumentException(
            "invalid coordinate range"
        )
        if ((x == 0 || x == width() - 1) || (y == 0 || y == height() - 1)) return 1000.0
        fun square(value: Int) = value * value
        val lp = defensiveCopy.get(x - 1, y)
        val rp = defensiveCopy.get(x + 1, y)
        val up = defensiveCopy.get(x, y + 1)
        val dp = defensiveCopy.get(x, y - 1)
        val xColorDiff = arrayOf(lp.red - rp.red, lp.green - rp.green, lp.blue - rp.blue)
        val yColorDiff = arrayOf(up.red - dp.red, up.green - dp.green, up.blue - dp.blue)
        val xGradient = xColorDiff.fold(0) { acc, i -> acc + square(i) }.toDouble()
        val yGradient = yColorDiff.fold(0) { acc, i -> acc + square(i) }.toDouble()
        return sqrt(xGradient + yGradient)
    }

    private fun findActionSequence() {
        val percentWidthReduction = (width() - desiredWidth) / width().toDouble()
        val percentHeightReduction = (height() - desiredHeight) / height().toDouble()

        fun arrangeWidth(percentWidthReduction: Double) {
            if (percentWidthReduction > 0) {
                actionSequence.add(Action.Reduction("x", desiredWidth))
            } else if (percentWidthReduction < 0) {
                actionSequence.add(Action.Expansion("x", desiredWidth))
            }
        }

        fun arrangeHeight(percentHeightReduction: Double) {
            if (percentHeightReduction > 0) {
                actionSequence.add(Action.Reduction("y", desiredHeight))
            } else if (percentHeightReduction < 0) {
                actionSequence.add(Action.Expansion("y", desiredHeight))
            }
        }
        if (percentWidthReduction < percentHeightReduction) {
            arrangeWidth(percentWidthReduction)
            arrangeHeight(percentHeightReduction)
        } else {
            arrangeHeight(percentHeightReduction)
            arrangeWidth(percentWidthReduction)
        }
    }

    fun executeActionSequence(): Path {
        if (cache != null) return (cache as Path)
        val actionSequenceIterator = actionSequence.iterator()
        while (actionSequenceIterator.hasNext()) {
            val currentAction = actionSequenceIterator.next()
            when (currentAction) {
                is Action.Reduction -> {
                    when (currentAction.axis) {
                        "x" -> {
                            for (i in 1..(width() - currentAction.desiredDimension)) {
                                reduceX()
                            }
                        }
                        "y" -> {
                            for (i in 1..(height() - currentAction.desiredDimension)) {
                                transpose()
                                reduceX()
                                transpose()
                            }
                        }
                    }
                }
                is Action.Expansion -> {
                    when (currentAction.axis) {
                        "x" -> {
                            expandX(currentAction)
                        }
                        "y" -> {
                            transpose()
                            expandX(currentAction)
                            transpose()
                        }
                    }
                }
            }
        }
        defensiveCopy.save(saveFile)
        return saveFile.toPath()
    }

    private fun reduceX() {
        removeVerticalSeam(findVerticalSeam())
    }

    private fun expandX(currentAction: Action.Expansion) {
        val tempCopy = Picture(defensiveCopy)
        val seamCollection = mutableListOf<Array<Int>>()
        for (i in 1..(currentAction.desiredDimension - width())) {
            val vSeam = findVerticalSeam()
            seamCollection.add(vSeam)
            removeVerticalSeam(vSeam)
        }
        defensiveCopy = tempCopy
        for (i in 1..(currentAction.desiredDimension - width())) {
            addVerticalSeam(seamCollection[i - 1])
        }
    }

    private fun transpose() {
        val newPicture = Picture(height(), width())
        for (i in 0..(width() - 1)) {
            for (j in 0..(height() - 1)) {
                newPicture.setRGB(j, i, defensiveCopy.getRGB(i, j))
            }
        }
        defensiveCopy = newPicture
    }

    private fun removeVerticalSeam(seam: Array<Int>) {
        validateVerticalSeam(seam)
        val newPicture = Picture(width() - 1, height())
        for (i in 0..(defensiveCopy.height() - 1)) {
            val seamWidth = seam[i]
            (0..(defensiveCopy.width() - 1)).asSequence().filter { it != seamWidth }
                .forEachIndexed { counter, j ->
                    newPicture.setRGB(
                        counter, i, defensiveCopy.getRGB(j, i)
                    )
                }
        }
        defensiveCopy = newPicture
    }

    private fun addVerticalSeam(seam: Array<Int>) {
        validateVerticalSeam(seam)
        val newPicture = Picture(width() + 1, height())
        for (i in 0..(defensiveCopy.height() - 1)) {
            val seamWidth = seam[i]
            when (seamWidth) {
                0 -> {
                    val rgbToInsert = getAverageRGB(listOf(Pair(0, i), Pair(1, i)))
                    newPicture.setRGB(0, i, defensiveCopy.getRGB(0, i))
                    newPicture.setRGB(1, i, defensiveCopy.getRGB(1, i))
                    newPicture.setRGB(2, i, rgbToInsert)
                    for (j in 3..(defensiveCopy.width())) {
                        newPicture.setRGB(j, i, defensiveCopy.getRGB(j - 1, i))
                    }
                }
                defensiveCopy.width() - 1 -> {
                    val rgbToInsert = getAverageRGB(
                        listOf(
                            Pair(defensiveCopy.width() - 2, i), Pair(defensiveCopy.width() - 1, i)
                        )
                    )
                    for (j in 0..(defensiveCopy.width() - 3)) {
                        newPicture.setRGB(j, i, defensiveCopy.getRGB(j, i))
                    }
                    newPicture.setRGB(defensiveCopy.width() - 2, i, rgbToInsert)
                    newPicture.setRGB(
                        defensiveCopy.width() - 1,
                        i,
                        defensiveCopy.getRGB(defensiveCopy.width() - 2, i)
                    )
                    newPicture.setRGB(
                        defensiveCopy.width(), i, defensiveCopy.getRGB(defensiveCopy.width() - 1, i)
                    )
                }
                else -> {
                    val rgbToInsert = getAverageRGB(
                        listOf(Pair(seamWidth - 1, i), Pair(seamWidth, i), Pair(seamWidth + 1, i))
                    )
                    var counter = 0
                    for (j in 0..(defensiveCopy.width())) {
                        if (j == seamWidth + 1) {
                            newPicture.setRGB(j, i, rgbToInsert)
                        } else {
                            newPicture.setRGB(j, i, defensiveCopy.getRGB(counter++, i))
                        }
                    }
                }
            }
        }
        defensiveCopy = newPicture
    }

    private fun getAverageRGB(list: List<Pair<Int, Int>>): Int {
        val allColors = list.map { defensiveCopy.get(it.first, it.second) }
        val avgRed = allColors.fold(0) { acc, color -> acc + color.red } / allColors.size
        val avgGreen = allColors.fold(0) { acc, color -> acc + color.green } / allColors.size
        val avgBlue = allColors.fold(0) { acc, color -> acc + color.blue } / allColors.size
        var rgb = avgRed
        rgb = (rgb shl 8) + avgGreen
        rgb = (rgb shl 8) + avgBlue
        return rgb
    }

    private fun findVerticalSeam(): Array<Int> {
        val numNodes = width() * height() + 2
        val distTo = Array(numNodes) { Double.POSITIVE_INFINITY }
        distTo[0] = 0.0
        val edgeTo = Array<Int?>(numNodes) { null }
        val ewd = EdgeWeightedDigraph(numNodes)
        fun pixelToIndex(x: Int, y: Int) = y * width() + x + 1
        fun indexToPixel(index: Int): Pair<Int, Int> {
            val x = (index - 1) % width()
            val y = (index - 1) / width()
            return if (width() > 1) Pair(x, y) else Pair(y, x)
        }

        for (x in 0..(width() - 1)) {
            ewd.addEdge(DirectedEdge(0, pixelToIndex(x, 0), 0.0))
            ewd.addEdge(
                DirectedEdge(
                    pixelToIndex(x, height() - 1), numNodes - 1, energy(x, height() - 1)
                )
            )
            for (y in 0..(height() - 2)) {
                val energy = energy(x, y)
                when (x) {
                    0 -> {
                        if (width() > 1 && height() > 1) {
                            ewd.addEdge(
                                DirectedEdge(pixelToIndex(x, y), pixelToIndex(x + 1, y + 1), energy)
                            )
                        }
                    }
                    width() - 1 -> {
                        if (width() > 1 && height() > 1) {
                            ewd.addEdge(
                                DirectedEdge(pixelToIndex(x, y), pixelToIndex(x - 1, y + 1), energy)
                            )
                        }
                    }
                    else -> {
                        ewd.addEdge(
                            DirectedEdge(pixelToIndex(x, y), pixelToIndex(x - 1, y + 1), energy)
                        )
                        ewd.addEdge(
                            DirectedEdge(pixelToIndex(x, y), pixelToIndex(x + 1, y + 1), energy)
                        )
                    }
                }
                if (height() > 1) {
                    ewd.addEdge(
                        DirectedEdge(pixelToIndex(x, y), pixelToIndex(x, y + 1), energy)
                    )
                }
            }
        }
        (0..(numNodes - 2)).flatMap { ewd.adj(it) }.forEach { relax(it, distTo, edgeTo) }
        return pathTo(numNodes - 1, edgeTo, ::indexToPixel)
    }

    private fun validateVerticalSeam(seam: Array<Int>) {
        if (width() <= 1) throw IllegalArgumentException("less than 2 vertical seam in the picture")
        if (seam.size != height()) throw IllegalArgumentException("seam.length must be equal to the height()")
        for (pixelWidth in seam.withIndex()) {
            if (pixelWidth.value < 0 || pixelWidth.value > width() - 1) throw IllegalArgumentException(
                "all pixels must be within range"
            )
            if (pixelWidth.index != seam.size - 1) {
                if (Math.abs(pixelWidth.value - seam[pixelWidth.index + 1]) > 1) throw IllegalArgumentException(
                    "consecutive pixels must not differ by more than 1 width"
                )
            }
        }
    }

    private fun ByteArray.toHex(): String {
        val bi = BigInteger(1, this)
        return String.format("%0" + (this.size shl 1) + "X", bi)
    }

    private fun relax(
        directedEdge: DirectedEdge, distTo: Array<Double>, edgeTo: Array<Int?>
    ) {
        val v = directedEdge.from()
        val w = directedEdge.to()
        if (compareValues(distTo[v] + directedEdge.weight(), distTo[w]) < 0) {
            distTo[w] = distTo[v] + directedEdge.weight()
            edgeTo[w] = directedEdge.from()
        }
    }

    private fun pathTo(
        v: Int, edgeTo: Array<Int?>, indexToPixel: (Int) -> Pair<Int, Int>
    ): Array<Int> {
        val path = Array(height()) { -1 }
        var currentEdgeFrom = edgeTo[v]
        var counter = path.size
        while (counter != 0 && currentEdgeFrom != null) {
            val (x, _) = indexToPixel(currentEdgeFrom)
            path[--counter] = x
            currentEdgeFrom = edgeTo[currentEdgeFrom]
        }
        return path
    }
}