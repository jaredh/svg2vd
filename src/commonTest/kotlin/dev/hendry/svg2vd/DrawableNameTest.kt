package dev.hendry.svg2vd

import dev.hendry.svg2vd.util.toValidDrawableName
import kotlin.test.Test
import kotlin.test.assertEquals

class DrawableNameTest {

    @Test
    fun simpleNameRemainsUnchanged() {
        assertEquals("icon", toValidDrawableName("icon"))
    }

    @Test
    fun uppercaseConvertedToLowercase() {
        assertEquals("myicon", toValidDrawableName("MyIcon"))
    }

    @Test
    fun spacesConvertedToUnderscores() {
        assertEquals("my_icon", toValidDrawableName("my icon"))
    }

    @Test
    fun specialCharactersConvertedToUnderscores() {
        assertEquals("my_icon", toValidDrawableName("my-icon"))
        assertEquals("my_icon", toValidDrawableName("my.icon"))
        assertEquals("my_icon", toValidDrawableName("my@icon"))
    }

    @Test
    fun multipleUnderscoresCollapsed() {
        assertEquals("my_icon", toValidDrawableName("my__icon"))
        assertEquals("my_icon", toValidDrawableName("my___icon"))
        assertEquals("my_icon", toValidDrawableName("my - icon"))
    }

    @Test
    fun leadingUnderscoresRemoved() {
        assertEquals("icon", toValidDrawableName("_icon"))
        assertEquals("icon", toValidDrawableName("__icon"))
        assertEquals("icon", toValidDrawableName("-icon"))
    }

    @Test
    fun trailingUnderscoresRemoved() {
        assertEquals("icon", toValidDrawableName("icon_"))
        assertEquals("icon", toValidDrawableName("icon__"))
        assertEquals("icon", toValidDrawableName("icon-"))
    }

    @Test
    fun emptyStringReturnsDrawable() {
        assertEquals("drawable", toValidDrawableName(""))
    }

    @Test
    fun onlySpecialCharsReturnsDrawable() {
        assertEquals("drawable", toValidDrawableName("---"))
        assertEquals("drawable", toValidDrawableName("___"))
        assertEquals("drawable", toValidDrawableName("@#$"))
    }

    @Test
    fun numberPrefixGetsIcPrefix() {
        assertEquals("ic_123icon", toValidDrawableName("123icon"))
        assertEquals("ic_1", toValidDrawableName("1"))
    }

    @Test
    fun complexNameHandledCorrectly() {
        assertEquals("ic_24_baseline_home", toValidDrawableName("24-Baseline-Home"))
        assertEquals("ic_01_my_icon_v2", toValidDrawableName("01_My-Icon (v2)"))
    }

    @Test
    fun unicodeCharactersConvertedToUnderscores() {
        assertEquals("ic_n", toValidDrawableName("icön"))
        assertEquals("caf", toValidDrawableName("café"))
    }

    @Test
    fun preservesNumbersInMiddle() {
        assertEquals("icon24px", toValidDrawableName("icon24px"))
        assertEquals("baseline_24_home", toValidDrawableName("baseline_24_home"))
    }
}
