//> using scala "3.3"
//> using dep "com.lihaoyi::os-lib:0.9.1"

import scala.util.matching.Regex
import java.net.URL
import java.nio.file.{Files, Paths, StandardCopyOption}
import scala.io.Source

object DownloadImgurImages {

  val imgurPattern: Regex = """(https?://i\.imgur\.com/[a-zA-Z0-9]+\.(?:png|jpg|jpeg|gif))""".r
  val baseImageDir = "images/munit"

  case class Stats(
    filesProcessed: Int = 0,
    imagesFound: Int = 0,
    imagesDownloaded: Int = 0
  )

  def main(args: Array[String]): Unit = {
    println("=== MUnit Imgur Image Downloader ===\n")

    // Create base image directory
    os.makeDir.all(os.pwd / os.RelPath(baseImageDir))

    // Find all markdown files (excluding node_modules, .metals, and target directories)
    println("Searching for markdown files...")
    val markdownFiles = os.walk(os.pwd)
      .filter(_.ext == "md")
      .filter(p =>
        !p.segments.contains("node_modules") &&
        !p.segments.contains(".metals") &&
        !p.segments.contains("target")
      )

    println(s"Found ${markdownFiles.length} markdown files\n")

    var stats = Stats()

    // Process each markdown file
    markdownFiles.foreach { mdFile =>
      val content = os.read(mdFile)

      // Check if file contains imgur links
      if (content.contains("imgur.com")) {
        stats = stats.copy(filesProcessed = stats.filesProcessed + 1)

        println(s"Processing: ${mdFile.relativeTo(os.pwd)}")

        // Get markdown file basename (without extension)
        val mdBasename = mdFile.baseName

        // Create subdirectory for this markdown file
        val imageSubdir = os.pwd / os.RelPath(baseImageDir) / mdBasename
        os.makeDir.all(imageSubdir)

        // Calculate relative path from markdown file to image directory
        val relImagePath = imageSubdir.relativeTo(mdFile / os.up)

        // Find all imgur URLs in the content
        val imgurUrls = imgurPattern.findAllIn(content).toSet

        // Download images and build URL mapping
        val urlMapping = imgurUrls.map { url =>
          val filename = url.split("/").last
          val localPath = imageSubdir / filename

          // Download image if it doesn't exist
          if (!os.exists(localPath)) {
            println(s"  Downloading: $url")
            try {
              val connection = new URL(url).openConnection()
              connection.setRequestProperty("User-Agent", "Mozilla/5.0")
              val inputStream = connection.getInputStream()

              Files.copy(
                inputStream,
                localPath.toNIO,
                StandardCopyOption.REPLACE_EXISTING
              )
              inputStream.close()

              println(s"  ✓ Saved to: ${localPath.relativeTo(os.pwd)}")
              stats = stats.copy(imagesDownloaded = stats.imagesDownloaded + 1)
            } catch {
              case e: Exception =>
                println(s"  ✗ Failed to download: $url - ${e.getMessage}")
            }
          } else {
            println(s"  ✓ Already exists: ${localPath.relativeTo(os.pwd)}")
          }

          stats = stats.copy(imagesFound = stats.imagesFound + 1)

          // Return mapping from URL to relative path
          url -> (relImagePath / filename).toString
        }.toMap

        // Replace imgur URLs with local paths
        var updatedContent = content
        urlMapping.foreach { case (url, localPath) =>
          updatedContent = updatedContent.replace(url, localPath)
        }

        // Write updated content back to file
        os.write.over(mdFile, updatedContent)
        println(s"✓ Updated: ${mdFile.relativeTo(os.pwd)}\n")
      }
    }

    // Print summary
    println("=== Summary ===")
    println(s"Processed files: ${stats.filesProcessed}")
    println(s"Total images found: ${stats.imagesFound}")
    println(s"New images downloaded: ${stats.imagesDownloaded}")
    println(s"Images directory: $baseImageDir")
    println("\nDone!")
  }
}

DownloadImgurImages.main(args)