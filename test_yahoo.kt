import java.net.HttpURLConnection
import java.net.URL
import java.util.Scanner

fun main() {
    val url = URL("https://query1.finance.yahoo.com/v8/finance/chart/XAUEUR=X?range=1mo&interval=1d")
    val conn = url.openConnection() as HttpURLConnection
    conn.requestMethod = "GET"
    conn.setRequestProperty("User-Agent", "Mozilla/5.0")
    if (conn.responseCode == 200) {
        val scanner = Scanner(conn.inputStream)
        var response = ""
        while (scanner.hasNextLine()) {
            response += scanner.nextLine()
        }
        println(response.take(500))
    } else {
        println("Error: ${conn.responseCode}")
    }
}
