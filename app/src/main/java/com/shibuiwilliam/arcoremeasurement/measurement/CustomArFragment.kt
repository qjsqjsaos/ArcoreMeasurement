package com.shibuiwilliam.arcoremeasurement.measurement

import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.sceneform.ux.ArFragment

class CustomArFragment: ArFragment() {

    override fun getSessionConfiguration(session: Session?): Config {
        val config = Config(session)
        config.focusMode = Config.FocusMode.AUTO
        config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
        return config
    }
}