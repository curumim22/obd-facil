package com.obdfacil.app

import android.app.Application

class ObdApp : Application() {
    /** Uma única sessão com o carro para o app inteiro (tela e serviço em segundo plano). */
    val session: CarSession by lazy { CarSession(this) }
}
