# Chat Cifrado - Aplicação em Rede (TCP & UDP)

Trabalho Prático da disciplina de **Redes de Computadores I** — PUC Minas.

Aplicação de rede cliente-servidor desenvolvida em Java (com interface gráfica Swing no cliente e no servidor), demonstrando o uso simultâneo de **TCP**, **UDP**, **multithreading**, **cifras didáticas** e **redirecionamento de portas (NAT)** através de roteadores.

---

## 📌 Visão Geral do Projeto

A aplicação consiste em um chat bidirecional seguro/cifrado entre duas máquinas:
- **PC1 (Cliente)**: Interface gráfica Swing com campos para IP/porta, escolha de algoritmo de cifra (César, Vigenère ou Nenhuma), área de chat e painel de logs em tempo real dividido em abas.
- **PC2 (Servidor)**: Processo multithread que atende múltiplos clientes simultaneamente via TCP, responde a datagramas UDP e permite envio de mensagens globais (*broadcast*).

### Por que TCP e UDP na mesma aplicação?
- **TCP (porta 5000)**: Utilizado para a troca de mensagens de texto (chat). Demanda conexão confiável, entrega ordenada e controle de integridade para que nenhuma mensagem ou caractere seja perdido ou corrompido.
- **UDP (porta 5000)**: Utilizado para envio periódico de sondas de latência (`PING`/`PONG`), medição de RTT (*Round Trip Time*) e diagnóstico de perda de pacotes. Demanda baixo overhead e dispensa handshake/confirmação pesada.

---

## 🌐 Topologia de Rede e Roteamento (NAT)

A topologia física do laboratório é composta por **2 roteadores físicos** (o roteador intermediário R2 foi omitido, ligando R1 diretamente a R3 via cabo RJ45):

```text
[ PC1 - Cliente (Mac) ]
       │ IP: 192.168.1.x (ex.: 192.168.1.10)
       ▼ Gateway: 192.168.1.254 (Rede: 192.168.0.0/16 ou /24)
┌────────────────────────────────────────────────────────┐
│ Roteador 1 (R1) - Nokia G-1425G-A                      │
│ - LAN IP: 192.168.1.254                                │
│ - Port Forwarding: Porta 5000 (TCP/UDP) -> 192.168.1.200│
└───────────────────────────┬────────────────────────────┘
                            │ Cabo RJ45 (Porta LAN 1 do R1 ──► Porta WAN do R3)
                            ▼
┌────────────────────────────────────────────────────────┐
│ Roteador 3 (R3) - TP-Link TL-WR642G                    │
│ - WAN IP (Estático): 192.168.1.200 (Gateway: 192.168.1.254)
│ - LAN IP: 10.0.0.1 (Rede: 10.0.0.0/8 ou /24)           │
│ - Virtual Server / Forwarding:                         │
│     Porta 5000 (TCP/UDP) -> 10.0.0.10:5000 (PC2)       │
└───────────────────────────┬────────────────────────────┘
                            │ Porta LAN do R3
                            ▼
[ PC2 - Servidor (Windows) ] (IP: 10.0.0.10, Gateway: 10.0.0.1, Porta 5000 TCP e UDP)
```

### Regras de Roteamento e Conexão
1. **Destino da Conexão do Cliente (PC1 / Mac)**:
   - O PC1 está fisicamente conectado à rede LAN do R1 (Nokia).
   - A interface WAN do R3 (TP-Link) também está conectada à rede LAN do R1 com o IP estático `192.168.1.200`.
   - Portanto, PC1 e a WAN do R3 compartilham o mesmo domínio de rede local (faixa `192.168.1.x`).
   - O cliente Java no Mac conecta-se diretamente ao **IP da WAN do R3 (`192.168.1.200`)** na porta `5000` (TCP e UDP), **nunca** diretamente ao IP privado `10.0.0.10` (que está isolado atrás do NAT do R3).
2. **Redirecionamento de Portas (Port Forwarding / NAT)**:
   - **R3 (TP-Link)**: Ao receber requisições TCP e datagramas UDP na porta `5000` de sua WAN (`192.168.1.200`), redireciona via *Virtual Server* para o IP do PC2 (`10.0.0.10:5000`).
   - **R1 (Nokia)**: Possui regra de *Port Forwarding* da porta `5000` apontando para o IP da WAN do R3 (`192.168.1.200`).
3. **Adaptação de Máscara de Sub-rede**:
   - Caso o firmware dos roteadores restrinja máscaras `/16` ou `/8` na interface web, utiliza-se a máscara padrão `/24` (`255.255.255.0`). Os hosts continuam operando normalmente nas sub-redes `192.168.1.x` e `10.0.0.x`.

---

## 🧵 Arquitetura Multithreading

### Cliente (`Cliente.java`)
- **EDT (Event Dispatch Thread)**: Gerencia a interface gráfica Swing e reage aos cliques e digitação.
- **`tcp-leitor`**: Thread dedicada para efetuar a conexão `Socket.connect()` e bloquear em `BufferedReader.readLine()` aguardando mensagens recebidas sem travar a interface.
- **`udp-ping`**: Thread agendada (`ScheduledExecutorService`) disparada a cada 2 segundos para enviar sondas UDP e calcular RTT com timeout de 1 segundo.

### Servidor (`Servidor.java`)
- **`tcp-accept`**: Loop que bloqueia em `ServerSocket.accept()`. A cada novo cliente conectado, instancia um `Atendente` e despacha para o pool de threads.
- **`pool` (CachedThreadPool)**: Cada cliente conectado é atendido por uma thread independente da classe interna `Atendente`.
- **`udp-recv`**: Thread dedicada que escuta datagramas em `DatagramSocket.receive()` e devolve o eco `PONG` para o endereço e porta de origem.

