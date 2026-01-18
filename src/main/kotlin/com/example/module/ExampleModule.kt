package com.example.module

import java.awt.Color
import org.cobalt.api.module.Module
import org.cobalt.api.module.setting.impl.*
import org.lwjgl.glfw.GLFW

object ExampleModule : Module(
  name = "Example Module",
) {

  val checkbox by CheckboxSetting(
    name = "Checkbox",
    description = "Example checkbox setting",
    defaultValue = true
  )

  val color by ColorSetting(
    name = "Color",
    description = "Example color setting",
    defaultValue = Color.WHITE.rgb
  )

  val keyBind by KeyBindSetting(
    name = "KeyBind",
    description = "Example keybind setting",
    defaultValue = GLFW.GLFW_KEY_ESCAPE
  )

  val mode by ModeSetting(
    name = "Mode",
    description = "Example mode setting",
    defaultValue = 0,
    options = arrayOf("Mode1", "Mode2", "Mode3")
  )

  val range by RangeSetting(
    name = "Range",
    description = "Example range setting",
    defaultValue = Pair(3.0, 5.0),
    min = 0.0,
    max = 10.0
  )

  val slider by SliderSetting(
    name = "Slider",
    description = "Example slider setting",
    defaultValue = 3.0,
    min = 1.0,
    max = 10.0
  )

  val text by TextSetting(
    name = "Text",
    description = "Example text setting",
    defaultValue = "Hello"
  )

}
