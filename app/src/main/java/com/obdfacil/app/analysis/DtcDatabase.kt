package com.obdfacil.app.analysis

enum class Severity(val label: String, val action: String) {
    CRITICAL("Grave", "Pare o carro assim que for seguro e procure uma oficina. Rodar assim pode causar dano sério."),
    HIGH("Atenção", "Leve à oficina nos próximos dias. Evite viagens longas até resolver."),
    LOW("Leve", "Pode esperar a próxima revisão, mas não esqueça de comentar com o mecânico."),
}

data class DtcInfo(
    val code: String,
    val title: String,
    val explanation: String,
    val severity: Severity,
    val tip: String? = null,
)

/**
 * Explicações em linguagem simples para os códigos de falha mais comuns,
 * com foco no Peugeot 206 1.4 Flex (motor TU3) e carros flex em geral.
 */
object DtcDatabase {

    private fun d(code: String, title: String, explanation: String, severity: Severity, tip: String? = null) =
        code to DtcInfo(code, title, explanation, severity, tip)

    private val C = Severity.CRITICAL
    private val H = Severity.HIGH
    private val L = Severity.LOW

    private val known: Map<String, DtcInfo> = mapOf(
        // Medição de ar
        d("P0105", "Sensor de pressão do coletor (MAP) com defeito", "Esse sensor mede quanto ar entra no motor. Com ele falhando, o motor pode ficar fraco, gastar mais e falhar na marcha lenta.", H, "Verifique conector e mangueira do sensor MAP antes de trocar a peça."),
        d("P0106", "Sensor MAP com leitura estranha", "O sensor que mede o ar admitido está dando valores fora do esperado. Pode ser sujeira, mangueira solta ou entrada falsa de ar.", H),
        d("P0107", "Sensor MAP com sinal baixo", "O sinal do sensor de ar está baixo demais. Normalmente é fio, conector ou o próprio sensor.", H),
        d("P0108", "Sensor MAP com sinal alto", "O sinal do sensor de ar está alto demais. Normalmente é fio, conector ou o próprio sensor.", H),
        d("P0110", "Sensor de temperatura do ar com defeito", "O sensor que mede a temperatura do ar que entra no motor está com problema. Afeta a partida e o consumo, mas não é urgente.", L),
        d("P0112", "Sensor de temperatura do ar com sinal baixo", "Leitura de temperatura do ar fora do normal. Afeta a partida e o consumo.", L),
        d("P0113", "Sensor de temperatura do ar com sinal alto", "Leitura de temperatura do ar fora do normal, geralmente conector solto ou sensor aberto.", L),
        // Temperatura do motor
        d("P0115", "Sensor de temperatura do motor com defeito", "A central não sabe a temperatura real do motor. Isso pode impedir a ventoinha de ligar e o motor pode superaquecer sem você perceber.", H, "Fique de olho no ponteiro de temperatura do painel até consertar."),
        d("P0116", "Sensor de temperatura do motor com leitura incoerente", "A temperatura informada não faz sentido. Pode ser o sensor ou a válvula termostática travada.", H),
        d("P0117", "Sensor de temperatura do motor com sinal baixo", "O sensor de temperatura está informando errado. Risco de a ventoinha não ligar e de a partida a frio falhar.", H),
        d("P0118", "Sensor de temperatura do motor com sinal alto", "O sensor de temperatura está informando errado. Risco de a ventoinha não ligar e de a partida a frio falhar.", H),
        d("P0125", "Motor demora para aquecer", "O motor está levando tempo demais para chegar na temperatura certa. Quase sempre é a válvula termostática travada aberta. Aumenta o consumo.", L),
        d("P0128", "Motor abaixo da temperatura ideal", "O motor não chega na temperatura normal de trabalho. Normalmente é a válvula termostática. Gasta mais combustível.", L),
        d("P0217", "Motor superaquecendo", "A central detectou temperatura do motor muito alta. Continuar rodando pode queimar a junta do cabeçote.", C, "Desligue o motor, espere esfriar e verifique o nível da água. Nunca abra o reservatório quente."),
        // Borboleta / acelerador
        d("P0120", "Sensor da borboleta (TPS) com defeito", "O sensor que informa quanto você está acelerando está com problema. O carro pode dar trancos, morrer ou ficar sem força.", H),
        d("P0121", "Sensor da borboleta com leitura incoerente", "A posição do acelerador informada não bate com o esperado. Pode dar trancos e marcha lenta irregular.", H),
        d("P0122", "Sensor da borboleta com sinal baixo", "Sinal do sensor do acelerador baixo demais. Normalmente fio, conector ou o sensor.", H),
        d("P0123", "Sensor da borboleta com sinal alto", "Sinal do sensor do acelerador alto demais. Normalmente fio, conector ou o sensor.", H),
        // Sonda lambda
        d("P0130", "Sonda lambda com defeito", "A sonda mede se a queima do combustível está correta. Com ela ruim, o carro gasta mais e polui mais.", H, "Em carro flex, sonda ruim também atrapalha o reconhecimento de álcool/gasolina."),
        d("P0131", "Sonda lambda com sinal baixo", "A sonda está indicando mistura pobre o tempo todo. Pode ser a sonda, falta de combustível ou entrada falsa de ar.", H),
        d("P0132", "Sonda lambda com sinal alto", "A sonda está indicando mistura rica o tempo todo. Pode ser a sonda ou excesso de combustível.", H),
        d("P0133", "Sonda lambda lenta", "A sonda está respondendo devagar, sinal de desgaste. O consumo pode aumentar.", L, "Sondas costumam durar entre 80 e 100 mil km."),
        d("P0134", "Sonda lambda sem sinal", "A central não recebe sinal da sonda. Verifique fios e conector.", H),
        d("P0135", "Aquecimento da sonda lambda com defeito", "A resistência que aquece a sonda queimou. O carro fica mais gastão logo depois da partida.", L),
        d("P0136", "Segunda sonda lambda com defeito", "A sonda depois do catalisador está com problema. Ela fiscaliza o catalisador. Não afeta muito o funcionamento.", L),
        d("P0141", "Aquecimento da segunda sonda com defeito", "A resistência da sonda pós-catalisador queimou. Não afeta muito o funcionamento.", L),
        // Mistura
        d("P0171", "Mistura pobre (pouco combustível)", "O motor está recebendo pouco combustível ou ar demais. Causas comuns: entrada falsa de ar, bomba de combustível fraca, filtro entupido ou bico sujo.", H, "No 206 é comum mangueira de vácuo ressecada. Peça para verificar vazamentos de ar e a pressão da bomba."),
        d("P0172", "Mistura rica (combustível demais)", "O motor está recebendo combustível demais. Causas comuns: bico pingando, sensor MAP ruim ou regulador de pressão.", H),
        // Injetores
        d("P0200", "Circuito dos bicos injetores com defeito", "Problema elétrico nos bicos que injetam combustível. O motor pode falhar.", H),
        d("P0201", "Bico injetor do cilindro 1 com defeito", "O bico do cilindro 1 não está funcionando direito. O motor falha e perde força.", H),
        d("P0202", "Bico injetor do cilindro 2 com defeito", "O bico do cilindro 2 não está funcionando direito. O motor falha e perde força.", H),
        d("P0203", "Bico injetor do cilindro 3 com defeito", "O bico do cilindro 3 não está funcionando direito. O motor falha e perde força.", H),
        d("P0204", "Bico injetor do cilindro 4 com defeito", "O bico do cilindro 4 não está funcionando direito. O motor falha e perde força.", H),
        d("P0230", "Circuito da bomba de combustível com defeito", "Problema elétrico na bomba de combustível. O carro pode morrer e não pegar mais.", C, "Verifique o relé e o fusível da bomba."),
        // Falhas de ignição
        d("P0300", "Motor falhando (vários cilindros)", "O motor está falhando na queima. Além de tremer e perder força, combustível não queimado pode derreter o catalisador.", C, "Se a luz da injeção estiver piscando, reduza a velocidade e evite acelerar forte. No 206, bobina e cabos de vela são os suspeitos mais comuns."),
        d("P0301", "Falha no cilindro 1", "O cilindro 1 não está queimando direito. O motor treme e perde força, e pode danificar o catalisador.", C, "Causas comuns: vela, cabo de vela, bobina ou bico injetor."),
        d("P0302", "Falha no cilindro 2", "O cilindro 2 não está queimando direito. O motor treme e perde força, e pode danificar o catalisador.", C, "Causas comuns: vela, cabo de vela, bobina ou bico injetor."),
        d("P0303", "Falha no cilindro 3", "O cilindro 3 não está queimando direito. O motor treme e perde força, e pode danificar o catalisador.", C, "Causas comuns: vela, cabo de vela, bobina ou bico injetor."),
        d("P0304", "Falha no cilindro 4", "O cilindro 4 não está queimando direito. O motor treme e perde força, e pode danificar o catalisador.", C, "Causas comuns: vela, cabo de vela, bobina ou bico injetor."),
        // Sensores de motor
        d("P0325", "Sensor de detonação com defeito", "O sensor que protege o motor contra a 'batida de pino' está com problema. O carro fica mais fraco por segurança.", H, "Evite gasolina de procedência duvidosa até consertar."),
        d("P0327", "Sensor de detonação com sinal baixo", "O sensor anti batida de pino está com sinal fraco. O motor perde força por segurança.", H),
        d("P0335", "Sensor de rotação com defeito", "É o sensor mais importante do motor: diz à central a posição e a rotação do motor. Se ele parar, o carro morre e não pega mais.", C, "Se o carro está morrendo do nada, principalmente quente, esse sensor é suspeito."),
        d("P0336", "Sensor de rotação com sinal irregular", "O sinal de rotação está falhando. O carro pode morrer ou falhar de repente.", C),
        d("P0340", "Sensor de fase com defeito", "O sensor do comando de válvulas está com problema. A partida pode ficar difícil.", H),
        // Bobina
        d("P0351", "Bobina de ignição A com defeito", "A bobina que gera a faísca das velas está com problema. O motor falha.", C, "No motor TU3 a bobina é dupla: pode afetar 2 cilindros."),
        d("P0352", "Bobina de ignição B com defeito", "A bobina que gera a faísca das velas está com problema. O motor falha.", C, "No motor TU3 a bobina é dupla: pode afetar 2 cilindros."),
        // Emissões
        d("P0400", "Sistema de recirculação de gases com defeito", "Problema no sistema que reduz poluição. Normalmente não afeta a dirigibilidade.", L),
        d("P0420", "Catalisador com eficiência baixa", "O catalisador, que limpa os gases do escapamento, está gasto. O carro polui mais, mas continua andando normalmente.", L, "Antes de trocar o catalisador, verifique as sondas lambda e falhas de ignição."),
        d("P0440", "Sistema do cânister com defeito", "Problema no sistema que recolhe os vapores do tanque. Às vezes é só a tampa do tanque mal fechada.", L),
        d("P0441", "Válvula do cânister com fluxo incorreto", "A válvula que recolhe os vapores do tanque não está funcionando bem. Pode dar cheiro de combustível.", L),
        d("P0443", "Válvula do cânister com defeito elétrico", "Problema elétrico na válvula dos vapores do tanque. Pouco impacto no funcionamento.", L),
        d("P0455", "Vazamento grande de vapores do tanque", "Vazamento no sistema de vapores do tanque. Confira se a tampa do tanque está bem fechada.", L),
        // Velocidade / marcha lenta
        d("P0500", "Sensor de velocidade com defeito", "O sensor que informa a velocidade está com problema. O velocímetro pode parar e a marcha lenta oscilar.", H),
        d("P0505", "Controle da marcha lenta com defeito", "O motor não consegue controlar a marcha lenta. O carro pode morrer quando você para no semáforo.", H, "No 206, limpar o corpo de borboleta e o motor de passo resolve muitos casos."),
        d("P0506", "Marcha lenta baixa demais", "A rotação em marcha lenta está abaixo do normal. O carro pode morrer parado.", H, "Limpeza do corpo de borboleta costuma resolver."),
        d("P0507", "Marcha lenta alta demais", "A rotação em marcha lenta está acima do normal. Pode ser entrada falsa de ar ou borboleta suja.", L),
        // Óleo
        d("P0520", "Sensor de pressão do óleo com defeito", "O sensor que vigia a pressão do óleo está com problema. Você pode não ser avisado se faltar óleo.", C, "Confira o nível do óleo na vareta com o motor frio."),
        d("P0524", "Pressão do óleo baixa", "A pressão do óleo está baixa. Rodar assim pode fundir o motor.", C, "Pare o carro e confira o nível do óleo imediatamente."),
        // Elétrica
        d("P0560", "Tensão do sistema elétrico com problema", "A tensão elétrica do carro está fora do normal. Pode ser bateria ou alternador.", H),
        d("P0562", "Tensão da bateria baixa", "A central detectou tensão baixa. A bateria pode estar fraca ou o alternador não está carregando.", H, "Se a bateria tem mais de 3 anos, teste-a numa loja de baterias."),
        d("P0563", "Tensão da bateria alta", "A tensão está alta demais, normalmente regulador do alternador com defeito. Pode danificar a parte elétrica.", C),
        d("P0601", "Memória da central com erro", "A central eletrônica (o 'cérebro' do motor) detectou erro interno.", H),
        d("P0603", "Central eletrônica com erro interno", "Erro na memória da central. Às vezes aparece depois de desligar a bateria e some sozinho.", L),
        d("P0605", "Central eletrônica com erro interno", "A central detectou um erro interno. Se voltar a aparecer, leve a um eletricista automotivo.", H),
        // Arrefecimento
        d("P0480", "Ventoinha com defeito elétrico", "Problema no circuito da ventoinha do radiador. Sem ela, o motor superaquece no trânsito.", C, "Fique atento à temperatura no trânsito e no calor."),
        d("P0481", "Segunda velocidade da ventoinha com defeito", "A velocidade alta da ventoinha não está funcionando. Risco de superaquecer no calor ou com o ar ligado.", H),
        // Comunicação
        d("U0100", "Perda de comunicação com a central do motor", "Os módulos do carro não estão conversando com a central do motor. Normalmente é fio ou conector.", H),
    )