---

## 🔒 Cifras Didáticas (`Cifra.java`)

Os algoritmos implementados operam sobre caracteres ASCII (A-Z, a-z):
- **CÉSAR**: Cifra de substituição monoalfabética baseada no deslocamento inteiro informado como chave (ex.: `3`).
- **VIGENÈRE**: Cifra polialfabética que utiliza uma palavra-chave para alternar deslocamentos letra a letra (ex.: `"redes"`).
- **NENHUMA**: Texto trafega em claro, ideal para comparação e validação no Wireshark.

> **Nota didática**: A chave viaja no pacote (`MSG|ALGORITMO|CHAVE|TEXTO_CIFRADO`) para fins demonstrativos e de inspeção acadêmica.

---

## 📡 Protocolo da Camada de Aplicação

### TCP (Texto delimitado por quebra de linha `\n`, codificação UTF-8)
- **Mensagem**: `MSG|<ALGORITMO>|<CHAVE>|<TEXTO_CIFRADO>`
  - *Exemplo*: `MSG|CESAR|3|rod` (texto decifrado: `ola`)
- **Erro**: `ERR|<motivo>`

### UDP (Datagramas independentes, UTF-8)
- **Cliente envia**: `PING|<seq>|<timestampNanos>`
- **Servidor responde**: `PONG|<seq>|<timestampNanos>`

---

## 🖥️ Interface Gráfica e Visualização em Tempo Real

Para facilitar a apresentação e a visualização ao vivo:
- **Área de Chat**: Mostra as mensagens trocadas já decifradas.
- **Painel de Log com Abas**:
  - `TCP / Cifra`: Acompanha conexão, envio/recebimento de pacotes TCP e o passo a passo da cifra sem interferência.
  - `UDP (Ping)`: Registra os envios de PING, recepção de PONG, medições de RTT e eventuais timeouts/perdas.
  - `Todos`: Visão cronológica unificada de todos os eventos da aplicação.
- **Barra de Status**: Indica estado da conexão TCP e estatísticas resumidas de UDP (RTT atual, pacotes enviados, perdidos e taxa percentual).

---

## 🚀 Como Compilar e Executar

### Pré-requisitos
- Java Development Kit (JDK 11 ou superior instalado).
- Sem nenhuma biblioteca externa (apenas Java Standard Edition).

### 1. Compilação
Na raiz do projeto:
```bash
javac -encoding UTF-8 app/*.java
```

### 2. Executando o Servidor (PC2 ou teste local)
```bash
java -cp app Servidor 5000
```
*(O servidor exibirá a lista de IPs locais e iniciará a escuta nas portas TCP e UDP).*

### 3. Executando o Cliente (PC1 ou teste local)
```bash
java -cp app Cliente
```

### 4. Cenários de Teste

#### A. Teste Local (mesma máquina)
- No campo **IP WAN R3 (ou Servidor)** do cliente: digite `127.0.0.1`.
- Clique em **Conectar**.
- Envie mensagens alternando os algoritmos de cifra (César, Vigenère, Nenhuma).
- Digite uma mensagem no servidor para testar o envio aos clientes.

#### B. Teste na Topologia com Roteadores Físicos (Laboratório)
- Certifique-se de que os PCs e roteadores estão configurados conforme detalhado no [Comandos.md](Comandos.md):
  - **PC1 (Mac)**: IP `192.168.1.10` (Gateway `192.168.1.254` do R1 Nokia).
  - **PC2 (Windows)**: IP `10.0.0.10` (Gateway `10.0.0.1` do R3 TP-Link).
  - **R3 (TP-Link)**: WAN `192.168.1.200` com *Virtual Server* na porta `5000` (TCP/UDP) para `10.0.0.10:5000`.
- No PC2 (Windows), inicie o `Servidor`:
  ```bash
  java -cp app Servidor 5000
  ```
- No PC1 (Mac), inicie o `Cliente`:
  ```bash
  java -cp app Cliente
  ```
- No campo **IP WAN R3 (ou Servidor)** do cliente, utilize o IP da WAN do R3: `192.168.1.200`, porta `5000`, e clique em **Conectar**.

---

## 🦈 Análise no Wireshark

Filtros recomendados para captura e análise dos pacotes durante a demonstração:
- Capturar apenas o tráfego da aplicação:
  ```text
  tcp.port == 5000 || udp.port == 5000
  ```
- Observar o handshake de 3 vias do TCP (SYN, SYN-ACK, ACK):
  ```text
  tcp.port == 5000 && tcp.flags.syn == 1
  ```
- Inspecionar o texto cifrado vs texto em claro no payload TCP:
  - Clicar com o botão direito no pacote TCP -> **Follow** -> **TCP Stream**.
- Observar o tráfego de datagramas UDP sem conexão:
  ```text
  udp.port == 5000
  ```

---

## 📂 Estrutura do Repositório

```text
.
├── app/
│   ├── Cifra.java       # Implementação das cifras (César, Vigenère, Nenhuma)
│   ├── Servidor.java    # Servidor multithread TCP/UDP com interface Swing
│   └── Cliente.java     # Cliente com interface gráfica Swing e abas de log
├── Comandos.md          # Roteiro de configuração dos roteadores (rotas e NAT)
├── ProjetoRede.pkt      # Topologia simulada no Cisco Packet Tracer
└── README.md            # Documentação completa do projeto
```
