package com.sjunka.spectrumar.app

import com.sjunka.spectrumar.core.CameraPreset

/** One scripted beat of the automated fly-through; executed once when the demo clock passes [at]. */
sealed class DemoAction {
    data class Seek(val matchTime: Double) : DemoAction()
    data class Speed(val value: Double) : DemoAction()
    data class Preset(val preset: CameraPreset) : DemoAction()
    data class PitchControl(val on: Boolean) : DemoAction()
    data class SelectCarrier(val on: Boolean) : DemoAction()
    data class WhatIf(val on: Boolean) : DemoAction()
    /** Nudges a teammate of the ball carrier by metres (dx, dy) to show the recomputation. */
    data class MoveTeammate(val dx: Float, val dy: Float) : DemoAction()
    data class Play(val playing: Boolean) : DemoAction()
    data class Caption(val text: String) : DemoAction()
}

data class DemoStep(val at: Double, val action: DemoAction)

/** Pure description of the demo so it can be unit tested without any rendering. */
object DemoScript {
    const val LOOP_SECONDS = 60.0

    val steps: List<DemoStep> = listOf(
        DemoStep(0.0, DemoAction.Seek(0.0)),
        DemoStep(0.0, DemoAction.Speed(1.0)),
        DemoStep(0.0, DemoAction.Play(true)),
        DemoStep(0.0, DemoAction.PitchControl(false)),
        DemoStep(0.0, DemoAction.WhatIf(false)),
        DemoStep(0.0, DemoAction.SelectCarrier(false)),
        DemoStep(0.0, DemoAction.Preset(CameraPreset.TACTICAL)),
        DemoStep(0.2, DemoAction.Caption("Optical tracking: 22 players and the ball at 25 Hz")),
        DemoStep(7.0, DemoAction.Preset(CameraPreset.BALL)),
        DemoStep(7.0, DemoAction.SelectCarrier(true)),
        DemoStep(7.0, DemoAction.Caption("Follow the ball: live speed, distance and heat trail")),
        DemoStep(13.0, DemoAction.Seek(12.0)),
        DemoStep(13.0, DemoAction.SelectCarrier(false)),
        DemoStep(13.0, DemoAction.Preset(CameraPreset.TACTICAL)),
        DemoStep(13.0, DemoAction.PitchControl(true)),
        DemoStep(13.0, DemoAction.Caption("Pitch control: who gets there first")),
        DemoStep(21.0, DemoAction.PitchControl(false)),
        DemoStep(21.0, DemoAction.Seek(6.2)),
        DemoStep(21.0, DemoAction.Preset(CameraPreset.BALL)),
        DemoStep(21.0, DemoAction.Play(false)),
        DemoStep(22.0, DemoAction.WhatIf(true)),
        DemoStep(22.0, DemoAction.Caption("What-if: best pass by expected threat (xT)")),
        DemoStep(26.5, DemoAction.MoveTeammate(-14f, 9f)),
        DemoStep(26.5, DemoAction.Caption("Drag a player and the best option updates")),
        DemoStep(31.0, DemoAction.WhatIf(false)),
        DemoStep(31.0, DemoAction.Seek(42.0)),
        DemoStep(31.0, DemoAction.Speed(1.0)),
        DemoStep(31.0, DemoAction.Play(true)),
        DemoStep(31.0, DemoAction.Preset(CameraPreset.STADIUM)),
        DemoStep(31.0, DemoAction.Caption("Corner kick")),
        DemoStep(37.0, DemoAction.Preset(CameraPreset.BALL)),
        DemoStep(43.5, DemoAction.Preset(CameraPreset.CLOSE)),
        DemoStep(44.0, DemoAction.Speed(0.25)),
        DemoStep(44.0, DemoAction.Caption("GOAL")),
        DemoStep(54.0, DemoAction.Speed(1.0)),
        DemoStep(54.0, DemoAction.Preset(CameraPreset.TACTICAL)),
        DemoStep(54.0, DemoAction.Caption("Independent concept prototype")),
    ).sortedBy { it.at }

    /** Steps with `from < at <= to` (plus those at exactly 0 on the first call, `from` = -1). */
    fun due(from: Double, to: Double): List<DemoStep> = steps.filter { it.at > from && it.at <= to }

    /** Slow yaw drift (radians) laid over the presets to keep the camera alive. */
    fun yawBias(t: Double): Float = (0.35 * kotlin.math.sin(t * 0.22)).toFloat()
}
