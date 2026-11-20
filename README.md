# OBD Fácil

App Android simples para ler os dados do carro por um adaptador **ELM327 Bluetooth** (OBD2 / OBD-BR2).
Feito pensando no Peugeot 206 1.4 Flex, mas funciona em qualquer carro com tomada OBD2.

## O que ele faz
- **Painel**: semáforo geral (tudo certo / atenção / pare o carro), temperatura do motor, bateria, rotação e velocidade em letras grandes.
- **Alertas**: superaquecimento, bateria que não carrega e falhas graves aparecem em vermelho, em tela cheia e com vibração.
- **Diagnóstico**: lê e apaga os códigos de falha, explicando cada um em português simples, com a gravidade e o que fazer.
- **Histórico**: cada viagem e cada leitura de falhas fica salva no celular. Exporta relatório em **PDF** (para o mecânico) e **planilha CSV** (Excel).
- **Modo demonstração**: para conhecer o app sem estar no carro.

## Como instalar
1. Abra a aba **Releases** deste repositório pelo celular e baixe o `OBD-Facil.apk` mais recente.
2. Toque no arquivo e permita "instalar apps desconhecidos" quando o Android pedir.

## Como compilar
O GitHub Actions compila sozinho a cada envio para o branch `main` (`.github/workflows/build.yml`).
Localmente: `./gradlew assembleRelease` (precisa do Android SDK).
