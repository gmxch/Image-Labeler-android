package com.eye.imagelaberlecropperkotlin

import android.graphics.Rect

data class BoundingBox(val rect: Rect, val className: String)

class Utils {
    fun generateXml(
        folder: String, filename: String, path: String, database: String,
        width: Int, height: Int, boxes: List<BoundingBox>
    ): String {
        val sb = StringBuilder()
        sb.appendLine("<annotation>")
        sb.appendLine("  <folder>$folder</folder>")
        sb.appendLine("  <filename>$filename</filename>")
        sb.appendLine("  <path>$path</path>")
        sb.appendLine("  <source><database>$database</database></source>")
        sb.appendLine("  <size><width>$width</width><height>$height</height><depth>3</depth></size>")
        sb.appendLine("  <segmented>0</segmented>")
        for (box in boxes) {
            sb.appendLine("  <object>")
            sb.appendLine("    <name>${box.className}</name>")
            sb.appendLine("    <pose>Unspecified</pose><truncated>0</truncated><difficult>0</difficult>")
            sb.appendLine("    <bndbox>")
            sb.appendLine("      <xmin>${box.rect.left}</xmin>")
            sb.appendLine("      <ymin>${box.rect.top}</ymin>")
            sb.appendLine("      <xmax>${box.rect.right}</xmax>")
            sb.appendLine("      <ymax>${box.rect.bottom}</ymax>")
            sb.appendLine("    </bndbox>")
            sb.appendLine("  </object>")
        }
        sb.appendLine("</annotation>")
        return sb.toString()
    }

    fun generateYoloFormat(boxes: List<BoundingBox>, imgWidth: Int, imgHeight: Int): String {
        val sb = StringBuilder()
        for (box in boxes) {
            val xCenter = box.rect.centerX().toFloat() / imgWidth
            val yCenter = box.rect.centerY().toFloat() / imgHeight
            val w = box.rect.width().toFloat() / imgWidth
            val h = box.rect.height().toFloat() / imgHeight
            sb.appendLine("${box.className} $xCenter $yCenter $w $h")
        }
        return sb.toString()
    }
}