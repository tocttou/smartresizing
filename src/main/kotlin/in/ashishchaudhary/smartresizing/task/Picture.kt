package `in`.ashishchaudhary.smartresizing.task

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import javax.imageio.ImageIO

class Picture {
    private var image = BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)
    private var width: Int = 0
    private var height: Int = 0

    constructor(width: Int, height: Int) {
        if (width < 0) throw IllegalArgumentException("width must be non-negative")
        if (height < 0) throw IllegalArgumentException("height must be non-negative")
        this.width = width
        this.height = height
        image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    }

    constructor(picture: Picture) {
        width = picture.width()
        height = picture.height()
        image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        for (col in 0 until width()) for (row in 0 until height()) image.setRGB(
            col, row, picture.image.getRGB(col, row)
        )
    }

    constructor(filename: String) {
        try {
            val file = File(filename)
            if (file.isFile) {
                image = ImageIO.read(file)
                width = image.getWidth(null)
                height = image.getHeight(null)
            } else {
                throw IllegalArgumentException("could not read image file: $filename")
            }
        } catch (ioe: IOException) {
            throw IllegalArgumentException("could not open image file: $filename", ioe)
        }
    }

    fun width() = width
    fun height() = height

    private fun validateRowIndex(row: Int) {
        if (row < 0 || row >= height()) throw IllegalArgumentException("row index must be between 0 and ${height() - 1}")
    }

    private fun validateColumnIndex(col: Int) {
        if (col < 0 || col >= width()) throw IllegalArgumentException("column index must be between 0 and ${width() - 1}")
    }

    fun get(col: Int, row: Int): Color {
        validateColumnIndex(col)
        validateRowIndex(row)
        val rgb = getRGB(col, row)
        return Color(rgb)
    }

    fun getRGB(col: Int, row: Int): Int {
        validateColumnIndex(col)
        validateRowIndex(row)
        return image.getRGB(col, height - row - 1)
    }

    fun getAllBytes(): ByteArray {
        val baos = ByteArrayOutputStream()
        ImageIO.write(image, "png", baos)
        return baos.toByteArray()
    }

    fun set(col: Int, row: Int, color: Color?) {
        validateColumnIndex(col)
        validateRowIndex(row)
        if (color == null) throw IllegalArgumentException("color argument is null")
        val rgb = color.rgb
        setRGB(col, row, rgb)
    }

    fun setRGB(col: Int, row: Int, rgb: Int) {
        validateColumnIndex(col)
        validateRowIndex(row)
        return image.setRGB(col, height - row - 1, rgb)
    }

    fun save(file: File) {
        val filename = file.name
        val suffix = filename.substring(filename.lastIndexOf('.') + 1)
        if ("jpg".equals(suffix, true) || "png".equals(suffix, true)) {
            try {
                ImageIO.write(image, suffix, file)
            } catch (e: IOException) {
                e.printStackTrace()
            }
        } else {
            println("Error: filename must end in .jpg or .png")
        }
    }
}