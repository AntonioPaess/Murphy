package com.murphy.android

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.murphy.core.domain.MeshSession
import com.murphy.core.domain.NodeId

private val Ink = Color.rgb(17, 18, 23)
private val Muted = Color.rgb(100, 104, 115)
private val Blue = Color.rgb(34, 75, 250)
private val Page = Color.rgb(246, 247, 250)
private val White = Color.WHITE

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val snapshot = MeshSession(NodeId("android-device")).snapshot()
        val content = column(Page).apply {
            setPadding(dp(24), dp(28), dp(24), dp(32))
        }
        content.addView(label("M U R P H Y", 13, Blue, bold = true))
        content.addView(space(28))
        content.addView(label("Sua rede.\nSem um chefe.", 34, Ink, bold = true))
        content.addView(space(12))
        content.addView(
            label(
                "Comunicação local para quando a internet não chega.",
                16,
                Muted,
            ),
        )
        content.addView(space(28))

        val statusCard = column(White, cornerRadius = 24).apply {
            setPadding(dp(22), dp(22), dp(22), dp(22))
        }
        statusCard.addView(label("ESTADO DA REDE", 12, Muted, bold = true))
        statusCard.addView(space(18))
        statusCard.addView(label("Bluetooth ainda não conectado", 20, Ink, bold = true))
        statusCard.addView(space(8))
        statusCard.addView(
            label("Esta versão ainda não descobre aparelhos próximos.", 14, Muted),
        )
        statusCard.addView(space(24))
        statusCard.addView(label("%02d".format(snapshot.peers.size), 64, Ink, bold = true))
        statusCard.addView(label("vizinhos diretos", 16, Ink, bold = true))
        statusCard.addView(space(20))
        statusCard.addView(
            label("Núcleo compartilhado carregado · ${snapshot.history.size} eventos", 13, Muted),
        )
        content.addView(statusCard)
        content.addView(space(16))

        val note = column(Color.rgb(234, 240, 255), cornerRadius = 20).apply {
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }
        note.addView(label("PRIMEIRA VERSÃO", 12, Blue, bold = true))
        note.addView(space(8))
        note.addView(
            label(
                "O app já abre e usa o núcleo Murphy. A descoberta Bluetooth, mensagens e SOS ainda não estão implementados.",
                14,
                Ink,
            ),
        )
        content.addView(note)
        content.addView(space(20))
        content.addView(
            label(
                "Sem conexão ativa, nenhuma mensagem pode ser enviada ou confirmada.",
                13,
                Muted,
            ),
        )

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Page)
            isFillViewport = true
            clipToPadding = false
            addView(content)
        }
        setContentView(scroll)
    }

    private fun column(color: Int, cornerRadius: Int = 0) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        if (cornerRadius == 0) {
            setBackgroundColor(color)
        } else {
            background = GradientDrawable().apply {
                setColor(color)
                this.cornerRadius = dp(cornerRadius).toFloat()
            }
        }
    }

    private fun label(
        text: String,
        sizeSp: Int,
        color: Int,
        bold: Boolean = false,
    ) = TextView(this).apply {
        this.text = text
        textSize = sizeSp.toFloat()
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.START
        includeFontPadding = true
    }

    private fun space(heightDp: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(heightDp))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
