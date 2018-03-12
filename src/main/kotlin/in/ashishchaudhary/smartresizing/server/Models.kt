package `in`.ashishchaudhary.smartresizing.server

import java.io.InputStream

object Models {
    data class Session(val id: String)
    data class MultipartRequest(
        var id: String? = null,
        var fileName: String? = null,
        var width: Int = 0,
        var height: Int = 0,
        var inStream: InputStream? = null
    )
}