    fun lookup(code: String): DtcInfo {
        known[code]?.let { return it }
        return generic(code)
    }

    private fun generic(code: String): DtcInfo {
        val letter = code.firstOrNull() ?: 'P'
        val manufacturer = code.getOrNull(1) == '1' || code.getOrNull(1) == '3'
        val area = when (letter) {
            'P' -> when (code.getOrNull(2)) {
                '0', '1' -> "medição de ar ou combustível"
                '2' -> "injeção de combustível"
                '3' -> "ignição (faísca) ou falhas de queima"
                '4' -> "controle de emissões (poluição)"
                '5' -> "velocidade, marcha lenta ou parte elétrica"
                '6' -> "central eletrônica"
                '7', '8', '9' -> "câmbio"
                else -> "motor"
            }
            'C' -> "chassi (freios, suspensão ou direção)"
            'B' -> "carroceria (airbag, travas, painel)"
            else -> "comunicação entre módulos"
        }
        val severity = when {
            letter == 'P' && code.getOrNull(2) == '3' -> Severity.HIGH
            letter == 'C' -> Severity.HIGH
            else -> Severity.LOW
        }
        val prefix = if (manufacturer) "Código específico do fabricante" else "Código genérico"
        return DtcInfo(
            code = code,
            title = "Problema no sistema de $area",
            explanation = "$prefix, ligado ao sistema de $area. O app não tem a descrição exata deste código. Mostre-o ao mecânico, que tem a tabela da Peugeot.",
            severity = severity,
        )
    }
}
