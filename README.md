[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT) ![GitHub Actions status](https://github.com/jaredh/svg2vd/actions/workflows/gradle.yml/badge.svg)

# svg2vd
Convert SVGs to Android Vector Drawables from the command line.

## Install

### macOS

```bash
brew install jaredh/svg2vd/svg2vd
```

## Building

Build using the bundled Gradle wrapper.

```bash
./gradlew build
```


## Running

```bash
java -jar svg2vd-0.4.0.jar
```

### Help

```
Usage: svg2vd [OPTIONS] [SOURCE]... DEST

Options:
  -f, --force              Force overwrites any existing files in the OUTPUT
                           directory
  -v, --verbose            Verbose logging, show files as they are converted
  -c, --continue-on-error  If an error occurs, continue processing SVGs
  -o, --optimize           Run Avocado on generated VectorDrawables
  -e, --experimental       Use Kotlin Multiplatform converter (no Android SDK
                           required)
  -r, --rename             Rename output files to valid Android resource names
                           (lowercase, a-z, 0-9, underscore only)
  --version                Display information about svg2vd
  -h, --help               Show this message and exit

Arguments:
  SOURCE  SVG files
  DEST    Directory to save VectorDrawables
```

### Kotlin Multiplatform (Experimental)

This project includes an experimental Kotlin Multiplatform SVG to VectorDrawable converter that works without Android SDK or Java AWT dependencies. This enables SVG conversion on platforms where the Android SDK tools are not available.

### Avocado support

To further optimize the VectorDrawable, use the `-o` option. This requires Avocado, a third-party app to be installed and accessible in your `PATH`.

Install `avocado` using `npm`

```bash
npm install -g avocado
```

See [Avocado's GitHub page](https://github.com/alexjlockwood/avocado) for more information.
