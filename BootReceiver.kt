package com.picopilot.agent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        AgentState.init(context)
        if (AgentState.enabled && AgentState.autoStart) {
            AgentService.start(context)
        }
    }
}
