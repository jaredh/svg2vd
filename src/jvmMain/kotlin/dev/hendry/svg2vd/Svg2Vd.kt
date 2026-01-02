package dev.hendry.svg2vd

import com.android.ide.common.vectordrawable.Svg2Vector
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.versionOption
import com.github.ajalt.clikt.parameters.types.path
import dev.hendry.svg2vd.converter.Svg2VectorConverter
import dev.hendry.svg2vd.util.toValidDrawableName
import java.io.File
import java.io.IOException
import java.nio.file.Path
import kotlin.system.exitProcess

fun main(args: Array<String>) = Svg2Vd().main(args)

class Svg2Vd : CliktCommand () {
    private val source by argument(help = "SVG files").path(mustExist = true, canBeDir = false).multiple()
    private val dest by argument(help = "Directory to save VectorDrawables").path(canBeDir = true, canBeFile = false)

    private val force by option("-f", "--force", help = "Force overwrites any existing files in the OUTPUT directory").flag(default = false)
    private val verbose by option("-v", "--verbose", help = "Verbose logging, show files as they are converted").flag(default = false)
    private val continueOnError by option("-c", "--continue-on-error", help = "If an error occurs, continue processing SVGs").flag(default = false)
    private val optimize by option("-o", "--optimize", help = "Run Avocado on generated VectorDrawables").flag(default = false)
    private val experimental by option("-e", "--experimental", help = "Use Kotlin Multiplatform converter (no Android SDK required)").flag(default = false)
    private val rename by option("-r", "--rename", help = "Rename output files to valid Android resource names (lowercase, a-z, 0-9, underscore only)").flag(default = false)

    init {
        versionOption("0.2", help = "Display information about svg2vd")
    }

    override fun run() {
        if (experimental && verbose) {
            println("Using experimental Kotlin Multiplatform converter")
        }
        convert(source, dest, force, optimize, experimental, rename)
    }

    private fun convert(inputFiles: List<Path>, outputDir: Path, forceOverwrite: Boolean = false, optimize: Boolean = false, useExperimental: Boolean = false, renameFiles: Boolean = false) {
        inputFiles.forEach { convert(it.toFile(), outputDir.toFile(), forceOverwrite, optimize, useExperimental, renameFiles) }
    }

    private fun convert(inputFile: File, outputDir: File, forceOverwrite: Boolean, optimize: Boolean, useExperimental: Boolean, renameFiles: Boolean) {
        if (!inputFile.extension.equals(SVG_EXTENSION, ignoreCase = true)) {
            printerrln("$inputFile does not have a SVG file extension, skipping")
            return
        }

        outputDir.mkdirs()
        val outputName = if (renameFiles) {
            toValidDrawableName(inputFile.nameWithoutExtension)
        } else {
            inputFile.nameWithoutExtension
        }
        val outputFile = outputDir.absoluteFile.resolve("$outputName.xml")

        if (verbose) println("Converting $inputFile to $outputFile")

        if (!forceOverwrite && outputFile.exists()) {
            println("$outputFile already exists, skipping.")
        } else {
            if (useExperimental) {
                convertWithExperimental(inputFile, outputFile)
            } else {
                convertWithAndroidSdk(inputFile, outputFile)
            }

            if (optimize) optimize(outputFile)
        }
    }

    private fun convertWithAndroidSdk(inputFile: File, outputFile: File) {
        Svg2Vector.parseSvgToXml(inputFile.toPath(), outputFile.outputStream()).run {
            if (isNotEmpty()) {
                printerrln(this)
                if (!continueOnError) exitProcess(SVG_CONVERT_ERROR)
            }
        }
    }

    private fun convertWithExperimental(inputFile: File, outputFile: File) {
        val svgContent = inputFile.readText()
        val result = Svg2VectorConverter.convert(svgContent, inputFile.name)

        if (result.success && result.content != null) {
            outputFile.writeText(result.content)
        } else {
            printerrln(result.errorMessage ?: "Unknown conversion error")
            if (!continueOnError) exitProcess(SVG_CONVERT_ERROR)
        }
    }

    private fun optimize(file: File) {
        if (verbose) println("Optimizing VectorDrawable with Avocado...")

        try {
            ProcessBuilder("avdo", "-q", "$file")
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start()
        } catch (e: IOException) {
            printerrln("Unable to execute Avocado. See https://github.com/alexjlockwood/avocado for installation instructions.")
            if (!continueOnError) exitProcess(SVG_CONVERT_ERROR)
        }
    }

    companion object {
        const val SVG_EXTENSION = "svg"
        const val SVG_CONVERT_ERROR = 1
    }
}

fun printerrln(message: String) {
    System.err.println(message)
}
