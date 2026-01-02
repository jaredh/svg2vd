package dev.hendry.svg2vd

import dev.hendry.svg2vd.converter.Svg2VectorConverter
import dev.hendry.svg2vd.util.toValidDrawableName
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.DragEvent
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.dom.events.Event
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag
import org.w3c.files.File
import org.w3c.files.FileReader
import org.w3c.files.get

fun main() {
    window.onload = {
        initializeApp()
    }
}

private fun initializeApp() {
    val dropZone = document.getElementById("drop-zone") as? HTMLElement ?: return
    val fileInput = document.getElementById("file-input") as? HTMLInputElement ?: return
    val outputArea = document.getElementById("output") as? HTMLTextAreaElement ?: return
    val downloadButton = document.getElementById("download-btn") as? HTMLElement ?: return
    val copyButton = document.getElementById("copy-btn") as? HTMLElement ?: return
    val statusLabel = document.getElementById("status") as? HTMLElement ?: return
    val lineNumbers = document.getElementById("line-numbers") as? HTMLElement ?: return

    var currentDrawableName = "drawable"
    var currentVectorDrawable: String? = null

    outputArea.addEventListener("input", {
        val text = outputArea.value
        val lineCount = if (text.isEmpty()) 1 else text.count { it == '\n' } + 1
        lineNumbers.innerHTML = (1..lineCount).joinToString("<br>")
    })

    outputArea.addEventListener("scroll", {
        lineNumbers.scrollTop = outputArea.scrollTop
    })

    listOf("dragenter", "dragover", "dragleave", "drop").forEach { eventName ->
        dropZone.addEventListener(eventName, { event -> event.preventDefault(); event.stopPropagation() })
        document.body?.addEventListener(eventName, { event -> event.preventDefault(); event.stopPropagation() })
    }

    listOf("dragenter", "dragover").forEach { eventName ->
        dropZone.addEventListener(eventName, { dropZone.classList.add("drag-over") })
    }

    listOf("dragleave", "drop").forEach { eventName ->
        dropZone.addEventListener(eventName, { dropZone.classList.remove("drag-over") })
    }

    fun handleFile(file: File) {
        processFile(file, outputArea, statusLabel, downloadButton) { name, content ->
            currentDrawableName = name
            currentVectorDrawable = content
        }
    }

    dropZone.addEventListener("drop", { event ->
        val dragEvent = event as DragEvent
        dragEvent.dataTransfer?.files?.get(0)?.let(::handleFile)
    })

    dropZone.addEventListener("click", {
        fileInput.click()
    })

    fileInput.addEventListener("change", {
        fileInput.files?.get(0)?.let(::handleFile)
    })

    downloadButton.addEventListener("click", {
        currentVectorDrawable?.let { content ->
            downloadFile("$currentDrawableName.xml", content)
        }
    })

    copyButton.addEventListener("click", {
        window.navigator.clipboard.writeText(outputArea.value).then {
            val originalText = copyButton.textContent
            copyButton.textContent = "Copied!"
            window.setTimeout({
                copyButton.textContent = originalText
            }, 2000)
        }
    })

    outputArea.dispatchEvent(Event("input"))
}

private fun processFile(
    file: File,
    outputArea: HTMLTextAreaElement,
    statusLabel: HTMLElement,
    downloadButton: HTMLElement,
    onSuccess: (drawableName: String, xmlContent: String) -> Unit
) {
    if (!file.name.endsWith(".svg", ignoreCase = true)) {
        statusLabel.textContent = "Please select an SVG file"
        statusLabel.className = "status error"
        return
    }

    val reader = FileReader()
    reader.onload = {
        val svgContent = reader.result as String
        val baseName = file.name.substringBeforeLast(".")
        val drawableName = toValidDrawableName(baseName)
        val conversionResult = Svg2VectorConverter.convert(svgContent, file.name)

        if (conversionResult.success && conversionResult.content != null) {
            outputArea.value = conversionResult.content
            statusLabel.textContent = "Converted successfully: ${file.name}"
            statusLabel.className = "status success"
            downloadButton.classList.remove("hidden")
            onSuccess(drawableName, conversionResult.content)
        } else {
            outputArea.value = ""
            statusLabel.textContent = "Conversion failed: ${conversionResult.errorMessage ?: "Unknown error"}"
            statusLabel.className = "status error"
            downloadButton.classList.add("hidden")
        }
        outputArea.dispatchEvent(Event("input"))
    }
    reader.onerror = {
        statusLabel.textContent = "Failed to read file"
        statusLabel.className = "status error"
    }
    reader.readAsText(file)
}

private fun downloadFile(outputFilename: String, content: String) {
    val blob = Blob(arrayOf(content), BlobPropertyBag(type = "application/xml"))
    val blobUrl = URL.createObjectURL(blob)
    val downloadLink = document.createElement("a") as HTMLAnchorElement
    downloadLink.href = blobUrl
    downloadLink.download = outputFilename
    document.body?.appendChild(downloadLink)
    downloadLink.click()
    document.body?.removeChild(downloadLink)
    URL.revokeObjectURL(blobUrl)
}
